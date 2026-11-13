package com.example.beautifuldecor.item;

import com.example.beautifuldecor.init.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * V7.2.0: audífonos de Spotifly. Clic derecho: canción 1 → 2 → 3 → apagado; solo los oye quien los usa y la música lo sigue.
 * Agachado + clic derecho: los apaga.
 */
public class SpotiflyHeadphonesItem extends Item {
    public SpotiflyHeadphonesItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            for (int i = 1; i <= 3; i++) {
                sp.connection.send(new ClientboundStopSoundPacket(ModSounds.song(i).getLocation(), SoundSource.RECORDS));
            }
            int cur = stack.getOrCreateTag().getInt("Song");
            int next = player.isShiftKeyDown() ? 0 : (cur + 1) % 4;
            stack.getOrCreateTag().putInt("Song", next);
            if (next > 0) {
                sp.connection.send(new ClientboundSoundEntityPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.song(next)), SoundSource.RECORDS, sp, 1.0f, 1.0f, level.random.nextLong()));
                player.displayClientMessage(Component.translatable("message.beautifuldecor.phones_song", next), true);
            } else {
                player.displayClientMessage(Component.translatable("message.beautifuldecor.phones_off"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
