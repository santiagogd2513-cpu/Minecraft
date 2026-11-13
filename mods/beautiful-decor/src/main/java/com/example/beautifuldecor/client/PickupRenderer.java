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
    private static final ResourceLocation[] SKINS = {
            null,
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150_police.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150_delivery.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150_construction.png"),
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/ford_f150_fire.png")};
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
        // V6.8.0: con una skin puesta se usa su textura (colores fijos); sin skin, la repintada de siempre.
        int skin = truck.getSkin();
        ResourceLocation texture = skin > 0 && skin < SKINS.length ? SKINS[skin] : VehiclePaintTextures.textureFor(truck, TEXTURE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float roll = truck.getLean(partialTick);
        if (roll != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);

        this.model.pose(truck.getWheelRot(partialTick), truck.getSteer(partialTick));
        this.model.setSkin(skin);
        // Pasada 1: carrocería e interior, opacos. Los vidrios van aparte, al final, para mezclarse con lo que hay detrás.
        this.model.showGlass(false);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        if (truck.isLightOn() || truck.isSirenOn()) {
            // Faros y luces traseras a pleno brillo, de día y de noche. Con la sirena, la barra alterna rojo y azul.
            this.model.setBarPhase(truck.isSirenOn() ? (truck.tickCount / 5) % 2 : -1);
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
        int skin = truck.getSkin();
        return skin > 0 && skin < SKINS.length ? SKINS[skin] : VehiclePaintTextures.textureFor(truck, TEXTURE);
    }
}
