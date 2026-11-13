package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
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
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Locomotoras y vagones del tren de carga (V4.8.0) y del de pasajeros (V5.0.0). ARCHIVO GENERADO por tools/v480/trains.py: no editar a
 * mano, se pierde al regenerar. Atlas de 1024x512. 1 unidad = 1/8 de bloque (el renderizador lo agranda 4 veces).
 * Cada variante tiene dos árboles ("body" opaco y "glass" vidrios) y ruedas "w0".."wN" (hijas de "body") que giran en x.
 */
public class TrainCarModel extends EntityModel<Entity> {
    /** Cuántas veces el renderizador agranda el modelo. */
    public static final float SCALE = 2.0f;
    /** Cuánto gira cada rueda respecto a una de radio 1/2 m (las motrices grandes giran más despacio). */
    private static final Map<String, float[]> RATIOS = new HashMap<>();

    static {
        RATIOS.put("freight_loco", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("coal_wagon", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("food_wagon", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("mineral_wagon", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("steam_classic", new float[] {0.5714f, 0.5714f, 0.5714f, 0.5714f, 0.5714f, 0.5714f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("steam_modern", new float[] {0.5714f, 0.5714f, 0.5714f, 0.5714f, 0.5714f, 0.5714f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("coach_low", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("coach_mid", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("coach_high", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("metro_head", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
        RATIOS.put("metro_car", new float[] {1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f});
    }

    /** Capa del modelo del coche con ese id (el mismo de la variante). */
    public static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, id), "main");
    }

    public static LayerDefinition create(String id) {
        return switch (id) {
            case "freight_loco" -> createFreightLocoLayer();
            case "coal_wagon" -> createCoalWagonLayer();
            case "food_wagon" -> createFoodWagonLayer();
            case "mineral_wagon" -> createMineralWagonLayer();
            case "steam_classic" -> createSteamClassicLayer();
            case "steam_modern" -> createSteamModernLayer();
            case "coach_low" -> createCoachLowLayer();
            case "coach_mid" -> createCoachMidLayer();
            case "coach_high" -> createCoachHighLayer();
            case "metro_head" -> createMetroHeadLayer();
            case "metro_car" -> createMetroCarLayer();
            default -> throw new IllegalArgumentException(id);
        };
    }

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart glass;
    private final List<ModelPart> wheels = new ArrayList<>();
    private final float[] ratio;

    public TrainCarModel(ModelPart root, String id) {
        this.root = root;
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
        this.ratio = RATIOS.get(id);
        for (int i = 0; this.body.hasChild("w" + i); i++) {
            this.wheels.add(this.body.getChild("w" + i));
        }
    }

    /** freight_loco */
    private static LayerDefinition createFreightLocoLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(470, 321).addBox(-9f, -7f, 29f, 18f, 2f, 30f)
                .texOffs(566, 321).addBox(3f, -7f, 30f, 1f, 4f, 28f)
                .texOffs(566, 321).addBox(-4f, -7f, 30f, 1f, 4f, 28f)
                .texOffs(470, 321).addBox(-9f, -7f, -59f, 18f, 2f, 30f)
                .texOffs(566, 321).addBox(3f, -7f, -58f, 1f, 4f, 28f)
                .texOffs(566, 321).addBox(-4f, -7f, -58f, 1f, 4f, 28f)
                .texOffs(310, 377).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(310, 377).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(406, 377).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(310, 377).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(310, 377).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(406, 377).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(0, 321).addBox(-8f, -6f, -26f, 16f, 4f, 52f)
                .texOffs(360, 0).addBox(-12f, -12f, -80f, 24f, 2f, 160f)
                .texOffs(136, 321).addBox(11f, -22f, 48f, 1f, 10f, 32f)
                .texOffs(404, 321).addBox(11f, -36f, 48f, 1f, 3f, 32f)
                .texOffs(978, 321).addBox(11f, -33f, 74f, 1f, 11f, 6f)
                .texOffs(978, 321).addBox(11f, -33f, 48f, 1f, 11f, 6f)
                .texOffs(0, 163).addBox(12f, -19f, -74f, 1f, 4f, 154f)
                .texOffs(136, 321).addBox(-12f, -22f, 48f, 1f, 10f, 32f)
                .texOffs(404, 321).addBox(-12f, -36f, 48f, 1f, 3f, 32f)
                .texOffs(978, 321).addBox(-12f, -33f, 74f, 1f, 11f, 6f)
                .texOffs(978, 321).addBox(-12f, -33f, 48f, 1f, 11f, 6f)
                .texOffs(0, 163).addBox(-13f, -19f, -74f, 1f, 4f, 154f)
                .texOffs(770, 321).addBox(-11f, -36f, 79f, 22f, 24f, 1f)
                .texOffs(138, 377).addBox(-11f, -22f, 48f, 22f, 10f, 1f)
                .texOffs(444, 377).addBox(-11f, -36f, 48f, 22f, 2f, 1f)
                .texOffs(202, 321).addBox(-13f, -38f, 46f, 26f, 2f, 36f)
                .texOffs(326, 321).addBox(10f, -39f, 46f, 3f, 1f, 36f)
                .texOffs(326, 321).addBox(-13f, -39f, 46f, 3f, 1f, 36f)
                .texOffs(930, 321).addBox(-5f, -41f, 56f, 10f, 3f, 14f)
                .texOffs(624, 321).addBox(-11f, -13f, 49f, 22f, 1f, 30f)
                .texOffs(70, 377).addBox(-9f, -21f, 50f, 18f, 8f, 4f)
                .texOffs(332, 377).addBox(-9f, -22f, 50f, 18f, 1f, 4f)
                .texOffs(376, 377).addBox(-2f, -23f, 50f, 4f, 1f, 4f)
                .texOffs(436, 377).addBox(-1f, -25f, 51f, 2f, 2f, 2f)
                .texOffs(490, 377).addBox(-8f, -24f, 52f, 2f, 2f, 1f)
                .texOffs(490, 377).addBox(-5f, -24f, 52f, 2f, 2f, 1f)
                .texOffs(490, 377).addBox(4f, -24f, 52f, 2f, 2f, 1f)
                .texOffs(490, 377).addBox(7f, -24f, 52f, 2f, 2f, 1f)
                .texOffs(392, 377).addBox(5f, -23f, 50f, 3f, 1f, 4f)
                .texOffs(328, 377).addBox(6f, -28f, 52f, 1f, 5f, 1f)
                .texOffs(392, 377).addBox(-8f, -23f, 50f, 3f, 1f, 4f)
                .texOffs(328, 377).addBox(-7f, -28f, 52f, 1f, 5f, 1f)
                .texOffs(0, 377).addBox(3f, -22f, 62f, 6f, 9f, 6f)
                .texOffs(282, 377).addBox(3f, -30f, 68f, 6f, 8f, 1f)
                .texOffs(0, 377).addBox(-9f, -22f, 62f, 6f, 9f, 6f)
                .texOffs(282, 377).addBox(-9f, -30f, 68f, 6f, 8f, 1f)
                .texOffs(254, 377).addBox(-3f, -36f, 58f, 6f, 1f, 8f)
                .texOffs(892, 321).addBox(-9f, -30f, 78f, 18f, 17f, 1f)
                .texOffs(310, 163).addBox(-10f, -22f, -60f, 20f, 10f, 108f)
                .texOffs(778, 163).addBox(-8f, -24f, -52f, 16f, 2f, 92f)
                .texOffs(816, 321).addBox(-11f, -22f, -74f, 22f, 10f, 14f)
                .texOffs(188, 377).addBox(-8f, -22f, -75f, 16f, 8f, 1f)
                .texOffs(416, 377).addBox(7f, -24f, -76f, 3f, 3f, 2f)
                .texOffs(416, 377).addBox(-10f, -24f, -76f, 3f, 3f, 2f)
                .texOffs(426, 377).addBox(6f, -17f, 80f, 3f, 3f, 2f)
                .texOffs(426, 377).addBox(-9f, -17f, 80f, 3f, 3f, 2f)
                .texOffs(114, 377).addBox(-3f, -30f, -6f, 6f, 6f, 6f)
                .texOffs(222, 377).addBox(-4f, -31f, -7f, 8f, 1f, 8f)
                .texOffs(114, 377).addBox(-3f, -30f, -30f, 6f, 6f, 6f)
                .texOffs(222, 377).addBox(-4f, -31f, -31f, 8f, 1f, 8f)
                .texOffs(184, 377).addBox(11f, -21f, 43f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, 27f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, 11f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, -5f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, -21f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, -37f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(11f, -21f, -53f, 1f, 9f, 1f)
                .texOffs(566, 163).addBox(11f, -22f, -61f, 1f, 1f, 105f)
                .texOffs(888, 321).addBox(13f, -30f, 75f, 1f, 18f, 1f)
                .texOffs(888, 321).addBox(13f, -30f, 70f, 1f, 18f, 1f)
                .texOffs(296, 377).addBox(13f, -16f, 70f, 1f, 1f, 6f)
                .texOffs(296, 377).addBox(13f, -21f, 70f, 1f, 1f, 6f)
                .texOffs(296, 377).addBox(13f, -26f, 70f, 1f, 1f, 6f)
                .texOffs(184, 377).addBox(-12f, -21f, 43f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, 27f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, 11f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, -5f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, -21f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, -37f, 1f, 9f, 1f)
                .texOffs(184, 377).addBox(-12f, -21f, -53f, 1f, 9f, 1f)
                .texOffs(566, 163).addBox(-12f, -22f, -61f, 1f, 1f, 105f)
                .texOffs(888, 321).addBox(-14f, -30f, 75f, 1f, 18f, 1f)
                .texOffs(888, 321).addBox(-14f, -30f, 70f, 1f, 18f, 1f)
                .texOffs(296, 377).addBox(-14f, -16f, 70f, 1f, 1f, 6f)
                .texOffs(296, 377).addBox(-14f, -21f, 70f, 1f, 1f, 6f)
                .texOffs(296, 377).addBox(-14f, -26f, 70f, 1f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 54f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 54f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 34f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 34f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -34f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -34f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -54f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(320, 377).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -54f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(992, 321).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(728, 321).addBox(11f, -33f, 54f, 1f, 11f, 20f)
                .texOffs(728, 321).addBox(-12f, -33f, 54f, 1f, 11f, 20f)
                .texOffs(24, 377).addBox(-11f, -34f, 48f, 22f, 12f, 1f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** coal_wagon */
    private static LayerDefinition createCoalWagonLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(226, 0).addBox(-10f, -9f, -56f, 20f, 3f, 112f)
                .texOffs(378, 128).addBox(-9f, -7f, 21f, 18f, 2f, 30f)
                .texOffs(474, 128).addBox(3f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(474, 128).addBox(-4f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(378, 128).addBox(-9f, -7f, -51f, 18f, 2f, 30f)
                .texOffs(474, 128).addBox(3f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(474, 128).addBox(-4f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(624, 128).addBox(-7f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(624, 128).addBox(5f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(642, 128).addBox(-1f, -9f, 57f, 2f, 2f, 3f)
                .texOffs(624, 128).addBox(-7f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(624, 128).addBox(5f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(642, 128).addBox(-1f, -9f, -60f, 2f, 2f, 3f)
                .texOffs(490, 0).addBox(-12f, -11f, -56f, 24f, 2f, 112f)
                .texOffs(0, 0).addBox(11f, -27f, -56f, 1f, 16f, 112f)
                .texOffs(762, 0).addBox(10f, -28f, -56f, 2f, 1f, 112f)
                .texOffs(532, 128).addBox(12f, -27f, 47f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, 33f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, 19f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, 5f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, -9f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, -23f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, -37f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(12f, -27f, -51f, 1f, 16f, 3f)
                .texOffs(0, 128).addBox(12f, -23f, -48f, 1f, 4f, 96f)
                .texOffs(0, 0).addBox(-12f, -27f, -56f, 1f, 16f, 112f)
                .texOffs(762, 0).addBox(-12f, -28f, -56f, 2f, 1f, 112f)
                .texOffs(532, 128).addBox(-13f, -27f, 47f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, 33f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, 19f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, 5f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, -9f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, -23f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, -37f, 1f, 16f, 3f)
                .texOffs(532, 128).addBox(-13f, -27f, -51f, 1f, 16f, 3f)
                .texOffs(0, 128).addBox(-13f, -23f, -48f, 1f, 4f, 96f)
                .texOffs(540, 128).addBox(-11f, -27f, -56f, 22f, 16f, 1f)
                .texOffs(652, 128).addBox(-12f, -28f, -57f, 24f, 1f, 2f)
                .texOffs(540, 128).addBox(-11f, -27f, 55f, 22f, 16f, 1f)
                .texOffs(652, 128).addBox(-12f, -28f, 55f, 24f, 1f, 2f)
                .texOffs(194, 128).addBox(8f, -11f, -44f, 4f, 4f, 88f)
                .texOffs(194, 128).addBox(-12f, -11f, -44f, 4f, 4f, 88f)
                .texOffs(586, 128).addBox(13f, -27f, 51f, 1f, 16f, 1f)
                .texOffs(586, 128).addBox(13f, -27f, 46f, 1f, 16f, 1f)
                .texOffs(610, 128).addBox(13f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(13f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(13f, -25f, 46f, 1f, 1f, 6f)
                .texOffs(586, 128).addBox(13f, -27f, -47f, 1f, 16f, 1f)
                .texOffs(586, 128).addBox(13f, -27f, -52f, 1f, 16f, 1f)
                .texOffs(610, 128).addBox(13f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(13f, -20f, -52f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(13f, -25f, -52f, 1f, 1f, 6f)
                .texOffs(586, 128).addBox(-14f, -27f, 51f, 1f, 16f, 1f)
                .texOffs(586, 128).addBox(-14f, -27f, 46f, 1f, 16f, 1f)
                .texOffs(610, 128).addBox(-14f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(-14f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(-14f, -25f, 46f, 1f, 1f, 6f)
                .texOffs(586, 128).addBox(-14f, -27f, -47f, 1f, 16f, 1f)
                .texOffs(586, 128).addBox(-14f, -27f, -52f, 1f, 16f, 1f)
                .texOffs(610, 128).addBox(-14f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(-14f, -20f, -52f, 1f, 1f, 6f)
                .texOffs(610, 128).addBox(-14f, -25f, -52f, 1f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 26f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 26f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -26f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -26f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(634, 128).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(590, 128).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create(),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** food_wagon */
    private static LayerDefinition createFoodWagonLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10f, -9f, -56f, 20f, 3f, 112f)
                .texOffs(0, 115).addBox(-9f, -7f, 21f, 18f, 2f, 30f)
                .texOffs(96, 115).addBox(3f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(96, 115).addBox(-4f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(0, 115).addBox(-9f, -7f, -51f, 18f, 2f, 30f)
                .texOffs(96, 115).addBox(3f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(96, 115).addBox(-4f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(244, 115).addBox(-7f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(244, 115).addBox(5f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(262, 115).addBox(-1f, -9f, 57f, 2f, 2f, 3f)
                .texOffs(244, 115).addBox(-7f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(244, 115).addBox(5f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(262, 115).addBox(-1f, -9f, -60f, 2f, 2f, 3f)
                .texOffs(490, 0).addBox(-12f, -11f, -56f, 24f, 2f, 112f)
                .texOffs(264, 0).addBox(11f, -14f, -56f, 1f, 3f, 112f)
                .texOffs(264, 0).addBox(11f, -18f, -56f, 1f, 3f, 112f)
                .texOffs(762, 0).addBox(11f, -21f, -56f, 1f, 2f, 112f)
                .texOffs(220, 115).addBox(12f, -22f, 54f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, 40f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, 26f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, 12f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, -2f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, -16f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, -30f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(12f, -22f, -44f, 1f, 11f, 2f)
                .texOffs(264, 0).addBox(-12f, -14f, -56f, 1f, 3f, 112f)
                .texOffs(264, 0).addBox(-12f, -18f, -56f, 1f, 3f, 112f)
                .texOffs(762, 0).addBox(-12f, -21f, -56f, 1f, 2f, 112f)
                .texOffs(220, 115).addBox(-13f, -22f, 54f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, 40f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, 26f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, 12f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, -2f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, -16f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, -30f, 1f, 11f, 2f)
                .texOffs(220, 115).addBox(-13f, -22f, -44f, 1f, 11f, 2f)
                .texOffs(154, 115).addBox(-11f, -29f, -56f, 22f, 18f, 1f)
                .texOffs(272, 115).addBox(-12f, -30f, -57f, 24f, 1f, 2f)
                .texOffs(154, 115).addBox(-11f, -29f, 55f, 22f, 18f, 1f)
                .texOffs(272, 115).addBox(-12f, -30f, 55f, 24f, 1f, 2f)
                .texOffs(226, 115).addBox(13f, -21f, 51f, 1f, 10f, 1f)
                .texOffs(226, 115).addBox(13f, -21f, 46f, 1f, 10f, 1f)
                .texOffs(230, 115).addBox(13f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(230, 115).addBox(13f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(226, 115).addBox(13f, -21f, -47f, 1f, 10f, 1f)
                .texOffs(226, 115).addBox(13f, -21f, -52f, 1f, 10f, 1f)
                .texOffs(230, 115).addBox(13f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(230, 115).addBox(13f, -20f, -52f, 1f, 1f, 6f)
                .texOffs(226, 115).addBox(-14f, -21f, 51f, 1f, 10f, 1f)
                .texOffs(226, 115).addBox(-14f, -21f, 46f, 1f, 10f, 1f)
                .texOffs(230, 115).addBox(-14f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(230, 115).addBox(-14f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(226, 115).addBox(-14f, -21f, -47f, 1f, 10f, 1f)
                .texOffs(226, 115).addBox(-14f, -21f, -52f, 1f, 10f, 1f)
                .texOffs(230, 115).addBox(-14f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(230, 115).addBox(-14f, -20f, -52f, 1f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 26f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 26f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -26f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -26f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(254, 115).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(200, 115).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create(),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** mineral_wagon */
    private static LayerDefinition createMineralWagonLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(226, 0).addBox(-10f, -9f, -56f, 20f, 3f, 112f)
                .texOffs(194, 132).addBox(-9f, -7f, 21f, 18f, 2f, 30f)
                .texOffs(290, 132).addBox(3f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(290, 132).addBox(-4f, -7f, 22f, 1f, 4f, 28f)
                .texOffs(194, 132).addBox(-9f, -7f, -51f, 18f, 2f, 30f)
                .texOffs(290, 132).addBox(3f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(290, 132).addBox(-4f, -7f, -50f, 1f, 4f, 28f)
                .texOffs(446, 132).addBox(-7f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(446, 132).addBox(5f, -10f, 56f, 2f, 3f, 3f)
                .texOffs(464, 132).addBox(-1f, -9f, 57f, 2f, 2f, 3f)
                .texOffs(446, 132).addBox(-7f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(446, 132).addBox(5f, -10f, -59f, 2f, 3f, 3f)
                .texOffs(464, 132).addBox(-1f, -9f, -60f, 2f, 2f, 3f)
                .texOffs(490, 0).addBox(-12f, -11f, -56f, 24f, 2f, 112f)
                .texOffs(0, 0).addBox(11f, -31f, -56f, 1f, 20f, 112f)
                .texOffs(762, 0).addBox(10f, -32f, -56f, 2f, 1f, 112f)
                .texOffs(348, 132).addBox(12f, -31f, 47f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, 33f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, 19f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, 5f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, -9f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, -23f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, -37f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(12f, -31f, -51f, 1f, 20f, 3f)
                .texOffs(0, 132).addBox(12f, -25f, -48f, 1f, 4f, 96f)
                .texOffs(0, 0).addBox(-12f, -31f, -56f, 1f, 20f, 112f)
                .texOffs(762, 0).addBox(-12f, -32f, -56f, 2f, 1f, 112f)
                .texOffs(348, 132).addBox(-13f, -31f, 47f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, 33f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, 19f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, 5f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, -9f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, -23f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, -37f, 1f, 20f, 3f)
                .texOffs(348, 132).addBox(-13f, -31f, -51f, 1f, 20f, 3f)
                .texOffs(0, 132).addBox(-13f, -25f, -48f, 1f, 4f, 96f)
                .texOffs(362, 132).addBox(-11f, -31f, -56f, 22f, 20f, 1f)
                .texOffs(474, 132).addBox(-12f, -32f, -57f, 24f, 1f, 2f)
                .texOffs(362, 132).addBox(-11f, -31f, 55f, 22f, 20f, 1f)
                .texOffs(474, 132).addBox(-12f, -32f, 55f, 24f, 1f, 2f)
                .texOffs(356, 132).addBox(11f, -31f, 26f, 1f, 20f, 2f)
                .texOffs(356, 132).addBox(-12f, -31f, 26f, 1f, 20f, 2f)
                .texOffs(356, 132).addBox(11f, -31f, -2f, 1f, 20f, 2f)
                .texOffs(356, 132).addBox(-12f, -31f, -2f, 1f, 20f, 2f)
                .texOffs(356, 132).addBox(11f, -31f, -30f, 1f, 20f, 2f)
                .texOffs(356, 132).addBox(-12f, -31f, -30f, 1f, 20f, 2f)
                .texOffs(408, 132).addBox(13f, -31f, 51f, 1f, 20f, 1f)
                .texOffs(408, 132).addBox(13f, -31f, 46f, 1f, 20f, 1f)
                .texOffs(432, 132).addBox(13f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -25f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -30f, 46f, 1f, 1f, 6f)
                .texOffs(408, 132).addBox(13f, -31f, -47f, 1f, 20f, 1f)
                .texOffs(408, 132).addBox(13f, -31f, -52f, 1f, 20f, 1f)
                .texOffs(432, 132).addBox(13f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -20f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -25f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(13f, -30f, -52f, 1f, 1f, 6f)
                .texOffs(408, 132).addBox(-14f, -31f, 51f, 1f, 20f, 1f)
                .texOffs(408, 132).addBox(-14f, -31f, 46f, 1f, 20f, 1f)
                .texOffs(432, 132).addBox(-14f, -15f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -20f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -25f, 46f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -30f, 46f, 1f, 1f, 6f)
                .texOffs(408, 132).addBox(-14f, -31f, -47f, 1f, 20f, 1f)
                .texOffs(408, 132).addBox(-14f, -31f, -52f, 1f, 20f, 1f)
                .texOffs(432, 132).addBox(-14f, -15f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -20f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -25f, -52f, 1f, 1f, 6f)
                .texOffs(432, 132).addBox(-14f, -30f, -52f, 1f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 26f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 26f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -26f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -26f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(456, 132).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(412, 132).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create(),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** steam_classic */
    private static LayerDefinition createSteamClassicLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10f, -11f, -86f, 20f, 3f, 130f)
                .texOffs(540, 133).addBox(-10f, -9f, 46f, 20f, 3f, 42f)
                .texOffs(78, 195).addBox(12f, -8f, -44f, 1f, 2f, 36f)
                .texOffs(152, 195).addBox(13f, -8f, -60f, 1f, 2f, 34f)
                .texOffs(434, 195).addBox(11f, -12f, -68f, 3f, 7f, 12f)
                .texOffs(78, 195).addBox(-13f, -8f, -44f, 1f, 2f, 36f)
                .texOffs(152, 195).addBox(-14f, -8f, -60f, 1f, 2f, 34f)
                .texOffs(434, 195).addBox(-14f, -12f, -68f, 3f, 7f, 12f)
                .texOffs(414, 0).addBox(-12f, -12f, -20f, 24f, 2f, 64f)
                .texOffs(300, 0).addBox(11f, -26f, -20f, 1f, 14f, 56f)
                .texOffs(0, 133).addBox(11f, -44f, -20f, 1f, 6f, 56f)
                .texOffs(344, 195).addBox(11f, -38f, 26f, 1f, 12f, 10f)
                .texOffs(344, 195).addBox(11f, -38f, -20f, 1f, 12f, 10f)
                .texOffs(114, 133).addBox(12f, -22f, -20f, 1f, 4f, 56f)
                .texOffs(300, 0).addBox(-12f, -26f, -20f, 1f, 14f, 56f)
                .texOffs(0, 133).addBox(-12f, -44f, -20f, 1f, 6f, 56f)
                .texOffs(344, 195).addBox(-12f, -38f, 26f, 1f, 12f, 10f)
                .texOffs(344, 195).addBox(-12f, -38f, -20f, 1f, 12f, 10f)
                .texOffs(114, 133).addBox(-13f, -22f, -20f, 1f, 4f, 56f)
                .texOffs(590, 0).addBox(-13f, -46f, -22f, 26f, 2f, 62f)
                .texOffs(766, 0).addBox(10f, -47f, -22f, 3f, 1f, 62f)
                .texOffs(766, 0).addBox(-13f, -47f, -22f, 3f, 1f, 62f)
                .texOffs(314, 133).addBox(-11f, -13f, -19f, 22f, 1f, 54f)
                .texOffs(366, 195).addBox(-8f, -28f, -19f, 16f, 15f, 6f)
                .texOffs(752, 195).addBox(-2f, -31f, -18f, 4f, 3f, 4f)
                .texOffs(840, 195).addBox(-2f, -24f, -13f, 4f, 4f, 2f)
                .texOffs(890, 195).addBox(-1f, -23f, -11f, 2f, 2f, 2f)
                .texOffs(882, 195).addBox(-7f, -26f, -13f, 3f, 4f, 1f)
                .texOffs(882, 195).addBox(4f, -26f, -13f, 3f, 4f, 1f)
                .texOffs(746, 195).addBox(5f, -20f, -13f, 1f, 6f, 2f)
                .texOffs(746, 195).addBox(-6f, -20f, -13f, 1f, 6f, 2f)
                .texOffs(604, 195).addBox(3f, -22f, 6f, 6f, 9f, 6f)
                .texOffs(732, 195).addBox(3f, -30f, 12f, 6f, 8f, 1f)
                .texOffs(604, 195).addBox(-9f, -22f, 6f, 6f, 9f, 6f)
                .texOffs(732, 195).addBox(-9f, -30f, 12f, 6f, 8f, 1f)
                .texOffs(862, 195).addBox(6f, -17f, 88f, 3f, 3f, 2f)
                .texOffs(862, 195).addBox(-9f, -17f, 88f, 3f, 3f, 2f)
                .texOffs(410, 195).addBox(-3f, -47f, -76f, 6f, 14f, 6f)
                .texOffs(680, 195).addBox(-4f, -49f, -77f, 8f, 2f, 8f)
                .texOffs(468, 195).addBox(-4f, -43f, -44f, 8f, 10f, 8f)
                .texOffs(628, 195).addBox(-3f, -40f, -58f, 6f, 7f, 6f)
                .texOffs(712, 195).addBox(-3f, -40f, -82f, 6f, 5f, 4f)
                .texOffs(550, 195).addBox(-8f, -30f, -85f, 16f, 15f, 1f)
                .texOffs(228, 133).addBox(11f, -28f, 46f, 1f, 17f, 42f)
                .texOffs(796, 133).addBox(10f, -29f, 46f, 2f, 1f, 42f)
                .texOffs(884, 133).addBox(12f, -20f, 48f, 1f, 4f, 38f)
                .texOffs(228, 133).addBox(-12f, -28f, 46f, 1f, 17f, 42f)
                .texOffs(796, 133).addBox(-12f, -29f, 46f, 2f, 1f, 42f)
                .texOffs(884, 133).addBox(-13f, -20f, 48f, 1f, 4f, 38f)
                .texOffs(664, 133).addBox(-12f, -11f, 46f, 24f, 2f, 42f)
                .texOffs(254, 195).addBox(-11f, -34f, 46f, 22f, 23f, 1f)
                .texOffs(504, 195).addBox(-11f, -26f, 87f, 22f, 15f, 1f)
                .texOffs(652, 195).addBox(-2f, -11f, 36f, 4f, 2f, 10f)
                .texOffs(500, 195).addBox(12f, -28f, 49f, 1f, 16f, 1f)
                .texOffs(464, 195).addBox(13f, -30f, 33f, 1f, 18f, 1f)
                .texOffs(464, 195).addBox(13f, -30f, 28f, 1f, 18f, 1f)
                .texOffs(768, 195).addBox(13f, -16f, 28f, 1f, 1f, 6f)
                .texOffs(768, 195).addBox(13f, -21f, 28f, 1f, 1f, 6f)
                .texOffs(768, 195).addBox(13f, -26f, 28f, 1f, 1f, 6f)
                .texOffs(464, 195).addBox(-14f, -30f, 33f, 1f, 18f, 1f)
                .texOffs(464, 195).addBox(-14f, -30f, 28f, 1f, 18f, 1f)
                .texOffs(768, 195).addBox(-14f, -16f, 28f, 1f, 1f, 6f)
                .texOffs(768, 195).addBox(-14f, -21f, 28f, 1f, 1f, 6f)
                .texOffs(768, 195).addBox(-14f, -26f, 28f, 1f, 1f, 6f)
                .texOffs(852, 195).addBox(-7f, -10f, 88f, 2f, 3f, 3f)
                .texOffs(852, 195).addBox(5f, -10f, 88f, 2f, 3f, 3f)
                .texOffs(872, 195).addBox(-1f, -9f, 88f, 2f, 2f, 3f)
                .texOffs(852, 195).addBox(-7f, -10f, -89f, 2f, 3f, 3f)
                .texOffs(852, 195).addBox(5f, -10f, -89f, 2f, 3f, 3f)
                .texOffs(872, 195).addBox(-1f, -9f, -89f, 2f, 2f, 3f)
                .texOffs(782, 195).addBox(-10f, -8f, -91f, 20f, 3f, 3f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -8f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w0_t2 = p_w0.addOrReplaceChild("w0_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -8f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w1_t2 = p_w1.addOrReplaceChild("w1_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -26f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w2_t2 = p_w2.addOrReplaceChild("w2_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -26f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w3_t2 = p_w3.addOrReplaceChild("w3_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -44f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w4_t2 = p_w4.addOrReplaceChild("w4_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -44f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w5_t2 = p_w5.addOrReplaceChild("w5_t2",
                CubeListBuilder.create()
                .texOffs(222, 195).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(6f, -4f, -76f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-6f, -4f, -76f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w8 = p_body.addOrReplaceChild("w8",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 52f));
        PartDefinition p_w8_t0 = p_w8.addOrReplaceChild("w8_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w8_t1 = p_w8.addOrReplaceChild("w8_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w9 = p_body.addOrReplaceChild("w9",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 52f));
        PartDefinition p_w9_t0 = p_w9.addOrReplaceChild("w9_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w9_t1 = p_w9.addOrReplaceChild("w9_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w10 = p_body.addOrReplaceChild("w10",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 62f));
        PartDefinition p_w10_t0 = p_w10.addOrReplaceChild("w10_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w10_t1 = p_w10.addOrReplaceChild("w10_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w11 = p_body.addOrReplaceChild("w11",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 62f));
        PartDefinition p_w11_t0 = p_w11.addOrReplaceChild("w11_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w11_t1 = p_w11.addOrReplaceChild("w11_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w12 = p_body.addOrReplaceChild("w12",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 72f));
        PartDefinition p_w12_t0 = p_w12.addOrReplaceChild("w12_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w12_t1 = p_w12.addOrReplaceChild("w12_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w13 = p_body.addOrReplaceChild("w13",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 72f));
        PartDefinition p_w13_t0 = p_w13.addOrReplaceChild("w13_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w13_t1 = p_w13.addOrReplaceChild("w13_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w14 = p_body.addOrReplaceChild("w14",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 82f));
        PartDefinition p_w14_t0 = p_w14.addOrReplaceChild("w14_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w14_t1 = p_w14.addOrReplaceChild("w14_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w15 = p_body.addOrReplaceChild("w15",
                CubeListBuilder.create()
                .texOffs(828, 195).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 82f));
        PartDefinition p_w15_t0 = p_w15.addOrReplaceChild("w15_t0",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w15_t1 = p_w15.addOrReplaceChild("w15_t1",
                CubeListBuilder.create()
                .texOffs(584, 195).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_boiler_r0 = p_body.addOrReplaceChild("boiler_r0",
                CubeListBuilder.create()
                .texOffs(896, 0).addBox(-9f, -9f, -22f, 18f, 18f, 44f),
                PartPose.offset(0f, -22f, -42f));
        PartDefinition p_boiler_r1 = p_body.addOrReplaceChild("boiler_r1",
                CubeListBuilder.create()
                .texOffs(896, 0).addBox(-9f, -9f, -22f, 18f, 18f, 44f),
                PartPose.offsetAndRotation(0f, -22f, -42f, 0f, 0f, 0.524f));
        PartDefinition p_boiler_r2 = p_body.addOrReplaceChild("boiler_r2",
                CubeListBuilder.create()
                .texOffs(896, 0).addBox(-9f, -9f, -22f, 18f, 18f, 44f),
                PartPose.offsetAndRotation(0f, -22f, -42f, 0f, 0f, 1.047f));
        PartDefinition p_smokebox_r0 = p_body.addOrReplaceChild("smokebox_r0",
                CubeListBuilder.create()
                .texOffs(0, 195).addBox(-9.5f, -9.5f, -10f, 19f, 19f, 20f),
                PartPose.offset(0f, -22f, -74f));
        PartDefinition p_smokebox_r1 = p_body.addOrReplaceChild("smokebox_r1",
                CubeListBuilder.create()
                .texOffs(0, 195).addBox(-9.5f, -9.5f, -10f, 19f, 19f, 20f),
                PartPose.offsetAndRotation(0f, -22f, -74f, 0f, 0f, 0.524f));
        PartDefinition p_smokebox_r2 = p_body.addOrReplaceChild("smokebox_r2",
                CubeListBuilder.create()
                .texOffs(0, 195).addBox(-9.5f, -9.5f, -10f, 19f, 19f, 20f),
                PartPose.offsetAndRotation(0f, -22f, -74f, 0f, 0f, 1.047f));
        PartDefinition p_band28_r0 = p_body.addOrReplaceChild("band28_r0",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offset(0f, -22f, -28f));
        PartDefinition p_band28_r1 = p_body.addOrReplaceChild("band28_r1",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -28f, 0f, 0f, 0.524f));
        PartDefinition p_band28_r2 = p_body.addOrReplaceChild("band28_r2",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -28f, 0f, 0f, 1.047f));
        PartDefinition p_band44_r0 = p_body.addOrReplaceChild("band44_r0",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offset(0f, -22f, -44f));
        PartDefinition p_band44_r1 = p_body.addOrReplaceChild("band44_r1",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -44f, 0f, 0f, 0.524f));
        PartDefinition p_band44_r2 = p_body.addOrReplaceChild("band44_r2",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -44f, 0f, 0f, 1.047f));
        PartDefinition p_band58_r0 = p_body.addOrReplaceChild("band58_r0",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offset(0f, -22f, -58f));
        PartDefinition p_band58_r1 = p_body.addOrReplaceChild("band58_r1",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -58f, 0f, 0f, 0.524f));
        PartDefinition p_band58_r2 = p_body.addOrReplaceChild("band58_r2",
                CubeListBuilder.create()
                .texOffs(300, 195).addBox(-10f, -10f, -1f, 20f, 20f, 2f),
                PartPose.offsetAndRotation(0f, -22f, -58f, 0f, 0f, 1.047f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(466, 133).addBox(11f, -38f, -10f, 1f, 12f, 36f)
                .texOffs(466, 133).addBox(-12f, -38f, -10f, 1f, 12f, 36f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** steam_modern */
    private static LayerDefinition createSteamModernLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10f, -11f, -86f, 20f, 3f, 130f)
                .texOffs(202, 199).addBox(-10f, -9f, 46f, 20f, 3f, 42f)
                .texOffs(624, 199).addBox(12f, -8f, -44f, 1f, 2f, 36f)
                .texOffs(698, 199).addBox(13f, -8f, -60f, 1f, 2f, 34f)
                .texOffs(960, 199).addBox(11f, -12f, -68f, 3f, 7f, 12f)
                .texOffs(624, 199).addBox(-13f, -8f, -44f, 1f, 2f, 36f)
                .texOffs(698, 199).addBox(-14f, -8f, -60f, 1f, 2f, 34f)
                .texOffs(960, 199).addBox(-14f, -12f, -68f, 3f, 7f, 12f)
                .texOffs(0, 133).addBox(-12f, -12f, -20f, 24f, 2f, 64f)
                .texOffs(756, 0).addBox(11f, -26f, -20f, 1f, 14f, 56f)
                .texOffs(482, 133).addBox(11f, -44f, -20f, 1f, 6f, 56f)
                .texOffs(894, 199).addBox(11f, -38f, 26f, 1f, 12f, 10f)
                .texOffs(894, 199).addBox(11f, -38f, -20f, 1f, 12f, 10f)
                .texOffs(596, 133).addBox(12f, -22f, -20f, 1f, 4f, 56f)
                .texOffs(756, 0).addBox(-12f, -26f, -20f, 1f, 14f, 56f)
                .texOffs(482, 133).addBox(-12f, -44f, -20f, 1f, 6f, 56f)
                .texOffs(894, 199).addBox(-12f, -38f, 26f, 1f, 12f, 10f)
                .texOffs(894, 199).addBox(-12f, -38f, -20f, 1f, 12f, 10f)
                .texOffs(596, 133).addBox(-13f, -22f, -20f, 1f, 4f, 56f)
                .texOffs(176, 133).addBox(-13f, -46f, -22f, 26f, 2f, 62f)
                .texOffs(352, 133).addBox(10f, -47f, -22f, 3f, 1f, 62f)
                .texOffs(352, 133).addBox(-13f, -47f, -22f, 3f, 1f, 62f)
                .texOffs(796, 133).addBox(-11f, -13f, -19f, 22f, 1f, 54f)
                .texOffs(916, 199).addBox(-8f, -28f, -19f, 16f, 15f, 6f)
                .texOffs(226, 252).addBox(-2f, -31f, -18f, 4f, 3f, 4f)
                .texOffs(330, 252).addBox(-2f, -24f, -13f, 4f, 4f, 2f)
                .texOffs(380, 252).addBox(-1f, -23f, -11f, 2f, 2f, 2f)
                .texOffs(372, 252).addBox(-7f, -26f, -13f, 3f, 4f, 1f)
                .texOffs(372, 252).addBox(4f, -26f, -13f, 3f, 4f, 1f)
                .texOffs(220, 252).addBox(5f, -20f, -13f, 1f, 6f, 2f)
                .texOffs(220, 252).addBox(-6f, -20f, -13f, 1f, 6f, 2f)
                .texOffs(106, 252).addBox(3f, -22f, 6f, 6f, 9f, 6f)
                .texOffs(206, 252).addBox(3f, -30f, 12f, 6f, 8f, 1f)
                .texOffs(106, 252).addBox(-9f, -22f, 6f, 6f, 9f, 6f)
                .texOffs(206, 252).addBox(-9f, -30f, 12f, 6f, 8f, 1f)
                .texOffs(352, 252).addBox(6f, -17f, 88f, 3f, 3f, 2f)
                .texOffs(352, 252).addBox(-9f, -17f, 88f, 3f, 3f, 2f)
                .texOffs(604, 0).addBox(-10f, -34f, -76f, 20f, 22f, 56f)
                .texOffs(0, 199).addBox(-7f, -37f, -70f, 14f, 3f, 50f)
                .texOffs(800, 199).addBox(-9f, -32f, -82f, 18f, 20f, 6f)
                .texOffs(0, 252).addBox(-7f, -28f, -86f, 14f, 14f, 4f)
                .texOffs(186, 252).addBox(-4f, -24f, -88f, 8f, 8f, 2f)
                .texOffs(300, 0).addBox(10f, -24f, -76f, 3f, 10f, 72f)
                .texOffs(300, 0).addBox(-13f, -24f, -76f, 3f, 10f, 72f)
                .texOffs(450, 0).addBox(12f, -22f, -80f, 1f, 4f, 76f)
                .texOffs(450, 0).addBox(-13f, -22f, -80f, 1f, 4f, 76f)
                .texOffs(158, 252).addBox(-3f, -39f, -64f, 6f, 2f, 8f)
                .texOffs(302, 252).addBox(-3f, -30f, -90f, 6f, 4f, 2f)
                .texOffs(710, 133).addBox(11f, -28f, 46f, 1f, 17f, 42f)
                .texOffs(458, 199).addBox(10f, -29f, 46f, 2f, 1f, 42f)
                .texOffs(546, 199).addBox(12f, -20f, 48f, 1f, 4f, 38f)
                .texOffs(710, 133).addBox(-12f, -28f, 46f, 1f, 17f, 42f)
                .texOffs(458, 199).addBox(-12f, -29f, 46f, 2f, 1f, 42f)
                .texOffs(546, 199).addBox(-13f, -20f, 48f, 1f, 4f, 38f)
                .texOffs(326, 199).addBox(-12f, -11f, 46f, 24f, 2f, 42f)
                .texOffs(848, 199).addBox(-11f, -34f, 46f, 22f, 23f, 1f)
                .texOffs(40, 252).addBox(-11f, -26f, 87f, 22f, 15f, 1f)
                .texOffs(130, 252).addBox(-2f, -11f, 36f, 4f, 2f, 10f)
                .texOffs(36, 252).addBox(12f, -28f, 49f, 1f, 16f, 1f)
                .texOffs(990, 199).addBox(13f, -30f, 33f, 1f, 18f, 1f)
                .texOffs(990, 199).addBox(13f, -30f, 28f, 1f, 18f, 1f)
                .texOffs(242, 252).addBox(13f, -16f, 28f, 1f, 1f, 6f)
                .texOffs(242, 252).addBox(13f, -21f, 28f, 1f, 1f, 6f)
                .texOffs(242, 252).addBox(13f, -26f, 28f, 1f, 1f, 6f)
                .texOffs(990, 199).addBox(-14f, -30f, 33f, 1f, 18f, 1f)
                .texOffs(990, 199).addBox(-14f, -30f, 28f, 1f, 18f, 1f)
                .texOffs(242, 252).addBox(-14f, -16f, 28f, 1f, 1f, 6f)
                .texOffs(242, 252).addBox(-14f, -21f, 28f, 1f, 1f, 6f)
                .texOffs(242, 252).addBox(-14f, -26f, 28f, 1f, 1f, 6f)
                .texOffs(342, 252).addBox(-7f, -10f, 88f, 2f, 3f, 3f)
                .texOffs(342, 252).addBox(5f, -10f, 88f, 2f, 3f, 3f)
                .texOffs(362, 252).addBox(-1f, -9f, 88f, 2f, 2f, 3f)
                .texOffs(342, 252).addBox(-7f, -10f, -89f, 2f, 3f, 3f)
                .texOffs(342, 252).addBox(5f, -10f, -89f, 2f, 3f, 3f)
                .texOffs(362, 252).addBox(-1f, -9f, -89f, 2f, 2f, 3f)
                .texOffs(256, 252).addBox(-10f, -8f, -91f, 20f, 3f, 3f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -8f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w0_t2 = p_w0.addOrReplaceChild("w0_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -8f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w1_t2 = p_w1.addOrReplaceChild("w1_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -26f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w2_t2 = p_w2.addOrReplaceChild("w2_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -26f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w3_t2 = p_w3.addOrReplaceChild("w3_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -7f, -44f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w4_t2 = p_w4.addOrReplaceChild("w4_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -7f, -44f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.524f, 0f, 0f));
        PartDefinition p_w5_t2 = p_w5.addOrReplaceChild("w5_t2",
                CubeListBuilder.create()
                .texOffs(768, 199).addBox(-1f, -7f, -7f, 2f, 14f, 14f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 1.047f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(6f, -4f, -76f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-6f, -4f, -76f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w8 = p_body.addOrReplaceChild("w8",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 52f));
        PartDefinition p_w8_t0 = p_w8.addOrReplaceChild("w8_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w8_t1 = p_w8.addOrReplaceChild("w8_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w9 = p_body.addOrReplaceChild("w9",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 52f));
        PartDefinition p_w9_t0 = p_w9.addOrReplaceChild("w9_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w9_t1 = p_w9.addOrReplaceChild("w9_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w10 = p_body.addOrReplaceChild("w10",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 62f));
        PartDefinition p_w10_t0 = p_w10.addOrReplaceChild("w10_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w10_t1 = p_w10.addOrReplaceChild("w10_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w11 = p_body.addOrReplaceChild("w11",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 62f));
        PartDefinition p_w11_t0 = p_w11.addOrReplaceChild("w11_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w11_t1 = p_w11.addOrReplaceChild("w11_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w12 = p_body.addOrReplaceChild("w12",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 72f));
        PartDefinition p_w12_t0 = p_w12.addOrReplaceChild("w12_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w12_t1 = p_w12.addOrReplaceChild("w12_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w13 = p_body.addOrReplaceChild("w13",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 72f));
        PartDefinition p_w13_t0 = p_w13.addOrReplaceChild("w13_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w13_t1 = p_w13.addOrReplaceChild("w13_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w14 = p_body.addOrReplaceChild("w14",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(11f, -4f, 82f));
        PartDefinition p_w14_t0 = p_w14.addOrReplaceChild("w14_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w14_t1 = p_w14.addOrReplaceChild("w14_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w15 = p_body.addOrReplaceChild("w15",
                CubeListBuilder.create()
                .texOffs(318, 252).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f),
                PartPose.offset(-11f, -4f, 82f));
        PartDefinition p_w15_t0 = p_w15.addOrReplaceChild("w15_t0",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w15_t1 = p_w15.addOrReplaceChild("w15_t1",
                CubeListBuilder.create()
                .texOffs(86, 252).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(128, 199).addBox(11f, -38f, -10f, 1f, 12f, 36f)
                .texOffs(128, 199).addBox(-12f, -38f, -10f, 1f, 12f, 36f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** coach_low */
    private static LayerDefinition createCoachLowLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(644, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(670, 333).addBox(-9f, -7f, 41f, 18f, 2f, 30f)
                .texOffs(766, 333).addBox(3f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(766, 333).addBox(-4f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(670, 333).addBox(-9f, -7f, -71f, 18f, 2f, 30f)
                .texOffs(766, 333).addBox(3f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(766, 333).addBox(-4f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(80, 495).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(80, 495).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(98, 495).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(80, 495).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(80, 495).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(98, 495).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(0, 171).addBox(-12f, -11f, -80f, 24f, 2f, 160f)
                .texOffs(0, 0).addBox(11f, -22f, -80f, 1f, 11f, 160f)
                .texOffs(322, 0).addBox(11f, -36f, -80f, 1f, 5f, 160f)
                .texOffs(824, 333).addBox(11f, -31f, -80f, 1f, 9f, 18f)
                .texOffs(824, 333).addBox(11f, -31f, 62f, 1f, 9f, 18f)
                .texOffs(994, 333).addBox(11f, -31f, 46f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, 30f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, 14f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, -2f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, -18f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, -34f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(11f, -31f, -50f, 1f, 9f, 4f)
                .texOffs(356, 333).addBox(12f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(0, 0).addBox(-12f, -22f, -80f, 1f, 11f, 160f)
                .texOffs(322, 0).addBox(-12f, -36f, -80f, 1f, 5f, 160f)
                .texOffs(824, 333).addBox(-12f, -31f, -80f, 1f, 9f, 18f)
                .texOffs(824, 333).addBox(-12f, -31f, 62f, 1f, 9f, 18f)
                .texOffs(994, 333).addBox(-12f, -31f, 46f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, 30f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, 14f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, -2f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, -18f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, -34f, 1f, 9f, 4f)
                .texOffs(994, 333).addBox(-12f, -31f, -50f, 1f, 9f, 4f)
                .texOffs(356, 333).addBox(-13f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(862, 333).addBox(-11f, -36f, -80f, 22f, 25f, 1f)
                .texOffs(908, 333).addBox(-8f, -31f, -82f, 16f, 19f, 2f)
                .texOffs(862, 333).addBox(-11f, -36f, 79f, 22f, 25f, 1f)
                .texOffs(908, 333).addBox(-8f, -31f, 80f, 16f, 19f, 2f)
                .texOffs(368, 171).addBox(-12f, -38f, -80f, 24f, 2f, 160f)
                .texOffs(0, 333).addBox(-9f, -40f, -80f, 18f, 2f, 160f)
                .texOffs(0, 495).addBox(-2f, -42f, 32f, 4f, 2f, 8f)
                .texOffs(0, 495).addBox(-2f, -42f, -36f, 4f, 2f, 8f)
                .texOffs(970, 333).addBox(13f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(970, 333).addBox(13f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(54, 495).addBox(13f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(13f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(13f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(970, 333).addBox(13f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(970, 333).addBox(13f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(54, 495).addBox(13f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(13f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(13f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(970, 333).addBox(-14f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(970, 333).addBox(-14f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(54, 495).addBox(-14f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(-14f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(-14f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(970, 333).addBox(-14f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(970, 333).addBox(-14f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(54, 495).addBox(-14f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(-14f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(54, 495).addBox(-14f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(68, 495).addBox(4f, -13f, 43f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, 42f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, 48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, 43f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, 42f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, 48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, 43f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, 42f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, 48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, 43f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, 42f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, 48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, 27f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, 26f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, 32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, 27f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, 26f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, 32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, 27f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, 26f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, 32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, 27f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, 26f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, 32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, 16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, 16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, 16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, 16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, -5f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, -6f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, 0f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, -5f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, -6f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, 0f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, -5f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, -6f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, 0f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, -5f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, -6f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, 0f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, -16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, -16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, -16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, -16f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, -37f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, -38f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, -32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, -37f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, -38f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, -32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, -37f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, -38f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, -32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, -37f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, -38f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, -32f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(4f, -13f, -53f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(3f, -16f, -54f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(3f, -24f, -48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(8f, -13f, -53f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(7f, -16f, -54f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(7f, -24f, -48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-6f, -13f, -53f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-7f, -16f, -54f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-7f, -24f, -48f, 4f, 8f, 1f)
                .texOffs(68, 495).addBox(-10f, -13f, -53f, 2f, 2f, 4f)
                .texOffs(24, 495).addBox(-11f, -16f, -54f, 4f, 3f, 6f)
                .texOffs(44, 495).addBox(-11f, -24f, -48f, 4f, 8f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 66f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 66f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -66f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(90, 495).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -66f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(974, 333).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(944, 333).addBox(11f, -31f, 50f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, 34f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, 18f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, 2f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, -14f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, -30f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, -46f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(11f, -31f, -62f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, 50f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, 34f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, 18f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, 2f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, -14f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, -30f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, -46f, 1f, 9f, 12f)
                .texOffs(944, 333).addBox(-12f, -31f, -62f, 1f, 9f, 12f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** coach_mid */
    private static LayerDefinition createCoachMidLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(322, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(716, 332).addBox(-9f, -7f, 41f, 18f, 2f, 30f)
                .texOffs(812, 332).addBox(3f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(812, 332).addBox(-4f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(716, 332).addBox(-9f, -7f, -71f, 18f, 2f, 30f)
                .texOffs(812, 332).addBox(3f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(812, 332).addBox(-4f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(80, 494).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(80, 494).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(98, 494).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(80, 494).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(80, 494).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(98, 494).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(0, 170).addBox(-12f, -11f, -80f, 24f, 2f, 160f)
                .texOffs(0, 0).addBox(11f, -21f, -80f, 1f, 10f, 160f)
                .texOffs(682, 0).addBox(11f, -36f, -80f, 1f, 3f, 160f)
                .texOffs(670, 332).addBox(11f, -33f, -80f, 1f, 12f, 22f)
                .texOffs(670, 332).addBox(11f, -33f, 58f, 1f, 12f, 22f)
                .texOffs(1010, 332).addBox(11f, -33f, 38f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(11f, -33f, 18f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(11f, -33f, -2f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(11f, -33f, -22f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(11f, -33f, -42f, 1f, 12f, 4f)
                .texOffs(356, 332).addBox(12f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(0, 0).addBox(-12f, -21f, -80f, 1f, 10f, 160f)
                .texOffs(682, 0).addBox(-12f, -36f, -80f, 1f, 3f, 160f)
                .texOffs(670, 332).addBox(-12f, -33f, -80f, 1f, 12f, 22f)
                .texOffs(670, 332).addBox(-12f, -33f, 58f, 1f, 12f, 22f)
                .texOffs(1010, 332).addBox(-12f, -33f, 38f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(-12f, -33f, 18f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(-12f, -33f, -2f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(-12f, -33f, -22f, 1f, 12f, 4f)
                .texOffs(1010, 332).addBox(-12f, -33f, -42f, 1f, 12f, 4f)
                .texOffs(356, 332).addBox(-13f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(904, 332).addBox(-11f, -36f, -80f, 22f, 25f, 1f)
                .texOffs(950, 332).addBox(-8f, -31f, -82f, 16f, 19f, 2f)
                .texOffs(904, 332).addBox(-11f, -36f, 79f, 22f, 25f, 1f)
                .texOffs(950, 332).addBox(-8f, -31f, 80f, 16f, 19f, 2f)
                .texOffs(368, 170).addBox(-12f, -38f, -80f, 24f, 2f, 160f)
                .texOffs(0, 332).addBox(-9f, -40f, -80f, 18f, 2f, 160f)
                .texOffs(10, 494).addBox(-2f, -42f, 32f, 4f, 2f, 8f)
                .texOffs(10, 494).addBox(-2f, -42f, -36f, 4f, 2f, 8f)
                .texOffs(986, 332).addBox(13f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(986, 332).addBox(13f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(54, 494).addBox(13f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(13f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(13f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(986, 332).addBox(13f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(986, 332).addBox(13f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(54, 494).addBox(13f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(13f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(13f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(986, 332).addBox(-14f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(986, 332).addBox(-14f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(54, 494).addBox(-14f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(-14f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(-14f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(986, 332).addBox(-14f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(986, 332).addBox(-14f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(54, 494).addBox(-14f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(-14f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(54, 494).addBox(-14f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(68, 494).addBox(4f, -13f, 39f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(3f, -16f, 38f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(3f, -27f, 44f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(3f, -29f, 44f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(8f, -13f, 39f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(7f, -16f, 38f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(7f, -27f, 44f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(7f, -29f, 44f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-6f, -13f, 39f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-7f, -16f, 38f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-7f, -27f, 44f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-7f, -29f, 44f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-10f, -13f, 39f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-11f, -16f, 38f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-11f, -27f, 44f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-11f, -29f, 44f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(4f, -13f, 19f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(3f, -16f, 18f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(3f, -27f, 24f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(3f, -29f, 24f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(8f, -13f, 19f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(7f, -16f, 18f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(7f, -27f, 24f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(7f, -29f, 24f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-6f, -13f, 19f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-7f, -16f, 18f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-7f, -27f, 24f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-7f, -29f, 24f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-10f, -13f, 19f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-11f, -16f, 18f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-11f, -27f, 24f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-11f, -29f, 24f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(4f, -13f, -1f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(3f, -16f, -2f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(3f, -27f, 4f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(3f, -29f, 4f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(8f, -13f, -1f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(7f, -16f, -2f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(7f, -27f, 4f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(7f, -29f, 4f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-6f, -13f, -1f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-7f, -16f, -2f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-7f, -27f, 4f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-7f, -29f, 4f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-10f, -13f, -1f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-11f, -16f, -2f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-11f, -27f, 4f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-11f, -29f, 4f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(4f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(3f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(3f, -27f, -16f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(3f, -29f, -16f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(8f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(7f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(7f, -27f, -16f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(7f, -29f, -16f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-6f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-7f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-7f, -27f, -16f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-7f, -29f, -16f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-10f, -13f, -21f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-11f, -16f, -22f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-11f, -27f, -16f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-11f, -29f, -16f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(4f, -13f, -41f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(3f, -16f, -42f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(3f, -27f, -36f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(3f, -29f, -36f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(8f, -13f, -41f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(7f, -16f, -42f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(7f, -27f, -36f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(7f, -29f, -36f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-6f, -13f, -41f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-7f, -16f, -42f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-7f, -27f, -36f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-7f, -29f, -36f, 4f, 2f, 1f)
                .texOffs(68, 494).addBox(-10f, -13f, -41f, 2f, 2f, 4f)
                .texOffs(34, 494).addBox(-11f, -16f, -42f, 4f, 3f, 6f)
                .texOffs(0, 494).addBox(-11f, -27f, -36f, 4f, 11f, 1f)
                .texOffs(108, 494).addBox(-11f, -29f, -36f, 4f, 2f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 66f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 66f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -66f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(90, 494).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -66f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(990, 332).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(870, 332).addBox(11f, -33f, 42f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(11f, -33f, 22f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(11f, -33f, 2f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(11f, -33f, -18f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(11f, -33f, -38f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(11f, -33f, -58f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, 42f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, 22f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, 2f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, -18f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, -38f, 1f, 12f, 16f)
                .texOffs(870, 332).addBox(-12f, -33f, -58f, 1f, 12f, 16f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** coach_high */
    private static LayerDefinition createCoachHighLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(322, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(314, 493).addBox(-9f, -7f, 41f, 18f, 2f, 30f)
                .texOffs(410, 493).addBox(3f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(410, 493).addBox(-4f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(314, 493).addBox(-9f, -7f, -71f, 18f, 2f, 30f)
                .texOffs(410, 493).addBox(3f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(410, 493).addBox(-4f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(814, 493).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(814, 493).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(832, 493).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(814, 493).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(814, 493).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(832, 493).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(0, 169).addBox(-12f, -11f, -80f, 24f, 2f, 160f)
                .texOffs(0, 0).addBox(11f, -20f, -80f, 1f, 9f, 160f)
                .texOffs(356, 331).addBox(11f, -36f, -80f, 1f, 2f, 160f)
                .texOffs(506, 493).addBox(11f, -34f, -80f, 1f, 14f, 16f)
                .texOffs(506, 493).addBox(11f, -34f, 64f, 1f, 14f, 16f)
                .texOffs(626, 493).addBox(11f, -34f, 42f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(11f, -34f, 20f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(11f, -34f, -2f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(11f, -34f, -24f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(11f, -34f, -46f, 1f, 14f, 4f)
                .texOffs(678, 331).addBox(12f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(0, 493).addBox(12f, -36f, -78f, 1f, 1f, 156f)
                .texOffs(0, 0).addBox(-12f, -20f, -80f, 1f, 9f, 160f)
                .texOffs(356, 331).addBox(-12f, -36f, -80f, 1f, 2f, 160f)
                .texOffs(506, 493).addBox(-12f, -34f, -80f, 1f, 14f, 16f)
                .texOffs(506, 493).addBox(-12f, -34f, 64f, 1f, 14f, 16f)
                .texOffs(626, 493).addBox(-12f, -34f, 42f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(-12f, -34f, 20f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(-12f, -34f, -2f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(-12f, -34f, -24f, 1f, 14f, 4f)
                .texOffs(626, 493).addBox(-12f, -34f, -46f, 1f, 14f, 4f)
                .texOffs(678, 331).addBox(-13f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(0, 493).addBox(-13f, -36f, -78f, 1f, 1f, 156f)
                .texOffs(540, 493).addBox(-11f, -36f, -80f, 22f, 25f, 1f)
                .texOffs(586, 493).addBox(-8f, -31f, -82f, 16f, 19f, 2f)
                .texOffs(540, 493).addBox(-11f, -36f, 79f, 22f, 25f, 1f)
                .texOffs(586, 493).addBox(-8f, -31f, 80f, 16f, 19f, 2f)
                .texOffs(368, 169).addBox(-12f, -38f, -80f, 24f, 2f, 160f)
                .texOffs(0, 331).addBox(-9f, -40f, -80f, 18f, 2f, 160f)
                .texOffs(656, 493).addBox(-5f, -42f, 36f, 10f, 2f, 12f)
                .texOffs(656, 493).addBox(-5f, -42f, -8f, 10f, 2f, 12f)
                .texOffs(656, 493).addBox(-5f, -42f, -52f, 10f, 2f, 12f)
                .texOffs(622, 493).addBox(13f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(622, 493).addBox(13f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(782, 493).addBox(13f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(13f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(13f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(622, 493).addBox(13f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(622, 493).addBox(13f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(782, 493).addBox(13f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(13f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(13f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(622, 493).addBox(-14f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(622, 493).addBox(-14f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(782, 493).addBox(-14f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(-14f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(-14f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(622, 493).addBox(-14f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(622, 493).addBox(-14f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(782, 493).addBox(-14f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(-14f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(782, 493).addBox(-14f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(796, 493).addBox(5f, -13f, 47f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, 46f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, 52f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, 46f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, 47f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, 46f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, 52f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, 46f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, 38f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, 41f, 1f, 7f, 1f)
                .texOffs(796, 493).addBox(5f, -13f, 25f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, 24f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, 30f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, 24f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, 25f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, 24f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, 30f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, 24f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, 16f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, 19f, 1f, 7f, 1f)
                .texOffs(796, 493).addBox(5f, -13f, 3f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, 2f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, 8f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, 2f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, 3f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, 2f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, 8f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, 2f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, -6f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, -3f, 1f, 7f, 1f)
                .texOffs(796, 493).addBox(5f, -13f, -19f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, -20f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, -14f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, -20f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, -19f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, -20f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, -14f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, -20f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, -28f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, -25f, 1f, 7f, 1f)
                .texOffs(796, 493).addBox(5f, -13f, -41f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, -42f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, -36f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, -42f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, -41f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, -42f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, -36f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, -42f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, -50f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, -47f, 1f, 7f, 1f)
                .texOffs(796, 493).addBox(5f, -13f, -63f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(4f, -16f, -64f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(4f, -28f, -58f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(4f, -17f, -64f, 1f, 2f, 6f)
                .texOffs(796, 493).addBox(-10f, -13f, -63f, 5f, 2f, 4f)
                .texOffs(716, 493).addBox(-11f, -16f, -64f, 7f, 3f, 6f)
                .texOffs(700, 493).addBox(-11f, -28f, -58f, 7f, 12f, 1f)
                .texOffs(742, 493).addBox(-5f, -17f, -64f, 1f, 2f, 6f)
                .texOffs(760, 493).addBox(6f, -19f, -72f, 5f, 1f, 6f)
                .texOffs(756, 493).addBox(8f, -18f, -69f, 1f, 7f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 66f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 66f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -66f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(824, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -66f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(636, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(468, 493).addBox(11f, -34f, 46f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(11f, -34f, 24f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(11f, -34f, 2f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(11f, -34f, -20f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(11f, -34f, -42f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(11f, -34f, -64f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, 46f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, 24f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, 2f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, -20f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, -42f, 1f, 14f, 18f)
                .texOffs(468, 493).addBox(-12f, -34f, -64f, 1f, 14f, 18f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** metro_head */
    private static LayerDefinition createMetroHeadLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(346, 297).addBox(-9f, -7f, 41f, 18f, 2f, 30f)
                .texOffs(442, 297).addBox(3f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(442, 297).addBox(-4f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(346, 297).addBox(-9f, -7f, -71f, 18f, 2f, 30f)
                .texOffs(442, 297).addBox(3f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(442, 297).addBox(-4f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(584, 419).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(584, 419).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(710, 419).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(584, 419).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(584, 419).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(710, 419).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(626, 0).addBox(-12f, -11f, -56f, 24f, 2f, 136f)
                .texOffs(360, 0).addBox(11f, -20f, -52f, 1f, 9f, 132f)
                .texOffs(312, 163).addBox(11f, -36f, -52f, 1f, 2f, 132f)
                .texOffs(676, 297).addBox(11f, -34f, 68f, 1f, 14f, 12f)
                .texOffs(276, 297).addBox(11f, -34f, -52f, 1f, 14f, 34f)
                .texOffs(962, 297).addBox(11f, -34f, 50f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(11f, -34f, 32f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(11f, -34f, 14f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(11f, -34f, -4f, 1f, 14f, 4f)
                .texOffs(578, 163).addBox(12f, -18f, -52f, 1f, 4f, 130f)
                .texOffs(360, 0).addBox(-12f, -20f, -52f, 1f, 9f, 132f)
                .texOffs(312, 163).addBox(-12f, -36f, -52f, 1f, 2f, 132f)
                .texOffs(676, 297).addBox(-12f, -34f, 68f, 1f, 14f, 12f)
                .texOffs(276, 297).addBox(-12f, -34f, -52f, 1f, 14f, 34f)
                .texOffs(962, 297).addBox(-12f, -34f, 50f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(-12f, -34f, 32f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(-12f, -34f, 14f, 1f, 14f, 4f)
                .texOffs(962, 297).addBox(-12f, -34f, -4f, 1f, 14f, 4f)
                .texOffs(578, 163).addBox(-13f, -18f, -52f, 1f, 4f, 130f)
                .texOffs(630, 297).addBox(-11f, -36f, 79f, 22f, 25f, 1f)
                .texOffs(702, 297).addBox(-8f, -31f, 80f, 16f, 19f, 2f)
                .texOffs(802, 297).addBox(-11f, -22f, -60f, 22f, 11f, 8f)
                .texOffs(194, 419).addBox(-11f, -36f, -60f, 22f, 2f, 8f)
                .texOffs(76, 419).addBox(-10f, -19f, -66f, 20f, 8f, 6f)
                .texOffs(350, 419).addBox(-10f, -33f, -66f, 20f, 2f, 6f)
                .texOffs(254, 419).addBox(-8f, -16f, -71f, 16f, 5f, 5f)
                .texOffs(402, 419).addBox(-8f, -30f, -71f, 16f, 2f, 5f)
                .texOffs(498, 419).addBox(-6f, -13f, -75f, 12f, 2f, 4f)
                .texOffs(530, 419).addBox(-6f, -27f, -75f, 12f, 2f, 4f)
                .texOffs(562, 419).addBox(-4f, -14f, -78f, 8f, 3f, 3f)
                .texOffs(658, 419).addBox(-4f, -24f, -78f, 8f, 2f, 3f)
                .texOffs(720, 419).addBox(2f, -18f, -80f, 3f, 3f, 2f)
                .texOffs(720, 419).addBox(-5f, -18f, -80f, 3f, 3f, 2f)
                .texOffs(740, 419).addBox(-3f, -13f, -80f, 6f, 2f, 2f)
                .texOffs(0, 163).addBox(-12f, -38f, -52f, 24f, 2f, 132f)
                .texOffs(0, 297).addBox(-9f, -40f, -40f, 18f, 2f, 120f)
                .texOffs(730, 419).addBox(6f, -17f, 80f, 3f, 3f, 2f)
                .texOffs(730, 419).addBox(-9f, -17f, 80f, 3f, 3f, 2f)
                .texOffs(500, 297).addBox(-11f, -12f, -56f, 22f, 1f, 28f)
                .texOffs(128, 419).addBox(-9f, -21f, -56f, 18f, 9f, 4f)
                .texOffs(614, 419).addBox(-9f, -22f, -56f, 18f, 1f, 4f)
                .texOffs(680, 419).addBox(-2f, -23f, -56f, 4f, 1f, 4f)
                .texOffs(756, 419).addBox(-1f, -25f, -55f, 2f, 2f, 2f)
                .texOffs(764, 419).addBox(-8f, -24f, -54f, 2f, 2f, 1f)
                .texOffs(764, 419).addBox(-5f, -24f, -54f, 2f, 2f, 1f)
                .texOffs(764, 419).addBox(4f, -24f, -54f, 2f, 2f, 1f)
                .texOffs(764, 419).addBox(7f, -24f, -54f, 2f, 2f, 1f)
                .texOffs(696, 419).addBox(5f, -23f, -56f, 3f, 1f, 4f)
                .texOffs(610, 419).addBox(6f, -28f, -54f, 1f, 5f, 1f)
                .texOffs(696, 419).addBox(-8f, -23f, -56f, 3f, 1f, 4f)
                .texOffs(610, 419).addBox(-7f, -28f, -54f, 1f, 5f, 1f)
                .texOffs(32, 419).addBox(3f, -22f, -40f, 6f, 10f, 6f)
                .texOffs(336, 419).addBox(3f, -30f, -34f, 6f, 8f, 1f)
                .texOffs(32, 419).addBox(-9f, -22f, -40f, 6f, 10f, 6f)
                .texOffs(336, 419).addBox(-9f, -30f, -34f, 6f, 8f, 1f)
                .texOffs(914, 297).addBox(-4f, -42f, -22f, 8f, 2f, 16f)
                .texOffs(296, 419).addBox(-6f, -42f, 18f, 12f, 2f, 8f)
                .texOffs(602, 419).addBox(-1f, -46f, 22f, 2f, 4f, 2f)
                .texOffs(602, 419).addBox(-1f, -50f, 20f, 2f, 4f, 2f)
                .texOffs(444, 419).addBox(-7f, -51f, 18f, 14f, 1f, 6f)
                .texOffs(798, 297).addBox(13f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(798, 297).addBox(13f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(484, 419).addBox(13f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(484, 419).addBox(13f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(484, 419).addBox(13f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(798, 297).addBox(-14f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(798, 297).addBox(-14f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(484, 419).addBox(-14f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(484, 419).addBox(-14f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(484, 419).addBox(-14f, -25f, 70f, 1f, 1f, 6f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 66f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 66f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -66f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(594, 419).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -66f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(56, 419).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(600, 297).addBox(11f, -34f, 54f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(11f, -34f, 36f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(11f, -34f, 18f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(11f, -34f, 0f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(11f, -34f, -18f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(-12f, -34f, 54f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(-12f, -34f, 36f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(-12f, -34f, 18f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(-12f, -34f, 0f, 1f, 14f, 14f)
                .texOffs(600, 297).addBox(-12f, -34f, -18f, 1f, 14f, 14f)
                .texOffs(738, 297).addBox(-11f, -34f, -60f, 22f, 12f, 8f)
                .texOffs(862, 297).addBox(-10f, -31f, -66f, 20f, 12f, 6f)
                .texOffs(972, 297).addBox(-8f, -28f, -71f, 16f, 12f, 5f)
                .texOffs(0, 419).addBox(-6f, -25f, -75f, 12f, 12f, 4f)
                .texOffs(172, 419).addBox(-4f, -22f, -78f, 8f, 8f, 3f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** metro_car */
    private static LayerDefinition createMetroCarLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                .texOffs(322, 0).addBox(-10f, -9f, -80f, 20f, 3f, 160f)
                .texOffs(42, 493).addBox(-9f, -7f, 41f, 18f, 2f, 30f)
                .texOffs(138, 493).addBox(3f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(138, 493).addBox(-4f, -7f, 42f, 1f, 4f, 28f)
                .texOffs(42, 493).addBox(-9f, -7f, -71f, 18f, 2f, 30f)
                .texOffs(138, 493).addBox(3f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(138, 493).addBox(-4f, -7f, -70f, 1f, 4f, 28f)
                .texOffs(460, 493).addBox(-7f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(460, 493).addBox(5f, -10f, 80f, 2f, 3f, 3f)
                .texOffs(478, 493).addBox(-1f, -9f, 81f, 2f, 2f, 3f)
                .texOffs(460, 493).addBox(-7f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(460, 493).addBox(5f, -10f, -83f, 2f, 3f, 3f)
                .texOffs(478, 493).addBox(-1f, -9f, -84f, 2f, 2f, 3f)
                .texOffs(0, 169).addBox(-12f, -11f, -80f, 24f, 2f, 160f)
                .texOffs(0, 0).addBox(11f, -20f, -80f, 1f, 9f, 160f)
                .texOffs(356, 331).addBox(11f, -36f, -80f, 1f, 2f, 160f)
                .texOffs(196, 493).addBox(11f, -34f, -80f, 1f, 14f, 18f)
                .texOffs(0, 493).addBox(11f, -34f, 60f, 1f, 14f, 20f)
                .texOffs(350, 493).addBox(11f, -34f, 42f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(11f, -34f, 24f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(11f, -34f, 6f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(11f, -34f, -12f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(11f, -34f, -30f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(11f, -34f, -48f, 1f, 14f, 4f)
                .texOffs(678, 331).addBox(12f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(0, 0).addBox(-12f, -20f, -80f, 1f, 9f, 160f)
                .texOffs(356, 331).addBox(-12f, -36f, -80f, 1f, 2f, 160f)
                .texOffs(196, 493).addBox(-12f, -34f, -80f, 1f, 14f, 18f)
                .texOffs(0, 493).addBox(-12f, -34f, 60f, 1f, 14f, 20f)
                .texOffs(350, 493).addBox(-12f, -34f, 42f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(-12f, -34f, 24f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(-12f, -34f, 6f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(-12f, -34f, -12f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(-12f, -34f, -30f, 1f, 14f, 4f)
                .texOffs(350, 493).addBox(-12f, -34f, -48f, 1f, 14f, 4f)
                .texOffs(678, 331).addBox(-13f, -17f, -78f, 1f, 3f, 156f)
                .texOffs(264, 493).addBox(-11f, -36f, -80f, 22f, 25f, 1f)
                .texOffs(310, 493).addBox(-8f, -31f, -82f, 16f, 19f, 2f)
                .texOffs(264, 493).addBox(-11f, -36f, 79f, 22f, 25f, 1f)
                .texOffs(310, 493).addBox(-8f, -31f, 80f, 16f, 19f, 2f)
                .texOffs(368, 169).addBox(-12f, -38f, -80f, 24f, 2f, 160f)
                .texOffs(0, 331).addBox(-9f, -40f, -80f, 18f, 2f, 160f)
                .texOffs(390, 493).addBox(-2f, -42f, 32f, 4f, 2f, 8f)
                .texOffs(390, 493).addBox(-2f, -42f, -36f, 4f, 2f, 8f)
                .texOffs(346, 493).addBox(13f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(346, 493).addBox(13f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(434, 493).addBox(13f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(13f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(13f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(346, 493).addBox(13f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(346, 493).addBox(13f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(434, 493).addBox(13f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(13f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(13f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(346, 493).addBox(-14f, -30f, -71f, 1f, 19f, 1f)
                .texOffs(346, 493).addBox(-14f, -30f, -76f, 1f, 19f, 1f)
                .texOffs(434, 493).addBox(-14f, -15f, -76f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(-14f, -20f, -76f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(-14f, -25f, -76f, 1f, 1f, 6f)
                .texOffs(346, 493).addBox(-14f, -30f, 75f, 1f, 19f, 1f)
                .texOffs(346, 493).addBox(-14f, -30f, 70f, 1f, 19f, 1f)
                .texOffs(434, 493).addBox(-14f, -15f, 70f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(-14f, -20f, 70f, 1f, 1f, 6f)
                .texOffs(434, 493).addBox(-14f, -25f, 70f, 1f, 1f, 6f)
                .texOffs(448, 493).addBox(4f, -13f, 47f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, 46f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, 52f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, 52f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, 47f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, 46f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, 52f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, 52f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, 47f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, 46f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, 52f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, 52f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, 47f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, 46f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, 52f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, 52f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(4f, -13f, 29f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, 28f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, 34f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, 34f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, 29f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, 28f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, 34f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, 34f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, 29f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, 28f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, 34f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, 34f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, 29f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, 28f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, 34f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, 34f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(4f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, 16f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, 16f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, 16f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, 16f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, 16f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, 16f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, 11f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, 10f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, 16f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, 16f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(4f, -13f, -7f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, -8f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, -2f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, -2f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, -7f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, -8f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, -2f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, -2f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, -7f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, -8f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, -2f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, -2f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, -7f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, -8f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, -2f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, -2f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(4f, -13f, -25f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, -26f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, -20f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, -20f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, -25f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, -26f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, -20f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, -20f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, -25f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, -26f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, -20f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, -20f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, -25f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, -26f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, -20f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, -20f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(4f, -13f, -43f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(3f, -16f, -44f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(3f, -27f, -38f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(3f, -29f, -38f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(8f, -13f, -43f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(7f, -16f, -44f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(7f, -27f, -38f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(7f, -29f, -38f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-6f, -13f, -43f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-7f, -16f, -44f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-7f, -27f, -38f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-7f, -29f, -38f, 4f, 2f, 1f)
                .texOffs(448, 493).addBox(-10f, -13f, -43f, 2f, 2f, 4f)
                .texOffs(414, 493).addBox(-11f, -16f, -44f, 4f, 3f, 6f)
                .texOffs(380, 493).addBox(-11f, -27f, -38f, 4f, 11f, 1f)
                .texOffs(488, 493).addBox(-11f, -29f, -38f, 4f, 2f, 1f),
                PartPose.offset(0f, 24f, 0f));
        PartDefinition p_w0 = p_body.addOrReplaceChild("w0",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 66f));
        PartDefinition p_w0_t0 = p_w0.addOrReplaceChild("w0_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w0_t1 = p_w0.addOrReplaceChild("w0_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w1 = p_body.addOrReplaceChild("w1",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 66f));
        PartDefinition p_w1_t0 = p_w1.addOrReplaceChild("w1_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w1_t1 = p_w1.addOrReplaceChild("w1_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w2 = p_body.addOrReplaceChild("w2",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, 46f));
        PartDefinition p_w2_t0 = p_w2.addOrReplaceChild("w2_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w2_t1 = p_w2.addOrReplaceChild("w2_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w3 = p_body.addOrReplaceChild("w3",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, 46f));
        PartDefinition p_w3_t0 = p_w3.addOrReplaceChild("w3_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w3_t1 = p_w3.addOrReplaceChild("w3_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w4 = p_body.addOrReplaceChild("w4",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -46f));
        PartDefinition p_w4_t0 = p_w4.addOrReplaceChild("w4_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w4_t1 = p_w4.addOrReplaceChild("w4_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w5 = p_body.addOrReplaceChild("w5",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -46f));
        PartDefinition p_w5_t0 = p_w5.addOrReplaceChild("w5_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w5_t1 = p_w5.addOrReplaceChild("w5_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w6 = p_body.addOrReplaceChild("w6",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(0.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(2.2f, -4f, -66f));
        PartDefinition p_w6_t0 = p_w6.addOrReplaceChild("w6_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w6_t1 = p_w6.addOrReplaceChild("w6_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_w7 = p_body.addOrReplaceChild("w7",
                CubeListBuilder.create()
                .texOffs(470, 493).addBox(-1.5f, -1.5f, -1.5f, 1f, 3f, 3f),
                PartPose.offset(-2.2f, -4f, -66f));
        PartDefinition p_w7_t0 = p_w7.addOrReplaceChild("w7_t0",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offset(0f, 0f, 0f));
        PartDefinition p_w7_t1 = p_w7.addOrReplaceChild("w7_t1",
                CubeListBuilder.create()
                .texOffs(360, 493).addBox(-1f, -4f, -4f, 2f, 8f, 8f),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0.785f, 0f, 0f));
        PartDefinition p_glass = root.addOrReplaceChild("glass",
                CubeListBuilder.create()
                .texOffs(234, 493).addBox(11f, -34f, 46f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, 28f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, 10f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, -8f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, -26f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, -44f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(11f, -34f, -62f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, 46f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, 28f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, 10f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, -8f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, -26f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, -44f, 1f, 14f, 14f)
                .texOffs(234, 493).addBox(-12f, -34f, -62f, 1f, 14f, 14f),
                PartPose.offset(0f, 24f, 0f));
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** Giro de las ruedas (rad). */
    public void pose(float wheelAngle) {
        for (int i = 0; i < this.wheels.size(); i++) {
            this.wheels.get(i).xRot = wheelAngle * (this.ratio != null && i < this.ratio.length ? this.ratio[i] : 1.0f);
        }
    }

    /** false = carrocería (opaca); true = solo los vidrios. */
    public void showGlass(boolean glassPass) {
        this.body.visible = !glassPass;
        this.glass.visible = glassPass;
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
