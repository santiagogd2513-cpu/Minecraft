package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.camera.SecurityCameraHub;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** V4.3.0: al entrar a un mundo o a otra dimensión, el jugador recibe la lista de cámaras de seguridad de ahí. */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CameraEvents {
    private CameraEvents() {
    }

    private static void sync(Player p) {
        if (p instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            SecurityCameraHub.get(level).sendAll(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }
}
