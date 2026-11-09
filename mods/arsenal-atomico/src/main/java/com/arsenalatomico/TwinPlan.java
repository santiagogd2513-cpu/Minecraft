package com.arsenalatomico;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * El plan del Cóndor Doble: dos misiles intercontinentales. Desde un submarino si hay mar cerca,
 * o desde dos lanzadoras móviles de 8 ejes si no. Sale igual en el servidor y en el cliente.
 */
public final class TwinPlan {
    public static final int LAND = 0, SEA = 1;
    /** Segundos de la cinemática en que despega cada misil. */
    public static final float[] LAUNCH_S = {110f, 116f};
    public static final int FLIGHT_TICKS = 880;
    /** Ticks hasta que se enciende el motor (antes los empuja el gas o el vapor, sin llama). */
    public static final int IGNITE_LAND = 14, IGNITE_SEA = 36;
    /** Tamaño del misil: los del submarino son más cortos. */
    public static final float SCALE_LAND = 1f, SCALE_SEA = 0.75f;

    // lanzadora: pivote del brazo atrás del camión, contenedor de 20 bloques
    public static final double TRUCK_SPACING = 7, PIVOT_BACK = 8.5, PIVOT_UP = 3.2, CAN_OFF = 1.15, CAN_LEN = 20;
    // submarino: el eje del casco va 2.3 bajo el agua; la cubierta de los tubos, 1.5 sobre el agua
    public static final double HULL_DOWN = 2.3, DECK_UP = 1.5;
    public static final double[][] HATCH = {{1.0, -5.5}, {-1.0, -8.5}};   // {x, z} en el casco

    public final int mode;
    public final Vec3 site, target;
    public final float yaw;
    public final MissilePath[] paths = new MissilePath[2];
    public final int[] launchTick = new int[2];

    public TwinPlan(int mode, Vec3 site, float yaw, Vec3 target, long seed) {
        this.mode = mode;
        this.site = site;
        this.yaw = yaw;
        this.target = target;
        RandomSource r = RandomSource.create(seed ^ 0x5EA5EAL);
        Vec3 right = Launcher.right(yaw);
        for (int i = 0; i < 2; i++) {
            Vec3 start = start(mode, site, yaw, i);
            Vec3 hit = target.add(right.scale(i == 0 ? -5 : 5)).add(r.nextDouble() * 4 - 2, 0, r.nextDouble() * 4 - 2);
            Vec3 back = start.subtract(hit).multiply(1, 0, 1);
            Vec3 p1 = start.add(0, 170, 0);
            Vec3 p2 = hit.add(back.scale(0.3)).add(0, 250, 0);
            paths[i] = new MissilePath(start, p1, p2, hit, FLIGHT_TICKS + r.nextInt(30));
            launchTick[i] = Math.round(LAUNCH_S[i] * 20);
        }
    }

    public float scale() {
        return mode == SEA ? SCALE_SEA : SCALE_LAND;
    }

    public int ignite() {
        return mode == SEA ? IGNITE_SEA : IGNITE_LAND;
    }

    /** Centro de cada lanzadora (en tierra). */
    public static Vec3 truck(Vec3 site, float yaw, int i) {
        return site.add(Launcher.right(yaw).scale(i == 0 ? -TRUCK_SPACING : TRUCK_SPACING));
    }

    /** Pivote del brazo que levanta el misil. */
    public static Vec3 pivot(Vec3 site, float yaw, int i) {
        return truck(site, yaw, i).add(Launcher.forward(yaw).scale(-PIVOT_BACK)).add(0, PIVOT_UP, 0);
    }

    /** Compuerta del tubo i del submarino (ya en la superficie). */
    public static Vec3 hatch(Vec3 site, float yaw, int i) {
        Vec3 f = Launcher.forward(yaw), r = Launcher.right(yaw);
        // en el dibujo, +x del casco queda a la izquierda
        return site.add(r.scale(-HATCH[i][0])).add(f.scale(HATCH[i][1])).add(0, DECK_UP, 0);
    }

    /** Centro del misil i antes de salir: en el contenedor vertical o dentro del submarino. */
    public static Vec3 start(int mode, Vec3 site, float yaw, int i) {
        if (mode == SEA) return hatch(site, yaw, i).add(0, -9.5 * SCALE_SEA - 0.2, 0);
        return pivot(site, yaw, i).add(Launcher.forward(yaw).scale(-CAN_OFF)).add(0, CAN_LEN / 2, 0);
    }

    public int lastImpactTick() {
        int m = 0;
        for (int i = 0; i < 2; i++) m = Math.max(m, launchTick[i] + paths[i].flightTicks);
        return m;
    }
}
