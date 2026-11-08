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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * La cinemática: una cámara que vuela por el mundo. Para la Lluvia de Acero:
 * <pre>
 *  0-6 s    objetivo fijado (desde el jugador)
 *  6-15 s   vuelo hasta la batería
 *  15-24 s  alarma, la batería se levanta, cuenta regresiva
 *  24-30 s  24 misiles salen uno tras otro
 *  30-33 s  persecución del enjambre
 *  33-45 s  impactos sobre el blanco
 *  45-56 s  humo y fuego, la cámara rodea el lugar
 *  56-62 s  regreso al jugador
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
        play(SoundEvents.NOTE_BLOCK_BELL.get(), 0.7f, 1f);
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

    private static void sounds(float s) {
        // alarma de la batería (15-24 s) y cuenta regresiva (19-24 s)
        int beep = (int) Math.floor(s * 2);
        if (beep >= nextBeep) {
            nextBeep = beep + 1;
            if (s >= 15 && s < 24) play(SoundEvents.NOTE_BLOCK_BIT.get(), beep % 2 == 0 ? 1.6f : 1.2f, 0.7f);
            if (s >= 19 && s < 24 && beep % 2 == 0) play(SoundEvents.NOTE_BLOCK_PLING.get(), 2f, 0.9f);
            if (beep == 48) play(SoundEvents.NOTE_BLOCK_BASEDRUM.get(), 0.5f, 1f);
        }
    }

    // ------------------------------------------------------------------ cámara

    public static Pose pose(float partial) {
        float s = seconds(partial);
        Pose p = salvo(s);
        float shake = shake(s);
        if (shake > 0.001f) {
            double t = s * 37;
            Vec3 off = new Vec3(Math.sin(t) * shake, Math.cos(t * 1.3) * shake, Math.sin(t * 0.7 + 1) * shake);
            p = new Pose(p.pos().add(off), p.yaw() + (float) Math.sin(t * 1.7) * shake * 3, p.pitch(), p.roll(), p.fov());
        }
        return p;
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

        if (s < 6) {
            // 1. objetivo fijado: la cámara avanza un poco desde los ojos del jugador
            float u = smooth(s / 6);
            Vec3 pos = eye.add(dirT.scale(-2 + 4 * u)).add(0, 1.5 + 2 * u, 0);
            return look(pos, target, 0, Mth.lerp(u, 70, 55));
        }
        if (s < 15) {
            // 2. vuelo hasta la batería, en arco por el aire
            float u = smooth((s - 6) / 9);
            Vec3 a = eye.add(dirT.scale(2)).add(0, 3.5, 0);
            Vec3 b = site.add(f.scale(16)).add(r.scale(11)).add(0, 7, 0);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 28, 0);
            Vec3 at = lerp(target, rack, smooth(Mth.clamp((s - 7) / 6, 0, 1)));
            return look(pos, at, (float) Math.sin(u * Math.PI) * 6, 70);
        }
        if (s < 24) {
            // 3. alarma y la batería se levanta: la cámara rodea despacio el camión
            float u = (s - 15) / 9;
            double ang = Math.toRadians(-40 + 110 * smooth(u));
            Vec3 off = f.scale(Math.cos(ang) * 15).add(r.scale(Math.sin(ang) * 15));
            Vec3 pos = site.add(off).add(0, 6 - 2.5 * u, 0);
            return look(pos, rack, 0, Mth.lerp(u, 70, 58));
        }
        if (s < 30) {
            // 4. despegues: plano bajo de costado, cerca de las bocas
            float u = (s - 24) / 6;
            Vec3 pos = site.add(r.scale(-13)).add(f.scale(-5 + 4 * u)).add(0, 1.6, 0);
            Vec3 at = rack.add(f.scale(2)).add(0, 2 + 10 * smooth(u), 0);
            return look(pos, at, 0, 62);
        }
        if (s < 33) {
            // 5. persecución del enjambre
            float u = (s - 30) / 3;
            Vec3 c = swarm(s);
            Vec3 back = c.subtract(swarm(s - 0.5f));
            Vec3 dir = back.lengthSqr() < 1e-4 ? f : back.normalize();
            Vec3 pos = c.subtract(dir.scale(16 + 6 * u)).add(0, 5, 0).add(r.scale(4));
            return look(pos, c.add(dir.scale(10)), (float) Math.sin(u * 3) * 3, 75);
        }
        Vec3 side = dirT.cross(up).normalize();
        if (s < 45) {
            // 6. impactos: de costado al blanco, con un acercamiento lento
            float u = (s - 33) / 12;
            Vec3 pos = target.add(side.scale(46 - 10 * u)).add(dirT.scale(-18)).add(0, 20 - 6 * u, 0);
            return look(pos, target.add(0, 4, 0), 0, 60);
        }
        if (s < 56) {
            // 7. humo y fuego: la cámara rodea el lugar
            float u = (s - 45) / 11;
            double ang = u * Math.PI * 0.9;
            Vec3 off = side.scale(Math.cos(ang) * 34).add(dirT.scale(-Math.sin(ang) * 34));
            return look(target.add(off).add(0, 14, 0), target.add(0, 3, 0), 0, 62);
        }
        // 8. regreso al jugador
        float u = smooth(Mth.clamp((s - 56) / 6, 0, 1));
        double ang = Math.PI * 0.9;
        Vec3 a = target.add(side.scale(Math.cos(ang) * 34).add(dirT.scale(-Math.sin(ang) * 34))).add(0, 14, 0);
        Vec3 pos = lerp(a, eye, u).add(0, Math.sin(u * Math.PI) * 10, 0);
        Vec3 at = lerp(target.add(0, 3, 0), eye.add(Launcher.forward(playerYaw).scale(10)), u);
        return look(pos, at, 0, Mth.lerp(u, 62, 70));
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

    /** El camión ya estaba antes de que llegara la cámara: elevación de la batería en el segundo s. */
    public static float rackElevation(float s) {
        return Launcher.RAISED * smooth((s - 16) / 5);
    }

    public static BlockPos targetBlock() {
        return BlockPos.containing(target);
    }
}
