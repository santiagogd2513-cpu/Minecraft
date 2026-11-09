package com.arsenalatomico.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador: abre la pantalla de coordenadas del panel de un silo. */
public class OpenTargetPacket {
    public final int silo, safe;

    public OpenTargetPacket(int silo, int safe) {
        this.silo = silo;
        this.safe = safe;
    }

    public OpenTargetPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(silo);
        b.writeVarInt(safe);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.TargetScreen.open(silo, safe));
        ctx.get().setPacketHandled(true);
    }
}
