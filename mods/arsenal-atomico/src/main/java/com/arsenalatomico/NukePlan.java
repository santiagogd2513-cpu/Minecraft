package com.arsenalatomico;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * El plan del Protocolo Hongo: una bomba nuclear pequeña en un misil de una sola etapa que sale de un
 * búnker enorme. Sale igual en el servidor y en el cliente.
 * <p>
 * El búnker se arma alrededor de «site» (el centro del silo, a la altura del borde) en un sistema
 * propio: «a» hacia el blanco (adelante) y «b» hacia la derecha, siempre en múltiplos de 90° para
 * que los bloques queden derechos.
 * <pre>
 *   adelante (a+)
 *        ↑        ventila           ventila
 *        |             [ventila]  [ventila]       ← escape del fuego en a = 9
 *        |                ( silo )              ← boca del silo en a = 0
 *        |                [cuarto de control]   ← a de -7 a -14, con ventana al misil
 *        |                     |  pasillo |     ← a de -15 a -21
 *        |                   [ escalera ]       ← caracol bajo la caseta, centro en a = -25
 *        |                   [  CASETA  ]       ← en la superficie
 * </pre>
 */
public final class NukePlan {
    public static final float LAUNCH_S = 110f;
    public static final int FLIGHT_TICKS = 700;            // 35 segundos de vuelo
    /** Medio largo del misil (18 bloques en total). */
    public static final double HALF_LEN = 9;
    /** El pozo: radio de adentro, de la pared y hondo. */
    public static final double SHAFT_R = 3.5, WALL_R = 5.5;
    public static final int DEPTH = 32;
    /** Piso del cuarto de control, medido desde el fondo del pozo. */
    public static final int ROOM_UP = 12;
    /** Centro de la escalera de caracol (en «a»), radio de la plataforma de concreto y de la cerca. */
    public static final int STAIR_A = -25, PAD_R = 38, FENCE_R = 35;
    /** Las dos ventilas de escape: adelante del silo, a los lados (centro en a = 9, b = ±4). */
    public static final int VENT_A = 9, VENT_B = 4;
    /** El cráter: radio, hondo en el centro y hasta dónde llega lo quemado. */
    public static final int CRATER_R = 46, CRATER_DEPTH = 22, SCORCH_R = 104;

    public final Vec3 site, target;
    /** Giro del búnker (múltiplo de 90°, mirando lo más cerca posible al blanco). */
    public final float yaw;
    public final MissilePath path;
    public final int launchTick;

    public NukePlan(Vec3 site, float yaw, Vec3 target, long seed) {
        this.site = site;
        this.yaw = snap(yaw);
        this.target = target;
        RandomSource r = RandomSource.create(seed ^ 0x4B4EL);
        Vec3 start = start(site);
        Vec3 hit = target.add(r.nextDouble() * 2 - 1, 0, r.nextDouble() * 2 - 1);
        Vec3 back = start.subtract(hit).multiply(1, 0, 1);
        Vec3 p1 = start.add(0, 340, 0);
        Vec3 p2 = hit.add(back.scale(0.22)).add(0, 400, 0);
        path = new MissilePath(start, p1, p2, hit, FLIGHT_TICKS);
        launchTick = Math.round(LAUNCH_S * 20);
    }

    /** El giro redondeado a 0, 90, 180 o 270 grados. */
    public static float snap(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        return (Math.round(y / 90f) % 4) * 90f;
    }

    /** Hacia adelante y hacia la derecha del búnker, en enteros (-1, 0 o 1). */
    public static int[] axes(float yaw) {
        Vec3 f = Launcher.forward(snap(yaw)), r = Launcher.right(snap(yaw));
        return new int[]{(int) Math.round(f.x), (int) Math.round(f.z), (int) Math.round(r.x), (int) Math.round(r.z)};
    }

    /** Del sistema del búnker (a, b, alto) al mundo. */
    public static Vec3 local(Vec3 site, float yaw, double a, double b, double y) {
        int[] ax = axes(yaw);
        return new Vec3(site.x + a * ax[0] + b * ax[2], site.y + y, site.z + a * ax[1] + b * ax[3]);
    }

    public static double bottom(Vec3 site) {
        return Math.floor(site.y) - DEPTH;
    }

    /** Altura del piso del cuarto de control. */
    public static double roomFloor(Vec3 site) {
        return bottom(site) + ROOM_UP;
    }

    /** Centro del misil parado en el fondo del pozo. */
    public static Vec3 start(Vec3 site) {
        return new Vec3(site.x, bottom(site) + 1 + HALF_LEN, site.z);
    }

    public int impactTick() {
        return launchTick + path.flightTicks;
    }

    public float impactSecond() {
        return impactTick() / 20f;
    }
}
