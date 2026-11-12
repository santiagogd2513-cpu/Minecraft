import com.example.beautifuldecor.entity.*;
import java.util.*;
public class WideArcTest {
    static void check(boolean b, String m) { if (!b) { System.out.println("FALLO: " + m); System.exit(1); } }
    public static void main(String[] args) {
        RailPath.Side[] S = RailPath.Side.values();
        int checks = 0;
        for (RailPath.Side s1 : S) for (RailPath.Side s2 : S) {
            if (s1 == s2 || s1 == s2.opposite()) continue;
            for (int k : new int[] {1, 2, 3, 5, 9, 20}) {
                int kx = 100, kz = -40;
                List<WideArc.Cell> cells = WideArc.cells(kx, kz, s1, s2, k);
                double r = k + 0.5;
                double total = 0;
                Set<String> seen = new HashSet<>();
                for (WideArc.Cell c : cells) { total += c.piece.length(); check(seen.add(c.x + "," + c.z), "celda repetida"); }
                check(Math.abs(total - r * Math.PI / 2) < 1e-6, "largo " + total + " vs " + r * Math.PI / 2 + " k=" + k);
                // vía: rectas largas a lo largo de las dos líneas, y las celdas del arco
                Map<String, RailPath.Piece> map = new HashMap<>();
                int far = 3 * k + 6;
                for (int j = k + 1; j <= far; j++) {
                    put(map, kx + s1.dx * j, kz + s1.dz * j, straight(s1));
                    put(map, kx + s2.dx * j, kz + s2.dz * j, straight(s2));
                }
                for (WideArc.Cell c : cells) put(map, c.x, c.z, c.piece);
                RailPath.Lookup lk = (x, y, z) -> map.get(x + "," + z);
                // empezar en el extremo lejano de la recta 1 yendo hacia la esquina
                int sx = kx + s1.dx * far, sz = kz + s1.dz * far;
                RailPath.Piece sp = map.get(sx + "," + sz);
                RailPath.Cursor c = new RailPath.Cursor(sx, 0, sz, sp, sp.length() / 2, 1);
                // elegir la dirección que se acerca a la esquina
                double[] p0 = new double[3]; c.worldPoint(p0);
                RailPath.Cursor t = c.copy(); t.advance(1.0, lk); double[] p1 = new double[3]; t.worldPoint(p1);
                double d0 = Math.hypot(p0[0] - (kx + .5), p0[2] - (kz + .5)), d1 = Math.hypot(p1[0] - (kx + .5), p1[2] - (kz + .5));
                if (d1 > d0) c.dir = -1;
                // distancia total esperada: de la mitad de la recta lejana al final de la otra
                double expect = (far - k - 1) + 0.5 + r * Math.PI / 2 + (far - k);
                double moved = c.advance(expect + 5, lk);
                // al final debe haber llegado al extremo de la recta 2 (no menos que lo esperado, la vía acaba ahí)
                check(Math.abs(moved - expect) < 1e-5, "recorrido " + moved + " vs " + expect + " (" + s1 + "," + s2 + " k=" + k + ")");
                double[] pe = new double[3]; c.worldPoint(pe);
                // el punto final está en la línea 2 (x o z de la esquina + .5)
                if (s2.dx != 0) check(Math.abs(pe[2] - (kz + .5)) < 1e-6, "no queda en la recta 2 (z)"); else check(Math.abs(pe[0] - (kx + .5)) < 1e-6, "no queda en la recta 2 (x)");
                // la dirección al final sigue la recta 2 hacia afuera
                double[] tg = new double[2]; c.shape.tangent(c.s, tg);
                double dot = (tg[0] * s2.dx + tg[1] * s2.dz) * c.dir;
                check(Math.abs(dot - 1.0) < 1e-6, "rumbo final " + dot);
                // y de vuelta, igual
                double back = c.advance(-(expect + 5), lk);
                check(Math.abs(Math.abs(back) - (expect + 0.5)) < 1e-5, "regreso " + back);
                checks++;
            }
        }
        System.out.println("arcos probados: " + checks);
        // k = 1: la cuenta de bloques (3 por cuarto de vuelta para R=1,5 con rectas incluidas)
        List<WideArc.Cell> c1 = WideArc.cells(0, 0, RailPath.Side.WEST, RailPath.Side.SOUTH, 1);
        System.out.println("k=1: " + c1.size() + " bloques");
        check(c1.size() == 3, "k=1 usa 3 bloques");
        System.out.println("OK WideArc");
    }
    static RailPath.Piece straight(RailPath.Side s) { return (s == RailPath.Side.NORTH || s == RailPath.Side.SOUTH) ? RailPath.Shape.NORTH_SOUTH : RailPath.Shape.EAST_WEST; }
    static void put(Map<String, RailPath.Piece> m, int x, int z, RailPath.Piece p) { m.put(x + "," + z, p); }
}
