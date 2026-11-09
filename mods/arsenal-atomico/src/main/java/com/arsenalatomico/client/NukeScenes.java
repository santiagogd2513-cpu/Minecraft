package com.arsenalatomico.client;

import com.arsenalatomico.Launcher;
import com.arsenalatomico.NukePlan;
import com.arsenalatomico.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.arsenalatomico.client.Cinematic.lerp;
import static com.arsenalatomico.client.Cinematic.look;
import static com.arsenalatomico.client.Cinematic.smooth;

/**
 * Cinemática del Protocolo Hongo (3 min 30 s):
 * <pre>
 *    0-8 s    blanco fijado
 *    8-20 s   reconocimiento desde lo alto
 *   20-32 s   vuelo hasta el complejo
 *   32-44 s   el complejo desde el aire: la cerca, las torres, la caseta y la compuerta
 *   44-50 s   suena la sirena; la cámara llega a la puerta de la caseta
 *   50-60 s   bajada por la escalera de caracol
 *   60-68 s   el pasillo, con sus luces rojas
 *   68-80 s   el cuarto de control: consolas, el mapa y las llaves
 *   80-90 s   por la ventana: el misil en el pozo; se prenden las luces
 *   90-98 s   dentro del pozo: se sueltan los brazos de servicio
 *   98-102 s  se giran las dos llaves
 *  100-110 s  se abre la compuerta; cuenta regresiva (10 a 1)
 *  110-128 s  ignición y despegue; persecución
 *  128-141 s  el arco en el cielo y la caída
 *  141-152 s  desde 320 bloques: la detonación, la bola de fuego y la onda expansiva que llega
 *  152-176 s  el hongo sube; la cámara lo rodea y pasa cerca del tallo
 *  176-188 s  desde muy alto: el hongo y el cráter
 *  188-202 s  el búnker se cierra con el hongo de fondo
 *  202-210 s  regreso al jugador
 * </pre>
 */
final class NukeScenes {
    private NukeScenes() {}

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float FLOOR = -(NukePlan.DEPTH - NukePlan.ROOM_UP);
    private static final float EYE = FLOOR + 1.62f;
    private static boolean windPlayed;
    private static float lastHalf = -1;

    static void reset() {
        windPlayed = false;
        lastHalf = -1;
    }

    /** ¿La cámara está bajo tierra (no hay que subirla al suelo)? */
    static boolean underground(float s) {
        return s >= 49 && s < 102;
    }

    private static Vec3 L(double a, double b, double y) {
        NukePlan p = Cinematic.nuke;
        return NukePlan.local(new Vec3(p.site.x, Math.floor(p.site.y), p.site.z), p.yaw, a, b, y);
    }

    static Vec3 missile(float s) {
        NukePlan p = Cinematic.nuke;
        double t = Mth.clamp(s * 20 - p.launchTick, 0, p.path.flightTicks);
        return p.path.atTick(t);
    }

    static Vec3 dir(float s) {
        NukePlan p = Cinematic.nuke;
        double t = Mth.clamp(s * 20 - p.launchTick, 0, p.path.flightTicks - 1);
        return p.path.direction(t);
    }

    /** Lugar desde donde se ve la detonación: de costado al vuelo, a cierta distancia del blanco. */
    static Vec3 vantage(double dist, double height) {
        NukePlan p = Cinematic.nuke;
        Vec3 t = Cinematic.target;
        Vec3 along = t.subtract(p.site).multiply(1, 0, 1);
        along = along.lengthSqr() < 1 ? new Vec3(0, 0, 1) : along.normalize();
        Vec3 across = along.cross(UP).normalize();
        return t.add(across.scale(dist * 0.92)).add(along.scale(-dist * 0.38)).add(0, height, 0);
    }

    static Cinematic.Pose pose(float s) {
        NukePlan p = Cinematic.nuke;
        Vec3 target = Cinematic.target, eye = Cinematic.eye;
        Vec3 site = new Vec3(p.site.x, Math.floor(p.site.y), p.site.z);
        Vec3 f = Launcher.forward(p.yaw), r = Launcher.right(p.yaw);
        Vec3 toT = target.subtract(eye).multiply(1, 0, 1);
        Vec3 dirT = toT.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toT.normalize();
        Vec3 side = dirT.cross(UP).normalize();
        float impact = p.impactSecond();
        float H = NukeFx.height(Math.max(0, s - impact));

        if (s < 8) {
            float u = smooth(s / 8);
            return look(eye.add(dirT.scale(-2 + 4 * u)).add(0, 1.5 + 2 * u, 0), target, 0, Mth.lerp(u, 70, 55));
        }
        if (s < 20) {
            float u = (s - 8) / 12;
            double ang = Math.toRadians(20 + 50 * u);
            Vec3 pos = target.add(side.scale(Math.cos(ang) * 14)).add(dirT.scale(Math.sin(ang) * 14)).add(0, 72 - 14 * u, 0);
            return look(pos, target, 0, Mth.lerp(u, 36, 28));
        }
        if (s < 32) {
            float u = smooth((s - 20) / 12);
            Vec3 a = target.add(dirT.scale(-10)).add(0, 50, 0);
            Vec3 b = L(40, -40, 30);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 30, 0);
            return look(pos, lerp(target, site, smooth((s - 21) / 9)), (float) Math.sin(u * Math.PI) * 5, 70);
        }
        if (s < 44) {
            // el complejo desde el aire, dando la vuelta
            float u = (s - 32) / 12;
            double ang = Math.toRadians(-40 + 130 * smooth(u));
            Vec3 pos = L(Math.cos(ang) * 46 - 10, Math.sin(ang) * 46, 26 - 10 * u);
            return look(pos, L(-12, 0, 0), 0, 62);
        }
        if (s < 49) {
            // la caseta: la cámara llega a la puerta
            float u = smooth((s - 44) / 5);
            Vec3 pos = lerp(L(-6, 6, 4), L(NukePlan.STAIR_A + 5.2, 0, 1.62), u);
            return look(pos, L(NukePlan.STAIR_A, 0, 1.2), 0, Mth.lerp(u, 62, 70));
        }
        if (s < 50) {
            float u = smooth(s - 49);
            Vec3 pos = lerp(L(NukePlan.STAIR_A + 5.2, 0, 1.62), L(NukePlan.STAIR_A, 1.05, 1.62), u);
            return look(pos, L(NukePlan.STAIR_A - 1, 0, 0), 0, 72);
        }
        if (s < 60) {
            // la escalera de caracol: 2,4 vueltas alrededor del pilar, bajando 20 escalones
            float u = (s - 50) / 10;
            double th = Math.toRadians(90 - 855 * u);
            double y = Mth.lerp(u, 1.62, EYE + 0.5);
            Vec3 pos = L(NukePlan.STAIR_A + Math.cos(th) * 1.05, Math.sin(th) * 1.05, y);
            double th2 = th - 0.9;
            Vec3 ahead = L(NukePlan.STAIR_A + Math.cos(th2) * 1.05, Math.sin(th2) * 1.05, y - 2.2);
            return look(pos, ahead, 0, 78);
        }
        if (s < 68) {
            // el pasillo
            float u = smooth((s - 60) / 8);
            Vec3 pos = u < 0.12 ? lerp(L(NukePlan.STAIR_A + 1, -1, EYE), L(NukePlan.STAIR_A + 2, 0, EYE), u / 0.12)
                    : lerp(L(NukePlan.STAIR_A + 2, 0, EYE), L(-15.5, 0, EYE), (u - 0.12) / 0.88);
            return look(pos, L(-8, 0, EYE - 0.2), 0, 72);
        }
        if (s < 74) {
            // el cuarto: de un lado al otro mirando las consolas y la ventana
            float u = smooth((s - 68) / 6);
            Vec3 pos = L(-13.5, Mth.lerp(u, -5.2, 5.2), EYE + 0.4);
            return look(pos, L(-8, Mth.lerp(u, -3, 3), EYE - 0.1), 0, 70);
        }
        if (s < 80) {
            // el mapa grande y las estaciones de llaves
            float u = smooth((s - 74) / 6);
            Vec3 pos = L(-9.5, Mth.lerp(u, 1.5, -1), EYE + 0.2);
            return look(pos, L(-14.5, Mth.lerp(u, 4.6, 3.5), EYE + 0.6), 0, 66);
        }
        if (s < 90) {
            // por la ventana: el misil en el pozo
            float u = smooth((s - 80) / 10);
            Vec3 pos = L(Mth.lerp(u, -10.5, -8.4), 0, EYE + 0.3);
            return look(pos, L(0, 0, EYE + Mth.lerp(u, 1, 4)), 0, Mth.lerp(u, 70, 56));
        }
        if (s < 98) {
            // dentro del pozo: los brazos se sueltan
            float u = smooth((s - 90) / 8);
            double ang = Math.toRadians(20 + 60 * u);
            double y = -NukePlan.DEPTH + Mth.lerp(u, 6, 20);
            Vec3 pos = L(Math.cos(ang) * 2.7, Math.sin(ang) * 2.7, y);
            return look(pos, L(0, 0, y + 1.5), 0, 80);
        }
        if (s < 102) {
            // las llaves
            float u = (s - 98) / 4;
            Vec3 pos = L(-12.2, 3.2 - 0.4 * u, EYE + 0.5);
            return look(pos, L(-13, 4, FLOOR + 2.1), 0, 50);
        }
        if (s < 110) {
            // la compuerta se abre; cuenta regresiva
            float u = (s - 102) / 8;
            Vec3 pos = L(-26 + 6 * u, 20 - 4 * u, 13 - 3 * u);
            return look(pos, L(0, 0, -2), 0, 58);
        }
        Vec3 m = missile(s), d = dir(s);
        if (s < 114) return look(L(-11, -11, 3), lerp(L(0, 0, 3), m, smooth((s - 110) / 4)), 0, 70);
        if (s < 120) return look(L(-70, -30, 3), m, 0, 52);
        if (s < 128) {
            float u = (s - 120) / 8;
            return look(m.subtract(d.scale(40)).add(r.scale(12)), m.add(d.scale(30)), (float) Math.sin(u * 3) * 3, 70);
        }
        if (s < 135) return look(L(-40, 0, 25), m, 0, 38);
        if (s < 141) {
            float u = (s - 135) / 6;
            return look(m.subtract(d.scale(36)).add(0, 8, 0), m.add(d.scale(50)), (float) Math.sin(u * 4) * 2, 74);
        }
        if (s < 152) {
            // la detonación desde lejos
            Vec3 v = vantage(320, 22);
            float k = Mth.clamp((s - impact) / 7, 0, 1);
            return look(v, target.add(0, Mth.lerp(k, 18, H * 0.6f), 0), 0, Mth.lerp(k, 48, 62));
        }
        if (s < 166) {
            // el hongo sube; la cámara lo rodea despacio
            float u = (s - 152) / 14;
            Vec3 v = vantage(320, 22);
            Vec3 rel = v.subtract(target);
            double ang = u * 0.5;
            Vec3 rot = new Vec3(rel.x * Math.cos(ang) - rel.z * Math.sin(ang), 0, rel.x * Math.sin(ang) + rel.z * Math.cos(ang));
            return look(target.add(rot.scale(Mth.lerp(u, 1, 0.92))).add(0, 18 + 20 * u, 0), target.add(0, H * 0.62f, 0), 0, 64);
        }
        if (s < 176) {
            // pase cerca del tallo, entre el polvo
            float u = smooth((s - 166) / 10);
            Vec3 v = vantage(300, 22);
            Vec3 rel = v.subtract(target);
            Vec3 rot = new Vec3(rel.x * Math.cos(0.5) - rel.z * Math.sin(0.5), 0, rel.x * Math.sin(0.5) + rel.z * Math.cos(0.5)).normalize();
            Vec3 pos = target.add(rot.scale(Mth.lerp(u, 290, 105))).add(0, Mth.lerp(u, 38, 62), 0);
            return look(pos, target.add(0, H * Mth.lerp(u, 0.6f, 0.85f), 0), 0, Mth.lerp(u, 64, 78));
        }
        if (s < 188) {
            // desde muy alto: el hongo entero y el cráter
            float u = (s - 176) / 12;
            Vec3 v = vantage(360, 0);
            Vec3 rel = v.subtract(target);
            return look(target.add(rel.scale(Mth.lerp(u, 1, 0.9))).add(0, Mth.lerp(u, 170, 230), 0), target.add(0, H * 0.45f, 0), 0, 66);
        }
        if (s < 202) {
            // el búnker se cierra, con el hongo de fondo
            float u = smooth((s - 188) / 14);
            Vec3 toTarget = target.subtract(site).multiply(1, 0, 1).normalize();
            Vec3 pos = site.subtract(toTarget.scale(Mth.lerp(u, 34, 26))).add(toTarget.cross(UP).scale(10)).add(0, Mth.lerp(u, 9, 6), 0);
            return look(pos, lerp(site.add(0, 2, 0), target.add(0, H * 0.7f, 0), 0.08 + 0.1 * u), 0, 70);
        }
        float u = smooth(Mth.clamp((s - 202) / 8, 0, 1));
        Vec3 toTarget = target.subtract(site).multiply(1, 0, 1).normalize();
        Vec3 back = site.subtract(toTarget.scale(26)).add(toTarget.cross(UP).scale(10)).add(0, 6, 0);
        Vec3 pos = lerp(back, eye, u).add(0, Math.sin(u * Math.PI) * 10, 0);
        return look(pos, lerp(target.add(0, H * 0.7f, 0), eye.add(Launcher.forward(Cinematic.playerYaw).scale(10)), u), 0, 70);
    }

    // ------------------------------------------------------------------ sonido

    static void sounds(float s) {
        int half = Cinematic.nextHalf(s);
        if (half < 0) return;
        NukePlan p = Cinematic.nuke;
        float t = half / 2f;
        Vec3 site = new Vec3(p.site.x, Math.floor(p.site.y), p.site.z);
        Vec3 room = L(-11, 0, EYE);
        Vec3 camPos = Cinematic.pose(0).pos();
        if (half == 1) Cinematic.play(ModSounds.BEEP.get(), 0.9f, 0.6f);
        // sirena antiaérea afuera y alarma adentro
        if (NukeRenderer.alarm(t) && half % 16 == 0) {
            Cinematic.playAt(ModSounds.SIREN.get(), L(NukePlan.STAIR_A, 0, 6), 6f, 1f);
            Cinematic.playAt(ModSounds.SIREN.get(), site.add(0, 3, 0), 6f, 0.97f);
        }
        if (NukeRenderer.alarm(t) && underground(t) && half % 4 == 0) Cinematic.playAt(ModSounds.ALARM.get(), room, 2.5f, 0.85f);
        if (t == 48.5f) Cinematic.playAt(SoundEvents.WOODEN_DOOR_OPEN, L(NukePlan.STAIR_A + 4, 0, 1), 2f, 0.8f);
        if (t == 50.5f) Cinematic.playAt(SoundEvents.WOODEN_DOOR_CLOSE, L(NukePlan.STAIR_A + 4, 0, 1), 2f, 0.8f);
        // pasos en la escalera y el pasillo
        if (t >= 50 && t < 68 && half % 1 == 0) Cinematic.playAt(SoundEvents.STONE_STEP, camPos.add(0, -1.5, 0), 0.6f, 0.9f + (half % 3) * 0.05f);
        if (t == 63) Cinematic.playAt(SoundEvents.IRON_DOOR_OPEN, L(-19, 0, EYE), 2f, 0.7f);
        // las consolas pitan
        if (t >= 68 && t < 102 && half % 3 == 0) Cinematic.playAt(SoundEvents.UI_BUTTON_CLICK.get(), L(-8, (half % 5) - 2, EYE), 0.4f, 1.6f + (half % 4) * 0.1f);
        if (t >= 80 && t < 90 && half % 2 == 0 && t >= 83 && t < 87) Cinematic.playAt(SoundEvents.BEACON_ACTIVATE, room, 1f, 1.5f);
        // los brazos se sueltan con vapor
        if (t >= 91 && t < 96 && half % 2 == 0) Cinematic.playAt(SoundEvents.FIRE_EXTINGUISH, L(0, 0, -NukePlan.DEPTH + 10), 3f, 0.6f);
        if (t == 96) Cinematic.playAt(SoundEvents.ANVIL_LAND, L(0, 0, -NukePlan.DEPTH + 10), 2f, 0.6f);
        // las llaves
        if (t == 100) {
            Cinematic.play(SoundEvents.LEVER_CLICK, 0.7f, 1f);
            Cinematic.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.5f, 1f);
        }
        // la compuerta
        if (t >= 100 && t < 108 && half % 5 == 0) Cinematic.playAt(ModSounds.HYDRAULIC.get(), site, 4f, 0.5f);
        if (t == 108 || t == 204) Cinematic.playAt(SoundEvents.IRON_DOOR_CLOSE, site, 4f, 0.35f);
        if (t >= 196 && t < 204 && half % 5 == 0) Cinematic.playAt(ModSounds.HYDRAULIC.get(), site, 4f, 0.5f);
        // cuenta regresiva
        float launch = NukePlan.LAUNCH_S;
        if (t >= launch - 10 && t < launch && half % 2 == 0) Cinematic.play(ModSounds.BEEP.get(), t >= launch - 3 ? 1.25f : 1f, 0.9f);
        if (t == launch) Cinematic.play(ModSounds.BEEP.get(), 1.6f, 1f);
        // la detonación: el estruendo llega un poco después del destello, y luego el retumbar
        float impact = p.impactSecond();
        if (t == impact - 3) Cinematic.playAt(ModSounds.FAR_BOOM.get(), Cinematic.target.add(0, 60, 0), 6f, 1.7f);
        if (t == impact + 1) Cinematic.play(ModSounds.NUKE_BLAST.get(), 1f, 1f);
        if (t == impact + 7 || t == impact + 15) Cinematic.play(ModSounds.FAR_BOOM.get(), 0.45f, 0.8f);
        // el viento de la onda expansiva cuando llega a la cámara
        float a = s - impact;
        if (!windPlayed && a > 0 && NukeFx.shock(a) >= camPos.distanceTo(Cinematic.target)) {
            windPlayed = true;
            Cinematic.play(ModSounds.NUKE_WIND.get(), 1f, 1f);
            Cinematic.addQuake(s, camPos, 2.4, 3.5, 60);
        }
    }

    static void particles(float s) {
        var level = Minecraft.getInstance().level;
        NukePlan p = Cinematic.nuke;
        if (level == null || p == null) return;
        var rnd = level.random;
        Vec3 site = new Vec3(p.site.x, Math.floor(p.site.y), p.site.z);
        float open = NukeRenderer.hatch(s);
        // vapor por la boca abierta
        if (open > 0.3f && s < NukePlan.LAUNCH_S) {
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, site.x + (rnd.nextDouble() - 0.5) * 5, site.y - 1,
                        site.z + (rnd.nextDouble() - 0.5) * 5, (rnd.nextDouble() - 0.5) * 0.05, 0.12 + rnd.nextDouble() * 0.1, (rnd.nextDouble() - 0.5) * 0.05);
            }
        }
        // los brazos se sueltan con chorros de vapor
        if (s >= 91 && s < 97) {
            for (float y : new float[]{-5.5f, 0f, 5.5f}) {
                Vec3 q = NukePlan.start(p.site).add(0, y, 0);
                for (int i = 0; i < 4; i++) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, q.x + (rnd.nextDouble() - 0.5) * 3, q.y, q.z + (rnd.nextDouble() - 0.5) * 3,
                            (rnd.nextDouble() - 0.5) * 0.3, 0.08, (rnd.nextDouble() - 0.5) * 0.3);
                }
            }
        }
        // ignición: llamaradas y humo por las ventilas y la boca
        float ig = s - NukePlan.LAUNCH_S;
        if (ig >= 0 && ig < 10) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 v = L(NukePlan.VENT_A, side * NukePlan.VENT_B, 0.5);
                for (int i = 0; i < 9; i++) {
                    level.addAlwaysVisibleParticle(i < 6 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, true,
                            v.x + (rnd.nextDouble() - 0.5) * 2.5, v.y, v.z + (rnd.nextDouble() - 0.5) * 2.5,
                            (rnd.nextDouble() - 0.5) * 0.3, 0.7 + rnd.nextDouble() * 0.5, (rnd.nextDouble() - 0.5) * 0.3);
                }
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, v.x, v.y + 1, v.z, 0, 0.25, 0);
            }
            for (int i = 0; i < 8; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, site.x + (rnd.nextDouble() - 0.5) * 6, site.y,
                        site.z + (rnd.nextDouble() - 0.5) * 6, (rnd.nextDouble() - 0.5) * 0.7, 0.6 + rnd.nextDouble() * 0.6, (rnd.nextDouble() - 0.5) * 0.7);
            }
            if (ig < 1) {
                for (int i = 0; i < 20; i++) level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, site.x, NukePlan.bottom(p.site) + 1, site.z, 0, 0, 0);
            }
        }
    }

    static String caption(float s) {
        float impact = Cinematic.nuke.impactSecond();
        if (s < 8) return "locked";
        if (s < 20) return "recon";
        if (s < 32) return "nuke.transit";
        if (s < 44) return "nuke.compound";
        if (s < 50) return "nuke.alarm";
        if (s < 60) return "nuke.stairs";
        if (s < 68) return "nuke.corridor";
        if (s < 80) return "nuke.control";
        if (s < 90) return "nuke.window";
        if (s < 98) return "nuke.umbilical";
        if (s < 102) return "nuke.keys";
        if (s < 110) return "nuke.doors";
        if (s < 128) return "nuke.liftoff";
        if (s < impact) return "nuke.flight";
        if (s < impact + 5) return "nuke.detonation";
        if (s < impact + 12) return "nuke.shockwave";
        if (s < 188) return "nuke.mushroom";
        if (s < 202) return "nuke.close";
        return "complete";
    }

    /** Destello: blanco total, una baja, un segundo pico (como el doble destello real) y se apaga. */
    static float flash(float s) {
        float d = s - Cinematic.nuke.impactSecond();
        if (d < 0) return 0;
        if (d < 0.35f) return 1f;
        if (d < 0.55f) return 0.78f;
        if (d < 0.8f) return 0.95f;
        return Math.max(0, 0.95f - (d - 0.8f) / 2.6f);
    }
}
