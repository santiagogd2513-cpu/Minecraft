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
 * Dibuja la escena espacial a pantalla completa: estrellas, Vía Láctea, sol, planeta con nubes,
 * atmósfera y luces de ciudades, el satélite ÉGIDA-7 y el rayo.
 */
final class SpaceRenderer {
    private static final Vector3f SUN = new Vector3f(-0.8f, 0.25f, -0.55f).normalize();
    private static final int STAR_COUNT = 7000;
    private static final int BAND_COUNT = 5000;
    private static float[] starDir;   // x,y,z
    private static float[] starInfo;  // tamaño, brillo, r, g, b, fase
    private static float[] dust;      // nubes de la Vía Láctea: x,y,z,tamaño,alpha,tono

    private SpaceRenderer() {}

    private static void buildStars() {
        if (starDir != null) return;
        Random rnd = new Random(1337);
        int total = STAR_COUNT + BAND_COUNT;
        starDir = new float[total * 3];
        starInfo = new float[total * 6];
        Vector3f bandNormal = new Vector3f(0.35f, 0.8f, 0.48f).normalize();
        Vector3f bandA = new Vector3f(bandNormal).cross(new Vector3f(0, 0, 1)).normalize();
        Vector3f bandB = new Vector3f(bandNormal).cross(bandA).normalize();
        for (int i = 0; i < total; i++) {
            Vector3f d;
            if (i < STAR_COUNT) {
                double u = rnd.nextDouble() * 2 - 1, phi = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                d = new Vector3f((float) (s * Math.cos(phi)), (float) u, (float) (s * Math.sin(phi)));
            } else {
                double ang = rnd.nextDouble() * Math.PI * 2;
                float off = (float) (rnd.nextGaussian() * 0.09);
                d = new Vector3f(bandA).mul((float) Math.cos(ang)).add(new Vector3f(bandB).mul((float) Math.sin(ang)))
                        .add(new Vector3f(bandNormal).mul(off)).normalize();
            }
            starDir[i * 3] = d.x; starDir[i * 3 + 1] = d.y; starDir[i * 3 + 2] = d.z;
            float mag = (float) Math.pow(rnd.nextDouble(), 6);           // pocas estrellas muy brillantes
            boolean band = i >= STAR_COUNT;
            starInfo[i * 6] = band ? 5f + mag * 8f : 6f + mag * 26f;
            starInfo[i * 6 + 1] = band ? 0.25f + mag * 0.6f : 0.35f + mag * 0.65f;
            float temp = rnd.nextFloat();
            // azul, blanco, amarillo, naranja, rojo
            float[] c = temp < 0.18f ? new float[]{0.65f, 0.75f, 1f}
                    : temp < 0.7f ? new float[]{1f, 1f, 1f}
                    : temp < 0.86f ? new float[]{1f, 0.93f, 0.7f}
                    : temp < 0.95f ? new float[]{1f, 0.75f, 0.5f}
                    : new float[]{1f, 0.55f, 0.45f};
            starInfo[i * 6 + 2] = c[0]; starInfo[i * 6 + 3] = c[1]; starInfo[i * 6 + 4] = c[2];
            starInfo[i * 6 + 5] = rnd.nextFloat() * 6.28f;
        }
        // polvo luminoso de la galaxia
        dust = new float[180 * 6];
        for (int i = 0; i < 180; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            float off = (float) (rnd.nextGaussian() * 0.05);
            Vector3f d = new Vector3f(bandA).mul((float) Math.cos(ang)).add(new Vector3f(bandB).mul((float) Math.sin(ang)))
                    .add(new Vector3f(bandNormal).mul(off)).normalize();
            dust[i * 6] = d.x; dust[i * 6 + 1] = d.y; dust[i * 6 + 2] = d.z;
            dust[i * 6 + 3] = 350f + rnd.nextFloat() * 700f;
            dust[i * 6 + 4] = 0.02f + rnd.nextFloat() * 0.04f;
            dust[i * 6 + 5] = rnd.nextFloat();
        }
    }

    // ------------------------------------------------------------------ estado del satélite

    private record SatState(float lights, float unfold, float antenna, float extend, float aim,
                            float charge, float fired, float recoil) {}

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
        float recoil = 0f;
        if (t >= Timeline.A5_END) {
            float since = (t - Timeline.A5_END) / (Timeline.SPACE_END - Timeline.A5_END);
            recoil = 6f * (float) Math.exp(-since * 4) * Math.min(1f, since * 20);
        }
        return new SatState(lights, unfold, antenna, extend, aim, charge, fired, recoil);
    }

    private static Matrix4f cannonFrame(SatState s) {
        Vec3 sat = Cinematic.SATELLITE;
        return new Matrix4f().translate((float) sat.x, (float) sat.y + s.recoil, (float) sat.z)
                .translate(0, -5.5f, 0)
                .rotateX((1f - s.aim) * (float) Math.PI / 2f);
    }

    private static float barrelLength(SatState s) {
        return 6f + 22f * s.extend;
    }

    /** Posición de la boca del cañón en la escena espacial (la usa la cámara). */
    static Vec3 muzzlePosition(float t) {
        SatState s = satState(t);
        Vector3f p = cannonFrame(s).transformPosition(new Vector3f(0, -barrelLength(s) - 2.5f, 0));
        return new Vec3(p.x, p.y, p.z);
    }

    // ------------------------------------------------------------------ dibujo

    static void render(float partialTick) {
        buildStars();
        PlanetMesh.build();
        Minecraft mc = Minecraft.getInstance();
        float t = Cinematic.progress(partialTick);
        float time = Cinematic.seconds(partialTick);
        Cinematic.SpacePose pose = Cinematic.spacePose(t);
        Vector3f eye = new Vector3f((float) pose.eye().x, (float) pose.eye().y, (float) pose.eye().z);
        Vector3f center = new Vector3f((float) pose.center().x, (float) pose.center().y, (float) pose.center().z);
        Vector3f forward = new Vector3f(center).sub(eye).normalize();
        Vector3f upHint = Math.abs(forward.y) > 0.97f ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        // inclinación (roll)
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
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, 0.5f, 60000f);

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

        // 1) cielo: estrellas, galaxia y sol (aditivo, sin profundidad)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawSky(bb, view, eye, right, up, time);
        drawSun(bb, view, eye, right, up, time);
        BufferUploader.drawWithShader(bb.end());

        // 2) cuerpos sólidos: planeta y satélite
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderSurface(bb, view, Cinematic.PLANET_RADIUS, SUN);
        drawSatellite(bb, view, sat, time);
        BufferUploader.drawWithShader(bb.end());

        // 3) nubes (transparencia normal)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderClouds(bb, view, Cinematic.PLANET_RADIUS * 1.012f, SUN, time * 0.004f);
        BufferUploader.drawWithShader(bb.end());

        // 4) brillos (aditivo): atmósfera, ciudades, objetivo, luces del satélite, carga y rayo
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PlanetMesh.renderAtmosphere(bb, view, Cinematic.PLANET_RADIUS * 1.035f, SUN, eye);
        PlanetMesh.renderCityLights(bb, view, Cinematic.PLANET_RADIUS, SUN, time);
        drawTargetMarker(bb, view, eye, right, up, time, t);
        drawSatelliteGlow(bb, view, eye, right, up, sat, time);
        drawBeam(bb, view, eye, right, up, sat, t, time);
        BufferUploader.drawWithShader(bb.end());

        // restaurar
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
        // polvo de la galaxia (nubes tenues de color)
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
            if (size > 22f) { // las más brillantes tienen un halo
                RenderUtil.billboard(bb, view, cx, cy, cz, right, up, size * 3.5f,
                        starInfo[i * 6 + 2], starInfo[i * 6 + 3], starInfo[i * 6 + 4], a * 0.12f);
            }
        }
    }

    private static void drawSun(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time) {
        float dist = 8000f;
        float cx = eye.x + SUN.x * dist, cy = eye.y + SUN.y * dist, cz = eye.z + SUN.z * dist;
        float pulse = 1f + 0.03f * (float) Math.sin(time * 1.7f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 2600f * pulse, 1f, 0.55f, 0.2f, 0.05f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 1300f * pulse, 1f, 0.7f, 0.35f, 0.10f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 600f, 1f, 0.85f, 0.55f, 0.25f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 260f, 1f, 0.95f, 0.8f, 0.7f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 170f, 1f, 1f, 0.97f, 1f);
        RenderUtil.billboard(bb, view, cx, cy, cz, right, up, 150f, 1f, 1f, 1f, 1f);
        // destello horizontal tipo lente
        RenderUtil.streak(bb, view, cx, cy, cz, right, 4200f, up, 22f, 1f, 0.8f, 0.6f, 0.18f);
        RenderUtil.streak(bb, view, cx, cy, cz, up, 900f, right, 14f, 1f, 0.85f, 0.7f, 0.10f);
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

    // ------------------------------------------------------------------ satélite ÉGIDA-7

    private static void drawSatellite(BufferBuilder bb, Matrix4f view, SatState s, float time) {
        Vec3 sat = Cinematic.SATELLITE;
        Matrix4f m = new Matrix4f(view).translate((float) sat.x, (float) sat.y + s.recoil, (float) sat.z);
        // cuerpo principal con lámina dorada
        RenderUtil.box(bb, m, -6, -5, -6, 6, 5, 6, 0.78f, 0.62f, 0.26f, 1f);
        // franjas y paneles del cuerpo
        RenderUtil.box(bb, m, -6.1f, -1f, -6.1f, 6.1f, 1f, 6.1f, 0.25f, 0.25f, 0.28f, 1f);
        RenderUtil.box(bb, m, -4, 5, -4, 4, 8, 4, 0.55f, 0.56f, 0.6f, 1f);      // módulo superior
        RenderUtil.box(bb, m, -2.5f, 8, -2.5f, 2.5f, 9.5f, 2.5f, 0.3f, 0.3f, 0.34f, 1f);
        // radiadores blancos a los lados
        RenderUtil.box(bb, m, -5, -4, 6, 5, 4, 6.6f, 0.92f, 0.92f, 0.95f, 1f);
        RenderUtil.box(bb, m, -5, -4, -6.6f, 5, 4, -6, 0.92f, 0.92f, 0.95f, 1f);
        for (int i = -4; i <= 4; i += 2) {
            RenderUtil.box(bb, m, i - 0.2f, -4, 6.6f, i + 0.2f, 4, 7.4f, 0.75f, 0.75f, 0.8f, 1f);
            RenderUtil.box(bb, m, i - 0.2f, -4, -7.4f, i + 0.2f, 4, -6.6f, 0.75f, 0.75f, 0.8f, 1f);
        }
        // propulsores traseros
        Matrix4f thr = new Matrix4f(m).translate(0, 0, 7.4f).rotateX((float) Math.PI / 2);
        RenderUtil.cylinder(bb, new Matrix4f(thr).translate(-3, 0, 0), 1.2f, 0, 3, 10, 0.2f, 0.2f, 0.22f, 1f);
        RenderUtil.cylinder(bb, new Matrix4f(thr).translate(3, 0, 0), 1.2f, 0, 3, 10, 0.2f, 0.2f, 0.22f, 1f);

        // alas de paneles solares (se despliegan)
        for (int side = -1; side <= 1; side += 2) {
            float sx = side;
            // brazo
            RenderUtil.box(bb, m, sx > 0 ? 6 : -10, -0.6f, -0.6f, sx > 0 ? 10 : -6, 0.6f, 0.6f, 0.6f, 0.6f, 0.65f, 1f);
            float len = 4f + 52f * s.unfold;
            float x0 = sx > 0 ? 10 : -10 - len, x1 = sx > 0 ? 10 + len : -10;
            float span = 3f + 7f * s.unfold;
            RenderUtil.box(bb, m, x0, -0.25f, -span, x1, 0.25f, span, 0.06f, 0.10f, 0.32f, 1f);
            // rejilla de celdas
            for (float gx = 0; gx <= len; gx += 4.5f) {
                float x = sx > 0 ? 10 + gx : -10 - gx;
                RenderUtil.box(bb, m, x - 0.15f, 0.25f, -span, x + 0.15f, 0.32f, span, 0.55f, 0.6f, 0.75f, 1f);
            }
            RenderUtil.box(bb, m, x0, 0.25f, -0.15f, x1, 0.32f, 0.15f, 0.55f, 0.6f, 0.75f, 1f);
            // marco
            RenderUtil.box(bb, m, x0, -0.3f, span - 0.3f, x1, 0.3f, span + 0.2f, 0.7f, 0.7f, 0.72f, 1f);
            RenderUtil.box(bb, m, x0, -0.3f, -span - 0.2f, x1, 0.3f, -span + 0.3f, 0.7f, 0.7f, 0.72f, 1f);
        }

        // antena parabólica
        Matrix4f ant = new Matrix4f(m).translate(0, 9.5f, 0).rotateZ((1f - s.antenna) * -1.2f + 0.35f);
        RenderUtil.box(bb, ant, -0.4f, 0, -0.4f, 0.4f, 5, 0.4f, 0.7f, 0.7f, 0.72f, 1f);
        Matrix4f dish = new Matrix4f(ant).translate(0, 5, 0).rotateX(0.5f);
        for (int ring = 0; ring < 4; ring++) {
            float r0 = ring * 1.6f, r1 = r0 + 1.6f;
            Matrix4f dr = new Matrix4f(dish).translate(0, ring * 0.35f, 0);
            RenderUtil.ring(bb, dr, r0, r1, 0, 20, 0.88f - ring * 0.04f, 0.88f - ring * 0.04f, 0.9f, 1f);
        }
        RenderUtil.box(bb, dish, -0.2f, 0, -0.2f, 0.2f, 3.5f, 0.2f, 0.6f, 0.6f, 0.62f, 1f);

        // cañón de partículas
        Matrix4f cf = new Matrix4f(view).mul(cannonFrame(s));
        float len = barrelLength(s);
        RenderUtil.box(bb, cf, -3, -1.5f, -3, 3, 1, 3, 0.3f, 0.3f, 0.34f, 1f);          // montura
        RenderUtil.cylinder(bb, cf, 2.4f, -len, -1.5f, 16, 0.42f, 0.43f, 0.48f, 1f);   // cañón
        RenderUtil.cylinder(bb, cf, 3.2f, -len - 2.5f, -len, 16, 0.28f, 0.28f, 0.32f, 1f); // boca
        for (int i = 0; i < 4; i++) { // aletas de refrigeración
            float y = -3 - i * (len - 5) / 4f;
            Matrix4f fin = new Matrix4f(cf).rotateY(i * 0.4f);
            RenderUtil.box(bb, fin, -4.2f, y - 0.4f, -0.3f, 4.2f, y + 0.4f, 0.3f, 0.5f, 0.5f, 0.55f, 1f);
            RenderUtil.box(bb, fin, -0.3f, y - 0.4f, -4.2f, 0.3f, y + 0.4f, 4.2f, 0.5f, 0.5f, 0.55f, 1f);
        }
        // luces de estado del cuerpo (se encienden una a una)
        int lit = Math.round(s.lights * 6);
        for (int i = 0; i < 6; i++) {
            boolean on = i < lit;
            float x = -4.5f + i * 1.8f;
            float r = on ? (i < 4 ? 0.2f : 1f) : 0.08f, g = on ? 1f : 0.08f, b = on ? (i < 4 ? 0.4f : 0.3f) : 0.1f;
            RenderUtil.box(bb, m, x - 0.5f, 2.2f, 6.05f, x + 0.5f, 3.2f, 6.35f, r, g, b, 1f);
        }
    }

    private static void drawSatelliteGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                          SatState s, float time) {
        Vec3 sat = Cinematic.SATELLITE;
        float sx = (float) sat.x, sy = (float) sat.y + s.recoil, sz = (float) sat.z;
        if (s.lights > 0.05f) {
            // luces de navegación: roja (izquierda), verde (derecha), estroboscópica blanca arriba
            float blink = (time % 1.2f) < 0.25f ? 1f : 0.15f;
            float wing = 10f + 4f + 52f * s.unfold;
            RenderUtil.billboard(bb, view, sx - wing, sy, sz, right, up, 2.2f, 1f, 0.1f, 0.1f, blink * s.lights);
            RenderUtil.billboard(bb, view, sx - wing, sy, sz, right, up, 7f, 1f, 0.1f, 0.1f, 0.25f * blink * s.lights);
            RenderUtil.billboard(bb, view, sx + wing, sy, sz, right, up, 2.2f, 0.1f, 1f, 0.2f, blink * s.lights);
            RenderUtil.billboard(bb, view, sx + wing, sy, sz, right, up, 7f, 0.1f, 1f, 0.2f, 0.25f * blink * s.lights);
            float strobe = (time % 2f) < 0.08f ? 1f : 0f;
            RenderUtil.billboard(bb, view, sx, sy + 10f, sz, right, up, 3f, 1f, 1f, 1f, strobe);
            RenderUtil.billboard(bb, view, sx, sy + 10f, sz, right, up, 14f, 0.8f, 0.9f, 1f, 0.3f * strobe);
            // brillo azul de los propulsores
            float flick = 0.6f + 0.4f * (float) Math.sin(time * 40);
            RenderUtil.billboard(bb, view, sx - 3, sy, sz + 10.6f, right, up, 2.5f, 0.3f, 0.6f, 1f, 0.6f * flick * s.lights);
            RenderUtil.billboard(bb, view, sx + 3, sy, sz + 10.6f, right, up, 2.5f, 0.3f, 0.6f, 1f, 0.6f * flick * s.lights);
        }
        // energía del cañón
        if (s.charge > 0f && s.fired < 1f) {
            Matrix4f cf = new Matrix4f(view).mul(cannonFrame(s));
            float len = barrelLength(s);
            for (int i = 0; i < 4; i++) { // anillos que giran cada vez más rápido
                float y = -4 - i * (len - 6) / 3f;
                float spin = time * (2f + 18f * s.charge) + i;
                Matrix4f rm = new Matrix4f(cf).translate(0, y, 0).rotateY(spin);
                RenderUtil.ring(bb, rm, 3.3f, 3.9f, 0, 24, 0.3f, 0.8f, 1f, 0.75f * s.charge);
                RenderUtil.ring(bb, rm, 3.9f, 5.2f, 0, 24, 0.2f, 0.5f, 1f, 0.2f * s.charge);
            }
            Vector3f muzzle = cannonFrame(s).transformPosition(new Vector3f(0, -len - 2.5f, 0));
            float pulse = 1f + 0.15f * (float) Math.sin(time * (8 + 30 * s.charge));
            float core = (1.5f + 9f * s.charge) * pulse;
            RenderUtil.billboard(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, core * 3.5f, 0.2f, 0.5f, 1f, 0.25f * s.charge);
            RenderUtil.billboard(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, core * 1.6f, 0.5f, 0.8f, 1f, 0.6f * s.charge);
            RenderUtil.billboard(bb, view, muzzle.x, muzzle.y, muzzle.z, right, up, core * 0.7f, 1f, 1f, 1f, 0.95f * s.charge);
            // descargas eléctricas alrededor de la boca
            if (s.charge > 0.4f) {
                Random rnd = new Random((long) (time * 12));
                int arcs = (int) (s.charge * 10);
                for (int i = 0; i < arcs; i++) {
                    Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalize();
                    float l = 4f + rnd.nextFloat() * 8f * s.charge;
                    Vector3f mid = new Vector3f(muzzle).add(new Vector3f(dir).mul(l * 0.5f));
                    Vector3f side = new Vector3f(dir).cross(new Vector3f(eye).sub(mid)).normalize();
                    RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, dir, l * 0.5f, side, 0.18f, 0.6f, 0.85f, 1f, 0.9f);
                }
            }
        }
    }

    private static void drawBeam(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                 SatState s, float t, float time) {
        if (t < Timeline.A5_END) return;
        Vector3f from = cannonFrame(s).transformPosition(new Vector3f(0, -barrelLength(s) - 2.5f, 0));
        Vec3 tp = Cinematic.TARGET_SPACE;
        Vector3f to = new Vector3f((float) tp.x, (float) tp.y, (float) tp.z);
        float travel = Mth.clamp((t - Timeline.A5_END) / ((Timeline.SPACE_END - Timeline.A5_END) * 0.9f), 0f, 1f);
        // el frente del rayo avanza al mismo ritmo que la cámara que lo persigue
        float front = travel < 0.18f ? 0.02f : Cinematic.easeInCubic((travel - 0.18f) / 0.82f);
        Vector3f end = new Vector3f(from).lerp(to, Math.max(0.02f, front));
        Vector3f axis = new Vector3f(end).sub(from);
        float length = axis.length();
        if (length < 0.01f) return;
        axis.div(length);
        Vector3f mid = new Vector3f(from).add(end).mul(0.5f);
        Vector3f side = new Vector3f(axis).cross(new Vector3f(eye).sub(mid)).normalize();
        float flick = 1f + 0.12f * (float) Math.sin(time * 50);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 16f * flick, 0.15f, 0.35f, 1f, 0.15f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 6f * flick, 0.3f, 0.7f, 1f, 0.5f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 2.2f, 0.85f, 0.95f, 1f, 0.95f);
        RenderUtil.streak(bb, view, mid.x, mid.y, mid.z, axis, length / 2, side, 0.9f, 1f, 1f, 1f, 1f);
        // destello en la boca y en el frente
        RenderUtil.billboard(bb, view, from.x, from.y, from.z, right, up, 26f * flick, 0.5f, 0.8f, 1f, 0.6f);
        RenderUtil.billboard(bb, view, end.x, end.y, end.z, right, up, 18f * flick, 0.7f, 0.9f, 1f, 0.8f);
        // anillos de choque a lo largo del rayo
        for (int i = 1; i <= 6; i++) {
            float f = ((time * 0.9f + i / 6f) % 1f);
            if (f > front) continue;
            Vector3f p = new Vector3f(from).lerp(to, f);
            RenderUtil.billboard(bb, view, p.x, p.y, p.z, right, up, 9f, 0.4f, 0.7f, 1f, 0.25f);
        }
        if (front > 0.97f) {
            RenderUtil.billboard(bb, view, to.x, to.y + 1, to.z, right, up, 90f * flick, 1f, 0.9f, 0.7f, 0.6f);
            RenderUtil.billboard(bb, view, to.x, to.y + 1, to.z, right, up, 260f, 1f, 0.6f, 0.3f, 0.2f);
        }
    }
}
