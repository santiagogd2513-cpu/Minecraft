package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SemiTruckEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja la tractomula KENWORT K880 (V7.9.0): ruedas que giran, delanteras que doblan, volante, faros que brillan. Se puede repintar (tecla R). */
public class SemiTruckRenderer extends EntityRenderer<SemiTruckEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/semi_truck.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/semi_truck_glow.png");
    private final SemiTruckModel model;

    public SemiTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SemiTruckModel(context.bakeLayer(SemiTruckModel.LAYER));
        this.shadowRadius = 1.8f;
    }

    @Override
    public void render(SemiTruckEntity truck, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        ResourceLocation texture = VehiclePaintTextures.textureFor(truck, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float airPitch = truck.getAirPitch(partialTick);
        if (Math.abs(airPitch) > 0.05f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(airPitch));
        }
        float roll = truck.getLean(partialTick);
        if (roll != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(truck.getWheelRot(partialTick), truck.getSteer(partialTick));
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (truck.isLightOn()) {
            this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        this.model.showGlass(true);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, VehicleGlass.ALPHA);
        poseStack.popPose();
        super.render(truck, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SemiTruckEntity truck) {
        return VehiclePaintTextures.textureFor(truck, TEXTURE);
    }
}
