package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.TelevisionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TelevisionPathPacket {
    /** Largo máximo del enlace (los enlaces con firma pueden ser largos). */
    public static final int MAX_PATH = 1024;

    private final BlockPos pos;
    private final String path;
    private final boolean play;

    public TelevisionPathPacket(BlockPos pos, String path, boolean play) {
        this.pos = pos;
        this.path = path == null ? "" : path;
        this.play = play;
    }

    public TelevisionPathPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.path = buf.readUtf(MAX_PATH);
        this.play = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(path, MAX_PATH);
        buf.writeBoolean(play);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            TelevisionBlock.applyPath(player.level(), pos, path, play);
        });
        ctx.get().setPacketHandled(true);
    }
}
