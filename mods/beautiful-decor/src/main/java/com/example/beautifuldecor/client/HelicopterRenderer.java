package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.HelicopterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el helicóptero BOING HX-6 (V3.0.0): rotores que giran, morro que baja al avanzar, escora al girar. */
public class HelicopterRenderer extends EntityRenderer<HelicopterEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/helicopter.png");
    /** Altura (en bloques) del centro de giro del helicóptero al inclinarse. */
    private static final double TILT_CENTER = 1.3;
    private final HelicopterModel model;

    public HelicopterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HelicopterModel(context.bakeLayer(HelicopterModel.LAYER));
        this.shadowRadius = 1.6f;
    }

    @Override
    public void render(HelicopterEntity helicopter, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V3.2.0: si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        ResourceLocation texture = VehiclePaintTextures.textureFor(helicopter, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float pitch = helicopter.getPitchVisual(partialTick);
        float roll = helicopter.getRollVisual(partialTick);
        if (pitch != 0.0f || roll != 0.0f) {
            poseStack.translate(0.0, TILT_CENTER, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));     // positivo = morro hacia abajo
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));       // positivo = se inclina hacia su izquierda
            poseStack.translate(0.0, -TILT_CENTER, 0.0);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(helicopter.getRotorAngle(partialTick), helicopter.getTailAngle(partialTick));
        // Pasada 1: carrocería, interior y rotores, opacos. Los vidrios van aparte, al final, para mezclarse con lo que hay detrás.
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        // Pasada 2: vidrios (translúcidos).
        this.model.showGlass(true);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(helicopter, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HelicopterEntity helicopter) {
        return VehiclePaintTextures.textureFor(helicopter, TEXTURE);
    }
}
