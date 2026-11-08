package com.amatista.arsenal.block;

import com.amatista.arsenal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Lo que guarda el pedestal: el objeto en exhibición y si la vitrina se ilumina en arcoíris. */
public class PedestalBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private boolean rainbow;

    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PEDESTAL_BE.get(), pos, state);
    }

    public ItemStack getItem() {
        return item;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    /** Lo usa la generación del búnker (antes de que exista el mundo del jugador). */
    public void setup(ItemStack stack, boolean rainbow) {
        this.item = stack;
        this.rainbow = rainbow;
        setChanged();
    }

    public void setItem(ItemStack stack) {
        this.item = stack;
        sync();
    }

    public ItemStack take() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        sync();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Color del arcoíris en este momento (una vuelta completa cada 6 segundos). */
    public static float[] rainbow(long gameTime, float partial) {
        float h = ((gameTime % 120) + partial) / 120f;
        int rgb = Mth.hsvToRgb(h - (float) Math.floor(h), 0.8f, 1f);
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        item = tag.contains("Item") ? ItemStack.of(tag.getCompound("Item")) : ItemStack.EMPTY;
        rainbow = tag.getBoolean("Rainbow");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!item.isEmpty()) tag.put("Item", item.save(new CompoundTag()));
        tag.putBoolean("Rainbow", rainbow);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 4, 0).inflate(1);
    }
}
