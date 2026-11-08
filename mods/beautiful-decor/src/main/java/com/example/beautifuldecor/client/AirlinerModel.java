package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.AirlinerEntity;
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
 * Avión BOING 7G7 Gigante (V3.8.0). ARCHIVO GENERADO por tools/v380/airliner.py: no editar a mano, se pierde al regenerar.
 * Atlas de textura de 512x512. Modelo a escala 1:4 (el renderizador lo agranda 4 veces: 1 unidad = 1/4 de bloque).
 * Coordenadas del modelo de entidad de Minecraft (y hacia abajo, suelo en y = 24). Dos árboles: "body" (opaco) y "glass" (vidrios).
 * Piezas animadas: turbinas (giran en z), ruedas (giran en x) y las tres patas del tren (se pliegan).
 */
public class AirlinerModel extends EntityModel<AirlinerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "airliner"), "main");
    /** Cuántas veces el renderizador agranda el modelo. */
    public static final float SCALE = 4.0f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final ModelPart[] fans;
    private final ModelPart[] wheels;
    private final ModelPart gearLeft;
    private final ModelPart gearRight;
    private final ModelPart gearNose;

    public AirlinerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.fans = new ModelPart[] {this.body.getChild("eng0").getChild("fan0"), this.body.getChild("eng1").getChild("fan1"), this.body.getChild("eng2").getChild("fan2"), this.body.getChild("eng3").getChild("fan3")};
        this.wheels = new ModelPart[] {
                this.body.getChild("gear_ml").getChild("w_gear_ml_00"),
                this.body.getChild("gear_ml").getChild("w_gear_ml_01"),
                this.body.getChild("gear_ml").getChild("w_gear_ml_10"),
                this.body.getChild("gear_ml").getChild("w_gear_ml_11"),
                this.body.getChild("gear_mr").getChild("w_gear_mr_00"),
                this.body.getChild("gear_mr").getChild("w_gear_mr_01"),
                this.body.getChild("gear_mr").getChild("w_gear_mr_10"),
                this.body.getChild("gear_mr").getChild("w_gear_mr_11"),
                this.body.getChild("gear_nose").getChild("w_nose_0"),
                this.body.getChild("gear_nose").getChild("w_nose_1")};
        this.gearLeft = this.body.getChild("gear_ml");
        this.gearRight = this.body.getChild("gear_mr");
        this.gearNose = this.body.getChild("gear_nose");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(440, 43).addBox(15f, -27f, -96f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -96f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -96f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -96f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -96f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -96f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -96f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -96f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -96f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -90f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -90f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -90f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -90f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -90f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -90f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -90f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -90f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -90f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -84f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -84f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -84f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -84f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -84f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -84f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -84f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -84f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -84f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -78f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -78f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -78f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -78f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -78f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -78f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -78f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -78f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -78f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -72f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -72f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -72f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -72f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -72f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -72f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -72f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -72f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -72f, 16f, 1f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -66f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -66f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -66f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -66f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -66f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -66f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -66f, 16f, 1f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -60f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -60f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -60f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -60f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -60f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -60f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -60f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -54f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -54f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -54f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -54f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -54f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -54f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -54f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -54f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -54f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -48f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -48f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -48f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -48f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -48f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -48f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -48f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -48f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -48f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -42f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -42f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -42f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -42f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -42f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -42f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -42f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -42f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -42f, 16f, 1f, 6f)
                .texOffs(468, 43).addBox(15f, -27f, -36f, 1f, 13f, 6f)
                .texOffs(482, 43).addBox(-16f, -27f, -36f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -36f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -36f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -36f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -36f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -36f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -36f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -36f, 16f, 1f, 6f)
                .texOffs(496, 43).addBox(15f, -27f, -30f, 1f, 13f, 6f)
                .texOffs(0, 68).addBox(-16f, -27f, -30f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -30f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -30f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -30f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -30f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -30f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -30f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -30f, 16f, 1f, 6f)
                .texOffs(14, 68).addBox(15f, -27f, -24f, 1f, 13f, 6f)
                .texOffs(28, 68).addBox(-16f, -27f, -24f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -24f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -24f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -24f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -24f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -24f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -24f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -24f, 16f, 1f, 6f)
                .texOffs(42, 68).addBox(15f, -27f, -18f, 1f, 13f, 6f)
                .texOffs(56, 68).addBox(-16f, -27f, -18f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -18f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -18f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -18f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -18f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -18f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -18f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -18f, 16f, 1f, 6f)
                .texOffs(70, 68).addBox(15f, -27f, -12f, 1f, 13f, 6f)
                .texOffs(84, 68).addBox(-16f, -27f, -12f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -12f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -12f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -12f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -12f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -12f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -12f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -12f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, -6f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, -6f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, -6f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, -6f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, -6f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, -6f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, -6f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, -6f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, -6f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 0f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 0f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 0f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 0f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 0f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 0f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 0f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 0f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 0f, 16f, 1f, 6f)
                .texOffs(98, 68).addBox(15f, -27f, 6f, 1f, 13f, 6f)
                .texOffs(112, 68).addBox(-16f, -27f, 6f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 6f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 6f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 6f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 6f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 6f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 6f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 6f, 16f, 1f, 6f)
                .texOffs(126, 68).addBox(15f, -27f, 12f, 1f, 13f, 6f)
                .texOffs(140, 68).addBox(-16f, -27f, 12f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 12f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 12f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 12f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 12f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 12f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 12f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 12f, 16f, 1f, 6f)
                .texOffs(154, 68).addBox(15f, -27f, 18f, 1f, 13f, 6f)
                .texOffs(168, 68).addBox(-16f, -27f, 18f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 18f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 18f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 18f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 18f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 18f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 18f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 18f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 24f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 24f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 24f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 24f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 24f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 24f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 24f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 24f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 24f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 30f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 30f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 30f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 30f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 30f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 30f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 30f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 30f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 30f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 36f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 36f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 36f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 36f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 36f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 36f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 36f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 36f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 36f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 42f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 42f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 42f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 42f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 42f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 42f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 42f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 42f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 42f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 48f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 48f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 48f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 48f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 48f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 48f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 48f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 48f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 48f, 16f, 1f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 54f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 54f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 54f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 54f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 54f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 54f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 54f, 16f, 1f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 60f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 60f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 60f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 60f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 60f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 60f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 60f, 16f, 1f, 6f)
                .texOffs(440, 43).addBox(15f, -27f, 66f, 1f, 13f, 6f)
                .texOffs(454, 43).addBox(-16f, -27f, 66f, 1f, 13f, 6f)
                .texOffs(120, 101).addBox(-16f, -28f, 66f, 32f, 1f, 6f)
                .texOffs(268, 101).addBox(-14f, -29f, 66f, 28f, 1f, 6f)
                .texOffs(336, 101).addBox(-10f, -30f, 66f, 20f, 1f, 6f)
                .texOffs(196, 101).addBox(-15f, -14f, 66f, 30f, 1f, 6f)
                .texOffs(246, 87).addBox(-16f, -13f, 66f, 32f, 3f, 6f)
                .texOffs(392, 87).addBox(-12f, -10f, 66f, 24f, 3f, 6f)
                .texOffs(388, 101).addBox(-8f, -7f, 66f, 16f, 1f, 6f)
                .texOffs(140, 43).addBox(15f, -27f, -66f, 1f, 13f, 12f)
                .texOffs(140, 43).addBox(15f, -27f, 54f, 1f, 13f, 12f)
                .texOffs(166, 43).addBox(-16f, -27f, -66f, 1f, 13f, 12f)
                .texOffs(166, 43).addBox(-16f, -27f, 54f, 1f, 13f, 12f)
                .texOffs(32, 110).addBox(9f, -16f, -47f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -43f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -47f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -43f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -47f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -43f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -41f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -37f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -41f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -37f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -41f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -37f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -35f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -31f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -35f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -31f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -35f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -31f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -29f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -25f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -29f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -25f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -29f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -25f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -23f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -19f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -23f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -19f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -23f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -19f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -17f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -13f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -17f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -13f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -17f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -13f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -11f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -7f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -11f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -7f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -11f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -7f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, -5f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, -1f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, -5f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, -1f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, -5f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, -1f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 1f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 5f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 1f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 5f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 1f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 5f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 7f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 11f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 7f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 11f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 7f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 11f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 13f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 17f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 13f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 17f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 13f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 17f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 19f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 23f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 19f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 23f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 19f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 23f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 25f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 29f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 25f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 29f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 25f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 29f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 31f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 35f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 31f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 35f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 31f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 35f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 37f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 41f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 37f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 41f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 37f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 41f, 6f, 6f, 1f)
                .texOffs(32, 110).addBox(9f, -16f, 43f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(9f, -22f, 47f, 6f, 6f, 1f)
                .texOffs(0, 110).addBox(-6f, -16f, 43f, 12f, 2f, 4f)
                .texOffs(432, 101).addBox(-6f, -22f, 47f, 12f, 6f, 1f)
                .texOffs(32, 110).addBox(-15f, -16f, 43f, 6f, 2f, 4f)
                .texOffs(480, 101).addBox(-15f, -22f, 47f, 6f, 6f, 1f)
                .texOffs(458, 101).addBox(7f, -16f, -83f, 6f, 2f, 5f)
                .texOffs(20, 101).addBox(7f, -24f, -78f, 6f, 8f, 1f)
                .texOffs(458, 101).addBox(-13f, -16f, -83f, 6f, 2f, 5f)
                .texOffs(20, 101).addBox(-13f, -24f, -78f, 6f, 8f, 1f)
                .texOffs(246, 68).addBox(-3f, -18f, -90f, 6f, 4f, 14f)
                .texOffs(322, 87).addBox(-15f, -21f, -95f, 30f, 4f, 5f)
                .texOffs(52, 87).addBox(-6f, -27f, -90f, 12f, 3f, 10f)
                .texOffs(370, 43).addBox(-14f, -20f, -103f, 28f, 12f, 7f)
                .texOffs(286, 68).addBox(-12f, -19f, -110f, 24f, 10f, 7f)
                .texOffs(448, 68).addBox(-10f, -18f, -116f, 20f, 8f, 6f)
                .texOffs(136, 87).addBox(-7f, -17f, -121f, 14f, 6f, 5f)
                .texOffs(42, 101).addBox(-4f, -16f, -125f, 8f, 4f, 4f)
                .texOffs(156, 110).addBox(10f, -13f, -111f, 2f, 2f, 1f)
                .texOffs(162, 110).addBox(-12f, -13f, -111f, 2f, 2f, 1f)
                .texOffs(290, 0).addBox(-14f, -29f, 72f, 28f, 18f, 12f)
                .texOffs(422, 0).addBox(-11f, -28f, 84f, 22f, 14f, 12f)
                .texOffs(192, 43).addBox(-7f, -27f, 96f, 14f, 10f, 12f)
                .texOffs(348, 68).addBox(-4f, -26f, 108f, 8f, 6f, 10f)
                .texOffs(52, 110).addBox(-2f, -25f, 118f, 4f, 4f, 2f)
                .texOffs(0, 0).addBox(-1f, -45f, 84f, 2f, 19f, 24f)
                .texOffs(156, 0).addBox(-1f, -59f, 90f, 2f, 14f, 18f)
                .texOffs(244, 43).addBox(-1f, -68f, 96f, 2f, 9f, 12f)
                .texOffs(76, 110).addBox(-1f, -70f, 100f, 2f, 2f, 2f)
                .texOffs(182, 68).addBox(10f, -28f, 90f, 16f, 2f, 16f)
                .texOffs(182, 68).addBox(-26f, -28f, 90f, 16f, 2f, 16f)
                .texOffs(0, 87).addBox(26f, -28f, 98f, 14f, 2f, 12f)
                .texOffs(0, 87).addBox(-40f, -28f, 98f, 14f, 2f, 12f)
                .texOffs(96, 87).addBox(40f, -28f, 102f, 10f, 2f, 10f)
                .texOffs(96, 87).addBox(-50f, -28f, 102f, 10f, 2f, 10f)
                .texOffs(52, 0).addBox(16f, -13f, -20f, 20f, 3f, 32f)
                .texOffs(52, 0).addBox(-36f, -13f, -20f, 20f, 3f, 32f)
                .texOffs(196, 0).addBox(36f, -13f, -8f, 20f, 3f, 27f)
                .texOffs(196, 0).addBox(-56f, -13f, -8f, 20f, 3f, 27f)
                .texOffs(0, 43).addBox(56f, -13f, 4f, 20f, 3f, 22f)
                .texOffs(0, 43).addBox(-76f, -13f, 4f, 20f, 3f, 22f)
                .texOffs(296, 43).addBox(76f, -13f, 16f, 20f, 2f, 17f)
                .texOffs(296, 43).addBox(-96f, -13f, 16f, 20f, 2f, 17f)
                .texOffs(384, 68).addBox(96f, -13f, 28f, 20f, 2f, 12f)
                .texOffs(384, 68).addBox(-116f, -13f, 28f, 20f, 2f, 12f)
                .texOffs(272, 43).addBox(114f, -23f, 30f, 2f, 10f, 10f)
                .texOffs(272, 43).addBox(-116f, -23f, 30f, 2f, 10f, 10f)
                .texOffs(84, 110).addBox(116f, -13f, 35f, 2f, 2f, 2f)
                .texOffs(92, 110).addBox(-118f, -13f, 35f, 2f, 2f, 2f)
                .texOffs(168, 110).addBox(16f, -13f, -21f, 2f, 2f, 1f)
                .texOffs(168, 110).addBox(-18f, -13f, -21f, 2f, 2f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_frame = p_body.addOrReplaceChild("ws_frame",
                CubeListBuilder.create()
                .texOffs(174, 87).addBox(13f, -9f, -1f, 2f, 9f, 2f)
                .texOffs(182, 87).addBox(-1f, -9f, -1f, 2f, 9f, 2f)
                .texOffs(190, 87).addBox(-15f, -9f, -1f, 2f, 9f, 2f)
                .texOffs(100, 110).addBox(-13f, -1f, -1f, 26f, 1f, 2f),
                PartPose.offsetAndRotation(0f, -20f, -97f, -0.524f, 0f, 0f));
        PartDefinition p_eng0 = p_body.addOrReplaceChild("eng0",
                CubeListBuilder.create()
                .texOffs(84, 43).addBox(-5f, -3.5f, 0f, 10f, 7f, 18f)
                .texOffs(370, 0).addBox(-4f, -4.5f, 0f, 8f, 9f, 18f)
                .texOffs(84, 101).addBox(-3f, -3f, 18f, 6f, 6f, 2f)
                .texOffs(64, 110).addBox(-2f, -2f, 20f, 4f, 4f, 2f),
                PartPose.offset(40f, -7.5f, -18f));
        PartDefinition p_fan0 = p_eng0.addOrReplaceChild("fan0",
                CubeListBuilder.create()
                .texOffs(66, 101).addBox(-4f, -3.5f, -0.5f, 8f, 7f, 1f),
                PartPose.offset(0f, 0f, -0.5f));
        PartDefinition p_eng1 = p_body.addOrReplaceChild("eng1",
                CubeListBuilder.create()
                .texOffs(84, 43).addBox(-5f, -3.5f, 0f, 10f, 7f, 18f)
                .texOffs(370, 0).addBox(-4f, -4.5f, 0f, 8f, 9f, 18f)
                .texOffs(84, 101).addBox(-3f, -3f, 18f, 6f, 6f, 2f)
                .texOffs(64, 110).addBox(-2f, -2f, 20f, 4f, 4f, 2f),
                PartPose.offset(68f, -7.5f, -6f));
        PartDefinition p_fan1 = p_eng1.addOrReplaceChild("fan1",
                CubeListBuilder.create()
                .texOffs(66, 101).addBox(-4f, -3.5f, -0.5f, 8f, 7f, 1f),
                PartPose.offset(0f, 0f, -0.5f));
        PartDefinition p_eng2 = p_body.addOrReplaceChild("eng2",
                CubeListBuilder.create()
                .texOffs(84, 43).addBox(-5f, -3.5f, 0f, 10f, 7f, 18f)
                .texOffs(370, 0).addBox(-4f, -4.5f, 0f, 8f, 9f, 18f)
                .texOffs(84, 101).addBox(-3f, -3f, 18f, 6f, 6f, 2f)
                .texOffs(64, 110).addBox(-2f, -2f, 20f, 4f, 4f, 2f),
                PartPose.offset(-40f, -7.5f, -18f));
        PartDefinition p_fan2 = p_eng2.addOrReplaceChild("fan2",
                CubeListBuilder.create()
                .texOffs(66, 101).addBox(-4f, -3.5f, -0.5f, 8f, 7f, 1f),
                PartPose.offset(0f, 0f, -0.5f));
        PartDefinition p_eng3 = p_body.addOrReplaceChild("eng3",
                CubeListBuilder.create()
                .texOffs(84, 43).addBox(-5f, -3.5f, 0f, 10f, 7f, 18f)
                .texOffs(370, 0).addBox(-4f, -4.5f, 0f, 8f, 9f, 18f)
                .texOffs(84, 101).addBox(-3f, -3f, 18f, 6f, 6f, 2f)
                .texOffs(64, 110).addBox(-2f, -2f, 20f, 4f, 4f, 2f),
                PartPose.offset(-68f, -7.5f, -6f));
        PartDefinition p_fan3 = p_eng3.addOrReplaceChild("fan3",
                CubeListBuilder.create()
                .texOffs(66, 101).addBox(-4f, -3.5f, -0.5f, 8f, 7f, 1f),
                PartPose.offset(0f, 0f, -0.5f));
        PartDefinition p_gear_ml = p_body.addOrReplaceChild("gear_ml",
                CubeListBuilder.create()
                .texOffs(198, 87).addBox(-1f, 0f, -1f, 2f, 9f, 2f)
                .texOffs(0, 101).addBox(-1f, 8f, -4f, 2f, 1f, 8f)
                .texOffs(174, 110).addBox(-3f, 8f, -3.5f, 6f, 1f, 1f)
                .texOffs(174, 110).addBox(-3f, 8f, 2.5f, 6f, 1f, 1f),
                PartPose.offset(9f, -12f, 6f));
        PartDefinition p_w_gear_ml_00 = p_gear_ml.addOrReplaceChild("w_gear_ml_00",
                CubeListBuilder.create(),
                PartPose.offset(2.5f, 8.5f, -3f));
        PartDefinition p_w_gear_ml_00_t0 = p_w_gear_ml_00.addOrReplaceChild("w_gear_ml_00_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_ml_00_t1 = p_w_gear_ml_00.addOrReplaceChild("w_gear_ml_00_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_ml_00_t2 = p_w_gear_ml_00.addOrReplaceChild("w_gear_ml_00_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_ml_00_t3 = p_w_gear_ml_00.addOrReplaceChild("w_gear_ml_00_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_ml_01 = p_gear_ml.addOrReplaceChild("w_gear_ml_01",
                CubeListBuilder.create(),
                PartPose.offset(2.5f, 8.5f, 3f));
        PartDefinition p_w_gear_ml_01_t0 = p_w_gear_ml_01.addOrReplaceChild("w_gear_ml_01_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_ml_01_t1 = p_w_gear_ml_01.addOrReplaceChild("w_gear_ml_01_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_ml_01_t2 = p_w_gear_ml_01.addOrReplaceChild("w_gear_ml_01_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_ml_01_t3 = p_w_gear_ml_01.addOrReplaceChild("w_gear_ml_01_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_ml_10 = p_gear_ml.addOrReplaceChild("w_gear_ml_10",
                CubeListBuilder.create(),
                PartPose.offset(-2.5f, 8.5f, -3f));
        PartDefinition p_w_gear_ml_10_t0 = p_w_gear_ml_10.addOrReplaceChild("w_gear_ml_10_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_ml_10_t1 = p_w_gear_ml_10.addOrReplaceChild("w_gear_ml_10_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_ml_10_t2 = p_w_gear_ml_10.addOrReplaceChild("w_gear_ml_10_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_ml_10_t3 = p_w_gear_ml_10.addOrReplaceChild("w_gear_ml_10_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_ml_11 = p_gear_ml.addOrReplaceChild("w_gear_ml_11",
                CubeListBuilder.create(),
                PartPose.offset(-2.5f, 8.5f, 3f));
        PartDefinition p_w_gear_ml_11_t0 = p_w_gear_ml_11.addOrReplaceChild("w_gear_ml_11_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_ml_11_t1 = p_w_gear_ml_11.addOrReplaceChild("w_gear_ml_11_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_ml_11_t2 = p_w_gear_ml_11.addOrReplaceChild("w_gear_ml_11_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_ml_11_t3 = p_w_gear_ml_11.addOrReplaceChild("w_gear_ml_11_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_gear_mr = p_body.addOrReplaceChild("gear_mr",
                CubeListBuilder.create()
                .texOffs(198, 87).addBox(-1f, 0f, -1f, 2f, 9f, 2f)
                .texOffs(0, 101).addBox(-1f, 8f, -4f, 2f, 1f, 8f)
                .texOffs(174, 110).addBox(-3f, 8f, -3.5f, 6f, 1f, 1f)
                .texOffs(174, 110).addBox(-3f, 8f, 2.5f, 6f, 1f, 1f),
                PartPose.offset(-9f, -12f, 6f));
        PartDefinition p_w_gear_mr_00 = p_gear_mr.addOrReplaceChild("w_gear_mr_00",
                CubeListBuilder.create(),
                PartPose.offset(2.5f, 8.5f, -3f));
        PartDefinition p_w_gear_mr_00_t0 = p_w_gear_mr_00.addOrReplaceChild("w_gear_mr_00_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_mr_00_t1 = p_w_gear_mr_00.addOrReplaceChild("w_gear_mr_00_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_mr_00_t2 = p_w_gear_mr_00.addOrReplaceChild("w_gear_mr_00_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_mr_00_t3 = p_w_gear_mr_00.addOrReplaceChild("w_gear_mr_00_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_mr_01 = p_gear_mr.addOrReplaceChild("w_gear_mr_01",
                CubeListBuilder.create(),
                PartPose.offset(2.5f, 8.5f, 3f));
        PartDefinition p_w_gear_mr_01_t0 = p_w_gear_mr_01.addOrReplaceChild("w_gear_mr_01_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_mr_01_t1 = p_w_gear_mr_01.addOrReplaceChild("w_gear_mr_01_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_mr_01_t2 = p_w_gear_mr_01.addOrReplaceChild("w_gear_mr_01_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_mr_01_t3 = p_w_gear_mr_01.addOrReplaceChild("w_gear_mr_01_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_mr_10 = p_gear_mr.addOrReplaceChild("w_gear_mr_10",
                CubeListBuilder.create(),
                PartPose.offset(-2.5f, 8.5f, -3f));
        PartDefinition p_w_gear_mr_10_t0 = p_w_gear_mr_10.addOrReplaceChild("w_gear_mr_10_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_mr_10_t1 = p_w_gear_mr_10.addOrReplaceChild("w_gear_mr_10_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_mr_10_t2 = p_w_gear_mr_10.addOrReplaceChild("w_gear_mr_10_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_mr_10_t3 = p_w_gear_mr_10.addOrReplaceChild("w_gear_mr_10_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_gear_mr_11 = p_gear_mr.addOrReplaceChild("w_gear_mr_11",
                CubeListBuilder.create(),
                PartPose.offset(-2.5f, 8.5f, 3f));
        PartDefinition p_w_gear_mr_11_t0 = p_w_gear_mr_11.addOrReplaceChild("w_gear_mr_11_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_gear_mr_11_t1 = p_w_gear_mr_11.addOrReplaceChild("w_gear_mr_11_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_gear_mr_11_t2 = p_w_gear_mr_11.addOrReplaceChild("w_gear_mr_11_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_gear_mr_11_t3 = p_w_gear_mr_11.addOrReplaceChild("w_gear_mr_11_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_gear_nose = p_body.addOrReplaceChild("gear_nose",
                CubeListBuilder.create()
                .texOffs(34, 101).addBox(-1f, 0f, -1f, 2f, 7f, 2f)
                .texOffs(174, 110).addBox(-3f, 6f, -0.5f, 6f, 1f, 1f),
                PartPose.offset(0f, -10f, -70f));
        PartDefinition p_w_nose_0 = p_gear_nose.addOrReplaceChild("w_nose_0",
                CubeListBuilder.create(),
                PartPose.offset(2f, 6.5f, 0f));
        PartDefinition p_w_nose_0_t0 = p_w_nose_0.addOrReplaceChild("w_nose_0_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_nose_0_t1 = p_w_nose_0.addOrReplaceChild("w_nose_0_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_nose_0_t2 = p_w_nose_0.addOrReplaceChild("w_nose_0_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_nose_0_t3 = p_w_nose_0.addOrReplaceChild("w_nose_0_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_w_nose_1 = p_gear_nose.addOrReplaceChild("w_nose_1",
                CubeListBuilder.create(),
                PartPose.offset(-2f, 6.5f, 0f));
        PartDefinition p_w_nose_1_t0 = p_w_nose_1.addOrReplaceChild("w_nose_1_t0",
                CubeListBuilder.create()
                .texOffs(206, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w_nose_1_t1 = p_w_nose_1.addOrReplaceChild("w_nose_1_t1",
                CubeListBuilder.create()
                .texOffs(216, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w_nose_1_t2 = p_w_nose_1.addOrReplaceChild("w_nose_1_t2",
                CubeListBuilder.create()
                .texOffs(226, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.571f, 0f, 0f));
        PartDefinition p_w_nose_1_t3 = p_w_nose_1.addOrReplaceChild("w_nose_1_t3",
                CubeListBuilder.create()
                .texOffs(236, 87).addBox(-1f, -3.5f, -1.5f, 2f, 7f, 3f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 2.356f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(100, 101).addBox(15f, -21f, -95f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -95f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -89f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -89f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -83f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -83f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -77f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -77f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -71f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -71f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -53f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -53f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -47f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -47f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -41f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -41f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -35f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -35f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -29f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -29f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -23f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -23f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -17f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -17f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -11f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -11f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, -5f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, -5f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 1f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 1f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 7f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 7f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 13f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 13f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 19f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 19f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 25f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 25f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 31f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 31f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 37f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 37f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 43f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 43f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 49f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 49f, 1f, 4f, 4f)
                .texOffs(100, 101).addBox(15f, -21f, 67f, 1f, 4f, 4f)
                .texOffs(110, 101).addBox(-16f, -21f, 67f, 1f, 4f, 4f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_ws_glass = p_glass.addOrReplaceChild("ws_glass",
                CubeListBuilder.create()
                .texOffs(452, 87).addBox(1f, -8.5f, -0.5f, 12f, 8f, 1f)
                .texOffs(478, 87).addBox(-13f, -8.5f, -0.5f, 12f, 8f, 1f),
                PartPose.offsetAndRotation(0f, -20f, -97f, -0.524f, 0f, 0f));
        return LayerDefinition.create(mesh, 512, 512);
    }

    /** fanAngle = giro de las turbinas (rad); wheelAngle = giro de las ruedas (rad); gear = 0 tren abajo .. 1 tren arriba. */
    public void pose(float fanAngle, float wheelAngle, float gear) {
        for (ModelPart fan : this.fans) {
            fan.zRot = fanAngle;
        }
        for (ModelPart wheel : this.wheels) {
            wheel.xRot = wheelAngle;
        }
        float fold = gear * 1.5708f;
        this.gearLeft.zRot = 1.0f * fold;
        this.gearRight.zRot = -1.0f * fold;
        this.gearNose.xRot = 1.0f * fold;
    }

    /** false = solo la carrocería y el interior (opaco); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }

    @Override
    public void setupAnim(AirlinerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // La pose se aplica desde el renderizador con pose(...), que sabe interpolar entre ticks.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
