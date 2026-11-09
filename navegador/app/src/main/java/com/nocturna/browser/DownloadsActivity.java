package com.nocturna.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pantalla de descargas: tarjetas con el progreso de cada conexión, velocidad y tiempo restante. */
public class DownloadsActivity extends Activity implements DownloadEngine.Listener {
    private DownloadEngine engine;
    private LinearLayout list;
    private TextView summary;
    private TextView speedBig;
    private TextView where;
    private View emptyView;
    private final Map<Long, Card> cards = new HashMap<>();
    private List<Long> order = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        engine = DownloadEngine.get(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        // ---- encabezado
        LinearLayout header = Ui.row(this);
        header.setPadding(dp(8), dp(10), dp(12), dp(4));
        header.addView(Ui.iconButton(this, R.drawable.ic_back, 46, v -> finish()));
        TextView title = Ui.text(this, "Descargas", 24, Ui.TEXT, true);
        title.setPadding(dp(6), 0, 0, 0);
        header.addView(title, Ui.weight(1));
        TextView clear = Ui.text(this, "Limpiar", 14, Ui.ACCENT, true);
        clear.setPadding(dp(12), dp(8), dp(12), dp(8));
        clear.setBackground(Ui.rippleRound(0x00000000, dp(18)));
        clear.setOnClickListener(v -> engine.clearFinished());
        header.addView(clear);
        root.addView(header);

        // ---- panel de velocidad total
        LinearLayout panel = Ui.column(this);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(Ui.gradient(dp(20), 0xFF14234A, 0xFF0F1A33, 0xFF101A2C));
        speedBig = Ui.text(this, "0 KB/s", 30, Ui.TEXT, true);
        summary = Ui.text(this, "", 13, Ui.SUB, false);
        where = Ui.text(this, "", 12, 0xFF6F86AE, false);
        where.setOnClickListener(v -> StoragePermission.ask(this, null));
        where.setPadding(0, dp(6), 0, 0);
        panel.addView(speedBig);
        panel.addView(summary);
        panel.addView(where);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(dp(14), dp(6), dp(14), dp(10));
        root.addView(panel, plp);

        // ---- lista
        ScrollView scroll = new ScrollView(this);
        list = Ui.column(this);
        list.setPadding(dp(14), 0, dp(14), dp(24));
        scroll.addView(list);
        FrameLayout body = new FrameLayout(this);
        body.addView(scroll);
        emptyView = empty();
        body.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refresh();
    }

    private View empty() {
        LinearLayout l = Ui.column(this);
        l.setGravity(Gravity.CENTER);
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, R.drawable.ic_download, 0xFF2C3A57));
        l.addView(i, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView t = Ui.text(this, "Todavía no hay descargas", 16, Ui.SUB, true);
        t.setPadding(0, dp(12), 0, 0);
        l.addView(t);
        return l;
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.addListener(this);
        engine.forgetMissing();
        refresh();
    }

    @Override
    protected void onPause() {
        engine.removeListener(this);
        super.onPause();
    }

    @Override
    public void onDownloadsChanged() {
        refresh();
    }

    private void refresh() {
        List<DownloadItem> items = engine.items();
        List<Long> ids = new ArrayList<>();
        for (DownloadItem d : items) ids.add(d.id);
        if (!ids.equals(order)) {
            order = ids;
            list.removeAllViews();
            Map<Long, Card> keep = new HashMap<>();
            for (DownloadItem d : items) {
                Card c = cards.get(d.id);
                if (c == null) c = new Card(this);
                keep.put(d.id, c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(12));
                list.addView(c.root, lp);
            }
            cards.clear();
            cards.putAll(keep);
        }
        double speed = 0;
        int active = 0, finished = 0;
        for (DownloadItem d : items) {
            Card c = cards.get(d.id);
            if (c != null) c.bind(d);
            if (d.status == DownloadItem.RUNNING) {
                speed += d.speed;
                active++;
            }
            if (d.status == DownloadItem.DONE) finished++;
        }
        speedBig.setText(active > 0 ? Ui.speed(speed) : "En reposo");
        summary.setText(active + (active == 1 ? " descarga activa" : " descargas activas") + " · " + finished
                + (finished == 1 ? " terminada" : " terminadas"));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        where.setText(DownloadEngine.canUseRootFolder() ? "Se guardan en  Nocturna Downloads  (almacenamiento principal)"
                : "Se guardan en  Descargas / Nocturna  ·  toca aquí para usar Nocturna Downloads");
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ tarjeta

    private final class Card {
        final LinearLayout root;
        final TextView badge, name, status, detail;
        final SegmentBar bar;
        final LinearLayout actions;
        DownloadItem item;
        int boundStatus = -1;

        Card(Context c) {
            root = Ui.column(c);
            root.setPadding(dp(14), dp(14), dp(14), dp(12));
            root.setBackground(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));

            LinearLayout top = Ui.row(c);
            badge = Ui.text(c, "", 12, 0xFF0A0E16, true);
            badge.setGravity(Gravity.CENTER);
            top.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout texts = Ui.column(c);
            texts.setPadding(dp(12), 0, 0, 0);
            name = Ui.text(c, "", 15, Ui.TEXT, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            status = Ui.text(c, "", 12.5f, Ui.SUB, false);
            texts.addView(name);
            texts.addView(status);
            top.addView(texts, Ui.weight(1));
            root.addView(top);

            bar = new SegmentBar(c);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(10));
            blp.setMargins(0, dp(12), 0, dp(6));
            root.addView(bar, blp);

            detail = Ui.text(c, "", 12, 0xFF6F86AE, false);
            root.addView(detail);

            actions = Ui.row(c);
            actions.setGravity(Gravity.END);
            actions.setPadding(0, dp(6), 0, 0);
            root.addView(actions);
        }

        void bind(DownloadItem d) {
            item = d;
            name.setText(d.name);
            int[] col = typeColor(d.extension());
            badge.setText(d.extension().isEmpty() ? "?" : d.extension().toUpperCase().substring(0, Math.min(4, d.extension().length())));
            badge.setBackground(Ui.gradient(dp(14), col[0], col[1]));
            bar.set(d);

            long done = d.done.get();
            String sizeText = d.total > 0 ? Ui.size(done) + " de " + Ui.size(d.total) : Ui.size(done);
            switch (d.status) {
                case DownloadItem.RUNNING -> {
                    long left = d.total > 0 && d.speed > 1 ? (long) ((d.total - done) / d.speed) : -1;
                    status.setText(Math.round(d.progress() * 100) + "%  ·  " + Ui.speed(d.speed) + "  ·  " + Ui.eta(left));
                    status.setTextColor(Ui.ACCENT2);
                    int conns = d.connections.get();
                    detail.setText(sizeText + "  ·  " + conns + (conns == 1 ? " conexión" : " conexiones"));
                }
                case DownloadItem.QUEUED -> {
                    status.setText("Conectando…");
                    status.setTextColor(Ui.SUB);
                    detail.setText(sizeText);
                }
                case DownloadItem.PAUSED -> {
                    status.setText("En pausa  ·  " + Math.round(d.progress() * 100) + "%");
                    status.setTextColor(Ui.WARN);
                    detail.setText(sizeText);
                }
                case DownloadItem.FAILED -> {
                    status.setText("Error: " + (d.error == null ? "desconocido" : d.error));
                    status.setTextColor(Ui.BAD);
                    detail.setText(sizeText + "  ·  toca Reintentar");
                }
                default -> {
                    status.setText("Listo  ·  " + Ui.size(d.total));
                    status.setTextColor(Ui.GOOD);
                    long secs = Math.max(1, (d.finished - d.created) / 1000);
                    detail.setText("Bajó en " + Ui.eta(secs) + (d.total > 0 ? "  ·  promedio " + Ui.speed(d.total / (double) secs) : ""));
                }
            }
            if (boundStatus != d.status) {
                boundStatus = d.status;
                buildActions(d);
            }
        }

        private void buildActions(DownloadItem d) {
            actions.removeAllViews();
            switch (d.status) {
                case DownloadItem.RUNNING, DownloadItem.QUEUED -> {
                    actions.addView(action(R.drawable.ic_pause, "Pausar", Ui.TEXT, v -> engine.pause(d)));
                    actions.addView(action(R.drawable.ic_close, "Cancelar", Ui.BAD, v -> engine.cancel(d)));
                }
                case DownloadItem.PAUSED, DownloadItem.FAILED -> {
                    actions.addView(action(R.drawable.ic_play, d.status == DownloadItem.FAILED ? "Reintentar" : "Seguir",
                            Ui.ACCENT2, v -> engine.start(d)));
                    actions.addView(action(R.drawable.ic_close, "Cancelar", Ui.BAD, v -> engine.cancel(d)));
                }
                default -> {
                    actions.addView(action(R.drawable.ic_open, "Abrir", Ui.ACCENT2, v -> open(d)));
                    actions.addView(action(R.drawable.ic_share, "Compartir", Ui.TEXT, v -> share(d)));
                    actions.addView(action(R.drawable.ic_delete, "Borrar", Ui.SUB, v -> askDelete(d)));
                }
            }
        }

        private View action(int icon, String label, int color, View.OnClickListener l) {
            LinearLayout b = Ui.row(DownloadsActivity.this);
            b.setPadding(dp(10), dp(7), dp(12), dp(7));
            b.setBackground(Ui.rippleRound(Ui.SURFACE2, dp(16)));
            ImageView i = new ImageView(DownloadsActivity.this);
            i.setImageDrawable(Ui.icon(DownloadsActivity.this, icon, color));
            b.addView(i, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView t = Ui.text(DownloadsActivity.this, label, 13, color, true);
            t.setPadding(dp(6), 0, 0, 0);
            b.addView(t);
            b.setOnClickListener(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(8), 0, 0, 0);
            b.setLayoutParams(lp);
            return b;
        }
    }

    private void open(DownloadItem d) {
        try {
            startActivity(DownloadEngine.openIntent(this, d));
        } catch (Exception e) {
            Toast.makeText(this, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void share(DownloadItem d) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(d.mime == null ? "*/*" : d.mime);
        i.putExtra(Intent.EXTRA_STREAM, DownloadEngine.shareUri(this, d));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Compartir " + d.name));
    }

    private static final int REQ_DELETE = 41;
    private DownloadItem pendingDelete;

    private void askDelete(DownloadItem d) {
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle(d.name)
                .setItems(new String[]{"Borrar solo del historial", "Borrar del celular y del historial"}, (dlg, which) -> {
                    if (which == 0) {
                        engine.remove(d, false);
                        Toast.makeText(this, "Quitado del historial (el archivo sigue en el celular)", Toast.LENGTH_SHORT).show();
                    } else {
                        confirmDeleteFile(d);
                    }
                })
                .show();
    }

    private void confirmDeleteFile(DownloadItem d) {
        new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                .setTitle("¿Borrar del celular?")
                .setMessage("«" + d.name + "» se borrará del almacenamiento y del historial. No se puede deshacer.")
                .setPositiveButton("Borrar", (dlg, w) -> new Thread(() -> {
                    DownloadEngine.DeleteResult r = engine.deleteFileNow(d);
                    runOnUiThread(() -> {
                        if (r.ok) {
                            engine.remove(d, false);
                            Toast.makeText(this, "Borrado del celular y del historial", Toast.LENGTH_SHORT).show();
                        } else if (r.confirm != null) {
                            // Android muestra su propia ventanita para confirmar
                            pendingDelete = d;
                            try {
                                startIntentSenderForResult(r.confirm, REQ_DELETE, null, 0, 0, 0);
                            } catch (Exception e) {
                                pendingDelete = null;
                                Toast.makeText(this, "No se pudo pedir permiso para borrar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            new AlertDialog.Builder(this, R.style.Theme_Nocturna_Dialog)
                                    .setTitle("No se pudo borrar")
                                    .setMessage(r.error + "\n\nEl archivo sigue en la lista.")
                                    .setPositiveButton("Entendido", null).show();
                        }
                    });
                }).start())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_DELETE || pendingDelete == null) return;
        DownloadItem d = pendingDelete;
        pendingDelete = null;
        if (res == RESULT_OK) {
            engine.remove(d, false);
            Toast.makeText(this, "Borrado del celular y del historial", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "No se borró: el archivo sigue en el celular y en la lista", Toast.LENGTH_SHORT).show();
        }
    }

    static int[] typeColor(String ext) {
        switch (ext) {
            case "apk", "xapk", "apks": return new int[]{0xFF35D08A, 0xFF1EA36B};
            case "jar": return new int[]{0xFFFFB547, 0xFFF07C2C};
            case "zip", "rar", "7z", "tar", "gz": return new int[]{0xFFFFD84D, 0xFFE0A21B};
            case "mp4", "mkv", "webm", "mov", "avi": return new int[]{0xFFFF6A7A, 0xFFD63A63};
            case "mp3", "ogg", "wav", "flac", "m4a", "opus": return new int[]{0xFFB58CFF, 0xFF7B52E8};
            case "jpg", "jpeg", "png", "gif", "webp", "svg": return new int[]{0xFF5CC8FF, 0xFF2E8BF0};
            case "pdf": return new int[]{0xFFFF7A59, 0xFFE0452B};
            default: return new int[]{0xFF8FB3FF, 0xFF3D8BFF};
        }
    }

    /** Barra que muestra cada conexión llenando su propio tramo del archivo. */
    static final class SegmentBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private List<long[]> segs = new ArrayList<>();
        private long total;
        private float progress;
        private int status;
        private long shimmerStart = System.currentTimeMillis();

        SegmentBar(Context c) {
            super(c);
            track.setColor(0xFF1C2638);
            glow.setColor(0x66FFFFFF);
        }

        void set(DownloadItem d) {
            segs = d.snapshot();
            total = d.total;
            progress = d.progress();
            status = d.status;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, track);
            int a = status == DownloadItem.DONE ? Ui.GOOD : status == DownloadItem.FAILED ? Ui.BAD
                    : status == DownloadItem.PAUSED ? Ui.WARN : Ui.ACCENT;
            int b = status == DownloadItem.DONE ? 0xFF7CF0BE : status == DownloadItem.FAILED ? 0xFFFF9AA5
                    : status == DownloadItem.PAUSED ? 0xFFFFD58A : Ui.ACCENT2;
            fill.setShader(new LinearGradient(0, 0, w, 0, a, b, Shader.TileMode.CLAMP));
            if (total > 0 && segs.size() > 1 && status != DownloadItem.DONE) {
                // un tramo por conexión
                c.save();
                c.clipRect(0, 0, w, h);
                for (long[] s : segs) {
                    float x0 = s[0] / (float) total * w;
                    float x1 = (s[2]) / (float) total * w;
                    if (x1 <= x0) continue;
                    r.set(x0, 0, Math.min(w, x1), h);
                    c.drawRoundRect(r, rad, rad, fill);
                    if (s[3] == 1 && status == DownloadItem.RUNNING) {
                        // punta brillante de cada conexión activa
                        r.set(Math.max(x0, x1 - h * 1.5f), 0, Math.min(w, x1), h);
                        c.drawRoundRect(r, rad, rad, glow);
                    }
                }
                c.restore();
            } else {
                float p = status == DownloadItem.DONE ? 1f : progress;
                if (total <= 0 && status == DownloadItem.RUNNING) {
                    // tamaño desconocido: franja que se mueve
                    float t = ((System.currentTimeMillis() - shimmerStart) % 1400) / 1400f;
                    r.set(w * t - w * 0.3f, 0, w * t, h);
                } else {
                    r.set(0, 0, w * p, h);
                }
                c.drawRoundRect(r, rad, rad, fill);
            }
            if (status == DownloadItem.RUNNING) postInvalidateDelayed(50);
        }
    }
}
