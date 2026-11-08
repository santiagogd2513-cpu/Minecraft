package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.MotorcycleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class MotorcycleRenderer extends EntityRenderer<MotorcycleEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/motorcycle.png");
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/motorcycle_glow.png");
    private final MotorcycleModel model;

    public MotorcycleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new MotorcycleModel(context.bakeLayer(MotorcycleModel.LAYER));
        this.shadowRadius = 0.6f;
    }

    @Override
    public void render(MotorcycleEntity moto, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V3.2.0: si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        ResourceLocation texture = VehiclePaintTextures.textureFor(moto, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        // Inclinación en las curvas (o apoyada en la pata de cabra si está parada), alrededor de la línea del suelo.
        float lean = moto.getLean(partialTick);
        if (lean != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(moto.getWheelRot(partialTick), moto.getSteer(partialTick), moto.isParked());
        // Con transparencia: el parabrisas es un cristal ahumado.
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (moto.isLightOn()) {
            // Faro y luz trasera: se dibujan encima a pleno brillo, de día y de noche.
            this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        poseStack.popPose();
        super.render(moto, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MotorcycleEntity moto) {
        return VehiclePaintTextures.textureFor(moto, TEXTURE);
    }
}
