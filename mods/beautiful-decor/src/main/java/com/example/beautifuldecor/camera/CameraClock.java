package com.example.beautifuldecor.camera;

/** V4.3.0: la hora de Minecraft como la muestra el monitor (24 horas): el tick 0 del día son las 06:00. */
public final class CameraClock {
    private CameraClock() {
    }

    public static String format(long dayTime) {
        long t = ((dayTime % 24000L) + 24000L) % 24000L;
        int hour = (int) ((t / 1000L + 6L) % 24L);
        int minute = (int) ((t % 1000L) * 60L / 1000L);
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }
}
