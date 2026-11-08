package com.boveda.archivos;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Miniaturas de imágenes y videos, e íconos de los APK, para la lista de archivos. */
final class Thumbs {
    static final int NONE = 0, IMAGE = 1, VIDEO = 2, APK = 3;

    private static final Set<String> IMG = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif"));
    private static final Set<String> VID = new HashSet<>(Arrays.asList("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts", "wmv", "flv"));

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 10)) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> pending = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService io = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Thumbs() {
    }

    static int kind(Entry e) {
        if (e.dir) return NONE;
        String x = e.extension();
        if (IMG.contains(x)) return IMAGE;
        if (VID.contains(x)) return VIDEO;
        if (x.equals("apk")) return APK;
        return NONE;
    }

    /** Solo archivos que se pueden leer directamente (no FTP, Drive ni carpetas protegidas). */
    static boolean local(String path) {
        // solo por la ruta: la pantalla no debe esperar al disco (una USB ocupada puede tardar segundos)
        return path != null && path.startsWith("/") && !FtpServers.isFtp(path) && !DriveFs.isDrive(path) && !Paths.restricted(path);
    }

    private static String key(Entry e) {
        return e.path + "|" + e.modified + "|" + e.size;
    }

    static Bitmap cached(Entry e) {
        return cache.get(key(e));
    }

    static boolean failedBefore(Entry e) {
        return failed.contains(key(e));
    }

    static void load(Context c, Entry e, int px, Consumer<Bitmap> done) {
        String k = key(e);
        if (failed.contains(k) || !pending.add(k)) return;
        Context app = c.getApplicationContext();
        int kind = kind(e);
        io.execute(() -> {
            Bitmap b = null;
            try {
                File f = new File(e.path);
                if (!f.canRead()) throw new java.io.IOException();
                if (kind == IMAGE) b = ThumbnailUtils.createImageThumbnail(f, new Size(px, px), null);
                else if (kind == VIDEO) b = ThumbnailUtils.createVideoThumbnail(f, new Size(px, px), null);
                else if (kind == APK) b = apkIcon(app, e.path, px);
            } catch (Throwable ignored) {
            }
            pending.remove(k);
            if (b == null) {
                failed.add(k);
                return;
            }
            cache.put(k, b);
            final Bitmap bmp = b;
            main.post(() -> done.accept(bmp));
        });
    }

    private static Bitmap apkIcon(Context c, String path, int px) {
        PackageManager pm = c.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(path, 0);
        if (pi == null || pi.applicationInfo == null) return null;
        pi.applicationInfo.sourceDir = path;
        pi.applicationInfo.publicSourceDir = path;
        Drawable d = pi.applicationInfo.loadIcon(pm);
        if (d == null) return null;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return Bitmap.createScaledBitmap(((BitmapDrawable) d).getBitmap(), px, px, true);
        }
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, px, px);
        d.draw(new Canvas(b));
        return b;
    }
}
