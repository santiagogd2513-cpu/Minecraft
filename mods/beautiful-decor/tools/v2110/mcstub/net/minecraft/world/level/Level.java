package net.minecraft.world.level;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
public class Level {
    public final boolean isClientSide = true;
    public ResourceKey<Level> dimension() { return null; }
    public BlockState getBlockState(BlockPos pos) { return null; }
    public boolean isLoaded(BlockPos pos) { return true; }
    public void sendBlockUpdated(BlockPos pos, BlockState a, BlockState b, int flags) { }
}
