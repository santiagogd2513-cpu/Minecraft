package com.example.beautifuldecor.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** V6.15.0: boleto de la lotería. Guarda su número (000-999), su clase (A-H) y el sorteo; el servidor ya lo registró a nombre del que lo compró. */
public class LotteryTicketItem extends Item {
    public LotteryTicketItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack make(int number, int cls, int draw) {
        ItemStack stack = new ItemStack(com.example.beautifuldecor.init.ModItems.LOTTERY_TICKET.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("Number", number);
        tag.putInt("Class", cls);
        tag.putInt("Draw", draw);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Number")) {
            return;
        }
        tooltip.add(Component.translatable("item.beautifuldecor.lottery_ticket.info", String.format("%03d", tag.getInt("Number")),
                String.valueOf((char) ('A' + tag.getInt("Class"))), tag.getInt("Draw")).withStyle(ChatFormatting.GOLD));
    }
}
