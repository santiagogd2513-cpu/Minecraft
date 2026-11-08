package com.example.beautifuldecor.browser.video;

/** Tablas y constantes de H.264 ya listas para usar (VLC de CAVLC, contextos CABAC, escaneos, desbloqueo...). */
final class H264Data {
    private H264Data() {
    }

    /** Tabla de búsqueda para códigos de longitud variable: se mira con peek(bits) y da (longitud << 8) | símbolo. */
    static final class Vlc {
        final short[] lut;
        final int bits;

        Vlc(int[] lens, int[] codes, int offset, int count) {
            int max = 0;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, lens[offset + i]);
            }
            this.bits = max;
            this.lut = new short[1 << max];
            for (int i = 0; i < count; i++) {
                int len = lens[offset + i];
                if (len == 0) {
                    continue;
                }
                int code = codes[offset + i];
                int first = code << (max - len);
                int n = 1 << (max - len);
                for (int k = 0; k < n; k++) {
                    if (this.lut[first + k] != 0) {
                        throw new IllegalStateException("código VLC repetido");
                    }
                    this.lut[first + k] = (short) ((len << 8) | i);
                }
            }
        }

        /** Devuelve el símbolo, o -1 si el código no existe. */
        int read(BitReader br) {
            int e = this.lut[br.peek(this.bits)];
            if (e == 0) {
                return -1;
            }
            br.skip((e >> 8) & 0xff);
            return e & 0xff;
        }
    }

    static int[] unhex(String s) {
        int[] r = new int[s.length() / 2];
        for (int i = 0; i < r.length; i++) {
            r[i] = Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return r;
    }

    static final byte[] CABAC_INIT = new byte[4 * 1024 * 2];
    static final Vlc[] COEFF_TOKEN = new Vlc[4];
    static final Vlc CHROMA_DC_TOKEN;
    static final Vlc[] TOTAL_ZEROS = new Vlc[15];
    static final Vlc[] CHROMA_DC_TOTAL_ZEROS = new Vlc[3];
    static final Vlc[] RUN = new Vlc[7];
    static final int[] SIG8;
    static final int[] LAST8;

    static {
        int[] c = unhex(H264Tables.CABAC_INIT_HEX);
        for (int i = 0; i < c.length; i++) {
            CABAC_INIT[i] = (byte) c[i];
        }
        int[] tl = unhex(H264Tables.COEFF_TOKEN_LEN_HEX);
        int[] tb = unhex(H264Tables.COEFF_TOKEN_BITS_HEX);
        for (int t = 0; t < 4; t++) {
            COEFF_TOKEN[t] = new Vlc(tl, tb, t * 68, 68);
        }
        CHROMA_DC_TOKEN = new Vlc(unhex(H264Tables.CHROMA_DC_TOKEN_LEN_HEX), unhex(H264Tables.CHROMA_DC_TOKEN_BITS_HEX), 0, 20);
        int[] zl = unhex(H264Tables.TOTAL_ZEROS_LEN_HEX);
        int[] zb = unhex(H264Tables.TOTAL_ZEROS_BITS_HEX);
        for (int t = 0; t < 15; t++) {
            TOTAL_ZEROS[t] = new Vlc(zl, zb, t * 16, 16);
        }
        int[] cl = unhex(H264Tables.CHROMA_TZ_LEN_HEX);
        int[] cb = unhex(H264Tables.CHROMA_TZ_BITS_HEX);
        for (int t = 0; t < 3; t++) {
            CHROMA_DC_TOTAL_ZEROS[t] = new Vlc(cl, cb, t * 4, 4);
        }
        int[] rl = unhex(H264Tables.RUN_LEN_HEX);
        int[] rb = unhex(H264Tables.RUN_BITS_HEX);
        for (int t = 0; t < 7; t++) {
            RUN[t] = new Vlc(rl, rb, t * 16, 16);
        }
        SIG8 = unhex(H264Tables.SIG8_HEX);
        LAST8 = unhex(H264Tables.LAST8_HEX);
    }

    // ------------------------------------------------------------------ CABAC
    static final int[][] RANGE_LPS = {
        {128, 176, 208, 240}, {128, 167, 197, 227}, {128, 158, 187, 216}, {123, 150, 178, 205}, {116, 142, 169, 195}, {111, 135, 160, 185},
        {105, 128, 152, 175}, {100, 122, 144, 166}, {95, 116, 137, 158}, {90, 110, 130, 150}, {85, 104, 123, 142}, {81, 99, 117, 135},
        {77, 94, 111, 128}, {73, 89, 105, 122}, {69, 85, 100, 116}, {66, 80, 95, 110}, {62, 76, 90, 104}, {59, 72, 86, 99},
        {56, 69, 81, 94}, {53, 65, 77, 89}, {51, 62, 73, 85}, {48, 59, 69, 80}, {46, 56, 66, 76}, {43, 53, 63, 72},
        {41, 50, 59, 69}, {39, 48, 56, 65}, {37, 45, 54, 62}, {35, 43, 51, 59}, {33, 41, 48, 56}, {32, 39, 46, 53},
        {30, 37, 43, 50}, {29, 35, 41, 48}, {27, 33, 39, 45}, {26, 31, 37, 43}, {24, 30, 35, 41}, {23, 28, 33, 39},
        {22, 27, 32, 37}, {21, 26, 30, 35}, {20, 24, 29, 33}, {19, 23, 27, 31}, {18, 22, 26, 30}, {17, 21, 25, 28},
        {16, 20, 23, 27}, {15, 19, 22, 25}, {14, 18, 21, 24}, {14, 17, 20, 23}, {13, 16, 19, 22}, {12, 15, 18, 21},
        {12, 14, 17, 20}, {11, 14, 16, 19}, {11, 13, 15, 18}, {10, 12, 15, 17}, {10, 12, 14, 16}, {9, 11, 13, 15},
        {9, 11, 12, 14}, {8, 10, 12, 14}, {8, 9, 11, 13}, {7, 9, 11, 12}, {7, 9, 10, 12}, {7, 8, 10, 11},
        {6, 8, 9, 11}, {6, 7, 9, 10}, {6, 7, 8, 9}, {2, 2, 2, 2}};

    static final int[] TRANS_LPS = {
        0, 0, 1, 2, 2, 4, 4, 5, 6, 7, 8, 9, 9, 11, 11, 12, 13, 13, 15, 15, 16, 16, 18, 18, 19, 19, 21, 21, 22, 22, 23, 24,
        24, 25, 26, 26, 27, 27, 28, 29, 29, 30, 30, 30, 31, 32, 32, 33, 33, 33, 34, 34, 35, 35, 35, 36, 36, 36, 37, 37, 37, 38, 38, 63};

    // ------------------------------------------------------------------ escaneos y cuantización
    /** Zigzag 4x4: posición del coeficiente i en una matriz 4x4 en filas (y*4+x). */
    static final int[] ZIGZAG4 = {0, 1, 4, 8, 5, 2, 3, 6, 9, 12, 13, 10, 7, 11, 14, 15};

    static final int[] ZIGZAG8 = {
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63};

    static final int[][] NORM4 = {{10, 16, 13}, {11, 18, 14}, {13, 20, 16}, {14, 23, 18}, {16, 25, 20}, {18, 29, 23}};
    static final int[][] NORM8 = {
        {20, 18, 32, 19, 25, 24}, {22, 19, 35, 21, 28, 26}, {26, 23, 42, 24, 33, 31},
        {28, 25, 45, 26, 35, 33}, {32, 28, 51, 30, 40, 38}, {36, 32, 58, 34, 46, 43}};

    static final int[] DEFAULT4_INTRA = {6, 13, 13, 20, 20, 20, 28, 28, 28, 28, 32, 32, 32, 37, 37, 42};
    static final int[] DEFAULT4_INTER = {10, 14, 14, 20, 20, 20, 24, 24, 24, 24, 27, 27, 27, 30, 30, 34};
    static final int[] DEFAULT8_INTRA = {
        6, 10, 10, 13, 11, 13, 16, 16, 16, 16, 18, 18, 18, 18, 18, 23, 23, 23, 23, 23, 23, 25, 25, 25, 25, 25, 25, 25, 27, 27, 27, 27,
        27, 27, 27, 27, 29, 29, 29, 29, 29, 29, 29, 31, 31, 31, 31, 31, 31, 33, 33, 33, 33, 33, 36, 36, 36, 36, 38, 38, 38, 40, 40, 42};
    static final int[] DEFAULT8_INTER = {
        9, 13, 13, 15, 13, 15, 17, 17, 17, 17, 19, 19, 19, 19, 19, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 24, 24, 24, 24,
        24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 27, 27, 27, 27, 27, 27, 28, 28, 28, 28, 28, 30, 30, 30, 30, 32, 32, 32, 33, 33, 35};

    static final int[] QPC = {
        29, 30, 31, 32, 32, 33, 34, 34, 35, 35, 36, 36, 37, 37, 37, 38, 38, 38, 39, 39, 39, 39};

    static int chromaQp(int qpi) {
        return qpi < 30 ? qpi : QPC[qpi - 30];
    }

    /** Mapa de coded_block_pattern (me(v)) para Intra4x4/8x8 y para Inter, croma 4:2:0. */
    static final int[] CBP_INTRA = {
        47, 31, 15, 0, 23, 27, 29, 30, 7, 11, 13, 14, 39, 43, 45, 46, 16, 3, 5, 10, 12, 19, 21, 26, 28, 35, 37, 42, 44, 1, 2, 4,
        8, 17, 18, 20, 24, 6, 9, 22, 25, 32, 33, 34, 36, 40, 38, 41};
    static final int[] CBP_INTER = {
        0, 16, 1, 2, 4, 8, 32, 3, 5, 10, 12, 15, 47, 7, 11, 13, 14, 6, 9, 31, 35, 37, 42, 44, 33, 34, 36, 40, 39, 43, 45, 46,
        17, 18, 20, 24, 19, 21, 26, 28, 23, 27, 29, 30, 22, 25, 38, 41};

    // ------------------------------------------------------------------ desbloqueo
    static final int[] ALPHA = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 17, 20, 22, 25, 28,
        32, 36, 40, 45, 50, 56, 63, 71, 80, 90, 101, 113, 127, 144, 162, 182, 203, 226, 255, 255};
    static final int[] BETA = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 6, 6, 7, 7, 8, 8,
        9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18};
    static final int[][] TC0 = {
        {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0},
        {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, 1}, {0, 0, 1}, {0, 0, 1}, {0, 0, 1}, {0, 1, 1}, {0, 1, 1}, {1, 1, 1},
        {1, 1, 1}, {1, 1, 1}, {1, 1, 1}, {1, 1, 2}, {1, 1, 2}, {1, 1, 2}, {1, 1, 2}, {1, 2, 3}, {1, 2, 3}, {2, 2, 3}, {2, 2, 4}, {2, 3, 4},
        {2, 3, 4}, {3, 3, 5}, {3, 4, 6}, {3, 4, 6}, {4, 5, 7}, {4, 5, 8}, {4, 6, 9}, {5, 7, 10}, {6, 8, 11}, {6, 8, 13}, {7, 10, 14},
        {8, 11, 16}, {9, 12, 18}, {10, 13, 20}, {11, 15, 23}, {13, 17, 25}};
}
