package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.RcCarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V3.1.0: el piloto manda cada tick dónde está su carro RC (posición y rumbo) o pide dejar de manejarlo.
 * El manejo lo calcula el cliente del piloto; el servidor solo valida y retransmite.
 */
public class RcCarControlPacket {
    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final boolean exit;

    public RcCarControlPacket(int entityId, double x, double y, double z, float yaw) {
        this(entityId, x, y, z, yaw, false);
    }

    private RcCarControlPacket(int entityId, double x, double y, double z, float yaw, boolean exit) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.exit = exit;
    }

    public static RcCarControlPacket exit(int entityId) {
        return new RcCarControlPacket(entityId, 0.0, 0.0, 0.0, 0.0f, true);
    }

    public static RcCarControlPacket decode(FriendlyByteBuf buffer) {
        return new RcCarControlPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.yaw);
        buffer.writeBoolean(this.exit);
    }

    public static void handle(RcCarControlPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(packet.entityId);
            if (entity instanceof RcCarEntity car) {
                if (packet.exit) {
                    car.stopPiloting(player);
                } else {
                    car.acceptPilotUpdate(player, packet.x, packet.y, packet.z, packet.yaw);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
