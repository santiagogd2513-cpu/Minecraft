package com.example.beautifuldecor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * V6.15.0: caja registradora, donde se paga. Clic derecho: suena la campanita y se abre el cajón un momento. Si llevas un boleto de lotería en la mano,
 * te dice cuál es el resultado del último sorteo para ese boleto.
 */
public class CashRegisterBlock extends ModBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public CashRegisterBlock(BlockBehaviour.Properties properties) {
        super(properties, box(1, 0, 2, 15, 11, 14));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(OPEN, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(OPEN, Boolean.TRUE), 3);
            level.scheduleTick(pos, this, 30);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 1.6f);
            ItemStack held = player.getItemInHand(hand);
            CompoundTag tag = held.getTag();
            if (tag != null && tag.contains("Number") && level.getServer() != null) {
                var d = com.example.beautifuldecor.lottery.LotteryData.get(level.getServer());
                player.displayClientMessage(describe(tag, d), true);
            } else {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.cash_register"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Component describe(CompoundTag tag, com.example.beautifuldecor.lottery.LotteryData d) {
        int draw = tag.getInt("Draw");
        if (draw >= d.draw) {
            return Component.translatable("message.beautifuldecor.lottery_ticket_waiting", draw);
        }
        if (draw < d.draw - 1 || d.lastNumber < 0) {
            return Component.translatable("message.beautifuldecor.lottery_ticket_old");
        }
        int tier = com.example.beautifuldecor.lottery.Lottery.tier(tag.getInt("Number"), tag.getInt("Class"), d.lastNumber, d.lastClass);
        return tier == 0 ? Component.translatable("message.beautifuldecor.lottery_ticket_lost") : Component.translatable("message.beautifuldecor.lottery_ticket_won." + tier);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, Boolean.FALSE), 3);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 1.6f);
        }
    }
}
