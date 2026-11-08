package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.GarageColors;
import com.example.beautifuldecor.block.GarageDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2.3.0, cliente a servidor: el menú de la puerta de garaje guarda los tres colores (puerta, franja y marco). */
public class GarageColorPacket {
    private final BlockPos pos;
    private final int[] colors;

    public GarageColorPacket(BlockPos pos, int[] colors) {
        this.pos = pos;
        this.colors = colors.clone();
    }

    public static GarageColorPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int[] colors = new int[GarageColors.SLOTS];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = buf.readInt() & 0xFFFFFF;
        }
        return new GarageColorPacket(pos, colors);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        for (int color : this.colors) {
            buf.writeInt(color);
        }
    }

    public static void handle(GarageColorPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!level.isLoaded(packet.pos) || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 400.0) {
                return;
            }
            GarageDoorBlock.applyColors(level, packet.pos, packet.colors);
        });
        context.setPacketHandled(true);
    }
}
