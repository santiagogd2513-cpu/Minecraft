package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.TrailerEntity;
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
 * Remolque de la tractomula: furgón de carga (V7.9.0). ARCHIVO GENERADO por tools/v790/trailers.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 512x448. El origen es el perno de enganche y el remolque se extiende hacia atrás. Animado: las 4 ruedas y las patas de apoyo.
 */
public class TrailerCargoModel extends EntityModel<TrailerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "trailer_cargo"), "main");

    private final ModelPart root;
    private final ModelPart legs;
    private final ModelPart[] wheels;

    public TrailerCargoModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.legs = body.getChild("legs");
        this.wheels = new ModelPart[] {
                body.getChild("wheel_0l"), body.getChild("wheel_0r"), body.getChild("wheel_1l"), body.getChild("wheel_1r")};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(444, 407).addBox(-3f, -17f, -3f, 6f, 3f, 6f)
                .texOffs(360, 333).addBox(-16f, -17f, -4f, 32f, 2f, 28f)
                .texOffs(200, 229).addBox(-12f, -17f, 28f, 24f, 2f, 72f)
                .texOffs(0, 333).addBox(-12f, -17f, 100f, 24f, 2f, 72f)
                .texOffs(268, 429).addBox(-18f, -9f, 151f, 36f, 2f, 2f)
                .texOffs(344, 429).addBox(-18f, -9f, 167f, 36f, 2f, 2f)
                .texOffs(192, 333).addBox(14f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(276, 333).addBox(-24f, -19f, 144f, 10f, 2f, 32f)
                .texOffs(142, 429).addBox(12f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(164, 429).addBox(-22f, -9f, 177f, 10f, 6f, 1f)
                .texOffs(186, 429).addBox(14f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(200, 429).addBox(-20f, -21f, 183f, 6f, 5f, 1f)
                .texOffs(0, 429).addBox(-20f, -15f, 183f, 40f, 5f, 3f)
                .texOffs(0, 125).addBox(-20f, -61f, 124f, 40f, 44f, 60f)
                .texOffs(200, 125).addBox(-20f, -61f, 64f, 40f, 44f, 60f)
                .texOffs(0, 229).addBox(-20f, -61f, 4f, 40f, 44f, 60f)
                .texOffs(0, 0).addBox(20f, -17f, 30f, 1f, 5f, 120f)
                .texOffs(242, 0).addBox(-21f, -17f, 30f, 1f, 5f, 120f)
                .texOffs(420, 429).addBox(18f, -60f, 181f, 2f, 2f, 2f)
                .texOffs(428, 429).addBox(-20f, -60f, 181f, 2f, 2f, 2f)
                .texOffs(436, 429).addBox(-2f, -61f, 4f, 4f, 2f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_wheel_0l = p_body.addOrReplaceChild("wheel_0l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0l_t0 = p_wheel_0l.addOrReplaceChild("wheel_0l_t0",
                CubeListBuilder.create()
                .texOffs(480, 333).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0l_t1 = p_wheel_0l.addOrReplaceChild("wheel_0l_t1",
                CubeListBuilder.create()
                .texOffs(0, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0l_t2 = p_wheel_0l.addOrReplaceChild("wheel_0l_t2",
                CubeListBuilder.create()
                .texOffs(28, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0l_t3 = p_wheel_0l.addOrReplaceChild("wheel_0l_t3",
                CubeListBuilder.create()
                .texOffs(56, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_0r = p_body.addOrReplaceChild("wheel_0r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 152f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_0r_t0 = p_wheel_0r.addOrReplaceChild("wheel_0r_t0",
                CubeListBuilder.create()
                .texOffs(84, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_0r_t1 = p_wheel_0r.addOrReplaceChild("wheel_0r_t1",
                CubeListBuilder.create()
                .texOffs(112, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_0r_t2 = p_wheel_0r.addOrReplaceChild("wheel_0r_t2",
                CubeListBuilder.create()
                .texOffs(140, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_0r_t3 = p_wheel_0r.addOrReplaceChild("wheel_0r_t3",
                CubeListBuilder.create()
                .texOffs(168, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1l = p_body.addOrReplaceChild("wheel_1l",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1l_t0 = p_wheel_1l.addOrReplaceChild("wheel_1l_t0",
                CubeListBuilder.create()
                .texOffs(196, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1l_t1 = p_wheel_1l.addOrReplaceChild("wheel_1l_t1",
                CubeListBuilder.create()
                .texOffs(224, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1l_t2 = p_wheel_1l.addOrReplaceChild("wheel_1l_t2",
                CubeListBuilder.create()
                .texOffs(252, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1l_t3 = p_wheel_1l.addOrReplaceChild("wheel_1l_t3",
                CubeListBuilder.create()
                .texOffs(280, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_wheel_1r = p_body.addOrReplaceChild("wheel_1r",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(-18f, -8f, 168f, 0.4f, 0f, 0f));
        PartDefinition p_wheel_1r_t0 = p_wheel_1r.addOrReplaceChild("wheel_1r_t0",
                CubeListBuilder.create()
                .texOffs(308, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_wheel_1r_t1 = p_wheel_1r.addOrReplaceChild("wheel_1r_t1",
                CubeListBuilder.create()
                .texOffs(336, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_wheel_1r_t2 = p_wheel_1r.addOrReplaceChild("wheel_1r_t2",
                CubeListBuilder.create()
                .texOffs(364, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_wheel_1r_t3 = p_wheel_1r.addOrReplaceChild("wheel_1r_t3",
                CubeListBuilder.create()
                .texOffs(392, 407).addBox(-4f, -8f, -3f, 8f, 16f, 6f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_legs = p_body.addOrReplaceChild("legs",
                CubeListBuilder.create()
                .texOffs(420, 407).addBox(12f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(432, 407).addBox(-15f, -15f, 37f, 3f, 15f, 3f)
                .texOffs(86, 429).addBox(10f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(114, 429).addBox(-17f, -1f, 35f, 7f, 1f, 7f)
                .texOffs(214, 429).addBox(-12f, -11f, 37f, 24f, 2f, 3f),
                PartPose.offset(0f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 448);
    }

    /** wheelRot en radianes (las 4 ruedas); legsDown = patas de apoyo abajo (sin tractor). */
    public void pose(float wheelRot, boolean legsDown) {
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = wheelRot;
        }
        this.legs.visible = legsDown;
    }

    @Override
    public void setupAnim(TrailerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...).
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
