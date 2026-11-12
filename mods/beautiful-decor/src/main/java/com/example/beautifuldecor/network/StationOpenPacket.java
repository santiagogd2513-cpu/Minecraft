package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V6.0.0, servidor a cliente: abre los ajustes de una estación. {@code index} = su lugar en la ruta del tren vinculado (-1 si no está). */
public class StationOpenPacket {
    public final BlockPos pos;
    public final String name;
    public final int dwell;
    public final int depart;
    public final int travel;
    public final int open;
    public final int close;
    public final int index;
    public final int total;

    public StationOpenPacket(BlockPos pos, String name, int dwell, int depart, int travel, int open, int close, int index, int total) {
        this.pos = pos;
        this.name = name;
        this.dwell = dwell;
        this.depart = depart;
        this.travel = travel;
        this.open = open;
        this.close = close;
        this.index = index;
        this.total = total;
    }

    public static StationOpenPacket decode(FriendlyByteBuf buf) {
        return new StationOpenPacket(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.name, 32);
        buf.writeVarInt(this.dwell);
        buf.writeVarInt(this.depart);
        buf.writeVarInt(this.travel);
        buf.writeVarInt(this.open);
        buf.writeVarInt(this.close);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.total);
    }

    public static void handle(StationOpenPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.client.ClientHooks.openStation(packet)));
        context.setPacketHandled(true);
    }
}
