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
        /** Dónde está su ficha de 64 bytes en la partición (para renombrar o borrar); -1 si no se sabe. */
        long entryAt = -1;
        int parentCluster;
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
        // máximo de clústeres = tamaño / clúster + 1; con menos de 0xFFF0 la tabla usa 2 bytes por entrada, si no 4
        entries = s.length() / clusterBytes + 1;
        fat32 = entries >= 0xFFF0;
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
                e.modified = date(be16(buf, o + 56), be16(buf, o + 58));   // última escritura
                e.path = parentPath.isEmpty() ? e.name : parentPath + "/" + e.name;
                e.entryAt = at + o;
                e.parentCluster = cluster;
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

    /** Copia un archivo entero a una salida, siguiendo la cadena de clústeres una sola vez. */
    void copyFile(Entry e, java.io.OutputStream out, java.util.function.LongConsumer progress) throws IOException {
        long left = e.size, done = 0;
        int c = e.cluster;
        byte[] buf = new byte[clusterBytes];
        while (left > 0 && c >= 1) {
            int n = (int) Math.min(left, clusterBytes);
            src.read(clusterAt(c), buf, 0, n);
            out.write(buf, 0, n);
            left -= n;
            done += n;
            if (progress != null) progress.accept(done);
            c = next(c);
        }
        if (left > 0) throw new IOException("El archivo «" + e.name + "» está incompleto o dañado");
    }

    /** Un archivo como fuente de bytes (para abrir un paquete que está adentro). La cadena de clústeres se lee una sola vez. */
    ByteSource asSource(Entry e) {
        Xtaf self = this;
        return new ByteSource() {
            private int[] clusters;

            @Override
            public long length() {
                return e.size;
            }

            @Override
            public synchronized void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > e.size) throw new IOException("Lectura fuera del archivo");
                if (clusters == null) {
                    List<Integer> ch = self.chain(e.cluster);
                    clusters = new int[ch.size()];
                    for (int i = 0; i < clusters.length; i++) clusters[i] = ch.get(i);
                }
                int done = 0;
                while (done < len) {
                    long pos = off + done;
                    int idx = (int) (pos / clusterBytes);
                    if (idx >= clusters.length) throw new IOException("El archivo «" + e.name + "» está incompleto o dañado");
                    int inCluster = (int) (pos % clusterBytes);
                    int n = Math.min(len - done, clusterBytes - inCluster);
                    src.read(self.clusterAt(clusters[idx]) + inCluster, buf, boff + done, n);
                    done += n;
                }
            }
        };
    }

    /** Cuánto ocupa todo lo que hay dentro de una carpeta (con tope de profundidad por si la tabla está dañada). */
    long totalSize(Entry dir, java.util.concurrent.atomic.AtomicBoolean stop) throws IOException {
        return totalSize(dir, stop, 0);
    }

    private long totalSize(Entry dir, java.util.concurrent.atomic.AtomicBoolean stop, int depth) throws IOException {
        if (depth > 24) return 0;
        long sum = 0;
        for (Entry e : list(dir)) {
            if (stop != null && stop.get()) return sum;
            sum += e.dir ? totalSize(e, stop, depth + 1) : e.size;
        }
        return sum;
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

    // ================================================================== escritura

    private int freeHint = 2;

    /** ¿La partición está abierta para escribir? */
    boolean canWrite() {
        return src.writable();
    }

    private void needWrite() throws IOException {
        if (!src.writable()) throw new IOException("Esta partición está abierta solo para leer");
    }

    /** Los nombres de la 360: hasta 42 letras, sin los signos que ella misma prohíbe. */
    static void checkName(String n) throws IOException {
        if (n == null || n.isEmpty() || n.length() > 42) throw new IOException("El nombre debe tener entre 1 y 42 letras");
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c < 0x20 || c > 0xFF || "\\/:*?\"<>|".indexOf(c) >= 0) throw new IOException("El nombre «" + n + "» tiene letras que la Xbox 360 no admite");
        }
    }

    private int entrySize() {
        return fat32 ? 4 : 2;
    }

    private long endMark() {
        return fat32 ? 0xFFFFFFFFL : 0xFFFFL;
    }

    private void putFat(byte[] b, int o, long v) {
        if (fat32) {
            b[o] = (byte) (v >> 24);
            b[o + 1] = (byte) (v >> 16);
            b[o + 2] = (byte) (v >> 8);
            b[o + 3] = (byte) v;
        } else {
            b[o] = (byte) (v >> 8);
            b[o + 1] = (byte) v;
        }
    }

    /** ¿Cabe el clúster entero dentro de la partición? */
    private boolean fits(int cluster) {
        return clusterAt(cluster) + clusterBytes <= src.length();
    }

    /** Busca clústeres libres (valor 0 en la tabla) a partir del último hallado, dando una vuelta si hace falta. */
    private List<Integer> allocate(int count) throws IOException {
        List<Integer> out = new ArrayList<>(count);
        if (count == 0) return out;
        int es = entrySize();
        long total = entries;
        int startAt = Math.max(2, Math.min(freeHint, (int) Math.min(total, Integer.MAX_VALUE)));
        boolean wrapped = false;
        long c = startAt;
        byte[] page = new byte[FAT_PAGE];
        while (out.size() < count) {
            if (c >= total) {
                if (wrapped) break;
                wrapped = true;
                c = 2;
            }
            if (wrapped && c >= startAt) break;
            long pageStart = c * es / FAT_PAGE * FAT_PAGE;
            int len = (int) Math.min(FAT_PAGE, total * es - pageStart);
            if (len <= 0) break;
            src.read(FAT_AT + pageStart, page, 0, len);
            for (; c < total && c * es < pageStart + len && out.size() < count; c++) {
                if (wrapped && c >= startAt) break;
                int o = (int) (c * es - pageStart);
                long v = fat32 ? be32(page, o) & 0xFFFFFFFFL : be16(page, o);
                if (v == 0 && fits((int) c)) out.add((int) c);
            }
        }
        if (out.size() < count) throw new IOException("No hay espacio suficiente en la partición");
        freeHint = out.get(out.size() - 1) + 1;
        return out;
    }

    /** Une los clústeres en una cadena (el último lleva la marca de final). Agrupa los que van seguidos para escribir menos veces. */
    private void linkChain(List<Integer> cl) throws IOException {
        int n = cl.size(), es = entrySize(), i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && cl.get(j + 1) == cl.get(j) + 1) j++;
            byte[] buf = new byte[(j - i + 1) * es];
            for (int k = i; k <= j; k++) putFat(buf, (k - i) * es, k + 1 < n ? cl.get(k + 1) : endMark());
            src.write(FAT_AT + (long) cl.get(i) * es, buf, 0, buf.length);
            i = j + 1;
        }
        fatCacheAt = -1;
    }

    private void setFat(int cluster, long value) throws IOException {
        byte[] b = new byte[entrySize()];
        putFat(b, 0, value);
        src.write(FAT_AT + (long) cluster * entrySize(), b, 0, b.length);
        fatCacheAt = -1;
    }

    private static int dosDate(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms);
        return ((c.get(java.util.Calendar.YEAR) - 1980) << 9) | ((c.get(java.util.Calendar.MONTH) + 1) << 5) | c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    private static int dosTime(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms);
        return (c.get(java.util.Calendar.HOUR_OF_DAY) << 11) | (c.get(java.util.Calendar.MINUTE) << 5) | (c.get(java.util.Calendar.SECOND) / 2);
    }

    /** La ficha de 64 bytes de un archivo o carpeta. */
    private byte[] makeEntry(String name, boolean dir, int firstCluster, long size, long when) {
        byte[] e = new byte[64];
        java.util.Arrays.fill(e, (byte) 0xFF);
        byte[] nb = name.getBytes(StandardCharsets.ISO_8859_1);
        e[0] = (byte) nb.length;
        e[1] = (byte) (dir ? 0x10 : 0x00);
        System.arraycopy(nb, 0, e, 2, nb.length);
        put32(e, 44, firstCluster);
        put32(e, 48, dir ? 0 : size);
        int d = dosDate(when), t = dosTime(when);
        for (int o = 52; o <= 56; o += 4) {
            e[o] = (byte) (d >> 8);
            e[o + 1] = (byte) d;
            e[o + 2] = (byte) (t >> 8);
            e[o + 3] = (byte) t;
        }
        return e;
    }

    private static void put32(byte[] b, int o, long v) {
        b[o] = (byte) (v >> 24);
        b[o + 1] = (byte) (v >> 16);
        b[o + 2] = (byte) (v >> 8);
        b[o + 3] = (byte) v;
    }

    /** Pone la ficha en el primer hueco de la carpeta (o agranda la carpeta con un clúster más). */
    private void addEntry(int dirCluster, byte[] entry) throws IOException {
        byte[] buf = new byte[clusterBytes];
        List<Integer> ch = chain(dirCluster);
        for (int c : ch) {
            src.read(clusterAt(c), buf, 0, clusterBytes);
            for (int o = 0; o + 64 <= clusterBytes; o += 64) {
                int nl = buf[o] & 0xFF;
                if (nl == 0xFF || nl == 0 || nl == 0xE5) {
                    src.write(clusterAt(c) + o, entry, 0, 64);
                    return;
                }
            }
        }
        // la carpeta está llena: se le agrega un clúster nuevo, vacío (relleno de 0xFF como hace la consola)
        int nc = allocate(1).get(0);
        byte[] fresh = new byte[clusterBytes];
        java.util.Arrays.fill(fresh, (byte) 0xFF);
        System.arraycopy(entry, 0, fresh, 0, 64);
        src.write(clusterAt(nc), fresh, 0, clusterBytes);
        setFat(nc, endMark());
        setFat(ch.get(ch.size() - 1), nc);
    }

    private boolean exists(int dirCluster, String name) throws IOException {
        for (Entry e : list(dirCluster, "")) if (e.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Crea una carpeta vacía dentro de otra (la raíz es el clúster 1). Devuelve su clúster. */
    int mkdir(int parentCluster, String name) throws IOException {
        needWrite();
        checkName(name);
        if (exists(parentCluster, name)) throw new IOException("Ya existe «" + name + "» en esa carpeta");
        int c = allocate(1).get(0);
        byte[] fresh = new byte[clusterBytes];
        java.util.Arrays.fill(fresh, (byte) 0xFF);
        src.write(clusterAt(c), fresh, 0, clusterBytes);      // 1.º los datos de la carpeta
        setFat(c, endMark());                                    // 2.º la tabla
        addEntry(parentCluster, makeEntry(name, true, c, 0, System.currentTimeMillis()));   // 3.º la ficha, al final
        src.flush();
        return c;
    }

    /** Recorre una ruta como «Content/0000000000000000/545408A7/00000001» creando las carpetas que falten. Devuelve el clúster de la última. */
    int ensureDirs(String path) throws IOException {
        needWrite();
        int cur = 1;
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            Entry hit = null;
            for (Entry e : list(cur, "")) {
                if (e.name.equalsIgnoreCase(part)) {
                    hit = e;
                    break;
                }
            }
            if (hit == null) cur = mkdir(cur, part);
            else if (!hit.dir) throw new IOException("«" + part + "» es un archivo, no una carpeta");
            else cur = hit.cluster;
        }
        return cur;
    }

    /**
     * Crea un archivo nuevo. Escribe primero los datos, luego la tabla y al final la ficha de la carpeta; después
     * vuelve a leer lo escrito y lo compara con lo que entró (CRC32). Si algo no cuadra, lo deshace y avisa.
     */
    Entry createFile(int parentCluster, String name, java.io.InputStream in, long size, java.util.function.LongConsumer progress) throws IOException {
        needWrite();
        checkName(name);
        if (size < 0 || size > 0xFFFFFFFFL) throw new IOException("Un archivo de la Xbox 360 no puede pasar de 4 GB");
        if (exists(parentCluster, name)) throw new IOException("Ya existe «" + name + "» en esa carpeta");
        int count = (int) ((size + clusterBytes - 1) / clusterBytes);
        List<Integer> cl = allocate(count);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        byte[] buf = new byte[clusterBytes];
        long left = size, done = 0;
        int i = 0;
        // los clústeres seguidos se escriben juntos (hasta 1 MB) para que una memoria USB no se arrastre
        int maxRun = Math.max(1, (1 << 20) / clusterBytes);
        while (i < count) {
            int j = i;
            while (j + 1 < count && j - i + 1 < maxRun && cl.get(j + 1) == cl.get(j) + 1) j++;
            int n = j - i + 1;
            byte[] big = new byte[n * clusterBytes];
            int want = (int) Math.min(left, (long) big.length);
            int got = 0;
            while (got < want) {
                int r = in.read(big, got, want - got);
                if (r < 0) throw new IOException("El archivo de origen terminó antes de lo esperado");
                got += r;
            }
            crc.update(big, 0, want);
            src.write(clusterAt(cl.get(i)), big, 0, big.length);
            left -= want;
            done += want;
            if (progress != null) progress.accept(done);
            i = j + 1;
        }
        if (count > 0) linkChain(cl);
        Entry e = new Entry();
        e.name = name;
        e.dir = false;
        e.cluster = count > 0 ? cl.get(0) : 0;
        e.size = size;
        // comprobación de lo escrito, antes de que el archivo «exista» para la consola
        if (count > 0) {
            java.util.zip.CRC32 back = new java.util.zip.CRC32();
            copyFile(e, new java.io.OutputStream() {
                public void write(int b) {
                    back.update(b);
                }

                public void write(byte[] b, int o, int l) {
                    back.update(b, o, l);
                }
            }, null);
            if (back.getValue() != crc.getValue()) {
                for (int c : cl) setFat(c, 0);
                throw new IOException("Lo que se escribió no coincide con el original: no se creó el archivo");
            }
        }
        addEntry(parentCluster, makeEntry(name, false, e.cluster, size, System.currentTimeMillis()));
        src.flush();
        return e;
    }

    /** Borra un archivo, o una carpeta con todo lo que tenga: libera sus clústeres y marca la ficha como borrada. */
    void delete(Entry e) throws IOException {
        needWrite();
        if (e.entryAt < 0) throw new IOException("No sé dónde está la ficha de «" + e.name + "»");
        if (e.dir) {
            for (Entry k : list(e.cluster, e.path)) delete(k);
        }
        if (e.cluster >= 1) {
            for (int c : chain(e.cluster)) setFat(c, 0);
        }
        src.write(e.entryAt, new byte[]{(byte) 0xE5}, 0, 1);
        src.flush();
    }

    /** Cambia el nombre de un archivo o carpeta. */
    void rename(Entry e, String newName) throws IOException {
        needWrite();
        checkName(newName);
        if (e.entryAt < 0) throw new IOException("No sé dónde está la ficha de «" + e.name + "»");
        if (!e.name.equalsIgnoreCase(newName) && exists(e.parentCluster, newName)) throw new IOException("Ya existe «" + newName + "» en esa carpeta");
        byte[] nb = newName.getBytes(StandardCharsets.ISO_8859_1);
        byte[] f = new byte[44];
        java.util.Arrays.fill(f, (byte) 0xFF);
        f[0] = (byte) nb.length;
        f[1] = (byte) (e.dir ? 0x10 : 0x00);
        System.arraycopy(nb, 0, f, 2, nb.length);
        src.write(e.entryAt, f, 0, 44);
        src.flush();
    }
}
