package com.arsenalatomico.strike;

import com.arsenalatomico.Launcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Dónde poner las lanzadoras: sobre suelo firme (nada de huecos, vacío, agua ni lava) o en el mar. */
public final class Sites {
    private Sites() {}

    /** Altura del suelo firme en esa columna, o null si ahí no se puede parar nada. */
    @Nullable
    static Integer ground(ServerLevel level, int x, int z) {
        BlockPos col = new BlockPos(x, 0, z);
        if (!level.hasChunkAt(col)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1) return null;      // hueco hasta el fondo del mundo
        BlockPos below = new BlockPos(x, y - 1, z);
        BlockState s = level.getBlockState(below);
        if (!s.getFluidState().isEmpty()) return null;            // agua o lava
        if (!s.isFaceSturdy(level, below, Direction.UP)) return null;
        return y;
    }

    /** ¿Cabe un vehículo de ese tamaño (mitad del largo y del ancho) sin quedar colgando? */
    static boolean flat(ServerLevel level, Vec3 c, float yaw, double halfLen, double halfWid) {
        Vec3 f = Launcher.forward(yaw), r = Launcher.right(yaw);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Vec3 p = c.add(f.scale(i * halfLen)).add(r.scale(j * halfWid));
                Integer y = ground(level, (int) Math.floor(p.x), (int) Math.floor(p.z));
                if (y == null || Math.abs(y - c.y) > 2) return false;
            }
        }
        return true;
    }

    /**
     * Un lugar en tierra firme a cierta distancia del jugador (para que su juego lo tenga dibujado),
     * lejos del blanco y mirando hacia él.
     */
    public static Vec3 land(ServerLevel level, Vec3 player, Vec3 target, RandomSource r,
                            double minD, double maxD, double halfLen, double halfWid) {
        Vec3 loose = null;
        for (int tries = 0; tries < 60; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = minD + r.nextDouble() * (maxD - minD);
            int x = (int) Math.floor(player.x + Math.cos(a) * d), z = (int) Math.floor(player.z + Math.sin(a) * d);
            Integer y = ground(level, x, z);
            if (y == null) continue;
            Vec3 c = new Vec3(x + 0.5, y, z + 0.5);
            if (horizontal(c, target) < 50) continue;
            if (flat(level, c, yawTowards(c, target), halfLen, halfWid)) return c;
            if (loose == null) loose = c;
        }
        if (loose != null) return loose;
        // nada sirve (todo hueco o agua): al lado del jugador, que está parado en algo
        Integer y = ground(level, (int) Math.floor(player.x), (int) Math.floor(player.z));
        return new Vec3(player.x, y != null ? y : player.y, player.z);
    }

    /**
     * Mar abierto para el submarino: agua de al menos 14 bloques de hondo en el centro y de 8 a lo
     * largo del casco. Null si no hay mar cerca.
     */
    @Nullable
    public static Vec3 sea(ServerLevel level, Vec3 player, Vec3 target, RandomSource r) {
        for (int tries = 0; tries < 90; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 45 + r.nextDouble() * 105;
            int x = (int) Math.floor(player.x + Math.cos(a) * d), z = (int) Math.floor(player.z + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Vec3 c = new Vec3(x + 0.5, top, z + 0.5);
            if (horizontal(c, target) < 60) continue;
            if (depth(level, x, top, z) < 14) continue;
            float yaw = yawTowards(c, target);
            Vec3 f = com.arsenalatomico.Launcher.forward(yaw), rr = com.arsenalatomico.Launcher.right(yaw);
            boolean ok = true;
            for (int i = -2; i <= 2 && ok; i++) {
                for (int j = -1; j <= 1 && ok; j++) {
                    Vec3 p = c.add(f.scale(i * 13)).add(rr.scale(j * 5));
                    int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
                    if (!level.hasChunkAt(new BlockPos(px, 0, pz))) ok = false;
                    else if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz) != top || depth(level, px, top, pz) < 8) ok = false;
                }
            }
            if (ok) return c;
        }
        return null;
    }

    /** Cuántos bloques de agua hay hacia abajo desde la superficie. */
    private static int depth(ServerLevel level, int x, int top, int z) {
        int n = 0;
        for (int y = top - 1; y > level.getMinBuildHeight() && n < 40; y--) {
            if (!level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) break;
            n++;
        }
        return n;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static float yawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
