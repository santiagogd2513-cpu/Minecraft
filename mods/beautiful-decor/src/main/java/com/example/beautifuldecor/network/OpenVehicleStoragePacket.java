package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.HasVehicleStorage;
import com.example.beautifuldecor.entity.VehicleStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V3.9.0: quien va montado en un vehículo con almacenamiento pulsó la tecla del inventario; el cliente cancela el inventario normal
 * y pide esto. El servidor abre el almacenamiento del vehículo en el que de verdad va montado.
 */
public class OpenVehicleStoragePacket {
    public OpenVehicleStoragePacket() {}

    public static OpenVehicleStoragePacket decode(FriendlyByteBuf buffer) {
        return new OpenVehicleStoragePacket();
    }

    public void encode(FriendlyByteBuf buffer) {
        // sin datos: el servidor sabe en qué vehículo va el jugador
    }

    public static void handle(OpenVehicleStoragePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            if (player.getVehicle() instanceof HasVehicleStorage vehicle && vehicle.hasStorage()
                    && player.containerMenu == player.inventoryMenu) {
                VehicleStorage.open(player, vehicle);
            }
        });
        context.setPacketHandled(true);
    }
}
