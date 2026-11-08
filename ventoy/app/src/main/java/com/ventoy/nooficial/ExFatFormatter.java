package com.ventoy.nooficial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Da formato exFAT a una partición (lo mismo que mkexfatfs en Ventoy2Disk.sh): región de
 * arranque con copia de respaldo, FAT, mapa de bits, tabla de mayúsculas y carpeta raíz con
 * la etiqueta del volumen. Sigue la especificación pública de exFAT de Microsoft.
 */
final class ExFatFormatter {
    private ExFatFormatter() {
    }

    static final class Result {
        long fatOffset, fatLength, heapOffset, clusterCount;
        int sectorsPerCluster;
    }

    /**
     * @param partStart         primer sector de la partición en el disco
     * @param partSectors       tamaño de la partición en sectores
     * @param sectorsPerCluster 64 (32 KB) o 256 (128 KB), como Ventoy
     */
    static Result format(BlockDevice dev, long partStart, long partSectors, int sectorsPerCluster,
                         String label, long serial, Progress p) throws IOException {
        final int S = BlockDevice.SECTOR;
        if (Integer.bitCount(sectorsPerCluster) != 1) throw new IllegalArgumentException("cluster");
        final long clusterBytes = (long) sectorsPerCluster * S;

        // ---------------------------------------------------------------- distribución
        long fatOffset = 128;          // después de las dos regiones de arranque (24 sectores), alineado
        long fatLength = 1, heap = 0, clusters = 0;
        for (int i = 0; i < 32; i++) {
            heap = roundUp(fatOffset + fatLength, sectorsPerCluster);
            clusters = (partSectors - heap) / sectorsPerCluster;
            long need = ceilDiv((clusters + 2) * 4, S);
            if (need == fatLength) break;
            fatLength = need;
        }
        heap = roundUp(fatOffset + fatLength, sectorsPerCluster);
        clusters = (partSectors - heap) / sectorsPerCluster;
        if (clusters < 16 || clusters > 0xFFFFFFF5L) throw new IOException("Tamaño de partición no válido para exFAT");

        byte[] upcase = upcaseTable();
        long bitmapBytes = ceilDiv(clusters, 8);
        long bitmapClusters = ceilDiv(bitmapBytes, clusterBytes);
        long upcaseClusters = ceilDiv(upcase.length, clusterBytes);
        long bitmapCluster = 2;
        long upcaseCluster = bitmapCluster + bitmapClusters;
        long rootCluster = upcaseCluster + upcaseClusters;
        long used = bitmapClusters + upcaseClusters + 1;

        // ---------------------------------------------------------------- FAT
        p.update(-1, "Creando el sistema de archivos exFAT…");
        long fatBytes = (clusters + 2) * 4;
        int chunk = 256 * S;
        for (long off = 0; off < fatLength * (long) S; off += chunk) {
            int len = (int) Math.min(chunk, fatLength * (long) S - off);
            byte[] b = new byte[len];
            for (long e = off / 4; e < (off + len) / 4 && e * 4 < fatBytes; e++) {
                long v = fatEntry(e, bitmapCluster, bitmapClusters, upcaseCluster, upcaseClusters, rootCluster);
                if (v != 0) Disk.put32(b, (int) (e * 4 - off), v);
            }
            Disk.writeSectors(dev, partStart + fatOffset + off / S, b, 0, len / S);
        }

        // ---------------------------------------------------------------- mapa de bits
        long heapLba = partStart + heap;
        byte[] bitmap = new byte[(int) (bitmapClusters * clusterBytes)];
        for (long c = 0; c < used; c++) bitmap[(int) (c >> 3)] |= (byte) (1 << (c & 7));
        Disk.writeSectors(dev, heapLba + (bitmapCluster - 2) * sectorsPerCluster, bitmap, 0, bitmap.length / S);

        // ---------------------------------------------------------------- tabla de mayúsculas
        byte[] up = new byte[(int) (upcaseClusters * clusterBytes)];
        System.arraycopy(upcase, 0, up, 0, upcase.length);
        Disk.writeSectors(dev, heapLba + (upcaseCluster - 2) * sectorsPerCluster, up, 0, up.length / S);

        // ---------------------------------------------------------------- carpeta raíz
        byte[] root = new byte[(int) clusterBytes];
        int o = 0;
        String lab = label == null ? "" : label.length() > 11 ? label.substring(0, 11) : label;
        if (!lab.isEmpty()) {
            root[o] = (byte) 0x83;
            root[o + 1] = (byte) lab.length();
            byte[] u = lab.getBytes(StandardCharsets.UTF_16LE);
            System.arraycopy(u, 0, root, o + 2, u.length);
        } else {
            root[o] = 0x03;
        }
        o += 32;
        root[o] = (byte) 0x81;                       // mapa de bits
        Disk.put32(root, o + 20, bitmapCluster);
        Disk.put64(root, o + 24, bitmapBytes);
        o += 32;
        root[o] = (byte) 0x82;                       // tabla de mayúsculas
        Disk.put32(root, o + 4, checksum(upcase, upcase.length, false));
        Disk.put32(root, o + 20, upcaseCluster);
        Disk.put64(root, o + 24, upcase.length);
        Disk.writeSectors(dev, heapLba + (rootCluster - 2) * sectorsPerCluster, root, 0, root.length / S);

        // ---------------------------------------------------------------- región de arranque (al final)
        byte[] boot = new byte[12 * S];
        boot[0] = (byte) 0xEB;
        boot[1] = 0x76;
        boot[2] = (byte) 0x90;
        System.arraycopy("EXFAT   ".getBytes(StandardCharsets.US_ASCII), 0, boot, 3, 8);
        Disk.put64(boot, 64, partStart);
        Disk.put64(boot, 72, partSectors);
        Disk.put32(boot, 80, fatOffset);
        Disk.put32(boot, 84, fatLength);
        Disk.put32(boot, 88, heap);
        Disk.put32(boot, 92, clusters);
        Disk.put32(boot, 96, rootCluster);
        Disk.put32(boot, 100, serial);
        Disk.put16(boot, 104, 0x0100);
        Disk.put16(boot, 106, 0);
        boot[108] = 9;
        boot[109] = (byte) Integer.numberOfTrailingZeros(sectorsPerCluster);
        boot[110] = 1;
        boot[111] = (byte) 0x80;
        boot[112] = (byte) (used * 100 / clusters);
        for (int i = 120; i < 510; i++) boot[i] = (byte) 0xF4;   // sin código de arranque: «hlt»
        boot[510] = 0x55;
        boot[511] = (byte) 0xAA;
        for (int s = 1; s <= 8; s++) {                          // sectores de arranque extendidos
            boot[s * S + 510] = 0x55;
            boot[s * S + 511] = (byte) 0xAA;
        }
        long sum = checksum(boot, 11 * S, true);
        for (int i = 0; i < S; i += 4) Disk.put32(boot, 11 * S + i, sum);
        Disk.writeSectors(dev, partStart + 12, boot, 0, 12);     // respaldo
        Disk.writeSectors(dev, partStart, boot, 0, 12);          // principal
        dev.flush();

        Result r = new Result();
        r.fatOffset = fatOffset;
        r.fatLength = fatLength;
        r.heapOffset = heap;
        r.clusterCount = clusters;
        r.sectorsPerCluster = sectorsPerCluster;
        return r;
    }

    private static long fatEntry(long e, long bmp, long bmpN, long upc, long upcN, long root) {
        if (e == 0) return 0xFFFFFFF8L;
        if (e == 1) return 0xFFFFFFFFL;
        if (e >= bmp && e < bmp + bmpN) return e == bmp + bmpN - 1 ? 0xFFFFFFFFL : e + 1;
        if (e >= upc && e < upc + upcN) return e == upc + upcN - 1 ? 0xFFFFFFFFL : e + 1;
        if (e == root) return 0xFFFFFFFFL;
        return 0;
    }

    /** Suma de comprobación de exFAT; en la región de arranque se saltan VolumeFlags y PercentInUse. */
    static long checksum(byte[] b, int len, boolean bootRegion) {
        long sum = 0;
        for (int i = 0; i < len; i++) {
            if (bootRegion && (i == 106 || i == 107 || i == 112)) continue;
            sum = (((sum & 1) != 0 ? 0x80000000L : 0) + (sum >>> 1) + (b[i] & 0xFF)) & 0xFFFFFFFFL;
        }
        return sum;
    }

    /** Tabla de mayúsculas comprimida: los tramos que no cambian se guardan como 0xFFFF + largo. */
    static byte[] upcaseTable() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int c = 0;
        while (c <= 0xFFFF) {
            int u = up(c);
            if (u == c) {
                int run = 0;
                while (c + run <= 0xFFFF && up(c + run) == c + run) run++;
                if (run >= 2) {
                    w16(out, 0xFFFF);
                    w16(out, run);
                    c += run;
                    continue;
                }
            }
            w16(out, u);
            c++;
        }
        return out.toByteArray();
    }

    private static int up(int c) {
        if (c >= 0xD800 && c <= 0xDFFF) return c;
        int u = Character.toUpperCase(c);
        return u > 0xFFFF ? c : u;
    }

    private static void w16(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    private static long roundUp(long v, long m) {
        return (v + m - 1) / m * m;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
