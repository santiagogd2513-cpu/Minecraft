package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.ModBlock;
import com.example.beautifuldecor.entity.SeatEntity;
import com.example.beautifuldecor.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A validated left/right turn request sent while the player rides the office chair. */
public class OfficeChairTurnPacket {
    private final int turn;

    public OfficeChairTurnPacket(int turn) {
        this.turn = turn < 0 ? -1 : 1;
    }

    public static OfficeChairTurnPacket decode(FriendlyByteBuf buffer) {
        return new OfficeChairTurnPacket(buffer.readInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(turn);
    }

    public static void handle(OfficeChairTurnPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.getVehicle() instanceof SeatEntity seat)) {
                return;
            }

            BlockPos anchor = seat.getAnchorPos();
            if (anchor == null) {
                return;
            }

            BlockState state = player.serverLevel().getBlockState(anchor);
            if (!state.is(ModBlocks.OFFICE_CHAIR.get())) {
                return;
            }

            Direction facing = state.getValue(ModBlock.FACING);
            Direction next = packet.turn < 0 ? facing.getCounterClockWise() : facing.getClockWise();
            player.serverLevel().setBlock(anchor, state.setValue(ModBlock.FACING, next), 3);
        });
        context.setPacketHandled(true);
    }
}
