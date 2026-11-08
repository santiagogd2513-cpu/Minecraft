package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.GarageColors;
import com.example.beautifuldecor.block.GarageDoorBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Colores de una puerta de garaje (V2.3.0). Solo la pieza de la esquina (pieza 0) tiene este block entity; las otras
 * ocho piezas leen los colores de ahí. Las puertas colocadas antes de la V2.3.0 lo reciben solas con los colores por
 * defecto.
 */
public class GarageDoorBlockEntity extends BlockEntity {
    private final int[] colors = GarageColors.defaults();

    public GarageDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GARAGE_DOOR.get(), pos, state);
    }

    public int[] getColors() {
        return this.colors.clone();
    }

    /** Guarda los tres colores y avisa a los jugadores cercanos. */
    public void setColors(int[] newColors) {
        for (int i = 0; i < GarageColors.SLOTS; i++) {
            this.colors[i] = newColors[i] & 0xFFFFFF;
        }
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Colors", this.colors);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] defaults = GarageColors.defaults();
        int[] saved = tag.contains("Colors", Tag.TAG_INT_ARRAY) ? tag.getIntArray("Colors") : defaults;
        for (int i = 0; i < GarageColors.SLOTS; i++) {
            this.colors[i] = (i < saved.length ? saved[i] : defaults[i]) & 0xFFFFFF;
        }
        if (this.level != null && this.level.isClientSide) {
            refreshRender();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Cliente: vuelve a dibujar las 9 piezas con los colores nuevos. */
    private void refreshRender() {
        BlockState state = this.getBlockState();
        Level level = this.level;
        if (level == null || !(state.getBlock() instanceof GarageDoorBlock)) {
            return;
        }
        Direction facing = state.getValue(GarageDoorBlock.FACING);
        for (int part = 0; part < 9; part++) {
            BlockPos p = GarageDoorBlock.partPos(this.worldPosition, facing, part);
            BlockState s = level.getBlockState(p);
            level.sendBlockUpdated(p, s, s, 8);
        }
    }
}
