package com.example.beautifuldecor.network;

import com.example.beautifuldecor.elevator.ElevatorShaft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2.2.0, cliente a servidor: el menú del controlador guarda tamaño, velocidad, dirección y nombre del piso (V3.3.0: y cuánto está corrida la zona). */
public class ElevatorSavePacket {
    private final BlockPos pos;
    private final Direction facing;
    private final int width;
    private final int depth;
    private final int height;
    private final int offX;
    private final int offY;
    private final int offZ;
    private final float speed;
    private final String name;

    public ElevatorSavePacket(BlockPos pos, Direction facing, int width, int depth, int height, int offX, int offY, int offZ,
                              float speed, String name) {
        this.pos = pos;
        this.facing = facing;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.offX = offX;
        this.offY = offY;
        this.offZ = offZ;
        this.speed = speed;
        this.name = name;
    }

    public static ElevatorSavePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction facing = Direction.from2DDataValue(buf.readByte());
        int width = buf.readVarInt();
        int depth = buf.readVarInt();
        int height = buf.readVarInt();
        int offX = buf.readByte();
        int offY = buf.readByte();
        int offZ = buf.readByte();
        float speed = buf.readFloat();
        String name = buf.readUtf(64);
        return new ElevatorSavePacket(pos, facing, width, depth, height, offX, offY, offZ, speed, name);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.facing.get2DDataValue());
        buf.writeVarInt(this.width);
        buf.writeVarInt(this.depth);
        buf.writeVarInt(this.height);
        buf.writeByte(this.offX);
        buf.writeByte(this.offY);
        buf.writeByte(this.offZ);
        buf.writeFloat(this.speed);
        buf.writeUtf(this.name, 64);
    }

    public static void handle(ElevatorSavePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!level.isLoaded(packet.pos) || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 100.0) {
                return;
            }
            ElevatorShaft.applySettings(level, packet.pos, packet.facing, packet.width, packet.depth, packet.height,
                    packet.offX, packet.offY, packet.offZ, packet.speed, packet.name, player);
        });
        context.setPacketHandled(true);
    }
}
