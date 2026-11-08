package com.arsenalatomico.item;

import com.arsenalatomico.strike.LaunchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Marcador de objetivos: clic derecho (o un toque) apuntando a un bloque lanza el arma elegida. */
public class TargetMarkerItem extends Item {
    public TargetMarkerItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) LaunchManager.tryStart(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.arsenalatomico.target_marker.tip1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.arsenalatomico.target_marker.tip2").withStyle(ChatFormatting.GOLD));
    }
}
