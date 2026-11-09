package com.arsenalatomico.client;

import com.arsenalatomico.ArsenalAtomico;
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

@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static final ResourceLocation OUR_OVERLAY = new ResourceLocation(ArsenalAtomico.MODID, "cinematic");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Cinematic.clientTick();
        Minecraft mc = Minecraft.getInstance();
        while (ClientSetup.WEAPONS.consumeClick()) {
            if (mc.screen == null && !Cinematic.isActive() && mc.player != null) mc.setScreen(new WeaponSelectScreen());
        }
        if (Cinematic.isActive()) while (mc.options.keyJump.consumeClick()) { }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            LauncherRenderer.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
            TwinRenderer.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!Cinematic.isActive()) return;
        Cinematic.Pose p = Cinematic.pose((float) e.getPartialTick());
        e.setYaw(p.yaw());
        e.setPitch(p.pitch());
        e.setRoll(p.roll());
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!Cinematic.isActive()) return;
        e.setFOV(Cinematic.pose((float) e.getPartialTick()).fov());
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent e) {
        if (!Cinematic.isActive()) return;
        Input in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!Cinematic.isActive()) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        if (Cinematic.isActive()) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (Cinematic.isActive() && !OUR_OVERLAY.equals(e.getOverlay().id())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        Cinematic.stop();
    }

    private ClientEvents() {}
}
