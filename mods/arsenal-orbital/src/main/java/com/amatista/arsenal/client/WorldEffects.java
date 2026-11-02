package com.amatista.arsenal.client;

import com.amatista.arsenal.registry.ModItems;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Efectos en el mundo: el rayo orbital cayendo, la onda expansiva y la mira roja al apuntar. */
public final class WorldEffects {
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final double AIM_RANGE = 160;

    private static final class Beam {
        final BlockPos target;
        final long start;
        final long impact;
        final long end;

        Beam(BlockPos target, long start, long impact) {
            this.target = target;
            this.start = start;
            this.impact = impact;
            this.end = impact + 80;
        }
    }

    public static void addBeam(BlockPos target, int ticksToImpact) {
        long now = Cinematic.clientTicks();
        BEAMS.add(new Beam(target, now, now + Math.max(1, ticksToImpact)));
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = Cinematic.clientTicks();
        Iterator<Beam> it = BEAMS.iterator();
        while (it.hasNext()) {
            Beam b = it.next();
            if (now > b.end) {
                it.remove();
                continue;
            }
            // chispas y humo donde el rayo toca el suelo
            if (now < b.impact) {
                Vec3 c = Vec3.atCenterOf(b.target).add(0, 0.6, 0);
                float u = (now - b.start) / (float) Math.max(1, b.impact - b.start);
                for (int i = 0; i < 2 + (int) (u * 8); i++) {
                    double a = mc.level.random.nextDouble() * Math.PI * 2, r = mc.level.random.nextDouble() * (1.5 + u * 4);
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r,
                            Math.cos(a) * 0.3, 0.2 + u, Math.sin(a) * 0.3);
                }
                if (now % 3 == 0) {
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE, true, c.x, c.y, c.z, 0, 0.15, 0);
                    mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, true, c.x, c.y + 1, c.z, 0, 0.4, 0);
                }
            }
        }
    }

    /** Con el designador en la mano (y el satélite listo) se muestra la mira. */
    private static boolean isAiming(LocalPlayer p) {
        boolean holding = p.getMainHandItem().is(ModItems.ORBITAL_DESIGNATOR.get())
                || p.getOffhandItem().is(ModItems.ORBITAL_DESIGNATOR.get());
        return holding && !p.getCooldowns().isOnCooldown(ModItems.ORBITAL_DESIGNATOR.get());
    }

    public static void render(PoseStack poseStack, Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean aiming = player != null && !Cinematic.isActive() && isAiming(player);
        if (BEAMS.isEmpty() && !aiming) return;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float now = Cinematic.clientTicks() - 1 + partialTick;
        for (Beam b : BEAMS) drawBeam(bb, m, b, now);
        if (aiming) drawAim(bb, m, player, partialTick, now);

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    private static void drawBeam(BufferBuilder bb, Matrix4f m, Beam b, float now) {
        float x = b.target.getX() + 0.5f, y = b.target.getY() + 1f, z = b.target.getZ() + 0.5f;
        float sinceStart = now - b.start;
        float toImpact = b.impact - now;
        float fadeIn = Mth.clamp(sinceStart / 8f, 0f, 1f);
        float fadeOut = now > b.impact ? Mth.clamp(1f - (now - b.impact) / 50f, 0f, 1f) : 1f;
        float a = fadeIn * fadeOut;
        if (a > 0f) {
            // el rayo se va haciendo más grueso a medida que se acerca el impacto
            float grow = now < b.impact ? Mth.clamp(1f - toImpact / Math.max(1f, b.impact - b.start), 0f, 1f) : 1f;
            float flick = 1f + 0.12f * (float) Math.sin(now * 2.7f);
            float top = 700f;
            Matrix4f mm = new Matrix4f(m).translate(x, y, z);
            RenderUtil.cylinder(bb, mm, (7f + 5f * grow) * flick, 0, top, 20, 0.15f, 0.3f, 1f, 0.12f * a);
            RenderUtil.cylinder(bb, mm, (2.6f + 2f * grow) * flick, 0, top, 16, 0.3f, 0.7f, 1f, 0.45f * a);
            RenderUtil.cylinder(bb, mm, (0.9f + 0.6f * grow), 0, top, 12, 0.9f, 0.97f, 1f, 0.95f * a);
            // resplandor en el suelo
            RenderUtil.ring(bb, mm, 0f, 4f + 10f * grow, 0.05f, 32, 0.6f, 0.85f, 1f, 0.45f * a);
            RenderUtil.ring(bb, mm, 4f + 10f * grow, 9f + 16f * grow, 0.05f, 32, 0.2f, 0.4f, 1f, 0.15f * a);
            // anillos que bajan por el rayo
            for (int i = 0; i < 5; i++) {
                float h = top - ((now * 6f + i * 140f) % top);
                RenderUtil.ring(bb, mm, 3.2f + 2 * grow, 4.4f + 2 * grow, h, 24, 0.5f, 0.8f, 1f, 0.35f * a);
            }
        }
        // onda expansiva tras el impacto
        if (now >= b.impact && now < b.impact + 45) {
            float u = (now - b.impact) / 45f;
            float radius = 4f + 90f * Cinematic.easeOutCubic(u);
            float alpha = (1f - u) * 0.8f;
            Matrix4f mm = new Matrix4f(m).translate(x, y, z);
            RenderUtil.ring(bb, mm, radius, radius + 3.5f, 0.3f, 72, 1f, 0.85f, 0.6f, alpha);
            RenderUtil.ring(bb, mm, radius * 0.7f, radius * 0.7f + 1.5f, 1.5f + u * 6, 64, 1f, 0.6f, 0.3f, alpha * 0.6f);
            // columna de fuego
            RenderUtil.cylinder(bb, mm, 6f + 10f * u, 0, 40f * (1 - u) + 5, 20, 1f, 0.5f, 0.15f, 0.35f * (1 - u));
        }
    }

    private static void drawAim(BufferBuilder bb, Matrix4f m, LocalPlayer player, float partialTick, float now) {
        HitResult hit = player.pick(AIM_RANGE, partialTick, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        float pulse = 0.6f + 0.4f * (float) Math.sin(now * 0.5f);
        float lock = 0.5f + 0.5f * (float) Math.sin(now * 0.12f);
        boolean locked = true;
        Matrix4f mm = new Matrix4f(m).translate(pos.getX() + 0.5f, pos.getY() + 1.02f, pos.getZ() + 0.5f);
        float r = Mth.lerp(lock, 9f, 2.5f);
        RenderUtil.ring(bb, mm, r, r + 0.35f, 0, 48, 1f, 0.1f, 0.08f, 0.9f * pulse);
        RenderUtil.ring(bb, mm, 0f, 0.6f, 0, 16, 1f, 0.2f, 0.1f, 0.9f);
        RenderUtil.ring(bb, mm, 5f, 5.15f, 0, 48, 1f, 0.2f, 0.1f, 0.35f);
        // cruz de la mira
        Matrix4f rot = new Matrix4f(mm).rotateY(now * 0.03f);
        for (int i = 0; i < 4; i++) {
            Matrix4f arm = new Matrix4f(rot).rotateY((float) (i * Math.PI / 2));
            RenderUtil.quad(bb, arm, 1.2f, 0, -0.08f, 4.2f, 0, -0.08f, 4.2f, 0, 0.08f, 1.2f, 0, 0.08f,
                    1f, 0.15f, 0.1f, 0.85f);
        }
        if (locked) {
            // columna roja que marca el objetivo fijado
            RenderUtil.cylinder(bb, mm, 0.25f, 0, 60, 8, 1f, 0.1f, 0.05f, 0.35f * pulse);
        }
    }

    private WorldEffects() {}
}
