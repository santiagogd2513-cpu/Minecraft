package com.forja.arca;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lo que hay ADENTRO de un paquete de la Xbox 360 (STFS): las carpetas y archivos de una partida guardada,
 * un perfil o una descarga. Los datos están en bloques de 4 KB, y cada 170 bloques hay una tabla con el
 * SHA-1 de cada bloque y cuál es el siguiente. Se lee, no se escribe.
 * <p>
 * Hay dos tipos de paquete: los de una sola tabla por grupo y los de dos copias (una activa y otra de
 * respaldo). Como no siempre se sabe cuál es cuál por los indicadores, se comprueba con el SHA-1 de los
 * propios bloques, que no miente.
 */
final class StfsPackage {
    static final int BLOCK = 0x1000;
    private static final int PER_TABLE = 0xAA;            // 170 bloques por tabla
    private static final int PER_TABLE2 = 0x70E4;         // 28 900 bloques por tabla de segundo nivel

    static final class Item {
        String name = "";
        boolean dir, consecutive;
        int start, parent = -1, index;
        long size, modified;
        int[] blocks;        // se calcula al leer
    }

    private final ByteSource src;
    private final long firstHash;
    private int sex;                  // 0 una tabla, 1 dos copias
    final int totalBlocks;
    final Stfs header;
    final List<Item> items = new ArrayList<>();

    private StfsPackage(ByteSource src, Stfs header, long firstHash, int totalBlocks, int sex) {
        this.src = src;
        this.header = header;
        this.firstHash = firstHash;
        this.totalBlocks = totalBlocks;
        this.sex = sex;
    }

    static StfsPackage open(ByteSource src) throws IOException {
        if (src.length() < 0xB000) throw new IOException("El paquete es muy chico: está incompleto o dañado");
        byte[] h = src.read(0, 0x400);
        Stfs head = Stfs.parse(src.read(0, (int) Math.min(src.length(), Stfs.HEADER)));
        if (head == null) throw new IOException("Esto no es un paquete de la Xbox 360");
        long headerSize = be32(h, 0x340) & 0xFFFFFFFFL;
        long firstHash = (headerSize + 0xFFF) & 0xF000L;
        if (headerSize < 0x971A || firstHash > src.length() - BLOCK) throw new IOException("La cabecera del paquete está dañada");
        if ((h[0x379] & 0xFF) != 0x24) throw new IOException("El paquete no tiene un descriptor de volumen válido (¿es de otro tipo?)");
        int sep = h[0x37B] & 0xFF;
        int ftCount = (h[0x37C] & 0xFF) | ((h[0x37D] & 0xFF) << 8);
        int ftBlock = (h[0x37E] & 0xFF) | ((h[0x37F] & 0xFF) << 8) | ((h[0x380] & 0xFF) << 16);
        int total = be32(h, 0x395);
        if (total < 0 || total > 0xFFFFFF) throw new IOException("El número de bloques del paquete no es válido");
        int guess = ((~sep) & 1);
        StfsPackage p = new StfsPackage(src, head, firstHash, total, guess);
        // se prueba cuál de los dos tipos acierta con el SHA-1 del primer bloque de la tabla de archivos
        int best = guess;
        for (int s : new int[]{guess, 1 - guess}) {
            p.sex = s;
            if (p.validates(ftBlock)) {
                best = s;
                break;
            }
        }
        p.sex = best;
        p.readFileTable(ftBlock, ftCount);
        return p;
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    // ------------------------------------------------------------------ dónde está cada bloque

    /** Número del bloque de datos dentro del archivo, contando también las tablas que hay en medio. */
    private long backingData(long n) {
        long r = ((n + PER_TABLE) / PER_TABLE << sex) + n;
        if (n < PER_TABLE) return r;
        r += ((n + PER_TABLE2) / PER_TABLE2) << sex;
        if (n < PER_TABLE2) return r;
        return r + (1L << sex);
    }

    /** Lo mismo, para la tabla de 170 bloques a la que pertenece el bloque n. */
    private long backingTable(long n) {
        if (n < PER_TABLE) return 0;
        long r = (n / PER_TABLE) * (sex == 1 ? 0xAC : 0xAB);
        r += ((n / PER_TABLE2) + 1) << sex;
        if (n / PER_TABLE2 == 0) return r;
        return r + (1L << sex);
    }

    private long dataOffset(long n) {
        return (backingData(n) << 12) + firstHash;
    }

    private long tableOffset(long n, int copy) {
        return (backingTable(n) << 12) + firstHash + ((long) copy << 12);
    }

    byte[] readBlock(int n) throws IOException {
        long at = dataOffset(n);
        if (n < 0 || at + BLOCK > src.length()) throw new IOException("Falta un pedazo del paquete (bloque " + n + "): está incompleto o dañado");
        return src.read(at, BLOCK);
    }

    private static byte[] sha1(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] hashEntry(int n, int copy) throws IOException {
        long at = tableOffset(n, copy) + (long) (n % PER_TABLE) * 0x18;
        if (at + 0x18 > src.length()) throw new IOException("Falta una tabla del paquete: está incompleto o dañado");
        return src.read(at, 0x18);
    }

    private boolean validates(int n) {
        try {
            byte[] h = sha1(readBlock(n));
            for (int c = 0; c <= sex; c++) {
                if (Arrays.equals(h, Arrays.copyOf(hashEntry(n, c), 20))) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /** El bloque que sigue al n en su archivo (0xFFFFFF si es el último). */
    int next(int n) throws IOException {
        byte[] h = sha1(readBlock(n));
        int plausible = -2;
        for (int c = 0; c <= sex; c++) {
            byte[] e = hashEntry(n, c);
            int nx = ((e[21] & 0xFF) << 16) | ((e[22] & 0xFF) << 8) | (e[23] & 0xFF);
            if (Arrays.equals(h, Arrays.copyOf(e, 20))) return nx;
            if (plausible == -2 && (nx == 0xFFFFFF || nx < totalBlocks)) plausible = nx;
        }
        if (plausible != -2) return plausible;
        throw new IOException("La cadena de bloques del paquete está dañada (bloque " + n + ")");
    }

    /** ¿El bloque n pasa la prueba del SHA-1? Sirve para verificar un paquete. */
    boolean blockIsValid(int n) {
        return validates(n);
    }

    // ------------------------------------------------------------------ archivos y carpetas

    private void readFileTable(int firstBlock, int count) throws IOException {
        int b = firstBlock;
        for (int i = 0; i < Math.max(1, count) && b != 0xFFFFFF; i++) {
            byte[] blk = readBlock(b);
            boolean end = false;
            for (int o = 0; o + 0x40 <= BLOCK; o += 0x40) {
                int flags = blk[o + 0x28] & 0xFF;
                int nameLen = flags & 0x3F;
                if (nameLen == 0) {
                    end = true;
                    break;
                }
                Item it = new Item();
                it.index = items.size();
                it.name = new String(blk, o, Math.min(nameLen, 0x28), java.nio.charset.StandardCharsets.ISO_8859_1);
                it.consecutive = (flags & 0x40) != 0;
                it.dir = (flags & 0x80) != 0;
                it.start = (blk[o + 0x2F] & 0xFF) | ((blk[o + 0x30] & 0xFF) << 8) | ((blk[o + 0x31] & 0xFF) << 16);
                int path = ((blk[o + 0x32] & 0xFF) << 8) | (blk[o + 0x33] & 0xFF);
                it.parent = path == 0xFFFF ? -1 : path;
                it.size = be32(blk, o + 0x34) & 0xFFFFFFFFL;
                it.modified = dosTime(be32(blk, o + 0x38));
                items.add(it);
            }
            if (end) break;
            b = next(b);
        }
    }

    List<Item> children(int parent) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.parent == parent) out.add(it);
        return out;
    }

    private int[] blocksOf(Item it) throws IOException {
        if (it.blocks != null) return it.blocks;
        int need = (int) ((it.size + BLOCK - 1) / BLOCK);
        int[] r = new int[need];
        int b = it.start;
        for (int i = 0; i < need; i++) {
            if (b < 0 || b >= 0xFFFFFF) throw new IOException("Los bloques de «" + it.name + "» están dañados");
            r[i] = b;
            if (i + 1 < need) b = it.consecutive ? b + 1 : next(b);
            if (b == 0xFFFFFF) throw new IOException("El archivo «" + it.name + "» está incompleto o dañado");
        }
        it.blocks = r;
        return r;
    }

    byte[] read(Item it, long off, int len) throws IOException {
        if (it.dir) throw new IOException("Es una carpeta");
        if (off >= it.size) return new byte[0];
        len = (int) Math.min(len, it.size - off);
        int[] bl = blocksOf(it);
        byte[] out = new byte[len];
        int done = 0;
        while (done < len) {
            long pos = off + done;
            int idx = (int) (pos / BLOCK);
            int inBlock = (int) (pos % BLOCK);
            byte[] blk = readBlock(bl[idx]);
            int n = Math.min(len - done, BLOCK - inBlock);
            System.arraycopy(blk, inBlock, out, done, n);
            done += n;
        }
        return out;
    }

    void copyTo(Item it, OutputStream out, java.util.function.LongConsumer progress) throws IOException {
        int[] bl = blocksOf(it);
        long left = it.size, done = 0;
        for (int b : bl) {
            byte[] blk = readBlock(b);
            int n = (int) Math.min(BLOCK, left);
            out.write(blk, 0, n);
            left -= n;
            done += n;
            if (progress != null) progress.accept(done);
        }
    }

    /** El resultado de revisar un paquete. */
    static final class Result {
        int total, bad;
        boolean cancelled;
        final List<Integer> badBlocks = new ArrayList<>();
        final List<String> files = new ArrayList<>();
    }

    private String pathOf(Item it) {
        StringBuilder b = new StringBuilder(it.name);
        int i = it.parent, guard = 0;
        while (i >= 0 && i < items.size() && guard++ < 40) {
            b.insert(0, items.get(i).name + "/");
            i = items.get(i).parent;
        }
        return b.toString();
    }

    /**
     * Revisa el SHA-1 de todos los bloques del paquete y dice cuáles están mal y qué archivos tocan.
     * Esto comprueba que los datos no estén corruptos; no comprueba la firma de la consola.
     */
    Result verify(java.util.concurrent.atomic.AtomicBoolean stop, java.util.function.IntConsumer progress) {
        Result r = new Result();
        r.total = totalBlocks;
        for (int n = 0; n < totalBlocks; n++) {
            if (stop != null && stop.get()) {
                r.cancelled = true;
                break;
            }
            if (!validates(n)) {
                r.bad++;
                if (r.badBlocks.size() < 500) r.badBlocks.add(n);
            }
            if (progress != null && n % 400 == 0) progress.accept(n);
        }
        if (r.bad > 0) {
            java.util.Set<Integer> bs = new java.util.HashSet<>(r.badBlocks);
            for (Item it : items) {
                if (it.dir) continue;
                try {
                    for (int b : blocksOf(it)) {
                        if (bs.contains(b)) {
                            r.files.add(pathOf(it));
                            break;
                        }
                    }
                } catch (IOException e) {
                    r.files.add(pathOf(it) + " (su cadena de bloques está dañada)");
                }
            }
        }
        return r;
    }

    int countBadBlocks(java.util.concurrent.atomic.AtomicBoolean stop) {
        return verify(stop, null).bad;
    }

    private static long dosTime(int ts) {
        int d = ts >>> 16, t = ts & 0xFFFF;
        int year = 1980 + (d >> 9), month = (d >> 5) & 0xF, day = d & 0x1F;
        int hour = t >> 11, min = (t >> 5) & 0x3F, sec = (t & 0x1F) * 2;
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || min > 59 || sec > 59) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, hour, min, sec);
        return c.getTimeInMillis();
    }
}
