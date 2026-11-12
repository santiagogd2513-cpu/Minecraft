import com.example.beautifuldecor.entity.VehiclePhysics;
import com.example.beautifuldecor.entity.VehiclePhysics.Params;

public class VehiclePhysicsTest {
    static void check(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
    }

    static double kmh(double ms) {
        return ms * 3.6;
    }

    public static void main(String[] a) {
        // mismos números que las entidades
        Params pickup = new Params(2500, 190_000, 3.5, 7.5, 3.5, 1.3, 0.015, 0.9, 80 / 3.6, 0.4 * 20);
        Params supercar = new Params(1900, 850_000, 9.0, 11.0, 4.0, 0.7, 0.012, 1.0, 432 / 3.6, 0.5 * 20);
        Params truck = new Params(7500, 150_000, 1.6, 5.0, 3.0, 3.6, 0.012, 1.2, 55 / 3.6, 0.3 * 20);
        Params bike = new Params(95, 700, 1.4, 3.8, 2.0, 0.5, 0.006, 0.1, 40 / 3.6, 0.12 * 20);

        // frenado: una camioneta a 80 km/h necesita decenas de metros, no 2 bloques
        double d = VehiclePhysics.brakingDistance(pickup, 2500, 80 / 3.6);
        check(d > 25 && d < 45, "camioneta 80 km/h frena en " + d);
        // más rápido cuesta mucho más (v^2): el superdeportivo a 432 km/h necesita cientos de metros
        double d2 = VehiclePhysics.brakingDistance(supercar, 1900, 432 / 3.6);
        check(d2 > 400 && d2 < 900, "superdeportivo 432 km/h frena en " + d2);
        double slow = VehiclePhysics.brakingDistance(supercar, 1900, 100 / 3.6);
        check(d2 > slow * 8, "la distancia debe crecer más que lineal con la velocidad");
        // más peso = frena peor
        double empty = VehiclePhysics.brakingDistance(truck, 7500, 55 / 3.6);
        double loaded = VehiclePhysics.brakingDistance(truck, 15000, 55 / 3.6);
        check(loaded > empty * 1.2, "con carga frena peor: " + empty + " vs " + loaded);
        // acelerar: más peso = más tiempo
        double t1 = VehiclePhysics.timeToSpeed(pickup, 2500, 60 / 3.6);
        double t2 = VehiclePhysics.timeToSpeed(pickup, 4500, 60 / 3.6);
        check(t1 > 4 && t1 < 15 && t2 > t1 * 1.3, "arranque " + t1 + " / " + t2);
        // el superdeportivo llega a 100 km/h en pocos segundos y a su tope tarda mucho más
        double s100 = VehiclePhysics.timeToSpeed(supercar, 1900, 100 / 3.6);
        double stop = VehiclePhysics.timeToSpeed(supercar, 1900, 400 / 3.6);
        check(s100 > 2 && s100 < 6 && stop > 20 && stop < 120, "superdeportivo " + s100 + " / " + stop);
        // la bici llega a su tope sin trampas
        check(VehiclePhysics.timeToSpeed(bike, 95, 38 / 3.6) > 0, "la bici debe poder llegar a 38 km/h");
        // pendiente: cuesta arriba pierde velocidad a pleno gas; cuesta abajo se dispara
        double v = 60 / 3.6 / 20.0;
        double up = v;
        double down = v;
        for (int i = 0; i < 200; i++) {
            up = VehiclePhysics.step(pickup, up, 2500, 0.45, 1, false, false);
            down = VehiclePhysics.step(pickup, down, 2500, -0.20, 0, false, false);
        }
        check(kmh(up * 20) < 55, "cuesta arriba debe frenar a pleno gas: " + kmh(up * 20));
        check(kmh(down * 20) > 85, "cuesta abajo debe pasar de su tope sin gas: " + kmh(down * 20));
        check(kmh(down * 20) <= 80 * 1.6 + 0.1, "tope cuesta abajo");
        // parado en plano se queda parado; el freno no da marcha atrás
        double z = 0.0;
        for (int i = 0; i < 100; i++) {
            z = VehiclePhysics.step(pickup, z, 2500, 0.0, 0, false, false);
        }
        check(z == 0.0, "parado en plano");
        double b = 0.5;
        for (int i = 0; i < 400; i++) {
            b = VehiclePhysics.step(pickup, b, 2500, 0.0, 0, true, false);
            check(b >= 0.0, "el freno no debe dar marcha atrás");
        }
        check(b == 0.0, "debe terminar parado");
        // soltar el gas: no se queda rodando para siempre ni se para al instante
        double c = 60 / 3.6 / 20.0;
        int ticks = 0;
        while (c > 0.0 && ticks < 20 * 600) {
            c = VehiclePhysics.step(pickup, c, 2500, 0.0, 0, false, false);
            ticks++;
        }
        check(ticks > 20 * 6 && ticks < 20 * 80, "rueda libre: " + ticks / 20.0 + " s");
        // reversa limitada
        double r = 0.0;
        for (int i = 0; i < 600; i++) {
            r = VehiclePhysics.step(pickup, r, 2500, 0.0, -1, false, false);
        }
        check(r < 0 && -r * 20 <= 0.4 * 20 + 0.3, "reversa " + r);
        System.out.println("VehiclePhysicsTest OK");
    }
}
