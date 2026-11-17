package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Visor de planos de Beautiful Decor (.bdplan): ficha, lista de materiales, mapa en 2D (arriba, capas y cortes) y vista en 3D. */
public class BdPlanActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private BdPlan plan;
    private FrameLayout content;
    private LinearLayout tabs;
    private TextView status;
    private int tab = -1;
    private final View[] views = new View[4];

    // 2D
    private PlanMapView map;
    private int mapMode = PlanMapView.TOP, mapLevel;
    private SeekBar mapSeek;
    private TextView mapLabel;
    private final AtomicBoolean mapBusy = new AtomicBoolean();
    private boolean mapDirty;

    // 3D
    private PlanGlView gl;
    private int cutY = -1;
    private final AtomicBoolean meshBusy = new AtomicBoolean();
    private boolean meshDirty;
    private TextView glLabel;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(8), dp(12), dp(4));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> finish()));
        TextView title = Ui.text(this, path == null ? "Plano" : Paths.name(path), 18, Ui.TEXT, true);
        title.setPadding(dp(6), 0, 0, 0);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        head.addView(title, Ui.weight(1));
        root.addView(head);
        tabs = Ui.row(this);
        tabs.setPadding(dp(10), dp(2), dp(10), dp(6));
        String[] names = {"ℹ Ficha", "🧱 Materiales", "🗺 2D", "🧊 3D"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            TextView t = Ui.text(this, names[i], 14, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            t.setOnClickListener(v -> select(k));
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(t, lp);
        }
        root.addView(tabs);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        status = Ui.text(this, L.t("Abriendo el plano…"), 15, Ui.SUB, false);
        status.setGravity(Gravity.CENTER);
        content.addView(status, new FrameLayout.LayoutParams(-1, -1));
        paintTabs();
        if (path == null) {
            finish();
            return;
        }
        io.execute(() -> {
            try (InputStream in = FsRouter.get(this, path).in(path)) {
                BdPlan p = BdPlan.readAny(Paths.name(path), in, what -> main.post(() -> status.setText(L.t(what))));
                main.post(() -> {
                    plan = p;
                    cutY = p.sy - 1;
                    mapLevel = p.sy - 1;
                    select(0);
                });
            } catch (Throwable t) {
                main.post(() -> new AlertDialog.Builder(this).setTitle(L.t("No se pudo abrir el plano"))
                        .setMessage(t instanceof OutOfMemoryError ? L.t("El plano es demasiado grande para la memoria de este celular.") : (t.getMessage() == null ? t.toString() : t.getMessage()))
                        .setPositiveButton("OK", (d, w) -> finish()).setOnCancelListener(d -> finish()).show());
            }
        });
    }

    private void paintTabs() {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            boolean on = i == tab;
            TextView t = (TextView) tabs.getChildAt(i);
            t.setBackground(Ui.round(on ? Ui.SURFACE3 : Ui.SURFACE, dp(12)));
            t.setTextColor(on ? Ui.ACCENT2 : Ui.TEXT);
        }
    }

    private void select(int i) {
        if (plan == null) return;
        tab = i;
        paintTabs();
        content.removeAllViews();
        if (views[i] == null) {
            views[i] = switch (i) {
                case 0 -> infoView();
                case 1 -> materialsView();
                case 2 -> mapView();
                default -> glView();
            };
        }
        content.addView(views[i], new FrameLayout.LayoutParams(-1, -1));
        if (i == 2) requestMap();
        if (i == 3) requestMesh();
    }

    // ------------------------------------------------------------------ ficha

    private View infoView() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(18), dp(8), dp(18), dp(30));
        sc.addView(box);
        box.addView(Ui.text(this, plan.name.isEmpty() ? L.t("(sin nombre)") : plan.name, 22, Ui.TEXT, true));
        if (!plan.description.isEmpty()) box.addView(Ui.text(this, plan.description, 14, Ui.SUB, false));
        long air = 0, solid = 0;
        for (int i = 0; i < plan.palette.length; i++) {
            if (BlockColors.kind(plan.palette[i]) == BlockColors.AIR) air += plan.counts[i];
            else solid += plan.counts[i];
        }
        String[][] rows = {
                {L.t("Autor"), plan.author.isEmpty() ? "—" : plan.author},
                {L.t("Creado"), plan.created > 0 ? Ui.date(plan.created) : "—"},
                {L.t("Tamaño"), plan.sx + " × " + plan.sy + " × " + plan.sz + L.t(" bloques (ancho × alto × largo)")},
                {L.t("Bloques con algo"), String.format("%,d", solid) + L.t(" de ") + String.format("%,d", plan.volume())},
                {L.t("Tipos de bloque"), String.valueOf(plan.palette.length)},
                {L.t("Bloques con datos"), String.valueOf(plan.blockEntities)},
                {L.t("Posición original"), plan.ox + ", " + plan.oy + ", " + plan.oz},
                {L.t("Versión del formato"), String.valueOf(plan.format)}};
        for (String[] r : rows) {
            LinearLayout row = Ui.column(this);
            row.setPadding(0, dp(12), 0, 0);
            row.addView(Ui.text(this, r[0], 12, Ui.SUB, false));
            row.addView(Ui.text(this, r[1], 15.5f, Ui.TEXT, false));
            box.addView(row);
        }
        if (!plan.entities.isEmpty()) {
            TextView h = Ui.text(this, L.t("Entidades"), 12, Ui.SUB, false);
            h.setPadding(0, dp(16), 0, dp(4));
            box.addView(h);
            int shown = 0;
            for (BdPlan.Ent e : plan.entityList) {
                if (shown++ >= 80) {
                    box.addView(Ui.text(this, L.t("…y ") + (plan.entityList.size() - 80) + L.t(" más"), 13, Ui.SUB, false));
                    break;
                }
                box.addView(Ui.text(this, EntityModels.label(e) + String.format("  ·  %.0f, %.0f, %.0f", e.x, e.y, e.z), 14, Ui.TEXT, false));
            }
            TextView tip = Ui.text(this, L.t("En 2D y 3D se dibujan como cajas de colores. En el mapa 2D, toca una para ver su nombre y su posición."), 12.5f, Ui.SUB, false);
            tip.setPadding(0, dp(10), 0, 0);
            box.addView(tip);
        }
        return sc;
    }

    // ------------------------------------------------------------------ materiales

    private static final class Mat {
        final String name;
        final long count;
        final int color;

        Mat(String n, long c, int col) {
            name = n;
            count = c;
            color = col;
        }
    }

    private View materialsView() {
        List<Mat> all = new ArrayList<>();
        for (int i = 0; i < plan.palette.length; i++) {
            if (BlockColors.kind(plan.palette[i]) == BlockColors.AIR || plan.counts[i] == 0) continue;
            all.add(new Mat(plan.palette[i], plan.counts[i], BlockColors.color(plan.palette[i])));
        }
        Collections.sort(all, (a, b) -> Long.compare(b.count, a.count));
        final List<Mat> shown = new ArrayList<>(all);
        LinearLayout col = Ui.column(this);
        EditText search = new EditText(this);
        search.setHint(L.t("Buscar un bloque…"));
        search.setHintTextColor(0xFF5E6884);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine();
        search.setPadding(dp(18), dp(10), dp(18), dp(10));
        col.addView(search);
        TextView total = Ui.text(this, "", 12.5f, Ui.SUB, false);
        total.setPadding(dp(18), 0, dp(18), dp(6));
        col.addView(total);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            public int getCount() {
                return shown.size();
            }

            public Object getItem(int i) {
                return shown.get(i);
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View v, ViewGroup p) {
                Mat m = shown.get(i);
                LinearLayout r = Ui.row(BdPlanActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(18), dp(8), dp(18), dp(8));
                View sw = new View(BdPlanActivity.this);
                sw.setBackground(Ui.roundStroke(m.color, dp(6), dp(1), Ui.STROKE));
                r.addView(sw, new LinearLayout.LayoutParams(dp(26), dp(26)));
                TextView n = Ui.text(BdPlanActivity.this, m.name, 14.5f, Ui.TEXT, false);
                n.setPadding(dp(12), 0, 0, 0);
                n.setSingleLine();
                n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                r.addView(n, Ui.weight(1));
                r.addView(Ui.text(BdPlanActivity.this, String.format("%,d", m.count), 14.5f, Ui.ACCENT2, true));
                return r;
            }
        });
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        Runnable refresh = () -> {
            String q = search.getText().toString().trim().toLowerCase();
            shown.clear();
            long sum = 0;
            for (Mat m : all) if (q.isEmpty() || m.name.toLowerCase().contains(q)) {
                shown.add(m);
                sum += m.count;
            }
            total.setText(shown.size() + L.t(" tipos · ") + String.format("%,d", sum) + L.t(" bloques"));
            ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                refresh.run();
            }
        });
        refresh.run();
        return col;
    }

    // ------------------------------------------------------------------ 2D

    private View mapView() {
        LinearLayout col = Ui.column(this);
        map = new PlanMapView(this, plan);
        map.onEntity = (label, x, y, z) -> Toast.makeText(this, label + String.format("\n%.1f, %.1f, %.1f", x, y, z), Toast.LENGTH_LONG).show();
        col.addView(map, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout modes = Ui.row(this);
        modes.setPadding(dp(8), dp(6), dp(8), dp(2));
        String[] names = {L.t("Desde arriba"), L.t("Capas"), L.t("Corte ↔"), L.t("Corte ↕")};
        int[] ids = {PlanMapView.TOP, PlanMapView.LAYER, PlanMapView.CUT_Z, PlanMapView.CUT_X};
        for (int i = 0; i < names.length; i++) {
            final int mode = ids[i];
            TextView t = Ui.text(this, names[i], 13, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(4), dp(9), dp(4), dp(9));
            t.setBackground(Ui.round(Ui.SURFACE, dp(10)));
            t.setOnClickListener(v -> {
                mapMode = mode;
                mapLevel = mode == PlanMapView.LAYER ? plan.sy - 1 : PlanMapView.levels(plan, mode) / 2;
                setupSeek();
                requestMap();
                for (int k = 0; k < modes.getChildCount(); k++) modes.getChildAt(k).setBackground(Ui.round(k == indexOf(ids, mapMode) ? Ui.SURFACE3 : Ui.SURFACE, dp(10)));
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            modes.addView(t, lp);
        }
        modes.getChildAt(0).setBackground(Ui.round(Ui.SURFACE3, dp(10)));
        col.addView(modes);
        mapLabel = Ui.text(this, "", 12.5f, Ui.SUB, false);
        mapLabel.setPadding(dp(18), dp(6), dp(18), 0);
        col.addView(mapLabel);
        mapSeek = new SeekBar(this);
        mapSeek.setPadding(dp(18), dp(4), dp(18), dp(14));
        mapSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                mapLevel = p;
                updateMapLabel();
                requestMap();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        col.addView(mapSeek);
        setupSeek();
        return col;
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    private void setupSeek() {
        int n = PlanMapView.levels(plan, mapMode);
        mapSeek.setVisibility(mapMode == PlanMapView.TOP ? View.GONE : View.VISIBLE);
        mapSeek.setMax(Math.max(0, n - 1));
        mapSeek.setProgress(mapLevel);
        updateMapLabel();
    }

    private void updateMapLabel() {
        mapLabel.setText(switch (mapMode) {
            case PlanMapView.TOP -> L.t("Vista desde arriba (lo más alto de cada lugar)");
            case PlanMapView.LAYER -> L.t("Capa de altura ") + mapLevel + " / " + (plan.sy - 1) + L.t("  ·  lo de abajo se ve apagado");
            case PlanMapView.CUT_Z -> L.t("Corte de frente, en la posición ") + mapLevel + " / " + (plan.sz - 1);
            default -> L.t("Corte de lado, en la posición ") + mapLevel + " / " + (plan.sx - 1);
        });
    }

    private void requestMap() {
        if (map == null) return;
        if (!mapBusy.compareAndSet(false, true)) {
            mapDirty = true;
            return;
        }
        final int mode = mapMode, level = mapLevel;
        io.execute(() -> {
            Bitmap b = null;
            try {
                b = map.render(mode, level);
            } catch (Throwable ignored) {
            }
            final Bitmap fb = b;
            main.post(() -> {
                if (fb != null) map.show(fb, mode, level);
                mapBusy.set(false);
                if (mapDirty) {
                    mapDirty = false;
                    requestMap();
                }
            });
        });
    }

    // ------------------------------------------------------------------ 3D

    // cámara libre
    private TextView freeBtn, glHint;
    private LinearLayout freePad, freeVert, glBar;
    private FrameLayout.LayoutParams glBarLp;
    private float freeSpeed = 6f;            // bloques por segundo
    private float mFwd, mStrafe, mUp;         // lo que se está pulsando ahora
    private final Runnable mover = new Runnable() {
        long last;

        @Override
        public void run() {
            if (gl == null || !gl.isFree()) return;
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            if (mFwd != 0 || mStrafe != 0 || mUp != 0) {
                gl.moveFree(mFwd * freeSpeed * dt, mStrafe * freeSpeed * dt, mUp * freeSpeed * dt);
                main.postDelayed(this, 16);
            } else last = 0;
        }
    };

    private void holdButton(TextView b, float fwd, float strafe, float up) {
        b.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN) {
                mFwd = fwd;
                mStrafe = strafe;
                mUp = up;
                v.setPressed(true);
                main.removeCallbacks(mover);
                main.post(mover);
            } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                mFwd = 0;
                mStrafe = 0;
                mUp = 0;
                v.setPressed(false);
            }
            return true;
        });
    }

    private TextView padButton(String s) {
        TextView t = Ui.text(this, s, 22, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(0xB3101A3A, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(54));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView bigButton(String s) {
        TextView t = Ui.text(this, s, 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(0xCC101A3A, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(52));
        lp.setMargins(0, dp(4), 0, dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private View glView() {
        FrameLayout f = new FrameLayout(this);
        gl = new PlanGlView(this, plan.sx, plan.sy, plan.sz);
        f.addView(gl, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bar = Ui.column(this);
        glBar = bar;
        bar.setBackground(Ui.round(0xB3101A3A, dp(14)));
        bar.setPadding(dp(14), dp(8), dp(14), dp(6));
        glLabel = Ui.text(this, L.t("Preparando el 3D…"), 12.5f, Ui.TEXT, false);
        bar.addView(glLabel);
        SeekBar cut = new SeekBar(this);
        cut.setMax(plan.sy - 1);
        cut.setProgress(plan.sy - 1);
        cut.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) glLabel.setText(L.t("Cortar a la altura ") + p + " / " + (plan.sy - 1));
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
                cutY = s.getProgress();
                requestMesh();
            }
        });
        bar.addView(cut);
        TextView hint = Ui.text(this, L.t("Un dedo gira · dos dedos acercan y mueven · dos toques: volver"), 11.5f, Ui.SUB, false);
        glHint = hint;
        bar.addView(hint);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(10), 0, dp(150), dp(10));
        glBarLp = lp;
        f.addView(bar, lp);

        // botón de la esquina inferior derecha
        freeBtn = Ui.text(this, "🎥 " + L.t("Cámara libre"), 13.5f, Ui.TEXT, true);
        freeBtn.setGravity(Gravity.CENTER);
        freeBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        freeBtn.setBackground(Ui.round(0xE61E4FD6, dp(14)));
        freeBtn.setOnClickListener(v -> toggleFree());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(132), dp(64), Gravity.BOTTOM | Gravity.END);
        bl.setMargins(0, 0, dp(10), dp(10));
        f.addView(freeBtn, bl);

        // flechas (a la izquierda) y subir/bajar (a la derecha), solo en cámara libre
        freePad = Ui.column(this);
        LinearLayout r1 = Ui.row(this), r2 = Ui.row(this);
        TextView up = padButton("▲"), left = padButton("◀"), down = padButton("▼"), right = padButton("▶");
        holdButton(up, 1, 0, 0);
        holdButton(down, -1, 0, 0);
        holdButton(left, 0, -1, 0);
        holdButton(right, 0, 1, 0);
        r1.setGravity(Gravity.CENTER);
        r1.addView(up);
        r2.addView(left);
        r2.addView(down);
        r2.addView(right);
        freePad.addView(r1);
        freePad.addView(r2);
        freePad.setVisibility(View.GONE);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        pl.setMargins(dp(8), 0, 0, dp(110));
        f.addView(freePad, pl);

        freeVert = Ui.column(this);
        TextView rise = bigButton("⬆ " + L.t("Subir")), sink = bigButton("⬇ " + L.t("Bajar")), speed = bigButton("🐢");
        holdButton(rise, 0, 0, 1);
        holdButton(sink, 0, 0, -1);
        speed.setOnClickListener(v -> {
            freeSpeed = freeSpeed < 10 ? 18f : freeSpeed < 30 ? 50f : 6f;
            speed.setText(freeSpeed < 10 ? "🐢" : freeSpeed < 30 ? "🚶" : "🚀");
        });
        freeVert.addView(rise);
        freeVert.addView(sink);
        freeVert.addView(speed);
        freeVert.setVisibility(View.GONE);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        vl.setMargins(0, 0, dp(10), dp(84));
        f.addView(freeVert, vl);
        return f;
    }

    private void toggleFree() {
        boolean on = !gl.isFree();
        gl.setFree(on);
        freeBtn.setText(on ? "✖ " + L.t("Salir") : "🎥 " + L.t("Cámara libre"));
        freeBtn.setBackground(Ui.round(on ? 0xE6C62828 : 0xE61E4FD6, dp(14)));
        freePad.setVisibility(on ? View.VISIBLE : View.GONE);
        freeVert.setVisibility(on ? View.VISIBLE : View.GONE);
        // en cámara libre, el menú de la altura se va arriba a la derecha para no tapar nada
        if (on) {
            glBarLp.width = dp(230);
            glBarLp.gravity = Gravity.TOP | Gravity.END;
            glBarLp.setMargins(0, dp(10), dp(10), 0);
            glHint.setVisibility(View.GONE);
        } else {
            glBarLp.width = -1;
            glBarLp.gravity = Gravity.BOTTOM;
            glBarLp.setMargins(dp(10), 0, dp(150), dp(10));
            glHint.setVisibility(View.VISIBLE);
        }
        glBar.setLayoutParams(glBarLp);
        glLabel.setText(on ? L.t("Cámara libre: arrastra para mirar, las flechas te mueven; puedes entrar bajo tierra") : glLabel.getText());
        if (!on) {
            mFwd = 0;
            mStrafe = 0;
            mUp = 0;
        }
    }

    private void requestMesh() {
        if (gl == null) return;
        if (!meshBusy.compareAndSet(false, true)) {
            meshDirty = true;
            return;
        }
        final int top = cutY;
        glLabel.setText(L.t("Preparando el 3D…"));
        io.execute(() -> {
            VoxelMesh m = null;
            String err = null;
            try {
                m = VoxelMesh.build(plan, top, null);
            } catch (Throwable t) {
                err = t instanceof OutOfMemoryError ? L.t("Se acabó la memoria preparando el 3D") : String.valueOf(t.getMessage());
            }
            final VoxelMesh fm = m;
            final String ferr = err;
            main.post(() -> {
                if (fm != null) {
                    gl.setMesh(fm);
                    glLabel.setText(String.format("%,d", fm.quads) + L.t(" caras · altura ") + top + " / " + (plan.sy - 1));
                } else glLabel.setText(ferr);
                meshBusy.set(false);
                if (meshDirty) {
                    meshDirty = false;
                    requestMesh();
                }
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gl != null) gl.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gl != null) gl.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
