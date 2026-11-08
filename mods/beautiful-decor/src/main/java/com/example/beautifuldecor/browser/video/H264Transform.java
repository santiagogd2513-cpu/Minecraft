package com.example.beautifuldecor.browser.video;

/** Transformadas inversas de H.264 (4x4, 8x8, DC de luma y DC de croma) sumadas sobre la predicción. */
final class H264Transform {
    private H264Transform() {
    }

    private static int clip(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Transformada 4x4 inversa de c[co .. co+15] (filas), sumada al plano dst; luego pone c a cero. */
    static void idct4x4Add(int[] c, int co, byte[] dst, int off, int stride) {
        int[] t = new int[16];
        for (int i = 0; i < 4; i++) {
            int d0 = c[co + i * 4];
            int d1 = c[co + i * 4 + 1];
            int d2 = c[co + i * 4 + 2];
            int d3 = c[co + i * 4 + 3];
            int e0 = d0 + d2;
            int e1 = d0 - d2;
            int e2 = (d1 >> 1) - d3;
            int e3 = d1 + (d3 >> 1);
            t[i * 4] = e0 + e3;
            t[i * 4 + 1] = e1 + e2;
            t[i * 4 + 2] = e1 - e2;
            t[i * 4 + 3] = e0 - e3;
        }
        for (int j = 0; j < 4; j++) {
            int f0 = t[j];
            int f1 = t[4 + j];
            int f2 = t[8 + j];
            int f3 = t[12 + j];
            int g0 = f0 + f2;
            int g1 = f0 - f2;
            int g2 = (f1 >> 1) - f3;
            int g3 = f1 + (f3 >> 1);
            int p = off + j;
            dst[p] = (byte) clip((dst[p] & 0xff) + ((g0 + g3 + 32) >> 6));
            p += stride;
            dst[p] = (byte) clip((dst[p] & 0xff) + ((g1 + g2 + 32) >> 6));
            p += stride;
            dst[p] = (byte) clip((dst[p] & 0xff) + ((g1 - g2 + 32) >> 6));
            p += stride;
            dst[p] = (byte) clip((dst[p] & 0xff) + ((g0 - g3 + 32) >> 6));
        }
        for (int i = 0; i < 16; i++) {
            c[co + i] = 0;
        }
    }

    /** Solo hay coeficiente DC: suma (dc + 32) >> 6 a todo el bloque 4x4. */
    static void dcAdd4x4(int dc, byte[] dst, int off, int stride) {
        int v = (dc + 32) >> 6;
        for (int y = 0; y < 4; y++) {
            int p = off + y * stride;
            for (int x = 0; x < 4; x++) {
                dst[p + x] = (byte) clip((dst[p + x] & 0xff) + v);
            }
        }
    }

    static void idct8x8Add(int[] c, int co, byte[] dst, int off, int stride) {
        int[] t = new int[64];
        for (int i = 0; i < 8; i++) {
            transform8(c, co + i * 8, 1, t, i * 8, 1);
        }
        int[] col = new int[8];
        for (int j = 0; j < 8; j++) {
            transform8(t, j, 8, col, 0, 1);
            for (int i = 0; i < 8; i++) {
                int p = off + i * stride + j;
                dst[p] = (byte) clip((dst[p] & 0xff) + ((col[i] + 32) >> 6));
            }
        }
        for (int i = 0; i < 64; i++) {
            c[co + i] = 0;
        }
    }

    private static void transform8(int[] in, int io, int is, int[] out, int oo, int os) {
        int d0 = in[io];
        int d1 = in[io + is];
        int d2 = in[io + 2 * is];
        int d3 = in[io + 3 * is];
        int d4 = in[io + 4 * is];
        int d5 = in[io + 5 * is];
        int d6 = in[io + 6 * is];
        int d7 = in[io + 7 * is];
        int a0 = d0 + d4;
        int a4 = d0 - d4;
        int a2 = (d2 >> 1) - d6;
        int a6 = d2 + (d6 >> 1);
        int b0 = a0 + a6;
        int b2 = a4 + a2;
        int b4 = a4 - a2;
        int b6 = a0 - a6;
        int a1 = -d3 + d5 - d7 - (d7 >> 1);
        int a3 = d1 + d7 - d3 - (d3 >> 1);
        int a5 = -d1 + d7 + d5 + (d5 >> 1);
        int a7 = d3 + d5 + d1 + (d1 >> 1);
        int b1 = a1 + (a7 >> 2);
        int b7 = a7 - (a1 >> 2);
        int b3 = a3 + (a5 >> 2);
        int b5 = (a3 >> 2) - a5;
        out[oo] = b0 + b7;
        out[oo + os] = b2 + b5;
        out[oo + 2 * os] = b4 + b3;
        out[oo + 3 * os] = b6 + b1;
        out[oo + 4 * os] = b6 - b1;
        out[oo + 5 * os] = b4 - b3;
        out[oo + 6 * os] = b2 - b5;
        out[oo + 7 * os] = b0 - b7;
    }

    /**
     * DC de luma de Intra16x16: c (16 valores en filas 4x4 de bloques) -> valores ya escalados que van al coeficiente DC de cada bloque.
     * ls = LevelScale(qP % 6, 0, 0), qp = QP'y.
     */
    static void lumaDc(int[] c, int ls, int qp, int[] out) {
        int[] f = new int[16];
        int[] t = new int[16];
        for (int i = 0; i < 4; i++) {
            int a = c[i * 4];
            int b = c[i * 4 + 1];
            int cc = c[i * 4 + 2];
            int d = c[i * 4 + 3];
            t[i * 4] = a + b + cc + d;
            t[i * 4 + 1] = a + b - cc - d;
            t[i * 4 + 2] = a - b - cc + d;
            t[i * 4 + 3] = a - b + cc - d;
        }
        for (int j = 0; j < 4; j++) {
            int a = t[j];
            int b = t[4 + j];
            int cc = t[8 + j];
            int d = t[12 + j];
            f[j] = a + b + cc + d;
            f[4 + j] = a + b - cc - d;
            f[8 + j] = a - b - cc + d;
            f[12 + j] = a - b + cc - d;
        }
        int q6 = qp / 6;
        for (int i = 0; i < 16; i++) {
            if (qp >= 36) {
                out[i] = (f[i] * ls) << (q6 - 6);
            } else {
                out[i] = (f[i] * ls + (1 << (5 - q6))) >> (6 - q6);
            }
        }
    }

    /** DC de croma 4:2:0: c[0..3] -> out[0..3] ya escalado. */
    static void chromaDc(int[] c, int ls, int qp, int[] out) {
        int a = c[0];
        int b = c[1];
        int cc = c[2];
        int d = c[3];
        int f0 = a + b + cc + d;
        int f1 = a - b + cc - d;
        int f2 = a + b - cc - d;
        int f3 = a - b - cc + d;
        int q6 = qp / 6;
        out[0] = ((f0 * ls) << q6) >> 5;
        out[1] = ((f1 * ls) << q6) >> 5;
        out[2] = ((f2 * ls) << q6) >> 5;
        out[3] = ((f3 * ls) << q6) >> 5;
    }
}
