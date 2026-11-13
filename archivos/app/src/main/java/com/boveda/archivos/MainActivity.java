package com.boveda.archivos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
    // tres pestañas: cada una recuerda su carpeta (null = inicio); se cambia deslizando
    private LinearLayout tabStrip;
    private final TextView[] tabViews = new TextView[Tabs.MAX];
    private Tabs.SwipeFrame swipe;
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

    static final int PICK_FILES = 1, PICK_TREE = 2, PICK_CREATE = 3;

    /** Qué pidió la otra app: archivos, una carpeta (con permiso) o dónde guardar un archivo nuevo. */
    protected int pickMode() {
        if (!picker()) return 0;
        String a = getIntent() == null ? null : getIntent().getAction();
        if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(a)) return PICK_TREE;
        if (Intent.ACTION_CREATE_DOCUMENT.equals(a)) return PICK_CREATE;
        return PICK_FILES;
    }

    private EditText saveName;

    // ------------------------------------------------------------------ inicio

    @Override
    protected void onStop() {
        super.onStop();
        // el ícono de temporada se cambia al salir (cambiarlo con la app abierta puede cerrarla)
        Icons.flush(this);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Season.listener = () -> {
            if (fab != null) styleFab();
            if (path == null && !picker()) showHome();
        };
        prefs = new Prefs(this);
        engine = TransferEngine.get(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && !picker()) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        handleIntent(getIntent());
        if (!picker()) main.postDelayed(this::reportLastExit, 600);
    }

    /**
     * Si Bóveda se cerró sola la última vez (error, «no responde» o falta de memoria), lo cuenta
     * con los detalles técnicos para poder copiarlos y arreglarlo.
     */
    private void reportLastExit() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("diagnostico", MODE_PRIVATE);
            List<android.app.ApplicationExitInfo> l = getSystemService(android.app.ActivityManager.class)
                    .getHistoricalProcessExitReasons(null, 0, 1);
            if (l.isEmpty()) return;
            android.app.ApplicationExitInfo x = l.get(0);
            if (x.getTimestamp() <= sp.getLong("visto", 0)) return;
            sp.edit().putLong("visto", x.getTimestamp()).apply();
            String what;
            switch (x.getReason()) {
                case android.app.ApplicationExitInfo.REASON_CRASH -> what = "se cerró por un error";
                case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> what = "se cerró por un error interno";
                case android.app.ApplicationExitInfo.REASON_ANR -> what = "dejó de responder y Android la cerró";
                case android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> what = "Android la cerró por falta de memoria";
                case android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> what = "Android la cerró por usar demasiados recursos";
                default -> what = null;
            }
            if (what == null) return;
            StringBuilder det = new StringBuilder();
            det.append("Bóveda ").append(BuildConfig.VERSION_NAME).append(L.t(" · Android ")).append(Build.VERSION.RELEASE)
                    .append(" · ").append(Build.MODEL).append('\n');
            det.append(L.t("Motivo: ")).append(what).append(" (").append(x.getReason()).append(")\n");
            if (x.getDescription() != null) det.append(x.getDescription()).append('\n');
            java.io.File crash = new java.io.File(getFilesDir(), "ultimo_error.txt");
            if (x.getReason() == android.app.ApplicationExitInfo.REASON_CRASH && crash.exists()) {
                det.append('\n').append(new String(java.nio.file.Files.readAllBytes(crash.toPath())));
            }
            if (x.getReason() == android.app.ApplicationExitInfo.REASON_ANR) {
                try (java.io.InputStream in = x.getTraceInputStream()) {
                    if (in != null) {
                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                        // el hilo principal es lo que importa: dónde se quedó trabado
                        String line;
                        boolean mainThread = false;
                        int n = 0;
                        while ((line = r.readLine()) != null && n < 60) {
                            if (line.startsWith("\"main\"")) mainThread = true;
                            else if (mainThread && line.isEmpty()) break;
                            if (mainThread) {
                                det.append(line).append('\n');
                                n++;
                            }
                        }
                    }
                }
            }
            String text = det.toString();
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("La última vez Bóveda ") + what)
                    .setMessage(L.t("Copia los detalles y mándaselos a quien hizo la app para arreglarlo.\n\n")
                            + (text.length() > 1800 ? text.substring(0, 1800) + "…" : text))
                    .setPositiveButton(L.t("Copiar detalles"), (d, w) -> {
                        android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(L.t("Error de Bóveda"), text));
                        Toast.makeText(this, L.t("Copiado"), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(L.t("Cerrar"), null)
                    .show();
        } catch (Throwable ignored) {
        }
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
        // ¿hay versión nueva? (no cuando otra app la abrió para elegir un archivo)
        if (!picker()) {
            Updater.resume(this);
            Updater.check(this);
        Integrity.check(this);
        // temas de temporada: la nieve o las chispitas, y el ícono si cambió la temporada
        SeasonFx.attach(this);
        if (!picker()) Season.checkIcon(this);
        }
        // la Bóveda se cerró sola mientras estabas en otra app: no se queda mostrando sus archivos
        if (path != null && VaultFs.isVault(path) && !Vault.unlocked()) showHome();
        engine.addListener(this);
        ShizukuHelper.addListener(shizukuListener);
        updateTransfersBadge();
        if (!hasStorage()) showPermissionGate();
        else if (!picker() && !java.util.Objects.equals(Tabs.path[Tabs.current], path)) {
            // se cambió de pestaña o se salió de MediaFire
            String want = Tabs.path[Tabs.current];
            if (Tabs.MEDIAFIRE.equals(want)) want = Tabs.path[Tabs.current] = Tabs.mfReturn[Tabs.current];
            selected.clear();
            showTab(want);
        } else if (path == null) showHome();
        else load(path, false);
        updateTabs();
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
        if (!picker()) head.addView(Ui.iconButton(this, R.drawable.ic_settings, 44, v -> showSettings()));
        head.addView(Ui.iconButton(this, R.drawable.ic_menu, 44, v -> showMainMenu()));
        col.addView(head);

        search = new EditText(this);
        search.setHint(L.t("Buscar en esta carpeta"));
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

        // pestañas 1 · 2 · 3 (compartidas con MediaFire)
        Tabs.configure(prefs);
        tabStrip = Tabs.strip(this, tabViews, this::switchTab);
        if (picker()) tabStrip.setVisibility(View.GONE);
        col.addView(tabStrip);

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        loading.setVisibility(View.INVISIBLE);
        col.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        swipe = new Tabs.SwipeFrame(this, dir -> switchTab(Tabs.current + dir));
        swipe.enabled = !picker();
        content = swipe;
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
        plus.setElevation(dp(6));
        plus.setOnClickListener(v -> showCreate());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, dp(18), dp(22));
        fab = plus;
        styleFab();
        root.addView(plus, fl);
        if (!picker()) {
            progressCard = new ProgressCard(this, root, 92, this::showTransfers);
            // si hay barra de acciones abajo (con letra grande es más alta), la tarjeta se coloca encima
            bottom.addOnLayoutChangeListener((vw, l, t, r, b, ol, ot, or, ob) ->
                    progressCard.setBottomMargin(bottom.getVisibility() == View.VISIBLE ? bottom.getHeight() + dp(10) : dp(92)));
        }
        setContentView(root);
    }

    // ------------------------------------------------------------------ tarjeta flotante de progreso

    private ProgressCard progressCard;

    private boolean transferring;

    private void updateTransfersBadge() {
        if (progressCard != null) progressCard.update();
        int active = 0;
        for (TransferEngine.Job j : engine.jobs()) if (j.state <= 1) active++;
        if ((active > 0) != transferring) {
            transferring = active > 0;
            applyKeepScreen();
            updateBars();
        }
        transfersBadge.setText(String.valueOf(active));
        transfersBadge.setVisibility(active > 0 ? View.VISIBLE : View.GONE);
    }

    /** Pantalla encendida mientras hay transferencias, si así lo eligió. */
    private void applyKeepScreen() {
        if (transferring && prefs.keepScreenOn()) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                Toast.makeText(this, L.t("Desanclada"), Toast.LENGTH_SHORT).show();
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
            out.add(new Volume(dir.getAbsolutePath(), v.isPrimary() ? L.t("Almacenamiento interno") : v.getDescription(this), v.isRemovable()));
        }
        if (out.isEmpty()) out.add(new Volume(Paths.ROOT, L.t("Almacenamiento interno"), false));
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
        if (VaultFs.isVault(p)) return new Volume(VaultFs.ROOT, L.t("La Bóveda"), L.t("Bóveda"), false);
        if (DriveFs.isDrive(p)) return new Volume(DriveFs.ROOT, "Google Drive", "Drive", false);
        if (MegaFs.isMega(p)) return new Volume(MegaFs.ROOT, "MEGA", "MEGA", false);
        if (OneDriveFs.isOneDrive(p)) return new Volume(OneDriveFs.ROOT, "OneDrive", "OneDrive", false);
        Volume best = null;
        for (Volume v : volumes()) if (Paths.under(p, v.path) && (best == null || v.path.length() > best.path.length())) best = v;
        return best;
    }

    private String label(String p) {
        Volume v = volumeOf(p);
        if (v != null && v.path.equals(p)) return v.removable ? L.t("Tarjeta SD") : v.shortName;
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
        TextView t = Ui.text(this, L.t("Bóveda necesita permiso"), 21, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, dp(8));
        box.addView(t);
        TextView d = Ui.text(this, L.t("Para ver y manejar todos tus archivos, Android pide el permiso «Acceso a todos los archivos». ")
                + L.t("En la pantalla que se abre, activa el interruptor de Bóveda y vuelve."), 14.5f, Ui.SUB, false);
        d.setGravity(Gravity.CENTER);
        box.addView(d);
        box.addView(bigButton(L.t("Dar permiso"), v -> {
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

    // ------------------------------------------------------------------ inicio en tarjetas

    /** null = inicio principal; si no, el nombre de la sección abierta (accesos, ancladas, favoritas, nube, funciones, seguridad, shizuku). */
    private String homePage;

    private String homeTitle(String page) {
        return switch (page) {
            case "accesos" -> L.t("Accesos directos");
            case "ancladas" -> L.t("Ancladas");
            case "favoritas" -> L.t("Favoritas");
            case "nube" -> L.t("Nube y red");
            case "funciones" -> L.t("Funciones especiales");
            case "seguridad" -> L.t("Seguridad y privacidad");
            default -> "Shizuku";
        };
    }

    private void openHomePage(String page) {
        homePage = page;
        showHome();
    }

    private void buildCardsHome() {
        boolean sub = homePage != null;
        title.setText(sub ? homeTitle(homePage) : "Bóveda");
        backBtn.setVisibility(sub ? View.VISIBLE : View.GONE);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(14), dp(6), dp(14), dp(40));
        if (!sub) {
            List<View> vols = new ArrayList<>();
            for (Volume v : volumes()) vols.add(volumeCard(v));
            addGrid(box, vols);
            List<View> tiles = new ArrayList<>();
            int pins = prefs.pinned().size(), favs = prefs.favorites().size();
            tiles.add(tile("⚡", 0xFF1E88E5, 0xFF26C6DA, L.t("Accesos directos"), L.t("Descargas, Cámara, Documentos, Android/data…"), v -> openHomePage("accesos")));
            tiles.add(tile("📌", 0xFF6C63FF, 0xFFA29BFE, L.t("Ancladas"), pins == 0 ? L.t("Ninguna todavía") : pins + (pins == 1 ? L.t(" carpeta") : L.t(" carpetas")), v -> openHomePage("ancladas")));
            tiles.add(tile("⭐", 0xFFF2A100, 0xFFFFC14D, L.t("Favoritas"), favs == 0 ? L.t("Ninguna todavía") : favs + (favs == 1 ? L.t(" carpeta") : L.t(" carpetas")), v -> openHomePage("favoritas")));
            int servers = FtpServers.get(this).all().size();
            tiles.add(tile("☁️", 0xFF0A64D6, 0xFF28A8EA, L.t("Nube y red"), servers > 0 ? servers + (servers == 1 ? L.t(" servidor") : L.t(" servidores")) + L.t(" y más") : L.t("Drive, OneDrive, MEGA y más"), v -> openHomePage("nube")));
            tiles.add(tile("🛠️", 0xFFE5603B, 0xFFFF9A5C, L.t("Funciones especiales"), L.t("Reducir peso y más por venir"), v -> openHomePage("funciones")));
            tiles.add(tile("🛡️", 0xFF7B4DFF, 0xFFB86BFF, L.t("Seguridad y privacidad"), L.t("La Bóveda, Radiografía, Apps"), v -> openHomePage("seguridad")));
            View trash = tile("🗑️", 0xFF4A5670, 0xFF6E7A8E, L.t("Papelera"), L.t("Lo que borres se guarda 30 días"), v -> showTrash());
            tiles.add(trash);
            tiles.add(tile("💧", 0xFF35D08A, 0xFF1EA97C, "Shizuku", ShizukuHelper.ready() ? L.t("Conectado") : L.t("Para entrar a Android/data"), v -> openHomePage("shizuku")));
            addGrid(box, tiles);
            TextView trashSub = (TextView) ((LinearLayout) trash).getChildAt(2);
            io.execute(() -> {
                String sx = trashSubtitle();
                main.post(() -> trashSub.setText(sx));
            });
        } else {
            switch (homePage) {
                case "accesos" -> {
                    String r0 = Paths.ROOT;
                    box.addView(shortcut(R.drawable.ic_download, 0xFF6FD6FF, L.t("Descargas"), r0 + "/Download", null));
                    box.addView(shortcut(R.drawable.ic_camera, 0xFFFF8A5C, L.t("Cámara (DCIM)"), r0 + "/DCIM", null));
                    box.addView(shortcut(R.drawable.ic_doc, 0xFFFFC14D, L.t("Documentos"), r0 + "/Documents", null));
                    box.addView(shortcut(R.drawable.ic_music, 0xFFB58CFF, L.t("Música"), r0 + "/Music", null));
                    boolean dataOk = FsRouter.hasAccess(this, Paths.DATA), obbOk = FsRouter.hasAccess(this, Paths.OBB);
                    box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/data", Paths.DATA, dataOk ? L.t("Con acceso") : L.t("Toca para dar acceso")));
                    box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/obb", Paths.OBB, obbOk ? L.t("Con acceso") : L.t("Toca para dar acceso")));
                }
                case "ancladas" -> {
                    List<String> pins = prefs.pinned();
                    if (pins.isEmpty()) box.addView(hint(L.t("Mantén presionada una carpeta, toca ⋮ y elige «Anclar arriba» para tenerla aquí.")));
                    for (String p : pins) box.addView(shortcut(R.drawable.ic_pin, Ui.ACCENT2, label(p), p, p));
                }
                case "favoritas" -> {
                    List<String> favs = prefs.favorites();
                    if (favs.isEmpty()) box.addView(hint(L.t("Mantén presionada una carpeta y elige «Favorita» para tenerla aquí.")));
                    for (String p : favs) box.addView(shortcut(R.drawable.ic_star, Ui.WARN, Paths.name(p), p, p));
                }
                case "nube" -> {
                    MediaFire mf = MediaFire.get(this);
                    box.addView(cloudCard(mf.loggedIn() ? mf.email() : L.t("Toca para iniciar sesión")));
                    Drive gd = Drive.get(this);
                    box.addView(netRow("GD", 0xFF34A853, 0xFF4285F4, "Google Drive",
                            gd.connected() ? (gd.email().isEmpty() ? L.t("Conectado") : gd.email()) : L.t("Toca para conectar"),
                            v -> openDrive(), v -> {
                                driveMenu();
                                return true;
                            }));
                    OneDrive od = OneDrive.get(this);
                    box.addView(netRow("OD", 0xFF0A64D6, 0xFF28A8EA, "OneDrive",
                            od.connected() ? (od.email().isEmpty() ? L.t("Conectado") : od.email()) : L.t("Toca para conectar"),
                            v -> openOneDrive(), v -> {
                                oneDriveMenu();
                                return true;
                            }));
                    Mega mg = Mega.get(this);
                    box.addView(netRow("M", 0xFFD9272E, 0xFFF0545A, "MEGA",
                            mg.loggedIn() ? mg.email() : L.t("Toca para iniciar sesión"),
                            v -> openMega(), v -> {
                                megaMenu();
                                return true;
                            }));
                    for (FtpServers.Server sv : FtpServers.get(this).all()) {
                        box.addView(netRow("FTP", 0xFFFFB547, 0xFFF07C2C, sv.name, sv.user + "@" + sv.host + ":" + sv.port,
                                v -> open(sv.root()), v -> {
                                    ftpMenu(sv);
                                    return true;
                                }));
                    }
                    box.addView(netRow("+", 0xFF2A3346, 0xFF2A3346, L.t("Añadir servidor FTP"), L.t("Xbox 360, PC, NAS, otro celular…"), v -> editFtp(null), null));
                }
                case "funciones" -> {
                    box.addView(netRow("♻️", 0xFFE5603B, 0xFFFF9A5C, L.t("Reducir peso"),
                            L.t("Busca archivos repetidos. Selecciona una carpeta o un comprimido, toca ⋮ y elige «Reducir peso»."),
                            v -> Toast.makeText(this, L.t("Selecciona una carpeta o un comprimido, toca ⋮ y elige «Reducir peso»."), Toast.LENGTH_LONG).show(), null));
                    box.addView(hint(L.t("Aquí irán las funciones originales de Bóveda a medida que lleguen.")));
                }
                case "seguridad" -> {
                    box.addView(netRow("🔐", 0xFF7B4DFF, 0xFFB86BFF, L.t("La Bóveda"),
                            !Vault.exists() ? L.t("Carpeta secreta cifrada · toca para crearla")
                                    : Vault.unlocked() ? L.t("Abierta · mantén presionado para opciones")
                                    : L.t("Cerrada · toca para abrir con tu PIN o tu huella"),
                            v -> openVault(), v -> {
                                vaultMenu();
                                return true;
                            }));
                    box.addView(netRow("📊", 0xFF1E88E5, 0xFF26C6DA, L.t("Radiografía del almacenamiento"),
                            L.t("Mira qué se está comiendo la memoria y libera espacio"), v -> showAnalyzer(), null));
                    box.addView(netRow("🧩", 0xFF35D08A, 0xFF1EA97C, L.t("Identificar aplicaciones"),
                            L.t("Todas las apps del celular, con su peso, sus rutas y sus permisos"), v -> AppsInspector.show(this), null));
                }
                default -> box.addView(shizukuCard());
            }
        }
        addExtraView(box);
    }

    private View hint(String text) {
        TextView t = Ui.text(this, text, 13, Ui.SUB, false);
        t.setPadding(dp(6), dp(10), dp(6), dp(6));
        return t;
    }

    /** Pone las vistas de dos en dos, mismo ancho. */
    private void addGrid(LinearLayout box, List<View> views) {
        for (int i = 0; i < views.size(); i += 2) {
            LinearLayout row = Ui.row(this);
            row.setBaselineAligned(false);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                lp.setMargins(k == 0 ? 0 : dp(5), 0, k == 0 ? dp(5) : 0, 0);
                if (i + k < views.size()) row.addView(views.get(i + k), lp);
                else row.addView(new View(this), lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.setMargins(0, 0, 0, dp(10));
            box.addView(row, rl);
        }
    }

    /** Un cuadro del inicio: ícono arriba, nombre completo y un dato chiquito. */
    private View tile(String badge, int c1, int c2, String name, String sub, View.OnClickListener tap) {
        LinearLayout t = Ui.column(this);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(Ui.rippleRound(Ui.SURFACE, dp(18)));
        TextView logo = Ui.text(this, badge, 22, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.gradient(dp(14), c1, c2));
        t.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView n = Ui.text(this, name, 15, Ui.TEXT, true);
        n.setPadding(0, dp(10), 0, 0);
        t.addView(n);
        TextView sv = Ui.text(this, sub, 12, Ui.SUB, false);
        sv.setPadding(0, dp(3), 0, 0);
        sv.setMaxLines(3);
        sv.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(sv);
        t.setOnClickListener(tap);
        return t;
    }

    /** Una unidad de almacenamiento (interna, SD o USB), con su barra de uso. */
    private View volumeCard(Volume v) {
        File f = new File(v.path);
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        int[] cc = SeasonFx.cardColors(this, 0xFF14284F, 0xFF101A30);
        card.setBackground(Ui.ripple(Ui.gradient(dp(20), cc[0], cc[1]), Ui.round(Color.WHITE, dp(20))));
        SeasonFx.decorate(card, dp(20));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Ui.icon(this, v.removable ? R.drawable.ic_sd : R.drawable.ic_phone, Ui.ACCENT2));
        card.addView(ic, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView n = Ui.text(this, v.name, 15.5f, Ui.TEXT, true);
        n.setPadding(0, dp(8), 0, 0);
        n.setMaxLines(2);
        // la palabra larga («Almacenamiento») se achica sola para no partirse a la mitad
        n.setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        card.addView(n, new LinearLayout.LayoutParams(-1, dp(48)));
        FrameLayout barBox = new FrameLayout(this);
        barBox.addView(new UsageBar(this, 0));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
        bl.setMargins(0, dp(10), 0, 0);
        card.addView(barBox, bl);
        TextView space = Ui.text(this, "…", 12.5f, Ui.SUB, false);
        space.setPadding(0, dp(6), 0, 0);
        card.addView(space);
        new Thread(() -> {
            long total = f.getTotalSpace(), free = f.getUsableSpace();
            main.post(() -> {
                space.setText(Ui.size(total - free) + " / " + Ui.size(total));
                barBox.removeAllViews();
                barBox.addView(new UsageBar(this, total > 0 ? (total - free) / (float) total : 0));
            });
        }, "espacio").start();
        card.setOnClickListener(x -> open(v.path));
        return card;
    }

    // ------------------------------------------------------------------ pantalla de inicio

    private void showHome() {
        path = null;
        if (!picker()) Tabs.path[Tabs.current] = null;
        updateTabs();
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
        title.setText(!picker() ? "Bóveda" : pickMode() == PICK_TREE ? L.t("Elige una carpeta")
                : pickMode() == PICK_CREATE ? L.t("¿Dónde guardarlo?") : L.t("Elige un archivo"));
        crumbs.removeAllViews();
        buildChips();
        if (!picker()) {
            buildCardsHome();
            return;
        }

        LinearLayout box = Ui.column(this);
        box.setPadding(dp(14), dp(6), dp(14), dp(40));

        for (Volume v : volumes()) {
            File f = new File(v.path);
            LinearLayout card = Ui.column(this);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            int[] cc = SeasonFx.cardColors(this, 0xFF14284F, 0xFF101A30);
            card.setBackground(Ui.ripple(Ui.gradient(dp(20), cc[0], cc[1]), Ui.round(Color.WHITE, dp(20))));
            SeasonFx.decorate(card, dp(20));
            LinearLayout r = Ui.row(this);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(Ui.icon(this, v.removable ? R.drawable.ic_sd : R.drawable.ic_phone, Ui.ACCENT2));
            r.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView n = Ui.text(this, v.name, 16.5f, Ui.TEXT, true);
            n.setPadding(dp(10), 0, 0, 0);
            r.addView(n, Ui.weight(1));
            TextView space = Ui.text(this, "…", 13, Ui.SUB, false);
            card.addView(r);
            FrameLayout barBox = new FrameLayout(this);
            barBox.addView(new UsageBar(this, 0));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
            bl.setMargins(0, dp(12), 0, 0);
            card.addView(barBox, bl);
            // el espacio va debajo: con letra grande, a un lado le quitaba todo el ancho al nombre
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.setMargins(0, dp(8), 0, 0);
            card.addView(space, sl);
            // el espacio se mide aparte: una USB o SD ocupada copiando puede tardar en contestar
            new Thread(() -> {
                long total = f.getTotalSpace(), free = f.getUsableSpace();
                main.post(() -> {
                    space.setText(Ui.size(total - free) + " / " + Ui.size(total));
                    barBox.removeAllViews();
                    barBox.addView(new UsageBar(this, total > 0 ? (total - free) / (float) total : 0));
                });
            }, "espacio").start();
            card.setOnClickListener(x -> open(v.path));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.setMargins(0, 0, 0, dp(10));
            box.addView(card, cl);
        }

        box.addView(section(L.t("Accesos rápidos")));
        String r0 = Paths.ROOT;
        box.addView(shortcut(R.drawable.ic_download, 0xFF6FD6FF, L.t("Descargas"), r0 + "/Download", null));
        box.addView(shortcut(R.drawable.ic_camera, 0xFFFF8A5C, L.t("Cámara (DCIM)"), r0 + "/DCIM", null));
        box.addView(shortcut(R.drawable.ic_doc, 0xFFFFC14D, L.t("Documentos"), r0 + "/Documents", null));
        box.addView(shortcut(R.drawable.ic_music, 0xFFB58CFF, L.t("Música"), r0 + "/Music", null));
        boolean dataOk = FsRouter.hasAccess(this, Paths.DATA), obbOk = FsRouter.hasAccess(this, Paths.OBB);
        box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/data", Paths.DATA,
                dataOk ? L.t("Con acceso") : L.t("Toca para dar acceso")));
        box.addView(shortcut(R.drawable.ic_android, 0xFF35D08A, "Android/obb", Paths.OBB,
                obbOk ? L.t("Con acceso") : L.t("Toca para dar acceso")));

        List<String> pins = prefs.pinned();
        if (!pins.isEmpty()) {
            box.addView(section(L.t("Ancladas")));
            for (String p : pins) box.addView(shortcut(R.drawable.ic_pin, Ui.ACCENT2, label(p), p, p));
        }
        List<String> favs = prefs.favorites();
        box.addView(section(L.t("Favoritas")));
        if (favs.isEmpty()) {
            TextView t = Ui.text(this, L.t("Mantén presionada una carpeta y elige «Favorita» para tenerla aquí."), 13, Ui.SUB, false);
            t.setPadding(dp(6), 0, dp(6), dp(6));
            box.addView(t);
        }
        for (String p : favs) box.addView(shortcut(R.drawable.ic_star, Ui.WARN, Paths.name(p), p, p));

        if (!picker()) {
            box.addView(section(L.t("Nube y red")));
            MediaFire mf = MediaFire.get(this);
            box.addView(cloudCard(mf.loggedIn() ? mf.email() : L.t("Toca para iniciar sesión")));
            Drive gd = Drive.get(this);
            box.addView(netRow("GD", 0xFF34A853, 0xFF4285F4, "Google Drive",
                    gd.connected() ? (gd.email().isEmpty() ? L.t("Conectado") : gd.email()) : L.t("Toca para conectar"),
                    v -> openDrive(), v -> {
                        driveMenu();
                        return true;
                    }));
            OneDrive od = OneDrive.get(this);
            box.addView(netRow("OD", 0xFF0A64D6, 0xFF28A8EA, "OneDrive",
                    od.connected() ? (od.email().isEmpty() ? L.t("Conectado") : od.email()) : L.t("Toca para conectar"),
                    v -> openOneDrive(), v -> {
                        oneDriveMenu();
                        return true;
                    }));
            Mega mg = Mega.get(this);
            box.addView(netRow("M", 0xFFD9272E, 0xFFF0545A, "MEGA",
                    mg.loggedIn() ? mg.email() : L.t("Toca para iniciar sesión"),
                    v -> openMega(), v -> {
                        megaMenu();
                        return true;
                    }));
            for (FtpServers.Server sv : FtpServers.get(this).all()) {
                box.addView(netRow("FTP", 0xFFFFB547, 0xFFF07C2C, sv.name, sv.user + "@" + sv.host + ":" + sv.port,
                        v -> open(sv.root()), v -> {
                            ftpMenu(sv);
                            return true;
                        }));
            }
            box.addView(netRow("+", 0xFF2A3346, 0xFF2A3346, L.t("Añadir servidor FTP"), L.t("Xbox 360, PC, NAS, otro celular…"),
                    v -> editFtp(null), null));
            box.addView(section(L.t("Análisis y seguridad")));
            box.addView(netRow("🔐", 0xFF7B4DFF, 0xFFB86BFF, L.t("La Bóveda"),
                    !Vault.exists() ? L.t("Carpeta secreta cifrada · toca para crearla")
                            : Vault.unlocked() ? L.t("Abierta · mantén presionado para opciones")
                            : L.t("Cerrada · toca para abrir con tu PIN o tu huella"),
                    v -> openVault(), v -> {
                        vaultMenu();
                        return true;
                    }));
            box.addView(netRow("📊", 0xFF1E88E5, 0xFF26C6DA, L.t("Radiografía del almacenamiento"),
                    L.t("Mira qué se está comiendo la memoria y libera espacio"), v -> showAnalyzer(), null));
            box.addView(netRow("🧩", 0xFF35D08A, 0xFF1EA97C, L.t("Identificar aplicaciones"),
                    L.t("Todas las apps del celular, con su peso, sus rutas y sus permisos"), v -> AppsInspector.show(this), null));
            View trashRow = netRow("🗑️", 0xFF4A5670, 0xFF6E7A8E, L.t("Papelera"), L.t("Lo que borres se guarda 30 días"),
                    v -> showTrash(), null);
            box.addView(trashRow);
            TextView trashSub = (TextView) ((LinearLayout) ((LinearLayout) trashRow).getChildAt(1)).getChildAt(1);
            io.execute(() -> {
                String s = trashSubtitle();
                main.post(() -> trashSub.setText(s));
            });
            box.addView(section("Shizuku"));
            box.addView(shizukuCard());
        }
        addExtraView(box);
    }

    /**
     * El botón de crear: «+» normal, una estrella en Navidad, un mechero prendiendo una velita en
     * Velitas, una carta de amor en San Valentín o un regalito en Amor y Amistad.
     */
    private void styleFab() {
        TextView plus = (TextView) fab;
        int season = SeasonFx.active(this);
        int icon, c1, c2;
        switch (season) {
            case Season.NAVIDAD -> { icon = R.drawable.ic_fab_star; c1 = 0xFFE63946; c2 = 0xFF7A1020; }
            case Season.VELITAS -> { icon = R.drawable.ic_fab_candle; c1 = 0xFF8A4A10; c2 = 0xFF3A1A06; }
            case Season.VALENTIN -> { icon = R.drawable.ic_fab_letter; c1 = 0xFFFF4D8D; c2 = 0xFF8A1040; }
            case Season.AMISTAD -> { icon = R.drawable.ic_fab_gift; c1 = 0xFF3FB8FF; c2 = 0xFF7B2CBF; }
            default -> { icon = 0; c1 = Ui.ACCENT2; c2 = Ui.ACCENT; }
        }
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(28));
        plus.setBackground(Ui.ripple(g, Ui.round(Color.WHITE, dp(28))));
        plus.setText(icon == 0 ? "+" : "");
        plus.setForeground(icon == 0 ? null : new android.graphics.drawable.InsetDrawable(getDrawable(icon), dp(11)));
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
                .setItems(new String[]{L.t("Editar"), L.t("Borrar este servidor")}, (d, w) -> {
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
        Ui.passwordEye(e);
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
        EditText name = input(L.t("Nombre (ej. Mi Xbox)"), sv.name, android.text.InputType.TYPE_CLASS_TEXT);
        EditText host = input(L.t("Dirección IP (ej. 192.168.1.20)"), sv.host, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText port = input(L.t("Puerto"), String.valueOf(sv.port), android.text.InputType.TYPE_CLASS_NUMBER);
        // sin mayúscula automática ni autocorrector: el usuario y la contraseña van tal cual
        EditText user = input(L.t("Usuario (vacío = anónimo)"), sv.user, android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = input(L.t("Contraseña"), sv.pass, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView opts = Ui.text(this, "", 12.5f, Ui.SUB, false);
        Runnable showOpts = () -> opts.setText((sv.passive ? L.t("Modo pasivo") : L.t("Modo activo")) + "  ·  " + sv.maxConnections
                + (sv.maxConnections == 1 ? L.t(" conexión") : L.t(" conexiones")) + "  ·  " + (sv.latin1 ? "Latin-1" : "UTF-8") + L.t("   (toca para cambiar)"));
        showOpts.run();
        opts.setPadding(dp(4), dp(10), dp(4), dp(4));
        opts.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(new String[]{L.t("Modo: ") + (sv.passive ? L.t("pasivo → activo") : L.t("activo → pasivo")),
                        L.t("Conexiones a la vez: ") + sv.maxConnections + " → " + (sv.maxConnections >= 4 ? 1 : sv.maxConnections * 2),
                        L.t("Letras: ") + (sv.latin1 ? L.t("Latin-1 → UTF-8") : L.t("UTF-8 → Latin-1 (servidores viejos)"))}, (d, w) -> {
                    if (w == 0) sv.passive = !sv.passive;
                    else if (w == 1) sv.maxConnections = sv.maxConnections >= 4 ? 1 : sv.maxConnections * 2;
                    else sv.latin1 = !sv.latin1;
                    showOpts.run();
                }).show());
        presets.addView(chip(L.t("🎮 Xbox 360"), false, v -> {
            if (name.getText().toString().trim().isEmpty()) name.setText("Xbox 360");
            port.setText("21");
            user.setText("xboxftp");
            pass.setText("xboxftp");
            sv.maxConnections = 1;
            sv.passive = true;
            sv.latin1 = true;
            showOpts.run();
            Toast.makeText(this, L.t("Pon la IP que muestra Aurora en la pantalla de la Xbox. Usuario y contraseña: xboxftp (en FreeStyle Dash: xbox)"), Toast.LENGTH_LONG).show();
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
                .setTitle(existing == null ? L.t("Nuevo servidor FTP") : L.t("Editar servidor"))
                .setView(scroll)
                .setPositiveButton(L.t("Conectar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String h = host.getText().toString().trim().replaceFirst("^ftp://", "");
            if (h.isEmpty()) {
                host.setError(L.t("Falta la dirección"));
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
            d.getButton(AlertDialog.BUTTON_POSITIVE).setText(L.t("Probando…"));
            io.execute(() -> {
                String err = null;
                FtpClient c = new FtpClient(sv.host, sv.port, sv.passive,
                        sv.latin1 ? java.nio.charset.StandardCharsets.ISO_8859_1 : java.nio.charset.StandardCharsets.UTF_8);
                try {
                    c.connect(sv.user, sv.pass);
                    c.list("/");
                } catch (Exception e) {
                    err = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    c.close();
                }
                String fe = err;
                main.post(() -> {
                    if (fe != null) {
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setText(L.t("Conectar"));
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle(L.t("No se pudo conectar"))
                                .setMessage(fe + L.t("\n\nRevisa la IP, que el celular y el equipo estén en la misma red Wi-Fi y que el servidor FTP esté encendido. ")
                                        + L.t("Si sigue fallando, cambia el modo pasivo/activo."))
                                .setPositiveButton(L.t("Entendido"), null).show();
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

    // ------------------------------------------------------------------ OneDrive

    private void openOneDrive() {
        if (OneDrive.get(this).connected()) open(OneDriveFs.ROOT);
        else showOneDriveSetup();
    }

    private void oneDriveMenu() {
        OneDrive od = OneDrive.get(this);
        if (!od.configured()) {
            showOneDriveSetup();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("OneDrive")
                .setItems(new String[]{L.t("Desconectar la cuenta"), L.t("Cambiar el ID de aplicación")}, (d, w) -> {
                    if (w == 0) od.disconnect();
                    else od.forgetClient();
                    showHome();
                })
                .show();
    }

    private void showOneDriveSetup() {
        OneDrive od = OneDrive.get(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText id = input(L.t("ID de aplicación (cliente)"), od.clientId(), android.text.InputType.TYPE_CLASS_TEXT);
        if (!od.configured()) {
            TextView guide = Ui.text(this, L.t("Microsoft pide que cada app esté registrada. Se hace una sola vez, gratis:\n\n")
                    + L.t("1. Entra a entra.microsoft.com (o portal.azure.com) con tu cuenta de Microsoft.\n")
                    + L.t("2. Busca «Registros de aplicaciones» → «Nuevo registro».\n")
                    + L.t("3. Nombre: Bóveda. En «Tipos de cuenta compatibles» elige la opción que incluye «cuentas personales de Microsoft».\n")
                    + "4. En «URI de redirección» elige la plataforma «Cliente público/nativo (móvil y escritorio)» y escribe: http://localhost\n"
                    + L.t("5. Toca «Registrar» y copia aquí el «Id. de aplicación (cliente)», que se ve así: 1a2b3c4d-…\n\n")
                    + L.t("No hace falta secreto ni tarjeta para esto. Al conectar, Microsoft te pedirá permiso para que Bóveda vea tus archivos."), 13, Ui.SUB, false);
            guide.setTextIsSelectable(true);
            box.addView(guide);
            box.addView(id, topMargin(dp(12)));
        } else {
            box.addView(Ui.text(this, L.t("Se abrirá el navegador para que entres con tu cuenta de Microsoft y le des permiso a Bóveda."), 14, Ui.SUB, false));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Conectar OneDrive"))
                .setView(scroll)
                .setPositiveButton(L.t("Conectar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!od.configured()) {
                String i = id.getText().toString().trim();
                if (!i.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                    id.setError(L.t("Copia el Id. de aplicación completo (8-4-4-4-12 letras y números)"));
                    return;
                }
                od.setClient(i);
            }
            d.dismiss();
            Toast.makeText(this, L.t("Entra con tu cuenta de Microsoft en el navegador y vuelve aquí"), Toast.LENGTH_LONG).show();
            od.login(this, err -> {
                if (err != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle(L.t("No se conectó OneDrive"))
                            .setMessage(err).setPositiveButton(L.t("Entendido"), null).show();
                    return;
                }
                Toast.makeText(this, L.t("OneDrive conectado"), Toast.LENGTH_SHORT).show();
                open(OneDriveFs.ROOT);
            });
        }));
        d.show();
    }

    // ------------------------------------------------------------------ firmador de APK

    private static boolean isKeyFile(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".p12") || n.endsWith(".pfx") || n.endsWith(".bks") || n.endsWith(".jks") || n.endsWith(".keystore");
    }

    private void askSignApk(Entry e) {
        clearSelection();
        io.execute(() -> {
            String label;
            try {
                label = ApkSign.signer(this).label;
            } catch (Exception ex) {
                label = L.t("Llave de Bóveda");
            }
            String fl = label;
            main.post(() -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("Firmar «") + e.name + "»")
                    .setMessage(L.t("Se crea una copia firmada en la misma carpeta, lista para instalar (firmas v1, v2 y v3, y alineada).")
                            + "\n\n" + L.t("Llave: ") + fl + "\n\n"
                            + L.t("Ojo: si esa app o juego ya está instalado con otra firma, primero toca desinstalarlo, y eso borra sus datos (como las partidas). Guarda una copia antes."))
                    .setPositiveButton(L.t("Firmar"), (d, w) -> signApk(e))
                    .setNeutralButton(L.t("Llave…"), (d, w) -> signKeyMenu())
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show());
        });
    }

    private void signApk(Entry e) {
        File in = new File(e.path);
        String name = e.name, base = name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") ? name.substring(0, name.length() - 4) : name;
        File dir = in.getParentFile();
        File out = new File(dir, base + "_firmado.apk");
        for (int i = 1; out.exists(); i++) out = new File(dir, base + "_firmado (" + i + ").apk");
        File target = out, tmp = new File(dir, "." + out.getName() + ".boveda-parte");
        AlertDialog wait = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Firmando…"))
                .setMessage(name + "\n\n" + L.t("Los APK grandes pueden tardar un poco."))
                .setCancelable(false).show();
        io.execute(() -> {
            String err = null, fp = null;
            try {
                ApkSign.Signer s = ApkSign.signer(this);
                ApkSign.sign(s, in, tmp);
                String bad = ApkSign.verify(tmp);
                if (bad != null) throw new Exception(bad);
                if (!tmp.renameTo(target)) throw new IOException(L.t("No se pudo guardar el APK firmado"));
                fp = ApkSign.fingerprint(s.chain.get(0));
            } catch (Throwable ex) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            String fe = err, ffp = fp;
            main.post(() -> {
                wait.dismiss();
                if (path != null) load(path, true);
                if (fe != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(L.t("No se pudo firmar"))
                            .setMessage(fe)
                            .setPositiveButton(L.t("Entendido"), null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                        .setTitle(L.t("APK firmado ✓"))
                        .setMessage(target.getName() + "\n\n" + L.t("Huella SHA-256 de la firma:") + "\n" + ffp)
                        .setPositiveButton(L.t("Instalar"), (d, w) -> openWith(new Entry(target.getPath(), target.getName(), false, target.length(), target.lastModified()), false))
                        .setNegativeButton(L.t("Listo"), null)
                        .show();
            });
        });
    }

    private void signKeyMenu() {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (ApkSign.usingCustom(this)) {
            labels.add(L.t("Volver a la llave de Bóveda"));
            acts.add(() -> {
                ApkSign.useOwn(this);
                Toast.makeText(this, L.t("Ahora firmas con la llave de Bóveda"), Toast.LENGTH_SHORT).show();
            });
        }
        labels.add(L.t("Guardar una copia de la llave de Bóveda (.p12)"));
        acts.add(() -> io.execute(() -> {
            String msg;
            try {
                File f = ApkSign.exportOwn(this, new File(Paths.ROOT, "Download"));
                msg = L.t("Guardada en Descargas: ") + f.getName() + "\n\n" + L.t("Contraseña: ") + ApkSign.ownPassword() + "\n\n"
                        + L.t("Guárdala bien: si algún día reinstalas Bóveda, con ella puedes seguir firmando igual y actualizar tus APK sin desinstalarlos.");
            } catch (Exception ex) {
                msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            String fm = msg;
            main.post(() -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("Llave de firma"))
                    .setMessage(fm)
                    .setPositiveButton(L.t("Entendido"), null)
                    .show());
        }));
        labels.add(L.t("Usar una llave mía"));
        acts.add(() -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Usar una llave mía"))
                .setMessage(L.t("Busca tu llave (.p12, .pfx o .bks), mantenla presionada y elige «Usar como llave de firma»."))
                .setPositiveButton(L.t("Entendido"), null)
                .show());
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Llave de firma"))
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void askImportKey(Entry e) {
        clearSelection();
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.addView(Ui.text(this, L.t("Escribe la contraseña de la llave. Desde ahora Bóveda firmará los APK con ella."), 13, Ui.SUB, false));
        EditText pass = input(L.t("Contraseña"), "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pass, topMargin(dp(12)));
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(e.name)
                .setView(box)
                .setPositiveButton(L.t("Usar esta llave"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = pass.getText().toString();
            v.setEnabled(false);
            io.execute(() -> {
                String err = null, alias = null;
                try {
                    alias = ApkSign.importKey(this, new File(e.path), p);
                } catch (Exception ex) {
                    err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                String fe = err, fa = alias;
                main.post(() -> {
                    v.setEnabled(true);
                    if (fe != null) {
                        pass.setError(fe);
                        return;
                    }
                    d.dismiss();
                    Toast.makeText(this, L.t("Listo: ahora firmas con tu llave «") + fa + "»", Toast.LENGTH_LONG).show();
                });
            });
        }));
        d.show();
    }

    // ------------------------------------------------------------------ Radiografía del almacenamiento

    private static final int[] CAT_COLORS = {0xFF4C9DFF, 0xFFFF5D8F, 0xFFB58CFF, 0xFFFFC14D, 0xFF35D08A, 0xFFFF8A5C, 0xFF8B97AB, 0xFF4A5670, 0xFF9B6BFF};
    private static final int SYSTEM_COLOR = 0xFF2A3346;
    private android.app.Dialog analyzer;

    private String catName(int c) {
        return switch (c) {
            case StorageScan.IMAGES -> L.t("Fotos e imágenes");
            case StorageScan.VIDEOS -> L.t("Videos");
            case StorageScan.AUDIO -> L.t("Música y audio");
            case StorageScan.DOCS -> L.t("Documentos");
            case StorageScan.APKS -> L.t("Instaladores (APK)");
            case StorageScan.ARCHIVES -> L.t("Comprimidos e imágenes de disco");
            case StorageScan.TRASH -> L.t("Papelera");
            case StorageScan.VAULT -> L.t("La Bóveda");
            default -> L.t("Otros archivos");
        };
    }

    /** Una pantalla completa con encabezado: devuelve {diálogo, caja de contenido, subtítulo}. */
    private Object[] fullScreen(String titleText) {
        android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(8), dp(10), dp(12), dp(6));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> dlg.dismiss()));
        LinearLayout ht = Ui.column(this);
        ht.setPadding(dp(6), 0, 0, 0);
        TextView tt = Ui.text(this, titleText, 20, Ui.TEXT, true);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        ht.addView(tt);
        TextView sub = Ui.text(this, "", 12.5f, Ui.SUB, false);
        ht.addView(sub);
        head.addView(ht, Ui.weight(1));
        root.addView(head);
        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(14), dp(4), dp(14), dp(40));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        dlg.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        if (dlg.getWindow() != null) {
            dlg.getWindow().setStatusBarColor(Ui.BG);
            dlg.getWindow().setNavigationBarColor(Ui.BG);
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return new Object[]{dlg, box, sub};
    }

    private void showAnalyzer() {
        Object[] fs = fullScreen(L.t("Radiografía del almacenamiento"));
        android.app.Dialog dlg = (android.app.Dialog) fs[0];
        LinearLayout box = (LinearLayout) fs[1];
        TextView sub = (TextView) fs[2];
        analyzer = dlg;
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        dlg.setOnDismissListener(x -> {
            cancel.set(true);
            if (analyzer == dlg) analyzer = null;
        });
        dlg.show();
        runAnalysis(box, sub, cancel);
    }

    private void runAnalysis(LinearLayout box, TextView sub, java.util.concurrent.atomic.AtomicBoolean cancel) {
        box.removeAllViews();
        sub.setText(L.t("Analizando…"));
        LinearLayout prog = Ui.column(this);
        prog.setPadding(dp(16), dp(16), dp(16), dp(16));
        prog.setBackground(Ui.gradient(dp(20), 0xFF1A2140, 0xFF121828));
        TextView pt = Ui.text(this, L.t("Preparando…"), 15, Ui.TEXT, true);
        TextView pw = Ui.text(this, "", 12, Ui.SUB, false);
        pw.setSingleLine(true);
        pw.setEllipsize(TextUtils.TruncateAt.START);
        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        prog.addView(pt);
        prog.addView(pw, topMargin(dp(4)));
        prog.addView(bar, topMargin(dp(10)));
        box.addView(prog, new LinearLayout.LayoutParams(-1, -2));
        long t0 = System.currentTimeMillis();
        List<Volume> vols = volumes();
        new Thread(() -> {
            for (Volume v : vols) {
                if (cancel.get()) return;
                File f = new File(v.path);
                long total = f.getTotalSpace(), free = f.getUsableSpace(), used = Math.max(1, total - free);
                StorageScan.Result r = StorageScan.scan(v.path, v.name, total, free, cancel, (files, bytes, where, dup) -> main.post(() -> {
                    if (dup) {
                        pt.setText(L.t("Buscando archivos repetidos en ") + v.name + "…");
                        pw.setText(files + L.t(" de ") + where);
                        int n = Integer.parseInt(where);
                        bar.setProgress(n > 0 ? files * 1000 / n : 0);
                    } else {
                        pt.setText(L.t("Analizando ") + v.name + "…  " + String.format(java.util.Locale.ROOT, "%,d", files) + L.t(" archivos · ") + Ui.size(bytes));
                        pw.setText(where);
                        bar.setProgress((int) Math.min(1000, bytes * 1000 / used));
                    }
                }));
                if (cancel.get()) return;
                main.post(() -> box.addView(resultCard(r), box.getChildCount() - 1));
            }
            main.post(() -> {
                box.removeView(prog);
                long secs = Math.max(1, (System.currentTimeMillis() - t0) / 1000);
                sub.setText(L.t("Listo · analizado en ") + Ui.eta(secs));
                TextView again = Ui.text(this, L.t("Volver a analizar"), 14, Ui.ACCENT2, true);
                again.setGravity(Gravity.CENTER);
                again.setPadding(0, dp(13), 0, dp(13));
                again.setBackground(Ui.rippleRound(Ui.SURFACE, dp(14)));
                again.setOnClickListener(x -> runAnalysis(box, sub, cancel));
                box.addView(again, topMargin(dp(6)));
            });
        }, "radiografia").start();
    }

    private View resultCard(StorageScan.Result r) {
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(Ui.gradient(dp(20), 0xFF14284F, 0xFF101A30));
        LinearLayout hr = Ui.row(this);
        hr.addView(Ui.text(this, r.name, 17, Ui.TEXT, true), Ui.weight(1));
        hr.addView(Ui.text(this, Ui.size(r.used()) + " / " + Ui.size(r.total), 13, Ui.SUB, false));
        card.addView(hr);
        TextView fr = Ui.text(this, L.t("Libre: ") + Ui.size(r.free) + "  ·  " + String.format(java.util.Locale.ROOT, "%,d", r.files) + L.t(" archivos en ")
                + String.format(java.util.Locale.ROOT, "%,d", r.dirs) + L.t(" carpetas"), 12, Ui.SUB, false);
        card.addView(fr, topMargin(dp(2)));

        // barra de colores: cada tipo su pedazo, y lo que falta es el espacio libre
        LinearLayout segs = Ui.row(this);
        segs.setBackground(Ui.round(0xFF0B1222, dp(7)));
        segs.setClipToOutline(true);
        float total = Math.max(1, r.total);
        for (int c = 0; c < StorageScan.CATS; c++) {
            if (r.cats[c] <= 0) continue;
            View s = new View(this);
            s.setBackgroundColor(CAT_COLORS[c]);
            segs.addView(s, new LinearLayout.LayoutParams(0, -1, r.cats[c] / total));
        }
        if (r.system() > 0) {
            View s = new View(this);
            s.setBackgroundColor(SYSTEM_COLOR);
            segs.addView(s, new LinearLayout.LayoutParams(0, -1, r.system() / total));
        }
        View rest = new View(this);
        segs.addView(rest, new LinearLayout.LayoutParams(0, -1, Math.max(0.0001f, r.free / total)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(14));
        sl.setMargins(0, dp(12), 0, dp(8));
        card.addView(segs, sl);

        // leyenda, de lo que más ocupa a lo que menos
        List<Integer> order = new ArrayList<>();
        for (int c = 0; c < StorageScan.CATS; c++) if (r.cats[c] > 0) order.add(c);
        order.add(-1);
        order.sort((a, b) -> Long.compare(b < 0 ? r.system() : r.cats[b], a < 0 ? r.system() : r.cats[a]));
        for (int c : order) {
            long size = c < 0 ? r.system() : r.cats[c];
            if (size <= 0) continue;
            LinearLayout row = Ui.row(this);
            row.setPadding(0, dp(5), 0, dp(5));
            View dot = new View(this);
            dot.setBackground(Ui.round(c < 0 ? 0xFF5B6680 : CAT_COLORS[c], dp(5)));
            row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            LinearLayout tx = Ui.column(this);
            tx.setPadding(dp(10), 0, 0, 0);
            tx.addView(Ui.text(this, c < 0 ? L.t("Sistema y apps") : catName(c), 14, Ui.TEXT, false));
            if (c >= 0) tx.addView(Ui.text(this, String.format(java.util.Locale.ROOT, "%,d", r.catFiles[c]) + L.t(" archivos"), 11.5f, Ui.SUB, false));
            else tx.addView(Ui.text(this, L.t("Android, las apps instaladas y sus datos"), 11.5f, Ui.SUB, false));
            row.addView(tx, Ui.weight(1));
            row.addView(Ui.text(this, Ui.size(size) + "  ·  " + Math.round(size * 100.0 / total) + "%", 13, Ui.SUB, true));
            if (c == StorageScan.TRASH) row.setOnClickListener(v -> showTrash());
            else if (c == StorageScan.VAULT) row.setOnClickListener(v -> {
                if (analyzer != null) analyzer.dismiss();
                openVault();
            });
            card.addView(row);
        }
        if (r.skippedAndroidData) {
            TextView n = Ui.text(this, L.t("Android/data y Android/obb están protegidas por Android y cuentan dentro de «Sistema y apps»."), 11.5f, 0xFF6E7A8E, false);
            card.addView(n, topMargin(dp(6)));
        }

        // lo que se puede limpiar
        card.addView(findingRow("🏆", L.t("Los archivos más grandes"), r.biggest.size() + L.t(" archivos"),
                v -> fileList(L.t("Los archivos más grandes") + " · " + r.name, r.biggest, null, null)), topMargin(dp(12)));
        List<File> topFolders = new ArrayList<>();
        java.util.Map<File, Long> fsz = new java.util.HashMap<>();
        for (long[] fsize : r.folderSizes) {
            if (topFolders.size() >= 15) break;
            File f = r.folders.get((int) fsize[0]);
            if (f.getName().equals(Trash.NAME) || f.getPath().equals(Vault.DIR)) continue;
            topFolders.add(f);
            fsz.put(f, fsize[1]);
        }
        card.addView(findingRow("📁", L.t("Las carpetas que más pesan"), topFolders.isEmpty() ? "" : topFolders.get(0).getName() + " · " + Ui.size(fsz.get(topFolders.get(0))),
                v -> folderList(r.name, topFolders, fsz)));
        card.addView(findingRow("♊", L.t("Archivos repetidos"), r.duplicates.isEmpty() ? L.t("Ninguno") :
                r.duplicates.size() + L.t(" grupos · puedes liberar ") + Ui.size(r.duplicateWaste), v -> duplicateList(r)));
        card.addView(findingRow("📥", L.t("Descargas de hace más de 3 meses"), r.oldDownloads.isEmpty() ? L.t("Ninguna") :
                r.oldDownloads.size() + L.t(" archivos · ") + Ui.size(r.oldDownloadsSize),
                v -> fileList(L.t("Descargas viejas"), r.oldDownloads, L.t("Mandar todas a la papelera"), r.oldDownloads)));
        card.addView(findingRow("📂", L.t("Carpetas vacías"), r.emptyDirs.isEmpty() ? L.t("Ninguna") : r.emptyDirs.size() + L.t(" carpetas"),
                v -> emptyDirList(r.emptyDirs)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cl);
        return card;
    }

    private View findingRow(String emoji, String name, String detail, View.OnClickListener tap) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(12), dp(10), dp(12), dp(10));
        r.setBackground(Ui.rippleRound(0x33000000, dp(14)));
        TextView e = Ui.text(this, emoji, 18, Ui.TEXT, false);
        r.addView(e, new LinearLayout.LayoutParams(dp(32), -2));
        LinearLayout tx = Ui.column(this);
        tx.addView(Ui.text(this, name, 14, Ui.TEXT, true));
        if (!detail.isEmpty()) tx.addView(Ui.text(this, detail, 12, Ui.SUB, false));
        r.addView(tx, Ui.weight(1));
        ImageView ch = new ImageView(this);
        ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, 0xFF4A5670));
        r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
        r.setOnClickListener(tap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, 0);
        r.setLayoutParams(lp);
        return r;
    }

    /** Cierra la Radiografía y abre la carpeta. */
    private void goToFolder(String dir, android.app.Dialog list) {
        if (list != null) list.dismiss();
        if (analyzer != null) analyzer.dismiss();
        open(dir);
    }

    private View fileRow(File f, android.app.Dialog dlg, LinearLayout box) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(12), dp(10), dp(12), dp(10));
        r.setBackground(Ui.rippleRound(Ui.SURFACE, dp(16)));
        ImageView ic = new ImageView(this);
        int cat = StorageScan.category(f.getName());
        ic.setImageDrawable(Ui.icon(this, f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file, f.isDirectory() ? Ui.WARN : CAT_COLORS[cat]));
        r.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout tx = Ui.column(this);
        tx.setPadding(dp(12), 0, 0, 0);
        TextView n = Ui.text(this, f.getName(), 14.5f, Ui.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tx.addView(n);
        TextView w = Ui.text(this, f.getParent().replace(Paths.ROOT, L.t("Interno")), 12, Ui.SUB, false);
        w.setSingleLine(true);
        w.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tx.addView(w);
        r.addView(tx, Ui.weight(1));
        if (!f.isDirectory()) r.addView(Ui.text(this, Ui.size(f.length()), 13, Ui.SUB, true));
        r.setOnClickListener(v -> {
            List<String> labels = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            labels.add(L.t("Ir a su carpeta"));
            acts.add(() -> goToFolder(f.getParent(), dlg));
            if (!f.isDirectory()) {
                labels.add(L.t("Abrir"));
                acts.add(() -> openFile(new Entry(f.getPath(), f.getName(), false, f.length(), f.lastModified())));
            }
            if (Trash.supported(f.getPath())) {
                labels.add(L.t("A la papelera"));
                acts.add(() -> io.execute(() -> {
                    String err = null;
                    try {
                        Trash.put(f.getPath());
                    } catch (IOException e) {
                        err = e.getMessage();
                    }
                    String fe = err;
                    main.post(() -> {
                        if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                        else {
                            box.removeView(r);
                            Toast.makeText(this, L.t("Se movió a la papelera"), Toast.LENGTH_SHORT).show();
                        }
                    });
                }));
            }
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(f.getName())
                    .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                    .show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lp);
        return r;
    }

    /** Pone un botón grande arriba de una lista (por ejemplo, «Mandar todo a la papelera»). */
    private TextView bigButton(String text, int color) {
        TextView b = Ui.text(this, text, 14, color, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, dp(12), 0, dp(12));
        b.setBackground(Ui.rippleRound(Ui.SURFACE2, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private void trashAll(List<File> files, Runnable done) {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(files.size() + L.t(" archivos a la papelera"))
                .setMessage(L.t("Se pueden restaurar durante 30 días desde la papelera."))
                .setPositiveButton(L.t("A la papelera"), (d, w) -> io.execute(() -> {
                    int ok = 0;
                    for (File f : files) {
                        try {
                            Trash.put(f.getPath());
                            ok++;
                        } catch (IOException ignored) {
                        }
                    }
                    int fok = ok;
                    main.post(() -> {
                        Toast.makeText(this, fok + L.t(" elementos se movieron a la papelera"), Toast.LENGTH_SHORT).show();
                        done.run();
                    });
                }))
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
    }

    private void fileList(String titleText, List<File> files, String allLabel, List<File> all) {
        Object[] fs = fullScreen(titleText);
        android.app.Dialog dlg = (android.app.Dialog) fs[0];
        LinearLayout box = (LinearLayout) fs[1];
        long total = 0;
        for (File f : files) total += f.length();
        ((TextView) fs[2]).setText(files.size() + L.t(" archivos · ") + Ui.size(total));
        if (allLabel != null && !all.isEmpty()) {
            TextView b = bigButton(allLabel, Ui.WARN);
            b.setOnClickListener(v -> trashAll(all, dlg::dismiss));
            box.addView(b);
        }
        if (files.isEmpty()) box.addView(Ui.text(this, L.t("No hay nada aquí 🎉"), 14, Ui.SUB, false));
        for (File f : files) box.addView(fileRow(f, dlg, box));
        dlg.show();
    }

    private void folderList(String volName, List<File> folders, java.util.Map<File, Long> sizes) {
        Object[] fs = fullScreen(L.t("Las carpetas que más pesan") + " · " + volName);
        android.app.Dialog dlg = (android.app.Dialog) fs[0];
        LinearLayout box = (LinearLayout) fs[1];
        ((TextView) fs[2]).setText(L.t("Toca una para entrar"));
        long max = folders.isEmpty() ? 1 : Math.max(1, sizes.get(folders.get(0)));
        for (File f : folders) {
            LinearLayout r = Ui.column(this);
            r.setPadding(dp(14), dp(12), dp(14), dp(12));
            r.setBackground(Ui.rippleRound(Ui.SURFACE, dp(16)));
            LinearLayout top = Ui.row(this);
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(Ui.icon(this, R.drawable.ic_folder, Ui.WARN));
            top.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView n = Ui.text(this, f.getName(), 15, Ui.TEXT, true);
            n.setPadding(dp(10), 0, 0, 0);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            top.addView(n, Ui.weight(1));
            top.addView(Ui.text(this, Ui.size(sizes.get(f)), 13, Ui.SUB, true));
            r.addView(top);
            FrameLayout bb = new FrameLayout(this);
            bb.addView(new UsageBar(this, sizes.get(f) / (float) max));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
            bl.setMargins(0, dp(8), 0, 0);
            r.addView(bb, bl);
            r.setOnClickListener(v -> goToFolder(f.getPath(), dlg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(6));
            box.addView(r, lp);
        }
        dlg.show();
    }

    private void duplicateList(StorageScan.Result r) {
        Object[] fs = fullScreen(L.t("Archivos repetidos") + " · " + r.name);
        android.app.Dialog dlg = (android.app.Dialog) fs[0];
        LinearLayout box = (LinearLayout) fs[1];
        ((TextView) fs[2]).setText(r.duplicates.size() + L.t(" grupos · puedes liberar ") + Ui.size(r.duplicateWaste));
        if (r.duplicates.isEmpty()) {
            box.addView(Ui.text(this, L.t("No hay nada aquí 🎉"), 14, Ui.SUB, false));
        } else {
            TextView note = Ui.text(this, L.t("Son archivos con exactamente el mismo contenido. En cada grupo el primero es el más viejo (normalmente el original)."), 12.5f, Ui.SUB, false);
            note.setPadding(dp(4), 0, dp(4), dp(10));
            box.addView(note);
            List<File> copies = new ArrayList<>();
            for (List<File> g : r.duplicates) for (int i = 1; i < g.size(); i++) if (Trash.supported(g.get(i).getPath())) copies.add(g.get(i));
            if (!copies.isEmpty()) {
                TextView b = bigButton(L.t("Dejar el original y mandar las copias a la papelera"), Ui.WARN);
                b.setOnClickListener(v -> trashAll(copies, dlg::dismiss));
                box.addView(b);
            }
        }
        for (List<File> g : r.duplicates) {
            TextView h = Ui.text(this, g.size() + L.t(" iguales · ") + Ui.size(g.get(0).length()) + L.t(" cada uno"), 12, 0xFF6F86AE, true);
            h.setPadding(dp(6), dp(10), 0, dp(6));
            box.addView(h);
            for (File f : g) box.addView(fileRow(f, dlg, box));
        }
        dlg.show();
    }

    private void emptyDirList(List<File> dirs) {
        Object[] fs = fullScreen(L.t("Carpetas vacías"));
        android.app.Dialog dlg = (android.app.Dialog) fs[0];
        LinearLayout box = (LinearLayout) fs[1];
        ((TextView) fs[2]).setText(dirs.size() + L.t(" carpetas"));
        if (dirs.isEmpty()) box.addView(Ui.text(this, L.t("No hay nada aquí 🎉"), 14, Ui.SUB, false));
        else {
            TextView b = bigButton(L.t("Borrar todas las carpetas vacías"), Ui.WARN);
            b.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("¿Borrar ") + dirs.size() + L.t(" carpetas vacías?"))
                    .setMessage(L.t("No tienen nada adentro, así que no se pierde ningún archivo."))
                    .setPositiveButton(L.t("Borrar"), (d, w) -> io.execute(() -> {
                        int ok = 0;
                        for (File f : dirs) {
                            String[] kids = f.list();
                            if (kids != null && kids.length == 0 && f.delete()) ok++;
                        }
                        int fok = ok;
                        main.post(() -> {
                            Toast.makeText(this, fok + L.t(" carpetas vacías borradas"), Toast.LENGTH_SHORT).show();
                            dlg.dismiss();
                        });
                    }))
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show());
            box.addView(b);
        }
        for (File f : dirs) box.addView(fileRow(f, dlg, box));
        dlg.show();
    }

    // ------------------------------------------------------------------ papelera

    private void moveToTrash(List<String> src) {
        clearSelection();
        io.execute(() -> {
            int ok = 0;
            List<String> failed = new ArrayList<>();
            for (String s : src) {
                try {
                    Trash.put(s);
                    ok++;
                } catch (IOException e) {
                    failed.add(s);
                }
            }
            int fok = ok;
            main.post(() -> {
                if (path != null) load(path, true);
                if (fok > 0) Toast.makeText(this, fok == 1 ? L.t("Se movió a la papelera") : fok + L.t(" elementos se movieron a la papelera"), Toast.LENGTH_SHORT).show();
                if (!failed.isEmpty()) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(failed.size() == 1 ? "«" + Paths.name(failed.get(0)) + L.t("» no pudo ir a la papelera") : failed.size() + L.t(" elementos no pudieron ir a la papelera"))
                            .setMessage(L.t("¿Borrarlos para siempre?"))
                            .setPositiveButton(L.t("Borrar para siempre"), (d, w) -> engine.submit(TransferEngine.Kind.DELETE, failed, null, TransferEngine.Conflict.SKIP))
                            .setNegativeButton(L.t("Cancelar"), null)
                            .show();
                }
            });
        });
    }

    private String trashSubtitle() {
        List<Trash.Item> items = Trash.list(this);
        if (items.isEmpty()) return L.t("Vacía · lo que borres se guarda 30 días");
        long total = 0;
        for (Trash.Item it : items) total += it.size;
        return items.size() + (items.size() == 1 ? L.t(" elemento") : L.t(" elementos")) + "  ·  " + Ui.size(total);
    }

    private void showTrash() {
        android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(8), dp(10), dp(12), dp(6));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> dlg.dismiss()));
        LinearLayout ht = Ui.column(this);
        ht.setPadding(dp(6), 0, 0, 0);
        ht.addView(Ui.text(this, L.t("Papelera"), 20, Ui.TEXT, true));
        TextView sub = Ui.text(this, "", 12.5f, Ui.SUB, false);
        ht.addView(sub);
        head.addView(ht, Ui.weight(1));
        root.addView(head);
        TextView note = Ui.text(this, L.t("Lo que borras se queda aquí 30 días y luego se borra solo. Toca algo para restaurarlo o borrarlo para siempre."), 13, Ui.SUB, false);
        note.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(note);
        LinearLayout actions = Ui.row(this);
        actions.setPadding(dp(14), 0, dp(14), dp(8));
        TextView restoreAll = Ui.text(this, L.t("Restaurar todo"), 14, Ui.ACCENT2, true);
        TextView empty = Ui.text(this, L.t("Vaciar papelera"), 14, Ui.BAD, true);
        for (TextView b : new TextView[]{restoreAll, empty}) {
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(11), 0, dp(11));
            b.setBackground(Ui.rippleRound(Ui.SURFACE, dp(14)));
        }
        LinearLayout.LayoutParams al = Ui.weight(1);
        al.setMargins(0, 0, dp(8), 0);
        actions.addView(restoreAll, al);
        actions.addView(empty, Ui.weight(1));
        root.addView(actions);
        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(14), 0, dp(14), dp(30));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> io.execute(() -> {
            List<Trash.Item> items = Trash.list(this);
            main.post(() -> {
                box.removeAllViews();
                long total = 0;
                for (Trash.Item it : items) total += it.size;
                sub.setText(items.isEmpty() ? L.t("Vacía") : items.size() + (items.size() == 1 ? L.t(" elemento") : L.t(" elementos")) + "  ·  " + Ui.size(total));
                actions.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                if (items.isEmpty()) {
                    TextView t = Ui.text(this, "🗑️\n\n" + L.t("La papelera está vacía"), 15, Ui.SUB, false);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(0, dp(80), 0, 0);
                    box.addView(t);
                }
                for (Trash.Item it : items) {
                    LinearLayout r = Ui.row(this);
                    r.setPadding(dp(12), dp(11), dp(12), dp(11));
                    r.setBackground(Ui.rippleRound(Ui.SURFACE, dp(16)));
                    ImageView ic = new ImageView(this);
                    ic.setImageDrawable(Ui.icon(this, it.dir ? R.drawable.ic_folder : R.drawable.ic_file, it.dir ? Ui.WARN : Ui.ACCENT2));
                    r.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
                    LinearLayout tx = Ui.column(this);
                    tx.setPadding(dp(12), 0, 0, 0);
                    TextView n = Ui.text(this, it.name(), 15, Ui.TEXT, true);
                    n.setSingleLine(true);
                    n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    tx.addView(n);
                    TextView w = Ui.text(this, Paths.parent(it.original).replace(Paths.ROOT, L.t("Interno")), 12, Ui.SUB, false);
                    w.setSingleLine(true);
                    w.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    tx.addView(w);
                    tx.addView(Ui.text(this, Ui.size(it.size) + "  ·  " + L.t("borrado ") + Ui.date(it.deleted) + "  ·  "
                            + (it.daysLeft() == 1 ? L.t("queda 1 día") : L.t("quedan ") + it.daysLeft() + L.t(" días")), 11.5f, 0xFF6E7A8E, false));
                    r.addView(tx, Ui.weight(1));
                    r.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(it.name())
                            .setItems(new String[]{L.t("Restaurar"), L.t("Borrar para siempre")}, (d, which) -> io.execute(() -> {
                                String err = null;
                                try {
                                    if (which == 0) Trash.restore(it);
                                    else Trash.deleteForever(it);
                                } catch (IOException e) {
                                    err = e.getMessage();
                                }
                                String fe = err;
                                main.post(() -> {
                                    if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                                    else Toast.makeText(this, which == 0 ? L.t("Restaurado") : L.t("Borrado para siempre"), Toast.LENGTH_SHORT).show();
                                    refresh[0].run();
                                });
                            }))
                            .show());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.setMargins(0, 0, 0, dp(6));
                    box.addView(r, lp);
                }
                restoreAll.setOnClickListener(v -> io.execute(() -> {
                    int fails = 0;
                    for (Trash.Item it : items) {
                        try {
                            Trash.restore(it);
                        } catch (IOException e) {
                            fails++;
                        }
                    }
                    int ff = fails;
                    main.post(() -> {
                        Toast.makeText(this, ff == 0 ? L.t("Todo restaurado") : ff + L.t(" no se pudieron restaurar"), Toast.LENGTH_SHORT).show();
                        refresh[0].run();
                    });
                }));
                empty.setOnClickListener(v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                        .setTitle(L.t("¿Vaciar la papelera?"))
                        .setMessage(L.t("Todo lo que hay aquí se borra para siempre."))
                        .setPositiveButton(L.t("Vaciar"), (d, w) -> io.execute(() -> {
                            for (Trash.Item it : items) {
                                try {
                                    Trash.deleteForever(it);
                                } catch (IOException ignored) {
                                }
                            }
                            main.post(refresh[0]);
                        }))
                        .setNegativeButton(L.t("Cancelar"), null)
                        .show());
            });
        });
        refresh[0].run();
        dlg.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        dlg.setOnDismissListener(x -> {
            if (path == null) showHome();
            else load(path, true);
        });
        if (dlg.getWindow() != null) {
            dlg.getWindow().setStatusBarColor(Ui.BG);
            dlg.getWindow().setNavigationBarColor(Ui.BG);
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dlg.show();
    }

    // ------------------------------------------------------------------ la Bóveda (carpeta secreta cifrada)

    private void openVault() {
        withVault(() -> open(VaultFs.ROOT));
    }

    /** Hace lo que sigue con la Bóveda abierta: la crea si no existe o pide el PIN o la huella. */
    private void withVault(Runnable then) {
        if (Vault.unlocked()) then.run();
        else if (!Vault.exists()) createVault(then);
        else unlockVault(then);
    }

    private EditText pinInput(String hint) {
        EditText e = input(hint, "", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});
        return e;
    }

    private void createVault(Runnable then) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        box.addView(Ui.text(this, L.t("Lo que guardes aquí queda cifrado con AES-256. Ninguna otra app, ni la galería, ni un computador conectado al celular puede verlo.")
                + "\n\n" + L.t("Elige un PIN de 4 a 12 números. ¡No lo olvides! Si lo olvidas, nadie puede recuperar los archivos, ni siquiera nosotros."), 13, Ui.SUB, false));
        EditText pin = pinInput(L.t("PIN nuevo"));
        EditText again = pinInput(L.t("Repite el PIN"));
        box.addView(pin, topMargin(dp(12)));
        box.addView(again, topMargin(dp(8)));
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Crear tu Bóveda"))
                .setView(box)
                .setPositiveButton(L.t("Crear"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = pin.getText().toString();
            if (p.length() < 4) {
                pin.setError(L.t("El PIN debe tener al menos 4 números"));
                return;
            }
            if (!p.equals(again.getText().toString())) {
                again.setError(L.t("Los PIN no coinciden"));
                return;
            }
            d.dismiss();
            AlertDialog wait = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("Creando tu Bóveda…")).setCancelable(false).show();
            new Thread(() -> {
                String err = null;
                try {
                    Vault.create(this, p);
                } catch (Exception e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                String fe = err;
                main.post(() -> {
                    wait.dismiss();
                    if (fe != null) {
                        Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (biometricAvailable()) {
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                                .setTitle(L.t("¿Abrir también con la huella?"))
                                .setMessage(L.t("Así no tienes que escribir el PIN cada vez. El PIN sigue sirviendo."))
                                .setPositiveButton(L.t("Usar la huella"), (dd, w) -> enableVaultBiometric(p, then))
                                .setNegativeButton(L.t("Solo el PIN"), (dd, w) -> then.run())
                                .setCancelable(false)
                                .show();
                    } else {
                        then.run();
                    }
                });
            }).start();
        }));
        d.show();
    }

    private boolean biometricAvailable() {
        android.hardware.biometrics.BiometricManager bm = getSystemService(android.hardware.biometrics.BiometricManager.class);
        if (bm == null) return false;
        int r = Build.VERSION.SDK_INT >= 30
                ? bm.canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                : bm.canAuthenticate();
        return r == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void biometricPrompt(String titleText, javax.crypto.Cipher cipher, java.util.function.Consumer<javax.crypto.Cipher> ok, Runnable usePin) {
        android.hardware.biometrics.BiometricPrompt.Builder b = new android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle(titleText)
                .setSubtitle(L.t("Pon tu huella"))
                .setNegativeButton(L.t("Usar el PIN"), getMainExecutor(), (dd, w) -> usePin.run());
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG);
        b.build().authenticate(new android.hardware.biometrics.BiometricPrompt.CryptoObject(cipher), new android.os.CancellationSignal(), getMainExecutor(),
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                        ok.accept(r.getCryptoObject().getCipher());
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        if (code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT
                                || code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            usePin.run();
                        }
                    }
                });
    }

    private void enableVaultBiometric(String pin, Runnable then) {
        try {
            javax.crypto.Cipher c = Vault.bioEncryptCipher();
            biometricPrompt(L.t("Activar la huella"), c, authed -> {
                try {
                    if (Vault.enableBiometric(this, authed, pin)) Toast.makeText(this, L.t("Listo: la Bóveda también se abre con tu huella"), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, L.t("No se pudo activar la huella"), Toast.LENGTH_SHORT).show();
                }
                if (then != null) then.run();
            }, () -> {
                if (then != null) then.run();
            });
        } catch (Exception e) {
            Toast.makeText(this, L.t("No se pudo activar la huella"), Toast.LENGTH_SHORT).show();
            if (then != null) then.run();
        }
    }

    private void unlockVault(Runnable then) {
        javax.crypto.Cipher c = Vault.biometricOn(this) ? Vault.bioDecryptCipher(this) : null;
        if (c != null && biometricAvailable()) {
            biometricPrompt(L.t("Abrir la Bóveda"), c, authed -> {
                if (Vault.unlockWithBiometric(this, authed)) then.run();
                else askVaultPin(then);
            }, () -> askVaultPin(then));
        } else {
            askVaultPin(then);
        }
    }

    private void askVaultPin(Runnable then) {
        long wait = Vault.waitSeconds(this);
        if (wait > 0) {
            Toast.makeText(this, L.t("Demasiados intentos. Espera ") + wait + L.t(" segundos"), Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        boolean last = Vault.autoWipe(this) && Vault.fails(this) >= Vault.WIPE_AFTER - 1;
        if (last) {
            TextView warn = Ui.text(this, L.t("⚠️ ÚLTIMO INTENTO. Si el PIN es incorrecto, todo lo que hay en La Bóveda se borrará para siempre."), 14, Ui.BAD, true);
            warn.setPadding(0, 0, 0, dp(10));
            box.addView(warn);
        }
        EditText pin = pinInput("PIN");
        box.addView(pin);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Abrir la Bóveda"))
                .setView(box)
                .setPositiveButton(L.t("Abrir"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = pin.getText().toString();
            if (p.length() < 4) {
                pin.setError(L.t("El PIN debe tener al menos 4 números"));
                return;
            }
            v.setEnabled(false);
            new Thread(() -> {
                boolean ok;
                try {
                    ok = Vault.unlockWithPin(this, p);
                } catch (Exception e) {
                    ok = false;
                }
                boolean fok = ok;
                main.post(() -> {
                    v.setEnabled(true);
                    if (fok) {
                        d.dismiss();
                        then.run();
                        return;
                    }
                    if (Vault.justWiped()) {
                        d.dismiss();
                        if (path != null && VaultFs.isVault(path)) showHome();
                        else if (path == null) showHome();
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                                .setTitle(L.t("La Bóveda se borró"))
                                .setMessage(L.t("Se equivocaron de PIN 7 veces seguidas y el autoborrado estaba encendido: todo lo que había en La Bóveda se borró para siempre."))
                                .setPositiveButton(L.t("Entendido"), null)
                                .show();
                        return;
                    }
                    if (Vault.autoWipe(this) && Vault.fails(this) >= Vault.WIPE_AFTER - 1) {
                        // el próximo es el último: se vuelve a abrir con el aviso en rojo
                        d.dismiss();
                        askVaultPin(then);
                        return;
                    }
                    long w = Vault.waitSeconds(this);
                    pin.setText("");
                    int left = Vault.WIPE_AFTER - Vault.fails(this);
                    pin.setError(w > 0 ? L.t("Demasiados intentos. Espera ") + w + L.t(" segundos")
                            : Vault.autoWipe(this) ? L.t("PIN incorrecto") + " · " + L.t("quedan ") + left + L.t(" intentos") : L.t("PIN incorrecto"));
                    if (w > 0) d.dismiss();
                });
            }).start();
        }));
        d.show();
        pin.requestFocus();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void vaultMenu() {
        if (!Vault.exists()) {
            createVault(() -> open(VaultFs.ROOT));
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (Vault.unlocked()) {
            labels.add(L.t("Cerrar la Bóveda ahora"));
            acts.add(() -> {
                Vault.lock();
                if (path != null && VaultFs.isVault(path)) showHome();
                Toast.makeText(this, L.t("Bóveda cerrada"), Toast.LENGTH_SHORT).show();
            });
        }
        labels.add(L.t("Cambiar el PIN"));
        acts.add(this::changeVaultPin);
        if (Vault.biometricOn(this)) {
            labels.add(L.t("Dejar de usar la huella"));
            acts.add(() -> {
                Vault.forgetBiometric(this);
                Toast.makeText(this, L.t("Ahora la Bóveda se abre solo con el PIN"), Toast.LENGTH_SHORT).show();
            });
        } else if (biometricAvailable()) {
            labels.add(L.t("Abrir también con la huella"));
            acts.add(() -> askPinThen(L.t("Activar la huella"), p -> enableVaultBiometric(p, null)));
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("La Bóveda"))
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    /** Pide el PIN actual, lo revisa y sigue con él. */
    private void askPinThen(String titleText, java.util.function.Consumer<String> then) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText pin = pinInput(L.t("PIN actual"));
        box.addView(pin);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(titleText)
                .setView(box)
                .setPositiveButton(L.t("Aceptar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p = pin.getText().toString();
            v.setEnabled(false);
            new Thread(() -> {
                boolean ok;
                try {
                    // revisa el PIN volviendo a guardar la llave con el mismo PIN
                    ok = Vault.changePin(p, p);
                } catch (Exception e) {
                    ok = false;
                }
                boolean fok = ok;
                main.post(() -> {
                    v.setEnabled(true);
                    if (!fok) {
                        pin.setText("");
                        pin.setError(L.t("PIN incorrecto"));
                        return;
                    }
                    d.dismiss();
                    then.accept(p);
                });
            }).start();
        }));
        d.show();
    }

    private void changeVaultPin() {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText old = pinInput(L.t("PIN actual"));
        EditText pin = pinInput(L.t("PIN nuevo"));
        EditText again = pinInput(L.t("Repite el PIN"));
        box.addView(old);
        box.addView(pin, topMargin(dp(8)));
        box.addView(again, topMargin(dp(8)));
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Cambiar el PIN"))
                .setView(box)
                .setPositiveButton(L.t("Guardar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String o = old.getText().toString(), p = pin.getText().toString();
            if (p.length() < 4) {
                pin.setError(L.t("El PIN debe tener al menos 4 números"));
                return;
            }
            if (!p.equals(again.getText().toString())) {
                again.setError(L.t("Los PIN no coinciden"));
                return;
            }
            v.setEnabled(false);
            new Thread(() -> {
                boolean ok;
                try {
                    ok = Vault.changePin(o, p);
                } catch (Exception e) {
                    ok = false;
                }
                boolean fok = ok;
                main.post(() -> {
                    v.setEnabled(true);
                    if (!fok) {
                        old.setText("");
                        old.setError(L.t("PIN incorrecto"));
                        return;
                    }
                    d.dismiss();
                    Toast.makeText(this, L.t("PIN cambiado"), Toast.LENGTH_SHORT).show();
                });
            }).start();
        }));
        d.show();
    }

    /** Ajustes de La Bóveda: PIN, huella, cerrarla y el autoborrado. */
    private void vaultSettings(LinearLayout col, Runnable refresh) {
        settingsSection(col, L.t("La Bóveda"));
        settingsRow(col, L.t("Cambiar el PIN"), L.t("Pide el PIN actual y el nuevo"), this::changeVaultPin);
        if (biometricAvailable() || Vault.biometricOn(this)) {
            settingsSwitch(col, L.t("Abrir con la huella"), L.t("Además del PIN"), Vault.biometricOn(this), on -> {
                if (on) askPinThen(L.t("Activar la huella"), p -> enableVaultBiometric(p, refresh));
                else {
                    Vault.forgetBiometric(this);
                    Toast.makeText(this, L.t("Ahora la Bóveda se abre solo con el PIN"), Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (Vault.unlocked()) {
            settingsRow(col, L.t("Cerrar la Bóveda ahora"), L.t("Pide el PIN o la huella la próxima vez"), () -> {
                Vault.lock();
                Toast.makeText(this, L.t("Bóveda cerrada"), Toast.LENGTH_SHORT).show();
                refresh.run();
            });
        }
        settingsSection(col, L.t("Autoborrado"));
        LinearLayout r = settingsCard(L.t("Autoborrado"), "");
        TextView warn = Ui.text(this, L.t("Si alguien se equivoca de PIN ") + Vault.WIPE_AFTER
                + L.t(" veces seguidas, todo lo que hay en La Bóveda se borra para siempre. Antes del último intento sale un aviso."), 12.5f, Ui.BAD, false);
        warn.setPadding(0, dp(2), 0, 0);
        ((LinearLayout) r.getChildAt(0)).addView(warn);
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(Vault.autoWipe(this));
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.BAD, 0xFF3A4458}));
        sw.setOnCheckedChangeListener((b, on) -> {
            if (!on) {
                Vault.setAutoWipe(this, false);
                Toast.makeText(this, L.t("Autoborrado apagado"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (Vault.autoWipe(this)) return;
            sw.setChecked(false);
            // dos confirmaciones: esto puede borrar todo
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("¿Encender el autoborrado?"))
                    .setMessage(L.t("Si alguien se equivoca de PIN ") + Vault.WIPE_AFTER + L.t(" veces seguidas, todo lo que hay en La Bóveda se borra para siempre. Antes del último intento sale un aviso."))
                    .setPositiveButton(L.t("Continuar"), (d1, w1) -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(L.t("⚠️ ¿Estás seguro?"))
                            .setMessage(L.t("Nadie podrá recuperar los archivos borrados, ni tú ni nosotros. Si alguien más usa tu celular (por ejemplo, un niño), podría borrarlo todo sin querer."))
                            .setPositiveButton(L.t("Sí, encenderlo"), (d2, w2) -> {
                                Vault.setAutoWipe(this, true);
                                Toast.makeText(this, L.t("Autoborrado encendido"), Toast.LENGTH_SHORT).show();
                                refresh.run();
                            })
                            .setNegativeButton(L.t("Cancelar"), null)
                            .show())
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMargins(dp(10), 0, 0, 0);
        r.addView(sw, sl);
        r.setOnClickListener(v -> sw.toggle());
        addCard(col, r);
    }

    private void moveToVault(List<String> sel) {
        withVault(() -> {
            engine.submit(TransferEngine.Kind.MOVE, new ArrayList<>(sel), VaultFs.ROOT, TransferEngine.Conflict.KEEP_BOTH);
            clearSelection();
            Toast.makeText(this, L.t("Moviendo a la Bóveda…"), Toast.LENGTH_SHORT).show();
        });
    }

    // ------------------------------------------------------------------ MEGA

    private void openMega() {
        if (Mega.get(this).loggedIn()) open(MegaFs.ROOT);
        else showMegaLogin(null, null);
    }

    private void megaMenu() {
        Mega mg = Mega.get(this);
        if (!mg.loggedIn()) {
            showMegaLogin(null, null);
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle("MEGA · " + mg.email())
                .setItems(new String[]{L.t("Cerrar sesión")}, (d, w) -> new Thread(() -> {
                    mg.logout();
                    main.post(this::showHome);
                }).start())
                .show();
    }

    /** Correo y contraseña de MEGA; si la cuenta tiene verificación en dos pasos, pide el código después. */
    private void showMegaLogin(String email, String password) {
        boolean askCode = password != null;
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText mail = input(L.t("Correo de MEGA"), email == null ? Mega.get(this).email() : email,
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = input(L.t("Contraseña"), "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText code = input(L.t("Código de 6 números"), "", android.text.InputType.TYPE_CLASS_NUMBER);
        if (askCode) {
            box.addView(Ui.text(this, L.t("Tu cuenta tiene verificación en dos pasos. Escribe el código de tu app de autenticación."), 14, Ui.SUB, false));
            box.addView(code, topMargin(dp(12)));
        } else {
            box.addView(Ui.text(this, L.t("Entra con tu cuenta de MEGA. La contraseña no se guarda: solo se queda la sesión, cifrada en el celular. ")
                    + L.t("Todo se descifra aquí mismo, como en la app de MEGA."), 13, Ui.SUB, false));
            box.addView(mail, topMargin(dp(12)));
            box.addView(pass, topMargin(dp(8)));
        }
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(askCode ? L.t("Verificación de MEGA") : L.t("Iniciar sesión en MEGA"))
                .setView(box)
                .setPositiveButton(L.t("Entrar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String m = askCode ? email : mail.getText().toString().trim();
            String p = askCode ? password : pass.getText().toString();
            String c = askCode ? code.getText().toString().trim() : null;
            if (!askCode && (!m.contains("@") || p.isEmpty())) {
                (m.contains("@") ? pass : mail).setError(m.contains("@") ? L.t("Falta la contraseña") : L.t("Escribe tu correo"));
                return;
            }
            if (askCode && c.length() < 6) {
                code.setError(L.t("El código tiene 6 números"));
                return;
            }
            d.dismiss();
            AlertDialog wait = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("Entrando a MEGA…"))
                    .setMessage(L.t("Preparando las llaves de cifrado. Puede tardar unos segundos."))
                    .setCancelable(false).show();
            new Thread(() -> {
                String err = null;
                boolean needCode = false;
                try {
                    Mega.get(this).login(m, p, c);
                } catch (Mega.ApiError e) {
                    if (e.code == -26 && !askCode) needCode = true;
                    else err = e.getMessage();
                } catch (Exception e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                String fe = err;
                boolean fc = needCode;
                main.post(() -> {
                    wait.dismiss();
                    if (fc) {
                        showMegaLogin(m, p);
                    } else if (fe != null) {
                        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle(L.t("No se pudo entrar a MEGA"))
                                .setMessage(fe).setPositiveButton(L.t("Entendido"), null).show();
                    } else {
                        Toast.makeText(this, L.t("MEGA conectado"), Toast.LENGTH_SHORT).show();
                        open(MegaFs.ROOT);
                    }
                });
            }).start();
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
                .setItems(new String[]{L.t("Desconectar la cuenta"), L.t("Cambiar el ID de cliente")}, (d, w) -> {
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
        EditText id = input(L.t("ID de cliente (termina en .apps.googleusercontent.com)"), gd.clientId(), android.text.InputType.TYPE_CLASS_TEXT);
        EditText sec = input(L.t("Secreto del cliente"), "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!gd.configured()) {
            TextView guide = Ui.text(this, L.t("Google pide que cada app esté registrada. Se hace una sola vez, gratis (unos 5 minutos):\n\n")
                    + L.t("1. Entra a console.cloud.google.com con tu cuenta y crea un proyecto (por ejemplo «Bóveda»).\n")
                    + L.t("2. «APIs y servicios» → «Biblioteca»: busca «Google Drive API» y actívala.\n")
                    + L.t("3. «Pantalla de consentimiento de OAuth»: tipo Externo, nombre Bóveda y tu correo. ")
                    + L.t("En «Usuarios de prueba» agrega tu correo. Luego toca «Publicar app» (así no te pide entrar cada semana).\n")
                    + L.t("4. «Credenciales» → «Crear credenciales» → «ID de cliente de OAuth» → tipo «App de escritorio».\n")
                    + L.t("5. Copia aquí el ID de cliente y el secreto.\n\n")
                    + L.t("Al conectar, Google dirá «Google no verificó esta app»: es tu propia app, toca «Continuar»."), 13, Ui.SUB, false);
            guide.setTextIsSelectable(true);
            box.addView(guide);
            box.addView(id, topMargin(dp(12)));
            box.addView(sec, topMargin(dp(8)));
        } else {
            box.addView(Ui.text(this, L.t("Se abrirá el navegador para que elijas tu cuenta de Google y le des permiso a Bóveda."), 14, Ui.SUB, false));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Conectar Google Drive"))
                .setView(scroll)
                .setPositiveButton(L.t("Conectar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!gd.configured()) {
                String i = id.getText().toString().trim(), s2 = sec.getText().toString().trim();
                if (!i.endsWith(".apps.googleusercontent.com")) {
                    id.setError(L.t("Copia el ID de cliente completo"));
                    return;
                }
                if (s2.isEmpty()) {
                    sec.setError(L.t("Falta el secreto"));
                    return;
                }
                gd.setClient(i, s2);
            }
            d.dismiss();
            Toast.makeText(this, L.t("Elige tu cuenta en el navegador y vuelve aquí"), Toast.LENGTH_LONG).show();
            gd.login(this, err -> {
                if (err != null) {
                    new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog).setTitle(L.t("No se conectó Google Drive"))
                            .setMessage(err).setPositiveButton(L.t("Entendido"), null).show();
                    return;
                }
                Toast.makeText(this, L.t("Google Drive conectado"), Toast.LENGTH_SHORT).show();
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
        r.setOnClickListener(v -> openMediaFire());
        return r;
    }

    private View shizukuCard() {
        LinearLayout card = Ui.column(this);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        LinearLayout r = Ui.row(this);
        ImageView i = new ImageView(this);
        // Shizuku es «gota» en japonés: en Navidad es un carámbano y en Velitas una gota de cera
        int season = SeasonFx.active(this);
        if (season == Season.NAVIDAD) i.setImageResource(R.drawable.ic_shizuku_icicle);
        else if (season == Season.VELITAS) i.setImageResource(R.drawable.ic_shizuku_wax);
        else if (season == Season.VALENTIN) i.setImageResource(R.drawable.ic_shizuku_heart);
        else if (season == Season.AMISTAD) i.setImageResource(R.drawable.ic_shizuku_gift);
        else i.setImageDrawable(Ui.icon(this, R.drawable.ic_shield, ShizukuHelper.ready() ? Ui.GOOD : Ui.SUB));
        int isz = season == Season.NONE ? 22 : 28;
        r.addView(i, new LinearLayout.LayoutParams(dp(isz), dp(isz)));
        TextView t = Ui.text(this, ShizukuHelper.status(), 14, ShizukuHelper.ready() ? Ui.GOOD : Ui.TEXT, true);
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, Ui.weight(1));
        card.addView(r);
        if (!ShizukuHelper.ready()) {
            TextView d = Ui.text(this, L.t("Con Shizuku, Bóveda entra a Android/data y Android/obb en cualquier versión de Android, sin root."),
                    12.5f, Ui.SUB, false);
            d.setPadding(0, dp(8), 0, 0);
            card.addView(d);
            card.addView(ghostButton(ShizukuHelper.running() ? L.t("Conectar con Shizuku") : L.t("¿Cómo activo Shizuku?"), v -> useShizuku()),
                    topMargin(dp(12)));
        }
        return card;
    }

    private void useShizuku() {
        if (ShizukuHelper.running()) {
            ShizukuHelper.connect();
            Toast.makeText(this, L.t("Acepta el permiso en la ventana de Shizuku"), Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Activar Shizuku"))
                .setMessage(L.t("1. Instala la app Shizuku (Play Store o GitHub).\n")
                        + L.t("2. Ábrela y enciéndela con «Depuración inalámbrica» (sigue sus pasos; no necesita computador ni root).\n")
                        + L.t("3. Vuelve a Bóveda y toca «Conectar con Shizuku».\n\n")
                        + L.t("Hay que volver a encender Shizuku cada vez que se reinicia el celular."))
                .setPositiveButton(L.t("Abrir Play Store"), (d, w) -> openUrl("market://details?id=moe.shizuku.privileged.api",
                        "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                .setNegativeButton(L.t("Cerrar"), null)
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
        fab.setVisibility(picker() || bottom.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        backBtn.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        Volume v = volumeOf(p);
        title.setText(v != null && v.path.equals(p) ? v.name : Paths.name(p));
        if (!picker()) Tabs.path[Tabs.current] = p;
        updateTabs();
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
                    if (VaultFs.isVault(p)) {
                        showHome();
                        withVault(() -> open(p));
                    } else if (OneDriveFs.isOneDrive(p)) {
                        showHome();
                        showOneDriveSetup();
                    } else if (MegaFs.isMega(p)) {
                        showHome();
                        showMegaLogin(null, null);
                    } else if (DriveFs.isDrive(p)) {
                        showHome();
                        showDriveSetup();
                    } else if (FtpServers.isFtp(p)) {
                        showHome();
                        Toast.makeText(this, L.t("Ese servidor FTP ya no está guardado"), Toast.LENGTH_SHORT).show();
                    } else {
                        showNeedAccess(p);
                    }
                    return;
                }
                all.clear();
                counts.clear();
                if (res != null) {
                    boolean hidden = prefs.showHidden();
                    for (Entry e : res) {
                        if (e.name.endsWith(".boveda-parte") || e.path.equals(Vault.DIR) || e.name.equals(Trash.NAME)) continue;
                        if (!hidden && e.hidden()) continue;
                        all.add(e);
                    }
                    sort(all);
                    appsFolder = prefs.appIcons() && AppIcons.isAppsFolder(p);
                    if (appsFolder) {
                        // las apps instaladas primero, por su nombre; luego las carpetas sobrantes
                        all.sort((x, y) -> {
                            AppIcons.App ax = x.dir ? AppIcons.of(this, x.name) : null, ay = y.dir ? AppIcons.of(this, y.name) : null;
                            if ((ax == null) != (ay == null)) return ax != null ? -1 : 1;
                            String lx = ax != null ? ax.label : x.name, ly = ay != null ? ay.label : y.name;
                            int c = naturalCompare(lx, ly);
                            return c != 0 ? c : x.name.compareToIgnoreCase(y.name);
                        });
                    }
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
        for (Entry e : all) {
            if (filter.isEmpty() || e.name.toLowerCase().contains(filter)) {
                shown.add(e);
                continue;
            }
            AppIcons.App app = appsFolder && e.dir ? AppIcons.of(this, e.name) : null;
            if (app != null && app.label.toLowerCase().contains(filter)) shown.add(e);
        }
        adapter.notifyDataSetChanged();
        if (path != null) {
            empty.setText(filter.isEmpty() ? L.t("Carpeta vacía") : L.t("Nada coincide con «") + filter + "»");
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
        TextView t = Ui.text(this, L.t("Carpeta protegida por Android"), 19, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(8));
        box.addView(t);
        TextView d = Ui.text(this, L.t("Desde Android 11, Android/data y Android/obb están protegidas. Hay dos formas de entrar:"), 14, Ui.SUB, false);
        d.setGravity(Gravity.CENTER);
        box.addView(d);

        String base = Paths.under(p, Paths.OBB) ? Paths.OBB : Paths.under(p, Paths.DATA) ? Paths.DATA : p;
        box.addView(section(L.t("1 · Con el selector del sistema")));
        TextView d1 = Ui.text(this, L.t("Se abre el selector de Android dentro de la carpeta «Android»: entra a «") + Paths.name(base)
                + L.t("», toca «Usar esta carpeta» y «Permitir». ")
                + L.t("Funciona en Android 11 y 12 (y en algunos 13). En Android 13–14 a veces sí deja con la carpeta de una app."), 13, Ui.SUB, false);
        box.addView(d1);
        box.addView(bigButton(L.t("Dar acceso a ") + Paths.name(base), v -> requestTree(base)), topMargin(dp(12)));
        box.addView(ghostButton(L.t("Dar acceso a la carpeta de una app"), v -> chooseAppFolder(base)), topMargin(dp(8)));

        box.addView(section(L.t("2 · Con Shizuku (cualquier Android)")));
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
            Toast.makeText(this, L.t("Este celular no tiene el selector de carpetas"), Toast.LENGTH_LONG).show();
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
                .setTitle(L.t("¿La carpeta de qué app?"))
                .setItems(labels, (d, w) -> requestTree(Paths.join(base, rows.get(w)[1])))
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_GRANT_FOLDER) {
            Grants.Grant g = changing;
            changing = null;
            if (res == RESULT_OK && data != null && data.getData() != null && g != null) {
                try {
                    String dir = DocumentsContract.getTreeDocumentId(data.getData());
                    Grants.change(this, g, dir);
                    Toast.makeText(this, L.t("Listo: ahora ") + appName(g.pkg) + L.t(" usa ") + dir, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, L.t("No se pudo cambiar la carpeta"), Toast.LENGTH_LONG).show();
                }
            }
            refreshGrants();
            return;
        }
        if (req == REQ_TREE && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                String base = FsRouter.basePath(DocumentsContract.getTreeDocumentId(u));
                Toast.makeText(this, L.t("Acceso concedido"), Toast.LENGTH_SHORT).show();
                if (base != null && Paths.restricted(base)) open(base);
                else if (path != null) load(path, false);
            } catch (Exception e) {
                Toast.makeText(this, L.t("Android no dejó guardar el permiso de esa carpeta"), Toast.LENGTH_LONG).show();
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
        else if (pickMode() == PICK_TREE) Toast.makeText(this, L.t("Entra a la carpeta y toca «Usar esta carpeta»"), Toast.LENGTH_SHORT).show();
        else if (pickMode() == PICK_CREATE) {
            if (saveName != null) saveName.setText(e.name);
        } else if (picker()) pick(List.of(e.path));
        else if (Archive.isRar(e.name)) Toast.makeText(this, L.t("Los RAR no son bienvenidos en Bóveda 🦶"), Toast.LENGTH_SHORT).show();
        else if (Archive.opensOnTap(e.name)) openArchive(e.path);
        else openFile(e);
    }

    private void openArchive(String p) {
        startActivity(new Intent(this, ArchiveActivity.class).putExtra(ArchiveActivity.EXTRA, p));
    }

    private void toggleSelect(Entry e) {
        if (pickMode() == PICK_TREE || pickMode() == PICK_CREATE) return;
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
        updateBarsInner();
        // el botón + no va encima de una barra de abajo (transferencia, pegar, extraer…)
        if (fab != null) {
            fab.setVisibility(path != null && !picker() && bottom.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        }
    }

    private void updateBarsInner() {
        bottom.removeAllViews();
        if (path != null && !selected.isEmpty()) {
            title.setText(selected.size() + (selected.size() == 1 ? L.t(" seleccionado") : L.t(" seleccionados")));
            if (picker()) {
                bottom.addView(action(R.drawable.ic_check, L.t("Elegir"), () -> pick(new ArrayList<>(selected))));
                bottom.addView(action(R.drawable.ic_select_all, L.t("Todo"), this::selectAll));
                bottom.addView(action(R.drawable.ic_close, L.t("Quitar"), this::clearSelection));
            } else {
                bottom.addView(action(R.drawable.ic_copy, L.t("Copiar"), () -> toClip(false)));
                bottom.addView(action(R.drawable.ic_cut, L.t("Mover"), () -> toClip(true)));
                bottom.addView(action(R.drawable.ic_delete, L.t("Borrar"), this::askDelete));
                bottom.addView(action(R.drawable.ic_archive, L.t("Comprimir"), this::askZip));
                bottom.addView(action(R.drawable.ic_select_all, L.t("Todo"), this::selectAll));
                bottom.addView(action(R.drawable.ic_menu, L.t("Más"), this::showMore));
            }
            bottom.setVisibility(View.VISIBLE);
            fab.setVisibility(View.GONE);
            return;
        }
        if (path != null) {
            Volume v = volumeOf(path);
            title.setText(v != null && v.path.equals(path) ? v.name : Paths.name(path));
        }
        if (path != null && pickMode() == PICK_TREE) {
            TextView info = Ui.text(this, L.t("Dar acceso a «") + label(path) + "»", 14, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bottom.addView(info, Ui.weight(1));
            TextView go = bigButton(L.t("Usar esta carpeta"), v -> returnTree(path));
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            bottom.addView(go);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && pickMode() == PICK_CREATE) {
            String suggested = getIntent().getStringExtra(Intent.EXTRA_TITLE);
            String current = saveName != null ? saveName.getText().toString() : null;
            saveName = input(L.t("Nombre del archivo"), current != null ? current : suggested == null ? "archivo" : suggested,
                    android.text.InputType.TYPE_CLASS_TEXT);
            bottom.addView(saveName, Ui.weight(1));
            TextView go = bigButton(L.t("Guardar aquí"), v -> createAndReturn(saveName.getText().toString().trim()));
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.setMargins(dp(8), 0, 0, 0);
            bottom.addView(go, gl);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && !cloudDownload.isEmpty() && !picker()) {
            TextView info = Ui.text(this, L.t("Descargar ") + (cloudDownload.size() == 1 ? "«" + cloudDownload.get(0).name + "»"
                    : cloudDownload.size() + L.t(" elementos")) + L.t(" de MediaFire"), 13.5f, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, L.t("Cancelar"), 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                cloudDownload.clear();
                updateBars();
            });
            bottom.addView(cancel);
            TextView go = bigButton(L.t("Descargar aquí"), v -> {
                engine.submitCloudDownload(new ArrayList<>(cloudDownload), path);
                cloudDownload.clear();
                updateBars();
                Toast.makeText(this, L.t("Descargando de MediaFire…"), Toast.LENGTH_SHORT).show();
            });
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            bottom.addView(go);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && extractArchive != null && !picker()) {
            String what = extractPicks.isEmpty() ? "todo" : extractPicks.size() + (extractPicks.size() == 1 ? L.t(" elemento") : L.t(" elementos"));
            TextView info = Ui.text(this, L.t("Extraer ") + what + L.t(" de «") + Paths.name(extractArchive) + "»", 13.5f, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setMaxLines(2);
            info.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, L.t("Cancelar"), 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                extractArchive = null;
                updateBars();
            });
            bottom.addView(cancel);
            TextView go = bigButton(L.t("Extraer aquí"), v -> {
                final String arch = extractArchive, base = extractBase, to = path;
                final List<String> picks = new ArrayList<>(extractPicks);
                extractArchive = null;
                updateBars();
                ArchivePass.ensure(this, arch, () -> {
                    engine.submitExtract(arch, picks, base, to);
                    Toast.makeText(this, L.t("Extrayendo…"), Toast.LENGTH_SHORT).show();
                });
            });
            go.setPadding(dp(16), dp(10), dp(16), dp(10));
            bottom.addView(go);
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (transferring && !picker()) {
            // mientras copia o mueve: en vez de Pegar/Cancelar, la opción de no apagar la pantalla
            TextView info = Ui.text(this, L.t("Mantener la pantalla siempre encendida"), 14, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            info.setMaxLines(2);
            bottom.addView(info, Ui.weight(1));
            android.widget.Switch sw = new android.widget.Switch(this);
            sw.setChecked(prefs.keepScreenOn());
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
            sw.setTrackTintList(android.content.res.ColorStateList.valueOf(0x663D8BFF));
            sw.setOnCheckedChangeListener((b, on) -> {
                prefs.setKeepScreenOn(on);
                applyKeepScreen();
                Toast.makeText(this, on ? L.t("La pantalla no se apagará mientras trabaja") : L.t("La pantalla se apagará como siempre"),
                        Toast.LENGTH_SHORT).show();
            });
            bottom.addView(sw);
            info.setOnClickListener(v -> sw.toggle());
            bottom.setVisibility(View.VISIBLE);
            return;
        }
        if (path != null && !clip.isEmpty() && !picker()) {
            TextView info = Ui.text(this, (clipMove ? L.t("Mover ") : L.t("Copiar ")) + clip.size() + (clip.size() == 1 ? L.t(" elemento") : L.t(" elementos")),
                    14, Ui.TEXT, true);
            info.setPadding(dp(10), 0, 0, 0);
            bottom.addView(info, Ui.weight(1));
            TextView cancel = Ui.text(this, L.t("Cancelar"), 14, Ui.SUB, true);
            cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
            cancel.setOnClickListener(v -> {
                clip.clear();
                updateBars();
            });
            bottom.addView(cancel);
            TextView paste = bigButton(L.t("Pegar aquí"), v -> paste());
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
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        // con letra grande, la palabra se achica sola en vez de partirse («Compr / imir»)
        t.setAutoSizeTextTypeUniformWithConfiguration(7, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        b.addView(t, new LinearLayout.LayoutParams(-1, -2));
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
        Toast.makeText(this, L.t("Ve a la carpeta destino y toca «Pegar aquí»"), Toast.LENGTH_SHORT).show();
    }

    private void paste() {
        if (path == null || clip.isEmpty()) return;
        String dest = path;
        List<String> src = new ArrayList<>(clip);
        boolean move = clipMove;
        for (String s : src) {
            if (Paths.under(dest, s)) {
                Toast.makeText(this, L.t("No se puede pegar una carpeta dentro de sí misma"), Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (move) {
            boolean same = true;
            for (String s : src) if (!Paths.parent(s).equals(dest)) same = false;
            if (same) {
                Toast.makeText(this, L.t("Ya están en esta carpeta"), Toast.LENGTH_SHORT).show();
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
        String[] opts = {L.t("Reemplazar"), L.t("Mantener ambos (renombrar)"), L.t("Saltar los repetidos")};
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(clashes + (clashes == 1 ? L.t(" elemento ya existe aquí") : L.t(" elementos ya existen aquí")))
                .setItems(opts, (d, w) -> submitTransfer(move, src, dest,
                        w == 0 ? TransferEngine.Conflict.REPLACE : w == 1 ? TransferEngine.Conflict.KEEP_BOTH : TransferEngine.Conflict.SKIP))
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
    }

    private void submitTransfer(boolean move, List<String> src, String dest, TransferEngine.Conflict c) {
        engine.submit(move ? TransferEngine.Kind.MOVE : TransferEngine.Kind.COPY, src, dest, c);
        clip.clear();
        updateBars();
        Toast.makeText(this, move ? L.t("Moviendo…") : L.t("Copiando…"), Toast.LENGTH_SHORT).show();
    }

    private void askDelete() {
        List<String> src = new ArrayList<>(selected);
        boolean bin = true;
        for (String s : src) if (!Trash.supported(s)) bin = false;
        if (bin) {
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("¿Borrar ") + (src.size() == 1 ? "«" + Paths.name(src.get(0)) + "»" : src.size() + L.t(" elementos")) + "?")
                    .setMessage(L.t("Se van a la papelera: puedes restaurarlos durante 30 días."))
                    .setPositiveButton(L.t("A la papelera"), (d, w) -> moveToTrash(src))
                    .setNeutralButton(L.t("Borrar para siempre"), (d, w) -> {
                        engine.submit(TransferEngine.Kind.DELETE, src, null, TransferEngine.Conflict.SKIP);
                        clearSelection();
                    })
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("¿Borrar ") + (src.size() == 1 ? "«" + Paths.name(src.get(0)) + "»" : src.size() + L.t(" elementos")) + "?")
                .setMessage(L.t("Se borran para siempre (no van a una papelera)."))
                .setPositiveButton(L.t("Borrar"), (d, w) -> {
                    engine.submit(TransferEngine.Kind.DELETE, src, null, TransferEngine.Conflict.SKIP);
                    clearSelection();
                })
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
    }

    private void askZip() {
        List<String> src = new ArrayList<>(selected);
        String suggested = src.size() == 1 ? stripExt(Paths.name(src.get(0))) : Paths.name(path);
        CompressDialog.show(this, suggested, (fileName, opts) -> {
            engine.submitCompress(src, Paths.join(path, fileName), opts);
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
            labels.add(L.t("Renombrar"));
            acts.add(() -> askRename(single));
            if (single.dir || (Archive.supported(single.name) && !VaultFs.isVault(single.path))) {
                labels.add(L.t("Reducir peso (buscar duplicados)"));
                acts.add(() -> {
                    clearSelection();
                    DuplicatesDialog.show(this, single.path, single.dir, () -> load(path, true));
                });
            }
            if (single.dir) {
                labels.add(prefs.isFavorite(single.path) ? L.t("Quitar de favoritas") : L.t("Añadir a favoritas"));
                acts.add(() -> {
                    prefs.toggleFavorite(single.path);
                    clearSelection();
                });
                labels.add(prefs.isPinned(single.path) ? L.t("Desanclar") : L.t("Anclar arriba"));
                acts.add(() -> {
                    prefs.togglePinned(single.path);
                    clearSelection();
                    buildChips();
                });
            } else {
                if (!VaultFs.isVault(single.path)) {
                    labels.add(L.t("Abrir con…"));
                    acts.add(() -> openWith(single, true));
                }
                boolean plainFile = Thumbs.local(single.path) && new File(single.path).canRead();
                if (plainFile && single.name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    labels.add(L.t("Firmar APK ✍️"));
                    acts.add(() -> askSignApk(single));
                }
                if (plainFile && isKeyFile(single.name)) {
                    labels.add(L.t("Usar como llave de firma"));
                    acts.add(() -> askImportKey(single));
                }
                if (Archive.supported(single.name) && !VaultFs.isVault(single.path)) {
                    labels.add(L.t("Ver contenido"));
                    acts.add(() -> {
                        clearSelection();
                        openArchive(single.path);
                    });
                    labels.add(L.t("Extraer aquí"));
                    acts.add(() -> {
                        final String ap = single.path, to = Paths.join(path, Archive.baseName(single.name));
                        clearSelection();
                        ArchivePass.ensure(this, ap, () -> {
                            engine.submitExtract(ap, new ArrayList<>(), "", to);
                            Toast.makeText(this, L.t("Extrayendo…"), Toast.LENGTH_SHORT).show();
                        });
                    });
                    labels.add(L.t("Extraer a…"));
                    acts.add(() -> {
                        setExtractClip(single.path, new ArrayList<>(), "");
                        clearSelection();
                        Toast.makeText(this, L.t("Ve a la carpeta destino y toca «Extraer aquí»"), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }
        boolean inVault = path != null && VaultFs.isVault(path);
        boolean allFiles = true;
        for (String s : sel) for (Entry e : all) if (e.path.equals(s) && e.dir) allFiles = false;
        if (allFiles && !inVault) {
            labels.add(L.t("Compartir"));
            acts.add(() -> share(sel));
        }
        if (!inVault) {
            labels.add(L.t("Mover a la Bóveda 🔐"));
            acts.add(() -> moveToVault(sel));
        }
        if (!inVault) {
            labels.add(L.t("Subir a MediaFire"));
            acts.add(() -> {
                CloudActivity.pendingUpload.clear();
                CloudActivity.pendingUpload.addAll(sel);
                clearSelection();
                Toast.makeText(this, L.t("Elige la carpeta de MediaFire y toca «Subir aquí»"), Toast.LENGTH_LONG).show();
                openMediaFire();
            });
        }
        labels.add(L.t("Propiedades"));
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
                .setPositiveButton(L.t("Aceptar"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = in.getText().toString().trim();
            if (!Paths.validName(n)) {
                in.setError(L.t("Nombre no válido"));
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
        askName(L.t("Renombrar"), e.name, n -> io.execute(() -> {
            String to = Paths.join(Paths.parent(e.path), n);
            String err = null;
            try {
                Fs f = FsRouter.get(this, e.path);
                if (f.stat(to) != null && !n.equalsIgnoreCase(e.name)) err = L.t("Ya existe algo con ese nombre");
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
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(16), dp(8), dp(16), dp(4));
        AlertDialog[] dlg = new AlertDialog[1];
        List<View> tiles = new ArrayList<>();
        tiles.add(tile("📁", 0xFF1E88E5, 0xFF26C6DA, L.t("Carpeta"), L.t("Para ordenar tus cosas"),
                v -> { dlg[0].dismiss(); askName(L.t("Nueva carpeta"), L.t("Nueva carpeta"), n -> create(n, null, false)); }));
        tiles.add(tile("📝", 0xFF35D08A, 0xFF1EA97C, L.t("Texto"), L.t("Un archivo .txt"),
                v -> { dlg[0].dismiss(); askDocument(".txt"); }));
        tiles.add(tile("📄", 0xFFE5603B, 0xFFFF9A5C, "PDF", L.t("Con título y texto"),
                v -> { dlg[0].dismiss(); askDocument(".pdf"); }));
        tiles.add(tile("🧩", 0xFF7B4DFF, 0xFFB86BFF, L.t("Otros"), L.t(".java, .json, .html, .py y más"),
                v -> { dlg[0].dismiss(); showOtherTypes(); }));
        addGrid(box, tiles);
        dlg[0] = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Crear"))
                .setView(box)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        dlg[0].show();
    }

    /** Tipos de archivo que Bóveda puede abrir, leer y editar como texto. */
    private static final String[][] TEXT_TYPES = {
            {"Java", ".java"}, {"Kotlin", ".kt"}, {"Python", ".py"}, {"JavaScript", ".js"}, {"TypeScript", ".ts"},
            {"JSON", ".json"}, {"XML", ".xml"}, {"HTML", ".html"}, {"CSS", ".css"}, {"Markdown", ".md"},
            {"CSV", ".csv"}, {"YAML", ".yml"}, {"TOML", ".toml"}, {"INI", ".ini"}, {"Configuración", ".cfg"},
            {"Propiedades", ".properties"}, {"Registro (log)", ".log"}, {"Shell", ".sh"}, {"Lote de Windows", ".bat"},
            {"PowerShell", ".ps1"}, {"C", ".c"}, {"C++", ".cpp"}, {"Encabezado C/C++", ".h"}, {"C#", ".cs"},
            {"Go", ".go"}, {"Rust", ".rs"}, {"PHP", ".php"}, {"Ruby", ".rb"}, {"Lua", ".lua"}, {"SQL", ".sql"},
            {"Gradle", ".gradle"}, {"Subtítulos", ".srt"}, {"SVG", ".svg"}, {"Lang de Minecraft", ".lang"},
            {"mcfunction", ".mcfunction"}, {"Otra extensión…", ""}
    };

    /** La lista de tipos: al elegir uno, el nombre del archivo nuevo termina en esa extensión. */
    private void showOtherTypes() {
        String[] labels = new String[TEXT_TYPES.length];
        for (int i = 0; i < labels.length; i++) {
            String[] t = TEXT_TYPES[i];
            labels[i] = t[1].isEmpty() ? t[0] : t[0] + "   (" + t[1] + ")";
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("¿Qué tipo de archivo?"))
                .setItems(labels, (d, w) -> {
                    if (!TEXT_TYPES[w][1].isEmpty()) {
                        askDocument(TEXT_TYPES[w][1]);
                        return;
                    }
                    EditText e = input(L.t("Extensión, por ejemplo: txt, cfg, nbt"), "", android.text.InputType.TYPE_CLASS_TEXT);
                    LinearLayout bx = Ui.column(this);
                    bx.setPadding(dp(20), dp(8), dp(20), dp(4));
                    bx.addView(e);
                    AlertDialog ad = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                            .setTitle(L.t("Otra extensión"))
                            .setView(bx)
                            .setPositiveButton(L.t("Seguir"), null)
                            .setNegativeButton(L.t("Cancelar"), null)
                            .create();
                    ad.setOnShowListener(x -> ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        String ext = e.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                        if (ext.startsWith(".")) ext = ext.substring(1);
                        if (ext.isEmpty() || !ext.matches("[a-z0-9_+-]{1,12}")) {
                            e.setError(L.t("Solo letras y números, sin espacios"));
                            return;
                        }
                        ad.dismiss();
                        askDocument("." + ext);
                    }));
                    ad.show();
                })
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
    }

    /** Nombre y contenido de un .txt o un PDF nuevos. */
    private void askDocument(String suffix) {
        final boolean pdf = suffix.equals(".pdf");
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText name = input(L.t("Nombre"), pdf ? L.t("Documento") : "nuevo", android.text.InputType.TYPE_CLASS_TEXT);
        TextView ext = Ui.text(this, suffix, 15, Ui.ACCENT2, true);
        ext.setPadding(dp(8), 0, 0, 0);
        LinearLayout nameRow = Ui.row(this);
        nameRow.addView(name, Ui.weight(1));
        nameRow.addView(ext);
        box.addView(nameRow);
        EditText title = input(L.t("Título (opcional)"), "", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (pdf) box.addView(title, topMargin(dp(8)));
        EditText body = input(pdf ? L.t("Escribe el contenido del PDF (opcional)") : L.t("Escribe el texto (opcional)"), "",
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setSingleLine(false);
        body.setMinLines(6);
        body.setMaxLines(14);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setVerticalScrollBarEnabled(true);
        box.addView(body, topMargin(dp(8)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(pdf ? L.t("Nuevo PDF") : suffix.equals(".txt") ? L.t("Nuevo archivo de texto") : L.t("Nuevo archivo ") + suffix)
                .setView(scroll)
                .setPositiveButton(L.t("Crear"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.toLowerCase().endsWith(suffix)) n = n.substring(0, n.length() - suffix.length());
            if (!Paths.validName(n + suffix)) {
                name.setError(L.t("Nombre no válido"));
                return;
            }
            d.dismiss();
            String content = body.getText().toString();
            String t = title.getText().toString().trim();
            create(n + suffix, out -> {
                if (pdf) PdfMaker.write(t, content, out);
                else out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }, true);
        }));
        d.show();
    }

    private interface Writer {
        void write(java.io.OutputStream out) throws IOException;
    }

    /** Crea una carpeta (writer null y file false) o un archivo con su contenido, donde sea (también FTP o Drive). */
    private void create(String n, Writer writer, boolean file) {
        String target = Paths.join(path, n);
        String dir = path;
        io.execute(() -> {
            String err = null;
            try {
                Fs f = FsRouter.get(this, target);
                if (f.stat(target) != null) err = L.t("Ya existe algo con ese nombre");
                else if (!file) f.mkdir(target);
                else try (java.io.OutputStream o = f.out(target)) {
                        if (writer != null) writer.write(o);
                    }
            } catch (IOException ex) {
                err = ex.getMessage();
            } catch (RuntimeException ex) {
                err = L.t("No se pudo crear: ") + ex.getMessage();
            }
            String fe = err;
            main.post(() -> {
                if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                else if (file) Toast.makeText(this, "«" + n + L.t("» creado"), Toast.LENGTH_SHORT).show();
                if (dir.equals(path)) load(path, true);
            });
        });
    }

    private void properties(List<String> sel) {
        TextView t = Ui.text(this, L.t("Calculando…"), 14, Ui.TEXT, false);
        t.setPadding(dp(24), dp(12), dp(24), dp(8));
        t.setTextIsSelectable(true);
        AlertDialog d = new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(sel.size() == 1 ? Paths.name(sel.get(0)) : sel.size() + L.t(" elementos"))
                .setView(t)
                .setPositiveButton(L.t("Cerrar"), null)
                .show();
        io.execute(() -> {
            long[] acc = {0, 0, 0};
            StringBuilder sb = new StringBuilder();
            try {
                for (String s : sel) measure(FsRouter.get(this, s), s, acc);
                if (sel.size() == 1) {
                    Entry e = FsRouter.get(this, sel.get(0)).stat(sel.get(0));
                    sb.append(L.t("Ruta: ")).append(sel.get(0)).append("\n");
                    if (e != null) sb.append(L.t("Modificado: ")).append(Ui.date(e.modified)).append("\n");
                }
                sb.append(L.t("Tamaño: ")).append(Ui.size(acc[0])).append(" (").append(String.format("%,d", acc[0])).append(" bytes)\n");
                sb.append(L.t("Archivos: ")).append(acc[1]).append(L.t("   Carpetas: ")).append(acc[2]);
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
        // música, video, texto/código y PDF se abren con los visores de Bóveda
        if (VaultFs.isVault(e.path)) {
            // lo de la Bóveda solo se abre con los visores de Bóveda: ninguna otra app lo puede leer
            if (!picker() && Viewers.open(this, e, all)) return;
            if (Archive.supported(e.name)) {
                Toast.makeText(this, L.t("Para ver lo que tiene, sácalo primero de la Bóveda"), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, L.t("Este tipo de archivo solo se puede abrir sacándolo de la Bóveda"), Toast.LENGTH_LONG).show();
            return;
        }
        if (!picker() && prefs.ownViewers() && Viewers.open(this, e, all)) return;
        openWith(e, false);
    }

    private void openWith(Entry e, boolean chooser) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(contentUri(e.path), FileTypes.mime(e.name));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(chooser ? Intent.createChooser(i, L.t("Abrir con")) : i);
        } catch (Exception ex) {
            Intent any = new Intent(Intent.ACTION_VIEW);
            any.setDataAndType(contentUri(e.path), "*/*");
            any.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(any, L.t("Abrir con")));
            } catch (Exception ignored) {
                Toast.makeText(this, L.t("No hay ninguna app para abrir este archivo"), Toast.LENGTH_SHORT).show();
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
        startActivity(Intent.createChooser(i, L.t("Compartir")));
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
        r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        setResult(RESULT_OK, r);
        finish();
    }

    /** Selector de carpetas: la otra app recibe permiso permanente sobre la carpeta y todo lo de adentro. */
    private void returnTree(String dir) {
        Grants.granted(this, getCallingPackage(), dir);
        Uri tree = DocumentsContract.buildTreeDocumentUri(StorageProvider.authority(this), dir);
        Intent r = new Intent().setData(tree);
        r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        setResult(RESULT_OK, r);
        finish();
    }

    /** «Guardar como»: crea el archivo vacío aquí y se lo entrega a la otra app para que escriba. */
    private void createAndReturn(String name) {
        if (!Paths.validName(name)) {
            Toast.makeText(this, L.t("Escribe un nombre válido"), Toast.LENGTH_SHORT).show();
            return;
        }
        String dir = path;
        io.execute(() -> {
            String err = null, made = null;
            try {
                Fs f = FsRouter.get(this, Paths.join(dir, name));
                String target = Paths.join(dir, name);
                for (int n = 1; f.stat(target) != null; n++) {
                    int dot = name.lastIndexOf('.');
                    target = Paths.join(dir, (dot > 0 ? name.substring(0, dot) : name) + " (" + n + ")" + (dot > 0 ? name.substring(dot) : ""));
                }
                f.out(target).close();
                made = target;
            } catch (IOException e) {
                err = e.getMessage();
            }
            String fe = err, fm = made;
            main.post(() -> {
                if (fe != null) {
                    Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent r = new Intent().setData(contentUri(fm));
                r.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                setResult(RESULT_OK, r);
                finish();
            });
        });
    }

    // ------------------------------------------------------------------ gestor predeterminado

    private static final int REQ_DEFAULT_TEST = 41;

    private String handlerOf(Intent i) {
        android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        return ri.activityInfo.packageName;
    }

    private String appName(String pkg) {
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Guía para que Bóveda quede como gestor de archivos para todo (Android no tiene un ajuste único para esto). */
    private void showDefaultSetup() {
        Intent[] tests = {
                new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
                new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/y"), "resource/folder"),
        };
        String[] what = {L.t("Adjuntar / elegir archivos"), L.t("Abrir documentos"), L.t("Elegir carpetas (emuladores…)"), L.t("Abrir carpetas")};
        String[] rows = new String[tests.length];
        String[] owners = new String[tests.length];
        for (int k = 0; k < tests.length; k++) {
            String p = handlerOf(tests[k]);
            owners[k] = p;
            String state;
            if (p == null) state = L.t("nadie (las apps se cierran) → toca para probar");
            else if (p.equals(getPackageName())) state = L.t("✓ Bóveda");
            else if (p.equals("android") || p.contains("resolver")) state = L.t("sin predeterminado → toca y elige Bóveda + «Siempre»");
            else state = appName(p) + L.t(" → toca para quitarlo");
            rows[k] = what[k] + "\n" + state;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Hacer a Bóveda el predeterminado"))
                .setItems(rows, (d, w) -> {
                    String p = owners[w];
                    if (p != null && !p.equals(getPackageName()) && !p.equals("android") && !p.contains("resolver")) {
                        // otra app está puesta como predeterminada: hay que quitarla desde sus ajustes
                        Toast.makeText(this, L.t("Toca «Abrir de forma predeterminada» → «Borrar valores predeterminados» y vuelve"),
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p)));
                    } else if (p == null || !p.equals(getPackageName())) {
                        Toast.makeText(this, L.t("Elige «Bóveda» y toca «Siempre»"), Toast.LENGTH_LONG).show();
                        try {
                            startActivityForResult(tests[w], REQ_DEFAULT_TEST);
                        } catch (Exception e) {
                            Toast.makeText(this, L.t("Android no dejó probarlo"), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, L.t("Bóveda ya es el predeterminado para esto"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton(L.t("Listo"), null)
                .show();
    }

    // ------------------------------------------------------------------ menús

    // ------------------------------------------------------------------ ajustes

    /** Pantalla de ajustes: todo lo que se puede configurar de Bóveda, ordenado por secciones. */
    /** Ajustes divididos en categorías: primero la lista de categorías y al tocar una, sus opciones. */
    private void showSettings() {
        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.view.Window win = d.getWindow();
        if (win != null) {
            win.setStatusBarColor(Ui.BG);
            win.setNavigationBarColor(Ui.BG);
        }
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(this);
        head.setPadding(dp(4), dp(8), dp(12), dp(6));
        TextView title = Ui.text(this, L.t("Ajustes"), 21, Ui.TEXT, true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(dp(14), dp(4), dp(14), dp(32));
        sv.addView(col);
        int[] page = {-1};
        Runnable[] home = new Runnable[1];
        home[0] = () -> {
            page[0] = -1;
            title.setText(L.t("Ajustes"));
            settingsCategories(col, d, i -> {
                page[0] = i;
                title.setText(SETTINGS_CATEGORIES[i][0]);
                fillSettings(col, d, i);
                sv.scrollTo(0, 0);
            });
            sv.scrollTo(0, 0);
        };
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 46, v -> {
            if (page[0] >= 0) home[0].run();
            else d.dismiss();
        }));
        head.addView(title, Ui.weight(1));
        root.addView(head);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(root);
        d.setOnKeyListener((dd, key, ev) -> {
            if (key == android.view.KeyEvent.KEYCODE_BACK && ev.getAction() == android.view.KeyEvent.ACTION_UP && page[0] >= 0) {
                home[0].run();
                return true;
            }
            return false;
        });
        home[0].run();
        d.setOnDismissListener(x -> {
            rebuildTabs();
            if (path != null) load(path, true);
            else showHome();
            updateTransfersBadge();
        });
        d.show();
    }

    /** {título, descripción} de cada categoría, en orden. */
    private static final String[][] SETTINGS_CATEGORIES = {
            {L.t("Idioma e interfaz"), L.t("Idioma, pestañas y pantalla")},
            {L.t("Vista y archivos"), L.t("Ocultos, orden, miniaturas y conteos")},
            {L.t("Visores"), L.t("Reproductor, fotos, editor y PDF")},
            {L.t("Transferencias"), L.t("Copiar, mover y la barra de progreso")},
            {L.t("Nube y red"), L.t("MEGA, OneDrive, Google Drive, MediaFire y FTP")},
            {L.t("Acceso a carpetas"), L.t("Shizuku y permisos para otras apps")},
            {L.t("Almacenamiento"), L.t("Archivos temporales")},
            {L.t("La Bóveda"), L.t("PIN, huella y autoborrado · pide tu PIN o tu huella")},
            {L.t("Temas de temporada"), L.t("Navidad, Velitas, Halloween y más")},
            {L.t("Experimentales y de prueba"), L.t("Funciones en pruebas · úsalas bajo tu propio riesgo")},
            {L.t("Tutoriales y aprendizaje"), L.t("Cómo usar Bóveda, paso a paso y sin sonido")},
            {L.t("Acerca de"), L.t("Versión y Forja Estudios")},
    };
    private static final int[] SETTINGS_ICONS = {R.drawable.ic_menu, R.drawable.ic_folder, R.drawable.ic_play, R.drawable.ic_swap,
            R.drawable.ic_download, R.drawable.ic_shield, R.drawable.ic_sd, R.drawable.ic_key, R.drawable.ic_season, R.drawable.ic_code, R.drawable.ic_play, R.drawable.ic_info};
    private static final int[][] SETTINGS_COLORS = {{0xFF4C9DFF, 0xFF6FD6FF}, {0xFFFFB547, 0xFFF07C2C}, {0xFF35D08A, 0xFF1EA97C},
            {0xFFB47CFF, 0xFF7C4DFF}, {0xFF28A8EA, 0xFF0A64D6}, {0xFFFF6B8A, 0xFFE0405F}, {0xFF8B97AB, 0xFF5D6B83}, {0xFF7B4DFF, 0xFFB86BFF}, {0xFFE63946, 0xFF2DC653}, {0xFFFFB547, 0xFFFF5D6C}, {0xFF6FD6FF, 0xFF4C9DFF}, {0xFFFF8A3D, 0xFFFFC857}};

    private void settingsCategories(LinearLayout col, android.app.Dialog d, java.util.function.IntConsumer open) {
        col.removeAllViews();
        for (int i = 0; i < SETTINGS_CATEGORIES.length; i++) {
            final int c = i;
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(14), dp(13), dp(12), dp(13));
            r.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(16))));
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(Ui.gradient(dp(12), SETTINGS_COLORS[i][0], SETTINGS_COLORS[i][1]));
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(Ui.icon(this, SETTINGS_ICONS[i], 0xFF0A0E16));
            badge.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            r.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout texts = Ui.column(this);
            texts.setPadding(dp(14), 0, 0, 0);
            texts.addView(Ui.text(this, SETTINGS_CATEGORIES[i][0], 15.5f, Ui.TEXT, true));
            TextView sub = Ui.text(this, SETTINGS_CATEGORIES[i][1], 12.5f, Ui.SUB, false);
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(sub);
            r.addView(texts, Ui.weight(1));
            ImageView ch = new ImageView(this);
            ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, 0xFF4A5670));
            r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
            // los ajustes de La Bóveda solo se abren con el PIN o la huella
            r.setOnClickListener(v -> {
                if (c != VAULT_SETTINGS) open.accept(c);
                else if (!Vault.exists()) createVault(() -> open.accept(c));
                else unlockVault(() -> open.accept(c));
            });
            addCard(col, r);
        }
        addCard(col, Studio.row(this, null));
    }

    private static final int VAULT_SETTINGS = 7;
    private static final int SEASON_SETTINGS = 8;
    private static final int LAB_SETTINGS = 9;
    private static final int TUTORIAL_SETTINGS = 10;

    private void fillSettings(LinearLayout col, android.app.Dialog d, int category) {
        col.removeAllViews();
        Runnable refresh = () -> fillSettings(col, d, category);
        if (category == VAULT_SETTINGS) {
            vaultSettings(col, refresh);
            return;
        }
        if (category == SEASON_SETTINGS) {
            Season.settings(this, col, refresh);
            return;
        }
        if (category == LAB_SETTINGS) {
            labSettings(col);
            return;
        }
        if (category == TUTORIAL_SETTINGS) {
            tutorialSettings(col, refresh);
            return;
        }
        switch (category) {
            case 0 -> {
                settingsSection(col, L.t("Idioma"));
                settingsRow(col, L.t("Idioma de Bóveda"), languageName(prefs.language()), this::pickLanguage);
                settingsSection(col, L.t("Interfaz"));
                tabsSetting(col, refresh);
                settingsSwitch(col, L.t("Pantalla encendida al copiar"), L.t("No deja que la pantalla se apague mientras hay una transferencia en curso"),
                        prefs.keepScreenOn(), b -> prefs.setKeepScreenOn(b));
            }
            case 1 -> {
                settingsSection(col, L.t("Vista"));
                settingsSwitch(col, L.t("Mostrar archivos ocultos"), L.t("Los que empiezan con un punto (.nomedia, .thumbnails…)"),
                        prefs.showHidden(), b -> prefs.setShowHidden(b));
                settingsRow(col, L.t("Ordenar por"), Prefs.SORT_NAMES[prefs.sort()] + (prefs.sortDesc() ? L.t(" (descendente)") : L.t(" (ascendente)")),
                        () -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                                .setTitle(L.t("Ordenar por"))
                                .setSingleChoiceItems(Prefs.SORT_NAMES, prefs.sort(), (dd, w) -> {
                                    if (w == prefs.sort()) prefs.setSortDesc(!prefs.sortDesc());
                                    else {
                                        prefs.setSort(w);
                                        prefs.setSortDesc(w == Prefs.SORT_DATE || w == Prefs.SORT_SIZE);
                                    }
                                    dd.dismiss();
                                    refresh.run();
                                }).show());
                settingsSwitch(col, L.t("Contar elementos de las carpetas"), L.t("«3 carpetas · 12 archivos» debajo de cada carpeta"),
                        prefs.showCounts(), b -> {
                            prefs.setShowCounts(b);
                            counts.clear();
                        });
                settingsSection(col, L.t("Archivos"));
                settingsSwitch(col, L.t("Miniaturas"), L.t("Vista previa de fotos y videos, e ícono de cada APK"),
                        prefs.thumbnails(), b -> prefs.setThumbnails(b));
                settingsSwitch(col, L.t("Íconos de apps en Android/data"), L.t("Muestra el ícono y el nombre de la app dueña de cada carpeta"),
                        prefs.appIcons(), b -> prefs.setAppIcons(b));
            }
            case 2 -> {
                settingsSection(col, L.t("Visores"));
                settingsSwitch(col, L.t("Abrir con los visores de Bóveda"), L.t("Música y video con el reproductor, imágenes con el visor de fotos, texto y código con el editor, y PDF con el visor. Apagado: con otras apps"),
                        prefs.ownViewers(), b -> prefs.setOwnViewers(b));
            }
            case 3 -> {
                settingsSection(col, L.t("Transferencias"));
                settingsSwitch(col, L.t("Verificar cada copia"), L.t("Vuelve a leer cada archivo copiado y lo compara con el original. Más seguro, un poco más lento"),
                        prefs.verify(), b -> prefs.setVerify(b));
                settingsSwitch(col, L.t("Barra de progreso flotante"), L.t("Porcentaje, velocidad y tiempo al copiar, mover, borrar, comprimir o subir"),
                        prefs.floatingProgress(), b -> prefs.setFloatingProgress(b));
                settingsRow(col, L.t("Ver transferencias"), L.t("Tareas en curso y terminadas"), this::showTransfers);
            }
            case 4 -> {
                settingsSection(col, L.t("Nubes"));
                settingsRow(col, "MEGA", Mega.get(this).loggedIn() ? Mega.get(this).email() : L.t("Iniciar sesión"), () -> {
                    d.dismiss();
                    if (Mega.get(this).loggedIn()) megaMenu();
                    else showMegaLogin(null, null);
                });
                settingsRow(col, "OneDrive", OneDrive.get(this).connected() ? L.t("Conectado") : L.t("Conectar"), () -> {
                    d.dismiss();
                    if (OneDrive.get(this).connected()) oneDriveMenu();
                    else showOneDriveSetup();
                });
                settingsRow(col, "Google Drive", Drive.get(this).connected() ? L.t("Conectado") : L.t("Conectar"), () -> {
                    d.dismiss();
                    if (Drive.get(this).connected()) driveMenu();
                    else showDriveSetup();
                });
                settingsRow(col, "MediaFire", MediaFire.get(this).loggedIn() ? L.t("Sesión iniciada") : L.t("Iniciar sesión"), () -> {
                    d.dismiss();
                    openMediaFire();
                });
                settingsSection(col, L.t("Red"));
                settingsRow(col, L.t("Añadir servidor FTP"), L.t("Xbox 360, PC, otro celular…"), () -> {
                    d.dismiss();
                    editFtp(null);
                });
            }
            case 5 -> {
                settingsSection(col, L.t("Acceso a carpetas"));
                settingsRow(col, "Shizuku", ShizukuHelper.status(), () -> {
                    useShizuku();
                    refresh.run();
                });
                settingsRow(col, L.t("Gestor de archivos predeterminado"), L.t("Que Bóveda abra las carpetas y elija archivos para otras apps"), this::showDefaultSetup);
                settingsRow(col, L.t("Dar carpetas a otras apps"), L.t("Emuladores y apps que piden una carpeta (Dolphin, Limbo…)"), this::explainProvider);
                settingsRow(col, L.t("Gestionar permisos de apps con carpetas / emuladores"), L.t("Cambiar la carpeta de cada app o quitarle el permiso"), this::showGrants);
            }
            case 6 -> {
                settingsSection(col, L.t("Almacenamiento"));
                settingsRow(col, L.t("Borrar archivos temporales"), Ui.size(dirSize(getCacheDir())) + L.t(" en caché (vistas previas, descargas de la nube y FTP)"), () -> {
                    deleteChildren(getCacheDir());
                    Toast.makeText(this, L.t("Archivos temporales borrados"), Toast.LENGTH_SHORT).show();
                    refresh.run();
                });
            }
            default -> {
                settingsSection(col, L.t("Acerca de"));
                settingsRow(col, "Bóveda " + BuildConfig.VERSION_NAME, L.t("Gestor de archivos · compilación ") + BuildConfig.VERSION_CODE, null);
                addCard(col, Studio.row(this, null));
                settingsRow(col, "🎬  " + L.t("Créditos"), L.t("Librerías, servicios, herramientas y quién hizo Bóveda"), this::showCredits);
                settingsRow(col, "🔒  " + L.t("Política de privacidad y seguridad"), L.t("Qué hace Bóveda con tu información"), () -> Legal.show(this, Legal.PRIVACY));
                settingsRow(col, "📄  " + L.t("Términos y condiciones"), L.t("Las reglas para usar la app"), () -> Legal.show(this, Legal.TERMS));
            }
        }
    }

    /** Tutoriales animados, sin sonido: carteles flotantes y un dedo que toca los botones. */
    private void tutorialSettings(LinearLayout col, Runnable refresh) {
        java.util.List<Tutorials.Tutorial> all = Tutorials.all();
        java.util.Set<String> seen = Tutorials.seen(this);
        int done = 0;
        for (Tutorials.Tutorial t : all) if (seen.contains(t.id)) done++;
        TextView head = Ui.text(this, done + L.t(" de ") + all.size() + L.t(" vistos"), 13, Ui.SUB, false);
        head.setPadding(dp(6), dp(4), 0, dp(10));
        col.addView(head);
        for (Tutorials.Tutorial t : all) {
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(14), dp(13), dp(12), dp(13));
            r.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(16))));
            TextView badge = Ui.text(this, t.emoji, 22, Color.WHITE, false);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.gradient(dp(12), t.c1, t.c2));
            r.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout texts = Ui.column(this);
            texts.setPadding(dp(14), 0, 0, 0);
            texts.addView(Ui.text(this, t.title, 15.5f, Ui.TEXT, true));
            TextView sub = Ui.text(this, t.sub, 12.5f, Ui.SUB, false);
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(sub);
            r.addView(texts, Ui.weight(1));
            r.addView(Ui.text(this, seen.contains(t.id) ? "✅" : "▶", 18, Ui.TEXT, false));
            r.setOnClickListener(v -> Tutorials.play(this, t, refresh));
            addCard(col, r);
        }
    }

    /** Ajustes experimentales y de prueba. El modo claro vive aquí… y de aquí no sale. */
    private void labSettings(LinearLayout col) {
        TextView warn = Ui.text(this, L.t("🧪 Estas funciones están en pruebas. Pueden fallar, cambiar o desaparecer en cualquier versión."),
                12.5f, Ui.WARN, false);
        warn.setPadding(dp(14), dp(12), dp(14), dp(12));
        warn.setBackground(Ui.round(0x1AFFB547, dp(14)));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.setMargins(0, dp(8), 0, 0);
        col.addView(warn, wl);
        settingsSection(col, L.t("Animaciones"));
        LinearLayout ar = settingsCard(L.t("Modo súper animaciones"), L.t("Brillos, degradados y chispas · puede gastar mucha RAM y GPU"));
        android.widget.Switch asw = new android.widget.Switch(this);
        asw.setChecked(prefs.superAnim());
        asw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        asw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, 0xFF3A4458}));
        final boolean[] quiet = {false};
        asw.setOnCheckedChangeListener((b, on) -> {
            if (quiet[0]) return;
            if (!on) {
                prefs.setSuperAnim(false);
                return;
            }
            // al encender: primero el aviso; solo si lo acepta queda activado
            quiet[0] = true;
            asw.setChecked(false);
            quiet[0] = false;
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("⚠️ Modo súper animaciones"))
                    .setMessage(L.t("Este modo agrega muchas animaciones: selecciones que brillan, barras de progreso con degradado y chispas, imágenes que aparecen poco a poco y más.\n\nPuede ocupar mucha memoria RAM y mucha GPU, hacer que la app vaya más lenta en celulares modestos y gastar más batería.\n\nEs una función experimental: cambiará mucho entre versiones. Puedes apagarla cuando quieras.\n\n¿Activarlo?"))
                    .setNegativeButton(L.t("Cancelar"), null)
                    .setPositiveButton(L.t("Activar"), (d, w) -> {
                        prefs.setSuperAnim(true);
                        quiet[0] = true;
                        asw.setChecked(true);
                        quiet[0] = false;
                        Toast.makeText(this, L.t("Modo súper animaciones activado"), Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
        LinearLayout.LayoutParams asl = new LinearLayout.LayoutParams(-2, -2);
        asl.setMargins(dp(10), 0, 0, 0);
        ar.addView(asw, asl);
        ar.setOnClickListener(v -> asw.toggle());
        addCard(col, ar);
        if (Fx.systemOff(this)) {
            TextView off = Ui.text(this, L.t("⚠ Tu Android tiene las animaciones apagadas (Ajustes → Accesibilidad → Quitar animaciones). Por eso el modo no se ve. Puedes desactivar eso en Android o usar el interruptor de abajo."),
                    12.5f, Ui.WARN, false);
            off.setPadding(dp(14), dp(10), dp(14), dp(10));
            off.setBackground(Ui.round(0x1AFFB547, dp(14)));
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
            ol.setMargins(0, dp(6), 0, 0);
            col.addView(off, ol);
        }
        LinearLayout fr = settingsCard(L.t("Mostrar aunque Android las tenga apagadas"), L.t("Solo para Bóveda · si el movimiento te molesta, déjalo apagado"));
        android.widget.Switch fsw = new android.widget.Switch(this);
        fsw.setChecked(prefs.animForce());
        fsw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        fsw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, 0xFF3A4458}));
        fsw.setOnCheckedChangeListener((b, on) -> prefs.setAnimForce(on));
        LinearLayout.LayoutParams fsl = new LinearLayout.LayoutParams(-2, -2);
        fsl.setMargins(dp(10), 0, 0, 0);
        fr.addView(fsw, fsl);
        fr.setOnClickListener(v -> fsw.toggle());
        addCard(col, fr);
        settingsSection(col, L.t("Apariencia"));
        LinearLayout r = settingsCard(L.t("Permitir modo claro"), L.t("Experimental · puede causar distorsiones visuales"));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(false);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.WARN, 0xFF3A4458}));
        sw.setOnCheckedChangeListener((b, on) -> {
            if (!on) return;
            sw.setChecked(false);
            lightModeWarning();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMargins(dp(10), 0, 0, 0);
        r.addView(sw, sl);
        r.setOnClickListener(v -> sw.toggle());
        addCard(col, r);
    }

    /** Tres avisos cada vez más dramáticos, y al final Bóveda se queda en modo oscuro. */
    private void lightModeWarning() {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("⚠️ ADVERTENCIA: modo claro"))
                .setMessage(L.t("Estás a punto de activar una función experimental e inestable.\n\nEl modo claro puede provocar distorsiones visuales, errores de renderizado, colores invertidos, textos invisibles, parpadeos de pantalla y un aumento repentino del brillo capaz de deslumbrarte a ti y a cualquiera que esté cerca.\n\nForja Estudios no se hace responsable por ojos irritados, baterías agotadas ni por la pérdida de la elegancia oscura de tu celular.\n\n¿De verdad quieres continuar?"))
                .setNegativeButton(L.t("Cancelar (recomendado)"), null)
                .setPositiveButton(L.t("Activar de todos modos"), (d1, w1) -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                        .setTitle(L.t("☠️ ¿Completamente seguro?"))
                        .setMessage(L.t("Esta es tu última oportunidad de volver a la oscuridad.\n\nUna vez que el blanco entra, ya nada vuelve a ser igual."))
                        .setNegativeButton(L.t("Volver a la oscuridad 🌙"), null)
                        .setPositiveButton(L.t("Sí, estoy seguro"), (d2, w2) -> {
                            android.app.ProgressDialog pd = new android.app.ProgressDialog(this, R.style.Theme_Boveda_Dialog);
                            pd.setMessage(L.t("Activando el modo claro…"));
                            pd.setCancelable(false);
                            pd.show();
                            main.postDelayed(() -> {
                                pd.dismiss();
                                new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                                        .setTitle(L.t("❌ Error de renderizado"))
                                        .setMessage(L.t("El modo claro no pudo iniciarse: Bóveda detectó demasiado blanco y se protegió quedándose en modo oscuro.\n\nEsta función seguirá en pruebas."))
                                        .setPositiveButton(L.t("Está bien 🌙"), null)
                                        .setCancelable(false)
                                        .show();
                            }, 2600);
                        })
                        .show())
                .show();
    }

    private String languageName(String code) {
        for (String[] l : L.LANGS) {
            if (l[0].equals(code)) return l[0].equals("auto") ? L.t("Automático") + " · " + languageName(L.code()) : l[1];
        }
        return code;
    }

    /** Elegir idioma: Bóveda se reinicia sola para cambiar todos los textos. */
    private void pickLanguage() {
        String[] names = new String[L.LANGS.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = i == 0 ? L.t("Automático (idioma del teléfono)") : L.LANGS[i][1];
            if (L.LANGS[i][0].equals(prefs.language())) sel = i;
        }
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Idioma"))
                .setSingleChoiceItems(names, sel, (dd, w) -> {
                    dd.dismiss();
                    if (L.LANGS[w][0].equals(prefs.language())) return;
                    prefs.setLanguage(L.LANGS[w][0]);
                    Toast.makeText(this, L.t("Cambiando el idioma…"), Toast.LENGTH_SHORT).show();
                    Intent restart = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (restart != null) {
                        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(restart);
                    }
                    main.postDelayed(() -> Runtime.getRuntime().exit(0), 300);
                })
                .setNegativeButton(L.t("Cancelar"), null)
                .show();
    }

    /**
     * Pestañas: el interruptor las prende o apaga; tocar el resto de la fila abre cuántas usar (2 a 8).
     */
    private void tabsSetting(LinearLayout col, Runnable refresh) {
        boolean on = prefs.tabsEnabled();
        LinearLayout r = settingsCard(L.t("Pestañas"), on ? prefs.tabCount() + L.t(" pestañas · toca para cambiar cuántas") : L.t("Apagadas: una sola vista"));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, 0xFF3A4458}));
        sw.setOnCheckedChangeListener((b, c) -> {
            prefs.setTabsEnabled(c);
            refresh.run();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMargins(dp(10), 0, 0, 0);
        r.addView(sw, sl);
        r.setOnClickListener(v -> {
            String[] names = new String[Tabs.MAX - 1];
            for (int i = 0; i < names.length; i++) names[i] = (i + 2) + L.t(" pestañas");
            new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("¿Cuántas pestañas?"))
                    .setSingleChoiceItems(names, prefs.tabCount() - 2, (dd, w) -> {
                        prefs.setTabCount(w + 2);
                        prefs.setTabsEnabled(true);
                        dd.dismiss();
                        refresh.run();
                    })
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show();
        });
        addCard(col, r);
    }

    private static long dirSize(java.io.File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        java.io.File[] k = f.listFiles();
        if (k != null) for (java.io.File c : k) s += dirSize(c);
        return s;
    }

    private static void deleteChildren(java.io.File dir) {
        java.io.File[] k = dir.listFiles();
        if (k == null) return;
        for (java.io.File c : k) {
            if (c.isDirectory()) deleteChildren(c);
            //noinspection ResultOfMethodCallIgnored
            c.delete();
        }
    }

    private void settingsSection(LinearLayout col, String title) {
        TextView t = Ui.text(this, title.toUpperCase(), 12, Ui.ACCENT2, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), dp(20), 0, dp(8));
        col.addView(t);
    }

    private LinearLayout settingsCard(String title, String sub) {
        LinearLayout r = Ui.row(this);
        r.setPadding(dp(16), dp(13), dp(12), dp(13));
        r.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(16))));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(this, sub, 12.5f, Ui.SUB, false);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, Ui.weight(1));
        return r;
    }

    private void addCard(LinearLayout col, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        col.addView(card, lp);
    }

    private void settingsRow(LinearLayout col, String title, String sub, Runnable action) {
        LinearLayout r = settingsCard(title, sub);
        if (action != null) {
            ImageView ch = new ImageView(this);
            ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, 0xFF4A5670));
            r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
            r.setOnClickListener(v -> action.run());
        } else {
            r.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        }
        addCard(col, r);
    }

    private void settingsSwitch(LinearLayout col, String title, String sub, boolean on, java.util.function.Consumer<Boolean> set) {
        LinearLayout r = settingsCard(title, sub);
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, 0xFF3A4458}));
        sw.setOnCheckedChangeListener((b, c) -> set.accept(c));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMargins(dp(10), 0, 0, 0);
        r.addView(sw, sl);
        r.setOnClickListener(v -> sw.toggle());
        addCard(col, r);
    }

    private void showMainMenu() {
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (!picker()) {
            labels.add(L.t("⚙  Ajustes"));
            acts.add(this::showSettings);
        }
        labels.add((prefs.showHidden() ? "✓ " : "") + L.t("Mostrar archivos ocultos"));
        acts.add(() -> {
            prefs.setShowHidden(!prefs.showHidden());
            if (path != null) load(path, true);
        });
        labels.add(L.t("Ordenar por: ") + Prefs.SORT_NAMES[prefs.sort()] + (prefs.sortDesc() ? L.t(" (descendente)") : ""));
        acts.add(this::chooseSort);
        if (path != null && !picker()) {
            labels.add(prefs.isPinned(path) ? L.t("Desanclar esta carpeta") : L.t("Anclar esta carpeta"));
            acts.add(() -> {
                prefs.togglePinned(path);
                buildChips();
            });
            labels.add(prefs.isFavorite(path) ? L.t("Quitar de favoritas") : L.t("Añadir a favoritas"));
            acts.add(() -> prefs.toggleFavorite(path));
            labels.add(L.t("Seleccionar todo"));
            acts.add(this::selectAll);
        }
        if (!picker()) {
            labels.add((prefs.verify() ? "✓ " : "") + L.t("Verificar cada copia (más seguro)"));
            acts.add(() -> {
                prefs.setVerify(!prefs.verify());
                Toast.makeText(this, prefs.verify() ? L.t("Cada archivo copiado se vuelve a leer y se compara con el original")
                        : L.t("Verificación apagada: copias más rápidas"), Toast.LENGTH_LONG).show();
            });
            labels.add(L.t("Shizuku: ") + ShizukuHelper.status());
            acts.add(this::useShizuku);
            labels.add(L.t("Hacer a Bóveda el gestor predeterminado"));
            acts.add(this::showDefaultSetup);
            labels.add(L.t("Dar carpetas a otras apps (emuladores…)"));
            acts.add(this::explainProvider);
            labels.add(L.t("Gestionar permisos de apps con carpetas / emuladores"));
            acts.add(this::showGrants);
        }
        labels.add(L.t("Inicio"));
        acts.add(this::showHome);
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    private void chooseSort() {
        new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Ordenar por"))
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
                .setTitle(L.t("Dar carpetas a otras apps"))
                .setMessage(L.t("Cuando una app (un emulador, por ejemplo) te pida elegir una carpeta o un archivo:\n\n")
                        + L.t("1. En el selector de Android, abre el menú (☰) de la izquierda.\n")
                        + L.t("2. Elige «Bóveda».\n")
                        + L.t("3. Entra a la carpeta que quieras y toca «Usar esta carpeta».\n\n")
                        + L.t("Con Bóveda puedes darle carpetas que el selector normal no deja (la raíz, Descargas) y, ")
                        + L.t("si Bóveda tiene Shizuku o el permiso, también Android/data y Android/obb.\n\n")
                        + L.t("Para que Bóveda sea tu gestor de archivos predeterminado, ábrelo desde «Abrir con» la primera vez ")
                        + L.t("y elige «Siempre»."))
                .setPositiveButton(L.t("Entendido"), null)
                .show();
    }

    // ------------------------------------------------------------------ créditos

    /** Como al final de una película: lo que hace posible a Bóveda y quién la hizo. */
    private void showCredits() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(18), dp(16), dp(18), dp(22));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.SURFACE, 0);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        View grip = new View(this);
        grip.setBackground(Ui.round(Ui.STROKE, dp(3)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(42), dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = dp(14);
        box.addView(grip, gp);
        box.addView(Ui.text(this, "🎬  " + L.t("Créditos"), 20, Ui.TEXT, true));
        TextView sub = Ui.text(this, L.t("Las librerías, servicios y herramientas que hacen posible a Bóveda"), 13, Ui.SUB, false);
        sub.setPadding(0, dp(4), 0, dp(8));
        box.addView(sub);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        sv.addView(col);

        creditsSection(col, "📚  " + L.t("Librerías"));
        creditsItem(col, "Apache Commons Compress", "The Apache Software Foundation · Apache 2.0",
                L.t("Abrir y crear archivos comprimidos: 7z, tar, gz, bz2, lz4…"));
        creditsItem(col, "XZ for Java", "Lasse Collin · Tukaani Project", L.t("Archivos .xz y .lzma"));
        creditsItem(col, "Shizuku API", "RikkaApps", L.t("Entrar a Android/data y Android/obb sin root"));
        creditsItem(col, "apksig", "The Android Open Source Project · Apache 2.0", L.t("Firmar APK para que se puedan instalar"));
        creditsItem(col, "AndroidX Core", "Google · Apache 2.0", L.t("Piezas básicas de la app"));

        creditsSection(col, "☁️  " + L.t("Servicios en la nube"));
        creditsItem(col, "Google Drive", "Google", null);
        creditsItem(col, "OneDrive", "Microsoft", null);
        creditsItem(col, "MEGA", "Mega Limited", null);
        creditsItem(col, "MediaFire", "MediaFire, LLC", null);

        creditsSection(col, "🛠  " + L.t("Herramientas"));
        creditsItem(col, "Android SDK y Gradle", "Google · Gradle, Inc.", L.t("Con lo que se construye Bóveda"));
        creditsItem(col, "GitHub Actions", "GitHub", L.t("Compila y publica cada versión"));

        creditsSection(col, "❤️  " + L.t("Hecho por Forja Estudios"));
        creditsPerson(col, "santiagogd2513-cpu", L.t("Idea, diseño y pruebas · cazador oficial de errores y director creativo"), 0xFFFFB547, 0xFFFF7A45);
        creditsPerson(col, "Claude · Anthropic", L.t("Código y corrección de errores · constructor de puentes rusos con fe y amor"), 0xFF4C9DFF, 0xFFB47CFF);

        TextView thanks = Ui.text(this, L.t("Gracias a todos ellos, y a ti por usar Bóveda 🌙"), 13, Ui.SUB, false);
        thanks.setGravity(Gravity.CENTER);
        thanks.setPadding(0, dp(18), 0, dp(6));
        col.addView(thanks);

        box.addView(sv, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.68f)));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    private void creditsSection(LinearLayout col, String title) {
        TextView t = Ui.text(this, title, 13, Ui.ACCENT, true);
        t.setPadding(dp(4), dp(16), 0, dp(8));
        col.addView(t);
    }

    private void creditsItem(LinearLayout col, String name, String by, String what) {
        LinearLayout c = Ui.column(this);
        c.setPadding(dp(14), dp(11), dp(14), dp(11));
        c.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE));
        c.addView(Ui.text(this, name, 14.5f, Ui.TEXT, true));
        TextView b = Ui.text(this, by, 12, Ui.SUB, false);
        b.setPadding(0, dp(2), 0, 0);
        c.addView(b);
        if (what != null) {
            TextView x = Ui.text(this, what, 12.5f, Ui.TEXT, false);
            x.setAlpha(0.8f);
            x.setPadding(0, dp(5), 0, 0);
            c.addView(x);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        col.addView(c, lp);
    }

    private void creditsPerson(LinearLayout col, String name, String role, int c1, int c2) {
        LinearLayout c = Ui.row(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14), dp(13), dp(14), dp(13));
        c.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(16), dp(1), Ui.STROKE));
        TextView badge = Ui.text(this, name.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 17, 0xFF0B0D14, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.gradient(dp(14), c1, c2));
        c.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout t = Ui.column(this);
        t.setPadding(dp(12), 0, 0, 0);
        t.addView(Ui.text(this, name, 15, Ui.TEXT, true));
        TextView rl = Ui.text(this, role, 12.5f, Ui.SUB, false);
        rl.setPadding(0, dp(3), 0, 0);
        t.addView(rl);
        c.addView(t, Ui.weight(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        col.addView(c, lp);
    }

    // ------------------------------------------------------------------ permisos de apps con carpetas

    private static final int REQ_GRANT_FOLDER = 43;
    private Grants.Grant changing;
    private LinearLayout grantsList;

    /** Todas las apps a las que Bóveda les dio una carpeta, con «Cambiar carpeta» y «Quitar permiso». */
    private void showGrants() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(18), dp(16), dp(18), dp(22));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.SURFACE, 0);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        View grip = new View(this);
        grip.setBackground(Ui.round(Ui.STROKE, dp(3)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(42), dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = dp(14);
        box.addView(grip, gp);
        box.addView(Ui.text(this, L.t("Apps con carpetas"), 20, Ui.TEXT, true));
        TextView sub = Ui.text(this, L.t("Emuladores y apps a las que Bóveda les dio una carpeta"), 13, Ui.SUB, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        box.addView(sub);
        ScrollView sv = new ScrollView(this);
        grantsList = Ui.column(this);
        sv.addView(grantsList);
        box.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(dp(460), (int) (getResources().getDisplayMetrics().heightPixels * 0.65f))));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.setOnDismissListener(x -> grantsList = null);
        refreshGrants();
        d.show();
    }

    private void refreshGrants() {
        if (grantsList == null) return;
        grantsList.removeAllViews();
        java.util.List<Grants.Grant> list = Grants.active(this);
        if (list.isEmpty()) {
            LinearLayout empty = Ui.column(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(10), dp(26), dp(10), dp(26));
            empty.addView(Ui.text(this, "🗂", 40, Ui.TEXT, false));
            TextView t = Ui.text(this, L.t("Todavía ninguna app tiene una carpeta de Bóveda."), 14.5f, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(4));
            empty.addView(t);
            TextView h = Ui.text(this, L.t("Cuando un emulador te pida una carpeta y la elijas con Bóveda, aparecerá aquí."), 13, Ui.SUB, false);
            h.setGravity(Gravity.CENTER);
            empty.addView(h);
            grantsList.addView(empty);
            return;
        }
        for (Grants.Grant g : list) {
            LinearLayout card = Ui.column(this);
            card.setPadding(dp(14), dp(14), dp(14), dp(12));
            card.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(18), dp(1), Ui.STROKE));
            LinearLayout top = Ui.row(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            boolean installed = true;
            try {
                icon.setImageDrawable(getPackageManager().getApplicationIcon(g.pkg));
            } catch (Exception e) {
                installed = false;
                icon.setImageDrawable(Ui.icon(this, R.drawable.ic_folder, Ui.SUB));
            }
            top.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout names = Ui.column(this);
            names.setPadding(dp(12), 0, 0, 0);
            TextView name = Ui.text(this, appName(g.pkg), 15.5f, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(name);
            TextView pkg = Ui.text(this, installed ? g.pkg : g.pkg + L.t(" · desinstalada"), 11.5f, Ui.SUB, false);
            pkg.setSingleLine(true);
            pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            names.addView(pkg);
            top.addView(names, Ui.weight(1));
            card.addView(top);
            // la carpeta (y, si se cambió, la de antes)
            LinearLayout folder = Ui.row(this);
            folder.setGravity(Gravity.CENTER_VERTICAL);
            folder.setPadding(dp(10), dp(8), dp(10), dp(8));
            folder.setBackground(Ui.round(Ui.BG, dp(12)));
            folder.addView(Ui.text(this, "📁", 15, Ui.TEXT, false));
            TextView where = Ui.text(this, pretty(g.real()), 13, Ui.ACCENT, true);
            where.setPadding(dp(8), 0, 0, 0);
            where.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            where.setSingleLine(true);
            folder.addView(where, Ui.weight(1));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
            fp.topMargin = dp(12);
            card.addView(folder, fp);
            if (g.target != null) {
                TextView was = Ui.text(this, L.t("Cambiada (antes: ") + pretty(g.root) + ")", 11.5f, Ui.WARN, false);
                was.setPadding(dp(4), dp(6), 0, 0);
                card.addView(was);
            }
            TextView since = Ui.text(this, L.t("Desde ") + Ui.date(g.since), 11.5f, Ui.SUB, false);
            since.setPadding(dp(4), dp(6), 0, 0);
            card.addView(since);
            // los dos botones
            LinearLayout btns = Ui.row(this);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(12);
            TextView change = grantButton(L.t("Cambiar carpeta"), Ui.ACCENT, v -> {
                changing = g;
                Toast.makeText(this, L.t("Elige la nueva carpeta y toca «Usar esta carpeta»"), Toast.LENGTH_LONG).show();
                Intent i = new Intent(this, PickerActivity.class).setAction(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(i, REQ_GRANT_FOLDER);
            });
            TextView revoke = grantButton(L.t("Quitar permiso"), Ui.BAD, v -> new AlertDialog.Builder(this, R.style.Theme_Boveda_Dialog)
                    .setTitle(L.t("¿Quitar el permiso?"))
                    .setMessage(appName(g.pkg) + L.t(" ya no podrá leer ni escribir en ") + pretty(g.real())
                            + L.t(". Si la vuelves a elegir con Bóveda desde esa app, recupera el permiso."))
                    .setPositiveButton(L.t("Quitar"), (dd, ww) -> {
                        Grants.revoke(this, g);
                        try {
                            Uri tree = DocumentsContract.buildTreeDocumentUri(StorageProvider.authority(this), g.root);
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                revokeUriPermission(g.pkg, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            }
                        } catch (Exception ignored) {
                        }
                        Toast.makeText(this, L.t("Permiso quitado"), Toast.LENGTH_SHORT).show();
                        refreshGrants();
                    })
                    .setNegativeButton(L.t("Cancelar"), null)
                    .show());
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(42), 1);
            l1.rightMargin = dp(5);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(42), 1);
            l2.leftMargin = dp(5);
            btns.addView(change, l1);
            btns.addView(revoke, l2);
            card.addView(btns, bp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(10);
            grantsList.addView(card, cp);
        }
    }

    private TextView grantButton(String label, int color, View.OnClickListener l) {
        TextView b = Ui.text(this, label, 13.5f, color, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.ripple(Ui.roundStroke((color & 0x00FFFFFF) | 0x1A000000, dp(14), dp(1), (color & 0x00FFFFFF) | 0x66000000),
                Ui.round(0xFFFFFFFF, dp(14))));
        b.setOnClickListener(l);
        return b;
    }

    /** La ruta como la ve la gente: «Almacenamiento interno/…» en vez de /storage/emulated/0/… */
    private static String pretty(String p) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (p.equals(root)) return L.t("Almacenamiento interno");
        if (p.startsWith(root + "/")) return L.t("Almacenamiento interno") + p.substring(root.length());
        return p;
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
        head.addView(Ui.text(this, L.t("Transferencias"), 20, Ui.TEXT, true), Ui.weight(1));
        TextView clear = Ui.text(this, L.t("Limpiar"), 13, Ui.ACCENT, true);
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
            TextView t = Ui.text(this, L.t("No hay copias ni movimientos."), 14, Ui.SUB, false);
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
                Toast.makeText(this, L.t("Cancelando… (lo que quedó a medias se borra)"), Toast.LENGTH_SHORT).show();
            }));
            c.addView(top);
            View bar = new UsageBar(this, j.progress(), j.state == 3 ? Ui.BAD : j.state == 2 ? Ui.GOOD : Ui.ACCENT);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
            bl.setMargins(0, dp(8), 0, dp(6));
            c.addView(bar, bl);
            String status = switch (j.state) {
                case 0 -> "En cola";
                case 1 -> (j.totalBytes > 0 ? Ui.size(j.doneBytes) + " de " + Ui.size(j.totalBytes) + " · " + Ui.speed(j.speed)
                        : j.doneItems + L.t(" de ") + j.totalItems) + (j.current.isEmpty() ? "" : "\n" + j.current);
                case 2 -> "Listo ✓" + (j.kind == TransferEngine.Kind.COPY || j.kind == TransferEngine.Kind.MOVE
                        ? (prefs.verify() ? L.t("  ·  verificado") : "") : "");
                case 3 -> "Error: " + j.error;
                default -> L.t("Cancelado");
            };
            c.addView(Ui.text(this, status, 12.5f, j.state == 3 ? Ui.BAD : j.state == 2 ? Ui.GOOD : Ui.SUB, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, 0);
            transfersList.addView(c, lp);
        }
    }

    // ------------------------------------------------------------------ pestañas

    /** Después de cambiar los ajustes de pestañas: rehace la barra (y si la actual ya no existe, vuelve a la primera). */
    private void rebuildTabs() {
        if (tabStrip == null || picker()) return;
        int before = Tabs.current;
        Tabs.path[before] = path;
        Tabs.configure(prefs);
        android.view.ViewGroup parent = (android.view.ViewGroup) tabStrip.getParent();
        int at = parent.indexOfChild(tabStrip);
        parent.removeView(tabStrip);
        tabStrip = Tabs.strip(this, tabViews, this::switchTab);
        parent.addView(tabStrip, at);
        if (Tabs.current != before) showTab(Tabs.path[Tabs.current]);
        updateTabs();
    }

    private String tabName(int t) {
        String p = Tabs.path[t];
        if (p == null || Tabs.MEDIAFIRE.equals(p)) return Tabs.label(p);
        Volume v = volumeOf(p);
        return v != null && v.path.equals(p) ? v.name : Tabs.label(p);
    }

    private void updateTabs() {
        if (tabStrip == null || picker()) return;
        String[] names = new String[Tabs.MAX];
        for (int i = 0; i < Tabs.count(); i++) names[i] = tabName(i);
        Tabs.paint(this, tabViews, names);
    }

    /** Abre MediaFire en la pestaña actual (vuelve a donde estaba con «atrás»). */
    private void openMediaFire() {
        if (!picker()) {
            Tabs.mfReturn[Tabs.current] = path;
            Tabs.path[Tabs.current] = Tabs.MEDIAFIRE;
            Tabs.mfStack[Tabs.current] = null;
        }
        startActivity(new Intent(this, CloudActivity.class));
    }

    /** Cambia de pestaña; lo copiado o cortado sigue listo para pegar en la otra (también en MediaFire). */
    private void switchTab(int t) {
        if (picker() || t < 0 || t >= Tabs.count() || t == Tabs.current) return;
        int dir = t > Tabs.current ? 1 : -1;
        if (path != null) scroll.put(path, list.getFirstVisiblePosition());
        Tabs.path[Tabs.current] = path;
        Tabs.current = t;
        selected.clear();
        search.setText("");
        search.setVisibility(View.GONE);
        if (Tabs.isMediaFire(t)) {
            updateTabs();
            startActivity(new Intent(this, CloudActivity.class).putExtra(CloudActivity.EXTRA_DIR, dir));
            overridePendingTransition(0, 0);
            return;
        }
        showTab(Tabs.path[t]);
        content.animate().cancel();
        content.setTranslationX(dir * content.getWidth() * 0.35f);
        content.setAlpha(0.3f);
        content.animate().translationX(0).alpha(1).setDuration(180).start();
        if (!clip.isEmpty()) {
            Toast.makeText(this, L.t("Pestaña ") + (t + 1) + L.t(": entra a la carpeta y pega"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showTab(String p) {
        if (p == null || !hasStorage()) showHome();
        else {
            path = p;
            load(p, false);
        }
        updateTabs();
    }

    /** Lo copiado o cortado en el explorador, para subirlo a MediaFire desde otra pestaña (se usa una vez). */
    static List<String> takeClipForCloud() {
        List<String> out = new ArrayList<>(clip);
        clip.clear();
        return out;
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
        if (path == null && homePage != null && !picker()) {
            homePage = null;
            showHome();
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
                // las miniaturas con esquinas redondeadas
                h.icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline o) {
                        o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.min(view.getWidth(), view.getHeight()) * 0.28f);
                    }
                });
                h.icon.setClipToOutline(true);
                h.play = new ImageView(MainActivity.this);
                h.play.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_play, Color.WHITE));
                h.play.setBackground(Ui.round(0x99000000, dp(9)));
                h.play.setPadding(dp(3), dp(3), dp(3), dp(3));
                h.play.setVisibility(View.GONE);
                h.iconBox.addView(h.play, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
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
            // en Android/data, obb y media: el ícono y el nombre de la app dueña de cada carpeta
            AppIcons.App app = e.dir && appsFolder ? AppIcons.of(MainActivity.this, e.name) : null;
            FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) h.icon.getLayoutParams();
            int tk = app == null && prefs.thumbnails() ? Thumbs.kind(e) : Thumbs.NONE;
            Bitmap thumb = tk != Thumbs.NONE ? Thumbs.cached(e) : null;
            h.icon.setTag(e.path);
            h.play.setVisibility(View.GONE);
            h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int want = app != null ? dp(40) : dp(22);
            if (ilp.width != want) {
                ilp.width = want;
                ilp.height = want;
                h.icon.setLayoutParams(ilp);
            }
            if (app != null) {
                h.icon.setImageDrawable(app.icon);
                h.iconBox.setBackground(null);
                h.icon.setAlpha(1f);
                h.name.setText(app.label);
                h.name.setTextColor(Ui.TEXT);
                h.info.setText(e.name);
            } else {
                h.icon.setImageDrawable(Ui.icon(MainActivity.this, FileTypes.icon(e), color));
                h.iconBox.setBackground(Ui.round((color & 0x00FFFFFF) | (e.hidden() ? 0x14000000 : 0x26000000), dp(14)));
                h.icon.setAlpha(e.hidden() ? 0.55f : 1f);
                h.name.setText(e.name);
                h.name.setTextColor(e.hidden() ? Ui.SUB : Ui.TEXT);
                String n = e.dir ? countOf(e.path) : null;
                h.info.setText(e.dir ? (appsFolder && e.name.contains(".") ? L.t("App no instalada  ·  ") : "")
                        + (n != null && !n.isEmpty() ? n + "  ·  " : "") + Ui.date(e.modified)
                        : Ui.size(e.size) + "  ·  " + Ui.date(e.modified));
                if (tk != Thumbs.NONE) {
                    if (thumb != null) showThumb(h, thumb, tk, e.hidden());
                    else if (!Thumbs.failedBefore(e) && (Thumbs.local(e.path) || (e.path.startsWith("/") && Paths.restricted(e.path))
                            || (VaultFs.isVault(e.path) && tk == Thumbs.IMAGE))) {
                        final String p = e.path;
                        final int kk = tk;
                        final boolean hid = e.hidden();
                        Thumbs.load(MainActivity.this, e, dp(88), bmp -> {
                            if (p.equals(h.icon.getTag())) {
                                showThumb(h, bmp, kk, hid);
                                // con el modo de animaciones, la imagen aparece poco a poco
                                if (prefs.superAnim() && Fx.on(MainActivity.this)) {
                                    h.icon.setAlpha(0f);
                                    h.icon.animate().alpha(hid ? 0.55f : 1f).setDuration(260).start();
                                }
                            }
                        });
                    }
                }
            }
            h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
            boolean fav = e.dir && (prefs.isPinned(e.path) || prefs.isFavorite(e.path));
            h.star.setVisibility(fav ? View.VISIBLE : View.GONE);
            if (fav) h.star.setImageDrawable(Ui.icon(MainActivity.this,
                    prefs.isPinned(e.path) ? R.drawable.ic_pin : R.drawable.ic_star, prefs.isPinned(e.path) ? Ui.ACCENT2 : Ui.WARN));
            v.setBackground(sel ? (prefs.superAnim() && Fx.on(MainActivity.this) ? new Fx.SelectGlow(dp(14)) : Ui.round(0xFF16264A, dp(14))) : Ui.rippleRound(0x00000000, dp(14)));
            return v;
        }
    }

    // ------------------------------------------------------------------ cuántos elementos tiene cada carpeta

    private final Map<String, String> counts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> counting = new java.util.HashSet<>();
    private final ExecutorService counter = Executors.newFixedThreadPool(2);
    private boolean countRefresh;

    /** «3 carpetas · 12 archivos» (se calcula aparte, sin frenar la lista). */
    private String countOf(String dir) {
        if (!prefs.showCounts()) return null;
        String c = counts.get(dir);
        if (c != null || FtpServers.isFtp(dir) || DriveFs.isDrive(dir) || MegaFs.isMega(dir) || OneDriveFs.isOneDrive(dir)) return c;
        if (!counting.add(dir)) return null;
        final boolean hidden = prefs.showHidden();
        counter.execute(() -> {
            String txt = "";
            try {
                int d = 0, f = 0;
                java.io.File[] kids = Paths.restricted(dir) ? null : new java.io.File(dir).listFiles();
                if (kids != null) {
                    for (java.io.File k : kids) {
                        if (!hidden && k.getName().startsWith(".")) continue;
                        if (k.isDirectory()) d++;
                        else f++;
                    }
                } else {
                    for (Entry k : FsRouter.get(this, dir).list(dir)) {
                        if (k.name.endsWith(".boveda-parte") || (!hidden && k.hidden())) continue;
                        if (k.dir) d++;
                        else f++;
                    }
                }
                txt = countText(d, f);
            } catch (Exception ignored) {
            }
            final String t = txt;
            main.post(() -> {
                counts.put(dir, t);
                counting.remove(dir);
                if (!countRefresh) {
                    countRefresh = true;
                    main.postDelayed(() -> {
                        countRefresh = false;
                        adapter.notifyDataSetChanged();
                    }, 80);
                }
            });
        });
        return null;
    }

    static String countText(int d, int f) {
        if (d == 0 && f == 0) return L.t("Vacía");
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append(d == 1 ? L.t(" carpeta") : L.t(" carpetas"));
        if (f > 0) b.append(b.length() > 0 ? " · " : "").append(f).append(f == 1 ? L.t(" archivo") : L.t(" archivos"));
        return b.toString();
    }

    /** La carpeta actual guarda una carpeta por app (Android/data, obb, media). */
    private boolean appsFolder;

    /** Pone la miniatura en lugar del ícono del tipo de archivo. */
    private void showThumb(Holder h, Bitmap b, int kind, boolean hidden) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.icon.getLayoutParams();
        boolean icon = kind == Thumbs.APK || kind == Thumbs.JAR;
        int size = icon ? dp(40) : dp(44);
        if (lp.width != size) {
            lp.width = size;
            lp.height = size;
            h.icon.setLayoutParams(lp);
        }
        h.icon.setScaleType(icon ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        h.icon.setImageBitmap(b);
        h.icon.setAlpha(hidden ? 0.55f : 1f);
        h.iconBox.setBackground(null);
        h.play.setVisibility(kind == Thumbs.VIDEO ? View.VISIBLE : View.GONE);
    }

    private static final class Holder {
        FrameLayout iconBox;
        ImageView icon, check, star, play;
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
