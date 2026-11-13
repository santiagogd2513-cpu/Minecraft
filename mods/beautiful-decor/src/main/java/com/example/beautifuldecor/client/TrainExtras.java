package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.ad.AdBrands;
import com.example.beautifuldecor.entity.TrainCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * V6.16.0: lo que se agrega al dibujo de los trenes sin tocar el modelo: las puertas de los coches de pasajeros y del metro (se cierran o se abren
 * corriéndose hacia el extremo), las lucecitas del botón de puertas en la cabina y los televisores de adelante (pasan los pósters de los anuncios).
 * Todo en bloques, en el mismo espacio que la carga ({@code +z} = hacia adelante).
 */
final class TrainExtras {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private static final int FULL_BRIGHT = 15728880;
    private static final float DOOR_W = 1.5f;
    private static final long TV_MILLIS = 6000L;

    private TrainExtras() {
    }

    /** Centro (z, en bloques) de las puertas de cada extremo según el coche (la zona libre entre la última ventana y la punta). */
    private static float doorZ(TrainCarEntity.Variant v) {
        return switch (v) {
            case COACH_LOW, METRO_CAR -> 8.875f;
            case COACH_MID -> 8.625f;
            case COACH_HIGH -> 9.0f;
            default -> 0.0f;
        };
    }

    static void draw(TrainCarEntity car, TrainCarEntity.Variant v, PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick) {
        PoseStack.Pose pose = poseStack.last();
        if (v.isCoach()) {
            drawDoors(car, v, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), light, partialTick);
            int tvs = car.tvMask();
            if (tvs != 0) {
                drawTvs(car, tvs, pose, buffers, light);
            }
        }
        double[] lb = TrainCarEntity.lightButton(v.id);              // V7.5.0: botoncito de las luces (se enciende en amarillo)
        if (lb != null) {
            boolean on = car.lightsOn();
            box(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), on ? FULL_BRIGHT : light, (float) lb[0] - 0.07f, (float) lb[1] - 0.07f, (float) lb[2] - 0.07f,
                    (float) lb[0] + 0.07f, (float) lb[1] + 0.07f, (float) lb[2] + 0.07f, on ? 255 : 120, on ? 236 : 104, on ? 120 : 40);
        }
        double[] btn = TrainCarEntity.doorButton(v.id);
        if (btn != null) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            boolean open = car.doorsOpen();
            int r = open ? 60 : 255;
            int g = open ? 230 : 190;
            int b = open ? 90 : 40;
            for (int s = -1; s <= 1; s += 2) {
                float x = (float) (s * Math.abs(btn[0]));
                box(pose, vc, FULL_BRIGHT, x - 0.09f, (float) btn[1] - 0.09f, (float) btn[2] - 0.09f, x + 0.09f, (float) btn[1] + 0.09f, (float) btn[2] + 0.09f, r, g, b);
            }
        }
    }

    private static void drawDoors(TrainCarEntity car, TrainCarEntity.Variant v, PoseStack.Pose pose, VertexConsumer vc, int light, float partialTick) {
        float p = car.doorProgress(partialTick);
        float zc = doorZ(v);
        float y0 = 1.45f;
        float y1 = 4.05f;
        for (int side = -1; side <= 1; side += 2) {
            for (int end = -1; end <= 1; end += 2) {
                float z = zc * end;
                float xo = 1.5f * side;
                float xf = 1.64f * side;
                float xl = 1.70f * side;
                if (p > 0.02f) {
                    box(pose, vc, light, Math.min(xo, xf), y0, z - DOOR_W / 2, Math.max(xo, xf), y1, z + DOOR_W / 2, 26, 28, 36);          // el hueco oscuro
                }
                float w = DOOR_W * (1.0f - 0.82f * p);                                                                                   // la hoja se corre hacia la punta
                float zEnd = z + end * DOOR_W / 2;
                float za = zEnd - end * w;
                float lo = Math.min(za, zEnd);
                float hi = Math.max(za, zEnd);
                float xa = Math.min(xf, xl);
                float xb = Math.max(xf, xl);
                box(pose, vc, light, xa, y0, lo, xb, y1, hi, 206, 210, 220);
                float margin = Math.min(0.12f, w / 4);
                if (w > 0.4f) {
                    float xw = side > 0 ? xb : xa;
                    box(pose, vc, light, Math.min(xw, xw + 0.02f * side), 2.7f, lo + margin, Math.max(xw, xw + 0.02f * side), 3.9f, hi - margin, 60, 90, 130);   // ventanilla
                }
            }
        }
    }

    private static void drawTvs(TrainCarEntity car, int mask, PoseStack.Pose pose, MultiBufferSource buffers, int light) {
        long millis = System.currentTimeMillis();
        int start = Math.floorMod(car.getId(), AdBrands.count());
        ResourceLocation poster = new ResourceLocation(BeautifulDecor.MOD_ID, AdBrands.posterPath(AdBrands.brand(AdBrands.posterIndex(start, millis))));
        VertexConsumer frame = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        VertexConsumer screen = buffers.getBuffer(RenderType.entityCutoutNoCull(poster));
        float zWall = 9.82f;
        for (int bit = 0; bit < 2; bit++) {
            if ((mask & (1 << bit)) == 0) {
                continue;
            }
            float cx = bit == 0 ? 0.85f : -0.85f;
            float cy = 3.25f;
            box(pose, frame, light, cx - 0.48f, cy - 0.28f, zWall - 0.1f, cx + 0.48f, cy + 0.28f, zWall, 22, 22, 26);
            float zs = zWall - 0.105f;
            float hx = 0.42f;
            float hy = 0.23f;
            screenQuad(pose, screen, cx - hx, cy - hy, cx + hx, cy + hy, zs);
        }
    }

    /** La pantalla, mirando hacia atrás (-z); del póster cuadrado se toma una franja ancha en vez de estirarlo. */
    private static void screenQuad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z) {
        float v0 = 0.24f;
        float v1 = 0.76f;
        vertex(pose, vc, FULL_BRIGHT, x1, y0, z, 0f, v1, 0, 0, -1, 255, 255, 255);
        vertex(pose, vc, FULL_BRIGHT, x0, y0, z, 1f, v1, 0, 0, -1, 255, 255, 255);
        vertex(pose, vc, FULL_BRIGHT, x0, y1, z, 1f, v0, 0, 0, -1, 255, 255, 255);
        vertex(pose, vc, FULL_BRIGHT, x1, y1, z, 0f, v0, 0, 0, -1, 255, 255, 255);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b) {
        face(pose, vc, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, r, g, b);
        face(pose, vc, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, r, g, b);
        face(pose, vc, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, b);
        face(pose, vc, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, r, g, b);
        face(pose, vc, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, r, g, b);
        face(pose, vc, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, r, g, b);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, int light, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int r, int g, int b) {
        vertex(pose, vc, light, ax, ay, az, 0f, 1f, nx, ny, nz, r, g, b);
        vertex(pose, vc, light, bx, by, bz, 1f, 1f, nx, ny, nz, r, g, b);
        vertex(pose, vc, light, cx, cy, cz, 1f, 0f, nx, ny, nz, r, g, b);
        vertex(pose, vc, light, dx, dy, dz, 0f, 0f, nx, ny, nz, r, g, b);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz, int r, int g, int b) {
        vc.vertex(pose.pose(), x, y, z).color(r, g, b, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
