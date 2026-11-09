package com.arsenalatomico.net;

import com.arsenalatomico.strike.SolNegro;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Jugador → servidor: oprimió el botón rojo de la consola. */
public class FirePacket {
    public FirePacket() {}

    public FirePacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) SolNegro.fire(p);
        ctx.get().setPacketHandled(true);
    }
}
