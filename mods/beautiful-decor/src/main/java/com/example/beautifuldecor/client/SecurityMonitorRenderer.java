package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.example.beautifuldecor.block.entity.SecurityMonitorBlockEntity;
import com.example.beautifuldecor.camera.CameraClock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * V4.3.0: dibuja en el monitor (o en la pantalla de pared) la imagen de la cámara elegida, con el nombre de la cámara arriba a la
 * izquierda, la hora de Minecraft arriba a la derecha y sus coordenadas muy pequeñas abajo a la izquierda. Si no hay imagen (cámara sin
 * cargar, conectando, sistema no disponible) muestra estática con un aviso. Medidas en dieciseisavos de bloque, con el bloque de la
 * izquierda (vista de frente) como origen y el monitor mirando al norte, como {@link AdScreenRenderer}.
 */
public class SecurityMonitorRenderer implements BlockEntityRenderer<SecurityMonitorBlockEntity> {
    private static final ResourceLocation BLACK = new ResourceLocation("minecraft", "textures/block/black_concrete.png");
    private static final ResourceLocation NOISE = new ResourceLocation(BeautifulDecor.MOD_ID, "textures/block/camera_noise.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float X_LEFT = 15f;
    private static final float X_RIGHT = -15f;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF7CFF8A;
    private static final int RED = 0xFFFF3B30;

    public SecurityMonitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecurityMonitorBlockEntity monitor, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        try {
            draw(monitor, pose, buffer);
        } catch (Throwable t) {
            // un fallo de dibujo no puede cerrar el juego: el monitor queda apagado
        }
    }

    private static void draw(SecurityMonitorBlockEntity monitor, PoseStack pose, MultiBufferSource buffer) {
        BlockPos camPos = monitor.getCamera();
        BlockState state = monitor.getBlockState();
        Level level = monitor.getLevel();
        if (camPos == null || level == null || !(state.getBlock() instanceof SecurityMonitorBlock block)) {
            return;
        }
        boolean wall = block.isWall();
        float yBottom = wall ? 1.5f : 4f;
        float yTop = wall ? 14.5f : 15f;
        float zFront = (wall ? 13.8f : 5.8f) - 0.05f;

        float yaw = (180f + state.getValue(SecurityMonitorBlock.FACING).toYRot()) % 360f;
        pose.pushPose();
        try {
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.translate(-0.5, 0.0, -0.5);
            Matrix4f m = pose.last().pose();
            Font font = Minecraft.getInstance().font;

            // dentro de una imagen de cámara no se vuelve a dibujar otra (sin recursión): pantalla oscura
            if (CameraFeedManager.isRendering()) {
                quad(buffer.getBuffer(RenderType.text(BLACK)), m, yTop, yBottom, zFront, 0f, 0f, 1f, 1f, 0, 0, 0);
                return;
            }

            CameraFeedManager.View view = CameraFeedManager.view(camPos);
            String name = cameraName(level, camPos);
            if (view.status() == CameraFeedManager.Status.LIVE && view.texture() != null) {
                // la textura de un RenderTarget está al revés: arriba es v = 1
                quad(buffer.getBuffer(RenderType.text(view.texture())), m, yTop, yBottom, zFront, 0f, 1f, 1f, 0f, 255, 255, 255);
                liveOverlay(pose, buffer, font, level, camPos, name, view.night(), yTop, yBottom, zFront - 0.05f);
            } else {
                quad(buffer.getBuffer(RenderType.text(BLACK)), m, yTop, yBottom, zFront, 0f, 0f, 1f, 1f, 0, 0, 0);
                long slot = System.currentTimeMillis() / 100L;
                float ou = (float) ((slot * 37L) % 50L) / 100f;
                float ov = (float) ((slot * 59L) % 50L) / 100f;
                quad(buffer.getBuffer(RenderType.text(NOISE)), m, yTop, yBottom, zFront - 0.03f, ou, ov, ou + 0.5f, ov + 0.5f, 150, 150, 150);
                String title = Component.translatable(switch (view.status()) {
                    case CONNECTING -> "gui.beautifuldecor.camera.connecting";
                    case STANDBY -> "gui.beautifuldecor.camera.standby";
                    case UNAVAILABLE -> "gui.beautifuldecor.camera.unavailable";
                    default -> "gui.beautifuldecor.camera.no_signal";
                }).getString();
                float cy = (yTop + yBottom) / 2f;
                centered(pose, buffer, font, title, cy + 1.2f, zFront - 0.1f, 0.0095f, WHITE);
                String small = !view.detail().isEmpty() ? view.detail() : name;
                centered(pose, buffer, font, font.plainSubstrByWidth(small, 190), cy - 1.6f, zFront - 0.1f, 0.0055f, 0xFFC8C8C8);
            }
        } finally {
            pose.popPose();
        }
    }

    private static String cameraName(Level level, BlockPos camPos) {
        if (level.getBlockEntity(camPos) instanceof SecurityCameraBlockEntity cam && !cam.getCameraName().isEmpty()) {
            return cam.getCameraName();
        }
        CameraClientRegistry.Entry e = CameraClientRegistry.get(camPos.asLong());
        return e == null ? "" : e.name();
    }

    private static void liveOverlay(PoseStack pose, MultiBufferSource buffer, Font font, Level level, BlockPos camPos, String name, boolean night,
                                    float yTop, float yBottom, float z) {
        int color = night ? GREEN : WHITE;
        float top = yTop - 0.7f;
        // nombre, arriba a la izquierda
        text(pose, buffer, font, font.plainSubstrByWidth(name, 150), X_LEFT - 0.8f, top, z, 0.0065f, color);
        // hora de Minecraft, arriba a la derecha, con el punto rojo de grabación
        String clock = CameraClock.format(level.getDayTime());
        float clockScale = 0.0065f;
        float clockWidth = font.width(clock) * clockScale * 16f;
        text(pose, buffer, font, clock, X_RIGHT + 0.8f + clockWidth, top, z, clockScale, color);
        if ((System.currentTimeMillis() / 700L) % 2L == 0L) {
            float dotWidth = font.width("●") * clockScale * 16f;
            text(pose, buffer, font, "●", X_RIGHT + 0.8f + clockWidth + dotWidth + 0.5f, top, z, clockScale, RED);
        }
        // coordenadas de la cámara, muy pequeñas, abajo a la izquierda
        String coords = camPos.getX() + ", " + camPos.getY() + ", " + camPos.getZ();
        text(pose, buffer, font, coords, X_LEFT - 0.8f, yBottom + 1.1f, z, 0.0042f, color);
        if (night) {
            String nv = "NV";
            float w = font.width(nv) * 0.0050f * 16f;
            text(pose, buffer, font, nv, X_RIGHT + 0.8f + w, yBottom + 1.2f, z, 0.0050f, GREEN);
        }
    }

    /** Un rectángulo visto de frente; {@code uv} en el orden (u0, v0) arriba a la izquierda y (u1, v1) abajo a la derecha. */
    private static void quad(VertexConsumer vc, Matrix4f m, float yTop, float yBottom, float z, float u0, float v0, float u1, float v1, int r, int g, int b) {
        float zz = z / 16f;
        float xl = X_LEFT / 16f;
        float xr = X_RIGHT / 16f;
        vc.vertex(m, xl, yTop / 16f, zz).color(r, g, b, 255).uv(u0, v0).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xl, yBottom / 16f, zz).color(r, g, b, 255).uv(u0, v1).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xr, yBottom / 16f, zz).color(r, g, b, 255).uv(u1, v1).uv2(FULL_BRIGHT).endVertex();
        vc.vertex(m, xr, yTop / 16f, zz).color(r, g, b, 255).uv(u1, v0).uv2(FULL_BRIGHT).endVertex();
    }

    /**
     * Texto sobre el monitor. {@code x16} es el borde izquierdo del texto visto de frente (x mayor), {@code y16} su parte de arriba y
     * {@code scale} cuánto mide una unidad de la letra en bloques.
     */
    private static void text(PoseStack pose, MultiBufferSource buffer, Font font, String s, float x16, float y16, float z16, float scale, int color) {
        pose.pushPose();
        try {
            pose.translate(x16 / 16f, y16 / 16f, z16 / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.scale(scale, -scale, scale);
            font.drawInBatch(s, 0f, 0f, color, false, pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        } finally {
            pose.popPose();
        }
    }

    private static void centered(PoseStack pose, MultiBufferSource buffer, Font font, String s, float y16, float z16, float scale, int color) {
        float width = font.width(s) * scale * 16f;
        text(pose, buffer, font, s, width / 2f, y16, z16, scale, color);
    }

    @Override
    public boolean shouldRenderOffScreen(SecurityMonitorBlockEntity monitor) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
