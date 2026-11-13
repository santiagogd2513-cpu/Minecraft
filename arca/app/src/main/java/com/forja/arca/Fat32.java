package com.forja.arca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lector mínimo (solo lectura) de FAT32 para encontrar, en una memoria USB que la consola dejó
 * configurada, la carpeta oculta «Xbox360» y sus archivos Data0000, Data0001… Con eso Arca abre
 * la memoria directo por USB, sin pasar por el selector de carpetas de Android.
 */
final class Fat32 {
    private static final Pattern DATA = Pattern.compile("(?i)data\\d{4}");

    private final ByteSource disk;
    private final long base;
    private final int bps, spc;
    private final long fatAt, dataAt, clusters, rootCluster;
    private final long clusterBytes;
    private long pageStart = -1;
    private byte[] page = new byte[0];

    private Fat32(ByteSource disk, long base, byte[] boot) {
        this.disk = disk;
        this.base = base;
        bps = le16(boot, 11);
        spc = boot[13] & 0xFF;
        int rsvd = le16(boot, 14), fats = boot[16] & 0xFF;
        long fatSz = le32(boot, 36), total = le32(boot, 32);
        rootCluster = le32(boot, 44);
        fatAt = base + (long) rsvd * bps;
        dataAt = base + ((long) rsvd + (long) fats * fatSz) * bps;
        clusterBytes = (long) spc * bps;
        clusters = (total - rsvd - fats * fatSz) / spc;
    }

    /** Los archivos Data de la carpeta Xbox360, en orden; vacío si no hay FAT32 o no está la carpeta. */
    static List<ByteSource> xboxDataFiles(ByteSource disk, int sectorSize) throws IOException {
        List<Long> starts = new ArrayList<>();
        if (disk.length() < 1024) return new ArrayList<>();
        byte[] s0 = disk.read(0, 512);
        if (isFat32(s0)) starts.add(0L);
        if ((s0[510] & 0xFF) == 0x55 && (s0[511] & 0xFF) == 0xAA) {
            for (int i = 0; i < 4; i++) {
                int o = 446 + i * 16, type = s0[o + 4] & 0xFF;
                long lba = le32(s0, o + 8);
                if ((type == 0x0B || type == 0x0C || type == 0x07 || type == 0x0E) && lba > 0 && (lba + 1) * (long) sectorSize < disk.length()) {
                    starts.add(lba * sectorSize);
                }
            }
            // GPT: la primera partición de datos
            if (starts.isEmpty() && disk.length() > 2L * sectorSize) {
                byte[] g = disk.read(sectorSize, 512);
                if (new String(g, 0, 8, StandardCharsets.US_ASCII).equals("EFI PART")) {
                    long entLba = le32(g, 72) | (le32(g, 76) << 32);
                    int n = (int) Math.min(le32(g, 80), 16), sz = (int) le32(g, 84);
                    if (sz >= 128 && sz <= 512) {
                        byte[] t = disk.read(entLba * sectorSize, n * sz);
                        for (int i = 0; i < n; i++) {
                            long first = le32(t, i * sz + 32) | (le32(t, i * sz + 36) << 32);
                            if (first > 0 && (first + 1) * (long) sectorSize < disk.length()) starts.add(first * sectorSize);
                        }
                    }
                }
            }
        }
        for (long st : starts) {
            byte[] boot = disk.read(st, 512);
            if (!isFat32(boot)) continue;
            Fat32 f = new Fat32(disk, st, boot);
            List<ByteSource> files = f.findXbox360();
            if (!files.isEmpty()) return files;
        }
        return new ArrayList<>();
    }

    private static boolean isFat32(byte[] b) {
        return b.length >= 512 && new String(b, 82, 5, StandardCharsets.US_ASCII).equals("FAT32") && le16(b, 11) >= 512 && (b[13] & 0xFF) != 0;
    }

    private static final class Ent {
        String name;
        boolean dir;
        long cluster, size;
    }

    private List<ByteSource> findXbox360() throws IOException {
        long dir = -1;
        for (Ent e : list(rootCluster)) if (e.dir && e.name.equalsIgnoreCase("Xbox360")) dir = e.cluster;
        List<ByteSource> out = new ArrayList<>();
        if (dir < 0) return out;
        List<Ent> files = new ArrayList<>();
        for (Ent e : list(dir)) if (!e.dir && DATA.matcher(e.name).matches() && e.size > 0) files.add(e);
        files.sort(Comparator.comparing(e -> e.name.toLowerCase(java.util.Locale.ROOT)));
        for (Ent e : files) out.add(chainSource(e.cluster, e.size));
        return out;
    }

    // ------------------------------------------------------------------ tabla FAT y carpetas

    private long next(long c) throws IOException {
        long off = c * 4, ps = off & ~0xFFFFL;
        if (ps != pageStart) {
            page = disk.read(fatAt + ps, (int) Math.min(0x10000, disk.length() - (fatAt + ps)));
            pageStart = ps;
        }
        int i = (int) (off - ps);
        if (i + 4 > page.length) throw new IOException("Tabla FAT incompleta");
        return le32(page, i) & 0x0FFFFFFFL;
    }

    private List<long[]> runs(long first, long limitBytes) throws IOException {
        // {inicio virtual, posición en el disco, largo}
        List<long[]> runs = new ArrayList<>();
        long c = first, v = 0;
        int guard = 0;
        while (c >= 2 && c < clusters + 2 && v < limitBytes && guard++ < 50_000_000) {
            long pos = dataAt + (c - 2) * clusterBytes;
            long[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (last != null && last[1] + last[2] == pos) last[2] += clusterBytes;
            else runs.add(new long[]{v, pos, clusterBytes});
            v += clusterBytes;
            long n = next(c);
            if (n >= 0x0FFFFFF8L) break;
            c = n;
        }
        return runs;
    }

    private byte[] readChain(long first, int max) throws IOException {
        List<long[]> r = runs(first, max);
        long tot = 0;
        for (long[] x : r) tot += x[2];
        byte[] b = new byte[(int) Math.min(tot, max)];
        for (long[] x : r) {
            if (x[0] >= b.length) break;
            disk.read(x[1], b, (int) x[0], (int) Math.min(x[2], b.length - x[0]));
        }
        return b;
    }

    private List<Ent> list(long cluster) throws IOException {
        byte[] d = readChain(cluster, 1 << 20);
        List<Ent> out = new ArrayList<>();
        StringBuilder lfn = new StringBuilder();
        for (int i = 0; i + 32 <= d.length; i += 32) {
            int f = d[i] & 0xFF;
            if (f == 0) break;
            if (f == 0xE5) {
                lfn.setLength(0);
                continue;
            }
            int attr = d[i + 11] & 0xFF;
            if (attr == 0x0F) {
                int[] pos = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
                StringBuilder part = new StringBuilder();
                for (int p : pos) {
                    int ch = le16(d, i + p);
                    if (ch == 0 || ch == 0xFFFF) break;
                    part.append((char) ch);
                }
                if ((f & 0x40) != 0) lfn.setLength(0);
                lfn.insert(0, part);
                continue;
            }
            if ((attr & 8) != 0) {
                lfn.setLength(0);
                continue;
            }
            Ent e = new Ent();
            String sfn = new String(d, i, 8, StandardCharsets.ISO_8859_1).trim();
            String ext = new String(d, i + 8, 3, StandardCharsets.ISO_8859_1).trim();
            e.name = lfn.length() > 0 ? lfn.toString() : ext.isEmpty() ? sfn : sfn + "." + ext;
            lfn.setLength(0);
            e.dir = (attr & 0x10) != 0;
            e.cluster = le16(d, i + 26) | ((long) le16(d, i + 20) << 16);
            e.size = le32(d, i + 28);
            if (!e.name.equals(".") && !e.name.equals("..")) out.add(e);
        }
        return out;
    }

    private ByteSource chainSource(long first, long size) throws IOException {
        final List<long[]> runs = runs(first, size);
        long cover = 0;
        for (long[] r : runs) cover += r[2];
        if (cover < size) throw new IOException("La cadena de un archivo Data está incompleta");
        return new ByteSource() {
            @Override
            public long length() {
                return size;
            }

            @Override
            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > size) throw new IOException("Lectura fuera del archivo");
                int lo = 0, hi = runs.size() - 1;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (runs.get(mid)[0] <= off) lo = mid;
                    else hi = mid - 1;
                }
                int i = lo;
                while (len > 0) {
                    long[] r = runs.get(i);
                    long inRun = off - r[0];
                    int n = (int) Math.min(len, r[2] - inRun);
                    disk.read(r[1] + inRun, buf, boff, n);
                    off += n;
                    boff += n;
                    len -= n;
                    i++;
                }
            }

            @Override
            public boolean writable() {
                return disk.writable();
            }

            @Override
            public void write(long off, byte[] buf, int boff, int len) throws IOException {
                // se escribe dentro de un archivo que ya existe: no cambia la tabla de FAT32, solo sus datos
                if (off < 0 || off + len > size) throw new IOException("Escritura fuera del archivo");
                int lo = 0, hi = runs.size() - 1;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (runs.get(mid)[0] <= off) lo = mid;
                    else hi = mid - 1;
                }
                int i = lo;
                while (len > 0) {
                    long[] r = runs.get(i);
                    long inRun = off - r[0];
                    int n = (int) Math.min(len, r[2] - inRun);
                    disk.write(r[1] + inRun, buf, boff, n);
                    off += n;
                    boff += n;
                    len -= n;
                    i++;
                }
            }

            @Override
            public void flush() throws IOException {
                disk.flush();
            }
        };
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static long le32(byte[] b, int o) {
        return (le16(b, o) | ((long) le16(b, o + 2) << 16)) & 0xFFFFFFFFL;
    }
}
