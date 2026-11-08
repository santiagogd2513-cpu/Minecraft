package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Pantalla de ascensor (V2.2.0): recuerda a qué controlador está vinculada (su piso). */
public class ElevatorDisplayBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    public ElevatorDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_DISPLAY.get(), pos, state);
    }

    @Nullable
    public BlockPos getController() {
        return this.controller;
    }

    public void setController(@Nullable BlockPos controller) {
        this.controller = controller;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.controller != null) {
            tag.putLong("Controller", this.controller.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
