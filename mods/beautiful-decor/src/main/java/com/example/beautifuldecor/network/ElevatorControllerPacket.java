package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2.2.0, servidor a cliente: los ajustes de un controlador de ascensor (marca la zona y, si {@code openMenu}, abre el menú). V3.3.0: con el desplazamiento de la zona. */
public class ElevatorControllerPacket {
    public final BlockPos pos;
    public final Direction facing;
    public final int width;
    public final int depth;
    public final int height;
    public final int offX;
    public final int offY;
    public final int offZ;
    public final float speed;
    public final String name;
    public final boolean openMenu;

    public ElevatorControllerPacket(BlockPos pos, Direction facing, int width, int depth, int height, int offX, int offY, int offZ,
                                    float speed, String name, boolean openMenu) {
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
        this.openMenu = openMenu;
    }

    public static ElevatorControllerPacket decode(FriendlyByteBuf buf) {
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
        boolean openMenu = buf.readBoolean();
        return new ElevatorControllerPacket(pos, facing, width, depth, height, offX, offY, offZ, speed, name, openMenu);
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
        buf.writeBoolean(this.openMenu);
    }

    public static void handle(ElevatorControllerPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.client.ClientHooks.openElevatorController(packet)));
        context.setPacketHandled(true);
    }
}
