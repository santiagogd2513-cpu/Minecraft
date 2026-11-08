package com.example.beautifuldecor.browser.video;

/** Lector de bits (con Exp-Golomb) sobre un trozo de bytes ya sin los bytes de "emulation prevention". Más allá del final lee ceros. */
public final class BitReader {
    final byte[] buf;
    int pos;            // posición en bits
    final int endBits;  // fin en bits

    public BitReader(byte[] data, int offset, int length) {
        this.buf = data;
        this.pos = offset * 8;
        this.endBits = (offset + length) * 8;
    }

    public int bitPos() {
        return this.pos;
    }

    public void setBitPos(int p) {
        this.pos = p;
    }

    public int bitsLeft() {
        return this.endBits - this.pos;
    }

    /** Mira los siguientes n bits (1..32) sin consumirlos. */
    public int peek(int n) {
        int p = this.pos >> 3;
        int len = this.endBits >> 3;
        long v;
        if (p + 4 < len) {
            byte[] b = this.buf;
            v = ((long) (b[p] & 0xff) << 32) | ((long) (b[p + 1] & 0xff) << 24) | ((b[p + 2] & 0xff) << 16) | ((b[p + 3] & 0xff) << 8) | (b[p + 4] & 0xff);
        } else {
            v = 0;
            for (int i = 0; i < 5; i++) {
                v = (v << 8) | (p + i < len ? this.buf[p + i] & 0xff : 0);
            }
        }
        int shift = 40 - (this.pos & 7) - n;
        return (int) ((v >>> shift) & ((1L << n) - 1));
    }

    public void skip(int n) {
        this.pos += n;
    }

    public int u(int n) {
        if (n == 0) {
            return 0;
        }
        int v = peek(n);
        this.pos += n;
        return v;
    }

    public int u1() {
        int p = this.pos;
        this.pos = p + 1;
        int i = p >> 3;
        if (i >= (this.endBits >> 3)) {
            return 0;
        }
        return (this.buf[i] >> (7 - (p & 7))) & 1;
    }

    public boolean flag() {
        return u1() != 0;
    }

    public int ue() {
        int w = peek(32);
        if (w != 0) {
            int lz = Integer.numberOfLeadingZeros(w);
            if (lz <= 15) {
                int len = 2 * lz + 1;
                this.pos += len;
                return (w >>> (32 - len)) - 1;
            }
        }
        int zeros = 0;
        while (u1() == 0) {
            if (++zeros > 32 || this.pos > this.endBits + 64) {
                throw new IllegalStateException("exp-golomb inválido");
            }
        }
        long v = zeros == 0 ? 0 : (((long) u(zeros)) & 0xffffffffL);
        return (int) (((1L << zeros) - 1) + v);
    }

    public int se() {
        int k = ue();
        return (k & 1) != 0 ? (k + 1) >> 1 : -(k >> 1);
    }

    public int te(int max) {
        if (max > 1) {
            return ue();
        }
        return u1() ^ 1;
    }

    public boolean byteAligned() {
        return (this.pos & 7) == 0;
    }

    public void alignToByte() {
        this.pos = (this.pos + 7) & ~7;
    }

    private int stopBit = -2;

    /** Hay más datos de la carga útil antes del bit de parada (rbsp_trailing_bits)? */
    public boolean moreRbspData() {
        if (this.stopBit == -2) {
            int last = (this.endBits >> 3) - 1;
            while (last >= 0 && this.buf[last] == 0) {
                last--;
            }
            this.stopBit = last < 0 ? -1 : last * 8 + 7 - Integer.numberOfTrailingZeros(this.buf[last] & 0xff);
        }
        return this.pos < this.stopBit;
    }
}
