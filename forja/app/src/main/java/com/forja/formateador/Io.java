package com.forja.formateador;

import java.io.IOException;

/**
 * Escritura por posición en bytes sobre un BlockDevice. Las posiciones y los tamaños deben
 * caer en límites de sector; los formateadores ya trabajan así.
 */
final class Io {
    final BlockDevice d;
    final int ss;

    Io(BlockDevice d) {
        this.d = d;
        this.ss = d.sectorSize();
    }

    long size() {
        return d.sectors() * ss;
    }

    private void aligned(long off, long len) throws IOException {
        if (off % ss != 0 || len % ss != 0) throw new IOException("Escritura no alineada a sector: " + off + "+" + len);
    }

    void write(long off, byte[] b) throws IOException {
        write(off, b, 0, b.length);
    }

    void write(long off, byte[] b, int boff, int len) throws IOException {
        aligned(off, len);
        int max = Math.max(ss, d.maxTransferBytes() / ss * ss);
        for (int done = 0; done < len; ) {
            int n = Math.min(max, len - done);
            d.write((off + done) / ss, b, boff + done, n / ss);
            done += n;
        }
    }

    byte[] read(long off, int len) throws IOException {
        aligned(off, len);
        byte[] b = new byte[len];
        int max = Math.max(ss, d.maxTransferBytes() / ss * ss);
        for (int done = 0; done < len; ) {
            int n = Math.min(max, len - done);
            d.read((off + done) / ss, b, done, n / ss);
            done += n;
        }
        return b;
    }

    /** Escribe ceros en [off, off+len). */
    void zero(long off, long len, Progress p, float from, float to, String step) throws IOException {
        aligned(off, len);
        int max = Math.max(ss, d.maxTransferBytes() / ss * ss);
        byte[] z = new byte[max];
        for (long done = 0; done < len; ) {
            int n = (int) Math.min(max, len - done);
            d.write((off + done) / ss, z, 0, n / ss);
            done += n;
            if (p != null) {
                if (p.cancelled()) throw new IOException("Cancelado");
                if (step != null) p.update(from + (to - from) * done / len, step);
            }
        }
    }

    void zero(long off, long len) throws IOException {
        zero(off, len, null, 0, 0, null);
    }

    void flush() throws IOException {
        d.flush();
    }

    // ------------------------------------------------------------------ little endian

    static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    static long le32(byte[] b, int o) {
        return (b[o] & 0xFFL) | (b[o + 1] & 0xFFL) << 8 | (b[o + 2] & 0xFFL) << 16 | (b[o + 3] & 0xFFL) << 24;
    }

    static long le64(byte[] b, int o) {
        return le32(b, o) | le32(b, o + 4) << 32;
    }

    static void put16(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    static void put32(byte[] b, int o, long v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    static void put64(byte[] b, int o, long v) {
        put32(b, o, v);
        put32(b, o + 4, v >>> 32);
    }

    static long roundUp(long v, long m) {
        return (v + m - 1) / m * m;
    }

    static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
