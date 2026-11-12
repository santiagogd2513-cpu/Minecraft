package com.example.beautifuldecor.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * V6.1.0: curvas anchas. Dos rectas que se cruzan en el bloque esquina K (por los lados {@code s1} y {@code s2}) se unen con un arco de radio
 * R = k + 0,5 tangente a las dos: arranca en el punto medio del borde entre los bloques k y k+1 de cada recta, así que empalma con el riel
 * recto de siempre. k = 0 es la curva de Minecraft; cada k más la hace más ancha, sin límite. Sin nada de Minecraft, para poder probarla.
 */
public final class WideArc {
    private WideArc() {
    }

    /** Un pedazo del arco: lo que queda dentro de un bloque. Coordenadas relativas al bloque. */
    public static final class Piece implements RailPath.Piece {
        private final RailPath.Side sideA;
        private final RailPath.Side sideB;
        private final double alongA;
        private final double alongB;
        private final double ox;
        private final double oz;
        private final double r;
        private final double thetaA;
        private final double thetaB;
        private final double u1x;
        private final double u1z;
        private final double u2x;
        private final double u2z;

        Piece(RailPath.Side a, double alongA, RailPath.Side b, double alongB, double ox, double oz, double r, double thetaA, double thetaB,
              double u1x, double u1z, double u2x, double u2z) {
            this.sideA = a;
            this.sideB = b;
            this.alongA = alongA;
            this.alongB = alongB;
            this.ox = ox;
            this.oz = oz;
            this.r = r;
            this.thetaA = thetaA;
            this.thetaB = thetaB;
            this.u1x = u1x;
            this.u1z = u1z;
            this.u2x = u2x;
            this.u2z = u2z;
        }

        @Override
        public RailPath.Side sideA() {
            return this.sideA;
        }

        @Override
        public int hA() {
            return 0;
        }

        @Override
        public RailPath.Side sideB() {
            return this.sideB;
        }

        @Override
        public int hB() {
            return 0;
        }

        @Override
        public double length() {
            return this.r * (this.thetaB - this.thetaA);
        }

        @Override
        public void point(double s, double[] out) {
            double th = this.thetaA + Math.max(0.0, Math.min(this.length(), s)) / this.r;
            out[0] = this.ox + this.r * (-this.u2x * Math.cos(th) - this.u1x * Math.sin(th));
            out[1] = 0.0;
            out[2] = this.oz + this.r * (-this.u2z * Math.cos(th) - this.u1z * Math.sin(th));
        }

        @Override
        public void tangent(double s, double[] out) {
            double th = this.thetaA + Math.max(0.0, Math.min(this.length(), s)) / this.r;
            out[0] = this.u2x * Math.sin(th) - this.u1x * Math.cos(th);
            out[1] = this.u2z * Math.sin(th) - this.u1z * Math.cos(th);
        }

        @Override
        public double slopeAB() {
            return 0.0;
        }

        @Override
        public double alongAt(int end) {
            return end == 0 ? this.alongA : this.alongB;
        }

        @Override
        public int endAt(RailPath.Side side, int height, double along) {
            if (height != 0) {
                return -1;
            }
            if (this.sideA == side && Math.abs(this.alongA - along) < 1.0e-4) {
                return 0;
            }
            if (this.sideB == side && Math.abs(this.alongB - along) < 1.0e-4) {
                return 1;
            }
            return -1;
        }
    }

    /** Un bloque del arco y su pedazo. */
    public static final class Cell {
        public final int x;
        public final int z;
        public final Piece piece;
        /** ¿Está sobre una de las dos rectas (el bloque esquina o uno de la fila / columna)? Esos eran rieles rectos. */
        public final boolean onLine;

        Cell(int x, int z, Piece piece, boolean onLine) {
            this.x = x;
            this.z = z;
            this.piece = piece;
            this.onLine = onLine;
        }
    }

    private static double[] at(double theta, double ox, double oz, double r, double u1x, double u1z, double u2x, double u2z) {
        return new double[] {ox + r * (-u2x * Math.cos(theta) - u1x * Math.sin(theta)), oz + r * (-u2z * Math.cos(theta) - u1z * Math.sin(theta))};
    }

    /** Valores de theta (0..pi/2) donde la coordenada f(theta) vale un entero (f es monótona en el intervalo). */
    private static void crossings(boolean useX, double ox, double oz, double r, double u1x, double u1z, double u2x, double u2z, List<Double> out) {
        double a = at(0.0, ox, oz, r, u1x, u1z, u2x, u2z)[useX ? 0 : 1];
        double b = at(Math.PI / 2, ox, oz, r, u1x, u1z, u2x, u2z)[useX ? 0 : 1];
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        for (int n = (int) Math.floor(lo) + 1; n < hi - 1.0e-9; n++) {
            if (n <= lo + 1.0e-9) {
                continue;
            }
            double l = 0.0;
            double h = Math.PI / 2;
            boolean increasing = b > a;
            for (int i = 0; i < 60; i++) {
                double m = (l + h) / 2;
                double v = at(m, ox, oz, r, u1x, u1z, u2x, u2z)[useX ? 0 : 1];
                if ((v < n) == increasing) {
                    l = m;
                } else {
                    h = m;
                }
            }
            out.add((l + h) / 2);
        }
    }

    private static RailPath.Side sideOf(double rx, double rz, boolean preferX) {
        boolean w = Math.abs(rx) < 1.0e-6;
        boolean e = Math.abs(rx - 1.0) < 1.0e-6;
        boolean n = Math.abs(rz) < 1.0e-6;
        boolean s = Math.abs(rz - 1.0) < 1.0e-6;
        if (preferX && (w || e)) {
            return w ? RailPath.Side.WEST : RailPath.Side.EAST;
        }
        if (n || s) {
            return n ? RailPath.Side.NORTH : RailPath.Side.SOUTH;
        }
        return w ? RailPath.Side.WEST : RailPath.Side.EAST;
    }

    /**
     * Los bloques que cruza el arco de radio k + 0,5 que une las rectas que salen del bloque esquina (kx, kz) por los lados s1 y s2
     * (que tienen que ser perpendiculares), en orden desde el lado s1.
     */
    public static List<Cell> cells(int kx, int kz, RailPath.Side s1, RailPath.Side s2, int k) {
        double r = k + 0.5;
        double px = kx + 0.5;
        double pz = kz + 0.5;
        double u1x = s1.dx;
        double u1z = s1.dz;
        double u2x = s2.dx;
        double u2z = s2.dz;
        double ox = px + r * (u1x + u2x);
        double oz = pz + r * (u1z + u2z);
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0);
        cuts.add(Math.PI / 2);
        crossings(true, ox, oz, r, u1x, u1z, u2x, u2z, cuts);
        crossings(false, ox, oz, r, u1x, u1z, u2x, u2z, cuts);
        cuts.sort(Double::compare);
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i + 1 < cuts.size(); i++) {
            double ta = cuts.get(i);
            double tb = cuts.get(i + 1);
            if (tb - ta < 1.0e-9) {
                continue;
            }
            double[] mid = at((ta + tb) / 2, ox, oz, r, u1x, u1z, u2x, u2z);
            int cx = (int) Math.floor(mid[0]);
            int cz = (int) Math.floor(mid[1]);
            double[] pa = at(ta, ox, oz, r, u1x, u1z, u2x, u2z);
            double[] pb = at(tb, ox, oz, r, u1x, u1z, u2x, u2z);
            double rax = pa[0] - cx;
            double raz = pa[1] - cz;
            double rbx = pb[0] - cx;
            double rbz = pb[1] - cz;
            // el extremo por el que entra y por el que sale: el lado cuya coordenada es 0 o 1 (en la punta, la que no es la del eje)
            RailPath.Side a = sideOf(rax, raz, i == 0 ? Math.abs(u1x) > 0.5 : true);
            RailPath.Side b = sideOf(rbx, rbz, i + 2 == cuts.size() ? Math.abs(u2x) > 0.5 : true);
            double alongA = (a == RailPath.Side.NORTH || a == RailPath.Side.SOUTH) ? rax : raz;
            double alongB = (b == RailPath.Side.NORTH || b == RailPath.Side.SOUTH) ? rbx : rbz;
            Piece piece = new Piece(a, alongA, b, alongB, ox - cx, oz - cz, r, ta, tb, u1x, u1z, u2x, u2z);
            boolean onLine = (cz == kz && u1z == 0.0 || cx == kx && u1x == 0.0 || cz == kz && u2z == 0.0 || cx == kx && u2x == 0.0);
            out.add(new Cell(cx, cz, piece, onLine));
        }
        return out;
    }
}
