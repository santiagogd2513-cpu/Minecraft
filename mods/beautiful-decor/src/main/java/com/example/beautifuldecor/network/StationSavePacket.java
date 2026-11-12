package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.entity.StationControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V6.0.0, cliente a servidor: guarda los ajustes de una estación. */
public class StationSavePacket {
    private final BlockPos pos;
    private final String name;
    private final int dwell;
    private final int depart;
    private final int travel;
    private final int open;
    private final int close;

    public StationSavePacket(BlockPos pos, String name, int dwell, int depart, int travel, int open, int close) {
        this.pos = pos;
        this.name = name;
        this.dwell = dwell;
        this.depart = depart;
        this.travel = travel;
        this.open = open;
        this.close = close;
    }

    public static StationSavePacket decode(FriendlyByteBuf buf) {
        return new StationSavePacket(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.name, 32);
        buf.writeVarInt(this.dwell);
        buf.writeVarInt(this.depart);
        buf.writeVarInt(this.travel);
        buf.writeVarInt(this.open);
        buf.writeVarInt(this.close);
    }

    public static void handle(StationSavePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level) || !level.isLoaded(packet.pos)
                    || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 144.0) {
                return;
            }
            if (level.getBlockEntity(packet.pos) instanceof StationControllerBlockEntity be) {
                be.apply(packet.name, packet.dwell, packet.depart, packet.travel, packet.open, packet.close);
            }
        });
        context.setPacketHandled(true);
    }
}
