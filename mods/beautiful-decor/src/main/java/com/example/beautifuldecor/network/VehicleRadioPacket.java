package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.VehicleEntity;
import com.example.beautifuldecor.radio.RadioStations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V3.5.0, cliente a servidor: el menú de la radio del vehículo (tecla X) elige emisora, enciende/apaga o cambia el volumen.
 * El servidor revisa que quien lo manda vaya montado en ese vehículo y limpia la dirección y el nombre.
 */
public class VehicleRadioPacket {
    public static final int SELECT = 0;
    public static final int POWER = 1;
    public static final int VOLUME = 2;

    private final int entityId;
    private final int action;
    private final String url;
    private final String name;
    private final int value;

    private VehicleRadioPacket(int entityId, int action, String url, String name, int value) {
        this.entityId = entityId;
        this.action = action;
        this.url = url == null ? "" : url;
        this.name = name == null ? "" : name;
        this.value = value;
    }

    public static VehicleRadioPacket select(int entityId, String url, String name) {
        String u = url == null ? "" : url;
        String n = name == null ? "" : name;
        return new VehicleRadioPacket(entityId, SELECT, u.length() > RadioStations.MAX_URL ? "" : u,
                n.length() > RadioStations.MAX_NAME ? n.substring(0, RadioStations.MAX_NAME) : n, 0);
    }

    public static VehicleRadioPacket power(int entityId, boolean on) {
        return new VehicleRadioPacket(entityId, POWER, "", "", on ? 1 : 0);
    }

    public static VehicleRadioPacket volume(int entityId, int volume) {
        return new VehicleRadioPacket(entityId, VOLUME, "", "", RadioStations.clampVolume(volume));
    }

    public static VehicleRadioPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int action = buf.readByte();
        if (action == SELECT) {
            String url = buf.readUtf(RadioStations.MAX_URL);
            String name = buf.readUtf(RadioStations.MAX_NAME);
            return new VehicleRadioPacket(id, action, url, name, 0);
        }
        return new VehicleRadioPacket(id, action, "", "", buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeByte(this.action);
        if (this.action == SELECT) {
            buf.writeUtf(this.url, RadioStations.MAX_URL);
            buf.writeUtf(this.name, RadioStations.MAX_NAME);
        } else {
            buf.writeVarInt(this.value);
        }
    }

    public static void handle(VehicleRadioPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.level().getEntity(packet.entityId);
            if (!(entity instanceof VehicleEntity vehicle) || entity.isRemoved() || player.getVehicle() != entity) {
                return;     // solo quien va montado en el vehículo maneja su radio
            }
            switch (packet.action) {
                case SELECT -> {
                    String url = RadioStations.cleanUrl(packet.url);
                    if (url == null) {
                        return;
                    }
                    String name = RadioStations.cleanName(packet.name);
                    vehicle.setRadioStation(url, name.isEmpty() ? RadioStations.nameFromUrl(url) : name);
                }
                case POWER -> vehicle.setRadioOn(packet.value != 0);
                case VOLUME -> vehicle.setRadioVolume(packet.value);
                default -> {
                    // acción desconocida: nada
                }
            }
        });
        context.setPacketHandled(true);
    }
}
