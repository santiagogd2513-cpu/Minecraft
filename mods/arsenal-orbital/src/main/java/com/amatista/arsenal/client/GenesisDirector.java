package com.amatista.arsenal.client;

import com.amatista.arsenal.Timeline;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.amatista.arsenal.GenesisTimeline.*;
import static com.amatista.arsenal.client.Cinematic.bezier;
import static com.amatista.arsenal.client.Cinematic.smooth;

/**
 * "Protocolo Génesis": de la Tierra a la red cósmica, al borde del universo observable y más allá,
 * donde la antigua estructura GÉNESIS cosecha la luz de galaxias enteras y forja una chispa de
 * creación que viaja de vuelta y florece sobre el objetivo como una supernova de cristal.
 */
final class GenesisDirector {
    enum Scene { SOLAR, MILKY_WAY, WEB, EDGE, GENESIS, RETURN, EARTH }

    static final float CMB_RADIUS = 2_400_000f;
    static final float CORE_R = 800f;
    static final float[] RING_RADII = {3000f, 4200f, 5400f, 6600f, 7800f, 9000f};
    static final Vec3 LAUNCH = new Vec3(0.25, 0.18, -1).normalize();
    static final Vec3 EARTH_DIR = new Vec3(-0.3, 0.88, 0.37).normalize();
    static final int HARVEST_GALAXIES = 6;

    private GenesisDirector() {}

    static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    static Scene scene(float t) {
        if (t < SOLAR_END) return Scene.SOLAR;
        if (t < HOME_END) return Scene.MILKY_WAY;
        if (t < WEB_END) return Scene.WEB;
        if (t < EDGE_END) return Scene.EDGE;
        if (t < LAUNCH_END) return Scene.GENESIS;
        if (t < RETURN_TRIP_END) return Scene.RETURN;
        return Scene.EARTH;
    }

    // ------------------------------------------------------------------ estado de Génesis

    static float awaken(float t) { return smooth(seg(t, BEYOND_END - 0.02f, AWAKEN_END)); }
    static float petals(float t) { return smooth(seg(t, BEYOND_END + 0.02f, AWAKEN_END - 0.01f)); }
    static float harvest(float t) { return seg(t, AWAKEN_END, HARVEST_END); }
    static float forge(float t) { return smooth(seg(t, HARVEST_END, FORGE_END)); }
    static boolean launched(float t) { return t >= FORGE_END; }

    /** Radio de un anillo: se cierra mientras se forja la chispa y se abre de golpe al lanzarla. */
    static float ringRadius(int k, float t) {
        float r = RING_RADII[k];
        float close = forge(t) * 0.82f;
        float open = launched(t) ? smooth(seg(t, FORGE_END, FORGE_END + 0.01f)) * 0.82f : 0f;
        return r * (1f - close + open);
    }

    /** Posición de cada galaxia cosechada: gira en espiral y cae hacia Génesis. */
    static Vec3 galaxyPos(int i, float t) {
        float h = harvest(t);
        float e = (float) Math.pow(smooth(h), 1.3);
        double a = i * Math.PI * 2 / HARVEST_GALAXIES + e * Math.PI * 2.2;
        double r = 90000 * (1 - e) + 2000;
        double y = Math.sin(i * 1.7) * 18000 * (1 - e);
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    static float galaxyScale(int i, float t) {
        return (0.16f + 0.05f * (i % 3)) * (1f - 0.92f * smooth(harvest(t)));
    }

    /** Posición de la chispa tras el lanzamiento. */
    static Vec3 sparkPos(float t) {
        if (!launched(t)) return Vec3.ZERO;
        float u = seg(t, FORGE_END, LAUNCH_END);
        return LAUNCH.scale(260000 * u * u);
    }

    static float warp(float t) {
        if (t >= SPACE_START && t < SOLAR_END) return 0.5f + 0.5f * seg(t, SPACE_START + 0.02f, SOLAR_END);
        if (t >= SOLAR_END && t < WEB_END) return 0.8f;
        if (t >= WEB_END && t < EDGE_END) return 0.6f;
        if (t >= RETURN_TRIP_END - 0.08f && t < RETURN_TRIP_END) return 1f;
        return 0f;
    }

    // ------------------------------------------------------------------ cámara en el espacio

    static Cinematic.SpacePose spacePose(float t) {
        switch (scene(t)) {
            case SOLAR: return BlackHoleDirector.solarPose(seg(t, SPACE_START, SOLAR_END));
            case MILKY_WAY: return BlackHoleDirector.milkyPose(seg(t, SOLAR_END, HOME_END));
            case WEB: {
                float u = seg(t, HOME_END, WEB_END);
                if (u < 0.6f) {
                    float e = u / 0.6f;
                    Vec3 eye = new Vec3(Math.sin(e * 5) * 60000, Math.cos(e * 4) * 40000, 900000 - 1100000 * e);
                    return new Cinematic.SpacePose(eye, eye.add(Math.sin(e * 5 + 0.4) * 30000, 0, -300000), 76f, (float) Math.sin(e * 6) * 6f);
                }
                float e = smooth((u - 0.6f) / 0.4f);
                Vec3 from = new Vec3(Math.sin(5) * 60000, Math.cos(4) * 40000, -200000);
                Vec3 eye = bezier(from, new Vec3(600000, 500000, -100000), new Vec3(0, 900000, 2200000), e);
                return new Cinematic.SpacePose(eye, from.add(0, 0, -300000).lerp(Vec3.ZERO, e), 70f, 0f);
            }
            case EDGE: {
                float u = seg(t, WEB_END, EDGE_END);
                Vec3 eye = new Vec3(0, 900000, 2200000).lerp(new Vec3(0, 0, CMB_RADIUS * 1.03), smooth(u));
                Vec3 look = Vec3.ZERO.lerp(new Vec3(0, 0, CMB_RADIUS * 2), smooth((u - 0.3f) / 0.4f));
                return new Cinematic.SpacePose(eye, look, 72f, 0f);
            }
            case GENESIS: return genesisPose(t);
            case RETURN: return new Cinematic.SpacePose(Vec3.ZERO, new Vec3(0, 0, -1000), 82f, (float) Math.sin(t * 600) * 3f);
            default: {
                float u = seg(t, RETURN_TRIP_END, SPACE_END);
                Vec3 target = Cinematic.TARGET_SPACE;
                Vec3 eye = target.add(1500, 1000, 1900).lerp(target.add(800, 520, 1000), smooth(u));
                return new Cinematic.SpacePose(eye, target.add(EARTH_DIR.scale(1200 * (1 - u))), 62f, 0f);
            }
        }
    }

    private static Cinematic.SpacePose genesisPose(float t) {
        if (t < BEYOND_END) {
            // más allá del borde: un mar de auroras y, al fondo, Génesis
            float u = seg(t, EDGE_END, BEYOND_END);
            Vec3 eye = bezier(new Vec3(0, 14000, 120000), new Vec3(-20000, 9000, 60000), new Vec3(-6000, 4200, 30000), smooth(u));
            return new Cinematic.SpacePose(eye, Vec3.ZERO, 66f, (float) Math.sin(u * 3) * 4f);
        }
        if (t < AWAKEN_END) {
            // Génesis despierta: la cámara la rodea
            float u = seg(t, BEYOND_END, AWAKEN_END);
            double a0 = Math.atan2(30000, -6000);
            double a = a0 + smooth(u) * Math.PI * 0.9;
            double r = Mth.lerp(smooth(u), 30600f, 22000f);
            Vec3 eye = new Vec3(Math.cos(a) * r, Mth.lerp(u, 4200f, 7000f), Math.sin(a) * r);
            return new Cinematic.SpacePose(eye, Vec3.ZERO, 62f, 3f);
        }
        if (t < HARVEST_END) {
            // las galaxias caen en espiral: plano muy abierto
            float u = seg(t, AWAKEN_END, HARVEST_END);
            Vec3 prev = genesisPose(AWAKEN_END - 0.0001f).eye();
            Vec3 wide = new Vec3(30000, 90000, 160000);
            Vec3 eye = u < 0.2f ? prev.lerp(wide, smooth(u / 0.2f)) : wide.lerp(new Vec3(20000, 30000, 60000), smooth((u - 0.2f) / 0.8f));
            return new Cinematic.SpacePose(eye, Vec3.ZERO, 70f, 0f);
        }
        if (t < FORGE_END) {
            // la chispa de la creación
            float u = seg(t, HARVEST_END, FORGE_END);
            Vec3 eye = new Vec3(20000, 30000, 60000).lerp(new Vec3(0, 900, 3600), smooth(Math.min(1f, u * 1.4f)));
            return new Cinematic.SpacePose(eye, Vec3.ZERO, Mth.lerp(u, 64f, 48f), 0f);
        }
        // el lanzamiento, de lado
        float u = seg(t, FORGE_END, LAUNCH_END);
        Vec3 eye = new Vec3(14000, 3500, 4000);
        return new Cinematic.SpacePose(eye, sparkPos(t).lerp(LAUNCH.scale(30000), 0.3 + 0.4 * u), 66f, 0f);
    }

    // ------------------------------------------------------------------ cámara en el mundo

    /** Posición de la chispa en el mundo (f = 0 al aparecer, 1 en la supernova). */
    static Vec3 sparkWorldPos(Vec3 target, float f) {
        float descend = (BLOOM_START - WORLD_START) / (IMPACT - WORLD_START);
        float d = Mth.clamp(f / descend, 0f, 1f);
        float e = 1f - (1f - d) * (1f - d);
        return target.add(EARTH_WORLD_OFFSET.scale(1 - e)).add(0, 22, 0);
    }

    static final Vec3 EARTH_WORLD_OFFSET = new Vec3(-60, 420, 70);

    static Cinematic.Pose worldPose(float t) {
        Vec3 eye = Cinematic.playerEye();
        Vec3 tc = Cinematic.targetCenter();
        if (t < G2_END) {
            float ts = t < G1_END ? t / G1_END * Timeline.A1_END
                    : Timeline.A1_END + (t - G1_END) / (G2_END - G1_END) * (Timeline.A2_END - Timeline.A1_END);
            return Cinematic.satelliteWorldPose(ts);
        }
        if (t < SPACE_END) return new Cinematic.Pose(eye.add(0, 3, 0), Cinematic.playerYaw(), 90f, 0f, 70f);
        float f = (t - WORLD_START) / (IMPACT - WORLD_START);
        Vec3 spark = sparkWorldPos(tc, f);
        if (t < BLOOM_START) {
            Cinematic.Pose p = Cinematic.lookAt(tc.add(115, 22, 90), spark, 0f, 70f);
            return Cinematic.shake(p, 0.05f, t);
        }
        if (t < IMPACT) {
            float u = seg(t, BLOOM_START, IMPACT);
            double ang = Math.atan2(90, 115) + u * Math.toRadians(120);
            double radius = 140 - 40 * u;
            Vec3 pos = tc.add(Math.sin(ang) * radius, 30 + 10 * Math.sin(u * 3), Math.cos(ang) * radius);
            Cinematic.Pose p = Cinematic.lookAt(pos, spark.add(0, -6, 0), 0f, 64f - 8f * u);
            return Cinematic.shake(p, 0.05f + 0.6f * u * u, t);
        }
        if (t < RETURN_START) {
            float u = seg(t, IMPACT, RETURN_START);
            float e = smooth(u);
            double ang = Math.atan2(90, 115) + Math.toRadians(120) + u * Math.toRadians(50);
            Vec3 start = tc.add(Math.sin(ang) * 100, 40, Math.cos(ang) * 100);
            Vec3 aerial = tc.add(Math.sin(ang) * 140, 210, Math.cos(ang) * 140);
            Cinematic.Pose p = Cinematic.lookAt(start.lerp(aerial, e), tc, 0f, 60f + 10f * e);
            return Cinematic.shake(p, 3f * (float) Math.pow(1 - Math.min(1f, u * 2f), 2), t);
        }
        float u = seg(t, RETURN_START, 1f);
        float e = smooth(u);
        double ang = Math.atan2(90, 115) + Math.toRadians(170);
        Vec3 aerial = tc.add(Math.sin(ang) * 140, 210, Math.cos(ang) * 140);
        Vec3 pos = bezier(aerial, aerial.lerp(eye, 0.5).add(0, 50, 0), eye, e);
        Cinematic.Pose look = Cinematic.lookAt(pos, tc, 0f, 70f);
        float k = smooth(Math.max(0f, (u - 0.4f) / 0.6f));
        return new Cinematic.Pose(pos, Cinematic.lerpAngle(look.yaw(), Cinematic.playerYaw(), k),
                Mth.lerp(k, look.pitch(), Cinematic.playerPitch()), 0f, 70f);
    }

    // ------------------------------------------------------------------ sonidos

    static float consoleLineStart(int i) {
        return 0.004f + i * (G1_END - 0.014f) / HudRenderer.CONSOLE_LINES;
    }

    static void buildCues() {
        Cinematic.cue(0.0f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.8f, 1.0f);
        for (int i = 0; i < HudRenderer.CONSOLE_LINES; i++) {
            Cinematic.cue(consoleLineStart(i), com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 0.8f + i * 0.1f, 1.0f);
        }
        Cinematic.cue(G1_END - 0.004f, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.8f, 1.0f);
        Cinematic.cue(G1_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 0.6f, 0.9f);
        Cinematic.cue(SPACE_START - 0.002f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.5f, 1.0f);
        Cinematic.cue(SOLAR_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.6f, 1.0f);
        Cinematic.cue(HOME_END, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.6f, 0.6f);
        Cinematic.cue(HOME_END + 0.01f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.5f, 1.0f);
        Cinematic.cue(WEB_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.8f, 1.0f);
        Cinematic.cue(EDGE_END - 0.01f, com.amatista.arsenal.registry.ModSounds.WARP_JUMP.get(), 0.5f, 0.4f);
        Cinematic.cue(EDGE_END, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.4f, 1.0f);
        Cinematic.cue(EDGE_END + 0.02f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.4f, 1.0f);
        Cinematic.cue(BEYOND_END, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.4f, 1.0f);
        Cinematic.cue(BEYOND_END + 0.01f, com.amatista.arsenal.registry.ModSounds.STATION_ONLINE.get(), 0.4f, 1.0f);
        for (int k = 0; k < 6; k++) Cinematic.cue(BEYOND_END + 0.015f + k * 0.01f, com.amatista.arsenal.registry.ModSounds.CATHEDRAL_BELL.get(), 0.5f + k * 0.15f, 0.7f);
        Cinematic.cue(AWAKEN_END, com.amatista.arsenal.registry.ModSounds.PLANET_GROAN.get(), 0.3f, 0.5f);
        Cinematic.cue(AWAKEN_END + 0.01f, com.amatista.arsenal.registry.ModSounds.VOID_DRONE.get(), 0.4f, 1.0f);
        Cinematic.cue(HARVEST_END, com.amatista.arsenal.registry.ModSounds.HEARTBEAT.get(), 0.5f, 1.0f);
        Cinematic.cue(HARVEST_END + 0.03f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.4f, 1.0f);
        Cinematic.cue(FORGE_END - 0.03f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.8f, 1.0f);
        float countdown = HudRenderer.countdownWindow();
        for (int i = 0; i < 5; i++) {
            Cinematic.cue(FORGE_END - countdown + countdown * i / 5f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 1.0f + i * 0.2f, 1.0f);
        }
        Cinematic.cue(FORGE_END, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.4f, 1.0f);
        Cinematic.cue(FORGE_END, com.amatista.arsenal.registry.ModSounds.BELL_STRIKE.get(), 0.5f, 1.0f);
        Cinematic.cue(FORGE_END + 0.003f, com.amatista.arsenal.registry.ModSounds.ENERGY_CRACK.get(), 0.4f, 1.0f);
        Cinematic.cue(LAUNCH_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.9f, 1.0f);
        Cinematic.cue(LAUNCH_END + 0.03f, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.8f, 0.6f);
        Cinematic.cue(RETURN_TRIP_END, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.6f, 1.0f);
        Cinematic.cue(SPACE_END, com.amatista.arsenal.registry.ModSounds.CATHEDRAL_BELL.get(), 0.5f, 1.0f);
        Cinematic.cue(BLOOM_START, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.5f, 1.0f);
        Cinematic.cue(IMPACT - 0.012f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 1.2f, 1.0f);
        Cinematic.cue(IMPACT, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.3f, 1.0f);
        Cinematic.cue(IMPACT, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), 0.3f, 1.0f);
        Cinematic.cue(IMPACT + 0.002f, com.amatista.arsenal.registry.ModSounds.BELL_STRIKE.get(), 0.5f, 1.0f);
        Cinematic.cue(IMPACT + 0.006f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.5f, 1.0f);
        Cinematic.cue(RETURN_START - 0.015f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.8f, 0.8f);
        Cinematic.cue(0.99f, com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 1.2f, 0.9f);
    }

    static void ambientSounds(float t, long tick) {
        if (t >= SPACE_START && t < SPACE_END && tick % 70 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.MACHINE_HUM.get(), 0.4f, 1.0f);
        if (t >= EDGE_END && t < LAUNCH_END && tick % 50 == 25) Cinematic.play(com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.5f + 0.3f * (float) Math.sin(tick * 0.1), 0.6f);
        if (warp(t) > 0.5f && tick % 12 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.3f + warp(t) * 0.5f, 0.45f);
        if (t >= HARVEST_END && t < FORGE_END) {
            float u = seg(t, HARVEST_END, FORGE_END);
            int every = Math.max(5, (int) (34 - 28 * u));
            if (tick % every == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.HEARTBEAT.get(), 0.5f + 0.8f * u, 1.0f);
        }
        if (t >= BLOOM_START && t < IMPACT && tick % 6 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 0.6f + 1.4f * seg(t, BLOOM_START, IMPACT), 0.7f);
        if (t >= IMPACT && t < 0.99f && tick % 30 == 0) Cinematic.play(com.amatista.arsenal.registry.ModSounds.CRYSTAL_RESONANCE.get(), 0.8f, 0.6f);
    }
}
