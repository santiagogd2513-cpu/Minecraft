package com.example.beautifuldecor.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Teclas del helicóptero (se pueden cambiar en Opciones > Controles > Beautiful Decor). */
public final class HelicopterKeys {
    /** Bajarse del helicóptero (hay que estar a menos de 2 bloques del suelo). */
    public static final KeyMapping EXIT = new KeyMapping("key.beautifuldecor.helicopter_exit", GLFW.GLFW_KEY_Z,
            VehicleKeys.CATEGORY);

    private HelicopterKeys() {}
}
