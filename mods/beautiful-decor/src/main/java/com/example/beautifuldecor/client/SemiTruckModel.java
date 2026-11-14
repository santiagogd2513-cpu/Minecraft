package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.SemiTruckEntity;
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
 * Tractomula KENWORT K880 (V7.9.0). ARCHIVO GENERADO por tools/v790/semi_truck.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 384x384. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios). Piezas animadas: las 6 ruedas (giran), las dos delanteras (doblan) y el volante.
 */
public class SemiTruckModel extends EntityModel<SemiTruckEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "semi_truck"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart[] wheels;
    private final ModelPart steerL;
    private final ModelPart steerR;
    private final ModelPart steeringWheel;

    public SemiTruckModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.steerL = this.body.getChild("steer_l");
        this.steerR = this.body.getChild("steer_r");
        this.wheels = new ModelPart[] {
                this.steerL.getChild("wheel_fl"), this.steerR.getChild("wheel_fr"),
                this.body.getChild("wheel_r0l"), this.body.getChild("wheel_r0r"),
                this.body.getChild("wheel_r1l"), this.body.getChild("wheel_r1r")};
        this.steeringWheel = this.body.getChild("steering_tilt").getChild("steering_wheel");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 59).addBox(-10f, -13f, -54f, 20f, 4f, 46f)
                .texOffs(0, 109).addBox(-10f, -13f, -8f, 20f, 4f, 34f)
                .texOffs(108, 109).addBox(-10f, -13f, 26f, 20f, 4f, 32f)
                .texOffs(148, 59).addBox(-12f, -19f, -52f, 24f, 7f, 32f)
                .texOffs(30, 213).addBox(20f, -21f, 4f, 6f, 12f, 16f)
                .texOffs(74, 213).addBox(-26f, -21f, 4f, 6f, 12f, 16f)
                .texOffs(88, 341).addBox(14f, -9f, 61f, 8f, 6f, 1f)
                .texOffs(106, 341).addBox(-22f, -9f, 61f, 8f, 6f, 1f)
                .texOffs(88, 242).addBox(-14f, -15f, 26f, 28f, 2f, 20f)
                .texOffs(222, 328).addBox(-2f, -16f, 34f, 4f, 1f, 10f)
                .texOffs(184, 242).addBox(13f, -19f, -54f, 10f, 2f, 20f)
                .texOffs(244, 242).addBox(-23f, -19f, -54f, 10f, 2f, 20f)
                .texOffs(0, 147).addBox(13f, -19f, 22f, 10f, 2f, 32f)
                .texOffs(84, 147).addBox(-23f, -19f, 22f, 10f, 2f, 32f)
                .texOffs(140, 181).addBox(-19f, -15f, -18f, 38f, 2f, 28f)
                .texOffs(120, 0).addBox(18f, -46f, -16f, 2f, 31f, 24f)
                .texOffs(172, 0).addBox(-20f, -46f, -16f, 2f, 31f, 24f)
                .texOffs(296, 287).addBox(20f, -11f, -14f, 4f, 1f, 18f)
                .texOffs(340, 287).addBox(-24f, -11f, -14f, 4f, 1f, 18f)
                .texOffs(0, 309).addBox(20f, -13f, -14f, 4f, 1f, 18f)
                .texOffs(44, 309).addBox(-24f, -13f, -14f, 4f, 1f, 18f)
                .texOffs(212, 341).addBox(20f, -34f, -14f, 1f, 1f, 5f)
                .texOffs(224, 341).addBox(-21f, -34f, -14f, 1f, 1f, 5f)
                .texOffs(168, 147).addBox(-19f, -46f, 8f, 38f, 31f, 2f)
                .texOffs(250, 309).addBox(-14f, -32f, -20f, 28f, 10f, 5f)
                .texOffs(118, 213).addBox(-3f, -22f, -14f, 6f, 7f, 18f)
                .texOffs(80, 341).addBox(-1f, -28f, -10f, 2f, 6f, 2f)
                .texOffs(166, 213).addBox(-17f, -21f, 10f, 34f, 6f, 18f)
                .texOffs(260, 59).addBox(-19f, -52f, 28f, 38f, 37f, 2f)
                .texOffs(130, 328).addBox(3f, -17f, -9f, 10f, 2f, 10f)
                .texOffs(112, 309).addBox(3f, -31f, 1f, 10f, 14f, 2f)
                .texOffs(124, 341).addBox(5f, -36f, 1f, 6f, 5f, 2f)
                .texOffs(170, 328).addBox(-13f, -17f, -9f, 10f, 2f, 10f)
                .texOffs(136, 309).addBox(-13f, -31f, 1f, 10f, 14f, 2f)
                .texOffs(140, 341).addBox(-11f, -36f, 1f, 6f, 5f, 2f)
                .texOffs(68, 341).addBox(7f, -27f, -16f, 2f, 5f, 4f)
                .texOffs(128, 351).addBox(1f, -30f, -15f, 2f, 2f, 1f)
                .texOffs(134, 351).addBox(-3f, -30f, -15f, 2f, 2f, 1f)
                .texOffs(140, 351).addBox(-9f, -30f, -15f, 2f, 2f, 1f)
                .texOffs(146, 351).addBox(-6f, -30f, -15f, 2f, 2f, 1f)
                .texOffs(224, 0).addBox(-15f, -37f, -52f, 30f, 18f, 34f)
                .texOffs(272, 181).addBox(-6f, -39f, -50f, 12f, 2f, 28f)
                .texOffs(0, 242).addBox(-20f, -34f, -20f, 40f, 19f, 4f)
                .texOffs(304, 242).addBox(-12f, -37f, -54f, 24f, 20f, 2f)
                .texOffs(236, 341).addBox(-13f, -39f, -55f, 26f, 2f, 3f)
                .texOffs(316, 309).addBox(15f, -30f, -52f, 6f, 8f, 6f)
                .texOffs(340, 309).addBox(-21f, -30f, -52f, 6f, 8f, 6f)
                .texOffs(0, 328).addBox(-21f, -16f, -60f, 42f, 7f, 6f)
                .texOffs(0, 341).addBox(-14f, -9f, -56f, 28f, 4f, 6f)
                .texOffs(330, 341).addBox(8f, -13f, -61f, 4f, 3f, 1f)
                .texOffs(340, 341).addBox(-12f, -13f, -61f, 4f, 3f, 1f)
                .texOffs(294, 341).addBox(-5f, -14f, -61f, 10f, 4f, 1f)
                .texOffs(0, 181).addBox(-20f, -48f, -20f, 40f, 2f, 30f)
                .texOffs(250, 328).addBox(-20f, -46f, -26f, 40f, 2f, 8f)
                .texOffs(0, 0).addBox(-20f, -54f, 10f, 40f, 39f, 20f)
                .texOffs(88, 309).addBox(18f, -46f, -20f, 2f, 14f, 4f)
                .texOffs(100, 309).addBox(-20f, -46f, -20f, 2f, 14f, 4f)
                .texOffs(304, 109).addBox(18f, -46f, 6f, 2f, 31f, 4f)
                .texOffs(316, 109).addBox(-20f, -46f, 6f, 2f, 31f, 4f)
                .texOffs(202, 351).addBox(20f, -37f, -15f, 4f, 1f, 1f)
                .texOffs(212, 351).addBox(-24f, -37f, -15f, 4f, 1f, 1f)
                .texOffs(264, 287).addBox(24f, -40f, -17f, 2f, 14f, 6f)
                .texOffs(280, 287).addBox(-26f, -40f, -17f, 2f, 14f, 6f)
                .texOffs(132, 59).addBox(21f, -62f, 10f, 2f, 48f, 2f)
                .texOffs(140, 59).addBox(-23f, -62f, 10f, 2f, 48f, 2f)
                .texOffs(180, 341).addBox(21f, -64f, 9f, 4f, 2f, 4f)
                .texOffs(196, 341).addBox(-25f, -64f, 9f, 4f, 2f, 4f)
                .texOffs(152, 351).addBox(-13f, -50f, -17f, 2f, 2f, 1f)
                .texOffs(158, 351).addBox(-7f, -50f, -17f, 2f, 2f, 1f)
                .texOffs(164, 351).addBox(-1f, -50f, -17f, 2f, 2f, 1f)
                .texOffs(170, 351).addBox(5f, -50f, -17f, 2f, 2f, 1f)
                .texOffs(176, 351).addBox(11f, -50f, -17f, 2f, 2f, 1f)
                .texOffs(156, 341).addBox(12f, -18f, 61f, 5f, 6f, 1f)
                .texOffs(168, 341).addBox(-17f, -18f, 61f, 5f, 6f, 1f)
                .texOffs(352, 181).addBox(20f, -51f, 13f, 1f, 15f, 14f)
                .texOffs(0, 213).addBox(-21f, -51f, 13f, 1f, 15f, 14f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(18f, -8f, -44f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(56, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(82, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(108, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(134, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_r0l = p_body.addOrReplaceChild("wheel_r0l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 30f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_r0l_t0 = p_wheel_r0l.addOrReplaceChild("wheel_r0l_t0",
                CubeListBuilder.create()
                .texOffs(356, 242).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_r0l_t1 = p_wheel_r0l.addOrReplaceChild("wheel_r0l_t1",
                CubeListBuilder.create()
                .texOffs(0, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_r0l_t2 = p_wheel_r0l.addOrReplaceChild("wheel_r0l_t2",
                CubeListBuilder.create()
                .texOffs(28, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_r0l_t3 = p_wheel_r0l.addOrReplaceChild("wheel_r0l_t3",
                CubeListBuilder.create()
                .texOffs(56, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_r1l = p_body.addOrReplaceChild("wheel_r1l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 46f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_r1l_t0 = p_wheel_r1l.addOrReplaceChild("wheel_r1l_t0",
                CubeListBuilder.create()
                .texOffs(84, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_r1l_t1 = p_wheel_r1l.addOrReplaceChild("wheel_r1l_t1",
                CubeListBuilder.create()
                .texOffs(112, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_r1l_t2 = p_wheel_r1l.addOrReplaceChild("wheel_r1l_t2",
                CubeListBuilder.create()
                .texOffs(140, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_r1l_t3 = p_wheel_r1l.addOrReplaceChild("wheel_r1l_t3",
                CubeListBuilder.create()
                .texOffs(168, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-18f, -8f, -44f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(160, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(186, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(212, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(238, 287).addBox(-3.5f, -8f, -3f, 7f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_r0r = p_body.addOrReplaceChild("wheel_r0r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 30f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_r0r_t0 = p_wheel_r0r.addOrReplaceChild("wheel_r0r_t0",
                CubeListBuilder.create()
                .texOffs(196, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_r0r_t1 = p_wheel_r0r.addOrReplaceChild("wheel_r0r_t1",
                CubeListBuilder.create()
                .texOffs(224, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_r0r_t2 = p_wheel_r0r.addOrReplaceChild("wheel_r0r_t2",
                CubeListBuilder.create()
                .texOffs(252, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_r0r_t3 = p_wheel_r0r.addOrReplaceChild("wheel_r0r_t3",
                CubeListBuilder.create()
                .texOffs(280, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_r1r = p_body.addOrReplaceChild("wheel_r1r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 46f, 0.5f, 0f, 0f));
        PartDefinition p_wheel_r1r_t0 = p_wheel_r1r.addOrReplaceChild("wheel_r1r_t0",
                CubeListBuilder.create()
                .texOffs(308, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_r1r_t1 = p_wheel_r1r.addOrReplaceChild("wheel_r1r_t1",
                CubeListBuilder.create()
                .texOffs(336, 265).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_r1r_t2 = p_wheel_r1r.addOrReplaceChild("wheel_r1r_t2",
                CubeListBuilder.create()
                .texOffs(0, 287).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_r1r_t3 = p_wheel_r1r.addOrReplaceChild("wheel_r1r_t3",
                CubeListBuilder.create()
                .texOffs(28, 287).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steering_tilt = p_body.addOrReplaceChild("steering_tilt",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(8f, -28f, -12f, -0.628f, 0f, 0f));
        PartDefinition p_steering_wheel = p_steering_tilt.addOrReplaceChild("steering_wheel",
                CubeListBuilder.create()
                .texOffs(76, 351).addBox(-5f, -6f, -1f, 11f, 1f, 2f)
                .texOffs(102, 351).addBox(-5f, 5f, -1f, 11f, 1f, 2f)
                .texOffs(210, 328).addBox(5f, -5f, -1f, 1f, 10f, 2f)
                .texOffs(216, 328).addBox(-5f, -5f, -1f, 1f, 10f, 2f)
                .texOffs(316, 341).addBox(-2f, -2f, -1f, 5f, 3f, 2f)
                .texOffs(182, 351).addBox(-4f, -1f, -1f, 9f, 1f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(160, 309).addBox(18f, -14f, -1f, 2f, 14f, 2f)
                .texOffs(168, 309).addBox(-20f, -14f, -1f, 2f, 14f, 2f)
                .texOffs(0, 351).addBox(-18f, -15f, -1f, 36f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -32f, -20f, -0.14f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(212, 109).addBox(19f, -46f, -16f, 1f, 13f, 22f)
                .texOffs(258, 109).addBox(-20f, -46f, -16f, 1f, 13f, 22f)
                .texOffs(270, 213).addBox(20f, -50f, 14f, 1f, 12f, 12f)
                .texOffs(296, 213).addBox(-21f, -50f, 14f, 1f, 12f, 12f)
                .texOffs(96, 328).addBox(-8f, -40f, 9f, 16f, 12f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(176, 309).addBox(-18f, -14f, -0.5f, 36f, 14f, 1f),
                PartPose.offsetAndRotation(0f, -32f, -20f, -0.14f, 0f, 0f));
        return LayerDefinition.create(mesh, 384, 384);
    }

    /** wheelRot en radianes (las 6 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = wheelRot;
        }
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
    public void setupAnim(SemiTruckEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
