package com.arsenalatomico.net;

import com.arsenalatomico.strike.SolNegro;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Jugador → servidor: las coordenadas escritas en el panel de un silo, y ¡lanzar! */
public class FireSiloPacket {
    public final int silo, x, z;

    public FireSiloPacket(int silo, int x, int z) {
        this.silo = silo;
        this.x = x;
        this.z = z;
    }

    public FireSiloPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readInt(), b.readInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(silo);
        b.writeInt(x);
        b.writeInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) SolNegro.fireSilo(p, silo, x, z);
        ctx.get().setPacketHandled(true);
    }
}
