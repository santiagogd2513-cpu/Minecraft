package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visor de imágenes: JPG, PNG, GIF y WebP animados, BMP, ICO, HEIC/HEIF, AVIF y RAW de cámara
 * (lo que abre Android), más TGA, TIFF, PSD, QOI, PPM/PGM/PBM (con {@link ImageCodecs}) y SVG.
 * Zoom con pellizco o dos toques, deslizar para pasar a la siguiente de la carpeta, girar, ver
 * datos y compartir.
 */
public class ImageActivity extends Activity {
    private String[] paths;
    private int index;
    private ZoomImageView view;
    private WebView svg;
    private ProgressBar spin;
    private TextView title, sub, error;
    private View top, bottom;
    private boolean barsShown = true;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private String info = "";

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        paths = getIntent().getStringArrayExtra("paths");
        index = getIntent().getIntExtra("index", 0);
        if (paths == null || paths.length == 0) {
            finish();
            return;
        }
        index = Math.max(0, Math.min(paths.length - 1, index));
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new ZoomImageView(this);
        view.setListener(new ZoomImageView.Listener() {
            @Override
            public void onTap() {
                toggleBars();
            }

            @Override
            public void onSwipe(int dir) {
                go(index + dir);
            }
        });
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        svg = new WebView(this);
        svg.setBackgroundColor(Color.BLACK);
        svg.getSettings().setBuiltInZoomControls(true);
        svg.getSettings().setDisplayZoomControls(false);
        svg.getSettings().setLoadWithOverviewMode(true);
        svg.getSettings().setUseWideViewPort(true);
        svg.setVisibility(View.GONE);
        root.addView(svg, new FrameLayout.LayoutParams(-1, -1));
        spin = new ProgressBar(this);
        root.addView(spin, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        error = Ui.text(this, "", 14, 0xFFE0E0E0, false);
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(32), 0, dp(32), 0);
        error.setVisibility(View.GONE);
        root.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // barra de arriba
        LinearLayout bar = Ui.row(this);
        bar.setPadding(dp(4), dp(6), dp(6), dp(14));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> finish()));
        LinearLayout tt = Ui.column(this);
        title = Ui.text(this, "", 16, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sub = Ui.text(this, "", 11.5f, 0xFFB8B8B8, false);
        tt.addView(title);
        tt.addView(sub);
        bar.addView(tt, Ui.weight(1));
        bar.setFitsSystemWindows(true);
        top = bar;
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // barra de abajo: anterior, girar, datos, compartir, abrir con, siguiente
        LinearLayout bot = Ui.row(this);
        bot.setGravity(Gravity.CENTER);
        bot.setPadding(dp(6), dp(14), dp(6), dp(10));
        bot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        TextView prev = Ui.text(this, "‹", 30, Color.WHITE, true);
        prev.setPadding(dp(16), 0, dp(16), 0);
        prev.setOnClickListener(v -> go(index - 1));
        TextView next = Ui.text(this, "›", 30, Color.WHITE, true);
        next.setPadding(dp(16), 0, dp(16), 0);
        next.setOnClickListener(v -> go(index + 1));
        bot.addView(prev);
        bot.addView(Ui.iconButton(this, R.drawable.ic_refresh, 48, v -> view.rotate()));
        bot.addView(Ui.iconButton(this, R.drawable.ic_info, 48, v -> showInfo()));
        bot.addView(Ui.iconButton(this, R.drawable.ic_share, 48, v -> share()));
        bot.addView(Ui.iconButton(this, R.drawable.ic_open, 48, v -> Viewers.openWithOther(this, paths[index], "image/*")));
        bot.addView(next);
        bot.setFitsSystemWindows(true);
        bottom = bot;
        root.addView(bot, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        load();
    }

    private void go(int i) {
        if (i < 0 || i >= paths.length || i == index) return;
        index = i;
        load();
    }

    private void toggleBars() {
        barsShown = !barsShown;
        top.animate().alpha(barsShown ? 1 : 0).setDuration(180).start();
        bottom.animate().alpha(barsShown ? 1 : 0).setDuration(180).start();
        top.setClickable(barsShown);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            if (barsShown) c.show(WindowInsets.Type.systemBars());
            else {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    // ------------------------------------------------------------------ carga

    private void load() {
        String path = paths[index];
        int gen = ++generation;
        title.setText(Paths.name(path));
        sub.setText(paths.length > 1 ? (index + 1) + " de " + paths.length : "");
        spin.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        view.setImage(null);
        svg.setVisibility(View.GONE);
        worker.execute(() -> {
            Object result;
            String details;
            try {
                Decoded d = decode(path);
                result = d.image;
                details = d.details;
            } catch (Throwable e) {
                result = e;
                details = "";
            }
            Object r = result;
            String det = details;
            main.post(() -> {
                if (gen != generation || isFinishing()) return;
                spin.setVisibility(View.GONE);
                info = det;
                if (r instanceof Drawable) {
                    view.setImage((Drawable) r);
                } else if (r instanceof String) {
                    svg.setVisibility(View.VISIBLE);
                    svg.loadDataWithBaseURL(null, (String) r, "text/html", "utf-8", null);
                } else {
                    Throwable t = (Throwable) r;
                    String msg = t instanceof OutOfMemoryError ? "La imagen es demasiado grande para la memoria."
                            : t.getMessage() == null ? t.toString() : t.getMessage();
                    error.setText("No se pudo abrir esta imagen.\n\n" + msg + "\n\nPrueba con «Abrir con otra app» (abajo).");
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private static final class Decoded {
        final Object image;
        final String details;

        Decoded(Object image, String details) {
            this.image = image;
            this.details = details;
        }
    }

    private Decoded decode(String path) throws Exception {
        String ext = Viewers.ext(path);
        File local = new File(path);
        boolean isLocal = path.startsWith("/") && local.isFile() && local.canRead();
        long size = isLocal ? local.length() : -1;
        if (ext.equals("svg") || ext.equals("svgz")) {
            byte[] data = read(path);
            if (ext.equals("svgz")) data = ImageCodecs.readAll(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data)));
            String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
            String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=12'></head>"
                    + "<body style='margin:0;background:#000;display:flex;align-items:center;justify-content:center;min-height:100vh'>"
                    + "<img style='max-width:100%;max-height:100vh;background:#fff' src='data:image/svg+xml;base64," + b64 + "'></body></html>";
            return new Decoded(html, details(path, "SVG (vectorial)", -1, -1, data.length));
        }
        if (ImageCodecs.handles(ext)) {
            byte[] data = read(path);
            Bitmap bm = ImageCodecs.decode(ext, data);
            return new Decoded(new BitmapDrawable(getResources(), bm), details(path, ext.toUpperCase(Locale.ROOT), bm.getWidth(), bm.getHeight(), data.length));
        }
        // lo que abre Android: JPG, PNG, GIF, WebP, BMP, ICO, HEIC, AVIF, RAW…
        byte[] data = isLocal ? null : read(path);
        if (data != null) size = data.length;
        ImageDecoder.Source src = isLocal ? ImageDecoder.createSource(local) : ImageDecoder.createSource(ByteBuffer.wrap(data));
        int[] dims = new int[2];
        String[] mime = new String[1];
        try {
            Drawable d = ImageDecoder.decodeDrawable(src, (dec, inf, s) -> {
                dims[0] = inf.getSize().getWidth();
                dims[1] = inf.getSize().getHeight();
                mime[0] = inf.getMimeType();
                // fotos gigantes: se achican lo justo para que quepan en la memoria
                int sample = 1;
                while ((long) dims[0] / sample * (dims[1] / sample) > 24_000_000L || Math.max(dims[0], dims[1]) / sample > 8192) sample *= 2;
                if (sample > 1) dec.setTargetSampleSize(sample);
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            String fmt = mime[0] == null ? ext.toUpperCase(Locale.ROOT) : mime[0].replace("image/", "").toUpperCase(Locale.ROOT);
            if (d instanceof android.graphics.drawable.AnimatedImageDrawable) fmt += " (animada)";
            return new Decoded(d, details(path, fmt, dims[0], dims[1], size));
        } catch (Exception e) {
            // último intento con el decodificador viejo
            if (data == null) data = read(path);
            BitmapFactory.Options o = new BitmapFactory.Options();
            Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (bm == null) throw new Exception("Android no reconoce este formato (" + ext.toUpperCase(Locale.ROOT) + ").");
            return new Decoded(new BitmapDrawable(getResources(), bm), details(path, ext.toUpperCase(Locale.ROOT), bm.getWidth(), bm.getHeight(), data.length));
        }
    }

    private byte[] read(String path) throws Exception {
        try (InputStream in = FsRouter.get(this, path).in(path)) {
            return ImageCodecs.readAll(in);
        }
    }

    private String details(String path, String format, int w, int h, long size) {
        StringBuilder s = new StringBuilder();
        s.append("Nombre: ").append(Paths.name(path)).append('\n');
        s.append("Formato: ").append(format).append('\n');
        if (w > 0 && h > 0) {
            s.append("Tamaño: ").append(w).append(" × ").append(h).append(" píxeles");
            s.append(String.format(Locale.ROOT, " (%.1f MP)", w * (double) h / 1e6)).append('\n');
        }
        if (size >= 0) s.append("Peso: ").append(Ui.size(size)).append('\n');
        s.append("Carpeta: ").append(Paths.parent(path));
        return s.toString();
    }

    private void showInfo() {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Datos de la imagen")
                .setMessage(info.isEmpty() ? "Todavía cargando…" : info)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void share() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_STREAM, StorageProvider.documentUri(this, paths[index]));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Compartir imagen"));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "No se pudo compartir", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (svg != null) svg.destroy();
        super.onDestroy();
    }
}
