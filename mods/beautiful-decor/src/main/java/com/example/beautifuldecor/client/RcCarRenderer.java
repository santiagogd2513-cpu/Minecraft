package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el carro RC Lamborgonzo Huracanito (V3.1.0): las ruedas giran y las delanteras doblan al girar. */
public class RcCarRenderer extends EntityRenderer<RcCarEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/rc_car.png");
    private final RcCarModel model;

    public RcCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RcCarModel(context.bakeLayer(RcCarModel.LAYER));
        this.shadowRadius = 0.35f;
    }

    @Override
    public void render(RcCarEntity car, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // El juego ya oculta la entidad de la cámara en primera persona; en tercera persona (F5) sí se ve.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        // El modelo está hecho a 1/32 de bloque por unidad: se baja el suelo (1,501 bloques x 0,5) y se escala a la mitad.
        poseStack.translate(0.0, -0.7505, 0.0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        this.model.pose(car.getWheelRot(partialTick), car.getSteer(partialTick));
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(car, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RcCarEntity car) {
        return TEXTURE;
    }
}
