package com.amatista.arsenal.net;

import com.amatista.arsenal.StrikeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugadores cercanos: mostrar el rayo orbital cayendo sobre un punto. */
public class BeamPacket {
    public final BlockPos target;
    /** Ticks hasta el impacto. */
    public final int ticksToImpact;
    /** Rayo del satélite o Fobos cayendo. */
    public final StrikeType type;

    public BeamPacket(BlockPos target, int ticksToImpact, StrikeType type) {
        this.target = target;
        this.ticksToImpact = ticksToImpact;
        this.type = type;
    }

    public BeamPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readVarInt(), StrikeType.byOrdinal(buf.readVarInt()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(target);
        buf.writeVarInt(ticksToImpact);
        buf.writeVarInt(type.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.amatista.arsenal.client.ClientPacketHandler.beam(this));
        ctx.get().setPacketHandled(true);
    }
}
