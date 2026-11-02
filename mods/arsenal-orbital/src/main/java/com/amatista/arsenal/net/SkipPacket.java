package com.amatista.arsenal.net;

import com.amatista.arsenal.strike.OrbitalStrikeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador omitió la cinemática; el rayo cae ya. */
public class SkipPacket {
    public SkipPacket() {}

    public SkipPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) OrbitalStrikeManager.skip(player);
        ctx.get().setPacketHandled(true);
    }
}
