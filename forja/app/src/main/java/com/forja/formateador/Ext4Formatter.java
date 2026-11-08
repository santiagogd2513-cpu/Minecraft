package com.forja.formateador;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * ext4 como lo crearía «mke2fs -t ext4 -O ^resize_inode,^flex_bg,^metadata_csum,uninit_bg
 * -E lazy_itable_init=1,lazy_journal_init=1»: superbloque con copias dispersas, grupos con
 * inicialización perezosa (no hay que escribir las tablas de inodos), raíz, lost+found y un
 * journal de hasta 64 MB. Así un disco de 4 TB se formatea escribiendo solo unos MB.
 */
final class Ext4Formatter {
    private static final int INODE_SIZE = 256;

    private Ext4Formatter() {
    }

    static void format(Io io, long partStart, long partBytes, int bs, String label, SecureRandom rnd, Progress p) throws IOException {
        if (bs != 1024 && bs != 2048 && bs != 4096) throw new IOException("Tamaño de bloque no válido para ext4");
        if (bs < io.ss) throw new IOException("El bloque no puede ser más chico que el sector del disco");
        long blocks = partBytes / bs;
        if (blocks > 0xFFFFFFFFL) throw new IOException("Demasiados bloques para ext4 sin 64 bits");
        int firstData = bs == 1024 ? 1 : 0;
        int bpg = 8 * bs;                                   // bloques por grupo
        int gdtEntry = 32;

        // inodos por grupo: uno cada 16 KB, llenando bloques enteros de la tabla
        int perBlock = bs / INODE_SIZE;
        long ipgL = (long) bpg * bs / 16384;
        ipgL = Io.roundUp(Math.max(ipgL, 16), Math.max(8, perBlock));
        int ipg = (int) Math.min(ipgL, 8L * bs);
        int itb = ipg / perBlock;                           // bloques de tabla de inodos por grupo

        long groups = Io.ceilDiv(blocks - firstData, bpg);
        int gdtBlocks = (int) Io.ceilDiv(groups * gdtEntry, bs);
        // el último grupo, si es muy chico, se descarta (como mke2fs)
        long lastSize = (blocks - firstData) - (groups - 1) * bpg;
        long overheadLast = (hasSuper(groups - 1) ? 1 + gdtBlocks : 0) + 2 + itb;
        if (groups > 1 && lastSize < overheadLast + 50) {
            blocks -= lastSize;
            groups--;
            gdtBlocks = (int) Io.ceilDiv(groups * gdtEntry, bs);
        }
        if (groups < 1) throw new IOException("Muy pequeño para ext4");

        byte[] uuid = new byte[16];
        rnd.nextBytes(uuid);
        uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x40);
        uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80);
        byte[] hashSeed = new byte[16];
        rnd.nextBytes(hashSeed);
        int now = (int) (System.currentTimeMillis() / 1000);

        // ---------------------------------------------------------------- distribución por grupo
        long[] blockBitmap = new long[(int) groups], inodeBitmap = new long[(int) groups], inodeTable = new long[(int) groups];
        long[] groupStart = new long[(int) groups], groupLen = new long[(int) groups], used = new long[(int) groups];
        for (int g = 0; g < groups; g++) {
            long start = firstData + (long) g * bpg;
            long len = Math.min(bpg, blocks - start);
            long b = start;
            if (hasSuper(g)) b += 1 + gdtBlocks;
            blockBitmap[g] = b++;
            inodeBitmap[g] = b++;
            inodeTable[g] = b;
            b += itb;
            groupStart[g] = start;
            groupLen[g] = len;
            used[g] = b - start;
            if (used[g] >= len) throw new IOException("Grupo demasiado pequeño");
        }

        // ---------------------------------------------------------------- bloques de datos del grupo 0
        long next = groupStart[0] + used[0];
        long rootBlock = next++;
        int lpfBlocks = Math.max(2, 16384 / bs);
        long lpfStart = next;
        next += lpfBlocks;
        long free0 = groupStart[0] + groupLen[0] - next;
        long jWant = journalSize(blocks);
        long jBlocks = Math.min(jWant, free0 - 16);
        if (jBlocks < 1024) jBlocks = 0;                     // demasiado chico para un journal
        long jStart = next;
        next += jBlocks;
        used[0] = next - groupStart[0];

        // ---------------------------------------------------------------- inodos 1..11 (grupo 0)
        int tableHead = (int) Io.ceilDiv(11L * INODE_SIZE, bs);
        byte[] itable = new byte[tableHead * bs];
        // 2: raíz
        inode(itable, bs, 2, 040755, 3, bs, new long[][]{{0, rootBlock, 1}}, now, 0);
        // 11: lost+found
        inode(itable, bs, 11, 040700, 2, (long) lpfBlocks * bs, new long[][]{{0, lpfStart, lpfBlocks}}, now, 0);
        // 8: journal
        byte[] jnlBlocksCopy = null;
        if (jBlocks > 0) {
            inode(itable, bs, 8, 0100600, 1, jBlocks * bs, new long[][]{{0, jStart, jBlocks}}, now, 0);
            jnlBlocksCopy = new byte[68];
            System.arraycopy(itable, 7 * INODE_SIZE + 0x28, jnlBlocksCopy, 0, 60);
            ByteBuffer.wrap(jnlBlocksCopy).order(ByteOrder.LITTLE_ENDIAN).putInt(60, (int) ((jBlocks * bs) >>> 32)).putInt(64, (int) (jBlocks * bs));
        }
        // 1 (bloques malos): vacío pero con fechas, como mke2fs
        put32(itable, 0 * INODE_SIZE + 0x08, now);
        put32(itable, 0 * INODE_SIZE + 0x0C, now);
        put32(itable, 0 * INODE_SIZE + 0x10, now);

        // ---------------------------------------------------------------- escribir
        long base = partStart;
        p.update(0.05f, "Creando las carpetas de ext4…");
        // raíz
        byte[] root = new byte[bs];
        int o = dirent(root, 0, 2, 12, ".", 2);
        o = dirent(root, o, 2, 12, "..", 2);
        dirent(root, o, 11, bs - o, "lost+found", 2);
        io.write(base + rootBlock * bs, root);
        // lost+found
        byte[] lpf = new byte[lpfBlocks * bs];
        o = dirent(lpf, 0, 11, 12, ".", 2);
        dirent(lpf, o, 2, bs - 12, "..", 2);
        for (int k = 1; k < lpfBlocks; k++) put16(lpf, k * bs + 4, bs);   // bloques vacíos: una entrada libre
        io.write(base + lpfStart * bs, lpf);
        // journal: solo el superbloque (el resto no hace falta borrarlo: s_start = 0)
        if (jBlocks > 0) {
            byte[] js = new byte[bs];
            ByteBuffer jb = ByteBuffer.wrap(js).order(ByteOrder.BIG_ENDIAN);
            jb.putInt(0, 0xC03B3998).putInt(4, 4).putInt(8, 0);
            jb.putInt(12, bs).putInt(16, (int) jBlocks).putInt(20, 1).putInt(24, 1).putInt(28, 0);
            System.arraycopy(uuid, 0, js, 48, 16);
            jb.putInt(64, 1);
            io.write(base + jStart * bs, js);
        }
        // tabla de inodos del grupo 0 (solo los primeros bloques; el resto lo inicializa el kernel)
        io.write(base + inodeTable[0] * bs, itable);

        // bitmaps: grupo 0, el último y los que tengan datos
        long totalFreeBlocks = 0;
        byte[] gdt = new byte[gdtBlocks * bs];
        for (int g = 0; g < groups; g++) {
            boolean last = g == groups - 1;
            boolean block0 = g == 0;
            long freeB = groupLen[g] - used[g];
            totalFreeBlocks += freeB;
            int freeI = block0 ? ipg - 11 : ipg;
            int flags = 0;
            if (!block0) flags |= 0x1;                        // INODE_UNINIT
            if (!block0 && !last) flags |= 0x2;               // BLOCK_UNINIT
            if (block0 || last) {
                byte[] bb = new byte[bs];
                for (long k = 0; k < used[g]; k++) bb[(int) (k >> 3)] |= (byte) (1 << (k & 7));
                for (long k = groupLen[g]; k < 8L * bs; k++) bb[(int) (k >> 3)] |= (byte) (1 << (k & 7));   // relleno
                io.write(base + blockBitmap[g] * bs, bb);
            }
            if (block0) {
                byte[] ib = new byte[bs];
                for (int k = 0; k < 11; k++) ib[k >> 3] |= (byte) (1 << (k & 7));
                for (long k = ipg; k < 8L * bs; k++) ib[(int) (k >> 3)] |= (byte) (1 << (k & 7));
                io.write(base + inodeBitmap[g] * bs, ib);
            }
            int d = g * gdtEntry;
            put32(gdt, d, blockBitmap[g]);
            put32(gdt, d + 4, inodeBitmap[g]);
            put32(gdt, d + 8, inodeTable[g]);
            put16(gdt, d + 12, (int) freeB);
            put16(gdt, d + 14, freeI);
            put16(gdt, d + 16, block0 ? 2 : 0);
            put16(gdt, d + 18, flags);
            put16(gdt, d + 28, block0 ? ipg - 11 : ipg);       // itable_unused
            put16(gdt, d + 30, gdCsum(uuid, g, gdt, d));
            if ((g & 63) == 0) p.update(0.1f + 0.6f * g / groups, "Preparando los grupos de bloques…");
        }

        long inodes = (long) ipg * groups;
        long reserved = blocks * 5 / 100;
        byte[] sb = new byte[1024];
        ByteBuffer s = ByteBuffer.wrap(sb).order(ByteOrder.LITTLE_ENDIAN);
        s.putInt(0x00, (int) inodes);
        s.putInt(0x04, (int) blocks);
        s.putInt(0x08, (int) reserved);
        s.putInt(0x0C, (int) totalFreeBlocks);
        s.putInt(0x10, (int) (inodes - 11));
        s.putInt(0x14, firstData);
        s.putInt(0x18, Integer.numberOfTrailingZeros(bs) - 10);
        s.putInt(0x1C, Integer.numberOfTrailingZeros(bs) - 10);
        s.putInt(0x20, bpg);
        s.putInt(0x24, bpg);
        s.putInt(0x28, ipg);
        s.putInt(0x30, now);
        s.putShort(0x36, (short) -1);
        s.putShort(0x38, (short) 0xEF53);
        s.putShort(0x3A, (short) 1);
        s.putShort(0x3C, (short) 1);
        s.putInt(0x40, now);
        s.putInt(0x4C, 1);
        s.putInt(0x54, 11);
        s.putShort(0x58, (short) INODE_SIZE);
        s.putInt(0x5C, (jBlocks > 0 ? 0x4 : 0) | 0x8 | 0x20);                     // has_journal, ext_attr, dir_index
        s.putInt(0x60, 0x2 | 0x40);                                                  // filetype, extents
        s.putInt(0x64, 0x1 | 0x2 | 0x8 | 0x10 | 0x20 | 0x40);                        // sparse_super, large_file, huge_file, uninit_bg, dir_nlink, extra_isize
        System.arraycopy(uuid, 0, sb, 0x68, 16);
        byte[] lab = (label == null ? "" : label).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(lab, 0, sb, 0x78, Math.min(16, lab.length));
        if (jBlocks > 0) s.putInt(0xE0, 8);
        System.arraycopy(hashSeed, 0, sb, 0xEC, 16);
        sb[0xFC] = 1;                                                                // half_md4
        if (jnlBlocksCopy != null) {
            sb[0xFD] = 1;
            System.arraycopy(jnlBlocksCopy, 0, sb, 0x10C, 68);
        }
        s.putInt(0x100, 0x000C);                                                     // user_xattr, acl
        s.putInt(0x108, now);
        s.putShort(0x15C, (short) 32);
        s.putShort(0x15E, (short) 32);
        s.putInt(0x160, 0x1);                                                        // hash con signo

        // copias del superbloque y de los descriptores en los grupos 0, 1, 3ⁿ, 5ⁿ, 7ⁿ
        p.update(0.8f, "Escribiendo el superbloque y sus copias…");
        for (int g = (int) groups - 1; g >= 0; g--) {
            if (!hasSuper(g)) continue;
            byte[] copy = sb.clone();
            put16(copy, 0x5A, g);
            long sbBlock = groupStart[g];
            if (g == 0) {
                // grupo 0: descriptores en el bloque siguiente al superbloque, y el superbloque
                // siempre en el byte 1024 del sistema de archivos (el principio queda en ceros)
                io.write(base + (sbBlock + 1) * bs, gdt);
                byte[] head = new byte[Math.max(2048, io.ss)];
                System.arraycopy(copy, 0, head, 1024, 1024);
                io.write(base, head);
            } else {
                byte[] blk = new byte[bs];
                System.arraycopy(copy, 0, blk, 0, 1024);
                io.write(base + sbBlock * bs, blk);
                io.write(base + (sbBlock + 1) * bs, gdt);
            }
        }
        io.flush();
    }

    static boolean hasSuper(long g) {
        if (g <= 1) return true;
        return pow(g, 3) || pow(g, 5) || pow(g, 7);
    }

    private static boolean pow(long g, int b) {
        long x = b;
        while (x < g) x *= b;
        return x == g;
    }

    static long journalSize(long blocks) {
        if (blocks < 2048) return 0;
        if (blocks < 32768) return 1024;
        if (blocks < 256 * 1024) return 4096;
        if (blocks < 512 * 1024) return 8192;
        return 16384;
    }

    /** Escribe el inodo n (1..) con sus extents (cada uno: {lógico, físico, largo}). */
    private static void inode(byte[] t, int bs, int n, int mode, int links, long size, long[][] extents, int now, int flags) {
        int o = (n - 1) * INODE_SIZE;
        ByteBuffer b = ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort(o, (short) mode);
        b.putInt(o + 0x04, (int) size);
        b.putInt(o + 0x08, now);
        b.putInt(o + 0x0C, now);
        b.putInt(o + 0x10, now);
        b.putShort(o + 0x1A, (short) links);
        long blocks512 = 0;
        for (long[] e : extents) blocks512 += e[2];
        b.putInt(o + 0x20, 0x80000 | flags);                 // EXTENTS_FL
        // cabecera del árbol de extents dentro de i_block
        int x = o + 0x28;
        b.putShort(x, (short) 0xF30A).putShort(x + 2, (short) extents.length).putShort(x + 4, (short) 4).putShort(x + 6, (short) 0).putInt(x + 8, 0);
        for (int i = 0; i < extents.length; i++) {
            int e = x + 12 + i * 12;
            b.putInt(e, (int) extents[i][0]);
            b.putShort(e + 4, (short) extents[i][2]);
            b.putShort(e + 6, (short) (extents[i][1] >>> 32));
            b.putInt(e + 8, (int) extents[i][1]);
        }
        b.putInt(o + 0x6C, (int) (size >>> 32));
        b.putShort(o + 0x80, (short) 32);                    // i_extra_isize
        b.putInt(o + 0x90, now);                             // crtime
        b.putInt(o + 0x1C, (int) (blocks512 * (bs / 512)));   // i_blocks en sectores de 512
    }

    private static int dirent(byte[] d, int o, int ino, int recLen, String name, int type) {
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        put32(d, o, ino);
        put16(d, o + 4, recLen);
        d[o + 6] = (byte) n.length;
        d[o + 7] = (byte) type;
        System.arraycopy(n, 0, d, o + 8, n.length);
        return o + recLen;
    }

    // ---------------------------------------------------------------- crc16 (checksum de descriptores)

    private static final int[] CRC16 = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? (c >>> 1) ^ 0xA001 : c >>> 1;
            CRC16[i] = c;
        }
    }

    private static int crc16(int crc, byte[] b, int off, int len) {
        for (int i = 0; i < len; i++) crc = (crc >>> 8) ^ CRC16[(crc ^ b[off + i]) & 0xFF];
        return crc & 0xFFFF;
    }

    private static int gdCsum(byte[] uuid, int g, byte[] gdt, int off) {
        int crc = crc16(0xFFFF, uuid, 0, 16);
        byte[] gn = {(byte) g, (byte) (g >> 8), (byte) (g >> 16), (byte) (g >> 24)};
        crc = crc16(crc, gn, 0, 4);
        crc = crc16(crc, gdt, off, 30);
        return crc;
    }

    private static void put16(byte[] b, int o, int v) {
        Io.put16(b, o, v);
    }

    private static void put32(byte[] b, int o, long v) {
        Io.put32(b, o, v);
    }

}
