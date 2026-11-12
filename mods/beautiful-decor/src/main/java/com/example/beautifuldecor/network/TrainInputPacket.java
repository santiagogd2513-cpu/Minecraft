package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.TrainCarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.8.0: lo que pide quien conduce la locomotora (bits: 1 adelante, 2 atrás/freno, 4 freno de emergencia). */
public class TrainInputPacket {
    private final int entityId;
    private final int flags;

    public TrainInputPacket(int entityId, int flags) {
        this.entityId = entityId;
        this.flags = flags;
    }

    public static TrainInputPacket decode(FriendlyByteBuf buffer) {
        return new TrainInputPacket(buffer.readVarInt(), buffer.readByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeByte(this.flags);
    }

    public static void handle(TrainInputPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.getVehicle() instanceof TrainCarEntity car && car.getId() == packet.entityId
                    && car.variant().isLoco() && car.getPassengers().indexOf(player) == 0) {
                car.setInput(packet.flags);
            }
        });
        context.setPacketHandled(true);
    }
}
