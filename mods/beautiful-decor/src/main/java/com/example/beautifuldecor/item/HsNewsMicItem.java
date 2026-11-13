package com.example.beautifuldecor.item;

import com.example.beautifuldecor.news.NewsEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** V6.11.0: micrófono de HS News. Clic derecho a un jugador (o a cualquier criatura): su "entrevista exclusiva" sale en el noticiero. Espera de 30 segundos. */
public class HsNewsMicItem extends Item {
    private static final int QUOTES = 8;

    public HsNewsMicItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (user.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int q = user.level().random.nextInt(QUOTES);
        NewsEvents.announce(user.level().getServer(), Component.translatable("news.interview", target.getDisplayName(), Component.translatable("news.quote." + q)));
        user.getCooldowns().addCooldown(this, 600);
        user.displayClientMessage(Component.translatable("message.beautifuldecor.hsnews_interview"), true);
        return InteractionResult.CONSUME;
    }
}
