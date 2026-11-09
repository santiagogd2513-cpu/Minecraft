package com.arsenalatomico.client;

import com.arsenalatomico.TwinPlan;
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

/**
 * Las máquinas del Cóndor Doble, dibujadas en su lugar del mundo: dos lanzadoras móviles de 8 ejes
 * (con su banderita de Rusia en la cabina) o un submarino lanzamisiles (con la suya en la torre).
 */
public final class TwinRenderer {
    private static TwinPlan plan;
    private static long until;

    private TwinRenderer() {}

    static void show(TwinPlan p, long start, int duration) {
        plan = p;
        until = start + duration + 1200;
    }

    public static void render(PoseStack ps, Camera cam, float partial) {
        if (plan == null || Cinematic.clientTicks() > until) return;
        Vec3 c = cam.getPosition();
        if (c.distanceToSqr(plan.site) > 400 * 400) return;
        boolean active = Cinematic.isActive() && Cinematic.twin == plan;
        float s = active ? Cinematic.seconds(partial) : Cinematic.totalSeconds();
        float time = Cinematic.clientTicks() + partial;
        if (plan.mode == TwinPlan.SEA && !active) return;   // ya se sumergió

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (plan.mode == TwinPlan.SEA) {
            float rise = TwinScenes.rise(s);
            Vec3 at = plan.site.add(0, Mth.lerp(rise, -14, -TwinPlan.HULL_DOWN), 0);
            ps.pushPose();
            ps.translate(at.x - c.x, at.y - c.y, at.z - c.z);
            ps.mulPose(Axis.YP.rotationDegrees(-plan.yaw));
            submarine(bb, ps, TwinScenes.hatches(s), time);
            ps.popPose();
        } else {
            float erect = active ? TwinScenes.erect(s) : 0, legs = active ? TwinScenes.legs(s) : 0;
            for (int i = 0; i < 2; i++) {
                Vec3 at = TwinPlan.truck(plan.site, plan.yaw, i);
                ps.pushPose();
                ps.translate(at.x - c.x, at.y - c.y, at.z - c.z);
                ps.mulPose(Axis.YP.rotationDegrees(-plan.yaw));
                truck(bb, ps, erect, legs, active && TwinScenes.cap(i, s), i == 0, time);
                ps.popPose();
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ lanzadora de 8 ejes

    private static final float GR = 0.24f, GG = 0.30f, GB = 0.20f;   // verde militar

    private static void truck(BufferBuilder bb, PoseStack ps, float erect, float legs, boolean cap, boolean first, float time) {
        Matrix4f m = ps.last().pose();
        // 8 ejes: 4 adelante y 4 atrás
        for (float z : new float[]{10.4f, 8.6f, 6.8f, 5.0f, -2.8f, -4.6f, -6.4f, -8.2f}) {
            for (int sd = -1; sd <= 1; sd += 2) {
                Shapes.box(bb, m, sd > 0 ? 1.45f : -2.0f, 0f, z - 0.75f, sd > 0 ? 2.0f : -1.45f, 1.5f, z + 0.75f, 0.07f, 0.07f, 0.08f);
                Shapes.box(bb, m, sd > 0 ? 2.0f : -2.03f, 0.5f, z - 0.3f, sd > 0 ? 2.03f : -2.0f, 1.0f, z + 0.3f, 0.35f, 0.35f, 0.3f);
            }
        }
        // chasis, guardabarros y cabina
        Shapes.box(bb, m, -1.6f, 0.9f, -11.5f, 1.6f, 1.8f, 12.3f, GR * 0.8f, GG * 0.8f, GB * 0.8f);
        Shapes.box(bb, m, -2.05f, 1.5f, 4.1f, 2.05f, 1.75f, 11.3f, GR * 0.9f, GG * 0.9f, GB * 0.9f);
        Shapes.box(bb, m, -2.05f, 1.5f, -9.1f, 2.05f, 1.75f, -2.0f, GR * 0.9f, GG * 0.9f, GB * 0.9f);
        Shapes.box(bb, m, -1.75f, 1.8f, 9.6f, 1.75f, 4.3f, 12.5f, GR, GG, GB);
        Shapes.box(bb, m, -1.5f, 3.0f, 12.5f, 1.5f, 4.0f, 12.56f, 0.10f, 0.17f, 0.25f);
        Shapes.box(bb, m, 1.75f, 3.1f, 10.4f, 1.79f, 4.0f, 12.1f, 0.10f, 0.17f, 0.25f);
        Shapes.box(bb, m, -1.79f, 3.1f, 10.4f, -1.75f, 4.0f, 12.1f, 0.10f, 0.17f, 0.25f);
        Shapes.box(bb, m, -1.2f, 1.2f, 12.3f, 1.2f, 1.7f, 12.6f, 0.12f, 0.12f, 0.12f);          // parachoques
        Shapes.box(bb, m, -1.6f, 1.8f, 3.6f, 1.6f, 3.1f, 9.5f, GR * 0.9f, GG * 0.9f, GB * 0.9f); // equipos
        Shapes.box(bb, m, -1.0f, 3.1f, 4.2f, 1.0f, 3.5f, 8.8f, GR * 0.75f, GG * 0.75f, GB * 0.75f);
        // gatos hidráulicos: salen a los lados y bajan hasta el suelo
        float reach = 0.4f + 0.5f * Math.min(1, legs * 2), foot = 0.85f * (1 - Math.max(0, legs * 2 - 1));
        for (float z : new float[]{-10.5f, 3.2f}) {
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = sd * (1.6f + reach);
                Shapes.box(bb, m, Math.min(sd * 1.5f, x), 1.0f, z - 0.15f, Math.max(sd * 1.5f, x), 1.25f, z + 0.15f, 0.25f, 0.27f, 0.2f);
                Shapes.box(bb, m, x - 0.12f, foot + 0.1f, z - 0.12f, x + 0.12f, 1.25f, z + 0.12f, 0.4f, 0.4f, 0.38f);
                Shapes.box(bb, m, x - 0.35f, foot, z - 0.35f, x + 0.35f, foot + 0.12f, z + 0.35f, 0.18f, 0.18f, 0.18f);
            }
        }
        // banderita de Rusia en la cabina (blanco, azul, rojo), moviéndose con el viento
        if (first) flag(bb, m, 1.29f, 4.3f, 9.7f, time);
        else flag(bb, m, 1.29f, 4.3f, 9.7f, time + 13);

        // brazo y contenedor del misil, que se levanta hasta quedar de pie
        ps.pushPose();
        ps.translate(0, TwinPlan.PIVOT_UP, -TwinPlan.PIVOT_BACK);
        ps.mulPose(Axis.XP.rotationDegrees(-erect));
        Matrix4f a = ps.last().pose();
        float len = (float) TwinPlan.CAN_LEN, off = (float) TwinPlan.CAN_OFF;
        Shapes.box(bb, a, -0.5f, -0.4f, -0.4f, 0.5f, 0.2f, len - 3, 0.3f, 0.32f, 0.3f);                // brazo
        Shapes.box(bb, a, -1.3f, -0.6f, -0.6f, 1.3f, 0.3f, 0.6f, 0.2f, 0.2f, 0.2f);                       // bisagra
        ps.translate(0, off, 0);
        Matrix4f t = ps.last().pose();
        Shapes.tube(bb, t, 1.15f, 1.15f, 0, len, 16, GR * 1.1f, GG * 1.1f, GB * 1.1f, 1);
        Shapes.tube(bb, t, 1.3f, 1.3f, 0, 0.5f, 16, GR * 0.6f, GG * 0.6f, GB * 0.6f, 1);
        Shapes.tube(bb, t, 1.25f, 1.25f, len * 0.5f - 0.2f, len * 0.5f + 0.2f, 16, GR * 0.6f, GG * 0.6f, GB * 0.6f, 1);
        Shapes.tube(bb, t, 1.25f, 1.25f, len - 0.4f, len, 16, GR * 0.6f, GG * 0.6f, GB * 0.6f, 1);
        if (cap) {
            Shapes.tube(bb, t, 1.2f, 0.9f, len, len + 0.5f, 16, 0.16f, 0.17f, 0.15f, 1);
            Shapes.tube(bb, t, 0.9f, 0f, len + 0.5f, len + 0.7f, 16, 0.16f, 0.17f, 0.15f, 1);
        }
        ps.popPose();
    }

    /** Bandera chiquita en un mástil: 0.8 de largo y 0.6 de alto, con tres franjas. */
    private static void flag(BufferBuilder bb, Matrix4f m, float x, float y0, float z, float time) {
        Shapes.box(bb, m, x - 0.035f, y0, z - 0.035f, x + 0.035f, y0 + 1.6f, z + 0.035f, 0.55f, 0.55f, 0.55f);
        float top = y0 + 1.5f;
        float[][] stripes = {{1f, 1f, 1f}, {0f, 0.22f, 0.65f}, {0.84f, 0.17f, 0.12f}};
        for (int k = 0; k < 4; k++) {
            float za = z - k * 0.2f, zb = z - (k + 1) * 0.2f;
            float wave = Mth.sin(time * 0.35f - k * 1.1f) * 0.05f * (k + 1) / 4f;
            for (int st = 0; st < 3; st++) {
                float ya = top - st * 0.2f - 0.2f, yb = top - st * 0.2f;
                Shapes.box(bb, m, x + wave - 0.012f, ya, zb, x + wave + 0.012f, yb, za, stripes[st][0], stripes[st][1], stripes[st][2]);
            }
        }
    }

    // ------------------------------------------------------------------ submarino

    private static void submarine(BufferBuilder bb, PoseStack ps, float open, float time) {
        Matrix4f m = ps.last().pose();
        float hr = 0.11f, hg = 0.12f, hb = 0.14f;
        Shapes.tube(bb, m, 3.5f, 3.5f, -22, 18, 20, hr, hg, hb, 1);
        Shapes.tube(bb, m, 3.5f, 2.6f, 18, 22, 20, hr, hg, hb, 1);
        Shapes.tube(bb, m, 2.6f, 0f, 22, 25, 20, hr, hg, hb, 1);
        Shapes.tube(bb, m, 3.5f, 1.2f, -22, -34, 20, hr, hg, hb, 1);
        Shapes.tube(bb, m, 1.2f, 0f, -34, -35, 20, hr, hg, hb, 1);
        Shapes.box(bb, m, -0.15f, -4.2f, -33.5f, 0.15f, 4.2f, -30, hr, hg, hb);                 // timón
        Shapes.box(bb, m, -4.2f, -0.15f, -33.5f, 4.2f, 0.15f, -30, hr, hg, hb);
        Shapes.box(bb, m, -2.1f, 3.0f, -14, 2.1f, 3.8f, 0, hr * 1.2f, hg * 1.2f, hb * 1.2f);   // cubierta de los tubos
        // torre, timones de la torre y periscopio
        Shapes.box(bb, m, -0.9f, 3.0f, 6.5f, 0.9f, 9.5f, 12.5f, hr, hg, hb);
        Shapes.box(bb, m, -3.4f, 7.4f, 8.8f, 3.4f, 7.7f, 10.2f, hr, hg, hb);
        Shapes.box(bb, m, -0.1f, 9.5f, 9.6f, 0.1f, 11f, 9.8f, 0.3f, 0.3f, 0.3f);
        flag(bb, m, 0f, 9.5f, 7.0f, time);
        // compuertas: 8 tubos; se abren los dos que disparan
        float[] zs = {-2.5f, -5.5f, -8.5f, -11.5f};
        for (float z : zs) {
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = sd * 1.0f;
                boolean firing = (sd > 0 && z == (float) TwinPlan.HATCH[0][1]) || (sd < 0 && z == (float) TwinPlan.HATCH[1][1]);
                if (!firing || open < 0.01f) {
                    Shapes.box(bb, m, x - 0.7f, 3.8f, z - 0.7f, x + 0.7f, 4.0f, z + 0.7f, 0.2f, 0.21f, 0.23f);
                    continue;
                }
                Shapes.box(bb, m, x - 0.62f, 3.79f, z - 0.62f, x + 0.62f, 3.81f, z + 0.62f, 0.02f, 0.02f, 0.02f);
                ps.pushPose();
                ps.translate(x + sd * 0.7f, 3.9f, z);
                ps.mulPose(Axis.ZP.rotationDegrees(-sd * 110 * open));
                Matrix4f l = ps.last().pose();
                if (sd > 0) Shapes.box(bb, l, -1.4f, -0.1f, -0.7f, 0f, 0.1f, 0.7f, 0.2f, 0.21f, 0.23f);
                else Shapes.box(bb, l, 0f, -0.1f, -0.7f, 1.4f, 0.1f, 0.7f, 0.2f, 0.21f, 0.23f);
                ps.popPose();
            }
        }
    }
}
