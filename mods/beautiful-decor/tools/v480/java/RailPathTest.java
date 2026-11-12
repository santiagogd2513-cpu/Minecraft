import com.example.beautifuldecor.entity.RailPath;
import com.example.beautifuldecor.entity.RailPath.Cursor;
import com.example.beautifuldecor.entity.RailPath.Shape;
import com.example.beautifuldecor.entity.TrainPhysics;
import com.example.beautifuldecor.entity.VehiclePhysics;

import java.util.HashMap;
import java.util.Map;

public class RailPathTest {
    static void check(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
    }

    static final Map<String, Shape> W = new HashMap<>();

    static void put(int x, int y, int z, Shape s) {
        W.put(x + "," + y + "," + z, s);
    }

    static final RailPath.Lookup LK = (x, y, z) -> W.get(x + "," + y + "," + z);

    static double[] pos(Cursor c) {
        double[] o = new double[3];
        c.worldPoint(o);
        return o;
    }

    public static void main(String[] args) {
        // ---- 1. óvalo de 4x4 con 4 curvas: una vuelta mide 8 + pi
        put(0, 64, 0, Shape.SOUTH_EAST);
        put(3, 64, 0, Shape.SOUTH_WEST);
        put(3, 64, 3, Shape.NORTH_WEST);
        put(0, 64, 3, Shape.NORTH_EAST);
        for (int i = 1; i <= 2; i++) {
            put(i, 64, 0, Shape.EAST_WEST);
            put(i, 64, 3, Shape.EAST_WEST);
            put(0, 64, i, Shape.NORTH_SOUTH);
            put(3, 64, i, Shape.NORTH_SOUTH);
        }
        double lap = 8 + Math.PI;
        Cursor c = new Cursor(1, 64, 0, Shape.EAST_WEST, 0.5, 1);       // va hacia el este por el lado norte
        double[] p0 = pos(c);
        double moved = c.advance(lap, LK);
        check(Math.abs(moved - lap) < 1e-6, "una vuelta completa avanza " + moved);
        double[] p1 = pos(c);
        check(Math.abs(p0[0] - p1[0]) < 1e-6 && Math.abs(p0[2] - p1[2]) < 1e-6, "tras una vuelta vuelve al mismo punto");
        // 20 vueltas en pasos de 0.37 m (como el tren, tick a tick) y sigue pegado al riel
        Cursor d = new Cursor(1, 64, 0, Shape.EAST_WEST, 0.5, 1);
        for (int i = 0; i < 20 * 30; i++) {
            d.advance(0.37, LK);
            double[] p = pos(d);
            // el óvalo es el rectángulo de 0.5..3.5, así que ningún punto sale de ahí
            check(p[0] >= -0.001 && p[0] <= 4.001 && p[2] >= -0.001 && p[2] <= 4.001, "se salió del óvalo: " + p[0] + "," + p[2]);
        }
        // hacia atrás es lo mismo: -lap vuelve a empezar
        Cursor e = new Cursor(1, 64, 0, Shape.EAST_WEST, 0.5, 1);
        e.advance(-lap, LK);
        double[] pe = pos(e);
        check(Math.abs(p0[0] - pe[0]) < 1e-6 && Math.abs(p0[2] - pe[2]) < 1e-6, "marcha atrás una vuelta");
        // la curva es redonda: el punto medio de la curva SOUTH_EAST en (0,0) está a 0.5 de su esquina (1,1)... local
        double[] q = new double[3];
        Shape.SOUTH_EAST.point(Shape.SOUTH_EAST.length() / 2, q);
        double r = Math.hypot(q[0] - 1.0, q[2] - 1.0);
        check(Math.abs(r - 0.5) < 1e-9, "radio de la curva " + r);

        // ---- 2. subida: plano -> subida -> plano un nivel más arriba -> y de vuelta bajando
        W.clear();
        put(0, 64, 0, Shape.EAST_WEST);
        put(1, 64, 0, Shape.ASCENDING_EAST);
        put(2, 65, 0, Shape.EAST_WEST);
        Cursor u = new Cursor(0, 64, 0, Shape.EAST_WEST, 0.0, 1);
        double[] a = pos(u);
        u.advance(1 + Math.sqrt(2) + 0.5, LK);
        double[] b = pos(u);
        check(u.y == 65 && u.shape == Shape.EAST_WEST, "terminó en el nivel de arriba");
        check(Math.abs(b[1] - (65 + RailPath.RAIL_TOP)) < 1e-9, "altura arriba " + b[1]);
        check(Math.abs(b[0] - 2.5) < 1e-6, "x arriba " + b[0]);
        // bajada: de arriba hacia el oeste
        Cursor dn = new Cursor(2, 65, 0, Shape.EAST_WEST, 0.5, -1);
        double mv = dn.advance(0.5 + Math.sqrt(2) + 0.25, LK);
        check(Math.abs(mv - (0.5 + Math.sqrt(2) + 0.25)) < 1e-9, "la bajada se recorre entera");
        check(dn.y == 64 && dn.x == 0, "bajó al nivel de abajo: " + dn.x + "," + dn.y);
        // pendiente en la subida y en la bajada
        Cursor mid = new Cursor(1, 64, 0, Shape.ASCENDING_EAST, 0.5, 1);
        check(Math.abs(mid.slope() - Math.sqrt(0.5)) < 1e-9, "pendiente subiendo");
        mid.dir = -1;
        check(Math.abs(mid.slope() + Math.sqrt(0.5)) < 1e-9, "pendiente bajando");
        // fin de la vía: se queda en el borde y avisa de que no avanzó todo
        Cursor end = new Cursor(2, 65, 0, Shape.EAST_WEST, 0.5, 1);
        double got = end.advance(5, LK);
        check(Math.abs(got - 0.5) < 1e-9 && Math.abs(end.s - 1.0) < 1e-9, "se detiene en el fin de la vía: " + got);

        // ---- 3. subida hacia el norte (rotación): ASCENDING_NORTH sube hacia el norte
        W.clear();
        put(5, 70, 5, Shape.NORTH_SOUTH);
        put(5, 70, 4, Shape.ASCENDING_NORTH);
        put(5, 71, 3, Shape.NORTH_SOUTH);
        Cursor nn = new Cursor(5, 70, 5, Shape.NORTH_SOUTH, 1.0, -1);   // va hacia el norte
        nn.advance(3.0, LK);
        check(nn.y == 71 && nn.z == 3, "subida hacia el norte: " + nn.y + "," + nn.z);

        // ---- 4. cresta: dos subidas que se juntan por arriba
        W.clear();
        put(0, 64, 0, Shape.ASCENDING_EAST);
        put(1, 64, 0, Shape.ASCENDING_WEST);
        Cursor cr = new Cursor(0, 64, 0, Shape.ASCENDING_EAST, 0.0, 1);
        cr.advance(2.0, LK);
        check(cr.x == 1 && cr.shape == Shape.ASCENDING_WEST, "cruza la cresta");
        double[] top = pos(cr);
        check(top[1] > 64.5, "sigue en la parte alta tras la cresta o baja: " + top[1]);

        // ---- 5. física del tren
        double locoOnly = LOCO();
        // locomotora sola: acelera con fuerza y llega a 130 km/h
        double v = 0;
        int ticksTo130 = -1;
        for (int i = 0; i < 20 * 600; i++) {
            v = TrainPhysics.step(v, TrainPhysics.LOCO_MASS_KG, TrainPhysics.LOCO_MASS_KG, 1, 0, 1, false, false);
            if (v * 72 >= 129.0) {
                ticksTo130 = i;
                break;
            }
        }
        check(ticksTo130 > 0 && ticksTo130 / 20.0 < 120, "locomotora sola llega a 130 km/h en " + ticksTo130 / 20.0 + " s");
        // y no pasa de 130 en plano
        for (int i = 0; i < 20 * 200; i++) {
            v = TrainPhysics.step(v, TrainPhysics.LOCO_MASS_KG, TrainPhysics.LOCO_MASS_KG, 1, 0, 1, false, false);
        }
        check(v * 72 <= 131.0 && v * 72 > 120, "tope de 130 km/h: " + v * 72);

        // tren de 10 vagones vacíos (+80 t de locomotora = 280 t)
        double empty10 = TrainPhysics.LOCO_MASS_KG + 10 * 20_000;
        double tt = timeTo(empty10, empty10, 1, 100 / 3.6);
        // tren de 10 vagones cargados (cada uno +80 t = 1080 t)
        double full10 = empty10 + 10 * TrainPhysics.LOCO_MASS_KG;
        double tf = timeTo(empty10, full10, 1, 100 / 3.6);
        check(tt > 0 && tf > tt * 3, "cargado tarda mucho más en acelerar: vacío " + tt + " s, cargado " + tf + " s");
        // frenado: más peso, más distancia
        double bd1 = brakeDist(empty10, empty10, 1, 100 / 3.6);
        double bd2 = brakeDist(empty10, full10, 1, 100 / 3.6);
        check(bd2 > bd1 * 3, "frenar cargado cuesta mucho más: " + bd1 + " vs " + bd2);
        // frenado desde 130 con el tren vacío: cientos de metros, no unos bloques
        double bd130 = brakeDist(empty10, empty10, 1, 130 / 3.6);
        check(bd130 > 300 && bd130 < 1500, "frenado 130 km/h vacío " + bd130);

        // cuesta arriba: el tren cargado de 10 vagones se queda atascado y pierde velocidad; la locomotora sola la sube
        double sl = Math.sqrt(0.5);
        double vv = 20.0 / 72.0 * 5;                       // 5 m/s
        vv = 5.0 / 20.0;
        for (int i = 0; i < 20 * 60; i++) {
            vv = TrainPhysics.step(vv, empty10, full10, 1, sl, 1, false, false);
        }
        check(vv * 20 < 2.0, "tren cargado no sube la cuesta: " + vv * 20 + " m/s");
        double vl = 5.0 / 20.0;
        for (int i = 0; i < 20 * 60; i++) {
            vl = TrainPhysics.step(vl, TrainPhysics.LOCO_MASS_KG, TrainPhysics.LOCO_MASS_KG, 1, sl, 1, false, false);
        }
        check(vl * 20 > 5.0, "la locomotora sola sí sube: " + vl * 20 + " m/s");

        // cuesta abajo sin frenar: se vuelve una bala (supera los 130 km/h hasta el tope de 1.6x), cargado más que vacío
        double vd = 0;
        for (int i = 0; i < 20 * 120; i++) {
            vd = TrainPhysics.step(vd, TrainPhysics.LOCO_MASS_KG + 20_000, TrainPhysics.LOCO_MASS_KG + 20_000 + TrainPhysics.LOCO_MASS_KG, 1, -sl, 0, false, false);
        }
        check(vd * 72 > 130, "bajando sin frenos pasa de 130 km/h: " + vd * 72);
        // y los frenos cuesta abajo con carga no alcanzan a pararlo rápido
        double vb = vd;
        int ticks = 0;
        while (vb > 0.01 && ticks < 20 * 600) {
            vb = TrainPhysics.step(vb, TrainPhysics.LOCO_MASS_KG + 20_000, TrainPhysics.LOCO_MASS_KG + 100_000, 1, -sl, 0, true, true);
            ticks++;
        }
        check(ticks > 20 * 60, "tren cargado bajando a velocidad máxima tarda más de 1 min en parar (" + ticks / 20.0 + " s)");

        // más locomotoras, más empuje
        double t1 = timeTo(empty10, full10, 1, 60 / 3.6);
        double t2 = timeTo(empty10 + TrainPhysics.LOCO_MASS_KG, full10 + TrainPhysics.LOCO_MASS_KG, 2, 60 / 3.6);
        check(t2 > 0 && t2 < t1, "dos locomotoras aceleran más: " + t1 + " vs " + t2);

        // parado y sin gas en plano se queda quieto; reversa llega a su tope
        double vs = TrainPhysics.step(0, empty10, empty10, 1, 0, 0, false, false);
        check(vs == 0.0, "parado en plano se queda quieto");
        double vr = 0;
        for (int i = 0; i < 20 * 200; i++) {
            vr = TrainPhysics.step(vr, TrainPhysics.LOCO_MASS_KG, TrainPhysics.LOCO_MASS_KG, 1, 0, -1, false, false);
        }
        check(vr * 72 < -10 && vr * 72 > -20, "reversa ~15 km/h: " + vr * 72);


        // ---- tren de pasajeros de vapor: tope 80 km/h, cuesta de ~4 vagones llenos
        double top80 = TrainPhysics.PASSENGER_TOP_KMH / 3.6;
        double coach = 40_000 + 28 * 80;                       // vagón con 28 pasajeros
        double emptyP = TrainPhysics.STEAM_MASS_KG + 6 * 40_000;
        double totalP = TrainPhysics.STEAM_MASS_KG + 6 * coach;
        double vp = 0;
        for (int i = 0; i < 20 * 400; i++) {
            vp = TrainPhysics.step(vp, emptyP, totalP, TrainPhysics.STEAM_MASS_KG, TrainPhysics.STEAM_POWER_W, top80, 0, 1, false, false);
        }
        check(vp * 72 <= 81.0 && vp * 72 > 70.0, "pasajeros: tope 80 km/h: " + vp * 72);
        // sin combustible (potencia 0) no acelera
        double vz = TrainPhysics.step(0, emptyP, totalP, 0, 0, top80, 0, 1, false, false);
        check(vz <= 0.0, "sin carbón no avanza: " + vz);

        // ---- metro eléctrico: 8 coches con motor, tope 284 km/h
        double topM = 284.0 / 3.6;
        double mm = 62_000 + 7 * 42_000;
        double mt = mm + 8 * 24 * 80;
        double pw = 2_400_000 + 7 * 1_200_000;
        double vm = 0;
        double t100 = -1;
        for (int i = 0; i < 20 * 900; i++) {
            vm = TrainPhysics.step(vm, mm, mt, mm, pw, topM, 0, 1, false, false);
            if (t100 < 0 && vm * 72 >= 100) {
                t100 = i / 20.0;
            }
        }
        check(vm * 72 > 270 && vm * 72 <= 285, "metro llega a ~284 km/h: " + vm * 72);
        check(t100 > 4 && t100 < 40, "metro 0-100 km/h en " + t100 + " s");

        // unidades de carga
        check(TrainPhysics.units(true, 2) == 18 && TrainPhysics.units(false, 5) == 5, "unidades");
        System.out.println("RailPathTest OK (loco solo a 130: " + ticksTo130 / 20.0 + " s; 10 vagones vacíos a 100: " + tt + " s; cargados: " + tf + " s)");
    }

    static double LOCO() {
        return 0;
    }

    static double timeTo(double empty, double total, int locos, double ms) {
        double v = 0;
        for (int i = 0; i < 20 * 3000; i++) {
            v = TrainPhysics.step(v, empty, total, locos, 0, 1, false, false);
            if (v * 20 >= ms) {
                return (i + 1) / 20.0;
            }
        }
        return -1;
    }

    static double brakeDist(double empty, double total, int locos, double ms) {
        double v = ms / 20.0;
        double d = 0;
        for (int i = 0; i < 20 * 3000 && v > 0.0005; i++) {
            d += v;
            v = TrainPhysics.step(v, empty, total, locos, 0, 0, true, false);
        }
        return d;
    }
}
