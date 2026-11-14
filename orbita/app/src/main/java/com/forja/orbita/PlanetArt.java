package com.forja.orbita;

/**
 * Las «texturas» de los planetas, hechas por código (sin imágenes): ruido para continentes, nubes, bandas, cráteres y manchas.
 * Cada esfera se pinta una sola vez con la luz viniendo desde la izquierda; al dibujar se gira para que la luz mire al Sol.
 * Sin nada de Android, así se pueden ver en el computador.
 */
final class PlanetArt {
    private PlanetArt() {}

    static final int SIZE = 192;
    // dirección de la luz (hacia donde está el Sol), normalizada
    private static final double LX = -0.86, LY = 0.30, LZ = 0.41;

    // ---------------------------------------------------------------- ruido 3D

    private static int hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + seed * 362437;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double rnd(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double noise(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
        double c000 = rnd(xi, yi, zi, seed), c100 = rnd(xi + 1, yi, zi, seed), c010 = rnd(xi, yi + 1, zi, seed), c110 = rnd(xi + 1, yi + 1, zi, seed);
        double c001 = rnd(xi, yi, zi + 1, seed), c101 = rnd(xi + 1, yi, zi + 1, seed), c011 = rnd(xi, yi + 1, zi + 1, seed), c111 = rnd(xi + 1, yi + 1, zi + 1, seed);
        double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx, x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    static double fbm(double x, double y, double z, int oct, int seed) {
        double s = 0, a = 0.5, f = 1, norm = 0;
        for (int i = 0; i < oct; i++) {
            s += a * noise(x * f, y * f, z * f, seed + i * 17);
            norm += a;
            a *= 0.5;
            f *= 2.03;
        }
        return s / norm;
    }

    // ---------------------------------------------------------------- colores

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double step(double e0, double e1, double x) {
        return smooth(clamp((x - e0) / (e1 - e0), 0, 1));
    }

    private static double[] rgb(int r, int g, int b) {
        return new double[]{r, g, b};
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double[] scale(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static int pack(double[] c, double alpha) {
        int r = (int) clamp(c[0], 0, 255), g = (int) clamp(c[1], 0, 255), b = (int) clamp(c[2], 0, 255), a = (int) clamp(alpha * 255, 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------- cráteres

    private static double[][] craters(int count, int seed, double minR, double maxR) {
        java.util.Random r = new java.util.Random(seed);
        double[][] c = new double[count][4];
        for (int i = 0; i < count; i++) {
            double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * 2 * Math.PI, rr = Math.sqrt(1 - z * z);
            double size = minR + (maxR - minR) * Math.pow(r.nextDouble(), 2.2);
            c[i] = new double[]{rr * Math.cos(a), z, rr * Math.sin(a), size};
        }
        return c;
    }

    /** Cuánto cambia el brillo por los cráteres: oscuro dentro, borde claro. */
    private static double craterShade(double[][] cr, double x, double y, double z) {
        double s = 0;
        for (double[] c : cr) {
            double dot = x * c[0] + y * c[1] + z * c[2];
            if (dot < 0.5) continue;
            double th = Math.acos(Math.min(1, dot)) / c[3];
            if (th < 1) s -= 0.22 * (1 - th * th) * (0.6 + 0.4 * th);
            else if (th < 1.35) s += 0.16 * (1 - (th - 1) / 0.35);
        }
        return s;
    }

    // ---------------------------------------------------------------- planetas

    /** Una textura sin luz: dado un punto de la esfera (eje polar = y), devuelve el color del terreno, las nubes, las bandas… */
    static final class Tex {
        final String type;
        final int seed, starColor;
        private final double[][] cr;
        private final double rotc, rots;

        Tex(String type, int seed, int starColor) {
            this.type = type;
            this.seed = seed;
            this.starColor = starColor;
            double[][] c = null;
            if (type.equals("mercurio")) c = craters(90, seed + 1, 0.04, 0.22);
            if (type.equals("luna")) c = craters(60, seed + 2, 0.04, 0.2);
            if (type.equals("roca")) c = craters(25, seed + 3, 0.1, 0.35);
            cr = c;
            rotc = Math.cos(seed * 0.7);
            rots = Math.sin(seed * 0.7);
        }

        boolean isStar() {
            return type.equals("estrella") || type.equals("sol");
        }

        /** El color (sin luz) en el punto (bx, by, bz) de la esfera unitaria; by es el eje de los polos. */
        double[] color(double nx, double ny, double nz) {
            double tx = nx * rotc + nz * rots, tz = -nx * rots + nz * rotc, ty = ny;
            double[] col;
        switch (type) {
            case "tierra" -> {
                double e = fbm(tx * 2.2 + 3, ty * 2.2, tz * 2.2, 5, seed);
                double polar = Math.abs(ty) + (fbm(tx * 6, ty * 6, tz * 6, 3, seed + 9) - 0.5) * 0.25;
                if (e < 0.5) {
                    double depth = clamp((0.5 - e) / 0.3, 0, 1);
                    col = mix(rgb(34, 98, 186), rgb(8, 30, 90), depth);
                    double coast = 1 - clamp((0.5 - e) / 0.035, 0, 1);
                    col = mix(col, rgb(70, 160, 190), coast * 0.55);
                } else {
                    double h = clamp((e - 0.5) / 0.28, 0, 1);
                    double dry = fbm(tx * 3.1 + 11, ty * 3.1, tz * 3.1, 4, seed + 5);
                    double[] low = mix(rgb(58, 118, 52), rgb(150, 138, 82), step(0.45, 0.65, dry));
                    col = mix(low, rgb(112, 92, 70), step(0.35, 0.8, h));
                    col = mix(col, rgb(236, 238, 242), step(0.75, 1.0, h) * 0.9);
                }
                col = mix(col, rgb(245, 248, 252), step(0.80, 0.92, polar));
                double cl = fbm(tx * 3.4 + 20, ty * 6.0, tz * 3.4 + 7, 5, seed + 31);
                double cloud = step(0.5, 0.74, cl) * 0.92;
                col = mix(col, rgb(255, 255, 255), cloud);
            }
            case "mercurio" -> {
                double g = 0.62 + 0.5 * fbm(tx * 5, ty * 5, tz * 5, 4, seed);
                col = scale(rgb(150, 143, 136), g * (1 + craterShade(cr, tx, ty, tz)));
            }
            case "luna" -> {
                double mare = step(0.5, 0.62, fbm(tx * 1.6 + 5, ty * 1.6, tz * 1.6, 4, seed));
                double g = 0.8 + 0.3 * fbm(tx * 7, ty * 7, tz * 7, 4, seed + 2);
                col = mix(rgb(178, 176, 170), rgb(92, 92, 98), mare * 0.85);
                col = scale(col, g * (1 + craterShade(cr, tx, ty, tz)));
            }
            case "roca" -> {
                double g = 0.55 + 0.6 * fbm(tx * 4, ty * 4, tz * 4, 4, seed);
                col = scale(rgb(128, 118, 106), g * (1 + craterShade(cr, tx, ty, tz)));
            }
            case "venus" -> {
                double q = fbm(tx * 2.2, ty * 3.5, tz * 2.2, 4, seed);
                double s = fbm(tx * 3 + q * 2.4, ty * 4.5 + q * 1.5, tz * 3 + q * 2.4, 5, seed + 3);
                col = mix(rgb(214, 178, 112), rgb(246, 228, 184), step(0.3, 0.75, s));
                col = mix(col, rgb(232, 200, 140), 0.25 * Math.sin(ty * 7 + q * 4) + 0.25);
            }
            case "marte" -> {
                double d = fbm(tx * 2.6, ty * 2.6, tz * 2.6, 5, seed);
                col = mix(rgb(196, 102, 58), rgb(120, 66, 46), step(0.5, 0.72, d));
                col = scale(col, 0.88 + 0.25 * fbm(tx * 9, ty * 9, tz * 9, 3, seed + 4));
                col = mix(col, rgb(240, 240, 245), step(0.9, 0.97, Math.abs(ty) + (fbm(tx * 8, ty * 8, tz * 8, 2, seed) - 0.5) * 0.1));
            }
            case "jupiter", "gaseoso", "saturno" -> {
                boolean sat = type.equals("saturno");
                double lat = ty;
                double warp = fbm(tx * 5, ty * 1.2, tz * 5, 4, seed) - 0.5;
                double band = Math.sin((lat + warp * (sat ? 0.10 : 0.22)) * (sat ? 15 : 21) + seed);
                double fine = fbm(tx * 14, ty * 3.2, tz * 14, 3, seed + 6);
                double[] a = sat ? rgb(230, 208, 160) : rgb(236, 218, 186);
                double[] b = sat ? rgb(206, 176, 124) : rgb(176, 120, 82);
                double[] c = sat ? rgb(218, 192, 140) : rgb(222, 196, 156);
                double t = clamp(band * 0.5 + 0.5, 0, 1);
                col = t < 0.5 ? mix(b, c, t * 2) : mix(c, a, (t - 0.5) * 2);
                col = scale(col, 0.9 + 0.2 * fine);
                if (type.equals("jupiter")) {
                    // la Gran Mancha Roja
                    double lon = Math.atan2(tx, tz), la = Math.asin(clamp(ty, -1, 1));
                    double d = Math.pow((lon + 0.30) / 0.34, 2) + Math.pow((la + 0.30) / 0.17, 2);
                    if (d < 1.6) {
                        double sw = 0.5 + 0.5 * Math.sin(Math.atan2(la + 0.30, lon + 0.30) * 3 + d * 6);
                        double[] red = mix(rgb(196, 86, 56), rgb(226, 150, 110), sw * 0.5);
                        col = mix(col, red, (1 - step(0.5, 1.6, d)) * 0.95);
                    }
                }
                if (type.equals("gaseoso")) {
                    // un tono distinto por planeta
                    double hue = (seed * 37 % 360) / 360.0;
                    double[] tint = rgb((int) (160 + 90 * Math.sin(hue * 6.28)), (int) (150 + 80 * Math.sin(hue * 6.28 + 2)), (int) (150 + 90 * Math.sin(hue * 6.28 + 4)));
                    col = mix(col, scale(tint, 0.95), 0.3);
                }
            }
            case "urano" -> {
                double b = fbm(tx * 2, ty * 9, tz * 2, 3, seed) * 0.25;
                col = mix(rgb(160, 218, 226), rgb(188, 236, 240), 0.5 + 0.5 * Math.sin(ty * 8 + b * 8));
            }
            case "neptuno" -> {
                double lat = ty, warp = fbm(tx * 4, ty * 1.4, tz * 4, 4, seed) - 0.5;
                double band = 0.5 + 0.5 * Math.sin((lat + warp * 0.2) * 14);
                col = mix(rgb(48, 84, 206), rgb(86, 132, 236), band * 0.7);
                double lon = Math.atan2(tx, tz), la = Math.asin(clamp(ty, -1, 1));
                double d = Math.pow((lon + 0.25) / 0.3, 2) + Math.pow((la + 0.4) / 0.16, 2);
                if (d < 1) col = mix(col, rgb(24, 40, 130), (1 - d) * 0.85);
                // nubes blancas finas
                double w = step(0.62, 0.8, fbm(tx * 7, ty * 14, tz * 7, 3, seed + 8));
                col = mix(col, rgb(225, 235, 255), w * 0.45);
            }
            case "rocoso" -> {
                // un mundo aleatorio: desierto, océano o hielo según la semilla
                int kind = Math.floorMod(seed, 3);
                double e = fbm(tx * 2.4 + 3, ty * 2.4, tz * 2.4, 5, seed);
                double[] lo = kind == 0 ? rgb(176, 120, 70) : kind == 1 ? rgb(30, 80, 160) : rgb(200, 220, 235);
                double[] hi = kind == 0 ? rgb(120, 78, 50) : kind == 1 ? rgb(70, 130, 70) : rgb(240, 245, 255);
                col = mix(lo, hi, step(0.4, 0.62, e));
                col = scale(col, 0.88 + 0.22 * fbm(tx * 9, ty * 9, tz * 9, 3, seed + 4));
            }
            default -> {   // sol y estrellas
                double gran = fbm(tx * 11, ty * 11, tz * 11, 4, seed);
                double spots = step(0.66, 0.78, fbm(tx * 3 + 8, ty * 3, tz * 3, 3, seed + 12));
                double[] base = new double[]{(starColor >> 16) & 255, (starColor >> 8) & 255, starColor & 255};
                double[] hot = mix(base, rgb(255, 255, 235), 0.45);
                col = mix(scale(base, 0.82), hot, step(0.3, 0.75, gran));
                col = mix(col, scale(base, 0.45), spots * 0.8);
            }
        }
            return col;
        }
    }

    /** Pinta una esfera con la luz desde la izquierda (la versión fija que se usa de lejos). */
    static int[] make(String type, int seed, int starColor) {
        int S = SIZE;
        int[] px = new int[S * S];
        Tex tex = new Tex(type, seed, starColor);
        for (int j = 0; j < S; j++) {
            for (int i = 0; i < S; i++) {
                double u = (i + 0.5) / S * 2 - 1, v = 1 - (j + 0.5) / S * 2;
                double r2 = u * u + v * v;
                double r = Math.sqrt(r2);
                if (r > 1.0 + 1.5 / S) continue;
                double edge = clamp((1.0 - r) * S / 2.0 + 0.5, 0, 1);    // borde suave
                double nz = Math.sqrt(Math.max(0, 1 - r2));
                double[] col = tex.color(u, v, nz);
                if (tex.isStar()) {
                    col = scale(col, 0.5 + 0.5 * Math.pow(nz, 0.55));
                } else {
                    col = lightPixel(type, col, u * LX + v * LY + nz * LZ, r);
                }
                px[j * S + i] = pack(col, edge);
            }
        }
        return px;
    }

    /** Aplica la luz (día y noche) y los brillos de la atmósfera a un color. dif = qué tan de frente le da el Sol; r = distancia al centro (0 a 1). */
    static double[] lightPixel(String type, double[] col, double dif, double r) {
        double lit = 0.05 + 0.95 * step(-0.12, 0.55, dif);
        col = scale(col, lit);
        if (type.equals("tierra")) {
            double rim = Math.pow(r, 5) * 0.55 * clamp(dif + 0.4, 0, 1);
            col = mix(col, rgb(130, 190, 255), clamp(rim, 0, 0.6));
        } else if (type.equals("venus") || type.equals("marte")) {
            double rim = Math.pow(r, 6) * 0.25 * clamp(dif + 0.4, 0, 1);
            col = mix(col, rgb(255, 235, 200), clamp(rim, 0, 0.4));
        }
        return col;
    }

    /** El mapa completo del planeta (proyección rectangular: longitud a lo ancho, latitud a lo alto), sin luz. */
    static int[] map(String type, int seed, int starColor, int w, int h) {
        Tex tex = new Tex(type, seed, starColor);
        int[] out = new int[w * h];
        for (int j = 0; j < h; j++) {
            double lat = (0.5 - (j + 0.5) / h) * Math.PI;
            double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int i = 0; i < w; i++) {
                double lon = ((i + 0.5) / w - 0.5) * 2 * Math.PI;
                out[j * w + i] = pack(tex.color(cl * Math.sin(lon), sl, cl * Math.cos(lon)), 1);
            }
        }
        return out;
    }

    /**
     * Dibuja la esfera vista desde cualquier ángulo: la cámara mira el planeta con la orientación (R derecha, U arriba, V hacia el que mira),
     * y el Sol está en la dirección «sun» (todo en ejes del mundo; el polo norte del planeta apunta a -z). spin gira el planeta sobre sí mismo.
     */
    static void render(int[] out, int n, int[] map, int mw, int mh, String type, boolean star, double[] R, double[] U, double[] V, double[] sun, double spin) {
        for (int j = 0; j < n; j++) {
            double v = 1 - (j + 0.5) / n * 2;
            for (int i = 0; i < n; i++) {
                double u = (i + 0.5) / n * 2 - 1;
                double r2 = u * u + v * v;
                if (r2 > 1.0 + 3.0 / n) {
                    out[j * n + i] = 0;
                    continue;
                }
                double r = Math.sqrt(r2);
                double edge = clamp((1.0 - r) * n / 2.0 + 0.5, 0, 1);
                double nz = Math.sqrt(Math.max(0, 1 - Math.min(1, r2)));
                // normal en ejes del mundo
                double wx = u * R[0] + v * U[0] + nz * V[0], wy = u * R[1] + v * U[1] + nz * V[1], wz = u * R[2] + v * U[2] + nz * V[2];
                // ejes del planeta: el norte es -z del mundo
                double by = -wz, bx = wx, bz = wy;
                double lat = Math.asin(clamp(by, -1, 1)), lon = Math.atan2(bx, bz) + spin;
                double fx = (lon / (2 * Math.PI) + 0.5) % 1.0;
                if (fx < 0) fx += 1;
                int ix = Math.min(mw - 1, (int) (fx * mw)), iy = Math.min(mh - 1, Math.max(0, (int) ((0.5 - lat / Math.PI) * mh)));
                int c = map[iy * mw + ix];
                double[] col = {(c >> 16) & 255, (c >> 8) & 255, c & 255};
                if (star) {
                    col = scale(col, 0.5 + 0.5 * Math.pow(nz, 0.55));
                } else {
                    col = lightPixel(type, col, wx * sun[0] + wy * sun[1] + wz * sun[2], r);
                }
                out[j * n + i] = pack(col, edge);
            }
        }
    }

    /** Qué textura usa cada cuerpo del sistema. */
    static String typeFor(String bodyName) {
        return switch (bodyName) {
            case "Mercurio" -> "mercurio";
            case "Venus" -> "venus";
            case "Tierra" -> "tierra";
            case "Luna" -> "luna";
            case "Marte" -> "marte";
            case "Júpiter" -> "jupiter";
            case "Saturno" -> "saturno";
            case "Urano" -> "urano";
            case "Neptuno" -> "neptuno";
            case "Sol" -> "estrella";
            default -> null;
        };
    }
}
