package com.example.beautifuldecor.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** V2.3.0: tecla del menú de colores de la puerta de garaje (R por defecto; se cambia en Opciones > Controles > Beautiful Decor). */
public final class GarageKeys {
    public static final KeyMapping COLORS = new KeyMapping("key.beautifuldecor.garage_colors", GLFW.GLFW_KEY_R,
            VehicleKeys.CATEGORY);

    private GarageKeys() {}
}
