package com.arsenalatomico.client;

import com.arsenalatomico.CondorPlan;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lo que se dibuja del silo del Cóndor (el pozo de concreto lo pone el servidor con bloques):
 * las compuertas que se corren con franjas de peligro, cuatro balizas rojas que giran, los ductos de
 * escape, el misil esperando en el fondo con sus brazos de servicio y las luces del pozo que se van
 * prendiendo.
 */
public final class SiloRenderer {
    private static CondorPlan plan;
    private static long until;

    private SiloRenderer() {}

    static void show(CondorPlan p, long start, int duration) {
        plan = p;
        until = start + duration + 1200;
    }

    /** Compuertas: 0 cerradas, 1 abiertas. */
    static float doors(float s) {
        return Cinematic.smooth((s - 48) / 12) - Cinematic.smooth((s - 194) / 8);
    }

    /** Brazos de servicio: 0 pegados al misil, 1 recogidos contra la pared. */
    static float arms(float s) {
        return Cinematic.smooth((s - 80) / 8);
    }

    static boolean alarm(float s) {
        return s >= 44 && s < 112;
    }

    public static void render(PoseStack ps, Camera cam, float partial) {
        if (plan == null || Cinematic.clientTicks() > until) return;
        Vec3 c = cam.getPosition();
        Vec3 site = plan.site;
        if (c.distanceToSqr(site) > 500 * 500) return;
        boolean active = Cinematic.isActive() && Cinematic.condor == plan;
        float s = active ? Cinematic.seconds(partial) : 999;
        float time = Cinematic.clientTicks() + partial;
        float open = active ? doors(s) : 0;
        float f = plan.yaw;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // ---- piezas sólidas
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        ps.pushPose();
        ps.translate(site.x - c.x, site.y - c.y, site.z - c.z);
        ps.mulPose(Axis.YP.rotationDegrees(-f));
        Matrix4f m = ps.last().pose();
        // compuertas (dos mitades que se corren a los lados)
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 0f : -4.4f, x1 = side > 0 ? 4.4f : 0f;
            float off = side * open * 4.7f;
            Shapes.box(bb, m, x0 + off, 0.02f, -4.4f, x1 + off, 0.5f, 4.4f, 0.3f, 0.32f, 0.34f);
            // franjas de peligro en el borde que se junta
            float edge = side > 0 ? x0 + off : x1 + off;
            for (int k = 0; k < 11; k++) {
                float z0 = -4.4f + k * 0.8f;
                boolean yellow = k % 2 == 0;
                float ex0 = side > 0 ? edge : edge - 0.6f, ex1 = side > 0 ? edge + 0.6f : edge;
                Shapes.box(bb, m, ex0, 0.5f, z0, ex1, 0.53f, z0 + 0.8f, yellow ? 0.95f : 0.06f, yellow ? 0.78f : 0.06f, yellow ? 0.1f : 0.06f);
            }
            // remaches
            for (int k = 0; k < 4; k++) {
                float cx = side * (1.1f + k * 0.8f) + off, cz = (k % 2 == 0 ? 1 : -1) * 2.6f;
                Shapes.box(bb, m, cx - 0.15f, 0.5f, cz - 0.15f, cx + 0.15f, 0.58f, cz + 0.15f, 0.2f, 0.21f, 0.22f);
            }
        }
        // balizas en el borde
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            float lx = (float) Math.cos(a) * 6.6f, lz = (float) Math.sin(a) * 6.6f;
            Shapes.box(bb, m, lx - 0.15f, 0f, lz - 0.15f, lx + 0.15f, 1.6f, lz + 0.15f, 0.25f, 0.25f, 0.27f);
            boolean on = active && alarm(s) && ((int) (time / 3) + i) % 2 == 0;
            Shapes.box(bb, m, lx - 0.3f, 1.6f, lz - 0.3f, lx + 0.3f, 2.1f, lz + 0.3f, on ? 1f : 0.4f, on ? 0.12f : 0.05f, on ? 0.08f : 0.05f);
        }
        // ductos de escape a los lados
        for (int side = -1; side <= 1; side += 2) {
            float vx = side * 8.2f;
            Shapes.box(bb, m, vx - 1.3f, 0f, -1.3f, vx + 1.3f, 1.4f, 1.3f, 0.34f, 0.35f, 0.36f);
            Shapes.box(bb, m, vx - 0.9f, 1.4f, -0.9f, vx + 0.9f, 1.42f, 0.9f, 0.03f, 0.03f, 0.03f);
        }
        ps.popPose();

        // misil esperando en el fondo, con sus brazos de servicio
        if (active && s * 20 < plan.launchTick) {
            Vec3 st = CondorPlan.start(site);
            ps.pushPose();
            ps.translate(st.x - c.x, st.y - c.y, st.z - c.z);
            ps.mulPose(Axis.YP.rotationDegrees(-f));
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            CondorModel.stage1(bb, ps.last().pose());
            CondorModel.upper(bb, ps.last().pose());
            ps.popPose();
            Matrix4f mm = ps.last().pose();
            float in = CondorModel.R + (float) (CondorPlan.SHAFT_R - 0.2 - CondorModel.R) * arms(s);
            for (float y : new float[]{-6f, 1f, 8f}) {
                for (int side = -1; side <= 1; side += 2) {
                    float a0 = side * in, a1 = side * (float) CondorPlan.SHAFT_R;
                    Shapes.box(bb, mm, Math.min(a0, a1), y - 0.25f, -0.3f, Math.max(a0, a1), y + 0.25f, 0.3f, 0.75f, 0.6f, 0.12f);
                    Shapes.box(bb, mm, Math.min(a0, a1) + (side > 0 ? 0 : 0.0f), y - 0.4f, -0.45f,
                            Math.min(a0, a1) + 0.3f, y + 0.4f, 0.45f, 0.2f, 0.2f, 0.22f);
                }
            }
            ps.popPose();
        }
        BufferUploader.drawWithShader(bb.end());

        // ---- luces (se suman)
        if (active) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(false);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            ps.pushPose();
            ps.translate(site.x - c.x, site.y - c.y, site.z - c.z);
            Matrix4f g = ps.last().pose();
            Vector3f up = new Vector3f(0, 1, 0);
            Vector3f camRight = new Vector3f(cam.getLeftVector()).negate(), camUp = new Vector3f(cam.getUpVector());
            float yawRad = (float) Math.toRadians(f);   // mismo giro que YP(-yaw) en las piezas sólidas
            if (alarm(s)) {
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 4 + i * Math.PI / 2 + yawRad;
                    float lx = (float) Math.cos(a) * 6.6f, lz = (float) Math.sin(a) * 6.6f;
                    // haz que gira
                    double rot = time * 0.22 + i * 1.3;
                    Vector3f dir = new Vector3f((float) Math.cos(rot), 0, (float) Math.sin(rot));
                    RenderUtil.softBeam(bb, g, lx + dir.x * 6f, 1.85f, lz + dir.z * 6f, dir, 6f, up, 0.9f, 1f, 0.1f, 0.05f, 0.35f);
                    RenderUtil.softDisc(bb, g, lx, 1.85f, lz, camRight, camUp, 1.4f, 12, 1f, 0.15f, 0.08f, 0.7f, 0f);
                }
                // luz roja de emergencia que late dentro del pozo
                float pulse = 0.5f + 0.5f * Mth.sin(time * 0.3f);
                ps.pushPose();
                ps.translate(0, -CondorPlan.DEPTH, 0);
                RenderUtil.cylinder(bb, ps.last().pose(), (float) CondorPlan.SHAFT_R - 0.05f, 0, (float) CondorPlan.DEPTH, 24, 1f, 0.1f, 0.05f, 0.08f * pulse);
                ps.popPose();
            }
            // anillos de luz del pozo que se prenden de arriba hacia abajo cuando baja la cámara
            for (int i = 0; i < 6; i++) {
                float y = (float) (3 + 6 * i - CondorPlan.DEPTH);
                float on = Cinematic.smooth((s - (60 + (5 - i) * 2.9f)) / 0.5f);
                if (on <= 0.01f) continue;
                ps.pushPose();
                ps.translate(0, y - 0.6f, 0);
                RenderUtil.cylinder(bb, ps.last().pose(), (float) CondorPlan.SHAFT_R - 0.04f, 0, 1.2f, 32, 0.75f, 0.9f, 1f, 0.45f * on);
                RenderUtil.cylinder(bb, ps.last().pose(), (float) CondorPlan.SHAFT_R - 0.05f, -1.5f, 2.7f, 32, 0.6f, 0.75f, 1f, 0.12f * on);
                ps.popPose();
            }
            // fuego en el fondo y en los ductos al encender
            float ig = s - CondorPlan.LAUNCH_S;
            if (ig > -0.5f && ig < 9f) {
                float k = Mth.clamp(ig + 0.5f, 0, 1) * Mth.clamp((9f - ig) / 4f, 0, 1);
                ps.pushPose();
                ps.translate(0, -CondorPlan.DEPTH, 0);
                RenderUtil.cylinder(bb, ps.last().pose(), (float) CondorPlan.SHAFT_R - 0.1f, 0, (float) CondorPlan.DEPTH, 24, 1f, 0.55f, 0.15f, 0.5f * k);
                ps.popPose();
                for (int side = -1; side <= 1; side += 2) {
                    float vx = (float) Math.cos(yawRad) * side * 8.2f, vz = (float) Math.sin(yawRad) * side * 8.2f;
                    RenderUtil.softDisc(bb, g, vx, 3f, vz, camRight, camUp, 5f * k, 16, 1f, 0.6f, 0.2f, 0.75f * k, 0f);
                }
                RenderUtil.softDisc(bb, g, 0, 2f, 0, camRight, camUp, 12f * k, 20, 1f, 0.7f, 0.3f, 0.6f * k, 0f);
            }
            ps.popPose();
            BufferUploader.drawWithShader(bb.end());
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        RenderSystem.enableCull();
    }
}
