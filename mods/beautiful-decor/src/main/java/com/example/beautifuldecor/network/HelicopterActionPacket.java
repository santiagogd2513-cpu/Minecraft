package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.HelicopterEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V3.0.0: quien va en el helicóptero pide bajarse (tecla Z). El servidor comprueba que esté cerca del suelo. */
public class HelicopterActionPacket {
    public static final int ACTION_EXIT = 0;

    private final int entityId;
    private final int action;

    public HelicopterActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static HelicopterActionPacket decode(FriendlyByteBuf buffer) {
        return new HelicopterActionPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.action);
    }

    public static void handle(HelicopterActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // Solo vale para el helicóptero en el que de verdad vas montado.
            if (player.getVehicle() instanceof HelicopterEntity helicopter && helicopter.getId() == packet.entityId
                    && packet.action == ACTION_EXIT) {
                helicopter.requestExit(player);
            }
        });
        context.setPacketHandled(true);
    }
}
