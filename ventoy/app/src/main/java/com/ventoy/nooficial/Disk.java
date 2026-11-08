package com.ventoy.nooficial;

import java.io.IOException;

/** Utilidades para leer y escribir bytes sueltos o tramos grandes en un BlockDevice. */
final class Disk {
    private Disk() {
    }

    static byte[] readSectors(BlockDevice d, long lba, int count) throws IOException {
        byte[] b = new byte[count * BlockDevice.SECTOR];
        int max = Math.max(1, d.maxTransfer());
        for (int done = 0; done < count; ) {
            int n = Math.min(max, count - done);
            d.read(lba + done, b, done * BlockDevice.SECTOR, n);
            done += n;
        }
        return b;
    }

    static void writeSectors(BlockDevice d, long lba, byte[] b, int off, int count) throws IOException {
        int max = Math.max(1, d.maxTransfer());
        for (int done = 0; done < count; ) {
            int n = Math.min(max, count - done);
            d.write(lba + done, b, off + done * BlockDevice.SECTOR, n);
            done += n;
        }
    }

    /** Escribe ceros en count sectores. */
    static void zero(BlockDevice d, long lba, long count) throws IOException {
        int max = Math.max(1, d.maxTransfer());
        byte[] z = new byte[max * BlockDevice.SECTOR];
        for (long done = 0; done < count; ) {
            int n = (int) Math.min(max, count - done);
            d.write(lba + done, z, 0, n);
            done += n;
        }
    }

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
}
