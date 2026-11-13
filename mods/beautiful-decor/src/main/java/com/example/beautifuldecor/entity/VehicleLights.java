package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.VehicleLightBlock;
import com.example.beautifuldecor.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * V6.4.0: luz real de los vehículos. Cada vehículo con las luces encendidas va poniendo unos pocos bloques de luz invisibles
 * ({@link VehicleLightBlock}) y los mueve con él. Los coches iluminan un cono delante; el helicóptero y el avión, mucho más:
 * donde están, por delante y el suelo que tienen debajo.
 * Solo se usa en el servidor.
 */
public final class VehicleLights {
    private static final Map<Entity, List<BlockPos>> OWN = new WeakHashMap<>();
    private static final Map<Level, Set<Long>> ACTIVE = new WeakHashMap<>();

    private VehicleLights() {
    }

    public static boolean isActive(Level level, BlockPos pos) {
        Set<Long> set = ACTIVE.get(level);
        return set != null && set.contains(pos.asLong());
    }

    /** Se llama cada tick desde el servidor; actualiza cada 3 ticks. {@code aircraft}: luz fuerte de avión y helicóptero. */
    public static void update(Entity e, boolean on, boolean aircraft) {
        Level level = e.level();
        if (level.isClientSide) {
            return;
        }
        if (!on) {
            clear(e);
            return;
        }
        if (e.tickCount % 3 != 0) {
            return;
        }
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        List<BlockPos> want = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        if (aircraft) {
            add(want, levels, BlockPos.containing(e.getX(), e.getY() + 1.0, e.getZ()), 15);
            add(want, levels, BlockPos.containing(e.getX() + fx * 10.0, e.getY() + 1.0, e.getZ() + fz * 10.0), 15);
            add(want, levels, BlockPos.containing(e.getX() + fx * 22.0, e.getY() + 1.0, e.getZ() + fz * 22.0), 15);
            BlockPos ground = groundBelow(level, e.blockPosition(), 48);
            if (ground != null) {
                add(want, levels, ground.above(), 15);
            }
        } else {
            double[][] cone = {{3.5, 15}, {8.0, 13}, {13.0, 11}};
            for (double[] c : cone) {
                add(want, levels, BlockPos.containing(e.getX() + fx * c[0], e.getY() + 1.0, e.getZ() + fz * c[0]), (int) c[1]);
            }
        }
        List<BlockPos> placed = new ArrayList<>();
        Set<Long> active = ACTIVE.computeIfAbsent(level, k -> new HashSet<>());
        for (int i = 0; i < want.size(); i++) {
            BlockPos p = place(level, want.get(i), levels.get(i));
            if (p == null) {
                p = place(level, want.get(i).above(), levels.get(i));
            }
            if (p != null) {
                placed.add(p);
                active.add(p.asLong());
            }
        }
        List<BlockPos> old = OWN.put(e, placed);
        if (old != null) {
            for (BlockPos p : old) {
                if (!placed.contains(p)) {
                    release(level, p);
                }
            }
        }
    }

    /** Quita las luces de este vehículo (se apagó, se bajó o desapareció). */
    public static void clear(Entity e) {
        List<BlockPos> old = OWN.remove(e);
        if (old != null) {
            Level level = e.level();
            for (BlockPos p : old) {
                release(level, p);
            }
        }
    }

    private static void add(List<BlockPos> want, List<Integer> levels, BlockPos pos, int level) {
        want.add(pos);
        levels.add(level);
    }

    private static BlockPos place(Level level, BlockPos pos, int lvl) {
        if (!level.isInWorldBounds(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        BlockState light = ModBlocks.VEHICLE_LIGHT.get().defaultBlockState().setValue(VehicleLightBlock.LEVEL, lvl);
        if (state.is(ModBlocks.VEHICLE_LIGHT.get())) {
            if (state.getValue(VehicleLightBlock.LEVEL) != lvl) {
                level.setBlock(pos, light, 2);
            }
            return pos;
        }
        if (state.isAir()) {
            level.setBlock(pos, light, 2);
            return pos;
        }
        return null;
    }

    private static void release(Level level, BlockPos pos) {
        Set<Long> active = ACTIVE.get(level);
        if (active != null) {
            active.remove(pos.asLong());
        }
        if (level.getBlockState(pos).is(ModBlocks.VEHICLE_LIGHT.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static BlockPos groundBelow(Level level, BlockPos from, int max) {
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i < max; i++) {
            p.move(0, -1, 0);
            if (!level.isInWorldBounds(p)) {
                return null;
            }
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(ModBlocks.VEHICLE_LIGHT.get())) {
                return p.immutable();
            }
        }
        return null;
    }
}
