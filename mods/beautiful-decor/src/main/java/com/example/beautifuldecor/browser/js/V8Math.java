package com.example.beautifuldecor.browser.js;

import java.math.BigDecimal;
import java.math.MathContext;

/** Funciones matemáticas calcadas de las de V8 (fdlibm) para que los resultados coincidan bit a bit con los de los navegadores. */
final class V8Math {
    private static final double LG1 = 6.666666666666735130e-01;
    private static final double LG2 = 3.999999999940941908e-01;
    private static final double LG3 = 2.857142874366239149e-01;
    private static final double LG4 = 2.222219843214978396e-01;
    private static final double LG5 = 1.818357216161805012e-01;
    private static final double LG6 = 1.531383769920937332e-01;
    private static final double LG7 = 1.479819860511658591e-01;
    private static final double LN2 = 6.93147180559945286227e-01;

    private V8Math() {
    }

    private static int hi(double x) {
        return (int) (Double.doubleToRawLongBits(x) >>> 32);
    }

    private static int lo(double x) {
        return (int) Double.doubleToRawLongBits(x);
    }

    static double exp(double x) {
        if (x == 1.0) {
            return Math.E;
        }
        return StrictMath.exp(x);
    }

    static double cosh(double x) {
        int ix = hi(x) & 0x7fffffff;
        if (ix < 0x3fd62e43) {
            double t = StrictMath.expm1(Math.abs(x));
            double w = 1.0 + t;
            if (ix < 0x3c800000) {
                return w;
            }
            return 1.0 + (t * t) / (w + w);
        }
        if (ix < 0x40360000) {
            double t = exp(Math.abs(x));
            return 0.5 * t + 0.5 / t;
        }
        if (ix < 0x40862e42) {
            return 0.5 * exp(Math.abs(x));
        }
        if (Math.abs(x) <= 710.4758600739439) {
            double w = exp(0.5 * Math.abs(x));
            double t = 0.5 * w;
            return t * w;
        }
        if (ix >= 0x7ff00000) {
            return x * x;
        }
        return Double.POSITIVE_INFINITY;
    }

    static double sinh(double x) {
        double h = x < 0 ? -0.5 : 0.5;
        double ax = Math.abs(x);
        if (ax < 22) {
            if (ax < 3.725290298461914e-9) {
                return x;
            }
            double t = StrictMath.expm1(ax);
            if (ax < 1) {
                return h * (2.0 * t - t * t / (t + 1.0));
            }
            return h * (t + t / (t + 1.0));
        }
        if (ax < 709.7822265625) {
            return h * exp(ax);
        }
        if (ax <= 710.4758600739439) {
            double w = exp(0.5 * ax);
            double t = h * w;
            return t * w;
        }
        return x * 1.0e307;
    }

    static double acosh(double x) {
        int hx = hi(x);
        int lx = lo(x);
        if (hx < 0x3ff00000) {
            return Double.NaN;
        } else if (hx >= 0x41b00000) {
            if (hx >= 0x7ff00000) {
                return x + x;
            }
            return StrictMath.log(x) + LN2;
        } else if (((hx - 0x3ff00000) | lx) == 0) {
            return 0.0;
        } else if (hx > 0x40000000) {
            double t = x * x;
            return StrictMath.log(2.0 * x - 1.0 / (x + Math.sqrt(t - 1.0)));
        } else {
            double t = x - 1.0;
            return StrictMath.log1p(t + Math.sqrt(2.0 * t + t * t));
        }
    }

    static double asinh(double x) {
        int hx = hi(x);
        int ix = hx & 0x7fffffff;
        if (ix >= 0x7ff00000) {
            return x + x;
        }
        if (ix < 0x3e300000) {
            return x;
        }
        double w;
        if (ix > 0x41b00000) {
            w = StrictMath.log(Math.abs(x)) + LN2;
        } else if (ix > 0x40000000) {
            double t = Math.abs(x);
            w = StrictMath.log(2.0 * t + 1.0 / (Math.sqrt(x * x + 1.0) + t));
        } else {
            double t = x * x;
            w = StrictMath.log1p(Math.abs(x) + t / (1.0 + Math.sqrt(1.0 + t)));
        }
        return hx > 0 ? w : -w;
    }

    static double atanh(double x) {
        int hx = hi(x);
        int lx = lo(x);
        int ix = hx & 0x7fffffff;
        if ((ix | ((lx | -lx) >>> 31)) > 0x3ff00000) {
            return Double.NaN;
        }
        if (ix == 0x3ff00000) {
            return x / 0.0;
        }
        if (ix < 0x3e300000) {
            return x;
        }
        double ax = Math.abs(x);
        double t;
        if (ix < 0x3fe00000) {
            t = ax + ax;
            t = 0.5 * StrictMath.log1p(t + t * ax / (1.0 - ax));
        } else {
            t = 0.5 * StrictMath.log1p((ax + ax) / (1.0 - ax));
        }
        return hx >= 0 ? t : -t;
    }

    private static double kLog1p(double f) {
        double s = f / (2.0 + f);
        double z = s * s;
        double w = z * z;
        double t1 = w * (LG2 + w * (LG4 + w * LG6));
        double t2 = z * (LG1 + w * (LG3 + w * (LG5 + w * LG7)));
        double r = t2 + t1;
        double hfsq = 0.5 * f * f;
        return s * (hfsq + r);
    }

    static double log2(double x0) {
        double two54 = 1.80143985094819840000e+16;
        double ivln2hi = 1.44269504072144627571e+00;
        double ivln2lo = 1.67517131648865118353e-10;
        double x = x0;
        int hx = hi(x);
        int lx = lo(x);
        int k = 0;
        if (hx < 0x00100000) {
            if (((hx & 0x7fffffff) | lx) == 0) {
                return Double.NEGATIVE_INFINITY;
            }
            if (hx < 0) {
                return Double.NaN;
            }
            k -= 54;
            x *= two54;
            hx = hi(x);
        }
        if (hx >= 0x7ff00000) {
            return x + x;
        }
        if (hx == 0x3ff00000 && lx == 0) {
            return 0.0;
        }
        k += (hx >> 20) - 1023;
        hx &= 0x000fffff;
        int i = (hx + 0x95f64) & 0x100000;
        long bits = Double.doubleToRawLongBits(x);
        bits = ((long) (hx | (i ^ 0x3ff00000)) << 32) | (bits & 0xFFFFFFFFL);
        x = Double.longBitsToDouble(bits);
        k += i >> 20;
        double y = k;
        double f = x - 1.0;
        double hfsq = 0.5 * f * f;
        double r = kLog1p(f);
        double h = f - hfsq;
        h = Double.longBitsToDouble(Double.doubleToRawLongBits(h) & 0xFFFFFFFF00000000L);
        double l = (f - h) - hfsq + r;
        double valHi = h * ivln2hi;
        double valLo = (l + h) * ivln2lo + l * ivln2hi;
        double w = y + valHi;
        valLo += (y - w) + valHi;
        valHi = w;
        return valLo + valHi;
    }

    // ------------------------------------------------------------------ cbrt (FreeBSD, como V8)

    static double cbrt(double x) {
        final int b1 = 715094163;
        final int b2 = 696219795;
        final double p0 = 1.87595182427177009643;
        final double p1 = -1.88497979543377169875;
        final double p2 = 1.621429720105354466140;
        final double p3 = -0.758397934778766047437;
        final double p4 = 0.145996192886612446982;
        int hx = hi(x);
        int low = lo(x);
        int sign = hx & 0x80000000;
        hx ^= sign;
        if (hx >= 0x7ff00000) {
            return x + x;
        }
        double t;
        if (hx < 0x00100000) {
            if ((hx | low) == 0) {
                return x;
            }
            t = Double.longBitsToDouble(0x4350000000000000L);
            t *= x;
            int high = hi(t);
            t = Double.longBitsToDouble(((long) (sign | ((high & 0x7fffffff) / 3 + b2))) << 32);
        } else {
            t = Double.longBitsToDouble(((long) (sign | (hx / 3 + b1))) << 32);
        }
        double r = (t * t) * (t / x);
        t = t * ((p0 + r * (p1 + r * p2)) + ((r * r) * r) * (p3 + r * p4));
        long bits = Double.doubleToRawLongBits(t);
        bits = (bits + 0x80000000L) & 0xffffffffc0000000L;
        t = Double.longBitsToDouble(bits);
        double s = t * t;
        r = x / s;
        double w = t + t;
        r = (r - t) / (w + r);
        t = t + t * r;
        return t;
    }

    // ------------------------------------------------------------------ hypot (como V8)

    static double hypot(double[] v) {
        boolean nan = false;
        double max = 0;
        for (double d : v) {
            if (Double.isInfinite(d)) {
                return Double.POSITIVE_INFINITY;
            }
            if (Double.isNaN(d)) {
                nan = true;
            } else {
                max = Math.max(max, Math.abs(d));
            }
        }
        if (nan) {
            return Double.NaN;
        }
        if (max == 0) {
            return 0;
        }
        double sum = 0;
        double comp = 0;
        for (double d : v) {
            double n = Math.abs(d) / max;
            double summand = n * n - comp;
            double prelim = sum + summand;
            comp = (prelim - sum) - summand;
            sum = prelim;
        }
        return Math.sqrt(sum) * max;
    }

    // ------------------------------------------------------------------ pow redondeada correctamente

    private static final MathContext MC = new MathContext(64);
    private static final BigDecimal BD_LN2 = new BigDecimal(
            "0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418687");
    private static final BigDecimal BD_TWO = BigDecimal.valueOf(2);

    private static BigDecimal ln(double x) {
        int e = Math.getExponent(x);
        if (e < Double.MIN_EXPONENT) {
            x *= 0x1p54;
            e = Math.getExponent(x) - 54;
        }
        double m = Math.scalb(x, -Math.getExponent(x));
        int ex = e;
        if (m > 1.4142135623730951) {
            m /= 2;
            ex++;
        }
        BigDecimal bm = new BigDecimal(m);
        BigDecimal z = bm.subtract(BigDecimal.ONE).divide(bm.add(BigDecimal.ONE), MC);
        BigDecimal z2 = z.multiply(z, MC);
        BigDecimal term = z;
        BigDecimal sum = z;
        BigDecimal eps = new BigDecimal("1e-62");
        for (int k = 1; k < 200; k++) {
            term = term.multiply(z2, MC);
            BigDecimal add = term.divide(BigDecimal.valueOf(2L * k + 1), MC);
            sum = sum.add(add, MC);
            if (add.abs().compareTo(eps) < 0) {
                break;
            }
        }
        return sum.multiply(BD_TWO, MC).add(BD_LN2.multiply(BigDecimal.valueOf(ex), MC), MC);
    }

    /** exp(t) para t BigDecimal; devuelve {mantisa en [0.7,1.5], exponente de 2}. */
    private static double expScaled(BigDecimal t, int[] k2) {
        BigDecimal kq = t.divide(BD_LN2, MC).setScale(0, java.math.RoundingMode.HALF_EVEN);
        int k = kq.intValueExact();
        BigDecimal r = t.subtract(BD_LN2.multiply(kq, MC), MC).divide(BigDecimal.valueOf(256), MC);
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal eps = new BigDecimal("1e-64");
        for (int n = 1; n < 60; n++) {
            term = term.multiply(r, MC).divide(BigDecimal.valueOf(n), MC);
            sum = sum.add(term, MC);
            if (term.abs().compareTo(eps) < 0) {
                break;
            }
        }
        for (int i = 0; i < 8; i++) {
            sum = sum.multiply(sum, MC);
        }
        k2[0] = k;
        return sum.doubleValue();
    }

    static double pow(double x, double y) {
        if (y == 0) {
            return 1;
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        if (y == 1) {
            return x;
        }
        boolean yInt = y == Math.rint(y);
        if (Double.isInfinite(x) || Double.isInfinite(y) || x == 0 || (x < 0 && !yInt)) {
            return StrictMath.pow(x, y);
        }
        if (x < 0) {
            double r = pow(-x, y);
            boolean odd = Math.abs(y) < 9007199254740992.0 && (((long) y) & 1L) != 0;
            return odd ? -r : r;
        }
        if (x == 1) {
            return 1;
        }
        if (y == 2) {
            return x * x;
        }
        if (y == -1) {
            return 1 / x;
        }
        if (y == 0.5) {
            return Math.sqrt(x);
        }
        double est = y * StrictMath.log(x);
        if (est > 710.5) {
            return Double.POSITIVE_INFINITY;
        }
        if (est < -746) {
            return 0.0;
        }
        if (yInt && Math.abs(y) <= 64) {
            int n = (int) Math.abs(y);
            BigDecimal p = new BigDecimal(x).pow(n);
            if (y < 0) {
                p = BigDecimal.ONE.divide(p, MC);
            }
            return p.doubleValue();
        }
        BigDecimal t = ln(x).multiply(new BigDecimal(y), MC);
        int[] k = new int[1];
        double m = expScaled(t, k);
        return Math.scalb(m, k[0]);
    }
}
