package com.example.beautifuldecor.client;

/**
 * Cuentas de la cuadrícula "plataforma en vivo" del menú del controlador de ascensor (V3.2.1). No usa nada de Minecraft:
 * el menú le da una fuente de celdas (lo que hay en cada bloque de la zona) y esta clase arma la vista desde arriba o por
 * capas, cuenta los bloques y calcula tamaños y posiciones. Así se puede probar fuera del juego.
 * <p>
 * Las coordenadas de la fuente son relativas a la esquina mínima de la zona: x hacia el este, z hacia el sur y y hacia arriba.
 */
final class ElevatorPlatformView {
    /** No hay bloque (aire). */
    static final int EMPTY = 0;
    /** Hay un bloque que NO se mueve con la cabina (controlador, pantalla, puerta, líquido). */
    static final int STAYS = 1;
    /** Vista desde arriba: en cada columna se ve el bloque más alto que se mueve. */
    static final int TOP = -1;

    private ElevatorPlatformView() {
    }

    /** Qué hay en una celda: {@link #EMPTY}, {@link #STAYS} o un color ARGB opaco (0xFFRRGGBB) si es un bloque que se mueve. */
    interface Source {
        int cell(int x, int y, int z);
    }

    /** Resultado de mirar la zona. */
    static final class Grid {
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        /** {@link #TOP} o la capa (0 = la de abajo) que se está mostrando. */
        final int layer;
        /** sizeX * sizeZ celdas, en el orden z * sizeX + x. */
        final int[] cells;
        /** Capa (0 = abajo) del bloque que se muestra en cada celda, o -1 si no se muestra ninguno. */
        final int[] heights;
        /** Bloques que se mueven en TODA la zona (no solo en la capa mostrada). */
        int movable;
        /** Bloques que se quedan en TODA la zona. */
        int stays;

        Grid(int sizeX, int sizeY, int sizeZ, int layer) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layer = layer;
            this.cells = new int[sizeX * sizeZ];
            this.heights = new int[sizeX * sizeZ];
            java.util.Arrays.fill(this.heights, -1);
        }

        int index(int x, int z) {
            return z * this.sizeX + x;
        }
    }

    static boolean isMovableCell(int cell) {
        return cell != EMPTY && cell != STAYS;
    }

    /**
     * Recorre toda la zona una vez. Con {@link #TOP} cada columna muestra su bloque más alto que se mueve (y si solo tiene
     * bloques que se quedan, el más alto de esos); con una capa muestra lo que haya en esa capa.
     */
    static Grid scan(int sizeX, int sizeY, int sizeZ, int layer, Source source) {
        int shownLayer = clampLayer(layer, sizeY);
        Grid grid = new Grid(sizeX, sizeY, sizeZ, shownLayer);
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int shown = EMPTY;
                int shownY = -1;
                for (int y = sizeY - 1; y >= 0; y--) {
                    int cell = source.cell(x, y, z);
                    if (cell == EMPTY) {
                        continue;
                    }
                    if (cell == STAYS) {
                        grid.stays++;
                    } else {
                        grid.movable++;
                    }
                    if (shownLayer == TOP) {
                        if (cell != STAYS) {
                            if (shown == EMPTY || shown == STAYS) {
                                shown = cell;
                                shownY = y;
                            }
                        } else if (shown == EMPTY) {
                            shown = STAYS;
                            shownY = y;
                        }
                    } else if (y == shownLayer) {
                        shown = cell;
                        shownY = y;
                    }
                }
                int i = grid.index(x, z);
                grid.cells[i] = shown;
                grid.heights[i] = shownY;
            }
        }
        return grid;
    }

    // ------------------------------------------------------------------------------------------ capas

    /** Deja la capa en TOP o entre 0 y sizeY - 1. */
    static int clampLayer(int layer, int sizeY) {
        if (layer < 0) {
            return TOP;
        }
        return Math.min(layer, Math.max(0, sizeY - 1));
    }

    /** Pasa a la capa de arriba (+1) o de abajo (-1). Más abajo de la capa 0 queda la vista desde arriba; sin vuelta. */
    static int stepLayer(int layer, int delta, int sizeY) {
        int next = clampLayer(layer, sizeY);
        if (delta > 0) {
            return next == TOP ? 0 : clampLayer(next + 1, sizeY);
        }
        return next == TOP ? TOP : next - 1 < 0 ? TOP : next - 1;
    }

    /** Un poco más oscuro lo que está más abajo en la vista desde arriba, para que se note el relieve. */
    static int shade(int argb, int layer, int sizeY) {
        if (sizeY <= 1) {
            return argb;
        }
        double factor = 0.62 + 0.38 * (layer + 1) / sizeY;
        int r = (int) Math.round(((argb >> 16) & 0xFF) * factor);
        int g = (int) Math.round(((argb >> 8) & 0xFF) * factor);
        int b = (int) Math.round((argb & 0xFF) * factor);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ------------------------------------------------------------------------------------------ geometría

    /** Lado en píxeles de cada celda para que quepa toda la zona en el recuadro (entre 1 y {@code max}). */
    static int cellSize(int areaW, int areaH, int sizeX, int sizeZ, int max) {
        int fit = Math.min(areaW / Math.max(1, sizeX), areaH / Math.max(1, sizeZ));
        return Math.max(1, Math.min(max, fit));
    }

    /** Índice (z * sizeX + x) de la celda bajo el mouse, o -1 si el mouse está fuera de la cuadrícula. */
    static int cellAt(double mx, double my, int gridX, int gridY, int cell, int sizeX, int sizeZ) {
        if (mx < gridX || my < gridY) {
            return -1;
        }
        int cx = (int) ((mx - gridX) / cell);
        int cz = (int) ((my - gridY) / cell);
        return cx < sizeX && cz < sizeZ ? cz * sizeX + cx : -1;
    }

    /**
     * Centro (en píxeles) de la marca del controlador. El controlador queda pegado a la zona, así que su celda ({@code rx},
     * {@code rz}, relativa a la esquina mínima de la zona) está justo afuera de un borde: la marca se dibuja en el margen.
     */
    static int[] markerCenter(int gridX, int gridY, int cell, int sizeX, int sizeZ, int rx, int rz, int offset) {
        int mx = rx < 0 ? gridX - offset : rx >= sizeX ? gridX + sizeX * cell + offset : gridX + rx * cell + cell / 2;
        int my = rz < 0 ? gridY - offset : rz >= sizeZ ? gridY + sizeZ * cell + offset : gridY + rz * cell + cell / 2;
        return new int[] {mx, my};
    }

    /** Eje del mundo (X o Z) por el que corre el ANCHO de la cabina: si mira al norte o al sur, el ancho va por X. */
    static char widthAxis(boolean facingAlongZ) {
        return facingAlongZ ? 'X' : 'Z';
    }

    /** Eje del mundo por el que corre el FONDO de la cabina (hacia donde mira el controlador). */
    static char depthAxis(boolean facingAlongZ) {
        return facingAlongZ ? 'Z' : 'X';
    }
}
