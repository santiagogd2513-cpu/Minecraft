package com.arsenalatomico.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → jugadores: dónde está la Base Sol Negro (para dibujar el misil esperando en su silo). */
public class BasePacket {
    public final Vec3 site;
    public final float yaw;

    public BasePacket(Vec3 site, float yaw) {
        this.site = site;
        this.yaw = yaw;
    }

    public BasePacket(FriendlyByteBuf b) {
        this(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(site.x);
        b.writeDouble(site.y);
        b.writeDouble(site.z);
        b.writeFloat(yaw);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.NukeRenderer.standby(site, yaw));
        ctx.get().setPacketHandled(true);
    }
}
