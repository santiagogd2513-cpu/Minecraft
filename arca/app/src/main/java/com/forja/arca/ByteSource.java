package com.forja.arca;

import java.io.IOException;

/** Una fuente de bytes de solo lectura con acceso directo: un disco, un archivo, un pedazo de otra fuente. */
interface ByteSource {
    long length();

    /** Lee exactamente {@code len} bytes desde {@code off}; falla si se pasa del final. */
    void read(long off, byte[] buf, int boff, int len) throws IOException;

    /** ¿Se puede escribir? Por defecto no: solo los discos abiertos para escribir lo permiten. */
    default boolean writable() {
        return false;
    }

    /** Escribe exactamente {@code len} bytes desde {@code off}. */
    default void write(long off, byte[] buf, int boff, int len) throws IOException {
        throw new IOException("Esta fuente es de solo lectura");
    }

    /** Pide que lo escrito llegue de verdad al disco. */
    default void flush() throws IOException {
    }

    default byte[] read(long off, int len) throws IOException {
        byte[] b = new byte[len];
        read(off, b, 0, len);
        return b;
    }

    /** Un pedazo de esta fuente, que empieza en {@code start} y mide {@code size} bytes. */
    default ByteSource slice(long start, long size) {
        ByteSource parent = this;
        return new ByteSource() {
            @Override
            public long length() {
                return size;
            }

            @Override
            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > size) throw new IOException("Lectura fuera del pedazo");
                parent.read(start + off, buf, boff, len);
            }

            @Override
            public boolean writable() {
                return parent.writable();
            }

            @Override
            public void write(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > size) throw new IOException("Escritura fuera del pedazo");
                parent.write(start + off, buf, boff, len);
            }

            @Override
            public void flush() throws IOException {
                parent.flush();
            }
        };
    }

    /** Varias fuentes seguidas como si fueran una sola (los archivos Data0000, Data0001… de una memoria USB de la 360). */
    static ByteSource concat(java.util.List<ByteSource> parts) {
        long[] starts = new long[parts.size()];
        long total = 0;
        for (int i = 0; i < starts.length; i++) {
            starts[i] = total;
            total += parts.get(i).length();
        }
        final long all = total;
        return new ByteSource() {
            @Override
            public long length() {
                return all;
            }

            @Override
            public void read(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > all) throw new IOException("Lectura fuera de la memoria");
                int i = 0;
                while (i + 1 < starts.length && starts[i + 1] <= off) i++;
                while (len > 0) {
                    ByteSource p = parts.get(i);
                    long inPart = off - starts[i];
                    int n = (int) Math.min(len, p.length() - inPart);
                    p.read(inPart, buf, boff, n);
                    off += n;
                    boff += n;
                    len -= n;
                    i++;
                }
            }

            @Override
            public boolean writable() {
                for (ByteSource p : parts) if (!p.writable()) return false;
                return true;
            }

            @Override
            public void write(long off, byte[] buf, int boff, int len) throws IOException {
                if (off < 0 || off + len > all) throw new IOException("Escritura fuera de la memoria");
                int i = 0;
                while (i + 1 < starts.length && starts[i + 1] <= off) i++;
                while (len > 0) {
                    ByteSource p = parts.get(i);
                    long inPart = off - starts[i];
                    int n = (int) Math.min(len, p.length() - inPart);
                    p.write(inPart, buf, boff, n);
                    off += n;
                    boff += n;
                    len -= n;
                    i++;
                }
            }

            @Override
            public void flush() throws IOException {
                for (ByteSource p : parts) p.flush();
            }
        };
    }
}
