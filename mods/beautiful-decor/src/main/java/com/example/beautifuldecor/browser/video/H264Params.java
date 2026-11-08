package com.example.beautifuldecor.browser.video;

/** Conjuntos de parámetros H.264: SPS y PPS (más las matrices de escala). */
final class H264Params {
    private H264Params() {
    }

    static final class Sps {
        int profile;
        int constraintFlags;
        int level;
        int id;
        int chromaFormat = 1;
        int bitDepthLuma = 8;
        int bitDepthChroma = 8;
        boolean transformBypass;
        boolean scalingPresent;
        /** Listas de escala 4x4 (6) y 8x8 (2: intra Y, inter Y) en orden zigzag; null si no hay matriz. */
        int[][] list4;
        int[][] list8;
        int log2MaxFrameNum;
        int pocType;
        int log2MaxPocLsb;
        boolean deltaPicOrderAlwaysZero;
        int offsetForNonRefPic;
        int offsetForTopToBottom;
        int[] offsetForRefFrame = new int[0];
        int numRefFrames;
        boolean gapsAllowed;
        int mbW;
        int mbH;
        boolean frameMbsOnly;
        boolean mbaff;
        boolean direct8x8Inference;
        int cropLeft;
        int cropRight;
        int cropTop;
        int cropBottom;

        int maxFrameNum() {
            return 1 << this.log2MaxFrameNum;
        }
    }

    static final class Pps {
        int id;
        int spsId;
        boolean cabac;
        boolean bottomFieldPicOrderPresent;
        int numSliceGroups = 1;
        int numRefIdx0 = 1;
        int numRefIdx1 = 1;
        boolean weightedPred;
        int weightedBipredIdc;
        int picInitQp;
        int picInitQs;
        int chromaQpOffset;
        int chromaQpOffset2;
        boolean deblockingControl;
        boolean constrainedIntra;
        boolean redundantPicCnt;
        boolean transform8x8;
        boolean scalingPresent;
        int[][] list4;     // null si el PPS no trae
        int[][] list8;
        boolean[] present4 = new boolean[6];
        boolean[] present8 = new boolean[2];
        boolean[] useDefault4 = new boolean[6];
        boolean[] useDefault8 = new boolean[2];
    }

    /** Lee una lista de escala. Devuelve null si se usa la lista por defecto (useDefaultScalingMatrixFlag). */
    private static int[] readList(BitReader br, int size) {
        int[] list = new int[size];
        int last = 8;
        int next = 8;
        boolean useDefault = false;
        for (int j = 0; j < size; j++) {
            if (next != 0) {
                int delta = br.se();
                next = (last + delta + 256) % 256;
                useDefault = j == 0 && next == 0;
            }
            list[j] = next == 0 ? last : next;
            last = list[j];
        }
        return useDefault ? null : list;
    }

    static Sps parseSps(byte[] rbsp) {
        BitReader br = new BitReader(rbsp, 0, rbsp.length);
        Sps s = new Sps();
        s.profile = br.u(8);
        s.constraintFlags = br.u(8);
        s.level = br.u(8);
        s.id = br.ue();
        int p = s.profile;
        if (p == 100 || p == 110 || p == 122 || p == 244 || p == 44 || p == 83 || p == 86 || p == 118 || p == 128 || p == 138 || p == 139
                || p == 134 || p == 135) {
            s.chromaFormat = br.ue();
            if (s.chromaFormat == 3) {
                br.u1();
            }
            s.bitDepthLuma = 8 + br.ue();
            s.bitDepthChroma = 8 + br.ue();
            s.transformBypass = br.flag();
            s.scalingPresent = br.flag();
            if (s.scalingPresent) {
                int n = s.chromaFormat != 3 ? 8 : 12;
                s.list4 = new int[6][];
                s.list8 = new int[2][];
                boolean[] present = new boolean[12];
                int[][] tmp = new int[12][];
                boolean[] def = new boolean[12];
                for (int i = 0; i < n; i++) {
                    present[i] = br.flag();
                    if (present[i]) {
                        int[] l = readList(br, i < 6 ? 16 : 64);
                        tmp[i] = l;
                        def[i] = l == null;
                    }
                }
                // regla de reserva A
                for (int i = 0; i < 6; i++) {
                    if (present[i] && !def[i]) {
                        s.list4[i] = tmp[i];
                    } else if (present[i]) {
                        s.list4[i] = i < 3 ? H264Data.DEFAULT4_INTRA : H264Data.DEFAULT4_INTER;
                    } else if (i == 0) {
                        s.list4[i] = H264Data.DEFAULT4_INTRA;
                    } else if (i == 3) {
                        s.list4[i] = H264Data.DEFAULT4_INTER;
                    } else {
                        s.list4[i] = s.list4[i - 1];
                    }
                }
                for (int i = 0; i < 2; i++) {
                    int k = 6 + i;
                    if (present[k] && !def[k]) {
                        s.list8[i] = tmp[k];
                    } else if (present[k]) {
                        s.list8[i] = i == 0 ? H264Data.DEFAULT8_INTRA : H264Data.DEFAULT8_INTER;
                    } else {
                        s.list8[i] = i == 0 ? H264Data.DEFAULT8_INTRA : H264Data.DEFAULT8_INTER;
                    }
                }
            }
        }
        s.log2MaxFrameNum = br.ue() + 4;
        s.pocType = br.ue();
        if (s.pocType == 0) {
            s.log2MaxPocLsb = br.ue() + 4;
        } else if (s.pocType == 1) {
            s.deltaPicOrderAlwaysZero = br.flag();
            s.offsetForNonRefPic = br.se();
            s.offsetForTopToBottom = br.se();
            int n = br.ue();
            if (n > 255) {
                throw new IllegalStateException("SPS inválido");
            }
            s.offsetForRefFrame = new int[n];
            for (int i = 0; i < n; i++) {
                s.offsetForRefFrame[i] = br.se();
            }
        }
        s.numRefFrames = br.ue();
        s.gapsAllowed = br.flag();
        s.mbW = br.ue() + 1;
        int mapUnitsH = br.ue() + 1;
        s.frameMbsOnly = br.flag();
        s.mbH = s.frameMbsOnly ? mapUnitsH : mapUnitsH * 2;
        if (!s.frameMbsOnly) {
            s.mbaff = br.flag();
        }
        s.direct8x8Inference = br.flag();
        if (br.flag()) {
            int cl = br.ue();
            int cr = br.ue();
            int ct = br.ue();
            int cb = br.ue();
            int unitX = s.chromaFormat == 0 || s.chromaFormat == 3 ? 1 : 2;
            int unitY = (s.chromaFormat == 1 ? 2 : 1) * (s.frameMbsOnly ? 1 : 2);
            s.cropLeft = cl * unitX;
            s.cropRight = cr * unitX;
            s.cropTop = ct * unitY;
            s.cropBottom = cb * unitY;
        }
        if (s.mbW < 1 || s.mbH < 1 || s.mbW > 512 || s.mbH > 512) {
            throw new IllegalStateException("tamaño de video no válido");
        }
        return s;
    }

    static Pps parsePps(byte[] rbsp, Sps[] spsTable) {
        BitReader br = new BitReader(rbsp, 0, rbsp.length);
        Pps p = new Pps();
        p.id = br.ue();
        p.spsId = br.ue();
        if (p.id > 255 || p.spsId > 31) {
            throw new IllegalStateException("PPS inválido");
        }
        Sps sps = spsTable[p.spsId];
        p.cabac = br.flag();
        p.bottomFieldPicOrderPresent = br.flag();
        p.numSliceGroups = br.ue() + 1;
        if (p.numSliceGroups > 1) {
            throw new UnsupportedOperationException("FMO (grupos de rebanadas)");
        }
        p.numRefIdx0 = br.ue() + 1;
        p.numRefIdx1 = br.ue() + 1;
        p.weightedPred = br.flag();
        p.weightedBipredIdc = br.u(2);
        p.picInitQp = 26 + br.se();
        p.picInitQs = 26 + br.se();
        p.chromaQpOffset = br.se();
        p.deblockingControl = br.flag();
        p.constrainedIntra = br.flag();
        p.redundantPicCnt = br.flag();
        p.chromaQpOffset2 = p.chromaQpOffset;
        if (br.moreRbspData()) {
            p.transform8x8 = br.flag();
            p.scalingPresent = br.flag();
            if (p.scalingPresent) {
                int n = 6 + (p.transform8x8 ? 2 : 0);
                p.list4 = new int[6][];
                p.list8 = new int[2][];
                boolean spsHas = sps != null && sps.scalingPresent;
                boolean[] present = new boolean[8];
                int[][] tmp = new int[8][];
                boolean[] def = new boolean[8];
                for (int i = 0; i < n; i++) {
                    present[i] = br.flag();
                    if (present[i]) {
                        int[] l = readList(br, i < 6 ? 16 : 64);
                        tmp[i] = l;
                        def[i] = l == null;
                    }
                }
                for (int i = 0; i < 6; i++) {
                    if (present[i] && !def[i]) {
                        p.list4[i] = tmp[i];
                    } else if (present[i]) {
                        p.list4[i] = i < 3 ? H264Data.DEFAULT4_INTRA : H264Data.DEFAULT4_INTER;
                    } else if (i == 0 || i == 3) {
                        // regla B (si el SPS trae matriz) o A (si no)
                        if (spsHas) {
                            p.list4[i] = sps.list4[i];
                        } else {
                            p.list4[i] = i == 0 ? H264Data.DEFAULT4_INTRA : H264Data.DEFAULT4_INTER;
                        }
                    } else {
                        p.list4[i] = p.list4[i - 1];
                    }
                }
                for (int i = 0; i < 2; i++) {
                    int k = 6 + i;
                    if (present[k] && !def[k]) {
                        p.list8[i] = tmp[k];
                    } else if (present[k]) {
                        p.list8[i] = i == 0 ? H264Data.DEFAULT8_INTRA : H264Data.DEFAULT8_INTER;
                    } else if (spsHas) {
                        p.list8[i] = sps.list8[i];
                    } else {
                        p.list8[i] = i == 0 ? H264Data.DEFAULT8_INTRA : H264Data.DEFAULT8_INTER;
                    }
                }
            }
            p.chromaQpOffset2 = br.se();
        }
        return p;
    }
}
