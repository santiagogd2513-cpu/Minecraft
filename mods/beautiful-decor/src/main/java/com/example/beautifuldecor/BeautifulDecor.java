package com.example.beautifuldecor;

import com.example.beautifuldecor.init.ModBlockEntities;
import com.example.beautifuldecor.init.ModBlocks;
import com.example.beautifuldecor.init.ModEntities;
import com.example.beautifuldecor.init.ModItems;
import com.example.beautifuldecor.init.ModCreativeTabs;
import com.example.beautifuldecor.init.ModSounds;
import com.example.beautifuldecor.network.ModMessages;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BeautifulDecor.MOD_ID)
public class BeautifulDecor {
    public static final String MOD_ID = "beautifuldecor";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeautifulDecor(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(TabOrder::moveToEnd));

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModMessages.register();
        LOGGER.info("Beautiful Decor loaded successfully for Minecraft 1.20.1!");
    }
}
