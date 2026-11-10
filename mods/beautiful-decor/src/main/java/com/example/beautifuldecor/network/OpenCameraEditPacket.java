package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V4.3.0, servidor a cliente: al poner una cámara, abre su menú (nombre y descripción) a quien la puso. */
public class OpenCameraEditPacket {
    private final BlockPos pos;

    public OpenCameraEditPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static OpenCameraEditPacket decode(FriendlyByteBuf buf) {
        return new OpenCameraEditPacket(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    public static void handle(OpenCameraEditPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.client.ClientHooks.openCameraEditScreen(packet.pos)));
        context.setPacketHandled(true);
    }
}
