package com.arsenalatomico.client;

import com.arsenalatomico.NukePlan;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.arsenalatomico.client.Cinematic.lerp;
import static com.arsenalatomico.client.Cinematic.look;
import static com.arsenalatomico.client.Cinematic.smooth;

/**
 * Las tomas del viaje en el camión del Protocolo Sol Negro.
 * <pre>
 *  salida (16 s):  0–4  desde el asiento del copiloto: el conductor (capucha, gafas… y dos puntitos blancos)
 *                  4–8  de frente, bajito: el camión viene hacia la cámara
 *                  8–12 de lado, siguiéndolo
 *                 12–16 la cámara sube por detrás y todo se funde a negro
 *  llegada (12 s): 0–7  «horas después…»: la Base Sol Negro desde el aire, el camión entra por el portón
 *                  7–12 junto a la caseta: el camión frena frente a la puerta
 * </pre>
 */
final class RideScenes {
    private RideScenes() {}

    private static Entity truck() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(Cinematic.rideTruck);
    }

    /** Un punto relativo al camión: a la derecha (side), adelante (ahead) y arriba (up). */
    private static Vec3 rel(Entity t, float s, double side, double ahead, double up) {
        float partial = (float) (s * 20 - Math.floor(s * 20));
        Vec3 c = t.getPosition(partial);
        float yaw = Mth.rotLerp(partial, t.yRotO, t.getYRot());
        Vec3 off = new Vec3(side, 0, ahead).yRot(-yaw * Mth.DEG_TO_RAD);
        return c.add(off.x, up, off.z);
    }

    /** Un punto de la Base Sol Negro (a hacia el silo, b a la derecha, alto desde la plataforma). */
    private static Vec3 base(double a, double b, double y) {
        Vec3 site = Cinematic.rideBase;
        return NukePlan.local(new Vec3(Math.floor(site.x) + 0.5, Math.floor(site.y), Math.floor(site.z) + 0.5), Cinematic.rideYaw, a, b, y);
    }

    static Cinematic.Pose pose(float s) {
        Entity t = truck();
        if (t == null) {
            Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return look(p, p.add(0, 0, 1), 0, 70);
        }
        Vec3 center = rel(t, s, 0, 0, 1.6);
        if (Cinematic.ridePhase == 0) {
            if (s < 4) {
                // el copiloto voltea a mirar al conductor… que no voltea
                Vec3 eye = rel(t, s, -0.62, 1.75, 2.85);
                Vec3 face = rel(t, s, 0.62, 1.8 + 0.3 * smooth(s / 4), 2.8);
                Vec3 road = rel(t, s, 0, 12, 2.4);
                return look(eye, lerp(face, road, smooth((s - 2.6f) / 1.4f)), 0, 70);
            }
            if (s < 8) {
                float u = (s - 4) / 4;
                Vec3 cam = rel(t, s, 1.5, 11 - 2 * u, 1.0);
                return look(cam, center, 0, 62);
            }
            if (s < 12.5f) {
                float u = (s - 8) / 4.5f;
                Vec3 cam = rel(t, s, 8.5, Mth.lerp(u, 4, -2), 2.4);
                return look(cam, center, 0, 58);
            }
            float u = smooth((s - 12.5f) / 3.5f);
            Vec3 cam = rel(t, s, 2, Mth.lerp(u, -10, -18), Mth.lerp(u, 3, 15));
            return look(cam, center, 0, 60);
        }
        // llegada a la base: primero desde el aire (toda la base), luego junto al edificio de acceso
        if (s < 7) {
            float u = smooth(s / 7);
            Vec3 cam = lerp(base(-100, 50, 46), base(-80, 34, 28), u);
            return look(cam, lerp(base(-10, 0, 0), center, Math.min(1, u * 1.4)), 0, 58);
        }
        Vec3 cam = base(-44, 4, 2.2);
        return look(cam, center, 0, 64);
    }

    /** Negro encima de todo (0 nada, 1 todo negro). */
    static float dark(float s) {
        if (Cinematic.ridePhase == 0) return Mth.clamp((s - 13.5f) / 2f, 0, 1);
        return 1 - Mth.clamp(s / 1.6f, 0, 1);
    }

    static String caption(float s) {
        if (Cinematic.ridePhase == 0) {
            if (s < 4) return "ride.driver";
            if (s < 12.5f) return "ride.road";
            return "ride.leaving";
        }
        return s < 4 ? "ride.later" : "ride.base";
    }

    static void sounds(float s) {
        int half = Cinematic.nextHalf(s);
        if (half < 0) return;
        Entity t = truck();
        if (t == null) return;
        Vec3 at = t.position().add(0, 1.5, 0);
        if (Cinematic.ridePhase == 0) {
            if (half == 0) Cinematic.playAt(SoundEvents.IRON_DOOR_CLOSE, at, 1.2f, 0.7f);
            // el motor acelera
            if (half == 2) Cinematic.playAt(SoundEvents.RAVAGER_STEP, at, 1.5f, 0.5f);
        } else {
            if (half == 21) Cinematic.playAt(SoundEvents.PISTON_CONTRACT, at, 1.5f, 0.5f);
            if (half == 23) Cinematic.playAt(SoundEvents.IRON_DOOR_OPEN, at, 1.2f, 0.7f);
        }
    }
}
