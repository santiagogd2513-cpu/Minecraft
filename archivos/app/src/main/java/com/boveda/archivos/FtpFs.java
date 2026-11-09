package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un servidor FTP visto como una carpeta más de Bóveda (/ftp/<id>/…): se puede listar, copiar,
 * mover, borrar, renombrar, comprimir y extraer igual que en el celular.
 * Usa pocas conexiones a la vez (la Xbox 360 solo acepta una).
 */
final class FtpFs implements Fs {
    private final FtpServers.Server server;
    private final String root;
    private final List<FtpClient> idle = new ArrayList<>();
    private int open;
    private final ThreadLocal<Integer> held = ThreadLocal.withInitial(() -> 0);
    private final Map<String, Object[]> cache = new HashMap<>();   // carpeta → {hora, lista}

    FtpFs(FtpServers.Server s) {
        server = s;
        root = s.root();
    }

    private String remote(String path) {
        String r = path.length() <= root.length() ? "/" : path.substring(root.length());
        return r.isEmpty() ? "/" : r;
    }

    // ------------------------------------------------------------------ conexiones

    private FtpClient acquire() throws IOException {
        int max = Math.max(1, server.maxConnections);
        if (held.get() >= max) {
            throw new IOException("«" + server.name + L.t("» solo acepta ") + max + (max == 1 ? L.t(" conexión") : L.t(" conexiones"))
                    + L.t(" a la vez: copia primero al celular y de ahí al servidor"));
        }
        long deadline = System.currentTimeMillis() + 90000;
        synchronized (this) {
            while (true) {
                while (!idle.isEmpty()) {
                    FtpClient c = idle.remove(idle.size() - 1);
                    if (c.alive()) {
                        held.set(held.get() + 1);
                        return c;
                    }
                    c.close();
                    open--;
                }
                if (open < max) {
                    open++;
                    break;
                }
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) throw new IOException(L.t("El servidor está ocupado con otra transferencia"));
                try {
                    wait(Math.min(wait, 1000));
                } catch (InterruptedException e) {
                    throw new IOException(L.t("Cancelado"));
                }
            }
        }
        try {
            FtpClient c = new FtpClient(server.host, server.port, server.passive,
                    server.latin1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
            c.connect(server.user, server.pass);
            held.set(held.get() + 1);
            return c;
        } catch (IOException e) {
            synchronized (this) {
                open--;
                notifyAll();
            }
            if (e.getMessage() != null && e.getMessage().contains("connect")) {
                throw new IOException(L.t("No se pudo conectar a ") + server.host + ":" + server.port
                        + L.t(" (¿están en la misma red y el servidor FTP está encendido?)"), e);
            }
            throw e;
        }
    }

    private void release(FtpClient c, boolean broken) {
        held.set(Math.max(0, held.get() - 1));
        synchronized (this) {
            if (broken || c.busy()) {
                c.close();
                open--;
            } else {
                idle.add(c);
            }
            notifyAll();
        }
    }

    synchronized void closeAll() {
        for (FtpClient c : idle) c.close();
        idle.clear();
        open = 0;
    }

    private interface Op<T> {
        T run(FtpClient c) throws IOException;
    }

    private <T> T with(Op<T> op) throws IOException {
        FtpClient c = acquire();
        boolean broken = false;
        try {
            return op.run(c);
        } catch (FtpClient.FtpException e) {
            throw e;            // error del servidor: la conexión sigue bien
        } catch (IOException e) {
            broken = true;
            throw e;
        } finally {
            release(c, broken);
        }
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public List<Entry> list(String dir) throws IOException {
        List<FtpClient.Item> items = with(c -> c.list(remote(dir)));
        List<Entry> out = new ArrayList<>(items.size());
        for (FtpClient.Item it : items) out.add(new Entry(Paths.join(dir, it.name), it.name, it.dir, it.size, it.time));
        synchronized (cache) {
            cache.put(dir, new Object[]{System.currentTimeMillis(), out});
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Entry> cachedList(String dir) throws IOException {
        synchronized (cache) {
            Object[] c = cache.get(dir);
            if (c != null && System.currentTimeMillis() - (Long) c[0] < 3000) return (List<Entry>) c[1];
        }
        return list(dir);
    }

    private void forget(String path) {
        synchronized (cache) {
            cache.remove(Paths.parent(path));
            cache.remove(path);
        }
    }

    @Override
    public Entry stat(String path) throws IOException {
        if (path.equals(root) || remote(path).equals("/")) return new Entry(path, server.name, true, 0, 0);
        String parent = Paths.parent(path), name = Paths.name(path);
        try {
            for (Entry e : cachedList(parent)) if (e.name.equals(name)) return e;
        } catch (FtpClient.FtpException e) {
            return null;
        }
        return null;
    }

    @Override
    public InputStream in(String path) throws IOException {
        FtpClient c = acquire();
        InputStream raw;
        try {
            raw = c.retrieve(remote(path), 0);
        } catch (IOException e) {
            release(c, !(e instanceof FtpClient.FtpException));
            throw e;
        }
        return new FilterInputStream(raw) {
            boolean done;

            @Override
            public void close() throws IOException {
                if (done) return;
                done = true;
                boolean broken = false;
                try {
                    super.close();
                } catch (IOException e) {
                    broken = true;
                    throw e;
                } finally {
                    release(c, broken);
                }
            }
        };
    }

    @Override
    public OutputStream out(String path) throws IOException {
        forget(path);
        FtpClient c = acquire();
        OutputStream raw;
        try {
            raw = c.store(remote(path));
        } catch (IOException e) {
            release(c, !(e instanceof FtpClient.FtpException));
            throw e;
        }
        return new FilterOutputStream(raw) {
            boolean done;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (done) return;
                done = true;
                boolean broken = false;
                try {
                    super.close();  // espera la confirmación del servidor
                } catch (IOException e) {
                    broken = true;
                    throw e;
                } finally {
                    release(c, broken);
                    forget(path);
                }
            }
        };
    }

    /** Solo lectura: se baja a la carpeta temporal y se abre desde ahí (para abrir o ver un .zip). */
    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException(L.t("En FTP solo se puede abrir para leer"));
        File dir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"), "ftp");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, server.id + "_" + Integer.toHexString(path.hashCode()) + "_" + Paths.name(path));
        Entry e = stat(path);
        if (!(f.exists() && e != null && f.length() == e.size)) {
            try (InputStream in = in(path); FileOutputStream o = new FileOutputStream(f)) {
                byte[] buf = new byte[1 << 20];
                int r;
                while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public void mkdir(String path) throws IOException {
        forget(path);
        with(c -> {
            c.mkdir(remote(path));
            return null;
        });
    }

    @Override
    public void delete(String path) throws IOException {
        Entry e = stat(path);
        if (e == null) return;
        if (e.dir) {
            for (Entry k : list(path)) delete(k.path);
            with(c -> {
                c.rmdir(remote(path));
                return null;
            });
        } else {
            with(c -> {
                c.deleteFile(remote(path));
                return null;
            });
        }
        forget(path);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        forget(from);
        forget(to);
        with(c -> {
            c.rename(remote(from), remote(to));
            return null;
        });
    }

    @Override
    public void setModified(String path, long time) {
        try {
            with(c -> {
                c.setModified(remote(path), time);
                return null;
            });
        } catch (IOException ignored) {
        }
    }

    @Override
    public String key(String path) {
        return "ftp:" + server.id;
    }
}
