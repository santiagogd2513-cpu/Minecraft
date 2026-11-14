package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V7.8.0: red de cruces. Cada detector de trenes cargado se anota aquí; el mecanismo y el semáforo del cruce preguntan si hay un detector
 * encendido cerca (sin buscar bloque por bloque).
 */
public final class CrossingNet {
    private CrossingNet() {
    }

    /** Distancia (en bloques) a la que un mecanismo o un semáforo hace caso a un detector. */
    public static final int LINK_RANGE = 8;

    private static final Map<Level, Set<BlockPos>> DETECTORS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void add(Level level, BlockPos pos) {
        DETECTORS.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = DETECTORS.get(level);
        if (set != null) {
            set.remove(pos);
        }
    }

    /** ¿Hay un detector encendido a menos de {@code range} bloques de {@code at}? */
    public static boolean active(Level level, BlockPos at, int range) {
        Set<BlockPos> set = DETECTORS.get(level);
        if (set == null || set.isEmpty()) {
            return false;
        }
        for (BlockPos p : set) {
            if (Math.abs(p.getX() - at.getX()) <= range && Math.abs(p.getY() - at.getY()) <= range && Math.abs(p.getZ() - at.getZ()) <= range
                    && level.hasChunkAt(p)) {
                var st = level.getBlockState(p);
                if (st.getBlock() instanceof CrossingDetectorBlock && st.getValue(CrossingDetectorBlock.POWERED)) {
                    return true;
                }
            }
        }
        return false;
    }
}
