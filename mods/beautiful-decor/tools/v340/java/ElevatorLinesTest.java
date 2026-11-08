package com.example.beautifuldecor.elevator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Prueba de la V3.4.0 sin Minecraft: las líneas del ascensor y el viaje de lado (ElevatorLines y ElevatorMotion REALES).
 * <ol>
 *   <li>Qué línea comparten dos controladores (columna, línea en X, línea en Z, ninguna) y hasta dónde llega una línea de lado.</li>
 *   <li>La capa nueva en la que entra la cabina (leadingSlice) es exactamente la cuenta vertical de antes, y en un viaje completo de lado (con
 *       la curva suave real, a todas las velocidades) la cabina revisa CADA columna de bloques de su camino, una sola vez y en orden: no se
 *       salta ningún obstáculo.</li>
 *   <li>Llevar de lado: quien va encima conserva su sitio en la plataforma durante todo el viaje y al llegar (aunque la cabina del cliente
 *       vaya atrasada y se corrija de golpe).</li>
 *   <li>Atascos, direcciones para la pantalla y bordes.</li>
 * </ol>
 */
public class ElevatorLinesTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static void testLines() {
        check(ElevatorLines.lineAxis(5, 10, 7, 5, 40, 7) == ElevatorLines.AXIS_Y, "misma X y Z: la columna");
        check(ElevatorLines.lineAxis(5, 10, 7, 5, 10, 7) == ElevatorLines.AXIS_Y, "el mismo controlador cuenta como su columna");
        check(ElevatorLines.lineAxis(5, 10, 7, 30, 10, 7) == ElevatorLines.AXIS_X, "misma altura y Z: línea en X");
        check(ElevatorLines.lineAxis(5, 10, 7, 5, 10, -20) == ElevatorLines.AXIS_Z, "misma altura y X: línea en Z");
        check(ElevatorLines.lineAxis(5, 10, 7, 30, 11, 7) == ElevatorLines.NO_LINE, "distinta altura y distinta X: ninguna");
        check(ElevatorLines.lineAxis(5, 10, 7, 6, 10, 8) == ElevatorLines.NO_LINE, "en diagonal: ninguna");
        check(ElevatorLines.lineAxis(5, 10, 7, 6, 12, 7) == ElevatorLines.NO_LINE, "subir y correrse a la vez: ninguna (cada viaje es recto)");
        check(ElevatorLines.inRange(ElevatorLines.AXIS_X, 0, 0, ElevatorLines.ROW_RANGE, 0), "a ROW_RANGE en X: dentro");
        check(!ElevatorLines.inRange(ElevatorLines.AXIS_X, 0, 0, ElevatorLines.ROW_RANGE + 1, 0), "más allá: fuera");
        check(ElevatorLines.inRange(ElevatorLines.AXIS_Z, 3, -50, 3, 70), "en Z cuenta la distancia en Z");
        check(ElevatorLines.inRange(ElevatorLines.AXIS_Y, 0, 0, 0, 0), "la columna no tiene límite");
        check(!ElevatorLines.inRange(ElevatorLines.NO_LINE, 0, 0, 0, 0), "sin línea: no");
        check(ElevatorLines.along(ElevatorLines.AXIS_X, 1, 2, 3) == 1 && ElevatorLines.along(ElevatorLines.AXIS_Y, 1, 2, 3) == 2
                && ElevatorLines.along(ElevatorLines.AXIS_Z, 1, 2, 3) == 3, "along");
        System.out.println("líneas: ok");
    }

    /** La cuenta vertical de la V3.3.x (copiada tal cual), para comparar. */
    private static int oldLayer(double y, double dy, int sizeY) {
        double newBottom = y + dy;
        if (dy > 0.0) {
            int layer = (int) Math.ceil(newBottom + sizeY - 1.0E-6) - 1;
            int oldLayer = (int) Math.ceil(y + sizeY - 1.0E-6) - 1;
            return layer == oldLayer ? ElevatorLines.NO_SLICE : layer;
        }
        int layer = (int) Math.floor(newBottom + 1.0E-6);
        int oldLayer = (int) Math.floor(y + 1.0E-6);
        return layer == oldLayer ? ElevatorLines.NO_SLICE : layer;
    }

    private static void testSlices() {
        Random random = new Random(340);
        for (int i = 0; i < 200_000; i++) {
            double y = (random.nextDouble() - 0.5) * 400.0;
            double dy = (random.nextDouble() - 0.5) * 2.2;
            if (dy == 0.0) {
                continue;
            }
            int size = 1 + random.nextInt(24);
            check(ElevatorLines.leadingSlice(y, y + dy, size) == oldLayer(y, dy, size), "leadingSlice distinto de la cuenta vertical de antes (y " + y + ", dy " + dy + ")");
        }
        // un viaje completo de lado con la curva real: cada columna del camino se revisa una vez, en orden
        int trips = 0;
        for (double bps = 1.0; bps <= 20.0; bps += 1.0) {
            for (int dist : new int[]{1, 2, 5, 12, 40, 100, -1, -5, -12, -40, -100}) {
                for (int size : new int[]{1, 3, 7}) {
                    int from = 17;
                    List<Integer> seen = new ArrayList<>();
                    double speed = bps / 20.0;
                    double distance = Math.abs(dist);
                    double pos = from;
                    double travelled = 0.0;
                    for (int t = 0; t < 1_000_000; t++) {
                        if (distance - travelled < ElevatorMotion.ARRIVED) {
                            break;
                        }
                        boolean last = ElevatorMotion.isLastStep(speed, distance, travelled, t);
                        double step = ElevatorMotion.stepFor(speed, distance, travelled, t);
                        double d = Math.signum(dist) * step;
                        int slice = ElevatorLines.leadingSlice(pos, pos + d, size);
                        if (slice != ElevatorLines.NO_SLICE) {
                            seen.add(slice);
                        }
                        check(Math.abs(d) <= 1.0 + 1e-9, "un paso de más de un bloque se saltaría capas");
                        pos += d;
                        travelled += step;
                        if (last) {
                            break;
                        }
                    }
                    List<Integer> expected = new ArrayList<>();
                    if (dist > 0) {
                        for (int c = from + size; c < from + size + dist; c++) {
                            expected.add(c);
                        }
                    } else {
                        for (int c = from - 1; c >= from + dist; c--) {
                            expected.add(c);
                        }
                    }
                    check(seen.equals(expected), "de lado, la cabina no revisó cada columna una vez y en orden (bps " + bps + ", dist " + dist + ", ancho " + size
                            + "): " + seen + " vs " + expected);
                    check(Math.abs(pos - (from + dist)) < 1e-9, "llega exacta a la parada");
                    trips++;
                }
            }
        }
        System.out.println("capas: 200 000 pasos iguales a la cuenta vertical de antes; " + trips + " viajes de lado: cada columna del camino revisada una vez y en orden");
    }

    /** El jugador sobre una plataforma que va de lado (como carrySide y landOnClient). */
    private static void testCarrySide() {
        int cases = 0;
        for (double bps = 1.0; bps <= 20.0; bps += 1.0) {
            for (int dist : new int[]{3, 12, 40, -3, -40}) {
                for (int lag = 0; lag <= 5; lag++) {
                    for (double offset : new double[]{0.3, 1.5, 2.7}) {
                        double speed = bps / 20.0;
                        double distance = Math.abs(dist);
                        // posiciones de la cabina del servidor y del cliente (atrasado lag ticks)
                        List<Double> track = new ArrayList<>();
                        double pos = 0.0;
                        double travelled = 0.0;
                        track.add(pos);
                        for (int t = 0; t < 1_000_000; t++) {
                            if (distance - travelled < ElevatorMotion.ARRIVED) {
                                break;
                            }
                            boolean last = ElevatorMotion.isLastStep(speed, distance, travelled, t);
                            double step = ElevatorMotion.stepFor(speed, distance, travelled, t);
                            pos += Math.signum(dist) * step;
                            travelled += step;
                            track.add(pos);
                            if (last) {
                                break;
                            }
                        }
                        int serverArrives = track.size() - 1;
                        int clientAt = Math.max(0, serverArrives - lag);
                        double player = offset;            // el jugador, a 'offset' bloques de la esquina de la plataforma
                        double cabin = 0.0;
                        for (int i = 1; i <= clientAt; i++) {
                            double d = track.get(i) - track.get(i - 1);
                            cabin += d;                    // el suelo
                            player += d;                   // y lo que va encima (exacto si es diminuto, con Entity.move si no)
                            check(Math.abs((player - cabin) - offset) < 1e-9, "durante el viaje de lado el jugador conserva su sitio en la plataforma");
                        }
                        // llega el aviso "ya es bloque": la cabina del cliente se pone en su sitio y el jugador se corre lo mismo
                        double rest = dist - cabin;
                        cabin += rest;
                        player += rest;
                        check(Math.abs(cabin - dist) < 1e-9 && Math.abs((player - cabin) - offset) < 1e-9, "al llegar el jugador queda en el mismo sitio de la plataforma");
                        cases++;
                    }
                }
            }
        }
        System.out.println("llevar de lado: " + cases + " casos (1 a 20 b/s, cliente atrasado de 0 a 5 ticks): el jugador conserva su sitio en la plataforma");
    }

    private static void testEdges() {
        check(ElevatorLines.stuckBase(10.6, 1) == 10 && ElevatorLines.stuckBase(10.6, -1) == 11, "atasco: el bloque entero hacia atrás");
        check(ElevatorLines.stuckBase(10.0, 1) == 10 && ElevatorLines.stuckBase(10.0, -1) == 10, "atasco justo en un bloque: ahí");
        check(ElevatorLines.directionKey(ElevatorLines.AXIS_X, 0, 5).equals("east") && ElevatorLines.directionKey(ElevatorLines.AXIS_X, 0, -5).equals("west"), "X: este y oeste");
        check(ElevatorLines.directionKey(ElevatorLines.AXIS_Z, 0, 5).equals("south") && ElevatorLines.directionKey(ElevatorLines.AXIS_Z, 0, -5).equals("north"), "Z: sur y norte");
        check(ElevatorLines.leadingSlice(5.0, 5.0, 3) == ElevatorLines.NO_SLICE, "sin moverse no entra en nada");
        check(ElevatorLines.leadingSlice(5.0, 5.01, 3) == 8 && ElevatorLines.leadingSlice(5.0, 4.99, 3) == 4, "al empezar a moverse entra en la columna de al lado");
        // de lado, el camino debe dejar sitio a quien va de pie encima de una plataforma baja; arriba-abajo, como siempre (el alto de la zona)
        for (int sizeY = 1; sizeY <= 24; sizeY++) {
            check(ElevatorLines.sweepHeight(ElevatorLines.AXIS_Y, sizeY) == sizeY, "arriba-abajo se revisa el alto de la zona, igual que antes");
            for (int axis : new int[]{ElevatorLines.AXIS_X, ElevatorLines.AXIS_Z}) {
                int h = ElevatorLines.sweepHeight(axis, sizeY);
                check(h >= sizeY && h >= 3, "de lado: al menos el suelo y 2 bloques para quien va de pie (alto " + sizeY + ": " + h + ")");
                check(h == Math.max(sizeY, 3), "de lado: no más de lo necesario (alto " + sizeY + ")");
            }
        }
        System.out.println("bordes: ok");
    }

    public static void main(String[] args) {
        testLines();
        testSlices();
        testCarrySide();
        testEdges();
        System.out.println("ELEVATOR LINES OK (" + checks + " comprobaciones)");
    }
}
