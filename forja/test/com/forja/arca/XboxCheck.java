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
        base = Long.parseLong(a[1]);
        byte[] boot = rd(0, 512);
        int bps = le16(boot, 11), spc = boot[13] & 0xFF, rsvd = le16(boot, 14);
        long fatSz = le32(boot, 36);
        long dataStart = rsvd + 2 * fatSz;
        long cl = (long) spc * bps;
        // cadena de la raíz y de la carpeta
        List<long[]> found = new ArrayList<>();          // {cluster, size}
        long[] xbox = null;
        byte[] root = rd(dataStart * bps, (int) cl);
        for (int i = 0; i + 32 <= root.length && root[i] != 0; i += 32) {
            if ((root[i + 11] & 0xFF) == 0x0F || (root[i + 11] & 8) != 0) continue;
            if (new String(root, i, 11).equals("XBOX360    ")) xbox = new long[]{le16(root, i + 26) | ((long) le16(root, i + 20) << 16), 0, root[i + 11]};
        }
        check(xbox != null, "la carpeta Xbox360 está en la raíz");
        check(xbox != null && (xbox[2] & 0x12) == 0x12, "y es oculta");
        byte[] dir = rd((dataStart + (xbox[0] - 2) * spc) * bps, (int) cl);
        for (int i = 64; i + 32 <= dir.length && dir[i] != 0; i += 32) {
            if ((dir[i + 11] & 0xFF) == 0x0F) continue;
            String n = new String(dir, i, 8);
            if (!n.startsWith("DATA")) continue;
            found.add(new long[]{le16(dir, i + 26) | ((long) le16(dir, i + 20) << 16), le32(dir, i + 28)});
        }
        // nombres largos
        StringBuilder lfn = new StringBuilder();
        for (int i = 64; i + 32 <= dir.length && dir[i] != 0; i += 32) {
            if ((dir[i + 11] & 0xFF) == 0x0F) {
                int[] pos = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
                for (int p : pos) { int c = le16(dir, i + p); if (c == 0 || c == 0xFFFF) break; lfn.append((char) c); }
            }
        }
        check(lfn.toString().equals("Data0000" + (found.size() > 1 ? "Data0001" : "")) || lfn.toString().startsWith("Data"),
                "los archivos tienen nombre largo con mayúscula y minúscula: " + lfn);
        check(!found.isEmpty(), found.size() + " archivos Data");
        // la cadena de cada archivo en la FAT debe ser seguida y terminar donde dice el tamaño
        List<ByteSource> parts = new ArrayList<>();
        for (long[] d : found) {
            long clusters = (d[1] + cl - 1) / cl;
            long c = d[0];
            byte[] fat = rd((long) rsvd * bps + c * 4, 4);
            long v = le32(fat, 0) & 0x0FFFFFFFL;
            check(clusters == 1 || v == c + 1, "la cadena de un archivo sigue seguida");
            long lastC = c + clusters - 1;
            long lv = le32(rd((long) rsvd * bps + lastC * 4, 4), 0) & 0x0FFFFFFFL;
            check(lv >= 0x0FFFFFF8L, "y termina donde corresponde");
            long phys = base + (dataStart + (c - 2) * spc) * bps;
            final long fp = phys, sz = d[1];
            parts.add(new ByteSource() {
                public long length() { return sz; }
                public void read(long off, byte[] buf, int boff, int len) throws java.io.IOException {
                    f.seek(fp + off);
                    f.readFully(buf, boff, len);
                }
            });
        }
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
