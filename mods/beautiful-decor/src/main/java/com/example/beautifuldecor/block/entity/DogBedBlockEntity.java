package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class DogBedBlockEntity extends BlockEntity {
    @Nullable
    private UUID sittingWolfId = null;

    public DogBedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DOG_BED.get(), pos, state);
    }

    @Nullable
    public UUID getSittingWolfId() {
        return sittingWolfId;
    }

    public void setSittingWolfId(@Nullable UUID id) {
        this.sittingWolfId = id;
        setChanged();
    }

    @Nullable
    public Wolf findSittingWolf() {
        if (sittingWolfId == null || level == null || level.isClientSide) return null;
        if (!(level instanceof ServerLevel server)) return null;
        Entity e = server.getEntity(sittingWolfId);
        return e instanceof Wolf w && w.isAlive() ? w : null;
    }

    /** Fully release wolf: gravity back, stand up, clear id */
    public void releaseWolf() {
        Wolf wolf = findSittingWolf();
        if (wolf != null) {
            wolf.setOrderedToSit(false);
            wolf.setNoGravity(false);
            if (wolf.isPassenger()) {
                wolf.stopRiding();
            }
            // Nudge onto bed top so it doesn't fall through floor
            wolf.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            wolf.setDeltaMovement(0, 0, 0);
        }
        sittingWolfId = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (sittingWolfId != null) {
            tag.putUUID("SittingWolf", sittingWolfId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("SittingWolf")) {
            sittingWolfId = tag.getUUID("SittingWolf");
        } else {
            sittingWolfId = null;
        }
    }
}
