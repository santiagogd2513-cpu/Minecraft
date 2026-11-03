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

/** Arsenal Orbital: armas espaciales. Primera arma: el Designador Orbital (satélite ÉGIDA-7). */
@Mod(ArsenalOrbital.MODID)
public class ArsenalOrbital {
    public static final String MODID = "arsenalorbital";

    public ArsenalOrbital() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModTabs.TABS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Network.register();
        MinecraftForge.EVENT_BUS.register(OrbitalStrikeManager.class);
    }
}
