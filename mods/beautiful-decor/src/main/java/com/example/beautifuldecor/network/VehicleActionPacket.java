package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.VehicleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2.0.0 (generalizado en la 2.1.0): el conductor pide encender/apagar la luz delantera o tocar el claxon/timbre. */
public class VehicleActionPacket {
    private final int entityId;
    private final int action;

    public VehicleActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static VehicleActionPacket decode(FriendlyByteBuf buffer) {
        return new VehicleActionPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.action);
    }

    public static void handle(VehicleActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // Solo el conductor de ese vehículo puede usar sus controles.
            if (player.getVehicle() instanceof VehicleEntity vehicle && vehicle.getId() == packet.entityId
                    && vehicle.getControllingPassenger() == player) {
                if (packet.action == VehicleEntity.ACTION_LIGHT) {
                    vehicle.toggleLight(player);
                } else if (packet.action == VehicleEntity.ACTION_HORN) {
                    vehicle.honk();
                } else if (packet.action == VehicleEntity.ACTION_SIREN) {
                    vehicle.toggleSiren(player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
