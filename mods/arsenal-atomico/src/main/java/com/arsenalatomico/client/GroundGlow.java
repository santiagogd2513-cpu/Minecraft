package com.arsenalatomico.client;

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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * La luz naranja del fuego de los misiles (y de las explosiones) sobre los bloques de alrededor:
 * se pinta encima de cada bloque, sea pasto, piedra o arena, más fuerte cuanto más cerca está la
 * llama, y parpadea como el fuego.
 */
public final class GroundGlow {
    private static final float R = 1f, G = 0.52f, B = 0.16f;   // naranja de la llama
    private static final List<float[]> LIGHTS = new ArrayList<>();

    private GroundGlow() {}

    public static void render(PoseStack ps, Camera cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        LIGHTS.clear();
        float time = Cinematic.clientTicks() + partial;
        Vec3 c = cam.getPosition();
        // luces de los misiles: {x, y, z, radio, fuerza}
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof MissileEntity m) || m.path() == null) continue;
            float age = m.age() + partial;
            if (age < m.ignite()) continue;
            boolean big = m.kind() == 1;
            Vec3 p = e.getPosition(partial);
            Vec3 d = m.path().direction(m.age());
            Vec3 tail = p.subtract(d.scale((big ? 10.5 : 1.6) * m.scale()));
            if (tail.distanceToSqr(c) > 260 * 260) continue;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tail.x), Mth.floor(tail.z));
            float h = (float) Math.max(0, tail.y - gy);
            float reach = big ? 70f : 22f;
            if (h > reach) continue;
            float near = 1f - h / reach;
            float grow = Math.min(1f, (age - m.ignite()) / 6f);
            float flick = 0.82f + 0.12f * Mth.sin(time * 1.9f + e.getId()) + 0.06f * Mth.sin(time * 5.3f + e.getId() * 3);
            float radius = (big ? 9f : 3.5f) + h * (big ? 0.35f : 0.3f);
            LIGHTS.add(new float[]{(float) tail.x, (float) tail.y, (float) tail.z, radius, near * near * grow * flick * (big ? 1f : 0.75f)});
        }
        ImpactFx.lights(LIGHTS);
        if (LIGHTS.isEmpty()) return;

        ps.pushPose();
        ps.translate(-c.x, -c.y, -c.z);
        Matrix4f mat = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int budget = 9000;   // cuadritos por cuadro, para que no se trabe el celular
        for (float[] l : LIGHTS) {
            float lx = l[0], ly = l[1], lz = l[2], rad = Math.min(l[3], 48f), k = l[4];
            int r = Mth.ceil(rad);
            int cx = Mth.floor(lx), cz = Mth.floor(lz);
            int step = rad > 30 ? 2 : 1;   // las luces muy grandes, en cuadros de 2×2
            for (int dx = -r; dx <= r && budget > 0; dx += step) {
                for (int dz = -r; dz <= r && budget > 0; dz += step) {
                    float dist = (float) Math.sqrt(dx * dx + dz * dz);
                    if (dist > rad) continue;
                    int x = cx + dx, z = cz + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    if (y <= level.getMinBuildHeight()) continue;
                    // un bloque muy por encima de la llama casi no recibe luz en su cara de arriba
                    if (y > ly + 2) continue;
                    float fall = 1f - dist / rad;
                    float a = k * fall * fall * 0.55f;
                    if (a < 0.01f) continue;
                    float top = y + 0.012f;
                    float x0 = x, z0 = z, x1 = x + step, z1 = z + step;
                    RenderUtil.quad(bb, mat, x0, top, z0, x0, top, z1, x1, top, z1, x1, top, z0, R, G, B, a);
                    budget--;
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        ps.popPose();
    }
}
