package com.example.beautifuldecor.elevator;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba de la V3.3.3 sin Minecraft: la LLEGADA de la cabina, con el orden real de los paquetes del servidor al cliente.
 * <p>
 * Lo que pasaba (video del 27/10, a velocidad alta): el servidor pone los bloques del piso nuevo y quita la cabina en el mismo tick, pero
 * los cambios de bloques salen en el tick SIGUIENTE (fase de chunks) mientras que el "quitar la entidad" sale al instante. En el cliente
 * la cabina desaparecía un tick antes de que aparecieran los bloques: el jugador caía 0,08 bloques, quedaba con los pies dentro del bloque
 * nuevo y, como Minecraft no frena a quien ya está metido en un bloque, lo atravesaba y caía por todo el pozo.
 * <p>
 * Aquí se simula eso tick a tick: servidor y cliente con la MISMA cuenta de movimiento (ElevatorMotion real), retraso de red de 0 a 6
 * ticks (distinto para la aparición de la cabina y para lo demás), colisión con la cabina que funciona o que no se encuentra (cabinas
 * anchas), y el aviso de "ya es bloque" atendido al llegar el dato o recién en el tick de la cabina. Se comprueba que con el código viejo
 * (V3.3.2) el jugador atraviesa el piso, y que con el nuevo NUNCA.
 */
public class ElevatorLandingTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double FLOOR_TOP = 1.0;
    private static final double SIZE_Y = 4.0;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final int WAIT = 24;

    /** La cuenta de la cabina (igual en cliente y servidor, como en ElevatorCabinEntity.tick). */
    private static final class Cabin {
        final double speed;
        final double start;
        final double target;
        double y;
        int wait = WAIT;
        int moveTicks;
        boolean arrivedOwn;

        Cabin(double bps, double dist) {
            this.speed = bps / 20.0;
            this.start = 0.0;
            this.target = dist;
            this.y = 0.0;
        }

        /** Un tick de movimiento; devuelve el paso dado (con signo). */
        double step() {
            if (this.wait > 0) {
                this.wait--;
                return 0.0;
            }
            double remaining = this.target - this.y;
            if (Math.abs(remaining) < ElevatorMotion.ARRIVED) {
                this.arrivedOwn = true;
                return 0.0;
            }
            double distance = Math.abs(this.target - this.start);
            double travelled = Math.abs(this.y - this.start);
            boolean last = ElevatorMotion.isLastStep(this.speed, distance, travelled, this.moveTicks);
            double dy = Math.signum(remaining) * ElevatorMotion.stepFor(this.speed, distance, travelled, this.moveTicks);
            this.moveTicks++;
            this.y += dy;
            if (last) {
                this.arrivedOwn = true;
            }
            return dy;
        }
    }

    private enum Kind { BLOCKS, LANDED, REMOVE }

    private static final class Packet {
        final int arrive;
        final int phase;
        final Kind kind;

        Packet(int arrive, int phase, Kind kind) {
            this.arrive = arrive;
            this.phase = phase;
            this.kind = kind;
        }
    }

    /** Estado del jugador del cliente. */
    private static final class Player {
        double feet = FLOOR_TOP;
        double dm;
    }

    static final class Result {
        boolean fell;
        double endGap;
        double rest;
    }

    /**
     * Un viaje con el jugador encima y la llegada con el orden real de los paquetes.
     *
     * @param spawnLat retraso (ticks) con que le llega al cliente la cabina nueva: su cabina va atrasada eso
     * @param lat      retraso (ticks) de los paquetes de la llegada (bloques, aviso y quitar la cabina)
     * @param newCode  true: V3.3.3 (la cabina espera LINGER_TICKS y avisa); false: V3.3.2 (se quita al instante)
     * @param callback true: el aviso se atiende al llegar el dato; false: recién en el tick de la cabina
     * @param collides true: la colisión con la cabina funciona; false: no se encuentra (cabina ancha) y solo sostiene el pegado
     */
    static Result trip(double bps, double dist, int spawnLat, int lat, boolean newCode, boolean callback, boolean collides) {
        // ---- servidor: cuándo llega
        Cabin server = new Cabin(bps, dist);
        int arrival = -1;
        for (int s = 0; s < 1_000_000; s++) {
            server.step();
            if (server.arrivedOwn) {
                arrival = s;
                break;
            }
        }
        check(arrival >= 0, "el servidor no llega");
        // ---- paquetes de la llegada: fase 0 = chunks (bloques), 1 = datos de entidades, 2 = ticks de entidades (quitar)
        List<Packet> packets = new ArrayList<>();
        packets.add(new Packet(arrival + 1 + lat, 0, Kind.BLOCKS));
        if (newCode) {
            packets.add(new Packet(arrival + 1 + lat, 1, Kind.LANDED));
            packets.add(new Packet(arrival + ElevatorMotion.LINGER_TICKS + lat, 2, Kind.REMOVE));
        } else {
            packets.add(new Packet(arrival + lat, 2, Kind.REMOVE));
        }
        packets.sort((a, b) -> a.arrive != b.arrive ? Integer.compare(a.arrive, b.arrive) : Integer.compare(a.phase, b.phase));
        int removedAt = packets.stream().filter(p -> p.kind == Kind.REMOVE).findFirst().orElseThrow().arrive;

        // ---- cliente
        double finalTop = dist + FLOOR_TOP;
        Cabin cabin = new Cabin(bps, dist);
        Player player = new Player();
        boolean entity = false;
        boolean landed = false;
        boolean syncedLanded = false;
        boolean worldFloor = false;
        Result result = new Result();
        int end = removedAt + 60;
        // al salir: los bloques del piso de salida siguen en el mundo del cliente hasta que llega su cambio (sale un tick después que la cabina)
        boolean startFloor = true;
        for (int t = 0; t <= end; t++) {
            if (t == spawnLat) {
                entity = true;
            }
            if (t == spawnLat + 1) {
                startFloor = false;
            }
            // 1) paquetes que llegan en este tick (en orden)
            for (Packet p : packets) {
                if (p.arrive != t) {
                    continue;
                }
                switch (p.kind) {
                    case BLOCKS -> worldFloor = true;
                    case LANDED -> {
                        syncedLanded = true;
                        if (callback && entity && !landed) {
                            result.rest = cabin.target - cabin.y;
                            landOnClient(cabin, player);
                            landed = true;
                        }
                    }
                    case REMOVE -> {
                        if (entity && !landed) {
                            // onClientRemoval (V3.3.1): el cliente termina de llevar al jugador si le falta poco
                            double rest = cabin.target - cabin.y;
                            if (Math.abs(rest) > ElevatorMotion.ARRIVED && Math.abs(rest) <= ElevatorMotion.CLIENT_CATCH_UP) {
                                moveBy(cabin, player, rest);
                            }
                        }
                        entity = false;
                    }
                }
            }
            // 2) el jugador: gravedad y colisión (la cabina, si se encuentra; el bloque del piso, si ya llegó y si los pies NO están dentro)
            double top = cabin.y + FLOOR_TOP;
            player.dm = (player.dm - GRAVITY) * DRAG;
            double next = player.feet + player.dm;
            double stop = Double.NEGATIVE_INFINITY;
            if (entity && collides && player.feet >= top - 1e-7 && next < top) {
                stop = Math.max(stop, top);
            }
            if (worldFloor && player.feet >= finalTop - 1e-7 && next < finalTop) {
                stop = Math.max(stop, finalTop);
            }
            if (startFloor && player.feet >= FLOOR_TOP - 1e-7 && next < FLOOR_TOP) {
                stop = Math.max(stop, FLOOR_TOP);
            }
            if (stop > Double.NEGATIVE_INFINITY) {
                next = stop;
                player.dm = 0.0;
            }
            player.feet = next;
            // 3) la cabina del cliente
            if (entity) {
                if (newCode && syncedLanded && !landed) {
                    result.rest = cabin.target - cabin.y;
                    landOnClient(cabin, player);
                    landed = true;
                }
                if (landed) {
                    keepOnFloor(cabin, player);
                } else {
                    boolean before = cabin.arrivedOwn;
                    double dy = cabin.step();
                    if (dy != 0.0) {
                        moveBy(cabin, player, dy);
                    }
                    if (!before || newCode || cabin.wait > 0 || !cabin.arrivedOwn) {
                        // V3.3.2: después de su propia llegada el cliente ya no pegaba al suelo; V3.3.3 sí
                        keepOnFloor(cabin, player);
                    }
                }
            }
            if (worldFloor && player.feet < finalTop - 1.0) {
                result.fell = true;
                break;
            }
        }
        result.endGap = player.feet - finalTop;
        if (Math.abs(result.endGap) > 1e-6) {
            result.fell = true;
        }
        return result;
    }

    private static boolean inZone(Cabin cabin, Player player) {
        return player.feet < cabin.y + SIZE_Y && player.feet + PLAYER_HEIGHT > cabin.y;
    }

    private static void moveBy(Cabin cabin, Player player, double dy) {
        // (en la cabina real moveBy mueve al jugador y al suelo; aquí cabin.y ya se movió en step(), salvo en onClientRemoval)
        boolean zone = inZone(cabin, player) || inZone(shifted(cabin, -dy), player);
        if (zone) {
            // (V3.3.4: los pasos diminutos, que Entity.move ignoraría, la cabina los da exactos; los demás, con Entity.move)
            player.feet += dy;
        }
        if (cabin.arrivedOwn && Math.abs(cabin.target - cabin.y) > 1e-12) {
            cabin.y = cabin.target;
        }
    }

    private static Cabin shifted(Cabin cabin, double dy) {
        Cabin c = new Cabin(cabin.speed * 20.0, cabin.target);
        c.y = cabin.y + dy;
        return c;
    }

    private static void keepOnFloor(Cabin cabin, Player player) {
        double bottom = cabin.y;
        double top = bottom + FLOOR_TOP;
        if (ElevatorMotion.standing(player.feet, bottom, top)) {
            double lifted = ElevatorMotion.liftedFeet(player.feet, bottom, top);
            player.feet = lifted;
            if (player.dm < 0.0) {
                player.dm = 0.0;
            }
        }
    }

    private static void landOnClient(Cabin cabin, Player player) {
        double rest = cabin.target - cabin.y;
        double finalTop = cabin.target + FLOOR_TOP;
        cabin.y = cabin.target;
        double lifted = ElevatorMotion.landedFeet(player.feet, finalTop, rest);
        if (lifted > player.feet) {
            player.feet = lifted;
            if (player.dm < 0.0) {
                player.dm = 0.0;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ pruebas

    private static void testLanding() {
        double[] speeds = {1, 2, 5, 8, 12, 16, 20};
        double[] distances = {3, 12, 40, 100, -3, -12, -40};
        int total = 0;
        int oldFell = 0;
        int newFell = 0;
        double worstRest = 0.0;
        for (double bps : speeds) {
            for (double dist : distances) {
                for (int spawnLat = 0; spawnLat <= 6; spawnLat += 2) {
                    for (int lat = 0; lat <= 6; lat++) {
                        for (boolean collides : new boolean[]{true, false}) {
                            for (boolean callback : new boolean[]{true, false}) {
                                Result now = trip(bps, dist, spawnLat, lat, true, callback, collides);
                                check(!now.fell, "V3.3.3: el jugador atraviesa el piso al llegar (bps " + bps + ", dist " + dist + ", retraso aparición "
                                        + spawnLat + ", retraso " + lat + ", colisión " + collides + ", aviso al llegar el dato " + callback
                                        + ", hueco final " + now.endGap + ")");
                                worstRest = Math.max(worstRest, Math.abs(now.rest));
                                total++;
                                if (callback) {
                                    Result old = trip(bps, dist, spawnLat, lat, false, true, collides);
                                    if (old.fell) {
                                        oldFell++;
                                    }
                                }
                                if (now.fell) {
                                    newFell++;
                                }
                            }
                        }
                    }
                }
            }
        }
        int oldTotal = total / 2;
        // lo que se quería ver: con la llegada vieja el jugador atravesaba el piso (a cualquier velocidad)
        check(oldFell > oldTotal * 9 / 10, "la prueba no reproduce el problema: con el código viejo solo atravesaba " + oldFell + " de " + oldTotal);
        for (double bps : speeds) {
            check(trip(bps, 40, 2, 2, false, true, true).fell, "con el código viejo, a " + bps + " b/s, el jugador atravesaba el piso al llegar");
            check(!trip(bps, 40, 2, 2, true, true, true).fell, "con el nuevo, a " + bps + " b/s, no");
        }
        System.out.println("llegada: " + total + " casos (7 velocidades, 7 distancias, retrasos de red de 0 a 6 ticks, colisión sí/no, aviso inmediato o en el tick)");
        System.out.println("  código viejo (V3.3.2): el jugador atravesaba el piso en " + oldFell + " de " + oldTotal + " casos; ahora: " + newFell);
        System.out.println(String.format("  lo más que le faltaba a la cabina del cliente al llegar el aviso: %.3f bloques (se le sube al jugador sin hueco)", worstRest));
    }

    private static void testEdges() {
        check(ElevatorMotion.LINGER_TICKS >= 2, "la cabina debe esperar al menos 2 ticks después de volverse bloques (los bloques salen un tick después)");
        check(ElevatorMotion.LINGER_TICKS <= 10, "la cabina no debe quedarse más de medio segundo");
        double top = 41.0;
        check(ElevatorMotion.landedFeet(41.0, top, 0.0) == 41.0, "justo encima: no se mueve");
        check(ElevatorMotion.landedFeet(40.92, top, 0.0) == 41.0, "hundido 0,08 (un tick de gravedad): sube a la superficie");
        check(ElevatorMotion.landedFeet(40.6, top, 0.3) == 41.0, "cabina del cliente atrasada 0,3: sube");
        check(ElevatorMotion.landedFeet(38.0, top, 2.6) == 41.0, "cabina del cliente atrasada 2,6 bloques: también sube");
        check(ElevatorMotion.landedFeet(41.5, top, 0.0) == 41.5, "quien está en el aire encima no se toca (cae solo sobre el bloque)");
        check(ElevatorMotion.landedFeet(30.0, top, 0.0) == 30.0, "alguien muy abajo (otro piso) no se toca");
        check(ElevatorMotion.landedFeet(39.4, top, 0.0) == 39.4, "a más de 1,5 bloques por debajo, sin atraso de la cabina, no se toca");
        System.out.println("bordes de la llegada: ok");
    }

    public static void main(String[] args) {
        testEdges();
        testLanding();
        System.out.println("ELEVATOR LANDING OK (" + checks + " comprobaciones)");
    }
}
