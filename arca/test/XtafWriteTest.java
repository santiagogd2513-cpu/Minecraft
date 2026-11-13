package com.forja.arca;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Prueba la escritura en XTAF con discos de mentira: crea carpetas y archivos y los vuelve a leer con el lector de siempre. */
public final class XtafWriteTest {
    private static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  MAL ") + what);
        if (!ok) fails++;
    }

    /** Un disco de mentira en un archivo (disperso: no ocupa lo que dice). */
    static final class FileDisk implements ByteSource {
        final RandomAccessFile raf;
        final long size;

        FileDisk(File f, long size) throws IOException {
            raf = new RandomAccessFile(f, "rw");
            raf.setLength(size);
            this.size = size;
        }

        public long length() {
            return size;
        }

        public boolean writable() {
            return true;
        }

        public void read(long off, byte[] buf, int boff, int len) throws IOException {
            if (off < 0 || off + len > size) throw new IOException("fuera del disco");
            raf.seek(off);
            raf.readFully(buf, boff, len);
        }

        public void write(long off, byte[] buf, int boff, int len) throws IOException {
            if (off < 0 || off + len > size) throw new IOException("fuera del disco");
            raf.seek(off);
            raf.write(buf, boff, len);
        }
    }

    /** Lo que hace «Dar formato» a mano: marca, tabla en 0x1000 y la carpeta raíz (clúster 1) rellena de 0xFF. */
    static void format(FileDisk d, int spc) throws IOException {
        int cb = spc * 512;
        long entries = d.size / cb + 1;
        boolean f32 = entries >= 0xFFF0;
        long fatBytes = (entries * (f32 ? 4 : 2) + 0xFFF) & ~0xFFFL;
        byte[] h = new byte[16];
        h[0] = 'X'; h[1] = 'T'; h[2] = 'A'; h[3] = 'F';
        h[7] = 7;
        h[11] = (byte) spc;
        h[15] = 1;
        d.write(0, h, 0, 16);
        byte[] root = new byte[cb];
        java.util.Arrays.fill(root, (byte) 0xFF);
        long dataAt = 0x1000 + fatBytes;
        d.write(dataAt, root, 0, cb);
        byte[] fe = f32 ? new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF} : new byte[]{(byte) 0xFF, (byte) 0xFF};
        d.write(0x1000L + (f32 ? 4 : 2), fe, 0, fe.length);
    }

    static byte[] rnd(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    static byte[] read(Xtaf fs, Xtaf.Entry e) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        fs.copyFile(e, o, null);
        return o.toByteArray();
    }

    static void run(String label, File f, long size, int spc) throws Exception {
        System.out.println("== " + label);
        FileDisk d = new FileDisk(f, size);
        format(d, spc);
        check(Xtaf.isXtaf(d), "el disco nuevo se reconoce como XTAF");
        Xtaf fs = new Xtaf(d);
        check(fs.listRoot().isEmpty(), "empieza vacío");

        int dir = fs.ensureDirs("Content/0000000000000000/545408A7/00000001");
        check(fs.find("Content/0000000000000000/545408A7/00000001") != null, "se crearon las carpetas de la ruta");
        check(fs.ensureDirs("content/0000000000000000/545408a7/00000001") == dir, "pedir la misma ruta otra vez no duplica nada");
        check(fs.listRoot().size() == 1, "sigue habiendo una sola carpeta en la raíz");

        int cb = fs.clusterBytes;
        int[] sizes = {0, 1, 100, cb - 1, cb, cb + 1, 3 * cb, 5 * 1024 * 1024 + 123};
        List<byte[]> data = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            byte[] b = rnd(sizes[i], i);
            data.add(b);
            fs.createFile(dir, "archivo" + i + ".bin", new ByteArrayInputStream(b), b.length, null);
        }
        for (int i = 0; i < sizes.length; i++) {
            Xtaf.Entry e = fs.find("Content/0000000000000000/545408A7/00000001/archivo" + i + ".bin");
            check(e != null && e.size == sizes[i] && java.util.Arrays.equals(read(fs, e), data.get(i)),
                    "archivo de " + sizes[i] + " bytes: se lee igual que se escribió");
        }

        // una carpeta con muchos archivos fuerza a que la carpeta crezca más de un clúster
        int many = fs.mkdir(dir, "muchos");
        int n = cb / 64 + 40;
        for (int i = 0; i < n; i++) fs.createFile(many, "f" + i, new ByteArrayInputStream(new byte[]{(byte) i}), 1, null);
        check(fs.list(many, "").size() == n, "carpeta con " + n + " archivos (más de un clúster de carpeta)");
        Xtaf.Entry last = fs.find("Content/0000000000000000/545408A7/00000001/muchos/f" + (n - 1));
        check(last != null && read(fs, last)[0] == (byte) (n - 1), "el último de la carpeta grande se lee bien");

        // nombres repetidos y no válidos
        boolean dup = false;
        try {
            fs.createFile(dir, "ARCHIVO2.bin", new ByteArrayInputStream(new byte[1]), 1, null);
        } catch (IOException e) {
            dup = true;
        }
        check(dup, "crear un nombre repetido (aunque cambien mayúsculas) da error");
        boolean bad = false;
        try {
            fs.mkdir(dir, "a:b");
        } catch (IOException e) {
            bad = true;
        }
        check(bad, "un nombre con «:» se rechaza");
        boolean longName = false;
        try {
            fs.mkdir(dir, "x".repeat(43));
        } catch (IOException e) {
            longName = true;
        }
        check(longName, "un nombre de 43 letras se rechaza");

        // renombrar y borrar
        Xtaf.Entry ren = fs.find("Content/0000000000000000/545408A7/00000001/archivo2.bin");
        fs.rename(ren, "NuevoNombre.bin");
        check(fs.find("Content/0000000000000000/545408A7/00000001/archivo2.bin") == null
                && java.util.Arrays.equals(read(fs, fs.find("Content/0000000000000000/545408A7/00000001/NuevoNombre.bin")), data.get(2)),
                "renombrar conserva el contenido");
        long before = fs.totalSize(fs.find("Content"), null);
        Xtaf.Entry big = fs.find("Content/0000000000000000/545408A7/00000001/archivo7.bin");
        fs.delete(big);
        check(fs.find("Content/0000000000000000/545408A7/00000001/archivo7.bin") == null, "un archivo borrado ya no aparece");
        check(fs.totalSize(fs.find("Content"), null) == before - sizes[7], "al borrar baja el peso total");
        Xtaf.Entry again = fs.createFile(dir, "archivo7.bin", new ByteArrayInputStream(data.get(7)), data.get(7).length, null);
        check(java.util.Arrays.equals(read(fs, fs.find("Content/0000000000000000/545408A7/00000001/archivo7.bin")), data.get(7)),
                "se puede volver a crear lo borrado (reusa lo liberado)");
        fs.delete(fs.find("Content/0000000000000000/545408A7/00000001/muchos"));
        check(fs.find("Content/0000000000000000/545408A7/00000001/muchos") == null, "borrar una carpeta se lleva todo lo de adentro");

        // otro lector, desde cero, ve lo mismo (no depende de lo guardado en memoria)
        Xtaf fresh = new Xtaf(d);
        check(fresh.find("Content/0000000000000000/545408A7/00000001/archivo5.bin") != null, "un lector nuevo ve lo escrito");

        // sin espacio: no deja nada a medias
        long free = size - 0x1000 - 0x100000;
        boolean full = false;
        try {
            fs.createFile(dir, "enorme.bin", new java.io.InputStream() {
                public int read() {
                    return 0;
                }

                public int read(byte[] b, int o, int l) {
                    return l;
                }
            }, size * 2L > 0xFFFFFFFFL ? 0xFFFFFFFFL : size * 2L, null);
        } catch (IOException e) {
            full = true;
        }
        check(full, "un archivo que no cabe da error");
        check(fs.find("Content/0000000000000000/545408A7/00000001/enorme.bin") == null, "y no deja el archivo a medias");
        check(java.util.Arrays.equals(read(fs, fs.find("Content/0000000000000000/545408A7/00000001/archivo5.bin")), data.get(5)),
                "lo que había antes sigue intacto");

        // solo lectura
        ByteSource ro = new ByteSource() {
            public long length() {
                return d.length();
            }

            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                d.read(off, buf, boff, len);
            }
        };
        boolean refused = false;
        try {
            new Xtaf(ro).mkdir(1, "no");
        } catch (IOException e) {
            refused = true;
        }
        check(refused, "una partición abierta solo para leer rechaza escribir");
        d.raf.close();
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        dir.mkdirs();
        run("tabla de 2 bytes (clústeres de 16 KB, 64 MB)", new File(dir, "w16.img"), 64L << 20, 32);
        run("tabla de 4 bytes (clústeres de 512 B, 40 MB)", new File(dir, "w32.img"), 40L << 20, 1);
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLÓ: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
