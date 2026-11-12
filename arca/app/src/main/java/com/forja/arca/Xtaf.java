package com.forja.arca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * El sistema de archivos de la Xbox 360 (XTAF, parecido a FAT pero con números de mayor a menor):
 * se lee, no se escribe. Una partición empieza con la marca «XTAF», luego viene la tabla de
 * clústeres (FAT) en 0x1000 y después los datos. Cada entrada de una carpeta mide 64 bytes.
 */
final class Xtaf {
    static final int MAGIC = 0x58544146;   // «XTAF»
    private static final int FAT_AT = 0x1000;
    private static final int MAX_CHAIN = 1 << 22;

    static final class Entry {
        String name;
        boolean dir;
        int cluster;
        long size;
        /** Fecha de modificación, en milisegundos (0 si no es válida). */
        long modified;
        /** Ruta completa desde la raíz, con «/». */
        String path;
    }

    private final ByteSource src;
    final int sectorsPerCluster;
    final int clusterBytes;
    private final boolean fat32;
    private final long dataAt, entries;
    final int volumeId;

    private byte[] fatCache;
    private long fatCacheAt = -1;
    private static final int FAT_PAGE = 1 << 16;

    /** ¿Empieza ahí una partición XTAF? */
    static boolean isXtaf(ByteSource s) {
        try {
            if (s.length() < 0x2000) return false;
            byte[] h = s.read(0, 16);
            return be32(h, 0) == MAGIC && be32(h, 8) > 0 && Integer.bitCount(be32(h, 8)) == 1 && be32(h, 8) <= 0x1000;
        } catch (IOException e) {
            return false;
        }
    }

    Xtaf(ByteSource s) throws IOException {
        src = s;
        byte[] h = s.read(0, 16);
        if (be32(h, 0) != MAGIC) throw new IOException("No es una partición de Xbox 360 (falta la marca XTAF)");
        volumeId = be32(h, 4);
        sectorsPerCluster = be32(h, 8);
        if (sectorsPerCluster <= 0 || sectorsPerCluster > 0x1000 || Integer.bitCount(sectorsPerCluster) != 1) {
            throw new IOException("El tamaño de clúster de la partición no es válido (" + sectorsPerCluster + ")");
        }
        clusterBytes = sectorsPerCluster * 512;
        entries = s.length() / clusterBytes;
        fat32 = entries >= 0xFFF4;
        long fatBytes = entries * (fat32 ? 4 : 2);
        fatBytes = (fatBytes + 0xFFF) & ~0xFFFL;
        dataAt = FAT_AT + fatBytes;
        if (dataAt >= s.length()) throw new IOException("La tabla de la partición está dañada");
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static int be16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    /** Siguiente clúster de la cadena, o -1 si es el último. */
    private int next(int cluster) throws IOException {
        long at = (long) cluster * (fat32 ? 4 : 2);
        long page = at / FAT_PAGE * FAT_PAGE;
        if (page != fatCacheAt) {
            long left = Math.max(0, Math.min(FAT_PAGE, entries * (fat32 ? 4 : 2) - page));
            byte[] b = new byte[(int) left + 4];
            src.read(FAT_AT + page, b, 0, (int) left);
            fatCache = b;
            fatCacheAt = page;
        }
        int o = (int) (at - page);
        if (o + (fat32 ? 4 : 2) > fatCache.length) return -1;
        long v = fat32 ? be32(fatCache, o) & 0xFFFFFFFFL : be16(fatCache, o);
        long end = fat32 ? 0xFFFFFFF8L : 0xFFF8L;
        if (v == 0 || v >= end || v >= entries) return -1;
        return (int) v;
    }

    private List<Integer> chain(int first) throws IOException {
        List<Integer> out = new ArrayList<>();
        int c = first;
        while (c >= 1 && out.size() < MAX_CHAIN) {
            out.add(c);
            c = next(c);
        }
        return out;
    }

    private long clusterAt(int cluster) {
        return dataAt + (long) (cluster - 1) * clusterBytes;
    }

    private static final String[] NO_NAMES = {};

    /** Lo que hay dentro de una carpeta (la raíz es el clúster 1). */
    List<Entry> list(int cluster, String parentPath) throws IOException {
        List<Entry> out = new ArrayList<>();
        byte[] buf = new byte[clusterBytes];
        for (int c : chain(cluster)) {
            long at = clusterAt(c);
            if (at + clusterBytes > src.length()) break;
            src.read(at, buf, 0, clusterBytes);
            for (int o = 0; o + 64 <= clusterBytes; o += 64) {
                int nameLen = buf[o] & 0xFF;
                if (nameLen == 0xFF || nameLen == 0) return out;
                if (nameLen == 0xE5) continue;       // borrado
                if (nameLen > 42) continue;
                Entry e = new Entry();
                e.name = new String(buf, o + 2, nameLen, StandardCharsets.ISO_8859_1);
                e.dir = (buf[o + 1] & 0x10) != 0;
                e.cluster = be32(buf, o + 44);
                e.size = be32(buf, o + 48) & 0xFFFFFFFFL;
                e.modified = date(be16(buf, o + 60), be16(buf, o + 62));
                e.path = parentPath.isEmpty() ? e.name : parentPath + "/" + e.name;
                out.add(e);
            }
        }
        return out;
    }

    List<Entry> listRoot() throws IOException {
        return list(1, "");
    }

    List<Entry> list(Entry dir) throws IOException {
        return list(dir.cluster, dir.path);
    }

    /** Busca una ruta como «Content/0000000000000000/4D5307E6» (sin importar mayúsculas). */
    Entry find(String path) throws IOException {
        if (path.isEmpty()) return null;
        List<Entry> cur = listRoot();
        Entry hit = null;
        for (String part : path.split("/")) {
            hit = null;
            for (Entry e : cur) {
                if (e.name.equalsIgnoreCase(part)) {
                    hit = e;
                    break;
                }
            }
            if (hit == null) return null;
            if (hit.dir) cur = list(hit);
        }
        return hit;
    }

    /** Lee un pedazo de un archivo. */
    byte[] readFile(Entry e, long off, int len) throws IOException {
        if (e.dir) throw new IOException("Es una carpeta");
        if (off >= e.size) return new byte[0];
        len = (int) Math.min(len, e.size - off);
        byte[] out = new byte[len];
        int done = 0;
        int skip = (int) (off / clusterBytes);
        int c = e.cluster;
        for (int i = 0; i < skip && c >= 1; i++) c = next(c);
        long inFirst = off % clusterBytes;
        while (done < len && c >= 1) {
            int n = (int) Math.min(len - done, clusterBytes - inFirst);
            src.read(clusterAt(c) + inFirst, out, done, n);
            done += n;
            inFirst = 0;
            c = next(c);
        }
        if (done < len) throw new IOException("El archivo «" + e.name + "» está incompleto o dañado");
        return out;
    }

    /** Fecha y hora de la Xbox 360 (como FAT, con año base 1980) a milisegundos. */
    private static long date(int d, int t) {
        int year = 1980 + (d >> 9), month = (d >> 5) & 0xF, day = d & 0x1F;
        int hour = t >> 11, min = (t >> 5) & 0x3F, sec = (t & 0x1F) * 2;
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || min > 59 || sec > 59) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, hour, min, sec);
        return c.getTimeInMillis();
    }
}
