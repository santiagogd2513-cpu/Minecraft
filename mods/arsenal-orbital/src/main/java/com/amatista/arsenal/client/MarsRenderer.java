package com.amatista.arsenal.client;

import com.amatista.arsenal.PhobosTimeline;
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

/** Escena espacial del "Protocolo Fobos": Tierra, Luna, viaje, Marte, Fobos, Deimos, misil y honda. */
final class MarsRenderer {
    private static final Vector3f SUN = SpaceRenderer.SUN;
    private static float[] rnd;

    private MarsRenderer() {}

    private static float rnd(int i) {
        if (rnd == null) {
            Random r = new Random(77);
            rnd = new float[8192];
            for (int k = 0; k < rnd.length; k++) rnd[k] = r.nextFloat();
        }
        return rnd[(i & 0x7fffffff) % rnd.length];
    }

    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    static void render(float partialTick) {
        SpaceRenderer.prepare();
        Minecraft mc = Minecraft.getInstance();
        float t = Cinematic.progress(partialTick);
        float time = Cinematic.seconds(partialTick);
        Cinematic.SpacePose pose = PhobosDirector.spacePose(t);
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
        Vector3f marsC = v(PhobosDirector.MARS), phobosC = v(PhobosDirector.phobosPosition(t)), moonC = SpaceRenderer.moonPosition(time);
        float near = RenderUtil.nearPlane(eye, 0.5f, 0f, 0f, 0f, Cinematic.PLANET_RADIUS * 1.04f,
                marsC.x, marsC.y, marsC.z, PhobosDirector.MARS_RADIUS * 1.03f,
                phobosC.x, phobosC.y, phobosC.z, 120f,
                moonC.x, moonC.y, moonC.z, 270f);
        near = Math.min(near, RenderUtil.nearToCenter(eye, center, 0.5f, 1e9f) * 4f);
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(pose.fov()), (float) w / h, near, 120000f);

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

        Vector3f mars = v(PhobosDirector.MARS);
        Vector3f phobos = v(PhobosDirector.phobosPosition(t));
        Vector3f motion = v(PhobosDirector.phobosVelocityDir(t));
        float spin = PhobosDirector.spin(t);
        Quaternionf rot = new Quaternionf().rotateY(spin).rotateX(spin * 0.37f).rotateZ(0.4f);
        double dAng = time * 0.02;
        Vector3f deimos = new Vector3f(mars.x + (float) Math.cos(dAng + 2.5) * 3600f, mars.y + 300f, mars.z + (float) Math.sin(dAng + 2.5) * 3600f);
        Quaternionf drot = new Quaternionf().rotateY(time * 0.03f);
        Vector3f moon = SpaceRenderer.moonPosition(time);
        float heat = PhobosDirector.heat(t);
        float warp = PhobosDirector.warp(t);

        // 1) cielo
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        SpaceRenderer.drawSky(bb, view, eye, right, up, time);
        SpaceRenderer.drawSun(bb, view, eye, right, up, time);
        drawWarp(bb, view, eye, forward, right, up, warp, time);
        BufferUploader.drawWithShader(bb.end());

        // 2) cuerpos sólidos
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        { // la Tierra y su luna
            PlanetMesh.renderSurface(bb, view, Cinematic.PLANET_RADIUS, SUN, eye);
            SpaceRenderer.drawMoon(bb, view, moon);
        }
        {
            MarsMesh.renderMars(bb, view, mars, PhobosDirector.MARS_RADIUS, SUN);
            MarsMesh.renderDeimos(bb, view, deimos, drot, SUN);
        }
        MarsMesh.renderPhobos(bb, view, phobos, rot, SUN);
        drawMissile(bb, view, t);
        BufferUploader.drawWithShader(bb.end());

        // 3) nubes de la Tierra
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        {
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            PlanetMesh.renderClouds(bb, view, Cinematic.PLANET_RADIUS * 1.012f, SUN, time * 0.004f);
            BufferUploader.drawWithShader(bb.end());
        }

        // 4) brillos
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        {
            PlanetMesh.renderAtmosphere(bb, view, Cinematic.PLANET_RADIUS * 1.035f, SUN, eye);
            PlanetMesh.renderCityLights(bb, view, Cinematic.PLANET_RADIUS, SUN, time);
            drawEarthTarget(bb, view, eye, right, up, time, t);
        }
        {
            MarsMesh.renderMarsAtmosphere(bb, view, mars, PhobosDirector.MARS_RADIUS * 1.02f, SUN, eye);
        }
        MarsMesh.renderPhobosHeat(bb, view, phobos, rot, motion, heat, time);
        drawMissileGlow(bb, view, eye, right, up, t, time);
        drawMissileImpact(bb, view, eye, right, up, t, time);
        drawSlingTrail(bb, view, right, up, t, heat);
        drawReentry(bb, view, eye, right, up, phobos, motion, t, heat, time);
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

    /** Estrellas estiradas y polvo que pasa volando a gran velocidad. */
    private static void drawWarp(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f forward, Vector3f right, Vector3f up,
                                 float warp, float time) {
        if (warp <= 0.01f) return;
        for (int i = 0; i < 520; i++) {
            float ang = rnd(i * 3) * 6.2832f;
            float radius = 40f + rnd(i * 3 + 1) * 900f;
            float speed = 2500f + rnd(i * 3 + 2) * 3500f;
            float z = 3000f - ((time * speed * warp + rnd(i * 5) * 6000f) % 6000f);
            Vector3f p = new Vector3f(eye).add(new Vector3f(forward).mul(z))
                    .add(new Vector3f(right).mul((float) Math.cos(ang) * radius))
                    .add(new Vector3f(up).mul((float) Math.sin(ang) * radius));
            float len = 60f + 520f * warp;
            Vector3f side = new Vector3f(forward).cross(new Vector3f(p).sub(eye)).normalize();
            float tint = rnd(i * 7);
            RenderUtil.softBeam(bb, view, p.x, p.y, p.z, forward, len, side, 2.2f + radius * 0.004f,
                    0.7f + 0.3f * tint, 0.8f + 0.2f * tint, 1f, 0.55f * warp);
        }
    }

    private static void drawEarthTarget(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float time, float t) {
        if (t > PhobosTimeline.SPACE_END) return;
        Vec3 tp = PhobosDirector.EARTH_TARGET;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 6);
        float size = Math.max(3f, eye.distance((float) tp.x, (float) tp.y, (float) tp.z) * 0.006f);
        RenderUtil.softDisc(bb, view, (float) tp.x, (float) tp.y + 1, (float) tp.z, right, up, size * 3f, 14, 1f, 0.15f, 0.1f, pulse, 0f);
    }

    // ------------------------------------------------------------------ misil

    private static void drawMissile(BufferBuilder bb, Matrix4f view, float t) {
        float u = PhobosDirector.missileProgress(t);
        if (u < 0f || u >= 1f) return;
        Vec3 p = PhobosDirector.missilePosition(u);
        Vec3 ahead = PhobosDirector.missilePosition(Math.min(1f, u + 0.01f));
        Vector3f dir = v(ahead.subtract(p)).normalize();
        Matrix4f m = new Matrix4f(view).translate((float) p.x, (float) p.y, (float) p.z)
                .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        // cuerpo blanco, punta roja, bandas negras y aletas
        RenderUtil.cylinder(bb, m, 2.4f, -16f, 12f, 14, 0.9f, 0.9f, 0.92f, 1f);
        RenderUtil.cylinder(bb, m, 1.6f, 12f, 17f, 14, 0.85f, 0.15f, 0.12f, 1f);
        RenderUtil.cylinder(bb, m, 0.7f, 17f, 20f, 10, 0.85f, 0.15f, 0.12f, 1f);
        RenderUtil.cylinder(bb, m, 2.5f, 4f, 5.5f, 14, 0.1f, 0.1f, 0.12f, 1f);
        RenderUtil.cylinder(bb, m, 2.5f, -8f, -6.5f, 14, 0.1f, 0.1f, 0.12f, 1f);
        for (int i = 0; i < 4; i++) {
            Matrix4f fin = new Matrix4f(m).rotateY(i * (float) Math.PI / 2);
            RenderUtil.box(bb, fin, 2f, -16f, -0.25f, 6f, -10f, 0.25f, 0.3f, 0.3f, 0.34f, 1f);
        }
        RenderUtil.cylinder(bb, m, 2f, -18f, -16f, 12, 0.2f, 0.2f, 0.22f, 1f);
    }

    private static void drawMissileGlow(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time) {
        float u = PhobosDirector.missileProgress(t);
        if (u < 0f || u >= 1f) return;
        Vec3 p = PhobosDirector.missilePosition(u);
        Vec3 ahead = PhobosDirector.missilePosition(Math.min(1f, u + 0.01f));
        Vector3f dir = v(ahead.subtract(p)).normalize();
        Vector3f tail = v(p).sub(new Vector3f(dir).mul(20f));
        float flick = 0.85f + 0.15f * (float) Math.sin(time * 60);
        RenderUtil.softDisc(bb, view, tail.x, tail.y, tail.z, right, up, 18f * flick, 12, 1f, 0.7f, 0.3f, 0.9f, 0f);
        RenderUtil.softDisc(bb, view, tail.x, tail.y, tail.z, right, up, 7f, 12, 1f, 1f, 0.9f, 1f, 0f);
        // estela de humo y fuego detrás del misil
        for (int i = 1; i <= 60; i++) {
            float uu = u - i * 0.004f;
            if (uu < 0f) break;
            Vec3 q = PhobosDirector.missilePosition(uu);
            float fade = 1f - i / 60f;
            float jitter = (rnd(i) - 0.5f) * 6f;
            RenderUtil.softDisc(bb, view, (float) q.x + jitter, (float) q.y, (float) q.z - jitter, right, up,
                    6f + i * 0.6f, 8, 1f, 0.55f + 0.3f * fade, 0.25f, 0.35f * fade, 0f);
            RenderUtil.softDisc(bb, view, (float) q.x, (float) q.y + jitter, (float) q.z, right, up,
                    10f + i * 1.2f, 8, 0.6f, 0.6f, 0.65f, 0.08f * fade, 0f);
        }
    }

    /** Explosión del misil en Fobos: bola de fuego, anillo de choque y escombros. */
    private static void drawMissileImpact(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up, float t, float time) {
        float since = (t - PhobosTimeline.MISSILE_HIT) * Cinematic.durationSeconds();
        if (since < 0f || since > 7f) return;
        Vector3f hit = v(PhobosDirector.missileTarget());
        // la explosión se queda pegada a Fobos mientras empieza a moverse
        Vector3f follow = v(PhobosDirector.phobosPosition(t)).sub(v(PhobosDirector.phobosPosition(PhobosTimeline.MISSILE_HIT)));
        hit.add(follow);
        float f = Mth.clamp(since / 3f, 0f, 1f);
        float fade = 1f - Mth.clamp((since - 1f) / 6f, 0f, 1f);
        RenderUtil.softDisc(bb, view, hit.x, hit.y, hit.z, right, up, 40f + 260f * f, 24, 1f, 0.6f, 0.2f, 0.9f * fade, 0f);
        RenderUtil.softDisc(bb, view, hit.x, hit.y, hit.z, right, up, 20f + 120f * f, 24, 1f, 0.9f, 0.6f, fade, 0f);
        if (since < 0.4f) RenderUtil.softDisc(bb, view, hit.x, hit.y, hit.z, right, up, 500f, 24, 1f, 1f, 1f, 1f - since / 0.4f, 0f);
        Matrix4f ring = new Matrix4f(view).translate(hit.x, hit.y, hit.z).rotateX(1.1f);
        float rr = 30f + 420f * Cinematic.easeOutCubic(f);
        RenderUtil.ring(bb, ring, rr, rr + 14f, 0, 72, 1f, 0.8f, 0.5f, 0.6f * fade);
        for (int i = 0; i < 260; i++) {
            Vector3f d = new Vector3f(rnd(i * 3 + 100) - 0.5f, rnd(i * 3 + 101) - 0.5f, rnd(i * 3 + 102) - 0.5f).normalize();
            float dist = since * (30f + 110f * rnd(i + 900));
            Vector3f p = new Vector3f(hit).add(d.mul(dist));
            boolean rock = i % 3 == 0;
            RenderUtil.softDisc(bb, view, p.x, p.y, p.z, right, up, rock ? 2.5f : 1.8f, 6,
                    rock ? 0.55f : 1f, rock ? 0.45f : 0.7f, rock ? 0.4f : 0.3f, (rock ? 0.6f : 0.9f) * fade, 0f);
        }
    }

    /** Estela incandescente de Fobos mientras gira alrededor de Marte. */
    private static void drawSlingTrail(BufferBuilder bb, Matrix4f view, Vector3f right, Vector3f up, float t, float heat) {
        if (t < PhobosTimeline.MISSILE_END || t > PhobosTimeline.SPACE_END) return;
        float u = Math.max(0f, PhobosDirector.slingProgress(t));
        int count = 70;
        float span = 0.004f + 0.02f * u;
        for (int i = 1; i <= count; i++) {
            float tt = t - span * i / count;
            if (tt < PhobosTimeline.MISSILE_END) break;
            Vec3 q = PhobosDirector.phobosPosition(tt);
            float fade = 1f - i / (float) count;
            RenderUtil.softDisc(bb, view, (float) q.x, (float) q.y, (float) q.z, right, up, 40f + 30f * heat, 10,
                    1f, 0.45f + 0.3f * fade, 0.15f, 0.22f * fade * (0.3f + heat), 0f);
        }
        // escombros que giran junto a Fobos
        Vec3 p = PhobosDirector.phobosPosition(t);
        for (int i = 0; i < 120; i++) {
            float a = rnd(i + 3000) * 6.2832f + t * 400f * (0.5f + rnd(i + 3100));
            float r = 90f + rnd(i + 3200) * 160f;
            float y = (rnd(i + 3300) - 0.5f) * 60f;
            RenderUtil.softDisc(bb, view, (float) p.x + (float) Math.cos(a) * r, (float) p.y + y, (float) p.z + (float) Math.sin(a) * r,
                    right, up, 2.4f, 6, 0.75f, 0.6f, 0.5f, 0.55f, 0f);
        }
    }

    /** Plasma y cola de fuego cuando Fobos va a toda velocidad hacia la Tierra. */
    private static void drawReentry(BufferBuilder bb, Matrix4f view, Vector3f eye, Vector3f right, Vector3f up,
                                    Vector3f p, Vector3f motion, float t, float heat, float time) {
        if (t < PhobosTimeline.SLING_END) return;
        float u = PhobosDirector.releaseProgress(t);
        float fire = 0.35f + 0.65f * Cinematic.smooth((u - 0.5f) / 0.5f);
        float flick = 0.85f + 0.15f * (float) Math.sin(time * 40);
        Vector3f front = new Vector3f(p).add(new Vector3f(motion).mul(55f));
        RenderUtil.softDisc(bb, view, front.x, front.y, front.z, right, up, 120f * flick, 20, 1f, 0.7f, 0.35f, 0.8f * fire, 0f);
        RenderUtil.softDisc(bb, view, front.x, front.y, front.z, right, up, 60f, 20, 1f, 0.95f, 0.8f, 0.9f * fire, 0f);
        // cola larga de fuego
        Vector3f side = new Vector3f(motion).cross(new Vector3f(eye).sub(p)).normalize();
        float tail = 900f + 2600f * fire;
        Vector3f mid = new Vector3f(p).sub(new Vector3f(motion).mul(tail * 0.5f));
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, motion, tail * 0.5f, side, 70f * fire, 1f, 0.5f, 0.15f, 0.5f * fire);
        RenderUtil.softBeam(bb, view, mid.x, mid.y, mid.z, motion, tail * 0.45f, side, 30f * fire, 1f, 0.85f, 0.5f, 0.7f * fire);
        for (int i = 0; i < 90; i++) {
            float back = rnd(i + 5000) * tail;
            float off = (rnd(i + 5100) - 0.5f) * 120f * fire;
            Vector3f q = new Vector3f(p).sub(new Vector3f(motion).mul(back)).add(new Vector3f(side).mul(off));
            RenderUtil.softDisc(bb, view, q.x, q.y, q.z, right, up, 8f + 14f * rnd(i + 5200), 6, 1f, 0.6f, 0.2f,
                    (1f - back / tail) * 0.7f * fire, 0f);
        }
    }
}
