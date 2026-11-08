package com.forja.formateador;

import java.io.IOException;

/**
 * NTFS con el mkntfs original de ntfs-3g (GPLv2+), compilado dentro de Forja: en vez de abrir
 * un /dev/sdX, escribe en la partición a través de {@link Device}.
 */
final class Ntfs {
    private static boolean loaded;
    private static Throwable loadError;

    static {
        try {
            System.loadLibrary("forjantfs");
            loaded = true;
        } catch (Throwable t) {
            loadError = t;
        }
    }

    private Ntfs() {
    }

    static boolean available() {
        return loaded;
    }

    /** Por qué no cargó el motor (para mostrarlo), o null. */
    static String loadError() {
        if (loadError == null) return null;
        String m = loadError.getMessage();
        return m == null ? loadError.getClass().getSimpleName() : m;
    }

    /** Lo que mkntfs lee y escribe: posiciones en bytes dentro de la partición. */
    static final class Device {
        private final Io io;
        private final long base, size;
        private final Progress p;
        private long written;

        Device(Io io, long base, long size, Progress p) {
            this.io = io;
            this.base = base;
            this.size = size;
            this.p = p;
        }

        /** Lee len bytes en off (puede no estar alineado a sector). */
        public int read(long off, byte[] buf, int len) {
            try {
                if (off >= size) return 0;
                len = (int) Math.min(len, size - off);
                int ss = io.ss;
                long a = (base + off) / ss * ss, b = Io.roundUp(base + off + len, ss);
                byte[] t = io.read(a, (int) (b - a));
                System.arraycopy(t, (int) (base + off - a), buf, 0, len);
                return len;
            } catch (IOException e) {
                return -1;
            }
        }

        public int write(long off, byte[] buf, int len) {
            try {
                if (off + len > size) return -1;
                int ss = io.ss;
                long a = (base + off) / ss * ss, b = Io.roundUp(base + off + len, ss);
                if (a == base + off && b == base + off + len) {
                    io.write(a, buf, 0, len);
                } else {
                    // escritura que no cae en límites de sector: leer, cambiar y escribir
                    byte[] t = io.read(a, (int) (b - a));
                    System.arraycopy(buf, 0, t, (int) (base + off - a), len);
                    io.write(a, t);
                }
                written += len;
                p.update(-1, "Creando NTFS… " + FsType.sizeText(written) + " escritos");
                return len;
            } catch (IOException e) {
                return -1;
            }
        }

        public int sync() {
            try {
                io.flush();
                return 0;
            } catch (IOException e) {
                return -1;
            }
        }
    }

    static synchronized void format(Io io, long partStart, long partBytes, int clusterBytes, String label, Progress p) throws IOException {
        if (!loaded) throw new IOException("No se pudo cargar el motor NTFS: " + loadError);
        int ss = io.ss;
        long sectors = partBytes / ss;
        String[] args = {
                "-F",                       // no es un /dev de verdad: seguir igual
                "-Q",                       // formato rápido (no escribe ceros en todo)
                "-s", String.valueOf(ss),
                "-p", String.valueOf(partStart / ss),
                "-H", "255", "-S", "63",
                "-c", String.valueOf(clusterBytes),
                "-L", label == null ? "" : label,
                "/dev/null",               // nombre que siempre existe; todo va por Device
                String.valueOf(sectors),
        };
        p.update(-1, "Creando NTFS…");
        int r = mkntfs(new Device(io, partStart, sectors * ss, p), sectors * ss, args);
        if (r != 0) {
            String msg = log();
            throw new IOException("mkntfs falló (" + r + ")" + (msg == null || msg.isEmpty() ? "" : ":\n" + tail(msg)));
        }
    }

    private static String tail(String s) {
        s = s.trim();
        return s.length() > 600 ? "…" + s.substring(s.length() - 600) : s;
    }

    private static native int mkntfs(Device dev, long size, String[] args);

    static native String log();
}
