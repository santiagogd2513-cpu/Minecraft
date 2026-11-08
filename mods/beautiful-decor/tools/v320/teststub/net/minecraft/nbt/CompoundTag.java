package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/** Pieza de mentira con la misma forma que la de Minecraft 1.20.1 para lo que usa VehiclePaint. */
public class CompoundTag {
    private final Map<String, int[]> ints = new HashMap<>();

    public boolean contains(String key) {
        return this.ints.containsKey(key);
    }

    public void putIntArray(String key, int[] value) {
        this.ints.put(key, value.clone());
    }

    public int[] getIntArray(String key) {
        int[] v = this.ints.get(key);
        return v == null ? new int[0] : v.clone();
    }
}
