package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.SecurityCameraBlock;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * V4.3.0: dibuja la cabeza de la cámara de seguridad encima de su soporte, girando hacia donde mira en cada momento (barrido lento si
 * tiene la rotación activada) e inclinada hacia abajo.
 */
public class SecurityCameraRenderer implements BlockEntityRenderer<SecurityCameraBlockEntity> {
    private static final ResourceLocation BLACK = new ResourceLocation(BeautifulDecor.MOD_ID, "textures/block/security_camera_head_black.png");
    private static final ResourceLocation WHITE = new ResourceLocation(BeautifulDecor.MOD_ID, "textures/block/security_camera_head_white.png");

    private final ModelPart head;

    public SecurityCameraRenderer(BlockEntityRendererProvider.Context context) {
        this.head = SecurityCameraModel.head(context.bakeLayer(SecurityCameraModel.LAYER));
    }

    @Override
    public void render(SecurityCameraBlockEntity camera, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        try {
            draw(camera, partialTick, pose, buffer, light);
        } catch (Throwable t) {
            // un fallo de dibujo no puede cerrar el juego: la cámara queda sin cabeza
        }
    }

    private void draw(SecurityCameraBlockEntity camera, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        if (camera.getLevel() == null || !(camera.getBlockState().getBlock() instanceof SecurityCameraBlock block)) {
            return;
        }
        float yaw = camera.currentYaw(camera.getLevel(), partialTick);
        Vec3 pivot = camera.pivot();
        double px = pivot.x - camera.getBlockPos().getX();
        double py = pivot.y - camera.getBlockPos().getY();
        double pz = pivot.z - camera.getBlockPos().getZ();
        this.head.xRot = (float) Math.toRadians(camera.pitch());
        pose.pushPose();
        try {
            pose.translate(px, py, pz);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
            pose.scale(-1.0f, -1.0f, 1.0f);
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(block.isWhite() ? WHITE : BLACK));
            this.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
