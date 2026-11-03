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
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        /** Tamaño (1 = satélite; el láser de Prometeo es mucho más grande y baja del cielo). */
        final float scale;
        final boolean descend;

        Beam(BlockPos target, long start, long impact) {
            this(target, start, impact, 1f, false);
        }

        Beam(BlockPos target, long start, long impact, float scale, boolean descend) {
            this.target = target;
            this.start = start;
            this.impact = impact;
            this.end = impact + 80;
            this.scale = scale;
            this.descend = descend;
        }
    }

    /** Fobos cayendo del cielo envuelta en fuego. */
    private static final class Meteor {
        final BlockPos target;
        final Vec3 center;
        final long start;
        final long impact;
        final long end;

        Meteor(BlockPos target, long start, long impact) {
            this.target = target;
            this.center = Vec3.atCenterOf(target).add(0, 0.5, 0);
            this.start = start;
            this.impact = impact;
            this.end = impact + 140;
        }

        float fraction(float now) {
            return Mth.clamp((now - start) / (float) Math.max(1, impact - start), 0f, 1f);
        }
    }

    private static final List<Meteor> METEORS = new ArrayList<>();
    /** Mini agujeros negros (usa la misma estructura: aparece, absorbe y colapsa en "impact"). */
    private static final List<Meteor> SINGULARITIES = new ArrayList<>();

    public static void addStrike(BlockPos target, int ticksToImpact, com.amatista.arsenal.StrikeType type) {
        long now = Cinematic.clientTicks();
        if (type == com.amatista.arsenal.StrikeType.BLACKHOLE) {
            SINGULARITIES.add(new Meteor(target, now, now + Math.max(1, ticksToImpact)));
            Cinematic.play(SoundEvents.WARDEN_EMERGE, 0.5f, 1.0f);
        } else if (type == com.amatista.arsenal.StrikeType.PHOBOS) {
            SpaceRenderer.prepare();
            METEORS.add(new Meteor(target, now, now + Math.max(1, ticksToImpact)));
            Cinematic.play(SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 0.5f);
        } else if (type == com.amatista.arsenal.StrikeType.STAR) {
            BEAMS.add(new Beam(target, now, now + Math.max(1, ticksToImpact), 2.6f, true));
            Cinematic.play(SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 0.8f);
        } else {
            BEAMS.add(new Beam(target, now, now + Math.max(1, ticksToImpact)));
        }
    }

    public static void clear() {
        BEAMS.clear();
        METEORS.clear();
        SINGULARITIES.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = Cinematic.clientTicks();
        tickMeteors(mc, now);
        SINGULARITIES.removeIf(sg -> now > sg.end);
        for (Meteor sg : SINGULARITIES) {
            if (now >= sg.impact) continue;
            Vec3 p = BlackHoleDirector.singularityWorldPos(sg.center, sg.fraction(now));
            for (int i = 0; i < 10; i++) {
                double a = mc.level.random.nextDouble() * Math.PI * 2, r = 4 + mc.level.random.nextDouble() * 14;
                mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, true, p.x + Math.cos(a) * r, p.y + (mc.level.random.nextDouble() - 0.5) * 4,
                        p.z + Math.sin(a) * r, -Math.cos(a) * 0.4, 0, -Math.sin(a) * 0.4);
            }
        }
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
                for (int i = 0; i < 4 + (int) (u * 20); i++) {
                    double a = mc.level.random.nextDouble() * Math.PI * 2, r = mc.level.random.nextDouble() * (4 + u * 14);
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

    private static void tickMeteors(Minecraft mc, long now) {
        Iterator<Meteor> it = METEORS.iterator();
        while (it.hasNext()) {
            Meteor m = it.next();
            if (now > m.end) {
                it.remove();
                continue;
            }
            if (now < m.impact) {
                Vec3 p = PhobosDirector.meteorWorldPos(m.center, m.fraction(now));
                for (int i = 0; i < 14; i++) {
                    double ox = mc.level.random.nextGaussian() * 9, oy = mc.level.random.nextGaussian() * 9, oz = mc.level.random.nextGaussian() * 9;
                    mc.level.addParticle(ParticleTypes.FLAME, true, p.x + ox, p.y + oy, p.z + oz, 0, 0.05, 0);
                    mc.level.addParticle(ParticleTypes.LARGE_SMOKE, true, p.x + ox, p.y + oy + 6, p.z + oz, 0, 0.1, 0);
                }
                mc.level.addParticle(ParticleTypes.LAVA, true, p.x, p.y, p.z, 0, 0, 0);
                mc.level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, p.x, p.y + 10, p.z, 0, 0.05, 0);
            }
        }
    }

    /** Con el designador en la mano (y el satélite listo) se muestra la mira. */
    private static boolean isAiming(LocalPlayer p) {
        boolean holding = p.getMainHandItem().is(ModItems.ORBITAL_DESIGNATOR.get())
                || p.getOffhandItem().is(ModItems.ORBITAL_DESIGNATOR.get());
        return holding && !p.getCooldowns().isOnCooldown(ModItems.ORBITAL_DESIGNATOR.get());
    }

    public static void render(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean aiming = player != null && !Cinematic.isActive() && isAiming(player);
        if (!METEORS.isEmpty()) renderMeteors(poseStack, camera, partialTick);
        if (!SINGULARITIES.isEmpty()) renderSingularities(poseStack, camera, partialTick);
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

    private static void renderMeteors(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        float now = Cinematic.clientTicks() - 1 + partialTick;
        float time = now / 20f;
        Vector3f light = new Vector3f(0.3f, 0.9f, 0.3f).normalize();
        Vector3f motion = new Vector3f((float) -PhobosDirector.METEOR_DIR.x,
                (float) -PhobosDirector.METEOR_DIR.y, (float) -PhobosDirector.METEOR_DIR.z);
        Quaternionf rot = new Quaternionf().rotateY(time * 0.8f).rotateX(time * 0.5f);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        // roca sólida
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor me : METEORS) {
            if (now >= me.impact) continue;
            Vec3 p = PhobosDirector.meteorWorldPos(me.center, me.fraction(now));
            MarsMesh.renderPhobosScaled(bb, m, new Vector3f((float) p.x, (float) p.y, (float) p.z), rot, light, 0.3f);
        }
        BufferUploader.drawWithShader(bb.end());
        // fuego, plasma y cola (aditivo)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor me : METEORS) {
            float x = (float) me.center.x, y = (float) me.center.y, z = (float) me.center.z;
            if (now < me.impact) {
                Vec3 pv = PhobosDirector.meteorWorldPos(me.center, me.fraction(now));
                Vector3f p = new Vector3f((float) pv.x, (float) pv.y, (float) pv.z);
                MarsMesh.renderPhobosHeat(bb, m, p, rot, motion, 1f, time, 0.3f);
                float flick = 0.85f + 0.15f * (float) Math.sin(time * 40);
                Vector3f front = new Vector3f(p).add(new Vector3f(motion).mul(14f));
                RenderUtil.softDisc(bb, m, front.x, front.y, front.z, right, up, 46f * flick, 20, 1f, 0.6f, 0.25f, 0.8f, 0f);
                RenderUtil.softDisc(bb, m, front.x, front.y, front.z, right, up, 22f, 20, 1f, 0.95f, 0.8f, 0.9f, 0f);
                // cola de fuego
                Vector3f toCam = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z).sub(p);
                Vector3f side = new Vector3f(motion).cross(toCam).normalize();
                float tail = 420f;
                Vector3f mid = new Vector3f(p).sub(new Vector3f(motion).mul(tail * 0.5f));
                RenderUtil.softBeam(bb, m, mid.x, mid.y, mid.z, motion, tail * 0.5f, side, 34f, 1f, 0.45f, 0.12f, 0.55f);
                RenderUtil.softBeam(bb, m, mid.x, mid.y, mid.z, motion, tail * 0.45f, side, 14f, 1f, 0.85f, 0.5f, 0.75f);
                // resplandor en el suelo que crece al acercarse
                float f = me.fraction(now);
                Matrix4f g = new Matrix4f(m).translate(x, y, z);
                RenderUtil.ring(bb, g, 0f, 20f + 60f * f, 0.2f, 48, 1f, 0.5f, 0.2f, 0.12f + 0.3f * f * f);
            } else if (now < me.impact + 70) {
                // onda expansiva gigante y columna de fuego
                float u = (now - me.impact) / 70f;
                float radius = 12f + 260f * Cinematic.easeOutCubic(u);
                float alpha = (1f - u) * 0.85f;
                Matrix4f g = new Matrix4f(m).translate(x, y, z);
                RenderUtil.ring(bb, g, radius, radius + 9f, 0.4f, 120, 1f, 0.8f, 0.55f, alpha);
                RenderUtil.ring(bb, g, radius * 0.7f, radius * 0.7f + 4f, 2f + u * 14f, 96, 1f, 0.55f, 0.25f, alpha * 0.6f);
                RenderUtil.cylinder(bb, g, 22f + 40f * u, 0, 180f * (1 - u) + 10, 32, 1f, 0.45f, 0.12f, 0.4f * (1 - u));
                RenderUtil.softDisc(bb, m, x, y + 20f, z, right, up, 160f * (1 - u * 0.5f), 24, 1f, 0.7f, 0.35f, 0.6f * (1 - u), 0f);
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    /** El mini agujero negro: esfera negra, halo oscuro, disco de acreción morado y anillo de luz. */
    private static void renderSingularities(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        float now = Cinematic.clientTicks() - 1 + partialTick;
        float time = now / 20f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        float pullFrac = (com.amatista.arsenal.BlackHoleTimeline.PULL_START - com.amatista.arsenal.BlackHoleTimeline.WORLD_START)
                / (com.amatista.arsenal.BlackHoleTimeline.IMPACT - com.amatista.arsenal.BlackHoleTimeline.WORLD_START);

        // 1) halo oscuro (la luz se "dobla" alrededor)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor sg : SINGULARITIES) {
            if (now >= sg.impact) continue;
            float f = sg.fraction(now);
            float grow = Cinematic.smooth(f / pullFrac) * 0.6f + 0.4f + 0.6f * Math.max(0f, (f - pullFrac) / (1f - pullFrac));
            Vec3 p = BlackHoleDirector.singularityWorldPos(sg.center, f);
            RenderUtil.softDisc(bb, m, (float) p.x, (float) p.y, (float) p.z, right, up, 11f * grow, 32, 0f, 0f, 0.02f, 0.75f, 0f);
        }
        BufferUploader.drawWithShader(bb.end());

        // 2) esfera negra
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor sg : SINGULARITIES) {
            if (now >= sg.impact) continue;
            float f = sg.fraction(now);
            float grow = Cinematic.smooth(f / pullFrac) * 0.6f + 0.4f + 0.6f * Math.max(0f, (f - pullFrac) / (1f - pullFrac));
            Vec3 p = BlackHoleDirector.singularityWorldPos(sg.center, f);
            float r = 3.2f * grow;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 32; j++) {
                    for (int k = 0; k < 4; k++) {
                        int ii = i + (k == 1 || k == 2 ? 1 : 0), jj = j + (k >= 2 ? 1 : 0);
                        double th = Math.PI * ii / 16, ph = Math.PI * 2 * jj / 32;
                        bb.vertex(m, (float) (p.x + Math.sin(th) * Math.cos(ph) * r), (float) (p.y + Math.cos(th) * r),
                                (float) (p.z + Math.sin(th) * Math.sin(ph) * r)).color(0f, 0f, 0f, 1f).endVertex();
                    }
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());

        // 3) disco de acreción, anillo de fotones y colapso (aditivo)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor sg : SINGULARITIES) {
            float x = (float) sg.center.x, y = (float) sg.center.y, z = (float) sg.center.z;
            if (now < sg.impact) {
                float f = sg.fraction(now);
                float grow = Cinematic.smooth(f / pullFrac) * 0.6f + 0.4f + 0.6f * Math.max(0f, (f - pullFrac) / (1f - pullFrac));
                Vec3 p = BlackHoleDirector.singularityWorldPos(sg.center, f);
                float px = (float) p.x, py = (float) p.y, pz = (float) p.z;
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, 3.2f * grow * 1.18f, 40, 1f, 0.8f, 1f, 0f, 0.95f);
                Matrix4f disk = new Matrix4f(m).translate(px, py, pz).rotateX(0.35f).rotateZ(0.2f).rotateY(time * 2.5f);
                for (int ring = 0; ring < 6; ring++) {
                    float r0 = 4.5f * grow + ring * 2.2f * grow, r1 = r0 + 2.2f * grow;
                    float heat = 1f - ring / 6f;
                    RenderUtil.ring(bb, disk, r0, r1, 0, 72, 0.8f + 0.2f * heat, 0.4f + 0.45f * heat, 1f, 0.25f + 0.55f * heat);
                }
                // espiral de materia que cae
                for (int i = 0; i < 120; i++) {
                    float ph = (i * 0.618f + time * 0.5f) % 1f;
                    float rr = (4f + 26f * (1 - ph)) * grow;
                    float a = i * 2.4f + ph * 9f + time * 3f;
                    RenderUtil.softDisc(bb, m, px + (float) Math.cos(a) * rr, py + (float) Math.sin(i) * 2f * (1 - ph), pz + (float) Math.sin(a) * rr,
                            right, up, 0.6f + 0.6f * ph, 6, 0.7f + 0.3f * ph, 0.4f + 0.3f * ph, 1f, 0.8f * ph, 0f);
                }
            } else if (now < sg.impact + 80) {
                float u = (now - sg.impact) / 80f;
                float py = y + (float) com.amatista.arsenal.strike.ImpactEffects.SINGULARITY_HEIGHT;
                float radius = 6f + 240f * Cinematic.easeOutCubic(u);
                Matrix4f g = new Matrix4f(m).translate(x, y, z);
                RenderUtil.ring(bb, g, radius, radius + 10f, 0.5f, 120, 0.8f, 0.5f, 1f, (1f - u) * 0.9f);
                RenderUtil.ring(bb, g, radius * 0.6f, radius * 0.6f + 5f, 3f + u * 20f, 96, 1f, 0.7f, 1f, (1f - u) * 0.6f);
                RenderUtil.softDisc(bb, m, x, py, z, right, up, 30f + 200f * u, 40, 0.85f, 0.6f, 1f, (1f - u) * 0.9f, 0f);
                RenderUtil.softDisc(bb, m, x, py, z, right, up, 12f + 60f * u, 40, 1f, 1f, 1f, (1f - u), 0f);
            }
        }
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
            float k = b.scale;
            float top = 1400f * (k > 1f ? 2.4f : 1f);
            // el láser de Prometeo baja desde el cielo en dos segundos
            float y0 = b.descend ? top * (1f - Cinematic.easeInCubic(Mth.clamp(sinceStart / 40f, 0f, 1f))) : 0f;
            boolean touching = y0 < 1f;
            Matrix4f mm = new Matrix4f(m).translate(x, y, z);
            RenderUtil.cylinder(bb, mm, (26f + 14f * grow) * flick * k, y0, top, 32, 0.12f, 0.25f, 1f, 0.08f * a);
            RenderUtil.cylinder(bb, mm, (15f + 9f * grow) * flick * k, y0, top, 28, 0.15f, 0.35f, 1f, 0.14f * a);
            RenderUtil.cylinder(bb, mm, (8f + 5f * grow) * flick * k, y0, top, 24, 0.3f, 0.7f, 1f, 0.45f * a);
            RenderUtil.cylinder(bb, mm, (3.5f + 2f * grow) * k, y0, top, 16, 0.9f, 0.97f, 1f, 0.95f * a);
            RenderUtil.cylinder(bb, mm, (1.5f + 0.8f * grow) * k, y0, top, 12, 1f, 1f, 1f, 1f * a);
            if (touching) {
                // resplandor en el suelo
                RenderUtil.ring(bb, mm, 0f, (10f + 22f * grow) * k, 0.05f, 48, 0.6f, 0.85f, 1f, 0.45f * a);
                RenderUtil.ring(bb, mm, (10f + 22f * grow) * k, (22f + 40f * grow) * k, 0.05f, 48, 0.2f, 0.4f, 1f, 0.15f * a);
            } else {
                // frente del rayo bajando
                RenderUtil.ring(bb, mm, 0f, 40f * k, y0, 48, 0.8f, 0.95f, 1f, 0.8f * a);
            }
            // anillos que bajan por el rayo
            for (int i = 0; i < 8; i++) {
                float h = top - ((now * 14f * k + i * 175f * k) % top);
                if (h < y0) continue;
                RenderUtil.ring(bb, mm, (10f + 6 * grow) * k, (14f + 6 * grow) * k, h, 32, 0.5f, 0.8f, 1f, 0.35f * a);
            }
            if (k > 1f) {
                // hélices de energía alrededor del láser
                for (int hx = 0; hx < 3; hx++) {
                    for (int s = 0; s < 70; s++) {
                        float h = y0 + (top - y0) * s / 70f;
                        float ang = h * 0.02f + now * 0.25f + hx * 2.094f;
                        float rr = (30f + 10f * grow) * k;
                        Matrix4f hm = new Matrix4f(mm).translate((float) Math.cos(ang) * rr, h, (float) Math.sin(ang) * rr);
                        RenderUtil.ring(bb, hm, 0f, 3.5f * k, 0, 10, 0.6f, 0.85f, 1f, 0.5f * a);
                    }
                }
            }
        }
        // onda expansiva tras el impacto
        if (now >= b.impact && now < b.impact + 45) {
            float u = (now - b.impact) / 45f;
            float k = b.scale;
            float radius = (8f + 170f * Cinematic.easeOutCubic(u)) * k;
            float alpha = (1f - u) * 0.8f;
            Matrix4f mm = new Matrix4f(m).translate(x, y, z);
            float r1 = k > 1f ? 0.7f : 1f, g1 = k > 1f ? 0.88f : 0.85f, b1 = k > 1f ? 1f : 0.6f;
            RenderUtil.ring(bb, mm, radius, radius + 6f * k, 0.3f, 128, r1, g1, b1, alpha);
            RenderUtil.ring(bb, mm, radius * 0.7f, radius * 0.7f + 3f * k, 1.5f + u * 10 * k, 128, 1f, 0.6f, 0.3f, alpha * 0.6f);
            // columna de fuego
            RenderUtil.cylinder(bb, mm, (14f + 24f * u) * k, 0, (110f * (1 - u) + 8) * k, 28, 1f, 0.5f, 0.15f, 0.35f * (1 - u));
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
