package com.example.beautifuldecor.item;

import com.example.beautifuldecor.entity.TrainCarEntity;
import com.example.beautifuldecor.entity.TrainRoute;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.TrainMenuOpenPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * V6.0.0: Mando de trenes. Clic derecho sobre una locomotora (o la cabeza del metro) la vincula y abre su menú (nombre, automático sí/no,
 * borrar ruta). Con el mando vinculado, clic en un controlador de estación vacío lo agrega como siguiente parada de la ruta; clic en uno
 * que ya está abre sus ajustes. Clic en el aire con el mando abre otra vez el menú del tren vinculado.
 */
public class TrainRemoteItem extends Item {
    public TrainRemoteItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Nullable
    public static UUID linked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID("Train") ? tag.getUUID("Train") : null;
    }

    public static void link(ItemStack stack, UUID id) {
        UUID before = linked(stack);
        if (before != null && !before.equals(id)) {
            stack.getOrCreateTag().remove("Pending");           // las paradas pendientes eran de otro tren
        }
        stack.getOrCreateTag().putUUID("Train", id);
    }

    /** V6.16.2: paradas que el mando guardó porque el tren no estaba cargado (se pasan a la ruta cuando se cargue). */
    public static net.minecraft.nbt.ListTag pending(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new net.minecraft.nbt.ListTag() : tag.getList("Pending", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
    }

    public static void setPending(ItemStack stack, net.minecraft.nbt.ListTag list) {
        if (list.isEmpty()) {
            if (stack.getTag() != null) {
                stack.getTag().remove("Pending");
            }
        } else {
            stack.getOrCreateTag().put("Pending", list);
        }
    }

    /** Pasa a la ruta del tren (ya cargado) las paradas pendientes. Devuelve cuántas se agregaron. */
    public static int applyPending(ServerPlayer player, TrainCarEntity car, ItemStack remote) {
        net.minecraft.nbt.ListTag list = pending(remote);
        if (list.isEmpty()) {
            return 0;
        }
        TrainRoute route = car.route();
        ServerLevel level = (ServerLevel) car.level();
        int added = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            net.minecraft.core.BlockPos ctrl = net.minecraft.core.BlockPos.of(t.getLong("Ctrl"));
            if (route.indexOf(ctrl) >= 0 || route.stops.size() >= TrainRoute.MAX_STOPS) {
                continue;
            }
            if (!route.stops.isEmpty()) {
                net.minecraft.core.BlockPos lastCtrl = route.stops.get(route.stops.size() - 1).ctrl;
                if (level.isLoaded(lastCtrl) && level.getBlockState(lastCtrl).getBlock() instanceof com.example.beautifuldecor.block.StationControllerBlock b && b.isDepot()) {
                    break;                                             // el depósito es la última parada
                }
            }
            TrainRoute.Stop stop = new TrainRoute.Stop(ctrl, net.minecraft.core.BlockPos.of(t.getLong("Rail")));
            stop.name = t.getString("Name");
            stop.dwell = t.getInt("DwellSec");
            stop.depart = t.getInt("Depart");
            stop.travel = t.getInt("Travel");
            stop.open = t.getInt("Open");
            stop.close = t.getInt("Close");
            route.stops.add(stop);
            added++;
        }
        setPending(remote, new net.minecraft.nbt.ListTag());
        if (added > 0) {
            route.invalidate();
            player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
            player.displayClientMessage(Component.translatable("message.beautifuldecor.station.applied", added, route.stops.size()), true);
        }
        return added;
    }

    /** La locomotora vinculada, si está cargada. */
    @Nullable
    public static TrainCarEntity linkedCar(ServerLevel level, ItemStack stack) {
        UUID id = linked(stack);
        if (id == null) {
            return null;
        }
        Entity e = level.getEntity(id);
        return e instanceof TrainCarEntity car && car.isAlive() ? car : null;
    }

    public static void openMenu(ServerPlayer player, TrainCarEntity car) {
        for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof TrainRemoteItem && car.getUUID().equals(linked(held))) {
                applyPending(player, car, held);                      // V6.16.2
            }
        }
        TrainRoute r = car.route();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < r.stops.size(); i++) {
            names.add(r.stops.get(i).label(i));
        }
        ModMessages.sendToPlayer(player, new TrainMenuOpenPacket(car.getId(), r.name, r.on, names));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            TrainCarEntity car = linkedCar(sl, stack);
            if (car != null) {
                openMenu(sp, car);
            } else {
                player.displayClientMessage(Component.translatable(linked(stack) == null ? "message.beautifuldecor.train_remote.unlinked"
                        : "message.beautifuldecor.train_remote.not_loaded"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
