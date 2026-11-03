package com.amatista.arsenal;

import net.minecraftforge.common.ForgeConfigSpec;

/** Ajustes del mod (config/arsenalorbital-common.toml). */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC_ENABLED;
    public static final ForgeConfigSpec.IntValue CINEMATIC_SECONDS;
    public static final ForgeConfigSpec.IntValue CRATER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue PIERCE_BEDROCK;
    public static final ForgeConfigSpec.IntValue SHAFT_RADIUS;
    public static final ForgeConfigSpec.IntValue DEBRIS_COUNT;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_RANGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Bombardeo orbital ÉGIDA-7").push("bombardeo");
        CINEMATIC_ENABLED = b.comment("Mostrar la cinemática al disparar (si es false, el rayo cae a los 2 segundos)")
                .define("cinematica", true);
        CINEMATIC_SECONDS = b.comment("Duración de la cinemática en segundos (40 a 180)")
                .defineInRange("duracionCinematicaSegundos", 180, 40, 180);
        CRATER_RADIUS = b.comment("Radio del cráter en bloques")
                .defineInRange("radioCrater", 22, 3, 48);
        DESTROY_BLOCKS = b.comment("Si el impacto destruye bloques (false = solo daño y efectos)")
                .define("destruirBloques", true);
        PIERCE_BEDROCK = b.comment("El rayo perfora todo hasta el fondo del mundo, incluida la roca madre (bedrock)")
                .define("perforarRocaMadre", true);
        SHAFT_RADIUS = b.comment("Radio del pozo que abre el rayo hasta el fondo del mundo")
                .defineInRange("radioPozo", 8, 2, 24);
        DEBRIS_COUNT = b.comment("Bloques que salen volando como escombros (más = más lento)")
                .defineInRange("escombros", 260, 0, 800);
        COOLDOWN_SECONDS = b.comment("Tiempo de recarga del satélite en segundos")
                .defineInRange("recargaSegundos", 120, 0, 3600);
        MAX_RANGE = b.comment("Distancia máxima para marcar un objetivo")
                .defineInRange("alcance", 160, 16, 400);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
