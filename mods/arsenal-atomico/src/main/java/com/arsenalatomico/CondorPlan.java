package com.arsenalatomico;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * El plan del Cóndor: un misil intercontinental de tres partes que sale de un silo. Primera etapa,
 * segunda etapa y la ojiva, que vuelve a entrar a la atmósfera brillando. Sale igual en el
 * servidor y en el cliente.
 * <p>
 * «site» es el centro del silo a la altura del borde (la boca del pozo).
 */
public final class CondorPlan {
    public static final float LAUNCH_S = 110f;
    public static final int FLIGHT_TICKS = 1280;      // 64 segundos de vuelo
    /** Ticks después del despegue en que se suelta la primera etapa y luego la ojiva. */
    public static final int SEP1 = 400, SEP2 = 680;
    /** Tamaño del misil: 26,6 bloques de largo. */
    public static final float SCALE = 1f;
    public static final double HALF_LEN = 13.3;
    /** El pozo: radio de adentro, radio de la pared y hondo. */
    public static final double SHAFT_R = 3.5, WALL_R = 5.2, DEPTH = 34;

    public final Vec3 site, target;
    public final float yaw;
    public final MissilePath path;
    public final int launchTick;

    public CondorPlan(Vec3 site, float yaw, Vec3 target, long seed) {
        this.site = site;
        this.yaw = yaw;
        this.target = target;
        RandomSource r = RandomSource.create(seed ^ 0xC0D0L);
        Vec3 start = start(site);
        Vec3 hit = target.add(r.nextDouble() * 3 - 1.5, 0, r.nextDouble() * 3 - 1.5);
        Vec3 back = start.subtract(hit).multiply(1, 0, 1);
        Vec3 p1 = start.add(0, 430, 0);
        Vec3 p2 = hit.add(back.scale(0.25)).add(0, 520, 0);
        path = new MissilePath(start, p1, p2, hit, FLIGHT_TICKS);
        launchTick = Math.round(LAUNCH_S * 20);
    }

    /** Fondo del pozo. */
    public static double bottom(Vec3 site) {
        return site.y - DEPTH;
    }

    /** Centro del misil parado en el fondo del silo. */
    public static Vec3 start(Vec3 site) {
        return new Vec3(site.x, bottom(site) + 1 + HALF_LEN, site.z);
    }

    public int impactTick() {
        return launchTick + path.flightTicks;
    }
}
