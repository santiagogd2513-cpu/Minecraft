package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/** Pieza de mentira de Level. */
public class Level {
    public boolean loaded = true;

    public boolean isLoaded(BlockPos pos) {
        return this.loaded;
    }
}
