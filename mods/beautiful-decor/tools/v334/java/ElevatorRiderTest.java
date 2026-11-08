package com.example.beautifuldecor.elevator;

/**
 * Prueba de la V3.3.4 sin Minecraft: ¿quién cuenta como pasajero? (video del 27/10, plataforma 3 x 3 x 1 a 5 bloques por segundo).
 * <p>
 * Lo que pasaba: la cabina buscaba a sus pasajeros en la caja de su zona, que terminaba en el techo de la zona. En una cabina que es solo una
 * plataforma (alto 1) ese techo es justo la superficie: quien estaba de pie encima quedaba en el borde de la caja, y Minecraft no cuenta el
 * borde. No era pasajero. Al SUBIR, la plataforma se le metía en los pies, se hundía y al tick siguiente (ya dentro de la caja) el pegado lo
 * sacaba: temblaba cada 2 ticks (en el video el borde de la plataforma oscila cada 6 fotogramas a 60 fps). Al BAJAR, la plataforma se iba y
 * él caía por gravedad, aterrizaba y volvía a caer (en el video, cada 18 fotogramas): se quedaba atrás.
 * <p>
 * Aquí se simula tick a tick un jugador con gravedad sobre cabinas de 1 a 4 de alto, con suelo de 1 o de 1/2 bloque, colisión con la cabina
 * que funciona o que no se encuentra, a todas las velocidades, subiendo y bajando; se mide, al final de cada tick (lo que se ve en pantalla),
 * la distancia entre los pies y el suelo. Con la búsqueda vieja la plataforma tiembla y deja atrás al jugador; con la nueva, cero en todos.
 */
public class ElevatorRiderTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double HEIGHT = 1.8;
    private static final int WAIT = 24;

    static final class Result {
        double worstGap;      // lo más lejos del suelo (por encima o hundido) al final de un tick de viaje
        double worstAbove;    // lo más por encima (quedarse atrás al bajar)
        double worstBelow;    // lo más hundido
        int wobbles;          // ticks en que la distancia al suelo cambió de signo o de tamaño (temblor)
        boolean leftCabin;    // quedó fuera de la cabina (cayó lejos)
    }

    private static boolean rider(boolean newSearch, double feet, double bottom, int sizeY, double floorTop) {
        if (newSearch) {
            return ElevatorMotion.isRider(feet, HEIGHT, bottom, sizeY, floorTop);
        }
        return feet < bottom + sizeY && feet + HEIGHT > bottom; // V3.3.3: la caja terminaba en el techo de la zona (el borde no cuenta)
    }

    static Result trip(double bps, double dist, int sizeY, double floorTop, boolean collides, boolean newSearch) {
        double speed = bps / 20.0;
        double distance = Math.abs(dist);
        double dir = Math.signum(dist);
        double bottom = 0.0;
        double feet = floorTop;
        double dm = 0.0;
        double travelled = 0.0;
        int moveTicks = 0;
        int wait = WAIT;
        Result r = new Result();
        double lastGap = 0.0;
        for (int tick = 0; tick < 1_000_000; tick++) {
            // 1) el jugador: gravedad y colisión con el suelo de la cabina (solo detiene a quien viene de arriba, no a quien ya está dentro)
            double top = bottom + floorTop;
            dm = (dm - GRAVITY) * DRAG;
            double next = feet + dm;
            if (collides && feet >= top - 1e-7 && next < top) {
                next = top;
                dm = 0.0;
            }
            feet = next;
            // 2) la cabina (como ElevatorCabinEntity.tick: los pasajeros se buscan antes de mover)
            boolean moving = false;
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
                moving = true;
            }
            boolean isRider = rider(newSearch, feet, bottom, sizeY, floorTop);
            // el arrastre: con la búsqueda vieja (V3.3.3) los pasos diminutos se perdían (Entity.move); con la nueva (V3.3.4) se dan exactos
            boolean tiny = dy * dy <= ElevatorMotion.TINY_MOVE_SQR;
            boolean carried = isRider && (!tiny || newSearch);
            if (dy > 0.0) {
                if (carried) {
                    feet += dy;              // primero lo que va encima
                }
                bottom += dy;                // luego el suelo
            } else if (dy < 0.0) {
                bottom += dy;                // primero el suelo
                if (carried) {
                    double newTop = bottom + floorTop;
                    double want = feet + dy; // luego lo que va encima (Entity.move, que choca con el suelo si se encuentra)
                    if (!tiny && collides && feet >= newTop - 1e-7 && want < newTop) {
                        want = newTop;
                    }
                    feet = want;
                }
            }
            top = bottom + floorTop;
            if (isRider && ElevatorMotion.standing(feet, bottom, top)) {   // keepOnFloor (exacto)
                feet = ElevatorMotion.liftedFeet(feet, bottom, top);
                if (dm < 0.0) {
                    dm = 0.0;
                }
            }
            if (moving) {
                double gap = feet - top;
                r.worstGap = Math.max(r.worstGap, Math.abs(gap));
                r.worstAbove = Math.max(r.worstAbove, gap);
                r.worstBelow = Math.max(r.worstBelow, -gap);
                if (Math.abs(gap - lastGap) > 1e-6) {
                    r.wobbles++;
                }
                lastGap = gap;
                if (feet + HEIGHT < bottom || feet > top + 3.0) {
                    r.leftCabin = true;
                }
            }
            if (last) {
                break;
            }
        }
        return r;
    }

    private static void testRiders() {
        double[] distances = {3, 12, 39, -3, -12, -39};
        int cases = 0;
        double oldWorstUp = 0.0;
        double oldWorstDown = 0.0;
        int oldWobbleUp = 0;
        int oldBadTrips = 0;
        int oldTrips = 0;
        for (int sizeY = 1; sizeY <= 4; sizeY++) {
            for (double floorTop : new double[]{1.0, 0.5}) {
                if (floorTop > sizeY) {
                    continue;
                }
                for (double bps = 1.0; bps <= 20.0; bps += 1.0) {
                    for (double dist : distances) {
                        for (boolean collides : new boolean[]{true, false}) {
                            Result now = trip(bps, dist, sizeY, floorTop, collides, true);
                            check(now.worstGap < 1e-9, "V3.3.4: el jugador no va pegado al suelo (alto " + sizeY + ", suelo " + floorTop + ", " + bps + " b/s, "
                                    + dist + " bloques, colisión " + collides + "): se separa " + now.worstGap);
                            check(!now.leftCabin, "V3.3.4: el jugador se sale de la cabina");
                            cases++;
                            Result old = trip(bps, dist, sizeY, floorTop, collides, false);
                            if (sizeY == 1 && floorTop == 1.0 && collides) {
                                // (cuando la colisión con la cabina NO se encuentra el jugador se hunde un poco en cada tick, entra en la caja y el
                                // pegado lo sostenía: por eso fallaba justo en las cabinas chicas, donde la colisión sí funciona, como en el video)
                                oldTrips++;
                                if (old.worstGap > 0.01) {
                                    oldBadTrips++;
                                }
                                if (dist > 0) {
                                    oldWorstUp = Math.max(oldWorstUp, old.worstBelow);
                                    oldWobbleUp = Math.max(oldWobbleUp, old.wobbles);
                                } else {
                                    oldWorstDown = Math.max(oldWorstDown, old.worstAbove);
                                }
                            }
                        }
                    }
                }
            }
        }
        // lo que se veía en el video: plataforma de alto 1, 5 b/s, subiendo tiembla y bajando se queda atrás
        Result upOld = trip(5.0, 39, 1, 1.0, true, false);
        Result downOld = trip(5.0, -39, 1, 1.0, true, false);
        check(upOld.worstBelow > 0.05 && upOld.wobbles > 50, "la prueba no reproduce el temblor al subir (hundido " + upOld.worstBelow + ", cambios " + upOld.wobbles + ")");
        check(downOld.worstAbove > 0.1, "la prueba no reproduce el quedarse atrás al bajar (" + downOld.worstAbove + ")");
        check(oldBadTrips == oldTrips && oldTrips > 0, "con la búsqueda vieja, en una plataforma de alto 1 con colisión, todos los viajes debían fallar (" + oldBadTrips + " de " + oldTrips + ")");
        Result upNew = trip(5.0, 39, 1, 1.0, true, true);
        Result downNew = trip(5.0, -39, 1, 1.0, true, true);
        check(upNew.worstGap < 1e-9 && downNew.worstGap < 1e-9 && upNew.wobbles == 0 && downNew.wobbles == 0, "con la búsqueda nueva, el caso del video va liso");
        System.out.println("pasajeros: " + cases + " viajes (cabinas de alto 1 a 4, suelo de 1 y 1/2, 1 a 20 b/s, subiendo y bajando, colisión sí/no): "
                + "el jugador va pegado al suelo en TODOS (0 de separación al final de cada tick)");
        System.out.println(String.format("  antes, plataforma de alto 1 (colisión normal, como en el video): fallaban %d de %d viajes; al subir se hundía hasta %.3f bloques y temblaba (%d cambios en un viaje);"
                + " al bajar se quedaba hasta %.3f bloques por encima del suelo", oldBadTrips, oldTrips, oldWorstUp, oldWobbleUp, oldWorstDown));
        System.out.println(String.format("  el caso del video (3x3x1, 5 b/s, 39 bloques): antes subiendo hundido hasta %.3f con %d cambios, bajando hasta %.3f por encima; ahora 0 y 0",
                upOld.worstBelow, upOld.wobbles, downOld.worstAbove));
    }

    private static void testEdges() {
        check(ElevatorMotion.riderTop(1, 1.0) > 1.0, "en una plataforma de alto 1 la búsqueda llega por encima de su superficie");
        check(ElevatorMotion.isRider(1.0, HEIGHT, 0.0, 1, 1.0), "de pie justo sobre una plataforma de alto 1: es pasajero");
        check(ElevatorMotion.isRider(1.9, HEIGHT, 0.0, 1, 1.0), "saltando hasta 0,9 sobre la plataforma: sigue siendo pasajero");
        check(!ElevatorMotion.isRider(2.0, HEIGHT, 0.0, 1, 1.0), "a 1 bloque o más sobre la plataforma: ya no");
        check(ElevatorMotion.isRider(1.0, HEIGHT, 0.0, 4, 1.0), "dentro de una cabina alta: es pasajero");
        check(ElevatorMotion.isRider(4.5, HEIGHT, 0.0, 4, 1.0), "sobre el techo de una cabina alta (hasta 1 bloque): también lo lleva");
        check(!ElevatorMotion.isRider(-2.1, HEIGHT, 0.0, 1, 1.0) && !ElevatorMotion.isRider(-1.8, HEIGHT, 0.0, 1, 1.0), "debajo de la cabina (la cabeza llega justo a la base o no llega): no es pasajero");
        check(ElevatorMotion.isRider(0.85, HEIGHT, 0.0, 1, 1.0), "hundido 0,15 en el suelo (suelo fino): sigue siendo pasajero");
        System.out.println("bordes de pasajeros: ok");
    }

    public static void main(String[] args) {
        testEdges();
        testRiders();
        System.out.println("ELEVATOR RIDERS OK (" + checks + " comprobaciones)");
    }
}
