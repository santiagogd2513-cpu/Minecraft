package com.forja.formateador;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Formatear una sola partición ya creada (sin tocar la tabla de particiones). Lo usa
 * Ventoy no oficial para la partición «Ventoy».
 */
public final class Formatters {
    private Formatters() {
    }

    /** Tipo de partición MBR que corresponde a cada formato. */
    public static int mbrType(FsType fs) {
        return fs.mbrType;
    }

    /** clusterBytes = 0: el recomendado para ese tamaño. */
    public static void formatPartition(BlockDevice dev, FsType fs, long startBytes, long sizeBytes, int clusterBytes,
                                       String label, Progress p) throws IOException {
        Io io = new Io(dev);
        int ss = io.ss;
        String lab = fs.cleanLabel(label);
        int cluster = clusterBytes > 0 ? clusterBytes : fs.defaultCluster(sizeBytes, ss);
        String prob = fs.problem(sizeBytes, ss);
        if (prob != null) throw new IOException(fs.title + ": " + prob);
        if (!fs.valid(sizeBytes, cluster, ss)) throw new IOException("Ese tamaño de " + fs.unitName() + " no sirve aquí");
        SecureRandom rnd = new SecureRandom();
        long serial = rnd.nextInt() & 0xFFFFFFFFL;
        // restos de un formato anterior
        io.zero(startBytes, Math.min(sizeBytes, 1024 * 1024));
        long tail = Math.min(sizeBytes, 1024 * 1024);
        io.zero(startBytes + sizeBytes - tail, tail);
        switch (fs) {
            case FAT32 -> Fat32Formatter.format(io, startBytes, sizeBytes, cluster, lab, serial, startBytes / ss, p);
            case EXFAT -> ExFatFormatter.format(io, startBytes, sizeBytes, cluster, lab, serial, p);
            case EXT4 -> Ext4Formatter.format(io, startBytes, sizeBytes, cluster, lab, rnd, p);
            case NTFS -> Ntfs.format(io, startBytes, sizeBytes, cluster, lab, p);
        }
        io.flush();
    }

    /** ¿Qué formato tiene la partición que empieza en startBytes? (null si no se reconoce) */
    public static FsType detect(BlockDevice dev, long startBytes) {
        try {
            Io io = new Io(dev);
            int ss = io.ss;
            byte[] b = io.read(startBytes, Math.max(2048, ss));
            String oem = new String(b, 3, 8, java.nio.charset.StandardCharsets.US_ASCII);
            if (oem.equals("EXFAT   ")) return FsType.EXFAT;
            if (oem.equals("NTFS    ")) return FsType.NTFS;
            if (new String(b, 82, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("FAT32")) return FsType.FAT32;
            if ((b[1080] & 0xFF) == 0x53 && (b[1081] & 0xFF) == 0xEF) return FsType.EXT4;
        } catch (Exception ignored) {
        }
        return null;
    }
}
