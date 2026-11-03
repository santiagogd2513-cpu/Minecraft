package com.amatista.arsenal;

/** Momentos del "Protocolo Génesis" (fracción de la duración total, 5 min por defecto). */
public final class GenesisTimeline {
    public static final float G1_END = 0.05f;          // autorización
    public static final float G2_END = 0.10f;          // ascenso
    public static final float SPACE_START = 0.097f;
    public static final float SOLAR_END = 0.13f;       // el sistema solar
    public static final float HOME_END = 0.16f;        // salir de la Vía Láctea
    public static final float WEB_END = 0.24f;         // la red cósmica
    public static final float EDGE_END = 0.30f;        // el borde del universo
    public static final float BEYOND_END = 0.38f;      // más allá: Génesis aparece
    public static final float AWAKEN_END = 0.48f;      // Génesis despierta
    public static final float HARVEST_END = 0.62f;     // la cosecha de galaxias
    public static final float FORGE_END = 0.72f;       // la chispa de la creación
    public static final float LAUNCH_END = 0.76f;      // el lanzamiento
    public static final float RETURN_TRIP_END = 0.84f; // el regreso por el universo
    public static final float SPACE_END = 0.87f;       // la chispa entra en la atmósfera
    public static final float WORLD_START = 0.865f;    // la chispa aparece en el mundo
    public static final float BLOOM_START = 0.90f;     // florece sobre el objetivo
    public static final float IMPACT = 0.93f;          // supernova
    public static final float RETURN_START = 0.965f;
    /** Dura 5 minutos cuando la configuración está en 3. */
    public static final float DURATION_SCALE = 300f / 180f;

    private GenesisTimeline() {}
}
