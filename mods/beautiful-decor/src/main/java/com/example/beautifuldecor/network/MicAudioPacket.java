package com.example.beautifuldecor.network;

import com.example.beautifuldecor.megaphone.PaServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.0.0, cliente a servidor: un bloque de voz (IMA ADPCM, 40 ms) de quien está hablando por el micrófono de {@code pos}. */
public class MicAudioPacket {
    public static final int MAX_BYTES = 1024;

    public final BlockPos pos;
    public final byte[] data;

    public MicAudioPacket(BlockPos pos, byte[] data) {
        this.pos = pos;
        this.data = data;
    }

    public static MicAudioPacket decode(FriendlyByteBuf buf) {
        return new MicAudioPacket(buf.readBlockPos(), buf.readByteArray(MAX_BYTES));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByteArray(this.data);
    }

    public static void handle(MicAudioPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> PaServer.handleAudio(player, packet.pos, packet.data));
        }
        context.setPacketHandled(true);
    }
}
