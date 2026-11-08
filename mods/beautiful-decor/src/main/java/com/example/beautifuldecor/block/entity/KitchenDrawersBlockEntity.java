package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.KitchenDrawersBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Almacenamiento de los cajones de cocina (V2.1.0): 27 espacios (3 filas, como un barril). Mientras alguien lo tiene
 * abierto el bloque pasa a OPEN = true y se ven los cajones sacados; al cerrarse el último, vuelven a entrar.
 */
public class KitchenDrawersBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            KitchenDrawersBlockEntity.this.playSound(state, SoundEvents.BARREL_OPEN, 1.3f);
            KitchenDrawersBlockEntity.this.updateOpenState(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            KitchenDrawersBlockEntity.this.playSound(state, SoundEvents.BARREL_CLOSE, 1.3f);
            KitchenDrawersBlockEntity.this.updateOpenState(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            if (player.containerMenu instanceof ChestMenu chest) {
                Container container = chest.getContainer();
                return container == KitchenDrawersBlockEntity.this;
            }
            return false;
        }
    };

    public KitchenDrawersBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KITCHEN_DRAWERS.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items);
        }
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.beautifuldecor.kitchen_drawers");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.incrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!this.remove && !player.isSpectator()) {
            this.openersCounter.decrementOpeners(player, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    /** Lo llama el tick programado del bloque: cierra los cajones si ya nadie lo tiene abierto. */
    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    private void updateOpenState(BlockState state, boolean open) {
        if (this.level != null) {
            this.level.setBlock(this.getBlockPos(), state.setValue(KitchenDrawersBlock.OPEN, open), 3);
        }
    }

    private void playSound(BlockState state, SoundEvent sound, float pitch) {
        if (this.level == null) {
            return;
        }
        Direction facing = state.getValue(KitchenDrawersBlock.FACING);
        double x = this.worldPosition.getX() + 0.5 + facing.getStepX() / 2.0;
        double y = this.worldPosition.getY() + 0.5 + facing.getStepY() / 2.0;
        double z = this.worldPosition.getZ() + 0.5 + facing.getStepZ() / 2.0;
        this.level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5f,
                pitch + this.level.random.nextFloat() * 0.1f);
    }
}
