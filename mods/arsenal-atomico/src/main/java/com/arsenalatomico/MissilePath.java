package com.arsenalatomico;

import net.minecraft.world.phys.Vec3;

/**
 * Trayectoria de un misil: curva de Bézier cúbica que sale por el tubo, sube hasta la cima y cae
 * casi en picada sobre el blanco. El servidor y el cliente la calculan igual, así la cámara de la
 * cinemática sabe dónde va cada misil sin preguntarle a nadie.
 */
public final class MissilePath {
    public final Vec3 p0, p1, p2, p3;
    public final int flightTicks;

    public MissilePath(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int flightTicks) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.flightTicks = flightTicks;
    }

    /** Sale en la dirección del tubo, sube «apex» bloques y cae desde arriba sobre el blanco. */
    public static MissilePath ballistic(Vec3 muzzle, Vec3 tubeDir, Vec3 target, double apex, int flightTicks) {
        Vec3 p1 = muzzle.add(tubeDir.normalize().scale(apex * 0.95));
        Vec3 p2 = new Vec3(target.x, Math.max(target.y, muzzle.y) + apex * 0.85, target.z);
        return new MissilePath(muzzle, p1, p2, target, flightTicks);
    }

    /** Del tiempo (0..1) al avance en la curva: arranca lento y acelera, como un cohete. */
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return 0.22 * t + 0.78 * t * t;
    }

    public Vec3 at(double s) {
        double u = 1 - s;
        double a = u * u * u, b = 3 * u * u * s, c = 3 * u * s * s, d = s * s * s;
        return new Vec3(
                a * p0.x + b * p1.x + c * p2.x + d * p3.x,
                a * p0.y + b * p1.y + c * p2.y + d * p3.y,
                a * p0.z + b * p1.z + c * p2.z + d * p3.z);
    }

    /** Posición a «ticks» de haber despegado (con fracción para la cámara). */
    public Vec3 atTick(double ticks) {
        return at(ease(ticks / flightTicks));
    }

    /** Dirección en la que apunta la punta. */
    public Vec3 direction(double ticks) {
        Vec3 a = atTick(ticks), b = atTick(ticks + 0.5);
        Vec3 d = b.subtract(a);
        return d.lengthSqr() < 1e-8 ? p1.subtract(p0).normalize() : d.normalize();
    }
}
