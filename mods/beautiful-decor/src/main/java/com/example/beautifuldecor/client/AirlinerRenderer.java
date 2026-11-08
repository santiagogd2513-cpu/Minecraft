package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Dibuja el avión BOING 7G7 Gigante (V3.8.0): modelo a escala 1:4 agrandado {@link AirlinerModel#SCALE} veces, turbinas y ruedas que
 * giran, tren que se pliega y cabeceo alrededor del punto bajo las ruedas principales (el mismo que usan los asientos).
 */
public class AirlinerRenderer extends EntityRenderer<AirlinerEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/airliner.png");
    private final AirlinerModel model;

    public AirlinerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AirlinerModel(context.bakeLayer(AirlinerModel.LAYER));
        this.shadowRadius = 4.0f;
    }

    @Override
    public void render(AirlinerEntity airliner, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // Si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        ResourceLocation texture = VehiclePaintTextures.textureFor(airliner, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float pitch = airliner.getPitchVisual(partialTick);
        if (pitch != 0.0f) {
            // En este sistema el frente es -z: el punto de giro está PIVOT_BACK bloques detrás del centro, es decir en +z.
            poseStack.translate(0.0, 0.0, AirlinerEntity.PIVOT_BACK);
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));     // positivo = morro hacia abajo
            poseStack.translate(0.0, 0.0, -AirlinerEntity.PIVOT_BACK);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.scale(AirlinerModel.SCALE, AirlinerModel.SCALE, AirlinerModel.SCALE);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(airliner.getFanAngle(partialTick), airliner.getWheelAngle(partialTick),
                airliner.getGearPosition(partialTick));
        // Pasada 1: fuselaje, interior, turbinas y tren, opacos. Los vidrios van aparte, al final, para mezclarse con lo que hay detrás.
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        // Pasada 2: vidrios (translúcidos).
        this.model.showGlass(true);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(airliner, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AirlinerEntity airliner) {
        return VehiclePaintTextures.textureFor(airliner, TEXTURE);
    }
}
