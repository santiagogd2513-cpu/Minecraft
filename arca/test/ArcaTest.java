package com.forja.arca;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Prueba los lectores de la Xbox 360 con los discos de mentira que arma hacer_imagenes.py. */
public final class ArcaTest {
    private static int fails;

    /** Un archivo como fuente de bytes. */
    static ByteSource file(File f) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        return new ByteSource() {
            public long length() {
                try {
                    return raf.length();
                } catch (IOException e) {
                    return 0;
                }
            }

            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > raf.length()) throw new IOException("fuera del archivo");
                raf.seek(off);
                raf.readFully(buf, boff, len);
            }
        };
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  MAL ") + what);
        if (!ok) fails++;
    }

    static String md5(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("MD5").digest(b)) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void partition(String label, ByteSource part, String bigMd5, int bigSize) throws Exception {
        System.out.println("== " + label);
        check(Xtaf.isXtaf(part), "se reconoce como XTAF");
        Xtaf fs = new Xtaf(part);
        List<Xtaf.Entry> root = fs.listRoot();
        List<String> names = new ArrayList<>();
        for (Xtaf.Entry e : root) names.add(e.name + (e.dir ? "/" : ""));
        check(names.containsAll(List.of("name.txt", "grande.bin", "Content/", "muchos/")), "la raíz tiene lo esperado: " + names);
        Xtaf.Entry txt = fs.find("name.txt");
        check(txt != null && new String(fs.readFile(txt, 0, 100), StandardCharsets.UTF_8).equals("Hola desde la Xbox 360\n"), "lee un archivo chico");
        Xtaf.Entry big = fs.find("grande.bin");
        byte[] all = fs.readFile(big, 0, (int) big.size);
        check(big.size == bigSize && md5(all).equals(bigMd5), "lee un archivo grande completo (varios clústeres)");
        byte[] mid = fs.readFile(big, 30_000, 5_000);
        check(java.util.Arrays.equals(mid, java.util.Arrays.copyOfRange(all, 30_000, 35_000)), "lee un pedazo del medio");
        check(fs.find("content/0000000000000000/4d5307e6") != null, "busca rutas sin importar mayúsculas");
        check(fs.find("Content/NoExiste") == null, "una ruta que no existe da null");
        Xtaf.Entry many = fs.find("muchos");
        check(fs.list(many).size() == 700, "una carpeta con 700 archivos (varios clústeres) se lista completa");
        Xtaf.Entry empty = fs.find("Content/0000000000000000/4D5307E6/vacio");
        check(empty != null && empty.size == 0 && fs.readFile(empty, 0, 10).length == 0, "un archivo vacío");
        check(fs.find("Content").modified > 0, "las fechas se leen");
        // paquetes
        Xtaf.Entry save = fs.find("Content/0000000000000000/4D5307E6/00000001/SAVE0001");
        Stfs s = Stfs.parse(fs.readFile(save, 0, Stfs.HEADER));
        check(s != null && s.magic.equals("CON") && s.contentType == 1 && s.titleId == 0x4D5307E6L, "cabecera STFS: tipo y juego");
        check(s != null && s.titleName.equals("Gears of War") && s.displayName.equals("Partida 1"), "nombre del juego y de la partida");
        check(s != null && s.titleThumbnail.length > 8 && s.titleThumbnail[1] == 'P' && s.thumbnail.length > 8, "los dos íconos son PNG");
        Xtaf.Entry n2 = fs.find("Content/0000000000000000/41560817/00000001/GUARDADO");
        Stfs t = Stfs.parse(fs.readFile(n2, 0, Stfs.HEADER));
        check(t != null && t.titleName.equals("Ñandú Racing"), "acentos en el nombre del juego");
        Xtaf.Entry prof = fs.find("Content/E000123456789ABC/FFFE07D1/00010000/E000123456789ABC");
        Stfs p = Stfs.parse(fs.readFile(prof, 0, Stfs.HEADER));
        check(p != null && p.contentType == 0x10000 && p.magic.equals("PIRS") && Stfs.typeName(p.contentType).equals("Perfil"), "un perfil");
        check(Stfs.parse(fs.readFile(txt, 0, 100)) == null, "un archivo que no es paquete da null");
    }

    static byte[] readAll(StfsPackage p, StfsPackage.Item it) throws IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        p.copyTo(it, o, null);
        return o.toByteArray();
    }

    static StfsPackage.Item child(StfsPackage p, int parent, String name) {
        for (StfsPackage.Item it : p.children(parent)) if (it.name.equals(name)) return it;
        return null;
    }

    static void paquete(String label, File f, String aMd5, int aSize, String bMd5, int bSize) throws Exception {
        System.out.println("== paquete: " + label);
        StfsPackage p = StfsPackage.open(file(f));
        check(p.header.titleName.equals("Juego de prueba"), "la cabecera se lee");
        List<String> raiz = new ArrayList<>();
        for (StfsPackage.Item it : p.children(-1)) raiz.add(it.name + (it.dir ? "/" : ""));
        check(raiz.equals(List.of("leeme.txt", "datos/", "vacio.txt")), "lista la raíz: " + raiz);
        StfsPackage.Item datos = child(p, -1, "datos");
        check(datos != null && datos.dir && p.children(datos.index).size() == 2, "una carpeta con dos archivos");
        StfsPackage.Item leeme = child(p, -1, "leeme.txt");
        check(new String(readAll(p, leeme), StandardCharsets.UTF_8).equals("Archivo de prueba\n"), "lee un archivo chico");
        StfsPackage.Item ab = child(p, datos.index, "a.bin");
        StfsPackage.Item bb = child(p, datos.index, "b.bin");
        byte[] x = readAll(p, ab), y = readAll(p, bb);
        check(x.length == aSize && md5(x).equals(aMd5), "a.bin completo");
        check(y.length == bSize && md5(y).equals(bMd5), "b.bin completo (varios bloques)" + (bb.consecutive ? "" : ", no consecutivos"));
        check(java.util.Arrays.equals(p.read(bb, 100_000, 5000), java.util.Arrays.copyOfRange(y, 100_000, 105_000)), "un pedazo del medio");
        check(child(p, -1, "vacio.txt").size == 0, "un archivo vacío");
        check(leeme.modified > 0, "la fecha se lee");
        check(p.countBadBlocks(null) == 0, "todos los bloques pasan la prueba del SHA-1");
        StfsPackage.Diag dg = p.verify(null, null).diag;
        check(dg.allOk() && dg.header.equals("ok") && dg.top.equals("ok"), "la cabecera y la tabla de arriba coinciden: " + dg.header + " / " + dg.top + " / " + dg.level1);
        // un paquete dañado: se cambia un byte de un bloque de datos y debe detectarse
        ByteSource good = file(f);
        long corrupt = 0xB000L + 0x1000L * 5 + 77;
        ByteSource bad = new ByteSource() {
            public long length() {
                return good.length();
            }

            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                good.read(off, buf, boff, len);
                if (corrupt >= off && corrupt < off + len) buf[boff + (int) (corrupt - off)] ^= 0x55;
            }
        };
        StfsPackage q = StfsPackage.open(bad);
        StfsPackage.Result rv = q.verify(null, null);
        int expected = label.contains("dos copias") ? 3 : 4;   // con dos copias de cada tabla hay un bloque de tabla más al principio
        check(rv.bad == 1 && rv.badBlocks.contains(expected), "un byte cambiado se detecta, en el bloque " + expected + ": " + rv.badBlocks);
        check(rv.files.size() == 1 && rv.files.get(0).endsWith("a.bin"), "y dice qué archivo toca: " + rv.files);
        check(!p.verify(null, null).cancelled && p.verify(null, null).total == p.totalBlocks, "revisar un paquete sano no da errores");
    }

    /** La comprobación de la firma, con una llave RSA de mentira (la misma forma de ordenar bytes que se supone). */
    static void firma() throws Exception {
        System.out.println("== firma del paquete");
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(1024);
        java.security.interfaces.RSAPrivateKey priv = null;
        java.security.interfaces.RSAPublicKey pub = null;
        for (int i = 0; i < 20; i++) {
            java.security.KeyPair kp = g.generateKeyPair();
            pub = (java.security.interfaces.RSAPublicKey) kp.getPublic();
            priv = (java.security.interfaces.RSAPrivateKey) kp.getPrivate();
            if (pub.getPublicExponent().equals(java.math.BigInteger.valueOf(65537)) && pub.getModulus().bitLength() == 1024) break;
        }
        byte[] h = new byte[0x1000];
        new java.util.Random(7).nextBytes(h);
        h[0] = 'C'; h[1] = 'O'; h[2] = 'N'; h[3] = ' ';
        for (int i = 4; i < 0x60; i++) if (h[i] == 0 && h[i + 1] == 1) h[i] = 7;   // que no haya otro 00010001 antes
        h[0x2B] = 0; h[0x2C] = 1; h[0x2D] = 0; h[0x2E] = 1;
        byte[] mod = Firma.fijo(pub.getModulus(), 0x80);
        System.arraycopy(Firma.orden(mod, 2), 0, h, 0x2F, 0x80);
        byte[] hash = java.security.MessageDigest.getInstance("SHA-1").digest(java.util.Arrays.copyOfRange(h, Firma.SIGNED_FROM, Firma.SIGNED_TO));
        byte[] m = new byte[0x80];
        m[1] = 1;
        java.util.Arrays.fill(m, 2, 0x80 - 21, (byte) 0xFF);
        System.arraycopy(hash, 0, m, 0x80 - 20, 20);
        java.math.BigInteger sg = new java.math.BigInteger(1, m).modPow(priv.getPrivateExponent(), priv.getModulus());
        System.arraycopy(Firma.orden(Firma.fijo(sg, 0x80), 2), 0, h, Firma.SIG, 0x80);
        Firma f = Firma.analizar(h);
        check(f.estado == Firma.Estado.VALIDA, "una firma correcta se reconoce: " + f.estado + " " + f.receta);
        h[0x300] ^= 1;
        check(Firma.analizar(h).estado == Firma.Estado.NO_COINCIDE, "si cambia un byte firmado, ya no coincide");
        h[0x300] ^= 1;
        java.util.Arrays.fill(h, Firma.SIG, Firma.SIG + 0x80, (byte) 0);
        check(Firma.analizar(h).sinFirma, "una firma en ceros se detecta");
        h[0] = 'L';
        check(Firma.analizar(h).estado == Firma.Estado.NO_ES_CON, "los LIVE/PIRS no se firman");
    }

    /** Edita un paquete (cambia dos archivos), recalcula todo y comprueba que queda sano y con lo nuevo. */
    static void editar(String label, ByteSource src, String archivo, boolean conNivel2) throws Exception {
        System.out.println("== editar: " + label);
        StfsPackage p = StfsPackage.open(src);
        StfsEditor ed = StfsEditor.open(p);
        StfsPackage.Item a = null, b = null;
        for (StfsPackage.Item it : p.items) {
            if (it.name.equals(archivo)) a = it;
            if (it.name.equals("fin.txt") || it.name.equals("leeme.txt")) b = it;
        }
        byte[] before = readAll(p, a);
        byte[] nuevo = before.clone();
        for (int i = 0; i < nuevo.length; i += 97) nuevo[i] ^= 0x5A;       // mismo tamaño
        byte[] masChico = java.util.Arrays.copyOf(before, before.length - 7);   // un poco menos, mismos bloques
        boolean chico = ed.fits(a, masChico.length);
        check(ed.fits(a, nuevo.length), "el archivo nuevo cabe en los mismos bloques");
        ed.replace(a, nuevo);
        if (b != null && b.size > 0) {
            byte[] nb = new byte[(int) b.size];
            java.util.Arrays.fill(nb, (byte) 'Z');
            ed.replace(b, nb);
        }
        // otro tamaño de bloques: debe negarse
        boolean negado = false;
        try {
            ed.replace(a, new byte[nuevo.length + 5000]);
        } catch (IOException e) {
            negado = true;
        }
        check(negado, "si cambia la cantidad de bloques, se niega");
        ed.finish();
        ByteSource out = ed.view();
        StfsPackage q = StfsPackage.open(out);
        StfsPackage.Result r = q.verify(null, null);
        check(r.bad == 0, "después de editar, todos los bloques pasan el SHA-1");
        check(r.diag.header.equals("ok") && r.diag.top.equals("ok") && (r.diag.level1.equals("ok") || r.diag.level1.equals("no aplica") || r.diag.level1.startsWith("no comprobada")),
                "y la estructura queda bien: " + r.diag.header + " / " + r.diag.top + " / " + r.diag.level1);
        StfsPackage.Item a2 = null;
        for (StfsPackage.Item it : q.items) if (it.name.equals(archivo)) a2 = it;
        check(java.util.Arrays.equals(readAll(q, a2), nuevo), "el archivo editado tiene el contenido nuevo");
        // los demás archivos no cambian
        for (StfsPackage.Item it : p.items) {
            if (it.dir || it.name.equals(archivo) || it == b) continue;
            StfsPackage.Item m = null;
            for (StfsPackage.Item k : q.items) if (k.name.equals(it.name)) m = k;
            check(java.util.Arrays.equals(readAll(p, it), readAll(q, m)), "«" + it.name + "» sigue igual");
        }
        // se escribe a un archivo y se vuelve a leer
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        ed.writeTo(bo, null);
        check(bo.size() == src.length(), "el paquete resultante mide lo mismo");
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        String md5 = null;
        int size = 0;
        for (String l : Files.readAllLines(new File(dir, "esperado.txt").toPath())) {
            if (l.startsWith("archivo grande.bin")) {
                String[] p = l.split(" ");
                size = Integer.parseInt(p[2]);
                md5 = p[3];
            }
        }
        partition("partición con tabla de 16 bits", file(new File(dir, "particion16.img")), md5, size);
        partition("partición con tabla de 32 bits", file(new File(dir, "particion32.img")), md5, size);

        System.out.println("== disco duro de Xbox 360 (partición en 0x130EB0000)");
        ByteSource disk = file(new File(dir, "disco.img"));
        List<XboxDisk.Part> parts = XboxDisk.hdd(disk);
        check(parts.size() == 1 && parts.get(0).name.startsWith("Datos"), "encuentra la partición de datos: " + parts.size());
        if (!parts.isEmpty()) partition("dentro del disco", parts.get(0).source, md5, size);

        System.out.println("== memoria USB de la consola (archivos Data)");
        File[] files = new File(dir, "usb").listFiles((d, n) -> n.startsWith("Data"));
        java.util.Arrays.sort(files);
        List<ByteSource> srcs = new ArrayList<>();
        for (File f : files) srcs.add(file(f));
        List<XboxDisk.Part> usbParts = XboxDisk.usbStick(srcs);
        check(usbParts.size() == 1 && usbParts.get(0).name.startsWith("Datos"), "encuentra la partición de datos dentro de " + files.length + " archivos");
        if (!usbParts.isEmpty()) partition("dentro de la memoria USB", usbParts.get(0).source, md5, size);

        // paquetes por dentro
        String aMd5 = null, bMd5 = null, gMd5 = null, mMd5 = null;
        int aSize = 0, bSize = 0, gSize = 0, mSize = 0;
        for (String l : Files.readAllLines(new File(dir, "esperado.txt").toPath())) {
            String[] p = l.split(" ");
            if (l.startsWith("paq a.bin")) { aSize = Integer.parseInt(p[2]); aMd5 = p[3]; }
            if (l.startsWith("paq b.bin")) { bSize = Integer.parseInt(p[2]); bMd5 = p[3]; }
            if (l.startsWith("grande ")) { gSize = Integer.parseInt(p[1]); gMd5 = p[2]; }
            if (l.startsWith("medio ")) { mSize = Integer.parseInt(p[1]); mMd5 = p[2]; }
        }
        paquete("una tabla por grupo", new File(dir, "paquete_macho.stfs"), aMd5, aSize, bMd5, bSize);
        paquete("dos copias de cada tabla", new File(dir, "paquete_hembra.stfs"), aMd5, aSize, bMd5, bSize);
        paquete("archivos fragmentados", new File(dir, "paquete_fragmentado.stfs"), aMd5, aSize, bMd5, bSize);
        paquete("dos copias y fragmentado", new File(dir, "paquete_hembra_frag.stfs"), aMd5, aSize, bMd5, bSize);
        System.out.println("== paquete mediano (cruza varias tablas, dos copias)");
        StfsPackage med = StfsPackage.open(file(new File(dir, "paquete_medio.stfs")));
        StfsPackage.Item mi = child(med, -1, "medio.bin");
        byte[] mb = readAll(med, mi);
        check(mb.length == mSize && md5(mb).equals(mMd5), "medio.bin (" + mi.size / 4096 + " bloques) completo");
        check(med.countBadBlocks(null) == 0, "todos los bloques bien");
        StfsPackage.Diag dm = med.verify(null, null).diag;
        check(dm.header.equals("ok") && dm.top.equals("ok") && dm.level1.equals("ok"), "con tabla de nivel 1: " + dm.header + " / " + dm.top + " / " + dm.level1);
        System.out.println("== paquete grande (más de 28 900 bloques)");
        StfsPackage gr = StfsPackage.open(file(new File(dir, "paquete_grande.stfs")));
        StfsPackage.Item gi = child(gr, -1, "grande.bin");
        byte[] gb = readAll(gr, gi);
        check(gb.length == gSize && md5(gb).equals(gMd5), "grande.bin completo");
        check(new String(readAll(gr, child(gr, -1, "fin.txt"))).equals("fin"), "el último archivo, después de las tablas de segundo nivel");
        check(gr.countBadBlocks(null) == 0, "todos los bloques bien");
        StfsPackage.Diag dgr = gr.verify(null, null).diag;
        check(dgr.header.equals("ok") && dgr.top.equals("ok"), "con tabla de nivel 2: " + dgr.header + " / " + dgr.top + " / " + dgr.level1);

        editar("una tabla por grupo", file(new File(dir, "paquete_macho.stfs")), "a.bin", false);
        editar("dos copias de cada tabla", file(new File(dir, "paquete_hembra.stfs")), "a.bin", false);
        editar("fragmentado", file(new File(dir, "paquete_fragmentado.stfs")), "a.bin", false);
        editar("dos copias y fragmentado", file(new File(dir, "paquete_hembra_frag.stfs")), "a.bin", false);
        editar("con tabla de nivel 1 y dos copias", file(new File(dir, "paquete_medio.stfs")), "medio.bin", false);
        editar("con tabla de nivel 2", file(new File(dir, "paquete_grande.stfs")), "grande.bin", true);
        firma();

        System.out.println("== un paquete dentro de la partición de la consola");
        Xtaf fs0 = new Xtaf(file(new File(dir, "particion16.img")));
        Xtaf.Entry pe = fs0.find("Content/0000000000000000/4D5307E6/00000001/PAQUETE");
        StfsPackage dentro = StfsPackage.open(fs0.asSource(pe));
        StfsPackage.Item lm = child(dentro, -1, "leeme.txt");
        check(new String(readAll(dentro, lm)).equals("Dentro de la Xbox"), "se abre y se lee por dentro");
        java.io.ByteArrayOutputStream cp = new java.io.ByteArrayOutputStream();
        Xtaf.Entry gb2 = fs0.find("grande.bin");
        fs0.copyFile(gb2, cp, null);
        check(cp.size() == gb2.size && md5(cp.toByteArray()).equals(md5), "copiar un archivo de la consola da el mismo contenido");
        long tot = fs0.totalSize(fs0.find("Content"), null);
        check(tot > 40_000, "el tamaño de una carpeta se calcula: " + tot);

        System.out.println("== un disco que no es de Xbox");
        File junk = File.createTempFile("junk", ".img");
        try (RandomAccessFile r = new RandomAccessFile(junk, "rw")) {
            r.setLength(1 << 20);
        }
        check(XboxDisk.hdd(file(junk)).isEmpty() && !Xtaf.isXtaf(file(junk)), "se rechaza sin fallar");
        junk.delete();

        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
