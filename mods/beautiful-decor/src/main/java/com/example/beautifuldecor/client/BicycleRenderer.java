package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.BicycleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja la bicicleta (V2.1.0): se inclina en las curvas y se apoya en su pata cuando está parada. */
public class BicycleRenderer extends EntityRenderer<BicycleEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle.png");
    /** V6.14.0: texturas de las skins (colores fijos): 0 = la repintable, 1 = montaña, 2 = competición, 3 = reparto, 4 = policía. */
    private static final ResourceLocation[] SKINS = {
            null,
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle_mountain.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle_racing.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle_delivery.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle_police.png")};
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/bicycle_glow.png");
    private final BicycleModel model;

    public BicycleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BicycleModel(context.bakeLayer(BicycleModel.LAYER));
        this.shadowRadius = 0.45f;
    }

    @Override
    public void render(BicycleEntity bike, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // V3.2.0: si el jugador lo repintó con la tecla R, se dibuja con la textura repintada.
        // V6.14.0: con una skin puesta se usa su textura (colores fijos); sin skin, la repintada de siempre.
        int skin = bike.getSkin();
        ResourceLocation texture = skin > 0 && skin < SKINS.length ? SKINS[skin] : VehiclePaintTextures.textureFor(bike, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float airPitch = bike.getAirPitch(partialTick);                     // V7.4.0: el morro sigue la parábola en el aire
        if (Math.abs(airPitch) > 0.05f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(airPitch));
        }
        float lean = bike.getLean(partialTick);
        if (lean != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(bike.getWheelRot(partialTick), bike.getSteer(partialTick), bike.isParked());
        this.model.setSkin(skin);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (bike.isLightOn()) {
            // Faro y luz trasera a pleno brillo.
            this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        poseStack.popPose();
        super.render(bike, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BicycleEntity bike) {
        int skin = bike.getSkin();
        return skin > 0 && skin < SKINS.length ? SKINS[skin] : VehiclePaintTextures.textureFor(bike, TEXTURE);
    }
}
