package com.arsenalatomico;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Un lanzamiento desde la Base Sol Negro: el Sol Negro desde el silo principal, o un misil nuclear
 * chiquito o mediano desde su silo. Sale igual en el servidor y en el cliente.
 */
public final class BasePlan {
    /** Segundos hasta el despegue: el Sol Negro tiene su ceremonia; los demás, su cuenta regresiva. */
    public static final float MAIN_LAUNCH_S = 25, SILO_LAUNCH_S = 13;

    public final Vec3 site, target;
    public final float yaw;
    public final int silo;
    public final MissilePath path;
    public final int launchTick, impactTick, duration;

    public BasePlan(Vec3 site, float yaw, Vec3 target, long seed, int silo) {
        this.site = site;
        this.yaw = NukePlan.snap(yaw);
        this.target = target;
        this.silo = Math.max(0, Math.min(BaseLayout.COUNT - 1, silo));
        RandomSource r = RandomSource.create(seed ^ 0x50AL);
        Vec3 start = start();
        Vec3 hit = target.add(r.nextDouble() * 2 - 1, 0, r.nextDouble() * 2 - 1);
        double dist = Math.hypot(hit.x - start.x, hit.z - start.z);
        boolean main = this.silo == 0;
        int flight = main ? 800 : (int) Math.max(380, Math.min(760, 300 + dist * 0.45));
        Vec3 back = start.subtract(hit).multiply(1, 0, 1);
        Vec3 p1 = start.add(0, main ? 360 : 230, 0);
        Vec3 p2 = hit.add(back.scale(0.22)).add(0, main ? 420 : 280, 0);
        path = new MissilePath(start, p1, p2, hit, flight);
        launchTick = Math.round((main ? MAIN_LAUNCH_S : SILO_LAUNCH_S) * 20);
        impactTick = launchTick + flight;
        duration = impactTick + (main ? 46 : 32) * 20;
    }

    /** El centro de la boca del silo (a la altura de la plataforma). */
    public Vec3 siloTop() {
        double[] s = BaseLayout.SILOS[silo];
        return BaseLayout.local(site, yaw, s[0], s[1], 0);
    }

    /** El centro del misil parado en el fondo del silo. */
    public Vec3 start() {
        double[] s = BaseLayout.SILOS[silo];
        return siloTop().add(0, -s[3] + 1 + BaseLayout.missileLength(silo) / 2, 0);
    }

    public float launchSecond() {
        return launchTick / 20f;
    }

    public float impactSecond() {
        return impactTick / 20f;
    }

    /** Del sistema de la base al mundo. */
    public Vec3 L(double a, double b, double y) {
        return BaseLayout.local(site, yaw, a, b, y);
    }
}
