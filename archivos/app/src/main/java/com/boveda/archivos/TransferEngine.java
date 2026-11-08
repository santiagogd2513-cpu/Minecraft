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
    enum Kind { COPY, MOVE, DELETE, ZIP, EXTRACT, CLOUD_DOWN, CLOUD_UP }

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
        volatile long started, finished;
        /** Al extraer: qué cosas de adentro (vacío = todo) y qué carpeta interna se quita del nombre. */
        List<String> picks = new ArrayList<>();
        String innerBase = "";
        /** MediaFire: qué bajar, o a qué carpeta subir. */
        List<MediaFire.Item> cloudItems = new ArrayList<>();
        String cloudFolder;
        String cloudFolderName = "MediaFire";

        Job(Kind kind, List<String> sources, String dest, Conflict conflict) {
            this.kind = kind;
            this.sources = new ArrayList<>(sources);
            this.dest = dest;
            this.conflict = conflict;
        }

        String title() {
            if (kind == Kind.CLOUD_DOWN) {
                int n = cloudItems.size();
                return "Bajando de MediaFire " + (n == 1 ? cloudItems.get(0).name : n + " elementos");
            }
            int n = sources.size();
            String what = n == 1 ? Paths.name(sources.get(0)) : n + " elementos";
            if (kind == Kind.CLOUD_UP) return "Subiendo a MediaFire " + what;
            return switch (kind) {
                case COPY -> "Copiando " + what;
                case MOVE -> "Moviendo " + what;
                case DELETE -> "Borrando " + what;
                case ZIP -> "Comprimiendo " + what;
                case EXTRACT -> "Extrayendo " + what;
                default -> what;
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
        return submit(new Job(kind, sources, dest, conflict));
    }

    /** Extraer de un archivo comprimido: todo (picks vacío) o solo lo elegido. */
    Job submitExtract(String archive, List<String> picks, String innerBase, String dest) {
        Job j = new Job(Kind.EXTRACT, java.util.Collections.singletonList(archive), dest, Conflict.REPLACE);
        j.picks = new ArrayList<>(picks);
        j.innerBase = innerBase == null ? "" : innerBase;
        return submit(j);
    }

    Job submitCloudDownload(List<MediaFire.Item> items, String dest) {
        Job j = new Job(Kind.CLOUD_DOWN, new ArrayList<>(), dest, Conflict.KEEP_BOTH);
        j.cloudItems = new ArrayList<>(items);
        return submit(j);
    }

    Job submitCloudUpload(List<String> sources, String folderKey, String folderName) {
        Job j = new Job(Kind.CLOUD_UP, sources, null, Conflict.KEEP_BOTH);
        j.cloudFolder = folderKey;
        j.cloudFolderName = folderName;
        return submit(j);
    }

    private Job submit(Job j) {
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
        j.started = System.currentTimeMillis();
        j.state = 1;
        changed(false);
        try {
            switch (j.kind) {
                case COPY, MOVE -> transfer(j);
                case DELETE -> delete(j);
                case ZIP -> zip(j);
                case EXTRACT -> extract(j);
                case CLOUD_DOWN -> cloudDown(j);
                case CLOUD_UP -> cloudUp(j);
            }
            j.state = j.cancel ? 4 : 2;
        } catch (Cancelled c) {
            j.state = 4;
        } catch (Throwable e) {
            // cualquier error (incluso falta de memoria) deja la tarea en error, sin cerrar la app
            j.state = j.cancel ? 4 : 3;
            j.error = e instanceof OutOfMemoryError ? "Sin memoria suficiente"
                    : e instanceof StackOverflowError ? "Demasiadas carpetas una dentro de otra"
                    : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        j.current = "";
        j.finished = System.currentTimeMillis();
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

    private void extract(Job j) throws IOException {
        String arch = j.sources.get(0);
        Archive.Type type = Archive.type(Paths.name(arch));
        if (type == null || Archive.isRar(Paths.name(arch))) throw new IOException("Formato no soportado");
        // para ZIP, 7Z e ISO el índice es rápido de leer: así la barra muestra el total real
        if (type == Archive.Type.ZIP || type == Archive.Type.SEVEN_Z || type == Archive.Type.ISO) {
            for (Archive.Item it : Archive.list(app, arch)) {
                if (!wanted(j, it.name)) continue;
                j.totalItems++;
                if (!it.dir) j.totalBytes += Math.max(0, it.size);
            }
        }
        Fs df = fs(j.dest);
        mkdirsAbsolute(df, j.dest);
        byte[] buf = new byte[1 << 20];
        Archive.walk(app, arch, (it, data) -> {
            check(j);
            if (!wanted(j, it.name)) return true;
            String rel = it.name;
            if (!j.innerBase.isEmpty() && rel.startsWith(j.innerBase + "/")) rel = rel.substring(j.innerBase.length() + 1);
            if (rel.isEmpty() || !Archive.safe(rel)) return true;
            String out = Paths.join(j.dest, rel);
            if (it.dir) {
                mkdirs(df, j.dest, out);
            } else {
                mkdirs(df, j.dest, Paths.parent(out));
                j.current = Paths.name(out);
                String tmp = Paths.join(Paths.parent(out), "." + Paths.name(out) + ".boveda-parte");
                try (OutputStream o = df.out(tmp)) {
                    if (data != null) {
                        int r;
                        while ((r = data.read(buf)) >= 0) {
                            check(j);
                            o.write(buf, 0, r);
                            j.doneBytes += r;
                        }
                    }
                    o.flush();
                    sync(o);
                } catch (IOException e) {
                    cleanup(df, tmp);
                    throw e;
                }
                if (df.stat(out) != null) df.delete(out);
                df.rename(tmp, out);
                if (it.time > 0) df.setModified(out, it.time);
            }
            j.doneItems++;
            changed(true);
            return true;
        });
        if (j.totalBytes > 0) j.doneBytes = j.totalBytes;
    }

    // ------------------------------------------------------------------ MediaFire

    private void cloudMeasure(Job j, MediaFire mf, MediaFire.Item it) throws IOException {
        check(j);
        j.totalItems++;
        if (it.folder) for (MediaFire.Item k : mf.list(it.key)) cloudMeasure(j, mf, k);
        else j.totalBytes += it.size;
    }

    private void cloudDown(Job j) throws IOException {
        MediaFire mf = MediaFire.get(app);
        j.current = "Revisando…";
        for (MediaFire.Item it : j.cloudItems) cloudMeasure(j, mf, it);
        Fs df = fs(j.dest);
        mkdirsAbsolute(df, j.dest);
        for (MediaFire.Item it : j.cloudItems) cloudDownItem(j, mf, it, j.dest, df);
    }

    private void cloudDownItem(Job j, MediaFire mf, MediaFire.Item it, String dir, Fs df) throws IOException {
        check(j);
        String target = Paths.join(dir, it.name.replace('/', '_'));
        if (it.folder) {
            if (df.stat(target) == null) df.mkdir(target);
            j.doneItems++;
            for (MediaFire.Item k : mf.list(it.key)) cloudDownItem(j, mf, k, target, df);
            return;
        }
        if (df.stat(target) != null) target = uniqueName(df, target);
        j.current = it.name;
        String tmp = Paths.join(dir, "." + Paths.name(target) + ".boveda-parte");
        long start = j.doneBytes;
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            j.doneBytes = start;
            try {
                if (df.stat(tmp) != null) df.delete(tmp);
                java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
                long written = 0;
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(mf.downloadLink(it)).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36");
                try (InputStream in = c.getInputStream(); OutputStream out = df.out(tmp)) {
                    if (c.getContentType() != null && c.getContentType().startsWith("text/html") && it.size > 0
                            && !it.name.toLowerCase().endsWith(".html")) {
                        throw new IOException("MediaFire mandó una página en vez del archivo");
                    }
                    byte[] buf = new byte[BUF];
                    int r;
                    while ((r = in.read(buf)) >= 0) {
                        check(j);
                        out.write(buf, 0, r);
                        sha.update(buf, 0, r);
                        written += r;
                        j.doneBytes += r;
                    }
                    out.flush();
                    sync(out);
                } finally {
                    c.disconnect();
                }
                // verificación: tamaño y huella SHA-256 que guarda MediaFire
                if (it.size > 0 && written != it.size) throw new IOException("Llegó incompleto: " + it.name);
                if (it.hash != null && it.hash.length() == 64 && !it.hash.equalsIgnoreCase(MediaFire.hex(sha.digest()))) {
                    throw new IOException("La verificación falló en " + it.name);
                }
                df.rename(tmp, target);
                if (it.created > 0) df.setModified(target, it.created);
                j.doneItems++;
                changed(true);
                return;
            } catch (Cancelled c) {
                cleanup(df, tmp);
                throw c;
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            } catch (IOException e) {
                last = e;
                cleanup(df, tmp);
            }
        }
        throw last;
    }

    private void cloudUp(Job j) throws IOException {
        MediaFire mf = MediaFire.get(app);
        for (String s : j.sources) measure(j, s);
        for (String s : j.sources) cloudUpItem(j, mf, s, j.cloudFolder);
    }

    private void cloudUpItem(Job j, MediaFire mf, String path, String folderKey) throws IOException {
        check(j);
        Fs f = fs(path);
        Entry e = f.stat(path);
        if (e == null) return;
        if (e.dir) {
            String sub = mf.createFolder(folderKey, e.name);
            j.doneItems++;
            for (Entry k : f.list(path)) cloudUpItem(j, mf, k.path, sub);
            return;
        }
        j.current = "Preparando " + e.name;
        long start = j.doneBytes;
        // huella SHA-256 (MediaFire la pide y con ella comprueba que llegó bien)
        String hash;
        try (InputStream in = f.in(path)) {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[BUF];
            int r;
            while ((r = in.read(buf)) >= 0) {
                check(j);
                sha.update(buf, 0, r);
            }
            hash = MediaFire.hex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
        j.current = e.name;
        MediaFire.UploadCheck uc = mf.check(folderKey, e.name, e.size, hash);
        if (uc.hashExists) {
            // MediaFire ya tiene ese contenido: se agrega al instante sin subirlo
            mf.instant(folderKey, e.name, e.size, hash);
            j.doneBytes = start + e.size;
            j.doneItems++;
            changed(true);
            return;
        }
        String key;
        if (e.size <= MediaFire.SIMPLE_LIMIT || uc.units <= 0 || uc.unitSize <= 0) {
            try (InputStream in = f.in(path)) {
                key = mf.uploadSimple(folderKey, e.name, e.size, hash, in, n -> {
                    check(j);
                    j.doneBytes += n;
                });
            }
        } else {
            key = null;
            byte[] unitBuf = new byte[(int) uc.unitSize];
            try (InputStream in = f.in(path)) {
                for (int u = 0; u < uc.units; u++) {
                    check(j);
                    int len = readFully(in, unitBuf);
                    if (len <= 0) break;
                    boolean already = u / 16 < uc.bitmap.length && ((uc.bitmap[u / 16] >> (u % 16)) & 1) == 1;
                    if (already) {
                        j.doneBytes += len;
                        continue;
                    }
                    IOException last = null;
                    for (int attempt = 0; attempt < 4; attempt++) {
                        long before = j.doneBytes;
                        try {
                            final int unit = u;
                            key = mf.uploadUnit(folderKey, e.name, e.size, hash, unit, unitBuf, len, n -> {
                                check(j);
                                j.doneBytes += n;
                            });
                            last = null;
                            break;
                        } catch (Cancelled c) {
                            throw c;
                        } catch (IOException ex) {
                            last = ex;
                            j.doneBytes = before;
                        }
                    }
                    if (last != null) throw last;
                }
            }
            if (key == null) key = uc.resumeKey;
            if (key == null) throw new IOException("MediaFire no aceptó las partes del archivo");
        }
        j.current = "MediaFire está procesando " + e.name;
        mf.waitUpload(key);
        j.doneBytes = start + e.size;
        j.doneItems++;
        changed(true);
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) break;
            off += r;
        }
        return off;
    }

    private static boolean wanted(Job j, String name) {
        if (j.picks.isEmpty()) return true;
        for (String p : j.picks) if (name.equals(p) || name.startsWith(p + "/")) return true;
        return false;
    }

    /** Crea una carpeta y las que falten antes de ella. */
    private static void mkdirsAbsolute(Fs f, String dir) throws IOException {
        if (f.stat(dir) != null) return;
        String parent = Paths.parent(dir);
        if (!parent.equals(dir) && !parent.equals("/") && f.stat(parent) == null) mkdirsAbsolute(f, parent);
        f.mkdir(dir);
    }

    private static void mkdirs(Fs f, String base, String dir) throws IOException {
        if (dir.equals(base) || dir.length() < base.length()) return;
        if (f.stat(dir) != null) return;
        mkdirs(f, base, Paths.parent(dir));
        f.mkdir(dir);
    }
}
