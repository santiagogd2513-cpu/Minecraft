package com.arsenalatomico.client;

import com.arsenalatomico.NukePlan;
import com.arsenalatomico.registry.ModSounds;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * La explosión nuclear del Protocolo Hongo, en orden:
 * <ol>
 *   <li>el destello (doble, como el de una bomba de verdad) y una esfera de luz blanca;</li>
 *   <li>la bola de fuego: blanca, luego amarilla, naranja y roja, con remolinos en la superficie y
 *       manchas de hollín que aparecen al enfriarse; sube y se convierte en el sombrero;</li>
 *   <li>la nube de condensación (cúpula blanca que aparece y se esfuma en segundos);</li>
 *   <li>la onda expansiva: un anillo de luz y un muro de polvo que corre por el suelo hasta casi 400 bloques;</li>
 *   <li>el hongo: un tallo de polvo y humo con la falda de polvo que lo alimenta desde abajo, y el
 *       sombrero que se enrolla hacia afuera, iluminado de naranja por debajo;</li>
 *   <li>los anillos de condensación alrededor del tallo y el casquete de hielo blanco encima del sombrero;</li>
 *   <li>rayos en la bola de fuego, escombros en llamas, el cráter al rojo, incendios y ceniza que cae.</li>
 * </ol>
 */
final class NukeFx {
    private static final float LIFE = 180f;
    private static final List<Blast> BLASTS = new ArrayList<>();

    private NukeFx() {}

    private static final class Debris {
        float x, y, z, vx, vy, vz, rx, ry, spin, size, r, g, b;
        boolean landed;
        float landedAt;
    }

    private static final class Blast {
        final Vec3 c;
        final float start;
        /** Tamaño: 1 = Protocolo Hongo; el Sol Negro es más grande. */
        final float k;
        final float[] seeds = new float[4096];
        final Debris[] debris = new Debris[320];
        float lastSim;

        Blast(Vec3 c, float start, float size) {
            this.c = c;
            this.start = start;
            this.k = size;
            lastSim = start;
            Random r = new Random((long) (c.x * 31 + c.z * 17 + start));
            for (int i = 0; i < seeds.length; i++) seeds[i] = r.nextFloat();
            for (int i = 0; i < debris.length; i++) {
                Debris d = new Debris();
                double a = r.nextDouble() * Math.PI * 2, elev = 0.3 + r.nextDouble() * 1.1;
                double speed = 30 + r.nextDouble() * 70;
                d.x = (float) (c.x + Math.cos(a) * r.nextDouble() * 12);
                d.z = (float) (c.z + Math.sin(a) * r.nextDouble() * 12);
                d.y = (float) c.y + 2;
                d.vx = (float) (Math.cos(a) * Math.cos(elev) * speed);
                d.vz = (float) (Math.sin(a) * Math.cos(elev) * speed);
                d.vy = (float) (Math.sin(elev) * speed * 1.1);
                d.rx = r.nextFloat() * 6.28f;
                d.ry = r.nextFloat() * 6.28f;
                d.spin = (r.nextFloat() - 0.5f) * 12f;
                d.size = 0.5f + r.nextFloat() * r.nextFloat() * 3.2f;
                float k = 0.75f + 0.3f * r.nextFloat();
                int pick = r.nextInt(5);
                float[] col = pick == 0 ? new float[]{0.5f, 0.49f, 0.48f} : pick == 1 ? new float[]{0.4f, 0.29f, 0.2f}
                        : pick == 2 ? new float[]{0.15f, 0.12f, 0.16f} : pick == 3 ? new float[]{0.3f, 0.42f, 0.18f} : new float[]{0.36f, 0.36f, 0.38f};
                d.r = col[0] * k;
                d.g = col[1] * k;
                d.b = col[2] * k;
                debris[i] = d;
            }
        }

        float seed(int i) {
            return seeds[(i & 0x7fffffff) % seeds.length];
        }
    }

    // ------------------------------------------------------------------ las medidas en el tiempo (segundos desde la detonación)

    /** Altura del centro del sombrero sobre el suelo. */
    static float height(float a) {
        return 14 + 196 * (1 - (float) Math.exp(-a / 15f)) + Math.min(a, 2f) * 6;
    }

    static float capR(float a) {
        return 22 + 68 * (1 - (float) Math.exp(-a / 13f));
    }

    static float fireR(float a) {
        return 36 * (1 - (float) Math.exp(-a / 0.45f));
    }

    /** Radio de la onda expansiva (bloques). */
    static float shock(float a) {
        return 390 * (1 - (float) Math.exp(-a / 2.6f));
    }

    static float stemR(float a) {
        return 8 + 9 * (1 - (float) Math.exp(-a / 9f));
    }

    /** Color de la bola de fuego según la edad: blanco, amarillo, naranja, rojo oscuro. */
    private static float[] fireColor(float a) {
        if (a < 0.6f) return new float[]{1f, 0.98f, 0.94f};
        if (a < 2f) return lerp3(new float[]{1f, 0.98f, 0.94f}, new float[]{1f, 0.86f, 0.42f}, (a - 0.6f) / 1.4f);
        if (a < 5f) return lerp3(new float[]{1f, 0.86f, 0.42f}, new float[]{1f, 0.5f, 0.14f}, (a - 2f) / 3f);
        return lerp3(new float[]{1f, 0.5f, 0.14f}, new float[]{0.7f, 0.16f, 0.06f}, Math.min(1, (a - 5f) / 9f));
    }

    private static float[] lerp3(float[] x, float[] y, float u) {
        u = Mth.clamp(u, 0, 1);
        return new float[]{Mth.lerp(u, x[0], y[0]), Mth.lerp(u, x[1], y[1]), Mth.lerp(u, x[2], y[2])};
    }

    // ------------------------------------------------------------------ vida

    static void spawn(Vec3 center) {
        spawn(center, 1f);
    }

    /** {@code k}: tamaño del hongo (1 = Protocolo Hongo). */
    static void spawn(Vec3 center, float k) {
        float now = Cinematic.clientTicks();
        for (Blast b : BLASTS) if (now - b.start < 200 && b.c.distanceToSqr(center) < 400) return;
        BLASTS.add(new Blast(center, now, k));
        if (BLASTS.size() > 4) BLASTS.remove(0);
        Minecraft mc = Minecraft.getInstance();
        // en la cinemática el estruendo lo pone la escena (llega un segundo después del destello)
        if (!(Cinematic.isActive() && (Cinematic.nuke != null || Cinematic.base != null))) mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.NUKE_BLAST.get(), SoundSource.HOSTILE, 4f, 1f,
                SoundInstance.createUnseededRandom(), center.x, center.y + 20, center.z));
        if (mc.level != null) {
            for (int i = 0; i < 40; i++) {
                mc.level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, center.x + (mc.level.random.nextDouble() - 0.5) * 40,
                        center.y + 2 + mc.level.random.nextDouble() * 20, center.z + (mc.level.random.nextDouble() - 0.5) * 40, 0, 0, 0);
            }
        }
    }

    static boolean any() {
        return !BLASTS.isEmpty();
    }

    /** Segundos desde la última detonación (o -1). */
    static float age() {
        if (BLASTS.isEmpty()) return -1;
        return (Cinematic.clientTicks() - BLASTS.get(BLASTS.size() - 1).start) / 20f;
    }

    static void clear() {
        BLASTS.clear();
    }

    /** Luces para el resplandor naranja del suelo: {x, y, z, radio, fuerza}. */
    static void lights(List<float[]> out) {
        float now = Cinematic.clientTicks();
        for (Blast b : BLASTS) {
            float a = (now - b.start) / 20f;
            float fire = (float) Math.exp(-a / 5f);
            if (fire > 0.01f) out.add(new float[]{(float) b.c.x, (float) b.c.y, (float) b.c.z, (90 + 160 * fire) * b.k, Math.min(1f, 1.4f * fire)});
            float crater = Math.max(0, 1 - a / 120f);
            if (crater > 0.01f) out.add(new float[]{(float) b.c.x, (float) b.c.y, (float) b.c.z, NukePlan.CRATER_R * 1.3f * b.k, 0.6f * crater});
        }
    }

    /** Partículas de Minecraft: ceniza que cae, columnas de humo de los incendios, llamas y lava en el cráter. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = Cinematic.clientTicks();
        BLASTS.removeIf(b -> (now - b.start) / 20f > LIFE);
        var rnd = mc.level.random;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Blast b : BLASTS) {
            float a = (now - b.start) / 20f;
            if (a > 12 && a < LIFE - 20 && cam.distanceTo(b.c) < 420) {
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                            cam.x + (rnd.nextDouble() - 0.5) * 40, cam.y + 8 + rnd.nextDouble() * 10, cam.z + (rnd.nextDouble() - 0.5) * 40, 0, -0.05, 0);
                }
            }
            if (a < LIFE - 30 && (int) now % 2 == 0) {
                for (int i = 0; i < 5; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2, rr = NukePlan.CRATER_R * b.k * (1.2 + rnd.nextDouble() * 1.8);
                    mc.level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, b.c.x + Math.cos(ang) * rr, b.c.y + 1,
                            b.c.z + Math.sin(ang) * rr, 0, 0.06 + rnd.nextDouble() * 0.05, 0);
                }
            }
            if (a < 90) {
                for (int i = 0; i < 6; i++) {
                    double cr = NukePlan.CRATER_R * b.k, ang = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * cr * 0.5;
                    double x = b.c.x + Math.cos(ang) * rr, z = b.c.z + Math.sin(ang) * rr;
                    double y = b.c.y - NukePlan.CRATER_DEPTH * b.k * (1 - (rr / cr) * (rr / cr)) + 1;
                    mc.level.addParticle(ParticleTypes.FLAME, true, x, y, z, 0, 0.05, 0);
                    if (i == 0) mc.level.addParticle(ParticleTypes.LAVA, true, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    static void render(PoseStack ps, Camera camera, float partial) {
        if (BLASTS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate(), up = new Vector3f(camera.getUpVector());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        float now = Cinematic.clientTicks() - 1 + partial;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        for (Blast b : BLASTS) simulate(b, now);

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawDebris(bb, scaled(m, b), b, now);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawSmoke(bb, scaled(m, b), right, up, b, now);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Blast b : BLASTS) drawGlow(bb, scaled(m, b), right, up, b, now);
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        ps.popPose();
    }

    /** Agranda todo el hongo alrededor de su centro (el Sol Negro es el mismo hongo, más grande). */
    private static Matrix4f scaled(Matrix4f m, Blast b) {
        if (b.k == 1f) return m;
        return new Matrix4f(m).translate((float) b.c.x, (float) b.c.y, (float) b.c.z).scale(b.k)
                .translate((float) -b.c.x, (float) -b.c.y, (float) -b.c.z);
    }

    private static void simulate(Blast b, float now) {
        float dt = Math.min(0.25f, (now - b.lastSim) / 20f);
        if (dt <= 0f) return;
        b.lastSim = now;
        float a = (now - b.start) / 20f;
        float ground = (float) b.c.y;
        for (Debris d : b.debris) {
            if (d.landed) continue;
            d.vy -= 24f * dt;
            float drag = 1f - 0.1f * dt;
            d.vx *= drag;
            d.vz *= drag;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.z += d.vz * dt;
            d.rx += d.spin * dt;
            d.ry += d.spin * 0.7f * dt;
            if (d.vy < 0 && d.y < ground + d.size * 0.5f) {
                d.y = ground + d.size * 0.5f;
                d.landed = true;
                d.landedAt = a;
            }
        }
    }

    private static float fade(float a) {
        return 1f - Cinematic.smooth((a - (LIFE - 40f)) / 40f);
    }

    private static void drawDebris(BufferBuilder bb, Matrix4f m, Blast b, float now) {
        float a = (now - b.start) / 20f;
        for (Debris d : b.debris) {
            if (d.landed && a - d.landedAt > 60f) continue;
            float hot = Math.max(0f, 1f - a / 10f);
            float s = d.size * 0.5f;
            Matrix4f mm = new Matrix4f(m).translate(d.x, d.y, d.z).rotateX(d.rx).rotateY(d.ry);
            RenderUtil.box(bb, mm, -s, -s * 0.8f, -s, s, s * 0.8f, s, Mth.lerp(hot, d.r, 1f), Mth.lerp(hot, d.g, 0.45f), Mth.lerp(hot, d.b, 0.12f), 1f);
        }
    }

    private static void drawSmoke(BufferBuilder bb, Matrix4f m, Vector3f right, Vector3f up, Blast b, float now) {
        float a = (now - b.start) / 20f, f = fade(a);
        float x = (float) b.c.x, y = (float) b.c.y, z = (float) b.c.z;
        float H = height(a), cr = capR(a), sr = stemR(a);
        float heat = (float) Math.exp(-a / 6f);
        float[] fc = fireColor(a);

        // ---- nube de condensación (de 0,4 a 3,6 s): una cúpula blanca enorme que se esfuma
        if (a > 0.4f && a < 3.6f) {
            float u = (a - 0.4f) / 3.2f;
            float wr = 50 + 150 * Cinematic.easeOutCubic(u);
            float wa = 0.32f * (float) Math.sin(Math.PI * u);
            for (int i = 0; i < 90; i++) {
                float ang = b.seed(i) * 6.2832f, el = b.seed(i + 90) * 1.3f;
                float px = x + (float) (Math.cos(ang) * Math.cos(el)) * wr, pz = z + (float) (Math.sin(ang) * Math.cos(el)) * wr;
                float py = y + 10 + (float) Math.sin(el) * wr * 0.55f;
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, wr * 0.22f, 14, 0.94f, 0.95f, 0.98f, wa, 0f);
            }
        }

        // ---- muro de polvo en el frente de la onda y la falda de polvo que queda atrás
        float S = shock(a);
        if (a < 40) {
            float wall = Math.min(1f, a * 2f) * Math.max(0f, 1f - a / 40f);
            int n = 150;
            for (int i = 0; i < n; i++) {
                float ang = i * 6.2832f / n + b.seed(i + 300) * 0.04f;
                float rr = S * (0.97f + 0.06f * b.seed(i + 400));
                float sz = 8 + 9 * b.seed(i + 500) + Math.min(a, 6) * 2;
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, y + sz * 0.45f, z + (float) Math.sin(ang) * rr, right, up, sz, 12,
                        0.55f, 0.48f, 0.38f, 0.5f * wall * f, 0f);
                float rr2 = rr * (0.35f + 0.5f * b.seed(i + 600));
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang + 0.03f) * rr2, y + sz * 0.3f, z + (float) Math.sin(ang + 0.03f) * rr2, right, up,
                        sz * 1.2f, 10, 0.5f, 0.44f, 0.36f, 0.3f * wall * f, 0f);
            }
        }
        // ---- la oleada de la base: un anillo de polvo que sigue creciendo despacio y se queda flotando
        if (a > 2) {
            float baseR = 60 + 140 * (1 - (float) Math.exp(-(a - 2) / 12f));
            float ba = Math.min(1f, (a - 2) / 4f) * 0.42f * f;
            for (int i = 0; i < 110; i++) {
                float ang = i * 6.2832f / 110 + b.seed(i + 700) * 0.06f + a * 0.01f;
                float rr = baseR * (0.55f + 0.5f * b.seed(i + 800));
                float sz = 12 + 14 * b.seed(i + 900);
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, y + sz * 0.5f + 2, z + (float) Math.sin(ang) * rr, right, up, sz, 12,
                        0.6f, 0.53f, 0.43f, ba, 0f);
            }
        }

        // ---- el tallo: humo y polvo que suben en remolino, más ancho abajo (la falda que lo alimenta)
        if (a > 1.2f) {
            int stem = 110;
            float sa = Math.min(1f, (a - 1.2f) / 2.5f) * 0.6f * f;
            float top = H - cr * 0.25f;
            for (int i = 0; i < stem; i++) {
                float h = (b.seed(i + 1000) + a * 0.045f) % 1f;
                float py = y + h * top;
                float flare = 1 + 2.6f * (float) Math.pow(1 - h, 5);
                float rad = sr * flare * (0.8f + 0.4f * b.seed(i + 1100));
                float ang = b.seed(i + 1200) * 6.28f + a * (0.5f + 0.4f * b.seed(i + 1250)) + h * 4;
                float off = sr * 0.45f * flare;
                float px = x + (float) Math.cos(ang) * off, pz = z + (float) Math.sin(ang) * off;
                float[] col = lerp3(new float[]{0.5f, 0.42f, 0.33f}, new float[]{0.46f, 0.44f, 0.44f}, h);
                float lit = heat * (float) Math.pow(h, 2) * 0.8f;
                RenderUtil.softDisc(bb, m, px, py, pz, right, up, rad, 14,
                        Mth.lerp(lit, col[0], fc[0]), Mth.lerp(lit, col[1], fc[1] * 0.6f), Mth.lerp(lit, col[2], fc[2] * 0.4f), sa, 0f);
            }
        }

        // ---- el sombrero: un toro que se enrolla hacia afuera (la bola de fuego ya fría, convertida en nube)
        if (a > 1.5f) {
            float ca = Math.min(1f, (a - 1.5f) / 3f) * f;
            int ring = 64, tube = 10;
            float tubeR = cr * 0.42f;
            for (int i = 0; i < ring; i++) {
                float ang = i * 6.2832f / ring + b.seed(i + 1300) * 0.12f;
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                for (int j = 0; j < tube; j++) {
                    float ph = j * 6.2832f / tube + a * 0.4f + b.seed(i * 13 + j) * 0.5f;
                    float rr = cr * 0.6f + tubeR * (float) Math.cos(ph);
                    float py = y + H + tubeR * 0.72f * (float) Math.sin(ph);
                    float under = Math.max(0f, -(float) Math.sin(ph));
                    float upSide = Math.max(0f, (float) Math.sin(ph));
                    float lit = heat * (0.4f + 0.6f * under) + (float) Math.exp(-a / 25f) * 0.35f * under;
                    float sun = 0.72f + 0.3f * upSide;
                    float sz = tubeR * (0.78f + 0.5f * b.seed(i * 7 + j + 50));
                    float[] base = {0.6f * sun, 0.57f * sun, 0.54f * sun};
                    RenderUtil.softDisc(bb, m, x + c * rr, py, z + s * rr, right, up, sz, 14,
                            Mth.lerp(Math.min(1, lit), base[0], fc[0]), Mth.lerp(Math.min(1, lit), base[1], fc[1] * 0.7f),
                            Mth.lerp(Math.min(1, lit), base[2], fc[2] * 0.45f), 0.52f * ca, 0f);
                }
            }
            // la cima del sombrero, abombada, con «coliflores» en la orilla
            for (int i = 0; i < 26; i++) {
                float ang = b.seed(i + 1500) * 6.2832f, rr = cr * 0.55f * (float) Math.sqrt(b.seed(i + 1550));
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, y + H + tubeR * 0.6f + cr * 0.12f * (1 - rr / cr), z + (float) Math.sin(ang) * rr,
                        right, up, cr * 0.36f, 16, 0.74f, 0.72f, 0.7f, 0.42f * ca, 0f);
            }
            for (int i = 0; i < 70; i++) {
                float ang = b.seed(i + 1600) * 6.2832f + a * 0.03f;
                float el = (b.seed(i + 1650) - 0.3f) * 1.6f;
                float rr = cr * (0.98f + 0.12f * b.seed(i + 1700));
                RenderUtil.softDisc(bb, m, x + (float) (Math.cos(ang) * Math.cos(el)) * rr, y + H + (float) Math.sin(el) * tubeR,
                        z + (float) (Math.sin(ang) * Math.cos(el)) * rr, right, up, tubeR * (0.35f + 0.3f * b.seed(i + 1750)), 12,
                        0.7f, 0.67f, 0.64f, 0.4f * ca, 0f);
            }
        }

        // ---- el casquete de hielo (pileus): una capa blanca y lisa encima del sombrero
        if (a > 6 && a < 50) {
            float u = (a - 6) / 44f;
            float pa = 0.22f * (float) Math.sin(Math.PI * u) * f;
            float pr = cr * 1.25f, py0 = y + H + cr * 0.5f;
            for (int i = 0; i < 48; i++) {
                float ang = i * 6.2832f / 48;
                for (int k = 0; k < 3; k++) {
                    float el = 0.25f + k * 0.35f;
                    RenderUtil.softDisc(bb, m, x + (float) (Math.cos(ang) * Math.cos(el)) * pr, py0 + (float) Math.sin(el) * pr * 0.35f,
                            z + (float) (Math.sin(ang) * Math.cos(el)) * pr, right, up, pr * 0.28f, 14, 0.97f, 0.98f, 1f, pa, 0f);
                }
            }
        }

        // ---- anillos de condensación alrededor del tallo
        if (a > 7 && a < 45) {
            float u = (a - 7) / 38f;
            float ra = 0.3f * (float) Math.sin(Math.PI * u) * f;
            for (int ringN = 0; ringN < 2; ringN++) {
                float hy = y + H * (ringN == 0 ? 0.42f : 0.66f);
                float rr = sr * (2.6f + 1.6f * u + ringN * 0.6f);
                for (int i = 0; i < 44; i++) {
                    float ang = i * 6.2832f / 44 + ringN;
                    RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, hy + (b.seed(i + 1800 + ringN * 50) - 0.5f) * 3,
                            z + (float) Math.sin(ang) * rr, right, up, sr * 0.9f, 12, 0.96f, 0.97f, 1f, ra, 0f);
                }
            }
        }

        // ---- hollín sobre la bola de fuego al enfriarse
        if (a > 2.5f && a < 12) {
            float fr = fireR(a), fy = y + Math.min(H, 18 + a * 9);
            float soot = Mth.clamp((a - 2.5f) / 4f, 0, 1) * Math.max(0, 1 - (a - 8) / 4f);
            for (int i = 0; i < 40; i++) {
                float ang = b.seed(i + 1900) * 6.2832f + a * 0.5f, el = (b.seed(i + 1950) - 0.5f) * 2.6f;
                RenderUtil.softDisc(bb, m, x + (float) (Math.cos(ang) * Math.cos(el)) * fr * 0.85f, fy + (float) Math.sin(el) * fr * 0.85f,
                        z + (float) (Math.sin(ang) * Math.cos(el)) * fr * 0.85f, right, up, fr * 0.32f, 12, 0.12f, 0.09f, 0.08f, 0.45f * soot, 0f);
            }
        }

        // ---- humo detrás de los escombros en llamas
        for (int i = 0; i < b.debris.length; i += 2) {
            Debris d = b.debris[i];
            if (d.landed || a > 12f) continue;
            float sp = (float) Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz) + 0.01f;
            for (int s = 1; s <= 4; s++) {
                float back = s * 2.2f;
                RenderUtil.softDisc(bb, m, d.x - d.vx / sp * back, d.y - d.vy / sp * back, d.z - d.vz / sp * back, right, up,
                        d.size * (0.9f + s * 0.5f), 8, 0.2f, 0.19f, 0.18f, 0.4f * (1f - s / 5f), 0f);
            }
        }
    }

    private static void drawGlow(BufferBuilder bb, Matrix4f m, Vector3f right, Vector3f up, Blast b, float now) {
        float a = (now - b.start) / 20f, f = fade(a);
        float x = (float) b.c.x, y = (float) b.c.y, z = (float) b.c.z;
        float time = now / 20f;
        float H = height(a), cr = capR(a);
        float[] fc = fireColor(a);

        // ---- el destello: una esfera de luz blanca enorme
        if (a < 0.9f) {
            float k = a < 0.35f ? 1 : a < 0.55f ? 0.75f : Math.max(0, 1 - (a - 0.55f) / 0.35f);
            RenderUtil.softDisc(bb, m, x, y + 25, z, right, up, 160, 32, 1f, 0.99f, 0.95f, 0.95f * k, 0f);
            RenderUtil.softDisc(bb, m, x, y + 25, z, right, up, 60, 24, 1f, 1f, 1f, 1f * k, 0.2f * k);
        }

        // ---- la bola de fuego
        if (a < 14) {
            float fr = fireR(a);
            float fy = y + Math.min(H, Math.max(fr * 0.8f, 18 + a * 9));
            float bright = (float) Math.exp(-a / 4.5f);
            for (int l = 0; l < 5; l++) {
                RenderUtil.softDisc(bb, m, x, fy, z, right, up, fr * (1.7f - l * 0.3f), 28, fc[0], fc[1] * (0.75f + l * 0.06f),
                        fc[2] * (0.55f + l * 0.1f), 0.32f * bright, 0f);
            }
            RenderUtil.softDisc(bb, m, x, fy, z, right, up, fr * 0.7f, 28, 1f, 0.98f, 0.9f, 0.95f * bright * bright, 0f);
            // remolinos que se mueven por la superficie
            for (int i = 0; i < 90; i++) {
                float ang = b.seed(i + 2000) * 6.2832f + a * (0.4f + b.seed(i + 2100) * 0.8f);
                float el = (b.seed(i + 2200) - 0.5f) * 2.8f;
                float rr = fr * (0.75f + 0.2f * b.seed(i + 2300));
                float flick = 0.7f + 0.3f * (float) Math.sin(time * 6 + i);
                RenderUtil.softDisc(bb, m, x + (float) (Math.cos(ang) * Math.cos(el)) * rr, fy + (float) Math.sin(el) * rr,
                        z + (float) (Math.sin(ang) * Math.cos(el)) * rr, right, up, fr * (0.22f + 0.18f * b.seed(i + 2400)), 12,
                        fc[0], fc[1] * 0.85f, fc[2] * 0.6f, 0.45f * bright * flick, 0f);
            }
            // columna de fuego que une la bola con el suelo al principio
            if (a < 6) {
                float ca = (1 - a / 6) * 0.5f;
                for (int i = 0; i < 16; i++) {
                    float h = i / 16f;
                    RenderUtil.softDisc(bb, m, x, y + h * (fy - y), z, right, up, 10 + 8 * (1 - h), 14, fc[0], fc[1] * 0.7f, fc[2] * 0.4f, ca, 0f);
                }
            }
        }

        // ---- la onda expansiva: anillo de luz en el suelo y una cúpula de aire comprimido
        float S = shock(a);
        if (a < 4.5f) {
            float u = a / 4.5f, wa = (1 - u) * (1 - u);
            Matrix4f g = new Matrix4f(m).translate(x, y + 0.6f, z);
            RenderUtil.ring(bb, g, S, S + 7, 0f, 160, 1f, 0.85f, 0.6f, 0.9f * wa);
            RenderUtil.ring(bb, g, S * 0.8f, S * 0.8f + 4, 1.5f, 128, 1f, 0.6f, 0.3f, 0.5f * wa);
            for (int i = 0; i < 64; i++) {
                float ang = i * 6.2832f / 64;
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * S * 0.7f, y + S * 0.45f, z + (float) Math.sin(ang) * S * 0.7f, right, up,
                        S * 0.18f, 10, 1f, 0.97f, 0.9f, 0.06f * wa, 0f);
            }
        }

        // ---- fuego que sigue adentro del tallo y debajo del sombrero
        float burn = (float) Math.exp(-a / 10f);
        if (burn > 0.02f && a > 1) {
            for (int i = 0; i < 26; i++) {
                float h = ((i / 26f) + a * 0.06f) % 1f;
                float flick = 0.7f + 0.3f * (float) Math.sin(time * 8 + i);
                RenderUtil.softDisc(bb, m, x, y + h * H * 0.9f, z, right, up, stemR(a) * 0.7f * flick, 14, fc[0], fc[1] * 0.7f, fc[2] * 0.45f,
                        0.22f * burn * (0.4f + 0.6f * h), 0f);
            }
            for (int i = 0; i < 28; i++) {
                float ang = i * 6.2832f / 28 + a * 0.25f;
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * cr * 0.55f, y + H - cr * 0.18f, z + (float) Math.sin(ang) * cr * 0.55f,
                        right, up, cr * 0.34f, 14, fc[0], fc[1] * 0.65f, fc[2] * 0.4f, 0.24f * burn, 0f);
            }
        }

        // ---- rayos dentro y alrededor de la bola de fuego (los primeros segundos)
        if (a > 0.5f && a < 4f) {
            int bolt = (int) (time * 6f);
            float flash = (float) Math.pow(Math.max(0f, Math.sin(time * 6f * Math.PI)), 6);
            Random r = new Random(bolt * 7919L + (long) b.start);
            for (int k = 0; k < 4; k++) {
                if (r.nextFloat() < 0.4f || flash < 0.05f) continue;
                float ang = r.nextFloat() * 6.2832f, rad = 30 + r.nextFloat() * 50;
                float px = x + (float) Math.cos(ang) * rad, pz = z + (float) Math.sin(ang) * rad, py = y;
                for (int s = 0; s < 10; s++) {
                    float nx = px + (r.nextFloat() - 0.5f) * 8, ny = py + 4 + r.nextFloat() * 6, nz = pz + (r.nextFloat() - 0.5f) * 8;
                    Vector3f dir = new Vector3f(nx - px, ny - py, nz - pz);
                    float len = dir.length();
                    dir.div(len);
                    Vector3f side = new Vector3f(dir).cross(up).normalize();
                    RenderUtil.softBeam(bb, m, (px + nx) / 2, (py + ny) / 2, (pz + nz) / 2, dir, len / 2, side, 0.9f, 0.8f, 0.85f, 1f, 0.9f * flash);
                    px = nx;
                    py = ny;
                    pz = nz;
                }
            }
        }

        // ---- el cráter al rojo y los incendios alrededor
        float heat = Math.max(0f, 1f - a / 130f) * f;
        if (heat > 0) {
            for (int i = 0; i < 60; i++) {
                float ang = b.seed(i + 2500) * 6.2832f, rr = (float) Math.sqrt(b.seed(i + 2550)) * NukePlan.CRATER_R * 0.5f;
                float py = y - NukePlan.CRATER_DEPTH * (1 - (rr / NukePlan.CRATER_R) * (rr / NukePlan.CRATER_R)) + 1.5f;
                float flick = 0.6f + 0.4f * (float) Math.sin(time * (3 + 4 * b.seed(i)) + i);
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, py, z + (float) Math.sin(ang) * rr, right, up, 5 + 5 * b.seed(i + 2600), 12,
                        1f, 0.42f, 0.1f, 0.5f * heat * flick, 0f);
            }
            for (int i = 0; i < 140; i++) {
                float ang = b.seed(i + 2700) * 6.2832f;
                float rr = NukePlan.CRATER_R * (1.1f + b.seed(i + 2800) * 1.1f);
                float flick = 0.5f + 0.5f * (float) Math.sin(time * (6 + 6 * b.seed(i + 2900)) + i * 2);
                RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, y + 1.8f, z + (float) Math.sin(ang) * rr, right, up,
                        1.6f + 2.4f * b.seed(i + 3000), 10, 1f, 0.55f, 0.15f, 0.6f * heat * flick, 0f);
            }
        }

        // ---- brasas que suben con la nube
        for (int i = 0; i < 260; i++) {
            float life = 8f + 10f * b.seed(i + 3100);
            float ph = (a + b.seed(i + 3200) * life) % life / life;
            if (a < ph * life) continue;
            float ang = b.seed(i + 3300) * 6.2832f + ph * 3f;
            float rr = stemR(a) * (0.5f + 2f * b.seed(i + 3400)) * (0.6f + ph);
            float py = y + 2 + ph * H * (0.4f + 0.6f * b.seed(i + 3500));
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 13 + i);
            RenderUtil.softDisc(bb, m, x + (float) Math.cos(ang) * rr, py, z + (float) Math.sin(ang) * rr, right, up, 0.5f + 0.5f * tw, 6,
                    1f, 0.62f, 0.25f, 0.85f * (1f - ph) * f * Math.max(0.15f, (float) Math.exp(-a / 40f)), 0f);
        }

        // ---- escombros al rojo vivo
        for (Debris d : b.debris) {
            float hot = Math.max(0f, 1f - a / 10f);
            if (hot <= 0.01f) continue;
            RenderUtil.softDisc(bb, m, d.x, d.y, d.z, right, up, d.size * 2f, 8, 1f, 0.5f, 0.15f, 0.6f * hot, 0f);
        }
    }
}
