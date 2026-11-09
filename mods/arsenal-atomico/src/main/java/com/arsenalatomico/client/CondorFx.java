package com.arsenalatomico.client;

import com.arsenalatomico.CondorPlan;
import com.arsenalatomico.MissilePath;
import com.arsenalatomico.entity.MissileEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que rodea al Cóndor en el cielo: las piezas que se sueltan y caen dando vueltas (la primera
 * etapa y la cofia), la estela de humo blanco que dibuja el arco del vuelo y la raya de fuego que
 * deja la ojiva al entrar a la atmósfera.
 */
public final class CondorFx {
    private static final class Piece {
        double x, y, z, vx, vy, vz;
        final Vector3f axis = new Vector3f();
        final float yaw, pitch;
        final int stage;
        float spin;
        int age;

        Piece(Vec3 p, Vec3 v, Vec3 dir, int stage) {
            x = p.x;
            y = p.y;
            z = p.z;
            vx = v.x;
            vy = v.y;
            vz = v.z;
            this.stage = stage;
            yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            pitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
            axis.set((float) Math.random() - 0.5f, (float) Math.random() - 0.5f, (float) Math.random() - 0.5f).normalize();
        }
    }

    private static final List<Piece> PIECES = new ArrayList<>();

    private CondorFx() {}

    static void separate(MissileEntity m, int stage) {
        MissilePath path = m.path();
        if (path == null) return;
        Vec3 p = path.atTick(m.age()), d = path.direction(m.age());
        Vec3 v = path.atTick(m.age() + 1).subtract(p).scale(0.92);
        Vec3 at = stage == 1 ? p.subtract(d.scale(7.9)) : p;
        PIECES.add(new Piece(at, v, d, stage));
        if (PIECES.size() > 8) PIECES.remove(0);
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (int i = 0; i < 30; i++) {
                level.addAlwaysVisibleParticle(i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.FLAME, true,
                        at.x + (Math.random() - 0.5) * 3, at.y + (Math.random() - 0.5) * 3, at.z + (Math.random() - 0.5) * 3,
                        (Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4);
            }
        }
    }

    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            PIECES.clear();
            return;
        }
        for (Piece p : PIECES) {
            p.age++;
            p.vy -= 0.035;
            p.vx *= 0.995;
            p.vz *= 0.995;
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.spin += 3.5f;
            if (p.age % 2 == 0) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p.x, p.y, p.z, 0, 0.01, 0);
        }
        PIECES.removeIf(p -> p.age > 900 || p.y < level.getMinBuildHeight());
    }

    public static void render(PoseStack ps, Camera cam, float partial) {
        CondorPlan plan = Cinematic.condor;
        boolean trail = Cinematic.isActive() && plan != null;
        if (PIECES.isEmpty() && !trail) return;
        Vec3 c = cam.getPosition();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // piezas que caen
        if (!PIECES.isEmpty()) {
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Piece p : PIECES) {
                ps.pushPose();
                ps.translate(p.x + p.vx * partial - c.x, p.y + p.vy * partial - c.y, p.z + p.vz * partial - c.z);
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-p.yaw));
                ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(p.pitch));
                ps.mulPose(new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(p.spin + partial * 3.5f), p.axis));
                Matrix4f m = ps.last().pose();
                if (p.stage == 1) {
                    ps.translate(0, 0, 7.9f);
                    CondorModel.stage1(bb, ps.last().pose());
                } else {
                    // la cofia vacía (sin la ojiva)
                    Shapes.tube(bb, m, CondorModel.R, CondorModel.R, -2.5f, 6f, 16, 0.9f, 0.91f, 0.9f, 1);
                    Shapes.tube(bb, m, CondorModel.R, 0.78f, 6f, 9.5f, 16, 0.93f, 0.93f, 0.92f, 1);
                }
                ps.popPose();
            }
            BufferUploader.drawWithShader(bb.end());
        }

        if (trail) {
            float s = Cinematic.seconds(partial);
            double now = s * 20 - plan.launchTick;
            if (now > 0) {
                ps.pushPose();
                ps.translate(-c.x, -c.y, -c.z);
                Matrix4f m = ps.last().pose();
                MissilePath path = plan.path;
                RenderSystem.enableBlend();
                RenderSystem.depthMask(false);
                // estela de humo blanco: del silo hasta donde se suelta la ojiva
                RenderSystem.defaultBlendFunc();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                double end = Math.min(now, CondorPlan.SEP2);
                for (double t = 6; t + 6 <= end; t += 6) {
                    float age = (float) (now - t) / 20f;
                    float w = 1.6f + Math.min(14f, age * 0.22f);
                    float a = 0.42f * Mth.clamp(1f - age / 110f, 0f, 1f) * Mth.clamp((float) t / 30f, 0f, 1f);
                    if (t > CondorPlan.SEP1) a *= 0.55f;
                    ribbon(bb, m, c, path.atTick(t), path.atTick(t + 6), w, 0.94f, 0.94f, 0.96f, a);
                }
                BufferUploader.drawWithShader(bb.end());
                // raya de fuego de la reentrada (se apaga rápido detrás de la ojiva)
                if (now > CondorPlan.SEP2) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    double from = Math.max(CondorPlan.SEP2 + 20, now - 160);
                    double to = Math.min(now, path.flightTicks);
                    for (double t = from; t + 2 <= to; t += 2) {
                        float age = (float) (now - t);
                        float a = Mth.clamp(1f - age / 160f, 0f, 1f);
                        ribbon(bb, m, c, path.atTick(t), path.atTick(t + 2), 1.4f + age * 0.04f, 1f, 0.55f, 0.18f, 0.55f * a);
                        ribbon(bb, m, c, path.atTick(t), path.atTick(t + 2), 0.5f, 1f, 0.92f, 0.75f, 0.8f * a * a);
                    }
                    BufferUploader.drawWithShader(bb.end());
                    RenderSystem.defaultBlendFunc();
                }
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                ps.popPose();
            }
        }
        RenderSystem.enableCull();
    }

    /** Franja entre dos puntos que mira a la cámara, más transparente en los bordes. */
    private static void ribbon(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        if (alpha < 0.005f) return;
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-4) return;
        dir = dir.scale(1 / len);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize();
        RenderUtil.softBeam(bb, m, (float) mid.x, (float) mid.y, (float) mid.z,
                new Vector3f((float) dir.x, (float) dir.y, (float) dir.z), (float) len / 2f,
                new Vector3f((float) side.x, (float) side.y, (float) side.z), width, r, g, bl, alpha);
    }
}
