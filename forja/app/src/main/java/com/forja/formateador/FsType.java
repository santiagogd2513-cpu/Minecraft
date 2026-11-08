package com.forja.formateador;

import java.util.ArrayList;
import java.util.List;

/** Los formatos que sabe crear Forja, con sus reglas de tamaño de clúster y etiqueta. */
public enum FsType {
    FAT32("FAT32", "Consolas, autoradios, TVs, impresoras 3D, Ventoy", 0x0C, "EBD0A0A2-B9E5-4433-87C0-68B6B72699C7", 11),
    EXFAT("exFAT", "Archivos de más de 4 GB; Windows, Mac, Android, Linux", 0x07, "EBD0A0A2-B9E5-4433-87C0-68B6B72699C7", 11),
    NTFS("NTFS", "Windows y discos grandes", 0x07, "EBD0A0A2-B9E5-4433-87C0-68B6B72699C7", 32),
    EXT4("ext4", "Linux", 0x83, "0FC63DAF-8483-4772-8E79-3D69D8477DE4", 16);

    public final String title, usage, gptType;
    public final int mbrType, labelMax;

    FsType(String title, String usage, int mbrType, String gptType, int labelMax) {
        this.title = title;
        this.usage = usage;
        this.mbrType = mbrType;
        this.gptType = gptType;
        this.labelMax = labelMax;
    }

    /** ¿Ya está hecho? (las etapas siguientes agregan NTFS y ext4) */
    public boolean available() {
        return this != NTFS || Ntfs.available();
    }

    /** «clúster» o, en ext4, «bloque». */
    public String unitName() {
        return this == EXT4 ? "bloque" : "clúster";
    }

    /** Tamaños de clúster que sirven para este volumen (de menor a mayor). */
    public List<Integer> clusterChoices(long volBytes, int sectorSize) {
        List<Integer> out = new ArrayList<>();
        int min, max;
        switch (this) {
            // 32 KB es el máximo de la especificación de Microsoft: con 64 KB Android, TVs y
            // consolas dicen que la memoria está dañada
            case FAT32 -> { min = 512; max = 32 * 1024; }
            case EXFAT -> { min = 512; max = 32 * 1024 * 1024; }
            case NTFS -> { min = 512; max = 64 * 1024; }
            default -> { min = 1024; max = 4096; }
        }
        for (int c = Math.max(min, sectorSize); c <= max; c *= 2) {
            if (valid(volBytes, c, sectorSize)) out.add(c);
        }
        return out;
    }

    boolean valid(long volBytes, int cluster, int sectorSize) {
        long clusters = volBytes / cluster;
        return switch (this) {
            case FAT32 -> cluster <= 32 * 1024 && clusters >= 65525 + 64 && clusters <= 0x0FFFFFF5L && volBytes / sectorSize <= 0xFFFFFFFFL;
            case EXFAT -> clusters >= 64 && clusters <= 0xFFFFFFF5L;
            case NTFS -> clusters >= 1024 && clusters <= 0xFFFFFFFEL;
            case EXT4 -> clusters >= 4096 && clusters <= 0xFFFFFFFFL;
        };
    }

    /** El tamaño que usaría Windows (o mke2fs) para este volumen. */
    public int defaultCluster(long volBytes, int sectorSize) {
        long gb = volBytes >> 30, mb = volBytes >> 20;
        int want = switch (this) {
            case FAT32 -> mb < 260 ? 512 : gb < 8 ? 4096 : gb < 16 ? 8192 : gb < 32 ? 16384 : 32768;
            case EXFAT -> mb < 256 ? 4096 : gb < 32 ? 32768 : 131072;
            case NTFS -> gb < 16 * 1024 ? 4096 : 8192;
            case EXT4 -> 4096;
        };
        List<Integer> ok = clusterChoices(volBytes, sectorSize);
        if (ok.isEmpty()) return want;
        if (ok.contains(want)) return want;
        // lo más cercano que sí sirve
        int best = ok.get(0);
        for (int c : ok) if (Math.abs(Long.numberOfTrailingZeros(c) - Long.numberOfTrailingZeros(want))
                < Math.abs(Long.numberOfTrailingZeros(best) - Long.numberOfTrailingZeros(want))) best = c;
        return best;
    }

    /** Por qué no se puede usar este formato en este volumen, o null si se puede. */
    public String problem(long volBytes, int sectorSize) {
        if (!available()) return "El motor NTFS no se pudo cargar" + (Ntfs.loadError() == null ? "" : ": " + Ntfs.loadError());
        if (clusterChoices(volBytes, sectorSize).isEmpty()) {
            if (this == FAT32 && volBytes / sectorSize > 0xFFFFFFFFL) return "FAT32 no llega a discos tan grandes";
            return "No sirve para un disco de este tamaño";
        }
        return null;
    }

    /** Etiqueta limpia según las reglas del formato. */
    public String cleanLabel(String s) {
        if (s == null) s = "";
        s = s.trim();
        if (this == FAT32) {
            StringBuilder b = new StringBuilder();
            for (char ch : s.toUpperCase().toCharArray()) {
                if (ch < 0x20 || ch > 0x7E || "\"*+,./:;<=>?[\\]|".indexOf(ch) >= 0) ch = '_';
                b.append(ch);
            }
            s = b.toString();
        }
        return s.length() > labelMax ? s.substring(0, labelMax) : s;
    }

    public static String sizeText(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f TB", bytes / 1099511627776.0);
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1024L * 1024) return (bytes >> 20) + " MB";
        if (bytes >= 1024) return (bytes >> 10) + " KB";
        return bytes + " B";
    }
}
