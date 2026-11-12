package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.block.FreightRailBlock;
import com.example.beautifuldecor.block.WideCurves;
import com.example.beautifuldecor.block.WideRailBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V6.1.0: clic derecho en la CARA DE ARRIBA de una curva (o de un pedazo de curva ancha) con un riel de la misma vía en la mano la ensancha un
 * nivel; agachado, la achica un nivel. (En la cara de arriba de un riel no se puede poner otro, así que no estorba al construir.)
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WideCurveEvents {
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getFace() != Direction.UP) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof FreightRailBlock railItem)) {
            return;
        }
        Block target = event.getLevel().getBlockState(event.getPos()).getBlock();
        boolean curveTarget = target instanceof FreightRailBlock || target instanceof WideRailBlock;
        if (!curveTarget) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            if (WideCurves.use(level, player, event.getPos(), railItem.family(), player.isShiftKeyDown())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } else if (event.getLevel().isClientSide) {
            // en el cliente se corta igual si es una curva, para que no intente poner el riel; el servidor decide
            Block b = target;
            boolean curve = b instanceof WideRailBlock || (b instanceof FreightRailBlock
                    && com.example.beautifuldecor.block.FreightRailBlock.pathShape(event.getLevel().getBlockState(event.getPos())).curve);
            if (curve) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
