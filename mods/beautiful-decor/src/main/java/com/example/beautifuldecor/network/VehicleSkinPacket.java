package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.SkinnableVehicle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V6.7.0: el jugador escoge la skin de un vehículo (menú de la tecla R, ficha Skins). */
public class VehicleSkinPacket {
    private final int entityId;
    private final int skin;

    public VehicleSkinPacket(int entityId, int skin) {
        this.entityId = entityId;
        this.skin = skin;
    }

    public static VehicleSkinPacket decode(FriendlyByteBuf buffer) {
        return new VehicleSkinPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.skin);
    }

    public static void handle(VehicleSkinPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(packet.entityId);
            if (entity instanceof SkinnableVehicle vehicle && player.distanceToSqr(entity) < 16.0 * 16.0 && packet.skin >= 0 && packet.skin < vehicle.skinCount()) {
                vehicle.setSkin(packet.skin);
            }
        });
        context.setPacketHandled(true);
    }
}
