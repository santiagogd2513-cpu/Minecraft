package com.arsenalatomico;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * El plan de la Lluvia de Acero: 24 misiles, uno cada cuarto de segundo, a puntos alrededor del
 * blanco. Sale igual en el servidor y en el cliente porque nace de la misma semilla.
 */
public final class SalvoPlan {
    public static final int COUNT = 24;
    /** Segundos de la cinemática en los que despega el primero y cada cuánto sale otro. */
    public static final float FIRST_LAUNCH_S = 24f;
    public static final int GAP_TICKS = 5;
    public static final int FLIGHT_TICKS = 150;

    public final MissilePath[] paths = new MissilePath[COUNT];
    public final int[] launchTick = new int[COUNT];
    public final Vec3 target;

    public SalvoPlan(Vec3 launcherBase, float yaw, Vec3 target, long seed) {
        this.target = target;
        RandomSource r = RandomSource.create(seed);
        Vec3 dir = Launcher.tubeDir(yaw, Launcher.RAISED);
        double dist = Math.sqrt(Math.pow(target.x - launcherBase.x, 2) + Math.pow(target.z - launcherBase.z, 2));
        double apex = 55 + dist * 0.35;
        for (int i = 0; i < COUNT; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * 16;
            Vec3 hit = target.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            Vec3 m = Launcher.muzzle(launcherBase, yaw, Launcher.RAISED, i % Launcher.TUBES);
            paths[i] = MissilePath.ballistic(m, dir, hit, apex + r.nextDouble() * 12, FLIGHT_TICKS + r.nextInt(16));
            launchTick[i] = Math.round(FIRST_LAUNCH_S * 20) + i * GAP_TICKS;
        }
    }

    /** Tick de la cinemática en que cae el último. */
    public int lastImpactTick() {
        int m = 0;
        for (int i = 0; i < COUNT; i++) m = Math.max(m, launchTick[i] + paths[i].flightTicks);
        return m;
    }
}
