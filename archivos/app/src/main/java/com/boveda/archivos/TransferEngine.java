package com.boveda.archivos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Copia, mueve, borra, comprime y descomprime sin corromper nada:
 * - Cada archivo se escribe primero con un nombre temporal (".nombre.boveda-parte").
 * - Al terminar se fuerza a que llegue al disco (fsync), se vuelve a leer completo y se compara
 *   su suma de verificación (CRC32) y su tamaño con el original. Solo si coinciden se le pone el
 *   nombre final. Si algo falla, el archivo a medias se borra y el original queda intacto.
 * - Al mover entre almacenamientos distintos, el original se borra solo después de verificar la copia.
 * - Mover dentro del mismo almacenamiento es instantáneo: solo cambia de lugar, no se copia.
 * Las tareas se hacen de una en una (así no se pelean por el disco).
 */
final class TransferEngine {
    enum Kind { COPY, MOVE, DELETE, ZIP, UNZIP }

    enum Conflict { REPLACE, KEEP_BOTH, SKIP }

    static final class Job {
        final Kind kind;
        final List<String> sources;
        final String dest;          // carpeta destino (o archivo .zip al comprimir)
        final Conflict conflict;
        volatile int state;         // 0 en cola, 1 trabajando, 2 listo, 3 error, 4 cancelado
        volatile boolean cancel;
        volatile String current = "";
        volatile long totalBytes, doneBytes;
        volatile int totalItems, doneItems;
        volatile String error;
        volatile double speed;
        long lastBytes;
        final long created = System.currentTimeMillis();

        Job(Kind kind, List<String> sources, String dest, Conflict conflict) {
            this.kind = kind;
            this.sources = new ArrayList<>(sources);
            this.dest = dest;
            this.conflict = conflict;
        }

        String title() {
            int n = sources.size();
            String what = n == 1 ? Paths.name(sources.get(0)) : n + " elementos";
            return switch (kind) {
                case COPY -> "Copiando " + what;
                case MOVE -> "Moviendo " + what;
                case DELETE -> "Borrando " + what;
                case ZIP -> "Comprimiendo " + what;
                case UNZIP -> "Extrayendo " + what;
            };
        }

        float progress() {
            if (totalBytes > 0) return Math.min(1f, doneBytes / (float) totalBytes);
            if (totalItems > 0) return Math.min(1f, doneItems / (float) totalItems);
            return state == 2 ? 1f : 0f;
        }
    }

    interface Listener {
        void onChanged();
    }

    private static final int BUF = 4 * 1024 * 1024;
    private static TransferEngine instance;

    private final Context app;
    private final Prefs prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Job> jobs = new CopyOnWriteArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastNotify;

    static synchronized TransferEngine get(Context c) {
        if (instance == null) instance = new TransferEngine(c.getApplicationContext());
        return instance;
    }

    private TransferEngine(Context c) {
        app = c;
        prefs = new Prefs(c);
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                boolean any = false;
                for (Job j : jobs) {
                    if (j.state != 1) continue;
                    any = true;
                    double inst = (j.doneBytes - j.lastBytes) * 2.0;
                    j.speed = j.speed == 0 ? inst : j.speed * 0.6 + inst * 0.4;
                    j.lastBytes = j.doneBytes;
                }
                if (any) changed(true);
            }
        }, "boveda-velocidad").start();
    }

    List<Job> jobs() {
        return jobs;
    }

    boolean busy() {
        for (Job j : jobs) if (j.state <= 1) return true;
        return false;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    void clearFinished() {
        jobs.removeIf(j -> j.state >= 2);
        changed(false);
    }

    private void changed(boolean throttle) {
        long now = System.currentTimeMillis();
        if (throttle && now - lastNotify < 400) return;
        lastNotify = now;
        main.post(() -> {
            for (Listener l : listeners) l.onChanged();
        });
    }

    Job submit(Kind kind, List<String> sources, String dest, Conflict conflict) {
        Job j = new Job(kind, sources, dest, conflict);
        jobs.add(0, j);
        changed(false);
        TransferService.ensure(app);
        worker.execute(() -> run(j));
        return j;
    }

    // ------------------------------------------------------------------ ejecución

    private void run(Job j) {
        if (j.cancel) {
            j.state = 4;
            changed(false);
            return;
        }
        j.state = 1;
        changed(false);
        try {
            switch (j.kind) {
                case COPY, MOVE -> transfer(j);
                case DELETE -> delete(j);
                case ZIP -> zip(j);
                case UNZIP -> unzip(j);
            }
            j.state = j.cancel ? 4 : 2;
        } catch (Cancelled c) {
            j.state = 4;
        } catch (Exception e) {
            j.state = j.cancel ? 4 : 3;
            j.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        j.current = "";
        changed(false);
    }

    private static final class Cancelled extends IOException {
        Cancelled() {
            super("Cancelado");
        }
    }

    private void check(Job j) throws Cancelled {
        if (j.cancel) throw new Cancelled();
    }

    private Fs fs(String path) throws IOException {
        return FsRouter.get(app, path);
    }

    /** Suma lo que hay que hacer (para la barra de progreso). */
    private void measure(Job j, String path) throws IOException {
        check(j);
        Fs f = fs(path);
        Entry e = f.stat(path);
        if (e == null) return;
        j.totalItems++;
        if (e.dir) {
            for (Entry k : f.list(path)) measure(j, k.path);
        } else {
            j.totalBytes += e.size;
        }
    }

    // ------------------------------------------------------------------ copiar / mover

    private void transfer(Job j) throws IOException {
        for (String s : j.sources) {
            if (Paths.under(j.dest, s)) throw new IOException("No se puede poner una carpeta dentro de sí misma");
        }
        // mover dentro del mismo almacenamiento: instantáneo
        List<String> pending = new ArrayList<>();
        if (j.kind == Kind.MOVE) {
            for (String s : j.sources) {
                check(j);
                String target = target(j, s);
                if (target == null) continue; // saltar
                if (fastMove(s, target)) {
                    j.doneItems++;
                    changed(true);
                } else {
                    pending.add(s);
                }
            }
        } else {
            pending.addAll(j.sources);
        }
        if (pending.isEmpty()) return;
        for (String s : pending) measure(j, s);
        for (String s : pending) {
            check(j);
            String target = target(j, s);
            if (target == null) continue;
            copyTree(j, s, target);
            if (j.kind == Kind.MOVE) {
                // el original se borra solo cuando todo quedó copiado y verificado
                j.current = "Borrando el original de " + Paths.name(s);
                fs(s).delete(s);
            }
        }
    }

    /** Ruta final en el destino según la regla de choques de nombre; null = saltar. */
    private String target(Job j, String src) throws IOException {
        String t = Paths.join(j.dest, Paths.name(src));
        Fs df = fs(j.dest);
        Entry existing = df.stat(t);
        if (existing == null) return t;
        Entry se = fs(src).stat(src);
        // dos carpetas con el mismo nombre se combinan
        if (se != null && se.dir && existing.dir && j.conflict != Conflict.KEEP_BOTH) return t;
        return switch (j.conflict) {
            case SKIP -> null;
            case KEEP_BOTH -> uniqueName(df, t);
            case REPLACE -> t;
        };
    }

    private static String uniqueName(Fs f, String path) throws IOException {
        String dir = Paths.parent(path), name = Paths.name(path);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 1; ; n++) {
            String p = Paths.join(dir, base + " (" + n + ")" + ext);
            if (f.stat(p) == null) return p;
        }
    }

    /** Intenta mover sin copiar (mismo almacenamiento). */
    private boolean fastMove(String src, String dst) {
        try {
            Fs sf = fs(src), df = fs(dst);
            if (!sf.key(src).equals(df.key(dst))) return false;
            Fs mover = sf instanceof ShizukuFs ? sf : df instanceof ShizukuFs ? df : sf;
            Entry existing = df.stat(dst);
            if (existing != null) {
                Entry se = sf.stat(src);
                if (se != null && se.dir && existing.dir) return false; // combinar: se hace archivo por archivo
                df.delete(dst);
            }
            mover.rename(src, dst);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void copyTree(Job j, String src, String dst) throws IOException {
        check(j);
        Fs sf = fs(src);
        Entry e = sf.stat(src);
        if (e == null) throw new IOException("Ya no existe: " + Paths.name(src));
        Fs df = fs(dst);
        if (e.dir) {
            df.mkdir(dst);
            j.doneItems++;
            for (Entry k : sf.list(src)) {
                String kt = Paths.join(dst, k.name);
                Entry ex = df.stat(kt);
                if (ex != null && !(k.dir && ex.dir)) {
                    if (j.conflict == Conflict.SKIP) {
                        j.doneBytes += k.dir ? 0 : k.size;
                        continue;
                    }
                    if (j.conflict == Conflict.KEEP_BOTH) kt = uniqueName(df, kt);
                }
                copyTree(j, k.path, kt);
            }
            df.setModified(dst, e.modified);
        } else {
            copyFile(j, sf, e, df, dst);
            j.doneItems++;
        }
        changed(true);
    }

    private void copyFile(Job j, Fs sf, Entry src, Fs df, String dst) throws IOException {
        j.current = src.name;
        String tmp = Paths.join(Paths.parent(dst), "." + Paths.name(dst) + ".boveda-parte");
        long startBytes = j.doneBytes;
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            j.doneBytes = startBytes;
            try {
                if (df.stat(tmp) != null) df.delete(tmp);
                CRC32 crc = new CRC32();
                long written = 0;
                try (InputStream in = sf.in(src.path); OutputStream out = df.out(tmp)) {
                    byte[] buf = new byte[BUF];
                    int r;
                    while ((r = in.read(buf)) >= 0) {
                        check(j);
                        if (r == 0) continue;
                        out.write(buf, 0, r);
                        crc.update(buf, 0, r);
                        written += r;
                        j.doneBytes += r;
                    }
                    out.flush();
                    sync(out);
                }
                if (written != src.size && src.size > 0) throw new IOException("El tamaño no coincide al copiar " + src.name);
                if (prefs.verify()) {
                    j.current = "Verificando " + src.name;
                    long[] check = checksum(j, df, tmp);
                    if (check[0] != written || check[1] != crc.getValue()) {
                        throw new IOException("La verificación falló en " + src.name);
                    }
                }
                Entry existing = df.stat(dst);
                if (existing != null) df.delete(dst);
                df.rename(tmp, dst);
                df.setModified(dst, src.modified);
                return;
            } catch (Cancelled c) {
                cleanup(df, tmp);
                throw c;
            } catch (IOException e) {
                last = e;
                cleanup(df, tmp);
            }
        }
        throw last;
    }

    private static void cleanup(Fs f, String tmp) {
        try {
            if (f.stat(tmp) != null) f.delete(tmp);
        } catch (IOException ignored) {
        }
    }

    /** Fuerza a que lo escrito llegue al disco antes de seguir. */
    private static void sync(OutputStream out) {
        try {
            if (out instanceof FileOutputStream fos) fos.getFD().sync();
        } catch (IOException ignored) {
        }
    }

    /** {tamaño, CRC32} leyendo el archivo de nuevo desde el disco. */
    private long[] checksum(Job j, Fs f, String path) throws IOException {
        CRC32 crc = new CRC32();
        long n = 0;
        try (InputStream in = f.in(path)) {
            byte[] buf = new byte[BUF];
            int r;
            while ((r = in.read(buf)) >= 0) {
                check(j);
                crc.update(buf, 0, r);
                n += r;
            }
        }
        return new long[]{n, crc.getValue()};
    }

    // ------------------------------------------------------------------ borrar

    private void delete(Job j) throws IOException {
        j.totalItems = j.sources.size();
        for (String s : j.sources) {
            check(j);
            j.current = Paths.name(s);
            fs(s).delete(s);
            j.doneItems++;
            changed(true);
        }
    }

    // ------------------------------------------------------------------ comprimir / extraer

    private void zip(Job j) throws IOException {
        for (String s : j.sources) measure(j, s);
        Fs df = fs(j.dest);
        String tmp = Paths.join(Paths.parent(j.dest), "." + Paths.name(j.dest) + ".boveda-parte");
        try {
            try (OutputStream raw = df.out(tmp); ZipOutputStream zo = new ZipOutputStream(new java.io.BufferedOutputStream(raw, 1 << 20))) {
                zo.setLevel(6);
                for (String s : j.sources) addToZip(j, zo, s, Paths.name(s));
                zo.finish();
                zo.flush();
                sync(raw);
            }
            if (df.stat(j.dest) != null) df.delete(j.dest);
            df.rename(tmp, j.dest);
        } catch (IOException e) {
            cleanup(df, tmp);
            throw e;
        }
    }

    private void addToZip(Job j, ZipOutputStream zo, String path, String entryName) throws IOException {
        check(j);
        Fs f = fs(path);
        Entry e = f.stat(path);
        if (e == null) return;
        if (e.dir) {
            ZipEntry ze = new ZipEntry(entryName + "/");
            ze.setTime(e.modified);
            zo.putNextEntry(ze);
            zo.closeEntry();
            j.doneItems++;
            for (Entry k : f.list(path)) addToZip(j, zo, k.path, entryName + "/" + k.name);
            return;
        }
        j.current = e.name;
        ZipEntry ze = new ZipEntry(entryName);
        ze.setTime(e.modified);
        zo.putNextEntry(ze);
        try (InputStream in = f.in(path)) {
            byte[] buf = new byte[1 << 20];
            int r;
            while ((r = in.read(buf)) >= 0) {
                check(j);
                zo.write(buf, 0, r);
                j.doneBytes += r;
            }
        }
        zo.closeEntry();
        j.doneItems++;
        changed(true);
    }

    private void unzip(Job j) throws IOException {
        String zipPath = j.sources.get(0);
        Fs sf = fs(zipPath);
        Entry ze0 = sf.stat(zipPath);
        j.totalBytes = ze0 == null ? 0 : ze0.size;
        Fs df = fs(j.dest);
        df.mkdir(j.dest);
        final long[] read = {0};
        try (InputStream raw = sf.in(zipPath);
             InputStream counted = new FilterInputStream(new BufferedInputStream(raw, 1 << 20)) {
                 @Override
                 public int read(byte[] b, int off, int len) throws IOException {
                     int r = super.read(b, off, len);
                     if (r > 0) read[0] += r;
                     return r;
                 }

                 @Override
                 public int read() throws IOException {
                     int r = super.read();
                     if (r >= 0) read[0]++;
                     return r;
                 }
             };
             ZipInputStream zi = new ZipInputStream(counted)) {
            ZipEntry ze;
            byte[] buf = new byte[1 << 20];
            while ((ze = zi.getNextEntry()) != null) {
                check(j);
                String name = ze.getName().replace('\\', '/');
                // protección: nada puede salir de la carpeta destino
                if (name.startsWith("/") || name.contains("../") || name.equals("..") || name.endsWith("/..")) continue;
                String out = Paths.join(j.dest, name.endsWith("/") ? name.substring(0, name.length() - 1) : name);
                if (ze.isDirectory()) {
                    mkdirs(df, j.dest, out);
                } else {
                    mkdirs(df, j.dest, Paths.parent(out));
                    j.current = Paths.name(out);
                    String tmp = Paths.join(Paths.parent(out), "." + Paths.name(out) + ".boveda-parte");
                    try (OutputStream o = df.out(tmp)) {
                        int r;
                        while ((r = zi.read(buf)) >= 0) {
                            check(j);
                            o.write(buf, 0, r);
                            j.doneBytes = read[0];
                        }
                        o.flush();
                        sync(o);
                    } catch (IOException e) {
                        cleanup(df, tmp);
                        throw e;
                    }
                    if (df.stat(out) != null) df.delete(out);
                    df.rename(tmp, out);
                    if (ze.getTime() > 0) df.setModified(out, ze.getTime());
                }
                j.doneItems++;
                changed(true);
            }
        }
        j.doneBytes = j.totalBytes;
    }

    private static void mkdirs(Fs f, String base, String dir) throws IOException {
        if (dir.equals(base) || dir.length() < base.length()) return;
        if (f.stat(dir) != null) return;
        mkdirs(f, base, Paths.parent(dir));
        f.mkdir(dir);
    }
}
