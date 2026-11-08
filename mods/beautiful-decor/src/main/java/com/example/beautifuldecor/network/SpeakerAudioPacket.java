package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V4.0.0, servidor a cliente: un bloque de voz (IMA ADPCM, 40 ms) que suena por las bocinas {@code speakers}
 * (solo las que tiene a su alcance quien lo recibe) del micrófono {@code mic}.
 */
public class SpeakerAudioPacket {
    public static final int MAX_SPEAKERS = 16;

    public final BlockPos mic;
    public final long[] speakers;
    public final byte[] data;

    public SpeakerAudioPacket(BlockPos mic, long[] speakers, byte[] data) {
        this.mic = mic;
        this.speakers = speakers;
        this.data = data;
    }

    public static SpeakerAudioPacket decode(FriendlyByteBuf buf) {
        BlockPos mic = buf.readBlockPos();
        int count = Math.min(buf.readVarInt(), MAX_SPEAKERS);
        long[] speakers = new long[count];
        for (int i = 0; i < count; i++) {
            speakers[i] = buf.readLong();
        }
        return new SpeakerAudioPacket(mic, speakers, buf.readByteArray(MicAudioPacket.MAX_BYTES));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.mic);
        buf.writeVarInt(this.speakers.length);
        for (long speaker : this.speakers) {
            buf.writeLong(speaker);
        }
        buf.writeByteArray(this.data);
    }

    public static void handle(SpeakerAudioPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.megaphone.client.PaClient.onSpeakerAudio(packet)));
        context.setPacketHandled(true);
    }
}
