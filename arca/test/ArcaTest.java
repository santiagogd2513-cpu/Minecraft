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
        XboxDisk.Part usb = XboxDisk.usbStick(srcs);
        check(usb != null, "encuentra la partición dentro de " + files.length + " archivos");
        if (usb != null) partition("dentro de la memoria USB", usb.source, md5, size);

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
