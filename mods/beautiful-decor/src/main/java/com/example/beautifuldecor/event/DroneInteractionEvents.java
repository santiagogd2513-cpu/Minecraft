package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.entity.DroneEntity;
import com.example.beautifuldecor.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DroneInteractionEvents {
    private DroneInteractionEvents() {}

    /** Servidor: mientras alguien pilota un dron no puede picar, poner, usar ni atacar nada. */
    private static boolean isPilot(Player player) {
        return player != null && !player.level().isClientSide && DroneEntity.isPiloting(player);
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
     * Si el jugador se desconectó (o el servidor se cerró) mientras pilotaba, se había quedado sin gravedad:
     * al volver se le devuelve.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.getPersistentData().getBoolean(DroneEntity.PILOT_MARKER) && !DroneEntity.isPiloting(player)) {
            player.getPersistentData().remove(DroneEntity.PILOT_MARKER);
            player.setNoGravity(false);
        }
    }

    /**
     * Control remoto sobre la entidad del dron: lo enciende y empieza a pilotarlo. En los dos lados se marca como
     * "consumido" para que el mando no ejecute además su clic en el aire (antes eso apagaba el dron al instante).
     */
    @SubscribeEvent
    public static void onDroneRightClick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof DroneEntity drone)
                || !event.getItemStack().is(ModItems.DRONE_REMOTE.get())) {
            return;
        }

        boolean clientSide = event.getLevel().isClientSide;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        if (clientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (drone.isPilotedBy(player)) {
            drone.stopPiloting(player);
            return;
        }

        if (drone.isPiloted()) {
            return;
        }

        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof DroneEntity other && other != drone && other.isPilotedBy(player)) {
                other.stopPiloting(player);
                break;
            }
        }
        if (player.distanceToSqr(drone) > 64.0) {
            return;
        }
        drone.startPiloting(player);
    }
}
