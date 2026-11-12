package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.PickupEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja la camioneta Furd F-150 (V2.1.0): ruedas que giran, ruedas delanteras que doblan, faros que brillan. */
public class PickupRenderer extends EntityRenderer<PickupEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150_glow.png");
    private final PickupModel model;

    public PickupRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PickupModel(context.bakeLayer(PickupModel.LAYER));
        this.shadowRadius = 1.3f;
    }

    @Override
    public void render(PickupEntity truck, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V3.2.0: si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        ResourceLocation texture = VehiclePaintTextures.textureFor(truck, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float roll = truck.getLean(partialTick);
        if (roll != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(truck.getWheelRot(partialTick), truck.getSteer(partialTick));
        // Pasada 1: carrocería e interior, opacos. Los vidrios van aparte, al final, para mezclarse con lo que hay detrás.
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (truck.isLightOn()) {
            // Faros y luces traseras a pleno brillo, de día y de noche.
            this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // Pasada 2: vidrios ahumados (translúcidos).
        this.model.showGlass(true);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, VehicleGlass.ALPHA);
        poseStack.popPose();
        super.render(truck, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PickupEntity truck) {
        return VehiclePaintTextures.textureFor(truck, TEXTURE);
    }
}
