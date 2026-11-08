package com.example.beautifuldecor.elevator;

/**
 * V3.4.0: las líneas del ascensor, sin nada de Minecraft (así se prueban fuera del juego, ver tools/v340/java/ElevatorLinesTest.java).
 * <p>
 * Antes un ascensor era solo la columna de controladores con la misma X y Z (la cabina sube y baja). Ahora también hay líneas
 * horizontales: controladores a la MISMA ALTURA y con la misma Z (la cabina va de lado en X, este/oeste) o con la misma X (va de lado en Z,
 * norte/sur). Cada viaje es en línea recta entre dos controladores que comparten una línea.
 */
public final class ElevatorLines {
    /** Eje de viaje: X (este-oeste). */
    public static final int AXIS_X = 0;
    /** Eje de viaje: Y (arriba-abajo, el ascensor de siempre). */
    public static final int AXIS_Y = 1;
    /** Eje de viaje: Z (norte-sur). */
    public static final int AXIS_Z = 2;
    /** No comparten ninguna línea. */
    public static final int NO_LINE = -1;
    /** Hasta cuántos bloques hacia cada lado se buscan controladores de una línea horizontal (8 chunks). */
    public static final int ROW_RANGE = 128;
    /**
     * V3.4.0: de lado, cuántas capas (desde la base de la zona) tienen que estar libres como mínimo en el camino: la del suelo y dos más para
     * quien va de pie encima. Así una plataforma baja (alto 1) no lleva al jugador contra un bloque a la altura de su cabeza (lo tiraría).
     */
    public static final int SIDE_CLEARANCE = 3;
    /** "No hay capa nueva": la cabina no entra en ningún bloque nuevo en este paso. */
    public static final int NO_SLICE = Integer.MIN_VALUE;

    private ElevatorLines() {
    }

    /**
     * ¿Qué línea comparten dos controladores? Misma X y Z: la columna ({@link #AXIS_Y}, también si son el mismo); misma altura y Z: línea en X;
     * misma altura y X: línea en Z; si no, {@link #NO_LINE}.
     */
    public static int lineAxis(int ax, int ay, int az, int bx, int by, int bz) {
        if (ax == bx && az == bz) {
            return AXIS_Y;
        }
        if (ay == by && az == bz) {
            return AXIS_X;
        }
        if (ay == by && ax == bx) {
            return AXIS_Z;
        }
        return NO_LINE;
    }

    /** ¿Están lo bastante cerca para una línea horizontal? (la columna no tiene límite: es la altura del mundo) */
    public static boolean inRange(int axis, int ax, int az, int bx, int bz) {
        if (axis == AXIS_X) {
            return Math.abs(ax - bx) <= ROW_RANGE;
        }
        if (axis == AXIS_Z) {
            return Math.abs(az - bz) <= ROW_RANGE;
        }
        return axis == AXIS_Y;
    }

    /**
     * La capa de bloques nueva en la que entra la cabina al pasar su esquina de {@code oldMin} a {@code newMin} en su eje (mide {@code size}
     * bloques en ese eje), o {@link #NO_SLICE} si sigue en las mismas. Es la cuenta que la cabina hacía en vertical, para cualquier eje.
     */
    public static int leadingSlice(double oldMin, double newMin, int size) {
        if (newMin > oldMin) {
            int next = (int) Math.ceil(newMin + size - 1.0E-6) - 1;
            int now = (int) Math.ceil(oldMin + size - 1.0E-6) - 1;
            return next != now ? next : NO_SLICE;
        }
        if (newMin < oldMin) {
            int next = (int) Math.floor(newMin + 1.0E-6);
            int now = (int) Math.floor(oldMin + 1.0E-6);
            return next != now ? next : NO_SLICE;
        }
        return NO_SLICE;
    }

    /** V3.4.0: cuántas capas (desde la base de la zona) se revisan en el camino: arriba-abajo, el alto de la zona; de lado, al menos {@link #SIDE_CLEARANCE}. */
    public static int sweepHeight(int axis, int sizeY) {
        return axis == AXIS_Y ? sizeY : Math.max(sizeY, SIDE_CLEARANCE);
    }

    /** Si la cabina se atasca, el bloque entero donde se queda: hacia atrás del sentido en que iba. */
    public static int stuckBase(double pos, int dir) {
        return dir > 0 ? (int) Math.floor(pos + 1.0E-6) : (int) Math.ceil(pos - 1.0E-6);
    }

    /**
     * Dirección para mostrar en la pantalla (clave de idioma "gui.beautifuldecor.elevator.dir.*"): de {@code from} a {@code to} en una
     * línea de eje {@code axis}. X + es el este, Z + es el sur (como en el mapa de Minecraft).
     */
    public static String directionKey(int axis, int from, int to) {
        if (axis == AXIS_X) {
            return to > from ? "east" : "west";
        }
        if (axis == AXIS_Z) {
            return to > from ? "south" : "north";
        }
        return to > from ? "up" : "down";
    }

    /** Coordenada de un punto en el eje {@code axis}. */
    public static int along(int axis, int x, int y, int z) {
        return axis == AXIS_X ? x : axis == AXIS_Z ? z : y;
    }
}
