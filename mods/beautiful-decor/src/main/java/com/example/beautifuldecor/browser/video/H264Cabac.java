package com.example.beautifuldecor.browser.video;

/** Motor aritmético CABAC de H.264 (9.3). */
final class H264Cabac {
    private byte[] buf;
    private int bitPos;
    private int endBits;
    private int range;
    private int offset;
    /** Estado de cada contexto: (pStateIdx << 1) | valMPS. */
    final byte[] state = new byte[1024];

    void initContexts(int sliceQp, int tableSet) {
        int qp = Math.max(0, Math.min(51, sliceQp));
        int base = tableSet * 2048;
        for (int i = 0; i < 1024; i++) {
            int m = H264Data.CABAC_INIT[base + 2 * i];
            int n = H264Data.CABAC_INIT[base + 2 * i + 1];
            int pre = ((m * qp) >> 4) + n;
            pre = pre < 1 ? 1 : Math.min(pre, 126);
            if (pre <= 63) {
                this.state[i] = (byte) ((63 - pre) << 1);
            } else {
                this.state[i] = (byte) (((pre - 64) << 1) | 1);
            }
        }
    }

    void start(byte[] data, int bitOffset, int endBits) {
        this.buf = data;
        this.bitPos = bitOffset;
        this.endBits = endBits;
        this.range = 510;
        this.offset = readBits(9);
    }

    private int readBits(int n) {
        int p = this.bitPos;
        this.bitPos = p + n;
        int idx = p >> 3;
        int len = this.endBits >> 3;
        int v = 0;
        for (int i = 0; i < 3; i++) {
            v = (v << 8) | (idx + i < len ? this.buf[idx + i] & 0xff : 0);
        }
        return (v >> (24 - (p & 7) - n)) & ((1 << n) - 1);
    }

    int bitPosition() {
        return this.bitPos;
    }

    int decision(int ctx) {
        int st = this.state[ctx];
        int s = st >> 1;
        int mps = st & 1;
        int lps = H264Data.RANGE_LPS[s][(this.range >> 6) & 3];
        int r = this.range - lps;
        int bin;
        if (this.offset >= r) {
            bin = 1 - mps;
            this.offset -= r;
            r = lps;
            if (s == 0) {
                mps = 1 - mps;
            }
            this.state[ctx] = (byte) ((H264Data.TRANS_LPS[s] << 1) | mps);
        } else {
            bin = mps;
            if (s < 62) {
                this.state[ctx] = (byte) (((s + 1) << 1) | mps);
            }
        }
        if (r < 256) {
            int shift = Integer.numberOfLeadingZeros(r) - 23;
            r <<= shift;
            this.offset = (this.offset << shift) | readBits(shift);
        }
        this.range = r;
        return bin;
    }

    int bypass() {
        this.offset = (this.offset << 1) | readBits(1);
        if (this.offset >= this.range) {
            this.offset -= this.range;
            return 1;
        }
        return 0;
    }

    int terminate() {
        this.range -= 2;
        if (this.offset >= this.range) {
            return 1;
        }
        if (this.range < 256) {
            this.range <<= 1;
            this.offset = (this.offset << 1) | readBits(1);
        }
        return 0;
    }

    /** Tras un terminate=1 de I_PCM: posición (en bits) donde empiezan los datos, ya alineada a byte. */
    int pcmStartBit() {
        // El decodificador ya consumió exactamente los bits que escribió el codificador (incluido el 1 final de "flush").
        return (this.bitPos + 7) & ~7;
    }
}
