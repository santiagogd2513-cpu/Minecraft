package com.ventoy.nooficial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Lector mínimo de FAT16: busca la versión de Ventoy en /grub/grub.cfg de la partición VTOYEFI. */
final class Fat16 {
    private Fat16() {
    }

    static String ventoyVersion(BlockDevice d, long partStart) throws IOException {
        byte[] bs = Disk.readSectors(d, partStart, 1);
        int bps = Disk.le16(bs, 11);
        if (bps != 512) throw new IOException("FAT no compatible");
        int spc = bs[13] & 0xFF, rsv = Disk.le16(bs, 14), fats = bs[16] & 0xFF, rootEnt = Disk.le16(bs, 17);
        int spf = Disk.le16(bs, 22);
        if (spc == 0 || fats == 0 || spf == 0) throw new IOException("FAT no válida");
        long fatLba = partStart + rsv;
        long rootLba = fatLba + (long) fats * spf;
        int rootSecs = (rootEnt * 32 + 511) / 512;
        long dataLba = rootLba + rootSecs;
        byte[] fat = Disk.readSectors(d, fatLba, spf);

        byte[] root = Disk.readSectors(d, rootLba, rootSecs);
        int grub = find(root, "GRUB       ", true);
        if (grub < 0) throw new IOException("Sin carpeta grub");
        byte[] grubDir = readChain(d, fat, dataLba, spc, grub, 64 * 1024);
        int cfg = find(grubDir, "GRUB    CFG", false);
        if (cfg < 0) throw new IOException("Sin grub.cfg");
        byte[] data = readChain(d, fat, dataLba, spc, cfg, 512 * 1024);
        String s = new String(data, StandardCharsets.ISO_8859_1);
        int i = s.indexOf("VENTOY_VERSION=\"");
        if (i < 0) return null;
        int a = i + 16, b = s.indexOf('"', a);
        return b > a ? s.substring(a, b) : null;
    }

    /** Primer cluster de la entrada con ese nombre 8.3, o -1. */
    private static int find(byte[] dir, String name83, boolean wantDir) {
        byte[] n = name83.getBytes(StandardCharsets.US_ASCII);
        for (int o = 0; o + 32 <= dir.length; o += 32) {
            int first = dir[o] & 0xFF;
            if (first == 0) break;
            if (first == 0xE5) continue;
            int attr = dir[o + 11] & 0xFF;
            if (attr == 0x0F) continue;   // nombre largo
            boolean isDir = (attr & 0x10) != 0;
            if (isDir != wantDir) continue;
            boolean eq = true;
            for (int k = 0; k < 11; k++) if (dir[o + k] != n[k]) { eq = false; break; }
            if (eq) return Disk.le16(dir, o + 26);
        }
        return -1;
    }

    private static byte[] readChain(BlockDevice d, byte[] fat, long dataLba, int spc, int cluster, int limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int c = cluster, guard = 0;
        while (c >= 2 && c < 0xFFF8 && out.size() < limit && guard++ < 70000) {
            out.write(Disk.readSectors(d, dataLba + (long) (c - 2) * spc, spc), 0, spc * 512);
            c = Disk.le16(fat, c * 2);
        }
        return out.toByteArray();
    }
}
