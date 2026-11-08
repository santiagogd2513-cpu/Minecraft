package com.example.beautifuldecor.block.entity;

import com.example.beautifuldecor.block.PrinterBlock;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class PrinterBlockEntity extends BlockEntity {
    /** ~29 seconds at 20 tps (matches printer.ogg length) */
    public static final int PRINT_TICKS = 580;

    private int ticksLeft = 0;
    @Nullable
    private UUID ownerUuid = null;

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTER.get(), pos, state);
    }

    public boolean isPrinting() {
        return ticksLeft > 0;
    }

    public void startPrint(Player player) {
        this.ticksLeft = PRINT_TICKS;
        this.ownerUuid = player.getUUID();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.setBlock(worldPosition, getBlockState().setValue(PrinterBlock.PRINTING, true), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrinterBlockEntity be) {
        if (be.ticksLeft <= 0) return;
        be.ticksLeft--;
        if (be.ticksLeft <= 0) {
            be.finishPrint(level, pos);
        }
        be.setChanged();
    }

    private void finishPrint(Level level, BlockPos pos) {
        ticksLeft = 0;
        if (level instanceof ServerLevel serverLevel) {
            level.setBlock(pos, getBlockState().setValue(PrinterBlock.PRINTING, false), 3);

            ItemStack paper = new ItemStack(Items.PAPER, 1);
            Player player = ownerUuid != null ? serverLevel.getPlayerByUUID(ownerUuid) : null;
            if (player != null && player.isAlive() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64) {
                if (!player.getInventory().add(paper)) {
                    player.drop(paper, false);
                }
            } else {
                // drop near output slot (front of printer)
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.3;
                double z = pos.getZ() + 0.5;
                ItemEntity item = new ItemEntity(level, x, y, z, paper);
                level.addFreshEntity(item);
            }
        }
        ownerUuid = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TicksLeft", ticksLeft);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ticksLeft = tag.getInt("TicksLeft");
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
