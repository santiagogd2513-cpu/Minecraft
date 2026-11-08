package com.forja.formateador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * exFAT según la especificación pública de Microsoft: región de arranque con respaldo, FAT,
 * mapa de bits, tabla de mayúsculas y carpeta raíz con la etiqueta. Sirve con sectores de
 * 512 B y de 4 KB.
 */
final class ExFatFormatter {
    private ExFatFormatter() {
    }

    static void format(Io io, long partStart, long partBytes, int clusterBytes, String label, long serial, Progress p) throws IOException {
        final int S = io.ss;
        int spc = clusterBytes / S;
        if (spc < 1 || Integer.bitCount(spc) != 1) throw new IOException("Tamaño de clúster no válido para exFAT");
        long partSectors = partBytes / S;

        long fatOffset = Io.roundUp(24, Math.max(1, 65536 / S));
        long fatLength = 1, heap = 0, clusters = 0;
        for (int i = 0; i < 32; i++) {
            heap = Io.roundUp(fatOffset + fatLength, spc);
            clusters = (partSectors - heap) / spc;
            long need = Io.ceilDiv((clusters + 2) * 4, S);
            if (need == fatLength) break;
            fatLength = need;
        }
        heap = Io.roundUp(fatOffset + fatLength, spc);
        clusters = (partSectors - heap) / spc;
        if (clusters < 16 || clusters > 0xFFFFFFF5L) throw new IOException("Tamaño no válido para exFAT con ese clúster");

        byte[] upcase = upcaseTable();
        long bitmapBytes = Io.ceilDiv(clusters, 8);
        long bitmapClusters = Io.ceilDiv(bitmapBytes, clusterBytes);
        long upcaseClusters = Io.ceilDiv(upcase.length, clusterBytes);
        long bitmapCluster = 2;
        long upcaseCluster = bitmapCluster + bitmapClusters;
        long rootCluster = upcaseCluster + upcaseClusters;
        long used = bitmapClusters + upcaseClusters + 1;

        // FAT
        long fatBytesTotal = fatLength * S;
        long fatEntries = clusters + 2;
        int chunk = Math.max(S, 1024 * 1024 / S * S);
        for (long off = 0; off < fatBytesTotal; off += chunk) {
            int len = (int) Math.min(chunk, fatBytesTotal - off);
            byte[] b = new byte[len];
            long firstE = off / 4, lastE = Math.min((off + len) / 4, fatEntries);
            for (long e = firstE; e < lastE; e++) {
                long v = fatEntry(e, bitmapCluster, bitmapClusters, upcaseCluster, upcaseClusters, rootCluster);
                if (v != 0) Io.put32(b, (int) (e * 4 - off), v);
            }
            io.write(partStart + fatOffset * S + off, b);
            p.update(0.05f + 0.6f * (off + len) / fatBytesTotal, "Escribiendo la tabla FAT de exFAT…");
            if (p.cancelled()) throw new IOException("Cancelado");
        }

        // mapa de bits (pueden ser varios MB en discos grandes: por tramos)
        long heapOff = partStart + heap * S;
        long bmBytes = bitmapClusters * clusterBytes;
        for (long off = 0; off < bmBytes; off += chunk) {
            int len = (int) Math.min(chunk, bmBytes - off);
            byte[] b = new byte[len];
            for (long c = off * 8; c < used && c < (off + len) * 8; c++) b[(int) ((c >> 3) - off)] |= (byte) (1 << (c & 7));
            io.write(heapOff + (bitmapCluster - 2) * clusterBytes + off, b);
            p.update(0.65f + 0.25f * (off + len) / bmBytes, "Escribiendo el mapa de espacio…");
        }

        // tabla de mayúsculas y carpeta raíz: lo que tiene datos se escribe tal cual y el resto del
        // clúster con ceros por tramos (con clústeres de 32 MB no se reserva un bloque de 32 MB)
        long upOff = heapOff + (upcaseCluster - 2) * clusterBytes;
        long upLen = upcaseClusters * clusterBytes;
        int upHead = (int) Io.roundUp(upcase.length, S);
        io.zero(upOff + upHead, upLen - upHead);
        byte[] up = new byte[upHead];
        System.arraycopy(upcase, 0, up, 0, upcase.length);
        io.write(upOff, up);

        long rootOff = heapOff + (rootCluster - 2) * clusterBytes;
        io.zero(rootOff, clusterBytes);
        byte[] root = new byte[S];
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
        root[o] = (byte) 0x81;
        Io.put32(root, o + 20, bitmapCluster);
        Io.put64(root, o + 24, bitmapBytes);
        o += 32;
        root[o] = (byte) 0x82;
        Io.put32(root, o + 4, checksum(upcase, upcase.length, false));
        Io.put32(root, o + 20, upcaseCluster);
        Io.put64(root, o + 24, upcase.length);
        io.write(rootOff, root);

        // región de arranque al final (principal y respaldo)
        p.update(0.95f, "Escribiendo el arranque exFAT…");
        byte[] boot = new byte[12 * S];
        boot[0] = (byte) 0xEB;
        boot[1] = 0x76;
        boot[2] = (byte) 0x90;
        System.arraycopy("EXFAT   ".getBytes(StandardCharsets.US_ASCII), 0, boot, 3, 8);
        Io.put64(boot, 64, partStart / S);
        Io.put64(boot, 72, partSectors);
        Io.put32(boot, 80, fatOffset);
        Io.put32(boot, 84, fatLength);
        Io.put32(boot, 88, heap);
        Io.put32(boot, 92, clusters);
        Io.put32(boot, 96, rootCluster);
        Io.put32(boot, 100, serial);
        Io.put16(boot, 104, 0x0100);
        boot[108] = (byte) Integer.numberOfTrailingZeros(S);
        boot[109] = (byte) Integer.numberOfTrailingZeros(spc);
        boot[110] = 1;
        boot[111] = (byte) 0x80;
        boot[112] = (byte) (used * 100 / clusters);
        for (int i = 120; i < 510; i++) boot[i] = (byte) 0xF4;
        boot[510] = 0x55;
        boot[511] = (byte) 0xAA;
        for (int s = 1; s <= 8; s++) {
            boot[s * S + S - 2] = 0x55;
            boot[s * S + S - 1] = (byte) 0xAA;
        }
        long sum = checksum(boot, 11 * S, true);
        for (int i = 0; i < S; i += 4) Io.put32(boot, 11 * S + i, sum);
        io.write(partStart + 12L * S, boot);
        io.write(partStart, boot);
        io.flush();
    }

    private static long fatEntry(long e, long bmp, long bmpN, long upc, long upcN, long root) {
        if (e == 0) return 0xFFFFFFF8L;
        if (e == 1) return 0xFFFFFFFFL;
        if (e >= bmp && e < bmp + bmpN) return e == bmp + bmpN - 1 ? 0xFFFFFFFFL : e + 1;
        if (e >= upc && e < upc + upcN) return e == upc + upcN - 1 ? 0xFFFFFFFFL : e + 1;
        if (e == root) return 0xFFFFFFFFL;
        return 0;
    }

    static long checksum(byte[] b, int len, boolean bootRegion) {
        long sum = 0;
        for (int i = 0; i < len; i++) {
            if (bootRegion && (i == 106 || i == 107 || i == 112)) continue;
            sum = (((sum & 1) != 0 ? 0x80000000L : 0) + (sum >>> 1) + (b[i] & 0xFF)) & 0xFFFFFFFFL;
        }
        return sum;
    }

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

    static int up(int c) {
        if (c >= 0xD800 && c <= 0xDFFF) return c;
        int u = Character.toUpperCase(c);
        return u > 0xFFFF ? c : u;
    }

    private static void w16(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }
}
