package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SupercarEntity;
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
 * Superdeportivo Bugotti Chillón (V3.7.0). ARCHIVO GENERADO por tools/v370/supercar.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 256x320. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios ahumados). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: las 4 ruedas (giran), las dos ruedas delanteras (doblan) y el volante (gira con la dirección).
 */
public class SupercarModel extends EntityModel<SupercarEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "bugotti_chillon"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart wheelFl;
    private final ModelPart wheelFr;
    private final ModelPart wheelRl;
    private final ModelPart wheelRr;
    private final ModelPart steerL;
    private final ModelPart steerR;
    private final ModelPart steeringWheel;
    private final ModelPart racing;
    private final ModelPart cop;
    private final ModelPart street;
    private final ModelPart taxi;
    private final ModelPart bar;
    private final ModelPart barRed;
    private final ModelPart barBlue;
    private final ModelPart sirenBtn;
    private boolean barsOn;

    public SupercarModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.steerL = this.body.getChild("steer_l");
        this.steerR = this.body.getChild("steer_r");
        this.wheelFl = this.steerL.getChild("wheel_fl");
        this.wheelFr = this.steerR.getChild("wheel_fr");
        this.wheelRl = this.body.getChild("wheel_rl");
        this.wheelRr = this.body.getChild("wheel_rr");
        this.steeringWheel = this.body.getChild("steering_tilt").getChild("steering_wheel");
        this.racing = this.body.getChild("racing");
        this.cop = this.body.getChild("cop");
        this.street = this.body.getChild("street");
        this.taxi = this.body.getChild("taxi");
        this.bar = this.body.getChild("bar");
        this.barRed = this.body.getChild("bar_red");
        this.barBlue = this.body.getChild("bar_blue");
        this.sirenBtn = this.body.getChild("siren_btn");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9f, -5f, -31f, 18f, 2f, 64f)
                .texOffs(164, 0).addBox(-14f, -5f, -16f, 28f, 2f, 32f)
                .texOffs(0, 207).addBox(-15f, -4f, -38f, 30f, 2f, 7f)
                .texOffs(172, 196).addBox(-12f, -8f, 32f, 24f, 5f, 5f)
                .texOffs(284, 0).addBox(14f, -6f, -16f, 2f, 2f, 32f)
                .texOffs(0, 66).addBox(-16f, -6f, -16f, 2f, 2f, 32f)
                .texOffs(204, 100).addBox(8f, -15f, -29f, 2f, 11f, 13f)
                .texOffs(234, 100).addBox(-10f, -15f, -29f, 2f, 11f, 13f)
                .texOffs(144, 100).addBox(7f, -17f, 16f, 2f, 13f, 13f)
                .texOffs(174, 100).addBox(-9f, -17f, 16f, 2f, 13f, 13f)
                .texOffs(166, 151).addBox(-14f, -11f, -36f, 28f, 7f, 8f)
                .texOffs(0, 216).addBox(-12f, -8f, -38f, 24f, 4f, 2f)
                .texOffs(284, 168).addBox(-13f, -13f, -18f, 26f, 3f, 10f)
                .texOffs(16, 183).addBox(-13f, -12f, -28f, 26f, 2f, 10f)
                .texOffs(284, 130).addBox(11f, -15f, -29f, 5f, 4f, 13f)
                .texOffs(320, 130).addBox(-16f, -15f, -29f, 5f, 4f, 13f)
                .texOffs(168, 183).addBox(14f, -11f, -34f, 2f, 6f, 6f)
                .texOffs(184, 183).addBox(-16f, -11f, -34f, 2f, 6f, 6f)
                .texOffs(146, 222).addBox(-12f, -10f, -37f, 24f, 2f, 1f)
                .texOffs(262, 216).addBox(12f, -10f, -37f, 2f, 4f, 1f)
                .texOffs(268, 216).addBox(-14f, -10f, -37f, 2f, 4f, 1f)
                .texOffs(120, 216).addBox(-9f, -8f, -39f, 18f, 4f, 1f)
                .texOffs(42, 222).addBox(9f, -7f, -38f, 4f, 3f, 1f)
                .texOffs(52, 222).addBox(-13f, -7f, -38f, 4f, 3f, 1f)
                .texOffs(300, 66).addBox(14f, -15f, -11f, 2f, 9f, 21f)
                .texOffs(0, 100).addBox(-16f, -15f, -11f, 2f, 9f, 21f)
                .texOffs(200, 183).addBox(14f, -13f, -16f, 2f, 7f, 5f)
                .texOffs(214, 183).addBox(-16f, -13f, -16f, 2f, 7f, 5f)
                .texOffs(356, 168).addBox(14f, -13f, 10f, 2f, 7f, 6f)
                .texOffs(0, 183).addBox(-16f, -13f, 10f, 2f, 7f, 6f)
                .texOffs(0, 130).addBox(12f, -15f, -8f, 4f, 1f, 20f)
                .texOffs(48, 130).addBox(-16f, -15f, -8f, 4f, 1f, 20f)
                .texOffs(286, 196).addBox(16f, -13f, 10f, 1f, 5f, 5f)
                .texOffs(298, 196).addBox(-17f, -13f, 10f, 1f, 5f, 5f)
                .texOffs(52, 216).addBox(16f, -12f, -1f, 1f, 1f, 5f)
                .texOffs(64, 216).addBox(-17f, -12f, -1f, 1f, 1f, 5f)
                .texOffs(334, 222).addBox(16f, -16f, -5f, 2f, 1f, 2f)
                .texOffs(342, 222).addBox(-18f, -16f, -5f, 2f, 1f, 2f)
                .texOffs(276, 207).addBox(17f, -18f, -6f, 3f, 3f, 4f)
                .texOffs(290, 207).addBox(-20f, -18f, -6f, 3f, 3f, 4f)
                .texOffs(264, 100).addBox(-12f, -5f, -8f, 24f, 1f, 22f)
                .texOffs(204, 66).addBox(12f, -14f, -8f, 2f, 9f, 22f)
                .texOffs(252, 66).addBox(-14f, -14f, -8f, 2f, 9f, 22f)
                .texOffs(228, 130).addBox(-13f, -20f, 12f, 26f, 15f, 2f)
                .texOffs(154, 207).addBox(-12f, -11f, -8f, 24f, 4f, 4f)
                .texOffs(0, 151).addBox(-2f, -9f, -4f, 4f, 4f, 13f)
                .texOffs(74, 207).addBox(2.5f, -6f, 1f, 8f, 1f, 8f)
                .texOffs(100, 196).addBox(2.5f, -15f, 9f, 8f, 9f, 2f)
                .texOffs(76, 216).addBox(4.5f, -19f, 9f, 4f, 4f, 2f)
                .texOffs(140, 196).addBox(1.5f, -10f, 2f, 1f, 4f, 7f)
                .texOffs(106, 207).addBox(-10.5f, -6f, 1f, 8f, 1f, 8f)
                .texOffs(120, 196).addBox(-10.5f, -15f, 9f, 8f, 9f, 2f)
                .texOffs(88, 216).addBox(-8.5f, -19f, 9f, 4f, 4f, 2f)
                .texOffs(156, 196).addBox(-2.5f, -10f, 2f, 1f, 4f, 7f)
                .texOffs(78, 222).addBox(5f, -8f, -4f, 2f, 3f, 1f)
                .texOffs(274, 216).addBox(8f, -9f, -4f, 1f, 4f, 1f)
                .texOffs(222, 216).addBox(10f, -7f, -5f, 2f, 2f, 3f)
                .texOffs(158, 216).addBox(2f, -13f, -7f, 9f, 2f, 3f)
                .texOffs(72, 196).addBox(-2f, -10f, -1f, 4f, 1f, 10f)
                .texOffs(206, 216).addBox(-2f, -11f, 0f, 4f, 1f, 4f)
                .texOffs(62, 222).addBox(-1f, -13f, 1f, 2f, 2f, 2f)
                .texOffs(350, 222).addBox(-1f, -14f, 1f, 2f, 1f, 2f)
                .texOffs(232, 216).addBox(-2f, -11f, 5f, 1f, 1f, 4f)
                .texOffs(84, 222).addBox(10f, -14f, -2f, 1f, 3f, 1f)
                .texOffs(88, 222).addBox(2f, -14f, -2f, 1f, 3f, 1f)
                .texOffs(132, 183).addBox(10.5f, -10f, 1f, 1f, 4f, 8f)
                .texOffs(310, 196).addBox(10.5f, -14f, 7f, 1f, 6f, 4f)
                .texOffs(270, 222).addBox(2.5f, -7f, 0f, 8f, 2f, 1f)
                .texOffs(0, 226).addBox(1.5f, -8f, 5f, 1f, 2f, 1f)
                .texOffs(150, 183).addBox(-11.5f, -10f, 1f, 1f, 4f, 8f)
                .texOffs(320, 196).addBox(-11.5f, -14f, 7f, 1f, 6f, 4f)
                .texOffs(288, 222).addBox(-10.5f, -7f, 0f, 8f, 2f, 1f)
                .texOffs(4, 226).addBox(-2.5f, -8f, 5f, 1f, 2f, 1f)
                .texOffs(88, 183).addBox(11f, -11f, -3f, 1f, 2f, 10f)
                .texOffs(110, 183).addBox(-12f, -11f, -3f, 1f, 2f, 10f)
                .texOffs(100, 216).addBox(11f, -13f, -2f, 1f, 2f, 4f)
                .texOffs(110, 216).addBox(-12f, -13f, -2f, 1f, 2f, 4f)
                .texOffs(138, 207).addBox(7f, -21f, 11f, 2f, 7f, 2f)
                .texOffs(146, 207).addBox(-9f, -21f, 11f, 2f, 7f, 2f)
                .texOffs(286, 216).addBox(-9f, -21f, 11f, 18f, 2f, 2f)
                .texOffs(366, 216).addBox(-3f, -19f, 1f, 6f, 2f, 2f)
                .texOffs(358, 222).addBox(-1f, -21f, 2f, 2f, 2f, 1f)
                .texOffs(252, 216).addBox(5.5f, -10f, -6f, 2f, 2f, 3f)
                .texOffs(364, 222).addBox(1f, -10f, -4f, 2f, 2f, 1f)
                .texOffs(370, 222).addBox(-3f, -10f, -4f, 2f, 2f, 1f)
                .texOffs(0, 196).addBox(-13f, -22f, 2f, 26f, 1f, 10f)
                .texOffs(210, 207).addBox(-3f, -25f, 5f, 6f, 3f, 5f)
                .texOffs(0, 222).addBox(-3f, -25f, 4f, 6f, 3f, 1f)
                .texOffs(232, 207).addBox(12f, -21f, 10f, 2f, 6f, 2f)
                .texOffs(240, 207).addBox(-14f, -21f, 10f, 2f, 6f, 2f)
                .texOffs(96, 130).addBox(11f, -18f, 15f, 5f, 7f, 14f)
                .texOffs(134, 130).addBox(-16f, -18f, 15f, 5f, 7f, 14f)
                .texOffs(248, 168).addBox(14f, -14f, 29f, 2f, 8f, 7f)
                .texOffs(266, 168).addBox(-16f, -14f, 29f, 2f, 8f, 7f)
                .texOffs(34, 151).addBox(-14f, -14f, 28f, 28f, 8f, 8f)
                .texOffs(92, 222).addBox(-13f, -12f, 36f, 26f, 2f, 1f)
                .texOffs(304, 207).addBox(4f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(318, 207).addBox(-7f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(332, 207).addBox(8f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(346, 207).addBox(-11f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(248, 207).addBox(8f, -20f, 32f, 2f, 6f, 2f)
                .texOffs(256, 207).addBox(-10f, -20f, 32f, 2f, 6f, 2f)
                .texOffs(240, 183).addBox(-16f, -22f, 29f, 32f, 2f, 9f)
                .texOffs(106, 151).addBox(15f, -24f, 28f, 1f, 6f, 10f)
                .texOffs(128, 151).addBox(-16f, -24f, 28f, 1f, 6f, 10f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offset(12.5f, -5.5f, 22f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(284, 151).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(306, 151).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(328, 151).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(350, 151).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(13f, -5.5f, -22f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(88, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(108, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(128, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(148, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offset(-12.5f, -5.5f, 22f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(0, 168).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(22, 168).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(44, 168).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(66, 168).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-13f, -5.5f, -22f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(168, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(188, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(208, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(228, 168).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_belt_l = p_body.addOrReplaceChild("belt_l",
                CubeListBuilder.create()
                .texOffs(228, 183).addBox(-1f, 0f, -0.5f, 2f, 11f, 1f),
                PartPose.offsetAndRotation(9.5f, -17.5f, 8.5f, 0f, 0f, 0.559f));
        PartDefinition p_belt_r = p_body.addOrReplaceChild("belt_r",
                CubeListBuilder.create()
                .texOffs(234, 183).addBox(-1f, 0f, -0.5f, 2f, 11f, 1f),
                PartPose.offsetAndRotation(-9.5f, -17.5f, 8.5f, 0f, 0f, -0.559f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(6.5f, -11.5f, -3f, -0.524f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(306, 222).addBox(-3f, -4f, -1f, 6f, 1f, 2f)
                .texOffs(322, 222).addBox(-2f, 2f, -1f, 4f, 1f, 2f)
                .texOffs(264, 207).addBox(3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(270, 207).addBox(-4f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(242, 216).addBox(-1f, -2f, -1f, 3f, 3f, 2f)
                .texOffs(104, 226).addBox(-3f, -1f, -1f, 7f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(150, 151).addBox(11f, -14f, -1f, 2f, 14f, 2f)
                .texOffs(158, 151).addBox(-13f, -14f, -1f, 2f, 14f, 2f),
                PartPose.offsetAndRotation(0f, -13f, -8f, -0.942f, 0f, 0f));
        PartDefinition p_deck = p_body.addOrReplaceChild("deck",
                CubeListBuilder.create()
                .texOffs(46, 100).addBox(-12f, -1f, 0f, 24f, 2f, 25f)
                .texOffs(230, 196).addBox(4f, -2f, 4.5f, 5f, 1f, 9f)
                .texOffs(258, 196).addBox(-9f, -2f, 4.5f, 5f, 1f, 9f),
                PartPose.offsetAndRotation(0f, -20f, 12f, -0.262f, 0f, 0f));
        PartDefinition p_siren_btn = p_body.addOrReplaceChild("siren_btn",
                CubeListBuilder.create()
                .texOffs(376, 222).addBox(-6f, -10f, -4f, 2f, 2f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar = p_body.addOrReplaceChild("bar",
                CubeListBuilder.create()
                .texOffs(196, 222).addBox(-9f, -23f, 2f, 18f, 1f, 2f)
                .texOffs(70, 222).addBox(-1f, -25f, 2f, 2f, 2f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_red = p_body.addOrReplaceChild("bar_red",
                CubeListBuilder.create()
                .texOffs(326, 216).addBox(1f, -25f, 2f, 8f, 2f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_blue = p_body.addOrReplaceChild("bar_blue",
                CubeListBuilder.create()
                .texOffs(346, 216).addBox(-9f, -25f, 2f, 8f, 2f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_racing = p_body.addOrReplaceChild("racing",
                CubeListBuilder.create()
                .texOffs(14, 222).addBox(13f, -6f, -38f, 4f, 1f, 3f)
                .texOffs(28, 222).addBox(-17f, -6f, -38f, 4f, 1f, 3f)
                .texOffs(8, 226).addBox(-13f, -15f, 37f, 26f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_cop = p_body.addOrReplaceChild("cop",
                CubeListBuilder.create()
                .texOffs(236, 222).addBox(-8f, -6f, -40f, 16f, 2f, 1f)
                .texOffs(278, 216).addBox(7f, -8f, -39f, 1f, 4f, 1f)
                .texOffs(282, 216).addBox(-8f, -8f, -39f, 1f, 4f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_street = p_body.addOrReplaceChild("street",
                CubeListBuilder.create()
                .texOffs(68, 66).addBox(14f, -4f, -16f, 2f, 1f, 32f)
                .texOffs(136, 66).addBox(-16f, -4f, -16f, 2f, 1f, 32f)
                .texOffs(62, 226).addBox(-10f, -3f, -39f, 20f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_taxi = p_body.addOrReplaceChild("taxi",
                CubeListBuilder.create()
                .texOffs(182, 216).addBox(-5f, -25f, 2f, 10f, 3f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(172, 130).addBox(13f, -21f, -3f, 1f, 6f, 13f)
                .texOffs(200, 130).addBox(-14f, -21f, -3f, 1f, 6f, 13f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(238, 151).addBox(-11f, -14f, -0.5f, 22f, 14f, 1f),
                PartPose.offsetAndRotation(0f, -13f, -8f, -0.942f, 0f, 0f));
        return LayerDefinition.create(mesh, 256, 320);
    }

    /** wheelRot en radianes (las 4 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {
        this.wheelFl.xRot = wheelRot;
        this.wheelFr.xRot = wheelRot;
        this.wheelRl.xRot = wheelRot;
        this.wheelRr.xRot = wheelRot;
        this.steerL.yRot = steerRot;
        this.steerR.yRot = steerRot;
        this.steeringWheel.zRot = -steerRot * 2.5f;
    }

    /** V6.8.0: accesorios de la skin: 0 = ninguno, 1 = carreras, 2 = policía, 3 = callejero, 4 = taxi. */
    public void setSkin(int skin) {
        this.racing.visible = skin == 1;
        this.cop.visible = skin == 2;
        this.street.visible = skin == 3;
        this.taxi.visible = skin == 4;
        this.barsOn = skin == 2;
        this.bar.visible = this.barsOn;
        this.sirenBtn.visible = this.barsOn;
        this.setBarPhase(-2);
    }

    /** V6.8.0: -2 = las dos mitades de la barra (pasada normal); en la pasada de brillo, 0 = roja, 1 = azul, otro = ninguna. */
    public void setBarPhase(int phase) {
        this.barRed.visible = this.barsOn && (phase == -2 || phase == 0);
        this.barBlue.visible = this.barsOn && (phase == -2 || phase == 1);
    }

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }

    @Override
    public void setupAnim(SupercarEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
