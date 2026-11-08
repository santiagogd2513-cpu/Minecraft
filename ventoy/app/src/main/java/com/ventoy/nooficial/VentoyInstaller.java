package com.ventoy.nooficial;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Instala y actualiza Ventoy en un disco igual que Ventoy2Disk.sh (estilo MBR, arranque seguro
 * activado): partición 1 exFAT «Ventoy» desde el sector 2048 y partición 2 «VTOYEFI» de 32 MB
 * al final, con los archivos oficiales boot.img, core.img y ventoy.disk.img sin modificar.
 */
public final class VentoyInstaller {
    public static final int PART2_SECTORS = 65536;        // 32 MB
    public static final long PART1_START = 2048;
    private static final int S = BlockDevice.SECTOR;

    /** Los tres archivos oficiales de Ventoy. */
    public interface Files {
        byte[] bootImg() throws IOException;              // boot/boot.img (512 bytes)

        InputStream coreImg() throws IOException;          // boot/core.img (ya descomprimido)

        InputStream diskImg() throws IOException;          // ventoy/ventoy.disk.img (ya descomprimido, 32 MB)

        String version();
    }

    /** Lo que hay en una USB. */
    public static final class Info {
        public boolean hasVentoy;
        public String version;          // null si no se pudo leer
        public boolean gpt;
        public long part1Start, part1Sectors, part2Start, part2Sectors;
        public long diskSectors;
        /** Formato de la partición «Ventoy» (null si no se reconoce). */
        public com.forja.formateador.FsType part1Fs;
    }

    public static final class Layout {
        public final long part1Start = PART1_START, part1End, part2Start, part2End;

        Layout(long sectorNum) {
            long p1end = sectorNum - PART2_SECTORS - 1;
            long p2start = p1end + 1;
            long mod = p2start % 8;
            if (mod > 0) {
                p1end -= mod;
                p2start = p1end + 1;
            }
            part1End = p1end;
            part2Start = p2start;
            part2End = p2start + PART2_SECTORS - 1;
        }
    }

    private VentoyInstaller() {
    }

    // ------------------------------------------------------------------ leer

    public static Info inspect(BlockDevice d) throws IOException {
        Info info = new Info();
        info.diskSectors = d.sectors();
        byte[] mbr = Disk.readSectors(d, 0, 1);
        if ((mbr[510] & 0xFF) != 0x55 || (mbr[511] & 0xFF) != 0xAA) return info;
        int t1 = mbr[450] & 0xFF;
        if (t1 == 0xEE) {
            // GPT: Ventoy también la usa (opción -g); se lee la tabla GPT
            info.gpt = true;
            byte[] hdr = Disk.readSectors(d, 1, 1);
            if (!new String(hdr, 0, 8, StandardCharsets.US_ASCII).equals("EFI PART")) return info;
            long entLba = Disk.le64(hdr, 72);
            int entSize = (int) Disk.le32(hdr, 84);
            byte[] ents = Disk.readSectors(d, entLba, Math.max(1, (2 * entSize + S - 1) / S));
            info.part1Start = Disk.le64(ents, 32);
            info.part1Sectors = Disk.le64(ents, 40) - info.part1Start + 1;
            info.part2Start = Disk.le64(ents, entSize + 32);
            info.part2Sectors = Disk.le64(ents, entSize + 40) - info.part2Start + 1;
        } else {
            info.part1Start = Disk.le32(mbr, 454);
            info.part1Sectors = Disk.le32(mbr, 458);
            info.part2Start = Disk.le32(mbr, 470);
            info.part2Sectors = Disk.le32(mbr, 474);
        }
        info.hasVentoy = info.part1Start == PART1_START && info.part2Sectors == PART2_SECTORS;
        if (info.part1Start > 0) info.part1Fs = com.forja.formateador.Formatters.detect(new ForjaDev(d), info.part1Start * S);
        if (info.hasVentoy) {
            try {
                info.version = Fat16.ventoyVersion(d, info.part2Start);
            } catch (IOException e) {
                info.version = null;
            }
        }
        return info;
    }

    // ------------------------------------------------------------------ instalar

    /** Instalación nueva con la partición «Ventoy» en exFAT. */
    public static void install(BlockDevice d, Files f, Progress p) throws IOException {
        install(d, f, com.forja.formateador.FsType.EXFAT, p);
    }

    /** Instalación nueva: BORRA todo el disco. fs: formato de la partición «Ventoy». */
    public static void install(BlockDevice d, Files f, com.forja.formateador.FsType fs, Progress p) throws IOException {
        long n = d.sectors();
        if (n <= PART2_SECTORS + PART1_START + 65536) throw new IOException("La USB es demasiado pequeña para Ventoy");
        if (n > 4294967296L) throw new IOException("La USB pasa de 2 TB: el estilo MBR no sirve");
        Layout l = new Layout(n);
        SecureRandom rnd = new SecureRandom();

        // 1) borrar el principio del disco (tablas viejas)
        p.update(0.01f, "Borrando las particiones anteriores…");
        Disk.zero(d, 0, 64);
        check(p);

        // 2) tabla de particiones MBR (como fdisk: 1 = 0x07 activa, 2 = 0xEF)
        p.update(0.03f, "Creando las particiones…");
        byte[] mbr = new byte[S];
        byte[] sig = new byte[4];
        rnd.nextBytes(sig);
        System.arraycopy(sig, 0, mbr, 440, 4);
        partEntry(mbr, 446, true, com.forja.formateador.Formatters.mbrType(fs), l.part1Start, l.part1End - l.part1Start + 1);
        partEntry(mbr, 462, false, 0xEF, l.part2Start, PART2_SECTORS);
        mbr[510] = 0x55;
        mbr[511] = (byte) 0xAA;
        d.write(0, mbr, 0, 1);
        d.flush();
        check(p);

        // 3) partición 1 «Ventoy»: exFAT como Ventoy2Disk (32 KB por cluster hasta 32 GB, si no 128 KB),
        //    o NTFS / FAT32 / ext4 con los formateadores de Forja
        if (fs == com.forja.formateador.FsType.EXFAT) {
            long diskGb = n / 2097152;
            int spc = diskGb > 32 ? 256 : 64;
            ExFatFormatter.format(d, l.part1Start, l.part1End - l.part1Start + 1, spc, "Ventoy", rnd.nextInt() & 0xFFFFFFFFL, p);
        } else {
            p.update(0.05f, "Creando el sistema de archivos " + fs.title + "…");
            com.forja.formateador.Formatters.formatPartition(new ForjaDev(d), fs, l.part1Start * S, (l.part1End - l.part1Start + 1) * S, 0,
                    "Ventoy", new com.forja.formateador.Progress() {
                        @Override
                        public void update(float fraction, String step) {
                            p.update(fraction < 0 ? 0.06f : 0.05f + 0.03f * fraction, step);
                        }

                        @Override
                        public boolean cancelled() {
                            return p.cancelled();
                        }
                    });
        }
        check(p);

        // 4) arranque de BIOS: boot.img (446 bytes) y core.img (2047 sectores desde el 1)
        p.update(0.08f, "Escribiendo el arranque (BIOS)…");
        byte[] boot = f.bootImg();
        byte[] s0 = Disk.readSectors(d, 0, 1);
        System.arraycopy(boot, 0, s0, 0, 446);
        d.write(0, s0, 0, 1);
        writeStream(d, f.coreImg(), 1, 2047, null, 0, 0);
        check(p);

        // 5) partición 2: VTOYEFI (arranque UEFI, arranque seguro incluido)
        writeStream(d, f.diskImg(), l.part2Start, PART2_SECTORS, p, 0.1f, 0.97f);

        // 6) identificador de Ventoy (16 bytes en 384) y firma del disco (4 bytes en 440)
        s0 = Disk.readSectors(d, 0, 1);
        byte[] uuid = new byte[16];
        rnd.nextBytes(uuid);
        System.arraycopy(uuid, 0, s0, 384, 16);
        rnd.nextBytes(sig);
        System.arraycopy(sig, 0, s0, 440, 4);
        d.write(0, s0, 0, 1);
        p.update(0.99f, "Guardando…");
        d.flush();
        p.update(1f, "¡Listo!");
    }

    // ------------------------------------------------------------------ actualizar

    /** Actualización segura: solo cambia el arranque; las ISOs de la partición 1 no se tocan. */
    public static void update(BlockDevice d, Files f, Progress p) throws IOException {
        Info info = inspect(d);
        if (!info.hasVentoy) throw new IOException("Esta USB no tiene Ventoy (o está dañado): usa «Instalar»");
        if (info.gpt) throw new IOException("Esta USB tiene Ventoy con particiones GPT; por ahora solo se actualiza el estilo MBR");

        p.update(0.02f, "Actualizando el arranque (BIOS)…");
        byte[] s0 = Disk.readSectors(d, 0, 1);
        byte[] uuid = new byte[16];
        System.arraycopy(s0, 384, uuid, 0, 16);
        System.arraycopy(f.bootImg(), 0, s0, 0, 440);
        System.arraycopy(uuid, 0, s0, 384, 16);
        // la partición 1 debe ser la activa
        if ((s0[446] & 0xFF) == 0x00 && (s0[462] & 0xFF) == 0x80) {
            s0[446] = (byte) 0x80;
            s0[462] = 0x00;
        }
        d.write(0, s0, 0, 1);

        byte[] rsv = Disk.readSectors(d, 2040, 8);    // datos reservados de Ventoy
        writeStream(d, f.coreImg(), 1, 2047, null, 0, 0);
        Disk.writeSectors(d, 2040, rsv, 0, 8);
        check(p);

        writeStream(d, f.diskImg(), info.part2Start, PART2_SECTORS, p, 0.05f, 0.98f);
        d.flush();
        p.update(1f, "¡Listo!");
    }

    // ------------------------------------------------------------------ ayudas

    /** La USB vista con la interfaz de disco de Forja (sectores de 512). */
    static final class ForjaDev implements com.forja.formateador.BlockDevice {
        private final BlockDevice d;

        ForjaDev(BlockDevice d) {
            this.d = d;
        }

        @Override
        public int sectorSize() {
            return S;
        }

        @Override
        public long sectors() {
            return d.sectors();
        }

        @Override
        public void read(long lba, byte[] buf, int off, int count) throws IOException {
            d.read(lba, buf, off, count);
        }

        @Override
        public void write(long lba, byte[] buf, int off, int count) throws IOException {
            d.write(lba, buf, off, count);
        }

        @Override
        public void flush() throws IOException {
            d.flush();
        }

        @Override
        public int maxTransferBytes() {
            return d.maxTransfer() * S;
        }
    }

    private static void check(Progress p) throws IOException {
        if (p.cancelled()) throw new IOException("Cancelado");
    }

    private static void writeStream(BlockDevice d, InputStream in, long lba, int sectors, Progress p, float from, float to) throws IOException {
        int max = Math.max(8, Math.min(d.maxTransfer(), 2048));
        byte[] buf = new byte[max * S];
        try (InputStream src = in) {
            int done = 0;
            while (done < sectors) {
                int want = Math.min(max, sectors - done) * S;
                int got = 0;
                while (got < want) {
                    int r = src.read(buf, got, want - got);
                    if (r < 0) break;
                    got += r;
                }
                if (got < want) java.util.Arrays.fill(buf, got, want, (byte) 0);
                d.write(lba + done, buf, 0, want / S);
                done += want / S;
                if (p != null) {
                    if (p.cancelled()) throw new IOException("Cancelado");
                    p.update(from + (to - from) * done / sectors, "Escribiendo la partición VTOYEFI… " + (done * 100L / sectors) + " %");
                }
            }
        }
    }

    /** Entrada de partición MBR con CHS calculado como fdisk (255 cabezas, 63 sectores). */
    private static void partEntry(byte[] mbr, int o, boolean active, int type, long start, long count) {
        mbr[o] = (byte) (active ? 0x80 : 0);
        chs(mbr, o + 1, start);
        mbr[o + 4] = (byte) type;
        chs(mbr, o + 5, start + count - 1);
        Disk.put32(mbr, o + 8, start);
        Disk.put32(mbr, o + 12, count);
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
