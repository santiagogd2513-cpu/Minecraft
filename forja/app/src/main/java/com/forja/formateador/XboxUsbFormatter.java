package com.forja.formateador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Memoria USB para la Xbox 360, como la deja la propia consola: una unidad FAT32 con una carpeta
 * oculta «Xbox360» que tiene los archivos Data0000, Data0001… Pegados uno tras otro forman un
 * disco virtual; la partición de la consola (XTAF, también llamada FATX) empieza a 0x20000000 bytes
 * de ese disco, y lo que hay antes queda reservado para el sistema.
 *
 * Todo el contenido de los archivos se escribe en la misma zona del disco, seguido, así que no hace
 * falta leer nada para saber dónde está cada cosa.
 */
final class XboxUsbFormatter {
    private XboxUsbFormatter() {
    }

    /** Lo que la consola acepta como mínimo y como máximo de una memoria USB. */
    static final long MIN_TOTAL = 0x20000000L + 0x4000000L;       // 512 MB reservados + 64 MB para datos
    static final long MAX_TOTAL = 32L << 30;
    static final long DATA_FILE = 0x7FFF0000L;                    // tamaño de cada archivo Data
    static final long XTAF_AT = 0x20000000L;
    static final int XTAF_CLUSTER = 0x4000;

    /** Cuántos bytes de datos caben en una unidad de este tamaño (o 0 si no sirve). */
    static long usable(long volBytes, int clusterBytes) {
        long fat = 2 * Io.roundUp((volBytes / clusterBytes + 2) * 4, 4096);   // las dos tablas FAT
        long avail = volBytes - fat - (4L << 20) - 4L * clusterBytes;          // y un margen para el arranque y las carpetas
        long t = Math.min(MAX_TOTAL, avail) & ~0xFFFFL;
        return t < MIN_TOTAL ? 0 : t;
    }

    static void format(Io io, long partStart, long partBytes, int clusterBytes, String label, long serial,
                       long hiddenSectors, Progress p) throws IOException {
        if (clusterBytes < 2048) throw new IOException("Para la Xbox 360 usa un clúster de 2 KB o más");
        long total = usable(partBytes, clusterBytes);
        if (total == 0) throw new IOException("La memoria es muy chica: la Xbox 360 pide 1 GB o más");

        Progress base = scaled(p, 0f, 0.1f);
        Fat32Formatter.format(io, partStart, partBytes, clusterBytes, label, serial, hiddenSectors, base);

        int bps = io.ss;
        byte[] boot = io.read(partStart, bps);
        int spc = boot[13] & 0xFF, rsvd = Io.le16(boot, 14);
        long fatSz = Io.le32(boot, 36), totSec = Io.le32(boot, 32);
        long dataStart = rsvd + 2 * fatSz;
        long clusters = (totSec - dataStart) / spc;
        long fatBytes = fatSz * bps;
        long fat0 = partStart + rsvd * (long) bps;

        // dónde va cada cosa: carpeta en el clúster 3, archivos seguidos desde el 4
        int files = (int) Io.ceilDiv(total, DATA_FILE);
        long[] first = new long[files], size = new long[files], count = new long[files];
        long next = 4;
        for (int i = 0; i < files; i++) {
            size[i] = i < files - 1 ? DATA_FILE : total - DATA_FILE * (files - 1);
            count[i] = Io.ceilDiv(size[i], clusterBytes);
            first[i] = next;
            next += count[i];
        }
        long lastCluster = next - 1;
        if (lastCluster - 1 > clusters) throw new IOException("No caben los archivos en esta unidad");
        if (p.cancelled()) throw new IOException("Cancelado");

        // tablas FAT: la cadena de cada archivo (las dos copias)
        p.update(0.12f, "Reservando el espacio de la Xbox 360…");
        final int CH = 1 << 16;                       // entradas por tramo
        long entries = fatBytes / 4;
        for (long b = 0; b <= lastCluster; b += CH) {
            int n = (int) Math.min(CH, entries - b);
            byte[] buf = io.read(fat0 + b * 4, n * 4);
            for (long c = Math.max(b, 3); c < b + n && c <= lastCluster; c++) {
                long v;
                if (c == 3) v = 0x0FFFFFFFL;
                else {
                    v = c + 1;
                    for (int i = 0; i < files; i++) if (c == first[i] + count[i] - 1) v = 0x0FFFFFFFL;
                }
                Io.put32(buf, (int) ((c - b) * 4), v);
            }
            for (int f = 0; f < 2; f++) io.write(fat0 + f * fatBytes + b * 4, buf);
            p.update(0.12f + 0.4f * Math.min(1f, (float) b / lastCluster), "Reservando el espacio de la Xbox 360…");
            if (p.cancelled()) throw new IOException("Cancelado");
        }

        // carpeta «Xbox360» en la raíz y los archivos dentro
        p.update(0.55f, "Creando la carpeta Xbox360…");
        long rootOff = partStart + dataStart * bps;
        byte[] root = io.read(rootOff, bps);
        int slot = 0;
        while (slot + 32 <= bps && root[slot] != 0) slot += 32;
        byte[] dirEntries = entries("Xbox360", "XBOX360", 0x12, 3, 0);
        if (slot + dirEntries.length > bps) throw new IOException("No hay lugar en la raíz");
        System.arraycopy(dirEntries, 0, root, slot, dirEntries.length);
        io.write(rootOff, root);

        long dirOff = partStart + (dataStart + 1L * spc) * bps;      // clúster 3
        io.zero(dirOff, clusterBytes);
        byte[] dir = new byte[Math.max(bps, (int) Io.roundUp(2 * 32 + files * 64, bps))];
        byte[] dot = entries(null, ".", 0x10, 3, 0), dotdot = entries(null, "..", 0x10, 0, 0);
        System.arraycopy(dot, 0, dir, 0, 32);
        System.arraycopy(dotdot, 0, dir, 32, 32);
        int at = 64;
        for (int i = 0; i < files; i++) {
            String nm = String.format(java.util.Locale.US, "Data%04d", i);
            byte[] e = entries(nm, nm.toUpperCase(java.util.Locale.US), 0x20, first[i], size[i]);
            System.arraycopy(e, 0, dir, at, e.length);
            at += e.length;
        }
        io.write(dirOff, dir);

        // FSInfo: cuántos clústeres quedan libres
        long used = lastCluster - 1;                  // del 2 al último
        for (long sec : new long[]{1, 7}) {
            byte[] fi = io.read(partStart + sec * bps, bps);
            Io.put32(fi, 488, clusters - used);
            Io.put32(fi, 492, lastCluster + 1);
            io.write(partStart + sec * bps, fi);
        }

        // la partición de la consola dentro de los archivos
        p.update(0.62f, "Dando formato Xbox 360 (FATX)…");
        long[] filePos = new long[files];
        for (int i = 0; i < files; i++) filePos[i] = partStart + (dataStart + (first[i] - 2) * spc) * bps;
        Xtaf.format(io, filePos[0], total - XTAF_AT, new SecureRandom().nextInt(), p, 0.62f, 0.98f);
        io.flush();
    }

    // ------------------------------------------------------------------ la partición XTAF

    private static final class Xtaf {
        static void format(Io io, long file0, long partBytes, int id, Progress p, float from, float to) throws IOException {
            long at = file0 + XTAF_AT;                       // todo esto queda dentro del primer archivo
            long clusters = partBytes / XTAF_CLUSTER;
            long entries = clusters + 1;
            boolean fat32 = entries >= 0xFFF0;
            long fatBytes = (entries * (fat32 ? 4 : 2) + 0xFFF) & ~0xFFFL;
            if (XTAF_AT + 0x1000 + fatBytes + XTAF_CLUSTER > DATA_FILE) throw new IOException("La tabla no cabe en el primer archivo");
            // lo que haya antes (sistema de la consola) se deja limpio
            io.zero(file0, 0x2000);
            io.zero(at, 0x1000 + fatBytes, p, from, to, "Dando formato Xbox 360 (FATX)…");
            byte[] h = new byte[0x1000];
            h[0] = 'X'; h[1] = 'T'; h[2] = 'A'; h[3] = 'F';
            be32(h, 4, id);
            be32(h, 8, XTAF_CLUSTER / 512);
            be32(h, 12, 1);                                  // clúster de la carpeta raíz
            io.write(at, h);
            byte[] f = new byte[0x1000];
            if (fat32) {
                be32(f, 0, 0xFFFFFFF8);
                be32(f, 4, 0xFFFFFFFF);
            } else {
                f[0] = (byte) 0xFF; f[1] = (byte) 0xF8;
                f[2] = (byte) 0xFF; f[3] = (byte) 0xFF;
            }
            io.write(at + 0x1000, f);
            // carpeta raíz vacía: todo en 0xFF
            byte[] ff = new byte[XTAF_CLUSTER];
            java.util.Arrays.fill(ff, (byte) 0xFF);
            io.write(at + 0x1000 + fatBytes, ff);
        }

        private static void be32(byte[] b, int o, int v) {
            b[o] = (byte) (v >>> 24); b[o + 1] = (byte) (v >>> 16); b[o + 2] = (byte) (v >>> 8); b[o + 3] = (byte) v;
        }
    }

    // ------------------------------------------------------------------ entradas de carpeta FAT32

    /** Entrada con nombre largo (si hay) más la corta, de 32 bytes cada una. */
    private static byte[] entries(String longName, String shortName, int attr, long cluster, long size) {
        byte[] sfn = new byte[11];
        java.util.Arrays.fill(sfn, (byte) ' ');
        if (shortName.equals(".") || shortName.equals("..")) {
            for (int i = 0; i < shortName.length(); i++) sfn[i] = '.';
        } else {
            byte[] s = shortName.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(s, 0, sfn, 0, Math.min(8, s.length));
        }
        List<byte[]> out = new ArrayList<>();
        if (longName != null) {
            int sum = 0;
            for (byte b : sfn) sum = (((sum & 1) << 7) | ((sum & 0xFF) >> 1)) + (b & 0xFF) & 0xFF;
            int parts = (longName.length() + 12) / 13;
            for (int k = parts; k >= 1; k--) {
                byte[] e = new byte[32];
                e[0] = (byte) (k | (k == parts ? 0x40 : 0));
                e[11] = 0x0F;
                e[13] = (byte) sum;
                int[] pos = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
                for (int j = 0; j < 13; j++) {
                    int idx = (k - 1) * 13 + j;
                    int ch = idx < longName.length() ? longName.charAt(idx) : idx == longName.length() ? 0 : 0xFFFF;
                    e[pos[j]] = (byte) ch;
                    e[pos[j] + 1] = (byte) (ch >> 8);
                }
                out.add(e);
            }
        }
        byte[] e = new byte[32];
        System.arraycopy(sfn, 0, e, 0, 11);
        e[11] = (byte) attr;
        java.util.Calendar c = java.util.Calendar.getInstance();
        int date = ((c.get(java.util.Calendar.YEAR) - 1980) << 9) | ((c.get(java.util.Calendar.MONTH) + 1) << 5) | c.get(java.util.Calendar.DAY_OF_MONTH);
        int time = (c.get(java.util.Calendar.HOUR_OF_DAY) << 11) | (c.get(java.util.Calendar.MINUTE) << 5) | (c.get(java.util.Calendar.SECOND) / 2);
        Io.put16(e, 14, time);
        Io.put16(e, 16, date);
        Io.put16(e, 18, date);
        Io.put16(e, 20, (int) (cluster >>> 16));
        Io.put16(e, 22, time);
        Io.put16(e, 24, date);
        Io.put16(e, 26, (int) (cluster & 0xFFFF));
        Io.put32(e, 28, size);
        out.add(e);
        byte[] r = new byte[out.size() * 32];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, r, i * 32, 32);
        return r;
    }

    private static Progress scaled(Progress p, float from, float to) {
        return new Progress() {
            @Override
            public void update(float f, String step) {
                p.update(f < 0 ? -1 : from + (to - from) * f, step);
            }

            @Override
            public boolean cancelled() {
                return p.cancelled();
            }
        };
    }
}
