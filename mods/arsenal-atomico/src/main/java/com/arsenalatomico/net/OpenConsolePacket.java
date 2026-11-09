package com.arsenalatomico.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador: abre la consola de lanzamiento (tapa, llave y botón). */
public class OpenConsolePacket {
    public final BlockPos target;

    public OpenConsolePacket(BlockPos target) {
        this.target = target;
    }

    public OpenConsolePacket(FriendlyByteBuf b) {
        this(b.readBlockPos());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(target);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // por un método del cliente: así el servidor nunca carga las clases de pantallas
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.ConsoleScreen.open(target));
        ctx.get().setPacketHandled(true);
    }
}
