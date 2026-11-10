package com.example.beautifuldecor.camera;

/**
 * V4.3.0: cuándo una cámara de seguridad pasa a visión nocturna, sin nada de Minecraft. Depende de la luz que hay en la cámara (0 a 15,
 * ya con la oscuridad del cielo descontada): de noche (desde las 18:00) se activa con menos exigencia, y de día solo si se queda casi
 * sin luz (por ejemplo, un cuarto cuyas luces se apagan). Con histéresis, para que no parpadee entre los dos modos.
 */
public final class CameraNight {
    /** Tick del día (0 = 06:00) desde el que se considera de noche: las 18:00. */
    public static final long NIGHT_START = 12000L;
    /** Tick del día hasta el que dura la noche: pasadas las 05:30 ya amanece. */
    public static final long NIGHT_END = 23500L;
    public static final int DARK_NIGHT = 11;
    public static final int DARK_DAY = 6;
    public static final int HYSTERESIS = 2;

    private CameraNight() {
    }

    public static boolean isNightTime(long dayTime) {
        long t = ((dayTime % 24000L) + 24000L) % 24000L;
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** Estado siguiente: {@code on} es el actual, {@code brightness} la luz en la cámara (0..15). */
    public static boolean next(boolean on, int brightness, long dayTime) {
        int limit = isNightTime(dayTime) ? DARK_NIGHT : DARK_DAY;
        if (on) {
            return brightness <= limit + HYSTERESIS;
        }
        return brightness <= limit;
    }
}
