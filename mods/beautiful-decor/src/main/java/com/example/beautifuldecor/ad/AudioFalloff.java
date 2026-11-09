package com.example.beautifuldecor.ad;

/**
 * V4.1.2: cuánto se oye algo que tiene sonido propio (televisores, pantallas de anuncios, radios de vehículo) según la distancia en
 * BLOQUES al jugador (no a los chunks cargados: aunque tengas 45 chunks de distancia de render, a más de {@code max} bloques no suena nada).
 * Hasta {@code full} bloques se oye al máximo; de ahí baja con curva cuadrática (cae más rápido al principio, como el sonido real) hasta
 * 0 en {@code max}. Lógica pura, sin Minecraft.
 */
public final class AudioFalloff {
    private AudioFalloff() {
    }

    /** Volumen de 0 a 1 a esa distancia. Distancias negativas, NaN o un rango mal puesto no rompen nada. */
    public static float gain(double distance, double full, double max) {
        if (Double.isNaN(distance) || distance >= max) {
            return 0.0f;
        }
        if (distance <= full || max <= full) {
            return 1.0f;
        }
        double t = 1.0 - (distance - full) / (max - full);
        return (float) (t * t);
    }

    /** Hasta dónde se oye al máximo una pantalla de anuncios de ese tamaño (4, 8 o 12 bloques de lado). */
    public static double adFull(int sizeBlocks) {
        return 4.0 + sizeBlocks;
    }

    /** A qué distancia se deja de oír del todo una pantalla de anuncios de ese tamaño: las grandes se oyen más lejos. */
    public static double adMax(int sizeBlocks) {
        return 20.0 + 2.0 * sizeBlocks;
    }
}
