package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.BicycleEntity;
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
 * Bicicleta (V2.1.0). ARCHIVO GENERADO por tools/v210/bike.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 128x128. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Piezas animadas: las dos ruedas (giran), el pedalier (gira más despacio), la dirección (gira con el manubrio)
 * y la pata de apoyo (solo cuando está parada).
 */
public class BicycleModel extends EntityModel<BicycleEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "bicycle"), "main");

    private final ModelPart root;
    private final ModelPart rearWheel;
    private final ModelPart frontWheel;
    private final ModelPart crank;
    private final ModelPart steer;
    private final ModelPart kickstand;

    public BicycleModel(ModelPart root) {
        this.root = root;
        ModelPart bike = root.getChild("bike");
        this.rearWheel = bike.getChild("rear_wheel");
        this.crank = bike.getChild("crank");
        this.steer = bike.getChild("steer");
        this.frontWheel = this.steer.getChild("fork").getChild("front_wheel");
        this.kickstand = bike.getChild("kickstand");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_bike = root.addOrReplaceChild("bike",
                CubeListBuilder.create()
                .texOffs(56, 35).addBox(-1.5f, -7.8f, -0.5f, 3f, 2f, 2f)
                .texOffs(110, 35).addBox(-0.5f, -15.6f, 2.3f, 1f, 3f, 1f)
                .texOffs(98, 25).addBox(-1.5f, -15.7f, -2f, 3f, 1f, 6f)
                .texOffs(66, 35).addBox(-1f, -15.7f, -4.5f, 2f, 1f, 3f)
                .texOffs(76, 35).addBox(-1f, -15f, 0.3f, 2f, 1f, 3f)
                .texOffs(0, 15).addBox(-1f, -12.1f, 4f, 2f, 1f, 9f)
                .texOffs(34, 41).addBox(-1f, -11.1f, 12.7f, 2f, 1f, 2f)
                .texOffs(78, 25).addBox(-2f, -13.4f, 6.7f, 4f, 1f, 6f)
                .texOffs(8, 35).addBox(0.9f, -11.7f, 8f, 1f, 5f, 1f)
                .texOffs(12, 35).addBox(-1.9f, -11.7f, 8f, 1f, 5f, 1f)
                .texOffs(66, 41).addBox(-1f, -12f, 13.6f, 2f, 2f, 1f)
                .texOffs(84, 15).addBox(-2.7f, -8.55f, 0f, 1f, 1f, 9f)
                .texOffs(104, 15).addBox(-2.7f, -4.75f, 0f, 1f, 1f, 9f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_seat_tube = p_bike.addOrReplaceChild("seat_tube",
                CubeListBuilder.create()
                .texOffs(4, 25).addBox(-1f, -1f, -7f, 2f, 2f, 7f),
                PartPose.offsetAndRotation(0f, -6.8f, 0.5f, -1.902f, 0f, 0f));
        PartDefinition p_top_tube = p_bike.addOrReplaceChild("top_tube",
                CubeListBuilder.create()
                .texOffs(80, 0).addBox(-1f, -1f, -9f, 2f, 2f, 9f),
                PartPose.offsetAndRotation(0f, -13.5f, 2.7f, -0.051f, 0f, 0f));
        PartDefinition p_down_tube = p_bike.addOrReplaceChild("down_tube",
                CubeListBuilder.create()
                .texOffs(102, 0).addBox(-1f, -1f, -9f, 2f, 2f, 9f),
                PartPose.offsetAndRotation(0f, -6.8f, 0.5f, -0.583f, 0f, 0f));
        PartDefinition p_chain_stay_l = p_bike.addOrReplaceChild("chain_stay_l",
                CubeListBuilder.create()
                .texOffs(22, 25).addBox(-0.5f, -0.5f, -8f, 1f, 1f, 8f),
                PartPose.offsetAndRotation(1.5f, -6.8f, 0.5f, 2.981f, 0f, 0f));
        PartDefinition p_seat_stay_l = p_bike.addOrReplaceChild("seat_stay_l",
                CubeListBuilder.create()
                .texOffs(44, 15).addBox(-0.5f, -0.5f, -9f, 1f, 1f, 9f),
                PartPose.offsetAndRotation(1.5f, -12.8f, 3f, 2.216f, 0f, 0f));
        PartDefinition p_chain_stay_r = p_bike.addOrReplaceChild("chain_stay_r",
                CubeListBuilder.create()
                .texOffs(40, 25).addBox(-0.5f, -0.5f, -8f, 1f, 1f, 8f),
                PartPose.offsetAndRotation(-1.5f, -6.8f, 0.5f, 2.981f, 0f, 0f));
        PartDefinition p_seat_stay_r = p_bike.addOrReplaceChild("seat_stay_r",
                CubeListBuilder.create()
                .texOffs(64, 15).addBox(-0.5f, -0.5f, -9f, 1f, 1f, 9f),
                PartPose.offsetAndRotation(-1.5f, -12.8f, 3f, 2.216f, 0f, 0f));
        PartDefinition p_head_tube = p_bike.addOrReplaceChild("head_tube",
                CubeListBuilder.create()
                .texOffs(116, 25).addBox(-1f, -1f, -4f, 2f, 2f, 4f),
                PartPose.offsetAndRotation(0f, -14.728f, -6.009f, 1.307f, 0f, 0f));
        PartDefinition p_rear_wheel = p_bike.addOrReplaceChild("rear_wheel",
                CubeListBuilder.create()
                .texOffs(28, 35).addBox(-2.5f, -1f, -1f, 5f, 2f, 2f)
                .texOffs(0, 35).addBox(-2.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offsetAndRotation(0f, -5.5f, 8.5f, 0.5f, 0f, 0f));
        PartDefinition p_rear_wheel_t0 = p_rear_wheel.addOrReplaceChild("rear_wheel_t0",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_rear_wheel_t1 = p_rear_wheel.addOrReplaceChild("rear_wheel_t1",
                CubeListBuilder.create()
                .texOffs(10, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_rear_wheel_t2 = p_rear_wheel.addOrReplaceChild("rear_wheel_t2",
                CubeListBuilder.create()
                .texOffs(20, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_rear_wheel_t3 = p_rear_wheel.addOrReplaceChild("rear_wheel_t3",
                CubeListBuilder.create()
                .texOffs(30, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_crank = p_bike.addOrReplaceChild("crank",
                CubeListBuilder.create()
                .texOffs(58, 25).addBox(-2.7f, -2f, -2f, 1f, 4f, 4f)
                .texOffs(0, 44).addBox(-3.5f, -0.5f, -0.5f, 7f, 1f, 1f)
                .texOffs(20, 35).addBox(2.2f, -0.4f, -0.5f, 1f, 4f, 1f)
                .texOffs(24, 35).addBox(-3.7f, -3.6f, -0.5f, 1f, 4f, 1f)
                .texOffs(14, 41).addBox(2.2f, 2.8f, -1f, 3f, 1f, 2f)
                .texOffs(24, 41).addBox(-5.7f, -3.8f, -1f, 3f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -6.8f, 0.5f, 0.225f, 0f, 0f));
        PartDefinition p_crank_45 = p_crank.addOrReplaceChild("crank_45",
                CubeListBuilder.create()
                .texOffs(68, 25).addBox(-2.7f, -2f, -2f, 1f, 4f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_kickstand = p_bike.addOrReplaceChild("kickstand",
                CubeListBuilder.create()
                .texOffs(16, 35).addBox(-0.5f, -0.1f, -0.5f, 1f, 5f, 1f)
                .texOffs(72, 41).addBox(-0.5f, 4.7f, -1.4f, 1f, 1f, 2f),
                PartPose.offsetAndRotation(1.7f, -5.4f, 3.6f, 0f, 0f, -0.244f));
        PartDefinition p_steer = p_bike.addOrReplaceChild("steer",
                CubeListBuilder.create()
                .texOffs(114, 35).addBox(-0.5f, -2.9f, -0.2f, 1f, 3f, 1f)
                .texOffs(92, 41).addBox(-5f, -3.3f, 0.5f, 10f, 1f, 1f)
                .texOffs(84, 41).addBox(2.9f, -3.1f, 0.5f, 1f, 2f, 1f)
                .texOffs(88, 41).addBox(-3.9f, -3.1f, 0.5f, 1f, 2f, 1f)
                .texOffs(16, 44).addBox(4.6f, -3.3f, 0.5f, 2f, 1f, 1f)
                .texOffs(86, 35).addBox(3.7f, -2.6f, -2.2f, 1f, 1f, 3f)
                .texOffs(22, 44).addBox(-6.6f, -3.3f, 0.5f, 2f, 1f, 1f)
                .texOffs(94, 35).addBox(-4.7f, -2.6f, -2.2f, 1f, 1f, 3f)
                .texOffs(42, 41).addBox(0.6f, -4f, 0f, 2f, 1f, 2f)
                .texOffs(50, 41).addBox(0.6f, -4.9f, 0f, 2f, 1f, 2f)
                .texOffs(78, 41).addBox(-0.5f, -1.5f, -2f, 1f, 1f, 2f)
                .texOffs(102, 35).addBox(-1f, -2.2f, -3.6f, 2f, 2f, 2f),
                PartPose.offset(0f, -15.5f, -5.8f));
        PartDefinition p_fork = p_steer.addOrReplaceChild("fork",
                CubeListBuilder.create()
                .texOffs(0, 41).addBox(-2.5f, 0.3f, -1f, 5f, 1f, 2f)
                .texOffs(124, 15).addBox(1.4f, 1.079f, -0.5f, 1f, 9f, 1f)
                .texOffs(0, 25).addBox(-2.4f, 1.079f, -0.5f, 1f, 9f, 1f)
                .texOffs(22, 15).addBox(-1f, 3.358f, -4.8f, 2f, 1f, 9f)
                .texOffs(58, 41).addBox(-1f, 4.458f, -5.8f, 2f, 1f, 2f),
                PartPose.offsetAndRotation(0f, 0f, 0f, -0.264f, 0f, 0f));
        PartDefinition p_front_wheel = p_fork.addOrReplaceChild("front_wheel",
                CubeListBuilder.create()
                .texOffs(42, 35).addBox(-2.5f, -1f, -1f, 5f, 2f, 2f),
                PartPose.offsetAndRotation(0f, 10.358f, 0f, 0.5f, 0f, 0f));
        PartDefinition p_front_wheel_t0 = p_front_wheel.addOrReplaceChild("front_wheel_t0",
                CubeListBuilder.create()
                .texOffs(40, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_front_wheel_t1 = p_front_wheel.addOrReplaceChild("front_wheel_t1",
                CubeListBuilder.create()
                .texOffs(50, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_front_wheel_t2 = p_front_wheel.addOrReplaceChild("front_wheel_t2",
                CubeListBuilder.create()
                .texOffs(60, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_front_wheel_t3 = p_front_wheel.addOrReplaceChild("front_wheel_t3",
                CubeListBuilder.create()
                .texOffs(70, 0).addBox(-0.5f, -5.5f, -2f, 1f, 11f, 4f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** wheelRot en radianes (las dos ruedas), steerRot = giro del manubrio en radianes, parked = pata puesta. */
    public void pose(float wheelRot, float steerRot, boolean parked) {
        this.frontWheel.xRot = wheelRot;
        this.rearWheel.xRot = wheelRot;
        this.crank.xRot = wheelRot * 0.45f;
        this.steer.yRot = steerRot;
        this.kickstand.visible = parked;
    }

    @Override
    public void setupAnim(BicycleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
