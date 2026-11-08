package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.DeliveryTruckEntity;
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
 * Camión de reparto AMAZOOM (V3.0.0). ARCHIVO GENERADO por tools/v300/delivery_truck.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 256x320. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios ahumados). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: las 4 ruedas (giran), las dos ruedas delanteras (doblan) y el volante (gira con la dirección).
 */
public class DeliveryTruckModel extends EntityModel<DeliveryTruckEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "delivery_truck"), "main");

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

    public DeliveryTruckModel(ModelPart root) {
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
                .texOffs(52, 98).addBox(-9f, -11f, 36f, 18f, 3f, 30f)
                .texOffs(148, 98).addBox(-9f, -11f, 6f, 18f, 3f, 30f)
                .texOffs(0, 136).addBox(-9f, -11f, -24f, 18f, 3f, 30f)
                .texOffs(152, 217).addBox(-10f, -11f, -27f, 20f, 5f, 13f)
                .texOffs(80, 217).addBox(10f, -11f, 10f, 4f, 5f, 14f)
                .texOffs(116, 217).addBox(-14f, -11f, 10f, 4f, 5f, 14f)
                .texOffs(238, 278).addBox(12f, -9f, 47f, 2f, 6f, 1f)
                .texOffs(244, 278).addBox(-14f, -9f, 47f, 2f, 6f, 1f)
                .texOffs(0, 0).addBox(-15f, -43f, 4f, 30f, 34f, 64f)
                .texOffs(108, 278).addBox(-15f, -12f, 68f, 30f, 4f, 3f)
                .texOffs(88, 278).addBox(11f, -21f, 68f, 4f, 7f, 1f)
                .texOffs(98, 278).addBox(-15f, -21f, 68f, 4f, 7f, 1f)
                .texOffs(70, 293).addBox(12f, -42f, 4f, 2f, 1f, 1f)
                .texOffs(76, 293).addBox(-14f, -42f, 4f, 2f, 1f, 1f)
                .texOffs(82, 293).addBox(-1f, -42f, 4f, 2f, 1f, 1f)
                .texOffs(0, 169).addBox(-14f, -12f, -24f, 28f, 2f, 26f)
                .texOffs(188, 0).addBox(12f, -25f, -22f, 2f, 14f, 24f)
                .texOffs(0, 98).addBox(-14f, -25f, -22f, 2f, 14f, 24f)
                .texOffs(64, 237).addBox(14f, -10f, -20f, 3f, 1f, 16f)
                .texOffs(102, 237).addBox(-17f, -10f, -20f, 3f, 1f, 16f)
                .texOffs(16, 287).addBox(14f, -22f, -20f, 1f, 1f, 5f)
                .texOffs(28, 287).addBox(-15f, -22f, -20f, 1f, 1f, 5f)
                .texOffs(184, 169).addBox(-14f, -34f, 0f, 28f, 22f, 2f)
                .texOffs(140, 237).addBox(-12f, -23f, -22f, 24f, 11f, 5f)
                .texOffs(198, 237).addBox(-2f, -16f, -14f, 4f, 4f, 11f)
                .texOffs(64, 287).addBox(-1f, -20f, -12f, 2f, 4f, 2f)
                .texOffs(76, 268).addBox(3f, -14f, -13f, 8f, 2f, 8f)
                .texOffs(0, 255).addBox(3f, -25f, -5f, 8f, 11f, 2f)
                .texOffs(40, 287).addBox(5f, -29f, -5f, 4f, 4f, 2f)
                .texOffs(108, 268).addBox(-11f, -14f, -13f, 8f, 2f, 8f)
                .texOffs(20, 255).addBox(-11f, -25f, -5f, 8f, 11f, 2f)
                .texOffs(52, 287).addBox(-9f, -29f, -5f, 4f, 4f, 2f)
                .texOffs(226, 278).addBox(6f, -18f, -20f, 2f, 3f, 4f)
                .texOffs(0, 237).addBox(-14f, -25f, -26f, 28f, 14f, 4f)
                .texOffs(42, 268).addBox(-8f, -22f, -27f, 16f, 9f, 1f)
                .texOffs(64, 278).addBox(9f, -21f, -27f, 5f, 7f, 1f)
                .texOffs(76, 278).addBox(-14f, -21f, -27f, 5f, 7f, 1f)
                .texOffs(152, 268).addBox(-15f, -13f, -30f, 30f, 5f, 4f)
                .texOffs(174, 278).addBox(-11f, -8f, -28f, 22f, 3f, 4f)
                .texOffs(22, 293).addBox(5f, -11f, -31f, 3f, 2f, 1f)
                .texOffs(30, 293).addBox(-8f, -11f, -31f, 3f, 2f, 1f)
                .texOffs(144, 287).addBox(-4f, -12f, -31f, 8f, 3f, 1f)
                .texOffs(104, 136).addBox(-14f, -36f, -26f, 28f, 2f, 28f)
                .texOffs(40, 255).addBox(12f, -34f, -24f, 2f, 9f, 4f)
                .texOffs(52, 255).addBox(-14f, -34f, -24f, 2f, 9f, 4f)
                .texOffs(64, 255).addBox(12f, -34f, -2f, 2f, 9f, 4f)
                .texOffs(76, 255).addBox(-14f, -34f, -2f, 2f, 9f, 4f)
                .texOffs(88, 255).addBox(10f, -34f, -24f, 2f, 9f, 4f)
                .texOffs(100, 255).addBox(-12f, -34f, -24f, 2f, 9f, 4f)
                .texOffs(54, 293).addBox(14f, -29f, -21f, 3f, 1f, 1f)
                .texOffs(62, 293).addBox(-17f, -29f, -21f, 3f, 1f, 1f)
                .texOffs(228, 237).addBox(17f, -32f, -22f, 2f, 10f, 4f)
                .texOffs(240, 237).addBox(-19f, -32f, -22f, 2f, 10f, 4f)
                .texOffs(112, 255).addBox(-13f, -38f, -6f, 26f, 2f, 10f)
                .texOffs(0, 278).addBox(-13f, -41f, -2f, 26f, 3f, 6f)
                .texOffs(72, 287).addBox(-13f, -43f, 1f, 26f, 2f, 3f)
                .texOffs(96, 136).addBox(-14f, -42f, 2f, 2f, 30f, 2f)
                .texOffs(0, 287).addBox(-15f, -44f, 1f, 4f, 2f, 4f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(13f, -7.5f, 36f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(0, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(20, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(40, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(60, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(13f, -7.5f, -17f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(80, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(100, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(120, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(140, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-13f, -7.5f, 36f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(160, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(180, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(200, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(220, 197).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-13f, -7.5f, -17f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(0, 217).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(20, 217).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(40, 217).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(60, 217).addBox(-2.5f, -7.5f, -2.5f, 5f, 15f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(7f, -20.5f, -14.5f, -0.559f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(218, 287).addBox(-4f, -5f, -1f, 9f, 1f, 2f)
                .texOffs(0, 293).addBox(-4f, 4f, -1f, 9f, 1f, 2f)
                .texOffs(140, 268).addBox(4f, -4f, -1f, 1f, 8f, 2f)
                .texOffs(146, 268).addBox(-4f, -4f, -1f, 1f, 8f, 2f)
                .texOffs(130, 287).addBox(-2f, -2f, -1f, 5f, 3f, 2f)
                .texOffs(38, 293).addBox(-3f, -1f, -1f, 7f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(184, 255).addBox(12f, -9f, -1f, 2f, 9f, 2f)
                .texOffs(192, 255).addBox(-14f, -9f, -1f, 2f, 9f, 2f)
                .texOffs(162, 287).addBox(-13f, -10f, -1f, 26f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -25f, -24f, -0.209f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(108, 169).addBox(13f, -34f, -20f, 1f, 9f, 18f)
                .texOffs(146, 169).addBox(-14f, -34f, -20f, 1f, 9f, 18f)
                .texOffs(0, 268).addBox(-10f, -33f, 1f, 20f, 9f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(200, 255).addBox(-12f, -9f, -0.5f, 24f, 9f, 1f),
                PartPose.offsetAndRotation(0f, -25f, -24f, -0.209f, 0f, 0f));
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

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }

    @Override
    public void setupAnim(DeliveryTruckEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
