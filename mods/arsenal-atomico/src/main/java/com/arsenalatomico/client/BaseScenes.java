package com.arsenalatomico.client;

import com.arsenalatomico.BaseLayout;
import com.arsenalatomico.BasePlan;
import com.arsenalatomico.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.arsenalatomico.client.Cinematic.lerp;
import static com.arsenalatomico.client.Cinematic.look;
import static com.arsenalatomico.client.Cinematic.smooth;

/**
 * Las tomas de los lanzamientos desde la Base Sol Negro.
 * <pre>
 *  Sol Negro (silo 0):  0–6 centro de mando · 6–12 el misil por la ventana · 12–19 dentro del silo, se abre
 *                       la compuerta · 19–25 la base desde el aire (cuenta regresiva) · 25 ¡despegue! ·
 *                       vuelo · detonación · el hongo · de vuelta a la base
 *  silos chicos y medianos: 0–5 la cabina · 5–9 dentro del silo · 9–13 la boca, cuenta regresiva ·
 *                       13 ¡despegue! · vuelo · detonación · el hongo · de vuelta a la base
 * </pre>
 * El misil siempre suena: el rugido del despegue y un motor que sigue su camino (no depende de que el
 * juego haya recibido el misil del servidor a tiempo).
 */
final class BaseScenes {
    private BaseScenes() {}

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float F = BaseLayout.FLOOR, EYE = F + 1.62f;
    private static boolean engineStarted, windPlayed;

    static void reset() {
        engineStarted = false;
        windPlayed = false;
    }

    private static BasePlan p() {
        return Cinematic.base;
    }

    private static Vec3 L(double a, double b, double y) {
        return p().L(a, b, y);
    }

    private static double[] silo() {
        return BaseLayout.SILOS[p().silo];
    }

    static Vec3 missile(float s) {
        BasePlan p = p();
        double t = s * 20 - p.launchTick;
        if (t <= 0) return p.path.p0;
        return p.path.atTick(Math.min(t, p.path.flightTicks));
    }

    static Vec3 dir(float s) {
        BasePlan p = p();
        double t = Mth.clamp(s * 20 - p.launchTick, 0, p.path.flightTicks - 1);
        return p.path.direction(t);
    }

    /** ¿La cámara va bajo tierra? (para que no la suba a la superficie) */
    static boolean underground(float s) {
        return p().silo == 0 ? s < 19 : s < 9;
    }

    private static float size() {
        return (float) BaseLayout.size(p().silo);
    }

    /** Un punto para mirar la detonación: de lado y un poco atrás, lejos según el tamaño. */
    private static Vec3 vantage(double dist, double height) {
        Vec3 t = Cinematic.target, site = p().site;
        Vec3 along = t.subtract(site).multiply(1, 0, 1);
        along = along.lengthSqr() < 1 ? new Vec3(0, 0, 1) : along.normalize();
        Vec3 across = along.cross(UP).normalize();
        return t.add(across.scale(dist * 0.92)).add(along.scale(-dist * 0.38)).add(0, height, 0);
    }

    static Cinematic.Pose pose(float s) {
        BasePlan p = p();
        boolean main = p.silo == 0;
        double[] si = silo();
        Vec3 target = Cinematic.target;
        float launch = p.launchSecond(), impact = p.impactSecond();
        float k = size();
        float H = NukeFx.height(Math.max(0, s - impact)) * k;
        double top = si[3];
        if (main) {
            if (s < 6) {
                // el centro de mando: la consola y la ventana al silo
                float u = smooth(s / 6);
                Vec3 pos = L(Mth.lerp(u, -3.5, -1.5), Mth.lerp(u, -7, -4), EYE + 0.8);
                return look(pos, L(BaseLayout.CONSOLE_A + 2, 0, EYE - 0.3), 0, 68);
            }
            if (s < 12) {
                // por la ventana: el misil gigante en su silo
                float u = smooth((s - 6) / 6);
                Vec3 pos = L(Mth.lerp(u, 3.5, 6.2), 0, EYE + 0.4);
                return look(pos, L(si[0], si[1], Mth.lerp(u, EYE, EYE + 8)), 0, Mth.lerp(u, 70, 58));
            }
        } else if (s < 5) {
            // la cabina del silo: el panel y la ventana
            double[] pn = BaseLayout.panel(p.silo);
            double toward = Math.signum(si[1]);
            Vec3 pos = L(pn[0] + 1.3, pn[1] - toward * 1.6, EYE + 0.3);
            return look(pos, L(si[0], si[1], EYE + 0.5), 0, 66);
        }
        float inStart = main ? 12 : 5, inEnd = main ? 19 : 9;
        if (s < inEnd) {
            // dentro del silo, mirando hacia arriba: se abre la compuerta
            float u = smooth((s - inStart) / (inEnd - inStart));
            double r = si[2] * 0.75;
            double ang = Math.toRadians(30 + 70 * u);
            double y = -top + Mth.lerp(u, 3, BaseLayout.missileLength(p.silo) * 0.6);
            Vec3 pos = L(si[0] + Math.cos(ang) * r, si[1] + Math.sin(ang) * r, y);
            return look(pos, L(si[0], si[1], 0), 0, 82);
        }
        if (s < launch) {
            // la base desde el aire: la boca abierta y la cuenta regresiva
            float u = (s - inEnd) / (launch - inEnd);
            double dist = main ? 46 : 26;
            Vec3 pos = L(si[0] - dist + 8 * u, si[1] - dist * 0.6, (main ? 30 : 16) - 4 * u);
            return look(pos, L(si[0], si[1], -2), 0, 58);
        }
        Vec3 m = missile(s), d = dir(s);
        Vec3 side = d.cross(UP).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.cross(UP).normalize();
        if (s < launch + 5) {
            // ¡despegue! desde el suelo, mirando hacia arriba
            Vec3 pos = L(si[0] - (main ? 14 : 8), si[1] - (main ? 18 : 10), 2);
            return look(pos, lerp(L(si[0], si[1], 4), m, smooth((s - launch) / 5)), 0, main ? 74 : 70);
        }
        if (s < impact - 4) {
            // el vuelo: la cámara lo persigue desde atrás y un poco al lado
            float u = (s - launch - 5) / Math.max(1, impact - 4 - launch - 5);
            double back = main ? 46 : 30;
            Vec3 pos = m.subtract(d.scale(back)).add(side.scale(main ? 14 : 9)).add(0, 4, 0);
            return look(pos, m.add(d.scale(30)), (float) Math.sin(u * 6) * 2, 72);
        }
        if (s < impact + 8) {
            // la detonación desde lejos
            Vec3 v = vantage(330 * k, 22 * k);
            float kk = Mth.clamp((s - impact) / 7, 0, 1);
            return look(v, target.add(0, Mth.lerp(kk, 18, H * 0.6f), 0), 0, Mth.lerp(kk, 48, 62));
        }
        float end = p.duration / 20f;
        if (s < end - 9) {
            // el hongo sube; la cámara lo rodea despacio
            float u = (s - impact - 8) / Math.max(1, end - 9 - impact - 8);
            Vec3 rel = vantage(330 * k, 0).subtract(target);
            double ang = u * 0.7;
            Vec3 rot = new Vec3(rel.x * Math.cos(ang) - rel.z * Math.sin(ang), 0, rel.x * Math.sin(ang) + rel.z * Math.cos(ang));
            return look(target.add(rot.scale(Mth.lerp(u, 1, 0.85))).add(0, (18 + 60 * u) * k, 0), target.add(0, H * 0.6f, 0), 0, 64);
        }
        // de vuelta a la base, con el hongo en el horizonte
        float u = smooth((s - (end - 9)) / 9);
        Vec3 toTarget = target.subtract(p.site).multiply(1, 0, 1).normalize();
        Vec3 pos = p.site.subtract(toTarget.scale(Mth.lerp(u, 70, 50))).add(toTarget.cross(UP).scale(20)).add(0, Mth.lerp(u, 26, 14), 0);
        return look(pos, lerp(L(si[0], si[1], 0), target.add(0, H * 0.7f, 0), 0.5 + 0.3 * u), 0, 66);
    }

    static String caption(float s) {
        BasePlan p = p();
        float launch = p.launchSecond(), impact = p.impactSecond();
        if (p.silo == 0) {
            if (s < 6) return "base.authorized";
            if (s < 12) return "base.window";
            if (s < 19) return "base.doors";
        } else {
            if (s < 5) return BaseLayout.small(p.silo) ? "base.small" : "base.medium";
            if (s < 9) return "base.doors";
        }
        if (s < launch) return "base.countdown";
        if (s < launch + 5) return "base.liftoff";
        if (s < impact) return "base.flight";
        if (s < impact + 5) return "nuke.detonation";
        if (s < impact + 12) return "nuke.shockwave";
        if (s < p.duration / 20f - 9) return "nuke.mushroom";
        return "base.back";
    }

    /** Compuerta del silo: 0 cerrada, 1 abierta (se abre antes del despegue y se cierra después). */
    static float hatch(BasePlan p, float s) {
        boolean main = p.silo == 0;
        float open = main ? 13 : 5.5f;
        return smooth((s - open) / 5) - smooth((s - (p.launchSecond() + 16)) / 6);
    }

    /** Destello: blanco total, una baja, un segundo pico y se apaga (más largo cuanto más grande). */
    static float flash(float s) {
        float d = (s - p().impactSecond()) / Math.max(0.7f, size());
        if (d < 0) return 0;
        if (d < 0.35f) return 1f;
        if (d < 0.55f) return 0.78f;
        if (d < 0.8f) return 0.95f;
        return Math.max(0, 0.95f - (d - 0.8f) / 2.6f);
    }

    // ------------------------------------------------------------------ sonido

    static void sounds(float s) {
        int half = Cinematic.nextHalf(s);
        if (half < 0) return;
        BasePlan p = p();
        float t = half / 2f, launch = p.launchSecond(), impact = p.impactSecond();
        Vec3 top = p.siloTop();
        boolean main = p.silo == 0;
        if (half == 1) Cinematic.play(ModSounds.BEEP.get(), 0.9f, 0.6f);
        // alarma adentro y sirena afuera hasta el despegue
        if (t < launch + 2 && half % 4 == 0) Cinematic.play(ModSounds.ALARM.get(), main ? 0.85f : 1f, 0.55f);
        if (t >= (main ? 19 : 9) - 2 && t < launch + 4 && half % 16 == 0) Cinematic.playAt(ModSounds.SIREN.get(), top.add(0, 4, 0), 8f, 1f);
        // la compuerta
        float open = main ? 13 : 5.5f;
        if (t >= open && t < open + 5 && half % 4 == 0) Cinematic.playAt(ModSounds.HYDRAULIC.get(), top, 5f, 0.5f);
        if (t == open + 5) Cinematic.playAt(SoundEvents.IRON_DOOR_CLOSE, top, 5f, 0.35f);
        // cuenta regresiva
        if (t >= launch - 10 && t < launch && half % 2 == 0) Cinematic.play(ModSounds.BEEP.get(), t >= launch - 3 ? 1.25f : 1f, 0.9f);
        // ¡el despegue! rugido, estruendo y el motor que lo sigue
        if (t == launch) {
            Cinematic.play(ModSounds.BEEP.get(), 1.6f, 1f);
            float big = main ? 1f : BaseLayout.small(p.silo) ? 0.55f : 0.75f;
            Cinematic.play(ModSounds.MISSILE_LAUNCH.get(), 0.42f + (1 - big) * 0.4f, 1f);
            Cinematic.play(ModSounds.FAR_BOOM.get(), 0.7f + (1 - big) * 0.3f, 0.9f);
            Cinematic.playAt(SoundEvents.GENERIC_EXPLODE, top, 8f, 0.45f);
            if (!engineStarted) {
                engineStarted = true;
                Minecraft.getInstance().getSoundManager().play(new PathEngine(p, main ? 12f : 7f, main ? 0.45f : 0.6f + (1 - big) * 0.3f));
            }
        }
        if (t == launch + 2 || t == launch + 4) Cinematic.play(ModSounds.FAR_BOOM.get(), 0.5f, 0.5f);
        // la detonación
        if (t == impact - 3) Cinematic.playAt(ModSounds.FAR_BOOM.get(), Cinematic.target.add(0, 60, 0), 6f, 1.7f);
        if (t == impact + 1) Cinematic.play(ModSounds.NUKE_BLAST.get(), main ? 0.85f : 1.1f, 1f);
        if (t == impact + 7 || t == impact + 15) Cinematic.play(ModSounds.FAR_BOOM.get(), 0.45f, 0.8f);
        float a = s - impact;
        Vec3 cam = Cinematic.pose(0).pos();
        if (!windPlayed && a > 0 && NukeFx.shock(a) * size() >= cam.distanceTo(Cinematic.target)) {
            windPlayed = true;
            Cinematic.play(ModSounds.NUKE_WIND.get(), 1f, 1f);
            Cinematic.addQuake(s, cam, 2.4, 3.5, 60);
        }
    }

    static void particles(float s) {
        var level = Minecraft.getInstance().level;
        BasePlan p = p();
        if (level == null || p == null) return;
        var rnd = level.random;
        Vec3 top = p.siloTop();
        double r = silo()[2];
        float open = hatch(p, s);
        if (open > 0.3f && s < p.launchSecond()) {
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, top.x + (rnd.nextDouble() - 0.5) * r * 1.6, top.y - 1,
                        top.z + (rnd.nextDouble() - 0.5) * r * 1.6, 0, 0.12 + rnd.nextDouble() * 0.1, 0);
            }
        }
        float ig = s - p.launchSecond();
        if (ig >= 0 && ig < 10) {
            int n = p.silo == 0 ? 14 : 6;
            for (int i = 0; i < n; i++) {
                level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true,
                        top.x + (rnd.nextDouble() - 0.5) * r * 2, top.y, top.z + (rnd.nextDouble() - 0.5) * r * 2,
                        (rnd.nextDouble() - 0.5) * 0.8, 0.6 + rnd.nextDouble() * 0.7, (rnd.nextDouble() - 0.5) * 0.8);
            }
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, top.x, top.y + 1, top.z, 0, 0.3, 0);
        }
    }

    /** El motor del misil: un sonido que viaja por el camino del misil, sin depender de la entidad. */
    private static final class PathEngine extends AbstractTickableSoundInstance {
        private final BasePlan plan;
        private final long start;

        PathEngine(BasePlan plan, float volume, float pitch) {
            super(ModSounds.MISSILE_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.plan = plan;
            this.start = Cinematic.clientTicks();
            looping = true;
            delay = 0;
            this.volume = volume;
            this.pitch = pitch;
            Vec3 q = plan.path.p0;
            x = q.x;
            y = q.y;
            z = q.z;
        }

        @Override
        public void tick() {
            long t = Cinematic.clientTicks() - start;
            if (t >= plan.path.flightTicks || !Cinematic.isActive()) {
                stop();
                return;
            }
            Vec3 q = plan.path.atTick(t);
            x = q.x;
            y = q.y;
            z = q.z;
        }
    }
}
