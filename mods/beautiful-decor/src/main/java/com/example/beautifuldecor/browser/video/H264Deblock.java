package com.example.beautifuldecor.browser.video;

/** Filtro de desbloqueo de H.264 (8.7) para cuadros progresivos 4:2:0. Se aplica al cuadro entero al terminar de decodificarlo. */
final class H264Deblock {
    private H264Deblock() {
    }

    private static int clip3(int lo, int hi, int v) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static int clip1(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    static void run(H264Decoder d) {
        final H264Pic pic = d.cur;
        final int mbW = d.mbW;
        final int w4 = d.w4;
        final int w8 = d.w8;
        final int stride = pic.width;
        final int cw = stride >> 1;
        final int[][] bsV = new int[4][4];
        final int[][] bsH = new int[4][4];
        for (int mb = 0; mb < d.mbCount; mb++) {
            final int dbIdc = d.mbDbIdc[mb];
            if (dbIdc == 1 || d.mbSlice[mb] < 0) {
                continue;
            }
            final int mx = mb % mbW;
            final int my = mb / mbW;
            final boolean left = mx > 0 && (dbIdc != 2 || d.mbSlice[mb - 1] == d.mbSlice[mb]);
            final boolean top = my > 0 && (dbIdc != 2 || d.mbSlice[mb - mbW] == d.mbSlice[mb]);
            final boolean t8 = d.mbT8[mb] != 0;
            final int offA = d.mbDbA[mb];
            final int offB = d.mbDbB[mb];
            final boolean qIntra = H264Decoder.isIntraKind(d.mbKind[mb]);
            final int qpQ = d.mbQp[mb];
            final int qMask = d.nzMask[mb];
            // ---- fuerzas de borde
            for (int e = 0; e < 4; e++) {
                for (int g = 0; g < 4; g++) {
                    bsV[e][g] = 0;
                    bsH[e][g] = 0;
                }
            }
            for (int e = 0; e < 4; e++) {
                if (t8 && (e & 1) == 1) {
                    continue;
                }
                // bordes verticales: bloque q = (e, g)
                if (e > 0 || left) {
                    for (int g = 0; g < 4; g++) {
                        int qgx = mx * 4 + e;
                        int qgy = my * 4 + g;
                        int b;
                        if (e == 0) {
                            int pm = mb - 1;
                            if (qIntra || H264Decoder.isIntraKind(d.mbKind[pm])) {
                                b = 4;
                            } else if ((qMask & (1 << (g * 4))) != 0 || (d.nzMask[pm] & (1 << (g * 4 + 3))) != 0) {
                                b = 2;
                            } else {
                                b = mvBs(pic, w4, w8, qgx - 1, qgy, qgx, qgy);
                            }
                        } else if (qIntra) {
                            b = 3;
                        } else if ((qMask & ((1 << (g * 4 + e)) | (1 << (g * 4 + e - 1)))) != 0) {
                            b = 2;
                        } else {
                            b = mvBs(pic, w4, w8, qgx - 1, qgy, qgx, qgy);
                        }
                        bsV[e][g] = b;
                    }
                }
                // bordes horizontales: bloque q = (g, e)
                if (e > 0 || top) {
                    for (int g = 0; g < 4; g++) {
                        int qgx = mx * 4 + g;
                        int qgy = my * 4 + e;
                        int b;
                        if (e == 0) {
                            int pm = mb - mbW;
                            if (qIntra || H264Decoder.isIntraKind(d.mbKind[pm])) {
                                b = 4;
                            } else if ((qMask & (1 << g)) != 0 || (d.nzMask[pm] & (1 << (12 + g))) != 0) {
                                b = 2;
                            } else {
                                b = mvBs(pic, w4, w8, qgx, qgy - 1, qgx, qgy);
                            }
                        } else if (qIntra) {
                            b = 3;
                        } else if ((qMask & ((1 << (e * 4 + g)) | (1 << ((e - 1) * 4 + g)))) != 0) {
                            b = 2;
                        } else {
                            b = mvBs(pic, w4, w8, qgx, qgy - 1, qgx, qgy);
                        }
                        bsH[e][g] = b;
                    }
                }
            }
            final int qcb = H264Data.chromaQp(clip3(0, 51, qpQ + d.mbQpCbOff[mb]));
            final int qcr = H264Data.chromaQp(clip3(0, 51, qpQ + d.mbQpCrOff[mb]));
            // ---- luma y croma: primero los bordes verticales, después los horizontales
            for (int dir = 0; dir < 2; dir++) {
                for (int e = 0; e < 4; e++) {
                    if (t8 && (e & 1) == 1) {
                        continue;
                    }
                    int[] bs = dir == 0 ? bsV[e] : bsH[e];
                    if ((bs[0] | bs[1] | bs[2] | bs[3]) == 0) {
                        continue;
                    }
                    int pm = mb;
                    if (e == 0) {
                        pm = dir == 0 ? mb - 1 : mb - mbW;
                    }
                    int qpP = d.mbQp[pm];
                    int avg = (qpP + qpQ + 1) >> 1;
                    int indexA = clip3(0, 51, avg + offA);
                    int indexB = clip3(0, 51, avg + offB);
                    int alpha = H264Data.ALPHA[indexA];
                    int beta = H264Data.BETA[indexB];
                    if (alpha != 0 && beta != 0) {
                        int off;
                        int across;
                        int along;
                        if (dir == 0) {
                            off = (my * 16) * stride + mx * 16 + e * 4;
                            across = 1;
                            along = stride;
                        } else {
                            off = (my * 16 + e * 4) * stride + mx * 16;
                            across = stride;
                            along = 1;
                        }
                        lumaEdge(pic.y, off, across, along, bs, indexA, alpha, beta);
                    }
                    if ((e & 1) == 0) {
                        int ce = e * 2;
                        for (int c = 0; c < 2; c++) {
                            int qc = c == 0 ? qcb : qcr;
                            int pc = c == 0 ? H264Data.chromaQp(clip3(0, 51, qpP + d.mbQpCbOff[pm]))
                                    : H264Data.chromaQp(clip3(0, 51, qpP + d.mbQpCrOff[pm]));
                            int avgC = (pc + qc + 1) >> 1;
                            int ia = clip3(0, 51, avgC + offA);
                            int ib = clip3(0, 51, avgC + offB);
                            int al = H264Data.ALPHA[ia];
                            int be = H264Data.BETA[ib];
                            if (al == 0 || be == 0) {
                                continue;
                            }
                            byte[] pl = c == 0 ? pic.u : pic.v;
                            if (dir == 0) {
                                chromaEdge(pl, (my * 8) * cw + mx * 8 + ce, 1, cw, bs, ia, al, be);
                            } else {
                                chromaEdge(pl, (my * 8 + ce) * cw + mx * 8, cw, 1, bs, ia, al, be);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean mvDiff(short[] a, int ai, short[] b, int bi) {
        return Math.abs(a[ai] - b[bi]) >= 4 || Math.abs(a[ai + 1] - b[bi + 1]) >= 4;
    }

    private static int mvBs(H264Pic pic, int w4, int w8, int pgx, int pgy, int qgx, int qgy) {
        int p8 = (pgy >> 1) * w8 + (pgx >> 1);
        int q8 = (qgy >> 1) * w8 + (qgx >> 1);
        int pr0 = pic.refUid[0][p8];
        int pr1 = pic.refUid[1][p8];
        int qr0 = pic.refUid[0][q8];
        int qr1 = pic.refUid[1][q8];
        int pn = (pr0 >= 0 ? 1 : 0) + (pr1 >= 0 ? 1 : 0);
        int qn = (qr0 >= 0 ? 1 : 0) + (qr1 >= 0 ? 1 : 0);
        if (pn != qn) {
            return 1;
        }
        int pi = (pgy * w4 + pgx) * 2;
        int qi = (qgy * w4 + qgx) * 2;
        short[] m0 = pic.mv[0];
        short[] m1 = pic.mv[1];
        if (pn == 1) {
            int pr = pr0 >= 0 ? pr0 : pr1;
            int qr = qr0 >= 0 ? qr0 : qr1;
            if (pr != qr) {
                return 1;
            }
            short[] pm = pr0 >= 0 ? m0 : m1;
            short[] qm = qr0 >= 0 ? m0 : m1;
            return mvDiff(pm, pi, qm, qi) ? 1 : 0;
        }
        if (pn == 0) {
            return 0;
        }
        if (!((pr0 == qr0 && pr1 == qr1) || (pr0 == qr1 && pr1 == qr0))) {
            return 1;
        }
        if (pr0 != pr1) {
            if (pr0 == qr0) {
                return mvDiff(m0, pi, m0, qi) || mvDiff(m1, pi, m1, qi) ? 1 : 0;
            }
            return mvDiff(m0, pi, m1, qi) || mvDiff(m1, pi, m0, qi) ? 1 : 0;
        }
        boolean a = mvDiff(m0, pi, m0, qi) || mvDiff(m1, pi, m1, qi);
        boolean b = mvDiff(m0, pi, m1, qi) || mvDiff(m1, pi, m0, qi);
        return a && b ? 1 : 0;
    }

    private static void lumaEdge(byte[] pl, int off, int across, int along, int[] bs, int indexA, int alpha, int beta) {
        for (int g = 0; g < 4; g++) {
            int b = bs[g];
            if (b == 0) {
                continue;
            }
            int tc0 = b < 4 ? H264Data.TC0[indexA][b - 1] : 0;
            for (int k = 0; k < 4; k++) {
                int o = off + (g * 4 + k) * along;
                int p0 = pl[o - across] & 0xff;
                int q0 = pl[o] & 0xff;
                int p1 = pl[o - 2 * across] & 0xff;
                int q1 = pl[o + across] & 0xff;
                if (Math.abs(p0 - q0) >= alpha || Math.abs(p1 - p0) >= beta || Math.abs(q1 - q0) >= beta) {
                    continue;
                }
                int p2 = pl[o - 3 * across] & 0xff;
                int q2 = pl[o + 2 * across] & 0xff;
                int ap = Math.abs(p2 - p0);
                int aq = Math.abs(q2 - q0);
                if (b < 4) {
                    int tc = tc0 + (ap < beta ? 1 : 0) + (aq < beta ? 1 : 0);
                    int delta = clip3(-tc, tc, (((q0 - p0) << 2) + (p1 - q1) + 4) >> 3);
                    pl[o - across] = (byte) clip1(p0 + delta);
                    pl[o] = (byte) clip1(q0 - delta);
                    if (ap < beta) {
                        pl[o - 2 * across] = (byte) (p1 + clip3(-tc0, tc0, (p2 + ((p0 + q0 + 1) >> 1) - (p1 << 1)) >> 1));
                    }
                    if (aq < beta) {
                        pl[o + across] = (byte) (q1 + clip3(-tc0, tc0, (q2 + ((p0 + q0 + 1) >> 1) - (q1 << 1)) >> 1));
                    }
                } else {
                    boolean small = Math.abs(p0 - q0) < ((alpha >> 2) + 2);
                    if (ap < beta && small) {
                        int p3 = pl[o - 4 * across] & 0xff;
                        pl[o - across] = (byte) ((p2 + 2 * p1 + 2 * p0 + 2 * q0 + q1 + 4) >> 3);
                        pl[o - 2 * across] = (byte) ((p2 + p1 + p0 + q0 + 2) >> 2);
                        pl[o - 3 * across] = (byte) ((2 * p3 + 3 * p2 + p1 + p0 + q0 + 4) >> 3);
                    } else {
                        pl[o - across] = (byte) ((2 * p1 + p0 + q1 + 2) >> 2);
                    }
                    if (aq < beta && small) {
                        int q3 = pl[o + 3 * across] & 0xff;
                        pl[o] = (byte) ((p1 + 2 * p0 + 2 * q0 + 2 * q1 + q2 + 4) >> 3);
                        pl[o + across] = (byte) ((p0 + q0 + q1 + q2 + 2) >> 2);
                        pl[o + 2 * across] = (byte) ((2 * q3 + 3 * q2 + q1 + q0 + p0 + 4) >> 3);
                    } else {
                        pl[o] = (byte) ((2 * q1 + q0 + p1 + 2) >> 2);
                    }
                }
            }
        }
    }

    private static void chromaEdge(byte[] pl, int off, int across, int along, int[] bs, int indexA, int alpha, int beta) {
        for (int k = 0; k < 8; k++) {
            int b = bs[k >> 1];
            if (b == 0) {
                continue;
            }
            int o = off + k * along;
            int p0 = pl[o - across] & 0xff;
            int q0 = pl[o] & 0xff;
            int p1 = pl[o - 2 * across] & 0xff;
            int q1 = pl[o + across] & 0xff;
            if (Math.abs(p0 - q0) >= alpha || Math.abs(p1 - p0) >= beta || Math.abs(q1 - q0) >= beta) {
                continue;
            }
            if (b < 4) {
                int tc = H264Data.TC0[indexA][b - 1] + 1;
                int delta = clip3(-tc, tc, (((q0 - p0) << 2) + (p1 - q1) + 4) >> 3);
                pl[o - across] = (byte) clip1(p0 + delta);
                pl[o] = (byte) clip1(q0 - delta);
            } else {
                pl[o - across] = (byte) ((2 * p1 + p0 + q1 + 2) >> 2);
                pl[o] = (byte) ((2 * q1 + q0 + p1 + 2) >> 2);
            }
        }
    }
}
