package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * V4.3.0: engancha las cámaras de seguridad al juego en el cliente: dibuja las imágenes al empezar cada cuadro, revisa la visión nocturna
 * (con su clic casi inaudible), agrega al propio jugador a las imágenes (el juego no lo dibuja desde otra cámara) y limpia todo al salir.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraClientEvents {
    /** Hasta dónde se revisa la luz de una cámara aunque ningún monitor la muestre, y hasta dónde se oye su clic (bloques). */
    private static final double CHECK_RANGE = 24.0;
    private static final double CLICK_RANGE = 12.0;

    private CameraClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CameraFeedManager.onRenderTick(Minecraft.getInstance(), event.renderTickTime);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            LocalPlayer me = mc.player;
            if (level == null || me == null || SecurityCameraBlockEntity.CLIENT.isEmpty()) {
                return;
            }
            for (SecurityCameraBlockEntity cam : new ArrayList<>(SecurityCameraBlockEntity.CLIENT)) {
                if (cam.isRemoved() || cam.getLevel() != level) {
                    continue;
                }
                double d2 = cam.getBlockPos().distToCenterSqr(me.position());
                if (d2 > CHECK_RANGE * CHECK_RANGE) {
                    continue;
                }
                if (cam.updateNight(level) && d2 <= CLICK_RANGE * CLICK_RANGE) {
                    Vec3 p = cam.pivot();
                    level.playLocalSound(p.x, p.y, p.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.08f, 1.9f, false);
                }
            }
        } catch (Throwable ignored) {
            // la visión nocturna nunca debe cerrar el juego
        }
    }

    /** El juego no dibuja al jugador local cuando la imagen sale de otra cámara: se agrega aquí, solo en las imágenes de cámara. */
    @SubscribeEvent
    public static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !CameraFeedManager.isRendering()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || p.isInvisible() || p.isSpectator() || !event.getFrustum().isVisible(p.getBoundingBox())) {
                return;
            }
            float pt = event.getPartialTick();
            Vec3 cam = event.getCamera().getPosition();
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            dispatcher.render(p, Mth.lerp(pt, p.xOld, p.getX()) - cam.x, Mth.lerp(pt, p.yOld, p.getY()) - cam.y, Mth.lerp(pt, p.zOld, p.getZ()) - cam.z,
                    Mth.lerp(pt, p.yRotO, p.getYRot()), pt, event.getPoseStack(), mc.renderBuffers().bufferSource(), dispatcher.getPackedLightCoords(p, pt));
            mc.renderBuffers().bufferSource().endBatch();
        } catch (Throwable ignored) {
            // si no se puede dibujar al jugador, la imagen sale sin él
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CameraFeedManager.reset();
        CameraClientRegistry.reset();
    }
}
