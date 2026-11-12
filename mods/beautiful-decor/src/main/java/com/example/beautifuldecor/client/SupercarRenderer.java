package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SupercarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el superdeportivo Bugotti Chillón (V3.7.0): ruedas que giran, ruedas delanteras que doblan, faros y luz trasera que brillan. */
public class SupercarRenderer extends EntityRenderer<SupercarEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bugotti_chillon.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bugotti_chillon_glow.png");
    private final SupercarModel model;

    public SupercarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SupercarModel(context.bakeLayer(SupercarModel.LAYER));
        this.shadowRadius = 1.4f;
    }

    @Override
    public void render(SupercarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V3.2.0: si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        ResourceLocation texture = VehiclePaintTextures.textureFor(car, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float roll = car.getLean(partialTick);
        if (roll != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(car.getWheelRot(partialTick), car.getSteer(partialTick));
        // Pasada 1: carrocería e interior, opacos. Los vidrios van aparte, al final, para mezclarse con lo que hay detrás.
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (car.isLightOn()) {
            // Faros y luces traseras a pleno brillo, de día y de noche.
            this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // Pasada 2: vidrios ahumados (translúcidos).
        this.model.showGlass(true);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, VehicleGlass.ALPHA);
        poseStack.popPose();
        super.render(car, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SupercarEntity car) {
        return VehiclePaintTextures.textureFor(car, TEXTURE);
    }
}
