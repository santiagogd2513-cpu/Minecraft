package com.example.beautifuldecor.network;

import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.entity.TrainRoute;
import com.example.beautifuldecor.item.TrainRemoteItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** V6.0.0, cliente a servidor: el menú del tren guarda el nombre y lo que se pidió (0 = solo guardar, 1 = activar automático, 2 = apagarlo, 3 = borrar la ruta). */
public class TrainMenuSavePacket {
    private final int entityId;
    private final String name;
    private final int action;

    public TrainMenuSavePacket(int entityId, String name, int action) {
        this.entityId = entityId;
        this.name = name;
        this.action = action;
    }

    public static TrainMenuSavePacket decode(FriendlyByteBuf buf) {
        return new TrainMenuSavePacket(buf.readVarInt(), buf.readUtf(32), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.name, 32);
        buf.writeByte(this.action);
    }

    public static void handle(TrainMenuSavePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            Entity e = player.level().getEntity(packet.entityId);
            if (!(e instanceof TrainCarEntity car) || !car.variant().isLoco()) {
                return;
            }
            // solo con el mando vinculado a este tren en la mano
            boolean holds = false;
            for (ItemStack s : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (s.getItem() instanceof TrainRemoteItem && car.getUUID().equals(TrainRemoteItem.linked(s))) {
                    holds = true;
                }
            }
            if (!holds) {
                return;
            }
            TrainRoute route = car.route();
            String name = packet.name == null ? "" : packet.name.trim();
            route.name = name.substring(0, Math.min(name.length(), 24));
            if (!route.name.isEmpty()) {
                car.setCustomName(Component.literal(route.name));
            } else {
                car.setCustomName(null);
            }
            switch (packet.action) {
                case 1 -> {
                    if (route.stops.isEmpty()) {
                        player.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.no_route"), true);
                    } else {
                        route.start();
                        player.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.auto_on", route.name), true);
                    }
                }
                case 2 -> {
                    route.stop();
                    player.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.auto_off", route.name), true);
                }
                case 3 -> {
                    route.clear();
                    player.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.cleared", route.name), true);
                }
                default -> player.displayClientMessage(Component.translatable("message.beautifuldecor.train_remote.saved", route.name), true);
            }
        });
        context.setPacketHandled(true);
    }
}
