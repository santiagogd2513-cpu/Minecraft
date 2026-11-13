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
 * Atlas de textura de 256x320. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
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
    private final ModelPart police;
    private final ModelPart delivery;
    private final ModelPart construction;
    private final ModelPart fire;
    private final ModelPart bar;
    private final ModelPart barRed;
    private final ModelPart barBlue;
    private final ModelPart sirenBtn;
    private boolean barsOn;

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
        this.police = this.body.getChild("police");
        this.delivery = this.body.getChild("delivery");
        this.construction = this.body.getChild("construction");
        this.fire = this.body.getChild("fire");
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
                .texOffs(80, 90).addBox(-9f, -15f, -35f, 18f, 9f, 22f)
                .texOffs(0, 0).addBox(-9f, -10f, -13f, 18f, 4f, 47f)
                .texOffs(154, 235).addBox(5f, -9f, 21f, 4f, 3f, 10f)
                .texOffs(206, 274).addBox(6f, -9f, 31f, 2f, 2f, 6f)
                .texOffs(108, 153).addBox(-12f, -12f, 13f, 24f, 2f, 21f)
                .texOffs(160, 90).addBox(12f, -22f, 11f, 2f, 7f, 24f)
                .texOffs(0, 122).addBox(-14f, -22f, 11f, 2f, 7f, 24f)
                .texOffs(80, 251).addBox(12f, -15f, 11f, 2f, 8f, 4f)
                .texOffs(92, 251).addBox(-14f, -15f, 11f, 2f, 8f, 4f)
                .texOffs(104, 251).addBox(12f, -15f, 31f, 2f, 8f, 4f)
                .texOffs(116, 251).addBox(-14f, -15f, 31f, 2f, 8f, 4f)
                .texOffs(0, 153).addBox(11f, -23f, 11f, 3f, 1f, 24f)
                .texOffs(54, 153).addBox(-14f, -23f, 11f, 3f, 1f, 24f)
                .texOffs(112, 218).addBox(9f, -15f, 16f, 3f, 3f, 14f)
                .texOffs(146, 218).addBox(-12f, -15f, 16f, 3f, 3f, 14f)
                .texOffs(0, 251).addBox(-12f, -22f, 11f, 24f, 10f, 2f)
                .texOffs(180, 218).addBox(-12f, -22f, 33f, 24f, 14f, 2f)
                .texOffs(184, 284).addBox(-14f, -10f, 35f, 28f, 3f, 3f)
                .texOffs(0, 284).addBox(11f, -21f, 35f, 3f, 7f, 1f)
                .texOffs(8, 284).addBox(-14f, -21f, 35f, 3f, 7f, 1f)
                .texOffs(152, 122).addBox(-12f, -10f, -13f, 24f, 2f, 24f)
                .texOffs(0, 51).addBox(12f, -22f, -13f, 2f, 15f, 24f)
                .texOffs(52, 51).addBox(-14f, -22f, -13f, 2f, 15f, 24f)
                .texOffs(198, 153).addBox(14f, -9f, -11f, 2f, 1f, 20f)
                .texOffs(0, 178).addBox(-16f, -9f, -11f, 2f, 1f, 20f)
                .texOffs(110, 292).addBox(14f, -19f, -7f, 1f, 1f, 4f)
                .texOffs(120, 292).addBox(-15f, -19f, -7f, 1f, 1f, 4f)
                .texOffs(130, 292).addBox(14f, -19f, 3f, 1f, 1f, 4f)
                .texOffs(140, 292).addBox(-15f, -19f, 3f, 1f, 1f, 4f)
                .texOffs(74, 235).addBox(-12f, -22f, 9f, 24f, 12f, 2f)
                .texOffs(18, 235).addBox(-12f, -21f, -13f, 24f, 11f, 4f)
                .texOffs(126, 235).addBox(-2f, -14f, -7f, 4f, 4f, 10f)
                .texOffs(170, 292).addBox(-1f, -17f, -4f, 2f, 3f, 2f)
                .texOffs(134, 263).addBox(2.7f, -12f, -8.8f, 8f, 2f, 8f)
                .texOffs(194, 251).addBox(2.7f, -21f, -0.8f, 8f, 9f, 2f)
                .texOffs(46, 292).addBox(4.7f, -25f, -0.8f, 4f, 4f, 2f)
                .texOffs(166, 263).addBox(-10.7f, -12f, -8.8f, 8f, 2f, 8f)
                .texOffs(214, 251).addBox(-10.7f, -21f, -0.8f, 8f, 9f, 2f)
                .texOffs(58, 292).addBox(-8.7f, -25f, -0.8f, 4f, 4f, 2f)
                .texOffs(150, 274).addBox(-11f, -12f, 1f, 22f, 2f, 6f)
                .texOffs(18, 263).addBox(-11f, -20f, 7f, 22f, 8f, 2f)
                .texOffs(160, 292).addBox(5.7f, -16f, -12f, 2f, 2f, 3f)
                .texOffs(94, 298).addBox(1f, -20f, -9f, 2f, 2f, 1f)
                .texOffs(100, 298).addBox(-3f, -20f, -9f, 2f, 2f, 1f)
                .texOffs(52, 122).addBox(-14f, -22f, -35f, 28f, 7f, 22f)
                .texOffs(0, 218).addBox(-8f, -23f, -31f, 16f, 1f, 16f)
                .texOffs(52, 251).addBox(11f, -15f, -35f, 3f, 8f, 4f)
                .texOffs(66, 251).addBox(-14f, -15f, -35f, 3f, 8f, 4f)
                .texOffs(90, 284).addBox(-9f, -21f, -36f, 18f, 6f, 1f)
                .texOffs(70, 292).addBox(9f, -21f, -36f, 5f, 5f, 1f)
                .texOffs(82, 292).addBox(-14f, -21f, -36f, 5f, 5f, 1f)
                .texOffs(28, 284).addBox(-14f, -13f, -38f, 28f, 4f, 3f)
                .texOffs(0, 292).addBox(-10f, -9f, -37f, 20f, 3f, 3f)
                .texOffs(78, 298).addBox(5f, -9f, -38f, 3f, 2f, 1f)
                .texOffs(86, 298).addBox(-8f, -9f, -38f, 3f, 2f, 1f)
                .texOffs(44, 178).addBox(-14f, -32f, -7f, 28f, 2f, 18f)
                .texOffs(218, 263).addBox(12f, -30f, -1f, 2f, 8f, 2f)
                .texOffs(226, 263).addBox(-14f, -30f, -1f, 2f, 8f, 2f)
                .texOffs(234, 263).addBox(12f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(242, 263).addBox(-14f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(0, 274).addBox(10f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(8, 274).addBox(-12f, -30f, 9f, 2f, 8f, 2f)
                .texOffs(164, 301).addBox(14f, -25f, -11f, 2f, 1f, 1f)
                .texOffs(170, 301).addBox(-16f, -25f, -11f, 2f, 1f, 1f)
                .texOffs(144, 284).addBox(15f, -27f, -12f, 3f, 4f, 3f)
                .texOffs(156, 284).addBox(-18f, -27f, -12f, 3f, 4f, 3f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(12f, -7f, 23f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(136, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(156, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(176, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(196, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(12f, -7f, -23f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(216, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(236, 178).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(0, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(20, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-12f, -7f, 23f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(40, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(60, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(80, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(100, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-12f, -7f, -23f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(120, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(140, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(160, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(180, 199).addBox(-2.5f, -7f, -2.5f, 5f, 14f, 5f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(6.7f, -17.5f, -8.2f, -0.436f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(42, 298).addBox(-3f, -4f, -1f, 7f, 1f, 2f)
                .texOffs(60, 298).addBox(-3f, 3f, -1f, 7f, 1f, 2f)
                .texOffs(16, 284).addBox(3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(22, 284).addBox(-3f, -3f, -1f, 1f, 6f, 2f)
                .texOffs(150, 292).addBox(-1f, -2f, -1f, 3f, 3f, 2f)
                .texOffs(152, 301).addBox(-2f, -1f, -1f, 5f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(128, 251).addBox(12f, -10.2f, -1f, 2f, 10f, 2f)
                .texOffs(136, 251).addBox(-14f, -10.2f, -1f, 2f, 10f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -13f, -0.698f, 0f, 0f));
        PartDefinition p_siren_btn = p_body.addOrReplaceChild("siren_btn",
                CubeListBuilder.create()
                .texOffs(106, 298).addBox(-7f, -20f, -9f, 2f, 2f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar = p_body.addOrReplaceChild("bar",
                CubeListBuilder.create()
                .texOffs(16, 274).addBox(-10f, -33f, -4f, 20f, 1f, 8f)
                .texOffs(198, 263).addBox(-1f, -35f, -4f, 2f, 2f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_red = p_body.addOrReplaceChild("bar_red",
                CubeListBuilder.create()
                .texOffs(66, 263).addBox(1f, -35f, -4f, 9f, 2f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_blue = p_body.addOrReplaceChild("bar_blue",
                CubeListBuilder.create()
                .texOffs(100, 263).addBox(-10f, -35f, -4f, 9f, 2f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_police = p_body.addOrReplaceChild("police",
                CubeListBuilder.create()
                .texOffs(138, 274).addBox(8f, -21f, -39f, 2f, 8f, 1f)
                .texOffs(144, 274).addBox(-10f, -21f, -39f, 2f, 8f, 1f)
                .texOffs(194, 292).addBox(-10f, -21f, -39f, 20f, 2f, 1f)
                .texOffs(0, 298).addBox(-10f, -15f, -39f, 20f, 2f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_delivery = p_body.addOrReplaceChild("delivery",
                CubeListBuilder.create()
                .texOffs(130, 0).addBox(-13f, -34f, 12f, 26f, 22f, 25f)
                .texOffs(178, 292).addBox(10f, -18f, 37f, 3f, 3f, 1f)
                .texOffs(186, 292).addBox(-13f, -18f, 37f, 3f, 3f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_construction = p_body.addOrReplaceChild("construction",
                CubeListBuilder.create()
                .texOffs(94, 292).addBox(-2f, -33f, -2f, 4f, 1f, 4f)
                .texOffs(128, 284).addBox(-2f, -36f, -2f, 4f, 3f, 4f)
                .texOffs(114, 274).addBox(8f, -18f, 30f, 3f, 6f, 3f)
                .texOffs(126, 274).addBox(-11f, -18f, 30f, 3f, 6f, 3f)
                .texOffs(64, 218).addBox(-6f, -17f, 16f, 12f, 5f, 12f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_fire = p_body.addOrReplaceChild("fire",
                CubeListBuilder.create()
                .texOffs(0, 90).addBox(-11f, -26f, 14f, 22f, 14f, 18f)
                .texOffs(104, 51).addBox(10f, -33f, -7f, 1f, 1f, 37f)
                .texOffs(180, 51).addBox(-11f, -33f, -7f, 1f, 1f, 37f)
                .texOffs(168, 284).addBox(10f, -32f, 29f, 1f, 6f, 1f)
                .texOffs(172, 284).addBox(-11f, -32f, 29f, 1f, 6f, 1f)
                .texOffs(176, 284).addBox(10f, -32f, 14f, 1f, 6f, 1f)
                .texOffs(180, 284).addBox(-11f, -32f, 14f, 1f, 6f, 1f)
                .texOffs(112, 298).addBox(-9f, -33f, 27f, 18f, 1f, 1f)
                .texOffs(150, 298).addBox(-9f, -33f, 23f, 18f, 1f, 1f)
                .texOffs(188, 298).addBox(-9f, -33f, 19f, 18f, 1f, 1f)
                .texOffs(0, 301).addBox(-9f, -33f, 15f, 18f, 1f, 1f)
                .texOffs(38, 301).addBox(-9f, -33f, 11f, 18f, 1f, 1f)
                .texOffs(76, 301).addBox(-9f, -33f, 7f, 18f, 1f, 1f)
                .texOffs(114, 301).addBox(-9f, -33f, -6f, 18f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(182, 235).addBox(13f, -25f, -11f, 1f, 3f, 10f)
                .texOffs(204, 235).addBox(-14f, -25f, -11f, 1f, 3f, 10f)
                .texOffs(234, 251).addBox(13f, -28f, -9f, 1f, 3f, 8f)
                .texOffs(0, 263).addBox(-14f, -28f, -9f, 1f, 3f, 8f)
                .texOffs(222, 274).addBox(13f, -30f, -7f, 1f, 2f, 6f)
                .texOffs(236, 274).addBox(-14f, -30f, -7f, 1f, 2f, 6f)
                .texOffs(232, 218).addBox(13f, -30f, 1f, 1f, 8f, 8f)
                .texOffs(0, 235).addBox(-14f, -30f, 1f, 1f, 8f, 8f)
                .texOffs(72, 274).addBox(-10f, -30f, 10f, 20f, 8f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(144, 251).addBox(-12f, -10.2f, -0.5f, 24f, 10f, 1f),
                PartPose.offsetAndRotation(0f, -22f, -13f, -0.698f, 0f, 0f));
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

    /** V6.8.0: accesorios de la skin: 0 = ninguno, 1 = policía, 2 = reparto, 3 = construcción, 4 = bomberos. */
    public void setSkin(int skin) {
        this.police.visible = skin == 1;
        this.delivery.visible = skin == 2;
        this.construction.visible = skin == 3;
        this.fire.visible = skin == 4;
        this.barsOn = skin == 1 || skin == 4;
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
