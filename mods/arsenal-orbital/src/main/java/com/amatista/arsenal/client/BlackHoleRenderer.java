package com.amatista.arsenal.client;

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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Random;

import static com.amatista.arsenal.BlackHoleTimeline.*;

/** Escenas del "Protocolo Singularidad": sistema solar, Vía Láctea, Andrómeda, agujero negro y agujero de gusano. */
final class BlackHoleRenderer {
    private static final Vector3f SUN = SpaceRenderer.SUN;
    private static float[] rnd;
    private static Galaxy milky, andromeda;
    private static float[] backgroundGalaxies; // dirección, ejes, tamaño, tono

    private BlackHoleRenderer() {}

    private static float rnd(int i) {
        if (rnd == null) {
            Random r = new Random(9001);
            rnd = new float[16384];
            for (int k = 0; k < rnd.length; k++) rnd[k] = r.nextFloat();
        }
        return rnd[(i & 0x7fffffff) % rnd.length];
    }

    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    // ------------------------------------------------------------------ galaxias

    /** Galaxia espiral hecha de puntos de luz, cúmulos rosados de formación estelar y un núcleo brillante. */
    private static final class Galaxy {
        final float[] pos;   // x,y,z en el plano de la galaxia
        final float[] col;   // r,g,b,alpha,tamaño
        final float[] clumps; // x,y,z,tamaño,r,g,b,alpha
        final float radius;

        Galaxy(long seed, int count, float radius, int arms) {
            this.radius = radius;
            Random r = new Random(seed);
            pos = new float[count * 3];
            col = new float[count * 5];
            for (int i = 0; i < count; i++) {
                float x, y, z;
                boolean bulge = r.nextFloat() < 0.22f;
                if (bulge) {
                    float rr = (float) Math.abs(r.nextGaussian()) * radius * 0.12f;
                    double a = r.nextDouble() * Math.PI * 2;
                    x = (float) Math.cos(a) * rr; z = (float) Math.sin(a) * rr;
                    y = (float) r.nextGaussian() * radius * 0.04f;
                } else {
                    float rr = (float) Math.pow(r.nextDouble(), 0.7) * radius;
                    int arm = r.nextInt(arms);
                    double a = arm * Math.PI * 2 / arms + Math.log(1 + rr / (radius * 0.08)) * 2.1 + r.nextGaussian() * 0.32;
                    x = (float) Math.cos(a) * rr; z = (float) Math.sin(a) * rr;
                    y = (float) r.nextGaussian() * radius * 0.012f;
                }
                pos[i * 3] = x; pos[i * 3 + 1] = y; pos[i * 3 + 2] = z;
                float d = (float) Math.sqrt(x * x + z * z) / radius;
                float warm = Math.max(0f, 1f - d * 2.2f);
                col[i * 5] = 0.75f + 0.25f * warm;
                col[i * 5 + 1] = 0.78f + 0.12f * warm;
                col[i * 5 + 2] = 1f - 0.35f * warm;
                col[i * 5 + 3] = 0.25f + 0.5f * r.nextFloat();
                col[i * 5 + 4] = radius * (0.0018f + 0.004f * r.nextFloat());
            }
            int nc = 320;
            clumps = new float[nc * 8];
            for (int i = 0; i < nc; i++) {
                float rr = (float) Math.pow(r.nextDouble(), 0.6) * radius * 0.95f;
                int arm = r.nextInt(arms);
                double a = arm * Math.PI * 2 / arms + Math.log(1 + rr / (radius * 0.08)) * 2.1 + r.nextGaussian() * 0.18;
                boolean pink = r.nextFloat() < 0.45f;
                clumps[i * 8] = (float) Math.cos(a) * rr; clumps[i * 8 + 1] = 0; clumps[i * 8 + 2] = (float) Math.sin(a) * rr;
                clumps[i * 8 + 3] = radius * (0.012f + 0.03f * r.nextFloat());
                clumps[i * 8 + 4] = pink ? 1f : 0.6f; clumps[i * 8 + 5] = pink ? 0.45f : 0.7f; clumps[i * 8 + 6] = pink ? 0.7f : 1f;
                clumps[i * 8 + 7] = 0.08f + 0.12f * r.nextFloat();
            }
        }

        void render(BufferBuilder bb, Matrix4f view, Matrix4f model, Vector3f eye, Vector3f right, Vector3f up, float brightness) {
            Matrix4f m = new Matrix4f(view).mul(model);
            // núcleo
            Vector3f core = model.transformPosition(new Vector3f());
            RenderUtil.softDisc(bb, view, core.x, core.y, core.z, right, up, radius * 0.35f, 32, 1f, 0.85f, 0.6f, 0.35f * brightness, 0f);
            RenderUtil.softDisc(bb, view, core.x, core.y, core.z, right, up, radius * 0.12f, 32, 1f, 0.95f, 0.85f, 0.8f * brightness, 0f);
            // brazos difusos
            for (int i = 0; i < clumps.length; i += 8) {
                Vector3f p = model.transformPosition(new Vector3f(clumps[i], clumps[i + 1], clumps[i + 2]));
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, clumps[i + 3], 10,
                        clumps[i + 4], clumps[i + 5], clumps[i + 6], clumps[i + 7] * brightness, 0f);
            }
            // estrellas
            Vector3f p = new Vector3f();
            for (int i = 0; i < pos.length / 3; i++) {
                model.transformPosition(p.set(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]));
                float s = col[i * 5 + 4];
                RenderUtil.billboard(bb, view, p.x, p.y, p.z, right, up, s, col[i * 5], col[i * 5 + 1], col[i * 5 + 2], col[i * 5 + 3] * brightness);
            }
        }
    }

    private static void build() {
        if (milky != null) return;
        milky = new Galaxy(11, 14000, BlackHoleDirector.MILKY_RADIUS, 4);
        andromeda = new Galaxy(31, 18000, BlackHoleDirector.ANDROMEDA_RADIUS, 2);
        Random r = new Random(55);
        int n = 70;
        backgroundGalaxies = new float[n * 11];
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            Vector3f c = new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
            Vector3f t1 = new Vector3f(c).cross(Math.abs(c.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f t2 = new Vector3f(c).cross(t1).normalize();
            float ang = r.nextFloat() * 6.28f;
            Vector3f ax = new Vector3f(t1).mul((float) Math.cos(ang)).add(new Vector3f(t2).mul((float) Math.sin(ang)));
            Vector3f bx = new Vector3f(t1).mul((float) -Math.sin(ang)).add(new Vector3f(t2).mul((float) Math.cos(ang)));
            backgroundGalaxies[i * 11] = c.x; backgroundGalaxies[i * 11 + 1] = c.y; backgroundGalaxies[i * 11 + 2] = c.z;
            backgroundGalaxies[i * 11 + 3] = ax.x; backgroundGalaxies[i * 11 + 4] = ax.y; backgroundGalaxies[i * 11 + 5] = ax.z;
            backgroundGalaxies[i * 11 + 6] = bx.x; backgroundGalaxies[i * 11 + 7] = bx.y; backgroundGalaxies[i * 11 + 8] = bx.z;
            backgroundGalaxies[i * 11 + 9] = 40f + (float) Math.pow(r.nextDouble(), 3) * 380f;
            backgroundGalaxies[i * 11 + 10] = r.nextFloat();
        }
    }

    /** Galaxias lejanas en todas direcciones (se ven al salir de la Vía Láctea). */
    private static void drawBackgroundGalaxies(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float alpha) {
        float dist = 9000f;
        for (int i = 0; i < backgroundGalaxies.length; i += 11) {
            float cx = eye.x + backgroundGalaxies[i] * dist, cy = eye.y + backgroundGalaxies[i + 1] * dist, cz = eye.z + backgroundGalaxies[i + 2] * dist;
            Vector3f ax = new Vector3f(backgroundGalaxies[i + 3], backgroundGalaxies[i + 4], backgroundGalaxies[i + 5]);
            Vector3f bx = new Vector3f(backgroundGalaxies[i + 6], backgroundGalaxies[i + 7], backgroundGalaxies[i + 8]);
            float size = backgroundGalaxies[i + 9], tone = backgroundGalaxies[i + 10];
            float r = 0.8f + 0.2f * tone, g = 0.75f + 0.15f * tone, b = 0.75f + 0.25f * (1 - tone);
            RenderUtil.softStreak(bb, view, cx, cy, cz, ax, size, bx, size * (0.25f + 0.5f * tone), r, g, b, 0.35f * alpha);
            RenderUtil.softStreak(bb, view, cx, cy, cz, new Vector3f(ax).add(new Vector3f(bx).mul(0.6f)).normalize(), size * 0.75f, bx, size * 0.2f, r, g, b, 0.2f * alpha);
            RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, size * 0.2f, 10, 1f, 0.95f, 0.85f, 0.7f * alpha, 0f);
        }
        for (int i = 0; i < 2500; i++) { // puntitos: galaxias diminutas
            float x = rnd(i * 3) * 2 - 1, y = rnd(i * 3 + 1) * 2 - 1, z = rnd(i * 3 + 2) * 2 - 1;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l < 0.01f) continue;
            RenderUtil.billboard(bb, view, eye.x + x / l * dist, eye.y + y / l * dist, eye.z + z / l * dist, right, up, 5f,
                    0.9f, 0.85f, 1f, 0.25f * alpha);
        }
    }

    // ------------------------------------------------------------------ dibujo

    static void render(float partialTick) {
        SpaceRenderer.prepare();
        build();
        Minecraft mc = Minecraft.getInstance();
        float t = Cinematic.progress(partialTick);
        float time = Cinematic.seconds(partialTick);
        BlackHoleDirector.Scene scene = BlackHoleDirector.scene(t);
        Cinematic.SpacePose pose = BlackHoleDirector.spacePose(t);
        Vector3f eye = v(pose.eye());
        Vector3f center = v(pose.center());
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
        float near = scene == BlackHoleDirector.Scene.MILKY_WAY ? 40f : 2f;
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, near, 2_000_000f);

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

        switch (scene) {
            case SOLAR -> renderSolar(bb, view, eye, forward, right, up, t, time);
            case MILKY_WAY -> renderMilky(bb, view, eye, forward, right, up, t, time);
            case ANDROMEDA -> renderAndromeda(bb, view, eye, forward, right, up, t, time);
            case WORMHOLE -> renderWormhole(bb, view, eye, forward, right, up, t, time);
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

    private static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static void beginGlow(boolean depth) {
        additive();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static void beginSolid() {
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void draw(BufferBuilder bb) {
        BufferUploader.drawWithShader(bb.end());
    }

    private static void begin(BufferBuilder bb) {
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /** Líneas de velocidad alrededor de la cámara. */
    private static void drawWarp(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up,
                                 float warp, float time, float r0, float g0, float b0, float scale) {
        if (warp <= 0.01f) return;
        for (int i = 0; i < 600; i++) {
            float ang = rnd(i * 3) * 6.2832f;
            float radius = (40f + rnd(i * 3 + 1) * 900f) * scale;
            float speed = (2500f + rnd(i * 3 + 2) * 3500f) * scale;
            float span = 6000f * scale;
            float z = span * 0.5f - ((time * speed * warp + rnd(i * 5) * span) % span);
            Vector3f p = new Vector3f(eye).add(new Vector3f(forward).mul(z))
                    .add(new Vector3f(right).mul((float) Math.cos(ang) * radius))
                    .add(new Vector3f(up).mul((float) Math.sin(ang) * radius));
            float len = (60f + 600f * warp) * scale;
            Vector3f side = new Vector3f(forward).cross(new Vector3f(p).sub(eye)).normalize();
            float tint = rnd(i * 7);
            RenderUtil.softBeam(bb, view, p.x, p.y, p.z, forward, len, side, (2.2f + radius * 0.004f / scale) * scale,
                    r0 * (0.7f + 0.3f * tint), g0 * (0.8f + 0.2f * tint), b0, 0.55f * warp);
        }
    }

    // ------------------------------------------------------------------ 1. sistema solar

    private static void renderSolar(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        beginGlow(false);
        begin(bb);
        SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        SpaceRenderer.drawSun(bb, view, eye, right, up, time);
        drawWarp(bb, view, eye, forward, right, up, BlackHoleDirector.warp(t) * 0.8f, time, 0.8f, 0.9f, 1f, 1f);
        draw(bb);

        beginSolid();
        begin(bb);
        PlanetMesh.renderSurface(bb, view, Cinematic.PLANET_RADIUS, SUN, eye);
        SpaceRenderer.drawMoon(bb, view, SpaceRenderer.moonPosition(time));
        MarsMesh.renderMars(bb, view, v(BlackHoleDirector.MARS_S), BlackHoleDirector.MARS_R, SUN);
        drawGasGiant(bb, view, v(BlackHoleDirector.JUPITER), BlackHoleDirector.JUPITER_R, time, false);
        drawGasGiant(bb, view, v(BlackHoleDirector.SATURN), BlackHoleDirector.SATURN_R, time, true);
        draw(bb);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        begin(bb);
        PlanetMesh.renderClouds(bb, view, Cinematic.PLANET_RADIUS * 1.012f, SUN, time * 0.004f);
        drawSaturnRings(bb, view, v(BlackHoleDirector.SATURN), BlackHoleDirector.SATURN_R);
        draw(bb);

        beginGlow(true);
        begin(bb);
        PlanetMesh.renderAtmosphere(bb, view, Cinematic.PLANET_RADIUS * 1.035f, SUN, eye);
        PlanetMesh.renderCityLights(bb, view, Cinematic.PLANET_RADIUS, SUN, time);
        MarsMesh.renderMarsAtmosphere(bb, view, v(BlackHoleDirector.MARS_S), BlackHoleDirector.MARS_R * 1.02f, SUN, eye);
        draw(bb);
    }

    /** Júpiter y Saturno: bandas de nubes, la Gran Mancha Roja en Júpiter. */
    private static void drawGasGiant(BufferBuilder bb, Matrix4f view, Vector3f c, float radius, float time, boolean saturn) {
        int lat = 48, lon = 96;
        Quaternionf tilt = new Quaternionf().rotateZ(saturn ? 0.47f : 0.05f);
        Vector3f p = new Vector3f();
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                for (int k = 0; k < 4; k++) {
                    int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon + time * 0.01;
                    float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
                    float band = (float) Math.sin(y * (saturn ? 22 : 30) + PlanetMesh.fbm(x * 3, y * 9, z * 3, 3) * 4);
                    float r, g, b;
                    if (saturn) { r = 0.86f + 0.06f * band; g = 0.76f + 0.06f * band; b = 0.52f + 0.05f * band; }
                    else { r = 0.80f + 0.12f * band; g = 0.66f + 0.10f * band; b = 0.50f + 0.06f * band; }
                    if (!saturn) { // Gran Mancha Roja
                        float dx = x - 0.8f, dy = y + 0.36f, dz = z - 0.45f;
                        float spot = 1f - Mth.clamp((float) Math.sqrt(dx * dx + dy * dy * 3 + dz * dz) / 0.22f, 0f, 1f);
                        r = Mth.lerp(spot, r, 0.78f); g = Mth.lerp(spot, g, 0.35f); b = Mth.lerp(spot, b, 0.25f);
                    }
                    tilt.transform(p.set(x, y, z));
                    float light = 0.03f + 0.95f * Math.max(0f, p.dot(SUN));
                    bb.vertex(view, c.x + p.x * radius, c.y + p.y * radius, c.z + p.z * radius).color(r * light, g * light, b * light, 1f).endVertex();
                }
            }
        }
    }

    private static void drawSaturnRings(BufferBuilder bb, Matrix4f view, Vector3f c, float radius) {
        Matrix4f m = new Matrix4f(view).translate(c.x, c.y, c.z).rotateZ(0.47f);
        float[][] bands = {{1.25f, 1.55f, 0.55f}, {1.55f, 1.95f, 0.85f}, {1.98f, 2.05f, 0.1f}, {2.05f, 2.3f, 0.7f}, {2.3f, 2.4f, 0.4f}};
        for (float[] band : bands) {
            RenderUtil.ring(bb, m, radius * band[0], radius * band[1], 0, 128, 0.85f, 0.78f, 0.62f, band[2]);
        }
    }

    // ------------------------------------------------------------------ 2. Vía Láctea

    private static void renderMilky(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = BlackHoleDirector.seg(t, SOLAR_END, MILKY_END);
        beginGlow(false);
        begin(bb);
        drawBackgroundGalaxies(bb, view, eye, right, up, Cinematic.smooth(u * 2f));
        Matrix4f model = new Matrix4f().rotateY(time * 0.003f);
        milky.render(bb, view, model, eye, right, up, 1f);
        drawWarp(bb, view, eye, forward, right, up, BlackHoleDirector.warp(t), time, 0.8f, 0.9f, 1f, 6f);
        draw(bb);
    }

    // ------------------------------------------------------------------ 3. Andrómeda y su agujero negro

    private static Matrix4f andromedaModel(float time) {
        return new Matrix4f().rotateX(0.22f).rotateZ(0.12f).rotateY(time * 0.002f);
    }

    private static void renderAndromeda(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        boolean atHole = t >= ANDROMEDA_END;
        float approach = BlackHoleDirector.seg(t, INTERGALACTIC_END, ANDROMEDA_END);
        beginGlow(false);
        begin(bb);
        drawBackgroundGalaxies(bb, view, eye, right, up, 1f - 0.6f * approach);
        // dentro de Andrómeda el fondo se llena de sus estrellas
        if (t >= INTERGALACTIC_END) SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        andromeda.render(bb, view, andromedaModel(time), eye, right, up, atHole ? 0.35f : 1f);
        drawWarp(bb, view, eye, forward, right, up, BlackHoleDirector.warp(t), time, 0.9f, 0.85f, 1f, t < INTERGALACTIC_END ? 8f : 2f);
        draw(bb);
        if (!atHole && approach < 0.85f) return;
        renderBlackHole(bb, view, eye, right, up, t, time);
    }

    private static void renderBlackHole(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time) {
        Vector3f n = v(BlackHoleDirector.DISK_NORMAL);
        Quaternionf diskRot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), n);
        float gate = BlackHoleDirector.gatePower(t);
        float portal = BlackHoleDirector.portal(t);
        Vector3f seed = v(BlackHoleDirector.seedPosition(t));
        float extract = BlackHoleDirector.seg(t, HOLE_ORBIT_END, EXTRACT_END);

        // sólido: horizonte de eventos, anillos de la estación Áncora y la semilla
        beginSolid();
        begin(bb);
        drawSphere(bb, view, new Vector3f(), BlackHoleDirector.HORIZON, 0f, 0f, 0f);
        if (t >= HOLE_ORBIT_END - 0.01f) {
            for (int i = 0; i < 3; i++) drawGate(bb, view, v(BlackHoleDirector.GATES[i]), time, i, portal);
            if (extract > 0.3f || t >= EXTRACT_END) drawSphere(bb, view, seed, 70f, 0f, 0f, 0f);
        }
        draw(bb);

        beginGlow(true);
        begin(bb);
        drawAccretionDisk(bb, view, eye, diskRot, n, time);
        // anillo de fotones y la imagen curvada del disco (lente gravitatoria)
        RenderUtil.softDisc(bb, view, 0, 0, 0, right, up, BlackHoleDirector.HORIZON * 1.13f, 64, 1f, 0.9f, 0.7f, 0f, 0.95f);
        drawLensedHalo(bb, view, right, up, time);
        // chorros relativistas
        for (int s = -1; s <= 1; s += 2) {
            Vector3f dir = new Vector3f(n).mul(s);
            Vector3f side = new Vector3f(dir).cross(new Vector3f(eye)).normalize();
            float len = 26000f;
            Vector3f mid = new Vector3f(dir).mul(len * 0.5f + BlackHoleDirector.HORIZON);
            float flick = 0.85f + 0.15f * (float) Math.sin(time * 8 + s);
            RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 260f * flick, 0.45f, 0.6f, 1f, 0.35f);
            RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, len * 0.5f, side, 70f, 0.85f, 0.92f, 1f, 0.7f);
        }
        // estrellas despedazadas que caen en espiral
        for (int i = 0; i < 260; i++) {
            float rr = BlackHoleDirector.DISK_OUT * (0.25f + 1.4f * rnd(i + 200));
            float speed = 9000f / (rr * (float) Math.sqrt(rr / 100f));
            float a = rnd(i + 400) * 6.283f + time * speed;
            Vector3f p = diskRot.transform(new Vector3f((float) Math.cos(a) * rr, (rnd(i + 600) - 0.5f) * 120f, (float) Math.sin(a) * rr));
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 14f + 20f * rnd(i), 6, 1f, 0.85f, 0.7f, 0.6f, 0f);
        }
        if (t >= HOLE_ORBIT_END - 0.01f) drawStationGlow(bb, view, eye, right, up, t, time, gate, portal, seed, extract);
        draw(bb);
    }

    private static void drawSphere(BufferBuilder bb, Matrix4f view, Vector3f c, float radius, float r, float g, float b) {
        int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                for (int k = 0; k < 4; k++) {
                    int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon;
                    bb.vertex(view, c.x + (float) (Math.sin(th) * Math.cos(ph)) * radius, c.y + (float) Math.cos(th) * radius,
                            c.z + (float) (Math.sin(th) * Math.sin(ph)) * radius).color(r, g, b, 1f).endVertex();
                }
            }
        }
    }

    /** Disco de acreción: blanco-amarillo por dentro, naranja y rojo por fuera; gira y brilla más del lado que se acerca. */
    private static void drawAccretionDisk(BufferBuilder bb, Matrix4f view, Vector3f eye, Quaternionf rot, Vector3f n, float time) {
        int rings = 14, segs = 180;
        float rin = BlackHoleDirector.DISK_IN, rout = BlackHoleDirector.DISK_OUT;
        Vector3f p = new Vector3f(), vel = new Vector3f(), toCam = new Vector3f();
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                for (int k = 0; k < 4; k++) {
                    int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                    float f = ii / (float) rings;
                    float r = rin + (rout - rin) * f * f;
                    float a = (float) (Math.PI * 2 * jj / segs);
                    rot.transform(p.set((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r));
                    rot.transform(vel.set((float) -Math.sin(a), 0, (float) Math.cos(a)));
                    toCam.set(eye).sub(p).normalize();
                    float doppler = 1f + 0.75f * vel.dot(toCam);
                    float omega = 40f / (1f + f * 6f);
                    float swirl = 0.65f + 0.35f * (float) Math.sin(a * 7 - time * omega + f * 18)
                            * (float) Math.sin(a * 3 + time * omega * 0.5f + f * 9);
                    float heat = 1f - f;
                    float cr = 1f, cg = 0.35f + 0.6f * heat, cb = 0.1f + 0.75f * heat * heat;
                    float alpha = (ii == 0 || ii == rings ? 0f : 0.85f) * swirl * doppler * (0.35f + 0.65f * heat);
                    bb.vertex(view, p.x, p.y, p.z).color(cr, cg, cb, Math.min(1f, alpha)).endVertex();
                }
            }
        }
    }

    /** Halo de la lente gravitatoria: la imagen del disco que se curva por encima y por debajo del agujero. */
    private static void drawLensedHalo(BufferBuilder bb, Matrix4f view, Vector3f right, Vector3f up, float time) {
        int segs = 128;
        float r0 = BlackHoleDirector.HORIZON * 1.16f, r1 = BlackHoleDirector.HORIZON * 2.4f;
        for (int j = 0; j < segs; j++) {
            float a0 = (float) (Math.PI * 2 * j / segs), a1 = (float) (Math.PI * 2 * (j + 1) / segs);
            float w0 = 0.35f + 0.65f * Math.abs((float) Math.sin(a0)), w1 = 0.35f + 0.65f * Math.abs((float) Math.sin(a1));
            float sw = 0.8f + 0.2f * (float) Math.sin(a0 * 9 - time * 6);
            Vector3f d0 = new Vector3f(right).mul((float) Math.cos(a0)).add(new Vector3f(up).mul((float) Math.sin(a0)));
            Vector3f d1 = new Vector3f(right).mul((float) Math.cos(a1)).add(new Vector3f(up).mul((float) Math.sin(a1)));
            bb.vertex(view, d0.x * r0, d0.y * r0, d0.z * r0).color(1f, 0.85f, 0.55f, 0.85f * w0 * sw).endVertex();
            bb.vertex(view, d0.x * r1, d0.y * r1, d0.z * r1).color(1f, 0.45f, 0.15f, 0f).endVertex();
            bb.vertex(view, d1.x * r1, d1.y * r1, d1.z * r1).color(1f, 0.45f, 0.15f, 0f).endVertex();
            bb.vertex(view, d1.x * r0, d1.y * r0, d1.z * r0).color(1f, 0.85f, 0.55f, 0.85f * w1 * sw).endVertex();
        }
    }

    /** Anillo de la estación Áncora: mira hacia el agujero negro y gira cuando se abre el portal. */
    private static Matrix4f gateMatrix(Matrix4f base, Vector3f pos, float time, int index, float portal) {
        Vector3f toHole = new Vector3f(pos).negate().normalize();
        return new Matrix4f(base).translate(pos.x, pos.y, pos.z)
                .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), toHole))
                .rotateY(time * (0.05f + portal * (1.5f + index * 0.4f)));
    }

    private static void drawGate(BufferBuilder bb, Matrix4f view, Vector3f pos, float time, int index, float portal) {
        Matrix4f m = gateMatrix(view, pos, time, index, portal);
        int segs = 40;
        float rad = BlackHoleDirector.GATE_RADIUS;
        for (int i = 0; i < segs; i++) {
            Matrix4f sm = new Matrix4f(m).rotateY((float) (Math.PI * 2 * i / segs));
            float half = (float) (Math.PI * rad / segs) + 4f;
            RenderUtil.box(bb, sm, rad - 30f, -22f, -half, rad + 30f, 22f, half, 0.42f, 0.42f, 0.48f, 1f);
            if (i % 5 == 0) RenderUtil.box(bb, sm, rad + 30f, -34f, -half * 0.6f, rad + 70f, 34f, half * 0.6f, 0.25f, 0.25f, 0.3f, 1f);
        }
    }

    private static void drawStationGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                        float t, float time, float gate, float portal, Vector3f seed, float extract) {
        for (int i = 0; i < 3; i++) {
            Vector3f g = v(BlackHoleDirector.GATES[i]);
            Matrix4f m = gateMatrix(view, g, time, i, portal);
            float rad = BlackHoleDirector.GATE_RADIUS;
            RenderUtil.ring(bb, m, rad - 34f, rad - 22f, 0, 96, 0.7f, 0.35f, 1f, 0.9f * gate);
            RenderUtil.ring(bb, m, rad - 120f, rad - 34f, 0, 96, 0.55f, 0.3f, 1f, 0.18f * gate);
            // vórtice del portal dentro de cada anillo
            if (portal > 0.01f) {
                for (int arm = 0; arm < 3; arm++) {
                    for (int k = 0; k < 40; k++) {
                        float f = k / 40f;
                        float a = arm * 2.094f + f * 7f - time * 4f;
                        float rr = (rad - 40f) * f;
                        Vector3f lp = new Vector3f((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr);
                        Vector3f wp = m.transformPosition(new Vector3f(lp), new Vector3f());
                        // m ya incluye la vista: dibujamos en coordenadas de vista con matriz identidad
                        RenderUtil.softDisc(bb, new Matrix4f(), wp.x, wp.y, wp.z, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                                22f + 30f * (1 - f), 8, 0.6f + 0.4f * f, 0.4f + 0.5f * f, 1f, 0.35f * portal * (1 - f * 0.6f), 0f);
                    }
                }
                Vector3f cpos = m.transformPosition(new Vector3f(), new Vector3f());
                RenderUtil.softDisc(bb, new Matrix4f(), cpos.x, cpos.y, cpos.z, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                        rad * 0.9f, 32, 0.8f, 0.7f, 1f, 0.5f * portal, 0f);
            }
            // rayo tractor desde cada anillo hacia la semilla
            if (gate > 0.01f && t < BlackHoleDirector.seg(0, 0, 1) + EXTRACT_END) {
                Vector3f axis = new Vector3f(seed).sub(g);
                float len = axis.length();
                if (len > 1f) {
                    axis.div(len);
                    Vector3f mid = new Vector3f(g).add(seed).mul(0.5f);
                    Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(mid)).normalize();
                    float fl = 0.8f + 0.2f * (float) Math.sin(time * 30 + i);
                    RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 70f * fl, 0.7f, 0.35f, 1f, 0.45f * gate);
                    RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, len * 0.5f, side, 18f, 0.95f, 0.85f, 1f, 0.8f * gate);
                }
            }
        }
        // brillo de la semilla de singularidad
        if (extract > 0.3f || t >= EXTRACT_END) {
            RenderUtil.softDisc(bb, view, seed.x, seed.y, seed.z, right, up, 80f, 40, 1f, 0.75f, 1f, 0f, 0.9f);
            RenderUtil.softDisc(bb, view, seed.x, seed.y, seed.z, right, up, 220f, 32, 0.7f, 0.4f, 1f, 0.4f, 0f);
            for (int i = 0; i < 80; i++) { // materia que gira alrededor de la semilla
                float a = rnd(i + 7000) * 6.283f + time * (3f + rnd(i + 7100) * 3f);
                float rr = 85f + rnd(i + 7200) * 90f;
                RenderUtil.softDisc(bb, view, seed.x + (float) Math.cos(a) * rr, seed.y + (rnd(i) - 0.5f) * 30f, seed.z + (float) Math.sin(a) * rr,
                        right, up, 6f, 6, 1f, 0.7f, 0.95f, 0.8f, 0f);
            }
        }
    }

    // ------------------------------------------------------------------ 4. agujero de gusano

    private static void renderWormhole(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up, float t, float time) {
        float u = BlackHoleDirector.seg(t, WORMHOLE_OPEN_END, SPACE_END);
        beginGlow(false);
        begin(bb);
        float speed = 2600f + 3000f * (float) Math.sin(Math.PI * u);
        float span = 9000f;
        for (int i = 0; i < 70; i++) {
            float z = span - ((time * speed + i * (span / 70f)) % span);
            float twist = z * 0.0012f + time * 1.4f;
            float radius = 520f + 160f * (float) Math.sin(z * 0.002f + time);
            float hue = (i % 7) / 7f;
            float cr = 0.5f + 0.5f * (float) Math.sin(6.28f * (hue + u)), cg = 0.35f + 0.3f * (float) Math.sin(6.28f * (hue + 0.33f)), cb = 1f;
            Vector3f c = new Vector3f(eye).add(new Vector3f(forward).mul(z));
            // anillo de luz alrededor del túnel
            Matrix4f m = new Matrix4f(view).translate(c.x, c.y, c.z)
                    .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), forward)).rotateY(twist);
            float fade = Mth.clamp(z / 900f, 0f, 1f) * Mth.clamp((span - z) / 2500f, 0f, 1f);
            RenderUtil.ring(bb, m, radius, radius + 40f, 0, 72, cr, cg, cb, 0.55f * fade);
            RenderUtil.ring(bb, m, radius - 160f, radius, 0, 72, cr, cg, cb, 0.08f * fade);
        }
        // espirales de luz en las paredes del túnel
        for (int s = 0; s < 6; s++) {
            for (int k = 0; k < 90; k++) {
                float z = (k / 90f) * span;
                float a = s * 1.047f + z * 0.0015f - time * 2.5f;
                float radius = 600f;
                Vector3f p = new Vector3f(eye).add(new Vector3f(forward).mul(z))
                        .add(new Vector3f(right).mul((float) Math.cos(a) * radius)).add(new Vector3f(up).mul((float) Math.sin(a) * radius));
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 40f, 8, 0.75f, 0.6f, 1f, 0.3f * Mth.clamp(z / 900f, 0f, 1f), 0f);
            }
        }
        drawWarp(bb, view, eye, forward, right, up, 1f, time, 0.85f, 0.75f, 1f, 1.2f);
        // la salida: la Tierra al final del túnel
        if (u > 0.55f) {
            float e = Cinematic.smooth((u - 0.55f) / 0.45f);
            Vector3f exit = new Vector3f(eye).add(new Vector3f(forward).mul(8000f - 6500f * e));
            RenderUtil.softDisc(bb, view, exit.x, exit.y, exit.z, right, up, 180f + 1400f * e, 48, 0.55f, 0.75f, 1f, 0.9f * e, 0f);
            RenderUtil.softDisc(bb, view, exit.x, exit.y, exit.z, right, up, 120f + 600f * e, 48, 0.3f, 0.55f, 0.95f, 1f * e, 0.6f * e);
        }
        // la semilla viaja delante de la cámara
        Vector3f seed = new Vector3f(eye).add(new Vector3f(forward).mul(700f + 120f * (float) Math.sin(time * 2)));
        RenderUtil.softDisc(bb, view, seed.x, seed.y, seed.z, right, up, 60f, 32, 1f, 0.75f, 1f, 0f, 0.9f);
        RenderUtil.softDisc(bb, view, seed.x, seed.y, seed.z, right, up, 160f, 32, 0.7f, 0.4f, 1f, 0.45f, 0f);
        draw(bb);
        // centro negro de la semilla (sólido)
        beginSolid();
        begin(bb);
        drawSphere(bb, view, seed, 45f, 0f, 0f, 0f);
        draw(bb);
    }
}
