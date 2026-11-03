package com.amatista.arsenal;

/** Momentos de la cinemática "Protocolo Fobos" (fracción de la duración total). */
public final class PhobosTimeline {
    /** Acto 1: autorización. */
    public static final float P1_END = 0.08f;
    /** Acto 2: ascenso hasta el espacio. */
    public static final float P2_END = 0.20f;
    public static final float SPACE_START = 0.195f;
    /** Acto 3: viaje a gran velocidad hasta Marte. */
    public static final float TRAVEL_END = 0.34f;
    /** Acto 4: llegada a Marte, Fobos y Deimos. */
    public static final float MARS_END = 0.46f;
    /** Acto 5: el misil se estrella contra Fobos. */
    public static final float MISSILE_END = 0.56f;
    /** Momento del choque del misil dentro del acto 5. */
    public static final float MISSILE_HIT = 0.52f;
    /** Acto 6: honda gravitatoria, cada vez más rápido alrededor de Marte. */
    public static final float SLING_END = 0.74f;
    /** Acto 7: Fobos sale disparado hacia la Tierra. */
    public static final float SPACE_END = 0.84f;
    /** El servidor empieza a mostrar la roca cayendo en el mundo. */
    public static final float WORLD_START = 0.835f;
    /** Impacto real. */
    public static final float IMPACT = 0.90f;
    /** Regreso de la cámara. */
    public static final float RETURN_START = 0.955f;

    private PhobosTimeline() {}
}
