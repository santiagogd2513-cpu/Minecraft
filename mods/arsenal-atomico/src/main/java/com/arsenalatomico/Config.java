package com.arsenalatomico;

import net.minecraftforge.common.ForgeConfigSpec;

/** Ajustes del mod (config/arsenalatomico-common.toml). */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue POWER;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_RANGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Arsenal Atómico").push("misiles");
        DESTROY_BLOCKS = b.comment("Si las explosiones rompen bloques (false = solo daño y efectos)")
                .define("destruirBloques", true);
        POWER = b.comment("Multiplicador de la fuerza de todas las explosiones (1 = normal)")
                .defineInRange("potencia", 1.0, 0.25, 4.0);
        COOLDOWN_SECONDS = b.comment("Tiempo de recarga entre lanzamientos, en segundos")
                .defineInRange("recargaSegundos", 30, 0, 3600);
        MAX_RANGE = b.comment("Distancia máxima para marcar un objetivo")
                .defineInRange("alcance", 160, 16, 400);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
