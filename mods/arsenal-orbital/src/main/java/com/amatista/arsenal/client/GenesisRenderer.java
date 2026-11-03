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

import static com.amatista.arsenal.GenesisTimeline.*;
import static com.amatista.arsenal.client.BlackHoleRenderer.begin;
import static com.amatista.arsenal.client.BlackHoleRenderer.beginGlow;
import static com.amatista.arsenal.client.BlackHoleRenderer.beginSolid;
import static com.amatista.arsenal.client.BlackHoleRenderer.draw;

/** Escenas del "Protocolo Génesis". */
final class GenesisRenderer {
    private static final Vector3f SUN = SpaceRenderer.SUN;
    private static float[] rnd;
    private static float[] webPoints;   // x,y,z,tamaño,r,g,b,alpha
    private static float[] webNodes;    // x,y,z,tamaño
    private static float[] cmbColor;    // r,g,b por vértice
    private static float[][] curtains;  // x,z,ángulo,largo,tono

    private GenesisRenderer() {}

    static float rnd(int i) {
        if (rnd == null) {
            Random r = new Random(1618);
            rnd = new float[16384];
            for (int k = 0; k < rnd.length; k++) rnd[k] = r.nextFloat();
        }
        return rnd[(i & 0x7fffffff) % rnd.length];
    }

    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    /** Color del arcoíris (h en 0..1). */
    static float[] hue(float h) {
        h = h - (float) Math.floor(h);
        float r = Mth.clamp(Math.abs(h * 6f - 3f) - 1f, 0f, 1f);
        float g = Mth.clamp(2f - Math.abs(h * 6f - 2f), 0f, 1f);
        float b = Mth.clamp(2f - Math.abs(h * 6f - 4f), 0f, 1f);
        return new float[]{0.35f + 0.65f * r, 0.35f + 0.65f * g, 0.35f + 0.65f * b};
    }

    static void build() {
        if (webPoints != null) return;
        Random r = new Random(4242);
        int nodes = 240;
        webNodes = new float[nodes * 4];
        for (int i = 0; i < nodes; i++) {
            double u = r.nextDouble() * 2 - 1, ph = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            double rad = Math.cbrt(r.nextDouble()) * 1_150_000;
            webNodes[i * 4] = (float) (s * Math.cos(ph) * rad);
            webNodes[i * 4 + 1] = (float) (u * rad * 0.7);
            webNodes[i * 4 + 2] = (float) (s * Math.sin(ph) * rad);
            webNodes[i * 4 + 3] = 12000f + r.nextFloat() * 26000f;
        }
        int per = 26;
        float[] pts = new float[nodes * 2 * per * 8];
        int k = 0;
        for (int i = 0; i < nodes; i++) {
            // une cada nudo con sus dos vecinos más cercanos (filamentos)
            int best1 = -1, best2 = -1;
            double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
            for (int j = 0; j < nodes; j++) {
                if (j == i) continue;
                double dx = webNodes[j * 4] - webNodes[i * 4], dy = webNodes[j * 4 + 1] - webNodes[i * 4 + 1], dz = webNodes[j * 4 + 2] - webNodes[i * 4 + 2];
                double d = dx * dx + dy * dy + dz * dz;
                if (d < d1) { d2 = d1; best2 = best1; d1 = d; best1 = j; }
                else if (d < d2) { d2 = d; best2 = j; }
            }
            for (int e = 0; e < 2; e++) {
                int j = e == 0 ? best1 : best2;
                if (j < 0) continue;
                for (int p = 0; p < per; p++) {
                    float f = (p + r.nextFloat()) / per;
                    float jit = 9000f * (float) Math.sin(Math.PI * f);
                    pts[k++] = Mth.lerp(f, webNodes[i * 4], webNodes[j * 4]) + (float) r.nextGaussian() * jit;
                    pts[k++] = Mth.lerp(f, webNodes[i * 4 + 1], webNodes[j * 4 + 1]) + (float) r.nextGaussian() * jit;
                    pts[k++] = Mth.lerp(f, webNodes[i * 4 + 2], webNodes[j * 4 + 2]) + (float) r.nextGaussian() * jit;
                    pts[k++] = 2500f + r.nextFloat() * 4500f;
                    float tone = r.nextFloat();
                    pts[k++] = 0.6f + 0.4f * tone; pts[k++] = 0.65f + 0.2f * tone; pts[k++] = 1f - 0.3f * tone;
                    pts[k++] = 0.25f + 0.4f * r.nextFloat();
                }
            }
        }
        webPoints = new float[k];
        System.arraycopy(pts, 0, webPoints, 0, k);
        // fondo de microondas (el resplandor del Big Bang)
        int lat = 48, lon = 96;
        cmbColor = new float[(lat + 1) * (lon + 1) * 3];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * j / lon;
                int id = i * (lon + 1) + j;
                float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
                float n = PlanetMesh.fbm(x * 6f + 3, y * 6f, z * 6f - 2, 5);
                float h = PlanetMesh.smoothstep(0.35f, 0.65f, n);
                cmbColor[id * 3] = Mth.lerp(h, 0.3f, 1f);
                cmbColor[id * 3 + 1] = Mth.lerp(h, 0.5f, 0.55f);
                cmbColor[id * 3 + 2] = Mth.lerp(h, 1f, 0.2f);
            }
        }
        // cortinas de auroras alrededor de Génesis
        curtains = new float[22][];
        for (int i = 0; i < curtains.length; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 40000 + r.nextDouble() * 90000;
            curtains[i] = new float[]{(float) (Math.cos(a) * d), (float) (Math.sin(a) * d), r.nextFloat() * 6.28f,
                    18000f + r.nextFloat() * 30000f, r.nextFloat()};
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
        GenesisDirector.Scene scene = GenesisDirector.scene(t);
        Cinematic.SpacePose pose = GenesisDirector.spacePose(t);
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
        float near = scene == GenesisDirector.Scene.WEB || scene == GenesisDirector.Scene.EDGE ? 400f
                : scene == GenesisDirector.Scene.GENESIS || scene == GenesisDirector.Scene.MILKY_WAY ? 20f : 4f;
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, near, 8_000_000f);

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
        float warp = GenesisDirector.warp(t);

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
            case WEB -> renderWeb(bb, view, eye, forward, right, up, t, time, 1f);
            case EDGE -> renderEdge(bb, view, eye, forward, right, up, t, time);
            case GENESIS -> renderGenesis(bb, view, eye, forward, right, up, t, time);
            case RETURN -> renderReturn(bb, view, eye, forward, right, up, t, time);
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

    // ------------------------------------------------------------------ la red cósmica y el borde

    private static void renderWeb(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up,
                                  float t, float time, float alpha) {
        beginGlow(false);
        begin(bb);
        for (int i = 0; i < webNodes.length; i += 4) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(time * 0.6f + i);
            RenderUtil.softDisc(bb, view, webNodes[i], webNodes[i + 1], webNodes[i + 2], right, up, webNodes[i + 3] * pulse, 16,
                    1f, 0.85f, 0.7f, 0.35f * alpha, 0f);
            RenderUtil.softDisc(bb, view, webNodes[i], webNodes[i + 1], webNodes[i + 2], right, up, webNodes[i + 3] * 0.25f, 12,
                    1f, 0.95f, 0.9f, 0.7f * alpha, 0f);
        }
        for (int i = 0; i < webPoints.length; i += 8) {
            RenderUtil.billboard(bb, view, webPoints[i], webPoints[i + 1], webPoints[i + 2], right, up, webPoints[i + 3],
                    webPoints[i + 4], webPoints[i + 5], webPoints[i + 6], webPoints[i + 7] * alpha);
        }
        BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, GenesisDirector.warp(t), time, 0.9f, 0.85f, 1f, 60f);
        draw(bb);
    }

    private static void renderEdge(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = GenesisDirector.seg(t, WEB_END, EDGE_END);
        renderWeb(bb, view, eye, forward, right, up, t, time, 1f - 0.7f * u);
        beginGlow(true);
        begin(bb);
        int lat = 48, lon = 96;
        float R = GenesisDirector.CMB_RADIUS;
        float a = 0.18f + 0.35f * u;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                for (int k = 0; k < 4; k++) {
                    int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon;
                    int id = ii * (lon + 1) + jj;
                    bb.vertex(view, (float) (Math.sin(th) * Math.cos(ph) * R), (float) (Math.cos(th) * R), (float) (Math.sin(th) * Math.sin(ph) * R))
                            .color(cmbColor[id * 3], cmbColor[id * 3 + 1], cmbColor[id * 3 + 2], a).endVertex();
                }
            }
        }
        draw(bb);
    }

    // ------------------------------------------------------------------ más allá: GÉNESIS

    private static void drawAuroras(BufferBuilder bb, Matrix4f view, Vector3f eye, float time, float alpha) {
        Vector3f upAxis = new Vector3f(0, 1, 0);
        for (float[] c : curtains) {
            float[] col = hue(0.35f + c[4] * 0.45f + time * 0.01f);
            for (int s = 0; s < 26; s++) {
                float f = s / 25f - 0.5f;
                float ang = c[2] + f * 0.9f;
                float wave = (float) Math.sin(f * 7 + time * 0.4f + c[4] * 10) * 3000f;
                float x = c[0] + (float) Math.cos(ang) * c[3] * f * 2f + wave;
                float z = c[1] + (float) Math.sin(ang) * c[3] * f * 2f;
                float y = -6000f + 4000f * (float) Math.sin(f * 3 + time * 0.2f);
                float height = 16000f + 6000f * (float) Math.sin(f * 5 + time * 0.3f);
                Vector3f mid = new Vector3f(x, y + height * 0.5f, z);
                Vector3f side = new Vector3f(upAxis).cross(new Vector3f(eye).sub(mid)).normalize();
                RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, upAxis, height * 0.5f, side, 1800f, col[0], col[1], col[2], 0.09f * alpha);
            }
        }
    }

    private static void renderGenesis(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float aw = GenesisDirector.awaken(t);
        float petals = GenesisDirector.petals(t);
        float forge = GenesisDirector.forge(t);
        boolean launched = GenesisDirector.launched(t);
        float harvest = GenesisDirector.harvest(t);

        beginGlow(false);
        begin(bb);
        SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        drawAuroras(bb, view, eye, time, 1f - forge * 0.6f);
        // galaxias cosechadas que caen en espiral
        if (t >= AWAKEN_END - 0.01f && t < HARVEST_END + 0.03f) {
            for (int i = 0; i < GenesisDirector.HARVEST_GALAXIES; i++) {
                Vec3 gp = GenesisDirector.galaxyPos(i, t);
                float sc = GenesisDirector.galaxyScale(i, t);
                float fade = 1f - GenesisDirector.seg(t, HARVEST_END, HARVEST_END + 0.03f);
                Matrix4f model = new Matrix4f().translate((float) gp.x, (float) gp.y, (float) gp.z)
                        .rotateX(0.4f + i * 0.5f).rotateY(time * 0.05f + i).scale(sc);
                BlackHoleRenderer.Galaxy src = i % 2 == 0 ? BlackHoleRenderer.milky : BlackHoleRenderer.andromeda;
                src.render(bb, view, model, eye, right, up, 0.9f * fade, sc, 3);
                // ríos de luz de cada galaxia hacia Génesis
                if (harvest > 0.05f) {
                    float[] col = hue(i / (float) GenesisDirector.HARVEST_GALAXIES + time * 0.02f);
                    for (int k = 0; k < 90; k++) {
                        float s = (k / 90f + time * 0.15f) % 1f;
                        Vector3f a = v(gp);
                        float sw = (float) Math.sin(Math.PI * s) * 14000f;
                        double ang = s * 4 + i;
                        Vector3f p = new Vector3f(a).lerp(new Vector3f(), s * s)
                                .add((float) Math.cos(ang) * sw, (float) Math.sin(ang * 1.3) * sw * 0.3f, (float) Math.sin(ang) * sw);
                        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 1400f * (1f - s * 0.6f), 8, col[0], col[1], col[2],
                                0.4f * Math.min(1f, harvest * 4f) * fade, 0f);
                    }
                }
            }
        }
        draw(bb);

        // estructura sólida: anillos dorados y pétalos
        beginSolid();
        begin(bb);
        for (int k = 0; k < 6; k++) {
            float rad = GenesisDirector.ringRadius(k, t);
            Matrix4f m = ringMatrix(view, k, time, aw);
            int seg = 64;
            for (int i = 0; i < seg; i++) {
                Matrix4f sm = new Matrix4f(m).rotateY((float) (Math.PI * 2 * i / seg));
                float half = (float) (Math.PI * rad / seg) + 20f;
                float shade = 0.9f + 0.1f * (i % 2);
                RenderUtil.box(bb, sm, rad - 90f, -70f, -half, rad + 90f, 70f, half, 0.86f * shade, 0.68f * shade, 0.3f * shade, 1f);
                if (i % 8 == 0) RenderUtil.box(bb, sm, rad + 90f, -120f, -half * 0.5f, rad + 220f, 120f, half * 0.5f, 0.92f, 0.9f, 0.86f, 1f);
            }
        }
        // pétalos que se abren alrededor del cuásar
        for (int p = 0; p < 8; p++) {
            float open = Mth.lerp(petals, 1.45f, 0.15f);
            if (launched) open = Mth.lerp(GenesisDirector.seg(t, FORGE_END, FORGE_END + 0.01f), open, -0.25f);
            Matrix4f pm = new Matrix4f(view).rotateY(p * (float) Math.PI / 4f + time * 0.02f * aw).rotateZ(open);
            for (int s = 0; s < 6; s++) {
                float x0 = 900f + s * 330f, x1 = x0 + 330f;
                float wdt = 380f * (1f - Math.abs(s - 2.5f) / 3.5f) + 60f;
                float lift = (float) Math.sin(s * 0.5f) * 220f;
                RenderUtil.box(bb, pm, x0, lift - 40f, -wdt, x1, lift + 40f, wdt, 0.95f, 0.93f, 0.88f, 1f);
            }
        }
        if (launched || forge > 0.5f) {
            Vec3 sp = GenesisDirector.sparkPos(t);
            BlackHoleRenderer.drawSphere(bb, view, v(sp), 60f * (launched ? 1f : forge), 1f, 1f, 1f);
        }
        draw(bb);

        // brillos: cuásar, glifos de los anillos, convergencia, chispa y lanzamiento
        beginGlow(true);
        begin(bb);
        float coreFade = 1f - forge * 0.85f;
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, 9000f, 48, 1f, 0.85f, 0.55f, 0.25f * coreFade, 0f);
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, 3000f, 48, 1f, 0.95f, 0.8f, 0.6f * coreFade, 0f);
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, GenesisDirector.CORE_R, 48, 1f, 1f, 1f, coreFade, 0f);
        Vector3f yAxis = new Vector3f(0, 1, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vector3f dir = new Vector3f(yAxis).mul(s);
            Vector3f side = new Vector3f(dir).cross(new Vector3f(eye)).normalize();
            Vector3f mid = new Vector3f(dir).mul(30000f);
            RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, 29000f, side, 900f, 0.8f, 0.85f, 1f, 0.3f * coreFade);
            RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, 29000f, side, 220f, 1f, 1f, 1f, 0.6f * coreFade);
        }
        Matrix4f acc = new Matrix4f(view).rotateY(time * 0.6f);
        RenderUtil.ring(bb, acc, 1300f, 2400f, 0, 96, 1f, 0.75f, 0.35f, 0.5f * coreFade);
        // glifos de luz que recorren los anillos
        if (aw > 0.01f) {
            for (int k = 0; k < 6; k++) {
                float rad = GenesisDirector.ringRadius(k, t);
                Matrix4f m = ringMatrix(view, k, time, aw);
                RenderUtil.ring(bb, m, rad - 30f, rad + 30f, 75f, 128, 1f, 0.85f, 0.5f, 0.5f * aw);
                for (int g = 0; g < 16; g++) {
                    float a = g * 0.3927f + time * (0.4f + k * 0.1f);
                    Vector3f gp = m.transformPosition(new Vector3f((float) Math.cos(a) * rad, 0, (float) Math.sin(a) * rad), new Vector3f());
                    float[] col = hue(g / 16f + time * 0.05f);
                    RenderUtil.softDisc(bb, new Matrix4f(), gp.x, gp.y, gp.z, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                            260f, 10, col[0], col[1], col[2], 0.9f * aw, 0f);
                }
            }
        }
        // la luz de todo converge en el centro
        if (forge > 0f && !launched) {
            for (int i = 0; i < 500; i++) {
                Vector3f d = new Vector3f(rnd(i * 3) - 0.5f, rnd(i * 3 + 1) - 0.5f, rnd(i * 3 + 2) - 0.5f).normalize();
                float ph = (rnd(i + 2000) + time * (0.2f + 0.8f * forge)) % 1f;
                Vector3f p = d.mul(14000f * (1f - ph));
                float[] col = hue(rnd(i + 3000) + time * 0.05f);
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 300f * (1f - ph * 0.7f), 8, col[0], col[1], col[2], 0.7f * forge * ph, 0f);
            }
        }
        if (forge > 0.15f || launched) {
            Vector3f sp = v(GenesisDirector.sparkPos(t));
            float size = launched ? 700f : 300f + 900f * forge;
            drawSpark(bb, view, sp, right, up, size, time, launched ? 1f : forge);
        }
        if (launched) drawLaunch(bb, view, eye, right, up, t, time);
        draw(bb);
    }

    private static Matrix4f ringMatrix(Matrix4f view, int k, float time, float aw) {
        float speed = (0.03f + aw * (0.15f + k * 0.07f)) * (k % 2 == 0 ? 1f : -1f);
        return new Matrix4f(view).rotateX(0.3f + k * 0.55f + time * speed * 0.5f).rotateZ(k * 0.9f + time * speed);
    }

    /** La chispa de la creación: una estrella prismática que cambia de color. */
    static void drawSpark(BufferBuilder bb, Matrix4f view, Vector3f p, Vector3f right, Vector3f up, float size, float time, float strength) {
        for (int l = 0; l < 5; l++) {
            float[] col = hue(time * 0.25f + l * 0.18f);
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, size * (4.5f - l * 0.8f), 32, col[0], col[1], col[2], 0.18f * strength, 0f);
        }
        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, size * 0.9f, 32, 1f, 1f, 1f, strength, 0f);
        for (int i = 0; i < 14; i++) {
            float ang = i * 0.4488f + time * 0.3f;
            float len = size * (5f + 3f * rnd(i)) * (0.85f + 0.15f * (float) Math.sin(time * 2 + i));
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            float[] col = hue(i / 14f + time * 0.1f);
            RenderUtil.softStreak(bb, view, p.x, p.y, p.z, dir, len, side, size * 0.08f, col[0], col[1], col[2], 0.6f * strength);
        }
        for (int i = 0; i < 60; i++) {
            float a = rnd(i + 500) * 6.283f + time * (1f + rnd(i + 600));
            float r = size * (1.2f + 2.5f * rnd(i + 700));
            Vector3f q = new Vector3f(p).add(new Vector3f(right).mul((float) Math.cos(a) * r)).add(new Vector3f(up).mul((float) Math.sin(a) * r));
            float tw = (float) Math.pow(Math.max(0, Math.sin(time * 4 + i)), 6);
            RenderUtil.softDisc(bb, view, q.x, q.y, q.z, right, up, size * 0.12f * (0.5f + tw), 6, 1f, 1f, 1f, 0.8f * tw * strength, 0f);
        }
    }

    private static void drawLaunch(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time) {
        Vector3f sp = v(GenesisDirector.sparkPos(t));
        float len = sp.length();
        if (len < 1f) return;
        Vector3f dir = new Vector3f(sp).div(len);
        Vector3f mid = new Vector3f(sp).mul(0.5f);
        Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
        Vector3f other = new Vector3f(dir).cross(side).normalize();
        for (int i = 0; i < 6; i++) {
            float[] col = hue(i / 6f + time * 0.1f);
            Vector3f off = new Vector3f(other).mul((i - 2.5f) * 220f);
            RenderUtil.softBeam(bb, view, mid.x + off.x, mid.y + off.y, mid.z + off.z, dir, len * 0.5f, side, 420f, col[0], col[1], col[2], 0.35f);
        }
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 140f, 1f, 1f, 1f, 0.9f);
        float since = (t - FORGE_END) * Cinematic.durationSeconds();
        for (int i = 0; i < 5; i++) {
            float f = Mth.clamp(since * 0.5f - i * 0.12f, 0f, 1f);
            if (f <= 0f || f >= 1f) continue;
            float[] col = hue(i / 5f);
            Matrix4f rf = new Matrix4f(view).rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
            float rr = 2000f + 30000f * f;
            RenderUtil.ring(bb, rf, rr, rr + 700f, 0, 128, col[0], col[1], col[2], (1f - f) * 0.7f);
        }
    }

    // ------------------------------------------------------------------ el regreso

    private static void renderReturn(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = GenesisDirector.seg(t, LAUNCH_END, RETURN_TRIP_END);
        beginGlow(false);
        begin(bb);
        float span = 9000f;
        float speed = 3200f + 3000f * (float) Math.sin(Math.PI * u);
        for (int i = 0; i < 80; i++) {
            float z = span - ((time * speed + i * (span / 80f)) % span);
            float[] col = hue(i / 80f * 2f + time * 0.15f);
            float radius = 560f + 140f * (float) Math.sin(z * 0.002f + time);
            Vector3f c = new Vector3f(eye).add(new Vector3f(forward).mul(z));
            Matrix4f m = new Matrix4f(view).translate(c.x, c.y, c.z).rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), forward))
                    .rotateY(z * 0.001f + time);
            float fade = Mth.clamp(z / 900f, 0f, 1f) * Mth.clamp((span - z) / 2500f, 0f, 1f);
            RenderUtil.ring(bb, m, radius, radius + 36f, 0, 72, col[0], col[1], col[2], 0.55f * fade);
        }
        // en medio del viaje se cruza otra vez el borde del universo y la red cósmica
        float web = Mth.clamp(1f - Math.abs(u - 0.45f) / 0.2f, 0f, 1f);
        BlackHoleRenderer.drawWarp(bb, view, eye, forward, right, up, 1f, time, 1f, 0.7f + 0.3f * web, 0.6f + 0.4f * (1 - web), 1.2f);
        Vector3f sp = new Vector3f(eye).add(new Vector3f(forward).mul(800f + 100f * (float) Math.sin(time * 2)));
        drawSpark(bb, view, sp, right, up, 70f, time, 1f);
        if (u > 0.6f) {
            float e = Cinematic.smooth((u - 0.6f) / 0.4f);
            Vector3f exit = new Vector3f(eye).add(new Vector3f(forward).mul(8000f - 6500f * e));
            RenderUtil.softDisc(bb, view, exit.x, exit.y, exit.z, right, up, 200f + 1500f * e, 48, 0.5f, 0.75f, 1f, 0.9f * e, 0f);
            RenderUtil.softDisc(bb, view, exit.x, exit.y, exit.z, right, up, 120f + 650f * e, 48, 0.3f, 0.55f, 0.95f, 1f * e, 0.6f * e);
        }
        draw(bb);
    }

    // ------------------------------------------------------------------ la Tierra

    private static void renderEarth(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = GenesisDirector.seg(t, RETURN_TRIP_END, SPACE_END);
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
        // auroras sobre el objetivo
        Vector3f target = v(Cinematic.TARGET_SPACE);
        for (int s = 0; s < 40; s++) {
            float a = s * 0.157f + time * 0.05f;
            float[] col = hue(0.3f + 0.2f * (float) Math.sin(s * 0.3f + time * 0.2f));
            Vector3f base = new Vector3f(target).add((float) Math.cos(a) * 260f, 30f, (float) Math.sin(a) * 260f);
            Vector3f axis = new Vector3f(base).normalize();
            Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(base)).normalize();
            Vector3f mid = new Vector3f(base).add(new Vector3f(axis).mul(45f));
            RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, 45f, side, 18f, col[0], col[1], col[2], 0.35f * u);
        }
        // la chispa llega con su estela de arcoíris
        Vector3f dir = v(GenesisDirector.EARTH_DIR);
        float arrive = Cinematic.smooth(u);
        Vector3f sp = new Vector3f(target).add(new Vector3f(dir).mul(30000f * (1f - arrive) + 60f));
        for (int i = 0; i < 6; i++) {
            float[] col = hue(i / 6f + time * 0.1f);
            Vector3f mid = new Vector3f(sp).add(new Vector3f(dir).mul(9000f));
            Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
            Vector3f off = new Vector3f(side).mul((i - 2.5f) * 10f);
            RenderUtil.softBeam(bb, view, mid.x + off.x, mid.y + off.y, mid.z + off.z, dir, 9000f, side, 26f, col[0], col[1], col[2], 0.45f);
        }
        drawSpark(bb, view, sp, right, up, 22f + 40f * arrive, time, 1f);
        draw(bb);
    }
}
