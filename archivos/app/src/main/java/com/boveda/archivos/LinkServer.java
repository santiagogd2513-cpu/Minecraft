package com.boveda.archivos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** El celular que comparte: contesta lo que pide el otro, solo dentro de las carpetas que su dueño dejó ver. */
final class LinkServer implements Runnable {
    interface Listener {
        /** El otro celular quiere conectarse: false = el dueño lo rechazó. Se llama antes de contestar. */
        boolean approve(String name);

        void onActivity(String what);

        void onClosed(String why);
    }

    private final LinkWire.Pipe pipe;
    private final LinkVfs vfs;
    private final String deviceName;
    private final Listener listener;
    private volatile boolean stop;

    LinkServer(LinkWire.Pipe pipe, LinkVfs vfs, String deviceName, Listener l) {
        this.pipe = pipe;
        this.vfs = vfs;
        this.deviceName = deviceName;
        this.listener = l;
    }

    void stop() {
        stop = true;
        try {
            pipe.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        String why = "desconectado";
        try {
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(pipe.in(), 1 << 16));
            DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(pipe.out(), 1 << 16));
            LinkVfs.Sink sink = null;
            String putError = null;
            while (!stop) {
                LinkWire.Msg m = LinkWire.read(in);
                try {
                    switch (m.type) {
                        case LinkWire.HELLO -> {
                            DataInputStream d = m.in();
                            int v = d.readInt();
                            String name = d.readUTF();
                            if (v != LinkWire.VERSION) throw new IOException("los dos celulares tienen versiones distintas de Bóveda");
                            if (listener != null && !listener.approve(name)) throw new SecurityException("el otro celular no aceptó la conexión");
                            LinkWire.write(out, LinkWire.R_HELLO, m.id, LinkWire.build(o -> {
                                o.writeInt(LinkWire.VERSION);
                                o.writeUTF(deviceName);
                            }));
                        }
                        case LinkWire.LIST -> list(out, m);
                        case LinkWire.CANCEL -> { /* llegó tarde: ya no hay nada que cancelar */ }
                        case LinkWire.STAT -> stat(out, m);
                        case LinkWire.READ -> read(in, out, m);
                        case LinkWire.PUT_BEGIN -> {
                            if (sink != null) sink.abort();
                            putError = null;
                            DataInputStream d = m.in();
                            String path = d.readUTF();
                            long size = d.readLong(), mod = d.readLong();
                            LinkVfs.Source s = vfs.source(path);
                            if (s == null || LinkVfs.rest(path).isEmpty()) throw new SecurityException("ruta no permitida");
                            sink = s.create(LinkVfs.rest(path), size, mod);
                            if (listener != null) listener.onActivity("Recibiendo " + LinkVfs.norm(path));
                            LinkWire.write(out, LinkWire.R_OK, m.id, new byte[0]);
                        }
                        case LinkWire.PUT_DATA -> {
                            // sin respuesta por trozo (más rápido); si falla algo, se avisa al final
                            if (sink != null && putError == null) {
                                try {
                                    sink.stream().write(m.payload);
                                } catch (IOException e) {
                                    putError = e.getMessage() == null ? e.toString() : e.getMessage();
                                    sink.abort();
                                    sink = null;
                                }
                            }
                        }
                        case LinkWire.PUT_ABORT -> {
                            if (sink != null) sink.abort();
                            sink = null;
                            putError = null;
                            LinkWire.write(out, LinkWire.R_OK, m.id, new byte[0]);
                        }
                        case LinkWire.PUT_END -> {
                            if (putError != null) {
                                String pe = putError;
                                putError = null;
                                throw new IOException(pe);
                            }
                            if (sink == null) throw new IOException("falta el inicio del archivo");
                            String finalName = sink.finish();
                            sink = null;
                            LinkWire.write(out, LinkWire.R_OK, m.id, LinkWire.build(o -> o.writeUTF(finalName)));
                        }
                        case LinkWire.MKDIR -> {
                            String path = m.in().readUTF();
                            LinkVfs.Source s = vfs.source(path);
                            if (s == null) throw new SecurityException("ruta no permitida");
                            s.mkdir(LinkVfs.rest(path));
                            LinkWire.write(out, LinkWire.R_OK, m.id, new byte[0]);
                        }
                        case LinkWire.DELETE -> throw new SecurityException("por seguridad, el otro celular no puede borrar");
                        case LinkWire.BYE -> {
                            why = "el otro celular terminó";
                            stop = true;
                        }
                        default -> throw new IOException("mensaje no entendido (" + m.type + ")");
                    }
                } catch (SecurityException | IOException e) {
                    if (sink != null && (m.type == LinkWire.PUT_BEGIN || m.type == LinkWire.PUT_END)) {
                        sink.abort();
                        sink = null;
                    }
                    final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    LinkWire.write(out, LinkWire.R_ERROR, m.id, LinkWire.build(o -> o.writeUTF(msg)));
                }
            }
            if (sink != null) sink.abort();
        } catch (IOException e) {
            why = stop ? "desconectado" : "se cortó el cable (" + (e.getMessage() == null ? e : e.getMessage()) + ")";
        } finally {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
            if (listener != null) listener.onClosed(why);
        }
    }

    private void list(DataOutputStream out, LinkWire.Msg m) throws IOException {
        String path = LinkVfs.norm(m.in().readUTF());
        List<LinkVfs.Item> items = new java.util.ArrayList<>();
        if (path.isEmpty()) {
            for (LinkVfs.Source s : vfs.sources()) items.add(new LinkVfs.Item(s.name(), true, 0, 0));
        } else {
            LinkVfs.Source s = vfs.source(path);
            if (s == null) throw new SecurityException("esa carpeta no está compartida");
            items = s.list(LinkVfs.rest(path));
        }
        final List<LinkVfs.Item> fin = items;
        LinkWire.write(out, LinkWire.R_LIST, m.id, LinkWire.build(o -> {
            o.writeInt(fin.size());
            for (LinkVfs.Item it : fin) {
                o.writeUTF(it.name);
                o.writeBoolean(it.dir);
                o.writeLong(it.size);
                o.writeLong(it.modified);
            }
        }));
    }

    private void stat(DataOutputStream out, LinkWire.Msg m) throws IOException {
        String path = LinkVfs.norm(m.in().readUTF());
        LinkVfs.Item it = null;
        if (path.isEmpty()) it = new LinkVfs.Item("", true, 0, 0);
        else {
            LinkVfs.Source s = vfs.source(path);
            if (s == null) throw new SecurityException("esa carpeta no está compartida");
            it = LinkVfs.rest(path).isEmpty() ? new LinkVfs.Item(s.name(), true, 0, 0) : s.stat(LinkVfs.rest(path));
        }
        final LinkVfs.Item fin = it;
        LinkWire.write(out, LinkWire.R_STAT, m.id, LinkWire.build(o -> {
            o.writeBoolean(fin != null);
            if (fin != null) {
                o.writeUTF(fin.name);
                o.writeBoolean(fin.dir);
                o.writeLong(fin.size);
                o.writeLong(fin.modified);
            }
        }));
    }

    private void read(DataInputStream cmd, DataOutputStream out, LinkWire.Msg m) throws IOException {
        DataInputStream d = m.in();
        String path = d.readUTF();
        long offset = d.readLong(), len = d.readLong();
        LinkVfs.Source s = vfs.source(path);
        if (s == null || LinkVfs.rest(path).isEmpty()) throw new SecurityException("ruta no permitida");
        if (listener != null) listener.onActivity("Enviando " + LinkVfs.norm(path));
        long total = 0;
        try (InputStream in = s.open(LinkVfs.rest(path), offset)) {
            byte[] buf = new byte[LinkWire.CHUNK];
            while (!stop && (len < 0 || total < len)) {
                // ¿el otro celular canceló? (se mira entre trozo y trozo)
                if (cmd.available() >= 11) {
                    LinkWire.Msg c = LinkWire.read(cmd);
                    if (c.type == LinkWire.CANCEL && c.id == m.id) break;
                    if (c.type == LinkWire.BYE) {
                        stop = true;
                        break;
                    }
                }
                int want = (int) (len < 0 ? buf.length : Math.min(buf.length, len - total));
                int n = in.read(buf, 0, want);
                if (n < 0) break;
                byte[] chunk = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
                LinkWire.write(out, LinkWire.R_DATA, m.id, chunk);
                total += n;
            }
        }
        final long t = total;
        LinkWire.write(out, LinkWire.R_END, m.id, LinkWire.build(o -> o.writeLong(t)));
    }
}
