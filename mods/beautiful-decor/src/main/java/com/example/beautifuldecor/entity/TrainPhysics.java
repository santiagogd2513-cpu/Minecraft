package com.example.beautifuldecor.entity;

/**
 * V4.8.0 / V5.0.0: la física del tren, sin nada de Minecraft para poder probarla suelta. Usa el mismo motor de cálculo que los demás
 * vehículos ({@link VehiclePhysics}); aquí solo se arma, en cada tick, el "vehículo" que es el tren entero:
 * <ul>
 * <li>masa = locomotoras + vagones vacíos + toda la carga y los pasajeros;</li>
 * <li>la fuerza de tracción tiene tope por adherencia: solo pesan sobre las ruedas motrices las locomotoras, así que con más
 *     vagones la aceleración baja en proporción;</li>
 * <li>los frenos son los de cada coche, pensados para su peso en vacío +20 %: cargado frena mucho menos;</li>
 * <li>la pendiente de los rieles cuenta con {@link #GRADE_FACTOR} (la subida de 45° del juego se siente como una pendiente de ~5°).</li>
 * </ul>
 * Velocidad máxima: 130 km/h el de carga, 80 km/h el de pasajeros (la fija la variante de los coches).
 */
public final class TrainPhysics {
    public static final double TOP_KMH = 130.0;
    public static final double TOP_MS = TOP_KMH / 3.6;
    /** Locomotora diésel de carga: masa (kg) y potencia (W). */
    public static final double LOCO_MASS_KG = 120_000.0;
    public static final double LOCO_POWER_W = 3_000_000.0;
    /** Locomotora de vapor de pasajeros con su ténder. */
    public static final double STEAM_MASS_KG = 110_000.0;
    public static final double STEAM_POWER_W = 2_200_000.0;
    public static final double PASSENGER_TOP_KMH = 80.0;
    /** Adherencia: fracción del peso de las locomotoras que se convierte en fuerza de tracción. */
    public static final double ADHESION = 0.22;
    /** Frenado de un tren vacío con freno a fondo (m/s²) y con el freno de emergencia/mano. */
    public static final double BRAKE = 1.1;
    public static final double HAND_BRAKE = 0.6;
    /** Cuánto de la pendiente del riel se siente de verdad (el riel sube 45°). */
    public static final double GRADE_FACTOR = 0.12;
    /** Velocidad máxima en reversa (m/s). */
    public static final double REVERSE_MS = 15.0 / 3.6;

    private TrainPhysics() {
    }

    /**
     * Los parámetros del tren de este instante.
     *
     * @param emptyMassKg masa de locomotoras y vagones sin carga
     * @param locoMassKg  masa de las locomotoras que ahora mismo empujan (con combustible)
     * @param powerW      potencia total de esas locomotoras
     * @param topMs       velocidad máxima del tren (la menor de sus coches)
     */
    public static VehiclePhysics.Params params(double emptyMassKg, double locoMassKg, double powerW, double topMs) {
        double empty = Math.max(emptyMassKg, 1.0);
        double force = ADHESION * VehiclePhysics.G * Math.max(locoMassKg, 0.0);
        return new VehiclePhysics.Params(empty, Math.max(powerW, 0.0), force / empty, BRAKE, HAND_BRAKE, 7.0, 0.002, 0.03, topMs, Math.min(REVERSE_MS, topMs));
    }

    /** Parámetros del tren de carga con {@code locos} locomotoras diésel. */
    public static VehiclePhysics.Params params(double emptyMassKg, int locos) {
        return params(emptyMassKg, LOCO_MASS_KG * Math.max(locos, 0), LOCO_POWER_W * Math.max(locos, 0), TOP_MS);
    }

    /** Un tick del tren. {@code slope} ya es la pendiente real del riel; aquí se le aplica el factor. */
    public static double step(double vBt, double emptyMassKg, double totalMassKg, double locoMassKg, double powerW, double topMs, double slope,
                              int drive, boolean braking, boolean hand) {
        return VehiclePhysics.step(params(emptyMassKg, locoMassKg, powerW, topMs), vBt, totalMassKg, slope * GRADE_FACTOR, drive, braking, hand);
    }

    /** Un tick del tren de carga. */
    public static double step(double vBt, double emptyMassKg, double totalMassKg, int locos, double slope, int drive, boolean braking,
                              boolean hand) {
        return step(vBt, emptyMassKg, totalMassKg, LOCO_MASS_KG * Math.max(locos, 0), LOCO_POWER_W * Math.max(locos, 0), TOP_MS, slope, drive, braking, hand);
    }

    // ---- cargas de los vagones

    /** Unidades de carga de un objeto: los bloques compactos (carbón, diamante, hierro…) valen 9. */
    public static int units(boolean compactBlock, int count) {
        return count * (compactBlock ? 9 : 1);
    }

    /** Carbón que quema una locomotora de vapor por tick a toda potencia (unidades). */
    public static final double FUEL_PER_TICK = 0.02;
}
