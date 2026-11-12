package com.forja.arca;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Revisa una memoria de mentira formateada por Yunque para la Xbox 360: lee la unidad FAT32 con un
 * lector propio, saca los archivos Data0000… de la carpeta Xbox360 y abre la partición FATX con el
 * mismo lector que usa Arca. Uso: XboxCheck imagen desplazamientoDeLaParticion
 */
public class XboxCheck {
    static RandomAccessFile f;
    static long base;

    static byte[] rd(long off, int n) throws Exception {
        byte[] b = new byte[n];
        f.seek(base + off);
        f.readFully(b);
        return b;
    }

    static int le16(byte[] b, int o) { return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8); }
    static long le32(byte[] b, int o) { return (le16(b, o) | ((long) le16(b, o + 2) << 16)) & 0xFFFFFFFFL; }

    static int fails = 0;
    static void check(boolean ok, String m) { System.out.println((ok ? "  ok  " : "  MAL ") + m); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        f = new RandomAccessFile(a[0], "r");
        final long fl = f.length();
        ByteSource disk = new ByteSource() {
            public long length() { return fl; }
            public void read(long off, byte[] buf, int boff, int len) throws java.io.IOException {
                f.seek(off);
                f.readFully(buf, boff, len);
            }
        };
        List<ByteSource> parts = Fat32.xboxDataFiles(disk, Integer.parseInt(a[2]));
        check(!parts.isEmpty(), "Arca encuentra la carpeta Xbox360 y sus archivos Data: " + parts.size());
        for (ByteSource p : parts) check(p.length() > 0 && p.length() <= 0x7FFF0000L, "un archivo Data de " + (p.length() >> 20) + " MB");
        long tot = 0;
        for (ByteSource p : parts) tot += p.length();
        System.out.println("   datos en total: " + (tot >> 20) + " MB");
        List<XboxDisk.Part> ps = XboxDisk.usbStick(parts);
        check(!ps.isEmpty(), "Arca encuentra la partición de la consola: " + ps.size());
        Xtaf x = new Xtaf(ps.get(0).source);
        check(x.listRoot().isEmpty(), "la carpeta raíz de la consola está vacía y se lee");
        check(Xtaf.isXtaf(ps.get(0).source), "es XTAF");
        System.out.println(fails == 0 ? "XBOX BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
