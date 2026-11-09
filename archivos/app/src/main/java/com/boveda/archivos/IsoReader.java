package com.boveda.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Lector de imágenes de disco ISO (ISO 9660), con nombres largos Joliet (Windows) y Rock Ridge
 * (Linux). Solo lee: lista el contenido y entrega cada archivo.
 */
final class IsoReader {
    private static final int SECTOR = 2048;

    static final class Node {
        final Archive.Item item;
        final boolean dir;
        final long lba;
        final long size;

        Node(Archive.Item item, long lba, long size) {
            this.item = item;
            this.dir = item.dir;
            this.lba = lba;
            this.size = size;
        }
    }

    private final FileChannel ch;
    private final List<Node> nodes = new ArrayList<>();
    private boolean joliet;

    IsoReader(FileChannel ch) throws IOException {
        this.ch = ch;
        byte[] root = null;
        // descriptores de volumen desde el sector 16
        for (int s = 16; s < 64; s++) {
            byte[] vd = read((long) s * SECTOR, SECTOR);
            if (vd[1] != 'C' || vd[2] != 'D' || vd[3] != '0' || vd[4] != '0' || vd[5] != '1') {
                if (s == 16) throw new IOException(L.t("No es una imagen ISO 9660 (puede ser un disco solo UDF)"));
                break;
            }
            int type = vd[0] & 0xFF;
            if (type == 255) break;
            if (type == 1 && root == null) root = slice(vd, 156, 34);
            if (type == 2 && vd[88] == '%' && vd[89] == '/' && (vd[90] == '@' || vd[90] == 'C' || vd[90] == 'E')) {
                root = slice(vd, 156, 34);   // Joliet: nombres largos en UTF-16
                joliet = true;
            }
        }
        if (root == null) throw new IOException(L.t("La imagen ISO no tiene volumen principal"));
        walk(le32(root, 2), le32(root, 10), "", new HashSet<>(), 0);
    }

    List<Node> nodes() {
        return nodes;
    }

    List<Archive.Item> list() {
        List<Archive.Item> out = new ArrayList<>(nodes.size());
        for (Node n : nodes) out.add(n.item);
        return out;
    }

    InputStream open(Node n) {
        return new InputStream() {
            long pos = n.lba * SECTOR;
            final long end = pos + n.size;

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                int r = read(b, 0, 1);
                return r < 0 ? -1 : b[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (pos >= end) return -1;
                int want = (int) Math.min(len, end - pos);
                int r = ch.read(ByteBuffer.wrap(b, off, want), pos);
                if (r <= 0) return -1;
                pos += r;
                return r;
            }
        };
    }

    private void walk(long lba, long size, String prefix, Set<Long> seen, int depth) throws IOException {
        if (depth > 64 || !seen.add(lba)) return; // protección contra imágenes dañadas
        byte[] dir = read(lba * SECTOR, (int) Math.min(size, 64L * 1024 * 1024));
        int p = 0;
        while (p < dir.length) {
            int len = dir[p] & 0xFF;
            if (len == 0) {
                // los registros no cruzan sectores: salta al siguiente
                p = (p / SECTOR + 1) * SECTOR;
                continue;
            }
            if (p + len > dir.length) break;
            int flags = dir[p + 25] & 0xFF;
            int idLen = dir[p + 32] & 0xFF;
            long extent = le32(dir, p + 2), dataLen = le32(dir, p + 10);
            boolean isDir = (flags & 2) != 0;
            if (!(idLen == 1 && (dir[p + 33] == 0 || dir[p + 33] == 1))) {
                String name = name(dir, p, len, idLen, isDir);
                if (!name.isEmpty()) {
                    String full = prefix.isEmpty() ? name : prefix + "/" + name;
                    long time = date(dir, p + 18);
                    nodes.add(new Node(new Archive.Item(full, isDir, isDir ? 0 : dataLen, time), extent, dataLen));
                    if (isDir) walk(extent, dataLen, full, seen, depth + 1);
                }
            }
            p += len;
        }
    }

    private String name(byte[] d, int p, int len, int idLen, boolean isDir) {
        if (joliet) {
            String n = new String(d, p + 33, idLen, StandardCharsets.UTF_16BE);
            return strip(n, isDir);
        }
        // Rock Ridge: nombre largo en el área de uso del sistema (entrada "NM")
        int su = p + 33 + idLen + ((idLen % 2 == 0) ? 1 : 0);
        StringBuilder rr = null;
        while (su + 4 <= p + len) {
            int sl = d[su + 2] & 0xFF;
            if (sl < 4 || su + sl > p + len) break;
            if (d[su] == 'N' && d[su + 1] == 'M') {
                if (rr == null) rr = new StringBuilder();
                rr.append(new String(d, su + 5, sl - 5, StandardCharsets.UTF_8));
            }
            su += sl;
        }
        if (rr != null && rr.length() > 0) return rr.toString();
        return strip(new String(d, p + 33, idLen, StandardCharsets.ISO_8859_1), isDir);
    }

    private static String strip(String n, boolean dir) {
        if (!dir) {
            int semi = n.indexOf(';');
            if (semi >= 0) n = n.substring(0, semi);
            if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
        }
        return n.replace('/', '_');
    }

    private static long date(byte[] d, int p) {
        try {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.clear();
            c.set(1900 + (d[p] & 0xFF), (d[p + 1] & 0xFF) - 1, d[p + 2] & 0xFF, d[p + 3] & 0xFF, d[p + 4] & 0xFF, d[p + 5] & 0xFF);
            return c.getTimeInMillis() - d[p + 6] * 15L * 60 * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] read(long pos, int len) throws IOException {
        byte[] b = new byte[len];
        ByteBuffer bb = ByteBuffer.wrap(b);
        long p = pos;
        while (bb.hasRemaining()) {
            int r = ch.read(bb, p);
            if (r <= 0) break;
            p += r;
        }
        return b;
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] o = new byte[len];
        System.arraycopy(b, off, o, 0, len);
        return o;
    }

    private static long le32(byte[] b, int p) {
        return (b[p] & 0xFFL) | (b[p + 1] & 0xFFL) << 8 | (b[p + 2] & 0xFFL) << 16 | (b[p + 3] & 0xFFL) << 24;
    }
}
