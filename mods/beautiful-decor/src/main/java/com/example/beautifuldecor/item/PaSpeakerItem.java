package com.example.beautifuldecor.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * V4.0.0: bocina de megafonía. Haciendo clic derecho con ella en la mano sobre un micrófono se vincula (todo el montón guarda el vínculo);
 * al ponerla queda lista, ya enlazada al micrófono. Mientras tiene vínculo brilla como un ítem encantado.
 */
public class PaSpeakerItem extends BlockItem {
    private static final String TAG = "Microphone";

    public PaSpeakerItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static void setLink(ItemStack stack, BlockPos microphone) {
        stack.getOrCreateTag().putLong(TAG, microphone.asLong());
    }

    @Nullable
    public static BlockPos getLink(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TAG, Tag.TAG_LONG)) {
            return BlockPos.of(stack.getTag().getLong(TAG));
        }
        return null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getLink(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlockPos link = getLink(stack);
        if (link != null) {
            tooltip.add(Component.translatable("tooltip.beautifuldecor.pa.speaker_linked", link.getX(), link.getY(), link.getZ()));
        } else {
            tooltip.add(Component.translatable("tooltip.beautifuldecor.pa.speaker_hint"));
        }
    }
}
