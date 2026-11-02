package com.amatista.arsenal.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador que disparó: empieza la cinemática. */
public class StartCinematicPacket {
    public final BlockPos target;
    public final int durationTicks;

    public StartCinematicPacket(BlockPos target, int durationTicks) {
        this.target = target;
        this.durationTicks = durationTicks;
    }

    public StartCinematicPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(target);
        buf.writeVarInt(durationTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.amatista.arsenal.client.ClientPacketHandler.startCinematic(this));
        ctx.get().setPacketHandled(true);
    }
}
