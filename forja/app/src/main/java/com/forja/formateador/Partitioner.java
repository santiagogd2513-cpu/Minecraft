package com.forja.formateador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.zip.CRC32;

/**
 * Crea una tabla de particiones nueva con una sola partición que ocupa el disco, alineada a
 * 1 MiB: MBR (hasta 2 TB con sectores de 512 B) o GPT (sin límite práctico).
 */
final class Partitioner {
    static final long ALIGN = 1024 * 1024;

    /** Dónde quedó la partición, en bytes. */
    static final class Part {
        long start, size;
    }

    private Partitioner() {
    }

    /** Tamaño máximo que puede manejar MBR en este disco. */
    static long mbrLimit(int sectorSize) {
        return 0xFFFFFFFFL * sectorSize;
    }

    static Part mbr(Io io, int type, SecureRandom rnd) throws IOException {
        int ss = io.ss;
        long sectors = io.d.sectors();
        long start = ALIGN / ss;
        long end = Math.min(sectors, 0xFFFFFFFFL);   // MBR: 32 bits de sectores
        long count = end - start;
        byte[] s0 = new byte[ss];
        byte[] sig = new byte[4];
        rnd.nextBytes(sig);
        System.arraycopy(sig, 0, s0, 440, 4);
        int o = 446;
        s0[o] = 0x00;                           // no activa (es un disco de datos)
        chs(s0, o + 1, start);
        s0[o + 4] = (byte) type;
        chs(s0, o + 5, start + count - 1);
        Io.put32(s0, o + 8, start);
        Io.put32(s0, o + 12, count);
        s0[510] = 0x55;
        s0[511] = (byte) 0xAA;
        io.write(0, s0);
        Part p = new Part();
        p.start = start * ss;
        p.size = count * ss;
        return p;
    }

    static Part gpt(Io io, byte[] typeGuid, String name, SecureRandom rnd) throws IOException {
        int ss = io.ss;
        long n = io.d.sectors();
        int entries = 128, esz = 128;
        int entSectors = (int) Io.ceilDiv((long) entries * esz, ss);
        long firstUsable = 2 + entSectors;
        long lastUsable = n - 1 - entSectors - 1;
        long start = Io.roundUp(Math.max(firstUsable, ALIGN / ss), ALIGN / ss);
        long end = lastUsable;                          // incluido
        // el final también alineado a 1 MiB (como hacen Windows y Linux)
        long endAligned = (end + 1) / (ALIGN / ss) * (ALIGN / ss) - 1;
        if (endAligned > start) end = endAligned;

        byte[] diskGuid = guid(rnd), partGuid = guid(rnd);
        byte[] ent = new byte[entSectors * ss];
        System.arraycopy(typeGuid, 0, ent, 0, 16);
        System.arraycopy(partGuid, 0, ent, 16, 16);
        Io.put64(ent, 32, start);
        Io.put64(ent, 40, end);
        Io.put64(ent, 48, 0);
        byte[] nm = (name == null ? "" : name).getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(nm, 0, ent, 56, Math.min(nm.length, 72));
        CRC32 c = new CRC32();
        c.update(ent, 0, entries * esz);
        long entCrc = c.getValue();

        // MBR protectora
        byte[] s0 = new byte[ss];
        int o = 446;
        s0[o + 1] = 0x00;
        s0[o + 2] = 0x02;
        s0[o + 3] = 0x00;
        s0[o + 4] = (byte) 0xEE;
        s0[o + 5] = (byte) 0xFF;
        s0[o + 6] = (byte) 0xFF;
        s0[o + 7] = (byte) 0xFF;
        Io.put32(s0, o + 8, 1);
        Io.put32(s0, o + 12, Math.min(n - 1, 0xFFFFFFFFL));
        s0[510] = 0x55;
        s0[511] = (byte) 0xAA;

        byte[] primary = header(ss, 1, n - 1, firstUsable, lastUsable, diskGuid, 2, entCrc);
        byte[] backup = header(ss, n - 1, 1, firstUsable, lastUsable, diskGuid, n - 1 - entSectors, entCrc);

        io.write(0, s0);
        io.write((long) ss, primary);
        io.write(2L * ss, ent);
        io.write((n - 1 - entSectors) * ss, ent);
        io.write((n - 1) * ss, backup);
        Part p = new Part();
        p.start = start * ss;
        p.size = (end - start + 1) * ss;
        return p;
    }

    private static byte[] header(int ss, long my, long alt, long first, long last, byte[] diskGuid, long entLba, long entCrc) {
        byte[] h = new byte[ss];
        System.arraycopy("EFI PART".getBytes(StandardCharsets.US_ASCII), 0, h, 0, 8);
        Io.put32(h, 8, 0x00010000);
        Io.put32(h, 12, 92);
        Io.put64(h, 24, my);
        Io.put64(h, 32, alt);
        Io.put64(h, 40, first);
        Io.put64(h, 48, last);
        System.arraycopy(diskGuid, 0, h, 56, 16);
        Io.put64(h, 72, entLba);
        Io.put32(h, 80, 128);
        Io.put32(h, 84, 128);
        Io.put32(h, 88, entCrc);
        CRC32 c = new CRC32();
        c.update(h, 0, 92);
        Io.put32(h, 16, c.getValue());
        return h;
    }

    /** GUID aleatorio (versión 4) en el orden de bytes de GPT. */
    static byte[] guid(SecureRandom rnd) {
        byte[] g = new byte[16];
        rnd.nextBytes(g);
        g[7] = (byte) ((g[7] & 0x0F) | 0x40);
        g[8] = (byte) ((g[8] & 0x3F) | 0x80);
        return g;
    }

    /** "EBD0A0A2-B9E5-4433-87C0-68B6B72699C7" → bytes en el orden mixto de GPT. */
    static byte[] guid(String s) {
        String h = s.replace("-", "");
        byte[] r = new byte[16];
        int[] order = {3, 2, 1, 0, 5, 4, 7, 6, 8, 9, 10, 11, 12, 13, 14, 15};
        for (int i = 0; i < 16; i++) r[i] = (byte) Integer.parseInt(h.substring(order[i] * 2, order[i] * 2 + 2), 16);
        return r;
    }

    private static void chs(byte[] b, int o, long lba) {
        long c = lba / (255 * 63), h = (lba / 63) % 255, s = lba % 63 + 1;
        if (c > 1023) {
            c = 1023;
            h = 254;
            s = 63;
        }
        b[o] = (byte) h;
        b[o + 1] = (byte) ((s & 0x3F) | ((c >> 2) & 0xC0));
        b[o + 2] = (byte) (c & 0xFF);
    }
}
