package com.amatista.arsenal.client;

import com.amatista.arsenal.PhobosTimeline;
import com.amatista.arsenal.StrikeType;
import com.amatista.arsenal.Timeline;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * El director de la cinemática del bombardeo orbital: decide dónde está la cámara en cada
 * momento, qué se dibuja (mundo o espacio) y qué suena.
 */
public final class Cinematic {
    private static boolean active;
    private static BlockPos target;
    private static Vec3 targetCenter;
    private static Vec3 playerEye;
    private static float playerYaw;
    private static float playerPitch;
    private static int durationTicks;
    private static long startTick;
    private static Marker camera;
    private static CameraType previousCameraType;
    private static final List<Cue> cues = new ArrayList<>();
    private static int nextCue;
    private static String playerName = "";
    private static long seed;
    private static StrikeType type = StrikeType.SATELLITE;

    /** Contador de ticks del cliente (se detiene en pausa, igual que el servidor en un solo jugador). */
    private static long clientTicks;

    private record Cue(float at, Runnable action) {}

    public static boolean isActive() {
        return active;
    }

    public static BlockPos target() {
        return target;
    }

    public static Vec3 targetCenter() {
        return targetCenter;
    }

    public static String playerName() {
        return playerName;
    }

    public static float durationSeconds() {
        return durationTicks / 20f;
    }

    public static long seed() {
        return seed;
    }

    public static StrikeType type() {
        return type;
    }

    static Vec3 playerEye() {
        return playerEye;
    }

    static float playerYaw() {
        return playerYaw;
    }

    static float playerPitch() {
        return playerPitch;
    }

    public static long clientTicks() {
        return clientTicks;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public static void start(BlockPos target, int durationTicks, StrikeType strikeType) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (active) stop();
        Cinematic.type = strikeType;
        Cinematic.target = target;
        Cinematic.targetCenter = Vec3.atCenterOf(target).add(0, 0.5, 0);
        Cinematic.durationTicks = Math.max(200, durationTicks);
        Cinematic.startTick = clientTicks;
        Cinematic.playerEye = player.getEyePosition(1f);
        Cinematic.playerYaw = player.getYRot();
        Cinematic.playerPitch = player.getXRot();
        Cinematic.playerName = player.getGameProfile().getName();
        Cinematic.seed = target.asLong() ^ System.nanoTime();

        camera = new Marker(EntityType.MARKER, mc.level);
        Pose p = worldPose(0f);
        camera.setPos(p.pos);
        camera.setOldPosAndRot();
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
        buildCues();
        SpaceRenderer.prepare();
        active = true;
    }

    public static void stop() {
        if (!active) return;
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        camera = null;
        cues.clear();
    }

    /** Tiempo normalizado (0..1) de la cinemática. */
    public static float progress(float partialTick) {
        if (!active) return 0f;
        float t = (clientTicks - startTick - 1 + partialTick) / (float) durationTicks;
        return Mth.clamp(t, 0f, 1f);
    }

    /** Segundos transcurridos (para animaciones continuas). */
    public static float seconds(float partialTick) {
        return progress(partialTick) * durationTicks / 20f;
    }

    public static boolean isSpace(float partialTick) {
        if (!active) return false;
        float t = progress(partialTick);
        if (type == StrikeType.PHOBOS) return t >= PhobosTimeline.SPACE_START && t < PhobosTimeline.SPACE_END;
        if (type == StrikeType.BLACKHOLE) return t >= com.amatista.arsenal.BlackHoleTimeline.SPACE_START && t < com.amatista.arsenal.BlackHoleTimeline.SPACE_END;
        if (type == StrikeType.GENESIS) return t >= com.amatista.arsenal.GenesisTimeline.SPACE_START && t < com.amatista.arsenal.GenesisTimeline.SPACE_END;
        if (type == StrikeType.STAR) return t >= com.amatista.arsenal.StarTimeline.SPACE_START && t < com.amatista.arsenal.StarTimeline.SPACE_END;
        return t >= Timeline.SPACE_START && t < Timeline.SPACE_END;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clientTicks++;
        if (!active) return;
        if (mc.player == null || !mc.player.isAlive()) {
            stop();
            return;
        }
        float t = (clientTicks - startTick) / (float) durationTicks;
        if (t >= 1f) {
            stop();
            return;
        }
        // mueve la entidad cámara (la posición se interpola entre ticks)
        camera.setOldPosAndRot();
        Pose p = worldPose(t);
        camera.setPos(p.pos);
        camera.setYRot(p.yaw);
        camera.setXRot(p.pitch);
        // sonidos programados
        while (nextCue < cues.size() && cues.get(nextCue).at <= t) {
            cues.get(nextCue).action.run();
            nextCue++;
        }
        ambientSounds(t);
    }

    // ------------------------------------------------------------------ cámara en el mundo

    public record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {}

    /** Pose de la cámara en el mundo real para un tiempo t (0..1). */
    public static Pose worldPose(float t) {
        if (type == StrikeType.PHOBOS) return PhobosDirector.worldPose(t);
        if (type == StrikeType.BLACKHOLE) return BlackHoleDirector.worldPose(t);
        if (type == StrikeType.STAR) return StarDirector.worldPose(t);
        if (type == StrikeType.GENESIS) return GenesisDirector.worldPose(t);
        return satelliteWorldPose(t);
    }

    /** Cámara de la cinemática del satélite (la de Fobos reutiliza sus dos primeros actos). */
    static Pose satelliteWorldPose(float t) {
        Vec3 eye = playerEye;
        Vec3 tc = targetCenter;
        double baseYaw = Math.toRadians(playerYaw);

        if (t < Timeline.A1_END) {
            // Acto 1: la cámara rodea al jugador mientras se autoriza el ataque
            float u = t / Timeline.A1_END;
            double ang = baseYaw + Math.PI + u * Math.toRadians(140);
            double radius = 3.2 + 1.6 * u;
            Vec3 pos = eye.add(-Math.sin(ang) * radius, 0.4 + 0.5 * u, Math.cos(ang) * radius);
            return lookAt(pos, eye.add(0, -0.2, 0), 0f, 62f);
        }
        if (t < Timeline.A2_END) {
            // Acto 2: despegue. Sube cada vez más rápido y termina mirando hacia abajo.
            float u = (t - Timeline.A1_END) / (Timeline.A2_END - Timeline.A1_END);
            double ang = baseYaw + Math.PI + Math.toRadians(140) + u * Math.toRadians(90);
            double radius = 4.8 * (1 - smooth(Math.min(1f, u * 3f)));
            double height = 1.3 + 2200 * Math.pow(u, 2.6);
            Vec3 pos = eye.add(-Math.sin(ang) * radius, height, Math.cos(ang) * radius);
            // mira al jugador al principio y luego al suelo, casi vertical
            float look = smooth(Math.min(1f, u * 2.2f));
            Vec3 lookTarget = eye.lerp(new Vec3(pos.x + 0.001, eye.y - 50, pos.z + 6), look);
            Pose p = lookAt(pos, lookTarget, (float) (u * 25), 70f + u * 15f);
            return p;
        }
        if (t < Timeline.SPACE_END) {
            // En el espacio no se ve el mundo: la cámara descansa cerca del jugador (más barato)
            return new Pose(eye.add(0, 3, 0), playerYaw, 90f, 0f, 70f);
        }
        if (t < Timeline.A6_END) {
            // Acto 6: la cámara baja junto al rayo, atravesando la atmósfera
            float u = (t - Timeline.SPACE_END) / (Timeline.A6_END - Timeline.SPACE_END);
            float e = easeOutCubic(u);
            Vec3 from = tc.add(25, 900, 18);
            Vec3 to = tc.add(48, 88, 36);
            Vec3 pos = from.lerp(to, e);
            Pose p = lookAt(pos, tc.add(0, 18 * (1 - e), 0), (float) Math.sin(u * 9) * 4f * (1 - u), 80f - 10f * e);
            return shake(p, 0.6f * (1 - u), t);
        }
        if (t < Timeline.IMPACT) {
            // Acto 7: cámara lenta rodeando el punto mientras el rayo toca el suelo
            float u = (t - Timeline.A6_END) / (Timeline.IMPACT - Timeline.A6_END);
            double ang = Math.atan2(20, 26) + u * Math.toRadians(75);
            double radius = 62 - 10 * u;
            double h = 40 - 12 * u;
            Vec3 pos = tc.add(Math.sin(ang) * radius, h, Math.cos(ang) * radius);
            Pose p = lookAt(pos, tc.add(0, 6, 0), 0f, 62f - 10f * u);
            return shake(p, 0.05f + 0.25f * u * u, t);
        }
        if (t < Timeline.RETURN_START) {
            // Acto 8: sube a una vista aérea del cráter, con el temblor del impacto
            float u = (t - Timeline.IMPACT) / (Timeline.RETURN_START - Timeline.IMPACT);
            float e = smooth(u);
            double ang = Math.atan2(20, 26) + Math.toRadians(75) + u * Math.toRadians(40);
            Vec3 start = tc.add(Math.sin(ang) * 52, 28, Math.cos(ang) * 52);
            Vec3 aerial = tc.add(Math.sin(ang) * 80, 135, Math.cos(ang) * 80);
            Vec3 pos = start.lerp(aerial, e);
            Pose p = lookAt(pos, tc, 0f, 48f + 20f * e);
            return shake(p, 2.4f * (float) Math.pow(1 - Math.min(1f, u * 2.5f), 2), t);
        }
        // Regreso suave hasta los ojos del jugador
        float u = (t - Timeline.RETURN_START) / (1f - Timeline.RETURN_START);
        float e = smooth(u);
        double ang = Math.atan2(20, 26) + Math.toRadians(115);
        Vec3 aerial = tc.add(Math.sin(ang) * 80, 135, Math.cos(ang) * 80);
        Vec3 mid = aerial.lerp(eye, 0.5).add(0, 30, 0);
        Vec3 pos = bezier(aerial, mid, eye, e);
        Pose look = lookAt(pos, tc, 0f, 68f);
        float yaw = lerpAngle(look.yaw, playerYaw, smooth(Math.max(0f, (u - 0.4f) / 0.6f)));
        float pitch = Mth.lerp(smooth(Math.max(0f, (u - 0.4f) / 0.6f)), look.pitch, playerPitch);
        return new Pose(pos, yaw, pitch, 0f, Mth.lerp(e, 68f, 70f));
    }

    // ------------------------------------------------------------------ cámara en el espacio

    /** Escena espacial: planeta en el origen, objetivo arriba del todo, satélite encima. */
    public static final float PLANET_RADIUS = 1000f;
    /** Tamaño del satélite (3 = tres veces su modelo base: más de 400 bloques de punta a punta). */
    public static final float SAT_SCALE = 3f;
    public static final Vec3 TARGET_SPACE = new Vec3(0, PLANET_RADIUS, 0);
    public static final Vec3 SATELLITE = new Vec3(0, PLANET_RADIUS + 450, 0);
    /** Dirección hacia el sol (de día sobre el objetivo). */
    public static final Vec3 SUN_DIR = new Vec3(-0.55, 0.55, -0.62).normalize();
    private static final Vec3 WIDE = new Vec3(1900, 1300, 2600);
    /** La vista amplia mira entre el planeta (abajo) y el sol (arriba). */
    private static final Vec3 WIDE_CENTER = WIDE.add(WIDE.scale(-1).normalize().add(SUN_DIR.scale(0.8)).normalize().scale(1000));
    /** Punto del planeta donde el sol está justo en el horizonte (amanecer). */
    private static final Vec3 SUNRISE_N = new Vec3(0, 1, 0).subtract(SUN_DIR.scale(SUN_DIR.y)).normalize();
    private static final Vec3 SUNRISE_EYE = SUNRISE_N.scale(PLANET_RADIUS + 75).subtract(SUN_DIR.scale(150));
    private static final Vec3 SUNRISE_CENTER = SUNRISE_EYE.add(SUN_DIR.scale(1000)).subtract(SUNRISE_N.scale(40));

    public record SpacePose(Vec3 eye, Vec3 center, float fov, float roll) {}

    private static Vec3 sat(double x, double y, double z) {
        return SATELLITE.add(x * SAT_SCALE, y * SAT_SCALE, z * SAT_SCALE);
    }

    public static SpacePose spacePose(float t) {
        if (t < Timeline.A3_END) {
            float u = Mth.clamp((t - Timeline.SPACE_START) / (Timeline.A3_END - Timeline.SPACE_START), 0f, 1f);
            Vec3 riseEnd = TARGET_SPACE.add(30, 220, 60);
            if (u < 0.22f) {
                // saliendo de la atmósfera: mirando hacia abajo al objetivo
                float e = smooth(u / 0.22f);
                return new SpacePose(TARGET_SPACE.add(0, 75, 0).lerp(riseEnd, e), TARGET_SPACE, 75f, 0f);
            }
            if (u < 0.38f) {
                // la cámara gira hacia el horizonte, donde va a salir el sol
                float e = smooth((u - 0.22f) / 0.16f);
                Vec3 eye = bezier(riseEnd, riseEnd.lerp(SUNRISE_EYE, 0.5).add(SUNRISE_N.scale(250)), SUNRISE_EYE, e);
                return new SpacePose(eye, TARGET_SPACE.lerp(SUNRISE_CENTER, e), 75f - 10f * e, 0f);
            }
            if (u < 0.6f) {
                // AMANECER: el sol asoma por el borde del planeta
                float e = (u - 0.38f) / 0.22f;
                Vec3 eye = SUNRISE_EYE.add(SUNRISE_N.scale(90 * smooth(e)));
                return new SpacePose(eye, SUNRISE_CENTER.add(SUNRISE_N.scale(60 * smooth(e))), 65f, 4f * (1 - e));
            }
            // se aleja hasta ver el planeta entero, el sol arriba y la luna a un lado
            float e = smooth((u - 0.6f) / 0.4f);
            Vec3 from = SUNRISE_EYE.add(SUNRISE_N.scale(90));
            Vec3 eye = bezier(from, from.add(WIDE).scale(0.5).add(0, 900, 0), WIDE, e);
            Vec3 center = SUNRISE_CENTER.add(SUNRISE_N.scale(60)).lerp(WIDE_CENTER, e);
            return new SpacePose(eye, center, Mth.lerp(e, 65f, 70f), 0f);
        }
        if (t < Timeline.A4_END) {
            float u = (t - Timeline.A3_END) / (Timeline.A4_END - Timeline.A3_END);
            Vec3 near = sat(140, 60, 180);
            if (u < 0.35f) {
                // viaje hacia el satélite gigante
                float e = smooth(u / 0.35f);
                Vec3 eye = bezier(WIDE, new Vec3(1300, 2300, 1900), near, e);
                Vec3 center = WIDE_CENTER.lerp(SATELLITE, e);
                return new SpacePose(eye, center, 70f - 18f * e, 0f);
            }
            // vuelta lenta alrededor del satélite mientras despierta
            float v = (u - 0.35f) / 0.65f;
            double a0 = Math.atan2(140, 180);
            double ang = a0 + v * Math.toRadians(150);
            double radius = Mth.lerp(smooth(Math.min(1f, v * 3)), (float) Math.sqrt(140 * 140 + 180 * 180), 78f) - 12 * v;
            double h = Mth.lerp(smooth(Math.min(1f, v * 3)), 60f, 22f) - 8 * v;
            Vec3 eye = sat(Math.sin(ang) * radius, h, Math.cos(ang) * radius);
            return new SpacePose(eye, sat(0, -2, 0), 52f, (float) Math.sin(v * 3) * 3f);
        }
        if (t < Timeline.A5_END) {
            float u = (t - Timeline.A4_END) / (Timeline.A5_END - Timeline.A4_END);
            if (u < 0.6f) {
                // detrás del satélite: se ve el cañón apuntando al planeta y el objetivo abajo
                float e = smooth(u / 0.6f);
                double a0 = Math.atan2(140, 180) + Math.toRadians(150);
                Vec3 orbitEnd = sat(Math.sin(a0) * 66, 14, Math.cos(a0) * 66);
                Vec3 behind = sat(10, 46, 86);
                Vec3 eye = orbitEnd.lerp(behind, e).add(0, 0, -10 * SAT_SCALE * u);
                Vec3 center = sat(0, -2, 0).lerp(SATELLITE.lerp(TARGET_SPACE, 0.35), e);
                return new SpacePose(eye, center, 58f, 0f);
            }
            // primer plano de la boca del cañón cargando
            float e = smooth((u - 0.6f) / 0.4f);
            Vec3 muzzle = SpaceRenderer.muzzlePosition(t);
            Vec3 eye = sat(10, 46, 76).lerp(muzzle.add(26 * SAT_SCALE, 6 * SAT_SCALE, 24 * SAT_SCALE), e);
            Vec3 center = SATELLITE.lerp(TARGET_SPACE, 0.35).lerp(muzzle.add(0, -8 * SAT_SCALE, 0), e);
            return new SpacePose(eye, center, 58f - 10f * e, 0f);
        }
        // Acto 6 (espacio): el rayo gigante sale y la cámara lo persigue hasta el planeta
        float u = Mth.clamp((t - Timeline.A5_END) / (Timeline.SPACE_END - Timeline.A5_END), 0f, 1f);
        Vec3 muzzle = SpaceRenderer.muzzlePosition(t);
        Vec3 close = muzzle.add(26 * SAT_SCALE, 6 * SAT_SCALE, 24 * SAT_SCALE);
        if (u < 0.18f) {
            return new SpacePose(close, muzzle.add(0, -12 * SAT_SCALE, 0), 48f + 25f * (u / 0.18f), 0f);
        }
        float v = easeInCubic((u - 0.18f) / 0.82f);
        Vec3 front = muzzle.lerp(TARGET_SPACE, v);
        Vec3 off = new Vec3(70, 90, 60).scale(1 - v).add(15, 25, 15);
        return new SpacePose(front.add(off), front.lerp(TARGET_SPACE, 0.6), 72f, (1 - v) * 10f);
    }

    // ------------------------------------------------------------------ sonidos

    private static void buildCues() {
        cues.clear();
        nextCue = 0;
        if (type == StrikeType.GENESIS) {
            GenesisDirector.buildCues();
            cues.sort((a, b) -> Float.compare(a.at, b.at));
            return;
        }
        if (type == StrikeType.STAR) {
            StarDirector.buildCues();
            cues.sort((a, b) -> Float.compare(a.at, b.at));
            return;
        }
        if (type == StrikeType.BLACKHOLE) {
            BlackHoleDirector.buildCues();
            cues.sort((a, b) -> Float.compare(a.at, b.at));
            return;
        }
        if (type == StrikeType.PHOBOS) {
            PhobosDirector.buildCues();
            cues.sort((a, b) -> Float.compare(a.at, b.at));
            return;
        }
        // Acto 1: consola
        cue(0.0f, com.amatista.arsenal.registry.ModSounds.UI_CONFIRM.get(), 1.3f, 0.9f);
        for (int i = 0; i < HudRenderer.CONSOLE_LINES; i++) {
            cue(HudRenderer.consoleLineStart(i), com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 1.4f + i * 0.08f, 1.0f);
        }
        cue(HudRenderer.consoleLineStart(HudRenderer.CONSOLE_LINES - 1) + 0.004f, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 1.5f, 1.0f);
        // Acto 2: despegue
        cue(Timeline.A1_END, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 0.6f, 0.9f);
        cue(Timeline.A1_END + 0.06f, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 0.85f, 0.9f);
        cue(Timeline.A2_END - 0.04f, com.amatista.arsenal.registry.ModSounds.PLASMA_WHOOSH.get(), 0.5f, 0.8f);
        cue(Timeline.SPACE_START - 0.002f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.5f, 1.0f);
        // Acto 4: el satélite despierta
        float a4 = Timeline.A3_END, d4 = Timeline.A4_END - Timeline.A3_END;
        cue(a4 + d4 * 0.36f, com.amatista.arsenal.registry.ModSounds.POWER_UP.get(), 0.6f, 1.0f);
        for (int i = 0; i < 6; i++) cue(a4 + d4 * (0.38f + i * 0.025f), com.amatista.arsenal.registry.ModSounds.CRYSTAL_CHIME.get(), 0.8f + i * 0.15f, 0.9f);
        cue(a4 + d4 * 0.50f, com.amatista.arsenal.registry.ModSounds.MECH_EXTEND.get(), 0.5f, 1.0f);
        cue(a4 + d4 * 0.58f, com.amatista.arsenal.registry.ModSounds.MECH_EXTEND.get(), 0.6f, 1.0f);
        cue(a4 + d4 * 0.66f, com.amatista.arsenal.registry.ModSounds.MECH_RETRACT.get(), 0.55f, 1.0f);
        cue(a4 + d4 * 0.80f, com.amatista.arsenal.registry.ModSounds.MECH_EXTEND.get(), 0.45f, 1.0f);
        cue(a4 + d4 * 0.88f, com.amatista.arsenal.registry.ModSounds.RUNE_GLIMMER.get(), 0.6f, 1.0f);
        // Acto 5: carga
        float a5 = Timeline.A4_END, d5 = Timeline.A5_END - Timeline.A4_END;
        cue(a5 + d5 * 0.02f, com.amatista.arsenal.registry.ModSounds.STATION_ONLINE.get(), 0.6f, 1.0f);
        cue(a5 + d5 * 0.35f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.5f, 1.0f);
        cue(a5 + d5 * 0.75f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.8f, 1.0f);
        float countdown = HudRenderer.countdownWindow();
        for (int i = 0; i < 5; i++) {
            cue(Timeline.A5_END - countdown + countdown * i / 5f, com.amatista.arsenal.registry.ModSounds.COUNTDOWN_TICK.get(), 0.8f + i * 0.1f, 1.0f);
        }
        cue(Timeline.A5_END - 0.012f, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 1.2f, 1.0f);
        // Disparo
        cue(Timeline.A5_END, com.amatista.arsenal.registry.ModSounds.SONIC_BLAST.get(), 0.5f, 1.0f);
        cue(Timeline.A5_END, com.amatista.arsenal.registry.ModSounds.DEEP_THUNDER.get(), 0.5f, 0.9f);
        cue(Timeline.A5_END + 0.004f, com.amatista.arsenal.registry.ModSounds.ENERGY_CRACK.get(), 0.6f, 1.0f);
        // Entrada en la atmósfera
        cue(Timeline.SPACE_END, com.amatista.arsenal.registry.ModSounds.PLASMA_WHOOSH.get(), 0.4f, 1.0f);
        cue(Timeline.SPACE_END, com.amatista.arsenal.registry.ModSounds.LASER_SHOT.get(), 0.5f, 1.0f);
        cue(Timeline.SPACE_END + 0.004f, com.amatista.arsenal.registry.ModSounds.RUSH_WIND.get(), 1.3f, 0.9f);
        // Acto 7: cámara lenta
        cue(Timeline.A6_END, com.amatista.arsenal.registry.ModSounds.CHARGE_WHINE.get(), 0.5f, 1.0f);
        cue(Timeline.A6_END + 0.035f, com.amatista.arsenal.registry.ModSounds.COSMIC_SWELL.get(), 0.6f, 0.7f);
        // Impacto (además de la explosión real del servidor)
        cue(Timeline.IMPACT, com.amatista.arsenal.registry.ModSounds.BEAM_IMPACT.get(), 0.5f, 1.0f);
        cue(Timeline.IMPACT, com.amatista.arsenal.registry.ModSounds.EXPLOSION.get(), 0.4f, 1.0f);
        cue(Timeline.IMPACT + 0.003f, com.amatista.arsenal.registry.ModSounds.PLANET_GROAN.get(), 0.5f, 0.5f);
        // Consecuencias y regreso
        cue(Timeline.RETURN_START - 0.02f, com.amatista.arsenal.registry.ModSounds.POWER_DOWN.get(), 0.8f, 0.8f);
        cue(0.99f, com.amatista.arsenal.registry.ModSounds.UI_CONFIRM.get(), 1.0f, 0.7f);
        cues.sort((a, b) -> Float.compare(a.at, b.at));
    }

    static void cue(float at, SoundEvent sound, float pitch, float volume) {
        cues.add(new Cue(at, () -> play(sound, pitch, volume)));
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void ambientSounds(float t) {
        long tick = clientTicks - startTick;
        if (type == StrikeType.PHOBOS) {
            PhobosDirector.ambientSounds(t, tick);
            return;
        }
        if (type == StrikeType.BLACKHOLE) {
            BlackHoleDirector.ambientSounds(t, tick);
            return;
        }
        if (type == StrikeType.GENESIS) {
            GenesisDirector.ambientSounds(t, tick);
            return;
        }
        if (type == StrikeType.STAR) {
            StarDirector.ambientSounds(t, tick);
            return;
        }
        if (t >= Timeline.SPACE_START && t < Timeline.A5_END) {
            // zumbido grave del espacio
            if (tick % 60 == 0) play(com.amatista.arsenal.registry.ModSounds.MACHINE_HUM.get(), 0.5f, 1.0f);
            if (tick % 140 == 70) play(com.amatista.arsenal.registry.ModSounds.VOID_DRONE.get(), 0.5f, 0.35f);
        }
        if (t >= Timeline.A4_END && t < Timeline.A5_END) {
            // la carga sube de tono
            float u = (t - Timeline.A4_END) / (Timeline.A5_END - Timeline.A4_END);
            int every = Math.max(6, (int) (40 - 32 * u));
            if (tick % every == 0) play(com.amatista.arsenal.registry.ModSounds.CAPACITOR_CHARGE.get(), 0.5f + 1.5f * u, 0.7f);
        }
        if (t >= Timeline.SPACE_END && t < Timeline.A6_END && tick % 10 == 0) {
            play(com.amatista.arsenal.registry.ModSounds.PLASMA_WHOOSH.get(), 0.5f, 0.4f);
        }
        if (t >= Timeline.IMPACT && t < 0.99f && tick % 30 == 0) {
            play(com.amatista.arsenal.registry.ModSounds.PLASMA_CRACKLE.get(), 0.7f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ utilidades

    static Pose lookAt(Vec3 from, Vec3 to, float roll, float fov) {
        Vec3 d = to.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, horiz));
        return new Pose(from, yaw, pitch, roll, fov);
    }

    static Pose shake(Pose p, float amount, float t) {
        if (amount <= 0.001f) return p;
        double s = t * durationTicks * 1.7;
        Vec3 off = new Vec3(Math.sin(s * 3.1) * 0.5 + Math.sin(s * 7.3) * 0.3,
                Math.sin(s * 4.7) * 0.4 + Math.sin(s * 9.1) * 0.2,
                Math.sin(s * 5.3) * 0.5).scale(amount);
        return new Pose(p.pos.add(off), p.yaw + (float) Math.sin(s * 6.1) * amount * 1.5f,
                p.pitch + (float) Math.sin(s * 5.5) * amount, p.roll + (float) Math.sin(s * 4.3) * amount * 2f, p.fov);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    static float easeInCubic(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * x;
    }

    static float easeOutCubic(float x) {
        x = Mth.clamp(x, 0f, 1f);
        float y = 1 - x;
        return 1 - y * y * y;
    }

    static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, float t) {
        Vec3 ab = a.lerp(b, t);
        Vec3 bc = b.lerp(c, t);
        return ab.lerp(bc, t);
    }

    static float lerpAngle(float a, float b, float t) {
        return a + Mth.wrapDegrees(b - a) * t;
    }

    private Cinematic() {}
}
