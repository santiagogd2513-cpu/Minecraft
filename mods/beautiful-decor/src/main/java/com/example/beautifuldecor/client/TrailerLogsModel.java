package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.TrailerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Remolque de la tractomula: maderero (troncos) (V7.9.0). ARCHIVO GENERADO por tools/v790/trailers.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 512x288. El origen es el perno de enganche y el remolque se extiende hacia atrás. Animado: las 4 ruedas y las patas de apoyo.
 */
public class TrailerLogsModel extends EntityModel<TrailerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "trailer_logs"), "main");

    private final ModelPart root;
    private final ModelPart legs;
    private final ModelPart[] wheels;

    public TrailerLogsModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.legs = body.getChild("legs");
        this.wheels = new ModelPart[] {
                body.getChild("wheel_0l"), body.getChild("wheel_0r"), body.getChild("wheel_1l"), body.getChild("wheel_1r")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(164, 232).addBox(-3f, -17f, -3f, 6f, 3f, 6f)
                .texOffs(84, 198).addBox(-16f, -17f, -4f, 32f, 2f, 28f)
                .texOffs(0, 0).addBox(-12f, -17f, 28f, 24f, 2f, 72f)
                .texOffs(192, 0).addBox(-12f, -17f, 100f, 24f, 2f, 72f)
                .texOffs(0, 254).addBox(-18f, -9f, 151f, 36f, 2f, 2f)
                .texOffs(76, 254).addBox(-18f, -9f, 167f, 36f, 2f, 2f)
                .texOffs(368, 136).addBox(14f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(0, 198).addBox(-24f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(330, 232).addBox(12f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(352, 232).addBox(-22f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(374, 232).addBox(14f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(388, 232).addBox(-20f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(188, 232).addBox(-20f, -15f, 183f, 40f, 5f, 3f)
                .texOffs(0, 74).addBox(-20f, -19f, 124f, 40f, 2f, 60f)
                .texOffs(200, 74).addBox(-20f, -19f, 64f, 40f, 2f, 60f)
                .texOffs(0, 136).addBox(-20f, -19f, 4f, 40f, 2f, 60f)
                .texOffs(200, 136).addBox(-20f, -60f, 4f, 40f, 41f, 4f)
                .texOffs(288, 136).addBox(20f, -56f, 21f, 2f, 37f, 3f)
                .texOffs(298, 136).addBox(-22f, -56f, 21f, 2f, 37f, 3f)
                .texOffs(308, 136).addBox(20f, -56f, 69f, 2f, 37f, 3f)
                .texOffs(318, 136).addBox(-22f, -56f, 69f, 2f, 37f, 3f)
                .texOffs(328, 136).addBox(20f, -56f, 117f, 2f, 37f, 3f)
                .texOffs(338, 136).addBox(-22f, -56f, 117f, 2f, 37f, 3f)
                .texOffs(348, 136).addBox(20f, -56f, 165f, 2f, 37f, 3f)
                .texOffs(358, 136).addBox(-22f, -56f, 165f, 2f, 37f, 3f)
                .texOffs(152, 254).addBox(18f, -22f, 183f, 2f, 3f, 1f)
                .texOffs(158, 254).addBox(-20f, -22f, 183f, 2f, 3f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_0l = p_body.addOrReplaceChild("wheel_0l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0l_t0 = p_wheel_0l.addOrReplaceChild("wheel_0l_t0",
                CubeListBuilder.create()
                .texOffs(204, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0l_t1 = p_wheel_0l.addOrReplaceChild("wheel_0l_t1",
                CubeListBuilder.create()
                .texOffs(232, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0l_t2 = p_wheel_0l.addOrReplaceChild("wheel_0l_t2",
                CubeListBuilder.create()
                .texOffs(260, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0l_t3 = p_wheel_0l.addOrReplaceChild("wheel_0l_t3",
                CubeListBuilder.create()
                .texOffs(288, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_0r = p_body.addOrReplaceChild("wheel_0r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0r_t0 = p_wheel_0r.addOrReplaceChild("wheel_0r_t0",
                CubeListBuilder.create()
                .texOffs(316, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0r_t1 = p_wheel_0r.addOrReplaceChild("wheel_0r_t1",
                CubeListBuilder.create()
                .texOffs(344, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0r_t2 = p_wheel_0r.addOrReplaceChild("wheel_0r_t2",
                CubeListBuilder.create()
                .texOffs(372, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0r_t3 = p_wheel_0r.addOrReplaceChild("wheel_0r_t3",
                CubeListBuilder.create()
                .texOffs(400, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1l = p_body.addOrReplaceChild("wheel_1l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1l_t0 = p_wheel_1l.addOrReplaceChild("wheel_1l_t0",
                CubeListBuilder.create()
                .texOffs(428, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1l_t1 = p_wheel_1l.addOrReplaceChild("wheel_1l_t1",
                CubeListBuilder.create()
                .texOffs(456, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1l_t2 = p_wheel_1l.addOrReplaceChild("wheel_1l_t2",
                CubeListBuilder.create()
                .texOffs(484, 198).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1l_t3 = p_wheel_1l.addOrReplaceChild("wheel_1l_t3",
                CubeListBuilder.create()
                .texOffs(0, 232).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1r = p_body.addOrReplaceChild("wheel_1r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1r_t0 = p_wheel_1r.addOrReplaceChild("wheel_1r_t0",
                CubeListBuilder.create()
                .texOffs(28, 232).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1r_t1 = p_wheel_1r.addOrReplaceChild("wheel_1r_t1",
                CubeListBuilder.create()
                .texOffs(56, 232).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1r_t2 = p_wheel_1r.addOrReplaceChild("wheel_1r_t2",
                CubeListBuilder.create()
                .texOffs(84, 232).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1r_t3 = p_wheel_1r.addOrReplaceChild("wheel_1r_t3",
                CubeListBuilder.create()
                .texOffs(112, 232).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_legs = p_body.addOrReplaceChild("legs",
                CubeListBuilder.create()
                .texOffs(140, 232).addBox(12f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(152, 232).addBox(-15f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(274, 232).addBox(10f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(302, 232).addBox(-17f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(402, 232).addBox(-12f, -11f, 37f, 24f, 2f, 3f),
                PartPose.offset(0f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 288);
    }

    /** wheelRot en radianes (las 4 ruedas); legsDown = patas de apoyo abajo (sin tractor). */
    public void pose(float wheelRot, boolean legsDown) {
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = wheelRot;
        }
        this.legs.visible = legsDown;
    }

    @Override
    public void setupAnim(TrailerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...).
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
