package com.amatista.arsenal.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * El cielo durante el despegue (y al volver a bajar): a medida que la cámara sube y pasa las
 * nubes, el azul se va oscureciendo hacia el añil y luego al negro del espacio, aparecen las
 * estrellas poco a poco, el sol se vuelve un disco blanco sin cielo alrededor y en el horizonte
 * queda una franja fina y brillante de atmósfera. Así no hace falta un destello blanco.
 */
final class AscentSky {
    private static float[] stars;   // x,y,z,tamaño,brillo
    private static final float R = 120f;

    private AscentSky() {}

    /** 0 = en el suelo, 1 = al borde del espacio (según la altura de la cámara sobre el jugador). */
    static float altitude(double cameraY) {
        if (!Cinematic.isActive()) return 0f;
        double h = cameraY - Cinematic.playerEye().y;
        return Mth.clamp((float) ((h - 60) / 1300.0), 0f, 1f);
    }

    private static float smooth(float x) {
        return Cinematic.smooth(Mth.clamp(x, 0f, 1f));
    }

    private static void build() {
        if (stars != null) return;
        Random r = new Random(77);
        int n = 2600;
        stars = new float[n * 5];
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble() * 2 - 1, ph = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            stars[i * 5] = (float) (s * Math.cos(ph));
            stars[i * 5 + 1] = (float) u;
            stars[i * 5 + 2] = (float) (s * Math.sin(ph));
            float mag = r.nextFloat();
            mag = mag * mag * mag;
            stars[i * 5 + 3] = 0.12f + mag * 0.45f;
            stars[i * 5 + 4] = 0.3f + 0.7f * mag;
        }
    }

    /** Colores del cielo a esta altura: {cenit, horizonte}. */
    static float[][] colors(float a) {
        float k = smooth(a);
        float[] blueZ = {0.30f, 0.52f, 0.95f}, deepZ = {0.04f, 0.07f, 0.26f}, spaceZ = {0.0f, 0.0f, 0.012f};
        float[] blueH = {0.62f, 0.78f, 1.0f}, deepH = {0.22f, 0.36f, 0.78f}, spaceH = {0.05f, 0.09f, 0.25f};
        float[] z = new float[3], h = new float[3];
        for (int i = 0; i < 3; i++) {
            z[i] = k < 0.5f ? Mth.lerp(k * 2f, blueZ[i], deepZ[i]) : Mth.lerp((k - 0.5f) * 2f, deepZ[i], spaceZ[i]);
            h[i] = k < 0.5f ? Mth.lerp(k * 2f, blueH[i], deepH[i]) : Mth.lerp((k - 0.5f) * 2f, deepH[i], spaceH[i]);
        }
        return new float[][]{z, h};
    }

    static void render(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        float a = altitude(camera.getPosition().y);
        if (a <= 0.001f || Cinematic.isSpace(partialTick)) return;
        build();
        Minecraft mc = Minecraft.getInstance();
        float cover = smooth(a / 0.35f);          // cuánto tapa el cielo normal
        float[][] col = colors(a);
        float[] zen = col[0], hor = col[1];
        Matrix4f m = poseStack.last().pose();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        float time = Cinematic.seconds(partialTick);
        float sunAngle = mc.level == null ? 0f : mc.level.getSunAngle(partialTick);
        Vector3f sun = new Vector3f(-(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f).normalize();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // 1) bóveda del cielo con degradado
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                for (int q = 0; q < 4; q++) {
                    int ii = i + (q == 1 || q == 2 ? 1 : 0), jj = j + (q >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon;
                    float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
                    float f = smooth((y + 0.05f) / 0.65f);
                    float r = Mth.lerp(f, hor[0], zen[0]), g = Mth.lerp(f, hor[1], zen[1]), b = Mth.lerp(f, hor[2], zen[2]);
                    // el cielo se aclara del lado del sol
                    float toSun = Math.max(0f, x * sun.x + y * sun.y + z * sun.z);
                    float glow = (float) Math.pow(toSun, 6) * 0.35f * (1f - smooth(a * 1.2f) * 0.7f);
                    r += glow; g += glow * 0.85f; b += glow * 0.6f;
                    // debajo del horizonte: bruma que se funde con la niebla del suelo
                    if (y < 0f) {
                        float d = smooth(-y / 0.25f);
                        r = Mth.lerp(d, r, hor[0] * 0.7f); g = Mth.lerp(d, g, hor[1] * 0.7f); b = Mth.lerp(d, b, hor[2] * 0.75f);
                    }
                    bb.vertex(m, x * R, y * R, z * R).color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), cover).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());

        // 2) estrellas, franja de atmósfera en el horizonte y sol (aditivo)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float starA = smooth((a - 0.35f) / 0.55f);
        if (starA > 0.01f) {
            for (int i = 0; i < stars.length; i += 5) {
                float x = stars[i], y = stars[i + 1], z = stars[i + 2];
                if (y < -0.05f) continue;
                // las más brillantes aparecen primero
                float appear = smooth((starA - (1f - stars[i + 4]) * 0.6f) / 0.4f);
                if (appear <= 0.01f) continue;
                float tw = 0.8f + 0.2f * (float) Math.sin(time * 3f + i);
                float d = R * 0.95f;
                RenderUtil.softDisc(bb, m, x * d, y * d, z * d, right, up, stars[i + 3], 6, 0.9f, 0.93f, 1f, stars[i + 4] * appear * tw, 0f);
            }
        }
        // franja fina de atmósfera brillante sobre el horizonte
        float rim = smooth((a - 0.4f) / 0.4f);
        if (rim > 0.01f) {
            Matrix4f flat = new Matrix4f(m);
            for (int k = 0; k < 4; k++) {
                float y0 = -2f + k * 2.2f;
                float alpha = rim * (0.22f - k * 0.05f);
                RenderUtil.cylinder(bb, flat, R * 0.9f, y0, y0 + 2.4f, 96, 0.35f + k * 0.05f, 0.6f, 1f, alpha);
            }
        }
        // sol: con la altura deja de tener halo de cielo y se vuelve un disco blanco y nítido
        if (sun.y > -0.15f) {
            float d = R * 0.9f;
            float sx = sun.x * d, sy = sun.y * d, sz = sun.z * d;
            float sharp = smooth(a);
            RenderUtil.softDisc(bb, m, sx, sy, sz, right, up, 22f * (1f - 0.6f * sharp), 24, 1f, 0.85f, 0.6f, 0.25f * cover, 0f);
            RenderUtil.softDisc(bb, m, sx, sy, sz, right, up, 6f, 24, 1f, 0.95f, 0.85f, 0.7f * cover, 0f);
            RenderUtil.softDisc(bb, m, sx, sy, sz, right, up, 3.2f, 24, 1f, 1f, 1f, cover, 0.9f * cover);
            // destello de lente horizontal (cámara en el espacio)
            RenderUtil.softStreak(bb, m, sx, sy, sz, right, 40f * sharp, up, 0.4f, 1f, 0.9f, 0.8f, 0.4f * sharp * cover);
        }
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /** Color de la niebla del suelo a esta altura (para que el terreno se funda con el cielo). */
    static float[] fogColor(double cameraY) {
        float a = altitude(cameraY);
        if (a <= 0.001f) return null;
        float[] h = colors(a)[1];
        return new float[]{h[0] * 0.85f, h[1] * 0.85f, h[2] * 0.9f, smooth(a / 0.35f)};
    }
}
