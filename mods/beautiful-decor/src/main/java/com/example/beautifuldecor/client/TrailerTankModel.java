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
 * Remolque de la tractomula: cisterna (líquidos) (V7.9.0). ARCHIVO GENERADO por tools/v790/trailers.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 512x576. El origen es el perno de enganche y el remolque se extiende hacia atrás. Animado: las 4 ruedas y las patas de apoyo.
 */
public class TrailerTankModel extends EntityModel<TrailerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "trailer_tank"), "main");

    private final ModelPart root;
    private final ModelPart legs;
    private final ModelPart[] wheels;

    public TrailerTankModel(ModelPart root) {
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
                .texOffs(280, 534).addBox(-3f, -17f, -3f, 6f, 3f, 6f)
                .texOffs(84, 500).addBox(-16f, -17f, -4f, 32f, 2f, 28f)
                .texOffs(0, 0).addBox(-12f, -17f, 28f, 24f, 2f, 72f)
                .texOffs(192, 0).addBox(-12f, -17f, 100f, 24f, 2f, 72f)
                .texOffs(68, 556).addBox(-18f, -9f, 151f, 36f, 2f, 2f)
                .texOffs(144, 556).addBox(-18f, -9f, 167f, 36f, 2f, 2f)
                .texOffs(384, 429).addBox(14f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(0, 500).addBox(-24f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(446, 534).addBox(12f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(468, 534).addBox(-22f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(490, 534).addBox(14f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(0, 556).addBox(-20f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(304, 534).addBox(-20f, -15f, 183f, 40f, 5f, 3f)
                .texOffs(290, 556).addBox(-3f, -56f, 174f, 6f, 1f, 2f)
                .texOffs(220, 556).addBox(-3f, -56f, 118f, 6f, 1f, 3f)
                .texOffs(238, 556).addBox(-3f, -56f, 62f, 6f, 1f, 3f)
                .texOffs(256, 556).addBox(-3f, -56f, 6f, 6f, 1f, 3f)
                .texOffs(208, 534).addBox(-5f, -58f, 90f, 10f, 3f, 10f)
                .texOffs(248, 534).addBox(-4f, -59f, 91f, 8f, 1f, 8f)
                .texOffs(164, 534).addBox(-8f, -27f, 184f, 16f, 10f, 6f)
                .texOffs(368, 429).addBox(19f, -50f, 148f, 2f, 33f, 2f)
                .texOffs(376, 429).addBox(-21f, -50f, 148f, 2f, 33f, 2f)
                .texOffs(274, 556).addBox(18f, -38f, 181f, 2f, 2f, 2f)
                .texOffs(282, 556).addBox(-20f, -38f, 181f, 2f, 2f, 2f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_0l = p_body.addOrReplaceChild("wheel_0l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0l_t0 = p_wheel_0l.addOrReplaceChild("wheel_0l_t0",
                CubeListBuilder.create()
                .texOffs(204, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0l_t1 = p_wheel_0l.addOrReplaceChild("wheel_0l_t1",
                CubeListBuilder.create()
                .texOffs(232, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0l_t2 = p_wheel_0l.addOrReplaceChild("wheel_0l_t2",
                CubeListBuilder.create()
                .texOffs(260, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0l_t3 = p_wheel_0l.addOrReplaceChild("wheel_0l_t3",
                CubeListBuilder.create()
                .texOffs(288, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_0r = p_body.addOrReplaceChild("wheel_0r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0r_t0 = p_wheel_0r.addOrReplaceChild("wheel_0r_t0",
                CubeListBuilder.create()
                .texOffs(316, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0r_t1 = p_wheel_0r.addOrReplaceChild("wheel_0r_t1",
                CubeListBuilder.create()
                .texOffs(344, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0r_t2 = p_wheel_0r.addOrReplaceChild("wheel_0r_t2",
                CubeListBuilder.create()
                .texOffs(372, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0r_t3 = p_wheel_0r.addOrReplaceChild("wheel_0r_t3",
                CubeListBuilder.create()
                .texOffs(400, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1l = p_body.addOrReplaceChild("wheel_1l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1l_t0 = p_wheel_1l.addOrReplaceChild("wheel_1l_t0",
                CubeListBuilder.create()
                .texOffs(428, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1l_t1 = p_wheel_1l.addOrReplaceChild("wheel_1l_t1",
                CubeListBuilder.create()
                .texOffs(456, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1l_t2 = p_wheel_1l.addOrReplaceChild("wheel_1l_t2",
                CubeListBuilder.create()
                .texOffs(484, 500).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1l_t3 = p_wheel_1l.addOrReplaceChild("wheel_1l_t3",
                CubeListBuilder.create()
                .texOffs(0, 534).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1r = p_body.addOrReplaceChild("wheel_1r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1r_t0 = p_wheel_1r.addOrReplaceChild("wheel_1r_t0",
                CubeListBuilder.create()
                .texOffs(28, 534).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1r_t1 = p_wheel_1r.addOrReplaceChild("wheel_1r_t1",
                CubeListBuilder.create()
                .texOffs(56, 534).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1r_t2 = p_wheel_1r.addOrReplaceChild("wheel_1r_t2",
                CubeListBuilder.create()
                .texOffs(84, 534).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1r_t3 = p_wheel_1r.addOrReplaceChild("wheel_1r_t3",
                CubeListBuilder.create()
                .texOffs(112, 534).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_legs = p_body.addOrReplaceChild("legs",
                CubeListBuilder.create()
                .texOffs(140, 534).addBox(12f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(152, 534).addBox(-15f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(390, 534).addBox(10f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(418, 534).addBox(-17f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(14, 556).addBox(-12f, -11f, 37f, 24f, 2f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_tank_0_0 = p_body.addOrReplaceChild("tank_0_0",
                CubeListBuilder.create()
                .texOffs(0, 74).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offset(0f, -36f, 148f));
        PartDefinition p_tank_0_1 = p_body.addOrReplaceChild("tank_0_1",
                CubeListBuilder.create()
                .texOffs(184, 74).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 148f, 0f, 0f, 0.785f));
        PartDefinition p_tank_0_2 = p_body.addOrReplaceChild("tank_0_2",
                CubeListBuilder.create()
                .texOffs(0, 145).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 148f, 0f, 0f, 1.571f));
        PartDefinition p_tank_0_3 = p_body.addOrReplaceChild("tank_0_3",
                CubeListBuilder.create()
                .texOffs(184, 145).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 148f, 0f, 0f, 2.356f));
        PartDefinition p_tank_1_0 = p_body.addOrReplaceChild("tank_1_0",
                CubeListBuilder.create()
                .texOffs(0, 216).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offset(0f, -36f, 92f));
        PartDefinition p_tank_1_1 = p_body.addOrReplaceChild("tank_1_1",
                CubeListBuilder.create()
                .texOffs(184, 216).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 92f, 0f, 0f, 0.785f));
        PartDefinition p_tank_1_2 = p_body.addOrReplaceChild("tank_1_2",
                CubeListBuilder.create()
                .texOffs(0, 287).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 92f, 0f, 0f, 1.571f));
        PartDefinition p_tank_1_3 = p_body.addOrReplaceChild("tank_1_3",
                CubeListBuilder.create()
                .texOffs(184, 287).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 92f, 0f, 0f, 2.356f));
        PartDefinition p_tank_2_0 = p_body.addOrReplaceChild("tank_2_0",
                CubeListBuilder.create()
                .texOffs(0, 358).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offset(0f, -36f, 36f));
        PartDefinition p_tank_2_1 = p_body.addOrReplaceChild("tank_2_1",
                CubeListBuilder.create()
                .texOffs(184, 358).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 36f, 0f, 0f, 0.785f));
        PartDefinition p_tank_2_2 = p_body.addOrReplaceChild("tank_2_2",
                CubeListBuilder.create()
                .texOffs(0, 429).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 36f, 0f, 0f, 1.571f));
        PartDefinition p_tank_2_3 = p_body.addOrReplaceChild("tank_2_3",
                CubeListBuilder.create()
                .texOffs(184, 429).addBox(-18f, -7.5f, -28f, 36f, 15f, 56f),
                PartPose.offsetAndRotation(0f, -36f, 36f, 0f, 0f, 2.356f));
        return LayerDefinition.create(mesh, 512, 576);
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
