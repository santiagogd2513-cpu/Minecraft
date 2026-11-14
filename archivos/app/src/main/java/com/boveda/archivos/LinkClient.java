package com.boveda.archivos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** El celular que mira: pide listas, baja y sube archivos al otro por el cable. Una petición a la vez. */
final class LinkClient implements java.io.Closeable {
    interface Progress {
        /** Devuelve false para cancelar. */
        boolean onBytes(long done, long total);
    }

    private final LinkWire.Pipe pipe;
    private final DataInputStream in;
    private final DataOutputStream out;
    private int nextId = 1;
    String peerName = "";

    LinkClient(LinkWire.Pipe pipe, String myName) throws IOException {
        this.pipe = pipe;
        this.in = new DataInputStream(new java.io.BufferedInputStream(pipe.in(), 1 << 16));
        this.out = new DataOutputStream(new java.io.BufferedOutputStream(pipe.out(), 1 << 16));
        LinkWire.Msg r = request(LinkWire.HELLO, LinkWire.build(o -> {
            o.writeInt(LinkWire.VERSION);
            o.writeUTF(myName);
        }));
        DataInputStream d = r.in();
        int v = d.readInt();
        if (v != LinkWire.VERSION) throw new IOException("los dos celulares tienen versiones distintas de Bóveda");
        peerName = d.readUTF();
    }

    private synchronized LinkWire.Msg request(int type, byte[] payload) throws IOException {
        int id = nextId++;
        LinkWire.write(out, type, id, payload);
        return expect(id);
    }

    private LinkWire.Msg expect(int id) throws IOException {
        LinkWire.Msg m = LinkWire.read(in);
        if (m.id != id) throw new IOException("respuesta fuera de orden");
        if (m.type == LinkWire.R_ERROR) throw new IOException(m.in().readUTF());
        return m;
    }

    synchronized List<LinkVfs.Item> list(String path) throws IOException {
        LinkWire.Msg r = request(LinkWire.LIST, LinkWire.build(o -> o.writeUTF(path)));
        DataInputStream d = r.in();
        int n = d.readInt();
        List<LinkVfs.Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new LinkVfs.Item(d.readUTF(), d.readBoolean(), d.readLong(), d.readLong()));
        return out;
    }

    synchronized LinkVfs.Item stat(String path) throws IOException {
        DataInputStream d = request(LinkWire.STAT, LinkWire.build(o -> o.writeUTF(path))).in();
        if (!d.readBoolean()) return null;
        return new LinkVfs.Item(d.readUTF(), d.readBoolean(), d.readLong(), d.readLong());
    }

    /** Baja un archivo (o un pedazo, con offset y largo). Devuelve cuántos bytes llegaron. */
    synchronized long read(String path, long offset, long len, OutputStream dest, Progress p, long expectedTotal) throws IOException {
        int id = nextId++;
        LinkWire.write(out, LinkWire.READ, id, LinkWire.build(o -> {
            o.writeUTF(path);
            o.writeLong(offset);
            o.writeLong(len);
        }));
        long got = 0;
        while (true) {
            LinkWire.Msg m = LinkWire.read(in);
            if (m.id != id) throw new IOException("respuesta fuera de orden");
            if (m.type == LinkWire.R_ERROR) throw new IOException(m.in().readUTF());
            if (m.type == LinkWire.R_END) {
                long total = m.in().readLong();
                if (total != got) throw new IOException("faltaron bytes: llegaron " + got + " de " + total);
                return got;
            }
            if (m.type != LinkWire.R_DATA) throw new IOException("respuesta inesperada");
            dest.write(m.payload);
            got += m.payload.length;
            if (p != null && !p.onBytes(got, expectedTotal)) {
                // se le avisa al otro y se descarta lo que ya venía en camino hasta que cierre la respuesta
                LinkWire.write(out, LinkWire.CANCEL, id, new byte[0]);
                while (true) {
                    LinkWire.Msg r = LinkWire.read(in);
                    if (r.id == id && (r.type == LinkWire.R_END || r.type == LinkWire.R_ERROR)) break;
                }
                throw new IOException("cancelado");
            }
        }
    }

    /** Sube un archivo al otro celular. Devuelve el nombre con el que quedó (puede cambiar si ya existía uno igual). */
    synchronized String put(String path, InputStream src, long size, long modified, Progress p) throws IOException {
        request(LinkWire.PUT_BEGIN, LinkWire.build(o -> {
            o.writeUTF(path);
            o.writeLong(size);
            o.writeLong(modified);
        }));
        byte[] buf = new byte[LinkWire.CHUNK];
        long sent = 0;
        int n;
        try {
            while ((n = src.read(buf)) > 0) {
                int id = nextId++;
                LinkWire.write(out, LinkWire.PUT_DATA, id, n == buf.length ? buf : java.util.Arrays.copyOf(buf, n));
                sent += n;
                if (p != null && !p.onBytes(sent, size)) throw new IOException("cancelado");
            }
        } catch (IOException e) {
            // el otro lado se queda con un archivo a medias: se le avisa para que lo descarte
            try {
                request(LinkWire.PUT_ABORT, new byte[0]);
            } catch (IOException ignored) {
            }
            throw e;
        }
        LinkWire.Msg r = request(LinkWire.PUT_END, new byte[0]);
        return r.payload.length == 0 ? Paths.name(path) : r.in().readUTF();
    }

    synchronized void mkdir(String path) throws IOException {
        request(LinkWire.MKDIR, LinkWire.build(o -> o.writeUTF(path)));
    }

    @Override
    public void close() throws IOException {
        try {
            LinkWire.write(out, LinkWire.BYE, nextId++, new byte[0]);
        } catch (IOException ignored) {
        }
        pipe.close();
    }
}
