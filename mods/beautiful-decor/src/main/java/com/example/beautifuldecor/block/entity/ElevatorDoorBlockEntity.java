package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.ElevatorDoorBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Puerta de ascensor (V2.2.0): recuerda su controlador y, unas cuantas veces por segundo, mira si la cabina está parada
 * en ese piso para abrirse (o cerrarse si ya se fue).
 */
public class ElevatorDoorBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    public ElevatorDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_DOOR.get(), pos, state);
    }

    @Nullable
    public BlockPos getController() {
        return this.controller;
    }

    public void setController(@Nullable BlockPos controller) {
        this.controller = controller;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElevatorDoorBlockEntity door) {
        if (door.controller == null || level.getGameTime() % 4L != 0L) {
            return;
        }
        if (!level.isLoaded(door.controller)) {
            return;
        }
        boolean shouldOpen = level.getBlockEntity(door.controller) instanceof ElevatorControllerBlockEntity ctrl
                && ctrl.isCabinHere();
        if (state.getValue(ElevatorDoorBlock.OPEN) != shouldOpen) {
            level.setBlock(pos, state.setValue(ElevatorDoorBlock.OPEN, shouldOpen), 3);
        }
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
