package net.minecraft.server.level;

import net.minecraft.world.level.Level;

/** Pieza de mentira de ServerPlayer. */
public class ServerPlayer {
    public final Level world;
    public double nearX;
    public double nearY;
    public double nearZ;

    public ServerPlayer(Level world) {
        this.world = world;
    }

    public Level level() {
        return this.world;
    }

    public double distanceToSqr(double x, double y, double z) {
        double dx = x - this.nearX;
        double dy = y - this.nearY;
        double dz = z - this.nearZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
