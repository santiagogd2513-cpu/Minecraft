package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.AirlinerEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * V3.8.0: acciones de quien va en el avión: bajarse (tecla Z; el servidor comprueba que esté cerca del suelo) y recoger o bajar el
 * tren de aterrizaje (tecla F; solo vale si es el piloto y el avión está en el aire para recogerlo).
 */
public class AirlinerActionPacket {
    public static final int ACTION_EXIT = 0;
    public static final int ACTION_GEAR = 1;

    private final int entityId;
    private final int action;

    public AirlinerActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static AirlinerActionPacket decode(FriendlyByteBuf buffer) {
        return new AirlinerActionPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeVarInt(this.action);
    }

    public static void handle(AirlinerActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // Solo vale para el avión en el que de verdad vas montado.
            if (player.getVehicle() instanceof AirlinerEntity airliner && airliner.getId() == packet.entityId) {
                if (packet.action == ACTION_EXIT) {
                    airliner.requestExit(player);
                } else if (packet.action == ACTION_GEAR) {
                    airliner.requestGear(player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
