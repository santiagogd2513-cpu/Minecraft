package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.MotorcycleEntity;
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
 * Moto de carreras (V2.0.0). ARCHIVO GENERADO por tools/v200/moto_gen.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 256x256. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Piezas animadas: rueda delantera y trasera (giran), dirección (gira con el manubrio) y pata de cabra (solo parada).
 */
public class MotorcycleModel extends EntityModel<MotorcycleEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "motorcycle"), "main");

    private final ModelPart root;
    private final ModelPart frontWheel;
    private final ModelPart rearWheel;
    private final ModelPart steer;
    private final ModelPart kickstand;

    public MotorcycleModel(ModelPart root) {
        this.root = root;
        ModelPart bike = root.getChild("bike");
        this.rearWheel = bike.getChild("rear_wheel");
        this.steer = bike.getChild("steer");
        this.frontWheel = this.steer.getChild("fork").getChild("front_wheel");
        this.kickstand = bike.getChild("kickstand");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bike = root.addOrReplaceChild("bike",
                CubeListBuilder.create()
                        .texOffs(188, 0).addBox(-2.5f, -11.1f, -5f, 5f, 5f, 8f)
                        .texOffs(64, 26).addBox(-2f, -13f, -4.9f, 4f, 2f, 5f)
                        .texOffs(38, 26).addBox(2.5f, -10.6f, -2.5f, 1f, 4f, 4f)
                        .texOffs(170, 26).addBox(-3.5f, -10.6f, -3f, 1f, 4f, 3f)
                        .texOffs(224, 26).addBox(2.4f, -7.7f, 3f, 1f, 3f, 3f)
                        .texOffs(214, 26).addBox(-2f, -10.7f, -6.9f, 4f, 5f, 1f)
                        .texOffs(24, 14).addBox(-3f, -4.8f, -7f, 6f, 2f, 9f)
                        .texOffs(20, 34).addBox(-2f, -5.6f, -10.3f, 4f, 2f, 3f)
                        .texOffs(0, 0).addBox(3.1f, -10.9f, -10f, 1f, 5f, 9f)
                        .texOffs(112, 34).addBox(3.7f, -12.9f, -13f, 3f, 1f, 3f)
                        .texOffs(136, 34).addBox(6.4f, -12.9f, -13f, 1f, 1f, 3f)
                        .texOffs(20, 0).addBox(-4.1f, -10.9f, -10f, 1f, 5f, 9f)
                        .texOffs(124, 34).addBox(-6.7f, -12.9f, -13f, 3f, 1f, 3f)
                        .texOffs(144, 34).addBox(-7.4f, -12.9f, -13f, 1f, 1f, 3f)
                        .texOffs(0, 39).addBox(3.2f, -19.9f, -9.9f, 1f, 2f, 1f)
                        .texOffs(202, 34).addBox(3.4f, -21.3f, -9.3f, 3f, 2f, 1f)
                        .texOffs(4, 39).addBox(-4.2f, -19.9f, -9.9f, 1f, 2f, 1f)
                        .texOffs(210, 34).addBox(-6.4f, -21.3f, -9.3f, 3f, 2f, 1f)
                        .texOffs(160, 0).addBox(-2.5f, -16.5f, -7.1f, 5f, 4f, 9f)
                        .texOffs(214, 14).addBox(-1.5f, -17.5f, -6.5f, 3f, 1f, 7f)
                        .texOffs(14, 26).addBox(2.3f, -15.5f, -4.5f, 1f, 3f, 5f)
                        .texOffs(26, 26).addBox(-3.3f, -15.5f, -4.5f, 1f, 3f, 5f)
                        .texOffs(84, 14).addBox(-2.5f, -11.9f, -1.7f, 5f, 2f, 9f)
                        .texOffs(100, 26).addBox(-1.5f, -12.1f, 8f, 3f, 1f, 6f)
                        .texOffs(168, 34).addBox(-0.5f, -11.2f, 12.6f, 1f, 2f, 2f)
                        .texOffs(218, 34).addBox(-1.5f, -10.4f, 14.3f, 3f, 2f, 1f)
                        .texOffs(234, 14).addBox(3.1f, -7f, 4.5f, 1f, 2f, 6f)
                        .texOffs(232, 26).addBox(3.1f, -6.9f, 0.3f, 1f, 3f, 3f)
                        .texOffs(38, 39).addBox(2.7f, -4.9f, 1.3f, 3f, 1f, 1f)
                        .texOffs(0, 26).addBox(-4.1f, -7f, 4.5f, 1f, 2f, 6f)
                        .texOffs(240, 26).addBox(-4.1f, -6.9f, 0.3f, 1f, 3f, 3f)
                        .texOffs(46, 39).addBox(-5.7f, -4.9f, 1.3f, 3f, 1f, 1f)
                        .texOffs(178, 26).addBox(-1f, -10.5f, 5f, 2f, 5f, 2f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        PartDefinition p_shell = bike.addOrReplaceChild("shell",
                CubeListBuilder.create()
                .texOffs(54, 14).addBox(-3f, -0.4f, -9f, 6f, 2f, 9f)
                .texOffs(214, 0).addBox(3f, 0.6f, -9f, 1f, 4f, 9f)
                .texOffs(234, 0).addBox(-4f, 0.6f, -9f, 1f, 4f, 9f)
                .texOffs(174, 34).addBox(-3f, 2.4f, -9.5f, 6f, 1f, 2f)
                .texOffs(138, 14).addBox(-3f, 4.1f, -9f, 6f, 1f, 9f)
                .texOffs(234, 34).addBox(0.6f, 1.4f, -9.8f, 2f, 2f, 1f)
                .texOffs(240, 34).addBox(-2.6f, 1.4f, -9.8f, 2f, 2f, 1f)
                .texOffs(252, 34).addBox(-0.5f, 1.4f, -9.9f, 1f, 2f, 1f)
                .texOffs(168, 14).addBox(-2f, 1.1f, -8.5f, 4f, 3f, 7f),
                PartPose.offsetAndRotation(0f, -18f, -8.6f, 0.262f, 0f, 0f));
        PartDefinition p_windscreen = bike.addOrReplaceChild("windscreen",
                CubeListBuilder.create()
                .texOffs(202, 26).addBox(-2.5f, -4.9f, -0.5f, 5f, 5f, 1f),
                PartPose.offsetAndRotation(0f, -18.4f, -9.8f, -0.593f, 0f, 0f));
        PartDefinition p_tail = bike.addOrReplaceChild("tail",
                CubeListBuilder.create()
                .texOffs(112, 14).addBox(-2.5f, -2.9f, -1f, 5f, 3f, 8f)
                .texOffs(8, 39).addBox(-2f, -2.1f, 7f, 4f, 1f, 1f)
                .texOffs(82, 26).addBox(-1.5f, -0.1f, -0.5f, 3f, 1f, 6f),
                PartPose.offsetAndRotation(0f, -11f, 6f, 0.227f, 0f, 0f));
        PartDefinition p_exhaust = bike.addOrReplaceChild("exhaust",
                CubeListBuilder.create()
                .texOffs(0, 14).addBox(-1f, -1f, 0f, 2f, 2f, 10f)
                .texOffs(246, 34).addBox(-1f, -1.2f, 9.8f, 2f, 2f, 1f)
                .texOffs(68, 34).addBox(-1f, 0.3f, -1.2f, 2f, 3f, 2f),
                PartPose.offsetAndRotation(-3.8f, -6.4f, 1f, 0.175f, 0f, 0f));
        PartDefinition p_kickstand = bike.addOrReplaceChild("kickstand",
                CubeListBuilder.create()
                .texOffs(248, 26).addBox(-0.5f, 0f, -0.5f, 1f, 5f, 1f)
                .texOffs(226, 34).addBox(-1f, 4.8f, -1f, 2f, 1f, 2f),
                PartPose.offsetAndRotation(3.6f, -5f, 1f, 0f, 0f, -0.349f));
        PartDefinition p_rear_wheel = bike.addOrReplaceChild("rear_wheel",
                CubeListBuilder.create()
                .texOffs(82, 34).addBox(-3f, -1f, -1f, 6f, 2f, 2f),
                PartPose.offsetAndRotation(0f, -5.5f, 10f, 0.4f, 0f, 0f));
        PartDefinition p_rear_wheel_t0 = p_rear_wheel.addOrReplaceChild("rear_wheel_t0",
                CubeListBuilder.create()
                .texOffs(40, 0).addBox(-2f, -5f, -2f, 4f, 10f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_rear_wheel_t1 = p_rear_wheel.addOrReplaceChild("rear_wheel_t1",
                CubeListBuilder.create()
                .texOffs(56, 0).addBox(-2f, -5f, -2f, 4f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_rear_wheel_t2 = p_rear_wheel.addOrReplaceChild("rear_wheel_t2",
                CubeListBuilder.create()
                .texOffs(72, 0).addBox(-2f, -5f, -2f, 4f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_rear_wheel_t3 = p_rear_wheel.addOrReplaceChild("rear_wheel_t3",
                CubeListBuilder.create()
                .texOffs(88, 0).addBox(-2f, -5f, -2f, 4f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_rear_wheel_d0 = p_rear_wheel.addOrReplaceChild("rear_wheel_d0",
                CubeListBuilder.create()
                .texOffs(146, 26).addBox(-2f, -2.5f, -1f, 4f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_rear_wheel_d1 = p_rear_wheel.addOrReplaceChild("rear_wheel_d1",
                CubeListBuilder.create()
                .texOffs(158, 26).addBox(-2f, -2.5f, -1f, 4f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_steer = bike.addOrReplaceChild("steer",
                CubeListBuilder.create()
                .texOffs(0, 34).addBox(-3.5f, -0.6f, -1.7f, 7f, 2f, 3f)
                .texOffs(34, 34).addBox(-2f, -2.4f, -0.3f, 4f, 2f, 3f)
                .texOffs(18, 39).addBox(1.9f, 0.7f, -0.1f, 4f, 1f, 1f)
                .texOffs(54, 39).addBox(5.4f, 0.9f, 0.1f, 2f, 1f, 1f)
                .texOffs(152, 34).addBox(5.9f, 0.1f, -3.3f, 1f, 1f, 3f)
                .texOffs(28, 39).addBox(-5.9f, 0.7f, -0.1f, 4f, 1f, 1f)
                .texOffs(60, 39).addBox(-7.4f, 0.9f, 0.1f, 2f, 1f, 1f)
                .texOffs(160, 34).addBox(-6.9f, 0.1f, -3.3f, 1f, 1f, 3f),
                PartPose.offset(0f, -16.5f, -7f));
        PartDefinition p_fork = p_steer.addOrReplaceChild("fork",
                CubeListBuilder.create()
                .texOffs(48, 26).addBox(2.3f, 1f, -1f, 2f, 6f, 2f)
                .texOffs(186, 26).addBox(2.3f, 6.5f, -1f, 2f, 5f, 2f)
                .texOffs(48, 34).addBox(2.3f, 11f, -1.5f, 2f, 2f, 3f)
                .texOffs(56, 26).addBox(-4.3f, 1f, -1f, 2f, 6f, 2f)
                .texOffs(194, 26).addBox(-4.3f, 6.5f, -1f, 2f, 5f, 2f)
                .texOffs(58, 34).addBox(-4.3f, 11f, -1.5f, 2f, 2f, 3f)
                .texOffs(76, 34).addBox(2.5f, 8.9f, 1f, 1f, 3f, 2f)
                .texOffs(190, 14).addBox(-2f, 5.1f, -4.5f, 4f, 1f, 8f)
                .texOffs(190, 34).addBox(-2f, 5.7f, -6f, 4f, 1f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, -0.419f, 0f, 0f));
        PartDefinition p_front_wheel = p_fork.addOrReplaceChild("front_wheel",
                CubeListBuilder.create()
                .texOffs(98, 34).addBox(-2.5f, -1f, -1f, 5f, 2f, 2f),
                PartPose.offsetAndRotation(0f, 12f, 0f, 0.4f, 0f, 0f));
        PartDefinition p_front_wheel_t0 = p_front_wheel.addOrReplaceChild("front_wheel_t0",
                CubeListBuilder.create()
                .texOffs(104, 0).addBox(-1.5f, -5f, -2f, 3f, 10f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_front_wheel_t1 = p_front_wheel.addOrReplaceChild("front_wheel_t1",
                CubeListBuilder.create()
                .texOffs(118, 0).addBox(-1.5f, -5f, -2f, 3f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_front_wheel_t2 = p_front_wheel.addOrReplaceChild("front_wheel_t2",
                CubeListBuilder.create()
                .texOffs(132, 0).addBox(-1.5f, -5f, -2f, 3f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_front_wheel_t3 = p_front_wheel.addOrReplaceChild("front_wheel_t3",
                CubeListBuilder.create()
                .texOffs(146, 0).addBox(-1.5f, -5f, -2f, 3f, 10f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_front_wheel_d0 = p_front_wheel.addOrReplaceChild("front_wheel_d0",
                CubeListBuilder.create()
                .texOffs(118, 26).addBox(-2.5f, -2.5f, -1f, 5f, 5f, 2f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_front_wheel_d1 = p_front_wheel.addOrReplaceChild("front_wheel_d1",
                CubeListBuilder.create()
                .texOffs(132, 26).addBox(-2.5f, -2.5f, -1f, 5f, 5f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** wheelRot en radianes (las dos ruedas), steerRot = giro del manubrio en radianes, parked = pata de cabra puesta. */
    public void pose(float wheelRot, float steerRot, boolean parked) {
        this.frontWheel.xRot = wheelRot;
        this.rearWheel.xRot = wheelRot;
        this.steer.yRot = steerRot;
        this.kickstand.visible = parked;
    }

    @Override
    public void setupAnim(MotorcycleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
