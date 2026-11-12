import com.example.beautifuldecor.entity.*;
import java.util.*;
public class AutoTest {
    static void check(boolean b, String m) { if (!b) { System.out.println("FALLO: " + m); System.exit(1); } }
    public static void main(String[] a) {
        // horario
        check(Math.abs(TrainAuto.hourOf(0) - 6.0) < 1e-9, "hora 0 = 6");
        check(Math.abs(TrainAuto.hourOf(18000) - 0.0) < 1e-9, "18000 = 0h");
        check(TrainAuto.isOpen(7, 6, 20) && !TrainAuto.isOpen(21, 6, 20) && !TrainAuto.isOpen(5.9, 6, 20), "6-20");
        check(TrainAuto.isOpen(23, 22, 4) && TrainAuto.isOpen(2, 22, 4) && !TrainAuto.isOpen(12, 22, 4), "cruza medianoche");
        check(TrainAuto.isOpen(0, 0, 24) && TrainAuto.isOpen(23.99, 0, 24), "24h");
        // vía recta de 400 m (norte-sur) con el punto de parada en el bloque z=300
        RailPath.Lookup lk = (x, y, z) -> (x == 0 && y == 0 && z >= 0 && z < 400) ? RailPath.Shape.NORTH_SOUTH : null;
        RailPath.Shape ns = RailPath.Shape.NORTH_SOUTH;
        // dirección: cursor que avanza hacia +z: A/B? se prueba ambas
        for (int dir : new int[] {1, -1}) {
            RailPath.Cursor c = new RailPath.Cursor(0, 0, 10, ns, 0.5, dir);
            TrainAuto.Ahead r = TrainAuto.look(c, lk, 0, 0, dir == 1 ? 300 : 3, 700);
            System.out.println("dir " + dir + " found " + r.found + " known " + r.known + " cut " + r.cut + " chunks " + r.chunks.size());
        }
        // elegir la dirección que va hacia +z
        RailPath.Cursor c1 = new RailPath.Cursor(0, 0, 10, ns, 0.5, 1);
        double zBefore = z(c1), zAfter;
        RailPath.Cursor t = c1.copy(); t.advance(5, lk); zAfter = z(t);
        int dirPlus = zAfter > zBefore ? 1 : -1;
        RailPath.Cursor c = new RailPath.Cursor(0, 0, 10, ns, 0.5, dirPlus);
        TrainAuto.Ahead r = TrainAuto.look(c, lk, 0, 0, 300, 700);
        double expect = (300 + 0.5) - (10 + 0.5);
        check(Math.abs(r.found - expect) < 1e-6, "distancia al punto: " + r.found + " vs " + expect);
        // objetivo ya pasado
        TrainAuto.Ahead r2 = TrainAuto.look(c, lk, 0, 0, 5, 700);
        check(r2.found < 0 && r2.cut && Math.abs(r2.known - (400 - 10.5)) < 1e-6, "pasado/final: " + r2.known);
        check(r2.chunks.size() == 26 || r2.chunks.size() >= 24, "chunks " + r2.chunks.size());
        // simulación de frenado: tren de 800 t que va a 36 m/s hacia un punto a 600 m
        double empty = 12e5 / 1.0, total = 800_000.0, locoMass = 120_000.0 * 2, power = 3e6 * 2, top = 130 / 3.6;
        double v = 0, pos = 0, stopAt = 600; // bogie
        int ticks = 0; boolean arrived = false;
        double maxV = 0;
        for (; ticks < 20 * 600; ticks++) {
            double dist = stopAt - pos;
            if (dist <= TrainAuto.ARRIVE_DIST) { arrived = true; break; }
            double tv = TrainAuto.targetSpeed(dist, top, TrainAuto.decel(300_000.0, total));
            int d = TrainAuto.decide(v * 20, tv);
            v = TrainPhysics.step(v, 300_000.0, total, locoMass, power, top, 0.0, d > 0 ? 1 : 0, d < 0, false);
            pos += v; maxV = Math.max(maxV, v * 72);
        }
        System.out.println("llegó " + arrived + " en " + ticks / 20.0 + " s, v final " + v * 72 + " km/h, pos " + pos + ", vmax " + maxV);
        check(arrived && pos <= stopAt + 0.01 && v * 72 < 6.0, "frenada llega a tiempo y despacio");
        // luego frena del todo con el freno de mano
        for (int i = 0; i < 200; i++) { v = TrainPhysics.step(v, 300_000.0, total, locoMass, power, top, 0.0, 0, true, true); pos += v; }
        check(v == 0 || v < 1e-4, "se detiene: " + v);
        check(stopAt - pos < 1.0 && stopAt - pos > -0.2, "queda a menos de 1 m del punto: " + (stopAt - pos));
        System.out.println("OK TrainAuto");
    }
    static double z(RailPath.Cursor c) { double[] o = new double[3]; c.worldPoint(o); return o[2]; }
}
