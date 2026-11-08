package com.example.beautifuldecor.megaphone;

/**
 * V4.0.0: códec IMA ADPCM de 4 bits (la voz del micrófono, a 16 kHz, ocupa 8 KB por segundo en vez de 32 KB).
 * Cada bloque lleva en su cabecera el estado (valor previo e índice de paso), así que se puede decodificar solo, sin los bloques anteriores:
 * [predictor bajo, predictor alto, índice] + 2 muestras por byte (la primera en los 4 bits bajos).
 * Código puro de Java (sin nada de Minecraft) para poder probarlo aparte.
 */
public final class ImaAdpcm {
    public static final int HEADER = 3;

    private static final int[] STEP = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552,
            1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487,
            12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
    private static final int[] INDEX_ADJUST = {-1, -1, -1, -1, 2, 4, 6, 8};

    private ImaAdpcm() {}

    /** Tamaño en bytes de un bloque de {@code samples} muestras (par). */
    public static int blockSize(int samples) {
        return HEADER + samples / 2;
    }

    /** Quien codifica: recuerda el estado entre bloques seguidos. */
    public static final class Encoder {
        private int predictor;
        private int index;

        public void reset() {
            this.predictor = 0;
            this.index = 0;
        }

        /** Codifica {@code count} muestras (par) a partir de {@code offset}. */
        public byte[] encode(short[] pcm, int offset, int count) {
            if ((count & 1) != 0) {
                throw new IllegalArgumentException("count debe ser par");
            }
            byte[] out = new byte[blockSize(count)];
            out[0] = (byte) (this.predictor & 0xFF);
            out[1] = (byte) ((this.predictor >> 8) & 0xFF);
            out[2] = (byte) this.index;
            for (int i = 0; i < count; i++) {
                int code = this.encodeSample(pcm[offset + i]);
                int at = HEADER + (i >> 1);
                if ((i & 1) == 0) {
                    out[at] = (byte) code;
                } else {
                    out[at] |= (byte) (code << 4);
                }
            }
            return out;
        }

        private int encodeSample(int sample) {
            int diff = sample - this.predictor;
            int sign = 0;
            if (diff < 0) {
                sign = 8;
                diff = -diff;
            }
            int step = STEP[this.index];
            int code = 0;
            int temp = step;
            if (diff >= temp) {
                code |= 4;
                diff -= temp;
            }
            temp >>= 1;
            if (diff >= temp) {
                code |= 2;
                diff -= temp;
            }
            temp >>= 1;
            if (diff >= temp) {
                code |= 1;
            }
            int[] state = {this.predictor, this.index};
            reconstruct(state, code | sign);
            this.predictor = state[0];
            this.index = state[1];
            return code | sign;
        }
    }

    /** Aplica un código de 4 bits al estado {predictor, índice}; es lo mismo que hace quien decodifica. */
    private static void reconstruct(int[] state, int nibble) {
        int step = STEP[state[1]];
        int diff = step >> 3;
        if ((nibble & 4) != 0) {
            diff += step;
        }
        if ((nibble & 2) != 0) {
            diff += step >> 1;
        }
        if ((nibble & 1) != 0) {
            diff += step >> 2;
        }
        int predictor = state[0] + ((nibble & 8) != 0 ? -diff : diff);
        state[0] = Math.max(-32768, Math.min(32767, predictor));
        state[1] = Math.max(0, Math.min(STEP.length - 1, state[1] + INDEX_ADJUST[nibble & 7]));
    }

    /** Decodifica un bloque entero. Si llega dañado (más corto que la cabecera, índice fuera de rango) devuelve un arreglo vacío. */
    public static short[] decode(byte[] block) {
        if (block == null || block.length < HEADER + 1) {
            return new short[0];
        }
        int[] state = {(short) ((block[0] & 0xFF) | ((block[1] & 0xFF) << 8)), block[2] & 0xFF};
        if (state[1] >= STEP.length) {
            return new short[0];
        }
        int count = (block.length - HEADER) * 2;
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            int b = block[HEADER + (i >> 1)] & 0xFF;
            int nibble = (i & 1) == 0 ? (b & 0x0F) : (b >> 4);
            reconstruct(state, nibble);
            out[i] = (short) state[0];
        }
        return out;
    }
}
