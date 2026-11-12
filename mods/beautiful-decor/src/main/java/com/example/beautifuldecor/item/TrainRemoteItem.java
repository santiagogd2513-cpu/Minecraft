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
        stack.getOrCreateTag().putUUID("Train", id);
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
