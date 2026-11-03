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
    static final Vector3f SUN = new Vector3f((float) Cinematic.SUN_DIR.x, (float) Cinematic.SUN_DIR.y, (float) Cinematic.SUN_DIR.z);
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
    static Vector3f moonPosition(float time) {
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
        MarsMesh.build(SUN);
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

    // ------------------------------------------------------------------ espacio profundo

    private static float[] faint;       // estrellas tenues de fondo: x,y,z,tamaño,alpha
    private static float[] nebula;      // nubes de nebulosas: x,y,z,tamaño,r,g,b,alpha
    private static float[] galaxy;      // galaxias lejanas: x,y,z, ejeA(x,y,z), ejeB(x,y,z), tamaño, tono
    private static float[] cluster;     // cúmulos de estrellas: x,y,z,tamaño,alpha

    private static Vector3f randomDir(Random r) {
        double u = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
    }

    private static void buildDeepSky() {
        if (faint != null) return;
        Random r = new Random(4040);
        faint = new float[9000 * 5];
        for (int i = 0; i < 9000; i++) {
            Vector3f d = randomDir(r);
            faint[i * 5] = d.x; faint[i * 5 + 1] = d.y; faint[i * 5 + 2] = d.z;
            faint[i * 5 + 3] = 3.5f + r.nextFloat() * 3f;
            faint[i * 5 + 4] = 0.10f + r.nextFloat() * 0.22f;
        }
        // nebulosas de colores (rosa, turquesa, naranja, violeta)
        float[][] palettes = {{1f, 0.35f, 0.6f}, {0.3f, 0.85f, 0.9f}, {1f, 0.55f, 0.25f}, {0.6f, 0.4f, 1f}, {0.4f, 0.6f, 1f}};
        int nebulae = 10, blobs = 46;
        nebula = new float[nebulae * blobs * 8];
        int k = 0;
        for (int n = 0; n < nebulae; n++) {
            Vector3f c = randomDir(r);
            Vector3f t1 = new Vector3f(c).cross(Math.abs(c.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f t2 = new Vector3f(c).cross(t1).normalize();
            float[] a = palettes[r.nextInt(palettes.length)], b = palettes[r.nextInt(palettes.length)];
            float spread = 0.04f + r.nextFloat() * 0.07f;
            for (int i = 0; i < blobs; i++) {
                float gx = (float) r.nextGaussian() * spread, gy = (float) r.nextGaussian() * spread * 0.6f;
                Vector3f d = new Vector3f(c).add(new Vector3f(t1).mul(gx)).add(new Vector3f(t2).mul(gy)).normalize();
                float mix = r.nextFloat();
                nebula[k++] = d.x; nebula[k++] = d.y; nebula[k++] = d.z;
                nebula[k++] = 140f + r.nextFloat() * 420f;
                nebula[k++] = a[0] + (b[0] - a[0]) * mix; nebula[k++] = a[1] + (b[1] - a[1]) * mix; nebula[k++] = a[2] + (b[2] - a[2]) * mix;
                nebula[k++] = 0.025f + r.nextFloat() * 0.05f;
            }
        }
        // galaxias lejanas (espirales vistas de lado o de frente)
        int gal = 16;
        galaxy = new float[gal * 11];
        for (int i = 0; i < gal; i++) {
            Vector3f c = randomDir(r);
            Vector3f t1 = new Vector3f(c).cross(Math.abs(c.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f t2 = new Vector3f(c).cross(t1).normalize();
            float ang = r.nextFloat() * 6.28f;
            Vector3f ax = new Vector3f(t1).mul((float) Math.cos(ang)).add(new Vector3f(t2).mul((float) Math.sin(ang)));
            Vector3f bx = new Vector3f(t1).mul((float) -Math.sin(ang)).add(new Vector3f(t2).mul((float) Math.cos(ang)));
            galaxy[i * 11] = c.x; galaxy[i * 11 + 1] = c.y; galaxy[i * 11 + 2] = c.z;
            galaxy[i * 11 + 3] = ax.x; galaxy[i * 11 + 4] = ax.y; galaxy[i * 11 + 5] = ax.z;
            galaxy[i * 11 + 6] = bx.x; galaxy[i * 11 + 7] = bx.y; galaxy[i * 11 + 8] = bx.z;
            galaxy[i * 11 + 9] = 50f + r.nextFloat() * 130f;
            galaxy[i * 11 + 10] = r.nextFloat();
        }
        // cúmulos de estrellas
        int clusters = 8, per = 90;
        cluster = new float[clusters * per * 5];
        k = 0;
        for (int n = 0; n < clusters; n++) {
            Vector3f c = randomDir(r);
            float spread = 0.006f + r.nextFloat() * 0.012f;
            for (int i = 0; i < per; i++) {
                Vector3f d = new Vector3f(c).add((float) r.nextGaussian() * spread, (float) r.nextGaussian() * spread,
                        (float) r.nextGaussian() * spread).normalize();
                cluster[k++] = d.x; cluster[k++] = d.y; cluster[k++] = d.z;
                cluster[k++] = 4f + r.nextFloat() * 6f;
                cluster[k++] = 0.35f + r.nextFloat() * 0.5f;
            }
        }
    }

    static void drawSky(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time) {
        buildDeepSky();
        float dist = 9000f;
        // nebulosas (lo más tenue, primero)
        for (int i = 0; i < nebula.length; i += 8) {
            RenderUtil.softDisc(bb, view, eye.x + nebula[i] * dist, eye.y + nebula[i + 1] * dist, eye.z + nebula[i + 2] * dist,
                    right, up, nebula[i + 3], 12, nebula[i + 4], nebula[i + 5], nebula[i + 6], nebula[i + 7], 0f);
        }
        for (int i = 0; i < dust.length; i += 6) {
            float cx = eye.x + dust[i] * dist, cy = eye.y + dust[i + 1] * dist, cz = eye.z + dust[i + 2] * dist;
            float tone = dust[i + 5];
            RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, dust[i + 3], 14, 0.55f + tone * 0.4f, 0.45f + tone * 0.2f, 0.75f - tone * 0.2f, dust[i + 4], 0f);
        }
        // galaxias lejanas
        for (int i = 0; i < galaxy.length; i += 11) {
            float cx = eye.x + galaxy[i] * dist, cy = eye.y + galaxy[i + 1] * dist, cz = eye.z + galaxy[i + 2] * dist;
            Vector3f ax = new Vector3f(galaxy[i + 3], galaxy[i + 4], galaxy[i + 5]);
            Vector3f bx = new Vector3f(galaxy[i + 6], galaxy[i + 7], galaxy[i + 8]);
            float size = galaxy[i + 9], tone = galaxy[i + 10];
            float r = 0.85f + 0.15f * tone, g = 0.8f + 0.1f * tone, b = 0.7f + 0.3f * (1 - tone);
            RenderUtil.softStreak(bb, view, cx, cy, cz, ax, size, bx, size * 0.35f, r, g, b, 0.22f);
            RenderUtil.softStreak(bb, view, cx, cy, cz, new Vector3f(ax).add(new Vector3f(bx).mul(0.5f)).normalize(), size * 0.7f, bx, size * 0.2f, r, g, b, 0.12f);
            RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, size * 0.22f, 10, 1f, 0.95f, 0.85f, 0.55f, 0f);
        }
        // estrellas tenues de fondo
        for (int i = 0; i < faint.length; i += 5) {
            RenderUtil.billboard(bb, view, eye.x + faint[i] * dist, eye.y + faint[i + 1] * dist, eye.z + faint[i + 2] * dist,
                    right, up, faint[i + 3], 0.85f, 0.88f, 1f, faint[i + 4]);
        }
        // cúmulos
        for (int i = 0; i < cluster.length; i += 5) {
            float tw = 0.8f + 0.2f * (float) Math.sin(time * 3f + i);
            RenderUtil.billboard(bb, view, eye.x + cluster[i] * dist, eye.y + cluster[i + 1] * dist, eye.z + cluster[i + 2] * dist,
                    right, up, cluster[i + 3], 0.8f, 0.9f, 1f, cluster[i + 4] * tw);
        }
        int total = STAR_COUNT + BAND_COUNT;
        for (int i = 0; i < total; i++) {
            float cx = eye.x + starDir[i * 3] * dist, cy = eye.y + starDir[i * 3 + 1] * dist, cz = eye.z + starDir[i * 3 + 2] * dist;
            float size = starInfo[i * 6];
            float twinkle = 0.75f + 0.25f * (float) Math.sin(time * 2.3f + starInfo[i * 6 + 5]);
            float a = starInfo[i * 6 + 1] * twinkle;
            RenderUtil.billboard(bb, view, cx, cy, cz, right, up, size,
                    starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a);
            if (size > 20f) {
                RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, size * 3.5f, 14, starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a * 0.14f, 0f);
                // destello en cruz de las estrellas más brillantes
                RenderUtil.softStreak(bb, view, cx, cy, cz, right, size * 5f, up, size * 0.25f, starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a * 0.5f);
                RenderUtil.softStreak(bb, view, cx, cy, cz, up, size * 5f, right, size * 0.25f, starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a * 0.5f);
            }
        }
    }

    static void drawSun(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time) {
        float dist = 8000f;
        float cx = eye.x + SUN.x * dist, cy = eye.y + SUN.y * dist, cz = eye.z + SUN.z * dist;
        float pulse = 1f + 0.03f * (float) Math.sin(time * 1.7f);
        // halo
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 4600f * pulse, 32, 1f, 0.45f, 0.12f, 0.09f, 0f);
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 2600f * pulse, 32, 1f, 0.58f, 0.22f, 0.15f, 0f);
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 1300f * pulse, 32, 1f, 0.72f, 0.38f, 0.28f, 0f);
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 640f, 32, 1f, 0.86f, 0.58f, 0.4f, 0f);
        // corona: serpentinas irregulares que cambian despacio
        for (int i = 0; i < 28; i++) {
            float ang = i * (float) Math.PI * 2f / 28f + rnd(i * 13) * 0.2f + time * 0.01f;
            float len = (330f + 700f * rnd(i * 17)) * (0.8f + 0.2f * (float) Math.sin(time * 0.5f + i * 1.3f));
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            float base = 235f + len * 0.5f;
            RenderUtil.softStreak(bb, view, cx + dir.x * base, cy + dir.y * base, cz + dir.z * base,
                    dir, len * 0.5f, side, 25f + 40f * rnd(i * 19), 1f, 0.93f, 0.8f, 0.13f);
        }
        // disco con oscurecimiento en el borde (más brillante en el centro)
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 236f, 40, 1f, 0.6f, 0.25f, 1f, 1f);
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 232f, 40, 1f, 0.92f, 0.7f, 1f, 0.6f);
        RenderUtil.softDisc(bb, view, cx, cy, cz, right, up, 215f, 40, 1f, 1f, 0.97f, 1f, 0f);
        // granulado de la superficie
        for (int i = 0; i < 40; i++) {
            float ang = rnd(i * 23 + 1) * 6.283f, rr = (float) Math.sqrt(rnd(i * 23 + 2)) * 200f;
            float fl = 0.5f + 0.5f * (float) Math.sin(time * (1f + rnd(i)) + i);
            Vector3f off = new Vector3f(right).mul((float) Math.cos(ang) * rr).add(new Vector3f(up).mul((float) Math.sin(ang) * rr));
            RenderUtil.softDisc(bb, view, cx + off.x, cy + off.y, cz + off.z, right, up, 18f + 18f * rnd(i * 5), 8, 1f, 1f, 0.9f, 0.25f * fl, 0f);
        }
        // protuberancias rojizas en el borde
        for (int i = 0; i < 7; i++) {
            float ang = rnd(i * 29 + 7) * 6.283f;
            float grow = 0.7f + 0.3f * (float) Math.sin(time * 0.6f + i * 2f);
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            float h = (25f + 40f * rnd(i * 31)) * grow;
            Vector3f p = new Vector3f(cx, cy, cz).add(new Vector3f(dir).mul(236f + h * 0.5f));
            RenderUtil.softStreak(bb, view, p.x, p.y, p.z, dir, h, side, 14f + 10f * rnd(i), 1f, 0.35f, 0.18f, 0.75f);
        }
        // rayos de luz que giran despacio
        for (int i = 0; i < 16; i++) {
            float ang = i * (float) Math.PI / 8f + time * 0.03f;
            float len = (1600f + 1000f * rnd(i * 7)) * (0.85f + 0.15f * (float) Math.sin(time * 0.8f + i));
            Vector3f dir = new Vector3f(right).mul((float) Math.cos(ang)).add(new Vector3f(up).mul((float) Math.sin(ang)));
            Vector3f side = new Vector3f(right).mul((float) -Math.sin(ang)).add(new Vector3f(up).mul((float) Math.cos(ang)));
            RenderUtil.softStreak(bb, view, cx + dir.x * len * 0.5f, cy + dir.y * len * 0.5f, cz + dir.z * len * 0.5f, dir, len * 0.5f, side, 30f + 30f * rnd(i * 3), 1f, 0.85f, 0.6f, 0.07f);
        }
        // destello de lente horizontal
        RenderUtil.softStreak(bb, view, cx, cy, cz, right, 7000f, up, 30f, 1f, 0.8f, 0.6f, 0.2f);
        RenderUtil.softStreak(bb, view, cx, cy, cz, right, 2600f, up, 9f, 1f, 1f, 0.9f, 0.45f);
        RenderUtil.softStreak(bb, view, cx, cy, cz, up, 1300f, right, 18f, 1f, 0.85f, 0.7f, 0.1f);
        // reflejos de lente hacia el centro de la pantalla (solo si se mira hacia el sol)
        Vector3f forward = new Vector3f(up).cross(right).normalize();
        float facing = forward.dot(SUN);
        if (facing > 0.3f) {
            float fa = Cinematic.smooth((facing - 0.3f) / 0.4f);
            Vector3f sun = new Vector3f(cx, cy, cz);
            Vector3f mid = new Vector3f(eye).add(new Vector3f(forward).mul(dist));
            float[][] ghosts = {{0.35f, 160f, 1f, 0.6f, 0.3f}, {0.6f, 70f, 0.4f, 1f, 0.8f}, {0.85f, 240f, 0.6f, 0.4f, 1f},
                    {1.15f, 110f, 1f, 0.8f, 0.4f}, {1.45f, 320f, 0.4f, 0.7f, 1f}, {1.85f, 150f, 0.9f, 0.4f, 0.9f}};
            for (float[] gh : ghosts) {
                Vector3f p = new Vector3f(sun).lerp(mid, gh[0]);
                RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, gh[1], 6, gh[2], gh[3], gh[4], 0.07f * fa, 0.02f * fa);
            }
        }
    }

    static void drawMoon(BufferBuilder bb, Matrix4f view, Vector3f moon) {
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

    static void drawMoonGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, Vector3f moon) {
        // halo suave alrededor de la luna
        RenderUtil.softDisc(bb, view, moon.x, moon.y, moon.z, right, up, MOON_RADIUS * 1.6f, 32, 0.75f, 0.8f, 0.95f, 0.05f, 0f);
    }

    private static void drawTargetMarker(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                         float time, float t) {
        if (t >= Timeline.A5_END + 0.01f) return;
        Vec3 tp = Cinematic.TARGET_SPACE;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 6);
        float distance = eye.distance((float) tp.x, (float) tp.y, (float) tp.z);
        float size = Math.max(3f, distance * 0.006f);
        RenderUtil.softDisc(bb, view, (float) tp.x, (float) tp.y + 1f, (float) tp.z, right, up, size, 14, 1f, 0.1f, 0.08f, pulse, 0f);
        RenderUtil.softDisc(bb, view, (float) tp.x, (float) tp.y + 1f, (float) tp.z, right, up, size * 4f, 14, 1f, 0.1f, 0.05f, 0.25f * pulse, 0f);
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
        RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, size * S, 10, r, g, b, a, 0f);
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
                    RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, dir, l * 0.5f, side, 0.22f * S, 0.6f, 0.85f, 1f, 0.9f);
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
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 90f * flick, 0.12f, 0.25f, 1f, 0.10f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 55f * flick, 0.15f, 0.35f, 1f, 0.18f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 26f * flick, 0.3f, 0.7f, 1f, 0.5f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 10f, 0.85f, 0.95f, 1f, 0.95f);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 4f, 1f, 1f, 1f, 1f);
        // espiral de energía alrededor del rayo
        Vector3f other = new Vector3f(axis).cross(side).normalize();
        for (int i = 0; i < 80; i++) {
            float f = (i / 80f + time * 0.6f) % 1f;
            if (f > front) continue;
            float ang = f * 60f + time * 8f;
            Vector3f p = new Vector3f(from).lerp(to, f)
                    .add(new Vector3f(side).mul((float) Math.cos(ang) * 40f))
                    .add(new Vector3f(other).mul((float) Math.sin(ang) * 40f));
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 7f, 14, 0.5f, 0.8f, 1f, 0.5f, 0f);
        }
        // destello en la boca y en el frente
        RenderUtil.softDisc(bb, view, from.x, from.y, from.z, right, up, 110f * flick, 14, 0.5f, 0.8f, 1f, 0.6f, 0f);
        RenderUtil.softDisc(bb, view, from.x, from.y, from.z, right, up, 40f * flick, 14, 1f, 1f, 1f, 0.9f, 0f);
        RenderUtil.softDisc(bb, view, end.x, end.y, end.z, right, up, 80f * flick, 14, 0.7f, 0.9f, 1f, 0.8f, 0f);
        for (int i = 1; i <= 8; i++) {
            float f = ((time * 0.9f + i / 8f) % 1f);
            if (f > front) continue;
            Vector3f p = new Vector3f(from).lerp(to, f);
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, 45f, 14, 0.4f, 0.7f, 1f, 0.25f, 0f);
        }
        if (front > 0.97f) {
            RenderUtil.softDisc(bb, view, to.x, to.y + 1, to.z, right, up, 320f * flick, 14, 1f, 0.9f, 0.7f, 0.7f, 0f);
            RenderUtil.softDisc(bb, view, to.x, to.y + 1, to.z, right, up, 800f, 14, 1f, 0.6f, 0.3f, 0.25f, 0f);
        }
    }
}
