package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.AdScreenBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * V4.1.0: datos de una pantalla de anuncios: solo si está encendida. Los anuncios se reproducen en cada jugador por su cuenta
 * (ver AdPlayback y AdScreenRenderer); el servidor no manda video.
 */
public class AdScreenBlockEntity extends BlockEntity {
    private boolean on = true;

    public AdScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AD_SCREEN.get(), pos, state);
    }

    public boolean isOn() {
        return on;
    }

    /** Enciende o apaga y avisa a los jugadores; devuelve cómo quedó. */
    public boolean toggle() {
        this.on = !this.on;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
        return this.on;
    }

    /** Cuántos bloques mide de lado (4, 8 o 12); 4 si el bloque no es una pantalla. */
    public int sizeBlocks() {
        return getBlockState().getBlock() instanceof AdScreenBlock b ? b.sizeBlocks() : 4;
    }

    /** El centro de la pantalla, para medir distancias. */
    public Vec3 center() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AdScreenBlock b) {
            double halfW = (b.width() - 1) / 2.0;
            double halfH = (b.height() - 1) / 2.0;
            net.minecraft.core.Direction right = state.getValue(AdScreenBlock.FACING).getCounterClockWise();
            return new Vec3(worldPosition.getX() + 0.5 + right.getStepX() * halfW, worldPosition.getY() + 0.5 + halfH,
                    worldPosition.getZ() + 0.5 + right.getStepZ() * halfW);
        }
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("On", on);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        on = !tag.contains("On") || tag.getBoolean("On");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("On", on);
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

    // ------------------------------------------------------------------ cliente

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.AdPlayback.register(this));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.AdPlayback.unregister(this));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.AdPlayback.unregister(this));
        }
    }

    /** La pantalla abarca varios bloques: no debe desaparecer cuando se ve solo una parte. */
    public AABB getRenderBoundingBox() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AdScreenBlock b) {
            BlockPos far = worldPosition.relative(state.getValue(AdScreenBlock.FACING).getCounterClockWise(), b.width() - 1)
                    .above(b.height() - 1);
            return new AABB(Math.min(worldPosition.getX(), far.getX()), worldPosition.getY(), Math.min(worldPosition.getZ(), far.getZ()),
                    Math.max(worldPosition.getX(), far.getX()) + 1, far.getY() + 1, Math.max(worldPosition.getZ(), far.getZ()) + 1);
        }
        return new AABB(worldPosition);
    }
}
