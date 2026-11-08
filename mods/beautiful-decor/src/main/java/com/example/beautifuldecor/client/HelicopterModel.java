package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.HelicopterEntity;
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
 * Helicóptero BOING HX-6 (V3.0.0). ARCHIVO GENERADO por tools/v300/helicopter.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 256x256. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
 * Dos árboles: "body" (opaco) y "glass" (vidrios). El renderizador los dibuja en pasadas distintas con
 * showGlass(false) y showGlass(true), así el vidrio translúcido se mezcla bien con lo que hay detrás (asientos, tablero).
 * Piezas animadas: "rotor" (rotor principal, gira en el eje vertical) y "tail_rotor" (rotor de cola, gira en el eje x).
 */
public class HelicopterModel extends EntityModel<HelicopterEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "helicopter"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart rotor;
    private final ModelPart tailRotor;

    public HelicopterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.rotor = this.body.getChild("rotor");
        this.tailRotor = this.body.getChild("tail_rotor");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 144).addBox(10f, -2f, 0f, 2f, 2f, 24f)
                .texOffs(52, 144).addBox(-12f, -2f, 0f, 2f, 2f, 24f)
                .texOffs(196, 144).addBox(10f, -2f, -22f, 2f, 2f, 22f)
                .texOffs(0, 170).addBox(-12f, -2f, -22f, 2f, 2f, 22f)
                .texOffs(34, 249).addBox(10f, -6f, -24f, 2f, 4f, 2f)
                .texOffs(42, 249).addBox(-12f, -6f, -24f, 2f, 4f, 2f)
                .texOffs(138, 249).addBox(10f, -4f, 24f, 2f, 2f, 2f)
                .texOffs(146, 249).addBox(-12f, -4f, 24f, 2f, 2f, 2f)
                .texOffs(232, 226).addBox(9f, -7f, -10f, 2f, 5f, 2f)
                .texOffs(240, 226).addBox(-11f, -7f, -10f, 2f, 5f, 2f)
                .texOffs(248, 226).addBox(9f, -7f, 14f, 2f, 5f, 2f)
                .texOffs(0, 236).addBox(-11f, -7f, 14f, 2f, 5f, 2f)
                .texOffs(50, 249).addBox(-10f, -6f, -10f, 20f, 2f, 2f)
                .texOffs(94, 249).addBox(-10f, -6f, 14f, 20f, 2f, 2f)
                .texOffs(0, 104).addBox(-10f, -8f, -14f, 20f, 2f, 38f)
                .texOffs(100, 0).addBox(10f, -20f, -14f, 2f, 12f, 38f)
                .texOffs(0, 54).addBox(-12f, -20f, -14f, 2f, 12f, 38f)
                .texOffs(56, 194).addBox(10f, -34f, -14f, 2f, 14f, 3f)
                .texOffs(66, 194).addBox(-12f, -34f, -14f, 2f, 14f, 3f)
                .texOffs(76, 194).addBox(10f, -34f, -1f, 2f, 14f, 2f)
                .texOffs(84, 194).addBox(-12f, -34f, -1f, 2f, 14f, 2f)
                .texOffs(32, 194).addBox(10f, -34f, 22f, 2f, 14f, 4f)
                .texOffs(44, 194).addBox(-12f, -34f, 22f, 2f, 14f, 4f)
                .texOffs(204, 104).addBox(-10f, -34f, 24f, 20f, 26f, 2f)
                .texOffs(80, 54).addBox(-12f, -36f, -14f, 24f, 2f, 40f)
                .texOffs(64, 226).addBox(-12f, -34f, -14f, 24f, 4f, 4f)
                .texOffs(104, 194).addBox(-10f, -13f, -24f, 20f, 5f, 10f)
                .texOffs(120, 226).addBox(-7f, -12f, -28f, 14f, 4f, 4f)
                .texOffs(0, 226).addBox(-10f, -19f, -12f, 20f, 6f, 4f)
                .texOffs(0, 213).addBox(-8f, -8f, -22f, 16f, 3f, 10f)
                .texOffs(108, 213).addBox(2f, -12f, -4f, 8f, 4f, 8f)
                .texOffs(208, 194).addBox(2f, -24f, 4f, 8f, 12f, 2f)
                .texOffs(0, 249).addBox(4f, -28f, 4f, 4f, 4f, 2f)
                .texOffs(140, 213).addBox(-10f, -12f, -4f, 8f, 4f, 8f)
                .texOffs(228, 194).addBox(-10f, -24f, 4f, 8f, 12f, 2f)
                .texOffs(12, 249).addBox(-8f, -28f, 4f, 4f, 4f, 2f)
                .texOffs(52, 213).addBox(-10f, -12f, 10f, 20f, 4f, 8f)
                .texOffs(164, 194).addBox(-10f, -24f, 18f, 20f, 12f, 2f)
                .texOffs(212, 170).addBox(-2f, -14f, -10f, 4f, 6f, 14f)
                .texOffs(124, 144).addBox(-8f, -40f, -2f, 16f, 4f, 20f)
                .texOffs(234, 243).addBox(-2f, -42f, 12f, 4f, 2f, 4f)
                .texOffs(48, 226).addBox(-2f, -46f, 0f, 4f, 6f, 4f)
                .texOffs(156, 170).addBox(-9f, -18f, 26f, 18f, 10f, 10f)
                .texOffs(0, 0).addBox(-4f, -30f, 26f, 8f, 12f, 42f)
                .texOffs(0, 194).addBox(-3f, -29f, 68f, 6f, 9f, 10f)
                .texOffs(104, 144).addBox(-1f, -46f, 72f, 2f, 17f, 8f)
                .texOffs(156, 226).addBox(-1f, -20f, 74f, 2f, 4f, 4f)
                .texOffs(168, 226).addBox(1f, -25f, 70f, 10f, 1f, 6f)
                .texOffs(200, 226).addBox(-11f, -25f, 70f, 10f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_rotor = p_body.addOrReplaceChild("rotor",
                CubeListBuilder.create()
                .texOffs(206, 213).addBox(-4f, -2.5f, -4f, 8f, 3f, 8f)
                .texOffs(8, 236).addBox(-56f, -1f, -2.5f, 112f, 1f, 5f),
                PartPose.offsetAndRotation(0f, -46f, 2f, 0f, 0.5f, 0f));
        PartDefinition p_blade_b_part = p_rotor.addOrReplaceChild("blade_b_part",
                CubeListBuilder.create()
                .texOffs(0, 243).addBox(-56f, -0.5f, -2.5f, 112f, 1f, 5f),
                PartPose.offsetAndRotation(0f, -1.5f, 0f, 0f, 1.571f, 0f));
        PartDefinition p_tail_rotor = p_body.addOrReplaceChild("tail_rotor",
                CubeListBuilder.create()
                .texOffs(24, 249).addBox(-1f, -1.5f, -1.5f, 2f, 3f, 3f)
                .texOffs(92, 194).addBox(0.5f, -7f, -1f, 1f, 14f, 2f),
                PartPose.offsetAndRotation(3f, -38f, 76f, 0.3f, 0f, 0f));
        PartDefinition p_tr_blade_b_part = p_tail_rotor.addOrReplaceChild("tr_blade_b_part",
                CubeListBuilder.create()
                .texOffs(98, 194).addBox(-0.5f, -7f, -1f, 1f, 14f, 2f),
                PartPose.offsetAndRotation(1.5f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(92, 170).addBox(10f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(100, 170).addBox(-12f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(108, 170).addBox(-1f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(154, 249).addBox(-11f, -1f, -1f, 22f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -12f, -23f, -0.541f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(48, 170).addBox(11f, -33f, -11f, 1f, 13f, 10f)
                .texOffs(70, 170).addBox(-12f, -33f, -11f, 1f, 13f, 10f)
                .texOffs(116, 104).addBox(11f, -33f, 1f, 1f, 13f, 21f)
                .texOffs(160, 104).addBox(-12f, -33f, 1f, 1f, 13f, 21f)
                .texOffs(172, 213).addBox(-8f, -32f, 25f, 16f, 10f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(116, 170).addBox(1f, -20.5f, -0.5f, 9f, 20f, 1f)
                .texOffs(136, 170).addBox(-10f, -20.5f, -0.5f, 9f, 20f, 1f),
                PartPose.offsetAndRotation(0f, -12f, -23f, -0.541f, 0f, 0f));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** rotorAngle = giro del rotor principal en radianes; tailAngle = giro del rotor de cola en radianes. */
    public void pose(float rotorAngle, float tailAngle) {
        this.rotor.yRot = rotorAngle;
        this.tailRotor.xRot = tailAngle;
    }

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }

    @Override
    public void setupAnim(HelicopterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
