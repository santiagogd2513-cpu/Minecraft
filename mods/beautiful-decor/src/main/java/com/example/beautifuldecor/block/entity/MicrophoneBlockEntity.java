package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.MicrophoneBlock;
import com.example.beautifuldecor.block.PaSpeakerBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.megaphone.PaServer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * V4.0.0: micrófono de megafonía. Guarda las posiciones de las bocinas vinculadas (hasta {@link #MAX_SPEAKERS}) y, solo en el servidor,
 * quién está hablando por él ahora.
 */
public class MicrophoneBlockEntity extends BlockEntity {
    public static final int MAX_SPEAKERS = 16;

    private final List<BlockPos> speakers = new ArrayList<>();
    /** Solo servidor, no se guarda: el jugador que está hablando. */
    @Nullable
    private UUID talker;

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROPHONE.get(), pos, state);
    }

    // ------------------------------------------------------------------ bocinas vinculadas

    public List<BlockPos> getSpeakers() {
        return this.speakers;
    }

    public int getSpeakerCount() {
        return this.speakers.size();
    }

    public boolean isFull() {
        return this.speakers.size() >= MAX_SPEAKERS;
    }

    /** @return true si quedó vinculada (o ya lo estaba); false si el micrófono ya está lleno. */
    public boolean addSpeaker(BlockPos pos) {
        BlockPos p = pos.immutable();
        if (this.speakers.contains(p)) {
            return true;
        }
        if (this.isFull()) {
            return false;
        }
        this.speakers.add(p);
        this.changed();
        return true;
    }

    public void removeSpeaker(BlockPos pos) {
        if (this.speakers.remove(pos)) {
            this.changed();
        }
    }

    public void clearSpeakers() {
        if (!this.speakers.isEmpty()) {
            this.speakers.clear();
            this.changed();
        }
    }

    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ quién habla

    @Nullable
    public UUID getTalker() {
        return this.talker;
    }

    public void setTalker(@Nullable UUID talker) {
        this.talker = talker;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MicrophoneBlockEntity be) {
        if (level.getGameTime() % 10L != 0L) {
            return;
        }
        PaServer.tickMicrophone(level, pos, state, be);
    }

    /** Quita las bocinas que ya no existen (solo si su zona está cargada, para no borrar vínculos por no haberlas visto). */
    public void dropMissingSpeakers(Level level) {
        boolean any = this.speakers.removeIf(p -> level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof PaSpeakerBlock));
        if (any) {
            this.changed();
        }
    }

    // ------------------------------------------------------------------ guardado y envío al cliente

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        long[] longs = new long[this.speakers.size()];
        for (int i = 0; i < longs.length; i++) {
            longs[i] = this.speakers.get(i).asLong();
        }
        tag.put("Speakers", new LongArrayTag(longs));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.speakers.clear();
        if (tag.contains("Speakers", Tag.TAG_LONG_ARRAY)) {
            for (long l : tag.getLongArray("Speakers")) {
                if (this.speakers.size() < MAX_SPEAKERS) {
                    this.speakers.add(BlockPos.of(l));
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Estado del bloque con la luz del micrófono (la lucecita roja) encendida o apagada. */
    public static BlockState withTalking(BlockState state, boolean talking) {
        return state.hasProperty(MicrophoneBlock.TALKING) ? state.setValue(MicrophoneBlock.TALKING, talking) : state;
    }
}
