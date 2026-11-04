package com.boveda.archivos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explorador de archivos: inicio, carpetas, selección, copiar/mover/pegar, comprimir y acceso a Android/data. */
public class MainActivity extends Activity implements TransferEngine.Listener {
    private static final int REQ_TREE = 21;

    protected Prefs prefs;
    private TransferEngine engine;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String path;                    // null = inicio
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Integer> scroll = new HashMap<>();
    private static final List<String> clip = new ArrayList<>();
    private static boolean clipMove;
    // «Extraer a…»: qué se va a sacar de qué archivo comprimido
    private static String extractArchive;
    private static final List<String> extractPicks = new ArrayList<>();
    private static String extractBase = "";

    // «Descargar a…» desde MediaFire
    private static final List<MediaFire.Item> cloudDownload = new ArrayList<>();

    static void setCloudDownloadClip(List<MediaFire.Item> items) {
        clip.clear();
        extractArchive = null;
        cloudDownload.clear();
        cloudDownload.addAll(items);
    }

    static void setExtractClip(String archive, List<String> picks, String base) {
        clip.clear();
        extractArchive = archive;
        extractPicks.clear();
        extractPicks.addAll(picks);
        extractBase = base == null ? "" : base;
    }
    private String filter = "";
    private int loadGen;

    private FrameLayout content;
    private ListView list;
    private Adapter adapter;
    private TextView title, empty, transfersBadge;
    private LinearLayout crumbs, bottom, chips;
    private HorizontalScrollView crumbScroll, chipScroll;
    private EditText search;
    private ProgressBar loading;
    private View fab;
    private ImageView backBtn;

    /** PickerActivity lo cambia: elegir archivos para otra app. */
    protected boolean picker() {
        return false;
    }

    // ------------------------------------------------------------------ inicio

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        engine = TransferEngine.get(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && !picker()) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent in) {
        if (in != null && "transferencias".equals(in.getAction())) {
            showTransfers();
        }
        String target = null;
        if (in != null && Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) target = pathFromUri(in.getData());
        if (target != null) open(target);
        else if (path == null) showHome();
    }

    /** Convierte la dirección de una carpeta que manda otra app en una ruta. */
    private String pathFromUri(Uri u) {
        try {
            if ("file".equals(u.getScheme())) return u.getPath();
            if (StorageProvider.authority(this).equals(u.getAuthority())) return DocumentsContract.getDocumentId(u);
            if (SafFs.AUTHORITY.equals(u.getAuthority())) {
                String id = DocumentsContract.isTreeUri(u) ? DocumentsContract.getTreeDocumentId(u) : DocumentsContract.getDocumentId(u);
                return FsRouter.basePath(id);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        engine.addListener(this);
        ShizukuHelper.addListener(shizukuListener);
        updateTransfersBadge();
        if (!hasStorage()) showPermissionGate();
        else if (path == null) showHome();
        else load(path, false);
    }

    @Override
    protected void onPause() {
        engine.removeListener(this);
        ShizukuHelper.removeListener(shizukuListener);
        super.onPause();
    }

    private final Runnable shizukuListener = () -> main.post(() -> {
        if (path == null) showHome();
        else if (Paths.restricted(path)) load(path, false);
    });

    @Override
    public void onChanged() {
        updateTransfersBadge();
        if (transfersSheet != null && transfersSheet.isShowing()) refreshTransfers();
        // cuando termina una tarea, se recarga la carpeta
        int done = 0;
        for (TransferEngine.Job j : engine.jobs()) if (j.state >= 2) done++;
        if (done != lastDone) {
            lastDone = done;
            if (path != null) load(path, true);
        }
    }

    private int lastDone;

    private boolean hasStorage() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
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

        // encabezado
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(4), dp(8), dp(4), dp(2));
        backBtn = Ui.iconButton(this, R.drawable.ic_back, 46, v -> onBackPressed());
        head.addView(backBtn);
        LinearLayout titles = Ui.column(this);
        title = Ui.text(this, "Bóveda", 21, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(title);
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.row(this);
        crumbScroll.addView(crumbs);
        titles.addView(crumbScroll);
        head.addView(titles, Ui.weight(1));
        head.addView(Ui.iconButton(this, R.drawable.ic_search, 44, v -> toggleSearch()));
        FrameLayout tbox = new FrameLayout(this);
        tbox.addView(Ui.iconButton(this, R.drawable.ic_swap, 44, v -> showTransfers()));
        transfersBadge = Ui.text(this, "", 10, 0xFF06101F, true);
        transfersBadge.setGravity(Gravity.CENTER);
        transfersBadge.setBackground(Ui.round(Ui.ACCENT2, dp(8)));
        transfersBadge.setVisibility(View.GONE);
        tbox.addView(transfersBadge, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END));
        head.addView(tbox, new LinearLayout.LayoutParams(dp(44), dp(44)));
        head.addView(Ui.iconButton(this, R.drawable.ic_menu, 44, v -> showMainMenu()));
        col.addView(head);

        search = new EditText(this);
        search.setHint("Buscar en esta carpeta");
        search.setHintTextColor(0xFF5D6B83);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setBackground(Ui.roundStroke(Ui.SURFACE, dp(20), dp(1), Ui.STROKE));
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase();
                applyFilter();
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(dp(12), dp(4), dp(12), dp(6));
        col.addView(search, sl);

        // carpetas ancladas (siempre a mano)
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(this);
        chips.setPadding(dp(10), dp(2), dp(10), dp(6));
        chipScroll.addView(chips);
        col.addView(chipScroll);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        loading.setVisibility(View.INVISIBLE);
        col.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        content = new FrameLayout(this);
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
            toggleSelect(shown.get(pos));
            return true;
        });
        content.addView(list);
        empty = Ui.text(this, "", 15, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = Ui.row(this);
        bottom.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottom.setBackground(Ui.roundStroke(Ui.SURFACE, 0, dp(1), Ui.STROKE));
        bottom.setVisibility(View.GONE);
        col.addView(bottom);
        root.addView(col);

        // botón flotante (+)
        TextView plus = Ui.text(this, "+", 30, 0xFF06101F, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(Ui.ripple(Ui.gradient(dp(28), Ui.ACCENT2, Ui.ACCENT), Ui.round(Color.WHITE, dp(28))));
        plus.setElevation(dp(6));
        plus.setOnClickListener(v -> showCreate());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, dp(18), dp(22));
        fab = plus;
        root.addView(plus, fl);
        setContentView(root);
    }

    private void updateTransfersBadge() {
        int active = 0;
        for (TransferEngine.Job j : engine.jobs()) if (j.state <= 1) active++;
        transfersBadge.setText(String.valueOf(active));
        transfersBadge.setVisibility(active > 0 ? View.VISIBLE : View.GONE);
    }

    private void toggleSearch() {
        if (path == null) return;
        boolean show = search.getVisibility() != View.VISIBLE;
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) search.setText("");
        else search.requestFocus();
    }

    private void buildChips() {
        chips.removeAllViews();
        List<String> pins = prefs.pinned();
        chipScroll.setVisibility(pins.isEmpty() ? View.GONE : View.VISIBLE);
        for (String p : pins) {
            LinearLayout c = Ui.row(this);
            c.setPadding(dp(10), dp(6), dp(12), dp(6));
            boolean here = p.equals(path);
            c.setBackground(Ui.rippleRound(here ? 0xFF1B2F55 : Ui.SURFACE2, dp(16)));
            ImageView i = new ImageView(this);
            i.setImageDrawable(Ui.icon(this, R.drawable.ic_pin, here ? Ui.ACCENT2 : Ui.SUB));
            c.addView(i, new LinearLayout.LayoutParams(dp(14), dp(14)));
            TextView t = Ui.text(this, label(p), 13, here ? Ui.TEXT : 0xFFC4CCDA, true);
            t.setPadding(dp(6), 0, 0, 0);
            c.addView(t);
            c.setOnClickListener(v -> open(p));
            c.setOnLongClickListener(v -> {
                prefs.togglePinned(p);
                buildChips();
                Toast.makeText(this, "Desanclada", Toast.LENGTH_SHORT).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(8), 0);
            chips.addView(c, lp);
        }
    }

    // ------------------------------------------------------------------ volúmenes

    static final class Volume {
        final String path, name, shortName;
        final boolean removable;

        Volume(String path, String name, boolean removable) {
            this(path, name, removable ? "SD" : "Interno", removable);
        }

        Volume(String path, String name, String shortName, boolean removable) {
            this.path = path;
            this.name = name;
            this.shortName = shortName;
            this.removable = removable;
        }
    }

    private List<Volume> volumes() {
        List<Volume> out = new ArrayList<>();
        StorageManager sm = getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            File dir = Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : (v.isPrimary() ? Environment.getExternalStorageDirectory() : null);
            if (dir == null) continue;
            out.add(new Volume(dir.getAbsolutePath(), v.isPrimary() ? "Almacenamiento interno" : v.getDescription(this), v.isRemovable()));
        }
        if (out.isEmpty()) out.add(new Volume(Paths.ROOT, "Almacenamiento interno", false));
        return out;
    }

    private Volume volumeOf(String p) {
        // servidores FTP y Google Drive también son "almacenamientos"
        if (FtpServers.isFtp(p)) {
            for (FtpServers.Server sv : FtpServers.get(this).all()) {
                if (Paths.under(p, sv.root())) return new Volume(sv.root(), sv.name, sv.name, false);
            }
            return null;
        }
        if (DriveFs.isDrive(p)) return new Volume(DriveFs.ROOT, "Google Drive", "Drive", false);
        Volume best = null;
        for (Volume v : volumes()) if (Paths.under(p, v.path) && (best == null || v.path.length() > best.path.length())) best = v;
        return best;
    }

    private String label(String p) {
        Volume v = volumeOf(p);
        if (v != null && v.path.equals(p)) return v.removable ? "Tarjeta SD" : v.shortName;
        return Paths.name(p);
    }

    // ------------------------------------------------------------------ permiso de almacenamiento

    private void showPermissionGate() {
        path = null;
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        title.setText("Bóveda");
        crumbs.removeAllViews();
        clearExtraViews();
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(28), dp(60), dp(28), dp(28));
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, R.drawable.ic_key, Ui.ACCENT));
        box.addView(i, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView t = Ui.text(this, "Bóveda necesita permiso", 21, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, dp(8));
        box.addView(t);
        TextView d = Ui.text(this, "Para ver y manejar todos tus archivos, Android pide el permiso «Acceso a todos los archivos». "
                + "En la pantalla que se abre, activa el interruptor de Bóveda y vuelve.", 14.5f, Ui.SUB, false);
        d.setGravity(Gravity.CENTER);
        box.addView(d);
        box.addView(bigButton("Dar permiso", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }), topMargin(dp(24)));
        addExtraView(box);
    }

    private View extraView;

    private void addExtraView(View v) {
        clearExtraViews();
        extraView = v;
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        extraView = sv;
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private void clearExtraViews() {
        if (extraView != null) content.removeView(extraView);
        extraView = null;
    }

    private LinearLayout.LayoutParams topMargin(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px, 0, 0);
        return lp;
    }

    private TextView bigButton(String text, View.OnClickListener l) {
        TextView b = Ui.text(this, text, 15.5f, 0xFF06101F, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(Ui.ripple(Ui.gradient(dp(24), Ui.ACCENT2, Ui.ACCENT), Ui.round(Color.WHITE, dp(24))));
        b.setOnClickListener(l);
        return b;
    }

    private TextView ghostButton(String text, View.OnClickListener l) {
        TextView b = Ui.text(this, text, 15, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE2, dp(24), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(24))));
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------------ pantalla de inicio

    private void showHome() {
        path = null;
        selected.clear();
        updateBars();
        if (!hasStorage()) {
            showPermissionGate();
            return;
        }
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        search.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        loading.setVisibility(View.INVISIBLE);
        backBtn.setVisibility(View.GONE);
        title.setText(picker() ? "Elige un archivo" : "Bóveda");
        crumbs.removeAllViews();
        buildChips();

        LinearLayout box = Ui.column(this);
        box.setPadding(dp(14), dp(6), dp(14), dp(40));

        for (Volume v : volumes()) {
            File f = new File(v.path);
            long total = f.getTotalSpace(), free = f.getUsableSpace();
            LinearLayout card = Ui.column(this);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(Ui.ripple(Ui.gradient(dp(20), 0xFF14284F, 0xFF101A30), Ui.round(Color.WHITE, dp(20))));
            LinearLayout r = Ui.row(this);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(Ui.icon(this, v.removable ? R.drawable.ic_sd : R.drawable.ic_phone, Ui.ACCENT2));
            r.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView n = Ui.text(this, v.name, 16.5f, Ui.TEXT, true);
            n.setPadding(dp(10), 0, 0, 0);
            r.addView(n, Ui.weight(1));
            r.addView(Ui.text(this, Ui.size(total - free) + " / " + Ui.size(total), 13, Ui.SUB, false));
            card.addView(r);
            View bar = new UsageBar(this, total > 0 ? (total - free) / (float) total : 0);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
            bl.setMargins(0, dp(12), 0, 0);
            card.addView(bar, bl);
            card.setOnClickListener(x -> open(v.path));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.setMargins(0, 0, 0, dp(10));
            box.addView(card, cl);
        }

        box.addView(section("Accesos rápidos"));
        String r0 = Paths.ROOT;
        box.addView(shortcut(R.drawable.ic_download, 0xFF6FD6FF, "Descargas", r0 + "/Download", null));
        box.addView(shortcut(R.drawable.ic_camera, 0xFFFF8A5C, "Cámara (DCIM)", r0 + "/DCIM", null));
        box.addView(shortcut(R.drawable.ic_doc, 0xFFFFC14D, "Documentos", r0 + "/Documents", null));
        box.addView(shortcut(R.drawable.ic_music, 0xFFB58CFF, "Música", r0 + "/Music", null));
        boolean dataOk = FsRouter.hasAccess(this, Paths.DATA), obbOk = FsRouter.hasAccess(this, Paths.OBB);
        box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/data", Paths.DATA,
                dataOk ? "Con acceso" : "Toca para dar acceso"));
        box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/obb", Paths.OBB,
                obbOk ? "Con acceso" : "Toca para dar acceso"));

        List<String> pins = prefs.pinned();
        if (!pins.isEmpty()) {
            box.addView(section("Ancladas"));
            for (String p : pins) box.addView(shortcut(R.drawable.ic_pin, Ui.ACCENT2, label(p), p, p));
        }
        List<String> favs = prefs.favorites();
        box.addView(section("Favoritas"));
        if (favs.isEmpty()) {
            TextView t = Ui.text(this, "Mantén presionada una carpeta y elige «Favorita» para tenerla aquí.", 13, Ui.SUB, false);
            t.setPadding(dp(6), 0, dp(6), dp(6));
            box.addView(t);
        }
        for (String p : favs) box.addView(shortcut(R.drawable.ic_star, Ui.WARN, Paths.name(p), p, p));

        if (!picker()) {
            box.addView(section("Nube y red"));
            MediaFire mf = MediaFire.get(this);
            box.addView(cloudCard(mf.loggedIn() ? mf.email() : "Toca para iniciar sesión"));
            Drive gd = Drive.get(this);
            box.addView(netRow("GD", 0xFF34A853, 0xFF4285F4, "Google Drive",
                    gd.connected() ? (gd.email().isEmpty() ? "Conectado" : gd.email()) : "Toca para conectar",
                    v -> openDrive(), v -> {
                        driveMenu();
                        return true;
                    }));
            for (FtpServers.Server sv : FtpServers.get(this).all()) {
                box.addView(netRow("FTP", 0xFFFFB547, 0xFFF07C2C, sv.name, sv.user + "@" + sv.host + ":" + sv.port,
                        v -> open(sv.root()), v -> {
                            ftpMenu(sv);
                            return true;
                        }));
            }
            box.addView(netRow("+", 0xFF2A3346, 0xFF2A3346, "Añadir servidor FTP", "Xbox 360, PC, NAS, otro celular…",
                    v -> editFtp(null), null));
            box.addView(section("Shizuku"));
            box.addView(shizukuCard());
        }
        addExtraView(box);
    }

    private View section(String s) {
        TextView t = Ui.text(this, s.toUpperCase(), 12, 0xFF6F86AE, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), dp(16), 0, dp(8));
        return t;
    }

    private View shortcut(int icon, int color, String name, String p, String sub) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(12), dp(11), dp(12), dp(11));
        r.setBackground(Ui.rippleRound(Ui.SURFACE, dp(16)));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Ui.round((color & 0x00FFFFFF) | 0x26000000, dp(12)));
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, icon, color));
        ib.addView(i, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        r.addView(ib, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView n = Ui.text(this, name, 15, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(n);
        if (sub != null) {
            TextView s = Ui.text(this, sub.startsWith("/") ? sub.replace(Paths.ROOT, "Interno") : sub, 12, Ui.SUB, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(s);
        }
        r.addView(texts, Ui.weight(1));
        ImageView ch = new ImageView(this);
        ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, 0xFF4A5670));
        r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
        r.setOnClickListener(v -> open(p));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lp);
        return r;
    }

    private View netRow(String badge, int c1, int c2, String name, String sub, View.OnClickListener tap, View.OnLongClickListener hold) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(Ui.rippleRound(Ui.SURFACE, dp(16)));
        TextView logo = Ui.text(this, badge, badge.length() > 2 ? 11 : 15, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.gradient(dp(12), c1, c2));
        r.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(Ui.text(this, name, 15, Ui.TEXT, true));
        TextView sv = Ui.text(this, sub, 12, Ui.SUB, false);
        sv.setSingleLine(true);
        sv.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(sv);
        r.addView(texts, Ui.weight(1));
        r.setOnClickListener(tap);
        if (hold != null) r.setOnLongClickListener(hold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, 0);
        r.setLayoutParams(lp);
        return r;
    }

    // ------------------------------------------------------------------ FTP

    private void ftpMenu(FtpServers.Server sv) {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(sv.name)
                .setItems(new String[]{"Editar", "Borrar este servidor"}, (d, w) -> {
                    if (w == 0) editFtp(sv);
                    else {
                        FtpServers.get(this).remove(sv.id);
                        prefs.favorites().stream().filter(p -> Paths.under(p, sv.root())).forEach(prefs::toggleFavorite);
                        prefs.pinned().stream().filter(p -> Paths.under(p, sv.root())).forEach(prefs::togglePinned);
                        showHome();
                    }
                })
                .show();
    }

    private EditText input(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setHintTextColor(0xFF5D6B83);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        return e;
    }

    private TextView chip(String text, boolean on, View.OnClickListener l) {
        TextView t = Ui.text(this, text, 13, on ? 0xFF06101F : Ui.TEXT, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(on ? Ui.round(Ui.ACCENT2, dp(16)) : Ui.roundStroke(Ui.SURFACE2, dp(16), dp(1), Ui.STROKE));
        t.setOnClickListener(l);
        return t;
    }

    /** Añadir o editar un servidor FTP (con atajo para la Xbox 360). */
    private void editFtp(FtpServers.Server existing) {
        FtpServers.Server sv = new FtpServers.Server();
        if (existing != null) {
            sv.id = existing.id; sv.name = existing.name; sv.host = existing.host; sv.port = existing.port; sv.user = existing.user;
            sv.pass = existing.pass; sv.startDir = existing.startDir; sv.maxConnections = existing.maxConnections;
            sv.passive = existing.passive; sv.latin1 = existing.latin1;
        }
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        LinearLayout presets = Ui.row(this);
        EditText name = input("Nombre (ej. Mi Xbox)", sv.name, android.text.InputType.TYPE_CLASS_TEXT);
        EditText host = input("Dirección IP (ej. 192.168.1.20)", sv.host, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText port = input("Puerto", String.valueOf(sv.port), android.text.InputType.TYPE_CLASS_NUMBER);
        EditText user = input("Usuario (vacío = anónimo)", sv.user, android.text.InputType.TYPE_CLASS_TEXT);
        EditText pass = input("Contraseña", sv.pass, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView opts = Ui.text(this, "", 12.5f, Ui.SUB, false);
        Runnable showOpts = () -> opts.setText((sv.passive ? "Modo pasivo" : "Modo activo") + "  ·  " + sv.maxConnections
                + (sv.maxConnections == 1 ? " conexión" : " conexiones") + "  ·  " + (sv.latin1 ? "Latin-1" : "UTF-8") + "   (toca para cambiar)");
        showOpts.run();
        opts.setPadding(dp(4), dp(10), dp(4), dp(4));
        opts.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(new String[]{"Modo: " + (sv.passive ? "pasivo → activo" : "activo → pasivo"),
                        "Conexiones a la vez: " + sv.maxConnections + " → " + (sv.maxConnections >= 4 ? 1 : sv.maxConnections * 2),
                        "Letras: " + (sv.latin1 ? "Latin-1 → UTF-8" : "UTF-8 → Latin-1 (servidores viejos)")}, (d, w) -> {
                    if (w == 0) sv.passive = !sv.passive;
                    else if (w == 1) sv.maxConnections = sv.maxConnections >= 4 ? 1 : sv.maxConnections * 2;
                    else sv.latin1 = !sv.latin1;
                    showOpts.run();
                }).show());
        presets.addView(chip("🎮 Xbox 360", false, v -> {
            if (name.getText().toString().trim().isEmpty()) name.setText("Xbox 360");
            port.setText("21");
            user.setText("xbox");
            pass.setText("xbox");
            sv.maxConnections = 1;
            sv.passive = true;
            sv.latin1 = true;
            showOpts.run();
            Toast.makeText(this, "Pon la IP que muestra Aurora/FSD en la pantalla de la Xbox", Toast.LENGTH_LONG).show();
        }));
        TextView sp = new TextView(this);
        sp.setWidth(dp(8));
        presets.addView(sp);
        presets.addView(chip("💻 PC / NAS", false, v -> {
            port.setText("21");
            sv.maxConnections = 2;
            sv.passive = true;
            sv.latin1 = false;
            showOpts.run();
        }));
        box.addView(presets);
        for (EditText e : new EditText[]{name, host, port, user, pass}) box.addView(e, topMargin(dp(8)));
        box.addView(opts);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(existing == null ? "Nuevo servidor FTP" : "Editar servidor")
                .setView(scroll)
                .setPositiveButton("Conectar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String h = host.getText().toString().trim().replaceFirst("^ftp://", "");
            if (h.isEmpty()) {
                host.setError("Falta la dirección");
                return;
            }
            sv.host = h;
            sv.name = name.getText().toString().trim().isEmpty() ? h : name.getText().toString().trim();
            try {
                sv.port = Integer.parseInt(port.getText().toString().trim());
            } catch (NumberFormatException e) {
                sv.port = 21;
            }
            sv.user = user.getText().toString().trim();
            sv.pass = pass.getText().toString();
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            d.getButton(AlertDialog.BUTTON_POSITIVE).setText("Probando…");
            io.execute(() -> {
                String err = null;
                FtpClient c = new FtpClient(sv.host, sv.port, sv.passive,
                        sv.latin1 ? java.nio.charset.StandardCharsets.ISO_8859_1 : java.nio.charset.StandardCharsets.UTF_8);
                try {
                    c.connect(sv.user, sv.pass);
                    c.list("/");
                } catch (IOException e) {
                    err = e.getMessage();
                } finally {
                    c.close();
                }
                String fe = err;
                main.post(() -> {
                    if (fe != null) {
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setText("Conectar");
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle("No se pudo conectar")
                                .setMessage(fe + "\n\nRevisa la IP, que el celular y el equipo estén en la misma red Wi-Fi y que el servidor FTP esté encendido. "
                                        + "Si sigue fallando, cambia el modo pasivo/activo.")
                                .setPositiveButton("Entendido", null).show();
                        return;
                    }
                    FtpServers.get(this).save(sv);
                    d.dismiss();
                    open(sv.root());
                });
            });
        }));
        d.show();
    }

    // ------------------------------------------------------------------ Google Drive

    private void openDrive() {
        Drive gd = Drive.get(this);
        if (gd.connected()) open(DriveFs.ROOT);
        else showDriveSetup();
    }

    private void driveMenu() {
        Drive gd = Drive.get(this);
        if (!gd.configured()) {
            showDriveSetup();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Google Drive")
                .setItems(new String[]{"Desconectar la cuenta", "Cambiar el ID de cliente"}, (d, w) -> {
                    if (w == 0) gd.disconnect();
                    else gd.forgetClient();
                    showHome();
                })
                .show();
    }

    private void showDriveSetup() {
        Drive gd = Drive.get(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText id = input("ID de cliente (termina en .apps.googleusercontent.com)", gd.clientId(), android.text.InputType.TYPE_CLASS_TEXT);
        EditText sec = input("Secreto del cliente", "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!gd.configured()) {
            TextView guide = Ui.text(this, "Google pide que cada app esté registrada. Se hace una sola vez, gratis (unos 5 minutos):\n\n"
                    + "1. Entra a console.cloud.google.com con tu cuenta y crea un proyecto (por ejemplo «Bóveda»).\n"
                    + "2. «APIs y servicios» → «Biblioteca»: busca «Google Drive API» y actívala.\n"
                    + "3. «Pantalla de consentimiento de OAuth»: tipo Externo, nombre Bóveda y tu correo. "
                    + "En «Usuarios de prueba» agrega tu correo. Luego toca «Publicar app» (así no te pide entrar cada semana).\n"
                    + "4. «Credenciales» → «Crear credenciales» → «ID de cliente de OAuth» → tipo «App de escritorio».\n"
                    + "5. Copia aquí el ID de cliente y el secreto.\n\n"
                    + "Al conectar, Google dirá «Google no verificó esta app»: es tu propia app, toca «Continuar».", 13, Ui.SUB, false);
            guide.setTextIsSelectable(true);
            box.addView(guide);
            box.addView(id, topMargin(dp(12)));
            box.addView(sec, topMargin(dp(8)));
        } else {
            box.addView(Ui.text(this, "Se abrirá el navegador para que elijas tu cuenta de Google y le des permiso a Bóveda.", 14, Ui.SUB, false));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Conectar Google Drive")
                .setView(scroll)
                .setPositiveButton("Conectar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!gd.configured()) {
                String i = id.getText().toString().trim(), s2 = sec.getText().toString().trim();
                if (!i.endsWith(".apps.googleusercontent.com")) {
                    id.setError("Copia el ID de cliente completo");
                    return;
                }
                if (s2.isEmpty()) {
                    sec.setError("Falta el secreto");
                    return;
                }
                gd.setClient(i, s2);
            }
            d.dismiss();
            Toast.makeText(this, "Elige tu cuenta en el navegador y vuelve aquí", Toast.LENGTH_LONG).show();
            gd.login(this, err -> {
                if (err != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle("No se conectó Google Drive")
                            .setMessage(err).setPositiveButton("Entendido", null).show();
                    return;
                }
                Toast.makeText(this, "Google Drive conectado", Toast.LENGTH_SHORT).show();
                open(DriveFs.ROOT);
            });
        }));
        d.show();
    }

    private View cloudCard(String sub) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(14), dp(14), dp(14), dp(14));
        r.setBackground(Ui.ripple(Ui.gradient(dp(18), 0xFF0F2B57, 0xFF0F1A30), Ui.round(Color.WHITE, dp(18))));
        TextView logo = Ui.text(this, "MF", 15, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.gradient(dp(12), 0xFF5AB2FF, 0xFF1E88FF));
        r.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(Ui.text(this, "MediaFire", 15.5f, Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 12, Ui.SUB, false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(s);
        r.addView(texts, Ui.weight(1));
        ImageView ch = new ImageView(this);
        ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, 0xFF4A5670));
        r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
        r.setOnClickListener(v -> startActivity(new Intent(this, CloudActivity.class)));
        return r;
    }

    private View shizukuCard() {
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        LinearLayout r = Ui.row(this);
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, R.drawable.ic_shield, ShizukuHelper.ready() ? Ui.GOOD : Ui.SUB));
        r.addView(i, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = Ui.text(this, ShizukuHelper.status(), 14, ShizukuHelper.ready() ? Ui.GOOD : Ui.TEXT, true);
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, Ui.weight(1));
        card.addView(r);
        if (!ShizukuHelper.ready()) {
            TextView d = Ui.text(this, "Con Shizuku, Bóveda entra a Android/data y Android/obb en cualquier versión de Android, sin root.",
                    12.5f, Ui.SUB, false);
            d.setPadding(0, dp(8), 0, 0);
            card.addView(d);
            card.addView(ghostButton(ShizukuHelper.running() ? "Conectar con Shizuku" : "¿Cómo activo Shizuku?", v -> useShizuku()),
                    topMargin(dp(12)));
        }
        return card;
    }

    private void useShizuku() {
        if (ShizukuHelper.running()) {
            ShizukuHelper.connect();
            Toast.makeText(this, "Acepta el permiso en la ventana de Shizuku", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Activar Shizuku")
                .setMessage("1. Instala la app Shizuku (Play Store o GitHub).\n"
                        + "2. Ábrela y enciéndela con «Depuración inalámbrica» (sigue sus pasos; no necesita computador ni root).\n"
                        + "3. Vuelve a Bóveda y toca «Conectar con Shizuku».\n\n"
                        + "Hay que volver a encender Shizuku cada vez que se reinicia el celular.")
                .setPositiveButton("Abrir Play Store", (d, w) -> openUrl("market://details?id=moe.shizuku.privileged.api",
                        "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void openUrl(String url, String fallback) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ carpetas

    protected void open(String p) {
        if (path != null) scroll.put(path, list.getFirstVisiblePosition());
        selected.clear();
        search.setText("");
        search.setVisibility(View.GONE);
        path = p;
        load(p, false);
    }

    private void load(String p, boolean keepScroll) {
        int gen = ++loadGen;
        clearExtraViews();
        list.setVisibility(View.VISIBLE);
        fab.setVisibility(picker() ? View.GONE : View.VISIBLE);
        backBtn.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        Volume v = volumeOf(p);
        title.setText(v != null && v.path.equals(p) ? v.name : Paths.name(p));
        buildCrumbs(p);
        buildChips();
        updateBars();
        final int first = keepScroll ? list.getFirstVisiblePosition() : scroll.getOrDefault(p, 0);
        io.execute(() -> {
            List<Entry> got;
            IOException err = null;
            try {
                got = FsRouter.get(this, p).list(p);
            } catch (IOException e) {
                got = null;
                err = e;
            }
            final List<Entry> res = got;
            final IOException error = err;
            main.post(() -> {
                if (gen != loadGen || !p.equals(path)) return;
                loading.setVisibility(View.INVISIBLE);
                if (error instanceof Fs.NeedAccess) {
                    if (DriveFs.isDrive(p)) {
                        showHome();
                        showDriveSetup();
                    } else if (FtpServers.isFtp(p)) {
                        showHome();
                        Toast.makeText(this, "Ese servidor FTP ya no está guardado", Toast.LENGTH_SHORT).show();
                    } else {
                        showNeedAccess(p);
                    }
                    return;
                }
                all.clear();
                if (res != null) {
                    boolean hidden = prefs.showHidden();
                    for (Entry e : res) {
                        if (e.name.endsWith(".boveda-parte")) continue;
                        if (!hidden && e.hidden()) continue;
                        all.add(e);
                    }
                    sort(all);
                }
                selected.retainAll(pathsOf(all));
                applyFilter();
                list.setSelection(Math.min(first, Math.max(0, shown.size() - 1)));
                if (error != null) {
                    empty.setText(error.getMessage());
                    empty.setVisibility(View.VISIBLE);
                }
                updateBars();
            });
        });
    }

    private static Set<String> pathsOf(List<Entry> l) {
        Set<String> s = new java.util.HashSet<>();
        for (Entry e : l) s.add(e.path);
        return s;
    }

    private void sort(List<Entry> l) {
        int mode = prefs.sort();
        boolean desc = prefs.sortDesc();
        Comparator<Entry> c = switch (mode) {
            case Prefs.SORT_DATE -> Comparator.comparingLong(e -> e.modified);
            case Prefs.SORT_SIZE -> Comparator.comparingLong(e -> e.size);
            case Prefs.SORT_TYPE -> Comparator.comparing(Entry::extension);
            default -> (a, b) -> naturalCompare(a.name, b.name);
        };
        if (mode != Prefs.SORT_NAME) c = c.thenComparing((a, b) -> naturalCompare(a.name, b.name));
        if (desc) c = c.reversed();
        Comparator<Entry> dirsFirst = (a, b) -> Boolean.compare(!a.dir, !b.dir);
        Collections.sort(l, dirsFirst.thenComparing(c));
    }

    /** "archivo2" va antes que "archivo10". */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0, na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < na && Character.isDigit(a.charAt(i))) i++;
                while (j < nb && Character.isDigit(b.charAt(j))) j++;
                String da = a.substring(si, i).replaceFirst("^0+(?=.)", ""), db = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (da.length() != db.length()) return da.length() - db.length();
                int c = da.compareTo(db);
                if (c != 0) return c;
            } else {
                int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return (na - i) - (nb - j);
    }

    private void applyFilter() {
        shown.clear();
        for (Entry e : all) if (filter.isEmpty() || e.name.toLowerCase().contains(filter)) shown.add(e);
        adapter.notifyDataSetChanged();
        if (path != null) {
            empty.setText(filter.isEmpty() ? "Carpeta vacía" : "Nada coincide con «" + filter + "»");
            empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void buildCrumbs(String p) {
        crumbs.removeAllViews();
        Volume v = volumeOf(p);
        String base = v != null ? v.path : "/";
        List<String> parts = new ArrayList<>();
        parts.add(base);
        if (!p.equals(base)) {
            String rel = p.substring(base.length() + (base.endsWith("/") ? 0 : 1));
            String acc = base;
            for (String s : rel.split("/")) {
                if (s.isEmpty()) continue;
                acc = Paths.join(acc, s);
                parts.add(acc);
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            boolean last = i == parts.size() - 1;
            TextView t = Ui.text(this, i == 0 ? (v != null ? v.shortName : "/") : Paths.name(part), 12.5f,
                    last ? Ui.ACCENT2 : Ui.SUB, last);
            t.setPadding(dp(2), dp(2), dp(2), dp(2));
            t.setOnClickListener(x -> open(part));
            crumbs.addView(t);
            if (!last) crumbs.addView(Ui.text(this, "  ›  ", 12.5f, 0xFF4A5670, false));
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ------------------------------------------------------------------ Android/data sin acceso

    private void showNeedAccess(String p) {
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        fab.setVisibility(View.GONE);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(22), dp(30), dp(22), dp(30));
        ImageView i = new ImageView(this);
        i.setImageDrawable(Ui.icon(this, R.drawable.ic_android, 0xFF35D08A));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(56), dp(56));
        il.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(i, il);
        TextView t = Ui.text(this, "Carpeta protegida por Android", 19, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(8));
        box.addView(t);
        TextView d = Ui.text(this, "Desde Android 11, Android/data y Android/obb están protegidas. Hay dos formas de entrar:", 14, Ui.SUB, false);
        d.setGravity(Gravity.CENTER);
        box.addView(d);

        String base = Paths.under(p, Paths.OBB) ? Paths.OBB : Paths.under(p, Paths.DATA) ? Paths.DATA : p;
        box.addView(section("1 · Con el selector del sistema"));
        TextView d1 = Ui.text(this, "Se abre el selector de Android dentro de la carpeta «Android»: entra a «" + Paths.name(base)
                + "», toca «Usar esta carpeta» y «Permitir». "
                + "Funciona en Android 11 y 12 (y en algunos 13). En Android 13–14 a veces sí deja con la carpeta de una app.", 13, Ui.SUB, false);
        box.addView(d1);
        box.addView(bigButton("Dar acceso a " + Paths.name(base), v -> requestTree(base)), topMargin(dp(12)));
        box.addView(ghostButton("Dar acceso a la carpeta de una app", v -> chooseAppFolder(base)), topMargin(dp(8)));

        box.addView(section("2 · Con Shizuku (cualquier Android)"));
        box.addView(shizukuCard());
        addExtraView(box);
    }

    private void requestTree(String folder) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // para Android/data y Android/obb el selector se abre dentro de la carpeta «Android»
        // (ahí ya se ven data y obb, y Android no deja abrir el selector directamente en ellas)
        String start = folder.equals(Paths.DATA) || folder.equals(Paths.OBB) ? Paths.ROOT + "/Android" : folder;
        String docId = FsRouter.docIdFor(start);
        if (docId != null) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(SafFs.AUTHORITY, docId));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            Toast.makeText(this, "Este celular no tiene el selector de carpetas", Toast.LENGTH_LONG).show();
        }
    }

    private void chooseAppFolder(String base) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String[]> rows = new ArrayList<>();
        for (ApplicationInfo a : apps) {
            if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (a.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
            rows.add(new String[]{String.valueOf(pm.getApplicationLabel(a)), a.packageName});
        }
        rows.sort((x, y) -> x[0].compareToIgnoreCase(y[0]));
        String[] labels = new String[rows.size()];
        for (int k = 0; k < rows.size(); k++) labels[k] = rows.get(k)[0] + "\n" + rows.get(k)[1];
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("¿La carpeta de qué app?")
                .setItems(labels, (d, w) -> requestTree(Paths.join(base, rows.get(w)[1])))
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_TREE && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                String base = FsRouter.basePath(DocumentsContract.getTreeDocumentId(u));
                Toast.makeText(this, "Acceso concedido", Toast.LENGTH_SHORT).show();
                if (base != null && Paths.restricted(base)) open(base);
                else if (path != null) load(path, false);
            } catch (Exception e) {
                Toast.makeText(this, "Android no dejó guardar el permiso de esa carpeta", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------------ tocar y seleccionar

    private void onTap(Entry e) {
        if (!selected.isEmpty()) {
            toggleSelect(e);
            return;
        }
        if (e.dir) open(e.path);
        else if (picker()) pick(List.of(e.path));
        else if (Archive.isRar(e.name)) Toast.makeText(this, "Los RAR no son bienvenidos en Bóveda 🦶", Toast.LENGTH_SHORT).show();
        else if (Archive.opensOnTap(e.name)) openArchive(e.path);
        else openFile(e);
    }

    private void openArchive(String p) {
        startActivity(new Intent(this, ArchiveActivity.class).putExtra(ArchiveActivity.EXTRA, p));
    }

    private void toggleSelect(Entry e) {
        if (!selected.remove(e.path)) selected.add(e.path);
        adapter.notifyDataSetChanged();
        updateBars();
    }

    private void selectAll() {
        if (selected.size() == shown.size()) selected.clear();
        else for (Entry e : shown) selected.add(e.path);
        adapter.notifyDataSetChanged();
        updateBars();
    }

    /** La barra de abajo: acciones de la selección, o "pegar aquí". */
    private void updateBars() {
        bottom.removeAllViews();
        if (path != null && !selected.isEmpty()) {
            title.setText(selected.size() + (selected.size() == 1 ? " seleccionado" : " seleccionados"));
            if (picker()) {
                bottom.addView(action(R.drawable.ic_check, "Elegir", () -> pick(new ArrayList<>(selected))));
                bottom.addView(action(R.drawable.ic_select_all, "Todo", this::selectAll));
                bottom.addView(action(R.drawable.ic_close, "Quitar", this::clearSelection));
            } else {
                bottom.addView(action(R.drawable.ic_copy, "Copiar", () -> toClip(false)));
                bottom.addView(action(R.drawable.ic_cut, "Mover", () -> toClip(true)));
                bottom.addView(action(R.drawable.ic_delete, "Borrar", this::askDelete));
                bottom.addView(action(R.drawable.ic_archive, "Comprimir", this::askZip));
                bottom.addView(action(R.drawable.ic_select_all, "Todo", this::selectAll));
                bottom.addView(action(R.drawable.ic_menu, "Más", this::showMore));
            }
            bottom.setVisibility(View.VISIBLE);
            fab.setVisibility(View.GONE);
            return;
        }
        if (path != null) {
            Volume v = volumeOf(path);
            title.setText(v != null && v.path.equals(path) ? v.name : Paths.name(path));
        }
        if (path != null && !cloudDownload.isEmpty() && !picker()) {
            TextView info = Ui.text(this, "Descargar " + (cloudDownload.size() == 1 ? "«" + cloudDownload.get(0).name + "»"
                    : cloudDownload.size() + " elementos") + " de MediaFire", 13.5f, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, "Cancelar", 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                cloudDownload.clear();
                updateBars();
            });
            bottom.addView(cancel);
            TextView go = bigButton("Descargar aquí", v -> {
                engine.submitCloudDownload(new ArrayList<>(cloudDownload), path);
                cloudDownload.clear();
                updateBars();
                Toast.makeText(this, "Descargando de MediaFire…", Toast.LENGTH_SHORT).show();
            });
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            bottom.addView(go);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && extractArchive != null && !picker()) {
            String what = extractPicks.isEmpty() ? "todo" : extractPicks.size() + (extractPicks.size() == 1 ? " elemento" : " elementos");
            TextView info = Ui.text(this, "Extraer " + what + " de «" + Paths.name(extractArchive) + "»", 13.5f, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, "Cancelar", 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                extractArchive = null;
                updateBars();
            });
            bottom.addView(cancel);
            TextView go = bigButton("Extraer aquí", v -> {
                engine.submitExtract(extractArchive, new ArrayList<>(extractPicks), extractBase, path);
                extractArchive = null;
                updateBars();
                Toast.makeText(this, "Extrayendo…", Toast.LENGTH_SHORT).show();
            });
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            bottom.addView(go);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && !clip.isEmpty() && !picker()) {
            TextView info = Ui.text(this, (clipMove ? "Mover " : "Copiar ") + clip.size() + (clip.size() == 1 ? " elemento" : " elementos"),
                    14, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, "Cancelar", 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                clip.clear();
                updateBars();
            });
            bottom.addView(cancel);
            TextView paste = bigButton("Pegar aquí", v -> paste());
            paste.setPadding(dp(20), dp(10), dp(20), dp(10));
            bottom.addView(paste);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        bottom.setVisibility(View.GONE);
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

    private void clearSelection() {
        selected.clear();
        adapter.notifyDataSetChanged();
        updateBars();
    }

    // ------------------------------------------------------------------ copiar, mover, pegar, borrar, comprimir

    private void toClip(boolean move) {
        extractArchive = null;
        cloudDownload.clear();
        clip.clear();
        clip.addAll(selected);
        clipMove = move;
        clearSelection();
        Toast.makeText(this, "Ve a la carpeta destino y toca «Pegar aquí»", Toast.LENGTH_SHORT).show();
    }

    private void paste() {
        if (path == null || clip.isEmpty()) return;
        String dest = path;
        List<String> src = new ArrayList<>(clip);
        boolean move = clipMove;
        for (String s : src) {
            if (Paths.under(dest, s)) {
                Toast.makeText(this, "No se puede pegar una carpeta dentro de sí misma", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (move) {
            boolean same = true;
            for (String s : src) if (!Paths.parent(s).equals(dest)) same = false;
            if (same) {
                Toast.makeText(this, "Ya están en esta carpeta", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // ¿hay nombres repetidos en el destino?
        Set<String> names = new java.util.HashSet<>();
        for (Entry e : all) names.add(e.name);
        int clashes = 0;
        for (String s : src) if (names.contains(Paths.name(s))) clashes++;
        if (clashes == 0) {
            submitTransfer(move, src, dest, TransferEngine.Conflict.KEEP_BOTH);
            return;
        }
        String[] opts = {"Reemplazar", "Mantener ambos (renombrar)", "Saltar los repetidos"};
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(clashes + (clashes == 1 ? " elemento ya existe aquí" : " elementos ya existen aquí"))
                .setItems(opts, (d, w) -> submitTransfer(move, src, dest,
                        w == 0 ? TransferEngine.Conflict.REPLACE : w == 1 ? TransferEngine.Conflict.KEEP_BOTH : TransferEngine.Conflict.SKIP))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void submitTransfer(boolean move, List<String> src, String dest, TransferEngine.Conflict c) {
        engine.submit(move ? TransferEngine.Kind.MOVE : TransferEngine.Kind.COPY, src, dest, c);
        if (move) clip.clear();
        updateBars();
        Toast.makeText(this, move ? "Moviendo…" : "Copiando…", Toast.LENGTH_SHORT).show();
    }

    private void askDelete() {
        List<String> src = new ArrayList<>(selected);
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("¿Borrar " + (src.size() == 1 ? "«" + Paths.name(src.get(0)) + "»" : src.size() + " elementos") + "?")
                .setMessage("Se borran para siempre (no van a una papelera).")
                .setPositiveButton("Borrar", (d, w) -> {
                    engine.submit(TransferEngine.Kind.DELETE, src, null, TransferEngine.Conflict.SKIP);
                    clearSelection();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askZip() {
        List<String> src = new ArrayList<>(selected);
        String suggested = src.size() == 1 ? stripExt(Paths.name(src.get(0))) : Paths.name(path);
        askName("Comprimir en .zip", suggested + ".zip", name -> {
            String n = name.toLowerCase().endsWith(".zip") ? name : name + ".zip";
            engine.submit(TransferEngine.Kind.ZIP, src, Paths.join(path, n), TransferEngine.Conflict.REPLACE);
            clearSelection();
        });
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private void showMore() {
        List<String> sel = new ArrayList<>(selected);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        Entry one = null;
        if (sel.size() == 1) for (Entry e : all) if (e.path.equals(sel.get(0))) one = e;
        final Entry single = one;
        if (single != null) {
            labels.add("Renombrar");
            acts.add(() -> askRename(single));
            if (single.dir) {
                labels.add(prefs.isFavorite(single.path) ? "Quitar de favoritas" : "Añadir a favoritas");
                acts.add(() -> {
                    prefs.toggleFavorite(single.path);
                    clearSelection();
                });
                labels.add(prefs.isPinned(single.path) ? "Desanclar" : "Anclar arriba");
                acts.add(() -> {
                    prefs.togglePinned(single.path);
                    clearSelection();
                    buildChips();
                });
            } else {
                labels.add("Abrir con…");
                acts.add(() -> openWith(single, true));
                if (Archive.supported(single.name)) {
                    labels.add("Ver contenido");
                    acts.add(() -> {
                        clearSelection();
                        openArchive(single.path);
                    });
                    labels.add("Extraer aquí");
                    acts.add(() -> {
                        engine.submitExtract(single.path, new ArrayList<>(), "", Paths.join(path, Archive.baseName(single.name)));
                        clearSelection();
                        Toast.makeText(this, "Extrayendo…", Toast.LENGTH_SHORT).show();
                    });
                    labels.add("Extraer a…");
                    acts.add(() -> {
                        setExtractClip(single.path, new ArrayList<>(), "");
                        clearSelection();
                        Toast.makeText(this, "Ve a la carpeta destino y toca «Extraer aquí»", Toast.LENGTH_LONG).show();
                    });
                }
            }
        }
        boolean allFiles = true;
        for (String s : sel) for (Entry e : all) if (e.path.equals(s) && e.dir) allFiles = false;
        if (allFiles) {
            labels.add("Compartir");
            acts.add(() -> share(sel));
        }
        labels.add("Subir a MediaFire");
        acts.add(() -> {
            CloudActivity.pendingUpload.clear();
            CloudActivity.pendingUpload.addAll(sel);
            clearSelection();
            Toast.makeText(this, "Elige la carpeta de MediaFire y toca «Subir aquí»", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, CloudActivity.class));
        });
        labels.add("Propiedades");
        acts.add(() -> properties(sel));
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void askName(String titleText, String initial, NameCallback cb) {
        EditText in = new EditText(this);
        in.setText(initial);
        in.setTextColor(Ui.TEXT);
        in.setSingleLine(true);
        int dot = initial.lastIndexOf('.');
        in.setSelection(0, dot > 0 ? dot : initial.length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(titleText)
                .setView(box)
                .setPositiveButton("Aceptar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = in.getText().toString().trim();
            if (!Paths.validName(n)) {
                in.setError("Nombre no válido");
                return;
            }
            d.dismiss();
            cb.onName(n);
        }));
        d.show();
        in.requestFocus();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void askRename(Entry e) {
        askName("Renombrar", e.name, n -> io.execute(() -> {
            String to = Paths.join(Paths.parent(e.path), n);
            String err = null;
            try {
                Fs f = FsRouter.get(this, e.path);
                if (f.stat(to) != null && !n.equalsIgnoreCase(e.name)) err = "Ya existe algo con ese nombre";
                else f.rename(e.path, to);
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String fe = err;
            main.post(() -> {
                if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                if (prefs.isFavorite(e.path)) {
                    prefs.toggleFavorite(e.path);
                    prefs.toggleFavorite(to);
                }
                if (prefs.isPinned(e.path)) {
                    prefs.togglePinned(e.path);
                    prefs.togglePinned(to);
                }
                clearSelection();
                load(path, true);
            });
        }));
    }

    private void showCreate() {
        if (path == null) return;
        String[] opts = {"Nueva carpeta", "Nuevo archivo de texto"};
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(opts, (d, w) -> askName(opts[w], w == 0 ? "Nueva carpeta" : "nuevo.txt", n -> io.execute(() -> {
                    String target = Paths.join(path, n);
                    String err = null;
                    try {
                        Fs f = FsRouter.get(this, target);
                        if (f.stat(target) != null) err = "Ya existe algo con ese nombre";
                        else if (w == 0) f.mkdir(target);
                        else f.out(target).close();
                    } catch (IOException ex) {
                        err = ex.getMessage();
                    }
                    String fe = err;
                    main.post(() -> {
                        if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                        load(path, true);
                    });
                })))
                .show();
    }

    private void properties(List<String> sel) {
        TextView t = Ui.text(this, "Calculando…", 14, Ui.TEXT, false);
        t.setPadding(dp(24), dp(12), dp(24), dp(8));
        t.setTextIsSelectable(true);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(sel.size() == 1 ? Paths.name(sel.get(0)) : sel.size() + " elementos")
                .setView(t)
                .setPositiveButton("Cerrar", null)
                .show();
        io.execute(() -> {
            long[] acc = {0, 0, 0};
            StringBuilder sb = new StringBuilder();
            try {
                for (String s : sel) measure(FsRouter.get(this, s), s, acc);
                if (sel.size() == 1) {
                    Entry e = FsRouter.get(this, sel.get(0)).stat(sel.get(0));
                    sb.append("Ruta: ").append(sel.get(0)).append("\n");
                    if (e != null) sb.append("Modificado: ").append(Ui.date(e.modified)).append("\n");
                }
                sb.append("Tamaño: ").append(Ui.size(acc[0])).append(" (").append(String.format("%,d", acc[0])).append(" bytes)\n");
                sb.append("Archivos: ").append(acc[1]).append("   Carpetas: ").append(acc[2]);
            } catch (IOException ex) {
                sb.append(ex.getMessage());
            }
            main.post(() -> {
                if (d.isShowing()) t.setText(sb.toString());
            });
        });
    }

    private static void measure(Fs f, String p, long[] acc) throws IOException {
        Entry e = f.stat(p);
        if (e == null) return;
        if (e.dir) {
            acc[2]++;
            for (Entry k : f.list(p)) measure(f, k.path, acc);
        } else {
            acc[0] += e.size;
            acc[1]++;
        }
    }

    // ------------------------------------------------------------------ abrir y compartir

    private Uri contentUri(String p) {
        return StorageProvider.documentUri(this, p);
    }

    private void openFile(Entry e) {
        openWith(e, false);
    }

    private void openWith(Entry e, boolean chooser) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(contentUri(e.path), FileTypes.mime(e.name));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(chooser ? Intent.createChooser(i, "Abrir con") : i);
        } catch (Exception ex) {
            Intent any = new Intent(Intent.ACTION_VIEW);
            any.setDataAndType(contentUri(e.path), "*/*");
            any.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(any, "Abrir con"));
            } catch (Exception ignored) {
                Toast.makeText(this, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void share(List<String> sel) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (String s : sel) uris.add(contentUri(s));
        Intent i = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        i.setType(uris.size() == 1 ? FileTypes.mime(Paths.name(sel.get(0))) : "*/*");
        if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Compartir"));
        clearSelection();
    }

    /** Modo selector: devuelve los archivos elegidos a la app que los pidió. */
    protected void pick(List<String> paths) {
        List<String> files = new ArrayList<>();
        for (String p : paths) {
            boolean dir = false;
            for (Entry e : all) if (e.path.equals(p) && e.dir) dir = true;
            if (!dir) files.add(p);
        }
        if (files.isEmpty()) return;
        Intent r = new Intent();
        Uri first = contentUri(files.get(0));
        r.setData(first);
        ClipData cd = ClipData.newRawUri("", first);
        for (int i = 1; i < files.size(); i++) cd.addItem(new ClipData.Item(contentUri(files.get(i))));
        r.setClipData(cd);
        r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        setResult(RESULT_OK, r);
        finish();
    }

    // ------------------------------------------------------------------ menús

    private void showMainMenu() {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add((prefs.showHidden() ? "✓ " : "") + "Mostrar archivos ocultos");
        acts.add(() -> {
            prefs.setShowHidden(!prefs.showHidden());
            if (path != null) load(path, true);
        });
        labels.add("Ordenar por: " + Prefs.SORT_NAMES[prefs.sort()] + (prefs.sortDesc() ? " (descendente)" : ""));
        acts.add(this::chooseSort);
        if (path != null && !picker()) {
            labels.add(prefs.isPinned(path) ? "Desanclar esta carpeta" : "Anclar esta carpeta");
            acts.add(() -> {
                prefs.togglePinned(path);
                buildChips();
            });
            labels.add(prefs.isFavorite(path) ? "Quitar de favoritas" : "Añadir a favoritas");
            acts.add(() -> prefs.toggleFavorite(path));
            labels.add("Seleccionar todo");
            acts.add(this::selectAll);
        }
        if (!picker()) {
            labels.add((prefs.verify() ? "✓ " : "") + "Verificar cada copia (más seguro)");
            acts.add(() -> {
                prefs.setVerify(!prefs.verify());
                Toast.makeText(this, prefs.verify() ? "Cada archivo copiado se vuelve a leer y se compara con el original"
                        : "Verificación apagada: copias más rápidas", Toast.LENGTH_LONG).show();
            });
            labels.add("Shizuku: " + ShizukuHelper.status());
            acts.add(this::useShizuku);
            labels.add("Dar carpetas a otras apps (emuladores…)");
            acts.add(this::explainProvider);
        }
        labels.add("Inicio");
        acts.add(this::showHome);
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void chooseSort() {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Ordenar por")
                .setSingleChoiceItems(Prefs.SORT_NAMES, prefs.sort(), (d, w) -> {
                    if (w == prefs.sort()) prefs.setSortDesc(!prefs.sortDesc());
                    else {
                        prefs.setSort(w);
                        prefs.setSortDesc(w == Prefs.SORT_DATE || w == Prefs.SORT_SIZE);
                    }
                    d.dismiss();
                    if (path != null) load(path, true);
                })
                .show();
    }

    private void explainProvider() {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("Dar carpetas a otras apps")
                .setMessage("Cuando una app (un emulador, por ejemplo) te pida elegir una carpeta o un archivo:\n\n"
                        + "1. En el selector de Android, abre el menú (☰) de la izquierda.\n"
                        + "2. Elige «Bóveda».\n"
                        + "3. Entra a la carpeta que quieras y toca «Usar esta carpeta».\n\n"
                        + "Con Bóveda puedes darle carpetas que el selector normal no deja (la raíz, Descargas) y, "
                        + "si Bóveda tiene Shizuku o el permiso, también Android/data y Android/obb.\n\n"
                        + "Para que Bóveda sea tu gestor de archivos predeterminado, ábrelo desde «Abrir con» la primera vez "
                        + "y elige «Siempre».")
                .setPositiveButton("Entendido", null)
                .show();
    }

    // ------------------------------------------------------------------ transferencias

    private Dialog transfersSheet;
    private LinearLayout transfersList;

    private void showTransfers() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(18), dp(16), dp(18), dp(22));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.SURFACE, 0);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.text(this, "Transferencias", 20, Ui.TEXT, true), Ui.weight(1));
        TextView clear = Ui.text(this, "Limpiar", 13, Ui.ACCENT, true);
        clear.setPadding(dp(10), dp(8), dp(10), dp(8));
        clear.setOnClickListener(v -> {
            engine.clearFinished();
            refreshTransfers();
        });
        head.addView(clear);
        box.addView(head);
        ScrollView sv = new ScrollView(this);
        transfersList = Ui.column(this);
        sv.addView(transfersList);
        box.addView(sv, new LinearLayout.LayoutParams(-1, dp(380)));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        transfersSheet = d;
        refreshTransfers();
        d.show();
    }

    private void refreshTransfers() {
        if (transfersList == null) return;
        transfersList.removeAllViews();
        if (engine.jobs().isEmpty()) {
            TextView t = Ui.text(this, "No hay copias ni movimientos.", 14, Ui.SUB, false);
            t.setPadding(0, dp(20), 0, 0);
            transfersList.addView(t);
        }
        for (TransferEngine.Job j : engine.jobs()) {
            LinearLayout c = Ui.column(this);
            c.setPadding(dp(14), dp(12), dp(14), dp(12));
            c.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(16), dp(1), Ui.STROKE));
            LinearLayout top = Ui.row(this);
            TextView name = Ui.text(this, j.title(), 14.5f, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            top.addView(name, Ui.weight(1));
            if (j.state <= 1) top.addView(Ui.iconButton(this, R.drawable.ic_close, 36, v -> {
                j.cancel = true;
                Toast.makeText(this, "Cancelando… (lo que quedó a medias se borra)", Toast.LENGTH_SHORT).show();
            }));
            c.addView(top);
            View bar = new UsageBar(this, j.progress(), j.state == 3 ? Ui.BAD : j.state == 2 ? Ui.GOOD : Ui.ACCENT);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
            bl.setMargins(0, dp(8), 0, dp(6));
            c.addView(bar, bl);
            String status = switch (j.state) {
                case 0 -> "En cola";
                case 1 -> (j.totalBytes > 0 ? Ui.size(j.doneBytes) + " de " + Ui.size(j.totalBytes) + " · " + Ui.speed(j.speed)
                        : j.doneItems + " de " + j.totalItems) + (j.current.isEmpty() ? "" : "\n" + j.current);
                case 2 -> "Listo ✓" + (j.kind == TransferEngine.Kind.COPY || j.kind == TransferEngine.Kind.MOVE
                        ? (prefs.verify() ? "  ·  verificado" : "") : "");
                case 3 -> "Error: " + j.error;
                default -> "Cancelado";
            };
            c.addView(Ui.text(this, status, 12.5f, j.state == 3 ? Ui.BAD : j.state == 2 ? Ui.GOOD : Ui.SUB, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, 0);
            transfersList.addView(c, lp);
        }
    }

    // ------------------------------------------------------------------ atrás

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            clearSelection();
            return;
        }
        if (search.getVisibility() == View.VISIBLE) {
            toggleSearch();
            return;
        }
        if (path == null) {
            super.onBackPressed();
            return;
        }
        Volume v = volumeOf(path);
        if (v != null && v.path.equals(path) || path.equals("/")) {
            showHome();
            return;
        }
        // si es una carpeta autorizada y no se puede subir más, al inicio
        String parent = Paths.parent(path);
        if (Paths.restricted(path) && !FsRouter.hasAccess(this, parent) && !parent.equals(Paths.ROOT + "/Android")) {
            showHome();
            return;
        }
        open(parent);
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
                LinearLayout r = Ui.row(MainActivity.this);
                r.setPadding(dp(12), dp(8), dp(12), dp(8));
                r.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                h.iconBox = new FrameLayout(MainActivity.this);
                h.icon = new ImageView(MainActivity.this);
                h.iconBox.addView(h.icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                h.check = new ImageView(MainActivity.this);
                h.check.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_check, 0xFF06101F));
                h.check.setBackground(Ui.round(Ui.ACCENT2, dp(10)));
                h.check.setPadding(dp(3), dp(3), dp(3), dp(3));
                h.iconBox.addView(h.check, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM | Gravity.END));
                r.addView(h.iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout t = Ui.column(MainActivity.this);
                t.setPadding(dp(12), 0, 0, 0);
                h.name = Ui.text(MainActivity.this, "", 15, Ui.TEXT, false);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.info = Ui.text(MainActivity.this, "", 12, Ui.SUB, false);
                h.info.setSingleLine(true);
                t.addView(h.name);
                t.addView(h.info);
                r.addView(t, Ui.weight(1));
                h.star = new ImageView(MainActivity.this);
                r.addView(h.star, new LinearLayout.LayoutParams(dp(16), dp(16)));
                v = r;
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Entry e = shown.get(pos);
            boolean sel = selected.contains(e.path);
            int color = FileTypes.color(e);
            h.icon.setImageDrawable(Ui.icon(MainActivity.this, FileTypes.icon(e), color));
            h.iconBox.setBackground(Ui.round((color & 0x00FFFFFF) | (e.hidden() ? 0x14000000 : 0x26000000), dp(14)));
            h.icon.setAlpha(e.hidden() ? 0.55f : 1f);
            h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
            h.name.setText(e.name);
            h.name.setTextColor(e.hidden() ? Ui.SUB : Ui.TEXT);
            h.info.setText(e.dir ? Ui.date(e.modified) : Ui.size(e.size) + "  ·  " + Ui.date(e.modified));
            boolean fav = e.dir && (prefs.isPinned(e.path) || prefs.isFavorite(e.path));
            h.star.setVisibility(fav ? View.VISIBLE : View.GONE);
            if (fav) h.star.setImageDrawable(Ui.icon(MainActivity.this,
                    prefs.isPinned(e.path) ? R.drawable.ic_pin : R.drawable.ic_star, prefs.isPinned(e.path) ? Ui.ACCENT2 : Ui.WARN));
            v.setBackground(sel ? Ui.round(0xFF16264A, dp(14)) : Ui.rippleRound(0x00000000, dp(14)));
            return v;
        }
    }

    private static final class Holder {
        FrameLayout iconBox;
        ImageView icon, check, star;
        TextView name, info;
    }

    /** Barra de uso del almacenamiento / progreso. */
    static final class UsageBar extends View {
        private final float value;
        private final int color;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        UsageBar(android.content.Context c, float value) {
            this(c, value, value > 0.9f ? Ui.BAD : value > 0.75f ? Ui.WARN : Ui.ACCENT);
        }

        UsageBar(android.content.Context c, float value, int color) {
            super(c);
            this.value = Math.max(0f, Math.min(1f, value));
            this.color = color;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            p.setShader(null);
            p.setColor(0xFF232B3C);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(new android.graphics.LinearGradient(0, 0, w, 0, color, Ui.ACCENT2, android.graphics.Shader.TileMode.CLAMP));
            r.set(0, 0, Math.max(h, w * value), h);
            if (value > 0) c.drawRoundRect(r, rad, rad, p);
        }
    }
}
