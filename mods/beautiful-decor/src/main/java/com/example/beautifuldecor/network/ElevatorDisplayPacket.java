package com.example.beautifuldecor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** V2.2.0, servidor a cliente: la lista de pisos de un ascensor para la pantalla (de abajo hacia arriba). */
public class ElevatorDisplayPacket {
    /**
     * Un piso: la posición de su controlador, su nombre, si la cabina está parada ahí y (V3.4.0) el eje: 1 = piso de la columna (arriba-abajo),
     * 0 = parada de lado en X (este-oeste), 2 = parada de lado en Z (norte-sur).
     */
    public record FloorEntry(BlockPos pos, String name, boolean cabinHere, int axis) {
        public FloorEntry(BlockPos pos, String name, boolean cabinHere) {
            this(pos, name, cabinHere, 1);
        }
    }

    public final BlockPos displayPos;
    public final List<FloorEntry> floors;
    /** Índice del piso de esta pantalla en {@link #floors} (-1 si no se encontró). */
    public final int ownFloor;
    public final boolean moving;

    public ElevatorDisplayPacket(BlockPos displayPos, List<FloorEntry> floors, int ownFloor, boolean moving) {
        this.displayPos = displayPos;
        this.floors = floors;
        this.ownFloor = ownFloor;
        this.moving = moving;
    }

    public static ElevatorDisplayPacket decode(FriendlyByteBuf buf) {
        BlockPos displayPos = buf.readBlockPos();
        int own = buf.readVarInt() - 1;
        boolean moving = buf.readBoolean();
        int count = buf.readVarInt();
        List<FloorEntry> floors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            floors.add(new FloorEntry(buf.readBlockPos(), buf.readUtf(64), buf.readBoolean(), buf.readByte()));
        }
        return new ElevatorDisplayPacket(displayPos, floors, own, moving);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.displayPos);
        buf.writeVarInt(this.ownFloor + 1);
        buf.writeBoolean(this.moving);
        buf.writeVarInt(this.floors.size());
        for (FloorEntry floor : this.floors) {
            buf.writeBlockPos(floor.pos());
            buf.writeUtf(floor.name(), 64);
            buf.writeBoolean(floor.cabinHere());
            buf.writeByte(floor.axis());
        }
    }

    public static void handle(ElevatorDisplayPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.beautifuldecor.client.ClientHooks.openElevatorDisplay(packet)));
        context.setPacketHandled(true);
    }
}
