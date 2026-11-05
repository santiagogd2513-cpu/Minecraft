package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** MediaFire dentro de Bóveda: iniciar sesión, ver carpetas, bajar, subir, mover, renombrar y borrar. */
public class CloudActivity extends Activity implements TransferEngine.Listener {
    private static final int REQ_PICK = 31;
    private static final int MF_COLOR = 0xFF1E88FF;

    /** Archivos locales esperando a que se elija dónde subirlos (desde el explorador). */
    static final List<String> pendingUpload = new ArrayList<>();
    /** Elementos de MediaFire esperando a que se elija a dónde moverlos. */
    private static final List<MediaFire.Item> moveClip = new ArrayList<>();

    private MediaFire mf;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String[]> stack = new ArrayList<>();   // {clave, nombre}
    private final List<MediaFire.Item> shown = new ArrayList<>();
    private final Map<String, MediaFire.Item> selected = new LinkedHashMap<>();
    private FrameLayout body;
    private ListView list;
    private BaseAdapter adapter;
    private TextView title, empty;
    private LinearLayout crumbs, bottom, accountBox;
    private HorizontalScrollView crumbScroll;
    private ProgressBar loading;
    private View fab;
    private int lastDone;
    static final String EXTRA_DIR = "dir";
    private final TextView[] tabViews = new TextView[Tabs.COUNT];
    private LinearLayout tabStrip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mf = MediaFire.get(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        if (!Tabs.isMediaFire(Tabs.current)) {
            // se abrió MediaFire desde otro lado: esta pestaña pasa a MediaFire
            Tabs.mfReturn[Tabs.current] = Tabs.path[Tabs.current];
            Tabs.path[Tabs.current] = Tabs.MEDIAFIRE;
        }
        enterTab(getIntent().getIntExtra(EXTRA_DIR, 0));
    }

    // ------------------------------------------------------------------ pestañas

    private void paintTabs() {
        String[] names = new String[Tabs.COUNT];
        for (int i = 0; i < Tabs.COUNT; i++) names[i] = Tabs.label(Tabs.path[i]);
        Tabs.paint(this, tabViews, names);
    }

    /** Muestra la carpeta de MediaFire que tenía la pestaña actual. */
    private void enterTab(int dir) {
        paintTabs();
        selected.clear();
        // lo copiado o cortado en el explorador queda listo para «Subir aquí»
        if (pendingUpload.isEmpty()) pendingUpload.addAll(MainActivity.takeClipForCloud());
        if (!mf.loggedIn()) {
            showLogin();
        } else {
            List<String[]> saved = Tabs.mfStack[Tabs.current];
            if (saved != null && !saved.isEmpty()) {
                stack.clear();
                stack.addAll(saved);
                load(true);
                loadAccount();
            } else {
                openRoot();
            }
        }
        if (dir != 0 && body != null) {
            body.animate().cancel();
            body.setTranslationX(dir * getResources().getDisplayMetrics().widthPixels * 0.35f);
            body.setAlpha(0.3f);
            body.animate().translationX(0).alpha(1).setDuration(180).start();
        }
    }

    private void switchTab(int t) {
        if (t < 0 || t >= Tabs.COUNT || t == Tabs.current) return;
        int dir = t > Tabs.current ? 1 : -1;
        Tabs.mfStack[Tabs.current] = new ArrayList<>(stack);
        Tabs.current = t;
        if (Tabs.isMediaFire(t)) {
            enterTab(dir);
        } else {
            // el explorador muestra la pestaña al volver
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TransferEngine.get(this).addListener(this);
        paintTabs();
        updateBottom();
    }

    @Override
    protected void onPause() {
        if (Tabs.isMediaFire(Tabs.current) && !stack.isEmpty()) Tabs.mfStack[Tabs.current] = new ArrayList<>(stack);
        TransferEngine.get(this).removeListener(this);
        super.onPause();
    }

    @Override
    public void onChanged() {
        int done = 0;
        for (TransferEngine.Job j : TransferEngine.get(this).jobs()) if (j.state >= 2) done++;
        if (done != lastDone) {
            lastDone = done;
            if (mf.loggedIn() && !stack.isEmpty()) load(false);
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ interfaz

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout col = Ui.column(this);
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(4), dp(8), dp(4), dp(2));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 46, v -> onBackPressed()));
        LinearLayout titles = Ui.column(this);
        title = Ui.text(this, "MediaFire", 20, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(title);
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.row(this);
        crumbScroll.addView(crumbs);
        titles.addView(crumbScroll);
        head.addView(titles, Ui.weight(1));
        head.addView(Ui.iconButton(this, R.drawable.ic_refresh, 44, v -> {
            if (mf.loggedIn() && !stack.isEmpty()) load(false);
        }));
        head.addView(Ui.iconButton(this, R.drawable.ic_menu, 44, v -> accountMenu()));
        col.addView(head);

        tabStrip = Tabs.strip(this, tabViews, this::switchTab);
        col.addView(tabStrip);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(MF_COLOR));
        loading.setVisibility(View.INVISIBLE);
        col.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        accountBox = Ui.column(this);
        col.addView(accountBox);

        body = new Tabs.SwipeFrame(this, d -> switchTab(Tabs.current + d));
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, dp(2), 0, dp(96));
        list.setFastScrollEnabled(true);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onTap(shown.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            toggle(shown.get(pos));
            return true;
        });
        body.addView(list);
        empty = Ui.text(this, "", 15, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), 0, dp(24), 0);
        empty.setVisibility(View.GONE);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = Ui.row(this);
        bottom.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottom.setBackground(Ui.roundStroke(Ui.SURFACE, 0, dp(1), Ui.STROKE));
        bottom.setVisibility(View.GONE);
        col.addView(bottom);
        root.addView(col);

        TextView plus = Ui.text(this, "+", 30, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(Ui.ripple(Ui.gradient(dp(28), 0xFF5AB2FF, MF_COLOR), Ui.round(Color.WHITE, dp(28))));
        plus.setElevation(dp(6));
        plus.setOnClickListener(v -> createMenu());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, dp(18), dp(22));
        fab = plus;
        fab.setVisibility(View.GONE);
        root.addView(plus, fl);
        setContentView(root);
    }

    private TextView bigButton(String text, View.OnClickListener l) {
        TextView b = Ui.text(this, text, 15.5f, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(Ui.ripple(Ui.gradient(dp(24), 0xFF5AB2FF, MF_COLOR), Ui.round(Color.WHITE, dp(24))));
        b.setOnClickListener(l);
        return b;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5D6B83);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        e.setPadding(dp(16), dp(13), dp(16), dp(13));
        return e;
    }

    private LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, top, 0, 0);
        return lp;
    }

    // ------------------------------------------------------------------ iniciar sesión

    private View loginView;

    private void showLogin() {
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        bottom.setVisibility(View.GONE);
        accountBox.removeAllViews();
        crumbs.removeAllViews();
        title.setText("MediaFire");
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(24), dp(30), dp(24), dp(30));
        TextView logo = Ui.text(this, "MediaFire", 30, MF_COLOR, true);
        logo.setGravity(Gravity.CENTER);
        box.addView(logo);
        TextView sub = Ui.text(this, "Inicia sesión para ver y manejar tus archivos de MediaFire desde Bóveda.", 14, Ui.SUB, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(10));
        box.addView(sub);
        EditText mail = field("Correo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = field("Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(mail, margins(dp(14)));
        box.addView(pass, margins(dp(10)));
        LinearLayout adv = Ui.column(this);
        adv.setVisibility(View.GONE);
        EditText app = field("ID de aplicación (opcional)", InputType.TYPE_CLASS_NUMBER);
        EditText key = field("Clave de API (opcional)", InputType.TYPE_CLASS_TEXT);
        TextView advInfo = Ui.text(this, "Solo si MediaFire pide tu propia aplicación: se crea gratis en mediafire.com/developers.", 12, Ui.SUB, false);
        advInfo.setPadding(dp(4), dp(8), dp(4), 0);
        adv.addView(app, margins(dp(10)));
        adv.addView(key, margins(dp(10)));
        adv.addView(advInfo);
        box.addView(adv);
        TextView toggle = Ui.text(this, "Opciones avanzadas", 13, Ui.SUB, true);
        toggle.setPadding(dp(4), dp(12), dp(4), dp(4));
        toggle.setOnClickListener(v -> adv.setVisibility(adv.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        box.addView(toggle);
        TextView go = bigButton("Iniciar sesión", null);
        go.setOnClickListener(v -> {
            String m = mail.getText().toString().trim(), p = pass.getText().toString();
            if (m.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "Escribe tu correo y tu contraseña", Toast.LENGTH_SHORT).show();
                return;
            }
            go.setEnabled(false);
            go.setText("Entrando…");
            loading.setVisibility(View.VISIBLE);
            io.execute(() -> {
                String err = null;
                try {
                    mf.login(m, p, app.getText().toString(), key.getText().toString());
                } catch (IOException e) {
                    err = e.getMessage();
                }
                String fe = err;
                main.post(() -> {
                    loading.setVisibility(View.INVISIBLE);
                    go.setEnabled(true);
                    go.setText("Iniciar sesión");
                    if (fe != null) {
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle("No se pudo entrar")
                                .setMessage(fe).setPositiveButton("Entendido", null).show();
                        return;
                    }
                    body.removeView(loginView);
                    loginView = null;
                    openRoot();
                });
            });
        });
        box.addView(go, margins(dp(18)));
        TextView safe = Ui.text(this, "🔒 La contraseña se guarda cifrada en este celular con el almacén de claves de Android.", 12, Ui.SUB, false);
        safe.setGravity(Gravity.CENTER);
        safe.setPadding(0, dp(16), 0, 0);
        box.addView(safe);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        if (loginView != null) body.removeView(loginView);
        loginView = sv;
        body.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private void accountMenu() {
        if (!mf.loggedIn()) return;
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(mf.email())
                .setItems(new String[]{"Ir al inicio de MediaFire", "Cerrar sesión"}, (d, w) -> {
                    if (w == 0) openRoot();
                    else {
                        mf.logout();
                        stack.clear();
                        shown.clear();
                        adapter.notifyDataSetChanged();
                        showLogin();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ carpetas

    private void openRoot() {
        stack.clear();
        stack.add(new String[]{MediaFire.ROOT, "Mis archivos"});
        load(true);
        loadAccount();
    }

    private void loadAccount() {
        io.execute(() -> {
            try {
                MediaFire.Account a = mf.account();
                main.post(() -> showAccount(a));
            } catch (IOException ignored) {
            }
        });
    }

    private void showAccount(MediaFire.Account a) {
        accountBox.removeAllViews();
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(Ui.gradient(dp(18), 0xFF0F2B57, 0xFF0F1A30));
        LinearLayout r = Ui.row(this);
        TextView n = Ui.text(this, a.name.isEmpty() ? a.email : a.name, 15, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(n, Ui.weight(1));
        r.addView(Ui.text(this, Ui.size(a.used) + " / " + Ui.size(a.limit), 12.5f, Ui.SUB, false));
        card.addView(r);
        View bar = new MainActivity.UsageBar(this, a.limit > 0 ? a.used / (float) a.limit : 0, MF_COLOR);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
        bl.setMargins(0, dp(10), 0, 0);
        card.addView(bar, bl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(dp(14), dp(4), dp(14), dp(6));
        accountBox.addView(card, cl);
    }

    private String currentKey() {
        return stack.get(stack.size() - 1)[0];
    }

    private void open(MediaFire.Item folder) {
        stack.add(new String[]{folder.key, folder.name});
        load(true);
    }

    private int gen;

    private void load(boolean top) {
        int g = ++gen;
        String key = currentKey();
        list.setVisibility(View.VISIBLE);
        fab.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        title.setText(stack.get(stack.size() - 1)[1]);
        accountBox.setVisibility(stack.size() == 1 ? View.VISIBLE : View.GONE);
        buildCrumbs();
        io.execute(() -> {
            List<MediaFire.Item> got = null;
            String err = null;
            try {
                got = mf.list(key);
            } catch (IOException e) {
                err = e.getMessage();
            }
            List<MediaFire.Item> res = got;
            String fe = err;
            main.post(() -> {
                if (g != gen) return;
                loading.setVisibility(View.INVISIBLE);
                shown.clear();
                if (res != null) {
                    shown.addAll(res);
                    Collections.sort(shown, (a, b) -> a.folder != b.folder ? (a.folder ? -1 : 1) : MainActivity.naturalCompare(a.name, b.name));
                }
                selected.keySet().retainAll(keys(shown));
                adapter.notifyDataSetChanged();
                if (top) list.setSelection(0);
                empty.setText(fe != null ? "No se pudo cargar: " + fe : "Esta carpeta está vacía");
                empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
                updateBottom();
            });
        });
    }

    private static java.util.Set<String> keys(List<MediaFire.Item> l) {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (MediaFire.Item i : l) s.add(i.key);
        return s;
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        for (int i = 0; i < stack.size(); i++) {
            final int idx = i;
            boolean last = i == stack.size() - 1;
            TextView t = Ui.text(this, i == 0 ? "MediaFire" : stack.get(i)[1], 12.5f, last ? MF_COLOR : Ui.SUB, last);
            t.setPadding(dp(2), dp(2), dp(2), dp(2));
            t.setOnClickListener(v -> {
                while (stack.size() > idx + 1) stack.remove(stack.size() - 1);
                load(true);
            });
            crumbs.addView(t);
            if (!last) crumbs.addView(Ui.text(this, "  ›  ", 12.5f, 0xFF4A5670, false));
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ------------------------------------------------------------------ tocar y seleccionar

    private void onTap(MediaFire.Item it) {
        if (!selected.isEmpty()) {
            toggle(it);
            return;
        }
        if (it.folder) open(it);
        else fileMenu(it);
    }

    private void toggle(MediaFire.Item it) {
        if (selected.remove(it.key) == null) selected.put(it.key, it);
        adapter.notifyDataSetChanged();
        updateBottom();
    }

    private void clearSelection() {
        selected.clear();
        adapter.notifyDataSetChanged();
        updateBottom();
    }

    private void updateBottom() {
        if (bottom == null) return;
        bottom.removeAllViews();
        if (!mf.loggedIn() || stack.isEmpty()) {
            bottom.setVisibility(View.GONE);
            return;
        }
        if (!selected.isEmpty()) {
            List<MediaFire.Item> sel = new ArrayList<>(selected.values());
            bottom.addView(action(R.drawable.ic_download, "Descargar", () -> download(sel, Paths.ROOT + "/Download")));
            bottom.addView(action(R.drawable.ic_open, "Bajar a…", () -> downloadTo(sel)));
            bottom.addView(action(R.drawable.ic_cut, "Mover", () -> {
                moveClip.clear();
                moveClip.addAll(sel);
                clearSelection();
                Toast.makeText(this, "Ve a la carpeta de MediaFire destino y toca «Mover aquí»", Toast.LENGTH_LONG).show();
            }));
            bottom.addView(action(R.drawable.ic_delete, "Borrar", () -> askDelete(sel)));
            bottom.addView(action(R.drawable.ic_select_all, "Todo", () -> {
                if (selected.size() == shown.size()) selected.clear();
                else for (MediaFire.Item i : shown) selected.put(i.key, i);
                adapter.notifyDataSetChanged();
                updateBottom();
            }));
            title.setText(selected.size() + (selected.size() == 1 ? " seleccionado" : " seleccionados"));
            bottom.setVisibility(View.VISIBLE);
            fab.setVisibility(View.GONE);
            return;
        }
        title.setText(stack.get(stack.size() - 1)[1]);
        if (!pendingUpload.isEmpty()) {
            bar("Subir " + pendingUpload.size() + (pendingUpload.size() == 1 ? " elemento aquí" : " elementos aquí"), "Subir aquí", () -> {
                TransferEngine.get(this).submitCloudUpload(new ArrayList<>(pendingUpload), currentKey(), stack.get(stack.size() - 1)[1]);
                pendingUpload.clear();
                Toast.makeText(this, "Subiendo a MediaFire…", Toast.LENGTH_SHORT).show();
                updateBottom();
            }, () -> {
                pendingUpload.clear();
                updateBottom();
            });
            return;
        }
        if (!moveClip.isEmpty()) {
            bar("Mover " + moveClip.size() + (moveClip.size() == 1 ? " elemento" : " elementos"), "Mover aquí", this::moveHere, () -> {
                moveClip.clear();
                updateBottom();
            });
            return;
        }
        bottom.setVisibility(View.GONE);
        fab.setVisibility(View.VISIBLE);
    }

    private void bar(String info, String button, Runnable go, Runnable cancel) {
        TextView t = Ui.text(this, info, 14, Ui.TEXT, true);
        t.setPadding(dp(10), 0, 0, 0);
        bottom.addView(t, Ui.weight(1));
        TextView c = Ui.text(this, "Cancelar", 14, Ui.SUB, true);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        c.setOnClickListener(v -> cancel.run());
        bottom.addView(c);
        TextView b = bigButton(button, v -> go.run());
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        bottom.addView(b);
        bottom.setVisibility(View.VISIBLE);
    }

    private View action(int icon, String label, Runnable r) {
        LinearLayout b = Ui.column(this);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(6), 0, dp(4));
        b.setBackground(Ui.rippleRound(0x00000000, dp(14)));
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, icon, Ui.TEXT));
        b.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = Ui.text(this, label, 11, 0xFFC4CCDA, false);
        t.setPadding(0, dp(4), 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> r.run());
        b.setLayoutParams(Ui.weight(1));
        return b;
    }

    // ------------------------------------------------------------------ acciones

    private void fileMenu(MediaFire.Item it) {
        String[] opts = {"Abrir", "Descargar en «Download»", "Descargar a…", "Compartir enlace", "Copiar enlace", "Renombrar", "Mover…", "Borrar"};
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(it.name)
                .setItems(opts, (d, w) -> {
                    switch (w) {
                        case 0 -> openFile(it);
                        case 1 -> download(List.of(it), Paths.ROOT + "/Download");
                        case 2 -> downloadTo(List.of(it));
                        case 3 -> link(it, true);
                        case 4 -> link(it, false);
                        case 5 -> rename(it);
                        case 6 -> {
                            moveClip.clear();
                            moveClip.add(it);
                            updateBottom();
                            Toast.makeText(this, "Ve a la carpeta destino y toca «Mover aquí»", Toast.LENGTH_LONG).show();
                        }
                        default -> askDelete(List.of(it));
                    }
                })
                .show();
    }

    private void download(List<MediaFire.Item> items, String dest) {
        TransferEngine.get(this).submitCloudDownload(items, dest);
        clearSelection();
        Toast.makeText(this, "Descargando en " + Paths.name(dest) + "… (se verifica al terminar)", Toast.LENGTH_SHORT).show();
    }

    private void downloadTo(List<MediaFire.Item> items) {
        MainActivity.setCloudDownloadClip(items);
        clearSelection();
        Toast.makeText(this, "Elige la carpeta y toca «Descargar aquí»", Toast.LENGTH_LONG).show();
        for (int i = 1; i < Tabs.COUNT; i++) {
            int t = (Tabs.current + i) % Tabs.COUNT;
            if (!Tabs.isMediaFire(t)) {
                switchTab(t);
                return;
            }
        }
        startActivity(new Intent(this, MainActivity.class).setAction("elegir_destino"));
    }

    private void moveHere() {
        List<MediaFire.Item> items = new ArrayList<>(moveClip);
        moveClip.clear();
        String dest = currentKey();
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            String err = null;
            for (MediaFire.Item it : items) {
                try {
                    if (it.key.equals(dest)) continue;
                    mf.move(it, dest);
                } catch (IOException e) {
                    err = e.getMessage();
                }
            }
            String fe = err;
            main.post(() -> {
                if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                load(false);
            });
        });
    }

    private void askDelete(List<MediaFire.Item> items) {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("¿Borrar " + (items.size() == 1 ? "«" + items.get(0).name + "»" : items.size() + " elementos") + "?")
                .setMessage("Van a la papelera de MediaFire (se pueden recuperar desde la web).")
                .setPositiveButton("Borrar", (d, w) -> {
                    clearSelection();
                    loading.setVisibility(View.VISIBLE);
                    io.execute(() -> {
                        String err = null;
                        for (MediaFire.Item it : items) {
                            try {
                                mf.delete(it);
                            } catch (IOException e) {
                                err = e.getMessage();
                            }
                        }
                        String fe = err;
                        main.post(() -> {
                            if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                            load(false);
                        });
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void rename(MediaFire.Item it) {
        askName("Renombrar", it.name, n -> run(() -> mf.rename(it, n)));
    }

    private interface Op {
        void run() throws IOException;
    }

    private void run(Op op) {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            String err = null;
            try {
                op.run();
            } catch (IOException e) {
                err = e.getMessage();
            }
            String fe = err;
            main.post(() -> {
                if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                load(false);
            });
        });
    }

    private interface NameCb {
        void on(String n);
    }

    private void askName(String t, String initial, NameCb cb) {
        EditText in = field("Nombre", InputType.TYPE_CLASS_TEXT);
        in.setText(initial);
        int dot = initial.lastIndexOf('.');
        in.setSelection(0, dot > 0 ? dot : initial.length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in);
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(t)
                .setView(box)
                .setPositiveButton("Aceptar", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (Paths.validName(n)) cb.on(n);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void link(MediaFire.Item it, boolean share) {
        io.execute(() -> {
            try {
                String l = mf.shareLink(it);
                main.post(() -> {
                    if (share) {
                        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, l);
                        startActivity(Intent.createChooser(i, "Compartir enlace"));
                    } else {
                        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("MediaFire", l));
                        Toast.makeText(this, "Enlace copiado", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                main.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Lo baja a una carpeta temporal y lo abre con la app que corresponda. */
    private void openFile(MediaFire.Item it) {
        loading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Abriendo " + it.name + "…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            File dir = new File(getCacheDir(), "mediafire");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, it.name.replace('/', '_'));
            String err = null;
            try {
                if (!(out.exists() && out.length() == it.size)) {
                    HttpURLConnection c = (HttpURLConnection) new URL(mf.downloadLink(it)).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(out)) {
                        byte[] buf = new byte[1 << 20];
                        int r;
                        while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
                    } finally {
                        c.disconnect();
                    }
                }
            } catch (IOException e) {
                err = e.getMessage();
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
            String fe = err;
            main.post(() -> {
                loading.setVisibility(View.INVISIBLE);
                if (fe != null) {
                    Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                    return;
                }
                if (Archive.opensOnTap(it.name)) {
                    startActivity(new Intent(this, ArchiveActivity.class).putExtra(ArchiveActivity.EXTRA, out.getAbsolutePath()));
                    return;
                }
                Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", out);
                Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, FileTypes.mime(it.name))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(i);
                } catch (Exception ex) {
                    Toast.makeText(this, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void createMenu() {
        String[] opts = {"Nueva carpeta", "Subir archivos de este celular"};
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        String parent = currentKey();
                        askName("Nueva carpeta", "Nueva carpeta", n -> run(() -> mf.createFolder(parent, n)));
                    } else {
                        Intent i = new Intent(this, PickerActivity.class).setAction(Intent.ACTION_GET_CONTENT)
                                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        Toast.makeText(this, "Toca un archivo, o mantén presionado para elegir varios", Toast.LENGTH_LONG).show();
                        startActivityForResult(i, REQ_PICK);
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
        List<String> paths = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) addPath(paths, data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            addPath(paths, data.getData());
        }
        if (paths.isEmpty()) return;
        TransferEngine.get(this).submitCloudUpload(paths, currentKey(), stack.get(stack.size() - 1)[1]);
        Toast.makeText(this, "Subiendo a MediaFire…", Toast.LENGTH_SHORT).show();
    }

    private void addPath(List<String> out, Uri u) {
        try {
            String id = DocumentsContract.getDocumentId(u);
            if (id != null && id.startsWith("/") && !out.contains(id)) out.add(id);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            clearSelection();
        } else if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            load(true);
        } else {
            // sale de MediaFire: la pestaña vuelve a donde estaba
            Tabs.path[Tabs.current] = Tabs.mfReturn[Tabs.current];
            Tabs.mfStack[Tabs.current] = null;
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
                LinearLayout r = Ui.row(CloudActivity.this);
                r.setPadding(dp(12), dp(8), dp(12), dp(8));
                r.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                h.box = new FrameLayout(CloudActivity.this);
                h.icon = new ImageView(CloudActivity.this);
                h.box.addView(h.icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                h.check = new ImageView(CloudActivity.this);
                h.check.setImageDrawable(Ui.icon(CloudActivity.this, R.drawable.ic_check, Color.WHITE));
                h.check.setBackground(Ui.round(MF_COLOR, dp(10)));
                h.check.setPadding(dp(3), dp(3), dp(3), dp(3));
                h.box.addView(h.check, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM | Gravity.END));
                r.addView(h.box, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout t = Ui.column(CloudActivity.this);
                t.setPadding(dp(12), 0, 0, 0);
                h.name = Ui.text(CloudActivity.this, "", 15, Ui.TEXT, false);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.info = Ui.text(CloudActivity.this, "", 12, Ui.SUB, false);
                t.addView(h.name);
                t.addView(h.info);
                r.addView(t, Ui.weight(1));
                v = r;
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            MediaFire.Item it = shown.get(pos);
            Entry e = it.asEntry();
            boolean sel = selected.containsKey(it.key);
            int color = it.folder ? MF_COLOR : FileTypes.color(e);
            h.icon.setImageDrawable(Ui.icon(CloudActivity.this, FileTypes.icon(e), color));
            h.box.setBackground(Ui.round((color & 0x00FFFFFF) | 0x26000000, dp(14)));
            h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
            h.name.setText(it.name);
            h.info.setText(it.folder ? it.count + (it.count == 1 ? " elemento" : " elementos")
                    : Ui.size(it.size) + (it.created > 0 ? "  ·  " + Ui.date(it.created) : ""));
            v.setBackground(sel ? Ui.round(0xFF102A52, dp(14)) : Ui.rippleRound(0x00000000, dp(14)));
            return v;
        }
    }

    private static final class Holder {
        FrameLayout box;
        ImageView icon, check;
        TextView name, info;
    }
}
