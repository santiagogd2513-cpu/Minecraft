package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.RcCarEntity;
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
 * Carro RC Lamborgonzo Huracanito (V3.1.0). ARCHIVO GENERADO por tools/v310/rc_car.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 128x128. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Una unidad del modelo es 1/32 de bloque: el renderizador lo dibuja a escala 0,5 (el bloque usa el mismo diseño).
 * Piezas animadas: las 4 ruedas (giran) y las dos delanteras (doblan).
 */
public class RcCarModel extends EntityModel<RcCarEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "rc_car"), "main");

    private final ModelPart root;
    private final ModelPart wheelFl;
    private final ModelPart wheelFr;
    private final ModelPart wheelRl;
    private final ModelPart wheelRr;
    private final ModelPart steerL;
    private final ModelPart steerR;

    public RcCarModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.steerL = body.getChild("steer_l");
        this.steerR = body.getChild("steer_r");
        this.wheelFl = this.steerL.getChild("wheel_fl");
        this.wheelFr = this.steerR.getChild("wheel_fr");
        this.wheelRl = body.getChild("wheel_rl");
        this.wheelRr = body.getChild("wheel_rr");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6f, -2f, -14f, 12f, 1f, 28f)
                .texOffs(0, 76).addBox(-6f, -1f, -16f, 12f, 1f, 3f)
                .texOffs(50, 69).addBox(-5f, -1f, 12f, 10f, 1f, 4f)
                .texOffs(72, 76).addBox(1f, -4f, 16f, 2f, 2f, 1f)
                .texOffs(78, 76).addBox(-3f, -4f, 16f, 2f, 2f, 1f)
                .texOffs(58, 46).addBox(-6f, -5f, 12f, 12f, 3f, 4f)
                .texOffs(50, 29).addBox(-4.5f, -6f, 6f, 9f, 4f, 6f)
                .texOffs(0, 29).addBox(-6f, -6f, -7f, 12f, 4f, 13f)
                .texOffs(80, 29).addBox(-4.5f, -5f, -13f, 9f, 3f, 6f)
                .texOffs(20, 69).addBox(-6f, -4f, -16f, 12f, 3f, 3f)
                .texOffs(28, 55).addBox(4.5f, -6f, 6f, 3f, 1f, 6f)
                .texOffs(46, 55).addBox(4.5f, -6f, -13f, 3f, 1f, 6f)
                .texOffs(64, 55).addBox(-7.5f, -6f, 6f, 3f, 1f, 6f)
                .texOffs(82, 55).addBox(-7.5f, -6f, -13f, 3f, 1f, 6f)
                .texOffs(0, 46).addBox(-4f, -8f, -1f, 8f, 2f, 7f)
                .texOffs(30, 46).addBox(-3.5f, -9f, -1f, 7f, 1f, 7f)
                .texOffs(84, 76).addBox(4f, -8f, -3f, 2f, 1f, 1f)
                .texOffs(90, 76).addBox(-6f, -8f, -3f, 2f, 1f, 1f)
                .texOffs(30, 76).addBox(-4f, -7f, -4f, 8f, 1f, 3f)
                .texOffs(78, 69).addBox(-4f, -7f, 6f, 8f, 1f, 4f)
                .texOffs(52, 76).addBox(-4f, -8f, 6f, 8f, 1f, 2f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_rl = p_body.addOrReplaceChild("wheel_rl",
                CubeListBuilder.create(),
                PartPose.offset(6f, -2.5f, 9f));
        PartDefinition p_wheel_rl_t0 = p_wheel_rl.addOrReplaceChild("wheel_rl_t0",
                CubeListBuilder.create()
                .texOffs(100, 55).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rl_t1 = p_wheel_rl.addOrReplaceChild("wheel_rl_t1",
                CubeListBuilder.create()
                .texOffs(110, 55).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rl_t2 = p_wheel_rl.addOrReplaceChild("wheel_rl_t2",
                CubeListBuilder.create()
                .texOffs(0, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rl_t3 = p_wheel_rl.addOrReplaceChild("wheel_rl_t3",
                CubeListBuilder.create()
                .texOffs(10, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_l = p_body.addOrReplaceChild("steer_l",
                CubeListBuilder.create(),
                PartPose.offset(6f, -2.5f, -10f));
        PartDefinition p_wheel_fl = p_steer_l.addOrReplaceChild("wheel_fl",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t0 = p_wheel_fl.addOrReplaceChild("wheel_fl_t0",
                CubeListBuilder.create()
                .texOffs(20, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fl_t1 = p_wheel_fl.addOrReplaceChild("wheel_fl_t1",
                CubeListBuilder.create()
                .texOffs(30, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fl_t2 = p_wheel_fl.addOrReplaceChild("wheel_fl_t2",
                CubeListBuilder.create()
                .texOffs(40, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fl_t3 = p_wheel_fl.addOrReplaceChild("wheel_fl_t3",
                CubeListBuilder.create()
                .texOffs(50, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_rr = p_body.addOrReplaceChild("wheel_rr",
                CubeListBuilder.create(),
                PartPose.offset(-6f, -2.5f, 9f));
        PartDefinition p_wheel_rr_t0 = p_wheel_rr.addOrReplaceChild("wheel_rr_t0",
                CubeListBuilder.create()
                .texOffs(60, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_rr_t1 = p_wheel_rr.addOrReplaceChild("wheel_rr_t1",
                CubeListBuilder.create()
                .texOffs(70, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_rr_t2 = p_wheel_rr.addOrReplaceChild("wheel_rr_t2",
                CubeListBuilder.create()
                .texOffs(80, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_rr_t3 = p_wheel_rr.addOrReplaceChild("wheel_rr_t3",
                CubeListBuilder.create()
                .texOffs(90, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_steer_r = p_body.addOrReplaceChild("steer_r",
                CubeListBuilder.create(),
                PartPose.offset(-6f, -2.5f, -10f));
        PartDefinition p_wheel_fr = p_steer_r.addOrReplaceChild("wheel_fr",
                CubeListBuilder.create(),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t0 = p_wheel_fr.addOrReplaceChild("wheel_fr_t0",
                CubeListBuilder.create()
                .texOffs(100, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_fr_t1 = p_wheel_fr.addOrReplaceChild("wheel_fr_t1",
                CubeListBuilder.create()
                .texOffs(110, 62).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_fr_t2 = p_wheel_fr.addOrReplaceChild("wheel_fr_t2",
                CubeListBuilder.create()
                .texOffs(0, 69).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_fr_t3 = p_wheel_fr.addOrReplaceChild("wheel_fr_t3",
                CubeListBuilder.create()
                .texOffs(10, 69).addBox(-1.5f, -2.5f, -1f, 3f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_windshield = p_body.addOrReplaceChild("windshield",
                CubeListBuilder.create()
                .texOffs(90, 46).addBox(-4f, -0.5f, -3f, 8f, 1f, 6f),
                PartPose.offsetAndRotation(0f, -7.2f, -3f, 0.393f, 0f, 0f));
        PartDefinition p_engine_cover = p_body.addOrReplaceChild("engine_cover",
                CubeListBuilder.create()
                .texOffs(0, 55).addBox(-4f, -0.5f, -3f, 8f, 1f, 6f),
                PartPose.offsetAndRotation(0f, -7.2f, 9f, -0.393f, 0f, 0f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** wheelRot en radianes (las 4 ruedas), steerRot = giro de las ruedas delanteras en radianes (positivo = a la derecha). */
    public void pose(float wheelRot, float steerRot) {
        this.wheelFl.xRot = wheelRot;
        this.wheelFr.xRot = wheelRot;
        this.wheelRl.xRot = wheelRot;
        this.wheelRr.xRot = wheelRot;
        this.steerL.yRot = steerRot;
        this.steerR.yRot = steerRot;
    }

    @Override
    public void setupAnim(RcCarEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
