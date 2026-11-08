package com.arsenalatomico.client;

import com.arsenalatomico.Launcher;
import com.arsenalatomico.SalvoPlan;
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
 * La cinemática: una cámara que vuela por el mundo. Todas duran 3 min 30 s. La Lluvia de Acero:
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
    private static Vec3 target, site, eye;
    private static float yaw, playerYaw;
    private static SalvoPlan plan;
    private static int duration;
    private static long startTick, clientTicks;
    private static Marker camera;
    private static CameraType previous;
    private static int nextBeep;

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
        plan = new SalvoPlan(site, yaw, target, p.seed);
        duration = p.ticks;
        startTick = clientTicks;
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
        LauncherRenderer.show(site, yaw, startTick, duration);
        active = true;
        play(ModSounds.BEEP.get(), 0.75f, 0.6f);
    }

    public static void stop() {
        if (!active) return;
        active = false;
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
        sounds(seconds(0));
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

    private static void sounds(float s) {
        int half = (int) Math.floor(s * 2);   // cada medio segundo
        if (half < nextBeep) return;
        nextBeep = half + 1;
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
        Pose p = ground(salvo(s));
        float shake = shake(s);
        if (shake > 0.001f) {
            double t = s * 37;
            Vec3 off = new Vec3(Math.sin(t) * shake, Math.cos(t * 1.3) * shake, Math.sin(t * 0.7 + 1) * shake);
            p = new Pose(p.pos().add(off), p.yaw() + (float) Math.sin(t * 1.7) * shake * 3, p.pitch(), p.roll(), p.fov());
        }
        return p;
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

    /** Temblor por los impactos cercanos y por los despegues. */
    private static float shake(float s) {
        if (plan == null) return 0;
        float sum = 0;
        Vec3 cam = salvo(s).pos();
        for (int i = 0; i < SalvoPlan.COUNT; i++) {
            float launch = plan.launchTick[i] / 20f, hit = (plan.launchTick[i] + plan.paths[i].flightTicks) / 20f;
            float d1 = s - launch, d2 = s - hit;
            if (d1 >= 0 && d1 < 0.6f) sum += (0.6f - d1) * 0.25f * near(cam, plan.paths[i].p0);
            if (d2 >= 0 && d2 < 1.2f) sum += (1.2f - d2) * 0.6f * near(cam, plan.paths[i].p3);
        }
        return Math.min(0.9f, sum);
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
        if (plan == null) return new int[]{0, 0};
        int flying = 0, hit = 0;
        double ticks = s * 20;
        for (int i = 0; i < SalvoPlan.COUNT; i++) {
            double t = ticks - plan.launchTick[i];
            if (t >= plan.paths[i].flightTicks) hit++;
            else if (t >= 0) flying++;
        }
        return new int[]{flying, hit};
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
