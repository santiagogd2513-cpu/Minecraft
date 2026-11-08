package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.RcCarEntity;
import com.example.beautifuldecor.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** V3.1.0: lo mismo que DroneInteractionEvents pero para el carro RC (mientras se maneja no se pica, pone ni ataca nada). */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RcCarInteractionEvents {
    private RcCarInteractionEvents() {}

    private static boolean isPilot(Player player) {
        return player != null && !player.level().isClientSide && RcCarEntity.isPiloting(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockAttack(AttackEntityEvent event) {
        if (isPilot(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockBreak(BlockEvent.BreakEvent event) {
        if (isPilot(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && isPilot(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Control remoto del carro sobre la entidad ya encendida: empieza a manejarla (o la suelta si ya la manejas).
     * En los dos lados se marca como "consumido" para que el mando no ejecute además su clic en el aire.
     */
    @SubscribeEvent
    public static void onCarRightClick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof RcCarEntity car)
                || !event.getItemStack().is(ModItems.RC_CAR_REMOTE.get())) {
            return;
        }

        boolean clientSide = event.getLevel().isClientSide;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        if (clientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (car.isPilotedBy(player)) {
            car.stopPiloting(player);
            return;
        }
        if (car.isPiloted()) {
            return;
        }
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof RcCarEntity other && other != car && other.isPilotedBy(player)) {
                other.stopPiloting(player);
                break;
            }
        }
        if (player.distanceToSqr(car) > 64.0) {
            return;
        }
        car.startPiloting(player);
    }
}
