package com.arsenalatomico.net;

import com.arsenalatomico.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugador que disparó: empieza la cinemática (con todo lo necesario para repetir el plan). */
public class StartCinematicPacket {
    public final WeaponType type;
    public final BlockPos target;
    public final Vec3 site;
    public final float yaw;
    /** 0 = en tierra, 1 = en el mar (submarino). */
    public final int mode;
    public final long seed;
    public final int ticks;

    public StartCinematicPacket(WeaponType type, BlockPos target, Vec3 site, float yaw, int mode, long seed, int ticks) {
        this.type = type;
        this.target = target;
        this.site = site;
        this.yaw = yaw;
        this.mode = mode;
        this.seed = seed;
        this.ticks = ticks;
    }

    public StartCinematicPacket(FriendlyByteBuf b) {
        this(WeaponType.byOrdinal(b.readVarInt()), b.readBlockPos(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                b.readFloat(), b.readVarInt(), b.readLong(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(type.ordinal());
        b.writeBlockPos(target);
        b.writeDouble(site.x);
        b.writeDouble(site.y);
        b.writeDouble(site.z);
        b.writeFloat(yaw);
        b.writeVarInt(mode);
        b.writeLong(seed);
        b.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.Cinematic.start(this));
        ctx.get().setPacketHandled(true);
    }
}
