package com.arsenalatomico.strike;

import com.arsenalatomico.CondorPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye el silo del Cóndor en el mundo, de a poco (unos miles de bloques por tick): un pozo de
 * concreto con anillos de luz, piso negro, borde con franjas de peligro y, si el mundo no deja cavar
 * tan hondo (superplano), una loma de tierra alrededor que lo esconde como un búnker. Al final del
 * ataque cierra la boca con compuertas de hierro. El silo se queda en el mundo.
 */
public final class SiloBuilder {
    private final ServerLevel level;
    private final List<Object[]> todo = new ArrayList<>();
    private int next;

    private SiloBuilder(ServerLevel level) {
        this.level = level;
    }

    /** Dónde va el borde del silo: en el suelo, o más arriba si no cabe hacia abajo. */
    public static double rimHeight(ServerLevel level, double ground) {
        return Math.max(ground, level.getMinBuildHeight() + 3 + CondorPlan.DEPTH);
    }

    public static SiloBuilder build(ServerLevel level, Vec3 site, int groundY) {
        SiloBuilder b = new SiloBuilder(level);
        int cx = Mth.floor(site.x), cz = Mth.floor(site.z);
        int top = Mth.floor(site.y);                 // primer bloque de aire sobre el borde
        int bottom = top - (int) CondorPlan.DEPTH;   // piso (aire desde aquí)
        double sr = CondorPlan.SHAFT_R, wr = CondorPlan.WALL_R;
        int R = 8 + Math.max(0, top - groundY);      // con loma, más ancho (pendiente de 45°)
        BlockState wall = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), dark = Blocks.GRAY_CONCRETE.defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R) continue;
                int x = cx + dx, z = cz + dz;
                double ang = Math.atan2(dz, dx);
                if (d <= sr) {
                    // el pozo: aire, y el piso con la zanja de las llamas
                    b.add(x, bottom - 1, z, d < 1.6 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                    b.add(x, bottom - 2, z, Blocks.BLACK_CONCRETE.defaultBlockState());
                    for (int y = bottom; y < top; y++) b.add(x, y, z, Blocks.AIR.defaultBlockState());
                } else if (d <= wr) {
                    // pared de concreto con anillos de luz cada 6 bloques y franjas
                    for (int y = bottom - 2; y < top; y++) {
                        int k = y - bottom;
                        BlockState s = wall;
                        if (k % 6 == 3 && d <= sr + 1.1 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) s = Blocks.SEA_LANTERN.defaultBlockState();
                        else if (k % 6 == 0) s = dark;
                        else if (y >= top - 2) s = Blocks.RED_CONCRETE.defaultBlockState();
                        b.add(x, y, z, s);
                    }
                    // franjas amarillas y negras en el borde
                    int seg = (int) Math.floor((ang + Math.PI) / (Math.PI / 12));
                    b.add(x, top - 1, z, seg % 2 == 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState());
                } else {
                    // alrededor: plataforma de piedra lisa y, si hay loma, tierra con pasto
                    int h = top - 1 - (int) Math.max(0, d - 7.5);   // altura de la loma en esta columna
                    if (h < groundY - 1) continue;
                    for (int y = groundY; y < h; y++) b.add(x, y, z, d <= 7.5 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    b.add(x, h, z, d <= 7.5 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = h + 1; y < h + 4; y++) b.add(x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        return b;
    }

    /** Compuertas de hierro sobre la boca del pozo (al final del ataque). */
    public static void close(ServerLevel level, Vec3 site) {
        int cx = Mth.floor(site.x), cz = Mth.floor(site.z), top = Mth.floor(site.y);
        BlockState door = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.FACING, Direction.NORTH);
        int r = Mth.ceil(CondorPlan.SHAFT_R);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > CondorPlan.SHAFT_R * CondorPlan.SHAFT_R) continue;
                BlockPos p = new BlockPos(cx + dx, top - 1, cz + dz);
                if (level.getBlockState(p).isAir()) level.setBlock(p, door, 2);
            }
        }
    }

    private void add(int x, int y, int z, BlockState s) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        todo.add(new Object[]{new BlockPos(x, y, z), s});
    }

    /** Pone otro pedazo; true cuando terminó. */
    public boolean step(int perTick) {
        int end = Math.min(todo.size(), next + perTick);
        for (; next < end; next++) {
            Object[] o = todo.get(next);
            BlockPos p = (BlockPos) o[0];
            BlockState s = (BlockState) o[1];
            BlockState old = level.getBlockState(p);
            if (old.is(Blocks.BEDROCK) || old.equals(s)) continue;   // la roca madre no se toca
            level.setBlock(p, s, 2);
        }
        return next >= todo.size();
    }

    public boolean done() {
        return next >= todo.size();
    }
}
