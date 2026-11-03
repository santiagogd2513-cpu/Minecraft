package com.amatista.arsenal.client;

import com.amatista.arsenal.ArsenalOrbital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArsenalOrbital.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static final ResourceLocation OUR_OVERLAY = new ResourceLocation(ArsenalOrbital.MODID, "cinematic");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Cinematic.clientTick();
        WorldEffects.tick();
        // La cinemática no se puede omitir: se descartan las pulsaciones de salto mientras dura
        Minecraft mc = Minecraft.getInstance();
        // H: menú de armas
        while (ClientSetup.WEAPONS.consumeClick()) {
            if (mc.screen == null && !Cinematic.isActive() && mc.player != null) mc.setScreen(new WeaponSelectScreen());
        }
        if (Cinematic.isActive()) {
            while (mc.options.keyJump.consumeClick()) { }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            AscentSky.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            WorldEffects.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            if (Cinematic.isSpace(event.getPartialTick())) {
                if (Cinematic.type() == com.amatista.arsenal.StrikeType.PHOBOS) MarsRenderer.render(event.getPartialTick());
                else if (Cinematic.type() == com.amatista.arsenal.StrikeType.BLACKHOLE) BlackHoleRenderer.render(event.getPartialTick());
                else if (Cinematic.type() == com.amatista.arsenal.StrikeType.STAR) StarRenderer.render(event.getPartialTick());
                else if (Cinematic.type() == com.amatista.arsenal.StrikeType.GENESIS) GenesisRenderer.render(event.getPartialTick());
                else SpaceRenderer.render(event.getPartialTick());
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!Cinematic.isActive()) return;
        Cinematic.Pose p = Cinematic.worldPose(Cinematic.progress((float) event.getPartialTick()));
        event.setYaw(p.yaw());
        event.setPitch(p.pitch());
        event.setRoll(p.roll());
    }

    /** La niebla del suelo toma el color del cielo mientras la cámara sube (o baja del espacio). */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float[] c = AscentSky.fogColor(event.getCamera().getPosition().y);
        if (c == null) return;
        event.setRed(net.minecraft.util.Mth.lerp(c[3], event.getRed(), c[0]));
        event.setGreen(net.minecraft.util.Mth.lerp(c[3], event.getGreen(), c[1]));
        event.setBlue(net.minecraft.util.Mth.lerp(c[3], event.getBlue(), c[2]));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!Cinematic.isActive()) return;
        event.setFOV(Cinematic.worldPose(Cinematic.progress((float) event.getPartialTick())).fov());
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!Cinematic.isActive()) return;
        Input in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!Cinematic.isActive()) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Cinematic.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (Cinematic.isActive() && !OUR_OVERLAY.equals(event.getOverlay().id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Cinematic.stop();
        WorldEffects.clear();
    }

    private ClientEvents() {}
}
