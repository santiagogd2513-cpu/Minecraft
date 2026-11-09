package com.arsenalatomico.client;

import com.arsenalatomico.Launcher;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * El camión lanzacohetes de la Lluvia de Acero, dibujado en su lugar del mundo durante la
 * cinemática (y un minuto después, mientras se disipa el humo).
 */
public final class LauncherRenderer {
    private static Vec3 site;
    private static float yaw;
    private static long until;

    private LauncherRenderer() {}

    static void show(Vec3 at, float facing, long start, int duration) {
        site = at;
        yaw = facing;
        until = start + duration + 1200;
    }

    public static void render(PoseStack ps, Camera cam, float partial) {
        if (site == null || Cinematic.clientTicks() > until) return;
        Vec3 c = cam.getPosition();
        if (c.distanceToSqr(site) > 320 * 320) return;
        // después de la cinemática el camión queda recogido
        boolean mine = Cinematic.isActive() && Cinematic.type() == com.arsenalatomico.WeaponType.SALVO;
        float now = mine ? Cinematic.seconds(partial) : 0;
        float elev = mine ? Cinematic.rackElevation(now) : 0;
        float legs = mine ? Cinematic.legDeploy(now) : 0;

        ps.pushPose();
        ps.translate(site.x - c.x, site.y - c.y, site.z - c.z);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = ps.last().pose();
        float or = 0.29f, og = 0.34f, ob = 0.2f;   // verde oliva militar
        // ruedas
        for (float z : new float[]{-2.4f, -1.1f, 2.3f}) {
            for (float x : new float[]{-1.25f, 1.25f}) Shapes.box(bb, m, x - 0.28f, 0f, z - 0.55f, x + 0.28f, 1.1f, z + 0.55f, 0.08f, 0.08f, 0.09f);
        }
        // chasis, cabina y vidrio
        Shapes.box(bb, m, -1.3f, 0.6f, -3.4f, 1.3f, 1.3f, 3.5f, or * 0.85f, og * 0.85f, ob * 0.85f);
        Shapes.box(bb, m, -1.25f, 1.3f, 1.9f, 1.25f, 2.9f, 3.5f, or, og, ob);
        Shapes.box(bb, m, -1.05f, 1.95f, 3.5f, 1.05f, 2.7f, 3.53f, 0.12f, 0.2f, 0.28f);
        Shapes.box(bb, m, -0.9f, 2.9f, 2.4f, 0.9f, 3.05f, 3.2f, 0.2f, 0.22f, 0.15f);
        // patas estabilizadoras
        // patas estabilizadoras: salen hacia los lados y bajan hasta el suelo
        float reach = 0.35f + 0.45f * Math.min(1, legs * 2), foot = 0.75f * (1 - Math.max(0, legs * 2 - 1));
        for (float z : new float[]{-3.05f, 1.65f}) {
            for (int side = -1; side <= 1; side += 2) {
                float x = side * (1.25f + reach);
                Shapes.box(bb, m, Math.min(side * 1.2f, x), 0.75f, z - 0.12f, Math.max(side * 1.2f, x), 0.95f, z + 0.12f, 0.25f, 0.27f, 0.2f);
                Shapes.box(bb, m, x - 0.1f, foot + 0.1f, z - 0.1f, x + 0.1f, 0.95f, z + 0.1f, 0.35f, 0.35f, 0.33f);
                Shapes.box(bb, m, x - 0.3f, foot, z - 0.3f, x + 0.3f, foot + 0.1f, z + 0.3f, 0.2f, 0.2f, 0.2f);
            }
        }
        // batería de 12 tubos que se levanta
        ps.pushPose();
        ps.translate(0, Launcher.PIVOT_UP, -Launcher.PIVOT_BACK);
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-elev));
        Matrix4f rm = ps.last().pose();
        float len = (float) Launcher.RACK_LEN;
        Shapes.box(bb, rm, -1.2f, -0.1f, 0f, 1.2f, 1.65f, len, or, og, ob);
        Shapes.box(bb, rm, -1.25f, 0.6f, 0.2f, 1.25f, 0.75f, len - 0.2f, or * 0.6f, og * 0.6f, ob * 0.6f);
        for (int i = 0; i < Launcher.TUBES; i++) {
            float x = -((i % 4) - 1.5f) * 0.55f, y = 0.3f + (i / 4) * 0.5f;
            Shapes.box(bb, rm, x - 0.21f, y - 0.21f, len, x + 0.21f, y + 0.21f, len + 0.04f, 0.05f, 0.05f, 0.05f);
        }
        ps.popPose();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        ps.popPose();
    }
}
