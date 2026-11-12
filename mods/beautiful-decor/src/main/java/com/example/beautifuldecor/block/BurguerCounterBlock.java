package com.example.beautifuldecor.block;

import com.example.beautifuldecor.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** V5.3.0: mostrador de Burguer Reino. Clic derecho con una esmeralda saca una Hamburguesa Reina. */
public class BurguerCounterBlock extends ModBlock {
    public BurguerCounterBlock(BlockBehaviour.Properties properties) {
        super(properties, box(0, 0, 2, 16, 13, 15));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.EMERALD)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.burguer_counter_price"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            ItemStack burger = new ItemStack(ModItems.BURGUER_ROYAL.get());
            if (!player.getInventory().add(burger)) {
                player.drop(burger, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
