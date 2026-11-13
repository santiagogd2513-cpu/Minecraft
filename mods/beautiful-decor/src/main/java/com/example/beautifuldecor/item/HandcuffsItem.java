package com.example.beautifuldecor.item;

import com.example.beautifuldecor.event.PoliceEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** V6.7.0: esposas de la Polisía Nasional. Clic derecho a un jugador lo esposa (no puede usar herramientas, minar ni colocar); otro clic lo suelta. */
public class HandcuffsItem extends Item {
    public HandcuffsItem() {
        super(new Item.Properties().stacksTo(8));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (user.level().isClientSide) {
            return target instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(target instanceof ServerPlayer victim) || !(user instanceof ServerPlayer cop)) {
            return InteractionResult.PASS;
        }
        boolean now = !PoliceEvents.isCuffed(victim);
        PoliceEvents.setCuffed(victim, now);
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
        victim.displayClientMessage(Component.translatable(now ? "message.beautifuldecor.cuffed" : "message.beautifuldecor.uncuffed"), true);
        cop.displayClientMessage(Component.translatable(now ? "message.beautifuldecor.cuffed_other" : "message.beautifuldecor.uncuffed_other", victim.getDisplayName()), true);
        return InteractionResult.CONSUME;
    }
}
