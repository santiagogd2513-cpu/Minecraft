package com.example.beautifuldecor.block;

import com.example.beautifuldecor.item.LotteryTicketItem;
import com.example.beautifuldecor.lottery.Lottery;
import com.example.beautifuldecor.lottery.LotteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

/** V6.15.0: puesto de lotería. Clic derecho con 4 lingotes de hierro vende un boleto al azar (máximo 20 por sorteo y jugador); con otra cosa muestra el precio y cuánto falta. */
public class LotteryBoothBlock extends ModBlock {
    public LotteryBoothBlock(BlockBehaviour.Properties properties) {
        super(properties, box(0, 0, 1, 16, 16, 15));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack held = player.getItemInHand(hand);
        int left = Lottery.ticksLeft(sp.server);
        if (!held.is(Items.IRON_INGOT) || held.getCount() < Lottery.PRICE_IRON) {
            player.displayClientMessage(Component.translatable("message.beautifuldecor.lottery_booth_price", Lottery.PRICE_IRON, Math.max(1, left / 1200)), true);
            return InteractionResult.CONSUME;
        }
        LotteryData.Ticket t = Lottery.sell(sp);
        if (t == null) {
            player.displayClientMessage(Component.translatable("message.beautifuldecor.lottery_booth_max", LotteryData.MAX_TICKETS), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(Lottery.PRICE_IRON);
        }
        ItemStack ticket = LotteryTicketItem.make(t.number(), t.cls(), t.draw());
        if (!player.getInventory().add(ticket)) {
            player.drop(ticket, false);
        }
        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.9f, 1.2f);
        player.displayClientMessage(Component.translatable("message.beautifuldecor.lottery_bought", String.format("%03d", t.number()),
                String.valueOf((char) ('A' + t.cls())), t.draw()), true);
        return InteractionResult.CONSUME;
    }
}
