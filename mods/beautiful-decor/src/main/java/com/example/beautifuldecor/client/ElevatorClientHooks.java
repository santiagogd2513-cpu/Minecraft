package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Puente del código común (la cabina) hacia el cliente. Solo se usa cuando el nivel es del cliente. */
public final class ElevatorClientHooks {
    private ElevatorClientHooks() {
    }

    public static boolean isLocalPlayer(Entity entity) {
        return entity == Minecraft.getInstance().player;
    }

    public static void startHum(ElevatorCabinEntity cabin) {
        Minecraft.getInstance().getSoundManager().play(new ElevatorHumSound(cabin));
    }
}
