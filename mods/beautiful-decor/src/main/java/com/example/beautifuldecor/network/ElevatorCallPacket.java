package com.example.beautifuldecor.network;

import com.example.beautifuldecor.block.entity.ElevatorDisplayBlockEntity;
import com.example.beautifuldecor.elevator.ElevatorLines;
import com.example.beautifuldecor.elevator.ElevatorShaft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V2.2.0, cliente a servidor: se eligió un piso en la pantalla; la cabina viaja al controlador {@code target}. */
public class ElevatorCallPacket {
    private final BlockPos displayPos;
    private final BlockPos target;

    public ElevatorCallPacket(BlockPos displayPos, BlockPos target) {
        this.displayPos = displayPos;
        this.target = target;
    }

    public static ElevatorCallPacket decode(FriendlyByteBuf buf) {
        return new ElevatorCallPacket(buf.readBlockPos(), buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.displayPos);
        buf.writeBlockPos(this.target);
    }

    public static void handle(ElevatorCallPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos display = packet.displayPos;
            // Solo desde una pantalla cercana, vinculada a un controlador de la misma columna que el piso pedido.
            if (!level.isLoaded(display) || player.distanceToSqr(display.getX() + 0.5, display.getY() + 0.5,
                    display.getZ() + 0.5) > 100.0) {
                return;
            }
            if (!(level.getBlockEntity(display) instanceof ElevatorDisplayBlockEntity be) || be.getController() == null) {
                return;
            }
            BlockPos controller = be.getController();
            // V3.4.0: su columna o una línea de lado (misma altura, misma X o Z, cerca)
            int axis = ElevatorShaft.lineAxis(controller, packet.target);
            if (axis == ElevatorLines.NO_LINE
                    || !ElevatorLines.inRange(axis, controller.getX(), controller.getZ(), packet.target.getX(), packet.target.getZ())) {
                return;
            }
            ElevatorShaft.requestTrip(level, packet.target, controller, player);
        });
        context.setPacketHandled(true);
    }
}
