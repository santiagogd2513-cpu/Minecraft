package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class DroneRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/drone.png");
    private final DroneModel model;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DroneModel(context.bakeLayer(DroneModel.LAYER));
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(DroneEntity drone, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V1.8.5: antes no se dibujaba nunca que la cámara fuera el dron, y por eso "no aparecía". El juego ya
        // oculta la entidad de la cámara en primera persona; en tercera persona (F5) sí se ve.
        poseStack.pushPose();
        // Vanilla entity-model setup: face the entity's heading, flip to y-down model space, put feet on the ground.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);
        this.model.setupAnim(drone, 0.0f, 0.0f, drone.tickCount + partialTick,
                entityYaw, drone.getXRot());
        this.model.renderToBuffer(poseStack,
                bufferSource.getBuffer(RenderType.entityCutout(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(drone, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity drone) {
        return TEXTURE;
    }
}
