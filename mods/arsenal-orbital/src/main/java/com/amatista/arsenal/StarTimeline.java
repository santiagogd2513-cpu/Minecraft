package com.amatista.arsenal;

/** Momentos del "Protocolo Prometeo" (fracción de la duración total, 3 min 30 s por defecto). */
public final class StarTimeline {
    public static final float S1_END = 0.06f;           // autorización
    public static final float S2_END = 0.145f;          // ascenso
    public static final float SPACE_START = 0.14f;
    public static final float SOLAR_END = 0.19f;        // sale del sistema solar
    public static final float MILKY_END = 0.25f;        // sale de la Vía Láctea
    public static final float ANDROMEDA_END = 0.31f;    // pasa junto a Andrómeda
    public static final float FIELD_END = 0.36f;        // cruza un cúmulo de galaxias
    public static final float VOID_END = 0.40f;         // la mitad de la nada
    public static final float APPROACH_END = 0.45f;     // llegada a la estrella azul
    public static final float FLYBY_END = 0.50f;        // vuelo rasante sobre la estrella
    public static final float STATION_END = 0.58f;      // la estación Prometeo
    public static final float CHARGE_END = 0.76f;       // carga con la energía de la estrella
    public static final float FIRE_END = 0.78f;         // el disparo
    public static final float CHASE_END = 0.83f;        // el rayo cruza el universo
    public static final float SPACE_END = 0.86f;        // el rayo llega a la Tierra
    public static final float WORLD_START = 0.855f;     // el rayo aparece en el mundo
    public static final float BURN_START = 0.885f;      // el rayo quema el suelo
    public static final float IMPACT = 0.93f;           // explosión final
    public static final float RETURN_START = 0.965f;
    /** Esta cinemática dura 3 min 30 s cuando la configuración está en 3 min. */
    public static final float DURATION_SCALE = 210f / 180f;

    private StarTimeline() {}
}
