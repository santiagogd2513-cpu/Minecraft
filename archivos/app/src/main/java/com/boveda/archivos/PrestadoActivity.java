package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modo prestado: le dejas el teléfono a alguien y solo puede ver UNA carpeta (y lo que tenga dentro): fotos, videos,
 * música y PDF. No puede borrar, mover, compartir, ver otras carpetas ni la Bóveda. Para salir hace falta el
 * bloqueo del teléfono (PIN, patrón o huella). Además fija la pantalla (como «fijar app») para que no pueda irse a otra app.
 */
public class PrestadoActivity extends Activity {
    private static final int REQ_EXIT = 1;
    private String root, cur;
    private final List<Entry> items = new ArrayList<>();
    private ListView list;
    private TextView title;
    private View back;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean leaving;

    /** ¿El teléfono tiene bloqueo? Sin él nadie podría proteger la salida. */
    static boolean canUse(Activity a) {
        KeyguardManager k = a.getSystemService(KeyguardManager.class);
        return k != null && k.isDeviceSecure();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = getIntent().getStringExtra("root");
        if (root == null || !canUse(this)) {
            finish();
            return;
        }
        cur = b != null && b.getString("cur") != null && b.getString("cur").startsWith(root) ? b.getString("cur") : root;
        Viewers.loan = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(Ui.BG);
        col.setFitsSystemWindows(true);
        LinearLayout bar = Ui.row(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView bk = Ui.text(this, "←", 22, Ui.TEXT, true);
        bk.setGravity(Gravity.CENTER);
        bk.setMinWidth(dp(46));
        bk.setMinHeight(dp(46));
        bk.setOnClickListener(v -> up());
        back = bk;
        bar.addView(bk);
        title = Ui.text(this, "", 17, Ui.TEXT, true);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, Ui.weight(1));
        TextView lock = Ui.text(this, "🔒", 20, Ui.TEXT, false);
        lock.setGravity(Gravity.CENTER);
        lock.setMinWidth(dp(46));
        lock.setMinHeight(dp(46));
        lock.setOnClickListener(v -> askExit());
        bar.addView(lock);
        col.addView(bar);
        col.addView(Ui.text(this, L.t("Modo prestado · solo se puede mirar"), 12, Ui.SUB, false), new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) col.getChildAt(1).getLayoutParams()).setMargins(dp(16), 0, 0, dp(4));

        list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public Object getItem(int i) {
                return items.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View v, ViewGroup p) {
                Entry e = items.get(i);
                LinearLayout r = Ui.row(PrestadoActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(14), dp(8), dp(14), dp(8));
                ImageView ic = new ImageView(PrestadoActivity.this);
                ic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ic.setBackground(Ui.round(Ui.SURFACE2, dp(10)));
                ic.setClipToOutline(true);
                ic.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                Bitmap c = e.dir ? null : Thumbs.cached(e);
                if (c != null) ic.setImageBitmap(c);
                else {
                    TextView em = null;
                    ic.setImageDrawable(null);
                    if (!e.dir) Thumbs.load(PrestadoActivity.this, e, dp(56), bm -> ((BaseAdapter) list.getAdapter()).notifyDataSetChanged());
                }
                r.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(52)));
                LinearLayout t = Ui.column(PrestadoActivity.this);
                t.setPadding(dp(12), 0, 0, 0);
                String mark = e.dir ? "📁 " : Viewers.isVideo(e.name) ? "🎬 " : Viewers.isAudio(e.name) ? "🎵 " : Viewers.isPdf(e.name) ? "📄 " : Viewers.isImage(e.name) ? "🖼️ " : "";
                TextView n = Ui.text(PrestadoActivity.this, mark + e.name, 15, Ui.TEXT, false);
                n.setSingleLine();
                n.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.addView(n);
                if (!e.dir) t.addView(Ui.text(PrestadoActivity.this, Ui.size(e.size), 12, Ui.SUB, false));
                r.addView(t, Ui.weight(1));
                return r;
            }
        });
        list.setOnItemClickListener((a, v, i, id) -> {
            Entry e = items.get(i);
            if (e.dir) {
                cur = e.path;
                load();
            } else if (!(Viewers.isMedia(e.name) || Viewers.isImage(e.name) || Viewers.isPdf(e.name))) {
                Toast.makeText(this, L.t("Este tipo de archivo no se puede abrir en modo prestado"), Toast.LENGTH_SHORT).show();
            } else {
                List<Entry> sib = new ArrayList<>();
                for (Entry x : items) if (!x.dir) sib.add(x);
                Viewers.open(this, e, sib);
            }
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);
        load();
        try {
            startLockTask();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("cur", cur);
    }

    private void load() {
        final String dir = cur;
        title.setText(dir.equals(root) ? Paths.name(root) : Paths.name(dir));
        back.setVisibility(dir.equals(root) ? View.INVISIBLE : View.VISIBLE);
        io.execute(() -> {
            List<Entry> out = new ArrayList<>();
            try {
                for (Entry e : FsRouter.get(this, dir).list(dir)) {
                    if (e.hidden()) continue;
                    out.add(e);
                }
            } catch (Exception ignored) {
            }
            Collections.sort(out, (a, b2) -> a.dir != b2.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b2.name));
            main.post(() -> {
                if (!dir.equals(cur)) return;
                items.clear();
                items.addAll(out);
                ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                list.setSelection(0);
                if (out.isEmpty()) Toast.makeText(this, L.t("Carpeta vacía"), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void up() {
        if (cur.equals(root)) return;
        String p = Paths.parent(cur);
        cur = p.startsWith(root) && p.length() >= root.length() ? p : root;
        load();
    }

    @Override
    public void onBackPressed() {
        if (cur.equals(root)) {
            Toast.makeText(this, L.t("Para salir toca el candado 🔒"), Toast.LENGTH_SHORT).show();
            return;
        }
        up();
    }

    private void askExit() {
        new AlertDialog.Builder(this).setMessage(L.t("¿Salir del modo prestado? Te pediré el bloqueo del teléfono."))
                .setPositiveButton(L.t("Salir"), (d, w) -> {
                    KeyguardManager k = getSystemService(KeyguardManager.class);
                    Intent i = k == null ? null : k.createConfirmDeviceCredentialIntent(L.t("Salir del modo prestado"), null);
                    if (i == null) {
                        finishLoan();
                        return;
                    }
                    try {
                        startActivityForResult(i, REQ_EXIT);
                    } catch (Exception e) {
                        Toast.makeText(this, L.t("No se pudo pedir el bloqueo"), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(L.t("Quedarme"), null).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXIT && res == RESULT_OK) finishLoan();
    }

    private void finishLoan() {
        leaving = true;
        Viewers.loan = false;
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (leaving) Viewers.loan = false;
        io.shutdownNow();
    }
}
