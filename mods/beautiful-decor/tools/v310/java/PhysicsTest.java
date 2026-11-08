import com.example.beautifuldecor.entity.RcCarPhysics;

/** Simula el manejo del carro RC con las cuentas reales (RcCarPhysics) y comprueba que se porte como un carro. */
public class PhysicsTest {
    static int fails = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("FALLO: " + what);
        }
    }

    public static void main(String[] args) {
        // 1) arrancar y acelerar a fondo: llega casi al tope y nunca lo pasa
        float v = 0;
        int ticksTo90 = -1;
        float max = 0;
        for (int t = 1; t <= 200; t++) {
            v = RcCarPhysics.speedAfter(v, true, true, false, false);
            max = Math.max(max, v);
            if (ticksTo90 < 0 && v >= 0.9f * RcCarPhysics.TOP_SPEED) {
                ticksTo90 = t;
            }
        }
        System.out.printf("acelerar: 90%% del tope en %d ticks (%.2f s), final %.4f, maximo %.4f%n", ticksTo90, ticksTo90 / 20.0, v, max);
        check(ticksTo90 > 20 && ticksTo90 < 120, "tarda entre 1 y 6 s en llegar al 90% del tope");
        check(max <= RcCarPhysics.TOP_SPEED + 1e-6f, "nunca pasa el tope");
        check(v > 0.43f, "llega casi al tope (>0,43)");
        float top = v;

        // 2) soltar el gas: se va frenando solo y se detiene
        float c = top;
        int stop = -1;
        for (int t = 1; t <= 300; t++) {
            c = RcCarPhysics.speedAfter(c, true, false, false, false);
            if (c == 0.0f) {
                stop = t;
                break;
            }
        }
        System.out.printf("soltar el gas: se detiene en %d ticks%n", stop);
        check(stop > 20 && stop < 200, "al soltar el gas se detiene solo en unos segundos");

        // 3) frenar con S: frena mas rapido que soltar el gas, sin pasar a reversa de golpe
        float b = top;
        int brakeStop = -1;
        for (int t = 1; t <= 100; t++) {
            b = RcCarPhysics.speedAfter(b, true, false, true, false);
            if (b <= 0.03f) {
                brakeStop = t;
                break;
            }
        }
        System.out.printf("frenar con S: baja a casi 0 en %d ticks%n", brakeStop);
        check(brakeStop > 0 && brakeStop < stop && brakeStop < 20, "frenar con S es mas rapido que soltar el gas");

        // 4) reversa: llega a -REVERSE_SPEED y no pasa de ahi
        float r = 0;
        for (int t = 1; t <= 100; t++) {
            r = RcCarPhysics.speedAfter(r, true, false, true, false);
            check(r >= -RcCarPhysics.REVERSE_SPEED - 1e-6f, "la reversa no pasa del tope");
        }
        System.out.printf("reversa: %.4f%n", r);
        check(Math.abs(r + RcCarPhysics.REVERSE_SPEED) < 1e-4f, "con S sostenido llega a la velocidad de reversa");

        // 5) freno de mano: frena mas que el gas suelto
        float h = top;
        for (int t = 0; t < 10; t++) {
            h = RcCarPhysics.speedAfter(h, true, false, false, true);
        }
        float noHand = top;
        for (int t = 0; t < 10; t++) {
            noHand = RcCarPhysics.speedAfter(noHand, true, false, false, false);
        }
        System.out.printf("freno de mano 10 ticks: %.4f (sin freno de mano %.4f)%n", h, noHand);
        check(h < noHand, "el freno de mano frena mas");

        // 6) en el aire no acelera ni frena
        float air = RcCarPhysics.speedAfter(0.3f, false, true, false, false);
        check(air <= 0.3f, "en el aire no acelera");

        // 7) giro: parado no gira; a la derecha es positivo; en reversa se invierte; nunca pasa del maximo
        check(RcCarPhysics.turnAfter(0.0f, 1.0f, false) == 0.0f, "parado no gira");
        check(RcCarPhysics.turnAfter(0.3f, 0.0f, false) == 0.0f, "sin volante no gira");
        check(RcCarPhysics.turnAfter(0.3f, 1.0f, false) > 0.0f, "volante a la derecha da giro positivo");
        check(RcCarPhysics.turnAfter(0.3f, -1.0f, false) < 0.0f, "volante a la izquierda da giro negativo");
        check(RcCarPhysics.turnAfter(-0.15f, 1.0f, false) < 0.0f, "en reversa el giro se invierte");
        float maxTurn = 0;
        for (int i = 1; i <= 90; i++) {
            maxTurn = Math.max(maxTurn, RcCarPhysics.turnAfter(i * 0.005f, 1.0f, false));
        }
        check(maxTurn <= RcCarPhysics.MAX_TURN + 1e-4f, "el giro nunca pasa del maximo");
        float tFast = RcCarPhysics.turnAfter(RcCarPhysics.TOP_SPEED, 1.0f, false);
        float tMid = RcCarPhysics.turnAfter(0.12f, 1.0f, false);
        float radiusFast = RcCarPhysics.TOP_SPEED / (float) Math.toRadians(tFast);
        float radiusMid = 0.12f / (float) Math.toRadians(tMid);
        System.out.printf("giro: a tope %.2f grados/tick (radio %.1f bloques); a 0,12: %.2f grados/tick (radio %.1f bloques)%n",
                tFast, radiusFast, tMid, radiusMid);
        check(radiusFast > 3.0f && radiusFast < 8.0f, "a toda velocidad la curva es de unos 3 a 8 bloques de radio");
        check(radiusMid < radiusFast, "a menos velocidad gira mas cerrado");
        check(radiusMid > 0.8f, "ni siquiera a poca velocidad da trompos");

        // 8) simulacion de un circulo a toda velocidad con el volante a la derecha: vuelve cerca de donde empezo
        double x = 0, z = 0, yaw = 0;
        float sp = top;
        double minDist = 1e9;
        for (int t = 1; t <= 400; t++) {
            yaw += RcCarPhysics.turnAfter(sp, 1.0f, false);
            double rad = Math.toRadians(yaw);
            x += -Math.sin(rad) * sp;
            z += Math.cos(rad) * sp;
            if (t > 20) {
                minDist = Math.min(minDist, Math.hypot(x, z));
            }
        }
        System.out.printf("circulo a tope: vuelve a %.2f bloques del inicio%n", minDist);
        check(minDist < 1.0, "con el volante a fondo el carro da un circulo cerrado");

        System.out.println(fails == 0 ? "PHYSICS OK" : "PHYSICS FALLO " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
