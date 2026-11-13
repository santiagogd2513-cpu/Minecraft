package com.example.beautifuldecor.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** V6.3.0: configuración del jugador (cliente). */
public final class ModClientConfig {
    public static final ForgeConfigSpec SPEC;
    /** Si es true, las funciones de los interiores (claxon, luces, radio) también se pueden usar con teclas. Por defecto: apagado. */
    public static final ForgeConfigSpec.BooleanValue VEHICLE_KEYS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        VEHICLE_KEYS = builder.comment("Usar teclas para las funciones de los interiores (claxon, luces, radio). Apagado = solo los botones del tablero.")
                .define("vehicleKeys", false);
        SPEC = builder.build();
    }

    private ModClientConfig() {
    }

    public static boolean vehicleKeys() {
        return VEHICLE_KEYS.get();
    }
}
