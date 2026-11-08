package net.minecraft.world.level.block.entity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
public class BlockEntity {
    protected Level level;
    protected final BlockPos worldPosition = new BlockPos();
    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { }
    public BlockState getBlockState() { return null; }
    public BlockPos getBlockPos() { return worldPosition; }
    public Level getLevel() { return level; }
    public boolean isRemoved() { return false; }
    public void setChanged() { }
    public void setRemoved() { }
    public void onLoad() { }
    public void onChunkUnloaded() { }
    protected void saveAdditional(CompoundTag tag) { }
    public void load(CompoundTag tag) { }
    public CompoundTag getUpdateTag() { return null; }
    public void handleUpdateTag(CompoundTag tag) { }
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return null; }
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) { }
    public AABB getRenderBoundingBox() { return null; }
}
