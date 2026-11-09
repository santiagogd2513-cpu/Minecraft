package com.example.beautifuldecor.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * V4.2.0: bafle de la radio de mesa. Con él en la mano, clic derecho sobre una radio lo vincula (todo el montón guarda el vínculo);
 * al ponerlo, suena lo que tenga puesto esa radio aunque esté lejos de ella. Mientras tiene vínculo brilla.
 * (Para poner un bafle sobre un bloque, y no vincular, se hace con el agachado, como cualquier ítem.)
 */
public class RadioSpeakerItem extends BlockItem {
    private static final String TAG_POS = "Radio";
    private static final String TAG_DIM = "RadioDim";

    public RadioSpeakerItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static void setLink(ItemStack stack, BlockPos radio, ResourceLocation dimension) {
        stack.getOrCreateTag().putLong(TAG_POS, radio.asLong());
        stack.getOrCreateTag().putString(TAG_DIM, dimension.toString());
    }

    @Nullable
    public static BlockPos getLink(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TAG_POS, Tag.TAG_LONG)) {
            return BlockPos.of(stack.getTag().getLong(TAG_POS));
        }
        return null;
    }

    /** La dimensión de la radio vinculada, o null si no hay vínculo. */
    @Nullable
    public static ResourceLocation getLinkDimension(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TAG_DIM, Tag.TAG_STRING)) {
            return ResourceLocation.tryParse(stack.getTag().getString(TAG_DIM));
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
            tooltip.add(Component.translatable("tooltip.beautifuldecor.radio.speaker_linked", link.getX(), link.getY(), link.getZ()));
        } else {
            tooltip.add(Component.translatable("tooltip.beautifuldecor.radio.speaker_hint"));
        }
    }
}
