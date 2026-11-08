package com.forja.formateador;

import java.io.IOException;
import java.security.SecureRandom;

/** Formatea un disco completo: borra lo viejo, crea la tabla de particiones y da formato. */
public final class Forja {
    public enum Table {MBR, GPT}

    public static final class Options {
        public FsType fs = FsType.EXFAT;
        public Table table = Table.MBR;
        public int clusterBytes;          // 0 = automático
        public String label = "";
        public boolean fullErase;         // escribir ceros en todo el disco antes (lento)
    }

    private Forja() {
    }

    /** Tamaño que tendrá la partición con esa tabla (para elegir clúster antes de formatear). */
    public static long partitionBytes(long diskBytes, int sectorSize, Table t) {
        long align = Partitioner.ALIGN;
        if (t == Table.MBR) {
            long end = Math.min(diskBytes, Partitioner.mbrLimit(sectorSize));
            return end - align;
        }
        long entries = Io.roundUp(128 * 128, sectorSize);
        long lastUsableEnd = diskBytes - sectorSize - entries;
        long end = lastUsableEnd / align * align;
        return end - align;
    }

    public static void format(BlockDevice dev, Options o, Progress p) throws IOException {
        Io io = new Io(dev);
        SecureRandom rnd = new SecureRandom();
        long disk = io.size();
        int ss = io.ss;
        if (disk < 64L * 1024 * 1024) throw new IOException("El disco es demasiado pequeño (menos de 64 MB)");
        if (o.table == Table.MBR && disk > Partitioner.mbrLimit(ss)) {
            throw new IOException("Con MBR solo se pueden usar " + FsType.sizeText(Partitioner.mbrLimit(ss)) + ": elige GPT para usar todo el disco");
        }
        String label = o.fs.cleanLabel(o.label);
        long partBytes = partitionBytes(disk, ss, o.table);
        int cluster = o.clusterBytes > 0 ? o.clusterBytes : o.fs.defaultCluster(partBytes, ss);
        if (!o.fs.valid(partBytes, cluster, ss)) throw new IOException("Ese tamaño de " + o.fs.unitName() + " no sirve para este disco");

        // 1) borrar lo viejo
        if (o.fullErase) {
            io.zero(0, disk, p, 0f, 0.9f, "Borrando todo el disco (escribiendo ceros)…");
        } else {
            p.update(0.01f, "Borrando las particiones anteriores…");
            io.zero(0, Math.min(disk, 2 * Partitioner.ALIGN));
            long tail = Math.min(disk, Partitioner.ALIGN);
            io.zero(disk - tail, tail);
        }
        if (p.cancelled()) throw new IOException("Cancelado");

        // 2) tabla de particiones
        p.update(o.fullErase ? 0.9f : 0.03f, "Creando la tabla de particiones " + o.table + "…");
        Partitioner.Part part = o.table == Table.MBR
                ? Partitioner.mbr(io, o.fs.mbrType, rnd)
                : Partitioner.gpt(io, Partitioner.guid(o.fs.gptType), label, rnd);
        if (!o.fullErase) {
            // restos de un formato anterior al principio y al final de la partición
            io.zero(part.start, Math.min(part.size, Partitioner.ALIGN));
            long tail = Math.min(part.size, Partitioner.ALIGN);
            io.zero(part.start + part.size - tail, tail);
        }
        io.flush();

        // 3) formato
        Progress sub = o.fullErase ? scaled(p, 0.92f, 0.99f) : scaled(p, 0.05f, 0.99f);
        long serial = rnd.nextInt() & 0xFFFFFFFFL;
        switch (o.fs) {
            case FAT32 -> Fat32Formatter.format(io, part.start, part.size, cluster, label, serial, part.start / ss, sub);
            case EXFAT -> ExFatFormatter.format(io, part.start, part.size, cluster, label, serial, sub);
            case EXT4 -> Ext4Formatter.format(io, part.start, part.size, cluster, label, rnd, sub);
            case NTFS -> Ntfs.format(io, part.start, part.size, cluster, label, sub);
            default -> throw new IOException(o.fs.title + " llega en una próxima versión");
        }
        io.flush();
        p.update(1f, "¡Listo!");
    }

    private static Progress scaled(Progress p, float from, float to) {
        return new Progress() {
            @Override
            public void update(float f, String step) {
                p.update(f < 0 ? -1 : from + (to - from) * f, step);
            }

            @Override
            public boolean cancelled() {
                return p.cancelled();
            }
        };
    }
}
