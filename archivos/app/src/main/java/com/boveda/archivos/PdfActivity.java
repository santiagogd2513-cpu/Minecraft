package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visor de PDF: muestra las páginas una debajo de otra, con el lector de PDF del propio Android. */
public class PdfActivity extends Activity {
    private String path;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private File temp;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LruCache<Integer, Bitmap> cache;
    private int pageWidth;
    private TextView pageLabel;
    private ListView list;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        int mem = (int) (Runtime.getRuntime().maxMemory() / 6);
        cache = new LruCache<>(mem) {
            @Override
            protected int sizeOf(Integer k, Bitmap v) {
                return v.getByteCount();
            }
        };
        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(0xFF2A2F3A);
        col.setFitsSystemWindows(true);
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Ui.BG);
        bar.setPadding(dp(4), dp(6), dp(6), dp(6));
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> finish()));
        LinearLayout tt = Ui.column(this);
        TextView title = Ui.text(this, Paths.name(path), 16, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pageLabel = Ui.text(this, L.t("Abriendo…"), 11.5f, Ui.SUB, false);
        tt.addView(title);
        tt.addView(pageLabel);
        bar.addView(tt, Ui.weight(1));
        bar.addView(Ui.iconButton(this, R.drawable.ic_open, 44, v -> Viewers.openWithOther(this, path, "application/pdf")));
        col.addView(bar);
        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(10));
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.setClipToPadding(false);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar spin = new ProgressBar(this);
        body.addView(spin, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        pageWidth = getResources().getDisplayMetrics().widthPixels - dp(16);

        worker.execute(() -> {
            String error = null;
            try {
                try {
                    pfd = getContentResolver().openFileDescriptor(StorageProvider.documentUri(this, path), "r");
                    renderer = new PdfRenderer(pfd);
                } catch (Exception seekable) {
                    // carpetas que no dejan saltar dentro del archivo (FTP, Shizuku…): copia temporal
                    if (pfd != null) try { pfd.close(); } catch (Exception ignored) { }
                    temp = new File(getCacheDir(), "visor.pdf");
                    try (InputStream in = FsRouter.get(this, path).in(path); OutputStream out = new FileOutputStream(temp)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
                    renderer = new PdfRenderer(pfd);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String err = error;
            main.post(() -> {
                body.removeView(spin);
                if (err != null || renderer == null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(L.t("No se pudo abrir el PDF"))
                            .setMessage((err == null ? "" : err) + L.t("\n\nPuede estar protegido con contraseña o dañado."))
                            .setPositiveButton(L.t("Abrir con otra app"), (d, w) -> {
                                Viewers.openWithOther(this, path, "application/pdf");
                                finish();
                            })
                            .setNegativeButton(L.t("Cerrar"), (d, w) -> finish())
                            .setOnCancelListener(d -> finish())
                            .show();
                    return;
                }
                int pages = renderer.getPageCount();
                pageLabel.setText(pages + (pages == 1 ? L.t(" página") : L.t(" páginas")));
                list.setAdapter(new Pages(pages));
                list.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView v, int s) { }

                    @Override
                    public void onScroll(AbsListView v, int first, int visible, int total) {
                        if (total > 0) pageLabel.setText(L.t("Página ") + (first + 1) + L.t(" de ") + total);
                    }
                });
            });
        });
    }

    private final class Pages extends BaseAdapter {
        final int count;

        Pages(int count) {
            this.count = count;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public Object getItem(int p) {
            return p;
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            ImageView iv = convert instanceof ImageView ? (ImageView) convert : new ImageView(PdfActivity.this);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundColor(Color.WHITE);
            iv.setTag(p);
            Bitmap bm = cache.get(p);
            if (bm != null) {
                iv.setImageBitmap(bm);
                return iv;
            }
            iv.setImageBitmap(null);
            iv.setMinimumHeight((int) (pageWidth * 1.3f));
            worker.execute(() -> {
                Bitmap made = render(p);
                main.post(() -> {
                    if (made == null) return;
                    cache.put(p, made);
                    if (Integer.valueOf(p).equals(iv.getTag())) {
                        iv.setMinimumHeight(0);
                        iv.setImageBitmap(made);
                    }
                });
            });
            return iv;
        }
    }

    /** Dibuja una página al ancho de la pantalla (y un poco más nítida, para hacer zoom con la vista). */
    private Bitmap render(int index) {
        if (renderer == null) return null;
        try (PdfRenderer.Page page = renderer.openPage(index)) {
            int w = Math.min(2400, Math.round(pageWidth * 1.5f));
            int h = Math.max(1, Math.round(w * (float) page.getHeight() / page.getWidth()));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(Color.WHITE);
            page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        worker.execute(() -> {
            try {
                if (renderer != null) renderer.close();
                if (pfd != null) pfd.close();
            } catch (Exception ignored) {
            }
            if (temp != null) //noinspection ResultOfMethodCallIgnored
                temp.delete();
        });
        worker.shutdown();
        super.onDestroy();
    }
}
