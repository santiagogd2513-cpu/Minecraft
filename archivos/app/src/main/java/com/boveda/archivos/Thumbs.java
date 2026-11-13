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

/**
 * Miniaturas de imágenes y videos, e íconos de los APK y de los JAR (el logo que traen los mods de
 * Minecraft y otros programas), para la lista de archivos. También en Android/data y obb (con Shizuku).
 */
final class Thumbs {
    static final int NONE = 0, IMAGE = 1, VIDEO = 2, APK = 3, JAR = 4;

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
        if (x.equals("jar")) return JAR;
        return NONE;
    }

    /** Solo archivos que se pueden leer directamente (no FTP, Drive ni carpetas protegidas). */
    static boolean local(String path) {
        // solo por la ruta: la pantalla no debe esperar al disco (una USB ocupada puede tardar segundos)
        return path != null && path.startsWith("/") && !FtpServers.isFtp(path) && !NetServers.isNet(path) && !DriveFs.isDrive(path) && !MegaFs.isMega(path) && !OneDriveFs.isOneDrive(path) && !VaultFs.isVault(path) && !Paths.restricted(path);
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
                if (VaultFs.isVault(e.path)) {
                    // la Bóveda: la miniatura se arma en la memoria, nunca se guarda en el disco
                    if (kind == IMAGE) b = vaultImage(app, e.path, px);
                } else if (Paths.restricted(e.path)) {
                    // Android/data y obb: se lee por Shizuku (o el permiso de la carpeta)
                    Fs fs = FsRouter.get(app, e.path);
                    if (kind == IMAGE) b = streamImage(fs, e.path, px);
                    else if (kind == JAR) b = jarIcon(() -> fs.in(e.path), px);
                    else if (kind == VIDEO || kind == APK) {
                        try (android.os.ParcelFileDescriptor pfd = fs.pfd(e.path, "r")) {
                            if (kind == VIDEO) b = videoFrame(pfd, px);
                            else b = apkIcon(app, "/proc/self/fd/" + pfd.getFd(), px);
                        }
                    }
                } else if (!f.canRead()) throw new java.io.IOException();
                else if (kind == IMAGE) b = ThumbnailUtils.createImageThumbnail(f, new Size(px, px), null);
                else if (kind == VIDEO) b = ThumbnailUtils.createVideoThumbnail(f, new Size(px, px), null);
                else if (kind == APK) b = apkIcon(app, e.path, px);
                else if (kind == JAR) b = jarIcon(() -> new java.io.FileInputStream(f), px);
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

    /** Al cerrar la Bóveda: que no quede ninguna miniatura de ella en la memoria. */
    static void clearAll() {
        cache.evictAll();
        failed.clear();
    }

    private static Bitmap vaultImage(Context c, String path, int px) throws java.io.IOException {
        return streamImage(FsRouter.get(c, path), path, px);
    }

    private static Bitmap streamImage(Fs fs, String path, int px) throws java.io.IOException {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (java.io.InputStream in = fs.in(path)) {
            android.graphics.BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= px) sample *= 2;
        o = new android.graphics.BitmapFactory.Options();
        o.inSampleSize = sample;
        try (java.io.InputStream in = fs.in(path)) {
            Bitmap full = android.graphics.BitmapFactory.decodeStream(in, null, o);
            return full == null ? null : ThumbnailUtils.extractThumbnail(full, px, px, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        }
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

    private static Bitmap videoFrame(android.os.ParcelFileDescriptor pfd, int px) {
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try {
            r.setDataSource(pfd.getFileDescriptor());
            Bitmap full = r.getFrameAtTime(1_000_000);
            return full == null ? null : ThumbnailUtils.extractThumbnail(full, px, px, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ el ícono de un .jar

    interface Opener {
        java.io.InputStream open() throws java.io.IOException;
    }

    private static final java.util.regex.Pattern LOGO_TOML = java.util.regex.Pattern.compile("logoFile\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final java.util.regex.Pattern ICON_STR = java.util.regex.Pattern.compile("\"(?:icon|logoFile)\"\\s*:\\s*\"([^\"]+)\"");
    private static final java.util.regex.Pattern ICON_OBJ = java.util.regex.Pattern.compile("\"icon\"\\s*:\\s*\\{([^}]*)\\}");
    private static final java.util.regex.Pattern ICON_SIZE = java.util.regex.Pattern.compile("\"(\\d+)\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * El logo de un .jar: el que dice su ficha (Forge/NeoForge: logoFile en mods.toml; Fabric y Quilt:
     * «icon» en su .json; mods viejos: mcmod.info). Si no dice, una imagen que se llame icon, logo o
     * pack.png. Si no hay nada, null (y queda el ícono de siempre).
     */
    static Bitmap jarIcon(Opener open, int px) throws java.io.IOException {
        String declared = null;
        java.util.Map<String, Long> pngs = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(open.open(), 1 << 16))) {
            java.util.zip.ZipEntry en;
            int seen = 0;
            while ((en = z.getNextEntry()) != null && seen++ < 60000) {
                String n = en.getName();
                if (en.isDirectory()) continue;
                String low = n.toLowerCase(java.util.Locale.ROOT);
                if (low.endsWith(".png")) {
                    pngs.put(n, en.getSize());
                    continue;
                }
                boolean toml = low.equals("meta-inf/mods.toml") || low.equals("meta-inf/neoforge.mods.toml");
                boolean json = low.equals("fabric.mod.json") || low.equals("quilt.mod.json") || low.equals("mcmod.info");
                if ((toml || json) && declared == null) {
                    String text = new String(readAll(z, 512 * 1024), java.nio.charset.StandardCharsets.UTF_8);
                    declared = toml ? find(LOGO_TOML, text) : iconFromJson(text);
                }
            }
        }
        String pick = null;
        if (declared != null) {
            String d = declared.startsWith("/") ? declared.substring(1) : declared;
            for (String n : pngs.keySet()) {
                if (n.equals(d) || n.endsWith("/" + d)) {
                    pick = n;
                    break;
                }
            }
        }
        if (pick == null) pick = guess(pngs.keySet());
        if (pick == null) return null;
        byte[] data = null;
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(open.open(), 1 << 16))) {
            java.util.zip.ZipEntry en;
            while ((en = z.getNextEntry()) != null) {
                if (en.getName().equals(pick)) {
                    data = readAll(z, 8 * 1024 * 1024);
                    break;
                }
            }
        }
        if (data == null) return null;
        Bitmap src = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
        return src == null ? null : fitRounded(src, px);
    }

    private static String iconFromJson(String text) {
        java.util.regex.Matcher m = ICON_OBJ.matcher(text);
        if (m.find()) {
            // {"16": "…", "128": "…"}: el más grande
            java.util.regex.Matcher k = ICON_SIZE.matcher(m.group(1));
            int best = -1;
            String path = null;
            while (k.find()) {
                int size = Integer.parseInt(k.group(1));
                if (size > best) {
                    best = size;
                    path = k.group(2);
                }
            }
            if (path != null) return path;
        }
        return find(ICON_STR, text);
    }

    private static String find(java.util.regex.Pattern p, String text) {
        java.util.regex.Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Sin ficha: la imagen con más pinta de ícono (icon/logo/pack, lo más cerca de la raíz). */
    private static String guess(java.util.Collection<String> names) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String n : names) {
            String low = n.toLowerCase(java.util.Locale.ROOT);
            String base = low.substring(low.lastIndexOf('/') + 1);
            if (low.contains("/gui/") || low.contains("/textures/block") || low.contains("/textures/item")) continue;
            int score;
            if (base.equals("icon.png") || base.equals("logo.png")) score = 100;
            else if (base.equals("pack.png")) score = 90;
            else if (base.contains("icon") || base.contains("logo")) score = 60;
            else continue;
            int depth = 0;
            for (int i = 0; i < low.length(); i++) if (low.charAt(i) == '/') depth++;
            score -= depth * 8;
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    private static byte[] readAll(java.io.InputStream in, int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            if (out.size() > max) throw new java.io.IOException("muy grande");
        }
        return out.toByteArray();
    }

    /** Cabe en el cuadro sin deformarse, con las esquinas redondeadas; los íconos pixelados quedan nítidos. */
    private static Bitmap fitRounded(Bitmap src, int px) {
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scale = Math.min(px / (float) src.getWidth(), px / (float) src.getHeight());
        float w = src.getWidth() * scale, h = src.getHeight() * scale;
        float x = (px - w) / 2f, y = (px - h) / 2f;
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(new android.graphics.RectF(x, y, x + w, y + h), px * 0.18f, px * 0.18f, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setFilterBitmap(Math.max(src.getWidth(), src.getHeight()) >= px / 2);
        c.drawBitmap(src, null, new android.graphics.RectF(x, y, x + w, y + h), p);
        src.recycle();
        return out;
    }
}
