package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** V6.7.0: un jugador esposado no puede usar objetos, minar, colocar bloques, interactuar ni atacar. La marca vive en los datos persistentes del jugador. */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PoliceEvents {
    public static final String CUFFED = "beautifuldecor_cuffed";

    public static boolean isCuffed(Player p) {
        return p.getPersistentData().getBoolean(CUFFED);
    }

    public static void setCuffed(Player p, boolean on) {
        if (on) {
            p.getPersistentData().putBoolean(CUFFED, true);
        } else {
            p.getPersistentData().remove(CUFFED);
        }
    }

    @SubscribeEvent
    public static void onUse(LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof Player p && isCuffed(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightItem(PlayerInteractEvent.RightClickItem e) {
        if (isCuffed(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightBlock(PlayerInteractEvent.RightClickBlock e) {
        if (isCuffed(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (isCuffed(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntity(PlayerInteractEvent.EntityInteract e) {
        if (isCuffed(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (isCuffed(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (isCuffed(e.getPlayer())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && isCuffed(p)) {
            e.setCanceled(true);
        }
    }

    /** Las esposas siguen puestas después de morir y volver a aparecer, hasta que otro jugador las quite. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.getBoolean(CUFFED)) {
            e.getEntity().getPersistentData().putBoolean(CUFFED, true);
        }
    }
}
