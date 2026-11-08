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
 * Atlas de textura de 384x256. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
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
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9f, -5f, -31f, 18f, 2f, 64f)
                .texOffs(164, 0).addBox(-14f, -5f, -16f, 28f, 2f, 32f)
                .texOffs(0, 186).addBox(-15f, -4f, -38f, 30f, 2f, 7f)
                .texOffs(226, 175).addBox(-12f, -8f, 32f, 24f, 5f, 5f)
                .texOffs(284, 0).addBox(14f, -6f, -16f, 2f, 2f, 32f)
                .texOffs(0, 66).addBox(-16f, -6f, -16f, 2f, 2f, 32f)
                .texOffs(30, 100).addBox(8f, -15f, -29f, 2f, 11f, 13f)
                .texOffs(60, 100).addBox(-10f, -15f, -29f, 2f, 11f, 13f)
                .texOffs(354, 66).addBox(7f, -17f, 16f, 2f, 13f, 13f)
                .texOffs(0, 100).addBox(-9f, -17f, 16f, 2f, 13f, 13f)
                .texOffs(0, 145).addBox(-14f, -11f, -36f, 28f, 7f, 8f)
                .texOffs(0, 195).addBox(-12f, -8f, -38f, 24f, 4f, 2f)
                .texOffs(116, 160).addBox(-13f, -13f, -18f, 26f, 3f, 10f)
                .texOffs(220, 160).addBox(-13f, -12f, -28f, 26f, 2f, 10f)
                .texOffs(84, 126).addBox(11f, -15f, -29f, 5f, 4f, 13f)
                .texOffs(120, 126).addBox(-16f, -15f, -29f, 5f, 4f, 13f)
                .texOffs(292, 160).addBox(14f, -11f, -34f, 2f, 6f, 6f)
                .texOffs(308, 160).addBox(-16f, -11f, -34f, 2f, 6f, 6f)
                .texOffs(258, 195).addBox(-12f, -10f, -37f, 24f, 2f, 1f)
                .texOffs(158, 195).addBox(12f, -10f, -37f, 2f, 4f, 1f)
                .texOffs(164, 195).addBox(-14f, -10f, -37f, 2f, 4f, 1f)
                .texOffs(100, 195).addBox(-9f, -8f, -39f, 18f, 4f, 1f)
                .texOffs(184, 195).addBox(9f, -7f, -38f, 4f, 3f, 1f)
                .texOffs(194, 195).addBox(-13f, -7f, -38f, 4f, 3f, 1f)
                .texOffs(164, 66).addBox(14f, -15f, -11f, 2f, 9f, 21f)
                .texOffs(210, 66).addBox(-16f, -15f, -11f, 2f, 9f, 21f)
                .texOffs(324, 160).addBox(14f, -13f, -16f, 2f, 7f, 5f)
                .texOffs(338, 160).addBox(-16f, -13f, -16f, 2f, 7f, 5f)
                .texOffs(188, 160).addBox(14f, -13f, 10f, 2f, 7f, 6f)
                .texOffs(204, 160).addBox(-16f, -13f, 10f, 2f, 7f, 6f)
                .texOffs(182, 100).addBox(12f, -15f, -8f, 4f, 1f, 20f)
                .texOffs(230, 100).addBox(-16f, -15f, -8f, 4f, 1f, 20f)
                .texOffs(340, 175).addBox(16f, -13f, 10f, 1f, 5f, 5f)
                .texOffs(352, 175).addBox(-17f, -13f, 10f, 1f, 5f, 5f)
                .texOffs(52, 195).addBox(16f, -12f, -1f, 1f, 1f, 5f)
                .texOffs(64, 195).addBox(-17f, -12f, -1f, 1f, 1f, 5f)
                .texOffs(336, 195).addBox(16f, -16f, -5f, 2f, 1f, 2f)
                .texOffs(344, 195).addBox(-18f, -16f, -5f, 2f, 1f, 2f)
                .texOffs(260, 186).addBox(17f, -18f, -6f, 3f, 3f, 4f)
                .texOffs(274, 186).addBox(-20f, -18f, -6f, 3f, 3f, 4f)
                .texOffs(90, 100).addBox(-12f, -5f, -8f, 24f, 1f, 22f)
                .texOffs(68, 66).addBox(12f, -14f, -8f, 2f, 9f, 22f)
                .texOffs(116, 66).addBox(-14f, -14f, -8f, 2f, 9f, 22f)
                .texOffs(28, 126).addBox(-13f, -20f, 12f, 26f, 15f, 2f)
                .texOffs(138, 186).addBox(-12f, -11f, -8f, 24f, 4f, 4f)
                .texOffs(156, 126).addBox(-2f, -9f, -4f, 4f, 4f, 13f)
                .texOffs(74, 186).addBox(2.5f, -6f, 1f, 8f, 1f, 8f)
                .texOffs(154, 175).addBox(2.5f, -15f, 9f, 8f, 9f, 2f)
                .texOffs(76, 195).addBox(4.5f, -19f, 9f, 4f, 4f, 2f)
                .texOffs(194, 175).addBox(1.5f, -10f, 2f, 1f, 4f, 7f)
                .texOffs(106, 186).addBox(-10.5f, -6f, 1f, 8f, 1f, 8f)
                .texOffs(174, 175).addBox(-10.5f, -15f, 9f, 8f, 9f, 2f)
                .texOffs(88, 195).addBox(-8.5f, -19f, 9f, 4f, 4f, 2f)
                .texOffs(210, 175).addBox(-2.5f, -10f, 2f, 1f, 4f, 7f)
                .texOffs(148, 195).addBox(5.5f, -10f, -6f, 2f, 2f, 3f)
                .texOffs(82, 175).addBox(-13f, -22f, 2f, 26f, 1f, 10f)
                .texOffs(194, 186).addBox(-3f, -25f, 5f, 6f, 3f, 5f)
                .texOffs(170, 195).addBox(-3f, -25f, 4f, 6f, 3f, 1f)
                .texOffs(216, 186).addBox(12f, -21f, 10f, 2f, 6f, 2f)
                .texOffs(224, 186).addBox(-14f, -21f, 10f, 2f, 6f, 2f)
                .texOffs(278, 100).addBox(11f, -18f, 15f, 5f, 7f, 14f)
                .texOffs(316, 100).addBox(-16f, -18f, 15f, 5f, 7f, 14f)
                .texOffs(80, 160).addBox(14f, -14f, 29f, 2f, 8f, 7f)
                .texOffs(98, 160).addBox(-16f, -14f, 29f, 2f, 8f, 7f)
                .texOffs(190, 126).addBox(-14f, -14f, 28f, 28f, 8f, 8f)
                .texOffs(204, 195).addBox(-13f, -12f, 36f, 26f, 2f, 1f)
                .texOffs(288, 186).addBox(4f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(302, 186).addBox(-7f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(316, 186).addBox(8f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(330, 186).addBox(-11f, -9f, 36f, 3f, 3f, 4f)
                .texOffs(232, 186).addBox(8f, -20f, 32f, 2f, 6f, 2f)
                .texOffs(240, 186).addBox(-10f, -20f, 32f, 2f, 6f, 2f)
                .texOffs(0, 175).addBox(-16f, -22f, 29f, 32f, 2f, 9f)
                .texOffs(262, 126).addBox(15f, -24f, 28f, 1f, 6f, 10f)
                .texOffs(284, 126).addBox(-16f, -24f, 28f, 1f, 6f, 10f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offset(12.5f, -5.5f, 22f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(118, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(140, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(162, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(184, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(13f, -5.5f, -22f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(294, 145).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(314, 145).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(334, 145).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(354, 145).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offset(-12.5f, -5.5f, 22f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(206, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(228, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(250, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(272, 145).addBox(-3.5f, -5.5f, -2f, 7f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-13f, -5.5f, -22f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(0, 160).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(20, 160).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(40, 160).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(60, 160).addBox(-3f, -5.5f, -2f, 6f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(6.5f, -11.5f, -3f, -0.524f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(308, 195).addBox(-3f, -4f, -1f, 6f, 1f, 2f)
                .texOffs(324, 195).addBox(-2f, 2f, -1f, 4f, 1f, 2f)
                .texOffs(248, 186).addBox(3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(254, 186).addBox(-4f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(138, 195).addBox(-1f, -2f, -1f, 3f, 3f, 2f)
                .texOffs(352, 195).addBox(-3f, -1f, -1f, 7f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(306, 126).addBox(11f, -14f, -1f, 2f, 14f, 2f)
                .texOffs(314, 126).addBox(-13f, -14f, -1f, 2f, 14f, 2f),
                PartPose.offsetAndRotation(0f, -13f, -8f, -0.942f, 0f, 0f));
        PartDefinition p_deck = p_body.addOrReplaceChild("deck",
                CubeListBuilder.create()
                .texOffs(256, 66).addBox(-12f, -1f, 0f, 24f, 2f, 25f)
                .texOffs(284, 175).addBox(4f, -2f, 4.5f, 5f, 1f, 9f)
                .texOffs(312, 175).addBox(-9f, -2f, 4.5f, 5f, 1f, 9f),
                PartPose.offsetAndRotation(0f, -20f, 12f, -0.262f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(354, 100).addBox(13f, -21f, -3f, 1f, 6f, 13f)
                .texOffs(0, 126).addBox(-14f, -21f, -3f, 1f, 6f, 13f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(72, 145).addBox(-11f, -14f, -0.5f, 22f, 14f, 1f),
                PartPose.offsetAndRotation(0f, -13f, -8f, -0.942f, 0f, 0f));
        return LayerDefinition.create(mesh, 384, 256);
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
