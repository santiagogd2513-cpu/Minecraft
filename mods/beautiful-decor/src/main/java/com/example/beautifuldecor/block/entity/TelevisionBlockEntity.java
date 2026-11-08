package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.BigTelevisionBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Datos de un televisor: el enlace (o la ruta) del video, si está reproduciendo y cuántas veces se pidió empezar (para que un
 * segundo "Reproducir" lo reinicie). El video se baja y se dibuja en cada jugador (V2.11.0, ver TvPlayback y TelevisionRenderer).
 */
public class TelevisionBlockEntity extends BlockEntity {
    private String videoPath = "";
    private boolean playing = false;
    private int startCount = 0;

    public TelevisionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEVISION.get(), pos, state);
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String path) {
        this.videoPath = path == null ? "" : path;
        setChanged();
        sync();
    }

    public boolean isPlaying() {
        return playing;
    }

    /** Cuántas veces se pidió reproducir; cambia en cada "Reproducir", aunque el enlace sea el mismo. */
    public int getStartCount() {
        return startCount;
    }

    /** Aplica lo que pidió el jugador: enlace nuevo y si debe reproducir (se avisa una sola vez a los clientes). */
    public void apply(String path, boolean play) {
        this.videoPath = path == null ? "" : path;
        this.playing = play && !this.videoPath.isEmpty();
        if (this.playing) {
            this.startCount++;
        }
        setChanged();
        sync();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("VideoPath", videoPath);
        tag.putBoolean("Playing", playing);
        tag.putInt("StartCount", startCount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        videoPath = tag.getString("VideoPath");
        playing = tag.getBoolean("Playing");
        startCount = tag.getInt("StartCount");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("VideoPath", videoPath);
        tag.putBoolean("Playing", playing);
        tag.putInt("StartCount", startCount);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    // ------------------------------------------------------------------ cliente (V2.11.0)

    /** En el cliente, el televisor se anota para que TvPlayback lo vea aunque no esté en pantalla. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.TvPlayback.register(this));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.TvPlayback.unregister(this));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.beautifuldecor.client.TvPlayback.unregister(this));
        }
    }

    /** Los televisores grandes se extienden por varios bloques: la pantalla no debe desaparecer cuando se ve solo una parte. */
    public AABB getRenderBoundingBox() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof BigTelevisionBlock big) {
            BlockPos far = worldPosition.relative(state.getValue(BigTelevisionBlock.FACING).getCounterClockWise(), big.widthBlocks() - 1)
                    .above(big.heightBlocks() - 1);
            return new AABB(Math.min(worldPosition.getX(), far.getX()), worldPosition.getY(), Math.min(worldPosition.getZ(), far.getZ()),
                    Math.max(worldPosition.getX(), far.getX()) + 1, far.getY() + 1, Math.max(worldPosition.getZ(), far.getZ()) + 1);
        }
        return new AABB(worldPosition);
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
}
