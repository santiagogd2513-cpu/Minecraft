package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * V4.2.0: bafle de la radio de mesa; recuerda a qué radio está vinculado (null = sin vincular). En el cliente se apunta en
 * {@link #CLIENT} mientras está cargado, para que la reproducción sepa dónde hay bafles.
 */
public class RadioSpeakerBlockEntity extends BlockEntity {
    /** Los bafles cargados en este cliente (solo se usa del lado del cliente). */
    public static final Set<RadioSpeakerBlockEntity> CLIENT = new HashSet<>();

    @Nullable
    private BlockPos radio;

    public RadioSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIO_SPEAKER.get(), pos, state);
    }

    @Nullable
    public BlockPos getRadio() {
        return this.radio;
    }

    public void setRadio(@Nullable BlockPos radio) {
        this.radio = radio == null ? null : radio.immutable();
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            CLIENT.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        CLIENT.remove(this);
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.radio != null) {
            tag.putLong("Radio", this.radio.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.radio = tag.contains("Radio", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Radio")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
