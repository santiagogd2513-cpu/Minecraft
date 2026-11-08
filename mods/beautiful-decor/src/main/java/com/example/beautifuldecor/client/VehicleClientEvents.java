package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.VehicleEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.VehicleActionPacket;
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
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Lado cliente de los vehículos (V2.0.0 la moto; V2.1.0 también bicicleta y camioneta): teclas, giro de la vista con
 * el vehículo, campo de visión según la velocidad, velocímetro y sonido del motor.
 */
@Mod.EventBusSubscriber(
        modid = BeautifulDecor.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class VehicleClientEvents {
    private static final Map<Integer, VehicleEngineSound> ENGINE_SOUNDS = new HashMap<>();

    private VehicleClientEvents() {}

    /** El vehículo que está montando este jugador, o null. */
    private static VehicleEntity ridden(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof VehicleEntity vehicle
                && !vehicle.isRemoved()) {
            return vehicle;
        }
        return null;
    }

    /** El vehículo que este jugador CONDUCE (no el que va de acompañante), o null. */
    private static VehicleEntity driven(Minecraft minecraft) {
        VehicleEntity vehicle = ridden(minecraft);
        return vehicle != null && vehicle.getControllingPassenger() == minecraft.player ? vehicle : null;
    }

    // ------------------------------------------------------------------------------------------------
    // Teclas
    // ------------------------------------------------------------------------------------------------

    /** W/S/A/D y Espacio (freno de mano) se los pasamos al vehículo; Shift se queda para bajarse. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        VehicleEntity vehicle = driven(minecraft);
        if (vehicle == null || event.getEntity() != minecraft.player) {
            return;
        }
        Input input = event.getInput();
        vehicle.setRiderInput(input.up, input.down, input.left, input.right, input.jumping);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopAllEngineSounds(minecraft);
            return;
        }

        VehicleEntity vehicle = driven(minecraft);
        // Se vacía siempre la cola de pulsaciones, aunque no conduzcas, para que no se acumulen.
        while (VehicleKeys.LIGHT.consumeClick()) {
            if (vehicle != null && minecraft.screen == null) {
                ModMessages.CHANNEL.sendToServer(new VehicleActionPacket(vehicle.getId(), VehicleEntity.ACTION_LIGHT));
            }
        }
        while (VehicleKeys.HORN.consumeClick()) {
            if (vehicle != null && minecraft.screen == null) {
                ModMessages.CHANNEL.sendToServer(new VehicleActionPacket(vehicle.getId(), VehicleEntity.ACTION_HORN));
            }
        }
        updateEngineSounds(minecraft);
    }

    // ------------------------------------------------------------------------------------------------
    // La vista gira junto con el vehículo
    // ------------------------------------------------------------------------------------------------

    /**
     * Cuando el vehículo gira, la cabeza del jugador gira con él (como en una barca). Va al final del tick del
     * jugador para que la cámara lo interpole suave. La mirada no puede pasar del límite de cada vehículo
     * (105 grados en la moto, 140 en la camioneta) respecto a él.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = event.player;
        if (player != minecraft.player || !(player.getVehicle() instanceof VehicleEntity moto)) {
            return;
        }
        float limit = moto.getSpec().lookLimit();
        float delta = moto.getYawDelta();
        if (delta != 0.0f) {
            player.setYRot(player.getYRot() + delta);
            player.setYHeadRot(player.getYHeadRot() + delta);
        }
        float relative = Mth.wrapDegrees(player.getYRot() - moto.getYRot());
        float clamped = Mth.clamp(relative, -limit, limit);
        if (clamped != relative) {
            player.yRotO += clamped - relative;
            player.setYRot(player.getYRot() + clamped - relative);
        }
        player.setYBodyRot(moto.getYRot());
        player.setYHeadRot(player.getYRot());
    }

    /** Sensación de velocidad: el campo de visión se abre cuanto más rápido vas. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        VehicleEntity vehicle = driven(Minecraft.getInstance());
        if (vehicle == null) {
            return;
        }
        float k = Mth.clamp(Math.abs(vehicle.getVisualSpeed()) / vehicle.getMaxSpeed(), 0.0f, 1.0f);
        event.setFOV(event.getFOV() + vehicle.getSpec().fovBoost() * k * k);
    }

    // ------------------------------------------------------------------------------------------------
    // Velocímetro
    // ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void renderSpeedometer(RenderGuiOverlayEvent.Post event) {
        // Este evento llega una vez por cada capa del HUD; se dibuja solo con la mira para no repetir.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        VehicleEntity moto = driven(minecraft);
        if (moto == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        float blocksPerSecond = Math.abs(moto.getVisualSpeed()) * 20.0f;
        float fraction = Mth.clamp(Math.abs(moto.getVisualSpeed()) / moto.getMaxSpeed(), 0.0f, 1.0f);

        String speed = Component.translatable("hud.beautifuldecor.motorcycle_speed",
                String.valueOf(Math.round(blocksPerSecond))).getString();
        int y = height / 2 + 34;
        graphics.drawString(minecraft.font, speed, (width - minecraft.font.width(speed)) / 2, y, 0xFFFFFFFF, true);

        // Barra: negra de fondo, roja de relleno (se pone más clara cerca del tope).
        int barWidth = 82;
        int x0 = (width - barWidth) / 2;
        int y0 = y + 11;
        graphics.fill(x0 - 1, y0 - 1, x0 + barWidth + 1, y0 + 5, 0xFF101014);
        int filled = Math.round(barWidth * fraction);
        graphics.fill(x0, y0, x0 + barWidth, y0 + 4, 0xFF2A2A30);
        graphics.fill(x0, y0, x0 + filled, y0 + 4, fraction > 0.9f ? 0xFFFF5A44 : 0xFFD0141C);

        String light = Component.translatable(moto.isLightOn()
                ? "hud.beautifuldecor.motorcycle_light_on" : "hud.beautifuldecor.motorcycle_light_off").getString();
        graphics.drawString(minecraft.font, light, (width - minecraft.font.width(light)) / 2, y0 + 8,
                moto.isLightOn() ? 0xFFFFE9A0 : 0xFF9A9AA6, true);

        String controls = Component.translatable("hud.beautifuldecor.motorcycle_controls",
                VehicleKeys.LIGHT.getTranslatedKeyMessage().getString(),
                VehicleKeys.HORN.getTranslatedKeyMessage().getString()).getString();
        graphics.drawString(minecraft.font, controls, Math.max(6, (width - minecraft.font.width(controls)) / 2),
                height - 58, 0xFFB8B8C4, true);
    }

    // ------------------------------------------------------------------------------------------------
    // Sonido del motor
    // ------------------------------------------------------------------------------------------------

    private static void updateEngineSounds(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof VehicleEntity vehicle && vehicle.isVehicle() && !vehicle.isRemoved()
                    && vehicle.getEngineLoop() != null && !ENGINE_SOUNDS.containsKey(vehicle.getId())) {
                VehicleEngineSound sound = new VehicleEngineSound(vehicle, vehicle.getEngineLoop());
                ENGINE_SOUNDS.put(vehicle.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
        Iterator<Map.Entry<Integer, VehicleEngineSound>> iterator = ENGINE_SOUNDS.entrySet().iterator();
        while (iterator.hasNext()) {
            VehicleEngineSound sound = iterator.next().getValue();
            if (sound.isFinished()) {
                minecraft.getSoundManager().stop(sound);
                iterator.remove();
            }
        }
    }

    private static void stopAllEngineSounds(Minecraft minecraft) {
        for (VehicleEngineSound sound : ENGINE_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        ENGINE_SOUNDS.clear();
    }
}
