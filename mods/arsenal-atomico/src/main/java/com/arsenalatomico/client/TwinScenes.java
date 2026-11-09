package com.arsenalatomico.client;

import com.arsenalatomico.Launcher;
import com.arsenalatomico.TwinPlan;
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
 * Cinemática del Cóndor Doble (3 min 30 s). En tierra, dos lanzadoras de 8 ejes; en el mar, un
 * submarino.
 * <pre>
 *    0-8 s    blanco fijado
 *    8-20 s   reconocimiento desde lo alto
 *   20-32 s   vuelo hasta las lanzadoras (o el mar)
 *   32-41 s   las lanzadoras en fila / el submarino sale a la superficie
 *   41-46 s   la banderita
 *   46-54 s   alarma: gatos hidráulicos abajo / se preparan los tubos
 *   54-84 s   los misiles se levantan hasta quedar de pie / se abren las compuertas
 *   84-96 s   misiles armados
 *   96-110 s  cuenta regresiva (10 a 1)
 *  110-124 s  despegan los dos (salida en frío: primero el gas o el vapor, después el fuego)
 *  124-150 s  vuelo por lo alto del cielo
 *  150-188 s  caída e impactos, humo, la cámara rodea el lugar
 *  188-200 s  las lanzadoras bajan los tubos vacíos / el submarino se sumerge
 *  200-210 s  regreso al jugador
 * </pre>
 */
final class TwinScenes {
    private TwinScenes() {}

    private static final Vec3 UP = new Vec3(0, 1, 0);

    // ------------------------------------------------------------------ estado de las máquinas

    /** Grados del brazo de cada lanzadora (0 acostado, 90 de pie). */
    static float erect(float s) {
        return 90 * (smooth((s - 54) / 30) - smooth((s - 188) / 10));
    }

    /** Gatos hidráulicos (0 recogidos, 1 en el suelo). */
    static float legs(float s) {
        return smooth((s - 46) / 6) - smooth((s - 198) / 4);
    }

    /** Submarino: 0 sumergido, 1 en la superficie. */
    static float rise(float s) {
        return smooth((s - 32) / 14) - smooth((s - 194) / 10);
    }

    /** Compuertas de los tubos del submarino (0 cerradas, 1 abiertas). */
    static float hatches(float s) {
        return smooth((s - 54) / 20) - smooth((s - 186) / 6);
    }

    /** ¿El contenedor i todavía tiene la tapa? (salta al despegar) */
    static boolean cap(int i, float s) {
        TwinPlan p = Cinematic.twin;
        return p == null || s * 20 < p.launchTick[i];
    }

    /** Dónde va el misil i en el segundo s (quieto antes de salir y en el blanco después). */
    static Vec3 missile(int i, float s) {
        TwinPlan p = Cinematic.twin;
        double t = Mth.clamp(s * 20 - p.launchTick[i], 0, p.paths[i].flightTicks);
        return p.paths[i].atTick(t);
    }

    static Vec3 missileDir(int i, float s) {
        TwinPlan p = Cinematic.twin;
        double t = Mth.clamp(s * 20 - p.launchTick[i], 0, p.paths[i].flightTicks - 1);
        return p.paths[i].direction(t);
    }

    /** Banderita: en la cabina de la primera lanzadora o arriba de la torre del submarino. */
    static Vec3 flag() {
        TwinPlan p = Cinematic.twin;
        Vec3 f = Launcher.forward(p.yaw), r = Launcher.right(p.yaw);
        if (p.mode == TwinPlan.SEA) {
            return p.site.add(f.scale(6.6)).add(0, -TwinPlan.HULL_DOWN + 10.7, 0);
        }
        return TwinPlan.truck(p.site, p.yaw, 0).add(r.scale(-1.29)).add(f.scale(9.3)).add(0, 5.2, 0);
    }

    // ------------------------------------------------------------------ cámara

    static Cinematic.Pose pose(float s) {
        TwinPlan p = Cinematic.twin;
        Vec3 target = Cinematic.target, site = Cinematic.site, eye = Cinematic.eye;
        boolean sea = p.mode == TwinPlan.SEA;
        Vec3 f = Launcher.forward(p.yaw), r = Launcher.right(p.yaw);
        Vec3 toT = target.subtract(eye).multiply(1, 0, 1);
        Vec3 dirT = toT.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toT.normalize();
        Vec3 side = dirT.cross(UP).normalize();
        Vec3 center = sea ? site.add(0, 1.5, 0) : site.add(0, 3, 0);
        Vec3 hatchMid = lerp(TwinPlan.hatch(site, p.yaw, 0), TwinPlan.hatch(site, p.yaw, 1), 0.5);
        Vec3 across = target.subtract(site).multiply(1, 0, 1);
        across = across.lengthSqr() < 1 ? r : across.normalize().cross(UP);

        if (s < 8) {
            float u = smooth(s / 8);
            Vec3 pos = eye.add(dirT.scale(-2 + 4 * u)).add(0, 1.5 + 2 * u, 0);
            return look(pos, target, 0, Mth.lerp(u, 70, 55));
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
            Vec3 b = sea ? site.add(r.scale(-34)).add(f.scale(4)).add(0, 4, 0) : site.add(f.scale(-40)).add(r.scale(-18)).add(0, 14, 0);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 24, 0);
            return look(pos, lerp(target, center, smooth((s - 21) / 9)), (float) Math.sin(u * Math.PI) * 5, 70);
        }
        if (s < 41) {
            float u = (s - 32) / 9;
            if (sea) {
                // el mar en calma… y el submarino aparece
                Vec3 pos = site.add(r.scale(-34)).add(f.scale(4 - 4 * u)).add(0, 4, 0);
                return look(pos, site.add(0, 1 + 2 * smooth((s - 34) / 10), 0), 0, 55);
            }
            Vec3 pos = site.add(r.scale(-18)).add(f.scale(-16 + 28 * u)).add(0, 2.2, 0);
            return look(pos, site.add(f.scale(-6 + 22 * u)).add(0, 2.5, 0), 0, 58);
        }
        if (s < 46) {
            // la banderita
            float u = (s - 41) / 5;
            Vec3 fl = flag();
            Vec3 pos = fl.add(f.scale(2.4 - 0.4 * u)).add(r.scale(-2.6)).add(0, -0.5, 0);
            return look(pos, fl.add(f.scale(-0.4)), 0, 38);
        }
        if (s < 54) {
            if (sea) return look(site.add(f.scale(-26)).add(r.scale(5)).add(0, 5, 0), hatchMid, 0, 55);
            Vec3 t1 = TwinPlan.truck(site, p.yaw, 1);
            return look(t1.add(f.scale(-14)).add(r.scale(4.5)).add(0, 1.3, 0), t1.add(f.scale(-10.5)).add(0, 0.8, 0), 0, 50);
        }
        if (s < 84) {
            float u = (s - 54) / 30;
            if (sea) {
                double ang = Math.toRadians(200 + 100 * smooth(u));
                Vec3 pos = site.add(f.scale(Math.cos(ang) * 30)).add(r.scale(Math.sin(ang) * 30)).add(0, 6 + 4 * u, 0);
                return look(pos, hatchMid, 0, 58);
            }
            double ang = Math.toRadians(-140 + 100 * smooth(u));
            Vec3 pos = site.add(f.scale(Math.cos(ang) * 32)).add(r.scale(Math.sin(ang) * 32)).add(0, 3 + 7 * u, 0);
            return look(pos, site.add(0, 3 + 14 * erect(s) / 90, 0), 0, 60);
        }
        if (s < 96) {
            float u = (s - 84) / 12;
            if (sea) return look(hatchMid.add(0, 9 - 2 * u, 0).add(f.scale(7)).add(r.scale(3)), hatchMid, 0, 50);
            // la cámara sube por el costado del misil de pie
            Vec3 base = TwinPlan.pivot(site, p.yaw, 0).add(f.scale(-TwinPlan.CAN_OFF));
            return look(base.add(r.scale(-7)).add(0, 1 + 19 * smooth(u), 0), base.add(0, 4 + 19 * smooth(u), 0), 0, 50);
        }
        if (s < 110) {
            float u = (s - 96) / 14;
            return look(site.add(f.scale(30 - 4 * u)).add(r.scale(24)).add(0, 7, 0), center.add(0, 8, 0), 0, 55);
        }
        Vec3 m0 = missile(0, s), m1 = missile(1, s);
        Vec3 s0 = p.paths[0].p0, s1 = p.paths[1].p0;
        if (s < 113) {
            Vec3 pos = sea ? site.add(r.scale(-24)).add(f.scale(-6)).add(0, 2.5, 0)
                    : TwinPlan.truck(site, p.yaw, 0).add(r.scale(-16)).add(f.scale(-4)).add(0, 1.6, 0);
            return look(pos, m0.add(0, 3, 0), 0, 66);
        }
        if (s < 116) return look(s0.add(r.scale(-30)).add(f.scale(-26)).add(0, 2, 0), m0, 0, 55);
        if (s < 119) {
            Vec3 pos = sea ? site.add(r.scale(24)).add(f.scale(-9)).add(0, 2.5, 0)
                    : TwinPlan.truck(site, p.yaw, 1).add(r.scale(16)).add(f.scale(-4)).add(0, 1.6, 0);
            return look(pos, m1.add(0, 3, 0), 0, 66);
        }
        if (s < 124) return look(site.add(f.scale(-55)).add(0, 4, 0), lerp(m0, m1, 0.5), 0, 45);
        if (s < 140) {
            // persecución por el cielo
            float u = (s - 124) / 16;
            Vec3 d = missileDir(0, s);
            Vec3 pos = m0.subtract(d.scale(40)).add(r.scale(12)).add(0, 6, 0);
            return look(pos, m0.add(d.scale(25)), (float) Math.sin(u * 4) * 3, 70);
        }
        if (s < 150) return look(m1.add(across.scale(80)).add(0, -10, 0), m1, 0, 60);
        if (s < 154) return look(target.add(f.scale(45)).add(r.scale(16)).add(0, 4, 0), lerp(m0, target, 0.25), 0, 72);
        if (s < 170) {
            float u = (s - 154) / 16;
            return look(target.add(r.scale(75 - 15 * u)).add(f.scale(-25)).add(0, 32 - 8 * u, 0), target.add(0, 8, 0), 0, 62);
        }
        if (s < 188) return look(orbit((s - 170) / 18, side, dirT), target.add(0, 6, 0), 0, 62);
        Vec3 back = sea ? site.add(r.scale(36)).add(f.scale(8)).add(0, 8, 0) : site.add(f.scale(20)).add(r.scale(26)).add(0, 10, 0);
        if (s < 200) {
            float u = smooth((s - 188) / 12);
            Vec3 pos = lerp(orbit(1, side, dirT), back, u).add(0, Math.sin(u * Math.PI) * 26, 0);
            return look(pos, lerp(target, center, smooth((s - 189) / 8)), 0, 64);
        }
        float u = smooth(Mth.clamp((s - 200) / 10, 0, 1));
        Vec3 pos = lerp(back, eye, u).add(0, Math.sin(u * Math.PI) * 10, 0);
        return look(pos, lerp(center, eye.add(Launcher.forward(Cinematic.playerYaw).scale(10)), u), 0, Mth.lerp(u, 64, 70));
    }

    private static Vec3 orbit(float u, Vec3 side, Vec3 dirT) {
        double ang = u * Math.PI * 1.2;
        return Cinematic.target.add(side.scale(Math.cos(ang) * 60)).add(dirT.scale(-Math.sin(ang) * 60)).add(0, 32 - 6 * u, 0);
    }

    // ------------------------------------------------------------------ sonido y espuma

    static void sounds(float s) {
        int half = Cinematic.nextHalf(s);
        if (half < 0) return;
        TwinPlan p = Cinematic.twin;
        float t = half / 2f;
        boolean sea = p.mode == TwinPlan.SEA;
        Vec3 site = p.site;
        if (half == 1) Cinematic.play(ModSounds.BEEP.get(), 0.9f, 0.6f);
        if (t >= 46 && t < 110 && half % 4 == 0) Cinematic.playAt(ModSounds.ALARM.get(), site.add(0, 3, 0), 3f, 0.95f);
        if (sea) {
            if (t >= 33 && t < 46 && half % 3 == 0) {
                Cinematic.playAt(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, site, 3f, 0.6f);
            }
            if (t == 40 || t == 42) Cinematic.playAt(SoundEvents.GENERIC_SPLASH, site, 4f, 0.5f);
            if ((t >= 54 && t < 74 || t >= 186 && t < 192) && half % 6 == 0) {
                Cinematic.playAt(ModSounds.HYDRAULIC.get(), TwinPlan.hatch(site, p.yaw, 0), 2.5f, 0.8f);
            }
            if (t == 74) Cinematic.playAt(SoundEvents.IRON_DOOR_CLOSE, TwinPlan.hatch(site, p.yaw, 0), 2.5f, 0.5f);
            if (t == 194 || t == 196) Cinematic.playAt(SoundEvents.GENERIC_SPLASH, site, 4f, 0.4f);
        } else {
            for (int i = 0; i < 2; i++) {
                Vec3 truck = TwinPlan.truck(site, p.yaw, i), pivot = TwinPlan.pivot(site, p.yaw, i);
                if (t == 46 || t == 49 || t == 198 || t == 200) Cinematic.playAt(ModSounds.HYDRAULIC.get(), truck, 2f, 0.7f);
                if ((t >= 54 && t < 84 || t >= 188 && t < 198) && half % 6 == 0) {
                    Cinematic.playAt(ModSounds.HYDRAULIC.get(), pivot, 2.5f, 0.75f);
                }
                if (t == 84) Cinematic.playAt(SoundEvents.ANVIL_LAND, pivot, 2f, 0.5f);
            }
        }
        float launch = TwinPlan.LAUNCH_S[0];
        if (t >= launch - 10 && t < launch && half % 2 == 0) Cinematic.play(ModSounds.BEEP.get(), t >= launch - 3 ? 1.25f : 1f, 0.9f);
        if (t == launch) Cinematic.play(ModSounds.BEEP.get(), 1.6f, 1f);
    }

    /** Espuma del submarino al salir y al sumergirse, y polvo de los gatos al tocar el suelo. */
    static void particles(float s) {
        var level = Minecraft.getInstance().level;
        TwinPlan p = Cinematic.twin;
        if (level == null || p == null) return;
        Vec3 f = Launcher.forward(p.yaw), r = Launcher.right(p.yaw);
        var rnd = level.random;
        if (p.mode == TwinPlan.SEA) {
            boolean surfacing = s >= 36 && s < 48, diving = s >= 194 && s < 204;
            if (!surfacing && !diving) return;
            for (int i = 0; i < 14; i++) {
                double z = -32 + rnd.nextDouble() * 56, x = (rnd.nextBoolean() ? 1 : -1) * (2.5 + rnd.nextDouble() * 1.5);
                Vec3 q = p.site.add(f.scale(z)).add(r.scale(x)).add(0, 0.2, 0);
                level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, q.x, q.y, q.z, 0, 0.2, 0);
                if (i % 3 == 0) level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, q.x, q.y + 0.3, q.z,
                        (rnd.nextDouble() - 0.5) * 0.1, 0.05, (rnd.nextDouble() - 0.5) * 0.1);
                if (diving) level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_POP, true, q.x, q.y, q.z, 0, 0.05, 0);
            }
        } else if (s >= 50 && s < 52.5f) {
            for (int i = 0; i < 2; i++) {
                Vec3 t = TwinPlan.truck(p.site, p.yaw, i);
                for (double z : new double[]{-10.5, 3.2}) {
                    for (int sd = -1; sd <= 1; sd += 2) {
                        Vec3 q = t.add(f.scale(z)).add(r.scale(sd * 2.4)).add(0, 0.1, 0);
                        level.addParticle(ParticleTypes.CLOUD, q.x, q.y, q.z, (rnd.nextDouble() - 0.5) * 0.1, 0.02, (rnd.nextDouble() - 0.5) * 0.1);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ textos

    static String caption(float s) {
        String m = Cinematic.twin.mode == TwinPlan.SEA ? "_sea" : "_land";
        if (s < 8) return "locked";
        if (s < 20) return "recon";
        if (s < 32) return "twin.transit" + m;
        if (s < 41) return "twin.intro" + m;
        if (s < 46) return "twin.flag";
        if (s < 54) return "twin.alarm" + m;
        if (s < 84) return "twin.raise" + m;
        if (s < 100) return "twin.armed";
        if (s < 110) return "countdown";
        if (s < 124) return "launch";
        if (s < 154) return "flight";
        if (s < 188) return "done";
        if (s < 200) return "twin.stow" + m;
        return "complete";
    }
}
