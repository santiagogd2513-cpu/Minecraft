package com.example.beautifuldecor.entity;

/**
 * V4.8.0: la geometría de los rieles de carga, sin nada de Minecraft para poder probarla suelta.
 * <p>
 * Cada bloque de riel tiene una forma (las mismas 10 de Minecraft) y por dentro un camino de un extremo (A) al otro (B):
 * recto (1 m), subida (1,414 m: sube 1 bloque en 1) o curva (cuarto de círculo de radio 0,5 alrededor de la esquina: 0,785 m).
 * Un {@link Cursor} es un punto que recorre ese camino y pasa solo de un bloque al siguiente (tramos planos, subidas y bajadas).
 * Unidades: 1 bloque = 1 m. Los ejes son los de Minecraft: x hacia el este, z hacia el sur, y hacia arriba.
 */
public final class RailPath {
    /** Altura de la cabeza del riel sobre la base del bloque (bloques). */
    public static final double RAIL_TOP = 0.25;

    private RailPath() {
    }

    public enum Side {
        NORTH(0, -1, 0.5, 0.0), EAST(1, 0, 1.0, 0.5), SOUTH(0, 1, 0.5, 1.0), WEST(-1, 0, 0.0, 0.5);

        public final int dx;
        public final int dz;
        /** Punto medio de ese lado del bloque (coordenadas dentro del bloque). */
        public final double mx;
        public final double mz;

        Side(int dx, int dz, double mx, double mz) {
            this.dx = dx;
            this.dz = dz;
            this.mx = mx;
            this.mz = mz;
        }

        public Side opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                default -> EAST;
            };
        }
    }

    /** Las 10 formas de riel de Minecraft (mismos nombres que RailShape). hA/hB = altura del extremo (0 abajo, 1 arriba). */
    public enum Shape {
        NORTH_SOUTH(Side.NORTH, 0, Side.SOUTH, 0, false),
        EAST_WEST(Side.WEST, 0, Side.EAST, 0, false),
        ASCENDING_EAST(Side.WEST, 0, Side.EAST, 1, false),
        ASCENDING_WEST(Side.EAST, 0, Side.WEST, 1, false),
        ASCENDING_NORTH(Side.SOUTH, 0, Side.NORTH, 1, false),
        ASCENDING_SOUTH(Side.NORTH, 0, Side.SOUTH, 1, false),
        SOUTH_EAST(Side.SOUTH, 0, Side.EAST, 0, true),
        SOUTH_WEST(Side.SOUTH, 0, Side.WEST, 0, true),
        NORTH_WEST(Side.NORTH, 0, Side.WEST, 0, true),
        NORTH_EAST(Side.NORTH, 0, Side.EAST, 0, true);

        public final Side sideA;
        public final int hA;
        public final Side sideB;
        public final int hB;
        public final boolean curve;
        private final double length;
        // curva: esquina, y vectores unitarios del centro hacia cada extremo
        private final double cx;
        private final double cz;
        private final double uax;
        private final double uaz;
        private final double ubx;
        private final double ubz;

        Shape(Side a, int hA, Side b, int hB, boolean curve) {
            this.sideA = a;
            this.hA = hA;
            this.sideB = b;
            this.hB = hB;
            this.curve = curve;
            if (curve) {
                this.length = Math.PI * 0.25;
                this.cx = b.mx;                     // el lado este/oeste pone la x de la esquina
                this.cz = a.mz;                     // el lado norte/sur pone la z de la esquina
                this.uax = (a.mx - cx) / 0.5;
                this.uaz = (a.mz - cz) / 0.5;
                this.ubx = (b.mx - cx) / 0.5;
                this.ubz = (b.mz - cz) / 0.5;
            } else {
                this.length = hA != hB ? Math.sqrt(2.0) : 1.0;
                this.cx = this.cz = this.uax = this.uaz = this.ubx = this.ubz = 0.0;
            }
        }

        /** Largo del camino dentro del bloque (m). */
        public double length() {
            return this.length;
        }

        public boolean ascending() {
            return this.hA != this.hB;
        }

        /** Posición dentro del bloque a distancia s del extremo A: out = {x, y (0..1), z}. */
        public void point(double s, double[] out) {
            double c = Math.max(0.0, Math.min(this.length, s));
            if (this.curve) {
                double phi = c / 0.5;
                out[0] = this.cx + 0.5 * (this.uax * Math.cos(phi) + this.ubx * Math.sin(phi));
                out[1] = 0.0;
                out[2] = this.cz + 0.5 * (this.uaz * Math.cos(phi) + this.ubz * Math.sin(phi));
            } else {
                double t = c / this.length;
                out[0] = this.sideA.mx + (this.sideB.mx - this.sideA.mx) * t;
                out[1] = this.hA + (this.hB - this.hA) * t;
                out[2] = this.sideA.mz + (this.sideB.mz - this.sideA.mz) * t;
            }
        }

        /** Dirección horizontal (unitaria) de A hacia B a distancia s: out = {tx, tz}. */
        public void tangent(double s, double[] out) {
            if (this.curve) {
                double phi = Math.max(0.0, Math.min(this.length, s)) / 0.5;
                out[0] = -this.uax * Math.sin(phi) + this.ubx * Math.cos(phi);
                out[1] = -this.uaz * Math.sin(phi) + this.ubz * Math.cos(phi);
            } else {
                double dx = this.sideB.mx - this.sideA.mx;
                double dz = this.sideB.mz - this.sideA.mz;
                double n = Math.sqrt(dx * dx + dz * dz);
                out[0] = dx / n;
                out[1] = dz / n;
            }
        }

        /** Seno del ángulo de subida yendo de A hacia B (0 en plano y en curva). */
        public double slopeAB() {
            return this.ascending() ? (this.hB - this.hA) / this.length : 0.0;
        }

        /** El extremo (0 = A, 1 = B) que está en ese lado y a esa altura, o -1. */
        public int endAt(Side side, int height) {
            if (this.sideA == side && this.hA == height) {
                return 0;
            }
            if (this.sideB == side && this.hB == height) {
                return 1;
            }
            return -1;
        }
    }

    /** Quien dice qué riel hay en cada bloque (null = no hay riel, o el chunk no está cargado). */
    public interface Lookup {
        Shape shapeAt(int x, int y, int z);
    }

    /** Un punto sobre la vía. Avanzar es moverse hacia el frente del vagón; las distancias negativas, hacia atrás. */
    public static final class Cursor {
        public int x;
        public int y;
        public int z;
        public Shape shape;
        /** Distancia desde el extremo A dentro de este bloque. */
        public double s;
        /** +1 si el frente del vagón va de A hacia B en este bloque; -1 si va de B hacia A. */
        public int dir = 1;

        public Cursor(int x, int y, int z, Shape shape, double s, int dir) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.shape = shape;
            this.s = s;
            this.dir = dir;
        }

        public Cursor copy() {
            return new Cursor(this.x, this.y, this.z, this.shape, this.s, this.dir);
        }

        /**
         * Mueve el punto {@code dist} metros (negativo = hacia atrás). Si se acaba la vía, se queda en el final.
         *
         * @return lo que de verdad avanzó (mismo signo que dist; menor en valor absoluto si chocó con el fin de la vía)
         */
        public double advance(double dist, Lookup lookup) {
            if (dist == 0.0) {
                return 0.0;
            }
            double sign = dist > 0 ? 1.0 : -1.0;
            double left = Math.abs(dist);
            double moved = 0.0;
            for (int guard = 0; guard < 200 && left > 1.0e-9; guard++) {
                double motion = this.dir * sign;                       // +1: s crece; -1: s decrece
                double toEnd = motion > 0 ? this.shape.length() - this.s : this.s;
                if (left <= toEnd) {
                    this.s += motion * left;
                    moved += left;
                    left = 0.0;
                    break;
                }
                // Llega al final de este bloque: se mira el siguiente.
                RailPath.Side side = motion > 0 ? this.shape.sideB : this.shape.sideA;
                int height = motion > 0 ? this.shape.hB : this.shape.hA;
                int nx = this.x + side.dx;
                int nz = this.z + side.dz;
                RailPath.Side entry = side.opposite();
                boolean found = false;
                for (int k = 0; k < 2 && !found; k++) {
                    int ny = this.y + height - k;
                    int need = k == 0 ? 0 : 1;                         // al mismo nivel entra por el extremo bajo; un nivel abajo, por el alto
                    Shape next = lookup.shapeAt(nx, ny, nz);
                    if (next == null) {
                        continue;
                    }
                    int end = next.endAt(entry, need);
                    if (end < 0) {
                        continue;
                    }
                    moved += toEnd;
                    left -= toEnd;
                    this.x = nx;
                    this.y = ny;
                    this.z = nz;
                    this.shape = next;
                    this.s = end == 0 ? 0.0 : next.length();
                    int motionNew = end == 0 ? 1 : -1;
                    this.dir = (int) (motionNew * sign);
                    found = true;
                }
                if (!found) {
                    this.s = motion > 0 ? this.shape.length() : 0.0;
                    moved += toEnd;
                    left = 0.0;
                    return sign * Math.min(Math.abs(moved), Math.abs(dist));
                }
            }
            return sign * moved;
        }

        /** Posición en el mundo de la cabeza del riel: out = {x, y, z}. */
        public void worldPoint(double[] out) {
            this.shape.point(this.s, out);
            out[0] += this.x;
            out[1] += this.y + RAIL_TOP;
            out[2] += this.z;
        }

        /** Seno de la pendiente en el sentido de marcha (positivo = sube). */
        public double slope() {
            return this.dir * this.shape.slopeAB();
        }
    }

    /** Guiñada de Minecraft (grados) de una dirección horizontal. */
    public static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
