package com.example.beautifuldecor.browser.video;

/** Compensación de movimiento de H.264: interpolación de luma a 1/4 de pixel y de croma a 1/8. */
final class H264Inter {
    private H264Inter() {
    }

    private static int clip(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Buffers de trabajo (uno por hilo de decodificación; el decodificador tiene el suyo). */
    static final class Scratch {
        final byte[] edge = new byte[(16 + 5) * (16 + 5)];
        final int[] tmp = new int[16 * (16 + 5)];
    }

    /**
     * Bloque de luma w x h en (x, y) entero con fracción (fx, fy) en cuartos de pixel. El resultado se escribe en out con paso outStride.
     */
    static void luma(byte[] ref, int pw, int ph, int x, int y, int fx, int fy, int w, int h, byte[] out, int outOff, int outStride, Scratch sc) {
        byte[] src;
        int base;
        int stride;
        if (x - 2 >= 0 && y - 2 >= 0 && x + w + 3 <= pw && y + h + 3 <= ph) {
            src = ref;
            stride = pw;
            base = y * pw + x;
        } else {
            int ew = w + 5;
            int eh = h + 5;
            byte[] e = sc.edge;
            for (int j = 0; j < eh; j++) {
                int yy = y - 2 + j;
                yy = yy < 0 ? 0 : Math.min(yy, ph - 1);
                int row = yy * pw;
                for (int i = 0; i < ew; i++) {
                    int xx = x - 2 + i;
                    xx = xx < 0 ? 0 : Math.min(xx, pw - 1);
                    e[j * ew + i] = ref[row + xx];
                }
            }
            src = e;
            stride = ew;
            base = 2 * ew + 2;
        }
        if (fx == 0 && fy == 0) {
            for (int j = 0; j < h; j++) {
                System.arraycopy(src, base + j * stride, out, outOff + j * outStride, w);
            }
            return;
        }
        if (fy == 0) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int b = bHalf(src, base + j * stride + i);
                    if (fx == 2) {
                        out[outOff + j * outStride + i] = (byte) b;
                    } else {
                        int g = src[base + j * stride + i + (fx == 3 ? 1 : 0)] & 0xff;
                        out[outOff + j * outStride + i] = (byte) ((g + b + 1) >> 1);
                    }
                }
            }
            return;
        }
        if (fx == 0) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int hh = hHalf(src, base + j * stride + i, stride);
                    if (fy == 2) {
                        out[outOff + j * outStride + i] = (byte) hh;
                    } else {
                        int g = src[base + (j + (fy == 3 ? 1 : 0)) * stride + i] & 0xff;
                        out[outOff + j * outStride + i] = (byte) ((g + hh + 1) >> 1);
                    }
                }
            }
            return;
        }
        if (fx == 2 || fy == 2) {
            // necesita el centro j
            int[] tmp = sc.tmp;
            for (int j = -2; j < h + 3; j++) {
                for (int i = 0; i < w; i++) {
                    int p = base + j * stride + i;
                    tmp[(j + 2) * w + i] = (src[p - 2] & 0xff) - 5 * (src[p - 1] & 0xff) + 20 * (src[p] & 0xff) + 20 * (src[p + 1] & 0xff)
                            - 5 * (src[p + 2] & 0xff) + (src[p + 3] & 0xff);
                }
            }
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int t = (j + 2) * w + i;
                    int j1 = tmp[t - 2 * w] - 5 * tmp[t - w] + 20 * tmp[t] + 20 * tmp[t + w] - 5 * tmp[t + 2 * w] + tmp[t + 3 * w];
                    int jj = clip((j1 + 512) >> 10);
                    int res;
                    if (fx == 2 && fy == 2) {
                        res = jj;
                    } else if (fx == 2) {
                        int bb = clip((tmp[t + (fy == 3 ? w : 0)] + 16) >> 5);
                        res = (jj + bb + 1) >> 1;
                    } else {
                        int hh = hHalf(src, base + j * stride + i + (fx == 3 ? 1 : 0), stride);
                        res = (jj + hh + 1) >> 1;
                    }
                    out[outOff + j * outStride + i] = (byte) res;
                }
            }
            return;
        }
        // diagonales: promedio de una mitad horizontal y una mitad vertical
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int b = bHalf(src, base + (j + (fy == 3 ? 1 : 0)) * stride + i);
                int hh = hHalf(src, base + j * stride + i + (fx == 3 ? 1 : 0), stride);
                out[outOff + j * outStride + i] = (byte) ((b + hh + 1) >> 1);
            }
        }
    }

    private static int bHalf(byte[] s, int p) {
        int v = (s[p - 2] & 0xff) - 5 * (s[p - 1] & 0xff) + 20 * (s[p] & 0xff) + 20 * (s[p + 1] & 0xff) - 5 * (s[p + 2] & 0xff) + (s[p + 3] & 0xff);
        return clip((v + 16) >> 5);
    }

    private static int hHalf(byte[] s, int p, int stride) {
        int v = (s[p - 2 * stride] & 0xff) - 5 * (s[p - stride] & 0xff) + 20 * (s[p] & 0xff) + 20 * (s[p + stride] & 0xff)
                - 5 * (s[p + 2 * stride] & 0xff) + (s[p + 3 * stride] & 0xff);
        return clip((v + 16) >> 5);
    }

    /** Bloque de croma w x h en (x, y) entero con fracción (fx, fy) en octavos de pixel. */
    static void chroma(byte[] ref, int pw, int ph, int x, int y, int fx, int fy, int w, int h, byte[] out, int outOff, int outStride, Scratch sc) {
        byte[] src;
        int base;
        int stride;
        if (x >= 0 && y >= 0 && x + w + 1 <= pw && y + h + 1 <= ph) {
            src = ref;
            stride = pw;
            base = y * pw + x;
        } else {
            int ew = w + 1;
            int eh = h + 1;
            byte[] e = sc.edge;
            for (int j = 0; j < eh; j++) {
                int yy = y + j;
                yy = yy < 0 ? 0 : Math.min(yy, ph - 1);
                int row = yy * pw;
                for (int i = 0; i < ew; i++) {
                    int xx = x + i;
                    xx = xx < 0 ? 0 : Math.min(xx, pw - 1);
                    e[j * ew + i] = ref[row + xx];
                }
            }
            src = e;
            stride = ew;
            base = 0;
        }
        int wa = (8 - fx) * (8 - fy);
        int wb = fx * (8 - fy);
        int wc = (8 - fx) * fy;
        int wd = fx * fy;
        for (int j = 0; j < h; j++) {
            int p = base + j * stride;
            for (int i = 0; i < w; i++) {
                int v = wa * (src[p + i] & 0xff) + wb * (src[p + i + 1] & 0xff) + wc * (src[p + i + stride] & 0xff) + wd * (src[p + i + stride + 1] & 0xff);
                out[outOff + j * outStride + i] = (byte) ((v + 32) >> 6);
            }
        }
    }
}
