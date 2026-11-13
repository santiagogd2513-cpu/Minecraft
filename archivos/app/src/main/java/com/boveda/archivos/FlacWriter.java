package com.boveda.archivos;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Escribe archivos FLAC (audio sin pérdida) de 16 bits, mono o estéreo, en Java puro. Cada bloque se codifica con el
 * mejor predictor fijo de orden 0 a 4 y códigos Rice, o como constante o sin comprimir si eso resulta mejor.
 * Android no trae codificador de FLAC con contenedor, por eso esto va aparte.
 */
final class FlacWriter implements Closeable {
    private static final int BLOCK = 4096;

    private final RandomAccessFile out;
    private final int rate, channels;
    private final int[][] block;          // [canal][muestra] del bloque en curso
    private int filled;
    private long frameNo, totalSamples;
    private int minFrame = Integer.MAX_VALUE, maxFrame;
    private final MessageDigest md5;

    FlacWriter(java.io.File f, int sampleRate, int channels) throws IOException {
        if (channels < 1 || channels > 2) throw new IOException("FLAC: solo mono o estéreo");
        this.rate = sampleRate;
        this.channels = channels;
        this.block = new int[channels][BLOCK];
        out = new RandomAccessFile(f, "rw");
        out.setLength(0);
        out.write(new byte[]{'f', 'L', 'a', 'C'});
        out.write(streamInfo());            // se vuelve a escribir al final con los datos reales
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Agrega sonido de 16 bits intercalado, little-endian (lo que entrega el decodificador de Android). */
    void write(byte[] pcm, int off, int len) throws IOException {
        md5.update(pcm, off, len);
        int frame = 2 * channels;
        for (int p = off; p + frame <= off + len; p += frame) {
            for (int c = 0; c < channels; c++) {
                block[c][filled] = (short) ((pcm[p + c * 2] & 0xFF) | (pcm[p + c * 2 + 1] << 8));
            }
            if (++filled == BLOCK) flush();
        }
    }

    private void flush() throws IOException {
        if (filled == 0) return;
        byte[] f = encodeFrame(filled);
        out.write(f);
        minFrame = Math.min(minFrame, f.length);
        maxFrame = Math.max(maxFrame, f.length);
        totalSamples += filled;
        frameNo++;
        filled = 0;
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
            out.seek(4);
            out.write(streamInfo());
        } finally {
            out.close();
        }
    }

    private byte[] streamInfo() {
        BitWriter b = new BitWriter(64);
        b.bits(1, 1);          // último bloque de datos adicionales
        b.bits(0, 7);          // tipo 0: STREAMINFO
        b.bits(34, 24);
        b.bits(BLOCK, 16);
        b.bits(BLOCK, 16);
        b.bits(minFrame == Integer.MAX_VALUE ? 0 : minFrame, 24);
        b.bits(maxFrame, 24);
        b.bits(rate, 20);
        b.bits(channels - 1, 3);
        b.bits(15, 5);         // 16 bits por muestra
        b.bits(totalSamples, 36);
        byte[] sum = md5 == null ? new byte[16] : ((MessageDigest) cloneMd5()).digest();
        for (byte x : sum) b.bits(x & 0xFF, 8);
        return b.toBytes();
    }

    private MessageDigest cloneMd5() {
        try {
            return (MessageDigest) md5.clone();
        } catch (CloneNotSupportedException e) {
            return md5;
        }
    }

    // ------------------------------------------------------------------ un bloque

    private byte[] encodeFrame(int n) {
        BitWriter b = new BitWriter(n * channels * 2 + 64);
        b.bits(0xFFF8, 16);                            // sincronía + tamaño de bloque fijo
        boolean full = n == BLOCK;
        b.bits(full ? 12 : 7, 4);                      // 4096 muestras, o tamaño explícito de 16 bits al final
        b.bits(0, 4);                                  // frecuencia: la de STREAMINFO
        b.bits(channels - 1, 4);                       // canales independientes
        b.bits(4, 3);                                  // 16 bits por muestra
        b.bits(0, 1);
        utf8(b, frameNo);
        if (!full) b.bits(n - 1, 16);
        b.bits(crc8(b.toBytesNoPad(), b.byteLength()), 8);
        for (int c = 0; c < channels; c++) subframe(b, block[c], n);
        b.align();
        byte[] body = b.toBytes();
        int crc = crc16(body, body.length);
        byte[] r = new byte[body.length + 2];
        System.arraycopy(body, 0, r, 0, body.length);
        r[body.length] = (byte) (crc >> 8);
        r[body.length + 1] = (byte) crc;
        return r;
    }

    private static void utf8(BitWriter b, long v) {
        if (v < 0x80) {
            b.bits(v, 8);
        } else if (v < 0x800) {
            b.bits(0xC0 | (v >> 6), 8);
            b.bits(0x80 | (v & 0x3F), 8);
        } else if (v < 0x10000) {
            b.bits(0xE0 | (v >> 12), 8);
            b.bits(0x80 | ((v >> 6) & 0x3F), 8);
            b.bits(0x80 | (v & 0x3F), 8);
        } else if (v < 0x200000) {
            b.bits(0xF0 | (v >> 18), 8);
            b.bits(0x80 | ((v >> 12) & 0x3F), 8);
            b.bits(0x80 | ((v >> 6) & 0x3F), 8);
            b.bits(0x80 | (v & 0x3F), 8);
        } else if (v < 0x4000000) {
            b.bits(0xF8 | (v >> 24), 8);
            b.bits(0x80 | ((v >> 18) & 0x3F), 8);
            b.bits(0x80 | ((v >> 12) & 0x3F), 8);
            b.bits(0x80 | ((v >> 6) & 0x3F), 8);
            b.bits(0x80 | (v & 0x3F), 8);
        } else {
            b.bits(0xFC | (v >> 30), 8);
            b.bits(0x80 | ((v >> 24) & 0x3F), 8);
            b.bits(0x80 | ((v >> 18) & 0x3F), 8);
            b.bits(0x80 | ((v >> 12) & 0x3F), 8);
            b.bits(0x80 | ((v >> 6) & 0x3F), 8);
            b.bits(0x80 | (v & 0x3F), 8);
        }
    }

    private static void subframe(BitWriter b, int[] s, int n) {
        boolean constant = true;
        for (int i = 1; i < n; i++) if (s[i] != s[0]) { constant = false; break; }
        if (constant) {
            b.bits(0, 8);                 // CONSTANT
            b.bits(s[0] & 0xFFFF, 16);
            return;
        }
        int bestOrder = -1, bestP = 0;
        long bestBits = 16L * n;          // sin comprimir
        int[] res = new int[n];
        for (int order = 0; order <= 4 && order < n; order++) {
            residual(s, n, order, res);
            // costo de los códigos Rice con el mejor parámetro cercano al promedio
            long sum = 0;
            for (int i = order; i < n; i++) sum += zig(res[i]);
            int cnt = Math.max(1, n - order);
            int est = 0;
            while (est < 14 && (1L << (est + 1)) <= sum / cnt) est++;
            for (int p = Math.max(0, est - 1); p <= Math.min(14, est + 1); p++) {
                long bits = order * 16L + 6 + 4;
                for (int i = order; i < n; i++) bits += (zig(res[i]) >> p) + 1 + p;
                if (bits < bestBits) {
                    bestBits = bits;
                    bestOrder = order;
                    bestP = p;
                }
            }
        }
        if (bestOrder < 0) {              // VERBATIM
            b.bits(1 << 1, 8);
            for (int i = 0; i < n; i++) b.bits(s[i] & 0xFFFF, 16);
            return;
        }
        residual(s, n, bestOrder, res);
        b.bits((8 + bestOrder) << 1, 8);  // FIXED de ese orden
        for (int i = 0; i < bestOrder; i++) b.bits(s[i] & 0xFFFF, 16);
        b.bits(0, 2);                      // Rice con parámetros de 4 bits
        b.bits(0, 4);                      // una sola partición
        b.bits(bestP, 4);
        for (int i = bestOrder; i < n; i++) {
            long u = zig(res[i]);
            long q = u >> bestP;
            for (long k = 0; k < q; k++) b.bits(0, 1);
            b.bits(1, 1);
            if (bestP > 0) b.bits(u & ((1L << bestP) - 1), bestP);
        }
    }

    private static long zig(int r) {
        return ((long) r << 1) ^ (long) (r >> 31);      // positivos y negativos intercalados: 0, -1, 1, -2, 2…
    }

    /** Lo que queda al restar el predictor fijo de ese orden. */
    private static void residual(int[] s, int n, int order, int[] res) {
        for (int i = order; i < n; i++) {
            long p = switch (order) {
                case 0 -> 0;
                case 1 -> s[i - 1];
                case 2 -> 2L * s[i - 1] - s[i - 2];
                case 3 -> 3L * s[i - 1] - 3L * s[i - 2] + s[i - 3];
                default -> 4L * s[i - 1] - 6L * s[i - 2] + 4L * s[i - 3] - s[i - 4];
            };
            res[i] = (int) (s[i] - p);
        }
    }

    // ------------------------------------------------------------------ bits y sumas de comprobación

    private static final class BitWriter {
        private byte[] buf;
        private int len;          // bytes completos
        private int cur, curBits; // bits sueltos del byte en curso

        BitWriter(int cap) {
            buf = new byte[Math.max(16, cap)];
        }

        void bits(long v, int n) {
            for (int i = n - 1; i >= 0; i--) {
                cur = (cur << 1) | (int) ((v >> i) & 1);
                if (++curBits == 8) {
                    if (len == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                    buf[len++] = (byte) cur;
                    cur = 0;
                    curBits = 0;
                }
            }
        }

        void align() {
            while (curBits != 0) bits(0, 1);
        }

        int byteLength() {
            return len;
        }

        byte[] toBytesNoPad() {
            return buf;
        }

        byte[] toBytes() {
            align();
            return java.util.Arrays.copyOf(buf, len);
        }
    }

    private static int crc8(byte[] d, int n) {
        int crc = 0;
        for (int i = 0; i < n; i++) {
            crc ^= d[i] & 0xFF;
            for (int k = 0; k < 8; k++) crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
    }

    private static int crc16(byte[] d, int n) {
        int crc = 0;
        for (int i = 0; i < n; i++) {
            crc ^= (d[i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x8005) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc;
    }
}
