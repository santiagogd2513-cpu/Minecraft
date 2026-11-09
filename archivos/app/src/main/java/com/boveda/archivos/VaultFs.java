package com.boveda.archivos;

import android.content.Context;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.OsConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * La Bóveda como una carpeta más (/boveda/…). En el disco cada archivo o carpeta tiene un nombre al azar
 * (como «3f9a…»), y al lado un archivito «3f9a….n» con su nombre de verdad cifrado. El contenido de cada
 * archivo va cifrado con AES-256 en modo CTR, que deja saltar a cualquier parte (para ver videos sin
 * descifrar todo antes). Nada se descifra al disco: todo pasa por la memoria.
 */
final class VaultFs implements Fs {
    static final String ROOT = "/boveda";
    private static final byte[] DATA_MAGIC = "BVD1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NAME_MAGIC = "BVN1".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER = 4 + 16;
    private static final int BUF = 1 << 20;

    private static VaultFs instance;
    private final Context app;
    /** Lo que hay en cada carpeta real: nombre de verdad → elemento. */
    private final Map<String, Map<String, Item>> index = new ConcurrentHashMap<>();

    private static final class Item {
        final String id, name;
        final long modified;
        final File real, side;

        Item(String id, String name, long modified, File real, File side) {
            this.id = id;
            this.name = name;
            this.modified = modified;
            this.real = real;
            this.side = side;
        }
    }

    private VaultFs(Context c) {
        app = c.getApplicationContext();
    }

    static synchronized VaultFs get(Context c) {
        if (instance == null) instance = new VaultFs(c);
        return instance;
    }

    static boolean isVault(String path) {
        return path != null && Paths.under(path, ROOT);
    }

    /** Al cerrar la Bóveda se olvidan los nombres que había en memoria. */
    static synchronized void forget() {
        if (instance != null) instance.index.clear();
    }

    private static File realRoot() {
        File r = new File(Vault.DIR, "r");
        //noinspection ResultOfMethodCallIgnored
        r.mkdirs();
        return r;
    }

    private static byte[] dataKey() throws IOException {
        byte[] k = Vault.dataKey();
        if (k == null) throw new NeedAccess(ROOT);
        return k;
    }

    private static byte[] nameKey() throws IOException {
        byte[] k = Vault.nameKey();
        if (k == null) throw new NeedAccess(ROOT);
        return k;
    }

    // ------------------------------------------------------------------ nombres

    private Map<String, Item> dirIndex(File realDir) throws IOException {
        Map<String, Item> m = index.get(realDir.getPath());
        if (m != null) return m;
        byte[] nk = nameKey();
        m = new HashMap<>();
        File[] files = realDir.listFiles();
        if (files != null) {
            for (File side : files) {
                String n = side.getName();
                if (!n.endsWith(".n") || side.isDirectory()) continue;
                String id = n.substring(0, n.length() - 2);
                File real = new File(realDir, id);
                if (!real.exists()) continue;
                try {
                    JSONObject j = readSide(side, nk);
                    String name = j.getString("n");
                    m.put(name, new Item(id, name, j.optLong("m", real.lastModified()), real, side));
                } catch (Exception ignored) {
                    // un nombre dañado no tumba la carpeta entera
                }
            }
        }
        m = java.util.Collections.synchronizedMap(m);
        index.put(realDir.getPath(), m);
        return m;
    }

    private static JSONObject readSide(File side, byte[] nk) throws Exception {
        byte[] all = java.nio.file.Files.readAllBytes(side.toPath());
        for (int i = 0; i < 4; i++) if (all[i] != NAME_MAGIC[i]) throw new IOException();
        byte[] iv = java.util.Arrays.copyOfRange(all, 4, 16);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(nk, "AES"), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(c.doFinal(all, 16, all.length - 16), StandardCharsets.UTF_8));
    }

    private static void writeSide(File side, String name, long modified) throws IOException {
        try {
            JSONObject j = new JSONObject();
            j.put("n", name);
            j.put("m", modified);
            byte[] iv = Vault.random(12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(nameKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(j.toString().getBytes(StandardCharsets.UTF_8));
            File tmp = new File(side.getPath() + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(NAME_MAGIC);
                o.write(iv);
                o.write(enc);
                o.getFD().sync();
            }
            if (!tmp.renameTo(side)) throw new IOException(L.t("No se pudo guardar en la Bóveda"));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(L.t("Error de cifrado"), e);
        }
    }

    private static String newId() {
        StringBuilder sb = new StringBuilder();
        for (byte b : Vault.random(12)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** La carpeta real que corresponde a una ruta de la Bóveda; null si no existe. */
    private File realDir(String path) throws IOException {
        if (path.equals(ROOT)) return realRoot();
        Item it = item(path);
        return it != null && it.real.isDirectory() ? it.real : null;
    }

    private Item item(String path) throws IOException {
        if (path.equals(ROOT)) return null;
        File parent = realDir(Paths.parent(path));
        if (parent == null) return null;
        return dirIndex(parent).get(Paths.name(path));
    }

    private Item need(String path) throws IOException {
        Item it = item(path);
        if (it == null) throw new IOException(L.t("Ya no existe: ") + Paths.name(path));
        return it;
    }

    private static long plainSize(File real) {
        return Math.max(0, real.length() - HEADER);
    }

    private Entry entry(String path, Item it) {
        boolean dir = it.real.isDirectory();
        return new Entry(path, it.name, dir, dir ? 0 : plainSize(it.real), it.modified);
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public synchronized List<Entry> list(String dir) throws IOException {
        File real = realDir(dir);
        if (real == null) throw new IOException(L.t("No se puede leer esta carpeta"));
        List<Entry> out = new ArrayList<>();
        Map<String, Item> m = dirIndex(real);
        synchronized (m) {
            for (Item it : m.values()) out.add(entry(Paths.join(dir, it.name), it));
        }
        return out;
    }

    @Override
    public synchronized Entry stat(String path) throws IOException {
        if (path.equals(ROOT)) {
            nameKey();
            return new Entry(ROOT, L.t("La Bóveda"), true, 0, realRoot().lastModified());
        }
        Item it = item(path);
        return it == null ? null : entry(path, it);
    }

    @Override
    public InputStream in(String path) throws IOException {
        File real;
        synchronized (this) {
            real = need(path).real;
        }
        if (real.isDirectory()) throw new IOException(L.t("Es una carpeta"));
        FileInputStream f = new FileInputStream(real);
        try {
            byte[] head = new byte[HEADER];
            int got = 0;
            while (got < HEADER) {
                int r = f.read(head, got, HEADER - got);
                if (r < 0) break;
                got += r;
            }
            if (got == 0) {
                f.close();
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
            if (got < HEADER) throw new IOException(L.t("Archivo dañado o no compatible"));
            for (int i = 0; i < 4; i++) if (head[i] != DATA_MAGIC[i]) throw new IOException(L.t("Archivo dañado o no compatible"));
            Cipher c = ctr(Cipher.DECRYPT_MODE, java.util.Arrays.copyOfRange(head, 4, HEADER), 0);
            return new CryptIn(f, c);
        } catch (IOException e) {
            f.close();
            throw e;
        }
    }

    @Override
    public synchronized OutputStream out(String path) throws IOException {
        File parent = realDir(Paths.parent(path));
        if (parent == null) throw new IOException(L.t("Ya no existe: ") + Paths.name(Paths.parent(path)));
        String name = Paths.name(path);
        Map<String, Item> m = dirIndex(parent);
        Item it = m.get(name);
        if (it != null && it.real.isDirectory()) throw new IOException(L.t("Es una carpeta"));
        if (it == null) {
            String id = newId();
            it = new Item(id, name, System.currentTimeMillis(), new File(parent, id), new File(parent, id + ".n"));
            writeSide(it.side, name, it.modified);
            m.put(name, it);
        }
        byte[] iv = Vault.random(16);
        FileOutputStream f = new FileOutputStream(it.real);
        f.write(DATA_MAGIC);
        f.write(iv);
        return new CryptOut(f, ctr(Cipher.ENCRYPT_MODE, iv, 0));
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException(L.t("En la Bóveda solo se puede abrir para leer"));
        File real;
        synchronized (this) {
            real = need(path).real;
        }
        if (real.isDirectory()) throw new IOException(L.t("Es una carpeta"));
        byte[] iv = new byte[16];
        try (RandomAccessFile r = new RandomAccessFile(real, "r")) {
            if (r.length() >= HEADER) {
                r.seek(4);
                r.readFully(iv);
            }
        }
        byte[] key = dataKey().clone();
        long size = plainSize(real);
        HandlerThread t = new HandlerThread("boveda-lectura");
        t.start();
        StorageManager sm = app.getSystemService(StorageManager.class);
        RandomAccessFile raf = new RandomAccessFile(real, "r");
        return sm.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, new ProxyFileDescriptorCallback() {
            @Override
            public long onGetSize() {
                return size;
            }

            @Override
            public int onRead(long offset, int len, byte[] data) throws ErrnoException {
                try {
                    if (offset >= size) return 0;
                    len = (int) Math.min(len, size - offset);
                    long block = offset / 16;
                    int skip = (int) (offset % 16);
                    byte[] enc = new byte[len + skip];
                    raf.seek(HEADER + block * 16);
                    raf.readFully(enc);
                    Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
                    c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(counter(iv, block)));
                    byte[] plain = c.update(enc);
                    System.arraycopy(plain, skip, data, 0, len);
                    return len;
                } catch (Exception e) {
                    throw new ErrnoException("leer", OsConstants.EIO);
                }
            }

            @Override
            public void onRelease() {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
                java.util.Arrays.fill(key, (byte) 0);
                t.quitSafely();
            }
        }, new android.os.Handler(t.getLooper()));
    }

    @Override
    public synchronized void mkdir(String path) throws IOException {
        File parent = realDir(Paths.parent(path));
        if (parent == null) throw new IOException(L.t("No se pudo crear la carpeta"));
        String name = Paths.name(path);
        Map<String, Item> m = dirIndex(parent);
        Item it = m.get(name);
        if (it != null) {
            if (it.real.isDirectory()) return;
            throw new IOException(L.t("Ya existe algo con ese nombre"));
        }
        String id = newId();
        File real = new File(parent, id);
        if (!real.mkdir()) throw new IOException(L.t("No se pudo crear la carpeta"));
        long now = System.currentTimeMillis();
        File side = new File(parent, id + ".n");
        writeSide(side, name, now);
        m.put(name, new Item(id, name, now, real, side));
    }

    @Override
    public synchronized void delete(String path) throws IOException {
        if (path.equals(ROOT)) throw new IOException(L.t("No se pudo borrar: ") + L.t("La Bóveda"));
        Item it = item(path);
        if (it == null) return;
        wipe(it.real);
        if (!it.side.delete() && it.side.exists()) throw new IOException(L.t("No se pudo borrar: ") + it.name);
        Map<String, Item> m = index.get(it.real.getParent());
        if (m != null) m.remove(it.name);
        if (it.real.isDirectory()) index.remove(it.real.getPath());
    }

    private void wipe(File f) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) wipe(k);
            index.remove(f.getPath());
        }
        if (!f.delete() && f.exists()) throw new IOException(L.t("No se pudo borrar: ") + f.getName());
    }

    @Override
    public synchronized void rename(String from, String to) throws IOException {
        Item it = need(from);
        File newParent = realDir(Paths.parent(to));
        if (newParent == null) throw new IOException(L.t("No se pudo mover"));
        String newName = Paths.name(to);
        Map<String, Item> dst = dirIndex(newParent);
        Item existing = dst.get(newName);
        if (existing != null && existing != it) {
            if (existing.real.isDirectory()) throw new IOException(L.t("Ya existe algo con ese nombre"));
            delete(to);
        }
        Map<String, Item> src = dirIndex(it.real.getParentFile());
        File real = it.real, side = it.side;
        if (!newParent.equals(it.real.getParentFile())) {
            real = new File(newParent, it.id);
            side = new File(newParent, it.id + ".n");
            if (!it.real.renameTo(real)) throw new IOException(L.t("No se pudo mover"));
            if (!it.side.renameTo(side)) {
                //noinspection ResultOfMethodCallIgnored
                real.renameTo(it.real);
                throw new IOException(L.t("No se pudo mover"));
            }
            if (it.real.isDirectory() || real.isDirectory()) {
                // lo de adentro cambió de carpeta real: que se vuelva a leer
                index.keySet().removeIf(k -> k.startsWith(it.real.getPath()));
            }
        }
        writeSide(side, newName, it.modified);
        src.remove(it.name);
        dst.put(newName, new Item(it.id, newName, it.modified, real, side));
    }

    @Override
    public synchronized void setModified(String path, long time) {
        try {
            Item it = item(path);
            if (it == null) return;
            writeSide(it.side, it.name, time);
            Map<String, Item> m = index.get(it.real.getParent());
            if (m != null) m.put(it.name, new Item(it.id, it.name, time, it.real, it.side));
        } catch (IOException ignored) {
        }
    }

    @Override
    public String key(String path) {
        return "boveda";
    }

    // ------------------------------------------------------------------ cifrado

    private static byte[] counter(byte[] iv, long block) {
        byte[] c = iv.clone();
        long add = block;
        for (int i = 15; i >= 0 && add != 0; i--) {
            long v = (c[i] & 0xFF) + (add & 0xFF);
            c[i] = (byte) v;
            add = (add >>> 8) + (v >>> 8);
        }
        return c;
    }

    private static Cipher ctr(int mode, byte[] iv, long block) throws IOException {
        try {
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(mode, new SecretKeySpec(dataKey(), "AES"), new IvParameterSpec(counter(iv, block)));
            return c;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(L.t("Error de cifrado"), e);
        }
    }

    /** Lee descifrando en bloques grandes (el CipherInputStream de Android va de a 512 bytes y es lento). */
    private static final class CryptIn extends InputStream {
        private final InputStream in;
        private final Cipher c;
        private final byte[] raw = new byte[BUF];
        private byte[] plain = new byte[0];
        private int pos;
        private boolean eof;

        CryptIn(InputStream in, Cipher c) {
            this.in = in;
            this.c = c;
        }

        private boolean fill() throws IOException {
            while (pos >= plain.length) {
                if (eof) return false;
                int r = in.read(raw);
                if (r < 0) {
                    eof = true;
                    return false;
                }
                byte[] p = c.update(raw, 0, r);
                plain = p == null ? new byte[0] : p;
                pos = 0;
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            return fill() ? plain[pos++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int n = Math.min(len, plain.length - pos);
            System.arraycopy(plain, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** Escribe cifrando. Al cerrar se asegura de que quedó guardado en la memoria. */
    private static final class CryptOut extends OutputStream {
        private final FileOutputStream out;
        private final Cipher c;
        private boolean closed;

        CryptOut(FileOutputStream out, Cipher c) {
            this.out = out;
            this.c = c;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] e = c.update(b, off, len);
            if (e != null) out.write(e);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                byte[] e = c.doFinal();
                if (e != null && e.length > 0) out.write(e);
                out.getFD().sync();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(L.t("Error de cifrado"), e);
            } finally {
                out.close();
            }
        }
    }
}
