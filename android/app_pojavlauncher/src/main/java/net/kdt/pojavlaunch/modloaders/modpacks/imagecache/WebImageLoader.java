package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import net.kdt.pojavlaunch.Tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Amatista: carga sencilla de imágenes web (fotos de mods) con caché en memoria.
 * Reduce la resolución al tamaño de la vista para no gastar RAM.
 */
public final class WebImageLoader {
    private WebImageLoader() {}

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public interface Callback { void onLoaded(Bitmap bitmap); }

    /** Carga la imagen en la vista; si la vista se recicla con otra URL, se ignora el resultado. */
    public static void load(String url, ImageView view, int targetWidthPx) {
        if (url == null) return;
        view.setTag(url);
        load(url, targetWidthPx, bitmap -> {
            if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
        });
    }

    public static void load(String url, int targetWidthPx, Callback callback) {
        if (url == null) return;
        String key = url + "@" + targetWidthPx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }
        POOL.execute(() -> {
            Bitmap bitmap = download(url, targetWidthPx);
            if (bitmap == null) return;
            CACHE.put(key, bitmap);
            Tools.runOnUiThread(() -> callback.onLoaded(bitmap));
        });
    }

    private static Bitmap download(String url, int targetWidthPx) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "AmatistaLauncher");
            byte[] data;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                data = out.toByteArray();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            while (targetWidthPx > 0 && bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Throwable e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
