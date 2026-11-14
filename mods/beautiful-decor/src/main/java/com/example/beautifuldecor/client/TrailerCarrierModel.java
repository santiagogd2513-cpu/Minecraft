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
 * Remolque de la tractomula: niñera de dos pisos con rampas (V7.9.0). ARCHIVO GENERADO por tools/v790/trailers.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 512x960. El origen es el perno de enganche y el remolque se extiende hacia atrás. Animado: las 4 ruedas, las patas de apoyo y las dos rampas.
 */
public class TrailerCarrierModel extends EntityModel<TrailerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "trailer_carrier"), "main");

    private final ModelPart root;
    private final ModelPart legs;
    private final ModelPart rampLow;
    private final ModelPart rampHigh;
    private final ModelPart[] wheels;

    public TrailerCarrierModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.legs = body.getChild("legs");
        this.rampLow = body.getChild("ramp_low");
        this.rampHigh = body.getChild("ramp_high");
        this.wheels = new ModelPart[] {
                body.getChild("wheel_0l"), body.getChild("wheel_0r"), body.getChild("wheel_1l"), body.getChild("wheel_1r")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(332, 894).addBox(-3f, -17f, -3f, 6f, 3f, 6f)
                .texOffs(88, 627).addBox(-16f, -17f, -4f, 32f, 2f, 28f)
                .texOffs(96, 0).addBox(-12f, -17f, 28f, 24f, 2f, 76f)
                .texOffs(296, 0).addBox(-12f, -17f, 104f, 24f, 2f, 76f)
                .texOffs(0, 84).addBox(-12f, -17f, 180f, 24f, 2f, 76f)
                .texOffs(132, 916).addBox(-12f, -17f, 256f, 24f, 2f, 4f)
                .texOffs(270, 916).addBox(-18f, -9f, 203f, 36f, 2f, 2f)
                .texOffs(346, 916).addBox(-18f, -9f, 219f, 36f, 2f, 2f)
                .texOffs(272, 593).addBox(14f, -19f, 196f, 10f, 2f, 32f)
                .texOffs(356, 593).addBox(-24f, -19f, 196f, 10f, 2f, 32f)
                .texOffs(88, 916).addBox(12f, -9f, 229f, 10f, 6f, 1f)
                .texOffs(110, 916).addBox(-22f, -9f, 229f, 10f, 6f, 1f)
                .texOffs(188, 916).addBox(14f, -21f, 259f, 6f, 5f, 1f)
                .texOffs(202, 916).addBox(-20f, -21f, 259f, 6f, 5f, 1f)
                .texOffs(356, 894).addBox(-20f, -15f, 259f, 40f, 5f, 3f)
                .texOffs(200, 84).addBox(-20f, -19f, 196f, 40f, 2f, 64f)
                .texOffs(0, 162).addBox(-20f, -60f, 196f, 40f, 2f, 64f)
                .texOffs(208, 162).addBox(-20f, -19f, 132f, 40f, 2f, 64f)
                .texOffs(0, 228).addBox(-20f, -60f, 132f, 40f, 2f, 64f)
                .texOffs(208, 228).addBox(-20f, -19f, 68f, 40f, 2f, 64f)
                .texOffs(0, 294).addBox(-20f, -60f, 68f, 40f, 2f, 64f)
                .texOffs(208, 294).addBox(-20f, -19f, 4f, 40f, 2f, 64f)
                .texOffs(0, 360).addBox(-20f, -60f, 4f, 40f, 2f, 64f)
                .texOffs(416, 739).addBox(20f, -22f, 237f, 2f, 3f, 23f)
                .texOffs(0, 765).addBox(-22f, -22f, 237f, 2f, 3f, 23f)
                .texOffs(54, 659).addBox(20f, -22f, 213f, 2f, 3f, 24f)
                .texOffs(106, 659).addBox(-22f, -22f, 213f, 2f, 3f, 24f)
                .texOffs(50, 765).addBox(20f, -22f, 190f, 2f, 3f, 23f)
                .texOffs(100, 765).addBox(-22f, -22f, 190f, 2f, 3f, 23f)
                .texOffs(150, 765).addBox(20f, -22f, 167f, 2f, 3f, 23f)
                .texOffs(200, 765).addBox(-22f, -22f, 167f, 2f, 3f, 23f)
                .texOffs(250, 765).addBox(20f, -22f, 144f, 2f, 3f, 23f)
                .texOffs(300, 765).addBox(-22f, -22f, 144f, 2f, 3f, 23f)
                .texOffs(158, 659).addBox(20f, -22f, 120f, 2f, 3f, 24f)
                .texOffs(210, 659).addBox(-22f, -22f, 120f, 2f, 3f, 24f)
                .texOffs(350, 765).addBox(20f, -22f, 97f, 2f, 3f, 23f)
                .texOffs(400, 765).addBox(-22f, -22f, 97f, 2f, 3f, 23f)
                .texOffs(450, 765).addBox(20f, -22f, 74f, 2f, 3f, 23f)
                .texOffs(0, 791).addBox(-22f, -22f, 74f, 2f, 3f, 23f)
                .texOffs(50, 791).addBox(20f, -22f, 51f, 2f, 3f, 23f)
                .texOffs(100, 791).addBox(-22f, -22f, 51f, 2f, 3f, 23f)
                .texOffs(262, 659).addBox(20f, -22f, 27f, 2f, 3f, 24f)
                .texOffs(314, 659).addBox(-22f, -22f, 27f, 2f, 3f, 24f)
                .texOffs(150, 791).addBox(20f, -22f, 4f, 2f, 3f, 23f)
                .texOffs(200, 791).addBox(-22f, -22f, 4f, 2f, 3f, 23f)
                .texOffs(250, 791).addBox(20f, -63f, 237f, 2f, 3f, 23f)
                .texOffs(300, 791).addBox(-22f, -63f, 237f, 2f, 3f, 23f)
                .texOffs(366, 659).addBox(20f, -63f, 213f, 2f, 3f, 24f)
                .texOffs(418, 659).addBox(-22f, -63f, 213f, 2f, 3f, 24f)
                .texOffs(350, 791).addBox(20f, -63f, 190f, 2f, 3f, 23f)
                .texOffs(400, 791).addBox(-22f, -63f, 190f, 2f, 3f, 23f)
                .texOffs(450, 791).addBox(20f, -63f, 167f, 2f, 3f, 23f)
                .texOffs(0, 817).addBox(-22f, -63f, 167f, 2f, 3f, 23f)
                .texOffs(50, 817).addBox(20f, -63f, 144f, 2f, 3f, 23f)
                .texOffs(100, 817).addBox(-22f, -63f, 144f, 2f, 3f, 23f)
                .texOffs(0, 686).addBox(20f, -63f, 120f, 2f, 3f, 24f)
                .texOffs(52, 686).addBox(-22f, -63f, 120f, 2f, 3f, 24f)
                .texOffs(150, 817).addBox(20f, -63f, 97f, 2f, 3f, 23f)
                .texOffs(200, 817).addBox(-22f, -63f, 97f, 2f, 3f, 23f)
                .texOffs(250, 817).addBox(20f, -63f, 74f, 2f, 3f, 23f)
                .texOffs(300, 817).addBox(-22f, -63f, 74f, 2f, 3f, 23f)
                .texOffs(350, 817).addBox(20f, -63f, 51f, 2f, 3f, 23f)
                .texOffs(400, 817).addBox(-22f, -63f, 51f, 2f, 3f, 23f)
                .texOffs(104, 686).addBox(20f, -63f, 27f, 2f, 3f, 24f)
                .texOffs(156, 686).addBox(-22f, -63f, 27f, 2f, 3f, 24f)
                .texOffs(450, 817).addBox(20f, -63f, 4f, 2f, 3f, 23f)
                .texOffs(0, 843).addBox(-22f, -63f, 4f, 2f, 3f, 23f)
                .texOffs(0, 0).addBox(20f, -100f, 5f, 3f, 81f, 3f)
                .texOffs(12, 0).addBox(-23f, -100f, 5f, 3f, 81f, 3f)
                .texOffs(24, 0).addBox(20f, -100f, 83f, 3f, 81f, 3f)
                .texOffs(36, 0).addBox(-23f, -100f, 83f, 3f, 81f, 3f)
                .texOffs(48, 0).addBox(20f, -100f, 161f, 3f, 81f, 3f)
                .texOffs(60, 0).addBox(-23f, -100f, 161f, 3f, 81f, 3f)
                .texOffs(72, 0).addBox(20f, -100f, 255f, 3f, 81f, 3f)
                .texOffs(84, 0).addBox(-23f, -100f, 255f, 3f, 81f, 3f)
                .texOffs(208, 686).addBox(20f, -101f, 237f, 3f, 3f, 23f)
                .texOffs(260, 686).addBox(-23f, -101f, 237f, 3f, 3f, 23f)
                .texOffs(208, 627).addBox(20f, -101f, 213f, 3f, 3f, 24f)
                .texOffs(262, 627).addBox(-23f, -101f, 213f, 3f, 3f, 24f)
                .texOffs(312, 686).addBox(20f, -101f, 190f, 3f, 3f, 23f)
                .texOffs(364, 686).addBox(-23f, -101f, 190f, 3f, 3f, 23f)
                .texOffs(416, 686).addBox(20f, -101f, 167f, 3f, 3f, 23f)
                .texOffs(0, 713).addBox(-23f, -101f, 167f, 3f, 3f, 23f)
                .texOffs(52, 713).addBox(20f, -101f, 144f, 3f, 3f, 23f)
                .texOffs(104, 713).addBox(-23f, -101f, 144f, 3f, 3f, 23f)
                .texOffs(316, 627).addBox(20f, -101f, 120f, 3f, 3f, 24f)
                .texOffs(370, 627).addBox(-23f, -101f, 120f, 3f, 3f, 24f)
                .texOffs(156, 713).addBox(20f, -101f, 97f, 3f, 3f, 23f)
                .texOffs(208, 713).addBox(-23f, -101f, 97f, 3f, 3f, 23f)
                .texOffs(260, 713).addBox(20f, -101f, 74f, 3f, 3f, 23f)
                .texOffs(312, 713).addBox(-23f, -101f, 74f, 3f, 3f, 23f)
                .texOffs(364, 713).addBox(20f, -101f, 51f, 3f, 3f, 23f)
                .texOffs(416, 713).addBox(-23f, -101f, 51f, 3f, 3f, 23f)
                .texOffs(424, 627).addBox(20f, -101f, 27f, 3f, 3f, 24f)
                .texOffs(0, 659).addBox(-23f, -101f, 27f, 3f, 3f, 24f)
                .texOffs(0, 739).addBox(20f, -101f, 4f, 3f, 3f, 23f)
                .texOffs(52, 739).addBox(-23f, -101f, 4f, 3f, 3f, 23f)
                .texOffs(0, 916).addBox(-20f, -101f, 4f, 40f, 3f, 4f)
                .texOffs(50, 843).addBox(20f, -58f, 237f, 2f, 2f, 23f)
                .texOffs(100, 843).addBox(-22f, -58f, 237f, 2f, 2f, 23f)
                .texOffs(104, 739).addBox(20f, -58f, 213f, 2f, 2f, 24f)
                .texOffs(156, 739).addBox(-22f, -58f, 213f, 2f, 2f, 24f)
                .texOffs(150, 843).addBox(20f, -58f, 190f, 2f, 2f, 23f)
                .texOffs(200, 843).addBox(-22f, -58f, 190f, 2f, 2f, 23f)
                .texOffs(250, 843).addBox(20f, -58f, 167f, 2f, 2f, 23f)
                .texOffs(300, 843).addBox(-22f, -58f, 167f, 2f, 2f, 23f)
                .texOffs(350, 843).addBox(20f, -58f, 144f, 2f, 2f, 23f)
                .texOffs(400, 843).addBox(-22f, -58f, 144f, 2f, 2f, 23f)
                .texOffs(208, 739).addBox(20f, -58f, 120f, 2f, 2f, 24f)
                .texOffs(260, 739).addBox(-22f, -58f, 120f, 2f, 2f, 24f)
                .texOffs(450, 843).addBox(20f, -58f, 97f, 2f, 2f, 23f)
                .texOffs(0, 869).addBox(-22f, -58f, 97f, 2f, 2f, 23f)
                .texOffs(50, 869).addBox(20f, -58f, 74f, 2f, 2f, 23f)
                .texOffs(100, 869).addBox(-22f, -58f, 74f, 2f, 2f, 23f)
                .texOffs(150, 869).addBox(20f, -58f, 51f, 2f, 2f, 23f)
                .texOffs(200, 869).addBox(-22f, -58f, 51f, 2f, 2f, 23f)
                .texOffs(312, 739).addBox(20f, -58f, 27f, 2f, 2f, 24f)
                .texOffs(364, 739).addBox(-22f, -58f, 27f, 2f, 2f, 24f)
                .texOffs(250, 869).addBox(20f, -58f, 4f, 2f, 2f, 23f)
                .texOffs(300, 869).addBox(-22f, -58f, 4f, 2f, 2f, 23f)
                .texOffs(0, 627).addBox(-20f, -88f, 4f, 40f, 28f, 4f)
                .texOffs(208, 360).addBox(21f, -17f, 182f, 1f, 7f, 48f)
                .texOffs(306, 360).addBox(-22f, -17f, 182f, 1f, 7f, 48f)
                .texOffs(98, 426).addBox(21f, -17f, 135f, 1f, 7f, 47f)
                .texOffs(194, 426).addBox(-22f, -17f, 135f, 1f, 7f, 47f)
                .texOffs(290, 426).addBox(21f, -17f, 88f, 1f, 7f, 47f)
                .texOffs(386, 426).addBox(-22f, -17f, 88f, 1f, 7f, 47f)
                .texOffs(404, 360).addBox(21f, -17f, 40f, 1f, 7f, 48f)
                .texOffs(0, 426).addBox(-22f, -17f, 40f, 1f, 7f, 48f)
                .texOffs(422, 916).addBox(18f, -100f, 258f, 2f, 2f, 2f)
                .texOffs(430, 916).addBox(-20f, -100f, 258f, 2f, 2f, 2f)
                .texOffs(438, 916).addBox(-2f, -101f, 4f, 4f, 2f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_0l = p_body.addOrReplaceChild("wheel_0l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 204f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0l_t0 = p_wheel_0l.addOrReplaceChild("wheel_0l_t0",
                CubeListBuilder.create()
                .texOffs(350, 869).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0l_t1 = p_wheel_0l.addOrReplaceChild("wheel_0l_t1",
                CubeListBuilder.create()
                .texOffs(378, 869).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0l_t2 = p_wheel_0l.addOrReplaceChild("wheel_0l_t2",
                CubeListBuilder.create()
                .texOffs(406, 869).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0l_t3 = p_wheel_0l.addOrReplaceChild("wheel_0l_t3",
                CubeListBuilder.create()
                .texOffs(434, 869).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_0r = p_body.addOrReplaceChild("wheel_0r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 204f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0r_t0 = p_wheel_0r.addOrReplaceChild("wheel_0r_t0",
                CubeListBuilder.create()
                .texOffs(462, 869).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0r_t1 = p_wheel_0r.addOrReplaceChild("wheel_0r_t1",
                CubeListBuilder.create()
                .texOffs(0, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0r_t2 = p_wheel_0r.addOrReplaceChild("wheel_0r_t2",
                CubeListBuilder.create()
                .texOffs(28, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0r_t3 = p_wheel_0r.addOrReplaceChild("wheel_0r_t3",
                CubeListBuilder.create()
                .texOffs(56, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1l = p_body.addOrReplaceChild("wheel_1l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 220f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1l_t0 = p_wheel_1l.addOrReplaceChild("wheel_1l_t0",
                CubeListBuilder.create()
                .texOffs(84, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1l_t1 = p_wheel_1l.addOrReplaceChild("wheel_1l_t1",
                CubeListBuilder.create()
                .texOffs(112, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1l_t2 = p_wheel_1l.addOrReplaceChild("wheel_1l_t2",
                CubeListBuilder.create()
                .texOffs(140, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1l_t3 = p_wheel_1l.addOrReplaceChild("wheel_1l_t3",
                CubeListBuilder.create()
                .texOffs(168, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1r = p_body.addOrReplaceChild("wheel_1r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 220f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1r_t0 = p_wheel_1r.addOrReplaceChild("wheel_1r_t0",
                CubeListBuilder.create()
                .texOffs(196, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1r_t1 = p_wheel_1r.addOrReplaceChild("wheel_1r_t1",
                CubeListBuilder.create()
                .texOffs(224, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1r_t2 = p_wheel_1r.addOrReplaceChild("wheel_1r_t2",
                CubeListBuilder.create()
                .texOffs(252, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1r_t3 = p_wheel_1r.addOrReplaceChild("wheel_1r_t3",
                CubeListBuilder.create()
                .texOffs(280, 894).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_legs = p_body.addOrReplaceChild("legs",
                CubeListBuilder.create()
                .texOffs(308, 894).addBox(12f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(320, 894).addBox(-15f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(442, 894).addBox(10f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(470, 894).addBox(-17f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(216, 916).addBox(-12f, -11f, 37f, 24f, 2f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ramp_low = p_body.addOrReplaceChild("ramp_low",
                CubeListBuilder.create()
                .texOffs(0, 593).addBox(-18f, 0f, 32f, 36f, 2f, 32f)
                .texOffs(136, 593).addBox(-18f, 0f, 0f, 36f, 2f, 32f)
                .texOffs(140, 557).addBox(18f, -2f, 32f, 2f, 4f, 32f)
                .texOffs(208, 557).addBox(-20f, -2f, 32f, 2f, 4f, 32f)
                .texOffs(276, 557).addBox(18f, -2f, 0f, 2f, 4f, 32f)
                .texOffs(344, 557).addBox(-20f, -2f, 0f, 2f, 4f, 32f),
                PartPose.offset(0f, -19f, 260f));
        PartDefinition p_ramp_high = p_body.addOrReplaceChild("ramp_high",
                CubeListBuilder.create()
                .texOffs(72, 519).addBox(-18f, 0f, 102f, 36f, 2f, 34f)
                .texOffs(212, 519).addBox(-18f, 0f, 68f, 36f, 2f, 34f)
                .texOffs(352, 519).addBox(-18f, 0f, 34f, 36f, 2f, 34f)
                .texOffs(0, 557).addBox(-18f, 0f, 0f, 36f, 2f, 34f)
                .texOffs(0, 481).addBox(18f, -2f, 102f, 2f, 4f, 34f)
                .texOffs(72, 481).addBox(-20f, -2f, 102f, 2f, 4f, 34f)
                .texOffs(144, 481).addBox(18f, -2f, 68f, 2f, 4f, 34f)
                .texOffs(216, 481).addBox(-20f, -2f, 68f, 2f, 4f, 34f)
                .texOffs(288, 481).addBox(18f, -2f, 34f, 2f, 4f, 34f)
                .texOffs(360, 481).addBox(-20f, -2f, 34f, 2f, 4f, 34f)
                .texOffs(432, 481).addBox(18f, -2f, 0f, 2f, 4f, 34f)
                .texOffs(0, 519).addBox(-20f, -2f, 0f, 2f, 4f, 34f),
                PartPose.offset(0f, -60f, 260f));
        return LayerDefinition.create(mesh, 512, 960);
    }

    /** Signo de xRot que baja el borde libre de las rampas (se comprueba en el juego). */
    private static final float RAMP_SIGN = -1.0f;

    /** wheelRot en radianes (lowAngle/highAngle = cuánto bajó cada rampa, en radianes, 0 = plana) (las 4 ruedas); legsDown = patas de apoyo abajo (sin tractor). */
    public void pose(float wheelRot, boolean legsDown, float lowAngle, float highAngle) {
        this.rampLow.xRot = RAMP_SIGN * lowAngle;
        this.rampHigh.xRot = RAMP_SIGN * highAngle;
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
