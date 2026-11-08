package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.block.DogBedBlock;
import com.example.beautifuldecor.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SeatEntity extends Entity {
    private BlockPos anchorPos;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public SeatEntity(Level level, BlockPos pos, double yOffset) {
        this(ModEntities.SEAT.get(), level);
        this.anchorPos = pos.immutable();
        this.setPos(pos.getX() + 0.5, pos.getY() + yOffset, pos.getZ() + 0.5);
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (this.getPassengers().isEmpty()) {
            this.discard();
            return;
        }

        if (anchorPos != null) {
            BlockState state = this.level().getBlockState(anchorPos);
            if (state.isAir() || (this.getFirstPassenger() instanceof Wolf && !(state.getBlock() instanceof DogBedBlock))) {
                releasePassengers();
                this.discard();
            }
        }
    }

    public void releasePassengers() {
        for (Entity passenger : this.getPassengers()) {
            passenger.stopRiding();
            if (passenger instanceof Wolf wolf) {
                wolf.setOrderedToSit(false);
                wolf.setDeltaMovement(0, 0.1, 0);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            releasePassengers();
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AnchorX")) {
            anchorPos = new BlockPos(tag.getInt("AnchorX"), tag.getInt("AnchorY"), tag.getInt("AnchorZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (anchorPos != null) {
            tag.putInt("AnchorX", anchorPos.getX());
            tag.putInt("AnchorY", anchorPos.getY());
            tag.putInt("AnchorZ", anchorPos.getZ());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double y = this.getY() + 0.2;
        if (anchorPos != null) {
            y = anchorPos.getY() + 0.5;
        }
        return new Vec3(this.getX(), y, this.getZ());
    }
}
