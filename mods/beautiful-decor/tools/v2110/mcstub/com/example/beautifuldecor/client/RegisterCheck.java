package com.example.beautifuldecor.client;
import com.example.beautifuldecor.init.ModBlockEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
/** Misma línea que ClientEvents.registerBlockEntityRenderers: comprueba que los tipos encajan. */
class RegisterCheck {
    static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TELEVISION.get(), TelevisionRenderer::new);
    }
}
