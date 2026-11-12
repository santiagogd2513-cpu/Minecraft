package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Dibuja la locomotora y los vagones del tren de carga (V4.8.0): modelo de {@link TrainCarModel} con ruedas que giran, inclinado con las
 * subidas, y la carga de los vagones como un montículo que crece con lo que lleva (se ve desde arriba).
 */
public class TrainCarRenderer extends EntityRenderer<TrainCarEntity> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[TrainCarEntity.Variant.values().length];
    private static final ResourceLocation[] CARGO = new ResourceLocation[TrainCarEntity.Variant.values().length];

    static {
        for (TrainCarEntity.Variant v : TrainCarEntity.Variant.values()) {
            TEXTURES[v.ordinal()] = ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/" + v.id + ".png");
            String cargo = switch (v.cargo) {
                case COAL -> "cargo_coal";
                case FOOD -> "cargo_food";
                case MINERAL -> "cargo_mineral";
                default -> null;
            };
            CARGO[v.ordinal()] = cargo == null ? null : ResourceLocation.fromNamespaceAndPath(BeautifulDecor.MOD_ID, "textures/entity/" + cargo + ".png");
        }
    }

    private final TrainCarModel[] models = new TrainCarModel[TrainCarEntity.Variant.values().length];

    public TrainCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        for (TrainCarEntity.Variant v : TrainCarEntity.Variant.values()) {
            this.models[v.ordinal()] = new TrainCarModel(context.bakeLayer(TrainCarModel.layer(v.id)), v.id);
        }
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(TrainCarEntity car, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        TrainCarEntity.Variant variant = car.variant();
        TrainCarModel model = this.models[variant.ordinal()];
        ResourceLocation texture = VehiclePaintTextures.textureFor(car, TEXTURES[variant.ordinal()]);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        float pitch = car.getPitchVisual(partialTick);
        if (pitch != 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));          // positivo = frente hacia abajo
        }
        if (CARGO[variant.ordinal()] != null) {
            float fill = car.fillFraction();
            if (fill > 0.0f) {
                float[] m = mound(variant);
                poseStack.pushPose();
                poseStack.translate(0.0, 0.0, m[0]);
                drawCargo(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(CARGO[variant.ordinal()])), packedLight, fill,
                        m[3], m[1], m[2], car.getId());
                poseStack.popPose();
            }
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.scale(TrainCarModel.SCALE, TrainCarModel.SCALE, TrainCarModel.SCALE);
        poseStack.translate(0.0, -1.501, 0.0);
        model.pose(car.getWheelAngle(partialTick));
        model.showGlass(false);
        model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight, OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f);
        if (variant.hasGlass()) {
            model.showGlass(true);
            model.renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentCull(texture)), packedLight, OverlayTexture.NO_OVERLAY,
                    1.0f, 1.0f, 1.0f, VehicleGlass.ALPHA);
        }
        poseStack.popPose();
        super.render(car, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainCarEntity car) {
        return VehiclePaintTextures.textureFor(car, TEXTURES[car.variant().ordinal()]);
    }

    // ---------------------------------------------------------------------------------------------- carga

    private static final float FLOOR = 1.375f;          // piso del vagón sobre el riel (bloques)
    private static final int LAYERS = 6;
    private static final float TILE = 1.0f;

    /** Dónde y qué tan grande es el montón de carga: {centro z, medio ancho, medio largo, altura máxima} en bloques; sobresale un poco de las paredes. */
    private static float[] mound(TrainCarEntity.Variant variant) {
        return switch (variant) {
            case COAL_WAGON -> new float[] {0.0f, 1.34f, 6.8f, 2.6f};
            case FOOD_WAGON -> new float[] {0.0f, 1.34f, 6.8f, 1.9f};
            case STEAM_CLASSIC, STEAM_MODERN -> new float[] {-8.375f, 1.3f, 2.45f, 2.0f};          // el carbón del ténder
            default -> new float[] {0.0f, 1.34f, 6.8f, 3.1f};
        };
    }

    private static void drawCargo(PoseStack poseStack, VertexConsumer vc, int light, float fill, float maxHeight, float halfX, float halfZ, int seed) {
        PoseStack.Pose pose = poseStack.last();
        float h = fill * maxHeight;
        float t = maxHeight / LAYERS;
        for (int j = 0; j < LAYERS; j++) {
            float y0 = j * t;
            if (h <= y0) {
                break;
            }
            float y1 = Math.min(h, (j + 1) * t);
            float shrink = 0.32f * (float) Math.pow(j / (float) (LAYERS - 1), 1.5);
            float hx = halfX * (1.0f - shrink);
            float hz = halfZ * (1.0f - shrink * 0.8f);
            box(pose, vc, light, -hx, FLOOR + y0, -hz, hx, FLOOR + y1, hz, seed + j * 31);
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1, int seed) {
        // arriba
        for (float x = x0; x < x1 - 1.0e-4f; x += TILE) {
            float xe = Math.min(x + TILE, x1);
            for (float z = z0; z < z1 - 1.0e-4f; z += TILE) {
                float ze = Math.min(z + TILE, z1);
                int k = hash(seed, (int) (x * 8), (int) (z * 8));
                quad(pose, vc, light, x, y1, z, xe, y1, z, xe, y1, ze, x, y1, ze, 0, 1, 0, k);
            }
        }
        // costados largos (normal en x) y testeros (normal en z)
        for (float y = y0; y < y1 - 1.0e-4f; y += TILE) {
            float ye = Math.min(y + TILE, y1);
            for (float z = z0; z < z1 - 1.0e-4f; z += TILE) {
                float ze = Math.min(z + TILE, z1);
                int k = hash(seed, (int) (y * 8), (int) (z * 8) + 77);
                quad(pose, vc, light, x1, y, z, x1, y, ze, x1, ye, ze, x1, ye, z, 1, 0, 0, k);
                quad(pose, vc, light, x0, y, z, x0, y, ze, x0, ye, ze, x0, ye, z, -1, 0, 0, k + 1);
            }
            for (float x = x0; x < x1 - 1.0e-4f; x += TILE) {
                float xe = Math.min(x + TILE, x1);
                int k = hash(seed, (int) (y * 8) + 99, (int) (x * 8));
                quad(pose, vc, light, x, y, z1, xe, y, z1, xe, ye, z1, x, ye, z1, 0, 0, 1, k);
                quad(pose, vc, light, x, y, z0, xe, y, z0, xe, ye, z0, x, ye, z0, 0, 0, -1, k + 1);
            }
        }
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        return (h ^ (h >>> 13)) & 3;
    }

    /** Un cuadrito de la textura de carga (32x32: cuatro mosaicos de 16x16; se elige uno al azar según el sitio). */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int variant) {
        float u0 = (variant & 1) * 0.5f;
        float v0 = ((variant >> 1) & 1) * 0.5f;
        float u1 = u0 + 0.5f;
        float v1 = v0 + 0.5f;
        vertex(pose, vc, light, ax, ay, az, u0, v1, nx, ny, nz);
        vertex(pose, vc, light, bx, by, bz, u1, v1, nx, ny, nz);
        vertex(pose, vc, light, cx, cy, cz, u1, v0, nx, ny, nz);
        vertex(pose, vc, light, dx, dy, dz, u0, v0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
