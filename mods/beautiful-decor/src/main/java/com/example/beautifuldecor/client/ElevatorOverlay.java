package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.elevator.ElevatorZone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Contorno de la zona del ascensor (V2.2.0): caja celeste con la capa del piso en amarillo. Se enciende al hacer clic en
 * un controlador y se apaga solo a los 20 segundos.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ElevatorOverlay {
    private static final long DURATION_MS = 20000L;
    private static ElevatorZone zone;
    private static long expiresAt;

    private ElevatorOverlay() {
    }

    public static void show(ElevatorZone newZone) {
        zone = newZone;
        expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    public static void clear() {
        zone = null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || zone == null) {
            return;
        }
        if (System.currentTimeMillis() > expiresAt) {
            zone = null;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double x1 = zone.minX() - cam.x;
        double y1 = zone.minY() - cam.y;
        double z1 = zone.minZ() - cam.z;
        double x2 = x1 + zone.sizeX();
        double y2 = y1 + zone.sizeY();
        double z2 = z1 + zone.sizeZ();
        LevelRenderer.renderLineBox(poseStack, lines, x1, y1, z1, x2, y2, z2, 0.22f, 0.74f, 0.97f, 1.0f);
        LevelRenderer.renderLineBox(poseStack, lines, x1, y1, z1, x2, y1 + 1.0, z2, 0.98f, 0.75f, 0.14f, 1.0f);
        buffers.endBatch(RenderType.lines());
    }
}
