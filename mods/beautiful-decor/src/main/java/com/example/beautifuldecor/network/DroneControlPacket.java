package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.DroneEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V1.8.5: el piloto manda cada tick dónde está su dron (posición y hacia dónde mira) o pide salir del control.
 * El vuelo lo calcula el cliente; el servidor solo valida y retransmite.
 */
public class DroneControlPacket {
    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean exit;

    public DroneControlPacket(int entityId, double x, double y, double z, float yaw, float pitch) {
        this(entityId, x, y, z, yaw, pitch, false);
    }

    private DroneControlPacket(int entityId, double x, double y, double z, float yaw, float pitch, boolean exit) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.exit = exit;
    }

    public static DroneControlPacket exit(int entityId) {
        return new DroneControlPacket(entityId, 0.0, 0.0, 0.0, 0.0f, 0.0f, true);
    }

    public static DroneControlPacket decode(FriendlyByteBuf buffer) {
        return new DroneControlPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.yaw);
        buffer.writeFloat(this.pitch);
        buffer.writeBoolean(this.exit);
    }

    public static void handle(DroneControlPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (entity instanceof DroneEntity drone) {
                if (packet.exit) {
                    drone.stopPiloting(player);
                } else {
                    drone.acceptPilotUpdate(player, packet.x, packet.y, packet.z, packet.yaw, packet.pitch);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
