package com.boveda.archivos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ver lo que hay dentro de un archivo comprimido (como una carpeta), abrir cosas de adentro y extraer. */
public class ArchiveActivity extends Activity {
    static final String EXTRA = "archivo";

    private String archive;
    private String inner = "";          // carpeta interna actual ("" = raíz)
    private final Map<String, List<Entry>> tree = new HashMap<>();
    private final List<Entry> shown = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ListView list;
    private BaseAdapter adapter;
    private TextView title, empty;
    private LinearLayout crumbs, bottom;
    private HorizontalScrollView crumbScroll;
    private ProgressBar loading;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        archive = getIntent().getStringExtra(EXTRA);
        if (archive == null) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        load();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(Ui.BG);
        col.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(4), dp(8), dp(8), dp(2));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 46, v -> onBackPressed()));
        LinearLayout titles = Ui.column(this);
        title = Ui.text(this, Paths.name(archive), 19, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(title);
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.row(this);
        crumbScroll.addView(crumbs);
        titles.addView(crumbScroll);
        head.addView(titles, Ui.weight(1));
        ImageView badge = new ImageView(this);
        badge.setImageDrawable(Ui.icon(this, R.drawable.ic_archive, 0xFFFFC14D));
        head.addView(badge, new LinearLayout.LayoutParams(dp(24), dp(24)));
        col.addView(head);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFFFFC14D));
        col.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, dp(2), 0, dp(16));
        list.setFastScrollEnabled(true);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onTap(shown.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            toggle(shown.get(pos));
            return true;
        });
        content.addView(list);
        empty = Ui.text(this, "Leyendo el archivo…", 15, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), 0, dp(24), 0);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = Ui.row(this);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(10));
        bottom.setBackground(Ui.roundStroke(Ui.SURFACE, 0, dp(1), Ui.STROKE));
        col.addView(bottom);
        setContentView(col);
    }

    // ------------------------------------------------------------------ leer

    private void load() {
        io.execute(() -> {
            List<Archive.Item> items;
            String err = null;
            try {
                items = Archive.list(this, archive);
            } catch (Throwable e) {
                items = null;
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (e instanceof OutOfMemoryError) err = "El archivo es demasiado grande para listarlo";
            }
            final List<Archive.Item> res = items;
            final String error = err;
            main.post(() -> {
                loading.setVisibility(View.INVISIBLE);
                if (res == null) {
                    empty.setText("No se pudo abrir: " + error);
                    return;
                }
                buildTree(res);
                show("");
            });
        });
    }

    /** Arma las carpetas (algunos archivos no traen las carpetas como entradas: se crean). */
    private void buildTree(List<Archive.Item> items) {
        tree.clear();
        tree.put("", new ArrayList<>());
        Map<String, Entry> dirs = new HashMap<>();
        for (Archive.Item it : items) {
            if (it.name.isEmpty() || !Archive.safe(it.name)) continue;
            String[] parts = it.name.split("/");
            String acc = "";
            for (int i = 0; i < parts.length - 1; i++) {
                String next = acc.isEmpty() ? parts[i] : acc + "/" + parts[i];
                if (!dirs.containsKey(next)) {
                    Entry d = new Entry(next, parts[i], true, 0, 0);
                    dirs.put(next, d);
                    tree.computeIfAbsent(acc, k -> new ArrayList<>()).add(d);
                    tree.computeIfAbsent(next, k -> new ArrayList<>());
                }
                acc = next;
            }
            String parent = acc;
            String name = parts[parts.length - 1];
            if (it.dir) {
                if (dirs.containsKey(it.name)) continue;
                Entry d = new Entry(it.name, name, true, 0, it.time);
                dirs.put(it.name, d);
                tree.computeIfAbsent(parent, k -> new ArrayList<>()).add(d);
                tree.computeIfAbsent(it.name, k -> new ArrayList<>());
            } else {
                tree.computeIfAbsent(parent, k -> new ArrayList<>()).add(new Entry(it.name, name, false, Math.max(0, it.size), it.time));
            }
        }
        for (List<Entry> l : tree.values()) {
            Collections.sort(l, (a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : MainActivity.naturalCompare(a.name, b.name));
        }
    }

    private void show(String dir) {
        inner = dir;
        selected.clear();
        shown.clear();
        List<Entry> l = tree.get(dir);
        if (l != null) shown.addAll(l);
        adapter.notifyDataSetChanged();
        list.setSelection(0);
        empty.setText("Vacío");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        buildCrumbs();
        updateBottom();
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        List<String> parts = new ArrayList<>();
        parts.add("");
        if (!inner.isEmpty()) {
            String acc = "";
            for (String s : inner.split("/")) {
                acc = acc.isEmpty() ? s : acc + "/" + s;
                parts.add(acc);
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            boolean last = i == parts.size() - 1;
            TextView t = Ui.text(this, p.isEmpty() ? "Contenido" : Paths.name("/" + p), 12.5f, last ? 0xFFFFC14D : Ui.SUB, last);
            t.setPadding(dp(2), dp(2), dp(2), dp(2));
            t.setOnClickListener(v -> show(p));
            crumbs.addView(t);
            if (!last) crumbs.addView(Ui.text(this, "  ›  ", 12.5f, 0xFF4A5670, false));
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ------------------------------------------------------------------ tocar y elegir

    private void onTap(Entry e) {
        if (!selected.isEmpty()) {
            toggle(e);
            return;
        }
        if (e.dir) show(e.path);
        else openInside(e);
    }

    private void toggle(Entry e) {
        if (!selected.remove(e.path)) selected.add(e.path);
        adapter.notifyDataSetChanged();
        updateBottom();
    }

    private void updateBottom() {
        bottom.removeAllViews();
        if (selected.isEmpty()) {
            bottom.addView(button("Extraer todo aquí", true, v -> extractHere(new ArrayList<>(), "")), Ui.weight(1));
            bottom.addView(spacer());
            bottom.addView(button("Extraer a…", false, v -> extractTo(new ArrayList<>(), "")), Ui.weight(1));
        } else {
            TextView n = Ui.text(this, selected.size() + "", 15, Ui.TEXT, true);
            n.setPadding(dp(6), 0, dp(10), 0);
            bottom.addView(n);
            bottom.addView(button("Extraer aquí", true, v -> extractHere(new ArrayList<>(selected), inner)), Ui.weight(1));
            bottom.addView(spacer());
            bottom.addView(button("Extraer a…", false, v -> extractTo(new ArrayList<>(selected), inner)), Ui.weight(1));
            bottom.addView(Ui.iconButton(this, R.drawable.ic_select_all, 44, v -> {
                if (selected.size() == shown.size()) selected.clear();
                else for (Entry e : shown) selected.add(e.path);
                adapter.notifyDataSetChanged();
                updateBottom();
            }));
        }
    }

    private View spacer() {
        View s = new View(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return s;
    }

    private TextView button(String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(this, text, 14, primary ? 0xFF06101F : Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        b.setBackground(primary ? Ui.ripple(Ui.gradient(dp(22), 0xFFFFD27A, 0xFFFFA94D), Ui.round(Color.WHITE, dp(22)))
                : Ui.ripple(Ui.roundStroke(Ui.SURFACE2, dp(22), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(22))));
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------------ extraer

    /** Junto al archivo, en una carpeta con su nombre. */
    private void extractHere(List<String> picks, String base) {
        String dest = Paths.join(Paths.parent(archive), Archive.baseName(Paths.name(archive)));
        TransferEngine.get(this).submitExtract(archive, picks, base, dest);
        Toast.makeText(this, "Extrayendo en «" + Paths.name(dest) + "»…", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Elegir la carpeta destino en el explorador y tocar «Extraer aquí». */
    private void extractTo(List<String> picks, String base) {
        MainActivity.setExtractClip(archive, picks, base);
        Toast.makeText(this, "Ve a la carpeta destino y toca «Extraer aquí»", Toast.LENGTH_LONG).show();
        finish();
    }

    /** Abre un archivo de adentro: se saca a una carpeta temporal y se abre con la app que corresponda. */
    private void openInside(Entry e) {
        loading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Abriendo " + e.name + "…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            File dir = new File(getCacheDir(), "dentro");
            deleteRec(dir);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, e.name);
            String err = null;
            final boolean[] found = {false};
            try {
                Archive.walk(this, archive, (it, data) -> {
                    if (!it.name.equals(e.path)) return true;
                    try (FileOutputStream o = new FileOutputStream(out)) {
                        if (data != null) {
                            byte[] buf = new byte[1 << 20];
                            int r;
                            while ((r = data.read(buf)) >= 0) o.write(buf, 0, r);
                        }
                    }
                    found[0] = true;
                    return false;
                });
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String fe = err;
            main.post(() -> {
                loading.setVisibility(View.INVISIBLE);
                if (!found[0]) {
                    Toast.makeText(this, fe != null ? fe : "No se encontró dentro del archivo", Toast.LENGTH_LONG).show();
                    return;
                }
                if (Archive.opensOnTap(e.name)) {
                    // un comprimido dentro de otro: se abre aquí mismo
                    startActivity(new Intent(this, ArchiveActivity.class).putExtra(EXTRA, out.getAbsolutePath()));
                    return;
                }
                Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", out);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, FileTypes.mime(e.name));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(i);
                } catch (Exception ex) {
                    Toast.makeText(this, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            selected.clear();
            adapter.notifyDataSetChanged();
            updateBottom();
        } else if (!inner.isEmpty()) {
            int i = inner.lastIndexOf('/');
            show(i < 0 ? "" : inner.substring(0, i));
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ lista

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            Holder h;
            if (v == null) {
                h = new Holder();
                LinearLayout r = Ui.row(ArchiveActivity.this);
                r.setPadding(dp(12), dp(8), dp(12), dp(8));
                r.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                h.box = new FrameLayout(ArchiveActivity.this);
                h.icon = new ImageView(ArchiveActivity.this);
                h.box.addView(h.icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                h.check = new ImageView(ArchiveActivity.this);
                h.check.setImageDrawable(Ui.icon(ArchiveActivity.this, R.drawable.ic_check, 0xFF06101F));
                h.check.setBackground(Ui.round(0xFFFFC14D, dp(10)));
                h.check.setPadding(dp(3), dp(3), dp(3), dp(3));
                h.box.addView(h.check, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM | Gravity.END));
                r.addView(h.box, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout t = Ui.column(ArchiveActivity.this);
                t.setPadding(dp(12), 0, 0, 0);
                h.name = Ui.text(ArchiveActivity.this, "", 15, Ui.TEXT, false);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.info = Ui.text(ArchiveActivity.this, "", 12, Ui.SUB, false);
                t.addView(h.name);
                t.addView(h.info);
                r.addView(t, Ui.weight(1));
                v = r;
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Entry e = shown.get(pos);
            boolean sel = selected.contains(e.path);
            int color = FileTypes.color(e);
            h.icon.setImageDrawable(Ui.icon(ArchiveActivity.this, FileTypes.icon(e), color));
            h.box.setBackground(Ui.round((color & 0x00FFFFFF) | 0x26000000, dp(14)));
            h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
            h.name.setText(e.name);
            if (e.dir) {
                List<Entry> kids = tree.get(e.path);
                int n = kids == null ? 0 : kids.size();
                h.info.setText(n + (n == 1 ? " elemento" : " elementos"));
            } else {
                h.info.setText(Ui.size(e.size) + (e.modified > 0 ? "  ·  " + Ui.date(e.modified) : ""));
            }
            v.setBackground(sel ? Ui.round(0xFF3A2E14, dp(14)) : Ui.rippleRound(0x00000000, dp(14)));
            return v;
        }
    }

    private static final class Holder {
        FrameLayout box;
        ImageView icon, check;
        TextView name, info;
    }
}
