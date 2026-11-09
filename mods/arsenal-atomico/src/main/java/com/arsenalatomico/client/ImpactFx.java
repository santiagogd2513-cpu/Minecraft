package com.arsenalatomico.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Copiado de Arsenal Orbital y ajustado a los misiles. Lo que queda después de cada impacto, en el mundo: bola de fuego, nube de hongo que sube y se
 * enrolla, onda de polvo que corre por el suelo, nube de condensación, escombros que salen volando
 * con estela de humo, brasas, incendios en el suelo y una columna de humo que tarda en irse.
 * Los intercontinentales hacen la explosión completa con onda expansiva; los misiles pequeños de la
 * Lluvia de Acero, una versión chica (son 24 a la vez).
 */
final class ImpactFx {
    private static final float LIFE = 75f;    // segundos que dura todo
    private static final List<Blast> BLASTS = new ArrayList<>();

    private ImpactFx() {}

    private static final class Debris {
        float x, y, z, vx, vy, vz, rx, ry, spin, size, r, g, b, hot;
        boolean landed;
        float landedAt;
    }

    private static final class Blast {
        final Vec3 c;
        final Kind type;
        final float start;
        final float R;              // tamaño base en bloques
        final float[] fire, smoke, dust;
        final Debris[] debris;
        final float[] seeds;
        /** Cuánto detalle (1 = completa; las pequeñas llevan mucho menos para no trabar el celular). */
        final float detail;
        float lastSim;

        Blast(Vec3 c, Kind type, float start) {
            this.c = c;
            this.type = type;
            this.start = start;
            this.lastSim = start;
            this.detail = type == Kind.ICBM ? 1f : 0.16f;
            Random r = new Random((long) (c.x * 31 + c.z * 17 + start));
            R = type == Kind.ICBM ? 24f : 4.5f;
            fire = new float[]{1f, 0.55f, 0.18f};       // naranja, como la llama de los misiles
            smoke = new float[]{0.22f, 0.21f, 0.2f};
            dust = new float[]{0.5f, 0.45f, 0.37f};
            int n = type == Kind.ICBM ? 190 : 14;
            debris = new Debris[n];
            for (int i = 0; i < n; i++) {
                Debris d = new Debris();
                double a = r.nextDouble() * Math.PI * 2;
                double elev = 0.35 + r.nextDouble() * 1.05;
                double speed = (14 + r.nextDouble() * 38) * Math.max(0.35, R / 22f) * 0.7;
                d.x = (float) (c.x + Math.cos(a) * r.nextDouble() * R * 0.3);
                d.z = (float) (c.z + Math.sin(a) * r.nextDouble() * R * 0.3);
                d.y = (float) c.y + 1f;
                d.vx = (float) (Math.cos(a) * Math.cos(elev) * speed);
                d.vz = (float) (Math.sin(a) * Math.cos(elev) * speed);
                d.vy = (float) (Math.sin(elev) * speed * 1.2);
                d.rx = r.nextFloat() * 6.28f;
                d.ry = r.nextFloat() * 6.28f;
                d.spin = (r.nextFloat() - 0.5f) * 14f;
                d.size = (0.35f + r.nextFloat() * r.nextFloat() * 2.2f) * (type == Kind.ICBM ? 1f : 0.45f);
                d.hot = r.nextFloat() < 0.45f ? 1f : 0f;
                float[] col = debrisColor(r);
                d.r = col[0]; d.g = col[1]; d.b = col[2];
                debris[i] = d;
            }
            seeds = new float[1024];
            for (int i = 0; i < seeds.length; i++) seeds[i] = r.nextFloat();
        }

        float seed(int i) {
            return seeds[(i & 0x7fffffff) % seeds.length];
        }
    }

    private static float[] debrisColor(Random r) {
        float k = 0.8f + 0.25f * r.nextFloat();
        int pick = r.nextInt(6);
        return switch (pick) {
            case 0, 1 -> new float[]{0.5f * k, 0.49f * k, 0.48f * k};        // piedra
            case 2 -> new float[]{0.42f * k, 0.3f * k, 0.2f * k};            // tierra
            case 3 -> new float[]{0.28f * k, 0.42f * k, 0.18f * k};          // pasto
            case 4 -> new float[]{0.36f * k, 0.36f * k, 0.38f * k};          // pizarra
            default -> new float[]{0.14f * k, 0.1f * k, 0.15f * k};          // obsidiana / basalto
        };
    }

    /** Tamaño de la explosión: misil táctico o intercontinental. */
    enum Kind { SALVO, ICBM }

    /** Una explosión nueva (si ya hay una casi en el mismo lugar y momento, no la repite). */
    static void spawn(Vec3 center, Kind type) {
        float now = Cinematic.clientTicks();
        for (Blast b : BLASTS) {
            if (b.type == type && now - b.start < 60 && b.c.distanceToSqr(center) < 9) return;
        }
        BLASTS.add(new Blast(center, type, now));
        if (BLASTS.size() > 30) BLASTS.remove(0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // el estallido en sí (se ve y se siente aunque el jugador esté lejos)
            int puffs = type == Kind.ICBM ? 14 : 3;
            for (int i = 0; i < puffs; i++) {
                double sp = type == Kind.ICBM ? 9 : 1.5;
                mc.level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, center.x + (mc.level.random.nextDouble() - 0.5) * sp,
                        center.y + 1 + mc.level.random.nextDouble() * sp * 0.5, center.z + (mc.level.random.nextDouble() - 0.5) * sp, 0, 0, 0);
            }
        }
    }

    /** Luces de las explosiones para el resplandor del suelo: {x, y, z, radio, fuerza}. */
    static void lights(java.util.List<float[]> out) {
        float now = Cinematic.clientTicks();
        for (Blast b : BLASTS) {
            float age = (now - b.start) / 20f;
            float fire = Math.max(0f, 1f - age / (b.type == Kind.ICBM ? 9f : 3f));
            float heat = Math.max(0f, 1f - age / 40f) * 0.35f;
            float k = Math.max(fire, heat);
            if (k > 0.01f) out.add(new float[]{(float) b.c.x, (float) b.c.y, (float) b.c.z, b.R * (1.6f + 1.4f * fire), k});
        }
    }

    static void clear() {
        BLASTS.clear();
    }

    // ------------------------------------------------------------------ partículas de Minecraft (humo que sube durante mucho rato)

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = Cinematic.clientTicks();
        BLASTS.removeIf(b -> (now - b.start) / 20f > LIFE);
        for (Blast b : BLASTS) {
            float age = (now - b.start) / 20f;
            var rnd = mc.level.random;
            // columnas de humo de fogata que suben altísimo
            boolean small = b.type == Kind.SALVO;
            if (age < (small ? 25 : LIFE - 15) && (int) now % (small ? 6 : 2) == 0) {
                for (int i = 0; i < (small ? 1 : 3); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * b.R * 0.9;
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                            b.c.x + Math.cos(a) * rr, b.c.y + 0.5, b.c.z + Math.sin(a) * rr, 0, 0.05 + rnd.nextDouble() * 0.06, 0);
                }
            }
            // llamas, lava y chispas en el cráter
            if (age < (small ? 12 : 40)) {
                for (int i = 0; i < (small ? 1 : 4); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * b.R;
                    double x = b.c.x + Math.cos(a) * rr, z = b.c.z + Math.sin(a) * rr;
                    mc.level.addParticle(ParticleTypes.FLAME, true, x, b.c.y + 0.5, z, 0, 0.04, 0);
                    if (i == 0) mc.level.addParticle(ParticleTypes.LAVA, true, x, b.c.y + 0.5, z, 0, 0, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    static void render(PoseStack poseStack, net.minecraft.client.Camera camera, float partialTick) {
        if (BLASTS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        float now = Cinematic.clientTicks() - 1 + partialTick;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        for (Blast b : BLASTS) simulate(b, now);

        // 1) escombros sólidos
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawDebris(bb, m, b, now);
        BufferUploader.drawWithShader(bb.end());

        // 2) humo y polvo (transparencia normal)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawSmoke(bb, m, right, up, b, now);
        BufferUploader.drawWithShader(bb.end());

        // 3) fuego, brillo y brasas (aditivo)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawGlow(bb, m, right, up, b, now);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    private static void simulate(Blast b, float now) {
        float dt = Math.min(0.25f, (now - b.lastSim) / 20f);
        if (dt <= 0f) return;
        b.lastSim = now;
        float ground = (float) b.c.y;
        float age = (now - b.start) / 20f;
        for (Debris d : b.debris) {
            if (d.landed) continue;
            d.vy -= 26f * dt;
            float drag = 1f - 0.12f * dt;
            d.vx *= drag; d.vz *= drag;
            d.x += d.vx * dt; d.y += d.vy * dt; d.z += d.vz * dt;
            d.rx += d.spin * dt; d.ry += d.spin * 0.7f * dt;
            if (d.vy < 0 && d.y < ground + d.size * 0.5f) {
                d.y = ground + d.size * 0.5f;
                d.landed = true;
                d.landedAt = age;
            }
        }
    }

    private static float fade(float age) {
        return 1f - Cinematic.smooth((age - (LIFE - 25f)) / 25f);
    }

    /** Escala de la nube según la edad: sube rápido al principio y luego despacio. */
    private static float rise(float age) {
        return 1f - (float) Math.exp(-age / 7f);
    }

    private static void drawDebris(BufferBuilder bb, Matrix4f m, Blast b, float now) {
        float age = (now - b.start) / 20f;
        for (Debris d : b.debris) {
            if (d.landed && age - d.landedAt > 30f) continue;
            float cool = Math.max(0f, 1f - age / 8f) * d.hot;
            float r = Mth.lerp(cool, d.r, b.fire[0]), g = Mth.lerp(cool, d.g, b.fire[1] * 0.8f), bl = Mth.lerp(cool, d.b, b.fire[2] * 0.6f);
            float s = d.size * 0.5f;
            Matrix4f mm = new Matrix4f(m).translate(d.x, d.y, d.z).rotateX(d.rx).rotateY(d.ry);
            RenderUtil.box(bb, mm, -s, -s * 0.8f, -s, s, s * 0.8f, s, r, g, bl, 1f);
        }
    }

    private static void drawSmoke(BufferBuilder bb, Matrix4f m, Vector3f right, Vector3f up, Blast b, float now) {
        float age = (now - b.start) / 20f;
        float R = b.R, f = fade(age);
        float x = (float) b.c.x, y = (float) b.c.y, z = (float) b.c.z;
        float k = rise(age);
        float capH = R * (0.6f + 3.4f * k);
        float capR = R * (0.5f + 1.1f * k);
        float tubeR = capR * 0.42f;
        // la base se oscurece al apagarse el fuego
        float burn = Math.max(0f, 1f - age / 9f);
        float[] sm = b.smoke;
        // ---- tallo de la nube
        if (age > 0.8f) {
            int stem = Math.max(6, (int) (46 * b.detail));
            for (int i = 0; i < stem; i++) {
                float h = ((i / (float) stem) + age * 0.03f) % 1f;
                float py = y + h * capH * 0.92f;
                float neck = 0.55f + 0.45f * (float) Math.abs(h - 0.55f) * 2f;
                float rad = R * 0.32f * neck * (0.7f + 0.3f * k);
                float a = b.seed(i) * 6.28f + age * 0.4f;
                float px = x + (float) Math.cos(a) * rad * 0.35f, pz = z + (float) Math.sin(a) * rad * 0.35f;
                float lit = burn * (1f - h) * 0.7f;
                float alpha = 0.55f * f * Math.min(1f, (age - 0.8f) / 2f);
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, rad * (0.9f + 0.4f * b.seed(i + 50)), 12,
                        Mth.lerp(lit, sm[0], b.fire[0] * 0.9f), Mth.lerp(lit, sm[1], b.fire[1] * 0.6f), Mth.lerp(lit, sm[2], b.fire[2] * 0.4f),
                        alpha, 0f);
            }
        }
        // ---- sombrero de la nube: un toro que se enrolla hacia afuera
        int ring = Math.max(8, (int) (34 * b.detail)), tube = b.detail < 1 ? 4 : 7;
        for (int i = 0; i < ring; i++) {
            float a = i * 6.2832f / ring + b.seed(i + 100) * 0.2f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            for (int j = 0; j < tube; j++) {
                float ph = j * 6.2832f / tube + age * 0.55f + b.seed(i * 7 + j) * 0.4f;
                float rr = capR * 0.62f + tubeR * (float) Math.cos(ph);
                float py = y + capH + tubeR * 0.75f * (float) Math.sin(ph);
                float px = x + ca * rr, pz = z + sa * rr;
                // los de abajo reflejan el fuego, los de arriba son humo
                float under = Math.max(0f, -(float) Math.sin(ph));
                float lit = burn * (0.35f + 0.65f * under);
                float shade = 0.75f + 0.35f * Math.max(0f, (float) Math.sin(ph));
                float sz = tubeR * (0.85f + 0.5f * b.seed(i * 11 + j));
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, sz, 12,
                        Mth.lerp(lit, sm[0] * shade, b.fire[0]), Mth.lerp(lit, sm[1] * shade, b.fire[1] * 0.75f),
                        Mth.lerp(lit, sm[2] * shade, b.fire[2] * 0.5f), 0.5f * f, 0f);
            }
        }
        // centro del sombrero
        RenderUtil.softDisc(bb, m, x, y + capH + tubeR * 0.3f, z, right, up, capR * 0.75f, 20, sm[0] * 1.1f, sm[1] * 1.1f, sm[2] * 1.1f, 0.5f * f, 0f);
        // ---- onda de polvo que corre por el suelo
        float surge = R * (1f + 4.5f * (1f - (float) Math.exp(-age / 3.5f)));
        float dustA = 0.5f * Math.min(1f, age * 2f) * Math.max(0f, 1f - age / 40f);
        if (dustA > 0.01f) {
            int n = Math.max(12, (int) (70 * b.detail));
            for (int i = 0; i < n; i++) {
                float a = i * 6.2832f / n + b.seed(i + 300) * 0.1f;
                float rr = surge * (0.9f + 0.15f * b.seed(i + 400));
                float sz = R * (0.45f + 0.35f * b.seed(i + 500)) * (0.6f + 0.4f * k);
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(a) * rr, y + sz * 0.45f, z + (float) Math.sin(a) * rr, right, up, sz, 12,
                        b.dust[0], b.dust[1], b.dust[2], dustA, 0f);
                // polvo que queda atrás
                float rr2 = rr * (0.45f + 0.4f * b.seed(i + 600));
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(a + 0.05f) * rr2, y + sz * 0.3f, z + (float) Math.sin(a + 0.05f) * rr2, right, up,
                        sz * 0.8f, 10, b.dust[0] * 0.9f, b.dust[1] * 0.9f, b.dust[2] * 0.9f, dustA * 0.6f, 0f);
            }
        }
        // ---- nube de condensación (cúpula blanca que aparece y se desvanece en segundos)
        if (age > 0.2f && age < 4f && b.detail >= 1) {
            float u = (age - 0.2f) / 3.8f;
            float wr = R * (1.5f + 3f * Cinematic.easeOutCubic(u));
            float wa = 0.28f * (float) Math.sin(Math.PI * u);
            for (int i = 0; i < 40; i++) {
                float a = b.seed(i + 700) * 6.2832f, el = b.seed(i + 800) * 1.2f;
                float px = x + (float) (Math.cos(a) * Math.cos(el)) * wr, pz = z + (float) (Math.sin(a) * Math.cos(el)) * wr;
                float py = y + (float) Math.sin(el) * wr * 0.6f + R * 0.3f;
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, R * 0.9f, 12, 0.92f, 0.93f, 0.96f, wa, 0f);
            }
        }
        // ---- estelas de humo de los escombros calientes
        for (int i = 0; i < b.debris.length; i += 2) {
            Debris d = b.debris[i];
            if (d.landed || d.hot <= 0f || age > 10f) continue;
            float sp = (float) Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz) + 0.01f;
            for (int s = 1; s <= 4; s++) {
                float back = s * 1.6f;
                RenderUtil.softDisc(bb, m, d.x - d.vx / sp * back, d.y - d.vy / sp * back, d.z - d.vz / sp * back, right, up,
                        d.size * (0.8f + s * 0.45f), 8, sm[0], sm[1], sm[2], 0.4f * (1f - s / 5f), 0f);
            }
        }
    }

    private static void drawGlow(BufferBuilder bb, Matrix4f m, Vector3f right, Vector3f up, Blast b, float now) {
        float age = (now - b.start) / 20f;
        float R = b.R, f = fade(age);
        float x = (float) b.c.x, y = (float) b.c.y, z = (float) b.c.z;
        float time = now / 20f;
        float[] fc = b.fire;
        boolean prism = false;
        // ---- onda expansiva: anillos de luz que corren por el suelo y se apagan
        float waveT = b.detail >= 1 ? 3.5f : 0.9f;
        if (age < waveT) {
            float u = age / waveT;
            float rad = R * 0.4f + R * (b.detail >= 1 ? 6.5f : 4f) * Cinematic.easeOutCubic(u);
            float wa = (1f - u) * (1f - u) * 0.85f;
            Matrix4f g = new Matrix4f(m).translate(x, y + 0.4f, z);
            RenderUtil.ring(bb, g, rad, rad + R * 0.25f, 0f, 96, 1f, 0.82f, 0.58f, wa);
            RenderUtil.ring(bb, g, rad * 0.72f, rad * 0.72f + R * 0.12f, R * 0.06f + u * R * 0.5f, 72, 1f, 0.55f, 0.25f, wa * 0.6f);
            if (b.detail >= 1) {
                RenderUtil.cylinder(bb, g, R * (0.9f + 1.6f * u), 0, R * 7f * (1 - u) + 6, 32, 1f, 0.5f, 0.15f, 0.35f * (1 - u));
            }
        }
        // ---- bola de fuego inicial
        if (age < 7f) {
            float u = age / 7f;
            float fr = R * (0.4f + 1.1f * Cinematic.easeOutCubic(Math.min(1f, age / 1.6f)));
            float fy = y + R * 0.4f + age * R * 0.18f;
            float a = (1f - u) * (1f - u);
            for (int l = 0; l < 4; l++) {
                float[] col = prism ? fc : fc;
                RenderUtil.softDisc(bb, m, x, fy, z, right, up, fr * (1.6f - l * 0.32f), 24, col[0], col[1] * (0.7f + l * 0.08f),
                        col[2] * (0.5f + l * 0.12f), 0.35f * a, 0f);
            }
            RenderUtil.softDisc(bb, m, x, fy, z, right, up, fr * 0.55f, 24, 1f, 0.97f, 0.9f, 0.9f * a, 0f);
            // remolinos de fuego en la superficie de la bola
            for (int i = 0; i < (int) (26 * b.detail) + 3; i++) {
                float aa = b.seed(i + 900) * 6.2832f + age * (0.6f + b.seed(i + 950));
                float el = (b.seed(i + 960) - 0.5f) * 2.4f;
                float px = x + (float) (Math.cos(aa) * Math.cos(el)) * fr * 0.8f, pz = z + (float) (Math.sin(aa) * Math.cos(el)) * fr * 0.8f;
                float py = fy + (float) Math.sin(el) * fr * 0.8f;
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, fr * 0.35f, 12, fc[0], fc[1] * 0.8f, fc[2] * 0.6f, 0.4f * a, 0f);
            }
        }
        // ---- el fuego que sigue ardiendo dentro del tallo y del sombrero
        float k = rise(age);
        float capH = R * (0.6f + 3.4f * k), capR = R * (0.5f + 1.1f * k);
        float burn = Math.max(0f, 1f - age / 12f);
        if (burn > 0f) {
            for (int i = 0; i < 18; i++) {
                float h = ((i / 18f) + age * 0.08f) % 1f;
                float flick = 0.7f + 0.3f * (float) Math.sin(time * 9 + i);
                RenderUtil.softDisc(bb, m, x, y + h * capH, z, right, up, R * 0.28f * flick, 12, fc[0], fc[1] * 0.75f, fc[2] * 0.5f,
                        0.22f * burn * (1f - h * 0.6f), 0f);
            }
            for (int i = 0; i < 16; i++) {
                float a = i * 0.3927f + age * 0.3f;
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(a) * capR * 0.6f, y + capH - capR * 0.12f, z + (float) Math.sin(a) * capR * 0.6f,
                        right, up, capR * 0.32f, 12, fc[0], fc[1] * 0.7f, fc[2] * 0.5f, 0.2f * burn, 0f);
            }
        }
        // ---- brillo del cráter (roca derretida) y fogatas alrededor
        float heat = Math.max(0f, 1f - age / 45f) * f;
        if (heat > 0f) {
            Matrix4f g = new Matrix4f(m).translate(x, y + 0.3f, z);
            RenderUtil.ring(bb, g, 0f, R * 0.7f, 0f, 48, fc[0], fc[1] * 0.6f, fc[2] * 0.35f, 0.35f * heat);
            for (int i = 0; i < (int) (40 * b.detail) + 2; i++) {
                float a = b.seed(i + 1000) * 6.2832f, rr = (0.3f + b.seed(i + 1040) * 1.6f) * R;
                float flick = 0.6f + 0.4f * (float) Math.sin(time * (7f + 5f * b.seed(i)) + i * 3);
                float px = x + (float) Math.cos(a) * rr, pz = z + (float) Math.sin(a) * rr;
                float[] col = prism ? fc : fc;
                RenderUtil.softDisc(bb, m, px, y + 1.2f, pz, right, up, 1.6f + 1.8f * b.seed(i + 1080), 10,
                        col[0], col[1] * 0.75f, col[2] * 0.5f, 0.55f * heat * flick, 0f);
            }
        }
        // ---- brasas que suben y se apagan
        for (int i = 0; i < (int) (220 * b.detail); i++) {
            float life = 6f + 6f * b.seed(i + 1200);
            float ph = (age + b.seed(i + 1300) * life) % life / life;
            if (age < ph * life) continue;
            float a = b.seed(i + 1400) * 6.2832f + ph * 2f;
            float rr = R * (0.15f + 0.9f * b.seed(i + 1500)) * (0.5f + ph);
            float px = x + (float) Math.cos(a) * rr, pz = z + (float) Math.sin(a) * rr;
            float py = y + 1f + ph * capH * (0.6f + 0.5f * b.seed(i + 1600));
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 13 + i);
            float[] col = prism ? fc : fc;
            RenderUtil.softDisc(bb, m, px, py, pz, right, up, 0.35f + 0.3f * tw, 6, col[0], col[1] * 0.85f, col[2] * 0.7f,
                    0.9f * (1f - ph) * f, 0f);
        }
        // ---- escombros al rojo vivo
        for (Debris d : b.debris) {
            float cool = Math.max(0f, 1f - age / 8f) * d.hot;
            if (cool <= 0.01f) continue;
            RenderUtil.softDisc(bb, m, d.x, d.y, d.z, right, up, d.size * 1.8f, 8, fc[0], fc[1] * 0.7f, fc[2] * 0.5f, 0.6f * cool, 0f);
        }
        // ---- relámpagos dentro de la nube (como en las erupciones de volcanes)
        if (age > 1.5f && age < 30f && b.detail >= 1) {
            int bolt = (int) (time * 3f);
            float flash = (float) Math.pow(Math.max(0f, Math.sin(time * 3f * Math.PI)), 8);
            if (b.seed(bolt) > 0.55f && flash > 0.05f) {
                Random r = new Random(bolt * 7919L + (long) b.start);
                float px = x + (r.nextFloat() - 0.5f) * capR, pz = z + (r.nextFloat() - 0.5f) * capR;
                float py = y + capH * (0.3f + 0.6f * r.nextFloat());
                float[] col = prism ? new float[]{0.75f, 0.82f, 1f} : new float[]{0.75f, 0.82f, 1f};
                for (int s = 0; s < 9; s++) {
                    float nx = px + (r.nextFloat() - 0.5f) * R * 0.5f, ny = py - R * (0.12f + 0.2f * r.nextFloat()), nz = pz + (r.nextFloat() - 0.5f) * R * 0.5f;
                    Vector3f dir = new Vector3f(nx - px, ny - py, nz - pz);
                    float len = dir.length();
                    dir.div(len);
                    Vector3f side = new Vector3f(dir).cross(up).normalize();
                    RenderUtil.softBeam(bb, m, (px + nx) / 2, (py + ny) / 2, (pz + nz) / 2, dir, len / 2, side, 0.6f, col[0], col[1], col[2], 0.9f * flash);
                    px = nx; py = ny; pz = nz;
                }
                RenderUtil.softDisc(bb, m, x, y + capH * 0.6f, z, right, up, capR * 1.2f, 16, col[0], col[1], col[2], 0.15f * flash, 0f);
            }
        }
    }
}
