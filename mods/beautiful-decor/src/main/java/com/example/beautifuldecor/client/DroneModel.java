package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

/**
 * Quadcopter drone: X frame, four spinning two-blade rotors, battery, antenna,
 * camera gimbal and landing skids. Texture atlas is 64x32.
 * Coordinates follow the vanilla entity convention (y grows downwards, ground at y = 24).
 */
public class DroneModel extends EntityModel<DroneEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "drone"), "main");

    private static final float QUARTER_PI = (float) (Math.PI / 4.0);
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private final ModelPart frame;
    private final ModelPart frontLeftRotor;
    private final ModelPart frontRightRotor;
    private final ModelPart rearLeftRotor;
    private final ModelPart rearRightRotor;

    public DroneModel(ModelPart root) {
        this.frame = root.getChild("frame");
        this.frontLeftRotor = this.frame.getChild("front_left_rotor");
        this.frontRightRotor = this.frame.getChild("front_right_rotor");
        this.rearLeftRotor = this.frame.getChild("rear_left_rotor");
        this.rearRightRotor = this.frame.getChild("rear_right_rotor");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition frame = root.addOrReplaceChild("frame", CubeListBuilder.create(),
                PartPose.offset(0.0f, 20.0f, 0.0f));

        // Hull
        frame.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -1.0f, -3.0f, 6.0f, 2.0f, 6.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("top_shell",
                CubeListBuilder.create().texOffs(24, 0).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 1.0f, 4.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("battery",
                CubeListBuilder.create().texOffs(40, 0).addBox(-2.0f, 1.0f, -2.0f, 4.0f, 1.0f, 4.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(48, 13).addBox(1.0f, -5.0f, 1.0f, 1.0f, 3.0f, 1.0f),
                PartPose.ZERO);

        // X frame (two diagonal bars)
        frame.addOrReplaceChild("arm_a",
                CubeListBuilder.create().texOffs(0, 8).addBox(-10.0f, -0.5f, -1.0f, 20.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, QUARTER_PI, 0.0f));
        frame.addOrReplaceChild("arm_b",
                CubeListBuilder.create().texOffs(0, 8).addBox(-10.0f, -0.5f, -1.0f, 20.0f, 1.0f, 2.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -QUARTER_PI, 0.0f));

        // Motors (red LEDs in front, green LEDs behind)
        frame.addOrReplaceChild("front_left_motor", motorBuilder(44, 8), PartPose.offset(-7.0f, -1.0f, -7.0f));
        frame.addOrReplaceChild("front_right_motor", motorBuilder(44, 8), PartPose.offset(7.0f, -1.0f, -7.0f));
        frame.addOrReplaceChild("rear_left_motor", motorBuilder(0, 13), PartPose.offset(-7.0f, -1.0f, 7.0f));
        frame.addOrReplaceChild("rear_right_motor", motorBuilder(0, 13), PartPose.offset(7.0f, -1.0f, 7.0f));

        // Rotors (each is a two-blade cross; the whole part spins)
        addRotor(frame, "front_left_rotor", -7.0f, -7.0f);
        addRotor(frame, "front_right_rotor", 7.0f, -7.0f);
        addRotor(frame, "rear_left_rotor", -7.0f, 7.0f);
        addRotor(frame, "rear_right_rotor", 7.0f, 7.0f);

        // Camera gimbal under the nose
        frame.addOrReplaceChild("camera_mount",
                CubeListBuilder.create().texOffs(30, 13).addBox(-1.5f, 1.0f, -4.5f, 3.0f, 2.0f, 3.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("camera_lens",
                CubeListBuilder.create().texOffs(42, 13).addBox(-1.0f, 1.5f, -5.5f, 2.0f, 1.0f, 1.0f),
                PartPose.ZERO);

        // Landing skids
        frame.addOrReplaceChild("skid_left",
                CubeListBuilder.create().texOffs(0, 18).addBox(-4.0f, 3.0f, -4.5f, 1.0f, 1.0f, 9.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("skid_right",
                CubeListBuilder.create().texOffs(0, 18).addBox(3.0f, 3.0f, -4.5f, 1.0f, 1.0f, 9.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("leg_left_front",
                CubeListBuilder.create().texOffs(20, 18).addBox(-4.0f, 0.0f, -3.0f, 1.0f, 3.0f, 1.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("leg_left_rear",
                CubeListBuilder.create().texOffs(20, 18).addBox(-4.0f, 0.0f, 2.0f, 1.0f, 3.0f, 1.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("leg_right_front",
                CubeListBuilder.create().texOffs(20, 18).addBox(3.0f, 0.0f, -3.0f, 1.0f, 3.0f, 1.0f),
                PartPose.ZERO);
        frame.addOrReplaceChild("leg_right_rear",
                CubeListBuilder.create().texOffs(20, 18).addBox(3.0f, 0.0f, 2.0f, 1.0f, 3.0f, 1.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }

    private static CubeListBuilder motorBuilder(int textureX, int textureY) {
        return CubeListBuilder.create().texOffs(textureX, textureY)
                .addBox(-1.5f, -1.0f, -1.5f, 3.0f, 2.0f, 3.0f);
    }

    private static void addRotor(PartDefinition frame, String name, float x, float z) {
        PartDefinition rotor = frame.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(12, 13).addBox(-4.0f, -0.5f, -0.5f, 8.0f, 1.0f, 1.0f),
                PartPose.offset(x, -2.5f, z));
        rotor.addOrReplaceChild("blade_b",
                CubeListBuilder.create().texOffs(12, 13).addBox(-4.0f, -0.5f, -0.5f, 8.0f, 1.0f, 1.0f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, HALF_PI, 0.0f));
    }

    @Override
    public void setupAnim(DroneEntity drone, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Inclinación suavizada entre ticks: el morro baja al avanzar y el lado que se mueve baja al desplazarse.
        float partialTick = ageInTicks - drone.tickCount;
        this.frame.xRot = drone.getLeanForward(partialTick) * 0.20f;
        this.frame.zRot = -drone.getLeanSide(partialTick) * 0.20f;
        float spin = ageInTicks * 2.5f;
        this.frontLeftRotor.yRot = spin;
        this.rearRightRotor.yRot = spin;
        this.frontRightRotor.yRot = -spin;
        this.rearLeftRotor.yRot = -spin;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.frame.render(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
