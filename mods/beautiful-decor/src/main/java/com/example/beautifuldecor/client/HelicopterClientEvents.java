package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.entity.HelicopterEntity;
import com.example.beautifuldecor.network.AirlinerActionPacket;
import com.example.beautifuldecor.network.HelicopterActionPacket;
import com.example.beautifuldecor.network.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Lado cliente del helicóptero (V3.0.0): teclas (Espacio sube, Shift baja, W/A/S/D, Z para bajarse), giro de la vista
 * con el helicóptero, indicadores en pantalla y sonido del rotor.
 * <p>
 * Shift NO baja del helicóptero (en vanilla es la tecla para bajarse de cualquier vehículo): aquí se usa para descender
 * y se le quita al juego, igual que hace el dron. Para bajarte es la Z.
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class HelicopterClientEvents {
    private static final Map<Integer, HelicopterRotorSound> ROTOR_SOUNDS = new HashMap<>();
    /** Cuánto puede girar la cabeza quien va montado respecto al helicóptero (grados). */
    private static final float LOOK_LIMIT = 150.0f;

    private HelicopterClientEvents() {}

    /** El helicóptero en el que va montado este jugador, o null. */
    private static HelicopterEntity ridden(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof HelicopterEntity helicopter
                && !helicopter.isRemoved()) {
            return helicopter;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Teclas
    // ------------------------------------------------------------------------------------------------

    /** W/S/A/D, Espacio y Shift se los pasamos al helicóptero; a Shift se le quita el efecto de bajarse. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        HelicopterEntity helicopter = ridden(minecraft);
        if (helicopter == null || event.getEntity() != minecraft.player) {
            return;
        }
        Input input = event.getInput();
        if (helicopter.getControllingPassenger() == minecraft.player) {
            helicopter.setRiderInput(input.up, input.down, input.left, input.right, input.jumping, input.shiftKeyDown);
        }
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopAllRotorSounds(minecraft);
            return;
        }

        HelicopterEntity helicopter = ridden(minecraft);
        // Se vacía siempre la cola de pulsaciones, aunque no vayas montado, para que no se acumulen.
        AirlinerEntity airliner = AirlinerClientEvents.ridden(minecraft);        // V3.8.0: la misma Z baja también del avión
        while (HelicopterKeys.EXIT.consumeClick()) {
            if (helicopter != null && minecraft.screen == null) {
                ModMessages.CHANNEL.sendToServer(
                        new HelicopterActionPacket(helicopter.getId(), HelicopterActionPacket.ACTION_EXIT));
            } else if (airliner != null && minecraft.screen == null) {
                ModMessages.CHANNEL.sendToServer(
                        new AirlinerActionPacket(airliner.getId(), AirlinerActionPacket.ACTION_EXIT));
            }
        }
        updateRotorSounds(minecraft);
    }

    // ------------------------------------------------------------------------------------------------
    // La vista gira junto con el helicóptero
    // ------------------------------------------------------------------------------------------------

    /**
     * Cuando el helicóptero gira, la cabeza del jugador gira con él (como en una barca). Va al final del tick del
     * jugador para que la cámara lo interpole suave.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = event.player;
        if (player != minecraft.player || !(player.getVehicle() instanceof HelicopterEntity helicopter)) {
            return;
        }
        float delta = helicopter.getYawDelta();
        if (delta != 0.0f) {
            player.setYRot(player.getYRot() + delta);
            player.setYHeadRot(player.getYHeadRot() + delta);
        }
        float relative = Mth.wrapDegrees(player.getYRot() - helicopter.getYRot());
        float clamped = Mth.clamp(relative, -LOOK_LIMIT, LOOK_LIMIT);
        if (clamped != relative) {
            player.yRotO += clamped - relative;
            player.setYRot(player.getYRot() + clamped - relative);
        }
        player.setYBodyRot(helicopter.getYRot());
        player.setYHeadRot(player.getYRot());
    }

    // ------------------------------------------------------------------------------------------------
    // Indicadores en pantalla
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void renderHud(RenderGuiOverlayEvent.Post event) {
        // Este evento llega una vez por cada capa del HUD; se dibuja solo con la mira para no repetir.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        HelicopterEntity helicopter = ridden(minecraft);
        if (helicopter == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int y = height / 2 + 34;

        String status;
        if (helicopter.getControllingPassenger() == minecraft.player && helicopter.getRpm() < HelicopterEntity.TAKEOFF_RPM) {
            status = Component.translatable("hud.beautifuldecor.helicopter_spooling").getString();
        } else {
            status = Component.translatable("hud.beautifuldecor.helicopter_status",
                    String.valueOf(Math.round(helicopter.getY())),
                    String.valueOf(Math.round(helicopter.getVisualSpeed() * 72.0f))).getString();
        }
        graphics.drawString(minecraft.font, status, (width - minecraft.font.width(status)) / 2, y, 0xFFFFFFFF, true);

        String controls = Component.translatable("hud.beautifuldecor.helicopter_controls",
                HelicopterKeys.EXIT.getTranslatedKeyMessage().getString()).getString();
        graphics.drawString(minecraft.font, controls, Math.max(6, (width - minecraft.font.width(controls)) / 2),
                height - 58, 0xFFB8B8C4, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Sonido del rotor
    // ------------------------------------------------------------------------------------------------

    private static void updateRotorSounds(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof HelicopterEntity helicopter && !helicopter.isRemoved() && helicopter.getRpm() > 0.05f
                    && !ROTOR_SOUNDS.containsKey(helicopter.getId())) {
                HelicopterRotorSound sound = new HelicopterRotorSound(helicopter);
                ROTOR_SOUNDS.put(helicopter.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
        Iterator<Map.Entry<Integer, HelicopterRotorSound>> iterator = ROTOR_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            HelicopterRotorSound sound = iterator.next().getValue();
            if (sound.isFinished()) {
                minecraft.getSoundManager().stop(sound);
                iterator.remove();
            }
        }
    }

    private static void stopAllRotorSounds(Minecraft minecraft) {
        for (HelicopterRotorSound sound : ROTOR_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        ROTOR_SOUNDS.clear();
    }
}
