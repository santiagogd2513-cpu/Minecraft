package com.forja.formateador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * FAT32 según la especificación de Microsoft (fatgen103): 2 FATs, raíz en el clúster 2,
 * FSInfo en el sector 1 y copia del arranque en el 6. El inicio de los datos queda alineado
 * al tamaño de clúster.
 */
final class Fat32Formatter {
    private Fat32Formatter() {
    }

    static void format(Io io, long partStart, long partBytes, int clusterBytes, String label, long serial,
                       long hiddenSectors, Progress p) throws IOException {
        int bps = io.ss;
        int spc = clusterBytes / bps;
        if (spc < 1 || spc > 128 || Integer.bitCount(spc) != 1) throw new IOException("Tamaño de clúster no válido para FAT32");
        long totSec = partBytes / bps;
        if (totSec > 0xFFFFFFFFL) throw new IOException("Demasiado grande para FAT32");

        long rsvd = 32, fatSz = 1, clusters = 0;
        for (int i = 0; i < 64; i++) {
            long data = totSec - rsvd - 2 * fatSz;
            clusters = data / spc;
            long need = Io.ceilDiv((clusters + 2) * 4, bps);
            // alinear el inicio de los datos a un clúster
            long r = rsvd;
            long misalign = (r + 2 * need) % spc;
            if (misalign != 0) r += spc - misalign;
            if (need == fatSz && r == rsvd) break;
            fatSz = need;
            rsvd = r;
        }
        long dataStart = rsvd + 2 * fatSz;
        clusters = (totSec - dataStart) / spc;
        if (clusters < 65525) throw new IOException("Clúster demasiado grande para este tamaño (FAT32 necesita al menos 65525 clústeres)");
        if (clusters > 0x0FFFFFF5L) throw new IOException("Clúster demasiado pequeño para este tamaño");

        String lab = label == null || label.isEmpty() ? "NO NAME" : label;
        byte[] lab11 = pad11(lab);

        // región reservada (arranque, FSInfo y sus copias)
        p.update(0.05f, "Escribiendo el arranque FAT32…");
        io.zero(partStart, rsvd * bps);
        byte[] boot = new byte[bps];
        boot[0] = (byte) 0xEB;
        boot[1] = 0x58;
        boot[2] = (byte) 0x90;
        System.arraycopy("MSWIN4.1".getBytes(StandardCharsets.US_ASCII), 0, boot, 3, 8);
        Io.put16(boot, 11, bps);
        boot[13] = (byte) spc;
        Io.put16(boot, 14, (int) rsvd);
        boot[16] = 2;
        Io.put16(boot, 17, 0);
        Io.put16(boot, 19, 0);
        boot[21] = (byte) 0xF8;
        Io.put16(boot, 22, 0);
        Io.put16(boot, 24, 63);
        Io.put16(boot, 26, 255);
        Io.put32(boot, 28, hiddenSectors);
        Io.put32(boot, 32, totSec);
        Io.put32(boot, 36, fatSz);
        Io.put16(boot, 40, 0);
        Io.put16(boot, 42, 0);
        Io.put32(boot, 44, 2);
        Io.put16(boot, 48, 1);
        Io.put16(boot, 50, 6);
        boot[64] = (byte) 0x80;
        boot[66] = 0x29;
        Io.put32(boot, 67, serial);
        System.arraycopy(lab11, 0, boot, 71, 11);
        System.arraycopy("FAT32   ".getBytes(StandardCharsets.US_ASCII), 0, boot, 82, 8);
        // código mínimo: si alguien intenta arrancar, muestra un aviso
        byte[] msg = "Este disco no arranca: es solo de datos (Forja).\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] code = {(byte) 0x0E, 0x1F, (byte) 0xBE, 0x77, 0x7C, (byte) 0xAC, 0x22, (byte) 0xC0, 0x74, 0x0B, 0x56,
                (byte) 0xB4, 0x0E, (byte) 0xBB, 0x07, 0x00, (byte) 0xCD, 0x10, 0x5E, (byte) 0xEB, (byte) 0xF0,
                0x32, (byte) 0xE4, (byte) 0xCD, 0x16, (byte) 0xCD, 0x19, (byte) 0xEB, (byte) 0xFE};
        System.arraycopy(code, 0, boot, 90, code.length);
        System.arraycopy(msg, 0, boot, 0x77, Math.min(msg.length, 510 - 0x77 - 1));
        boot[510] = 0x55;
        boot[511] = (byte) 0xAA;

        byte[] fsinfo = new byte[bps];
        Io.put32(fsinfo, 0, 0x41615252L);
        Io.put32(fsinfo, 484, 0x61417272L);
        Io.put32(fsinfo, 488, clusters - 1);
        Io.put32(fsinfo, 492, 3);
        Io.put32(fsinfo, 508, 0xAA550000L);

        byte[] sector2 = new byte[bps];           // el sector 2 también lleva firma
        sector2[510] = 0x55;
        sector2[511] = (byte) 0xAA;

        // FATs
        long fatBytes = fatSz * bps;
        for (int f = 0; f < 2; f++) {
            long off = partStart + rsvd * bps + f * fatBytes;
            io.zero(off, fatBytes, p, 0.1f + 0.4f * f, 0.5f + 0.4f * f, "Escribiendo la tabla FAT " + (f + 1) + " de 2…");
            byte[] first = new byte[bps];
            Io.put32(first, 0, 0x0FFFFFF8L);
            Io.put32(first, 4, 0x0FFFFFFFL);
            Io.put32(first, 8, 0x0FFFFFFFL);   // carpeta raíz
            io.write(off, first);
        }

        // carpeta raíz (clúster 2) con la etiqueta
        // (el clúster se llena de ceros por tramos: con clústeres grandes no se reserva un bloque enorme)
        long rootOff = partStart + dataStart * bps;
        io.zero(rootOff, clusterBytes);
        byte[] root = new byte[bps];
        if (label != null && !label.isEmpty()) {
            System.arraycopy(lab11, 0, root, 0, 11);
            root[11] = 0x08;
        }
        io.write(rootOff, root);

        // por último el arranque, para que un formato a medias no parezca válido
        io.write(partStart + 6L * bps, boot);
        io.write(partStart + 7L * bps, fsinfo);
        io.write(partStart + 8L * bps, sector2);
        io.write(partStart + 1L * bps, fsinfo);
        io.write(partStart + 2L * bps, sector2);
        io.write(partStart, boot);
        io.flush();
    }

    private static byte[] pad11(String s) {
        byte[] b = new byte[11];
        java.util.Arrays.fill(b, (byte) ' ');
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, b, 0, Math.min(11, src.length));
        return b;
    }
}
