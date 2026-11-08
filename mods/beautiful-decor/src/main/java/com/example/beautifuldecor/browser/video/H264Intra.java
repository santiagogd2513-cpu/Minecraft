package com.example.beautifuldecor.browser.video;

/** Predicción intra (4x4, 8x8, 16x16 y croma) sobre un plano de bytes. */
final class H264Intra {
    private H264Intra() {
    }

    private static int clip(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /**
     * Predicción NxN (N = 4 u 8) con los modos 0..8.
     * t[0] = esquina, t[1 + x] = p[x, -1] (x = 0..2N-1); l[0] = esquina, l[1 + y] = p[-1, y] (y = 0..N-1).
     */
    static void predictNxN(int mode, int n, int[] t, int[] l, boolean hasTop, boolean hasLeft, byte[] dst, int off, int stride) {
        switch (mode) {
            case 0 -> {
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        dst[off + y * stride + x] = (byte) t[1 + x];
                    }
                }
            }
            case 1 -> {
                for (int y = 0; y < n; y++) {
                    byte v = (byte) l[1 + y];
                    for (int x = 0; x < n; x++) {
                        dst[off + y * stride + x] = v;
                    }
                }
            }
            case 2 -> {
                int sum = 0;
                int dc;
                int shift = n == 4 ? 2 : 3;
                if (hasTop && hasLeft) {
                    for (int i = 0; i < n; i++) {
                        sum += t[1 + i] + l[1 + i];
                    }
                    dc = (sum + n) >> (shift + 1);
                } else if (hasLeft) {
                    for (int i = 0; i < n; i++) {
                        sum += l[1 + i];
                    }
                    dc = (sum + (n >> 1)) >> shift;
                } else if (hasTop) {
                    for (int i = 0; i < n; i++) {
                        sum += t[1 + i];
                    }
                    dc = (sum + (n >> 1)) >> shift;
                } else {
                    dc = 128;
                }
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        dst[off + y * stride + x] = (byte) dc;
                    }
                }
            }
            default -> {
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        dst[off + y * stride + x] = (byte) directional(mode, n, x, y, t, l);
                    }
                }
            }
        }
    }

    // p(x, -1) = t[1 + x]; p(-1, y) = l[1 + y]; p(-1, -1) = t[0]
    private static int top(int[] t, int x) {
        return t[1 + x];
    }

    private static int left(int[] l, int y) {
        return l[1 + y];
    }

    /** Elemento de borde p[x, y] con x = -1 o y = -1 (esquina incluida). */
    private static int edge(int[] t, int[] l, int x, int y) {
        if (y < 0) {
            return t[1 + x];
        }
        return l[1 + y];
    }

    private static int directional(int mode, int n, int x, int y, int[] t, int[] l) {
        switch (mode) {
            case 3 -> {                         // diagonal abajo-izquierda
                if (x == n - 1 && y == n - 1) {
                    return (top(t, 2 * n - 2) + 3 * top(t, 2 * n - 1) + 2) >> 2;
                }
                return (top(t, x + y) + 2 * top(t, x + y + 1) + top(t, x + y + 2) + 2) >> 2;
            }
            case 4 -> {                         // diagonal abajo-derecha
                if (x > y) {
                    return (edge(t, l, x - y - 2, -1) + 2 * edge(t, l, x - y - 1, -1) + edge(t, l, x - y, -1) + 2) >> 2;
                } else if (x < y) {
                    return (edge(t, l, -1, y - x - 2) + 2 * edge(t, l, -1, y - x - 1) + edge(t, l, -1, y - x) + 2) >> 2;
                }
                return (top(t, 0) + 2 * t[0] + left(l, 0) + 2) >> 2;
            }
            case 5 -> {                         // vertical-derecha
                int z = 2 * x - y;
                if (z >= 0) {
                    int xx = x - (y >> 1);
                    if ((z & 1) == 0) {
                        return (edge(t, l, xx - 1, -1) + edge(t, l, xx, -1) + 1) >> 1;
                    }
                    return (edge(t, l, xx - 2, -1) + 2 * edge(t, l, xx - 1, -1) + edge(t, l, xx, -1) + 2) >> 2;
                } else if (z == -1) {
                    return (left(l, 0) + 2 * t[0] + top(t, 0) + 2) >> 2;
                }
                return (edge(t, l, -1, y - 2 * x - 1) + 2 * edge(t, l, -1, y - 2 * x - 2) + edge(t, l, -1, y - 2 * x - 3) + 2) >> 2;
            }
            case 6 -> {                         // horizontal-abajo
                int z = 2 * y - x;
                if (z >= 0) {
                    int yy = y - (x >> 1);
                    if ((z & 1) == 0) {
                        return (edge(t, l, -1, yy - 1) + edge(t, l, -1, yy) + 1) >> 1;
                    }
                    return (edge(t, l, -1, yy - 2) + 2 * edge(t, l, -1, yy - 1) + edge(t, l, -1, yy) + 2) >> 2;
                } else if (z == -1) {
                    return (left(l, 0) + 2 * t[0] + top(t, 0) + 2) >> 2;
                }
                return (edge(t, l, x - 2 * y - 1, -1) + 2 * edge(t, l, x - 2 * y - 2, -1) + edge(t, l, x - 2 * y - 3, -1) + 2) >> 2;
            }
            case 7 -> {                         // vertical-izquierda
                int xx = x + (y >> 1);
                if ((y & 1) == 0) {
                    return (top(t, xx) + top(t, xx + 1) + 1) >> 1;
                }
                return (top(t, xx) + 2 * top(t, xx + 1) + top(t, xx + 2) + 2) >> 2;
            }
            default -> {                        // 8: horizontal-arriba
                int z = x + 2 * y;
                int yy = y + (x >> 1);
                int lim = 2 * n - 3;
                if (z > lim) {
                    return left(l, n - 1);
                } else if (z == lim) {
                    return (left(l, n - 2) + 3 * left(l, n - 1) + 2) >> 2;
                } else if ((z & 1) == 0) {
                    return (left(l, yy) + left(l, yy + 1) + 1) >> 1;
                }
                return (left(l, yy) + 2 * left(l, yy + 1) + left(l, yy + 2) + 2) >> 2;
            }
        }
    }

    /** Filtro de las muestras de referencia para Intra 8x8 (8.3.2.2.1). Modifica t y l en su sitio. */
    static void filter8x8(int[] t, int[] l, boolean hasTop, boolean hasLeft, boolean hasCorner, boolean hasTopRight) {
        int[] ot = t.clone();
        int[] ol = l.clone();
        if (hasTop) {
            if (!hasTopRight) {
                for (int i = 8; i < 16; i++) {
                    ot[1 + i] = ot[1 + 7];
                }
            }
            t[1] = hasCorner ? (ot[0] + 2 * ot[1] + ot[2] + 2) >> 2 : (3 * ot[1] + ot[2] + 2) >> 2;
            for (int x = 1; x < 15; x++) {
                t[1 + x] = (ot[x] + 2 * ot[1 + x] + ot[2 + x] + 2) >> 2;
            }
            t[16] = (ot[15] + 3 * ot[16] + 2) >> 2;
        }
        if (hasCorner) {
            if (hasTop && hasLeft) {
                t[0] = (ot[1] + 2 * ot[0] + ol[1] + 2) >> 2;
            } else if (hasTop) {
                t[0] = (3 * ot[0] + ot[1] + 2) >> 2;
            } else if (hasLeft) {
                t[0] = (3 * ot[0] + ol[1] + 2) >> 2;
            } else {
                t[0] = ot[0];
            }
            l[0] = t[0];
        }
        if (hasLeft) {
            l[1] = hasCorner ? (ot[0] + 2 * ol[1] + ol[2] + 2) >> 2 : (3 * ol[1] + ol[2] + 2) >> 2;
            for (int y = 1; y < 7; y++) {
                l[1 + y] = (ol[y] + 2 * ol[1 + y] + ol[2 + y] + 2) >> 2;
            }
            l[8] = (ol[7] + 3 * ol[8] + 2) >> 2;
        }
    }

    /** Intra 16x16: modos 0 vertical, 1 horizontal, 2 DC, 3 plano. */
    static void predict16x16(int mode, boolean hasTop, boolean hasLeft, byte[] p, int off, int stride) {
        switch (mode) {
            case 0 -> {
                for (int y = 0; y < 16; y++) {
                    System.arraycopy(p, off - stride, p, off + y * stride, 16);
                }
            }
            case 1 -> {
                for (int y = 0; y < 16; y++) {
                    byte v = p[off + y * stride - 1];
                    for (int x = 0; x < 16; x++) {
                        p[off + y * stride + x] = v;
                    }
                }
            }
            case 2 -> {
                int sumT = 0;
                int sumL = 0;
                for (int i = 0; i < 16; i++) {
                    if (hasTop) {
                        sumT += p[off - stride + i] & 0xff;
                    }
                    if (hasLeft) {
                        sumL += p[off + i * stride - 1] & 0xff;
                    }
                }
                int dc;
                if (hasTop && hasLeft) {
                    dc = (sumT + sumL + 16) >> 5;
                } else if (hasLeft) {
                    dc = (sumL + 8) >> 4;
                } else if (hasTop) {
                    dc = (sumT + 8) >> 4;
                } else {
                    dc = 128;
                }
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        p[off + y * stride + x] = (byte) dc;
                    }
                }
            }
            default -> {
                int corner = p[off - stride - 1] & 0xff;
                int h = 0;
                int v = 0;
                for (int i = 0; i < 8; i++) {
                    int tr = p[off - stride + 8 + i] & 0xff;
                    int tl = i == 7 ? corner : (p[off - stride + 6 - i] & 0xff);
                    int lb = p[off + (8 + i) * stride - 1] & 0xff;
                    int lt = i == 7 ? corner : (p[off + (6 - i) * stride - 1] & 0xff);
                    h += (i + 1) * (tr - tl);
                    v += (i + 1) * (lb - lt);
                }
                int a = 16 * ((p[off + 15 * stride - 1] & 0xff) + (p[off - stride + 15] & 0xff));
                int b = (5 * h + 32) >> 6;
                int c = (5 * v + 32) >> 6;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        p[off + y * stride + x] = (byte) clip((a + b * (x - 7) + c * (y - 7) + 16) >> 5);
                    }
                }
            }
        }
    }

    /** Intra de croma 4:2:0 (8x8): modos 0 DC, 1 horizontal, 2 vertical, 3 plano. */
    static void predictChroma(int mode, boolean hasTop, boolean hasLeft, byte[] p, int off, int stride) {
        switch (mode) {
            case 0 -> {
                for (int by = 0; by < 2; by++) {
                    for (int bx = 0; bx < 2; bx++) {
                        int xo = bx * 4;
                        int yo = by * 4;
                        int sumT = 0;
                        int sumL = 0;
                        for (int i = 0; i < 4; i++) {
                            if (hasTop) {
                                sumT += p[off - stride + xo + i] & 0xff;
                            }
                            if (hasLeft) {
                                sumL += p[off + (yo + i) * stride - 1] & 0xff;
                            }
                        }
                        int dc;
                        if ((bx == 0 && by == 0) || (bx > 0 && by > 0)) {
                            if (hasTop && hasLeft) {
                                dc = (sumT + sumL + 4) >> 3;
                            } else if (hasLeft) {
                                dc = (sumL + 2) >> 2;
                            } else if (hasTop) {
                                dc = (sumT + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        } else if (bx > 0) {
                            if (hasTop) {
                                dc = (sumT + 2) >> 2;
                            } else if (hasLeft) {
                                dc = (sumL + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        } else {
                            if (hasLeft) {
                                dc = (sumL + 2) >> 2;
                            } else if (hasTop) {
                                dc = (sumT + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        }
                        for (int y = 0; y < 4; y++) {
                            for (int x = 0; x < 4; x++) {
                                p[off + (yo + y) * stride + xo + x] = (byte) dc;
                            }
                        }
                    }
                }
            }
            case 1 -> {
                for (int y = 0; y < 8; y++) {
                    byte v = p[off + y * stride - 1];
                    for (int x = 0; x < 8; x++) {
                        p[off + y * stride + x] = v;
                    }
                }
            }
            case 2 -> {
                for (int y = 0; y < 8; y++) {
                    System.arraycopy(p, off - stride, p, off + y * stride, 8);
                }
            }
            default -> {
                int h = 0;
                int v = 0;
                for (int i = 0; i < 4; i++) {
                    int tl = i == 3 ? (p[off - stride - 1] & 0xff) : (p[off - stride + 2 - i] & 0xff);
                    int tu = i == 3 ? (p[off - stride - 1] & 0xff) : (p[off + (2 - i) * stride - 1] & 0xff);
                    h += (i + 1) * ((p[off - stride + 4 + i] & 0xff) - tl);
                    v += (i + 1) * ((p[off + (4 + i) * stride - 1] & 0xff) - tu);
                }
                int a = 16 * ((p[off + 7 * stride - 1] & 0xff) + (p[off - stride + 7] & 0xff));
                int b = (34 * h + 32) >> 6;
                int c = (34 * v + 32) >> 6;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        p[off + y * stride + x] = (byte) clip((a + b * (x - 3) + c * (y - 3) + 16) >> 5);
                    }
                }
            }
        }
    }
}
