package com.amatista.arsenal.client;

import com.amatista.arsenal.Timeline;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * Dibuja la escena espacial a pantalla completa: estrellas, Vía Láctea, sol con rayos de luz,
 * planeta con nubes, atmósfera y luces de ciudades, la luna en órbita, el satélite gigante
 * ÉGIDA-7 con sus partículas y el rayo.
 */
final class SpaceRenderer {
    private static final Vector3f SUN = new Vector3f((float) Cinematic.SUN_DIR.x, (float) Cinematic.SUN_DIR.y, (float) Cinematic.SUN_DIR.z);
    private static final float S = Cinematic.SAT_SCALE;
    private static final int STAR_COUNT = 7000;
    private static final int BAND_COUNT = 5000;
    private static float[] starDir;   // x,y,z
    private static float[] starInfo;  // tamaño, brillo, r, g, b, fase
    private static float[] dust;      // nubes de la Vía Láctea: x,y,z,tamaño,alpha,tono
    private static float[] rnd;       // números aleatorios fijos para las partículas

    // luna
    private static final float MOON_RADIUS = 270f;
    private static final int MOON_LAT = 40, MOON_LON = 80;
    private static float[] moonN;     // normales x,y,z
    private static float[] moonC;     // gris

    private SpaceRenderer() {}

    private static void buildStatic() {
        if (starDir != null) return;
        Random r = new Random(1337);
        int total = STAR_COUNT + BAND_COUNT;
        starDir = new float[total * 3];
        starInfo = new float[total * 6];
        Vector3f bandNormal = new Vector3f(0.35f, 0.8f, 0.48f).normalize();
        Vector3f bandA = new Vector3f(bandNormal).cross(new Vector3f(0, 0, 1)).normalize();
        Vector3f bandB = new Vector3f(bandNormal).cross(bandA).normalize();
        for (int i = 0; i < total; i++) {
            Vector3f d;
            if (i < STAR_COUNT) {
                double u = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                d = new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
            } else {
                double ang = r.nextDouble() * Math.PI * 2;
                float off = (float) (r.nextGaussian() * 0.09);
                d = new Vector3f(bandA).mul((float) Math.cos(ang)).add(new Vector3f(bandB).mul((float) Math.sin(ang)))
                        .add(new Vector3f(bandNormal).mul(off)).normalize();
            }
            starDir[i * 3] = d.x; starDir[i * 3 + 1] = d.y; starDir[i * 3 + 2] = d.z;
            float mag = (float) Math.pow(r.nextDouble(), 6);
            boolean band = i >= STAR_COUNT;
            starInfo[i * 6] = band ? 5f + mag * 8f : 6f + mag * 26f;
            starInfo[i * 6 + 1] = band ? 0.25f + mag * 0.6f : 0.35f + mag * 0.65f;
            float temp = r.nextFloat();
            float[] c = temp < 0.18f ? new float[]{0.65f, 0.75f, 1f}
                    : temp < 0.7f ? new float[]{1f, 1f, 1f}
                    : temp < 0.86f ? new float[]{1f, 0.93f, 0.7f}
                    : temp < 0.95f ? new float[]{1f, 0.75f, 0.5f}
                    : new float[]{1f, 0.55f, 0.45f};
            starInfo[i * 6 + 2] = c[0]; starInfo[i * 6 + 3] = c[1]; starInfo[i * 6 + 4] = c[2];
            starInfo[i * 6 + 5] = r.nextFloat() * 6.28f;
        }
        dust = new float[180 * 6];
        for (int i = 0; i < 180; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float off = (float) (r.nextGaussian() * 0.05);
            Vector3f d = new Vector3f(bandA).mul((float) Math.cos(ang)).add(new Vector3f(bandB).mul((float) Math.sin(ang)))
                    .add(new Vector3f(bandNormal).mul(off)).normalize();
            dust[i * 6] = d.x; dust[i * 6 + 1] = d.y; dust[i * 6 + 2] = d.z;
            dust[i * 6 + 3] = 350f + r.nextFloat() * 700f;
            dust[i * 6 + 4] = 0.02f + r.nextFloat() * 0.04f;
            dust[i * 6 + 5] = r.nextFloat();
        }
        rnd = new float[8192];
        for (int i = 0; i < rnd.length; i++) rnd[i] = r.nextFloat();

        // luna: gris con mares oscuros y cráteres
        int n = (MOON_LAT + 1) * (MOON_LON + 1);
        moonN = new float[n * 3];
        moonC = new float[n];
        for (int i = 0; i <= MOON_LAT; i++) {
            double theta = Math.PI * i / MOON_LAT;
            for (int j = 0; j <= MOON_LON; j++) {
                double phi = Math.PI * 2 * j / MOON_LON;
                int k = i * (MOON_LON + 1) + j;
                float x = (float) (Math.sin(theta) * Math.cos(phi)), y = (float) Math.cos(theta), z = (float) (Math.sin(theta) * Math.sin(phi));
                moonN[k * 3] = x; moonN[k * 3 + 1] = y; moonN[k * 3 + 2] = z;
                float maria = PlanetMesh.smoothstep(0.5f, 0.62f, PlanetMesh.fbm(x * 1.6f + 30, y * 1.6f, z * 1.6f - 9, 4));
                float crater = PlanetMesh.fbm(x * 9 + 3, y * 9 - 2, z * 9 + 7, 3);
                float g = 0.72f - 0.28f * maria - 0.18f * Math.max(0f, crater - 0.55f) * 4f + 0.08f * crater;
                moonC[k] = Mth.clamp(g, 0.25f, 0.85f);
            }
        }
    }

    private static float rnd(int i) {
        return rnd[(i & 0x7fffffff) % rnd.length];
    }

    /** Posición de la luna (orbita despacio alrededor del planeta). */
    private static Vector3f moonPosition(float time) {
        double base = Math.atan2(900, -2200);
        double ang = base + (time - 70f) * 0.004;
        double dist = Math.sqrt(2200 * 2200 + 900 * 900);
        return new Vector3f((float) (Math.cos(ang) * dist), 600f, (float) (Math.sin(ang) * dist));
    }

    // ------------------------------------------------------------------ estado del satélite

    private record SatState(float lights, float unfold, float antenna, float extend, float aim,
                            float charge, float fired, float recoil, float sinceFire) {}

    private static SatState satState(float t) {
        float u4 = Mth.clamp((t - Timeline.A3_END) / (Timeline.A4_END - Timeline.A3_END), 0f, 1f);
        float u5 = Mth.clamp((t - Timeline.A4_END) / (Timeline.A5_END - Timeline.A4_END), 0f, 1f);
        float lights = Cinematic.smooth((u4 - 0.36f) / 0.2f);
        float unfold = Cinematic.smooth((u4 - 0.45f) / 0.28f);
        float antenna = Cinematic.smooth((u4 - 0.62f) / 0.15f);
        float extend = Cinematic.smooth((u4 - 0.76f) / 0.2f);
        float aim = Cinematic.smooth(u5 / 0.3f);
        float charge = Cinematic.smooth((u5 - 0.08f) / 0.92f);
        float fired = t >= Timeline.A5_END ? 1f : 0f;
        float recoil = 0f, since = 0f;
        if (t >= Timeline.A5_END) {
            since = (t - Timeline.A5_END) / (Timeline.SPACE_END - Timeline.A5_END);
            recoil = 6f * (float) Math.exp(-since * 4) * Math.min(1f, since * 20);
        }
        return new SatState(lights, unfold, antenna, extend, aim, charge, fired, recoil, since);
    }

    /** Matriz del satélite (sin vista): local → escena espacial. */
    private static Matrix4f satModel(SatState s) {
        Vec3 sat = Cinematic.SATELLITE;
        return new Matrix4f().translate((float) sat.x, (float) sat.y + s.recoil * S, (float) sat.z).scale(S);
    }

    private static Matrix4f cannonFrame(SatState s) {
        return satModel(s).translate(0, -5.5f, 0).rotateX((1f - s.aim) * (float) Math.PI / 2f);
    }

    private static float barrelLength(SatState s) {
        return 8f + 34f * s.extend;
    }

    /** Posición de la boca del cañón en la escena espacial (la usa la cámara). */
    static Vec3 muzzlePosition(float t) {
        SatState s = satState(t);
        Vector3f p = cannonFrame(s).transformPosition(new Vector3f(0, -barrelLength(s) - 3f, 0));
        return new Vec3(p.x, p.y, p.z);
    }

    // ------------------------------------------------------------------ dibujo

    /** Prepara estrellas, luna y planeta (se llama al empezar la cinemática para no trabar después). */
    static void prepare() {
        buildStatic();
        PlanetMesh.build(SUN);
    }

    static void render(float partialTick) {
        prepare();
        Minecraft mc = Minecraft.getInstance();
        float t = Cinematic.progress(partialTick);
        float time = Cinematic.seconds(partialTick);
        Cinematic.SpacePose pose = Cinematic.spacePose(t);
        Vector3f eye = new Vector3f((float) pose.eye().x, (float) pose.eye().y, (float) pose.eye().z);
        Vector3f center = new Vector3f((float) pose.center().x, (float) pose.center().y, (float) pose.center().z);
        Vector3f forward = new Vector3f(center).sub(eye).normalize();
        Vector3f upHint = Math.abs(forward.y) > 0.97f ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(forward).cross(upHint).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        if (pose.roll() != 0f) {
            float rr = (float) Math.toRadians(pose.roll());
            float c = (float) Math.cos(rr), sn = (float) Math.sin(rr);
            Vector3f r2 = new Vector3f(right).mul(c).add(new Vector3f(up).mul(sn));
            Vector3f u2 = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(sn));
            right = r2.normalize();
            up = u2.normalize();
        }
        Matrix4f view = new Matrix4f().lookAt(eye.x, eye.y, eye.z, center.x, center.y, center.z, up.x, up.y, up.z);

        int w = mc.getWindow().getWidth(), h = Math.max(1, mc.getWindow().getHeight());
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, 1f, 80000f);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.clearColor(0f, 0f, 0.004f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        SatState sat = satState(t);
        Vector3f moon = moonPosition(time);

        // 1) cielo: estrellas, galaxia y sol (aditivo, sin profundidad)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawSky(bb, view, eye, right, up, time);
        drawSun(bb, view, eye, right, up, time);
        BufferUploader.drawWithShader(bb.end());

        // 2) cuerpos sólidos: planeta, luna y satélite
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderSurface(bb, view, Cinematic.PLANET_RADIUS, SUN, eye);
        drawMoon(bb, view, moon);
        drawSatellite(bb, view, sat, time);
        BufferUploader.drawWithShader(bb.end());

        // 3) nubes (transparencia normal)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderClouds(bb, view, Cinematic.PLANET_RADIUS * 1.012f, SUN, time * 0.004f);
        BufferUploader.drawWithShader(bb.end());

        // 4) brillos (aditivo): atmósfera, ciudades, objetivo, luces y partículas del satélite, rayo
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderAtmosphere(bb, view, Cinematic.PLANET_RADIUS * 1.035f, SUN, eye);
        PlanetMesh.renderCityLights(bb, view, Cinematic.PLANET_RADIUS, SUN, time);
        drawMoonGlow(bb, view, eye, right, up, moon);
        drawTargetMarker(bb, view, eye, right, up, time, t);
        drawSatelliteGlow(bb, view, eye, right, up, sat, time);
        drawParticles(bb, view, right, up, sat, time);
        drawBeam(bb, view, eye, right, up, sat, t, time);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    private static void drawSky(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time) {
        float dist = 9000f;
        for (int i = 0; i < dust.length; i += 6) {
            float cx = eye.x + dust[i] * dist, cy = eye.y + dust[i + 1] * dist, cz = eye.z + dust[i + 2] * dist;
            float tone = dust[i + 5];
            RenderUtil.billboard(bb, view, cx, cy, cz, right, up, dust[i + 3],
                    0.55f + tone * 0.4f, 0.45f + tone * 0.2f, 0.75f - tone * 0.2f, dust[i + 4]);
        }
        int total = STAR_COUNT + BAND_COUNT;
        for (int i = 0; i < total; i++) {
            float cx = eye.x + starDir[i * 3] * dist, cy = eye.y + starDir[i * 3 + 1] * dist, cz = eye.z + starDir[i * 3 + 2] * dist;
            float size = starInfo[i * 6];
            float twinkle = 0.75f + 0.25f * (float) Math.sin(time * 2.3f + starInfo[i * 6 + 5]);
            float a = starInfo[i * 6 + 1] * twinkle;
            RenderUtil.billboard(bb, view, cx, cy, cz, right, up, size,
                    starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a);
            if (size > 22f) {
                RenderUtil.billboard(bb, view, cx, cy, cz, right, up, size * 3.5f,
                        starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a * 0.12f);
            }
        }
    }

    private static void drawSun(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time) {
        float dist = 8000f;
        float cx = eye.x + SUN.x * dist, cy = eye.y + SUN.y * dist, cz = eye.z + SUN.z * dist;
        float pulse = 1f + 0.03f * (float) Math.sin(time * 1.7f);
        // halo enorme
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 4200f * pulse, 1f, 0.5f, 0.15f, 0.05f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 2400f * pulse, 1f, 0.6f, 0.25f, 0.09f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 1200f * pulse, 1f, 0.75f, 0.4f, 0.16f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 620f, 1f, 0.88f, 0.6f, 0.35f);
        // disco
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 330f, 1f, 0.96f, 0.82f, 0.8f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 250f, 1f, 1f, 0.97f, 1f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 230f, 1f, 1f, 1f, 1f);
        // rayos de luz que giran despacio
        for (int i = 0; i < 16; i++) {
            float ang = i * (float) Math.PI / 8f + time * 0.03f;
            float len = (1400f + 900f * rnd(i * 7)) * (0.85f + 0.15f * (float) Math.sin(time * 0.8f + i));
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            RenderUtil.streak(bb, view, cx + dir.x * len * 0.5f, cy + dir.y * len * 0.5f, cz + dir.z * len * 0.5f,
                    dir, len * 0.5f, side, 30f + 30f * rnd(i * 3), 1f, 0.85f, 0.6f, 0.07f);
        }
        // destello de lente horizontal
        RenderUtil.streak(bb, view, cx, cy, cz, right, 6500f, up, 30f, 1f, 0.8f, 0.6f, 0.2f);
        RenderUtil.streak(bb, view, cx, cy, cz, right, 2500f, up, 9f, 1f, 1f, 0.9f, 0.4f);
        RenderUtil.streak(bb, view, cx, cy, cz, up, 1300f, right, 18f, 1f, 0.85f, 0.7f, 0.1f);
    }

    private static void drawMoon(BufferBuilder bb, Matrix4f view, Vector3f moon) {
        for (int i = 0; i < MOON_LAT; i++) {
            for (int j = 0; j < MOON_LON; j++) {
                int k0 = i * (MOON_LON + 1) + j, k1 = k0 + 1, k2 = k0 + MOON_LON + 2, k3 = k0 + MOON_LON + 1;
                moonVertex(bb, view, moon, k0);
                moonVertex(bb, view, moon, k3);
                moonVertex(bb, view, moon, k2);
                moonVertex(bb, view, moon, k1);
            }
        }
    }

    private static void moonVertex(BufferBuilder bb, Matrix4f view, Vector3f moon, int k) {
        float x = moonN[k * 3], y = moonN[k * 3 + 1], z = moonN[k * 3 + 2];
        float d = x * SUN.x + y * SUN.y + z * SUN.z;
        float light = 0.02f + 0.98f * PlanetMesh.smoothstep(-0.05f, 0.35f, d);
        float g = moonC[k] * light;
        bb.vertex(view, moon.x + x * MOON_RADIUS, moon.y + y * MOON_RADIUS, moon.z + z * MOON_RADIUS)
                .color(g * 1.02f, g, g * 0.97f, 1f).endVertex();
    }

    private static void drawMoonGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, Vector3f moon) {
        // halo suave alrededor de la luna
        RenderUtil.billboard(bb, view, moon.x, moon.y, moon.z, right, up, MOON_RADIUS * 1.5f, 0.75f, 0.8f, 0.95f, 0.06f);
    }

    private static void drawTargetMarker(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                         float time, float t) {
        if (t >= Timeline.A5_END + 0.01f) return;
        Vec3 tp = Cinematic.TARGET_SPACE;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 6);
        float distance = eye.distance((float) tp.x, (float) tp.y, (float) tp.z);
        float size = Math.max(3f, distance * 0.006f);
        RenderUtil.billboard(bb, view, (float) tp.x, (float) tp.y + 1f, (float) tp.z, right, up, size, 1f, 0.1f, 0.08f, pulse);
        RenderUtil.billboard(bb, view, (float) tp.x, (float) tp.y + 1f, (float) tp.z, right, up, size * 4f, 1f, 0.1f, 0.05f, 0.25f * pulse);
        Matrix4f m = new Matrix4f(view).translate((float) tp.x, (float) tp.y + 1.2f, (float) tp.z);
        float ringR = 10f + 6f * (float) ((time * 0.8) % 1.0);
        RenderUtil.ring(bb, m, ringR, ringR + 1.6f, 0f, 48, 1f, 0.15f, 0.1f, 0.8f);
        RenderUtil.ring(bb, m, 22f, 23f, 0f, 64, 1f, 0.2f, 0.1f, 0.5f);
    }

    // ------------------------------------------------------------------ satélite ÉGIDA-7 (gigante)

    private static void drawSatellite(BufferBuilder bb, Matrix4f view, SatState s, float time) {
        Matrix4f m = new Matrix4f(view).mul(satModel(s));
        // cuerpo principal con lámina dorada acolchada
        RenderUtil.box(bb, m, -6, -5, -6, 6, 5, 6, 0.55f, 0.43f, 0.18f, 1f);
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                float shade = 0.85f + 0.15f * rnd(a * 3 + b);
                float y0 = -4.6f + b * 3.1f, y1 = y0 + 2.9f, z0 = -5.6f + a * 3.8f, z1 = z0 + 3.6f;
                RenderUtil.box(bb, m, 6f, y0, z0, 6.25f, y1, z1, 0.86f * shade, 0.68f * shade, 0.28f * shade, 1f);
                RenderUtil.box(bb, m, -6.25f, y0, z0, -6f, y1, z1, 0.86f * shade, 0.68f * shade, 0.28f * shade, 1f);
            }
        }
        RenderUtil.box(bb, m, -6.15f, -0.7f, -6.15f, 6.15f, 0.7f, 6.15f, 0.22f, 0.22f, 0.26f, 1f);
        // módulo de mando blanco con cúpula
        RenderUtil.cylinder(bb, new Matrix4f(m), 4.6f, 5f, 11f, 20, 0.93f, 0.93f, 0.96f, 1f);
        RenderUtil.ring(bb, m, 0f, 4.6f, 11f, 20, 0.8f, 0.82f, 0.88f, 1f);
        RenderUtil.cylinder(bb, new Matrix4f(m), 3f, 11f, 13.5f, 16, 0.35f, 0.36f, 0.42f, 1f);
        RenderUtil.cylinder(bb, new Matrix4f(m), 4.8f, 7.6f, 8.2f, 20, 0.3f, 0.3f, 0.35f, 1f);
        // radiadores blancos con aletas
        RenderUtil.box(bb, m, -5, -4, 6, 5, 4, 6.6f, 0.92f, 0.92f, 0.95f, 1f);
        RenderUtil.box(bb, m, -5, -4, -6.6f, 5, 4, -6, 0.92f, 0.92f, 0.95f, 1f);
        for (int i = -4; i <= 4; i += 2) {
            RenderUtil.box(bb, m, i - 0.2f, -4, 6.6f, i + 0.2f, 4, 7.4f, 0.75f, 0.75f, 0.8f, 1f);
            RenderUtil.box(bb, m, i - 0.2f, -4, -7.4f, i + 0.2f, 4, -6.6f, 0.75f, 0.75f, 0.8f, 1f);
        }
        // anillo giratorio (estabilizador) unido por cuatro radios
        Matrix4f ringM = new Matrix4f(m).rotateY(time * 0.15f);
        int seg = 36;
        for (int i = 0; i < seg; i++) {
            Matrix4f sm = new Matrix4f(ringM).rotateY((float) (Math.PI * 2 * i / seg));
            float half = (float) (Math.PI * 22 / seg) + 0.1f;
            RenderUtil.box(bb, sm, 21f, -1.4f, -half, 24f, 1.4f, half, 0.88f, 0.89f, 0.93f, 1f);
            RenderUtil.box(bb, sm, 21.2f, 1.4f, -half * 0.8f, 23.8f, 1.7f, half * 0.8f, 0.4f, 0.42f, 0.5f, 1f);
        }
        for (int i = 0; i < 4; i++) {
            Matrix4f sm = new Matrix4f(ringM).rotateY((float) (Math.PI / 2 * i + Math.PI / 4));
            RenderUtil.box(bb, sm, 6f, -0.5f, -0.5f, 21f, 0.5f, 0.5f, 0.6f, 0.6f, 0.65f, 1f);
        }
        // propulsores traseros
        Matrix4f thr = new Matrix4f(m).translate(0, 0, 7.4f).rotateX((float) Math.PI / 2);
        RenderUtil.cylinder(bb, new Matrix4f(thr).translate(-3, 0, 0), 1.3f, 0, 3.2f, 12, 0.2f, 0.2f, 0.22f, 1f);
        RenderUtil.cylinder(bb, new Matrix4f(thr).translate(3, 0, 0), 1.3f, 0, 3.2f, 12, 0.2f, 0.2f, 0.22f, 1f);

        // alas de paneles solares (se despliegan)
        for (int side = -1; side <= 1; side += 2) {
            float sx = side;
            RenderUtil.box(bb, m, sx > 0 ? 6 : -11, -0.7f, -0.7f, sx > 0 ? 11 : -6, 0.7f, 0.7f, 0.6f, 0.6f, 0.65f, 1f);
            float len = 4f + 60f * s.unfold;
            float x0 = sx > 0 ? 11 : -11 - len, x1 = sx > 0 ? 11 + len : -11;
            float span = 3f + 10f * s.unfold;
            RenderUtil.box(bb, m, x0, -0.25f, -span, x1, 0.25f, span, 0.05f, 0.09f, 0.30f, 1f);
            for (float gx = 0; gx <= len; gx += 4f) {
                float x = sx > 0 ? 11 + gx : -11 - gx;
                RenderUtil.box(bb, m, x - 0.12f, 0.25f, -span, x + 0.12f, 0.32f, span, 0.5f, 0.56f, 0.72f, 1f);
            }
            for (float gz = -span; gz <= span; gz += span / 3f) {
                RenderUtil.box(bb, m, x0, 0.25f, gz - 0.1f, x1, 0.3f, gz + 0.1f, 0.5f, 0.56f, 0.72f, 1f);
            }
            RenderUtil.box(bb, m, x0, -0.3f, span - 0.3f, x1, 0.35f, span + 0.25f, 0.75f, 0.75f, 0.78f, 1f);
            RenderUtil.box(bb, m, x0, -0.3f, -span - 0.25f, x1, 0.35f, -span + 0.3f, 0.75f, 0.75f, 0.78f, 1f);
        }

        // antena parabólica y antenas de varilla
        Matrix4f ant = new Matrix4f(m).translate(0, 13.5f, 0).rotateZ((1f - s.antenna) * -1.2f + 0.35f);
        RenderUtil.box(bb, ant, -0.4f, 0, -0.4f, 0.4f, 6, 0.4f, 0.7f, 0.7f, 0.72f, 1f);
        Matrix4f dish = new Matrix4f(ant).translate(0, 6, 0).rotateX(0.5f);
        for (int ring = 0; ring < 5; ring++) {
            float r0 = ring * 1.7f, r1 = r0 + 1.7f;
            Matrix4f dr = new Matrix4f(dish).translate(0, ring * 0.38f, 0);
            RenderUtil.ring(bb, dr, r0, r1, 0, 24, 0.9f - ring * 0.04f, 0.9f - ring * 0.04f, 0.92f, 1f);
        }
        RenderUtil.box(bb, dish, -0.2f, 0, -0.2f, 0.2f, 4f, 0.2f, 0.6f, 0.6f, 0.62f, 1f);
        RenderUtil.box(bb, m, 3.5f, 11f, 3.5f, 3.8f, 19f, 3.8f, 0.8f, 0.8f, 0.82f, 1f);
        RenderUtil.box(bb, m, -3.8f, 11f, -3.8f, -3.5f, 17f, -3.5f, 0.8f, 0.8f, 0.82f, 1f);

        // cañón de partículas con bobinas de cobre
        Matrix4f cf = new Matrix4f(view).mul(cannonFrame(s));
        float len = barrelLength(s);
        RenderUtil.box(bb, cf, -4, -2f, -4, 4, 1, 4, 0.28f, 0.28f, 0.32f, 1f);
        RenderUtil.cylinder(bb, cf, 3.2f, -len, -2f, 20, 0.45f, 0.46f, 0.52f, 1f);
        for (float y = -4f; y > -len + 2; y -= 4f) {
            RenderUtil.cylinder(bb, cf, 3.9f, y - 1.2f, y, 20, 0.78f, 0.45f, 0.2f, 1f);
        }
        RenderUtil.cylinder(bb, cf, 4.6f, -len - 3f, -len, 20, 0.25f, 0.25f, 0.3f, 1f);
        for (int i = 0; i < 4; i++) {
            float y = -3 - i * (len - 5) / 4f;
            Matrix4f fin = new Matrix4f(cf).rotateY(i * 0.4f);
            RenderUtil.box(bb, fin, -5.5f, y - 0.4f, -0.3f, 5.5f, y + 0.4f, 0.3f, 0.5f, 0.5f, 0.55f, 1f);
            RenderUtil.box(bb, fin, -0.3f, y - 0.4f, -5.5f, 0.3f, y + 0.4f, 5.5f, 0.5f, 0.5f, 0.55f, 1f);
        }
        // luces de estado del cuerpo
        int lit = Math.round(s.lights * 6);
        for (int i = 0; i < 6; i++) {
            boolean on = i < lit;
            float x = -4.5f + i * 1.8f;
            float r = on ? (i < 4 ? 0.2f : 1f) : 0.08f, g = on ? 1f : 0.08f, b = on ? (i < 4 ? 0.4f : 0.3f) : 0.1f;
            RenderUtil.box(bb, m, x - 0.5f, 2.2f, 6.05f, x + 0.5f, 3.2f, 6.35f, r, g, b, 1f);
        }
    }

    private static Vector3f local(Matrix4f model, float x, float y, float z) {
        return model.transformPosition(new Vector3f(x, y, z));
    }

    private static void glow(BufferBuilder bb, Matrix4f view, Vector3f p, Vector3f right, Vector3f up,
                             float size, float r, float g, float b, float a) {
        RenderUtil.billboard(bb, view, p.x, p.y, p.z, right, up, size * S, r, g, b, a);
    }

    private static void drawSatelliteGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                          SatState s, float time) {
        Matrix4f model = satModel(s);
        if (s.lights > 0.05f) {
            float blink = (time % 1.2f) < 0.25f ? 1f : 0.15f;
            float wing = 11f + 4f + 60f * s.unfold;
            glow(bb, view, local(model, -wing, 0, 0), right, up, 2.2f, 1f, 0.1f, 0.1f, blink * s.lights);
            glow(bb, view, local(model, -wing, 0, 0), right, up, 8f, 1f, 0.1f, 0.1f, 0.25f * blink * s.lights);
            glow(bb, view, local(model, wing, 0, 0), right, up, 2.2f, 0.1f, 1f, 0.2f, blink * s.lights);
            glow(bb, view, local(model, wing, 0, 0), right, up, 8f, 0.1f, 1f, 0.2f, 0.25f * blink * s.lights);
            float strobe = (time % 2f) < 0.08f ? 1f : 0f;
            glow(bb, view, local(model, 3.65f, 19.3f, 3.65f), right, up, 2.5f, 1f, 1f, 1f, strobe);
            glow(bb, view, local(model, 3.65f, 19.3f, 3.65f), right, up, 14f, 0.8f, 0.9f, 1f, 0.3f * strobe);
            // anillo estabilizador encendido (morado)
            Matrix4f ringM = new Matrix4f(view).mul(model).rotateY(time * 0.15f);
            RenderUtil.ring(bb, ringM, 24.05f, 24.6f, 0, 72, 0.7f, 0.35f, 1f, 0.8f * s.lights);
            RenderUtil.ring(bb, ringM, 24.6f, 27.5f, 0, 72, 0.6f, 0.3f, 1f, 0.12f * s.lights);
            // bordes azules de los paneles solares
            for (int side = -1; side <= 1; side += 2) {
                float len = 4f + 60f * s.unfold, span = 3f + 10f * s.unfold;
                float x0 = side > 0 ? 11 : -11 - len, x1 = side > 0 ? 11 + len : -11;
                Matrix4f mm = new Matrix4f(view).mul(model);
                RenderUtil.quad(bb, mm, x0, 0.4f, span + 0.25f, x1, 0.4f, span + 0.25f, x1, 0.4f, span + 0.7f, x0, 0.4f, span + 0.7f,
                        0.3f, 0.7f, 1f, 0.6f * s.lights);
                RenderUtil.quad(bb, mm, x0, 0.4f, -span - 0.7f, x1, 0.4f, -span - 0.7f, x1, 0.4f, -span - 0.25f, x0, 0.4f, -span - 0.25f,
                        0.3f, 0.7f, 1f, 0.6f * s.lights);
            }
        }
        // energía del cañón
        if (s.charge > 0f && s.fired < 1f) {
            Matrix4f cf = new Matrix4f(view).mul(cannonFrame(s));
            float len = barrelLength(s);
            for (int i = 0; i < 6; i++) {
                float y = -4 - i * (len - 6) / 5f;
                float spin = time * (2f + 18f * s.charge) + i;
                Matrix4f rm = new Matrix4f(cf).translate(0, y, 0).rotateY(spin);
                RenderUtil.ring(bb, rm, 4.4f, 5.1f, 0, 28, 0.3f, 0.8f, 1f, 0.75f * s.charge);
                RenderUtil.ring(bb, rm, 5.1f, 7f, 0, 28, 0.2f, 0.5f, 1f, 0.2f * s.charge);
            }
            Vector3f muzzle = cannonFrame(s).transformPosition(new Vector3f(0, -len - 3f, 0));
            float pulse = 1f + 0.15f * (float) Math.sin(time * (8 + 30 * s.charge));
            float core = (2f + 12f * s.charge) * pulse;
            glow(bb, view, muzzle, right, up, core * 3.5f, 0.2f, 0.5f, 1f, 0.25f * s.charge);
            glow(bb, view, muzzle, right, up, core * 1.6f, 0.5f, 0.8f, 1f, 0.6f * s.charge);
            glow(bb, view, muzzle, right, up, core * 0.7f, 1f, 1f, 1f, 0.95f * s.charge);
            if (s.charge > 0.4f) {
                Random r = new Random((long) (time * 12));
                int arcs = (int) (s.charge * 14);
                for (int i = 0; i < arcs; i++) {
                    Vector3f dir = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
                    float l = (5f + r.nextFloat() * 10f * s.charge) * S;
                    Vector3f mid = new Vector3f(muzzle).add(new Vector3f(dir).mul(l * 0.5f));
                    Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
                    RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, dir, l * 0.5f, side, 0.22f * S, 0.6f, 0.85f, 1f, 0.9f);
                }
            }
        }
    }

    /** Partículas: polvo brillante, chorros de los propulsores, energía que entra al cañón y chispas del disparo. */
    private static void drawParticles(BufferBuilder bb, Matrix4f view, Vector3f right, Vector3f up, SatState s, float time) {
        Matrix4f model = satModel(s);
        // cristales de hielo y polvo que flotan y brillan con el sol
        for (int i = 0; i < 220; i++) {
            float x = (rnd(i * 5) - 0.5f) * 160f, y = (rnd(i * 5 + 1) - 0.5f) * 80f, z = (rnd(i * 5 + 2) - 0.5f) * 160f;
            float drift = time * (0.4f + rnd(i * 5 + 3));
            x += (float) Math.sin(drift * 0.3f + i) * 4f;
            y += (float) Math.cos(drift * 0.2f + i) * 3f;
            float glint = (float) Math.pow(Math.max(0, Math.sin(time * (1.5f + rnd(i * 5 + 4) * 2) + i)), 12);
            glow(bb, view, local(model, x, y, z), right, up, 0.18f + glint * 0.5f, 0.85f, 0.9f, 1f, 0.15f + 0.85f * glint);
        }
        // chorros de los propulsores (azules)
        if (s.lights > 0.05f) {
            for (int k = -1; k <= 1; k += 2) {
                for (int i = 0; i < 48; i++) {
                    float ph = (rnd(i * 3 + k * 1000 + 5000) + time * 1.3f) % 1f;
                    float jx = (rnd(i * 3 + 1) - 0.5f) * ph * 2.5f, jy = (rnd(i * 3 + 2) - 0.5f) * ph * 2.5f;
                    Vector3f p = local(model, k * 3f + jx, jy, 10.6f + ph * 22f);
                    glow(bb, view, p, right, up, 0.5f + ph * 1.8f, 0.35f + 0.4f * (1 - ph), 0.6f + 0.3f * (1 - ph), 1f,
                            (1 - ph) * 0.55f * s.lights);
                }
            }
            // destellos del sol en los paneles
            for (int i = 0; i < 24; i++) {
                float len = 4f + 60f * s.unfold;
                float side = i % 2 == 0 ? 1 : -1;
                float x = side * (11 + rnd(i * 11) * len), z = (rnd(i * 11 + 1) - 0.5f) * (6 + 20 * s.unfold);
                float glint = (float) Math.pow(Math.max(0, Math.sin(time * 0.9f + i * 1.7f)), 30);
                if (glint > 0.02f) glow(bb, view, local(model, x, 0.5f, z), right, up, 1.2f + 2f * glint, 1f, 0.95f, 0.8f, glint);
            }
        }
        // energía que entra en espiral a la boca del cañón
        if (s.charge > 0.05f && s.fired < 1f) {
            Matrix4f cf = cannonFrame(s);
            float len = barrelLength(s);
            int count = (int) (360 * s.charge);
            for (int i = 0; i < count; i++) {
                float ph = (rnd(i * 2 + 9000) + time * (0.25f + 1.1f * s.charge)) % 1f;
                float radius = (1f - ph) * (14f + 30f * rnd(i * 2 + 9001));
                float ang = i * 2.399f + ph * 7f;
                Vector3f p = cf.transformPosition(new Vector3f((float) Math.cos(ang) * radius,
                        -len - 3f + (1 - ph) * 10f, (float) Math.sin(ang) * radius));
                glow(bb, view, p, right, up, 0.25f + 0.35f * ph, 0.45f + 0.5f * ph, 0.75f + 0.25f * ph, 1f, ph * s.charge * 0.9f);
            }
        }
        // chispas del disparo
        if (s.fired > 0f && s.sinceFire < 0.8f) {
            Vector3f muzzle = cannonFrame(s).transformPosition(new Vector3f(0, -barrelLength(s) - 3f, 0));
            float since = s.sinceFire;
            for (int i = 0; i < 280; i++) {
                Vector3f dir = new Vector3f(rnd(i * 3 + 12000) - 0.5f, rnd(i * 3 + 12001) - 0.5f, rnd(i * 3 + 12002) - 0.5f).normalize();
                float dist = since * (60f + 140f * rnd(i + 15000)) * S;
                Vector3f p = new Vector3f(muzzle).add(dir.mul(dist));
                glow(bb, view, p, right, up, 0.4f + rnd(i) * 0.6f, 1f, 0.8f + 0.2f * rnd(i + 1), 0.5f, (1f - since / 0.8f));
            }
            // anillos de choque que se expanden desde la boca
            Matrix4f cf = new Matrix4f(view).mul(cannonFrame(s));
            for (int i = 0; i < 3; i++) {
                float f = Mth.clamp(since * 3f - i * 0.25f, 0f, 1f);
                if (f <= 0f || f >= 1f) continue;
                float rr = 6f + f * 60f;
                RenderUtil.ring(bb, new Matrix4f(cf).translate(0, -barrelLength(s) - 3f - f * 20f, 0), rr, rr + 3f, 0, 64,
                        0.6f, 0.85f, 1f, (1f - f) * 0.7f);
            }
        }
    }

    private static void drawBeam(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                 SatState s, float t, float time) {
        if (t < Timeline.A5_END) return;
        Vector3f from = cannonFrame(s).transformPosition(new Vector3f(0, -barrelLength(s) - 3f, 0));
        Vec3 tp = Cinematic.TARGET_SPACE;
        Vector3f to = new Vector3f((float) tp.x, (float) tp.y, (float) tp.z);
        float travel = Mth.clamp((t - Timeline.A5_END) / ((Timeline.SPACE_END - Timeline.A5_END) * 0.9f), 0f, 1f);
        float front = travel < 0.18f ? 0.02f : Cinematic.easeInCubic((travel - 0.18f) / 0.82f);
        Vector3f end = new Vector3f(from).lerp(to, Math.max(0.02f, front));
        Vector3f axis = new Vector3f(end).sub(from);
        float length = axis.length();
        if (length < 0.01f) return;
        axis.div(length);
        Vector3f mid = new Vector3f(from).add(end).mul(0.5f);
        Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(mid)).normalize();
        float flick = 1f + 0.12f * (float) Math.sin(time * 50);
        // rayo GIGANTE: varias capas de luz
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 90f * flick, 0.12f, 0.25f, 1f, 0.10f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 55f * flick, 0.15f, 0.35f, 1f, 0.18f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 26f * flick, 0.3f, 0.7f, 1f, 0.5f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 10f, 0.85f, 0.95f, 1f, 0.95f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 4f, 1f, 1f, 1f, 1f);
        // espiral de energía alrededor del rayo
        Vector3f other = new Vector3f(axis).cross(side).normalize();
        for (int i = 0; i < 80; i++) {
            float f = (i / 80f + time * 0.6f) % 1f;
            if (f > front) continue;
            float ang = f * 60f + time * 8f;
            Vector3f p = new Vector3f(from).lerp(to, f)
                    .add(new Vector3f(side).mul((float) Math.cos(ang) * 40f))
                    .add(new Vector3f(other).mul((float) Math.sin(ang) * 40f));
            RenderUtil.billboard(bb, view, p.x, p.y, p.z, right, up, 7f, 0.5f, 0.8f, 1f, 0.5f);
        }
        // destello en la boca y en el frente
        RenderUtil.billboard(bb, view, from.x, from.y, from.z, right, up, 110f * flick, 0.5f, 0.8f, 1f, 0.6f);
        RenderUtil.billboard(bb, view, from.x, from.y, from.z, right, up, 40f * flick, 1f, 1f, 1f, 0.9f);
        RenderUtil.billboard(bb, view, end.x, end.y, end.z, right, up, 80f * flick, 0.7f, 0.9f, 1f, 0.8f);
        for (int i = 1; i <= 8; i++) {
            float f = ((time * 0.9f + i / 8f) % 1f);
            if (f > front) continue;
            Vector3f p = new Vector3f(from).lerp(to, f);
            RenderUtil.billboard(bb, view, p.x, p.y, p.z, right, up, 45f, 0.4f, 0.7f, 1f, 0.25f);
        }
        if (front > 0.97f) {
            RenderUtil.billboard(bb, view, to.x, to.y + 1, to.z, right, up, 320f * flick, 1f, 0.9f, 0.7f, 0.7f);
            RenderUtil.billboard(bb, view, to.x, to.y + 1, to.z, right, up, 800f, 1f, 0.6f, 0.3f, 0.25f);
        }
    }
}
