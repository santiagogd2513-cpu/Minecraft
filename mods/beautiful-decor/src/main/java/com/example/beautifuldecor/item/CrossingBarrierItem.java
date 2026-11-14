package com.example.beautifuldecor.item;

import com.example.beautifuldecor.block.CrossingBarrierBlock;
import com.example.beautifuldecor.block.CrossingMechanismBlock;
import com.example.beautifuldecor.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** V7.8.0: la barrera se pone con clic derecho en un lado del mecanismo: pone los 3 tramos hacia afuera. */
public class CrossingBarrierItem extends BlockItem {
    public CrossingBarrierItem(Properties properties) {
        super(ModBlocks.CROSSING_BARRIER.get(), properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos mech = ctx.getClickedPos();
        BlockState ms = level.getBlockState(mech);
        Player player = ctx.getPlayer();
        if (!(ms.getBlock() instanceof CrossingMechanismBlock)) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.barrier_needs_mechanism"), true);
            }
            return InteractionResult.FAIL;
        }
        Direction f = ctx.getClickedFace().getAxis().isHorizontal() ? ctx.getClickedFace() : ms.getValue(CrossingMechanismBlock.FACING);
        Direction old = ms.getValue(CrossingMechanismBlock.FACING);
        if (CrossingMechanismBlock.hasArm(level, mech, old)) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("hud.beautifuldecor.barrier_already"), true);
            }
            return InteractionResult.FAIL;
        }
        for (int i = 1; i <= 3; i++) {
            BlockState s = level.getBlockState(mech.relative(f, i));
            if (!s.canBeReplaced()) {
                if (player != null && !level.isClientSide) {
                    player.displayClientMessage(Component.translatable("hud.beautifuldecor.barrier_no_room"), true);
                }
                return InteractionResult.FAIL;
            }
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(mech, ms.setValue(CrossingMechanismBlock.FACING, f), 3);
        for (int i = 1; i <= 3; i++) {
            level.setBlock(mech.relative(f, i), ModBlocks.CROSSING_BARRIER.get().defaultBlockState()
                    .setValue(CrossingBarrierBlock.FACING, f).setValue(CrossingBarrierBlock.PART, i - 1).setValue(CrossingBarrierBlock.ARM, 0), 3);
        }
        level.playSound(null, mech, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.2f);
        if (player == null || !player.isCreative()) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
