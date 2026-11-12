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

/** V5.2.0: minibar de Rojo Toro. Clic derecho con una esmeralda saca una lata (la esmeralda se gasta). */
public class RojoToroMinibarBlock extends ModBlock {
    public RojoToroMinibarBlock(BlockBehaviour.Properties properties) {
        super(properties, box(1, 0, 2, 15, 14, 14));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.EMERALD)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.rojotoro_minibar_price"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            ItemStack can = new ItemStack(ModItems.ROJOTORO_CAN.get());
            if (!player.getInventory().add(can)) {
                player.drop(can, false);
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.3f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
