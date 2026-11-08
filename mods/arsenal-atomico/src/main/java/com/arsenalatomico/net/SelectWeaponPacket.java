package com.arsenalatomico.net;

import com.arsenalatomico.WeaponType;
import com.arsenalatomico.strike.LaunchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: arma elegida en el menú (J). Servidor → cliente: la guardada al entrar. */
public class SelectWeaponPacket {
    public final WeaponType type;

    public SelectWeaponPacket(WeaponType type) {
        this.type = type;
    }

    public SelectWeaponPacket(FriendlyByteBuf b) {
        this(WeaponType.byOrdinal(b.readVarInt()));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(type.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) LaunchManager.setSelected(p, type);
        else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.arsenalatomico.client.ClientState.selected = type);
        ctx.get().setPacketHandled(true);
    }
}
