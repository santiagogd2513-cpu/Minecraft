package com.amatista.arsenal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Random;

import static com.amatista.arsenal.StarTimeline.*;
import static com.amatista.arsenal.client.BlackHoleRenderer.begin;
import static com.amatista.arsenal.client.BlackHoleRenderer.beginGlow;
import static com.amatista.arsenal.client.BlackHoleRenderer.beginSolid;
import static com.amatista.arsenal.client.BlackHoleRenderer.draw;

/** Escenas del "Protocolo Prometeo": el gran viaje, la hipergigante azul, la estación y el láser. */
final class StarRenderer {
    private static final Vector3f SUN = SpaceRenderer.SUN;
    private static final int LAT = 160, LON = 320;
    private static float[] sn;            // normales de la estrella
    private static float[] g1, g2, spot;  // granulado (dos fases) y manchas
    private static float[] rnd;
    private static float[][] fieldGalaxies; // x,y,z, rotX, rotY, escala

    private StarRenderer() {}

    private static float rnd(int i) {
        if (rnd == null) {
            Random r = new Random(2718);
            rnd = new float[16384];
            for (int k = 0; k < rnd.length; k++) rnd[k] = r.nextFloat();
        }
        return rnd[(i & 0x7fffffff) % rnd.length];
    }

    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    static void build() {
        if (sn != null) return;
        int n = (LAT + 1) * (LON + 1);
        sn = new float[n * 3];
        g1 = new float[n]; g2 = new float[n]; spot = new float[n];
        for (int i = 0; i <= LAT; i++) {
            double th = Math.PI * i / LAT;
            for (int j = 0; j <= LON; j++) {
                double ph = Math.PI * 2 * j / LON;
                int k = i * (LON + 1) + j;
                float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
                sn[k * 3] = x; sn[k * 3 + 1] = y; sn[k * 3 + 2] = z;
                g1[k] = PlanetMesh.fbm(x * 22f, y * 22f, z * 22f, 3);
                g2[k] = PlanetMesh.fbm(x * 22f + 13f, y * 22f - 7f, z * 22f + 3f, 3);
                spot[k] = PlanetMesh.smoothstep(0.66f, 0.74f, PlanetMesh.fbm(x * 3f + 50f, y * 3f, z * 3f, 4));
            }
        }
        Random r = new Random(77);
        fieldGalaxies = new float[9][];
        for (int i = 0; i < fieldGalaxies.length; i++) {
            float z = 20000f - i * 75000f - r.nextFloat() * 20000f;
            float x = (r.nextBoolean() ? 1 : -1) * (25000f + r.nextFloat() * 45000f);
            float y = (r.nextFloat() - 0.5f) * 40000f;
            fieldGalaxies[i] = new float[]{x, y, z, r.nextFloat() * 3f, r.nextFloat() * 6f, 0.25f + r.nextFloat() * 0.35f};
        }
    }

    // ------------------------------------------------------------------ entrada

    static void render(float partialTick) {
        SpaceRenderer.prepare();
        BlackHoleRenderer.build();
        build();
        Minecraft mc = Minecraft.getInstance();
        float t = Cinematic.progress(partialTick);
        float time = Cinematic.seconds(partialTick);
        StarDirector.Scene scene = StarDirector.scene(t);
        Cinematic.SpacePose pose = StarDirector.spacePose(t);
        Vector3f eye = v(pose.eye());
        Vector3f center = v(pose.center());
        Vector3f forward = new Vector3f(center).sub(eye).normalize();
        Vector3f upHint = Math.abs(forward.y) > 0.97f ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(forward).cross(upHint).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        if (pose.roll() != 0f) {
            float rr = (float) Math.toRadians(pose.roll());
            float c = (float) Math.cos(rr), s = (float) Math.sin(rr);
            Vector3f r2 = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s));
            Vector3f u2 = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s));
            right = r2.normalize();
            up = u2.normalize();
        }
        Matrix4f view = new Matrix4f().lookAt(eye.x, eye.y, eye.z, center.x, center.y, center.z, up.x, up.y, up.z);
        int w = mc.getWindow().getWidth(), h = Math.max(1, mc.getWindow().getHeight());
        float near = scene == StarDirector.Scene.STAR || scene == StarDirector.Scene.EARTH || scene == StarDirector.Scene.SOLAR ? 4f : 40f;
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, near, 4_000_000f);

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
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        float warp = StarDirector.warp(t);
        switch (scene) {
            case SOLAR -> BlackHoleRenderer.renderSolarScene(bb, view, eye, forward, right, up, warp, time);
            case MILKY_WAY -> {
                beginGlow(false);
                begin(bb);
                BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 1f);
                BlackHoleRenderer.milky.render(bb, view, new Matrix4f().rotateY(time * 0.003f), eye, right, up, 1f);
                BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, warp, time, 0.8f, 0.9f, 1f, 6f);
                draw(bb);
            }
            case ANDROMEDA -> {
                beginGlow(false);
                begin(bb);
                BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 1f);
                BlackHoleRenderer.andromeda.render(bb, view, BlackHoleRenderer.andromedaModel(time), eye, right, up, 0.8f);
                BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, warp, time, 0.9f, 0.85f, 1f, 8f);
                draw(bb);
            }
            case FIELD -> {
                beginGlow(false);
                begin(bb);
                BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 1f);
                for (float[] g : fieldGalaxies) {
                    Matrix4f model = new Matrix4f().translate(g[0], g[1], g[2]).rotateX(g[3]).rotateY(g[4] + time * 0.01f).scale(g[5]);
                    BlackHoleRenderer.Galaxy src = ((int) g[4]) % 2 == 0 ? BlackHoleRenderer.milky : BlackHoleRenderer.andromeda;
                    src.render(bb, view, model, eye, right, up, 0.9f, g[5], 3);
                }
                BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, warp, time, 0.85f, 0.85f, 1f, 8f);
                draw(bb);
            }
            case VOID -> renderVoid(bb, view, eye, forward, right, up, t, time);
            case STAR -> renderStarScene(bb, view, eye, forward, right, up, t, time);
            case CHASE -> renderChase(bb, view, eye, forward, right, up, t, time);
            case EARTH -> renderEarth(bb, view, eye, forward, right, up, t, time);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    // ------------------------------------------------------------------ la mitad de la nada

    private static void renderVoid(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = StarDirector.seg(t, FIELD_END, VOID_END);
        beginGlow(false);
        begin(bb);
        // solo un puñado de galaxias diminutas, muy lejos
        BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 0.12f);
        // al fondo empieza a brillar una luz azul
        float glow = u * u;
        Vector3f p = new Vector3f(eye).add(new Vector3f(forward).mul(9000f));
        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 6f + 160f * glow, 24, 0.55f, 0.75f, 1f, 0.9f, 0f);
        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 3f + 40f * glow, 24, 0.9f, 0.95f, 1f, 1f, 0f);
        RenderUtil.softStreak(bb, view, p.x, p.y, p.z, right, 40f + 900f * glow, up, 3f + 6f * glow, 0.6f, 0.8f, 1f, 0.5f * glow);
        draw(bb);
    }

    // ------------------------------------------------------------------ la estrella azul

    /** Brillo de la estrella: baja un poco mientras la estación le roba energía y destella al disparar. */
    private static float starBrightness(float t) {
        float dim = 1f - 0.18f * StarDirector.charge(t);
        if (t >= CHARGE_END && t < FIRE_END) dim += 0.35f * (1f - StarDirector.seg(t, CHARGE_END, FIRE_END));
        return dim;
    }

    private static float sdot(int idx, Vector3f e) {
        return sn[idx * 3] * e.x + sn[idx * 3 + 1] * e.y + sn[idx * 3 + 2] * e.z;
    }

    private static void drawStar(BufferBuilder bb, Matrix4f view, Vector3f eye, float time, float bright, float siphon) {
        float R = StarDirector.STAR_R;
        Vector3f sip = v(StarDirector.SIPHON).normalize();
        float phase = 0.5f + 0.5f * (float) Math.sin(time * 0.35f);
        // solo la cara que mira a la cámara, y menos detalle cuando la estrella se ve pequeña
        float ed = eye.length();
        boolean cull = ed > R * 1.001f;
        float lim = R * 0.985f;
        int step = Lod.step(R, ed);
        for (int i = 0; i < LAT; i += step) {
            int i2 = Math.min(LAT, i + step);
            for (int j = 0; j < LON; j += step) {
                int j2 = Math.min(LON, j + step);
                if (cull) {
                    int a = i * (LON + 1) + j, b = i2 * (LON + 1) + j, c = i2 * (LON + 1) + j2, d = i * (LON + 1) + j2;
                    if (sdot(a, eye) <= lim && sdot(b, eye) <= lim && sdot(c, eye) <= lim && sdot(d, eye) <= lim) continue;
                }
                for (int k = 0; k < 4; k++) {
                    int ii = (k == 1 || k == 2) ? i2 : i, jj = k >= 2 ? j2 : j;
                    int idx = ii * (LON + 1) + jj;
                    float x = sn[idx * 3], y = sn[idx * 3 + 1], z = sn[idx * 3 + 2];
                    float px = x * R, py = y * R, pz = z * R;
                    float vx = eye.x - px, vy = eye.y - py, vz = eye.z - pz;
                    float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                    float mu = Math.max(0f, (x * vx + y * vy + z * vz) / vl);
                    float gran = Mth.lerp(phase, g1[idx], g2[idx]);
                    float inten = (0.5f + 0.5f * (float) Math.pow(mu, 0.55)) * (0.8f + 0.28f * gran) * (1f - 0.35f * spot[idx]) * bright;
                    float limb = (float) Math.pow(mu, 0.5);
                    float r = Mth.lerp(limb, 0.32f, 0.82f), g = Mth.lerp(limb, 0.5f, 0.9f), b = 1f;
                    // la zona de la que la estación absorbe energía: más caliente y brillante
                    float hot = PlanetMesh.smoothstep(0.93f, 0.995f, x * sip.x + y * sip.y + z * sip.z) * siphon;
                    r += hot * 0.3f; g += hot * 0.2f;
                    bb.vertex(view, px, py, pz).color(Math.min(1f, r * inten), Math.min(1f, g * inten), Math.min(1f, b * inten), 1f).endVertex();
                }
            }
        }
    }

    private static void drawStarGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time, float bright) {
        float R = StarDirector.STAR_R;
        float pulse = 1f + 0.03f * (float) Math.sin(time * 1.3f);
        // corona azul en capas
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, R * 1.12f, 64, 0.6f, 0.8f, 1f, 0f, 0.7f * bright);
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, R * 1.6f * pulse, 64, 0.45f, 0.65f, 1f, 0.35f * bright, 0f);
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, R * 2.6f * pulse, 64, 0.35f, 0.55f, 1f, 0.16f * bright, 0f);
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, R * 5f, 64, 0.3f, 0.45f, 1f, 0.06f * bright, 0f);
        // serpentinas de la corona
        for (int i = 0; i < 40; i++) {
            float ang = i * 6.2832f / 40f + rnd(i) * 0.15f + time * 0.004f;
            float len = R * (0.5f + 1.3f * rnd(i + 50)) * (0.85f + 0.15f * (float) Math.sin(time * 0.4f + i));
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            Vector3f c = new Vector3f(dir).mul(R * 1.02f + len * 0.5f);
            RenderUtil.softStreak(bb, view, c.x, c.y, c.z, dir, len * 0.5f, side, R * (0.03f + 0.05f * rnd(i + 99)), 0.7f, 0.85f, 1f, 0.16f * bright);
        }
        // protuberancias: arcos de plasma que salen y vuelven a la superficie
        for (int l = 0; l < 12; l++) {
            Vector3f a = new Vector3f(rnd(l * 7) - 0.5f, rnd(l * 7 + 1) - 0.5f, rnd(l * 7 + 2) - 0.5f).normalize();
            Vector3f tdir = new Vector3f(a).cross(new Vector3f(0.3f, 1, 0.2f)).normalize();
            Vector3f b = new Vector3f(a).add(new Vector3f(tdir).mul(0.12f + 0.12f * rnd(l * 7 + 3))).normalize();
            float height = R * (0.08f + 0.22f * rnd(l * 7 + 4)) * (0.8f + 0.2f * (float) Math.sin(time * 0.5f + l));
            for (int k = 0; k <= 34; k++) {
                float s = k / 34f;
                float flow = (s + time * 0.08f) % 1f;
                Vector3f p = new Vector3f(a).lerp(b, s).normalize().mul(R + height * (float) Math.sin(Math.PI * s));
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, R * 0.03f * (0.7f + 0.6f * flow), 8,
                        0.85f, 0.75f + 0.2f * flow, 1f, 0.55f * bright, 0f);
            }
        }
        // viento estelar: partículas que se alejan
        for (int i = 0; i < 500; i++) {
            Vector3f d = new Vector3f(rnd(i * 3 + 1000) - 0.5f, rnd(i * 3 + 1001) - 0.5f, rnd(i * 3 + 1002) - 0.5f).normalize();
            float ph = (rnd(i + 3000) + time * 0.03f) % 1f;
            Vector3f p = new Vector3f(d).mul(R * (1.05f + 2.5f * ph));
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, R * 0.008f, 6, 0.75f, 0.85f, 1f, 0.4f * (1f - ph) * bright, 0f);
        }
        // llamaradas que aparecen y se apagan
        for (int i = 0; i < 6; i++) {
            float cycle = (time * 0.12f + rnd(i + 4000)) % 1f;
            if (cycle > 0.25f) continue;
            float f = cycle / 0.25f;
            Vector3f d = new Vector3f(rnd(i * 3 + 4100) - 0.5f, rnd(i * 3 + 4101) - 0.5f, rnd(i * 3 + 4102) - 0.5f).normalize().mul(R * 1.01f);
            RenderUtil.softDisc(bb, view, d.x, d.y, d.z, right, up, R * (0.05f + 0.2f * f), 16, 0.95f, 0.97f, 1f, (1f - f) * bright, 0f);
        }
    }

    // ------------------------------------------------------------------ la estación Prometeo

    private static Matrix4f stationModel() {
        Vec3 s = StarDirector.STATION;
        return new Matrix4f().translate((float) s.x, (float) s.y, (float) s.z)
                .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), v(StarDirector.D))).scale(StarDirector.SC);
    }

    private static void drawStation(BufferBuilder bb, Matrix4f view, float time, float recoil) {
        Matrix4f m = new Matrix4f(view).mul(new Matrix4f().translate(v(StarDirector.D).mul(-recoil))).mul(stationModel());
        // columna central (el cañón)
        RenderUtil.cylinder(bb, m, 6f, -92f, 170f, 24, 0.36f, 0.37f, 0.42f, 1f);
        for (float y = -84f; y < 150f; y += 12f) RenderUtil.cylinder(bb, m, 7.2f, y, y + 2f, 24, 0.22f, 0.22f, 0.26f, 1f);
        // condensadores
        for (int k = 0; k < 8; k++) {
            float y = 10f + k * 18f;
            RenderUtil.cylinder(bb, m, 16f, y, y + 5f, 32, 0.55f, 0.56f, 0.62f, 1f);
            RenderUtil.ring(bb, m, 7f, 16f, y, 32, 0.4f, 0.4f, 0.46f, 1f);
            RenderUtil.ring(bb, m, 7f, 16f, y + 5f, 32, 0.45f, 0.45f, 0.5f, 1f);
        }
        // anillos de enfoque y boca
        float[][] focus = {{150f, 24f}, {159f, 20f}, {167f, 15f}};
        for (float[] f : focus) {
            RenderUtil.cylinder(bb, m, f[1], f[0], f[0] + 3f, 32, 0.7f, 0.62f, 0.4f, 1f);
            RenderUtil.ring(bb, m, 9f, f[1], f[0], 32, 0.5f, 0.45f, 0.3f, 1f);
        }
        RenderUtil.cylinder(bb, m, 10f, 165f, 178f, 24, 0.25f, 0.25f, 0.3f, 1f);
        // anillo habitable que gira
        Matrix4f hab = new Matrix4f(m).rotateY(time * 0.04f);
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            Matrix4f sm = new Matrix4f(hab).rotateY((float) (Math.PI * 2 * i / seg));
            float half = (float) (Math.PI * 60 / seg) + 0.4f;
            RenderUtil.box(bb, sm, 56f, -4f, -half, 64f, 4f, half, 0.85f, 0.86f, 0.9f, 1f);
        }
        for (int i = 0; i < 6; i++) {
            Matrix4f sm = new Matrix4f(hab).rotateY((float) (Math.PI / 3 * i));
            RenderUtil.box(bb, sm, 6f, -1.2f, -1.2f, 56f, 1.2f, 1.2f, 0.5f, 0.5f, 0.55f, 1f);
        }
        // paneles radiadores
        for (int i = 0; i < 4; i++) {
            Matrix4f sm = new Matrix4f(m).rotateY((float) (Math.PI / 2 * i + Math.PI / 4));
            RenderUtil.box(bb, sm, 8f, -60f, -0.6f, 90f, -20f, 0.6f, 0.75f, 0.76f, 0.8f, 1f);
            for (float x = 14f; x < 90f; x += 8f) RenderUtil.box(bb, sm, x, -60f, -0.9f, x + 0.8f, -20f, 0.9f, 0.5f, 0.5f, 0.55f, 1f);
        }
        // colector parabólico que mira a la estrella
        for (int ring = 0; ring < 9; ring++) {
            float r0 = ring * 8f, r1 = r0 + 8f;
            float y = -92f - 0.006f * r1 * r1;
            RenderUtil.ring(bb, m, r0, r1, y, 64, 0.85f - ring * 0.03f, 0.8f - ring * 0.03f, 0.6f, 1f);
        }
        RenderUtil.box(bb, m, -3f, -120f, -3f, 3f, -92f, 3f, 0.4f, 0.4f, 0.45f, 1f);
        // módulos de atraque
        for (int i = 0; i < 4; i++) {
            Matrix4f sm = new Matrix4f(m).rotateY((float) (Math.PI / 2 * i));
            RenderUtil.box(bb, sm, 6f, -10f, -5f, 22f, 0f, 5f, 0.6f, 0.6f, 0.66f, 1f);
        }
    }

    private static void drawStationGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time, float recoil) {
        Matrix4f model = new Matrix4f().translate(v(StarDirector.D).mul(-recoil)).mul(stationModel());
        Matrix4f m = new Matrix4f(view).mul(model);
        float ch = StarDirector.charge(t);
        float u = StarDirector.seg(t, STATION_END, CHARGE_END);
        // luces de navegación y ventanas del anillo
        float blink = (time % 1.4f) < 0.3f ? 1f : 0.2f;
        for (int i = 0; i < 6; i++) {
            float a = (float) (Math.PI / 3 * i) + time * 0.04f;
            Vector3f p = model.transformPosition(new Vector3f((float) Math.cos(a) * 64f, 0, (float) Math.sin(a) * 64f));
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 60f, 8, i % 2 == 0 ? 1f : 0.2f, i % 2 == 0 ? 0.2f : 1f, 0.3f, blink, 0f);
        }
        Matrix4f hab = new Matrix4f(m).rotateY(time * 0.04f);
        RenderUtil.ring(bb, hab, 64.1f, 64.6f, 0, 96, 1f, 0.85f, 0.5f, 0.6f);
        // condensadores que se encienden uno a uno
        for (int k = 0; k < 8; k++) {
            float on = Mth.clamp((u - 0.4f - k * 0.035f) / 0.03f, 0f, 1f);
            if (on <= 0f) continue;
            float y = 10f + k * 18f;
            float fl = 0.85f + 0.15f * (float) Math.sin(time * 20 + k);
            RenderUtil.ring(bb, m, 15.5f, 17.5f, y + 2.5f, 48, 0.5f, 0.8f, 1f, on * fl);
            RenderUtil.ring(bb, m, 17.5f, 24f, y + 2.5f, 48, 0.4f, 0.65f, 1f, 0.25f * on);
        }
        // energía recorriendo el cañón hacia la boca
        if (ch > 0.05f) {
            for (int i = 0; i < 10; i++) {
                float y = -90f + ((time * (40f + 160f * ch) + i * 26f) % 260f);
                Matrix4f rm = new Matrix4f(m).translate(0, y, 0).rotateY(time * 3f);
                RenderUtil.ring(bb, rm, 6.5f, 9.5f, 0, 32, 0.6f, 0.85f, 1f, 0.6f * ch);
            }
        }
        // carga en la boca: una esfera de luz que crece
        Vector3f muzzle = model.transformPosition(new Vector3f(0, 180f, 0));
        if (ch > 0.02f && t < CHARGE_END) {
            float pulse = 1f + 0.1f * (float) Math.sin(time * (6 + 30 * ch));
            float size = (200f + 2200f * ch * ch) * pulse;
            RenderUtil.softDisc(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, size * 3f, 32, 0.4f, 0.6f, 1f, 0.25f * ch, 0f);
            RenderUtil.softDisc(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, size * 1.4f, 32, 0.6f, 0.85f, 1f, 0.6f * ch, 0f);
            RenderUtil.softDisc(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, size * 0.6f, 32, 1f, 1f, 1f, 0.95f * ch, 0f);
            for (int r = 0; r < 4; r++) { // anillos de energía alrededor de la boca
                Matrix4f rm = new Matrix4f(m).translate(0, 182f + r * 6f, 0).rotateY(time * (2f + r));
                RenderUtil.ring(bb, rm, 20f + r * 6f, 22f + r * 6f, 0, 48, 0.55f, 0.8f, 1f, 0.7f * ch);
            }
            if (ch > 0.4f) { // rayos eléctricos
                Random rr = new Random((long) (time * 14));
                for (int i = 0; i < (int) (ch * 24); i++) {
                    Vector3f dir = new Vector3f(rr.nextFloat() - 0.5f, rr.nextFloat() - 0.5f, rr.nextFloat() - 0.5f).normalize();
                    float l = 400f + rr.nextFloat() * 1800f * ch;
                    Vector3f mid = new Vector3f(muzzle).add(new Vector3f(dir).mul(l * 0.5f));
                    Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
                    RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, l * 0.5f, side, 30f, 0.7f, 0.85f, 1f, 0.9f);
                }
            }
        }
    }

    /** Ríos de plasma que salen de la estrella y entran en el colector de la estación. */
    private static void drawSiphon(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time) {
        float u = StarDirector.seg(t, STATION_END, CHARGE_END + 0.005f);
        if (u <= 0f || t > CHARGE_END + 0.005f) return;
        float on = Cinematic.smooth(u / 0.12f) * (t < CHARGE_END ? 1f : 0f);
        Vector3f a = v(StarDirector.SIPHON);
        Vector3f b = v(StarDirector.DISH);
        Vector3f axis = new Vector3f(b).sub(a);
        float len = axis.length();
        axis.div(len);
        Vector3f s1 = new Vector3f(axis).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f s2 = new Vector3f(axis).cross(s1).normalize();
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(mid)).normalize();
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 1600f, 0.4f, 0.65f, 1f, 0.12f * on);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 450f, 0.7f, 0.88f, 1f, 0.35f * on);
        for (int st = 0; st < 7; st++) {
            for (int k = 0; k < 70; k++) {
                float s = (k / 70f + time * (0.12f + 0.05f * st)) % 1f;
                float ang = st * 0.9f + s * 9f + time * 1.5f;
                float rad = (1f - s) * 1400f + 120f;
                Vector3f p = new Vector3f(a).lerp(b, s)
                        .add(new Vector3f(s1).mul((float) Math.cos(ang) * rad * (float) Math.sin(Math.PI * s)))
                        .add(new Vector3f(s2).mul((float) Math.sin(ang) * rad * (float) Math.sin(Math.PI * s)));
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 260f - 150f * s, 8, 0.65f + 0.35f * s, 0.85f, 1f, 0.45f * on, 0f);
            }
        }
        // remolino en la boca del colector
        Matrix4f m = new Matrix4f(view).mul(stationModel());
        Matrix4f sw = new Matrix4f(m).translate(0, -100f, 0).rotateY(-time * 3f);
        RenderUtil.ring(bb, sw, 10f, 60f, 0, 64, 0.6f, 0.85f, 1f, 0.35f * on);
    }

    /** El láser más grande del universo. */
    private static void drawBeam(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time, float front) {
        if (!StarDirector.fired(t)) return;
        Vector3f from = v(StarDirector.MUZZLE);
        Vector3f dir = v(StarDirector.D);
        float len = Math.max(10f, front);
        Vector3f mid = new Vector3f(from).add(new Vector3f(dir).mul(len * 0.5f));
        Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
        float since = (t - CHARGE_END) * Cinematic.durationSeconds();
        float flick = 1f + 0.08f * (float) Math.sin(time * 60);
        float swell = Math.min(1f, since * 1.5f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 4200f * swell * flick, 0.25f, 0.4f, 1f, 0.08f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 2400f * swell * flick, 0.3f, 0.55f, 1f, 0.16f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 1100f * swell, 0.5f, 0.78f, 1f, 0.45f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 420f * swell, 0.82f, 0.94f, 1f, 0.9f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 140f, 1f, 1f, 1f, 1f);
        // hélices de energía que giran alrededor del rayo
        Vector3f s1 = new Vector3f(dir).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f s2 = new Vector3f(dir).cross(s1).normalize();
        float stepLen = Math.min(len, 60000f) / 160f;
        Vector3f camOnBeam = new Vector3f(eye).sub(from);
        float startAlong = Mth.clamp(camOnBeam.dot(dir) - 30000f, 0f, Math.max(0f, len - 60000f));
        for (int hlx = 0; hlx < 2; hlx++) {
            for (int k = 0; k < 160; k++) {
                float along = startAlong + k * stepLen;
                float ang = along * 0.0006f + time * 5f + hlx * 3.1416f;
                Vector3f p = new Vector3f(from).add(new Vector3f(dir).mul(along))
                        .add(new Vector3f(s1).mul((float) Math.cos(ang) * 1300f)).add(new Vector3f(s2).mul((float) Math.sin(ang) * 1300f));
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 260f, 8, 0.6f, 0.85f, 1f, 0.5f, 0f);
            }
        }
        // anillos de choque en la boca
        Matrix4f ringFrame = new Matrix4f(view).translate(from.x, from.y, from.z).rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        for (int i = 0; i < 4; i++) {
            float f = Mth.clamp(since * 0.6f - i * 0.15f, 0f, 1f);
            if (f <= 0f || f >= 1f) continue;
            float rr = 1500f + f * 12000f;
            RenderUtil.ring(bb, new Matrix4f(ringFrame).translate(0, f * 6000f, 0), rr, rr + 500f, 0, 96, 0.6f, 0.85f, 1f, (1f - f) * 0.7f);
        }
        // destello en el frente del rayo
        Vector3f tip = new Vector3f(from).add(new Vector3f(dir).mul(len));
        RenderUtil.softDisc(bb, view, tip.x, tip.y, tip.z, right, up, 3000f, 32, 0.7f, 0.9f, 1f, 0.8f, 0f);
        if (since < 0.6f) RenderUtil.softDisc(bb, view, from.x, from.y, from.z, right, up, 26000f, 32, 0.8f, 0.9f, 1f, 1f - since / 0.6f, 0f);
    }

    private static float recoil(float t) {
        if (t < CHARGE_END) return 0f;
        float s = (t - CHARGE_END) * Cinematic.durationSeconds();
        return 900f * (float) Math.exp(-s * 1.5f) * Math.min(1f, s * 8f);
    }

    private static void renderStarScene(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float bright = starBrightness(t);
        float rec = recoil(t);
        boolean stationVisible = t >= FLYBY_END - 0.01f;
        beginGlow(false);
        begin(bb);
        SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 0.2f);
        draw(bb);

        beginSolid();
        begin(bb);
        drawStar(bb, view, eye, time, bright, StarDirector.charge(t));
        if (stationVisible) drawStation(bb, view, time, rec);
        draw(bb);

        beginGlow(true);
        begin(bb);
        drawStarGlow(bb, view, eye, right, up, time, bright);
        if (stationVisible) {
            drawSiphon(bb, view, eye, right, up, t, time);
            drawStationGlow(bb, view, eye, right, up, t, time, rec);
        }
        drawBeam(bb, view, eye, right, up, t, time, StarDirector.beamFront(t));
        draw(bb);
    }

    private static void renderChase(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        beginGlow(false);
        begin(bb);
        BlackHoleRenderer.drawBackgroundGalaxies(bb, view, eye, right, up, 1f);
        BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, 1f, time, 0.7f, 0.85f, 1f, 8f);
        drawBeam(bb, view, eye, right, up, t, time, StarDirector.beamFront(t));
        draw(bb);
    }

    // ------------------------------------------------------------------ llegada a la Tierra

    private static void renderEarth(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = StarDirector.seg(t, CHASE_END, SPACE_END);
        beginGlow(false);
        begin(bb);
        SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        SpaceRenderer.drawSun(bb, view, eye, right, up, time);
        draw(bb);
        beginSolid();
        begin(bb);
        PlanetMesh.renderSurface(bb, view, Cinematic.PLANET_RADIUS, SUN, eye);
        SpaceRenderer.drawMoon(bb, view, SpaceRenderer.moonPosition(time));
        draw(bb);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        begin(bb);
        PlanetMesh.renderClouds(bb, view, Cinematic.PLANET_RADIUS * 1.012f, SUN, time * 0.004f);
        draw(bb);
        beginGlow(true);
        begin(bb);
        PlanetMesh.renderAtmosphere(bb, view, Cinematic.PLANET_RADIUS * 1.035f, SUN, eye);
        PlanetMesh.renderCityLights(bb, view, Cinematic.PLANET_RADIUS, SUN, time);
        // el rayo llega desde el espacio profundo
        Vector3f target = v(Cinematic.TARGET_SPACE);
        Vector3f dir = v(StarDirector.EARTH_BEAM_DIR);
        float arrive = Cinematic.smooth(Math.min(1f, u / 0.45f));
        Vector3f tip = new Vector3f(target).add(new Vector3f(dir).mul(90000f * (1f - arrive)));
        Vector3f far = new Vector3f(target).add(new Vector3f(dir).mul(200000f));
        Vector3f axis = new Vector3f(far).sub(tip);
        float len = axis.length();
        axis.div(len);
        Vector3f mid = new Vector3f(tip).add(far).mul(0.5f);
        Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(mid)).normalize();
        float flick = 1f + 0.08f * (float) Math.sin(time * 60);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 520f * flick, 0.3f, 0.55f, 1f, 0.2f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 220f, 0.6f, 0.85f, 1f, 0.6f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 70f, 1f, 1f, 1f, 1f);
        if (arrive >= 0.99f) {
            float since = (u - 0.45f) / 0.55f;
            RenderUtil.softDisc(bb, view, target.x, target.y + 2, target.z, right, up, 120f + 900f * since, 40, 0.75f, 0.9f, 1f, 0.9f, 0f);
            RenderUtil.softDisc(bb, view, target.x, target.y + 2, target.z, right, up, 60f + 200f * since, 40, 1f, 1f, 1f, 1f, 0f);
        }
        draw(bb);
    }
}
