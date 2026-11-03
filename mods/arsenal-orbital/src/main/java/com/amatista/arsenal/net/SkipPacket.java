package com.amatista.arsenal.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador omitió la cinemática; el rayo cae ya. */
public class SkipPacket {
    public SkipPacket() {}

    public SkipPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // La cinemática ya no se puede omitir: se ignora.
        ctx.get().setPacketHandled(true);
    }
}
