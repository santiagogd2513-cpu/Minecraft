package com.amatista.arsenal.client;

import com.amatista.arsenal.Timeline;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.amatista.arsenal.StarTimeline.*;
import static com.amatista.arsenal.client.Cinematic.bezier;
import static com.amatista.arsenal.client.Cinematic.smooth;

/**
 * "Protocolo Prometeo": viaje lejísimos (sistema solar, Vía Láctea, Andrómeda, un cúmulo de
 * galaxias, la mitad de la nada) hasta la hipergigante azul «Zafiro». La estación Prometeo absorbe
 * su energía, carga su cañón y dispara el láser más grande del universo, que cruza todo hasta la Tierra.
 *
 * En la escena de la estrella, la estrella está en el origen.
 */
final class StarDirector {
    enum Scene { SOLAR, MILKY_WAY, ANDROMEDA, FIELD, VOID, STAR, CHASE, EARTH }

    static final float STAR_R = 6000f;
    static final Vec3 D = new Vec3(0.3, 0.12, 1).normalize();          // hacia dónde apunta el cañón
    static final Vec3 X = D.cross(new Vec3(0, 1, 0)).normalize();       // lateral de la estación
    static final Vec3 U = X.cross(D).normalize();                       // "arriba" de la estación
    static final float SC = 30f;                                         // escala de la estación
    static final Vec3 STATION = D.scale(17000);
    static final Vec3 MUZZLE = STATION.add(D.scale(178 * SC));
    static final Vec3 DISH = STATION.subtract(D.scale(95 * SC));
    static final Vec3 SIPHON = D.scale(STAR_R);                         // punto de la estrella que se absorbe
    /** Dirección desde la que llega el rayo a la Tierra (escena de la Tierra). */
    static final Vec3 EARTH_BEAM_DIR = new Vec3(0.35, 0.85, 0.4).normalize();

    private StarDirector() {}

    static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    static Scene scene(float t) {
        if (t < SOLAR_END) return Scene.SOLAR;
        if (t < MILKY_END) return Scene.MILKY_WAY;
        if (t < ANDROMEDA_END) return Scene.ANDROMEDA;
        if (t < FIELD_END) return Scene.FIELD;
        if (t < VOID_END) return Scene.VOID;
        if (t < FIRE_END) return Scene.STAR;
        if (t < CHASE_END) return Scene.CHASE;
        return Scene.EARTH;
    }

    // ------------------------------------------------------------------ estado

    /** Carga del cañón (0..1). */
    static float charge(float t) {
        return smooth(seg(t, STATION_END, CHARGE_END));
    }

    /** Cuánto se ha disparado (0 antes del disparo). */
    static boolean fired(float t) {
        return t >= CHARGE_END;
    }

    /** Distancia que lleva recorrida el frente del rayo desde la boca del cañón. */
    static float beamFront(float t) {
        if (t < CHARGE_END) return 0f;
        float a = seg(t, CHARGE_END, FIRE_END);
        float b = seg(t, FIRE_END, CHASE_END);
        return 30000f * a + 2_000_000f * b * b;
    }

    static float warp(float t) {
        if (t >= SPACE_START && t < SOLAR_END) return 0.4f + 0.6f * seg(t, SPACE_START + 0.03f, SOLAR_END);
        if (t >= SOLAR_END && t < FIELD_END) return 0.9f;
        if (t >= FIELD_END && t < VOID_END) return 0.9f * (1f - smooth(seg(t, FIELD_END, FIELD_END + 0.015f)));
        if (t >= FIRE_END && t < CHASE_END) return 1f;
        return 0f;
    }

    // ------------------------------------------------------------------ cámara en el espacio

    static Cinematic.SpacePose spacePose(float t) {
        switch (scene(t)) {
            case SOLAR: return BlackHoleDirector.solarPose(seg(t, SPACE_START, SOLAR_END));
            case MILKY_WAY: return BlackHoleDirector.milkyPose(seg(t, SOLAR_END, MILKY_END));
            case ANDROMEDA: {
                float u = seg(t, MILKY_END, ANDROMEDA_END);
                Vec3 eye = bezier(new Vec3(0, 70000, 460000), new Vec3(130000, 25000, 60000), new Vec3(170000, -10000, -330000), u);
                Vec3 look = Vec3.ZERO.lerp(new Vec3(210000, -20000, -700000), smooth((u - 0.5f) / 0.5f));
                return new Cinematic.SpacePose(eye, look, 70f, 0f);
            }
            case FIELD: {
                float u = seg(t, ANDROMEDA_END, FIELD_END);
                Vec3 eye = new Vec3(Math.sin(u * 4) * 20000, Math.cos(u * 3) * 8000, 60000 - 720000 * u);
                return new Cinematic.SpacePose(eye, eye.add(0, 0, -100000), 78f, (float) Math.sin(u * 6) * 8f);
            }
            case VOID: {
                float u = seg(t, FIELD_END, VOID_END);
                Vec3 eye = new Vec3(0, 0, -2000 * u);
                return new Cinematic.SpacePose(eye, eye.add(0, 0, -1000), 60f, 0f);
            }
            case STAR: return starPose(t);
            case CHASE: {
                float front = beamFront(t);
                Vec3 f = MUZZLE.add(D.scale(front));
                Vec3 eye = f.add(X.scale(1700)).add(U.scale(650)).subtract(D.scale(2600));
                return new Cinematic.SpacePose(eye, f.add(D.scale(7000)), 82f, (float) Math.sin(t * 900) * 2f);
            }
            default: {
                float u = seg(t, CHASE_END, SPACE_END);
                Vec3 target = Cinematic.TARGET_SPACE;
                Vec3 eye = target.add(1500, 950, 1900).lerp(target.add(900, 600, 1200), smooth(u));
                return new Cinematic.SpacePose(eye, target.add(EARTH_BEAM_DIR.scale(900 * (1 - u))), 64f, 0f);
            }
        }
    }

    private static Cinematic.SpacePose starPose(float t) {
        if (t < APPROACH_END) {
            float u = seg(t, VOID_END, APPROACH_END);
            float e = smooth(u);
            Vec3 eye = new Vec3(-40000, 15000, 70000).lerp(new Vec3(-9500, 3200, 14500), e);
            return new Cinematic.SpacePose(eye, Vec3.ZERO, 66f, 0f);
        }
        if (t < FLYBY_END) {
            // vuelo rasante sobre la superficie: granulado, protuberancias y llamaradas
            float u = seg(t, APPROACH_END, FLYBY_END);
            double a0 = Math.atan2(14500, -9500);
            double a = a0 + u * 1.1;
            Vec3 eye = new Vec3(Math.cos(a), 0.18, Math.sin(a)).normalize().scale(Mth.lerp(smooth(Math.min(1f, u * 3)), 17600f, 7700f));
            if (u < 0.33f) eye = new Vec3(-9500, 3200, 14500).lerp(eye, smooth(u / 0.33f));
            double la = a + 0.55;
            Vec3 look = new Vec3(Math.cos(la), 0.1, Math.sin(la)).normalize().scale(STAR_R * 1.02);
            return new Cinematic.SpacePose(eye, look, 70f, (float) Math.sin(u * 4) * 6f);
        }
        if (t < STATION_END) {
            // aparece la estación Prometeo
            float u = seg(t, FLYBY_END, STATION_END);
            Vec3 wide = STATION.add(X.scale(9500)).add(U.scale(3200)).subtract(D.scale(1500));
            if (u < 0.35f) {
                Vec3 from = starPose(FLYBY_END - 0.0001f).eye();
                float e = smooth(u / 0.35f);
                return new Cinematic.SpacePose(bezier(from, from.add(wide).scale(0.5).add(U.scale(6000)), wide, e),
                        Vec3.ZERO.lerp(STATION.add(D.scale(1000)), e), 66f, 0f);
            }
            float e = smooth((u - 0.35f) / 0.65f);
            Vec3 eye = wide.lerp(STATION.add(X.scale(3000)).add(U.scale(900)).subtract(D.scale(3300)), smooth(Math.min(1f, e * 2.5f)));
            eye = eye.lerp(STATION.add(X.scale(2600)).add(U.scale(800)).add(D.scale(5600)), smooth((e - 0.4f) / 0.6f));
            Vec3 look = STATION.add(D.scale(Mth.lerp(e, -1500f, 7000f)));
            return new Cinematic.SpacePose(eye, look, 58f, 0f);
        }
        if (t < CHARGE_END) {
            float u = seg(t, STATION_END, CHARGE_END);
            if (u < 0.4f) {
                // la estación absorbe la energía de la estrella
                float e = smooth(u / 0.4f);
                Vec3 eye = STATION.add(X.scale(13000)).add(U.scale(6500)).subtract(D.scale(9000));
                Vec3 prev = STATION.add(X.scale(2600)).add(U.scale(800)).add(D.scale(5600));
                return new Cinematic.SpacePose(prev.lerp(eye, smooth(Math.min(1f, e * 3f))), STATION.subtract(D.scale(5200)), 66f, 0f);
            }
            if (u < 0.7f) {
                // los condensadores se encienden uno a uno
                float e = smooth((u - 0.4f) / 0.3f);
                Vec3 eye = STATION.add(X.scale(1500)).add(U.scale(650)).add(D.scale(Mth.lerp(e, -400f, 4600f)));
                return new Cinematic.SpacePose(eye, STATION.add(D.scale(Mth.lerp(e, 900f, 5600f))), 62f, 0f);
            }
            // detrás de la estación, mirando por el cañón: la boca brilla cada vez más
            float e = smooth((u - 0.7f) / 0.3f);
            Vec3 eye = STATION.subtract(D.scale(Mth.lerp(e, 4500f, 6500f))).add(U.scale(1600)).add(X.scale(900));
            return new Cinematic.SpacePose(eye, MUZZLE.add(D.scale(8000)), 64f - 8f * e, 0f);
        }
        // EL DISPARO, visto de lado
        Vec3 eye = STATION.add(X.scale(16000)).add(U.scale(4500)).add(D.scale(7000));
        return new Cinematic.SpacePose(eye, MUZZLE.add(D.scale(16000)), 70f, 0f);
    }

    // ------------------------------------------------------------------ cámara en el mundo

    static Cinematic.Pose worldPose(float t) {
        Vec3 eye = Cinematic.playerEye();
        Vec3 tc = Cinematic.targetCenter();
        if (t < S2_END) {
            float ts = t < S1_END ? t / S1_END * Timeline.A1_END
                    : Timeline.A1_END + (t - S1_END) / (S2_END - S1_END) * (Timeline.A2_END - Timeline.A1_END);
            return Cinematic.satelliteWorldPose(ts);
        }
        if (t < SPACE_END) return new Cinematic.Pose(eye.add(0, 3, 0), Cinematic.playerYaw(), 90f, 0f, 70f);
        if (t < BURN_START) {
            // de lejos: el rayo baja del cielo
            float u = seg(t, SPACE_END, BURN_START);
            Cinematic.Pose p = Cinematic.lookAt(tc.add(250, 70, 195), tc.add(0, 260 * (1 - smooth(u)), 0), 0f, 74f);
            return Cinematic.shake(p, 0.3f + 0.5f * u, t);
        }
        if (t < IMPACT) {
            // el rayo quema el suelo
            float u = seg(t, BURN_START, IMPACT);
            double ang = Math.atan2(195, 250) + u * Math.toRadians(100);
            double radius = 240 - 60 * u;
            Vec3 pos = tc.add(Math.sin(ang) * radius, 80 - 20 * u, Math.cos(ang) * radius);
            Cinematic.Pose p = Cinematic.lookAt(pos, tc.add(0, 25, 0), 0f, 66f - 10f * u);
            return Cinematic.shake(p, 0.6f + 1.6f * u * u, t);
        }
        if (t < RETURN_START) {
            float u = seg(t, IMPACT, RETURN_START);
            float e = smooth(u);
            double ang = Math.atan2(195, 250) + Math.toRadians(100) + u * Math.toRadians(40);
            Vec3 start = tc.add(Math.sin(ang) * 180, 60, Math.cos(ang) * 180);
            Vec3 aerial = tc.add(Math.sin(ang) * 150, 290, Math.cos(ang) * 150);
            Cinematic.Pose p = Cinematic.lookAt(start.lerp(aerial, e), tc, 0f, 60f + 10f * e);
            return Cinematic.shake(p, 4f * (float) Math.pow(1 - Math.min(1f, u * 2f), 2), t);
        }
        float u = seg(t, RETURN_START, 1f);
        float e = smooth(u);
        double ang = Math.atan2(195, 250) + Math.toRadians(140);
        Vec3 aerial = tc.add(Math.sin(ang) * 150, 290, Math.cos(ang) * 150);
        Vec3 pos = bezier(aerial, aerial.lerp(eye, 0.5).add(0, 60, 0), eye, e);
        Cinematic.Pose look = Cinematic.lookAt(pos, tc, 0f, 70f);
        float k = smooth(Math.max(0f, (u - 0.4f) / 0.6f));
        return new Cinematic.Pose(pos, Cinematic.lerpAngle(look.yaw(), Cinematic.playerYaw(), k),
                Mth.lerp(k, look.pitch(), Cinematic.playerPitch()), 0f, 70f);
    }

    // ------------------------------------------------------------------ sonidos

    static float consoleLineStart(int i) {
        return 0.004f + i * (S1_END - 0.016f) / HudRenderer.CONSOLE_LINES;
    }

    static void buildCues() {
        Cinematic.cue(0.0f, com.amatista.arsenal.registry.ModSounds.UI_CONFIRM.get(), 0.8f, 0.9f);
        for (int i = 0; i < HudRenderer.CONSOLE_LINES; i++) {
            Cinematic.cue(consoleLineStart(i), com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 1.0f + i * 0.08f, 1.0f);
        }
        Cinematic.cue(S1_END - 0.005f, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 1.0f, 1.0f);
        Cinematic.cue(S1_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 0.6f, 0.9f);
        Cinematic.cue(SPACE_START - 0.002f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.5f, 1.0f);
        Cinematic.cue(SOLAR_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.6f, 1.0f);
        Cinematic.cue(MILKY_END, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.7f, 0.6f);
        Cinematic.cue(ANDROMEDA_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.8f, 1.0f);
        Cinematic.cue(FIELD_END, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.4f, 1.0f);
        Cinematic.cue(VOID_END - 0.02f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.5f, 1.0f);
        Cinematic.cue(VOID_END, com.amatista.arsenal.registry.ModSounds.PLANET_GROAN.get(), 0.3f, 0.5f);
        Cinematic.cue(APPROACH_END, com.amatista.arsenal.registry.ModSounds.THRUSTER_IDLE.get(), 0.4f, 0.8f);
        Cinematic.cue(FLYBY_END, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.5f, 1.0f);
        Cinematic.cue(STATION_END, com.amatista.arsenal.registry.ModSounds.STATION_ONLINE.get(), 0.5f, 1.0f);
        Cinematic.cue(STATION_END + 0.005f, com.amatista.arsenal.registry.ModSounds.LOCK_ON.get(), 0.5f, 1.0f);
        for (int k = 0; k < 8; k++) {
            Cinematic.cue(STATION_END + (CHARGE_END - STATION_END) * (0.4f + k * 0.035f), com.amatista.arsenal.registry.ModSounds.UI_CONFIRM.get(), 0.6f + k * 0.12f, 1.0f);
        }
        Cinematic.cue(CHARGE_END - 0.05f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.4f, 1.0f);
        Cinematic.cue(CHARGE_END - 0.025f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.7f, 1.0f);
        float countdown = HudRenderer.countdownWindow();
        for (int i = 0; i < 5; i++) {
            Cinematic.cue(CHARGE_END - countdown + countdown * i / 5f, com.amatista.arsenal.registry.ModSounds.COUNTDOWN_TICK.get(), 0.7f + i * 0.1f, 1.0f);
        }
        Cinematic.cue(CHARGE_END, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.3f, 1.0f);
        Cinematic.cue(CHARGE_END, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), 0.3f, 1.0f);
        Cinematic.cue(CHARGE_END + 0.002f, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.4f, 1.0f);
        Cinematic.cue(CHARGE_END + 0.004f, com.amatista.arsenal.registry.ModSounds.ENERGY_CRACK.get(), 0.4f, 1.0f);
        Cinematic.cue(FIRE_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.8f, 1.0f);
        Cinematic.cue(CHASE_END, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.4f, 1.0f);
        Cinematic.cue(SPACE_END, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), 0.4f, 1.0f);
        Cinematic.cue(SPACE_END + 0.003f, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.5f, 0.8f);
        Cinematic.cue(BURN_START, com.amatista.arsenal.registry.ModSounds.PLASMA_WHOOSH.get(), 0.4f, 1.0f);
        Cinematic.cue(IMPACT - 0.01f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 1.1f, 1.0f);
        Cinematic.cue(IMPACT, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), 0.25f, 1.0f);
        Cinematic.cue(IMPACT, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.3f, 1.0f);
        Cinematic.cue(IMPACT + 0.003f, com.amatista.arsenal.registry.ModSounds.TITAN_ROAR.get(), 0.4f, 0.6f);
        Cinematic.cue(RETURN_START - 0.015f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.8f, 0.8f);
        Cinematic.cue(0.99f, com.amatista.arsenal.registry.ModSounds.UI_CONFIRM.get(), 1.0f, 0.7f);
    }

    static void ambientSounds(float t, long tick) {
        if (t >= SPACE_START && t < SPACE_END && t < FIELD_END && tick % 60 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.MACHINE_HUM.get(), 0.45f, 1.0f);
        if (warp(t) > 0.5f && tick % 12 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.3f + warp(t) * 0.5f, 0.45f);
        if (t >= VOID_END && t < CHARGE_END && tick % 40 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.THRUSTER_BURN.get(), 0.4f, 0.6f);
        if (t >= STATION_END && t < CHARGE_END) {
            float u = seg(t, STATION_END, CHARGE_END);
            int every = Math.max(4, (int) (36 - 32 * u));
            if (tick % every == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.CAPACITOR_CHARGE.get(), 0.4f + 1.6f * u, 0.8f);
        }
        if (t >= SPACE_END && t < IMPACT && tick % 10 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.MACHINE_HUM.get(), 1.5f, 0.8f);
        if (t >= IMPACT && t < 0.99f && tick % 25 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.PLASMA_CRACKLE.get(), 0.6f, 0.9f);
    }
}
