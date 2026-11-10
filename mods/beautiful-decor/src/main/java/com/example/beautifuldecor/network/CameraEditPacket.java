package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.SecurityCameraBlock;
import com.example.beautifuldecor.block.entity.SecurityCameraBlockEntity;
import com.example.beautifuldecor.camera.CameraInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.3.0, cliente a servidor: el menú de la cámara guarda su nombre, su descripción y si rota. Se revisa que estés cerca de la cámara. */
public class CameraEditPacket {
    public static final double REACH = 10.0;

    private final BlockPos pos;
    private final String name;
    private final String description;
    private final boolean rotate;

    public CameraEditPacket(BlockPos pos, String name, String description, boolean rotate) {
        this.pos = pos;
        this.name = CameraInfo.clean(name, CameraInfo.MAX_NAME);
        this.description = CameraInfo.clean(description, CameraInfo.MAX_DESC);
        this.rotate = rotate;
    }

    public static CameraEditPacket decode(FriendlyByteBuf buf) {
        return new CameraEditPacket(buf.readBlockPos(), buf.readUtf(CameraInfo.MAX_NAME), buf.readUtf(CameraInfo.MAX_DESC), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.name, CameraInfo.MAX_NAME);
        buf.writeUtf(this.description, CameraInfo.MAX_DESC);
        buf.writeBoolean(this.rotate);
    }

    public static void handle(CameraEditPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(packet.pos) || player.distanceToSqr(Vec3.atCenterOf(packet.pos)) > REACH * REACH) {
                return;
            }
            if (level.getBlockState(packet.pos).getBlock() instanceof SecurityCameraBlock
                    && level.getBlockEntity(packet.pos) instanceof SecurityCameraBlockEntity camera) {
                camera.applyEdit(level, packet.name, packet.description, packet.rotate);
            }
        });
        context.setPacketHandled(true);
    }
}
