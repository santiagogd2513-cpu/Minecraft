package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SemiTruckEntity;
import com.example.beautifuldecor.entity.TrailerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dibuja los remolques de la tractomula (V7.9.0): el furgón, el de troncos (con la pila de troncos que lleva, a medio bloque cada uno) y la cisterna.
 * Se inclina para seguir la cuesta con su eje trasero; las patas de apoyo bajan cuando está suelto; las luces traseras se encienden con las del tractor.
 */
public class TrailerRenderer extends EntityRenderer<TrailerEntity> {
    private static final ResourceLocation[] TEXTURES = {
            tex("trailer_cargo"), tex("trailer_logs"), tex("trailer_tank"), tex("trailer_carrier")};
    private static final ResourceLocation[] GLOWS = {
            tex("trailer_cargo_glow"), tex("trailer_logs_glow"), tex("trailer_tank_glow"), tex("trailer_carrier_glow")};

    private final TrailerCargoModel cargo;
    private final TrailerLogsModel logs;
    private final TrailerTankModel tank;
    private final TrailerCarrierModel carrier;

    public TrailerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.cargo = new TrailerCargoModel(context.bakeLayer(TrailerCargoModel.LAYER));
        this.logs = new TrailerLogsModel(context.bakeLayer(TrailerLogsModel.LAYER));
        this.tank = new TrailerTankModel(context.bakeLayer(TrailerTankModel.LAYER));
        this.carrier = new TrailerCarrierModel(context.bakeLayer(TrailerCarrierModel.LAYER));
        this.shadowRadius = 0.0f;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public boolean shouldRender(TrailerEntity trailer, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;                                                       // es muy largo: la caja del origen no sirve para decidir
    }

    @Override
    public void render(TrailerEntity trailer, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        int kind = Math.max(0, Math.min(3, trailer.getKind()));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        // cola arriba = ángulo negativo en este marco (el remolque se extiende hacia +z)
        float pitch = trailer.getPitch(partialTick);
        if (Math.abs(pitch) > 0.05f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }

        if (kind == TrailerEntity.LOGS) {
            this.renderLogs(trailer, poseStack, bufferSource, packedLight);
        }

        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0, -1.501, 0.0);
        float wheel = trailer.getWheelRot(partialTick);
        boolean legs = !trailer.isCoupled();
        EntityModel<TrailerEntity> model;
        switch (kind) {
            case TrailerEntity.LOGS -> {
                this.logs.pose(wheel, legs);
                model = this.logs;
            }
            case TrailerEntity.TANK -> {
                this.tank.pose(wheel, legs);
                model = this.tank;
            }
            case TrailerEntity.CARRIER -> {
                this.carrier.pose(wheel, legs, trailer.getRampLow(partialTick), trailer.getRampHigh(partialTick));
                model = this.carrier;
            }
            default -> {
                this.cargo.pose(wheel, legs);
                model = this.cargo;
            }
        }
        model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[kind])),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        SemiTruckEntity tractor = trailer.getTractor();
        if (tractor != null && tractor.isLightOn()) {
            model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.eyes(GLOWS[kind])),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        poseStack.popPose();
        poseStack.popPose();
        super.render(trailer, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** La pila: 5 de ancho x 22 de largo x 4 de alto, cada tronco a medio bloque, apoyada en la cubierta (marco: +z = hacia atrás). */
    private void renderLogs(TrailerEntity trailer, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BlockState[] pile = trailer.getPileStates();
        if (pile.length == 0) {
            return;
        }
        var renderer = Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < pile.length; i++) {
            int col = i % 5;
            int row = (i / 5) % 22;
            int layer = i / 110;
            poseStack.pushPose();
            poseStack.translate(-1.25 + col * 0.5, 1.1875 + layer * 0.5, 0.5 + row * 0.5);
            poseStack.scale(0.5f, 0.5f, 0.5f);
            renderer.renderSingleBlock(pile[i], poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TrailerEntity trailer) {
        return TEXTURES[Math.max(0, Math.min(3, trailer.getKind()))];
    }
}
