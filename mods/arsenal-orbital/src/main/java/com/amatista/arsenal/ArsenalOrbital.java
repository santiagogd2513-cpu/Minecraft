package com.amatista.arsenal;

import com.amatista.arsenal.net.Network;
import com.amatista.arsenal.registry.ModItems;
import com.amatista.arsenal.registry.ModTabs;
import com.amatista.arsenal.strike.OrbitalStrikeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Arsenal Orbital: armas espaciales. Primera arma: el Designador Orbital (satélite ÉGIDA-7), un
 * artefacto que se encuentra en la bóveda de los búnkeres militares bajo tierra.
 */
@Mod(ArsenalOrbital.MODID)
public class ArsenalOrbital {
    public static final String MODID = "arsenalorbital";

    public ArsenalOrbital() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        com.amatista.arsenal.registry.ModBlocks.BLOCKS.register(modBus);
        com.amatista.arsenal.registry.ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.amatista.arsenal.world.ModStructures.TYPES.register(modBus);
        com.amatista.arsenal.world.ModStructures.PIECES.register(modBus);
        ModTabs.TABS.register(modBus);
        com.amatista.arsenal.registry.ModSounds.SOUNDS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Network.register();
        MinecraftForge.EVENT_BUS.register(OrbitalStrikeManager.class);
    }
}
