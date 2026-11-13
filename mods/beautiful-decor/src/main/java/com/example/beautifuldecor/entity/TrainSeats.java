package com.example.beautifuldecor.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Dónde se sienta quien va montado en cada coche del tren de pasajeros (x = izquierda, y = altura de los pies sobre el riel, z = hacia
 * delante, en metros desde el centro del coche). ARCHIVO GENERADO por tools/v480/trains.py junto con los modelos: no editar a mano.
 */
public final class TrainSeats {
    private static final Map<String, double[][]> SEATS = new HashMap<>();
    private static final double[][] NONE = new double[0][];
    private static final Map<String, double[]> BUTTONS = new HashMap<>();

    static {
        SEATS.put("coach_high", new double[][] {{0.938, 1.500, -6.125}, {-0.938, 1.500, -6.125}, {0.938, 1.500, -3.375}, {-0.938, 1.500, -3.375}, {0.938, 1.500, -0.625}, {-0.938, 1.500, -0.625}, {0.938, 1.500, 2.125}, {-0.938, 1.500, 2.125}, {0.938, 1.500, 4.875}, {-0.938, 1.500, 4.875}, {0.938, 1.500, 7.625}, {-0.938, 1.500, 7.625}});
        SEATS.put("coach_low", new double[][] {{0.625, 1.500, -5.625}, {1.125, 1.500, -5.625}, {-0.625, 1.500, -5.625}, {-1.125, 1.500, -5.625}, {0.625, 1.500, -3.625}, {1.125, 1.500, -3.625}, {-0.625, 1.500, -3.625}, {-1.125, 1.500, -3.625}, {0.625, 1.500, -1.625}, {1.125, 1.500, -1.625}, {-0.625, 1.500, -1.625}, {-1.125, 1.500, -1.625}, {0.625, 1.500, 0.375}, {1.125, 1.500, 0.375}, {-0.625, 1.500, 0.375}, {-1.125, 1.500, 0.375}, {0.625, 1.500, 2.375}, {1.125, 1.500, 2.375}, {-0.625, 1.500, 2.375}, {-1.125, 1.500, 2.375}, {0.625, 1.500, 4.375}, {1.125, 1.500, 4.375}, {-0.625, 1.500, 4.375}, {-1.125, 1.500, 4.375}, {0.625, 1.500, 6.375}, {1.125, 1.500, 6.375}, {-0.625, 1.500, 6.375}, {-1.125, 1.500, 6.375}});
        SEATS.put("coach_mid", new double[][] {{0.625, 1.500, -5.125}, {1.125, 1.500, -5.125}, {-0.625, 1.500, -5.125}, {-1.125, 1.500, -5.125}, {0.625, 1.500, -2.625}, {1.125, 1.500, -2.625}, {-0.625, 1.500, -2.625}, {-1.125, 1.500, -2.625}, {0.625, 1.500, -0.125}, {1.125, 1.500, -0.125}, {-0.625, 1.500, -0.125}, {-1.125, 1.500, -0.125}, {0.625, 1.500, 2.375}, {1.125, 1.500, 2.375}, {-0.625, 1.500, 2.375}, {-1.125, 1.500, 2.375}, {0.625, 1.500, 4.875}, {1.125, 1.500, 4.875}, {-0.625, 1.500, 4.875}, {-1.125, 1.500, 4.875}});
        SEATS.put("freight_loco", new double[][] {{0.600, 2.150, -8.000}, {-0.600, 2.150, -8.000}});
        SEATS.put("metro_car", new double[][] {{0.625, 1.500, -6.125}, {1.125, 1.500, -6.125}, {-0.625, 1.500, -6.125}, {-1.125, 1.500, -6.125}, {0.625, 1.500, -3.875}, {1.125, 1.500, -3.875}, {-0.625, 1.500, -3.875}, {-1.125, 1.500, -3.875}, {0.625, 1.500, -1.625}, {1.125, 1.500, -1.625}, {-0.625, 1.500, -1.625}, {-1.125, 1.500, -1.625}, {0.625, 1.500, 0.625}, {1.125, 1.500, 0.625}, {-0.625, 1.500, 0.625}, {-1.125, 1.500, 0.625}, {0.625, 1.500, 2.875}, {1.125, 1.500, 2.875}, {-0.625, 1.500, 2.875}, {-1.125, 1.500, 2.875}, {0.625, 1.500, 5.125}, {1.125, 1.500, 5.125}, {-0.625, 1.500, 5.125}, {-1.125, 1.500, 5.125}});
        SEATS.put("metro_head", new double[][] {{0.600, 2.050, 4.600}, {-0.600, 2.050, 4.600}});
        SEATS.put("steam_classic", new double[][] {{0.600, 2.150, -1.000}, {-0.600, 2.150, -1.000}});
        SEATS.put("steam_modern", new double[][] {{0.600, 2.150, -1.000}, {-0.600, 2.150, -1.000}});
        BUTTONS.put("freight_loco", new double[] {0.000, 3.000, -6.500});
        BUTTONS.put("metro_head", new double[] {0.000, 3.000, 6.250});
        BUTTONS.put("steam_classic", new double[] {0.000, 2.750, 1.250});
        BUTTONS.put("steam_modern", new double[] {0.000, 2.750, 1.250});
    }

    private TrainSeats() {
    }

    public static double[][] of(String variantId) {
        return SEATS.getOrDefault(variantId, NONE);
    }

    /** V6.2.0: posición (x, y, z en metros) del botón de emergencia del panel de la cabina, o null si el coche no tiene. */
    public static double[] button(String variantId) {
        return BUTTONS.get(variantId);
    }
}
