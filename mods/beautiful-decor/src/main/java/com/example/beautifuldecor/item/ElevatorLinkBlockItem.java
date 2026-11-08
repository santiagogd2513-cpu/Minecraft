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
 * Pantalla o puerta de ascensor (V2.2.0): se vincula haciendo clic derecho con ella en la mano sobre un controlador.
 * Todo el montón de ítems guarda el vínculo, así que se pueden colocar de seguido (por ejemplo, las 9 piezas de una puerta).
 * Mientras tiene vínculo brilla como un ítem encantado.
 */
public class ElevatorLinkBlockItem extends BlockItem {
    private static final String TAG = "ElevatorController";

    public ElevatorLinkBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static void setLink(ItemStack stack, Level level, BlockPos controller) {
        stack.getOrCreateTag().putLong(TAG, controller.asLong());
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
            tooltip.add(Component.translatable("tooltip.beautifuldecor.elevator.linked",
                    link.getX(), link.getY(), link.getZ()));
        } else {
            tooltip.add(Component.translatable("tooltip.beautifuldecor.elevator.link_hint"));
        }
    }
}
