package net.minecraft.world.item;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/** Pieza de mentira con la misma forma que la de Minecraft 1.20.1 para lo que usa VehiclePaint. */
public class ItemStack {
    @Nullable
    private CompoundTag tag;

    public CompoundTag getOrCreateTag() {
        if (this.tag == null) {
            this.tag = new CompoundTag();
        }
        return this.tag;
    }

    @Nullable
    public CompoundTag getTag() {
        return this.tag;
    }
}
