package com.example.beautifuldecor.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * V3.9.0: lo común del almacenamiento de los vehículos: el contenedor, guardarlo y leerlo, soltarlo al recoger o destruir el vehículo,
 * y abrir la ventana (la misma de un cofre, de 1 a 6 filas).
 */
public final class VehicleStorage {
    public static final String TAG = "Storage";

    private VehicleStorage() {}

    /** Las casillas del vehículo. Solo vale mientras el vehículo exista y quien lo mira vaya montado en él. */
    public static final class Box extends SimpleContainer {
        private final Entity owner;

        public Box(Entity owner, int rows) {
            super(rows * 9);
            this.owner = owner;
        }

        @Override
        public boolean stillValid(Player player) {
            return !this.owner.isRemoved() && player.getVehicle() == this.owner;
        }
    }

    public static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static void save(@Nullable SimpleContainer box, CompoundTag tag) {
        if (box == null) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(box.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, box.getItem(i));
        }
        CompoundTag sub = new CompoundTag();
        ContainerHelper.saveAllItems(sub, items);
        tag.put(TAG, sub);
    }

    public static void load(@Nullable SimpleContainer box, CompoundTag tag) {
        if (box == null || !tag.contains(TAG, 10)) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(box.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound(TAG), items);
        for (int i = 0; i < items.size(); i++) {
            box.setItem(i, items.get(i));
        }
    }

    /** Suelta todo lo que lleva (al recoger el vehículo, o si lo destruyen). Deja el almacenamiento vacío. */
    public static void drop(Entity vehicle, @Nullable SimpleContainer box) {
        if (box == null || vehicle.level().isClientSide) {
            return;
        }
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack stack = box.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                vehicle.spawnAtLocation(stack);
            }
        }
    }

    /** Servidor: abre la ventana del almacenamiento a quien va montado en el vehículo. */
    public static void open(ServerPlayer player, HasVehicleStorage vehicle) {
        SimpleContainer box = vehicle.getStorage();
        int rows = vehicle.storageRows();
        if (box == null || rows <= 0) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, who) -> new ChestMenu(menuType(rows), id, inventory, box, rows), vehicle.storageTitle()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.5f, 1.1f);
    }
}
