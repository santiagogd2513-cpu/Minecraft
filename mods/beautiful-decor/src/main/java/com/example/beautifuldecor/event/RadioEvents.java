package com.example.beautifuldecor.event;

import com.example.beautifuldecor.BeautifulDecor;
import com.example.beautifuldecor.radio.RadioHub;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** V4.2.0: al entrar a un mundo o a otra dimensión, el jugador recibe el estado de las radios de mesa de ahí. */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadioEvents {
    private RadioEvents() {
    }

    private static void sync(net.minecraft.world.entity.player.Player p) {
        if (p instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            RadioHub.get(level).sendAll(player);
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
