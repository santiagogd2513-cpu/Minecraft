package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.SecurityMonitorBlock;
import com.example.beautifuldecor.block.entity.SecurityMonitorBlockEntity;
import com.example.beautifuldecor.camera.SecurityCameraHub;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.3.0, cliente a servidor: el menú del monitor elige qué cámara muestra (o la apaga). Todos los que miran ese monitor ven la misma. */
public class MonitorSelectPacket {
    private final BlockPos monitor;
    private final boolean hasCamera;
    private final BlockPos camera;

    private MonitorSelectPacket(BlockPos monitor, boolean hasCamera, BlockPos camera) {
        this.monitor = monitor;
        this.hasCamera = hasCamera;
        this.camera = camera;
    }

    public static MonitorSelectPacket show(BlockPos monitor, BlockPos camera) {
        return new MonitorSelectPacket(monitor, true, camera);
    }

    public static MonitorSelectPacket off(BlockPos monitor) {
        return new MonitorSelectPacket(monitor, false, BlockPos.ZERO);
    }

    public static MonitorSelectPacket decode(FriendlyByteBuf buf) {
        BlockPos monitor = buf.readBlockPos();
        boolean has = buf.readBoolean();
        return new MonitorSelectPacket(monitor, has, has ? buf.readBlockPos() : BlockPos.ZERO);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.monitor);
        buf.writeBoolean(this.hasCamera);
        if (this.hasCamera) {
            buf.writeBlockPos(this.camera);
        }
    }

    public static void handle(MonitorSelectPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockPos pos = packet.monitor;
            if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > CameraEditPacket.REACH * CameraEditPacket.REACH) {
                return;
            }
            if (!(level.getBlockState(pos).getBlock() instanceof SecurityMonitorBlock) || !(level.getBlockEntity(pos) instanceof SecurityMonitorBlockEntity monitor)) {
                return;
            }
            if (!packet.hasCamera) {
                monitor.setCamera(null);
            } else if (SecurityCameraHub.get(level).contains(packet.camera)) {
                monitor.setCamera(packet.camera);
            }
        });
        context.setPacketHandled(true);
    }
}
