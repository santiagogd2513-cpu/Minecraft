package com.example.beautifuldecor.elevator;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba de la V3.3.2 sin Minecraft: usa el ElevatorMotion REAL (el mismo que usa la cabina).
 * <ol>
 *   <li>Viajes completos a todas las velocidades del menú (1 a 20 bloques por segundo, de medio en medio) y muchas distancias, subiendo y
 *       bajando: llega exacto al piso, nunca se pasa ni da marcha atrás, ningún paso supera la velocidad configurada, y solo el último
 *       tick está a menos de ARRIVE_SNAP del piso (la cabina vuelve a ser bloques en ese mismo tick).</li>
 *   <li>Fluidez: arranca desde cero, la aceleración nunca pasa de MAX_ACCEL (ni el tirón: cómo cambia la aceleración), el arranque y el
 *       frenado duran al menos MIN_RAMP_TICKS, y es mucho más suave que la V3.3.1 (se imprime la comparación).</li>
 *   <li>Un pasajero con gravedad en una cabina cuya colisión falla en algunos ticks: con el "pegado al suelo" nunca se hunde ni sale por
 *       debajo, a ninguna velocidad (también con un suelo finito de 1/16); sin él, sí salía.</li>
 *   <li>El trecho que le falta al cliente (atrasado unos ticks) cuando el servidor quita la cabina cabe en CLIENT_CATCH_UP.</li>
 *   <li>Retomar un viaje guardado a medias (ticksFor), el cliente que va por delante o por detrás de la curva, y los bordes.</li>
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

    /** Recorrido (bloques, positivo) al empezar cada tick de un viaje; el último elemento es el tick en que llega (con su paso final). */
    private static List<Double> trip(double bps, double dist) {
        double speed = bps / 20.0;
        double distance = Math.abs(dist);
        double travelled = 0.0;
        List<Double> starts = new ArrayList<>();
        for (int ticks = 0; ticks < 1_000_000; ticks++) {
            starts.add(travelled);
            if (distance - travelled < ElevatorMotion.ARRIVED) {
                return starts;
            }
            double dy = ElevatorMotion.stepFor(speed, distance, travelled, ticks);
            boolean last = ElevatorMotion.isLastStep(speed, distance, travelled, ticks);
            check(dy >= 0.0, "el paso nunca es negativo (bps " + bps + ", dist " + dist + ")");
            check(dy <= distance - travelled + 1e-12, "el paso no se pasa del piso (bps " + bps + ", dist " + dist + ")");
            if (!last) {
                check(dy <= speed + 1e-12, "un paso normal no supera la velocidad configurada (bps " + bps + ", paso " + dy + ")");
            } else {
                travelled += dy;
                check(Math.abs(travelled - distance) < 1e-9, "el último paso llega EXACTO al piso (bps " + bps + ", dist " + dist + ", llevaba " + travelled + ")");
                starts.add(travelled);
                return starts;
            }
            travelled += dy;
        }
        throw new AssertionError("el viaje no termina (bps " + bps + ", dist " + dist + ")");
    }

    /** Pasos de cada tick de un viaje (el último incluido). */
    private static List<Double> steps(List<Double> starts) {
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) {
            out.add(starts.get(i) - starts.get(i - 1));
        }
        return out;
    }

    /** Los pasos de la V3.3.1 (la rampa en la distancia), para comparar. */
    private static List<Double> oldSteps(double bps, double dist) {
        double speed = bps / 20.0;
        double distance = Math.abs(dist);
        double y = 0.0;
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 1_000_000; i++) {
            double remaining = distance - y;
            if (remaining < 1.0E-4) {
                return out;
            }
            double dy;
            if (remaining <= 0.12) {
                dy = remaining; // el último paso de golpe
            } else {
                double v = Math.min(speed, Math.max(0.02, 0.35 * Math.min(y, remaining)));
                dy = Math.min(v, remaining);
            }
            out.add(dy);
            y += dy;
            if (remaining <= 0.12) {
                return out;
            }
        }
        throw new AssertionError("el viaje viejo no termina");
    }

    private static final double[] DISTANCES = {1, 2, 3, 4, 5, 7, 12, 24, 40, 100, 250, -1, -2, -3, -5, -12, -40, -100, 0.5, -0.5, 0.05, 0.03};

    private static void testTrips() {
        int trips = 0;
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : DISTANCES) {
                double distance = Math.abs(dist);
                double speed = bps / 20.0;
                List<Double> starts = trip(bps, dist);
                // solo el último tick está a menos de ARRIVE_SNAP del piso (salvo que el viaje entero sea tan corto)
                int near = 0;
                for (int i = 0; i < starts.size() - 1; i++) {
                    if (distance - starts.get(i) <= ElevatorMotion.ARRIVE_SNAP) {
                        near++;
                    }
                }
                if (distance > 0.2) {
                    int nearAbs = 0;
                    for (int i = 0; i < starts.size() - 1; i++) {
                        if (distance - starts.get(i) <= 0.04 + 1e-9) {
                            nearAbs++;
                        }
                    }
                    check(nearAbs <= 1, "la cabina pasa " + nearAbs + " ticks a menos de 0,04 bloques del piso antes de llegar (bps " + bps + ", dist " + dist + "): se arrastra");
                }
                if (distance > ElevatorMotion.ARRIVE_SNAP * 3) {
                    check(near <= 1, "la cabina pasa " + near + " ticks a menos de ARRIVE_SNAP antes de llegar (bps " + bps + ", dist " + dist + ")");
                }
                // el último paso es corto: lo que le falta al llegar a ARRIVE_SNAP más un paso normal
                List<Double> steps = steps(starts);
                double lastStep = steps.get(steps.size() - 1);
                if (steps.size() > 1) {
                    double before = steps.get(steps.size() - 2);
                    check(lastStep <= ElevatorMotion.ARRIVE_SNAP + before + 1e-9, "el último paso es demasiado grande: " + lastStep + " (bps " + bps + ", dist " + dist + ")");
                }
                // no tarda más de lo razonable: lo ideal (sin rampas) más las dos rampas y un poco
                double peak = ElevatorMotion.peakSpeed(speed, distance);
                double ramp = ElevatorMotion.rampTicks(peak);
                double ideal = distance / speed;
                check(starts.size() <= ideal + 2.0 * ramp + 6, "el viaje tarda demasiado: " + starts.size() + " ticks (bps " + bps + ", dist " + dist + ")");
                check(peak <= speed + 1e-12 && peak > 0.0, "la velocidad de crucero no pasa de la configurada (bps " + bps + ")");
                check(peak * ramp <= distance + 1e-9 || distance <= 0.0, "las dos rampas caben en el viaje (bps " + bps + ", dist " + dist + ")");
                trips++;
            }
        }
        check(trip(4.0, 12).size() == trip(4.0, -12).size(), "subir y bajar la misma distancia tardan lo mismo");
        System.out.println("viajes: " + trips + " (1 a 20 b/s, " + DISTANCES.length + " distancias): todos llegan exactos, sin pasarse, un solo tick cerca del piso");
    }

    // ------------------------------------------------------------------------------------------------ fluidez

    private static double worstAccel(List<Double> steps) {
        double worst = 0.0;
        for (int i = 1; i < steps.size() - 1; i++) { // el último paso (el de golpe) no cuenta
            worst = Math.max(worst, Math.abs(steps.get(i) - steps.get(i - 1)));
        }
        return worst;
    }

    private static double worstJerk(List<Double> steps) {
        double worst = 0.0;
        for (int i = 2; i < steps.size() - 2; i++) {
            double a1 = steps.get(i) - steps.get(i - 1);
            double a0 = steps.get(i - 1) - steps.get(i - 2);
            worst = Math.max(worst, Math.abs(a1 - a0));
        }
        return worst;
    }

    private static void testSmooth() {
        double worstNew = 0.0;
        double worstOld = 0.0;
        double worstJerkNew = 0.0;
        double worstJerkOld = 0.0;
        double slowestStart = 0.0;
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : new double[]{3, 5, 12, 24, 40, 100, -12, -40}) {
                double distance = Math.abs(dist);
                List<Double> starts = trip(bps, dist);
                List<Double> steps = steps(starts);
                double speed = bps / 20.0;
                check(steps.get(0) < 0.001, "arranca desde cero: el primer paso es " + steps.get(0) + " (bps " + bps + ", dist " + dist + ")");
                slowestStart = Math.max(slowestStart, steps.get(0));
                double accel = worstAccel(steps);
                check(accel <= ElevatorMotion.MAX_ACCEL * 1.001 + 1e-9, "la aceleración " + accel + " pasa de MAX_ACCEL (bps " + bps + ", dist " + dist + ")");
                worstNew = Math.max(worstNew, accel);
                double jerk = worstJerk(steps);
                worstJerkNew = Math.max(worstJerkNew, jerk);
                // el tirón (cómo cambia la aceleración) es una fracción de la aceleración máxima
                check(jerk <= ElevatorMotion.MAX_ACCEL * 0.2, "el tirón " + jerk + " es demasiado fuerte (bps " + bps + ", dist " + dist + ")");
                // el arranque dura lo menos MIN_RAMP_TICKS: en ese tiempo todavía no ha llegado a la velocidad de crucero, salvo viaje corto
                double peak = ElevatorMotion.peakSpeed(speed, distance);
                int rampTicks = (int) Math.floor(ElevatorMotion.rampTicks(peak));
                check(rampTicks >= ElevatorMotion.MIN_RAMP_TICKS, "la rampa dura menos de MIN_RAMP_TICKS");
                if (steps.size() > rampTicks + 2) {
                    check(steps.get(Math.max(0, rampTicks / 2)) < peak * 0.99, "a mitad de la rampa todavía no va a velocidad de crucero");
                }
                List<Double> old = oldSteps(bps, dist);
                worstOld = Math.max(worstOld, worstAccel(old));
                worstJerkOld = Math.max(worstJerkOld, worstJerk(old));
            }
        }
        // a poca velocidad el cambio de velocidad en un tick era grande comparado con la propia velocidad: ahora no
        List<Double> slowOld = oldSteps(1.0, 12);
        List<Double> slowNew = steps(trip(1.0, 12));
        double jumpOld = worstAccel(slowOld) / 0.05;
        double jumpNew = worstAccel(slowNew) / 0.05;
        check(jumpNew * 2.5 < jumpOld, "a 1 b/s el cambio de velocidad por tick debía ser mucho menor que antes");
        check(worstJerkNew < worstJerkOld, "el tirón nuevo debía ser menor que el de antes");
        System.out.println(String.format("fluidez: mayor aceleración por tick: antes %.4f, ahora %.4f (tope %.4f); mayor tirón: antes %.5f, ahora %.5f; a 1 b/s el mayor salto de velocidad en un tick era %.0f%% de la velocidad, ahora %.1f%%",
                worstOld, worstNew, ElevatorMotion.MAX_ACCEL, worstJerkOld, worstJerkNew, jumpOld * 100, jumpNew * 100));
        System.out.println(String.format("  duración: 12 bloques a 1 b/s: %d ticks (antes %d); 12 bloques a 20 b/s: %d ticks (antes %d); 100 bloques a 20 b/s: %d ticks (antes %d)",
                trip(1.0, 12).size() - 1, oldSteps(1.0, 12).size(), trip(20.0, 12).size() - 1, oldSteps(20.0, 12).size(),
                trip(20.0, 100).size() - 1, oldSteps(20.0, 100).size()));
    }

    // ------------------------------------------------------------------------------------------------ pasajero

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
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
     * "pegado al suelo". {@code floorTop}: grosor del suelo (1 = un bloque, 0.0625 = una alfombra).
     */
    private static Ride ride(double bps, double dist, boolean glue, double floorTop, int... glitch) {
        double speed = bps / 20.0;
        double distance = Math.abs(dist);
        double dir = Math.signum(dist);
        double bottom = 0.0;
        double top = bottom + floorTop;
        double feet = top;
        double dm = 0.0;
        double travelled = 0.0;
        int moveTicks = 0;
        int wait = WAIT;
        Ride result = new Ride();
        boolean arrived = false;
        for (int tick = 0; tick < 1_000_000 && !arrived; tick++) {
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
                if (distance - travelled < ElevatorMotion.ARRIVED) {
                    break;
                }
                double step = ElevatorMotion.stepFor(speed, distance, travelled, moveTicks);
                last = ElevatorMotion.isLastStep(speed, distance, travelled, moveTicks);
                travelled += step;
                moveTicks++;
                dy = dir * step;
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
            top = bottom + floorTop;
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

    private static void testRiders() {
        double[] distances = {3, 12, 40, -3, -12, -40};
        int[][] glitches = {
                {},                       // la colisión nunca falla
                {5},                      // falla un tick durante la espera
                {WAIT},                   // falla justo al arrancar
                {3, WAIT + 2, WAIT + 20}, // varios
                {WAIT + 1, WAIT + 2, WAIT + 3, WAIT + 4, WAIT + 5, WAIT + 6, WAIT + 7, WAIT + 8}, // 8 ticks seguidos (muy mal)
        };
        double[] floors = {1.0, 0.5, 0.0625};
        int cases = 0;
        int plainLeft = 0;
        int plainTotal = 0;
        for (double floorTop : floors) {
            for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
                for (double dist : distances) {
                    for (int[] glitch : glitches) {
                        Ride with = ride(bps, dist, true, floorTop, glitch);
                        check(!with.left, "con el pegado al suelo el pasajero NO sale por debajo (bps " + bps + ", dist " + dist + ", suelo " + floorTop + ", fallos " + glitch.length + ")");
                        check(with.worstGap > -(GRAVITY + 1e-9) || glitch.length > 3, "con el pegado el pasajero se hunde más de un tick de gravedad (bps " + bps + ", gap " + with.worstGap + ")");
                        cases++;
                        if (glitch.length > 0) {
                            Ride without = ride(bps, dist, false, floorTop, glitch);
                            plainTotal++;
                            if (without.left) {
                                plainLeft++;
                            }
                        }
                    }
                }
            }
        }
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : distances) {
                Ride plain = ride(bps, dist, false, 1.0);
                check(!plain.left && plain.worstGap > -1e-9, "sin fallos de colisión el pasajero va sobre el suelo aun sin el pegado (bps " + bps + ")");
                Ride glued = ride(bps, dist, true, 1.0);
                check(!glued.left && glued.worstGap > -1e-9, "el pegado no estorba cuando la colisión funciona (bps " + bps + ")");
                cases += 2;
            }
        }
        check(plainLeft > 0, "la prueba no reproduce el problema (nadie sale por debajo sin el pegado)");
        check(ride(1.0, 12, false, 1.0, WAIT).left, "sin el pegado, a 1 b/s un fallo al arrancar deja salir al jugador por debajo");
        check(!ride(1.0, 12, true, 1.0, WAIT).left, "con el pegado, a 1 b/s el mismo fallo no lo deja salir");
        check(ride(1.0, 12, false, 0.0625, WAIT).left && !ride(1.0, 12, true, 0.0625, WAIT).left, "con un suelo de 1/16 el pegado también lo sostiene");
        System.out.println("pasajeros: " + cases + " casos (suelos de 1, 1/2 y 1/16); sin el pegado salían por debajo " + plainLeft + " de " + plainTotal
                + " viajes con fallos de colisión; con el pegado, ninguno");
    }

    // ------------------------------------------------------------------------------------------------ cliente atrasado

    private static void testClientCatchUp() {
        int worstLag = 4; // ticks de atraso del cliente (200 ms) que se quieren cubrir
        double worstRest = 0.0;
        for (double bps = 1.0; bps <= 20.0; bps += 0.5) {
            for (double dist : new double[]{1, 2, 3, 12, 40, 100, -1, -3, -40}) {
                double distance = Math.abs(dist);
                List<Double> starts = trip(bps, dist);
                int serverLast = starts.size() - 2; // el tick en que el servidor da el último paso y quita la cabina
                for (int lag = 0; lag <= worstLag; lag++) {
                    int clientTick = Math.max(0, serverLast - lag);
                    double rest = distance - starts.get(clientTick);
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

    // ------------------------------------------------------------------------------------------------ retomar, desfases y bordes

    private static void testResume() {
        int resumed = 0;
        for (double bps = 1.0; bps <= 20.0; bps += 1.5) {
            for (double dist : new double[]{3, 12, 40}) {
                double speed = bps / 20.0;
                for (int t = 0; t < 400; t += 7) {
                    double s = ElevatorMotion.travelledAt(speed, dist, t);
                    if (s >= dist) {
                        break;
                    }
                    int back = ElevatorMotion.ticksFor(speed, dist, s);
                    check(Math.abs(back - t) <= 1 || Math.abs(ElevatorMotion.travelledAt(speed, dist, back) - s) < 1e-9,
                            "ticksFor no recupera el tick " + t + " (devolvió " + back + ", bps " + bps + ", dist " + dist + ")");
                    resumed++;
                }
            }
        }
        // un cliente que va por delante de la curva espera sin retroceder ni moverse de más; uno que va por detrás se pone al día
        double speed = 0.1;
        check(ElevatorMotion.stepFor(speed, 12.0, 3.0, 5) == 0.0, "por delante de la curva: no avanza hasta que la curva lo alcance");
        double behind = ElevatorMotion.stepFor(speed, 12.0, 0.0, 100);
        check(behind > 0.0 && behind > ElevatorMotion.travelledAt(speed, 12.0, 101) - ElevatorMotion.travelledAt(speed, 12.0, 100) - 1e-12,
                "por detrás de la curva: se pone al día");
        check(ElevatorMotion.stepFor(speed, 12.0, 12.0, 5) == 0.0, "ya en el piso: no avanza");
        check(ElevatorMotion.ticksFor(speed, 12.0, 0.0) == 0, "al principio: tick 0");
        System.out.println("retomar viajes guardados: " + resumed + " casos ok");
    }

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
        check(ElevatorMotion.liftedFeet(9.9, bottom, top) == 11.0, "0,1 por debajo de la base cuenta como hundido (suelos finos)");
        check(ElevatorMotion.liftedFeet(9.7, bottom, top) == 9.7, "0,3 por debajo de la base es estar debajo de la cabina: no se sube a nadie");
        check(ElevatorMotion.liftedFeet(5.0, bottom, top) == 5.0, "lejos por debajo: no se toca");
        check(ElevatorMotion.stepFor(0.05, 12.0, 0.0, 0) < 1e-3, "arranca desde cero");
        check(ElevatorMotion.isLastStep(0.05, 0.03, 0.0, 0), "un viaje más corto que ARRIVE_SNAP es un solo paso");
        check(ElevatorMotion.stepFor(0.05, 0.03, 0.0, 0) == 0.03, "…y llega exacto");
        check(ElevatorMotion.stepFor(0.05, 12.0, 11.97, 50) == 0.03 + 0.0 || Math.abs(ElevatorMotion.stepFor(0.05, 12.0, 11.97, 50) - 0.03) < 1e-12,
                "a 0,03 del piso el paso es el que falta, de golpe");
        // la velocidad de crucero
        check(ElevatorMotion.peakSpeed(0.05, 100.0) == 0.05, "viaje largo, poca velocidad: va a la configurada");
        check(ElevatorMotion.peakSpeed(1.0, 100.0) < 1.0, "20 b/s en 100 bloques: no cabe acelerar hasta 20 b/s (la aceleración máxima manda)");
        check(ElevatorMotion.peakSpeed(1.0, 2000.0) == 1.0, "20 b/s en 2000 bloques: sí llega");
        check(ElevatorMotion.peakSpeed(0.05, 0.8) < 0.05, "0,8 bloques a 1 b/s: no da tiempo a llegar a 1 b/s");
        double prev = 0.0;
        for (double d = 0.1; d < 3000.0; d *= 1.3) {
            double p = ElevatorMotion.peakSpeed(1.0, d);
            check(p >= prev - 1e-12, "la velocidad de crucero crece con la distancia");
            prev = p;
        }
        check(ElevatorMotion.rampTicks(0.05) == ElevatorMotion.MIN_RAMP_TICKS && ElevatorMotion.rampTicks(0.05) >= 20.0, "a 1 b/s la rampa es la mínima y dura al menos un segundo (20 ticks)");
        check(ElevatorMotion.rampTicks(1.0) > 100, "a 20 b/s la rampa es larga (aceleración limitada)");
        check(ElevatorMotion.speedAt(0.1, 12.0, 0) < 1e-3 && ElevatorMotion.speedAt(0.1, 12.0, 10_000) == 0.0, "speedAt: parada al empezar y al acabar");
        System.out.println("bordes: ok");
    }

    public static void main(String[] args) {
        testEdges();
        testTrips();
        testSmooth();
        testRiders();
        testClientCatchUp();
        testResume();
        System.out.println("ELEVATOR MOTION OK (" + checks + " comprobaciones)");
    }
}
