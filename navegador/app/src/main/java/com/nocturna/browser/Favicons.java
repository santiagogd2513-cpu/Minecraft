package com.nocturna.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Íconos de las páginas (favicons), guardados por dominio para la lista de favoritos. */
final class Favicons {
    private static final ExecutorService io = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<String> tried = new HashSet<>();

    private Favicons() {
    }

    static String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return "";
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }

    private static File file(Context c, String host) {
        File dir = new File(c.getFilesDir(), "iconos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, Integer.toHexString(host.hashCode()) + ".png");
    }

    /** La página mandó su ícono: se guarda (si no es mejor el que ya hay). */
    static void save(Context c, String url, Bitmap icon) {
        String host = host(url);
        if (host.isEmpty() || icon == null) return;
        Context app = c.getApplicationContext();
        io.execute(() -> {
            File f = file(app, host);
            if (f.exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                if (o.outWidth >= icon.getWidth()) return;
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception ignored) {
            }
        });
    }

    /** Pone el ícono de la página; mientras tanto (o si no tiene) una letra de color. */
    static void into(Context c, ImageView v, String url, String title) {
        String host = host(url);
        v.setTag(host);
        v.setImageBitmap(letter(c, host.isEmpty() ? title : host));
        if (host.isEmpty()) return;
        Context app = c.getApplicationContext();
        io.execute(() -> {
            File f = file(app, host);
            Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
            if (b == null) {
                boolean first;
                synchronized (tried) {
                    first = tried.add(host);
                }
                if (first) b = download(f, host);
            }
            final Bitmap bmp = b;
            if (bmp != null) main.post(() -> {
                if (host.equals(v.getTag())) v.setImageBitmap(bmp);
            });
        });
    }

    /** Favoritos guardados antes de que existieran los íconos: se busca el ícono del dominio. */
    private static Bitmap download(File f, String host) {
        String[] urls = {
                "https://www.google.com/s2/favicons?domain=" + host + "&sz=64",
                "https://" + host + "/favicon.ico",
        };
        for (String u : urls) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) continue;
                Bitmap b;
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in);
                }
                if (b == null || b.getWidth() < 8) continue;
                try (FileOutputStream out = new FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                return b;
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    /** Círculo de color con la primera letra del sitio. */
    static Bitmap letter(Context c, String name) {
        int size = Ui.dp(c, 36);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[] hsv = {Math.abs((name == null ? 0 : name.hashCode()) % 360), 0.55f, 0.75f};
        p.setColor(Color.HSVToColor(hsv));
        cv.drawRoundRect(new RectF(0, 0, size, size), size * 0.28f, size * 0.28f, p);
        String l = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size * 0.48f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(l, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }
}
