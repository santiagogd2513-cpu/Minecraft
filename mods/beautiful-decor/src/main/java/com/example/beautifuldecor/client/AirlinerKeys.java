package com.example.beautifuldecor.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Teclas del avión (se pueden cambiar en Opciones > Controles > Beautiful Decor). Para bajarse se usa la misma Z del helicóptero. */
public final class AirlinerKeys {
    /** Recoger o bajar el tren de aterrizaje (solo el piloto). Mientras pilotas, esta tecla no cambia el objeto de mano. */
    public static final KeyMapping GEAR = new KeyMapping("key.beautifuldecor.airliner_gear", GLFW.GLFW_KEY_F,
            VehicleKeys.CATEGORY);

    private AirlinerKeys() {}
}
