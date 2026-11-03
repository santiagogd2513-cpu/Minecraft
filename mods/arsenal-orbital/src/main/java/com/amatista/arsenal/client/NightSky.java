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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cosas del cielo nocturno normal (fuera de las cinemáticas, en el mundo normal):
 * - El satélite ÉGIDA-7 cruzando el cielo muy despacio, casi apagado (solo un reflejo tenue
 *   y una lucecita roja que parpadea débil).
 * - Una noche de cada 3: la galaxia de Andrómeda, pequeña, girando con las estrellas.
 * - Una noche de cada 24: auroras boreales hacia el norte.
 */
final class NightSky {
    private static final float R = 100f;

    private NightSky() {}

    /** Número de noche (la madrugada cuenta como parte de la noche anterior). */
    static long nightIndex(ClientLevel level) {
        return Math.floorDiv(level.getDayTime() - 12000L, 24000L);
    }

    static boolean andromedaNight(ClientLevel level) {
        return Math.floorMod(nightIndex(level), 3L) == 0;
    }

    static boolean auroraNight(ClientLevel level) {
        return Math.floorMod(nightIndex(level), 24L) == 0;
    }

    static void render(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        if (Cinematic.isActive()) return;
        // 0 de día, 1 en plena noche; la lluvia lo tapa
        float night = Mth.clamp(level.getStarBrightness(partialTick) * 2f, 0f, 1f) * (1f - level.getRainLevel(partialTick));
        if (night <= 0.02f) return;
        Matrix4f view = poseStack.last().pose();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        float time = (level.getGameTime() + partialTick) / 20f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // ---- brillos (aditivo): Andrómeda, auroras, reflejo y luz del satélite
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (andromedaNight(level)) drawAndromeda(bb, view, level, partialTick, night);
        if (auroraNight(level)) drawAurora(bb, view, right, up, time, night);
        Vector3f sat = satelliteDir(level, partialTick);
        if (sat.y > 0.02f) drawSatelliteGlow(bb, view, right, up, sat, time, night);
        BufferUploader.drawWithShader(bb.end());

        // ---- el satélite: una silueta oscura diminuta
        if (sat.y > 0.02f) {
            RenderSystem.defaultBlendFunc();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            drawSatelliteBody(bb, view, sat, level, partialTick, night);
            BufferUploader.drawWithShader(bb.end());
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ ÉGIDA-7

    /** Órbita inclinada: cruza el cielo de lado a lado en unos 5 minutos. */
    private static Vector3f satelliteDir(ClientLevel level, float partialTick) {
        double ang = (level.getGameTime() + partialTick) / 12000.0 * Math.PI * 2;
        Vector3f p = new Vector3f((float) Math.cos(ang), 0f, (float) Math.sin(ang));
        return p.rotateZ(1.15f).rotateY(0.6f).normalize();
    }

    private static void drawSatelliteBody(BufferBuilder bb, Matrix4f view, Vector3f dir, ClientLevel level, float partialTick, float night) {
        Vector3f p = new Vector3f(dir).mul(R);
        double ang = (level.getGameTime() + partialTick) / 12000.0 * Math.PI * 2;
        Matrix4f m = new Matrix4f(view).translate(p.x, p.y, p.z)
                .rotateTowards(-dir.x, -dir.y, -dir.z, 0, 1, 0).rotateZ((float) ang * 3f);
        float a = 0.9f * night;
        // cuerpo y paneles solares oscuros
        RenderUtil.box(bb, m, -0.09f, -0.07f, -0.07f, 0.09f, 0.07f, 0.07f, 0.16f, 0.14f, 0.11f, a);
        RenderUtil.box(bb, m, -0.42f, -0.012f, -0.05f, -0.11f, 0.012f, 0.05f, 0.05f, 0.07f, 0.13f, a);
        RenderUtil.box(bb, m, 0.11f, -0.012f, -0.05f, 0.42f, 0.012f, 0.05f, 0.05f, 0.07f, 0.13f, a);
    }

    private static void drawSatelliteGlow(BufferBuilder bb, Matrix4f view, Vector3f right, Vector3f up, Vector3f dir, float time, float night) {
        Vector3f p = new Vector3f(dir).mul(R * 0.99f);
        // un reflejo muy tenue del sol en los paneles
        float glint = 0.06f + 0.1f * (float) Math.pow(Math.max(0, Math.sin(time * 0.35f)), 12);
        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 0.35f, 10, 0.85f, 0.88f, 1f, glint * night, 0f);
        // luz de posición roja, débil, que parpadea cada 2,5 s
        float blink = (time % 2.5f) < 0.12f ? 1f : 0f;
        if (blink > 0f) {
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 0.18f, 8, 1f, 0.15f, 0.1f, 0.35f * night, 0f);
        }
    }

    // ------------------------------------------------------------------ Andrómeda

    private static void drawAndromeda(BufferBuilder bb, Matrix4f view, ClientLevel level, float partialTick, float night) {
        // gira junto con las estrellas de Minecraft
        Matrix4f m = new Matrix4f(view).rotateY((float) Math.toRadians(-90))
                .rotateX((float) Math.toRadians(level.getTimeOfDay(partialTick) * 360f));
        Vector3f c = new Vector3f(0.35f, -0.78f, 0.52f).normalize().mul(R);
        Vector3f n = new Vector3f(c).normalize();
        Vector3f a = new Vector3f(n).cross(0.2f, 0.3f, 1f).normalize();
        Vector3f b = new Vector3f(n).cross(a).normalize();
        // inclinada: un óvalo alargado
        Vector3f major = new Vector3f(a).mul(0.92f).add(new Vector3f(b).mul(0.39f)).normalize();
        Vector3f minor = new Vector3f(n).cross(major).normalize();
        float k = night;
        RenderUtil.softStreak(bb, m, c.x, c.y, c.z, major, 3.6f, minor, 1.1f, 0.62f, 0.66f, 0.85f, 0.10f * k);
        RenderUtil.softStreak(bb, m, c.x, c.y, c.z, major, 2.2f, minor, 0.7f, 0.75f, 0.76f, 0.9f, 0.16f * k);
        RenderUtil.softStreak(bb, m, c.x, c.y, c.z, major, 1.1f, minor, 0.38f, 0.95f, 0.88f, 0.75f, 0.25f * k);
        RenderUtil.softDisc(bb, m, c.x, c.y, c.z, major, minor, 0.32f, 12, 1f, 0.94f, 0.8f, 0.55f * k, 0f);
        // sus dos galaxias satélite (M32 y M110), puntitos borrosos
        Vector3f m32 = new Vector3f(c).add(new Vector3f(minor).mul(0.9f)).add(new Vector3f(major).mul(0.3f));
        Vector3f m110 = new Vector3f(c).sub(new Vector3f(minor).mul(1.6f)).sub(new Vector3f(major).mul(0.7f));
        RenderUtil.softDisc(bb, m, m32.x, m32.y, m32.z, major, minor, 0.16f, 8, 0.95f, 0.9f, 0.8f, 0.3f * k, 0f);
        RenderUtil.softDisc(bb, m, m110.x, m110.y, m110.z, major, minor, 0.28f, 8, 0.85f, 0.85f, 0.9f, 0.14f * k, 0f);
    }

    // ------------------------------------------------------------------ auroras

    private static void drawAurora(BufferBuilder bb, Matrix4f view, Vector3f right, Vector3f up, float time, float night) {
        Vector3f yAxis = new Vector3f(0, 1, 0);
        for (int band = 0; band < 3; band++) {
            int segs = 70;
            for (int s = 0; s < segs; s++) {
                float f = s / (float) (segs - 1);
                // de noroeste a noreste (el norte en Minecraft es -Z)
                float az = (float) Math.toRadians(-75 + 150 * f) + 0.08f * (float) Math.sin(time * 0.13f + band * 2f + f * 9f);
                float elev = (float) Math.toRadians(14 + band * 7 + 6 * Math.sin(f * 5 + time * 0.07f + band));
                float dist = R * 0.95f;
                float x = (float) (Math.sin(az) * Math.cos(elev)) * dist;
                float z = (float) (-Math.cos(az) * Math.cos(elev)) * dist;
                float y = (float) Math.sin(elev) * dist;
                float height = (14f + 8f * (float) Math.sin(f * 7 + time * 0.25f + band)) * (1f - band * 0.2f);
                float wave = 0.5f + 0.5f * (float) Math.sin(f * 23 - time * 0.9f + band * 1.7f);
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 0.4f + band + f * 3);
                Vector3f mid = new Vector3f(x, y + height * 0.5f, z);
                Vector3f side = new Vector3f(yAxis).cross(new Vector3f(mid)).normalize();
                float edge = Mth.clamp(Math.min(f, 1f - f) * 6f, 0f, 1f);
                float a = 0.10f * night * pulse * (0.5f + 0.5f * wave) * edge;
                // verde abajo, rosa y violeta arriba
                RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, yAxis, height * 0.5f, side, 2.6f, 0.2f, 1f, 0.5f, a);
                Vector3f top = new Vector3f(x, y + height * 1.05f, z);
                RenderUtil.softBeam(bb, view, top.x, top.y, top.z, yAxis, height * 0.35f, side, 2.4f, 0.85f, 0.3f, 0.9f, a * 0.6f);
                RenderUtil.softDisc(bb, view, x, y + 1f, z, right, up, 3f, 8, 0.3f, 1f, 0.6f, a * 0.8f, 0f);
            }
        }
    }
}
