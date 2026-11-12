package com.forja.arca;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cambia el contenido de archivos dentro de un paquete STFS sin tocar el original: los cambios se anotan en una
 * capa por encima (por páginas de 4 KB) y al final se recalculan los SHA-1 de las tablas, de abajo hacia arriba,
 * hasta la cabecera. El resultado se puede leer como una fuente de bytes o escribir a un archivo del celular.
 * <p>
 * Por ahora cada archivo nuevo debe ocupar el mismo número de bloques que el viejo (puede ser un poco más
 * chico o más grande dentro del último bloque). Reacomodar bloques viene después, cuando se haya comprobado con
 * partidas reales que esto funciona. Y todavía no se firma: la consola no aceptará el resultado hasta que se firme.
 */
final class StfsEditor {
    private static final int PAGE = 0x1000;

    private final StfsPackage pkg;
    private final ByteSource orig;
    private final Map<Long, byte[]> overlay = new HashMap<>();
    private final TreeSet<Integer> dirtyBlocks = new TreeSet<>();
    private boolean finished;

    private StfsEditor(StfsPackage pkg) {
        this.pkg = pkg;
        this.orig = pkg.source();
    }

    /** Solo se edita un paquete sano: sin bloques dañados y con la estructura bien. */
    static StfsEditor open(StfsPackage pkg) throws IOException {
        StfsPackage.Result r = pkg.verify(null, null);
        if (r.bad > 0) throw new IOException("El paquete tiene " + r.bad + " bloques dañados: no lo edito para no empeorarlo");
        if (r.diag == null || !r.diag.header.equals("ok") || !r.diag.top.equals("ok") || r.diag.level1.startsWith("mal")) {
            throw new IOException("La estructura del paquete no es la que Arca entiende (" + (r.diag == null ? "?" : r.diag.header + " / " + r.diag.top + " / " + r.diag.level1) + "): no lo edito");
        }
        return new StfsEditor(pkg);
    }

    // ------------------------------------------------------------------ lectura con los cambios encima

    private byte[] page(long pageStart) throws IOException {
        byte[] o = overlay.get(pageStart);
        if (o != null) return o.clone();
        long len = Math.min(PAGE, orig.length() - pageStart);
        byte[] b = new byte[PAGE];
        if (len > 0) orig.read(pageStart, b, 0, (int) len);
        return b;
    }

    private void putPage(long pageStart, byte[] data) {
        overlay.put(pageStart, data);
    }

    /** El paquete con los cambios hechos (y, si ya se llamó a finish, con todo recalculado). */
    ByteSource view() {
        return new ByteSource() {
            @Override
            public long length() {
                return orig.length();
            }

            @Override
            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > orig.length()) throw new IOException("Lectura fuera del paquete");
                int done = 0;
                while (done < len) {
                    long pos = off + done;
                    long ps = pos / PAGE * PAGE;
                    int in = (int) (pos - ps);
                    int n = Math.min(len - done, PAGE - in);
                    byte[] o = overlay.get(ps);
                    if (o != null) System.arraycopy(o, in, buf, boff + done, n);
                    else orig.read(pos, buf, boff + done, n);
                    done += n;
                }
            }
        };
    }

    // ------------------------------------------------------------------ cambios

    /** Cuántos bloques ocupa un archivo de ese tamaño. */
    static int blocksFor(long size) {
        return (int) ((size + StfsPackage.BLOCK - 1) / StfsPackage.BLOCK);
    }

    /** ¿Cabe este contenido en los bloques que ya tiene el archivo? */
    boolean fits(StfsPackage.Item it, long newSize) throws IOException {
        return blocksFor(newSize) == pkg.blocksFor(it).length;
    }

    /** Reemplaza el contenido de un archivo (con la condición de que ocupe los mismos bloques). */
    void replace(StfsPackage.Item it, byte[] data) throws IOException {
        if (finished) throw new IOException("Ya se calcularon las tablas: no se pueden hacer más cambios");
        if (it.dir) throw new IOException("«" + it.name + "» es una carpeta");
        int[] bl = pkg.blocksFor(it);
        if (blocksFor(data.length) != bl.length) {
            throw new IOException("«" + it.name + "» ocupa " + bl.length + " bloques y el nuevo ocuparía " + blocksFor(data.length)
                    + ". Cambiar la cantidad de bloques todavía no se puede.");
        }
        for (int i = 0; i < bl.length; i++) {
            byte[] blk = new byte[PAGE];
            int from = i * PAGE, n = Math.min(PAGE, data.length - from);
            System.arraycopy(data, from, blk, 0, n);
            putPage(pkg.blockOffset(bl[i]), blk);
            dirtyBlocks.add(bl[i]);
        }
        // el tamaño exacto va en la entrada de la tabla de archivos
        long entryPage = pkg.blockOffset(it.tableBlock);
        byte[] t = page(entryPage);
        t[it.tableOffset + 0x34] = (byte) (data.length >>> 24);
        t[it.tableOffset + 0x35] = (byte) (data.length >>> 16);
        t[it.tableOffset + 0x36] = (byte) (data.length >>> 8);
        t[it.tableOffset + 0x37] = (byte) data.length;
        putPage(entryPage, t);
        dirtyBlocks.add(it.tableBlock);
    }

    // ------------------------------------------------------------------ recalcular las tablas

    private static byte[] sha1(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean sameHash(long offset, byte[] expected) throws IOException {
        return offset + PAGE <= orig.length() && Arrays.equals(sha1(orig.read(offset, PAGE)), expected);
    }

    /**
     * La copia (0 o 1) de una tabla que es la buena, según el SHA-1 que dice la tabla de arriba. Se mira en el
     * paquete original, antes de cambiar nada.
     */
    private int activeCopy(java.util.function.IntFunction<Long> offsetOf, byte[] expected, String what) throws IOException {
        for (int c = 0; c <= pkg.sex(); c++) {
            if (sameHash(offsetOf.apply(c), expected)) return c;
        }
        throw new IOException("No encuentro la copia buena de " + what + ": el paquete no está como Arca espera");
    }

    /** Calcula todo lo que depende de lo cambiado: tablas de nivel 0, 1 y 2, el descriptor de volumen y el SHA-1 de la cabecera. */
    void finish() throws IOException {
        if (finished) return;
        if (dirtyBlocks.isEmpty()) {
            finished = true;
            return;
        }
        byte[] head = orig.read(0, 0x400);
        byte[] descriptorHash = Arrays.copyOfRange(head, 0x381, 0x395);
        int level = pkg.level();
        int groupCount = pkg.groups();

        // 1) las copias buenas, mirando el paquete original de arriba hacia abajo
        int topCopy = activeCopy(c -> level == 0 ? pkg.tableOffsetL0(0, c) : level == 1 ? pkg.tableOffsetL1(0, c) : pkg.tableOffsetL2(c),
                descriptorHash, "la tabla principal");
        long topOffset = level == 0 ? pkg.tableOffsetL0(0, topCopy) : level == 1 ? pkg.tableOffsetL1(0, topCopy) : pkg.tableOffsetL2(topCopy);
        int n1 = (groupCount + 169) / 170;
        int[] l1Copy = new int[Math.max(n1, 1)];
        int[] l0Copy = new int[groupCount];
        if (level == 0) {
            l0Copy[0] = topCopy;
        } else if (level == 1) {
            l1Copy[0] = topCopy;
            for (int g = 0; g < groupCount; g++) {
                byte[] e = orig.read(topOffset + (long) g * 0x18, 0x18);
                final int gg = g;
                l0Copy[g] = activeCopy(c -> pkg.tableOffsetL0(gg, c), Arrays.copyOf(e, 20), "la tabla " + g);
            }
        } else {
            for (int k = 0; k < n1; k++) {
                byte[] e = orig.read(topOffset + (long) k * 0x18, 0x18);
                final int kk = k;
                l1Copy[k] = activeCopy(c -> pkg.tableOffsetL1(kk, c), Arrays.copyOf(e, 20), "la tabla de nivel 1 número " + k);
            }
            for (int g = 0; g < groupCount; g++) {
                int k = g / 170;
                byte[] e = orig.read(pkg.tableOffsetL1(k, l1Copy[k]) + (long) (g % 170) * 0x18, 0x18);
                final int gg = g;
                l0Copy[g] = activeCopy(c -> pkg.tableOffsetL0(gg, c), Arrays.copyOf(e, 20), "la tabla " + g);
            }
        }

        // 2) nivel 0: el SHA-1 de cada bloque cambiado
        Map<Integer, TreeSet<Integer>> byGroup = new TreeMap<>();
        for (int n : dirtyBlocks) byGroup.computeIfAbsent(n / 170, k -> new TreeSet<>()).add(n);
        TreeSet<Integer> dirtyL1 = new TreeSet<>();
        for (Map.Entry<Integer, TreeSet<Integer>> e : byGroup.entrySet()) {
            int g = e.getKey();
            long tOff = pkg.tableOffsetL0(g, l0Copy[g]);
            byte[] t = page(tOff);
            for (int n : e.getValue()) {
                byte[] blk = page(pkg.blockOffset(n));
                System.arraycopy(sha1(blk), 0, t, (n % 170) * 0x18, 20);
            }
            putPage(tOff, t);
            dirtyL1.add(g / 170);
        }
        // 3) nivel 1: el SHA-1 de cada tabla de nivel 0 cambiada
        byte[] topTable = null;
        if (level >= 1) {
            for (int g : byGroup.keySet()) {
                int k = g / 170;
                long l1Off = pkg.tableOffsetL1(k, l1Copy[k]);
                byte[] t = page(l1Off);
                System.arraycopy(sha1(page(pkg.tableOffsetL0(g, l0Copy[g]))), 0, t, (g % 170) * 0x18, 20);
                putPage(l1Off, t);
            }
            if (level == 1) {
                topTable = page(pkg.tableOffsetL1(0, l1Copy[0]));
            } else {
                long l2Off = pkg.tableOffsetL2(topCopy);
                byte[] t = page(l2Off);
                for (int k : dirtyL1) System.arraycopy(sha1(page(pkg.tableOffsetL1(k, l1Copy[k]))), 0, t, k * 0x18, 20);
                putPage(l2Off, t);
                topTable = t;
            }
        } else {
            topTable = page(pkg.tableOffsetL0(0, l0Copy[0]));
        }
        // 4) el descriptor de volumen guarda el SHA-1 de la tabla principal
        byte[] p0 = page(0);
        System.arraycopy(sha1(topTable), 0, p0, 0x381, 20);
        putPage(0, p0);
        // 5) el SHA-1 de la cabecera: de 0x344 hasta la primera tabla
        long first = pkg.firstHash();
        byte[] hdr = new byte[(int) (first - 0x344)];
        ByteSource v = view();
        v.read(0x344, hdr, 0, hdr.length);
        byte[] p0b = page(0);
        System.arraycopy(sha1(hdr), 0, p0b, 0x32C, 20);
        putPage(0, p0b);
        finished = true;
    }

    /** Escribe el paquete resultante a una salida. */
    void writeTo(OutputStream out, java.util.function.LongConsumer progress) throws IOException {
        finish();
        ByteSource v = view();
        byte[] buf = new byte[1 << 20];
        long pos = 0, len = v.length();
        while (pos < len) {
            int n = (int) Math.min(buf.length, len - pos);
            v.read(pos, buf, 0, n);
            out.write(buf, 0, n);
            pos += n;
            if (progress != null) progress.accept(pos);
        }
    }
}
