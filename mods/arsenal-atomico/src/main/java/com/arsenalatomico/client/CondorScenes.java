package com.arsenalatomico.client;

import com.arsenalatomico.CondorPlan;
import com.arsenalatomico.Launcher;
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
 * Cinemática del Cóndor (3 min 30 s):
 * <pre>
 *    0-8 s    blanco fijado
 *    8-20 s   reconocimiento desde lo alto
 *   20-32 s   vuelo hasta el silo
 *   32-44 s   el silo cerrado, en silencio
 *   44-48 s   alarma: balizas rojas
 *   48-60 s   se abren las compuertas, sale vapor
 *   60-78 s   la cámara baja por el pozo junto al misil; se prenden las luces
 *   78-90 s   desde el fondo, mirando arriba: se sueltan los brazos de servicio
 *   90-100 s  la cámara sale del pozo
 *  100-110 s  cuenta regresiva (10 a 1)
 *  110-120 s  ignición: fuego en el pozo y por los ductos; el misil sale
 *  120-136 s  persecución; se suelta la primera etapa (130 s)
 *  136-150 s  el arco de humo en el cielo; se suelta la ojiva (144 s)
 *  150-174 s  reentrada: la ojiva baja envuelta en plasma; impacto (174 s)
 *  174-194 s  la explosión, la cámara se aleja
 *  194-210 s  el silo se cierra y regreso al jugador
 * </pre>
 */
final class CondorScenes {
    private CondorScenes() {}

    private static final Vec3 UP = new Vec3(0, 1, 0);

    static Vec3 missile(float s) {
        CondorPlan p = Cinematic.condor;
        double t = Mth.clamp(s * 20 - p.launchTick, 0, p.path.flightTicks);
        return p.path.atTick(t);
    }

    static Vec3 dir(float s) {
        CondorPlan p = Cinematic.condor;
        double t = Mth.clamp(s * 20 - p.launchTick, 0, p.path.flightTicks - 1);
        return p.path.direction(t);
    }

    static Cinematic.Pose pose(float s) {
        CondorPlan p = Cinematic.condor;
        Vec3 target = Cinematic.target, site = p.site, eye = Cinematic.eye;
        Vec3 f = Launcher.forward(p.yaw), r = Launcher.right(p.yaw);
        Vec3 toT = target.subtract(eye).multiply(1, 0, 1);
        Vec3 dirT = toT.lengthSqr() < 1 ? new Vec3(0, 0, 1) : toT.normalize();
        Vec3 side = dirT.cross(UP).normalize();
        double bottom = CondorPlan.bottom(site);
        Vec3 axis = new Vec3(site.x, 0, site.z);
        Vec3 across = target.subtract(site).multiply(1, 0, 1);
        across = across.lengthSqr() < 1 ? r : across.normalize().cross(UP);
        Vec3 diag = f.add(r).normalize();   // dentro del pozo, en diagonal (entre los brazos)

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
            Vec3 b = site.add(f.scale(-30)).add(r.scale(-20)).add(0, 14, 0);
            Vec3 pos = lerp(a, b, u).add(0, Math.sin(u * Math.PI) * 24, 0);
            return look(pos, lerp(target, site, smooth((s - 21) / 9)), (float) Math.sin(u * Math.PI) * 5, 70);
        }
        if (s < 44) {
            // el silo cerrado, en silencio: la cámara lo rodea despacio
            float u = (s - 32) / 12;
            double ang = Math.toRadians(200 + 60 * u);
            Vec3 pos = site.add(f.scale(Math.cos(ang) * 22)).add(r.scale(Math.sin(ang) * 22)).add(0, 5 - u, 0);
            return look(pos, site.add(0, 0.5, 0), 0, 55);
        }
        if (s < 48) {
            // primer plano de una baliza
            Vec3 lamp = site.add(f.scale(4.67)).add(r.scale(-4.67)).add(0, 1.9, 0);
            return look(lamp.add(f.scale(3.2)).add(r.scale(-2.2)).add(0, 0.6, 0), lamp, 0, 48);
        }
        if (s < 60) {
            // se abren las compuertas: desde arriba
            float u = (s - 48) / 12;
            Vec3 pos = site.add(f.scale(-10)).add(r.scale(6)).add(0, 15 - 4 * u, 0);
            return look(pos, site.add(0, -4 - 6 * u, 0), 0, 62);
        }
        if (s < 78) {
            // bajada por el pozo junto al misil
            float u = smooth((s - 60) / 18);
            double y = Mth.lerp(u, site.y - 0.5, bottom + 2.5);
            Vec3 pos = new Vec3(site.x, y, site.z).add(diag.scale(2.65));
            return look(pos, new Vec3(site.x, y - 1.2, site.z).add(diag.scale(0.2)), 0, 74);
        }
        if (s < 90) {
            // desde el fondo, mirando la punta y el cielo
            float u = (s - 78) / 12;
            Vec3 pos = new Vec3(site.x, bottom + 1.3, site.z).add(diag.scale(-2.65));
            return look(pos, new Vec3(site.x, site.y + 8, site.z).add(diag.scale(0.3 * u)), 0, 82);
        }
        if (s < 100) {
            // la cámara sube y sale del pozo
            float u = smooth((s - 90) / 10);
            Vec3 a = new Vec3(site.x, bottom + 3, site.z).add(diag.scale(-2.65));
            Vec3 b = site.add(f.scale(-14)).add(r.scale(-8)).add(0, 12, 0);
            Vec3 pos = u < 0.55 ? new Vec3(a.x, Mth.lerp(u / 0.55, a.y, site.y + 2), a.z) : lerp(new Vec3(a.x, site.y + 2, a.z), b, (u - 0.55) / 0.45);
            return look(pos, new Vec3(site.x, Mth.lerp(u, bottom + 26, site.y), site.z), 0, Mth.lerp(u, 80, 60));
        }
        if (s < 110) {
            float u = (s - 100) / 10;
            return look(site.add(f.scale(-42 + 4 * u)).add(r.scale(18)).add(0, 5, 0), site.add(0, 6, 0), 0, 52);
        }
        Vec3 m = missile(s);
        Vec3 d = dir(s);
        if (s < 114) {
            // ignición: desde el borde del pozo
            return look(site.add(r.scale(-9)).add(f.scale(-3)).add(0, 3, 0), lerp(site.add(0, 3, 0), m, smooth((s - 110) / 4)), 0, 70);
        }
        if (s < 120) return look(site.add(f.scale(-60)).add(r.scale(-25)).add(0, 2, 0), m, 0, 55);
        if (s < 128) {
            float u = (s - 120) / 8;
            return look(m.subtract(d.scale(45)).add(r.scale(14)), m.add(d.scale(30)), (float) Math.sin(u * 3) * 3, 70);
        }
        if (s < 136) {
            // se suelta la primera etapa (a los 130 s)
            return look(m.add(across.scale(35)).subtract(d.scale(10)).add(0, -5, 0), m.subtract(d.scale(6)), 0, 60);
        }
        if (s < 144) {
            // desde abajo: el arco de humo cruzando el cielo
            return look(site.add(f.scale(-30)).add(0, 20, 0), m, 0, 38);
        }
        if (s < 150) {
            // se suelta la ojiva (a los 144 s)
            return look(m.add(across.scale(20)).add(0, 6, 0), m, 0, 55);
        }
        if (s < 168) {
            float u = (s - 150) / 18;
            return look(m.subtract(d.scale(30)).add(across.scale(10)).add(0, 5, 0), m.add(d.scale(40)), (float) Math.sin(u * 5) * 2, 72);
        }
        if (s < 174) return look(target.add(f.scale(55)).add(r.scale(20)).add(0, 3, 0), lerp(m, target, 0.15), 0, 72);
        if (s < 194) {
            float u = (s - 174) / 20;
            return look(target.add(r.scale(95 - 20 * u)).add(f.scale(-30)).add(0, 40 - 10 * u, 0), target.add(0, 12, 0), 0, 64);
        }
        Vec3 back = site.add(r.scale(30)).add(f.scale(10)).add(0, 16, 0);
        if (s < 202) {
            float u = smooth((s - 194) / 8);
            Vec3 a = target.add(r.scale(75)).add(f.scale(-30)).add(0, 30, 0);
            Vec3 pos = lerp(a, back, u).add(0, Math.sin(u * Math.PI) * 24, 0);
            return look(pos, lerp(target, site, smooth((s - 195) / 5)), 0, 64);
        }
        float u = smooth(Mth.clamp((s - 202) / 8, 0, 1));
        Vec3 pos = lerp(back, eye, u).add(0, Math.sin(u * Math.PI) * 10, 0);
        return look(pos, lerp(site, eye.add(Launcher.forward(Cinematic.playerYaw).scale(10)), u), 0, Mth.lerp(u, 64, 70));
    }

    static void sounds(float s) {
        int half = Cinematic.nextHalf(s);
        if (half < 0) return;
        CondorPlan p = Cinematic.condor;
        float t = half / 2f;
        Vec3 site = p.site;
        double bottom = CondorPlan.bottom(site);
        if (half == 1) Cinematic.play(ModSounds.BEEP.get(), 0.9f, 0.6f);
        if (SiloRenderer.alarm(t) && half % 4 == 0) {
            Cinematic.playAt(ModSounds.ALARM.get(), site.add(0, 2, 0), 3f, 0.9f);
            Cinematic.playAt(ModSounds.ALARM.get(), new Vec3(site.x, bottom + 10, site.z), 2f, 0.9f);
        }
        if ((t >= 48 && t < 60 || t >= 194 && t < 202) && half % 6 == 0) Cinematic.playAt(ModSounds.HYDRAULIC.get(), site, 3f, 0.6f);
        if (t == 60 || t == 202) Cinematic.playAt(SoundEvents.IRON_DOOR_CLOSE, site, 3f, 0.4f);
        if (t >= 60 && t < 78 && half % 5 == 0) Cinematic.playAt(SoundEvents.BEACON_ACTIVATE, Cinematic.pose(0).pos(), 1.5f, 1.4f);
        if (t >= 80 && t < 88 && half % 3 == 0) Cinematic.playAt(SoundEvents.FIRE_EXTINGUISH, new Vec3(site.x, bottom + 8, site.z), 3f, 0.6f);
        if (t == 88) Cinematic.playAt(SoundEvents.ANVIL_LAND, new Vec3(site.x, bottom + 8, site.z), 2f, 0.6f);
        if (t == 95) Cinematic.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1f);
        if (t == 96) Cinematic.play(SoundEvents.UI_BUTTON_CLICK.get(), 0.5f, 1f);
        float launch = CondorPlan.LAUNCH_S;
        if (t >= launch - 10 && t < launch && half % 2 == 0) Cinematic.play(ModSounds.BEEP.get(), t >= launch - 3 ? 1.25f : 1f, 0.9f);
        if (t == launch) Cinematic.play(ModSounds.BEEP.get(), 1.6f, 1f);
        float impact = p.impactTick() / 20f;
        if (t == impact - 4) Cinematic.playAt(ModSounds.FAR_BOOM.get(), Cinematic.target.add(0, 60, 0), 6f, 1.6f);   // estampido sónico
    }

    static void particles(float s) {
        var level = Minecraft.getInstance().level;
        CondorPlan p = Cinematic.condor;
        if (level == null || p == null) return;
        var rnd = level.random;
        Vec3 site = p.site;
        double bottom = CondorPlan.bottom(site);
        float open = SiloRenderer.doors(s);
        // vapor que sale por la boca abierta
        if (open > 0.3f && s < 110) {
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, site.x + (rnd.nextDouble() - 0.5) * 5, site.y - 1,
                        site.z + (rnd.nextDouble() - 0.5) * 5, (rnd.nextDouble() - 0.5) * 0.05, 0.12 + rnd.nextDouble() * 0.1, (rnd.nextDouble() - 0.5) * 0.05);
            }
        }
        // los brazos se sueltan con chorros de vapor
        if (s >= 80 && s < 89) {
            for (float y : new float[]{-6f, 1f, 8f}) {
                Vec3 q = CondorPlan.start(site).add(0, y, 0);
                for (int i = 0; i < 4; i++) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, q.x + (rnd.nextDouble() - 0.5) * 3, q.y, q.z + (rnd.nextDouble() - 0.5) * 3,
                            (rnd.nextDouble() - 0.5) * 0.3, 0.08, (rnd.nextDouble() - 0.5) * 0.3);
                }
            }
        }
        // ignición: llamaradas y humo por los ductos y la boca
        float ig = s - CondorPlan.LAUNCH_S;
        if (ig >= 0 && ig < 9) {
            Vec3 r = Launcher.right(p.yaw);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 v = site.add(r.scale(side * -8.2)).add(0, 1.5, 0);
                Vec3 out = r.scale(side * -1);
                for (int i = 0; i < 8; i++) {
                    level.addAlwaysVisibleParticle(i < 5 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, true,
                            v.x + (rnd.nextDouble() - 0.5), v.y, v.z + (rnd.nextDouble() - 0.5),
                            out.x * 0.4 + (rnd.nextDouble() - 0.5) * 0.3, 0.45 + rnd.nextDouble() * 0.3, out.z * 0.4 + (rnd.nextDouble() - 0.5) * 0.3);
                }
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, v.x, v.y + 1, v.z, out.x * 0.2, 0.2, out.z * 0.2);
            }
            for (int i = 0; i < 6; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, site.x + (rnd.nextDouble() - 0.5) * 6, site.y,
                        site.z + (rnd.nextDouble() - 0.5) * 6, (rnd.nextDouble() - 0.5) * 0.6, 0.6 + rnd.nextDouble() * 0.5, (rnd.nextDouble() - 0.5) * 0.6);
            }
            if (ig < 1) {
                for (int i = 0; i < 20; i++) {
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, site.x, bottom + 1, site.z, 0, 0, 0);
                }
            }
        }
    }

    static String caption(float s) {
        float impact = Cinematic.condor.impactTick() / 20f;
        if (s < 8) return "locked";
        if (s < 20) return "recon";
        if (s < 32) return "condor.transit";
        if (s < 44) return "condor.silo";
        if (s < 48) return "condor.alarm";
        if (s < 60) return "condor.doors";
        if (s < 78) return "condor.descent";
        if (s < 90) return "condor.umbilical";
        if (s < 100) return "condor.keys";
        if (s < 110) return "countdown";
        if (s < 128) return "condor.liftoff";
        if (s < 136) return "condor.stage1";
        if (s < 144) return "condor.space";
        if (s < 150) return "condor.stage2";
        if (s < impact) return "condor.reentry";
        if (s < 194) return "done";
        if (s < 202) return "condor.close";
        return "complete";
    }
}
