package com.example.beautifuldecor.entity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * V6.0.0: la parte pura (sin Minecraft) del piloto automático de los trenes: cuánto hay que frenar, a qué velocidad ir,
 * el horario del depósito y el recorrido de la vía por delante. Distancias en metros; velocidades en m/s.
 */
public final class TrainAuto {
    /** Cuánto de su freno usa el piloto (deja margen para pendientes y la carga). */
    public static final double BRAKE_USE = 0.75;
    /** Frenada mínima que se supone (m/s²) para que un tren casi sin freno no quede con velocidad 0 para siempre. */
    public static final double MIN_DECEL = 0.12;

    /** Frenada que se le puede pedir al tren (m/s²): el freno se calcula para el tren vacío (+20 %), así que cargado frena menos. */
    public static double decel(double emptyKg, double totalKg) {
        double full = TrainPhysics.BRAKE * Math.min(1.0, 1.2 * Math.max(emptyKg, 1.0) / Math.max(totalKg, 1.0));
        return Math.max(MIN_DECEL, full * BRAKE_USE);
    }
    /** Distancia del punto de parada a la que se considera llegado (m). */
    public static final double ARRIVE_DIST = 0.35;
    /** Los primeros metros después de salir se hacen a la velocidad de salida (m). */
    public static final double DEPART_ZONE = 120.0;
    /** Hasta dónde mira hacia adelante (m). */
    public static final double WINDOW = 700.0;
    /** Paso con el que se recorre la vía (m): menor que un bloque, para no saltarse ninguno. */
    public static final double STEP = 0.5;
    /** Velocidad de arrastre al final de la frenada (m/s). */
    public static final double CREEP = 0.6;
    /** Distancia de vía libre que se deja siempre sin usar, al frenar por un final de vía (m). */
    public static final double SAFE_GAP = 8.0;

    private TrainAuto() {
    }

    /** Hora del mundo (0–24) según el tiempo del día: a las 0 del reloj del juego son las 6 de la mañana. */
    public static double hourOf(long dayTime) {
        double h = (((dayTime % 24000L) + 24000L) % 24000L) / 1000.0;
        return (h + 6.0) % 24.0;
    }

    /** ¿Está abierto el depósito a esa hora? De {@code open} a {@code close} (puede cruzar la medianoche); 0 a 24 = siempre. */
    public static boolean isOpen(double hour, int open, int close) {
        if (open == close) {
            return true;
        }
        if (open < close) {
            return hour >= open && hour < close;
        }
        return hour >= open || hour < close;
    }

    /** Velocidad máxima desde la que se alcanza a frenar en {@code dist} metros. */
    public static double brakeSpeed(double dist, double decel) {
        return dist <= 0.0 ? 0.0 : Math.sqrt(2.0 * decel * dist);
    }

    /** Velocidad que conviene llevar a {@code dist} metros del punto de parada, con {@code cruise} como tope. */
    public static double targetSpeed(double dist, double cruise, double decel) {
        if (dist <= ARRIVE_DIST) {
            return 0.0;
        }
        double v = brakeSpeed(dist - ARRIVE_DIST, decel);
        v = Math.max(v, Math.min(CREEP, dist * 1.5));                  // no se queda corto antes de llegar
        return Math.min(cruise, v);
    }

    /** 1 = acelerar, -1 = frenar, 0 = dejar rodar. */
    public static int decide(double v, double target) {
        if (v < target - 0.35) {
            return 1;
        }
        if (v > target + 0.35) {
            return -1;
        }
        return 0;
    }

    /** Resultado de mirar la vía por delante. */
    public static final class Ahead {
        /** Distancia (m) hasta el punto de parada del riel buscado, o -1 si no está a la vista. */
        public double found = -1.0;
        /** Cuánta vía continua se vio (m). */
        public double known;
        /** Chunks por los que pasa esa vía (clave = ChunkPos.asLong). */
        public final Set<Long> chunks = new LinkedHashSet<>();
        /** Bloque donde se cortó la vista (si no llegó a la ventana). */
        public int endX;
        public int endZ;
        public boolean cut;
    }

    /**
     * Recorre la vía desde {@code from} (no se modifica) hasta {@code window} metros o hasta que se acabe, y busca el punto de parada
     * del riel ({@code tx,ty,tz}): su centro. Si el punto de parada ya quedó atrás, no cuenta.
     */
    public static Ahead look(RailPath.Cursor from, RailPath.Lookup lk, int tx, int ty, int tz, double window) {
        Ahead out = new Ahead();
        RailPath.Cursor c = from.copy();
        // ¿está el punto en el mismo bloque, por delante?
        double walked = 0.0;
        boolean inTarget = c.x == tx && c.y == ty && c.z == tz;
        if (inTarget) {
            double d = (c.shape.length() / 2.0 - c.s) * c.dir;
            if (d >= -1.0e-6) {
                out.found = d;
            }
        }
        out.chunks.add(chunkKey(c.x, c.z));
        while (out.found < 0.0 && walked < window) {
            double moved = c.advance(STEP, lk);
            walked += moved;
            out.chunks.add(chunkKey(c.x, c.z));
            if (moved < STEP - 1.0e-6) {
                out.cut = true;
                out.endX = c.x;
                out.endZ = c.z;
                break;
            }
            if (c.x == tx && c.y == ty && c.z == tz) {
                double d = (c.shape.length() / 2.0 - c.s) * c.dir;
                if (d >= -1.0e-6) {
                    out.found = walked + d;
                }
            }
        }
        out.known = walked;
        return out;
    }

    public static long chunkKey(int blockX, int blockZ) {
        return ((long) (blockX >> 4) & 0xFFFFFFFFL) | (((long) (blockZ >> 4) & 0xFFFFFFFFL) << 32);
    }
}
