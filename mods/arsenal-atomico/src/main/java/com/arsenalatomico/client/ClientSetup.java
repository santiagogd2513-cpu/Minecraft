package com.arsenalatomico.client;

import com.arsenalatomico.ArsenalAtomico;
import com.arsenalatomico.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArsenalAtomico.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Tecla J: menú de armas (la H es de Arsenal Orbital). */
    public static final KeyMapping WEAPONS = new KeyMapping("key.arsenalatomico.weapons",
            InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.arsenalatomico");

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(WEAPONS);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("cinematic", Hud::render);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
        e.registerEntityRenderer(ModEntities.TRUCK.get(), TruckRenderer::new);
    }

    private ClientSetup() {}
}
