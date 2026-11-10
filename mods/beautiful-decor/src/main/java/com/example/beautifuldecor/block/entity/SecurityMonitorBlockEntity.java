package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * V4.3.0: monitor (o pantalla de pared) de cámaras de seguridad. Solo la parte izquierda (vista de frente) tiene block entity. Recuerda
 * qué cámara muestra (null = apagado); se sincroniza con los clientes, que dibujan la imagen (SecurityMonitorRenderer).
 */
public class SecurityMonitorBlockEntity extends BlockEntity {
    /** Los monitores cargados en este cliente (solo se usa del lado del cliente). */
    public static final Set<SecurityMonitorBlockEntity> CLIENT = new HashSet<>();

    @Nullable
    private BlockPos camera;

    public SecurityMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECURITY_MONITOR.get(), pos, state);
    }

    @Nullable
    public BlockPos getCamera() {
        return this.camera;
    }

    public void setCamera(@Nullable BlockPos camera) {
        this.camera = camera == null ? null : camera.immutable();
        setChanged();
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
        if (this.camera != null) {
            tag.putLong("Camera", this.camera.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.camera = tag.contains("Camera", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("Camera")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    /** El monitor abarca dos bloques: no debe desaparecer cuando se ve solo una parte. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof SecurityMonitorBlock) {
            BlockPos far = this.worldPosition.relative(state.getValue(SecurityMonitorBlock.FACING).getCounterClockWise(), SecurityMonitorBlock.WIDTH - 1);
            return new AABB(Math.min(this.worldPosition.getX(), far.getX()), this.worldPosition.getY(), Math.min(this.worldPosition.getZ(), far.getZ()),
                    Math.max(this.worldPosition.getX(), far.getX()) + 1, this.worldPosition.getY() + 1, Math.max(this.worldPosition.getZ(), far.getZ()) + 1);
        }
        return new AABB(this.worldPosition);
    }

    /** Hacia dónde mira el monitor. */
    public Direction facing() {
        return getBlockState().getValue(SecurityMonitorBlock.FACING);
    }
}
