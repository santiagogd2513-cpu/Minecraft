package com.amatista.arsenal.client;

import com.amatista.arsenal.ArsenalOrbital;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArsenalOrbital.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Tecla H: abre el menú para elegir arma. */
    public static final KeyMapping WEAPONS = new KeyMapping("key.arsenalorbital.weapons",
            InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.arsenalorbital");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WEAPONS);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cinematic", HudRenderer::render);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.amatista.arsenal.registry.ModBlocks.PEDESTAL_BE.get(), PedestalRenderer::new);
    }

    private ClientSetup() {}
}
