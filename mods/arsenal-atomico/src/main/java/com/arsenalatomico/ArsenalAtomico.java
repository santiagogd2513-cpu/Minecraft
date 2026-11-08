package com.arsenalatomico;

import com.arsenalatomico.net.Network;
import com.arsenalatomico.registry.ModEntities;
import com.arsenalatomico.registry.ModItems;
import com.arsenalatomico.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Arsenal Atómico: misiles en el mundo de Minecraft, con cinemáticas. Cinco armas, de la ráfaga
 * de misiles tácticos hasta la bomba termonuclear (se eligen con la tecla J).
 */
@Mod(ArsenalAtomico.MODID)
public class ArsenalAtomico {
    public static final String MODID = "arsenalatomico";

    public ArsenalAtomico() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModTabs.TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Network.register();
    }
}
