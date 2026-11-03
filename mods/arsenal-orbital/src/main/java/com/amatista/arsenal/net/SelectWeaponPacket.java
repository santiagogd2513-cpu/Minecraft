package com.amatista.arsenal.net;

import com.amatista.arsenal.StrikeType;
import com.amatista.arsenal.strike.OrbitalStrikeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: arma elegida en el menú (H). Servidor → cliente: arma guardada al entrar. */
public class SelectWeaponPacket {
    public final StrikeType type;

    public SelectWeaponPacket(StrikeType type) {
        this.type = type;
    }

    public SelectWeaponPacket(FriendlyByteBuf buf) {
        this(StrikeType.byOrdinal(buf.readVarInt()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(type.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            OrbitalStrikeManager.setSelected(player, type);
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.amatista.arsenal.client.ClientPacketHandler.selected(this));
        }
        ctx.get().setPacketHandled(true);
    }
}
