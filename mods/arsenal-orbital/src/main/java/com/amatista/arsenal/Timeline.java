package com.amatista.arsenal;

/**
 * Momentos de la cinemática como fracción de su duración total (0 = inicio, 1 = fin).
 * Los usa el cliente (cámara, sonidos, textos) y el servidor (rayo e impacto).
 */
public final class Timeline {
    /** Acto 1: autorización (consola). */
    public static final float A1_END = 0.10f;
    /** Acto 2: la cámara sube hasta salir de la atmósfera. */
    public static final float A2_END = 0.25f;
    /** Desde aquí se dibuja el espacio. */
    public static final float SPACE_START = 0.245f;
    /** Acto 3: el espacio, el planeta y el sol. */
    public static final float A3_END = 0.444f;
    /** Acto 4: el satélite despierta. */
    public static final float A4_END = 0.622f;
    /** Acto 5: carga del cañón. Al final, el disparo. */
    public static final float A5_END = 0.778f;
    /** Acto 6 (espacio): el rayo baja hacia el planeta. */
    public static final float SPACE_END = 0.81f;
    /** El servidor empieza a mostrar el rayo en el mundo (a todos los jugadores cercanos). */
    public static final float BEAM_WORLD_START = 0.80f;
    /** Acto 6 (mundo): la cámara baja con el rayo atravesando la atmósfera. */
    public static final float A6_END = 0.844f;
    /** Acto 7: cámara lenta. Al final, el impacto real. */
    public static final float IMPACT = 0.922f;
    /** Acto 8: consecuencias y regreso hasta 1.0. */
    public static final float RETURN_START = 0.962f;

    private Timeline() {}
}
