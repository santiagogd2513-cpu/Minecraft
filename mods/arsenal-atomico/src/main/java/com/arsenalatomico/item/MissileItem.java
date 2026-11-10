package com.arsenalatomico.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Un misil nuclear del depósito (chiquito o mediano). Se carga en el camión (clic derecho al camión con
 * el misil en la mano) y se mete en un silo vacío de la Base Sol Negro (clic derecho a su panel).
 */
public class MissileItem extends Item {
    public final boolean medium;

    public MissileItem(Properties p, boolean medium) {
        super(p);
        this.medium = medium;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(medium ? "item.arsenalatomico.missile_medium.tip" : "item.arsenalatomico.missile_small.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
