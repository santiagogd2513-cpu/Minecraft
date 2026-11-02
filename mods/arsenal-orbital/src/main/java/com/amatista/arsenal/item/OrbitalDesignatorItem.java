package com.amatista.arsenal.item;

import com.amatista.arsenal.strike.OrbitalStrikeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Designador orbital: mantén pulsado (2 s) apuntando a un bloque y suelta para llamar
 * al satélite ÉGIDA-7.
 */
public class OrbitalDesignatorItem extends Item {
    /** Ticks que hay que mantener pulsado para fijar el objetivo. */
    public static final int LOCK_TICKS = 40;

    public OrbitalDesignatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && OrbitalStrikeManager.isBusy(player)) {
            player.displayClientMessage(Component.translatable("message.arsenalorbital.busy").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (used < LOCK_TICKS) return;
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            OrbitalStrikeManager.tryStart(player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.arsenalorbital.orbital_designator.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.arsenalorbital.orbital_designator.tip2").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
