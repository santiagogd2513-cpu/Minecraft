package com.example.beautifuldecor.elevator;

/**
 * Las cuentas del movimiento de la cabina del ascensor, sin nada de Minecraft (así se prueban fuera del juego, ver
 * tools/v332/java/ElevatorMotionTest.java). La cabina las usa tal cual.
 * <p>
 * V3.3.2: el movimiento ya no es "una rampa en la distancia" sino una curva en el TIEMPO, como un ascensor de verdad: arranca desde cero,
 * acelera con suavidad (la aceleración sube y baja sin saltos: curva en S), va a la velocidad de crucero, y frena igual de suave hasta
 * parar en el piso. A poca velocidad la rampa dura al menos {@link #MIN_RAMP_TICKS} ticks (antes eran casi 3 ticks, por eso el arranque y
 * el frenado se sentían a golpes); a mucha velocidad la aceleración nunca pasa de {@link #MAX_ACCEL}, y en un viaje corto la cabina no
 * llega a la velocidad máxima (no hay sitio para acelerar tanto). Cliente y servidor cuentan los mismos ticks, así que van iguales.
 * <p>
 * V3.3.1 (sigue igual): quien va de pie en la cabina se pega a su suelo en cada tick ({@link #liftedFeet}), y al llegar el último trocito
 * ({@link #ARRIVE_SNAP}) se da de golpe y la cabina se vuelve bloques en ese mismo tick.
 * <p>
 * V3.3.3: la llegada sin hueco. La cabina sigue {@link #LINGER_TICKS} ticks después de volverse bloques (invisible, sosteniendo al jugador)
 * para que en el cliente los bloques aparezcan ANTES de que se quite la cabina, y al llegar el jugador se sube justo a la superficie del
 * piso nuevo ({@link #landedFeet}). Antes, a cualquier velocidad, el jugador podía quedar con los pies dentro del bloque y atravesarlo.
 */
public final class ElevatorMotion {
    /** Aceleración máxima (bloques por tick por tick). 0,015 = 6 bloques por segundo cada segundo: rápido pero sin latigazo. */
    public static final double MAX_ACCEL = 0.015;
    /** Lo menos que dura la rampa de arranque (y la de frenado), en ticks: un segundo. */
    public static final int MIN_RAMP_TICKS = 20;
    /** Menos de esto (bloques) de distancia cuenta como "ya llegó". */
    public static final double ARRIVED = 1.0E-4;
    /** Si faltan menos de esto (bloques) la cabina da el último paso de golpe y se vuelve bloques en ese mismo tick. */
    public static final double ARRIVE_SNAP = 0.04;
    /** Cuánto (bloques) por encima del suelo de la cabina se cuenta todavía como "de pie" sobre ella. */
    public static final double STAND_TOLERANCE = 1.0E-3;
    /** V3.3.2: cuánto (bloques) por DEBAJO de la base del suelo se cuenta todavía como hundido en él (suelos finos: una alfombra, una losa). */
    public static final double STAND_BELOW = 0.2;
    /**
     * V3.3.1: el cliente va un poco atrasado respecto al servidor; cuando el servidor quita la cabina, el cliente termina de llevar al
     * jugador si le faltaba menos de esto (bloques), para que los bloques nuevos lo encuentren justo encima de su suelo.
     */
    public static final double CLIENT_CATCH_UP = 1.0;
    /**
     * V3.3.3: ticks que la cabina sigue existiendo (invisible, sosteniendo a quien va encima) después de volver a ser bloques. El servidor
     * manda los bloques nuevos un tick después de ponerlos, pero el "quitar la cabina" salía al instante: en el cliente la cabina
     * desaparecía ANTES de que aparecieran los bloques, el jugador caía un poquito, quedaba con los pies dentro del bloque nuevo y lo
     * atravesaba (Minecraft no frena a quien ya está metido en un bloque). Con esta espera el cliente siempre tiene los bloques antes.
     */
    public static final int LINGER_TICKS = 4;
    /** V3.3.3: al llegar, a quien está hasta esto (bloques) por debajo de la superficie del piso nuevo se lo sube a ella. */
    public static final double LAND_WINDOW = 1.5;
    /**
     * V3.3.4: cuánto (bloques) por encima de la cabina se busca a los pasajeros. Antes la búsqueda terminaba en el techo de la zona, y en una
     * cabina que es solo una plataforma (alto 1) eso es justo la superficie: quien estaba de pie encima quedaba en el borde y NO contaba como
     * pasajero (al subir, la plataforma se le metía en los pies y temblaba; al bajar, se quedaba atrás cayendo).
     */
    public static final double RIDE_HEADROOM = 1.0;
    /**
     * V3.3.4: Minecraft (Entity.move) ignora un movimiento si su largo al cuadrado no pasa de esto (~0,0003 bloques). Los pasos así de pequeños
     * se le dan exactos al pasajero.
     */
    public static final double TINY_MOVE_SQR = 1.0E-7;

    /** Pendiente máxima de la curva suave (smootherstep) = cuánto más fuerte que la aceleración media es su punto más fuerte. */
    private static final double S_PEAK = 1.875;

    private ElevatorMotion() {
    }

    // ------------------------------------------------------------------------------------------------ la curva

    /** Duración (ticks) de la rampa de arranque o de frenado para una velocidad de crucero {@code peak} (bloques por tick). */
    public static double rampTicks(double peak) {
        return Math.max(MIN_RAMP_TICKS, S_PEAK * peak / MAX_ACCEL);
    }

    /**
     * Velocidad de crucero (bloques por tick) de un viaje de {@code distance} bloques con velocidad máxima {@code maxSpeed}: la máxima si
     * cabe acelerar y frenar, y menos si el viaje es corto (la rampa de subida y la de bajada tienen que caber en la distancia).
     */
    public static double peakSpeed(double maxSpeed, double distance) {
        double peak = maxSpeed;
        if (distance <= 0.0) {
            return peak;
        }
        if (peak * rampTicks(peak) > distance) {
            double shortRamp = distance / MIN_RAMP_TICKS;
            if (S_PEAK * shortRamp / MAX_ACCEL <= MIN_RAMP_TICKS) {
                peak = shortRamp;
            } else {
                peak = Math.sqrt(distance * MAX_ACCEL / S_PEAK);
            }
        }
        return peak;
    }

    /** ∫ de la curva suave (smootherstep) de 0 a x: vale 0,5 en x = 1. */
    private static double ease(double x) {
        double x2 = x * x;
        return x2 * x2 * (x2 - 3.0 * x + 2.5);
    }

    /** Cuántos bloques lleva recorridos la cabina después de {@code ticks} ticks de viaje (curva ideal, sin redondeos del último paso). */
    public static double travelledAt(double maxSpeed, double distance, double ticks) {
        if (ticks <= 0.0 || distance <= 0.0) {
            return 0.0;
        }
        double peak = peakSpeed(maxSpeed, distance);
        double ramp = rampTicks(peak);
        double cruise = Math.max(0.0, distance - peak * ramp);
        double cruiseTicks = cruise / peak;
        double total = 2.0 * ramp + cruiseTicks;
        if (ticks >= total) {
            return distance;
        }
        if (ticks < ramp) {
            return peak * ramp * ease(ticks / ramp);
        }
        if (ticks < ramp + cruiseTicks) {
            return peak * ramp * 0.5 + peak * (ticks - ramp);
        }
        return distance - peak * ramp * ease((total - ticks) / ramp);
    }

    /** ¿Este tick es el último del viaje? (lo que falta es tan poco que se da de golpe y la cabina vuelve a ser bloques) */
    public static boolean isLastStep(double maxSpeed, double distance, double travelled, int ticks) {
        return distance - travelled <= ARRIVE_SNAP || distance - travelledAt(maxSpeed, distance, ticks + 1.0) <= ARRIVE_SNAP;
    }

    /**
     * Lo que avanza la cabina en este tick (siempre positivo; la cabina le pone el signo según suba o baje). {@code travelled} es lo que
     * lleva recorrido de verdad y {@code ticks} los ticks de viaje que lleva: se avanza hasta donde dice la curva para el tick siguiente,
     * así que cualquier desfase se corrige solo. El último paso llega EXACTO al piso.
     */
    public static double stepFor(double maxSpeed, double distance, double travelled, int ticks) {
        double rest = distance - travelled;
        if (rest <= 0.0) {
            return 0.0;
        }
        if (isLastStep(maxSpeed, distance, travelled, ticks)) {
            return rest;
        }
        double want = travelledAt(maxSpeed, distance, ticks + 1.0);
        return Math.max(0.0, Math.min(want - travelled, rest));
    }

    /** Velocidad de la curva (bloques por tick) en este tick, para el zumbido: lo que avanzaría la cabina si todo va según lo previsto. */
    public static double speedAt(double maxSpeed, double distance, int ticks) {
        return Math.max(0.0, travelledAt(maxSpeed, distance, ticks + 1.0) - travelledAt(maxSpeed, distance, ticks));
    }

    /** Cuántos ticks de la curva corresponden a haber recorrido ya {@code travelled} bloques (para retomar un viaje guardado a medias). */
    public static int ticksFor(double maxSpeed, double distance, double travelled) {
        if (travelled <= 0.0 || distance <= 0.0) {
            return 0;
        }
        int lo = 0;
        int hi = 1;
        while (hi < 10_000_000 && travelledAt(maxSpeed, distance, hi) < travelled) {
            hi *= 2;
        }
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (travelledAt(maxSpeed, distance, mid) < travelled) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // ------------------------------------------------------------------------------------------------ quién va en la cabina

    /** V3.3.4: hasta qué altura (sobre la base de la cabina) se busca a los pasajeros: lo más alto de la zona o del suelo, más un margen. */
    public static double riderTop(int sizeY, double floorTop) {
        return Math.max(sizeY, floorTop) + RIDE_HEADROOM;
    }

    /**
     * V3.3.4: desde qué altura (respecto de la base de la cabina) se busca a los pasajeros: desde la base. Quien se hunde un poco en un suelo
     * fino igual cuenta (su cuerpo sigue dentro de la caja), y a quien está DEBAJO de la cabina no se lo arrastra.
     */
    public static double riderBottom() {
        return 0.0;
    }

    /**
     * V3.3.4: ¿cuenta como pasajero alguien cuyos pies están en {@code feet} y mide {@code height}, con la cabina en {@code bottom}? Es la
     * misma prueba que hace Minecraft al buscar entidades en una caja (el borde no cuenta: hay que meterse en ella).
     */
    public static boolean isRider(double feet, double height, double bottom, int sizeY, double floorTop) {
        return feet < bottom + riderTop(sizeY, floorTop) && feet + height > bottom + riderBottom();
    }

    // ------------------------------------------------------------------------------------------------ de pie sobre la cabina

    /** ¿Tiene alguien los pies sobre el suelo de la cabina (que va de {@code bottom} a {@code top}) o hundidos en él? */
    public static boolean standing(double feet, double bottom, double top) {
        return feet >= bottom - STAND_BELOW && feet <= top + STAND_TOLERANCE;
    }

    /** Dónde deben quedar los pies: si estaban sobre el suelo o hundidos en él, justo encima; si no, donde estaban. */
    public static double liftedFeet(double feet, double bottom, double top) {
        return standing(feet, bottom, top) && feet < top ? top : feet;
    }

    /**
     * V3.3.3: dónde deben quedar los pies cuando la cabina llega y se vuelve bloques. {@code finalTop} es la superficie del piso nuevo y
     * {@code rest} lo que le faltaba a la cabina del cliente para llegar (el cliente va unos ticks atrasado). Quien estaba debajo de la
     * superficie (hundido, o llevado por una cabina atrasada) sube justo a ella; a nadie se lo baja (quien está encima cae solo, sobre el
     * bloque, y eso Minecraft sí lo frena).
     */
    public static double landedFeet(double feet, double finalTop, double rest) {
        double window = Math.max(LAND_WINDOW, Math.abs(rest) + 0.5);
        return feet < finalTop && feet >= finalTop - window ? finalTop : feet;
    }
}
