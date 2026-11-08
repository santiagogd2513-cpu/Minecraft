package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** V4.0.0: bocina de megafonía; recuerda a qué micrófono está vinculada (null = sin vincular). */
public class PaSpeakerBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos microphone;

    public PaSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PA_SPEAKER.get(), pos, state);
    }

    @Nullable
    public BlockPos getMicrophone() {
        return this.microphone;
    }

    public void setMicrophone(@Nullable BlockPos microphone) {
        this.microphone = microphone == null ? null : microphone.immutable();
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.microphone != null) {
            tag.putLong("Microphone", this.microphone.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.microphone = tag.contains("Microphone", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Microphone")) : null;
    }
}
