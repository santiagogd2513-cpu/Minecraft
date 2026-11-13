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
 * Atlas de textura de 256x448. Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24).
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
    private final ModelPart police;
    private final ModelPart fire;
    private final ModelPart news;
    private final ModelPart bar;
    private final ModelPart barRed;
    private final ModelPart barBlue;
    private final ModelPart sirenBtn;
    private boolean barsOn;

    public HelicopterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.rotor = this.body.getChild("rotor");
        this.tailRotor = this.body.getChild("tail_rotor");
        this.police = this.body.getChild("police");
        this.fire = this.body.getChild("fire");
        this.news = this.body.getChild("news");
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
                .texOffs(92, 144).addBox(10f, -2f, 0f, 2f, 2f, 24f)
                .texOffs(144, 144).addBox(-12f, -2f, 0f, 2f, 2f, 24f)
                .texOffs(72, 174).addBox(10f, -2f, -22f, 2f, 2f, 22f)
                .texOffs(120, 174).addBox(-12f, -2f, -22f, 2f, 2f, 22f)
                .texOffs(126, 273).addBox(10f, -6f, -24f, 2f, 4f, 2f)
                .texOffs(134, 273).addBox(-12f, -6f, -24f, 2f, 4f, 2f)
                .texOffs(100, 279).addBox(10f, -4f, 24f, 2f, 2f, 2f)
                .texOffs(108, 279).addBox(-12f, -4f, 24f, 2f, 2f, 2f)
                .texOffs(244, 246).addBox(9f, -7f, -10f, 2f, 5f, 2f)
                .texOffs(0, 254).addBox(-11f, -7f, -10f, 2f, 5f, 2f)
                .texOffs(8, 254).addBox(9f, -7f, 14f, 2f, 5f, 2f)
                .texOffs(16, 254).addBox(-11f, -7f, 14f, 2f, 5f, 2f)
                .texOffs(0, 279).addBox(-10f, -6f, -10f, 20f, 2f, 2f)
                .texOffs(44, 279).addBox(-10f, -6f, 14f, 20f, 2f, 2f)
                .texOffs(0, 104).addBox(-10f, -8f, -14f, 20f, 2f, 38f)
                .texOffs(100, 0).addBox(10f, -20f, -14f, 2f, 12f, 38f)
                .texOffs(0, 54).addBox(-12f, -20f, -14f, 2f, 12f, 38f)
                .texOffs(200, 198).addBox(10f, -34f, -14f, 2f, 14f, 3f)
                .texOffs(210, 198).addBox(-12f, -34f, -14f, 2f, 14f, 3f)
                .texOffs(220, 198).addBox(10f, -34f, -1f, 2f, 14f, 2f)
                .texOffs(228, 198).addBox(-12f, -34f, -1f, 2f, 14f, 2f)
                .texOffs(176, 198).addBox(10f, -34f, 22f, 2f, 14f, 4f)
                .texOffs(188, 198).addBox(-12f, -34f, 22f, 2f, 14f, 4f)
                .texOffs(48, 144).addBox(-10f, -34f, 24f, 20f, 26f, 2f)
                .texOffs(80, 54).addBox(-12f, -36f, -14f, 24f, 2f, 40f)
                .texOffs(0, 246).addBox(-12f, -34f, -14f, 24f, 4f, 4f)
                .texOffs(0, 219).addBox(-10f, -13f, -24f, 20f, 5f, 10f)
                .texOffs(56, 246).addBox(-7f, -12f, -28f, 14f, 4f, 4f)
                .texOffs(158, 234).addBox(-10f, -19f, -12f, 20f, 6f, 4f)
                .texOffs(144, 219).addBox(-8f, -8f, -22f, 16f, 3f, 10f)
                .texOffs(164, 279).addBox(3f, -11f, -29f, 3f, 2f, 1f)
                .texOffs(172, 279).addBox(-6f, -11f, -29f, 3f, 2f, 1f)
                .texOffs(186, 273).addBox(-2f, -5f, -19f, 4f, 1f, 4f)
                .texOffs(180, 279).addBox(-1f, -37f, 22f, 2f, 1f, 2f)
                .texOffs(0, 234).addBox(2f, -12f, -4f, 8f, 4f, 8f)
                .texOffs(104, 219).addBox(2f, -24f, 4f, 8f, 12f, 2f)
                .texOffs(80, 273).addBox(4f, -28f, 4f, 4f, 4f, 2f)
                .texOffs(32, 234).addBox(-10f, -12f, -4f, 8f, 4f, 8f)
                .texOffs(124, 219).addBox(-10f, -24f, 4f, 8f, 12f, 2f)
                .texOffs(92, 273).addBox(-8f, -28f, 4f, 4f, 4f, 2f)
                .texOffs(196, 219).addBox(-10f, -12f, 10f, 20f, 4f, 8f)
                .texOffs(60, 219).addBox(-10f, -24f, 18f, 20f, 12f, 2f)
                .texOffs(76, 198).addBox(-2f, -14f, -10f, 4f, 6f, 14f)
                .texOffs(188, 279).addBox(-8f, -17f, -8f, 2f, 2f, 1f)
                .texOffs(194, 279).addBox(-5f, -17f, -8f, 2f, 2f, 1f)
                .texOffs(200, 279).addBox(5f, -17f, -8f, 2f, 2f, 1f)
                .texOffs(206, 279).addBox(8f, -17f, -8f, 2f, 2f, 1f)
                .texOffs(212, 279).addBox(-1f, -17f, -8f, 2f, 2f, 1f)
                .texOffs(168, 246).addBox(5f, -15f, -6f, 2f, 7f, 1f)
                .texOffs(218, 279).addBox(5f, -17f, -6f, 2f, 2f, 1f)
                .texOffs(174, 246).addBox(-7f, -15f, -6f, 2f, 7f, 1f)
                .texOffs(224, 279).addBox(-7f, -17f, -6f, 2f, 2f, 1f)
                .texOffs(92, 246).addBox(-5f, -34f, -8f, 10f, 2f, 6f)
                .texOffs(230, 279).addBox(-3f, -32f, -6f, 1f, 1f, 2f)
                .texOffs(236, 279).addBox(-1f, -32f, -6f, 1f, 1f, 2f)
                .texOffs(242, 279).addBox(1f, -32f, -6f, 1f, 1f, 2f)
                .texOffs(248, 279).addBox(3f, -32f, -6f, 1f, 1f, 2f)
                .texOffs(0, 174).addBox(-8f, -40f, -2f, 16f, 4f, 20f)
                .texOffs(48, 273).addBox(-2f, -42f, 12f, 4f, 2f, 4f)
                .texOffs(226, 234).addBox(-2f, -46f, 0f, 4f, 6f, 4f)
                .texOffs(20, 198).addBox(-9f, -18f, 26f, 18f, 10f, 10f)
                .texOffs(0, 0).addBox(-4f, -30f, 26f, 8f, 12f, 42f)
                .texOffs(144, 198).addBox(-3f, -29f, 68f, 6f, 9f, 10f)
                .texOffs(196, 144).addBox(-1f, -46f, 72f, 2f, 17f, 8f)
                .texOffs(156, 246).addBox(-1f, -20f, 74f, 2f, 4f, 4f)
                .texOffs(180, 246).addBox(1f, -25f, 70f, 10f, 1f, 6f)
                .texOffs(212, 246).addBox(-11f, -25f, 70f, 10f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_rotor = p_body.addOrReplaceChild("rotor",
                CubeListBuilder.create()
                .texOffs(98, 234).addBox(-4f, -2.5f, -4f, 8f, 3f, 8f)
                .texOffs(0, 261).addBox(-56f, -1f, -2.5f, 112f, 1f, 5f),
                PartPose.offsetAndRotation(0f, -46f, 2f, 0f, 0.5f, 0f));
        PartDefinition p_blade_b_part = p_rotor.addOrReplaceChild("blade_b_part",
                CubeListBuilder.create()
                .texOffs(0, 267).addBox(-56f, -0.5f, -2.5f, 112f, 1f, 5f),
                PartPose.offsetAndRotation(0f, -1.5f, 0f, 0f, 1.571f, 0f));
        PartDefinition p_tail_rotor = p_body.addOrReplaceChild("tail_rotor",
                CubeListBuilder.create()
                .texOffs(116, 273).addBox(-1f, -1.5f, -1.5f, 2f, 3f, 3f)
                .texOffs(236, 198).addBox(0.5f, -7f, -1f, 1f, 14f, 2f),
                PartPose.offsetAndRotation(3f, -38f, 76f, 0.3f, 0f, 0f));
        PartDefinition p_tr_blade_b_part = p_tail_rotor.addOrReplaceChild("tr_blade_b_part",
                CubeListBuilder.create()
                .texOffs(242, 198).addBox(-0.5f, -7f, -1f, 1f, 14f, 2f),
                PartPose.offsetAndRotation(1.5f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(212, 174).addBox(10f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(220, 174).addBox(-12f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(228, 174).addBox(-1f, -21f, -1f, 2f, 21f, 2f)
                .texOffs(116, 279).addBox(-11f, -1f, -1f, 22f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -12f, -23f, -0.541f, 0f, 0f));
        PartDefinition p_siren_btn = p_body.addOrReplaceChild("siren_btn",
                CubeListBuilder.create()
                .texOffs(0, 283).addBox(2f, -17f, -8f, 2f, 2f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar = p_body.addOrReplaceChild("bar",
                CubeListBuilder.create()
                .texOffs(142, 273).addBox(-9f, -37f, -8f, 18f, 1f, 4f)
                .texOffs(104, 273).addBox(-1f, -39f, -8f, 2f, 2f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_red = p_body.addOrReplaceChild("bar_red",
                CubeListBuilder.create()
                .texOffs(0, 273).addBox(1f, -39f, -8f, 8f, 2f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_bar_blue = p_body.addOrReplaceChild("bar_blue",
                CubeListBuilder.create()
                .texOffs(24, 273).addBox(-9f, -39f, -8f, 8f, 2f, 4f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_police = p_body.addOrReplaceChild("police",
                CubeListBuilder.create()
                .texOffs(88, 279).addBox(10f, -17f, -18f, 4f, 2f, 2f)
                .texOffs(112, 198).addBox(14f, -21f, -22f, 6f, 10f, 10f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_fire = p_body.addOrReplaceChild("fire",
                CubeListBuilder.create()
                .texOffs(204, 104).addBox(12f, -18f, 0f, 4f, 10f, 20f)
                .texOffs(0, 144).addBox(-16f, -18f, 0f, 4f, 10f, 20f)
                .texOffs(64, 273).addBox(-2f, -7f, -26f, 4f, 2f, 4f)
                .texOffs(206, 234).addBox(-1f, -7f, -34f, 2f, 2f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_news = p_body.addOrReplaceChild("news",
                CubeListBuilder.create()
                .texOffs(202, 273).addBox(-2f, -8f, -27f, 4f, 1f, 4f)
                .texOffs(130, 234).addBox(-3f, -7f, -28f, 6f, 5f, 6f)
                .texOffs(218, 273).addBox(-1f, -39f, 22f, 2f, 3f, 2f)
                .texOffs(124, 246).addBox(-5f, -41f, 20f, 10f, 2f, 6f)
                .texOffs(154, 234).addBox(6f, -46f, 23f, 1f, 10f, 1f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(168, 174).addBox(11f, -33f, -11f, 1f, 13f, 10f)
                .texOffs(190, 174).addBox(-12f, -33f, -11f, 1f, 13f, 10f)
                .texOffs(116, 104).addBox(11f, -33f, 1f, 1f, 13f, 21f)
                .texOffs(160, 104).addBox(-12f, -33f, 1f, 1f, 13f, 21f)
                .texOffs(64, 234).addBox(-8f, -32f, 25f, 16f, 10f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(236, 174).addBox(1f, -20.5f, -0.5f, 9f, 20f, 1f)
                .texOffs(0, 198).addBox(-10f, -20.5f, -0.5f, 9f, 20f, 1f),
                PartPose.offsetAndRotation(0f, -12f, -23f, -0.541f, 0f, 0f));
        return LayerDefinition.create(mesh, 256, 448);
    }

    /** rotorAngle = giro del rotor principal en radianes; tailAngle = giro del rotor de cola en radianes. */
    public void pose(float rotorAngle, float tailAngle) {
        this.rotor.yRot = rotorAngle;
        this.tailRotor.xRot = tailAngle;
    }

    /** V6.9.0: accesorios de la skin: 0 = ninguno, 1 = policía, 2 = bomberos, 3 = noticias. */
    public void setSkin(int skin) {
        this.police.visible = skin == 1;
        this.fire.visible = skin == 2;
        this.news.visible = skin == 3;
        this.barsOn = skin == 1 || skin == 2;
        this.bar.visible = this.barsOn;
        this.sirenBtn.visible = this.barsOn;
        this.setBarPhase(-2);
    }

    /** V6.9.0: -2 = las dos mitades de la barra (pasada normal); en la pasada de brillo, 0 = roja, 1 = azul, otro = ninguna. */
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
