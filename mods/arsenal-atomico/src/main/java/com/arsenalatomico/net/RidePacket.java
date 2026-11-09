package com.arsenalatomico.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador: empieza una parte del viaje en el camión (0 = salida, 1 = llegada a la base). */
public class RidePacket {
    public final int truck, phase, ticks;
    public final Vec3 base;
    public final float yaw;
    public final BlockPos target;

    public RidePacket(int truck, int phase, int ticks, Vec3 base, float yaw, BlockPos target) {
        this.truck = truck;
        this.phase = phase;
        this.ticks = ticks;
        this.base = base;
        this.yaw = yaw;
        this.target = target;
    }

    public RidePacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarInt(), b.readVarInt(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readBlockPos());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(truck);
        b.writeVarInt(phase);
        b.writeVarInt(ticks);
        b.writeDouble(base.x);
        b.writeDouble(base.y);
        b.writeDouble(base.z);
        b.writeFloat(yaw);
        b.writeBlockPos(target);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.Cinematic.startRide(this));
        ctx.get().setPacketHandled(true);
    }
}
