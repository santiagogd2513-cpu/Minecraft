package com.example.beautifuldecor.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Teclas de los vehículos (se pueden cambiar en Opciones > Controles > Beautiful Decor). */
public final class VehicleKeys {
    public static final String CATEGORY = "key.categories.beautifuldecor";

    /** Luz delantera del vehículo: encender / apagar. */
    public static final KeyMapping LIGHT = new KeyMapping("key.beautifuldecor.motorcycle_light", GLFW.GLFW_KEY_G, CATEGORY);
    /** Claxon (moto y camioneta) o timbre (bicicleta). */
    public static final KeyMapping HORN = new KeyMapping("key.beautifuldecor.motorcycle_horn", GLFW.GLFW_KEY_H, CATEGORY);
    /** V3.5.0: radio del vehículo terrestre (moto, bicicleta, camioneta, camión): abre su menú yendo montado. */
    public static final KeyMapping RADIO = new KeyMapping("key.beautifuldecor.vehicle_radio", GLFW.GLFW_KEY_X, CATEGORY);

    private VehicleKeys() {}
}
