package com.forja.arca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Encuentra las particiones de una Xbox 360 dentro de un disco duro o de una memoria USB.
 * <ul>
 *   <li>Disco duro de la consola (conectado por un adaptador USB): las particiones XTAF están en lugares
 *       fijos del disco; se prueban uno por uno y solo cuentan los que de verdad empiezan con «XTAF».</li>
 *   <li>Memoria USB configurada por la consola: dentro de la carpeta «Xbox360» hay archivos Data0000,
 *       Data0001… que juntos forman un disco; la partición XTAF empieza dentro del primero.</li>
 * </ul>
 */
final class XboxDisk {
    /** Una partición lista para leer. */
    static final class Part {
        final String name;
        final ByteSource source;

        Part(String name, ByteSource source) {
            this.name = name;
            this.source = source;
        }
    }

    private static final long[] OFFSETS = {0x80000L, 0x80080000L, 0x10C080000L, 0x118EB0000L, 0x120EB0000L, 0x130EB0000L};
    private static final String[] NAMES = {"Sistema", "Caché 0", "Caché 1", "Juegos viejos", "Compatibilidad", "Datos (juegos, partidas y perfiles)"};

    private XboxDisk() {}

    /** Las particiones XTAF de un disco duro de Xbox 360. */
    static List<Part> hdd(ByteSource disk) {
        List<Part> out = new ArrayList<>();
        for (int i = 0; i < OFFSETS.length; i++) {
            long start = OFFSETS[i];
            long end = i + 1 < OFFSETS.length ? OFFSETS[i + 1] : disk.length();
            if (start + 0x2000 >= disk.length()) continue;
            ByteSource part = disk.slice(start, Math.min(end, disk.length()) - start);
            if (Xtaf.isXtaf(part)) out.add(new Part(NAMES[i], part));
        }
        // por si el disco es una sola partición (copia hecha a mano, memoria vieja)
        if (out.isEmpty() && Xtaf.isXtaf(disk)) out.add(new Part("Datos", disk));
        return out;
    }

    /**
     * La partición de una memoria USB de la consola, a partir de sus archivos Data0000, Data0001…
     * (ya en orden). Busca la marca «XTAF» dentro del primero.
     */
    static Part usbStick(List<ByteSource> dataFiles) throws IOException {
        if (dataFiles.isEmpty()) return null;
        ByteSource all = ByteSource.concat(dataFiles);
        ByteSource first = dataFiles.get(0);
        long limit = Math.min(first.length() - 16, 0x1000000L);
        byte[] h = new byte[16];
        for (long off = 0; off <= limit; off += 0x1000) {
            first.read(off, h, 0, 16);
            if (h[0] == 'X' && h[1] == 'T' && h[2] == 'A' && h[3] == 'F') {
                ByteSource part = all.slice(off, all.length() - off);
                if (Xtaf.isXtaf(part)) return new Part("Memoria USB de la consola", part);
            }
        }
        return null;
    }
}
