package com.amatista.arsenal.client;

import com.amatista.arsenal.BlackHoleTimeline;
import com.amatista.arsenal.Timeline;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.amatista.arsenal.BlackHoleTimeline.*;
import static com.amatista.arsenal.client.Cinematic.bezier;
import static com.amatista.arsenal.client.Cinematic.smooth;

/**
 * "Protocolo Singularidad": la cámara sale de la Tierra, cruza el sistema solar, sale de la Vía
 * Láctea, viaja hasta Andrómeda, entra en ella y llega a su agujero negro. Tres anillos (la estación
 * Áncora) arrancan una semilla de singularidad y abren un agujero de gusano hasta la Tierra, donde
 * un mini agujero negro absorbe todo y colapsa.
 *
 * Cada parte del viaje tiene su propia escena (ver {@link Scene}).
 */
final class BlackHoleDirector {
    enum Scene { SOLAR, MILKY_WAY, ANDROMEDA, WORMHOLE }

    // --- escena del sistema solar (la Tierra en el origen)
    static final Vec3 MARS_S = new Vec3(2600, 400, -16000);
    static final float MARS_R = 520f;
    static final Vec3 JUPITER = new Vec3(-7000, -900, -42000);
    static final float JUPITER_R = 2800f;
    static final Vec3 SATURN = new Vec3(8000, 1500, -80000);
    static final float SATURN_R = 2300f;

    // --- galaxias (centradas en el origen de su escena)
    static final float MILKY_RADIUS = 60000f;
    static final float ANDROMEDA_RADIUS = 70000f;

    // --- agujero negro (centro de Andrómeda, en el origen)
    static final float HORIZON = 300f;
    static final float DISK_IN = 520f;
    static final float DISK_OUT = 3000f;
    static final Vec3 DISK_NORMAL = new Vec3(0.15, 1, 0.1).normalize();
    static final Vec3[] GATES = {new Vec3(1500, 1200, 0), new Vec3(2400, 1600, 0), new Vec3(3300, 2000, 0)};
    static final float GATE_RADIUS = 520f;

    private BlackHoleDirector() {}

    static Scene scene(float t) {
        if (t < SOLAR_END) return Scene.SOLAR;
        if (t < MILKY_END) return Scene.MILKY_WAY;
        if (t < WORMHOLE_OPEN_END) return Scene.ANDROMEDA;
        return Scene.WORMHOLE;
    }

    static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    // ------------------------------------------------------------------ cámara en el espacio

    static Cinematic.SpacePose spacePose(float t) {
        switch (scene(t)) {
            case SOLAR: return solarPose(seg(t, SPACE_START, SOLAR_END));
            case MILKY_WAY: return milkyPose(seg(t, SOLAR_END, MILKY_END));
            case ANDROMEDA:
                if (t < INTERGALACTIC_END) return intergalacticPose(seg(t, MILKY_END, INTERGALACTIC_END));
                if (t < ANDROMEDA_END) return andromedaPose(seg(t, INTERGALACTIC_END, ANDROMEDA_END));
                return holePose(t);
            default: return new Cinematic.SpacePose(new Vec3(0, 0, 0), new Vec3(0, 0, -1000), 80f + 20f * warp(t), 0f);
        }
    }

    private static final Vec3[] SOLAR_POINTS = {
            Cinematic.TARGET_SPACE.add(30, 260, 60),
            new Vec3(300, 1600, -3500),
            MARS_S.add(1100, 350, 1500),
            JUPITER.add(4600, 900, 3400),
            SATURN.add(-6500, 1400, 5200),
            SATURN.add(-2000, 3500, -36000)};
    private static final float[] SOLAR_TIMES = {0f, 0.12f, 0.30f, 0.55f, 0.80f, 1f};

    private static Vec3 spline(Vec3[] pts, float[] times, float u) {
        int i = 0;
        while (i < times.length - 2 && u > times[i + 1]) i++;
        float local = Mth.clamp((u - times[i]) / (times[i + 1] - times[i]), 0f, 1f);
        Vec3 p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
        float t2 = local * local, t3 = t2 * local;
        // Catmull-Rom
        return p0.scale(-0.5 * t3 + t2 - 0.5 * local)
                .add(p1.scale(1.5 * t3 - 2.5 * t2 + 1))
                .add(p2.scale(-1.5 * t3 + 2 * t2 + 0.5 * local))
                .add(p3.scale(0.5 * t3 - 0.5 * t2));
    }

    private static Cinematic.SpacePose solarPose(float u) {
        Vec3 eye = spline(SOLAR_POINTS, SOLAR_TIMES, u);
        Vec3 earth = Vec3.ZERO;
        Vec3 ahead = SATURN.add(-2000, 3500, -90000);
        Vec3 look;
        if (u < 0.1f) look = Cinematic.TARGET_SPACE.lerp(earth, u / 0.1f);
        else if (u < 0.3f) look = earth.lerp(MARS_S, smooth((u - 0.1f) / 0.2f));
        else if (u < 0.55f) look = MARS_S.lerp(JUPITER, smooth((u - 0.3f) / 0.25f));
        else if (u < 0.8f) look = JUPITER.lerp(SATURN, smooth((u - 0.55f) / 0.25f));
        else look = SATURN.lerp(ahead, smooth((u - 0.8f) / 0.2f));
        return new Cinematic.SpacePose(eye, look, 68f + 20f * smooth((u - 0.85f) / 0.15f), (float) Math.sin(u * 9) * 3f);
    }

    private static Cinematic.SpacePose milkyPose(float u) {
        float e = smooth(u);
        Vec3 eye = bezier(new Vec3(36000, 250, 0), new Vec3(52000, 28000, 70000), new Vec3(0, 95000, 165000), e);
        Vec3 look = new Vec3(30000, 0, -20000).lerp(Vec3.ZERO, smooth(u * 1.6f));
        return new Cinematic.SpacePose(eye, look, 72f, 0f);
    }

    private static Cinematic.SpacePose intergalacticPose(float u) {
        float e = u * u * 0.5f + u * 0.5f;
        Vec3 eye = new Vec3(0, 70000, 460000).lerp(new Vec3(42000, 26000, 170000), e);
        return new Cinematic.SpacePose(eye, Vec3.ZERO, 66f + 18f * warp(MILKY_END + u * (INTERGALACTIC_END - MILKY_END)), 0f);
    }

    private static Cinematic.SpacePose andromedaPose(float u) {
        // entra en el disco de Andrómeda atravesando sus brazos y se dirige al centro
        Vec3 eye = Cinematic.bezier(new Vec3(42000, 26000, 170000), new Vec3(36000, 4000, 40000), new Vec3(5200, 900, 6500), smooth(u));
        Vec3 look = new Vec3(0, 0, 0).lerp(new Vec3(0, 0, 0), 1);
        return new Cinematic.SpacePose(eye, look, 70f, (float) Math.sin(u * 7) * 5f);
    }

    private static Cinematic.SpacePose holePose(float t) {
        if (t < HOLE_ORBIT_END) {
            // vuelta alrededor del agujero negro, casi rasante al disco
            float u = seg(t, ANDROMEDA_END, HOLE_ORBIT_END);
            float e = smooth(u);
            double ang = Math.atan2(6500, 5200) + e * Math.PI * 1.25;
            double radius = Mth.lerp(e, 8300f, 4300f);
            Vec3 eye = new Vec3(Math.cos(ang) * radius, Mth.lerp(e, 900f, 520f), Math.sin(ang) * radius);
            if (u < 0.15f) eye = new Vec3(5200, 900, 6500).lerp(eye, smooth(u / 0.15f));
            return new Cinematic.SpacePose(eye, Vec3.ZERO, 62f, 4f);
        }
        if (t < EXTRACT_END) {
            // los anillos se encienden y arrancan la semilla de singularidad
            float u = seg(t, HOLE_ORBIT_END, EXTRACT_END);
            Vec3 start = holePose(HOLE_ORBIT_END - 0.0001f).eye();
            Vec3 wide = new Vec3(2600, 3000, 5200);
            Vec3 eye = start.lerp(wide, smooth(u / 0.3f));
            Vec3 look = Vec3.ZERO.lerp(GATES[1].scale(0.5), smooth(u));
            return new Cinematic.SpacePose(eye, look, 58f, 0f);
        }
        // los anillos se alinean, se abre el agujero de gusano y la cámara se lanza dentro con la semilla
        float u = seg(t, EXTRACT_END, WORMHOLE_OPEN_END);
        Vec3 seed = seedPosition(t);
        Vec3 dir = GATES[2].subtract(GATES[0]).normalize();
        Vec3 eye = new Vec3(2600, 3000, 5200).lerp(seed.subtract(dir.scale(700)).add(0, 180, 0), smooth(u / 0.5f));
        Vec3 look = GATES[1].lerp(GATES[2].add(dir.scale(2000)), smooth(u));
        return new Cinematic.SpacePose(eye, look, 58f + 30f * smooth((u - 0.6f) / 0.4f), 0f);
    }

    /** La semilla: sale del borde del horizonte y viaja por los anillos. */
    static Vec3 seedPosition(float t) {
        Vec3 out = GATES[0].subtract(Vec3.ZERO).normalize().scale(HORIZON * 1.05);
        if (t < HOLE_ORBIT_END + (EXTRACT_END - HOLE_ORBIT_END) * 0.35f) return out;
        if (t < EXTRACT_END) {
            float u = seg(t, HOLE_ORBIT_END + (EXTRACT_END - HOLE_ORBIT_END) * 0.35f, EXTRACT_END);
            return out.lerp(GATES[0].scale(0.7), smooth(u));
        }
        float u = seg(t, EXTRACT_END, WORMHOLE_OPEN_END);
        Vec3 a = GATES[0].scale(0.7);
        Vec3 dir = GATES[2].subtract(GATES[0]).normalize();
        return a.lerp(GATES[2].add(dir.scale(400)), u * u);
    }

    /** Energía de los anillos (0..1). */
    static float gatePower(float t) {
        if (t < HOLE_ORBIT_END) return 0f;
        if (t < EXTRACT_END) return smooth(seg(t, HOLE_ORBIT_END, HOLE_ORBIT_END + 0.012f));
        return 1f;
    }

    /** Apertura del agujero de gusano entre los anillos (0..1). */
    static float portal(float t) {
        return smooth(seg(t, EXTRACT_END, EXTRACT_END + 0.02f));
    }

    static float warp(float t) {
        if (t >= SPACE_START && t < SOLAR_END) {
            float u = seg(t, SPACE_START, SOLAR_END);
            return 0.25f + 0.75f * smooth((u - 0.82f) / 0.18f);
        }
        if (t >= SOLAR_END && t < MILKY_END) return 0.9f * (1f - smooth(seg(t, SOLAR_END, SOLAR_END + 0.02f))) + 0.15f;
        if (t >= MILKY_END && t < INTERGALACTIC_END) return (float) Math.pow(Math.sin(Math.PI * seg(t, MILKY_END, INTERGALACTIC_END)), 1.2);
        if (t >= INTERGALACTIC_END && t < ANDROMEDA_END) return 0.35f * (1f - seg(t, INTERGALACTIC_END, ANDROMEDA_END));
        if (t >= WORMHOLE_OPEN_END && t < SPACE_END) return 1f;
        return 0f;
    }

    // ------------------------------------------------------------------ cámara en el mundo

    /** Posición de la singularidad en el mundo (f = 0 al aparecer, 1 en el colapso). */
    static Vec3 singularityWorldPos(Vec3 target, float f) {
        float descend = (PULL_START - WORLD_START) / (IMPACT - WORLD_START);
        float d = Mth.clamp(f / descend, 0f, 1f);
        float e = 1f - (1f - d) * (1f - d);
        return target.add(0, 14 + 300 * (1 - e), 0);
    }

    static Cinematic.Pose worldPose(float t) {
        Vec3 eye = Cinematic.playerEye();
        Vec3 tc = Cinematic.targetCenter();
        if (t < B2_END) {
            float ts = t < B1_END ? t / B1_END * Timeline.A1_END
                    : Timeline.A1_END + (t - B1_END) / (B2_END - B1_END) * (Timeline.A2_END - Timeline.A1_END);
            return Cinematic.satelliteWorldPose(ts);
        }
        if (t < SPACE_END) return new Cinematic.Pose(eye.add(0, 3, 0), Cinematic.playerYaw(), 90f, 0f, 70f);
        float f = (t - WORLD_START) / (IMPACT - WORLD_START);
        Vec3 sing = singularityWorldPos(tc, f);
        if (t < PULL_START) {
            // la singularidad baja del cielo
            Cinematic.Pose p = Cinematic.lookAt(tc.add(75, 26, 58), sing, 0f, 72f);
            return Cinematic.shake(p, 0.15f, t);
        }
        if (t < IMPACT) {
            // absorbe todo: la cámara la rodea despacio
            float u = seg(t, PULL_START, IMPACT);
            double ang = Math.atan2(58, 75) + u * Math.toRadians(160);
            double radius = 72 - 22 * u;
            Vec3 pos = tc.add(Math.sin(ang) * radius, 24 + 6 * Math.sin(u * 3), Math.cos(ang) * radius);
            Cinematic.Pose p = Cinematic.lookAt(pos, sing, (float) Math.sin(u * 10) * 3f * u, 70f - 14f * u);
            return Cinematic.shake(p, 0.2f + 1.2f * u * u, t);
        }
        if (t < RETURN_START) {
            float u = seg(t, IMPACT, RETURN_START);
            float e = smooth(u);
            double ang = Math.atan2(58, 75) + Math.toRadians(160) + u * Math.toRadians(40);
            Vec3 start = tc.add(Math.sin(ang) * 50, 30, Math.cos(ang) * 50);
            Vec3 aerial = tc.add(Math.sin(ang) * 110, 170, Math.cos(ang) * 110);
            Cinematic.Pose p = Cinematic.lookAt(start.lerp(aerial, e), tc, 0f, 58f + 12f * e);
            return Cinematic.shake(p, 3.5f * (float) Math.pow(1 - Math.min(1f, u * 2.2f), 2), t);
        }
        float u = seg(t, RETURN_START, 1f);
        float e = smooth(u);
        double ang = Math.atan2(58, 75) + Math.toRadians(200);
        Vec3 aerial = tc.add(Math.sin(ang) * 110, 170, Math.cos(ang) * 110);
        Vec3 pos = bezier(aerial, aerial.lerp(eye, 0.5).add(0, 40, 0), eye, e);
        Cinematic.Pose look = Cinematic.lookAt(pos, tc, 0f, 70f);
        float k = smooth(Math.max(0f, (u - 0.4f) / 0.6f));
        return new Cinematic.Pose(pos, Cinematic.lerpAngle(look.yaw(), Cinematic.playerYaw(), k),
                Mth.lerp(k, look.pitch(), Cinematic.playerPitch()), 0f, 70f);
    }

    // ------------------------------------------------------------------ sonidos

    static float consoleLineStart(int i) {
        return 0.005f + i * (B1_END - 0.018f) / HudRenderer.CONSOLE_LINES;
    }

    static void buildCues() {
        Cinematic.cue(0.0f, SoundEvents.BEACON_POWER_SELECT, 0.7f, 0.9f);
        for (int i = 0; i < HudRenderer.CONSOLE_LINES; i++) {
            Cinematic.cue(consoleLineStart(i), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + i * 0.07f, 1.0f);
        }
        Cinematic.cue(B1_END - 0.006f, SoundEvents.BEACON_ACTIVATE, 0.9f, 1.0f);
        Cinematic.cue(B1_END, SoundEvents.ELYTRA_FLYING, 0.6f, 0.9f);
        Cinematic.cue(SPACE_START - 0.002f, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.0f);
        Cinematic.cue(SPACE_START + 0.04f, SoundEvents.ELYTRA_FLYING, 1.2f, 0.9f);
        Cinematic.cue(SOLAR_END - 0.03f, SoundEvents.END_PORTAL_SPAWN, 0.9f, 0.6f);
        Cinematic.cue(SOLAR_END, SoundEvents.ELYTRA_FLYING, 1.6f, 1.0f);
        Cinematic.cue(MILKY_END, SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.7f);
        Cinematic.cue(MILKY_END + 0.01f, SoundEvents.ELYTRA_FLYING, 1.8f, 1.0f);
        Cinematic.cue(INTERGALACTIC_END, SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 0.5f);
        Cinematic.cue(ANDROMEDA_END, SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1.0f);
        Cinematic.cue(ANDROMEDA_END + 0.003f, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 0.5f);
        Cinematic.cue(HOLE_ORBIT_END, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.0f);
        Cinematic.cue(HOLE_ORBIT_END + 0.004f, SoundEvents.CONDUIT_ACTIVATE, 0.5f, 1.0f);
        Cinematic.cue(HOLE_ORBIT_END + 0.014f, SoundEvents.WARDEN_SONIC_CHARGE, 0.5f, 1.0f);
        Cinematic.cue(EXTRACT_END, SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.0f);
        Cinematic.cue(EXTRACT_END + 0.006f, SoundEvents.PORTAL_TRIGGER, 0.6f, 0.8f);
        Cinematic.cue(WORMHOLE_OPEN_END, SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.0f);
        Cinematic.cue(WORMHOLE_OPEN_END + 0.004f, SoundEvents.PORTAL_TRAVEL, 0.7f, 0.5f);
        Cinematic.cue(SPACE_END, SoundEvents.TRIDENT_THUNDER, 0.5f, 1.0f);
        Cinematic.cue(SPACE_END + 0.004f, SoundEvents.WARDEN_EMERGE, 0.5f, 1.0f);
        Cinematic.cue(PULL_START, SoundEvents.WARDEN_SONIC_CHARGE, 0.4f, 1.0f);
        Cinematic.cue(IMPACT - 0.012f, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        Cinematic.cue(IMPACT, SoundEvents.WARDEN_SONIC_BOOM, 0.3f, 1.0f);
        Cinematic.cue(IMPACT, SoundEvents.GENERIC_EXPLODE, 0.3f, 1.0f);
        Cinematic.cue(IMPACT + 0.003f, SoundEvents.WITHER_SPAWN, 0.4f, 0.6f);
        Cinematic.cue(RETURN_START - 0.015f, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.8f);
        Cinematic.cue(0.99f, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.7f);
    }

    static void ambientSounds(float t, long tick) {
        if (t >= SPACE_START && t < SPACE_END && tick % 60 == 0) Cinematic.play(SoundEvents.BEACON_AMBIENT, 0.4f, 1.0f);
        if (warp(t) > 0.5f && tick % 12 == 0) Cinematic.play(SoundEvents.ELYTRA_FLYING, 1.3f + warp(t) * 0.5f, 0.45f);
        if (t >= ANDROMEDA_END && t < WORMHOLE_OPEN_END && tick % 22 == 0) Cinematic.play(SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1.0f);
        if (t >= WORMHOLE_OPEN_END && t < SPACE_END && tick % 16 == 0) Cinematic.play(SoundEvents.PORTAL_AMBIENT, 1.4f, 0.6f);
        if (t >= PULL_START && t < IMPACT) {
            float u = seg(t, PULL_START, IMPACT);
            int every = Math.max(4, (int) (30 - 26 * u));
            if (tick % every == 0) Cinematic.play(SoundEvents.WARDEN_HEARTBEAT, 0.5f + u, 1.0f);
        }
        if (t >= IMPACT && t < 0.99f && tick % 25 == 0) Cinematic.play(SoundEvents.FIRE_AMBIENT, 0.6f, 0.8f);
    }
}
