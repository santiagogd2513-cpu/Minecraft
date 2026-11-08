package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.PickupEntity;
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
 * Camioneta Furd F-150 (V2.1.0). ARCHIVO GENERADO por tools/v210/truck.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 256x256. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios ahumados). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: las 4 ruedas (giran), las dos ruedas delanteras (doblan) y el volante (gira con la dirección).
 */
public class PickupModel extends EntityModel<PickupEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "ford_f150"), "main");

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

    public PickupModel(ModelPart root) {
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
                .texOffs(0, 51).addBox(-9f, -15f, -35f, 18f, 9f, 22f)
                .texOffs(0, 0).addBox(-9f, -10f, -13f, 18f, 4f, 47f)
                .texOffs(80, 192).addBox(5f, -9f, 21f, 4f, 3f, 10f)
                .texOffs(178, 218).addBox(6f, -9f, 31f, 2f, 2f, 6f)
                .texOffs(54, 111).addBox(-12f, -12f, 13f, 24f, 2f, 21f)
                .texOffs(80, 51).addBox(12f, -22f, 11f, 2f, 7f, 24f)
                .texOffs(132, 51).addBox(-14f, -22f, 11f, 2f, 7f, 24f)
                .texOffs(232, 192).addBox(12f, -15f, 11f, 2f, 8f, 4f)
                .texOffs(244, 192).addBox(-14f, -15f, 11f, 2f, 8f, 4f)
                .texOffs(0, 206).addBox(12f, -15f, 31f, 2f, 8f, 4f)
                .texOffs(12, 206).addBox(-14f, -15f, 31f, 2f, 8f, 4f)
                .texOffs(196, 82).addBox(11f, -23f, 11f, 3f, 1f, 24f)
                .texOffs(0, 111).addBox(-14f, -23f, 11f, 3f, 1f, 24f)
                .texOffs(0, 175).addBox(9f, -15f, 16f, 3f, 3f, 14f)
                .texOffs(34, 175).addBox(-12f, -15f, 16f, 3f, 3f, 14f)
                .texOffs(152, 192).addBox(-12f, -22f, 11f, 24f, 10f, 2f)
                .texOffs(68, 175).addBox(-12f, -22f, 33f, 24f, 14f, 2f)
                .texOffs(124, 228).addBox(-14f, -10f, 35f, 28f, 3f, 3f)
                .texOffs(222, 218).addBox(11f, -21f, 35f, 3f, 7f, 1f)
                .texOffs(230, 218).addBox(-14f, -21f, 35f, 3f, 7f, 1f)
                .texOffs(100, 82).addBox(-12f, -10f, -13f, 24f, 2f, 24f)
                .texOffs(130, 0).addBox(12f, -22f, -13f, 2f, 15f, 24f)
                .texOffs(182, 0).addBox(-14f, -22f, -13f, 2f, 15f, 24f)
                .texOffs(144, 111).addBox(14f, -9f, -11f, 2f, 1f, 20f)
                .texOffs(188, 111).addBox(-16f, -9f, -11f, 2f, 1f, 20f)
                .texOffs(24, 235).addBox(14f, -19f, -7f, 1f, 1f, 4f)
                .texOffs(34, 235).addBox(-15f, -19f, -7f, 1f, 1f, 4f)
                .texOffs(44, 235).addBox(14f, -19f, 3f, 1f, 1f, 4f)
                .texOffs(54, 235).addBox(-15f, -19f, 3f, 1f, 1f, 4f)
                .texOffs(0, 192).addBox(-12f, -22f, 9f, 24f, 12f, 2f)
                .texOffs(156, 175).addBox(-12f, -21f, -13f, 24f, 11f, 4f)
                .texOffs(52, 192).addBox(-2f, -14f, -7f, 4f, 4f, 10f)
                .texOffs(84, 235).addBox(-1f, -17f, -4f, 2f, 3f, 2f)
                .texOffs(214, 206).addBox(2.7f, -12f, -8.8f, 8f, 2f, 8f)
                .texOffs(90, 206).addBox(2.7f, -21f, -0.8f, 8f, 9f, 2f)
                .texOffs(232, 228).addBox(4.7f, -25f, -0.8f, 4f, 4f, 2f)
                .texOffs(0, 218).addBox(-10.7f, -12f, -8.8f, 8f, 2f, 8f)
                .texOffs(110, 206).addBox(-10.7f, -21f, -0.8f, 8f, 9f, 2f)
                .texOffs(244, 228).addBox(-8.7f, -25f, -0.8f, 4f, 4f, 2f)
                .texOffs(122, 218).addBox(-11f, -12f, 1f, 22f, 2f, 6f)
                .texOffs(166, 206).addBox(-11f, -20f, 7f, 22f, 8f, 2f)
                .texOffs(74, 235).addBox(5.7f, -16f, -12f, 2f, 2f, 3f)
                .texOffs(0, 82).addBox(-14f, -22f, -35f, 28f, 7f, 22f)
                .texOffs(160, 156).addBox(-8f, -23f, -31f, 16f, 1f, 16f)
                .texOffs(204, 192).addBox(11f, -15f, -35f, 3f, 8f, 4f)
                .texOffs(218, 192).addBox(-14f, -15f, -35f, 3f, 8f, 4f)
                .texOffs(62, 228).addBox(-9f, -21f, -36f, 18f, 6f, 1f)
                .texOffs(0, 235).addBox(9f, -21f, -36f, 5f, 5f, 1f)
                .texOffs(12, 235).addBox(-14f, -21f, -36f, 5f, 5f, 1f)
                .texOffs(0, 228).addBox(-14f, -13f, -38f, 28f, 4f, 3f)
                .texOffs(186, 228).addBox(-10f, -9f, -37f, 20f, 3f, 3f)
                .texOffs(128, 235).addBox(5f, -9f, -38f, 3f, 2f, 1f)
                .texOffs(136, 235).addBox(-8f, -9f, -38f, 3f, 2f, 1f)
                .texOffs(0, 136).addBox(-14f, -32f, -7f, 28f, 2f, 18f)
                .texOffs(32, 218).addBox(12f, -30f, -1f, 2f, 8f, 2f)
                .texOffs(40, 218).addBox(-14f, -30f, -1f, 2f, 8f, 2f)
                .texOffs(48, 218).addBox(12f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(56, 218).addBox(-14f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(64, 218).addBox(10f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(72, 218).addBox(-12f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(156, 235).addBox(14f, -25f, -11f, 2f, 1f, 1f)
                .texOffs(162, 235).addBox(-16f, -25f, -11f, 2f, 1f, 1f)
                .texOffs(100, 228).addBox(15f, -27f, -12f, 3f, 4f, 3f)
                .texOffs(112, 228).addBox(-18f, -27f, -12f, 3f, 4f, 3f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(12f, -7f, 23f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(92, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(112, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(132, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(152, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(12f, -7f, -23f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(172, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(192, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(212, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(232, 136).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-12f, -7f, 23f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(0, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(20, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(40, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(60, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-12f, -7f, -23f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(80, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(100, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(120, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(140, 156).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(6.7f, -17.5f, -8.2f, -0.436f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(92, 235).addBox(-3f, -4f, -1f, 7f, 1f, 2f)
                .texOffs(110, 235).addBox(-3f, 3f, -1f, 7f, 1f, 2f)
                .texOffs(238, 218).addBox(3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(244, 218).addBox(-3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(64, 235).addBox(-1f, -2f, -1f, 3f, 3f, 2f)
                .texOffs(144, 235).addBox(-2f, -1f, -1f, 5f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(24, 206).addBox(12f, -10.2f, -1f, 2f, 10f, 2f)
                .texOffs(32, 206).addBox(-14f, -10.2f, -1f, 2f, 10f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -13f, -0.698f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(108, 192).addBox(13f, -25f, -11f, 1f, 3f, 10f)
                .texOffs(130, 192).addBox(-14f, -25f, -11f, 1f, 3f, 10f)
                .texOffs(130, 206).addBox(13f, -28f, -9f, 1f, 3f, 8f)
                .texOffs(148, 206).addBox(-14f, -28f, -9f, 1f, 3f, 8f)
                .texOffs(194, 218).addBox(13f, -30f, -7f, 1f, 2f, 6f)
                .texOffs(208, 218).addBox(-14f, -30f, -7f, 1f, 2f, 6f)
                .texOffs(120, 175).addBox(13f, -30f, 1f, 1f, 8f, 8f)
                .texOffs(138, 175).addBox(-14f, -30f, 1f, 1f, 8f, 8f)
                .texOffs(80, 218).addBox(-10f, -30f, 10f, 20f, 8f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(40, 206).addBox(-12f, -10.2f, -0.5f, 24f, 10f, 1f),
                PartPose.offsetAndRotation(0f, -22f, -13f, -0.698f, 0f, 0f));
        return LayerDefinition.create(mesh, 256, 256);
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
    public void setupAnim(PickupEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
