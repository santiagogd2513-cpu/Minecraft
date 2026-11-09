package com.arsenalatomico.client;

import com.arsenalatomico.Launcher;
import com.arsenalatomico.SalvoPlan;
import com.arsenalatomico.CondorPlan;
import com.arsenalatomico.NukePlan;
import com.arsenalatomico.TwinPlan;
import com.arsenalatomico.WeaponType;
import com.arsenalatomico.net.StartCinematicPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import com.arsenalatomico.registry.ModSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * La cinemática: una cámara que vuela por el mundo. Todas duran 3 min 30 s. Los planos del
 * Cóndor Doble están en {@link TwinScenes}. La Lluvia de Acero:
 * <pre>
 *    0-8 s    blanco fijado (desde el jugador)
 *    8-20 s   reconocimiento: el blanco visto desde muy arriba
 *   20-32 s   vuelo hasta la batería
 *   32-44 s   la batería en reposo, la cámara recorre el camión
 *   44-52 s   alarma: bajan los estabilizadores
 *   52-72 s   los lanzadores se elevan
 *   72-86 s   bocas de los tubos, apuntando
 *   86-100 s  cuenta regresiva (10 a 1), plano por detrás del camión
 *  100-110 s  24 misiles salen en ráfaga; la cámara los sigue
 *  110-128 s  llegada e impactos sobre el blanco
 *  128-162 s  humo y fuego; la cámara rodea el lugar
 *  162-190 s  vuelta a la batería, que se repliega
 *  190-210 s  vista desde lo alto y regreso al jugador
 * </pre>
 */
public final class Cinematic {
    public record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {}

    private static boolean active;
    private static WeaponType type = WeaponType.SALVO;
    static Vec3 target, site, eye;
    static float yaw, playerYaw;
    private static SalvoPlan plan;
    /** Plan del Cóndor Doble (null en las otras armas). */
    static TwinPlan twin;
    /** Plan del Cóndor (null en las otras armas). */
    static CondorPlan condor;
    /** Plan del Protocolo Hongo (null en las otras armas). */
    static NukePlan nuke;
    /** Despegues e impactos que hacen temblar la cámara: {segundo, x, y, z, fuerza, duración, alcance}. */
    private static final java.util.List<double[]> quakes = new java.util.ArrayList<>();
    /** Impactos de la cinemática: {segundo, x, y, z, grande (1/0), ya dibujado (1/0)}. */
    private static final java.util.List<double[]> blasts = new java.util.ArrayList<>();
    private static int duration;
    private static long startTick, clientTicks;
    private static Marker camera;
    private static CameraType previous;
    private static int nextBeep;
    /** El viaje en el camión del Sol Negro (0 = salida, 1 = llegada a la base). */
    static boolean ride;
    static int rideTruck, ridePhase;
    static Vec3 rideBase;
    static float rideYaw;

    private Cinematic() {}

    public static boolean isActive() {
        return active;
    }

    public static WeaponType type() {
        return type;
    }

    public static SalvoPlan plan() {
        return plan;
    }

    public static Vec3 site() {
        return site;
    }

    public static float launcherYaw() {
        return yaw;
    }

    public static Vec3 target() {
        return target;
    }

    /** Segundos desde que empezó (con fracción de tick). */
    public static float seconds(float partial) {
        if (!active) return 0;
        return Math.max(0, clientTicks - startTick - 1 + partial) / 20f;
    }

    public static float totalSeconds() {
        return duration / 20f;
    }

    public static void start(StartCinematicPacket p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (active) stop();
        type = p.type;
        target = Vec3.atBottomCenterOf(p.target).add(0, 0.5, 0);
        site = p.site;
        yaw = p.yaw;
        quakes.clear();
        blasts.clear();
        condor = null;
        nuke = null;
        ride = false;
        if (type == WeaponType.NUKE || type == WeaponType.THERMO) {
            plan = null;
            twin = null;
            nuke = new NukePlan(site, yaw, target, p.seed);
            NukeScenes.reset();
            boolean thermo = type == WeaponType.THERMO;
            Vec3 a = nuke.path.p0, b = nuke.path.p3;
            quakes.add(new double[]{nuke.launchTick / 20.0, a.x, a.y + 12, a.z, 1.8, 8, 110});
            quakes.add(new double[]{nuke.impactSecond(), b.x, b.y, b.z, thermo ? 4.6 : 3.6, thermo ? 12 : 9, thermo ? 1000 : 700});
            blasts.add(new double[]{nuke.impactSecond(), b.x, b.y, b.z, thermo ? 4 : 3, 0});
        } else if (type == WeaponType.ICBM) {
            plan = null;
            twin = null;
            condor = new CondorPlan(site, yaw, target, p.seed);
            Vec3 a = condor.path.p0, b = condor.path.p3;
            quakes.add(new double[]{condor.launchTick / 20.0, a.x, a.y + 14, a.z, 1.6, 7, 90});
            quakes.add(new double[]{condor.impactTick() / 20.0, b.x, b.y, b.z, 3.2, 5, 170});
            blasts.add(new double[]{condor.impactTick() / 20.0, b.x, b.y, b.z, 2, 0});
        } else if (type == WeaponType.TWIN) {
            plan = null;
            twin = new TwinPlan(p.mode, site, yaw, target, p.seed);
            for (int i = 0; i < 2; i++) {
                Vec3 a = twin.paths[i].p0, b = twin.paths[i].p3;
                quakes.add(new double[]{twin.launchTick[i] / 20.0, a.x, a.y, a.z, 1.2, 3, 60});
                quakes.add(new double[]{(twin.launchTick[i] + twin.paths[i].flightTicks) / 20.0, b.x, b.y, b.z, 2.5, 4, 120});
                blasts.add(new double[]{(twin.launchTick[i] + twin.paths[i].flightTicks) / 20.0, b.x, b.y, b.z, 1, 0});
            }
        } else {
            twin = null;
            plan = new SalvoPlan(site, yaw, target, p.seed);
            for (int i = 0; i < SalvoPlan.COUNT; i++) {
                Vec3 a = plan.paths[i].p0, b = plan.paths[i].p3;
                quakes.add(new double[]{plan.launchTick[i] / 20.0, a.x, a.y, a.z, 0.25, 0.6, 30});
                quakes.add(new double[]{(plan.launchTick[i] + plan.paths[i].flightTicks) / 20.0, b.x, b.y, b.z, 0.6, 1.2, 30});
                blasts.add(new double[]{(plan.launchTick[i] + plan.paths[i].flightTicks) / 20.0, b.x, b.y, b.z, 0, 0});
            }
        }
        duration = p.ticks;
        startTick = clientTicks;
        // el Sol Negro empieza dentro del silo: el jugador ya bajó caminando hasta el centro de mando
        if (type == WeaponType.THERMO) startTick -= com.arsenalatomico.strike.SolNegro.OFFSET * 20L;
        eye = player.getEyePosition(1f);
        playerYaw = player.getYRot();
        nextBeep = 0;
        camera = new Marker(EntityType.MARKER, mc.level);
        Pose s = pose(0);
        camera.setPos(s.pos());
        camera.setOldPosAndRot();
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
        if (nuke != null) NukeRenderer.show(nuke, startTick, duration);
        else if (condor != null) SiloRenderer.show(condor, startTick, duration);
        else if (twin != null) TwinRenderer.show(twin, startTick, duration);
        else LauncherRenderer.show(site, yaw, startTick, duration);
        active = true;
        play(ModSounds.BEEP.get(), 0.75f, 0.6f);
    }

    /** El viaje en el camión: la cámara sigue al camión con varias tomas y se funde a negro. */
    public static void startRide(com.arsenalatomico.net.RidePacket p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (active) stop();
        type = WeaponType.THERMO;
        ride = true;
        rideTruck = p.truck;
        ridePhase = p.phase;
        rideBase = p.base;
        rideYaw = p.yaw;
        target = Vec3.atBottomCenterOf(p.target);
        site = p.base;
        yaw = p.yaw;
        plan = null;
        twin = null;
        condor = null;
        nuke = null;
        quakes.clear();
        blasts.clear();
        duration = p.ticks;
        startTick = clientTicks;
        nextBeep = 0;
        camera = new Marker(EntityType.MARKER, mc.level);
        Pose s = pose(0);
        camera.setPos(s.pos());
        camera.setOldPosAndRot();
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
        active = true;
    }

    public static void stop() {
        if (!active) return;
        active = false;
        ride = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (previous != null) mc.options.setCameraType(previous);
        camera = null;
    }

    public static long clientTicks() {
        return clientTicks;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clientTicks++;
        if (!active) return;
        if (mc.player == null || !mc.player.isAlive() || clientTicks - startTick >= duration) {
            stop();
            return;
        }
        camera.setOldPosAndRot();
        Pose p = pose(0);
        camera.setPos(p.pos());
        camera.setYRot(p.yaw());
        camera.setXRot(p.pitch());
        // las explosiones se dibujan a su hora aunque el misil no se haya visto
        float now = seconds(0);
        for (double[] b : blasts) {
            if (b[5] == 0 && now >= b[0]) {
                b[5] = 1;
                if (b[4] > 2) {
                    NukeFx.spawn(new Vec3(b[1], b[2], b[3]), b[4] > 3 ? (float) com.arsenalatomico.strike.NukeCrater.THERMO : 1f);
                    continue;
                }
                ImpactFx.spawn(new Vec3(b[1], b[2], b[3]), b[4] > 1 ? ImpactFx.Kind.CONDOR : b[4] > 0 ? ImpactFx.Kind.ICBM : ImpactFx.Kind.SALVO);
            }
        }
        if (ride) {
            RideScenes.sounds(seconds(0));
        } else if (nuke != null) {
            NukeScenes.sounds(seconds(0));
            NukeScenes.particles(seconds(0));
        } else if (condor != null) {
            CondorScenes.sounds(seconds(0));
            CondorScenes.particles(seconds(0));
        } else if (twin != null) {
            TwinScenes.sounds(seconds(0));
            TwinScenes.particles(seconds(0));
        } else {
            sounds(seconds(0));
        }
    }

    // ------------------------------------------------------------------ sonido

    public static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Sonido con lugar en el mundo (se oye más fuerte cuanto más cerca está la cámara). */
    public static void playAt(SoundEvent e, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(e, SoundSource.HOSTILE, volume, pitch,
                SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
    }

    /** Pasa una vez por cada medio segundo nuevo de la cinemática; devuelve el medio segundo o -1. */
    static int nextHalf(float s) {
        int half = (int) Math.floor(s * 2);
        if (half < nextBeep) return -1;
        nextBeep = half + 1;
        return half;
    }

    private static void sounds(float s) {
        int half = nextHalf(s);
        if (half < 0) return;
        float t = half / 2f;
        Vec3 truck = site.add(0, 2, 0);
        Vec3 pivot = site.add(Launcher.forward(yaw).scale(-Launcher.PIVOT_BACK)).add(0, Launcher.PIVOT_UP, 0);
        if (half == 1) play(ModSounds.BEEP.get(), 0.9f, 0.6f);
        // alarma de la batería, sirena cada 2 s
        if (t >= 44 && t < 100 && half % 4 == 0) playAt(ModSounds.ALARM.get(), truck, 2.2f, 1f);
        // estabilizadores abajo y arriba
        if (t == 44 || t == 47 || t == 186 || t == 188) playAt(ModSounds.HYDRAULIC.get(), site.add(0, 0.6, 0), 1.6f, 0.8f);
        // los tubos se levantan y se recogen
        if ((t >= 52 && t < 72 || t >= 176 && t < 186) && half % 6 == 0) playAt(ModSounds.HYDRAULIC.get(), pivot, 2f, 1f);
        if (t == 80 || t == 82.5f) playAt(SoundEvents.IRON_TRAPDOOR_CLOSE, pivot, 2f, 0.6f);
        // cuenta regresiva de 10 a 1
        if (t >= 90 && t < 100 && half % 2 == 0) play(ModSounds.BEEP.get(), t >= 97 ? 1.25f : 1f, 0.9f);
        if (t == 100) play(ModSounds.BEEP.get(), 1.6f, 1f);
    }

    // ------------------------------------------------------------------ cámara

    public static Pose pose(float partial) {
        float s = seconds(partial);
        Pose p = raw(s);
        // dentro del silo la cámara sí va bajo tierra
        if (!(condor != null && s >= 59 && s < 99) && !(nuke != null && NukeScenes.underground(s))) p = ground(p);
        float shake = shake(s);
        if (shake > 0.001f) {
            double t = s * 37;
            Vec3 off = new Vec3(Math.sin(t) * shake, Math.cos(t * 1.3) * shake, Math.sin(t * 0.7 + 1) * shake);
            p = new Pose(p.pos().add(off), p.yaw() + (float) Math.sin(t * 1.7) * shake * 3, p.pitch(), p.roll(), p.fov());
        }
        return p;
    }

    private static Pose raw(float s) {
        if (ride) return RideScenes.pose(s);
        if (nuke != null) return NukeScenes.pose(s);
        if (condor != null) return CondorScenes.pose(s);
        return twin != null ? TwinScenes.pose(s) : salvo(s);
    }

    /** Que la cámara no se meta en una montaña. */
    private static Pose ground(Pose p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return p;
        Vec3 c = p.pos();
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(c.x), Mth.floor(c.z));
        if (c.y >= h + 1.2) return p;
        return new Pose(new Vec3(c.x, h + 1.2, c.z), p.yaw(), p.pitch(), p.roll(), p.fov());
    }

    /** Temblor por los despegues y los impactos (más fuerte cuanto más cerca está la cámara). */
    private static float shake(float s) {
        if (quakes.isEmpty()) return 0;
        float sum = 0;
        Vec3 cam = raw(s).pos();
        for (double[] q : quakes) {
            double d = s - q[0];
            if (d < 0 || d >= q[5]) continue;
            double dist = cam.distanceTo(new Vec3(q[1], q[2], q[3]));
            sum += (float) ((q[5] - d) / q[5] * q[4] * Mth.clamp(q[6] / Math.max(10, dist), 0, 1));
        }
        return Math.min(nuke != null ? 3.4f : condor != null ? 2.6f : twin != null ? 2.2f : 0.9f, sum);
    }

    private static float near(Vec3 a, Vec3 b) {
        return (float) Mth.clamp(30 / Math.max(10, a.distanceTo(b)), 0, 1);
    }

    private static Pose salvo(float s) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 toT = target.subtract(eye).multiply(1, 0, 1);
        Vec3 dirT = toT.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toT.normalize();
        Vec3 f = Launcher.forward(yaw), r = Launcher.right(yaw);
        Vec3 rack = site.add(f.scale(-1)).add(0, 3, 0);
        Vec3 mouth = site.add(f.scale(0.5)).add(0, 5.6, 0);
        Vec3 side = dirT.cross(up).normalize();
        Vec3 mid = lerp(site, target, 0.5);
        Vec3 across = target.subtract(site).multiply(1, 0, 1).normalize().cross(up);

        if (s < 8) {
            // 1. blanco fijado: la cámara avanza un poco desde los ojos del jugador
            float u = smooth(s / 8);
            Vec3 pos = eye.add(dirT.scale(-2 + 4 * u)).add(0, 1.5 + 2 * u, 0);
            return look(pos, target, 0, Mth.lerp(u, 70, 55));
        }
        if (s < 20) {
            // 2. reconocimiento: el blanco desde muy alto, como desde un dron, girando despacio
            float u = (s - 8) / 12;
            double ang = Math.toRadians(20 + 50 * u);
            Vec3 pos = target.add(side.scale(Math.cos(ang) * 14)).add(dirT.scale(Math.sin(ang) * 14)).add(0, 72 - 14 * u, 0);
            return look(pos, target, 0, Mth.lerp(u, 36, 28));
        }
        if (s < 32) {
            // 3. vuelo hasta la batería, en arco por el aire
            float u = smooth((s - 20) / 12);
            Vec3 a = target.add(dirT.scale(-10)).add(0, 50, 0);
            Vec3 b = site.add(f.scale(-22)).add(r.scale(10)).add(0, 8, 0);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 22, 0);
            Vec3 at = lerp(target, rack, smooth((s - 21) / 9));
            return look(pos, at, (float) Math.sin(u * Math.PI) * 5, 70);
        }
        if (s < 44) {
            // 4. la batería en reposo: la cámara recorre el camión de atrás hacia adelante
            float u = (s - 32) / 12;
            Vec3 pos = site.add(r.scale(-11)).add(f.scale(-9 + 16 * u)).add(0, 1.8 + 0.8 * u, 0);
            return look(pos, site.add(f.scale(-2 + 5 * u)).add(0, 1.6, 0), 0, 55);
        }
        if (s < 52) {
            // 5. alarma: primer plano de los estabilizadores bajando
            float u = (s - 44) / 8;
            Vec3 pos = site.add(f.scale(-7)).add(r.scale(-3.5 + 1.5 * u)).add(0, 1.3, 0);
            return look(pos, site.add(f.scale(-3)).add(0, 0.6, 0), 0, 50);
        }
        if (s < 72) {
            // 6. los lanzadores se elevan: la cámara rodea el camión
            float u = (s - 52) / 20;
            double ang = Math.toRadians(-110 + 220 * smooth(u));
            Vec3 pos = site.add(f.scale(Math.cos(ang) * 14)).add(r.scale(Math.sin(ang) * 14)).add(0, 4 + 3 * u, 0);
            return look(pos, rack.add(0, 1.5 * u, 0), 0, Mth.lerp(u, 62, 56));
        }
        if (s < 86) {
            // 7. las bocas de los tubos, desde abajo, mientras apuntan
            float u = (s - 72) / 14;
            Vec3 pos = site.add(f.scale(8 - 2 * u)).add(r.scale(2 - u)).add(0, 1.3, 0);
            return look(pos, mouth, 0, Mth.lerp(u, 55, 42));
        }
        if (s < 100) {
            // 8. cuenta regresiva: por detrás del camión, mirando hacia donde van a ir
            float u = (s - 86) / 14;
            Vec3 pos = site.add(f.scale(-24 + 5 * u)).add(r.scale(5)).add(0, 3.5, 0);
            return look(pos, site.add(f.scale(30)).add(0, 12, 0), 0, Mth.lerp(u, 60, 52));
        }
        if (s < 103) {
            // 9. despegues: plano bajo de costado
            float u = (s - 100) / 3;
            Vec3 pos = site.add(r.scale(-14)).add(f.scale(-2 + 2 * u)).add(0, 1.4, 0);
            return look(pos, mouth.add(f.scale(2)).add(0, 8 * u, 0), 0, 64);
        }
        if (s < 106) {
            // 10. desde atrás y arriba: la ráfaga sube
            float u = (s - 103) / 3;
            Vec3 pos = site.add(f.scale(-16)).add(r.scale(-4)).add(0, 6 + 4 * u, 0);
            return look(pos, lerp(site.add(f.scale(20)).add(0, 25, 0), swarm(s), 0.6), 0, 70);
        }
        if (s < 110) {
            // 11. persecución del enjambre
            float u = (s - 106) / 4;
            Vec3 c = swarm(s);
            Vec3 back = c.subtract(swarm(s - 0.5f));
            Vec3 dir = back.lengthSqr() < 1e-4 ? f : back.normalize();
            Vec3 pos = c.subtract(dir.scale(16 + 6 * u)).add(0, 5, 0).add(r.scale(4));
            return look(pos, c.add(dir.scale(10)), (float) Math.sin(u * 3) * 3, 75);
        }
        if (s < 113) {
            // 12. desde el suelo, más allá del blanco: los misiles vienen bajando
            Vec3 pos = target.add(f.scale(46)).add(r.scale(12)).add(0, 4, 0);
            return look(pos, lerp(swarm(s), target, 0.35), 0, 72);
        }
        if (s < 128) {
            // 13. impactos: de costado, con un acercamiento lento
            float u = (s - 113) / 15;
            Vec3 pos = target.add(r.scale(55 - 12 * u)).add(f.scale(-14)).add(0, 22 - 6 * u, 0);
            return look(pos, target.add(0, 4, 0), 0, 58);
        }
        if (s < 140) {
            // 14. a ras del suelo, entre el humo
            float u = (s - 128) / 12;
            Vec3 pos = target.add(r.scale(-30)).add(f.scale(-20 + 40 * u)).add(0, 3.5, 0);
            return look(pos, target.add(0, 3, 0), 0, 62);
        }
        if (s < 162) {
            // 15. la cámara rodea el lugar desde arriba
            return look(orbitCrater((s - 140) / 22, side, dirT), target.add(0, 2, 0), 0, 60);
        }
        if (s < 176) {
            // 16. vuelta a la batería
            float u = smooth((s - 162) / 14);
            Vec3 a = orbitCrater(1, side, dirT);
            Vec3 b = site.add(r.scale(16)).add(f.scale(6)).add(0, 7, 0);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 26, 0);
            return look(pos, lerp(target, rack, smooth((s - 163) / 10)), (float) -Math.sin(u * Math.PI) * 5, 68);
        }
        if (s < 190) {
            // 17. la batería se repliega
            float u = (s - 176) / 14;
            double ang = Math.toRadians(70 + 70 * smooth(u));
            Vec3 pos = site.add(f.scale(Math.cos(ang) * 16)).add(r.scale(Math.sin(ang) * 16)).add(0, 7 - 3 * u, 0);
            return look(pos, rack, 0, 60);
        }
        Vec3 high = mid.add(across.scale(-70)).add(0, 70, 0);
        if (s < 200) {
            // 18. desde lo alto: el camión y las columnas de humo
            float u = (s - 190) / 10;
            return look(lerp(mid.add(across.scale(-55)).add(0, 45, 0), high, smooth(u)), mid.add(0, 5, 0), 0, 66);
        }
        // 19. regreso al jugador
        float u = smooth(Mth.clamp((s - 200) / 10, 0, 1));
        Vec3 pos = lerp(high, eye, u).add(0, Math.sin(u * Math.PI) * 10, 0);
        Vec3 at = lerp(mid.add(0, 5, 0), eye.add(Launcher.forward(playerYaw).scale(10)), u);
        return look(pos, at, 0, Mth.lerp(u, 66, 70));
    }

    private static Vec3 orbitCrater(float u, Vec3 side, Vec3 dirT) {
        double ang = u * Math.PI * 1.5;
        return target.add(side.scale(Math.cos(ang) * 38)).add(dirT.scale(-Math.sin(ang) * 38)).add(0, 22 - 4 * u, 0);
    }

    /** Centro de los misiles que van en el aire en el segundo s. */
    private static Vec3 swarm(float s) {
        Vec3 sum = Vec3.ZERO;
        int n = 0;
        double ticks = s * 20;
        for (int i = 0; i < SalvoPlan.COUNT; i++) {
            double t = ticks - plan.launchTick[i];
            if (t < 0 || t > plan.paths[i].flightTicks) continue;
            sum = sum.add(plan.paths[i].atTick(t));
            n++;
        }
        return n == 0 ? site.add(0, 10, 0) : sum.scale(1.0 / n);
    }

    /** Misiles en el aire y caídos en el segundo s (para el texto en pantalla). */
    static int[] counts(float s) {
        double ticks = s * 20;
        int flying = 0, hit = 0;
        if (nuke != null) {
            double t = ticks - nuke.launchTick;
            return new int[]{t >= 0 && t < nuke.path.flightTicks ? 1 : 0, t >= nuke.path.flightTicks ? 1 : 0, 1};
        }
        if (condor != null) {
            double t = ticks - condor.launchTick;
            return new int[]{t >= 0 && t < condor.path.flightTicks ? 1 : 0, t >= condor.path.flightTicks ? 1 : 0, 1};
        }
        if (twin != null) {
            for (int i = 0; i < 2; i++) {
                double t = ticks - twin.launchTick[i];
                if (t >= twin.paths[i].flightTicks) hit++;
                else if (t >= 0) flying++;
            }
            return new int[]{flying, hit, 2};
        }
        if (plan == null) return new int[]{0, 0, SalvoPlan.COUNT};
        for (int i = 0; i < SalvoPlan.COUNT; i++) {
            double t = ticks - plan.launchTick[i];
            if (t >= plan.paths[i].flightTicks) hit++;
            else if (t >= 0) flying++;
        }
        return new int[]{flying, hit, SalvoPlan.COUNT};
    }

    /** Texto de abajo según el momento de la cinemática. */
    static String caption(float s) {
        if (ride) return RideScenes.caption(s);
        if (nuke != null) return NukeScenes.caption(s);
        if (condor != null) return CondorScenes.caption(s);
        if (twin != null) return TwinScenes.caption(s);
        if (s < 8) return "locked";
        if (s < 20) return "recon";
        if (s < 32) return "transit";
        if (s < 44) return "battery";
        if (s < 52) return "alarm";
        if (s < 72) return "raise";
        if (s < 86) return "aim";
        if (s < 100) return "countdown";
        if (s < 107) return "launch";
        if (s < 130) return "flight";
        if (s < 176) return "done";
        if (s < 190) return "stow";
        return "complete";
    }

    /** Segundo en que termina la cuenta regresiva (despega el primero). */
    static float launchSecond() {
        if (nuke != null) return NukePlan.LAUNCH_S;
        if (condor != null) return CondorPlan.LAUNCH_S;
        return twin != null ? TwinPlan.LAUNCH_S[0] : SalvoPlan.FIRST_LAUNCH_S;
    }

    /** Desde cuándo suena la alarma (el texto parpadea en rojo). */
    static float alarmSecond() {
        return twin != null || nuke != null ? 46 : 44;
    }

    /** Destello blanco de los impactos grandes (0 = nada, 1 = todo blanco). */
    static float flash(float s) {
        if (nuke != null) return NukeScenes.flash(s);
        if (condor != null) {
            float d = s - condor.impactTick() / 20f;
            return d >= 0 && d < 1.8f ? 1 - d / 1.8f : 0;
        }
        if (twin == null) return 0;
        float f = 0;
        for (int i = 0; i < 2; i++) {
            float d = s - (twin.launchTick[i] + twin.paths[i].flightTicks) / 20f;
            if (d >= 0 && d < 1.2f) f = Math.max(f, 1 - d / 1.2f);
        }
        return f;
    }

    /** Un temblor más (por ejemplo, cuando la onda expansiva llega a la cámara). */
    static void addQuake(float s, Vec3 at, double strength, double duration, double reach) {
        quakes.add(new double[]{s, at.x, at.y, at.z, strength, duration, reach});
    }

    static Pose look(Vec3 from, Vec3 to, float roll, float fov) {
        Vec3 d = to.subtract(from);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        return new Pose(from, yaw, pitch, roll, fov);
    }

    static Vec3 lerp(Vec3 a, Vec3 b, double u) {
        return a.add(b.subtract(a).scale(u));
    }

    static float easeOutCubic(float x) {
        x = Mth.clamp(x, 0, 1);
        float u = 1 - x;
        return 1 - u * u * u;
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Elevación de los tubos en el segundo s: suben despacio, y al final se recogen. */
    public static float rackElevation(float s) {
        return Launcher.RAISED * (smooth((s - 52) / 20) - smooth((s - 176) / 10));
    }

    /** Cuánto han bajado los estabilizadores (0 recogidos, 1 en el suelo). */
    public static float legDeploy(float s) {
        return smooth((s - 44) / 6) - smooth((s - 186) / 4);
    }

    public static BlockPos targetBlock() {
        return BlockPos.containing(target);
    }
}
