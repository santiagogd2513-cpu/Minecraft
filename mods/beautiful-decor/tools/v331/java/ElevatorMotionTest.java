package com.example.beautifuldecor.elevator;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba de la V3.3.1 sin Minecraft: usa el ElevatorMotion REAL (el mismo que usa la cabina).
 * <ol>
 *   <li>Viajes completos a todas las velocidades del menú (1 a 20 bloques por segundo, de medio en medio) y muchas distancias, subiendo y
 *       bajando: llega exacto al piso, nunca se pasa ni da marcha atrás, y el último trozo (menos de ARRIVE_SNAP) se da de golpe: la
 *       cabina pasa a lo sumo UN tick a menos de ARRIVE_SNAP del piso (antes se arrastraba varios ticks a 0,02 bloques por tick).</li>
 *   <li>Un pasajero con gravedad en una cabina cuya colisión falla en algunos ticks (como la caja que se pierde un tick): con el
 *       "pegado al suelo" nunca se hunde ni sale por debajo, a ninguna velocidad; sin él (la V3.3.0) a poca velocidad sí salía por
 *       debajo (esto se imprime, para ver que la prueba mide lo que debe).</li>
 *   <li>El trecho que le falta al cliente (atrasado unos ticks) cuando el servidor quita la cabina cabe en CLIENT_CATCH_UP.</li>
 *   <li>Los bordes de standing / liftedFeet.</li>
 * </ol>
 */
public class ElevatorMotionTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    // ------------------------------------------------------------------------------------------------ viajes

    /** Posición (relativa al piso de salida) al empezar cada tick de un viaje; el último tick es el que llega. */
    private static List<Double> trip(double bps, double dist) {
        double speed = bps / 20.0;
        double y = 0.0;
        List<Double> starts = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            starts.add(y);
            double remaining = dist - y;
            if (Math.abs(remaining) < ElevatorMotion.ARRIVED) {
                return starts;
            }
            double dy = ElevatorMotion.stepFor(speed, Math.abs(y), remaining);
            boolean last = ElevatorMotion.isLastStep(remaining);
            check(Math.signum(dy) == Math.signum(remaining), "el paso va hacia el piso (bps " + bps + ", dist " + dist + ")");
            check(Math.abs(dy) <= Math.abs(remaining) + 1e-12, "el paso no se pasa del piso (bps " + bps + ", dist " + dist + ")");
            if (!last) {
                check(Math.abs(dy) <= speed + 1e-12, "un paso normal no supera la velocidad configurada (bps " + bps + ")");
            } else {
                check(Math.abs(dy) <= ElevatorMotion.ARRIVE_SNAP + 1e-12, "el último paso es de ARRIVE_SNAP o menos");
                y += dy;
                check(Math.abs(y - dist) < 1e-9, "el último paso llega EXACTO al piso (bps " + bps + ", dist " + dist + ", y " + y + ")");
                return starts;
            }
            y += dy;
        }
        throw new AssertionError("el viaje no termina (bps " + bps + ", dist " + dist + ")");
    }

    /** Cuántos ticks pasa la cabina a menos de {@code zone} bloques del piso (contando el tick en que llega), con la fórmula de la V3.3.0. */
    private static int oldTail(double bps, double dist, double zone) {
        double speed = bps / 20.0;
        double y = 0.0;
        int tail = 0;
        for (int i = 0; i < 1_000_000; i++) {
            double remaining = dist - y;
            if (Math.abs(remaining) < 1.0E-4) {
                return tail + 1;
            }
            if (Math.abs(remaining) <= zone) {
                tail++;
            }
            double v = Math.min(speed, Math.max(0.02, 0.35 * Math.min(Math.abs(y), Math.abs(remaining))));
            y += Math.signum(remaining) * Math.min(v, Math.abs(remaining));
        }
        throw new AssertionError("el viaje viejo no termina");
    }

    private static void testTrips() {
        double[] distances = {1, 2, 3, 4, 5, 7, 12, 24, 40, 100, 250, -1, -2, -3, -5, -12, -40, -100, 0.5, -0.5};
        int trips = 0;
        int worstTail = 0;
        int worstOld = 0;
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : distances) {
                List<Double> starts = trip(bps, dist);
                int near = 0;
                for (double y : starts) {
                    if (Math.abs(dist - y) <= ElevatorMotion.ARRIVE_SNAP) {
                        near++;
                    }
                }
                // el primer tick (y = 0) cuenta solo si el viaje es más corto que ARRIVE_SNAP; los viajes de prueba no lo son, salvo 0,5 (no entra)
                check(near == 1, "la cabina pasa " + near + " ticks a menos de ARRIVE_SNAP del piso (bps " + bps + ", dist " + dist + "); debe ser 1");
                worstTail = Math.max(worstTail, near);
                int old = oldTail(bps, dist, ElevatorMotion.ARRIVE_SNAP);
                check(near < old, "el final nuevo (" + near + " ticks) no es más corto que el viejo (" + old + ") en bps " + bps + ", dist " + dist);
                worstOld = Math.max(worstOld, old);
                int bound = (int) Math.ceil(Math.abs(dist) / ElevatorMotion.MIN_STEP) + 2;
                check(starts.size() <= bound, "el viaje tarda demasiado: " + starts.size() + " ticks (bps " + bps + ", dist " + dist + ")");
                trips++;
            }
        }
        // el viaje de ida y de vuelta es simétrico en duración
        check(trip(4.0, 12).size() == trip(4.0, -12).size(), "subir y bajar la misma distancia tardan lo mismo");
        System.out.println("viajes: " + trips + " (1 a 20 b/s, 20 distancias); ticks cerca del piso: antes hasta " + worstOld + ", ahora " + worstTail);
    }

    // ------------------------------------------------------------------------------------------------ pasajero

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double FLOOR_TOP = 1.0;
    private static final double SIZE_Y = 4.0;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final int WAIT = 24;

    /** Resultado de un viaje con pasajero. */
    private static final class Ride {
        boolean left;          // salió por debajo de la cabina
        double worstGap;       // lo más hundido que estuvo (pies - superficie, negativo = hundido), al final de un tick
        int leftAt = -1;
    }

    /**
     * Un pasajero con gravedad sobre una cabina que sube o baja {@code dist} bloques. Orden de ticks como en el cliente: primero el
     * jugador, luego la cabina. {@code glitch}: ticks en que la colisión de la cabina no existe (la caja perdida). {@code glue}: el
     * "pegado al suelo" de la V3.3.1.
     */
    private static Ride ride(double bps, double dist, boolean glue, int... glitch) {
        double speed = bps / 20.0;
        double bottom = 0.0;
        double top = bottom + FLOOR_TOP;
        double feet = top;
        double dm = 0.0;
        double start = 0.0;
        int wait = WAIT;
        Ride result = new Ride();
        boolean arrived = false;
        for (int tick = 0; tick < 100_000 && !arrived; tick++) {
            boolean glitchy = false;
            for (int g : glitch) {
                glitchy |= g == tick;
            }
            // 1) el jugador (gravedad + colisión con el suelo de la cabina, que solo detiene a quien viene de arriba)
            dm = (dm - GRAVITY) * DRAG;
            double next = feet + dm;
            if (!glitchy && feet >= top - 1e-7 && next < top) {
                next = top;
                dm = 0.0;
            }
            feet = next;
            // 2) la cabina
            double dy = 0.0;
            boolean last = false;
            if (wait > 0) {
                wait--;
            } else {
                double remaining = dist - (bottom - start);
                if (Math.abs(remaining) < ElevatorMotion.ARRIVED) {
                    break;
                }
                dy = ElevatorMotion.stepFor(speed, Math.abs(bottom - start), remaining);
                last = ElevatorMotion.isLastStep(remaining);
            }
            boolean inZone = feet < bottom + SIZE_Y && feet + PLAYER_HEIGHT > bottom;
            if (dy > 0.0) {
                if (inZone) {
                    feet += dy;
                }
                bottom += dy;
            } else if (dy < 0.0) {
                bottom += dy;
                if (inZone) {
                    feet += dy;
                }
            }
            top = bottom + FLOOR_TOP;
            if (glue && ElevatorMotion.standing(feet, bottom, top)) {
                feet = ElevatorMotion.liftedFeet(feet, bottom, top);
                if (dm < 0.0) {
                    dm = 0.0;
                }
            }
            result.worstGap = Math.min(result.worstGap, feet - top);
            if (feet + PLAYER_HEIGHT <= bottom && !result.left) {
                result.left = true;
                result.leftAt = tick;
            }
            if (last) {
                arrived = true;
            }
        }
        return result;
    }


    /** "1.0 a 20.0 (39 velocidades)" a partir de una lista de números separados por espacios. */
    private static String range(StringBuilder list) {
        String[] parts = list.toString().trim().split(" ");
        return parts[0] + " a " + parts[parts.length - 1] + " (" + parts.length + " velocidades)";
    }

    private static void testRiders() {
        double[] distances = {3, 12, 40, -3, -12, -40};
        int[][] glitches = {
                {},                       // la colisión nunca falla
                {5},                      // falla un tick durante la espera
                {WAIT},                   // falla justo al arrancar
                {3, WAIT + 2, WAIT + 20}, // varios
                {WAIT + 1, WAIT + 2, WAIT + 3, WAIT + 4, WAIT + 5, WAIT + 6, WAIT + 7, WAIT + 8}, // 8 ticks seguidos (muy mal)
        };
        int cases = 0;
        int plainLeft = 0;
        int plainTotal = 0;
        StringBuilder slowLeft = new StringBuilder();
        StringBuilder fastKept = new StringBuilder();
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : distances) {
                for (int[] glitch : glitches) {
                    Ride with = ride(bps, dist, true, glitch);
                    check(!with.left, "con el pegado al suelo el pasajero NO sale por debajo (bps " + bps + ", dist " + dist + ", fallos " + glitch.length + ")");
                    check(with.worstGap > -(GRAVITY + 1e-9) * 1.0 - 1e-9 || glitch.length > 3, "con el pegado al suelo el pasajero se hunde más de un tick de gravedad (bps " + bps + ", gap " + with.worstGap + ")");
                    cases++;
                    if (glitch.length > 0) {
                        Ride without = ride(bps, dist, false, glitch);
                        plainTotal++;
                        if (without.left) {
                            plainLeft++;
                            if (dist == 12 && glitch.length == 1 && glitch[0] == WAIT) {
                                slowLeft.append(bps).append(' ');
                            }
                        } else if (dist == 12 && glitch.length == 1 && glitch[0] == WAIT) {
                            fastKept.append(bps).append(' ');
                        }
                    }
                }
            }
        }
        // sin fallos de colisión no hay nada que arreglar: sin pegado tampoco se hunde
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : distances) {
                Ride plain = ride(bps, dist, false);
                check(!plain.left && plain.worstGap > -1e-9, "sin fallos de colisión el pasajero va sobre el suelo aun sin el pegado (bps " + bps + ")");
                Ride glued = ride(bps, dist, true);
                check(!glued.left && glued.worstGap > -1e-9, "el pegado no estorba cuando la colisión funciona (bps " + bps + ")");
                cases += 2;
            }
        }
        // lo que se quería ver: a poca velocidad, sin el pegado, un fallo de un tick al arrancar SÍ deja pasar al jugador
        check(plainLeft > 0, "la prueba no reproduce el problema (nadie sale por debajo sin el pegado)");
        check(ride(1.0, 12, false, WAIT).left, "sin el pegado, a 1 b/s un fallo al arrancar deja salir al jugador por debajo");
        check(!ride(1.0, 12, true, WAIT).left, "con el pegado, a 1 b/s el mismo fallo no lo deja salir");
        System.out.println("pasajeros: " + cases + " casos; sin el pegado salían por debajo " + plainLeft + " de " + plainTotal
                + " viajes con fallos de colisión");
        System.out.println("  fallo de un tick al arrancar, 12 bloques, SIN el pegado: salía por debajo a " + range(slowLeft)
                + " b/s; se salvaba a " + (fastKept.length() == 0 ? "ninguna velocidad" : range(fastKept) + " b/s"));
    }

    // ------------------------------------------------------------------------------------------------ cliente atrasado

    private static void testClientCatchUp() {
        int worstLag = 4; // ticks de atraso del cliente (200 ms) que se quieren cubrir
        double worstRest = 0.0;
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : new double[]{1, 2, 3, 12, 40, 100, -1, -3, -40}) {
                List<Double> starts = trip(bps, dist);
                int serverLast = starts.size() - 1; // el tick en que el servidor da el último paso y quita la cabina
                for (int lag = 0; lag <= worstLag; lag++) {
                    int clientTick = Math.max(0, serverLast - lag);
                    double rest = Math.abs(dist - starts.get(clientTick));
                    worstRest = Math.max(worstRest, rest);
                    check(rest <= ElevatorMotion.CLIENT_CATCH_UP, "al quitar el servidor la cabina, a un cliente atrasado " + lag + " ticks le faltan " + rest
                            + " bloques (más que CLIENT_CATCH_UP) en bps " + bps + ", dist " + dist);
                }
            }
        }
        check(ElevatorMotion.CLIENT_CATCH_UP >= ElevatorMotion.ARRIVE_SNAP * 2, "CLIENT_CATCH_UP debe ser bastante mayor que ARRIVE_SNAP");
        check(ElevatorMotion.CLIENT_CATCH_UP < 1.5, "CLIENT_CATCH_UP no debe mover al jugador más de un bloque");
        System.out.println("cliente atrasado hasta " + worstLag + " ticks: lo más que le falta al llegar es " + String.format("%.3f", worstRest)
                + " bloques (tope " + ElevatorMotion.CLIENT_CATCH_UP + ")");
    }

    // ------------------------------------------------------------------------------------------------ bordes

    private static void testEdges() {
        double bottom = 10.0;
        double top = 11.0;
        check(ElevatorMotion.liftedFeet(11.0, bottom, top) == 11.0, "de pie justo encima: no se mueve");
        check(ElevatorMotion.liftedFeet(11.0005, bottom, top) == 11.0005, "0,0005 por encima sigue de pie y no se baja");
        check(ElevatorMotion.standing(11.0005, bottom, top) && !ElevatorMotion.standing(11.01, bottom, top), "0,01 por encima ya no está de pie (salta o cae)");
        check(ElevatorMotion.liftedFeet(11.01, bottom, top) == 11.01, "quien salta no se toca");
        check(ElevatorMotion.liftedFeet(10.92, bottom, top) == 11.0, "hundido 0,08: sube a la superficie");
        check(ElevatorMotion.liftedFeet(10.3, bottom, top) == 11.0, "hundido 0,7: sube a la superficie");
        check(ElevatorMotion.liftedFeet(10.0, bottom, top) == 11.0, "con los pies en la base del suelo: sube a la superficie");
        check(ElevatorMotion.liftedFeet(9.9995, bottom, top) == 11.0, "0,0005 por debajo de la base cuenta como en el suelo (tolerancia)");
        check(ElevatorMotion.liftedFeet(9.9, bottom, top) == 9.9, "por debajo de la cabina no se sube a nadie (está debajo, no encima)");
        check(ElevatorMotion.liftedFeet(5.0, bottom, top) == 5.0, "lejos por debajo: no se toca");
        check(ElevatorMotion.stepFor(0.05, 0.0, 3.0) == 0.02, "arranca a MIN_STEP");
        check(ElevatorMotion.stepFor(0.05, 5.0, 3.0) == 0.05, "a velocidad de crucero da la velocidad configurada");
        check(ElevatorMotion.stepFor(0.05, 5.0, -3.0) == -0.05, "bajando, el paso es negativo");
        check(ElevatorMotion.stepFor(1.0, 50.0, 0.1) == 0.1, "a 0,1 del piso el paso es el que falta, de golpe");
        check(ElevatorMotion.stepFor(1.0, 50.0, -0.1) == -0.1, "bajando a 0,1 del piso también");
        check(ElevatorMotion.stepFor(1.0, 50.0, 0.2) < 0.2 && ElevatorMotion.stepFor(1.0, 50.0, 0.2) > 0.0, "a 0,2 todavía frena normal");
        check(ElevatorMotion.isLastStep(0.12) && ElevatorMotion.isLastStep(-0.12) && !ElevatorMotion.isLastStep(0.1201), "el último paso es a ARRIVE_SNAP o menos");
        check(ElevatorMotion.speedFor(0.05, 0.0, 3.0) == 0.02 && ElevatorMotion.speedFor(0.05, 100.0, 100.0) == 0.05, "speedFor: arranque y crucero");
        System.out.println("bordes: ok");
    }

    public static void main(String[] args) {
        testEdges();
        testTrips();
        testRiders();
        testClientCatchUp();
        System.out.println("ELEVATOR MOTION OK (" + checks + " comprobaciones)");
    }
}
