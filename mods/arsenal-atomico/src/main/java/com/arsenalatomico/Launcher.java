package com.arsenalatomico;

import net.minecraft.world.phys.Vec3;

/**
 * El camión lanzacohetes de la Lluvia de Acero: dónde está cada tubo según el ángulo de la
 * batería. Lo usan el dibujo (cliente) y la salida de los misiles (servidor).
 */
public final class Launcher {
    public static final int TUBES = 12;          // 3 filas × 4 columnas
    public static final float RAISED = 52f;      // grados de la batería al disparar
    /** Pivote de la batería sobre el camión (hacia atrás y arriba). */
    public static final double PIVOT_BACK = 2.3, PIVOT_UP = 2.0;
    public static final double RACK_LEN = 4.6;

    private Launcher() {}

    /** Hacia dónde mira el camión (yaw en grados, como Minecraft: 0 = sur). */
    public static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    public static Vec3 right(float yaw) {
        Vec3 f = forward(yaw);
        return new Vec3(-f.z, 0, f.x);
    }

    /** Dirección de los tubos con la batería levantada «deg» grados (apuntan hacia adelante). */
    public static Vec3 tubeDir(float yaw, float deg) {
        double e = Math.toRadians(deg);
        return forward(yaw).scale(Math.cos(e)).add(0, Math.sin(e), 0).normalize();
    }

    /** Boca del tubo i. «base» es el centro del camión al nivel del suelo. */
    public static Vec3 muzzle(Vec3 base, float yaw, float deg, int i) {
        Vec3 f = forward(yaw), r = right(yaw), dir = tubeDir(yaw, deg);
        Vec3 pivot = base.add(f.scale(-PIVOT_BACK)).add(0, PIVOT_UP, 0);
        int col = i % 4, row = (i / 4) % 3;
        // «arriba» de la batería (perpendicular a los tubos)
        Vec3 up = r.cross(dir).normalize().scale(-1);
        if (up.y < 0) up = up.scale(-1);
        return pivot.add(dir.scale(RACK_LEN)).add(r.scale((col - 1.5) * 0.55)).add(up.scale(0.3 + row * 0.5));
    }
}
