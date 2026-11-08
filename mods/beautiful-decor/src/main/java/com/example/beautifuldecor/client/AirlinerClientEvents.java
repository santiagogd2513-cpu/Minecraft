package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.network.AirlinerActionPacket;
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
 * Lado cliente del avión (V3.8.0): teclas (W/S acelerar y frenar, A/D girar, Espacio sube con el morro arriba, Shift baja con el morro
 * abajo, F tren de aterrizaje, Z bajarse), vista que acompaña al avión al girar y al cabecear, indicadores en pantalla y sonido de las turbinas.
 * <p>
 * La tecla Z de bajarse es la misma del helicóptero (ver {@link HelicopterClientEvents}: una tecla solo se puede consumir en un sitio).
 * Shift NO baja del avión (en vanilla es la tecla para bajarse de cualquier vehículo): aquí se usa para descender y se le quita al juego.
 * La F es también la del cambio de objeto de mano de vanilla: mientras pilotas se le quita ese efecto.
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class AirlinerClientEvents {
    private static final Map<Integer, AirlinerEngineSound> ENGINE_SOUNDS = new HashMap<>();

    private AirlinerClientEvents() {}

    /** El avión en el que va montado este jugador, o null. */
    public static AirlinerEntity ridden(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof AirlinerEntity airliner && !airliner.isRemoved()) {
            return airliner;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Teclas
    // ------------------------------------------------------------------------------------------------

    /** W/S/A/D, Espacio y Shift se los pasamos al avión (si eres el piloto); a Shift se le quita el efecto de bajarse. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        AirlinerEntity airliner = ridden(minecraft);
        if (airliner == null || event.getEntity() != minecraft.player) {
            return;
        }
        Input input = event.getInput();
        if (airliner.getControllingPassenger() == minecraft.player) {
            airliner.setRiderInput(input.up, input.down, input.left, input.right, input.jumping, input.shiftKeyDown);
        }
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            // Antes de que el juego procese las teclas: si F también es "cambiar objeto de mano", se descarta mientras pilotas.
            AirlinerEntity piloted = ridden(minecraft);
            if (piloted != null && piloted.getControllingPassenger() == minecraft.player
                    && minecraft.options.keySwapOffhand.getKey().equals(AirlinerKeys.GEAR.getKey())) {
                while (minecraft.options.keySwapOffhand.consumeClick()) {
                    // se descarta
                }
            }
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            stopAllSounds(minecraft);
            return;
        }

        AirlinerEntity airliner = ridden(minecraft);
        // Se vacía siempre la cola de pulsaciones, aunque no vayas montado, para que no se acumulen.
        while (AirlinerKeys.GEAR.consumeClick()) {
            if (airliner != null && airliner.getControllingPassenger() == minecraft.player && minecraft.screen == null) {
                ModMessages.CHANNEL.sendToServer(new AirlinerActionPacket(airliner.getId(), AirlinerActionPacket.ACTION_GEAR));
            }
        }
        updateEngineSounds(minecraft);
    }

    // ------------------------------------------------------------------------------------------------
    // La vista gira junto con el avión (rumbo y cabeceo)
    // ------------------------------------------------------------------------------------------------

    /**
     * Cuando el avión gira o cabecea, la cabeza de quien va dentro gira con él (como en una barca). Va al final del tick del jugador
     * para que la cámara lo interpole suave.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = event.player;
        if (player != minecraft.player || !(player.getVehicle() instanceof AirlinerEntity airliner)) {
            return;
        }
        float yaw = airliner.getYawDelta();
        if (yaw != 0.0f) {
            player.setYRot(player.getYRot() + yaw);
            player.setYHeadRot(player.getYHeadRot() + yaw);
        }
        float pitch = airliner.getPitchDelta();
        if (pitch != 0.0f) {
            player.setXRot(Mth.clamp(player.getXRot() + pitch, -90.0f, 90.0f));
        }
        player.setYBodyRot(airliner.getYRot());
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
        AirlinerEntity airliner = ridden(minecraft);
        if (airliner == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int y = height / 2 + 34;
        boolean pilot = airliner.getControllingPassenger() == minecraft.player;

        String status;
        if (pilot && airliner.getRpm() < AirlinerEntity.TAKEOFF_RPM) {
            status = Component.translatable("hud.beautifuldecor.airliner_spooling").getString();
        } else {
            String gear = Component.translatable(airliner.isGearUp()
                    ? "hud.beautifuldecor.airliner_gear_word_up" : "hud.beautifuldecor.airliner_gear_word_down").getString();
            status = Component.translatable("hud.beautifuldecor.airliner_status",
                    String.valueOf(Math.round(airliner.getY())),
                    String.valueOf(Math.round(airliner.getVisualSpeed() * 20.0f)), gear).getString();
        }
        graphics.drawString(minecraft.font, status, (width - minecraft.font.width(status)) / 2, y, 0xFFFFFFFF, true);

        String exit = HelicopterKeys.EXIT.getTranslatedKeyMessage().getString();
        String controls = pilot
                ? Component.translatable("hud.beautifuldecor.airliner_controls",
                        AirlinerKeys.GEAR.getTranslatedKeyMessage().getString(), exit).getString()
                : Component.translatable("hud.beautifuldecor.airliner_passenger", exit).getString();
        graphics.drawString(minecraft.font, controls, Math.max(6, (width - minecraft.font.width(controls)) / 2),
                height - 58, 0xFFB8B8C4, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Sonido de las turbinas
    // ------------------------------------------------------------------------------------------------

    private static void updateEngineSounds(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof AirlinerEntity airliner && !airliner.isRemoved() && airliner.getRpm() > 0.05f
                    && airliner.distanceToSqr(minecraft.player) < 160.0 * 160.0
                    && !ENGINE_SOUNDS.containsKey(airliner.getId())) {
                AirlinerEngineSound sound = new AirlinerEngineSound(airliner);
                ENGINE_SOUNDS.put(airliner.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
        Iterator<Map.Entry<Integer, AirlinerEngineSound>> iterator = ENGINE_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            AirlinerEngineSound sound = iterator.next().getValue();
            if (sound.isFinished() || sound.isStopped()) {
                minecraft.getSoundManager().stop(sound);
                iterator.remove();
            }
        }
    }

    private static void stopAllSounds(Minecraft minecraft) {
        for (AirlinerEngineSound sound : ENGINE_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        ENGINE_SOUNDS.clear();
    }
}
