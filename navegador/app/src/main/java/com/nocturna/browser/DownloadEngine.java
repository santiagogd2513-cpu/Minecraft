package com.nocturna.browser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.media.MediaScannerConnection;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestor de descargas rápido:
 * - Divide cada archivo en varios pedazos y los baja a la vez por conexiones separadas
 *   (hasta 16), escribiendo cada pedazo directamente en su lugar del archivo final.
 * - Cuando una conexión termina, le quita la mitad del trabajo a la más atrasada, así
 *   todas siguen trabajando hasta el final (no se queda una sola conexión lenta).
 * - Si una conexión se cae, reintenta desde donde iba.
 * - Se puede pausar y reanudar (también después de cerrar la app).
 * - Guarda en «Nocturna Downloads» (raíz del almacenamiento) o, sin permiso, en Descargas/Nocturna.
 */
final class DownloadEngine {
    interface Listener {
        void onDownloadsChanged();
    }

    private static final long MIN_SEGMENT = 1024 * 1024;        // 1 MB
    private static final long STEAL_MIN = 2 * 1024 * 1024;      // solo se reparte si quedan más de 2 MB
    private static final int BUFFER = 256 * 1024;
    static final String CHANNEL = "descargas";
    static final String CHANNEL_DONE = "descargas_listas";

    private static DownloadEngine instance;

    private final Context app;
    private final Store store;
    private final List<DownloadItem> items = new CopyOnWriteArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastSave;

    static synchronized DownloadEngine get(Context c) {
        if (instance == null) instance = new DownloadEngine(c.getApplicationContext());
        return instance;
    }

    private DownloadEngine(Context c) {
        app = c;
        store = new Store(c);
        load();
        createChannels();
        ticker.scheduleAtFixedRate(this::tick, 500, 500, TimeUnit.MILLISECONDS);
    }

    List<DownloadItem> items() {
        return items;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    boolean anyActive() {
        for (DownloadItem d : items) if (d.status == DownloadItem.RUNNING || d.status == DownloadItem.QUEUED) return true;
        return false;
    }

    // ------------------------------------------------------------------ acciones

    DownloadItem enqueue(String url, String name, String mime, String userAgent, String referer, long length) {
        DownloadItem d = new DownloadItem();
        d.id = System.currentTimeMillis();
        d.url = url;
        d.name = name;
        d.mime = mime == null || mime.isEmpty() ? guessMime(name) : mime;
        d.userAgent = userAgent;
        d.referer = referer;
        d.total = length > 0 ? length : -1;
        d.created = System.currentTimeMillis();
        items.add(0, d);
        start(d);
        return d;
    }

    /** Guarda unos bytes que ya tenemos (enlaces data: y blob:). */
    void saveBytes(byte[] data, String name, String mime) {
        pool.execute(() -> {
            DownloadItem d = new DownloadItem();
            d.id = System.currentTimeMillis();
            d.url = "data:";
            d.name = name;
            d.mime = mime == null || mime.isEmpty() ? guessMime(name) : mime;
            d.created = System.currentTimeMillis();
            d.total = data.length;
            items.add(0, d);
            try {
                createFile(d);
                try (Sink sink = openSink(d)) {
                    writeFully(sink.channel, data, data.length, 0);
                }
                d.done.set(data.length);
                finish(d);
            } catch (Exception e) {
                d.status = DownloadItem.FAILED;
                d.error = e.getMessage();
            }
            changed(true);
        });
    }

    void start(DownloadItem d) {
        if (d.status == DownloadItem.RUNNING) return;
        d.stop = false;
        d.error = null;
        d.status = DownloadItem.QUEUED;
        changed(true);
        DownloadService.ensure(app);
        pool.execute(() -> run(d));
    }

    void pause(DownloadItem d) {
        d.stop = true;
        d.status = DownloadItem.PAUSED;
        changed(true);
    }

    void cancel(DownloadItem d) {
        d.stop = true;
        items.remove(d);
        pool.execute(() -> {
            // espera a que las conexiones suelten el archivo
            for (int i = 0; i < 40 && d.connections.get() > 0; i++) sleep(50);
            if (d.uri != null && d.status != DownloadItem.DONE) deleteFile(d);
        });
        changed(true);
    }

    /** Quita de la lista; si deleteFile, también borra el archivo. */
    void remove(DownloadItem d, boolean deleteFile) {
        items.remove(d);
        if (deleteFile && d.uri != null) pool.execute(() -> deleteFile(d));
        changed(true);
    }

    void clearFinished() {
        items.removeIf(d -> d.status == DownloadItem.DONE);
        changed(true);
    }

    // ------------------------------------------------------------------ descarga

    private void run(DownloadItem d) {
        if (d.stop) {
            d.status = DownloadItem.PAUSED;
            changed(true);
            return;
        }
        d.status = DownloadItem.RUNNING;
        changed(true);
        Sink sink = null;
        try {
            probe(d);
            if (d.uri == null) createFile(d);
            sink = openSink(d);
            FileChannel ch = sink.channel;
            if (d.ranges && d.total > 0) {
                prepareSegments(d);
                runSegments(d, ch);
            } else {
                runSingle(d, ch);
            }
            if (d.stop) {
                d.status = DownloadItem.PAUSED;
            } else if (d.total > 0 && d.done.get() < d.total) {
                throw new IOException("La descarga quedó incompleta");
            } else {
                ch.force(false);
                finish(d);
            }
        } catch (Exception e) {
            if (d.status == DownloadItem.PAUSED || !items.contains(d)) {
                d.status = DownloadItem.PAUSED;
            } else {
                d.status = DownloadItem.FAILED;
                d.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        } finally {
            try {
                if (sink != null) sink.close();
            } catch (IOException ignored) {
            }
            d.connections.set(0);
            changed(true);
        }
    }

    /** Sigue las redirecciones y averigua el tamaño y si el servidor deja bajar por pedazos. */
    private void probe(DownloadItem d) throws IOException {
        String current = d.url;
        for (int hop = 0; hop < 10; hop++) {
            HttpURLConnection c = open(d, current, "bytes=0-0", false);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirección sin destino");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code == 206) {
                String cr = c.getHeaderField("Content-Range");
                long total = -1;
                if (cr != null && cr.contains("/")) {
                    try {
                        total = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (total > 0) {
                    if (d.total > 0 && d.total != total && !d.segments.isEmpty()) {
                        // el archivo cambió en el servidor: empezar de cero
                        resetProgress(d);
                    }
                    d.total = total;
                    d.ranges = true;
                } else {
                    d.ranges = false;
                }
            } else if (code == 200) {
                long len = c.getContentLengthLong();
                if (len > 0) d.total = len;
                d.ranges = false;
            } else {
                c.disconnect();
                throw new IOException("El servidor respondió " + code);
            }
            String type = c.getContentType();
            if (type != null && (d.mime == null || d.mime.equals("application/octet-stream"))) {
                d.mime = type.split(";")[0].trim();
            }
            c.disconnect();
            d.finalUrl = current;
            return;
        }
        throw new IOException("Demasiadas redirecciones");
    }

    private void resetProgress(DownloadItem d) {
        synchronized (d) {
            d.segments.clear();
        }
        d.done.set(0);
    }

    private void prepareSegments(DownloadItem d) {
        synchronized (d) {
            if (!d.segments.isEmpty()) return;
            int conns = Math.max(1, store.connections());
            int n = (int) Math.max(1, Math.min(conns, d.total / MIN_SEGMENT));
            long size = d.total / n;
            for (int i = 0; i < n; i++) {
                long s = i * size;
                long e = i == n - 1 ? d.total - 1 : s + size - 1;
                d.segments.add(new DownloadItem.Segment(s, e, s));
            }
        }
    }

    private void runSegments(DownloadItem d, FileChannel ch) throws Exception {
        final Exception[] failure = {null};
        List<DownloadItem.Segment> pending = new ArrayList<>();
        synchronized (d) {
            for (DownloadItem.Segment s : d.segments) if (s.remaining() > 0) pending.add(s);
        }
        for (DownloadItem.Segment s : pending) launch(d, s, ch, failure);
        // espera a que todas las conexiones terminen (las que se reparten trabajo se suman solas)
        while (d.connections.get() > 0) {
            sleep(100);
            if (failure[0] != null) d.stop = true;
        }
        if (failure[0] != null && !itemPausedByUser(d)) throw failure[0];
    }

    private boolean itemPausedByUser(DownloadItem d) {
        return d.status == DownloadItem.PAUSED;
    }

    private void launch(DownloadItem d, DownloadItem.Segment s, FileChannel ch, Exception[] failure) {
        d.connections.incrementAndGet();
        s.active = true;
        pool.execute(() -> {
            try {
                downloadSegment(d, s, ch);
                // terminó su parte: ayuda a la conexión más atrasada
                if (!d.stop) {
                    DownloadItem.Segment extra = steal(d);
                    if (extra != null) launch(d, extra, ch, failure);
                }
            } catch (Exception e) {
                synchronized (failure) {
                    if (failure[0] == null) failure[0] = e;
                }
            } finally {
                s.active = false;
                d.connections.decrementAndGet();
            }
        });
    }

    /** Parte en dos el pedazo al que más le falta y devuelve la mitad nueva. */
    private DownloadItem.Segment steal(DownloadItem d) {
        synchronized (d) {
            DownloadItem.Segment best = null;
            for (DownloadItem.Segment s : d.segments) {
                if (s.active && (best == null || s.remaining() > best.remaining())) best = s;
            }
            if (best == null) return null;
            synchronized (best) {
                long rem = best.remaining();
                if (rem < STEAL_MIN) return null;
                long mid = best.pos + rem / 2;
                DownloadItem.Segment n = new DownloadItem.Segment(mid, best.end, mid);
                best.end = mid - 1;
                d.segments.add(n);
                return n;
            }
        }
    }

    private void downloadSegment(DownloadItem d, DownloadItem.Segment s, FileChannel ch) throws Exception {
        int retries = 0;
        byte[] buf = new byte[BUFFER];
        while (!d.stop && s.remaining() > 0) {
            HttpURLConnection c = null;
            try {
                c = open(d, d.finalUrl, "bytes=" + s.pos + "-" + s.end, true);
                int code = c.getResponseCode();
                if (code != 206) throw new IOException("El servidor dejó de aceptar pedazos (" + code + ")");
                try (InputStream in = c.getInputStream()) {
                    while (!d.stop) {
                        int want;
                        synchronized (s) {
                            if (s.pos > s.end) break;
                            want = (int) Math.min(buf.length, s.end - s.pos + 1);
                        }
                        int r = in.read(buf, 0, want);
                        if (r < 0) break;
                        long at;
                        synchronized (s) {
                            // otra conexión pudo quedarse con el final de este pedazo mientras leíamos
                            if (s.pos > s.end) break;
                            r = (int) Math.min(r, s.end - s.pos + 1);
                            at = s.pos;
                            s.pos += r;
                        }
                        writeFully(ch, buf, r, at);
                        d.done.addAndGet(r);
                        retries = 0;
                    }
                }
            } catch (IOException e) {
                if (d.stop) return;
                if (++retries > 8) throw e;
                sleep(Math.min(8000, 400L << Math.min(retries, 5)));
                // el enlace firmado puede haber vencido: se vuelve a resolver
                if (retries == 4) {
                    try {
                        probe(d);
                    } catch (IOException ignored) {
                    }
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    private void runSingle(DownloadItem d, FileChannel ch) throws Exception {
        // sin pedazos no se puede continuar a medias: empieza desde cero
        d.done.set(0);
        synchronized (d) {
            d.segments.clear();
            d.segments.add(new DownloadItem.Segment(0, Math.max(0, d.total - 1), 0));
        }
        DownloadItem.Segment seg = d.segments.get(0);
        ch.truncate(0);
        d.connections.set(1);
        seg.active = true;
        HttpURLConnection c = open(d, d.finalUrl, null, true);
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[BUFFER];
            long pos = 0;
            int r;
            while (!d.stop && (r = in.read(buf)) >= 0) {
                writeFully(ch, buf, r, pos);
                pos += r;
                seg.pos = pos;
                d.done.set(pos);
            }
            if (!d.stop && d.total <= 0) d.total = pos;
        } finally {
            seg.active = false;
            c.disconnect();
        }
    }

    private static void writeFully(FileChannel ch, byte[] buf, int len, long at) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
        long p = at;
        while (bb.hasRemaining()) p += ch.write(bb, p);
    }

    private HttpURLConnection open(DownloadItem d, String url, String range, boolean follow) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(follow);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (d.userAgent != null) c.setRequestProperty("User-Agent", d.userAgent);
        if (d.referer != null && !d.referer.isEmpty()) c.setRequestProperty("Referer", d.referer);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies == null) cookies = CookieManager.getInstance().getCookie(d.url);
        if (cookies != null) c.setRequestProperty("Cookie", cookies);
        c.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) c.setRequestProperty("Range", range);
        return c;
    }

    // ------------------------------------------------------------------ archivo

    /** Carpeta "Nocturna Downloads" en la raíz del almacenamiento (junto a Download, DCIM, Android…). */
    static File rootFolder() {
        return new File(Environment.getExternalStorageDirectory(), "Nocturna Downloads");
    }

    static boolean canUseRootFolder() {
        return android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
    }

    static boolean isPlainFile(DownloadItem d) {
        return d.uri != null && d.uri.startsWith("file:");
    }

    static File fileOf(DownloadItem d) {
        return new File(Uri.parse(d.uri).getPath());
    }

    /** El archivo abierto para escribir (en la carpeta propia o en Descargas por MediaStore). */
    private static final class Sink implements java.io.Closeable {
        final FileChannel channel;
        private final java.io.Closeable a, b;

        Sink(FileChannel channel, java.io.Closeable a, java.io.Closeable b) {
            this.channel = channel;
            this.a = a;
            this.b = b;
        }

        @Override
        public void close() throws IOException {
            try {
                if (a != null) a.close();
            } finally {
                if (b != null) b.close();
            }
        }
    }

    private Sink openSink(DownloadItem d) throws IOException {
        if (isPlainFile(d)) {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(fileOf(d), "rw");
            return new Sink(raf.getChannel(), raf, null);
        }
        ParcelFileDescriptor pfd = app.getContentResolver().openFileDescriptor(Uri.parse(d.uri), "rw");
        if (pfd == null) throw new IOException("No se pudo abrir el archivo");
        FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
        return new Sink(fos.getChannel(), fos, pfd);
    }

    private void deleteFile(DownloadItem d) {
        try {
            if (isPlainFile(d)) {
                //noinspection ResultOfMethodCallIgnored
                fileOf(d).delete();
            } else {
                app.getContentResolver().delete(Uri.parse(d.uri), null, null);
            }
        } catch (Exception ignored) {
        }
    }

    private void createFile(DownloadItem d) throws IOException {
        if (canUseRootFolder()) {
            File dir = rootFolder();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No se pudo crear la carpeta Nocturna Downloads");
            String name = d.name == null || d.name.isEmpty() ? "descarga" : d.name.replaceAll("[\\\\/:*?\"<>|]", "_");
            File f = new File(dir, name);
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
            for (int n = 1; f.exists(); n++) f = new File(dir, base + " (" + n + ")" + ext);
            if (!f.createNewFile()) throw new IOException("No se pudo crear el archivo");
            d.name = f.getName();
            d.uri = Uri.fromFile(f).toString();
            return;
        }
        createMediaStoreFile(d);
    }

    private void createMediaStoreFile(DownloadItem d) throws IOException {
        ContentResolver r = app.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, d.name);
        v.put(MediaStore.Downloads.MIME_TYPE, d.mime == null ? "application/octet-stream" : d.mime);
        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Nocturna");
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("No se pudo crear el archivo en Descargas");
        // Android puede renombrarlo si ya existía uno igual
        try (Cursor cur = r.query(uri, new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) d.name = cur.getString(0);
        } catch (Exception ignored) {
        }
        d.uri = uri.toString();
    }

    private void finish(DownloadItem d) {
        try {
            if (isPlainFile(d)) {
                // para que aparezca en la galería, el reproductor, etc.
                MediaScannerConnection.scanFile(app, new String[]{fileOf(d).getAbsolutePath()}, new String[]{d.mime}, null);
            } else {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                app.getContentResolver().update(Uri.parse(d.uri), v, null, null);
            }
        } catch (Exception ignored) {
        }
        d.status = DownloadItem.DONE;
        d.finished = System.currentTimeMillis();
        notifyDone(d);
    }

    /** Dirección que se le puede pasar a otras apps (abrir, compartir, instalar). */
    static Uri shareUri(Context c, DownloadItem d) {
        if (isPlainFile(d)) return androidx.core.content.FileProvider.getUriForFile(c, c.getPackageName() + ".files", fileOf(d));
        return Uri.parse(d.uri);
    }

    static Intent openIntent(Context c, DownloadItem d) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        String mime = d.mime;
        if ("apk".equals(d.extension())) mime = "application/vnd.android.package-archive";
        i.setDataAndType(shareUri(c, d), mime == null ? "*/*" : mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    static String guessMime(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot >= 0) {
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
            if (m != null) return m;
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ velocidad, avisos y guardado

    private void tick() {
        boolean any = false;
        for (DownloadItem d : items) {
            long now = d.done.get();
            if (d.status == DownloadItem.RUNNING) {
                any = true;
                double inst = (now - d.lastBytes) * 2.0;
                d.speed = d.speed == 0 ? inst : d.speed * 0.65 + inst * 0.35;
                d.peakSpeed = Math.max(d.peakSpeed, d.speed);
            } else {
                d.speed = 0;
            }
            d.lastBytes = now;
        }
        if (any) changed(false);
    }

    private void changed(boolean persist) {
        if (persist || System.currentTimeMillis() - lastSave > 3000) save();
        main.post(() -> {
            for (Listener l : listeners) l.onDownloadsChanged();
        });
    }

    private synchronized void save() {
        lastSave = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray();
            for (DownloadItem d : items) a.put(d.toJson());
            File tmp = new File(app.getFilesDir(), "descargas.json.tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(new File(app.getFilesDir(), "descargas.json"));
        } catch (Exception ignored) {
        }
    }

    private void load() {
        try {
            File f = new File(app.getFilesDir(), "descargas.json");
            if (!f.exists()) return;
            JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                DownloadItem d = DownloadItem.fromJson(a.getJSONObject(i));
                d.lastBytes = d.done.get();
                items.add(d);
            }
        } catch (Exception ignored) {
        }
    }

    private void createChannels() {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Descargas en curso", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void notifyDone(DownloadItem d) {
        try {
            PendingIntent pi = PendingIntent.getActivity(app, (int) (d.id % 100000), openIntent(app, d),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            android.app.Notification n = new android.app.Notification.Builder(app, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_download)
                    .setColor(Ui.ACCENT)
                    .setContentTitle("Descarga completa")
                    .setContentText(d.name + " · " + Ui.size(d.total))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            app.getSystemService(NotificationManager.class).notify((int) (d.id % 100000) + 10, n);
        } catch (Exception ignored) {
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
