package com.amatista.arsenal.client;

import com.amatista.arsenal.PhobosTimeline;
import com.amatista.arsenal.Timeline;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.amatista.arsenal.client.Cinematic.bezier;
import static com.amatista.arsenal.client.Cinematic.easeInCubic;
import static com.amatista.arsenal.client.Cinematic.smooth;

/**
 * "Protocolo Fobos": la cámara sale de la Tierra, viaja a toda velocidad hasta Marte, un misil
 * golpea a Fobos, la luna gira cada vez más rápido usando la gravedad de Marte como honda y sale
 * disparada de vuelta hasta estrellarse en el punto marcado.
 *
 * Escena espacial: la Tierra en el origen (radio 1000, objetivo arriba), Marte lejos en -Z.
 */
final class PhobosDirector {
    static final Vec3 EARTH_TARGET = Cinematic.TARGET_SPACE;
    static final float MARS_RADIUS = 700f;
    static final Vec3 MARS = new Vec3(0, 0, -42000);
    /** Vueltas que da Fobos durante la honda gravitatoria. */
    static final float TURNS = 4f;
    static final float ORBIT_START = 1500f;
    static final float ORBIT_END = 1050f;
    /** Dirección desde la que cae Fobos en el mundo real. */
    static final Vec3 METEOR_DIR = new Vec3(-0.45, 0.8, -0.4).normalize();
    static final double METEOR_START_DIST = 1500;

    private PhobosDirector() {}

    // ------------------------------------------------------------------ Fobos

    /** Fracción de la honda (0..1) o -1 si aún no empieza. */
    static float slingProgress(float t) {
        if (t < PhobosTimeline.MISSILE_END) return -1f;
        return Mth.clamp((t - PhobosTimeline.MISSILE_END) / (PhobosTimeline.SLING_END - PhobosTimeline.MISSILE_END), 0f, 1f);
    }

    static float releaseProgress(float t) {
        return Mth.clamp((t - PhobosTimeline.SLING_END) / (PhobosTimeline.SPACE_END - PhobosTimeline.SLING_END), 0f, 1f);
    }

    /** Ángulo de Fobos en su órbita (termina exactamente en 0, con la velocidad apuntando a la Tierra). */
    static double orbitAngle(float t) {
        double secs = t * Cinematic.durationSeconds();
        double slingStartSecs = PhobosTimeline.MISSILE_END * Cinematic.durationSeconds();
        if (t < PhobosTimeline.MISSILE_END) {
            return -Math.PI * 2 * TURNS + 0.04 * (secs - slingStartSecs);
        }
        float u = slingProgress(t);
        double f = Math.pow(u, 2.6);
        return -Math.PI * 2 * TURNS * (1 - f);
    }

    static float orbitRadius(float t) {
        float u = Math.max(0f, slingProgress(t));
        return Mth.lerp(smooth(u), ORBIT_START, ORBIT_END);
    }

    /** Posición de Fobos en la escena espacial. */
    static Vec3 phobosPosition(float t) {
        if (t >= PhobosTimeline.SLING_END) {
            float u = releaseProgress(t);
            float s = u * 0.65f + u * u * 0.35f;
            Vec3 p0 = MARS.add(ORBIT_END, 0, 0);
            Vec3 p1 = p0.add(0, 0, 16000);
            Vec3 p2 = EARTH_TARGET.add(800, 5200, 7000);
            Vec3 p3 = EARTH_TARGET.add(0, 70, 0);
            return cubic(p0, p1, p2, p3, s);
        }
        double a = orbitAngle(t);
        float r = orbitRadius(t);
        return MARS.add(Math.cos(a) * r, Math.sin(a * 0.5) * r * 0.05, Math.sin(a) * r);
    }

    /** Dirección en la que se mueve Fobos. */
    static Vec3 phobosVelocityDir(float t) {
        float dt = 0.0015f;
        Vec3 a = phobosPosition(t), b = phobosPosition(Math.min(1f, t + dt));
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) {
            double ang = orbitAngle(t);
            return new Vec3(-Math.sin(ang), 0, Math.cos(ang));
        }
        return d.normalize();
    }

    /** Velocidad "de pantalla" para la telemetría (km/s, inventada pero coherente). */
    static float phobosSpeedKms(float t) {
        if (t < PhobosTimeline.MISSILE_HIT) return 2.1f;
        if (t < PhobosTimeline.MISSILE_END) return 2.6f;
        if (t < PhobosTimeline.SLING_END) {
            float u = slingProgress(t);
            return 2.6f + 38f * (float) Math.pow(u, 1.8);
        }
        return 41f + 30f * releaseProgress(t);
    }

    /** Calor (0..1): grietas encendidas tras el misil, cada vez más al acelerar, fuego al entrar en la atmósfera. */
    static float heat(float t) {
        if (t < PhobosTimeline.MISSILE_HIT) return 0f;
        if (t < PhobosTimeline.MISSILE_END) return 0.18f;
        if (t < PhobosTimeline.SLING_END) return 0.18f + 0.5f * smooth(slingProgress(t));
        return 0.68f + 0.32f * smooth((releaseProgress(t) - 0.55f) / 0.45f);
    }

    /** Giro propio de Fobos (da vueltas sobre sí misma cada vez más rápido). */
    static float spin(float t) {
        float secs = t * Cinematic.durationSeconds();
        float extra = 0f;
        if (t >= PhobosTimeline.MISSILE_HIT) extra += (secs - PhobosTimeline.MISSILE_HIT * Cinematic.durationSeconds()) * 0.6f;
        if (t >= PhobosTimeline.MISSILE_END) extra += (float) Math.pow(slingProgress(t), 2) * 40f;
        return secs * 0.05f + extra;
    }

    // ------------------------------------------------------------------ misil

    static Vec3 missileTarget() {
        return phobosPosition(PhobosTimeline.MISSILE_HIT);
    }

    static Vec3 missileStart() {
        return missileTarget().add(-2600, 700, 1800);
    }

    /** Fracción del vuelo del misil (0..1) o -1 fuera del vuelo. */
    static float missileProgress(float t) {
        if (t < PhobosTimeline.MARS_END || t > PhobosTimeline.MISSILE_HIT) return -1f;
        return (t - PhobosTimeline.MARS_END) / (PhobosTimeline.MISSILE_HIT - PhobosTimeline.MARS_END);
    }

    static Vec3 missilePosition(float u) {
        float e = u * u * 0.6f + u * 0.4f;
        Vec3 a = missileStart(), b = missileTarget();
        Vec3 mid = a.lerp(b, 0.5).add(0, 350, 0);
        return bezier(a, mid, b, e);
    }

    // ------------------------------------------------------------------ cámara en el espacio

    static Cinematic.SpacePose spacePose(float t) {
        if (t < 0.215f) {
            float e = smooth((t - PhobosTimeline.SPACE_START) / (0.215f - PhobosTimeline.SPACE_START));
            Vec3 eye = EARTH_TARGET.add(0, 80, 0).lerp(EARTH_TARGET.add(30, 260, 60), e);
            return new Cinematic.SpacePose(eye, EARTH_TARGET, 75f, 0f);
        }
        if (t < PhobosTimeline.TRAVEL_END) {
            // viaje a toda velocidad: primero se mira la Tierra alejarse, luego Marte acercarse
            float u = (t - 0.215f) / (PhobosTimeline.TRAVEL_END - 0.215f);
            float e = smooth(u);
            Vec3 eye = cubic(EARTH_TARGET.add(30, 260, 60), new Vec3(0, 2600, -3000),
                    MARS.add(0, 1500, 9500), MARS.add(0, 900, 4300), e);
            float look = smooth((u - 0.15f) / 0.3f);
            Vec3 center = Vec3.ZERO.lerp(MARS, look);
            return new Cinematic.SpacePose(eye, center, 70f + 25f * warp(t), (float) Math.sin(u * 6) * 6f * warp(t));
        }
        if (t < PhobosTimeline.MARS_END) {
            float u = (t - PhobosTimeline.TRAVEL_END) / (PhobosTimeline.MARS_END - PhobosTimeline.TRAVEL_END);
            Vec3 p = phobosPosition(t);
            if (u < 0.5f) {
                // llegada: Marte enorme con Fobos delante
                float e = smooth(u / 0.5f);
                Vec3 eye = MARS.add(0, 900, 4300).lerp(MARS.add(1900, 520, 2500), e);
                Vec3 center = MARS.lerp(p, 0.25 * e);
                return new Cinematic.SpacePose(eye, center, 60f, 0f);
            }
            // primer plano de Fobos: cráteres y surcos
            float v = (u - 0.5f) / 0.5f;
            double ang = 0.6 + v * 1.4;
            double radius = Mth.lerp(smooth(Math.min(1f, v * 2.5f)), 1400f, 230f);
            Vec3 eye = p.add(Math.cos(ang) * radius, 70 + 40 * v, Math.sin(ang) * radius);
            if (v < 0.4f) eye = MARS.add(1900, 520, 2500).lerp(eye, smooth(v / 0.4f));
            return new Cinematic.SpacePose(eye, p, 55f, 0f);
        }
        if (t < PhobosTimeline.MISSILE_END) {
            // el misil cruza el espacio y se estrella contra Fobos
            Vec3 p = phobosPosition(t);
            Vec3 eye = missileTarget().add(430, 170, 540);
            float mu = missileProgress(t);
            Vec3 center = mu >= 0f ? missilePosition(mu).lerp(p, 0.35 + 0.65 * mu) : p;
            return new Cinematic.SpacePose(eye, center, 58f, 0f);
        }
        if (t < PhobosTimeline.SLING_END) {
            float u = slingProgress(t);
            Vec3 p = phobosPosition(t);
            Vec3 v = phobosVelocityDir(t);
            Vec3 wideEye = MARS.add(0, 3600, 1700);
            Vec3 rideEye = p.subtract(v.scale(340)).add(0, 130, 0);
            Vec3 rideCenter = p.add(v.scale(420));
            float blend = smooth((u - 0.45f) / 0.12f);
            Vec3 eye = wideEye.lerp(rideEye, blend);
            Vec3 center = MARS.lerp(rideCenter, blend);
            return new Cinematic.SpacePose(eye, center, Mth.lerp(blend, 62f, 80f), blend * (float) Math.sin(u * 40) * 4f);
        }
        // salida disparada hacia la Tierra
        float u = releaseProgress(t);
        Vec3 p = phobosPosition(t);
        Vec3 v = phobosVelocityDir(t);
        if (u < 0.6f) {
            Vec3 eye = p.subtract(v.scale(360)).add(0, 120, 0);
            return new Cinematic.SpacePose(eye, p.add(v.scale(700)), 85f, (float) Math.sin(u * 30) * 3f);
        }
        // desde la Tierra se ve llegar la bola de fuego
        float e = smooth((u - 0.6f) / 0.4f);
        Vec3 eye = EARTH_TARGET.add(320, 760, 980).lerp(EARTH_TARGET.add(160, 260, 420), e);
        return new Cinematic.SpacePose(eye, p.lerp(EARTH_TARGET, 0.25 + 0.5 * e), 65f, 0f);
    }

    /** Intensidad del efecto de velocidad (líneas de estrellas): 0..1. */
    static float warp(float t) {
        if (t >= 0.215f && t < PhobosTimeline.TRAVEL_END) {
            float u = (t - 0.215f) / (PhobosTimeline.TRAVEL_END - 0.215f);
            return (float) Math.pow(Math.sin(Math.PI * u), 1.5);
        }
        if (t >= PhobosTimeline.SLING_END && t < PhobosTimeline.SPACE_END) {
            float u = releaseProgress(t);
            return u < 0.6f ? 0.85f : 0f;
        }
        return 0f;
    }

    // ------------------------------------------------------------------ cámara en el mundo

    /** Posición de la roca cayendo en el mundo (f = 0 al aparecer, 1 en el impacto). */
    static Vec3 meteorWorldPos(Vec3 target, float f) {
        f = Mth.clamp(f, 0f, 1f);
        float e = (float) Math.pow(f, 1.35);
        return target.add(METEOR_DIR.scale(METEOR_START_DIST * (1 - e)));
    }

    static Cinematic.Pose worldPose(float t) {
        Vec3 eye = Cinematic.playerEye();
        Vec3 tc = Cinematic.targetCenter();
        if (t < PhobosTimeline.P2_END) {
            // los dos primeros actos son como los del satélite, en menos tiempo
            float ts = t < PhobosTimeline.P1_END
                    ? t / PhobosTimeline.P1_END * Timeline.A1_END
                    : Timeline.A1_END + (t - PhobosTimeline.P1_END) / (PhobosTimeline.P2_END - PhobosTimeline.P1_END) * (Timeline.A2_END - Timeline.A1_END);
            return Cinematic.satelliteWorldPose(ts);
        }
        if (t < PhobosTimeline.SPACE_END) {
            return new Cinematic.Pose(eye.add(0, 3, 0), Cinematic.playerYaw(), 90f, 0f, 70f);
        }
        if (t < PhobosTimeline.IMPACT) {
            // la bola de fuego cae del cielo: la cámara mira hacia arriba y la sigue
            float u = (t - PhobosTimeline.SPACE_END) / (PhobosTimeline.IMPACT - PhobosTimeline.SPACE_END);
            float f = (t - PhobosTimeline.WORLD_START) / (PhobosTimeline.IMPACT - PhobosTimeline.WORLD_START);
            Vec3 meteor = meteorWorldPos(tc, f);
            Vec3 cam = tc.add(120, 32, 95).lerp(tc.add(150, 55, 120), smooth(u));
            Vec3 look = meteor.lerp(tc, smooth((u - 0.5f) / 0.5f) * 0.6);
            Cinematic.Pose p = Cinematic.lookAt(cam, look, 0f, Mth.lerp(u, 75f, 62f));
            return Cinematic.shake(p, 0.1f + 0.9f * u * u, t);
        }
        if (t < PhobosTimeline.RETURN_START) {
            float u = (t - PhobosTimeline.IMPACT) / (PhobosTimeline.RETURN_START - PhobosTimeline.IMPACT);
            float e = smooth(u);
            double ang = Math.atan2(95, 120) + u * Math.toRadians(50);
            Vec3 start = tc.add(Math.sin(ang) * 150, 55, Math.cos(ang) * 150);
            Vec3 aerial = tc.add(Math.sin(ang) * 120, 190, Math.cos(ang) * 120);
            Cinematic.Pose p = Cinematic.lookAt(start.lerp(aerial, e), tc, 0f, 55f + 15f * e);
            return Cinematic.shake(p, 3.2f * (float) Math.pow(1 - Math.min(1f, u * 2.2f), 2), t);
        }
        float u = (t - PhobosTimeline.RETURN_START) / (1f - PhobosTimeline.RETURN_START);
        float e = smooth(u);
        double ang = Math.atan2(95, 120) + Math.toRadians(50);
        Vec3 aerial = tc.add(Math.sin(ang) * 120, 190, Math.cos(ang) * 120);
        Vec3 pos = bezier(aerial, aerial.lerp(eye, 0.5).add(0, 40, 0), eye, e);
        Cinematic.Pose look = Cinematic.lookAt(pos, tc, 0f, 70f);
        float k = smooth(Math.max(0f, (u - 0.4f) / 0.6f));
        return new Cinematic.Pose(pos, Cinematic.lerpAngle(look.yaw(), Cinematic.playerYaw(), k),
                Mth.lerp(k, look.pitch(), Cinematic.playerPitch()), 0f, 70f);
    }

    // ------------------------------------------------------------------ sonidos

    static void buildCues() {
        Cinematic.cue(0.0f, SoundEvents.BEACON_POWER_SELECT, 0.9f, 0.9f);
        for (int i = 0; i < HudRenderer.CONSOLE_LINES; i++) {
            Cinematic.cue(PhobosHud.consoleLineStart(i), SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f + i * 0.07f, 1.0f);
        }
        Cinematic.cue(PhobosTimeline.P1_END - 0.008f, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.0f);
        Cinematic.cue(PhobosTimeline.P1_END, SoundEvents.ELYTRA_FLYING, 0.6f, 0.9f);
        Cinematic.cue(PhobosTimeline.P1_END + 0.05f, SoundEvents.ELYTRA_FLYING, 0.85f, 0.9f);
        Cinematic.cue(PhobosTimeline.SPACE_START - 0.002f, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.0f);
        // salto a gran velocidad
        Cinematic.cue(0.22f, SoundEvents.END_PORTAL_SPAWN, 0.8f, 0.6f);
        Cinematic.cue(0.23f, SoundEvents.ELYTRA_FLYING, 1.4f, 1.0f);
        Cinematic.cue(0.26f, SoundEvents.ELYTRA_FLYING, 1.6f, 1.0f);
        Cinematic.cue(0.30f, SoundEvents.ELYTRA_FLYING, 1.2f, 1.0f);
        Cinematic.cue(PhobosTimeline.TRAVEL_END - 0.01f, SoundEvents.BEACON_DEACTIVATE, 0.7f, 1.0f);
        // Marte
        Cinematic.cue(PhobosTimeline.TRAVEL_END + 0.005f, SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 0.35f);
        // misil
        Cinematic.cue(PhobosTimeline.MARS_END, SoundEvents.FIRECHARGE_USE, 0.6f, 1.0f);
        Cinematic.cue(PhobosTimeline.MARS_END + 0.003f, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5f, 1.0f);
        Cinematic.cue(PhobosTimeline.MARS_END + 0.03f, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7f, 0.8f);
        Cinematic.cue(PhobosTimeline.MISSILE_HIT, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.0f);
        Cinematic.cue(PhobosTimeline.MISSILE_HIT, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 0.9f);
        Cinematic.cue(PhobosTimeline.MISSILE_HIT + 0.003f, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 1.0f);
        // honda gravitatoria
        Cinematic.cue(PhobosTimeline.MISSILE_END, SoundEvents.CONDUIT_ACTIVATE, 0.5f, 1.0f);
        Cinematic.cue(PhobosTimeline.SLING_END - 0.08f, SoundEvents.WARDEN_SONIC_CHARGE, 0.5f, 1.0f);
        float countdown = HudRenderer.countdownWindow();
        for (int i = 0; i < 5; i++) {
            Cinematic.cue(PhobosTimeline.SLING_END - countdown + countdown * i / 5f, SoundEvents.ARROW_HIT_PLAYER, 0.8f + i * 0.1f, 1.0f);
        }
        // liberación
        Cinematic.cue(PhobosTimeline.SLING_END, SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.0f);
        Cinematic.cue(PhobosTimeline.SLING_END, SoundEvents.TRIDENT_THUNDER, 0.5f, 1.0f);
        Cinematic.cue(PhobosTimeline.SLING_END + 0.004f, SoundEvents.ELYTRA_FLYING, 1.5f, 1.0f);
        Cinematic.cue(PhobosTimeline.SLING_END + 0.06f, SoundEvents.BLAZE_SHOOT, 0.4f, 1.0f);
        // entrada en la atmósfera y caída
        Cinematic.cue(PhobosTimeline.SPACE_END, SoundEvents.FIRECHARGE_USE, 0.3f, 1.0f);
        Cinematic.cue(PhobosTimeline.SPACE_END + 0.003f, SoundEvents.ELYTRA_FLYING, 1.0f, 1.0f);
        Cinematic.cue(PhobosTimeline.SPACE_END + 0.03f, SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 0.7f);
        // impacto
        Cinematic.cue(PhobosTimeline.IMPACT, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.0f);
        Cinematic.cue(PhobosTimeline.IMPACT, SoundEvents.GENERIC_EXPLODE, 0.3f, 1.0f);
        Cinematic.cue(PhobosTimeline.IMPACT + 0.003f, SoundEvents.WITHER_SPAWN, 0.5f, 0.6f);
        Cinematic.cue(PhobosTimeline.RETURN_START - 0.02f, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.8f);
        Cinematic.cue(0.99f, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
    }

    static void ambientSounds(float t, long tick) {
        if (t >= PhobosTimeline.SPACE_START && t < PhobosTimeline.SPACE_END && tick % 60 == 0) {
            Cinematic.play(SoundEvents.BEACON_AMBIENT, 0.45f, 1.0f);
        }
        if (warp(t) > 0.4f && tick % 12 == 0) Cinematic.play(SoundEvents.ELYTRA_FLYING, 1.2f + warp(t) * 0.5f, 0.5f);
        if (t >= PhobosTimeline.MISSILE_END && t < PhobosTimeline.SLING_END) {
            float u = slingProgress(t);
            int every = Math.max(4, (int) (40 - 36 * u));
            if (tick % every == 0) Cinematic.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.4f + 1.6f * u, 0.7f);
        }
        if (t >= PhobosTimeline.SLING_END && t < PhobosTimeline.IMPACT && tick % 8 == 0) {
            Cinematic.play(SoundEvents.FIRECHARGE_USE, 0.4f, 0.45f);
        }
        if (t >= PhobosTimeline.IMPACT && t < 0.99f && tick % 25 == 0) Cinematic.play(SoundEvents.FIRE_AMBIENT, 0.6f, 0.9f);
    }

    // ------------------------------------------------------------------ utilidades

    static Vec3 cubic(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float t) {
        Vec3 ab = a.lerp(b, t), bc = b.lerp(c, t), cd = c.lerp(d, t);
        return ab.lerp(bc, t).lerp(bc.lerp(cd, t), t);
    }

    @SuppressWarnings("unused")
    private static float unused() {
        return easeInCubic(0);
    }
}
