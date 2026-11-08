package com.example.beautifuldecor.network;

import com.example.beautifuldecor.megaphone.PaServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.0.0, cliente a servidor: una orden del menú del micrófono (hablar, dejar de hablar, vincular, desvincular, pedir el estado). */
public class MicControlPacket {
    public static final byte START_TALK = 0;
    public static final byte STOP_TALK = 1;
    public static final byte LINK_NEARBY = 2;
    public static final byte UNLINK_ALL = 3;
    public static final byte QUERY = 4;

    public final BlockPos pos;
    public final byte action;

    public MicControlPacket(BlockPos pos, byte action) {
        this.pos = pos;
        this.action = action;
    }

    public static MicControlPacket decode(FriendlyByteBuf buf) {
        return new MicControlPacket(buf.readBlockPos(), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.action);
    }

    public static void handle(MicControlPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> PaServer.handleControl(player, packet.pos, packet.action));
        }
        context.setPacketHandled(true);
    }
}
