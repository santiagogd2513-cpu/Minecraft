package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V4.0.0, servidor a cliente: cómo está un micrófono (cuántas bocinas tiene, quién habla) para el menú.
 * {@code message} es una clave de idioma con un aviso corto (vacía si no hay), por ejemplo «ocupado» o «sin bocinas cerca».
 */
public class MicStatePacket {
    public final BlockPos pos;
    public final int speakers;
    public final int maxSpeakers;
    /** Nombre de quien habla ahora por este micrófono (vacío si nadie). */
    public final String talkerName;
    /** true si quien habla eres tú (el servidor aceptó). */
    public final boolean youTalking;
    public final String message;
    public final int arg;

    public MicStatePacket(BlockPos pos, int speakers, int maxSpeakers, String talkerName, boolean youTalking, String message, int arg) {
        this.pos = pos;
        this.speakers = speakers;
        this.maxSpeakers = maxSpeakers;
        this.talkerName = talkerName;
        this.youTalking = youTalking;
        this.message = message;
        this.arg = arg;
    }

    public static MicStatePacket decode(FriendlyByteBuf buf) {
        return new MicStatePacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(),
                buf.readUtf(96), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.speakers);
        buf.writeVarInt(this.maxSpeakers);
        buf.writeUtf(this.talkerName, 64);
        buf.writeBoolean(this.youTalking);
        buf.writeUtf(this.message, 96);
        buf.writeVarInt(this.arg);
    }

    public static void handle(MicStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.megaphone.client.PaClient.onMicState(packet)));
        context.setPacketHandled(true);
    }
}
