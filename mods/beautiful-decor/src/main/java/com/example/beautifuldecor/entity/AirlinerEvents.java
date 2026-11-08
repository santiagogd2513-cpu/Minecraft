package com.example.beautifuldecor.entity;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V3.8.0: el avión mide 62 bloques pero su caja de colisión solo cubre el centro, así que si la cola o la nariz se meten en una montaña
 * los pasajeros de esos asientos quedarían "dentro de un bloque" y se asfixiarían. Quien va montado en el avión no recibe ese daño.
 */
@Mod.EventBusSubscriber(modid = BeautifulDecor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AirlinerEvents {
    private AirlinerEvents() {}

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().getVehicle() instanceof AirlinerEntity && event.getSource().is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
        }
    }
}
