package com.forja.arca;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Arca: mira desde el celular lo que hay en la memoria USB o en el disco duro de una Xbox 360, con el
 * nombre y el ícono de cada juego. Esta primera versión solo lee: no escribe nada en la unidad.
 */
public class MainActivity extends Activity {
    private static final String ACTION_PERM = "com.forja.arca.USB_PERMISO";
    private static final int REQ_TREE = 21;
    private static final Pattern HEX8 = Pattern.compile("[0-9A-Fa-f]{8}"), HEX16 = Pattern.compile("[0-9A-Fa-f]{16}");

    private UsbManager um;
    private TitleCache titles;
    private FrameLayout root, content;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    // lo que está abierto
    private UsbDisk usb;
    private List<Saf.FileSource> safFiles;
    private List<XboxDisk.Part> parts;
    private Xtaf fs;
    private String partName = "", sessionLabel = "";
    private int usbId = -1;
    private String path = "";
    private List<Xtaf.Entry> entries = new ArrayList<>();
    private final Map<String, Stfs> infos = new HashMap<>();
    private BaseAdapter adapter;
    private int generation;

    // pantallas y lo que hay abierto además del explorador
    private enum Mode { HOME, PARTS, BROWSER, SEARCH, RADIO, PACKAGE, EDITOR }
    private Mode mode = Mode.HOME;
    private StfsPackage pkg;
    private Xtaf.Entry pkgEntry;
    private int pkgParent = -1;
    private List<Idx> index;
    private Runnable afterDest;
    private static final int REQ_DEST = 22;

    // el cartel de «Preparando…»
    private View overlay;
    private TextView overlayDetail;
    private final AtomicBoolean skip = new AtomicBoolean();

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installCrashLog();
        um = getSystemService(UsbManager.class);
        titles = new TitleCache(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_PERM);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);
        showHome();
        if (!getSharedPreferences("arca", MODE_PRIVATE).getBoolean("preparado", false)) firstRun();
        showLastCrash();
        UsbDevice d = intentDevice(getIntent());
        if (d != null && UsbDisk.storageInterface(d) != null) openUsb(d);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.resume(this);
        Updater.check(this);
        Integrity.check(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        UsbDevice d = intentDevice(intent);
        if (d != null && UsbDisk.storageInterface(d) != null && !busy()) openUsb(d);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        closeSession();
        exec.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (overlay != null) {
            skip.set(true);
            return;
        }
        switch (mode) {
            case PACKAGE -> {
                if (pkg != null && pkgParent >= 0) {
                    pkgParent = pkg.items.get(pkgParent).parent;
                    showPackage();
                } else {
                    pkg = null;
                    showBrowser();
                }
            }
            case SEARCH, RADIO -> showBrowser();
            case EDITOR -> {
                if (fs != null) showBrowser();
                else showHome();
            }
            case BROWSER -> {
                if (!path.isEmpty()) {
                    int i = path.lastIndexOf('/');
                    browse(i < 0 ? "" : path.substring(0, i));
                } else if (parts != null && parts.size() > 1) {
                    showPartitions();
                } else {
                    closeSession();
                    showHome();
                }
            }
            case PARTS -> {
                closeSession();
                showHome();
            }
            default -> super.onBackPressed();
        }
    }

    private boolean busy() {
        return overlay != null;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ------------------------------------------------------------------ errores de la propia app

    private java.io.File crashFile() {
        return new java.io.File(getFilesDir(), "ultimo_error.txt");
    }

    private void installCrashLog() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev instanceof CrashLog) return;
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashLog(crashFile(), prev, ver));
    }

    private static final class CrashLog implements Thread.UncaughtExceptionHandler {
        private final java.io.File file;
        private final Thread.UncaughtExceptionHandler prev;
        private final String version;

        CrashLog(java.io.File file, Thread.UncaughtExceptionHandler prev, String version) {
            this.file = file;
            this.prev = prev;
            this.version = version;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                w.println("Arca " + version + " · Android " + Build.VERSION.RELEASE + " · " + Build.MODEL);
                w.println("Hilo: " + t.getName());
                e.printStackTrace(w);
            } catch (Exception ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        }
    }

    private void showLastCrash() {
        java.io.File f = crashFile();
        if (!f.exists()) return;
        String text;
        try {
            text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = String.valueOf(e);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        final String t = text.length() > 6000 ? text.substring(0, 6000) : text;
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("Arca se cerró la última vez")
                .setMessage("Copia esto y mándaselo a quien te ayuda con la app:\n\n" + t)
                .setPositiveButton("Copiar", (d, w) -> {
                    getSystemService(android.content.ClipboardManager.class)
                            .setPrimaryClip(android.content.ClipData.newPlainText("error", t));
                    Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ------------------------------------------------------------------ USB

    @SuppressWarnings("deprecation")
    private static UsbDevice intentDevice(Intent i) {
        if (i == null) return null;
        return Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                : (UsbDevice) i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            UsbDevice d = intentDevice(i);
            switch (String.valueOf(i.getAction())) {
                case ACTION_PERM -> {
                    if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) openNow(d);
                    else Aviso.show(MainActivity.this, root, "Sin permiso no puedo leer la unidad",
                            "Android te pregunta cada vez que conectas una memoria. Vuelve a tocarla y acepta el permiso.", "El usuario no dio el permiso USB.");
                }
                case UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (fs == null && !busy()) showHome();
                }
                case UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (d != null && d.getDeviceId() == usbId) {
                        closeSession();
                        hideOverlay();
                        showHome();
                        Aviso.show(MainActivity.this, root, "Se desconectó la memoria",
                                "El cable se movió o el celular dejó de darle corriente al puerto. Vuelve a conectarla y ábrela otra vez.",
                                "USB_DEVICE_DETACHED de la unidad abierta.");
                    } else if (fs == null && !busy()) {
                        showHome();
                    }
                }
                default -> {
                }
            }
        }
    };

    private void openUsb(UsbDevice d) {
        if (!um.hasPermission(d)) {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PERM).setPackage(getPackageName()),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            um.requestPermission(d, pi);
        } else {
            openNow(d);
        }
    }

    private void openNow(UsbDevice d) {
        if (busy()) return;
        closeSession();
        showOverlay("Abriendo la unidad…");
        exec.execute(() -> {
            try {
                UsbDisk disk = new UsbDisk(um, d);
                usb = disk;
                usbId = d.getDeviceId();
                List<XboxDisk.Part> found = XboxDisk.hdd(new DiskSource(disk));
                if (found.isEmpty()) {
                    throw new java.io.IOException("Esa unidad no es una partición de Xbox 360 (no encontré la marca XTAF)."
                            + " Si es una memoria USB, usa «Elegir carpeta de una memoria USB» y escoge su carpeta «Xbox360».");
                }
                sessionLabel = disk.name();
                parts = found;
                ui.post(() -> {
                    hideOverlay();
                    if (found.size() == 1) openPartition(0);
                    else showPartitions();
                });
            } catch (Throwable e) {
                ui.post(() -> fail(e));
            }
        });
    }

    private void closeSession() {
        generation++;
        if (usb != null) {
            usb.close();
            usb = null;
        }
        if (safFiles != null) {
            for (Saf.FileSource s : safFiles) s.close();
            safFiles = null;
        }
        parts = null;
        fs = null;
        usbId = -1;
        path = "";
        entries = new ArrayList<>();
        infos.clear();
        pkg = null;
        pkgEntry = null;
        pkgParent = -1;
        index = null;
        resetEditor();
    }

    private void fail(Throwable e) {
        hideOverlay();
        boolean wasOpen = fs != null;
        if (!wasOpen) {
            closeSession();
            showHome();
        }
        Aviso.show(this, root, e);
    }

    // ------------------------------------------------------------------ carpeta elegida con el selector (memoria USB ya montada)

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            Aviso.show(this, root, e);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if ((req == REQ_SLOT_A || req == REQ_SLOT_B) && res == RESULT_OK && data != null && data.getData() != null) {
            slotFromPhone(data.getData(), req == REQ_SLOT_A);
            return;
        }
        if (req == REQ_DEST) {
            Runnable next = afterDest;
            afterDest = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                Uri d = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(d, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getSharedPreferences("arca", MODE_PRIVATE).edit().putString("destino", d.toString()).apply();
                    if (next != null) next.run();
                } catch (Exception e) {
                    Aviso.show(this, root, "No pude guardar esa carpeta", "Android no dejó guardar el permiso de esa carpeta. Elige otra, por ejemplo una dentro de «Documentos».", Aviso.details(e));
                }
            }
            return;
        }
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        closeSession();
        showOverlay("Buscando la carpeta Xbox360…");
        exec.execute(() -> {
            try {
                List<Saf.FileSource> files = Saf.dataFiles(this, tree);
                if (files.isEmpty()) {
                    throw new java.io.IOException("No encontré archivos Data0000, Data0001… en esa carpeta."
                            + " Elige la memoria USB completa o su carpeta «Xbox360».");
                }
                safFiles = files;
                List<ByteSource> srcs = new ArrayList<>(files);
                List<XboxDisk.Part> found = XboxDisk.usbStick(srcs);
                if (found.isEmpty()) throw new java.io.IOException("Esos archivos no tienen una partición XTAF de Xbox 360.");
                sessionLabel = "Memoria USB de la consola";
                parts = new ArrayList<>(found);
                ui.post(() -> {
                    hideOverlay();
                    if (found.size() == 1) openPartition(0);
                    else showPartitions();
                });
            } catch (Throwable e) {
                ui.post(() -> fail(e));
            }
        });
    }

    // ------------------------------------------------------------------ pantallas

    private void setScreen(View v) {
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout header(String title, String sub, boolean back) {
        LinearLayout h = Ui.row(this);
        h.setPadding(dp(8), dp(10), dp(16), dp(8));
        if (back) h.addView(Ui.iconButton(this, R.drawable.ic_back, 46, v -> onBackPressed()));
        LinearLayout t = Ui.column(this);
        t.setPadding(back ? dp(4) : dp(12), 0, 0, 0);
        TextView a = Ui.text(this, title, back ? 19 : 28, Ui.TEXT, true);
        a.setSingleLine(true);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(this, sub, 12.5f, Ui.SUB, false);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            t.addView(s);
        }
        h.addView(t, Ui.weight(1));
        return h;
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s.toUpperCase(java.util.Locale.ROOT), 12, Ui.ACCENT, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(6), dp(20), 0, dp(8));
        return t;
    }

    private LinearLayout card(int iconRes, String title, String sub, Runnable onClick) {
        LinearLayout c = Ui.row(this);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(18))));
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(0x224ADE6B, dp(14)));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Ui.icon(this, iconRes, Ui.ACCENT));
        badge.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        c.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout t = Ui.column(this);
        t.setPadding(dp(14), 0, dp(8), 0);
        t.addView(Ui.text(this, title, 15.5f, Ui.TEXT, true));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 12.5f, Ui.SUB, false);
            s.setPadding(0, dp(2), 0, 0);
            t.addView(s);
        }
        c.addView(t, Ui.weight(1));
        if (onClick != null) {
            ImageView ch = new ImageView(this);
            ch.setImageDrawable(Ui.icon(this, R.drawable.ic_chevron, Ui.SUB));
            c.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
            c.setOnClickListener(v -> onClick.run());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private void showHome() {
        mode = Mode.HOME;
        generation++;
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.column(this);
        col.setPadding(dp(14), 0, dp(14), dp(32));
        sv.addView(col);
        col.addView(header("Arca", "Tu Xbox 360 en el bolsillo", false));
        // aviso de modo lectura
        TextView ro = Ui.text(this, "🔒  Modo lectura: esta versión solo mira. No modifica tu memoria ni tu disco.", 13, Ui.ACCENT2, false);
        ro.setPadding(dp(14), dp(12), dp(14), dp(12));
        ro.setLineSpacing(0, 1.1f);
        ro.setBackground(Ui.round(0x1A4ADE6B, dp(14)));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.setMargins(0, dp(4), 0, 0);
        col.addView(ro, rl);

        col.addView(section("Unidades conectadas por USB"));
        List<UsbDevice> devices = UsbDisk.find(um);
        if (devices.isEmpty()) {
            TextView none = Ui.text(this, "No hay ninguna unidad conectada.\nConecta el disco duro o la memoria de tu Xbox 360 con un cable OTG (o un adaptador USB del disco) y toca «Buscar de nuevo».", 13.5f, Ui.SUB, false);
            none.setLineSpacing(0, 1.15f);
            none.setPadding(dp(6), dp(2), dp(6), dp(8));
            col.addView(none);
        }
        for (UsbDevice d : devices) {
            String n = d.getProductName() != null ? d.getProductName() : "Unidad USB";
            String m = d.getManufacturerName() != null ? d.getManufacturerName() + " · " : "";
            col.addView(card(R.drawable.ic_usb, n, m + "Toca para abrirla", () -> openUsb(d)));
        }
        col.addView(card(R.drawable.ic_refresh, "Buscar de nuevo", null, this::showHome));

        col.addView(section("Memoria USB que Android ya abrió"));
        col.addView(card(R.drawable.ic_folder, "Elegir carpeta de una memoria USB",
                "Para memorias configuradas por la consola: elige la memoria o su carpeta «Xbox360»", this::chooseFolder));

        col.addView(section("Acerca de"));
        col.addView(card(R.drawable.ic_studio, "Forja Estudios", "Quién hace Arca", () -> Studio.show(this)));
        col.addView(card(R.drawable.ic_info, "Versión " + version(), "Inspirada en Horizon · sin relación con Microsoft", null));
        col.addView(Legal.buttons(this));
        setScreen(sv);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void showPartitions() {
        mode = Mode.PARTS;
        generation++;
        fs = null;
        path = "";
        LinearLayout col = Ui.column(this);
        col.setPadding(dp(14), 0, dp(14), dp(14));
        col.addView(header(sessionLabel, "Elige la partición", true));
        for (int i = 0; i < parts.size(); i++) {
            final int k = i;
            XboxDisk.Part p = parts.get(i);
            col.addView(card(R.drawable.ic_folder, p.name, Ui.size(p.source.length()), () -> openPartition(k)));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setScreen(sv);
    }

    private void openPartition(int i) {
        showOverlay("Abriendo la partición…");
        exec.execute(() -> {
            try {
                Xtaf x = new Xtaf(parts.get(i).source);
                x.listRoot();   // si la tabla está mal, falla aquí y no más adelante
                fs = x;
                partName = parts.get(i).name;
                path = "";
                infos.clear();
                scanGames();
                ui.post(() -> {
                    hideOverlay();
                    browse("");
                });
            } catch (Throwable e) {
                fs = null;
                ui.post(() -> fail(e));
            }
        });
    }

    /**
     * Lee los juegos que hay en «Content» para aprender sus nombres e íconos (cada paquete los trae adentro).
     * Es lo que hace la pantalla «Preparando…». Los que ya se conocen no se vuelven a leer.
     */
    private void scanGames() {
        Xtaf x = fs;
        try {
            Xtaf.Entry content = x.find("Content");
            if (content == null) return;
            List<Xtaf.Entry> profiles = new ArrayList<>();
            List<Xtaf.Entry> todo = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Xtaf.Entry p : x.list(content)) {
                if (!p.dir || !HEX16.matcher(p.name).matches()) continue;
                profiles.add(p);
                for (Xtaf.Entry t : x.list(p)) {
                    if (t.dir && HEX8.matcher(t.name).matches() && !titles.knows(t.name) && seen.add(t.name.toUpperCase(java.util.Locale.US))) todo.add(t);
                }
            }
            if (todo.isEmpty() && profiles.isEmpty()) return;
            skip.set(false);
            int total = todo.size();
            ui.post(() -> setOverlayText("Preparando…", total == 0 ? "Leyendo los perfiles…" : "Leyendo " + total + " juegos y sus íconos…"));
            int n = 0;
            for (Xtaf.Entry t : todo) {
                if (skip.get() || Thread.currentThread().isInterrupted()) break;
                n++;
                final int k = n;
                if (n == 1 || n % 4 == 0) ui.post(() -> setOverlayDetail("Preparando carátulas e íconos… (" + k + " de " + total + ")"));
                readTitle(x, t);
            }
            // el nombre de cada jugador, si su perfil se puede leer
            for (Xtaf.Entry p : profiles) {
                if (skip.get() || !p.name.toUpperCase(java.util.Locale.US).startsWith("E000") || titles.profile(p.name) != null) continue;
                Xtaf.Entry pk = x.find("Content/" + p.name + "/FFFE07D1/00010000/" + p.name);
                if (pk != null && !pk.dir && pk.size >= 0x1800) {
                    Stfs s = Stfs.parse(x.readFile(pk, 0, Stfs.HEADER));
                    if (s != null && !s.displayName.isEmpty()) titles.putProfile(p.name, s.displayName);
                }
            }
        } catch (Throwable e) {
            // si algo falla leyendo nombres, la carpeta se muestra igual con los números
        }
    }

    private void readTitle(Xtaf x, Xtaf.Entry title) {
        try {
            for (Xtaf.Entry type : x.list(title)) {
                if (!type.dir) continue;
                for (Xtaf.Entry f : x.list(type)) {
                    if (f.dir || f.size < 0x1800) continue;
                    Stfs s = Stfs.parse(x.readFile(f, 0, Stfs.HEADER));
                    if (s == null) continue;
                    String name = s.bestTitle();
                    if (name.isEmpty()) continue;
                    byte[] icon = s.titleThumbnail.length > 0 ? s.titleThumbnail : s.thumbnail;
                    titles.put(title.name, name, icon);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ explorador

    private static final class Row {
        Xtaf.Entry e;
        String title, sub;
        Bitmap icon;
        int iconRes;
    }

    private void browse(String p) {
        final Xtaf x = fs;
        if (x == null) return;
        final int gen = ++generation;
        // el cartel de «Preparando…» solo sale si la carpeta tarda (una memoria lenta o una carpeta enorme)
        final Runnable late = () -> showOverlay("Leyendo la carpeta…");
        ui.postDelayed(late, 400);
        exec.execute(() -> {
            try {
                List<Xtaf.Entry> list;
                if (p.isEmpty()) {
                    list = x.listRoot();
                } else {
                    Xtaf.Entry dir = x.find(p);
                    if (dir == null || !dir.dir) throw new java.io.IOException("No encontré la carpeta «" + p + "»");
                    list = x.list(dir);
                }
                list.sort((a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
                ui.post(() -> {
                    ui.removeCallbacks(late);
                    hideOverlay();
                    if (gen != generation || fs != x) return;
                    path = p;
                    entries = list;
                    showBrowser();
                    loadHeaders(x, list, gen);
                });
            } catch (Throwable e) {
                ui.post(() -> {
                    ui.removeCallbacks(late);
                    fail(e);
                });
            }
        });
    }

    private boolean inPackageDir() {
        String[] q = path.isEmpty() ? new String[0] : path.split("/");
        return q.length >= 4 && q[0].equalsIgnoreCase("Content");
    }

    /** En las carpetas de paquetes se lee la cabecera de cada archivo para mostrar su nombre y su tipo. */
    private void loadHeaders(Xtaf x, List<Xtaf.Entry> list, int gen) {
        if (!inPackageDir()) return;
        exec.execute(() -> {
            int n = 0;
            for (Xtaf.Entry e : list) {
                if (gen != generation || fs != x) return;
                if (e.dir || e.size < 0x1800 || infos.containsKey(e.path)) continue;
                try {
                    Stfs s = Stfs.parse(x.readFile(e, 0, Stfs.HEADER));
                    if (s != null) {
                        infos.put(e.path, s);
                        if (++n % 3 == 0) ui.post(() -> {
                            if (adapter != null) adapter.notifyDataSetChanged();
                        });
                    }
                } catch (Exception ignored) {
                }
            }
            ui.post(() -> {
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        });
    }

    private Row rowFor(Xtaf.Entry e) {
        String[] p = path.isEmpty() ? new String[0] : path.split("/");
        boolean content = p.length >= 1 && p[0].equalsIgnoreCase("Content");
        Row r = new Row();
        r.e = e;
        r.title = e.name;
        r.iconRes = e.dir ? R.drawable.ic_folder : R.drawable.ic_file;
        if (e.dir) r.sub = e.modified > 0 ? Ui.date(e.modified) : "";
        else r.sub = Ui.size(e.size) + (e.modified > 0 ? "  ·  " + Ui.date(e.modified) : "");
        if (p.length == 0 && e.dir && e.name.equalsIgnoreCase("Content")) {
            r.sub = "Juegos, partidas guardadas, perfiles y descargas";
        } else if (content && p.length == 1 && e.dir && HEX16.matcher(e.name).matches()) {
            String gt = titles.profile(e.name);
            boolean shared = e.name.matches("0+");
            r.title = shared ? "Contenido compartido" : (gt != null ? gt : "Perfil de jugador");
            r.sub = e.name + (shared ? "  ·  de todos los perfiles" : "");
        } else if (content && p.length == 2 && e.dir && HEX8.matcher(e.name).matches()) {
            String n = titles.name(e.name);
            r.title = e.name.toUpperCase(java.util.Locale.US) + (n != null ? " (" + n + ")" : "");
            r.icon = titles.icon(e.name);
            r.sub = n != null ? "Juego" : "Juego o aplicación";
        } else if (content && p.length == 3 && e.dir && HEX8.matcher(e.name).matches()) {
            int t = (int) Long.parseLong(e.name, 16);
            r.title = e.name.toUpperCase(java.util.Locale.US) + " · " + Stfs.typeName(t);
            r.sub = "Tipo de contenido";
        } else if (!e.dir) {
            Stfs s = infos.get(e.path);
            if (s != null) {
                String nm = !s.displayName.isEmpty() ? s.displayName : e.name;
                r.title = nm;
                r.sub = Stfs.typeName(s.contentType) + "  ·  " + Ui.size(e.size);
                byte[] ic = s.thumbnail.length > 0 ? s.thumbnail : s.titleThumbnail;
                if (ic.length > 0) r.icon = android.graphics.BitmapFactory.decodeByteArray(ic, 0, ic.length);
            }
        }
        return r;
    }

    private void showBrowser() {
        mode = Mode.BROWSER;
        LinearLayout col = Ui.column(this);
        String shownPath = partName + (path.isEmpty() ? "" : "  ›  " + path.replace("/", "  ›  "));
        String title = path.isEmpty() ? sessionLabel : path.substring(path.lastIndexOf('/') + 1);
        col.addView(header(title, shownPath, true));
        LinearLayout tools = Ui.row(this);
        tools.setPadding(dp(14), 0, dp(14), dp(8));
        tools.addView(chip("🔍  Buscar", this::showSearch));
        tools.addView(chip("📊  Radiografía", this::showRadiography));
        tools.addView(chip("✅  Verificar", this::verifyAllMenu));
        tools.addView(chip("💾  Copias", this::backupMenu));
        tools.addView(chip("✏️  Editor", this::showEditor));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tools);
        col.addView(hs);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(dp(12), 0, dp(12), dp(16));
        lv.setClipToPadding(false);
        if (entries.isEmpty()) {
            TextView e = Ui.text(this, "Esta carpeta está vacía", 14, Ui.SUB, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(60), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
        adapter = new BaseAdapter() {
            private final Map<Integer, Row> cache = new HashMap<>();

            @Override
            public void notifyDataSetChanged() {
                cache.clear();
                super.notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                return entries.size();
            }

            @Override
            public Object getItem(int i) {
                return entries.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                Row r = cache.computeIfAbsent(i, k -> rowFor(entries.get(k)));
                LinearLayout row = v instanceof LinearLayout ? (LinearLayout) v : null;
                if (row == null) {
                    row = Ui.row(MainActivity.this);
                    row.setPadding(dp(12), dp(10), dp(10), dp(10));
                    FrameLayout box = new FrameLayout(MainActivity.this);
                    ImageView ic = new ImageView(MainActivity.this);
                    ic.setId(1);
                    box.addView(ic, new FrameLayout.LayoutParams(-1, -1));
                    row.addView(box, new LinearLayout.LayoutParams(dp(46), dp(46)));
                    LinearLayout t = Ui.column(MainActivity.this);
                    t.setPadding(dp(14), 0, dp(6), 0);
                    TextView a = Ui.text(MainActivity.this, "", 15, Ui.TEXT, true);
                    a.setId(2);
                    a.setSingleLine(true);
                    a.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    TextView b = Ui.text(MainActivity.this, "", 12, Ui.SUB, false);
                    b.setId(3);
                    b.setSingleLine(true);
                    b.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    t.addView(a);
                    t.addView(b);
                    row.addView(t, Ui.weight(1));
                    ImageView ch = new ImageView(MainActivity.this);
                    ch.setId(4);
                    row.addView(ch, new LinearLayout.LayoutParams(dp(18), dp(18)));
                    row.setBackground(Ui.rippleRound(0x00000000, dp(14)));
                }
                ImageView ic = row.findViewById(1);
                ((View) ic.getParent()).setBackground(r.icon != null ? null : Ui.round(0x224ADE6B, dp(12)));
                if (r.icon != null) {
                    ic.setImageBitmap(r.icon);
                    ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ic.setPadding(0, 0, 0, 0);
                } else {
                    ic.setImageDrawable(Ui.icon(MainActivity.this, r.iconRes, r.e.dir ? Ui.ACCENT : Ui.ACCENT2));
                    ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ic.setPadding(dp(11), dp(11), dp(11), dp(11));
                }
                ((TextView) row.findViewById(2)).setText(r.title);
                TextView sub = row.findViewById(3);
                sub.setText(r.sub == null ? "" : r.sub);
                sub.setVisibility(r.sub == null || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
                ImageView ch = row.findViewById(4);
                if (r.e.dir) ch.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_chevron, Ui.SUB));
                else ch.setImageDrawable(null);
                return row;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, i, id) -> {
            Xtaf.Entry e = entries.get(i);
            if (e.dir) browse(e.path);
            else showFile(e);
        });
        lv.setOnItemLongClickListener((p, v, i, id) -> {
            rowMenu(entries.get(i));
            return true;
        });
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setScreen(col);
    }

    private void showFile(Xtaf.Entry e) {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                Stfs s = infos.get(e.path);
                if (s == null && e.size >= 0x1800) {
                    s = Stfs.parse(x.readFile(e, 0, Stfs.HEADER));
                    if (s != null) infos.put(e.path, s);
                }
                final Stfs st = s;
                ui.post(() -> showFileDialog(e, st));
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void showFileDialog(Xtaf.Entry e, Stfs s) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(22), dp(18), dp(22), dp(6));
        StringBuilder b = new StringBuilder();
        if (s != null) {
            byte[] ic = s.titleThumbnail.length > 0 ? s.titleThumbnail : s.thumbnail;
            if (ic.length > 0) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(ic, 0, ic.length));
                box.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(72)));
            }
            TextView t = Ui.text(this, s.displayName.isEmpty() ? e.name : s.displayName, 19, Ui.TEXT, true);
            t.setPadding(0, dp(10), 0, dp(2));
            box.addView(t);
            b.append("Juego: ").append(s.bestTitle().isEmpty() ? "(sin nombre)" : s.bestTitle());
            b.append("\nID del juego: ").append(Stfs.hex8(s.titleId));
            b.append("\nTipo: ").append(Stfs.typeName(s.contentType));
            b.append("\nFormato del paquete: ").append(s.magic).append(s.magic.equals("CON") ? " (firmado por una consola)" : " (firmado por Microsoft)");
        } else {
            TextView t = Ui.text(this, e.name, 19, Ui.TEXT, true);
            box.addView(t);
            b.append("No es un paquete de la Xbox 360 (o es muy chico para tener cabecera).");
        }
        b.append("\nArchivo: ").append(e.name);
        b.append("\nTamaño: ").append(Ui.size(e.size));
        if (e.modified > 0) b.append("\nFecha: ").append(Ui.date(e.modified));
        TextView info = Ui.text(this, b.toString(), 14, Ui.SUB, false);
        info.setLineSpacing(0, 1.2f);
        info.setPadding(0, dp(8), 0, 0);
        box.addView(info);
        AlertDialog[] dlg = new AlertDialog[1];
        if (s != null) {
            box.addView(dialogAction("🧩  Ver por dentro", () -> {
                dlg[0].dismiss();
                openPackage(e);
            }));
            box.addView(dialogAction("✅  Verificar el paquete", () -> {
                dlg[0].dismiss();
                verifyEntry(e);
            }));
            box.addView(dialogAction("✏️  Usar como origen (A) en el editor", () -> {
                dlg[0].dismiss();
                useInEditor(e, true);
            }));
            box.addView(dialogAction("✏️  Usar como destino (B) en el editor", () -> {
                dlg[0].dismiss();
                useInEditor(e, false);
            }));
        }
        box.addView(dialogAction("📤  Copiar al celular", () -> {
            dlg[0].dismiss();
            exportEntry(e);
        }));
        dlg[0] = new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setView(box).setNeutralButton("Cerrar", null).create();
        dlg[0].show();
    }

    private TextView dialogAction(String label, Runnable r) {
        TextView t = Ui.text(this, label, 15, Ui.TEXT, true);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(14))));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ fichas, menú de cada fila

    private TextView chip(String text, Runnable r) {
        TextView t = Ui.text(this, text, 13.5f, Ui.TEXT, true);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(20), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(20))));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    /** Pulsación larga sobre un archivo o carpeta. */
    private void rowMenu(Xtaf.Entry e) {
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        names.add(e.dir ? "Copiar la carpeta al celular" : "Copiar al celular");
        acts.add(() -> exportEntry(e));
        if (!e.dir && e.size >= 0xB000) {
            names.add("Ver por dentro (si es un paquete)");
            acts.add(() -> openPackage(e));
            names.add("Verificar el paquete");
            acts.add(() -> verifyEntry(e));
        }
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle(e.name)
                .setItems(names.toArray(new String[0]), (d, w) -> acts.get(w).run())
                .show();
    }

    // ------------------------------------------------------------------ copiar al celular

    private static String safeName(String n) {
        String s = n.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        return s.isEmpty() ? "sin_nombre" : s;
    }

    private static String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
        return m == null ? "application/octet-stream" : m;
    }

    private Uri destination() {
        String s = getSharedPreferences("arca", MODE_PRIVATE).getString("destino", null);
        return s == null ? null : Uri.parse(s);
    }

    /** La carpeta del celular donde van las copias; la primera vez se le pide al usuario. */
    private void withDestination(java.util.function.Consumer<Uri> action) {
        Uri d = destination();
        if (d != null) {
            for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().equals(d) && p.isWritePermission()) {
                    action.accept(d);
                    return;
                }
            }
        }
        afterDest = () -> action.accept(destination());
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("¿Dónde guardo las copias?")
                .setMessage("Elige una carpeta de tu celular (por ejemplo, crea una llamada «Arca»). Ahí irá lo que copies de tu Xbox. Solo te lo pregunto una vez.")
                .setPositiveButton("Elegir carpeta", (x, y) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    try {
                        startActivityForResult(i, REQ_DEST);
                    } catch (Exception ex) {
                        Aviso.show(this, root, ex);
                    }
                })
                .setNegativeButton("Cancelar", (x, y) -> afterDest = null)
                .show();
    }

    /** Lo que va pasando mientras se copia. */
    private final class CopyState {
        long done, total, last;
        int files;
        String note;

        void add(long n) {
            done += n;
            long now = android.os.SystemClock.uptimeMillis();
            if (now - last > 300) {
                last = now;
                final String t = Ui.size(done) + " de " + Ui.size(total) + (files > 1 ? "  ·  " + files + " archivos" : "");
                ui.post(() -> setOverlayDetail(t));
            }
        }

        void check() throws java.io.IOException {
            if (skip.get() || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Copia cancelada");
        }
    }

    private interface CopyJob {
        void run(Uri parent, CopyState st) throws Exception;
    }

    private Uri makeDir(Uri parent, String name) throws java.io.IOException {
        Uri d = android.provider.DocumentsContract.createDocument(getContentResolver(), parent,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR, safeName(name));
        if (d == null) throw new java.io.IOException("Android no dejó crear la carpeta «" + name + "» en tu celular");
        return d;
    }

    private java.io.OutputStream makeFile(Uri parent, String name) throws java.io.IOException {
        Uri f = android.provider.DocumentsContract.createDocument(getContentResolver(), parent, mimeFor(name), safeName(name));
        if (f == null) throw new java.io.IOException("Android no dejó crear el archivo «" + name + "» en tu celular");
        java.io.OutputStream o = getContentResolver().openOutputStream(f);
        if (o == null) throw new java.io.IOException("Android no dejó escribir en tu celular");
        return new java.io.BufferedOutputStream(o, 1 << 16);
    }

    private void runCopy(String what, long total, CopyJob job) {
        withDestination(tree -> {
            Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree));
            skip.set(false);
            showOverlay("");
            setOverlayText("Copiando…", what, "Cancelar");
            CopyState st = new CopyState();
            st.total = total;
            exec.execute(() -> {
                try {
                    job.run(parent, st);
                    ui.post(() -> {
                        hideOverlay();
                        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                                .setTitle("Listo ✅")
                                .setMessage("Copié " + st.files + (st.files == 1 ? " archivo" : " archivos") + " (" + Ui.size(st.done) + ") a tu carpeta de copias."
                                        + (st.note != null ? "\n\n" + st.note : ""))
                                .setPositiveButton("Entendido", null)
                                .show();
                    });
                } catch (Throwable e) {
                    ui.post(() -> {
                        hideOverlay();
                        if (e instanceof java.io.IOException && "Copia cancelada".equals(e.getMessage())) {
                            Toast.makeText(this, "Copia cancelada. Lo que ya se copió queda en tu celular.", Toast.LENGTH_LONG).show();
                        } else {
                            Aviso.show(this, root, e);
                        }
                    });
                }
            });
        });
    }

    private void copyXtaf(Xtaf x, Xtaf.Entry e, Uri parent, CopyState st) throws Exception {
        st.check();
        if (e.dir) {
            Uri d = makeDir(parent, e.name);
            for (Xtaf.Entry k : x.list(e)) copyXtaf(x, k, d, st);
        } else {
            try (java.io.OutputStream o = makeFile(parent, e.name)) {
                long[] before = {st.done};
                long[] prev = {0};
                x.copyFile(e, o, p -> {
                    st.add(p - prev[0]);
                    prev[0] = p;
                    if (skip.get()) throw new IllegalStateException("cancelado");
                });
            } catch (IllegalStateException c) {
                throw new java.io.IOException("Copia cancelada");
            }
            st.files++;
        }
    }

    /** Copia un archivo o carpeta de la consola a la carpeta de copias del celular. */
    private void exportEntry(Xtaf.Entry e) {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                long total = e.dir ? x.totalSize(e, null) : e.size;
                ui.post(() -> {
                    if (e.dir && total > 4L << 30) {
                        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                                .setTitle("Es una carpeta grande")
                                .setMessage("«" + e.name + "» ocupa " + Ui.size(total) + ". ¿Seguro que la copias al celular? Puede tardar mucho y llenar la memoria.")
                                .setPositiveButton("Copiar", (d, w) -> runCopy(e.name, total, (parent, st) -> copyXtaf(x, e, parent, st)))
                                .setNegativeButton("Cancelar", null)
                                .show();
                    } else {
                        runCopy(e.name, total, (parent, st) -> copyXtaf(x, e, parent, st));
                    }
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void copyItem(StfsPackage p, StfsPackage.Item it, Uri parent, CopyState st) throws Exception {
        st.check();
        if (it.dir) {
            Uri d = makeDir(parent, it.name);
            for (StfsPackage.Item k : p.children(it.index)) copyItem(p, k, d, st);
        } else {
            try (java.io.OutputStream o = makeFile(parent, it.name)) {
                long[] prev = {0};
                p.copyTo(it, o, pr -> {
                    st.add(pr - prev[0]);
                    prev[0] = pr;
                    if (skip.get()) throw new IllegalStateException("cancelado");
                });
            } catch (IllegalStateException c) {
                throw new java.io.IOException("Copia cancelada");
            }
            st.files++;
        }
    }

    private long itemSize(StfsPackage p, StfsPackage.Item it) {
        if (!it.dir) return it.size;
        long s = 0;
        for (StfsPackage.Item k : p.children(it.index)) s += itemSize(p, k);
        return s;
    }

    private void exportItem(StfsPackage.Item it) {
        final StfsPackage p = pkg;
        if (p == null) return;
        runCopy(it.name, itemSize(p, it), (parent, st) -> copyItem(p, it, parent, st));
    }

    // ------------------------------------------------------------------ verificar paquetes

    private static String badList(List<String> files, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(max, files.size()); i++) b.append("\n• ").append(files.get(i));
        if (files.size() > max) b.append("\n… y ").append(files.size() - max).append(" más");
        return b.toString();
    }

    private void verifyEntry(Xtaf.Entry e) {
        final Xtaf x = fs;
        if (x == null) return;
        skip.set(false);
        showOverlay("");
        setOverlayText("Verificando…", e.name, "Cancelar");
        exec.execute(() -> {
            try {
                StfsPackage p = StfsPackage.open(x.asSource(e));
                StfsPackage.Result r = p.verify(skip, n -> ui.post(() -> setOverlayDetail(e.name + "\nRevisando bloques… " + (n * 100L / Math.max(1, p.totalBlocks)) + " %")));
                ui.post(() -> {
                    hideOverlay();
                    if (!r.cancelled) showVerifyDialog(e.name, p.header.displayName, r);
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void showVerifyDialog(String file, String display, StfsPackage.Result r) {
        String what = display == null || display.isEmpty() ? file : display;
        String msg;
        if (r.bad == 0) {
            msg = "Revisé los " + r.total + " bloques de «" + what + "» y todos están bien: ningún dato está corrupto.\n\n"
                    + "Ojo: esto comprueba los datos, no la firma de la consola.";
        } else {
            msg = r.bad + " de " + r.total + " bloques están dañados.\n\n"
                    + (r.files.isEmpty() ? "No pude saber qué archivos tocan." : "Archivos afectados:" + badList(r.files, 12))
                    + "\n\nLa consola tampoco podría abrir esos datos. Arca solo lee, así que no se empeoró nada.";
        }
        if (r.diag != null) {
            msg += "\n\nEstructura del paquete:\n• Cabecera: " + mark(r.diag.header) + "\n• Tabla principal: " + mark(r.diag.top)
                    + (r.diag.level1.equals("no aplica") ? "" : "\n• Tablas de nivel 1: " + mark(r.diag.level1));
            if (!r.diag.allOk() && r.bad == 0) {
                msg += "\n\nSi un paquete sano de tu consola sale con ⚠️ aquí, es que Arca todavía no entiende bien ese tipo de paquete: mándame la captura.";
            }
        }
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle(r.bad == 0 ? "✅ Sin errores" : "⚠️ Paquete dañado")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private static String mark(String s) {
        return s.equals("ok") ? "✅" : s.startsWith("mal") ? "⚠️ " + s : s;
    }

    private void verifyAllMenu() {
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("Verificar paquetes")
                .setItems(new String[]{"Solo perfiles y partidas guardadas (rápido)", "Todo el contenido (puede tardar mucho)"},
                        (d, w) -> verifyAll(w == 0))
                .show();
    }

    /** Todos los paquetes de «Content» (o solo los de partidas y perfiles). */
    private List<Xtaf.Entry> packagesIn(Xtaf x, boolean onlySaves) throws java.io.IOException {
        List<Xtaf.Entry> out = new ArrayList<>();
        Xtaf.Entry c = x.find("Content");
        if (c == null) return out;
        for (Xtaf.Entry p : x.list(c)) {
            if (!p.dir || !HEX16.matcher(p.name).matches()) continue;
            for (Xtaf.Entry t : x.list(p)) {
                if (!t.dir || !HEX8.matcher(t.name).matches()) continue;
                for (Xtaf.Entry ty : x.list(t)) {
                    if (!ty.dir) continue;
                    if (onlySaves && !ty.name.equals("00000001") && !ty.name.equals("00010000")) continue;
                    for (Xtaf.Entry f : x.list(ty)) if (!f.dir && f.size >= 0xB000) out.add(f);
                }
            }
        }
        return out;
    }

    private void verifyAll(boolean onlySaves) {
        final Xtaf x = fs;
        if (x == null) return;
        skip.set(false);
        showOverlay("");
        setOverlayText("Verificando…", "Buscando los paquetes…", "Cancelar");
        exec.execute(() -> {
            try {
                List<Xtaf.Entry> pk = packagesIn(x, onlySaves);
                List<String> problems = new ArrayList<>();
                int ok = 0, unreadable = 0, n = 0;
                for (Xtaf.Entry f : pk) {
                    if (skip.get()) break;
                    n++;
                    final int k = n;
                    final String nm = f.path;
                    ui.post(() -> setOverlayDetail("Paquete " + k + " de " + pk.size() + "\n" + nm));
                    try {
                        StfsPackage p = StfsPackage.open(x.asSource(f));
                        StfsPackage.Result r = p.verify(skip, null);
                        if (r.cancelled) break;
                        if (r.bad == 0) ok++;
                        else problems.add(f.path + " — " + r.bad + " bloques dañados");
                    } catch (java.io.IOException ex) {
                        String m = String.valueOf(ex.getMessage());
                        if (m.contains("no es un paquete")) continue;
                        unreadable++;
                        problems.add(f.path + " — no se pudo abrir (" + m + ")");
                    }
                }
                final int fOk = ok, fTotal = pk.size();
                final boolean stopped = skip.get();
                ui.post(() -> {
                    hideOverlay();
                    String head = (stopped ? "Cancelaste antes de terminar.\n\n" : "")
                            + "Revisé " + fOk + " de " + fTotal + " paquetes sin encontrar errores.";
                    new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                            .setTitle(problems.isEmpty() ? "✅ Todo bien" : "⚠️ Hay paquetes con problemas")
                            .setMessage(head + (problems.isEmpty() ? "" : "\n\nCon problemas:" + badList(problems, 15))
                                    + "\n\nEsto comprueba los datos, no la firma de la consola.")
                            .setPositiveButton("Entendido", null)
                            .show();
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    // ------------------------------------------------------------------ copias de seguridad con fecha

    private void backupMenu() {
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("Copia de seguridad al celular")
                .setItems(new String[]{"Perfiles y partidas guardadas (todo)", "Solo un perfil…", "Solo las partidas de un juego…"}, (d, w) -> {
                    if (w == 0) startBackup("todo", null, null);
                    else if (w == 1) pickProfile();
                    else pickTitle();
                })
                .show();
    }

    private void pickProfile() {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                List<String> ids = new ArrayList<>(), names = new ArrayList<>();
                Xtaf.Entry c = x.find("Content");
                if (c != null) {
                    for (Xtaf.Entry p : x.list(c)) {
                        if (!p.dir || !HEX16.matcher(p.name).matches()) continue;
                        String gt = titles.profile(p.name);
                        ids.add(p.name);
                        names.add((p.name.matches("0+") ? "Contenido compartido" : gt != null ? gt : "Perfil") + "  ·  " + p.name);
                    }
                }
                ui.post(() -> {
                    if (ids.isEmpty()) {
                        Toast.makeText(this, "No encontré perfiles", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setTitle("¿De cuál perfil?")
                            .setItems(names.toArray(new String[0]), (d, w) -> startBackup("perfil " + names.get(w), ids.get(w), null)).show();
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void pickTitle() {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                Map<String, String> found = new java.util.TreeMap<>();
                Xtaf.Entry c = x.find("Content");
                if (c != null) {
                    for (Xtaf.Entry p : x.list(c)) {
                        if (!p.dir || !HEX16.matcher(p.name).matches()) continue;
                        for (Xtaf.Entry t : x.list(p)) {
                            if (!t.dir || !HEX8.matcher(t.name).matches()) continue;
                            String id = t.name.toUpperCase(java.util.Locale.US);
                            String n = titles.name(id);
                            found.putIfAbsent(id, n != null ? n + "  ·  " + id : id);
                        }
                    }
                }
                List<String> ids = new ArrayList<>(found.keySet());
                ids.sort((a, b) -> found.get(a).compareToIgnoreCase(found.get(b)));
                List<String> names = new ArrayList<>();
                for (String id : ids) names.add(found.get(id));
                ui.post(() -> {
                    if (ids.isEmpty()) {
                        Toast.makeText(this, "No encontré juegos", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setTitle("¿De cuál juego?")
                            .setItems(names.toArray(new String[0]), (d, w) -> startBackup("juego " + names.get(w), null, ids.get(w))).show();
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    /** Los archivos de una copia: el paquete del perfil y las partidas guardadas (o solo las de un juego). */
    private List<Xtaf.Entry> backupFiles(Xtaf x, String profile, String title) throws java.io.IOException {
        List<Xtaf.Entry> out = new ArrayList<>();
        Xtaf.Entry c = x.find("Content");
        if (c == null) return out;
        for (Xtaf.Entry p : x.list(c)) {
            if (!p.dir || !HEX16.matcher(p.name).matches()) continue;
            if (profile != null && !p.name.equalsIgnoreCase(profile)) continue;
            for (Xtaf.Entry t : x.list(p)) {
                if (!t.dir || !HEX8.matcher(t.name).matches()) continue;
                if (title != null && !t.name.equalsIgnoreCase(title)) continue;
                for (Xtaf.Entry ty : x.list(t)) {
                    if (!ty.dir) continue;
                    boolean save = ty.name.equals("00000001"), prof = ty.name.equals("00010000");
                    if (!save && !prof) continue;
                    if (title != null && !save) continue;
                    for (Xtaf.Entry f : x.list(ty)) if (!f.dir) out.add(f);
                }
            }
        }
        return out;
    }

    private void startBackup(String what, String profile, String title) {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                List<Xtaf.Entry> files = backupFiles(x, profile, title);
                long total = 0;
                for (Xtaf.Entry f : files) total += f.size;
                final long tot = total;
                ui.post(() -> {
                    if (files.isEmpty()) {
                        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setTitle("No hay nada que copiar")
                                .setMessage("No encontré partidas guardadas ni perfiles en esta partición. ¿Estás en la partición de datos?")
                                .setPositiveButton("Entendido", null).show();
                    } else {
                        runCopy("Copia de seguridad (" + what + ")", tot, (parent, st) -> doBackup(x, files, parent, st));
                    }
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private Uri ensureDir(Uri root, String rel, Map<String, Uri> cache) throws java.io.IOException {
        Uri cur = root;
        StringBuilder acc = new StringBuilder();
        for (String part : rel.split("/")) {
            if (part.isEmpty()) continue;
            acc.append('/').append(part);
            Uri known = cache.get(acc.toString());
            if (known == null) {
                known = makeDir(cur, part);
                cache.put(acc.toString(), known);
            }
            cur = known;
        }
        return cur;
    }

    private void doBackup(Xtaf x, List<Xtaf.Entry> files, Uri parent, CopyState st) throws Exception {
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.US).format(new java.util.Date());
        Uri root = makeDir(parent, "Copia " + stamp);
        Map<String, Uri> dirs = new HashMap<>();
        StringBuilder idx = new StringBuilder("Copia de seguridad hecha con Arca\nFecha: " + stamp + "\nUnidad: " + sessionLabel + " · " + partName
                + "\n\nruta\ttamaño\testado\n");
        List<String> broken = new ArrayList<>();
        for (Xtaf.Entry f : files) {
            st.check();
            int cut = f.path.lastIndexOf('/');
            Uri d = ensureDir(root, cut < 0 ? "" : f.path.substring(0, cut), dirs);
            String state = "sin verificar";
            if (f.size >= 0xB000) {
                try {
                    StfsPackage p = StfsPackage.open(x.asSource(f));
                    StfsPackage.Result r = p.verify(skip, null);
                    if (r.cancelled) throw new java.io.IOException("Copia cancelada");
                    state = r.bad == 0 ? "ok" : "DAÑADO (" + r.bad + " bloques)";
                    if (r.bad > 0) broken.add(f.path + " — " + r.bad + " bloques");
                } catch (java.io.IOException ex) {
                    if ("Copia cancelada".equals(ex.getMessage())) throw ex;
                    state = "no se pudo revisar";
                }
            }
            try (java.io.OutputStream o = makeFile(d, f.name)) {
                long[] prev = {0};
                x.copyFile(f, o, pr -> {
                    st.add(pr - prev[0]);
                    prev[0] = pr;
                    if (skip.get()) throw new IllegalStateException("cancelado");
                });
            } catch (IllegalStateException c) {
                throw new java.io.IOException("Copia cancelada");
            }
            st.files++;
            idx.append(f.path).append('\t').append(f.size).append('\t').append(state).append('\n');
        }
        try (java.io.OutputStream o = makeFile(root, "indice.txt")) {
            o.write(idx.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        st.note = broken.isEmpty() ? "Todos los paquetes pasaron la verificación ✅"
                : "⚠️ " + broken.size() + (broken.size() == 1 ? " paquete ya estaba dañado" : " paquetes ya estaban dañados") + " en la consola:" + badList(broken, 8);
        st.note += "\n\nLa copia quedó en la carpeta «Copia " + stamp + "».";
    }

    // ------------------------------------------------------------------ editor por paneles

    /** Uno de los dos paquetes del editor. */
    private static final class Slot {
        String label;
        StfsPackage pkg;
        ByteSource src;
        Saf.FileSource file;     // si viene del celular
        Map<String, StfsPackage.Item> files = new java.util.TreeMap<>();
    }

    private Slot slotA, slotB;
    private StfsEditor editor;
    private final Set<String> changed = new HashSet<>();
    private final Map<String, Boolean> sameCache = new HashMap<>();
    private static final int REQ_SLOT_A = 23, REQ_SLOT_B = 24;

    private static String pathOf(StfsPackage p, StfsPackage.Item it) {
        StringBuilder b = new StringBuilder(it.name);
        int i = it.parent, guard = 0;
        while (i >= 0 && guard++ < 40) {
            b.insert(0, p.items.get(i).name + "/");
            i = p.items.get(i).parent;
        }
        return b.toString();
    }

    private Slot makeSlot(String label, ByteSource src, Saf.FileSource file) throws java.io.IOException {
        Slot s = new Slot();
        s.label = label;
        s.src = src;
        s.file = file;
        s.pkg = StfsPackage.open(src);
        for (StfsPackage.Item it : s.pkg.items) if (!it.dir) s.files.put(pathOf(s.pkg, it), it);
        return s;
    }

    private void closeSlot(Slot s) {
        if (s != null && s.file != null) s.file.close();
    }

    private void resetEditor() {
        closeSlot(slotA);
        closeSlot(slotB);
        slotA = slotB = null;
        editor = null;
        changed.clear();
        sameCache.clear();
    }

    /** Pone un paquete de la consola como origen (A) o destino (B). */
    private void useInEditor(Xtaf.Entry e, boolean asSource) {
        final Xtaf x = fs;
        if (x == null) return;
        exec.execute(() -> {
            try {
                Slot s = makeSlot(e.name + " (de la consola)", x.asSource(e), null);
                ui.post(() -> {
                    if (asSource) {
                        closeSlot(slotA);
                        slotA = s;
                    } else {
                        closeSlot(slotB);
                        slotB = s;
                        editor = null;
                        changed.clear();
                    }
                    sameCache.clear();
                    Toast.makeText(this, (asSource ? "Origen: " : "Destino: ") + e.name, Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void pickSlotFile(boolean asSource) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, asSource ? REQ_SLOT_A : REQ_SLOT_B);
        } catch (Exception e) {
            Aviso.show(this, root, e);
        }
    }

    private void slotFromPhone(Uri uri, boolean asSource) {
        showOverlay("Abriendo el paquete…");
        exec.execute(() -> {
            Saf.FileSource f = null;
            try {
                f = new Saf.FileSource(getContentResolver(), uri);
                String name = uri.getLastPathSegment() == null ? "paquete" : uri.getLastPathSegment().replaceAll(".*[/:]", "");
                Slot s = makeSlot(name + " (del celular)", f, f);
                ui.post(() -> {
                    hideOverlay();
                    if (asSource) {
                        closeSlot(slotA);
                        slotA = s;
                    } else {
                        closeSlot(slotB);
                        slotB = s;
                        editor = null;
                        changed.clear();
                    }
                    sameCache.clear();
                    showEditor();
                });
            } catch (Throwable t) {
                if (f != null) f.close();
                ui.post(() -> fail(t));
            }
        });
    }

    private void showEditor() {
        mode = Mode.EDITOR;
        LinearLayout col = Ui.column(this);
        col.addView(header("Editor de partidas", "Trabaja con copias en tu celular", true));
        TextView warn = Ui.text(this, "Esto no escribe en tu consola: cambia una COPIA de la partida en el celular. Para llevarla de vuelta falta firmarla, "
                + "y eso todavía no está.", 12.5f, Ui.WARN, false);
        warn.setPadding(dp(14), dp(10), dp(14), dp(10));
        warn.setBackground(Ui.round(0x1AFFB547, dp(14)));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.setMargins(dp(14), 0, dp(14), dp(8));
        col.addView(warn, wl);
        col.addView(slotCard("A · Origen (de donde copio los archivos)", slotA, true));
        col.addView(slotCard("B · Destino (el que se cambia)", slotB, false));
        if (slotA != null && slotB != null) {
            List<String> paths = new ArrayList<>(new java.util.TreeSet<>(union()));
            LinearLayout bar = Ui.row(this);
            bar.setPadding(dp(14), dp(4), dp(14), dp(8));
            bar.addView(chip("🔁  Reemplazar todo", this::replaceAll));
            bar.addView(chip("💾  Guardar copia editada" + (changed.isEmpty() ? "" : " (" + changed.size() + ")"), this::saveEdited));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(bar);
            col.addView(hs);
            ListView lv = new ListView(this);
            lv.setDivider(null);
            lv.setSelector(android.R.color.transparent);
            lv.setPadding(dp(12), 0, dp(12), dp(16));
            lv.setClipToPadding(false);
            lv.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return paths.size();
                }

                @Override
                public Object getItem(int i) {
                    return paths.get(i);
                }

                @Override
                public long getItemId(int i) {
                    return i;
                }

                @Override
                public View getView(int i, View v, ViewGroup parent) {
                    String p = paths.get(i);
                    StfsPackage.Item a = slotA.files.get(p), b = slotB.files.get(p);
                    String state;
                    if (a == null) state = "solo en B";
                    else if (b == null) state = "solo en A";
                    else if (changed.contains(p)) state = "✅ ya reemplazado";
                    else {
                        Boolean same = sameCache.get(p);
                        state = same == null ? "comparando…" : same ? "igual" : "distinto";
                    }
                    String sizes = (a == null ? "—" : Ui.size(a.size)) + "  ·  " + (b == null ? "—" : Ui.size(b.size));
                    LinearLayout c = card(R.drawable.ic_file, p, "A · B:  " + sizes + "   →   " + state, null);
                    return wrapRow(c);
                }
            });
            lv.setOnItemClickListener((x, v, i, id) -> replaceOne(paths.get(i)));
            col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
            compareInBackground(paths);
        } else {
            TextView hint = Ui.text(this, "Elige los dos paquetes: en el explorador, toca un paquete y escoge «Usar como origen (A)» o «Usar como destino (B)», "
                    + "o ábrelos desde el celular con los botones de arriba.", 13.5f, Ui.SUB, false);
            hint.setPadding(dp(20), dp(10), dp(20), 0);
            col.addView(hint);
        }
        setScreen(col);
    }

    private Set<String> union() {
        Set<String> u = new HashSet<>(slotA.files.keySet());
        u.addAll(slotB.files.keySet());
        return u;
    }

    private LinearLayout slotCard(String title, Slot s, boolean asSource) {
        LinearLayout c = card(R.drawable.ic_file, title, s == null ? "Sin elegir" : s.label + "  ·  " + s.files.size() + " archivos", null);
        LinearLayout t = (LinearLayout) c.getChildAt(1);
        LinearLayout buttons = Ui.row(this);
        buttons.setPadding(0, dp(8), 0, 0);
        buttons.addView(chip("📱  Del celular", () -> pickSlotFile(asSource)));
        if (s != null) {
            buttons.addView(chip("Quitar", () -> {
                if (asSource) {
                    closeSlot(slotA);
                    slotA = null;
                } else {
                    closeSlot(slotB);
                    slotB = null;
                    editor = null;
                    changed.clear();
                }
                sameCache.clear();
                showEditor();
            }));
        }
        t.addView(buttons);
        return c;
    }

    /** Dice cuáles archivos son distintos entre A y B (lee los dos). */
    private void compareInBackground(List<String> paths) {
        final Slot a = slotA, b = slotB;
        exec.execute(() -> {
            int n = 0;
            for (String p : paths) {
                if (mode != Mode.EDITOR || slotA != a || slotB != b) return;
                StfsPackage.Item ia = a.files.get(p), ib = b.files.get(p);
                if (ia == null || ib == null || sameCache.containsKey(p)) continue;
                boolean same = false;
                try {
                    if (ia.size == ib.size) same = Arrays.equals(digest(a.pkg, ia), digest(b.pkg, ib));
                } catch (Exception ignored) {
                }
                sameCache.put(p, same);
                if (++n % 4 == 0) ui.post(() -> {
                    if (mode == Mode.EDITOR) showEditor();
                });
            }
            ui.post(() -> {
                if (mode == Mode.EDITOR) showEditor();
            });
        });
    }

    private static byte[] digest(StfsPackage p, StfsPackage.Item it) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        p.copyTo(it, new java.io.OutputStream() {
            @Override
            public void write(int b) {
                md.update((byte) b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                md.update(b, off, len);
            }
        }, null);
        return md.digest();
    }

    private StfsEditor editorForB() throws java.io.IOException {
        if (editor == null) editor = StfsEditor.open(slotB.pkg);
        return editor;
    }

    private void replaceOne(String p) {
        if (slotA == null || slotB == null) return;
        StfsPackage.Item a = slotA.files.get(p), b = slotB.files.get(p);
        if (a == null || b == null) {
            Toast.makeText(this, a == null ? "Ese archivo solo está en B" : "Ese archivo solo está en A: agregar archivos nuevos todavía no se puede", Toast.LENGTH_LONG).show();
            return;
        }
        if (changed.contains(p)) {
            Toast.makeText(this, "Ya lo reemplazaste", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("Reemplazar «" + p + "»")
                .setMessage("El de B (" + Ui.size(b.size) + ") se cambia por el de A (" + Ui.size(a.size) + "). Solo cambia la copia que guardes.")
                .setPositiveButton("Reemplazar", (d, w) -> doReplace(List.of(p)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void replaceAll() {
        List<String> todo = new ArrayList<>();
        for (String p : union()) {
            StfsPackage.Item a = slotA.files.get(p), b = slotB.files.get(p);
            if (a != null && b != null && !changed.contains(p) && Boolean.FALSE.equals(sameCache.get(p))) todo.add(p);
        }
        if (todo.isEmpty()) {
            Toast.makeText(this, "No hay archivos distintos para reemplazar (o todavía estoy comparando)", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog)
                .setTitle("Reemplazar todo")
                .setMessage("Se van a reemplazar " + todo.size() + " archivos de B con los de A. Los que ocupen otra cantidad de bloques no se pueden todavía y se saltan.")
                .setPositiveButton("Reemplazar", (d, w) -> doReplace(todo))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void doReplace(List<String> paths) {
        final Slot a = slotA, b = slotB;
        showOverlay("");
        skip.set(false);
        setOverlayText("Reemplazando…", "", "Cancelar");
        exec.execute(() -> {
            List<String> skipped = new ArrayList<>();
            int ok = 0;
            try {
                StfsEditor ed = editorForB();
                for (String p : paths) {
                    if (skip.get()) break;
                    StfsPackage.Item ia = a.files.get(p), ib = b.files.get(p);
                    final String nm = p;
                    ui.post(() -> setOverlayDetail(nm));
                    if (!ed.fits(ib, ia.size)) {
                        skipped.add(p + " (ocupa " + (ia.size + 4095) / 4096 + " bloques y en B ocupa " + (ib.size + 4095) / 4096 + ")");
                        continue;
                    }
                    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream((int) Math.min(ia.size, 1 << 26));
                    a.pkg.copyTo(ia, o, null);
                    ed.replace(ib, o.toByteArray());
                    changed.add(p);
                    ok++;
                }
                final int fOk = ok;
                ui.post(() -> {
                    hideOverlay();
                    showEditor();
                    if (!skipped.isEmpty()) {
                        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setTitle("Algunos no se pudieron reemplazar")
                                .setMessage(fOk + " reemplazados.\n\nNo se pudieron:" + badList(skipped, 10)
                                        + "\n\nCambiar la cantidad de bloques de un archivo todavía no se puede.")
                                .setPositiveButton("Entendido", null).show();
                    }
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void saveEdited() {
        if (slotB == null || editor == null || changed.isEmpty()) {
            Toast.makeText(this, "Todavía no reemplazaste nada", Toast.LENGTH_SHORT).show();
            return;
        }
        final StfsEditor ed = editor;
        final Slot b = slotB;
        final long total = b.src.length();
        runCopy("copia editada", total, (parent, st) -> {
            ed.finish();
            ByteSource view = ed.view();
            StfsPackage check = StfsPackage.open(view);
            StfsPackage.Result r = check.verify(skip, null);
            if (r.cancelled) throw new java.io.IOException("Copia cancelada");
            String name = b.label.replaceAll(" \\(.*\\)$", "") + " (editado)";
            try (java.io.OutputStream o = makeFile(parent, name)) {
                ed.writeTo(o, pr -> st.add(pr - st.done));
            }
            st.files = 1;
            st.note = (r.bad == 0 && r.diag.header.equals("ok") && r.diag.top.equals("ok")
                    ? "Revisé la copia editada: todos los bloques y las tablas están bien ✅"
                    : "⚠️ La copia editada no pasó la revisión (" + r.bad + " bloques mal). No la uses.")
                    + "\n\nOjo: todavía está SIN FIRMAR, así que la consola no la aceptará hasta que se firme.";
        });
    }

    // ------------------------------------------------------------------ ver por dentro un paquete

    private void openPackage(Xtaf.Entry e) {
        final Xtaf x = fs;
        if (x == null) return;
        showOverlay("Abriendo el paquete…");
        exec.execute(() -> {
            try {
                StfsPackage p = StfsPackage.open(x.asSource(e));
                ui.post(() -> {
                    hideOverlay();
                    pkg = p;
                    pkgEntry = e;
                    pkgParent = -1;
                    showPackage();
                });
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void showPackage() {
        mode = Mode.PACKAGE;
        final StfsPackage p = pkg;
        LinearLayout col = Ui.column(this);
        String title = !p.header.displayName.isEmpty() ? p.header.displayName : pkgEntry.name;
        String where = pkgParent < 0 ? "En la raíz del paquete" : pathInPackage(p, pkgParent);
        col.addView(header(title, where, true));
        LinearLayout info = Ui.row(this);
        info.setPadding(dp(14), 0, dp(14), dp(8));
        info.addView(chip(Stfs.typeName(p.header.contentType), () -> { }));
        info.addView(chip(p.items.size() + (p.items.size() == 1 ? " elemento" : " elementos"), () -> { }));
        info.addView(chip("✅  Verificar", () -> verifyEntry(pkgEntry)));
        col.addView(info);
        List<StfsPackage.Item> list = new ArrayList<>(p.children(pkgParent));
        list.sort((a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(dp(12), 0, dp(12), dp(16));
        lv.setClipToPadding(false);
        if (list.isEmpty()) {
            TextView e = Ui.text(this, "Esta carpeta está vacía", 14, Ui.SUB, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(60), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return list.size();
            }

            @Override
            public Object getItem(int i) {
                return list.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                StfsPackage.Item it = list.get(i);
                LinearLayout c = card(it.dir ? R.drawable.ic_folder : R.drawable.ic_file, it.name,
                        it.dir ? p.children(it.index).size() + " elementos" : Ui.size(it.size) + (it.modified > 0 ? "  ·  " + Ui.date(it.modified) : ""),
                        null);
                return wrapRow(c);
            }
        });
        lv.setOnItemClickListener((a, v, i, id) -> {
            StfsPackage.Item it = list.get(i);
            if (it.dir) {
                pkgParent = it.index;
                showPackage();
            } else {
                showPackageFile(it);
            }
        });
        lv.setOnItemLongClickListener((a, v, i, id) -> {
            StfsPackage.Item it = list.get(i);
            new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setTitle(it.name)
                    .setItems(new String[]{it.dir ? "Copiar la carpeta al celular" : "Copiar al celular"}, (d, w) -> exportItem(it)).show();
            return true;
        });
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setScreen(col);
    }

    /** Una tarjeta como fila de lista, con un poco de espacio abajo. */
    private View wrapRow(LinearLayout card) {
        FrameLayout w = new FrameLayout(this);
        w.setPadding(0, 0, 0, dp(8));
        if (card.getParent() instanceof ViewGroup) ((ViewGroup) card.getParent()).removeView(card);
        w.addView(card, new FrameLayout.LayoutParams(-1, -2));
        w.setLayoutParams(new AbsListViewLayout(-1, -2));
        return w;
    }

    /** Los parámetros de una fila de lista. */
    private static final class AbsListViewLayout extends android.widget.AbsListView.LayoutParams {
        AbsListViewLayout(int w, int h) {
            super(w, h);
        }
    }

    private String pathInPackage(StfsPackage p, int idx) {
        StringBuilder b = new StringBuilder();
        int i = idx, guard = 0;
        while (i >= 0 && guard++ < 40) {
            b.insert(0, "/" + p.items.get(i).name);
            i = p.items.get(i).parent;
        }
        return b.toString();
    }

    private void showPackageFile(StfsPackage.Item it) {
        final StfsPackage p = pkg;
        exec.execute(() -> {
            try {
                byte[] head = p.read(it, 0, (int) Math.min(it.size, 64 * 1024));
                ui.post(() -> showPackageFileDialog(it, head));
            } catch (Throwable t) {
                ui.post(() -> fail(t));
            }
        });
    }

    private void showPackageFileDialog(StfsPackage.Item it, byte[] head) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(22), dp(18), dp(22), dp(6));
        TextView t = Ui.text(this, it.name, 19, Ui.TEXT, true);
        box.addView(t);
        TextView info = Ui.text(this, "Tamaño: " + Ui.size(it.size) + (it.modified > 0 ? "\nFecha: " + Ui.date(it.modified) : ""), 14, Ui.SUB, false);
        info.setPadding(0, dp(6), 0, dp(10));
        box.addView(info);
        Bitmap bmp = head.length > 8 && it.size <= head.length ? android.graphics.BitmapFactory.decodeByteArray(head, 0, head.length) : null;
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            box.addView(iv, new LinearLayout.LayoutParams(-1, dp(220)));
        } else if (head.length > 0 && looksLikeText(head)) {
            TextView tv = Ui.text(this, new String(head, java.nio.charset.StandardCharsets.UTF_8), 12, Ui.TEXT, false);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setBackground(Ui.round(Ui.BG, dp(10)));
            tv.setPadding(dp(10), dp(8), dp(10), dp(8));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            box.addView(sv, new LinearLayout.LayoutParams(-1, dp(220)));
        } else if (head.length > 0) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(head.length, 96); i++) {
                hex.append(String.format(java.util.Locale.US, "%02X ", head[i] & 0xFF));
                if (i % 16 == 15) hex.append('\n');
            }
            TextView tv = Ui.text(this, "Los primeros bytes:\n" + hex, 11.5f, Ui.SUB, false);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            box.addView(tv);
        }
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setView(box)
                .setPositiveButton("Copiar al celular", (d, w) -> exportItem(it))
                .setNegativeButton("Cerrar", null).show();
    }

    private static boolean looksLikeText(byte[] b) {
        int bad = 0, n = Math.min(b.length, 2000);
        for (int i = 0; i < n; i++) {
            int c = b[i] & 0xFF;
            if (c == 0 || (c < 9) || (c > 13 && c < 32)) bad++;
        }
        return bad * 50 < n;
    }

    // ------------------------------------------------------------------ buscar

    private static final class Idx {
        String path, name, titleId, lower;
        boolean dir;
    }

    private void showSearch() {
        mode = Mode.SEARCH;
        final Xtaf x = fs;
        if (x == null) return;
        if (index == null) {
            showOverlay("Leyendo todo lo que hay en la unidad…");
            setOverlayText("Preparando…", "Armando la lista para buscar…");
            skip.set(false);
            exec.execute(() -> {
                List<Idx> out = new ArrayList<>();
                try {
                    walk(x, null, "", 0, out);
                } catch (Throwable ignored) {
                    // lo que alcanzó a leer sirve
                }
                ui.post(() -> {
                    hideOverlay();
                    if (fs != x) return;
                    if (skip.get()) {
                        showBrowser();      // lo cancelaron: no se guarda una lista a medias
                        return;
                    }
                    index = out;
                    buildSearchScreen();
                });
            });
        } else {
            buildSearchScreen();
        }
    }

    private void walk(Xtaf x, Xtaf.Entry dir, String path, int depth, List<Idx> out) throws java.io.IOException {
        if (depth > 12 || out.size() > 200_000 || skip.get()) return;
        List<Xtaf.Entry> kids = dir == null ? x.listRoot() : x.list(dir);
        for (Xtaf.Entry e : kids) {
            Idx i = new Idx();
            i.path = e.path;
            i.name = e.name;
            i.lower = e.name.toLowerCase(java.util.Locale.ROOT);
            i.dir = e.dir;
            String[] p = e.path.split("/");
            if (p.length >= 3 && p[0].equalsIgnoreCase("Content") && HEX8.matcher(p[2]).matches()) i.titleId = p[2].toUpperCase(java.util.Locale.US);
            out.add(i);
            if (e.dir) walk(x, e, e.path, depth + 1, out);
        }
    }

    private void buildSearchScreen() {
        LinearLayout col = Ui.column(this);
        col.addView(header("Buscar", "Por nombre de juego, de carpeta o de archivo", true));
        android.widget.EditText box = new android.widget.EditText(this);
        box.setHint("Escribe, por ejemplo: gears");
        box.setHintTextColor(Ui.SUB);
        box.setTextColor(Ui.TEXT);
        box.setSingleLine(true);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.setMargins(dp(14), dp(4), dp(14), dp(8));
        col.addView(box, bl);
        TextView count = Ui.text(this, "", 12, Ui.SUB, false);
        count.setPadding(dp(18), 0, dp(14), dp(6));
        col.addView(count);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(dp(12), 0, dp(12), dp(16));
        lv.setClipToPadding(false);
        List<Idx> shown = new ArrayList<>();
        lv.setAdapter(new BaseAdapter() {
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
            public View getView(int i, View v, ViewGroup parent) {
                Idx x = shown.get(i);
                String n = x.titleId != null && x.path.split("/").length == 3 ? titles.name(x.titleId) : null;
                String title = n != null ? x.titleId + " (" + n + ")" : x.name;
                LinearLayout c = card(x.dir ? R.drawable.ic_folder : R.drawable.ic_file, title, x.path.replace("/", " › "), null);
                if (x.titleId != null && n != null && x.path.split("/").length == 3) {
                    Bitmap b = titles.icon(x.titleId);
                    if (b != null) {
                        FrameLayout badge = (FrameLayout) c.getChildAt(0);
                        badge.removeAllViews();
                        ImageView iv = new ImageView(MainActivity.this);
                        iv.setImageBitmap(b);
                        badge.setBackground(null);
                        badge.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                    }
                }
                return wrapRow(c);
            }
        });
        lv.setOnItemClickListener((a, v, i, id) -> {
            Idx x = shown.get(i);
            if (x.dir) {
                browse(x.path);
            } else {
                int k = x.path.lastIndexOf('/');
                browse(k < 0 ? "" : x.path.substring(0, k));
            }
        });
        box.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                shown.clear();
                if (q.length() >= 2 && index != null) {
                    List<Idx> titlesFirst = new ArrayList<>(), others = new ArrayList<>();
                    for (Idx x : index) {
                        boolean isTitleDir = x.dir && x.titleId != null && x.path.split("/").length == 3;
                        String tn = isTitleDir ? titles.name(x.titleId) : null;
                        if (isTitleDir && (x.lower.contains(q) || (tn != null && tn.toLowerCase(java.util.Locale.ROOT).contains(q)))) titlesFirst.add(x);
                        else if (x.lower.contains(q)) others.add(x);
                        if (titlesFirst.size() + others.size() > 400) break;
                    }
                    shown.addAll(titlesFirst);
                    shown.addAll(others);
                    count.setText(shown.isEmpty() ? "No encontré nada con «" + s + "»"
                            : shown.size() + (shown.size() > 400 ? "+" : "") + " resultados");
                } else {
                    count.setText(index == null ? "" : q.isEmpty() ? index.size() + " elementos en la unidad" : "Escribe al menos 2 letras");
                }
                ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
            }
        });
        count.setText(index.size() + " elementos en la unidad");
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setScreen(col);
        box.requestFocus();
    }

    // ------------------------------------------------------------------ radiografía

    private void showRadiography() {
        final Xtaf x = fs;
        if (x == null) return;
        skip.set(false);
        showOverlay("Midiendo lo que ocupa cada juego…");
        setOverlayText("Preparando…", "Midiendo lo que ocupa cada juego…", "Omitir");
        exec.execute(() -> {
            try {
                Map<String, long[]> sizes = new HashMap<>();      // juego -> {bytes, cuántos perfiles}
                Map<String, String> where = new HashMap<>();
                long other = 0, content = 0;
                Xtaf.Entry c = x.find("Content");
                long all = 0;
                for (Xtaf.Entry e : x.listRoot()) {
                    if (skip.get()) break;
                    if (c != null && e.path.equals(c.path)) continue;
                    all += e.dir ? x.totalSize(e, skip) : e.size;
                }
                other = all;
                if (c != null) {
                    List<Xtaf.Entry> profiles = x.list(c);
                    int n = 0;
                    for (Xtaf.Entry p : profiles) {
                        if (!p.dir) continue;
                        for (Xtaf.Entry t : x.list(p)) {
                            if (skip.get()) break;
                            if (!t.dir) continue;
                            n++;
                            final int k = n;
                            if (k % 3 == 0) ui.post(() -> setOverlayDetail("Midiendo… (" + k + " carpetas de juegos)"));
                            long sz = x.totalSize(t, skip);
                            long[] acc = sizes.computeIfAbsent(t.name.toUpperCase(java.util.Locale.US), k2 -> new long[2]);
                            acc[0] += sz;
                            acc[1]++;
                            where.putIfAbsent(t.name.toUpperCase(java.util.Locale.US), t.path);
                            content += sz;
                        }
                    }
                }
                List<Map.Entry<String, long[]>> list = new ArrayList<>(sizes.entrySet());
                list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
                final long ct = content, ot = other;
                ui.post(() -> {
                    hideOverlay();
                    if (fs != x) return;
                    buildRadiographyScreen(list, where, ct, ot);
                });
            } catch (Throwable e) {
                ui.post(() -> fail(e));
            }
        });
    }

    private static final int[] BAR_COLORS = {0xFF4ADE6B, 0xFF36D1B6, 0xFF3D9BFF, 0xFFB47CFF, 0xFFFFC857, 0xFFFF8A5B};

    private void buildRadiographyScreen(List<Map.Entry<String, long[]>> list, Map<String, String> where, long content, long other) {
        mode = Mode.RADIO;
        LinearLayout col = Ui.column(this);
        col.addView(header("Radiografía", partName, true));
        long part = 0;
        try {
            part = parts != null && !parts.isEmpty() ? parts.get(0).source.length() : 0;
            for (XboxDisk.Part p : parts) if (p.name.equals(partName)) part = p.source.length();
        } catch (Exception ignored) {
        }
        LinearLayout sum = Ui.column(this);
        sum.setPadding(dp(16), dp(14), dp(16), dp(14));
        sum.setBackground(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        sum.addView(Ui.text(this, Ui.size(content + other) + " usados" + (part > 0 ? " de " + Ui.size(part) : ""), 20, Ui.TEXT, true));
        TextView t2 = Ui.text(this, "Juegos, partidas y perfiles: " + Ui.size(content) + "  ·  Otros archivos: " + Ui.size(other), 12.5f, Ui.SUB, false);
        t2.setPadding(0, dp(4), 0, 0);
        sum.addView(t2);
        if (part > 0) {
            View bar = new View(this);
            float frac = Math.min(1f, (content + other) / (float) part);
            bar.setBackground(Ui.gradient(dp(5), 0xFF4ADE6B, 0xFF36D1B6));
            FrameLayout track = new FrameLayout(this);
            track.setBackground(Ui.round(Ui.SURFACE3, dp(5)));
            track.addView(bar, new FrameLayout.LayoutParams(Math.max(dp(6), (int) ((getResources().getDisplayMetrics().widthPixels - dp(60)) * frac)), -1));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(10));
            tl.topMargin = dp(10);
            sum.addView(track, tl);
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(dp(14), dp(4), dp(14), dp(10));
        col.addView(sum, sl);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setPadding(dp(12), 0, dp(12), dp(16));
        lv.setClipToPadding(false);
        final long max = list.isEmpty() ? 1 : Math.max(1, list.get(0).getValue()[0]);
        final long grand = Math.max(1, content);
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return list.size();
            }

            @Override
            public Object getItem(int i) {
                return list.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View v, ViewGroup parent) {
                Map.Entry<String, long[]> e = list.get(i);
                String id = e.getKey();
                String n = titles.name(id);
                LinearLayout row = Ui.column(MainActivity.this);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout top = Ui.row(MainActivity.this);
                ImageView ic = new ImageView(MainActivity.this);
                Bitmap b = titles.icon(id);
                if (b != null) ic.setImageBitmap(b);
                else ic.setImageDrawable(Ui.icon(MainActivity.this, R.drawable.ic_folder, Ui.ACCENT2));
                top.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
                LinearLayout names = Ui.column(MainActivity.this);
                names.setPadding(dp(12), 0, dp(8), 0);
                TextView a = Ui.text(MainActivity.this, n != null ? n : id, 14.5f, Ui.TEXT, true);
                a.setSingleLine(true);
                a.setEllipsize(android.text.TextUtils.TruncateAt.END);
                names.addView(a);
                names.addView(Ui.text(MainActivity.this, id + (e.getValue()[1] > 1 ? "  ·  en " + e.getValue()[1] + " perfiles" : ""), 11.5f, Ui.SUB, false));
                top.addView(names, Ui.weight(1));
                top.addView(Ui.text(MainActivity.this, Ui.size(e.getValue()[0]) + "\n" + Math.round(e.getValue()[0] * 100f / grand) + " %", 12.5f, Ui.ACCENT2, true));
                row.addView(top);
                FrameLayout track = new FrameLayout(MainActivity.this);
                track.setBackground(Ui.round(Ui.SURFACE3, dp(4)));
                View bar = new View(MainActivity.this);
                bar.setBackground(Ui.round(BAR_COLORS[i % BAR_COLORS.length], dp(4)));
                int full = getResources().getDisplayMetrics().widthPixels - dp(48);
                track.addView(bar, new FrameLayout.LayoutParams(Math.max(dp(4), (int) (full * (e.getValue()[0] / (float) max))), -1));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(8));
                tl.topMargin = dp(8);
                row.addView(track, tl);
                row.setBackground(Ui.rippleRound(0x00000000, dp(14)));
                row.setLayoutParams(new AbsListViewLayout(-1, -2));
                return row;
            }
        });
        lv.setOnItemClickListener((a, v, i, id) -> {
            String p = where.get(list.get(i).getKey());
            if (p != null) browse(p);
        });
        if (list.isEmpty()) {
            TextView e = Ui.text(this, "No encontré juegos en esta partición (¿es la de datos?)", 14, Ui.SUB, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(30), dp(40), dp(30), 0);
            col.addView(e);
        }
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setScreen(col);
    }

    // ------------------------------------------------------------------ «Preparando…»

    private void showOverlay(String detail) {
        if (overlay != null) {
            setOverlayDetail(detail);
            return;
        }
        FrameLayout o = new FrameLayout(this);
        o.setBackgroundColor(0xF20A0F0C);
        o.setClickable(true);
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Ring ring = new Ring(this);
        col.addView(ring, new LinearLayout.LayoutParams(dp(128), dp(128)));
        TextView title = Ui.text(this, "Preparando…", 22, Ui.TEXT, true);
        title.setId(11);
        title.setPadding(0, dp(24), 0, dp(6));
        col.addView(title);
        TextView d = Ui.text(this, detail, 13.5f, Ui.SUB, false);
        d.setId(12);
        d.setGravity(Gravity.CENTER);
        d.setPadding(dp(30), 0, dp(30), 0);
        col.addView(d);
        TextView skipBtn = Ui.text(this, "Omitir", 13, Ui.ACCENT, true);
        skipBtn.setPadding(dp(20), dp(26), dp(20), dp(10));
        skipBtn.setOnClickListener(v -> skip.set(true));
        skipBtn.setId(13);
        skipBtn.setVisibility(View.GONE);
        col.addView(skipBtn);
        o.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(o, new FrameLayout.LayoutParams(-1, -1));
        overlay = o;
        overlayDetail = d;
    }

    private void setOverlayDetail(String s) {
        if (overlayDetail != null) overlayDetail.setText(s);
    }

    private void setOverlayText(String title, String detail) {
        setOverlayText(title, detail, "Omitir");
    }

    private void setOverlayText(String title, String detail, String button) {
        if (overlay == null) return;
        TextView t = overlay.findViewById(11);
        if (t != null) t.setText(title);
        setOverlayDetail(detail);
        TextView skipBtn = overlay.findViewById(13);
        if (skipBtn != null) {
            skipBtn.setText(button);
            skipBtn.setVisibility(View.VISIBLE);
        }
    }

    private void hideOverlay() {
        if (overlay != null) {
            root.removeView(overlay);
            overlay = null;
            overlayDetail = null;
        }
    }

    /** La primera vez que se abre la app: prepara lo que necesita y avisa que solo pasa una vez. */
    private void firstRun() {
        showOverlay("Revisando que el celular pueda leer memorias USB…");
        exec.execute(() -> {
            boolean host = getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
            step(900, "Preparando la lista de juegos…");
            titles.count();
            step(800, "Preparando carátulas e íconos…");
            step(700, "Ya casi…");
            getSharedPreferences("arca", MODE_PRIVATE).edit().putBoolean("preparado", true).apply();
            ui.post(() -> {
                hideOverlay();
                readyCard(host);
            });
        });
    }

    private void step(long ms, String text) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
        ui.post(() -> setOverlayDetail(text));
    }

    private void readyCard(boolean usbHost) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(22), dp(24), dp(6));
        Ring ring = new Ring(this);
        ring.setMode(Ring.DONE);
        box.addView(ring, new LinearLayout.LayoutParams(dp(84), dp(84)));
        TextView t = Ui.text(this, "¡Todo listo!", 22, Ui.TEXT, true);
        t.setPadding(0, dp(14), 0, dp(8));
        box.addView(t);
        TextView m = Ui.text(this, "Ya preparé lo que necesito para trabajar. Este proceso solo se hace una vez.\n\n"
                + "Desde ahora Arca lee tus memorias y discos sin internet: la conexión solo se usa para buscar actualizaciones de la app."
                + (usbHost ? "" : "\n\n⚠️ Este celular dice que no puede leer memorias USB por cable OTG. Arca solo podrá abrir carpetas que Android ya haya montado."), 14, Ui.SUB, false);
        m.setGravity(Gravity.CENTER);
        m.setLineSpacing(0, 1.2f);
        box.addView(m);
        new AlertDialog.Builder(this, R.style.Theme_Arca_Dialog).setView(box).setPositiveButton("Empezar", null).setCancelable(false).show();
    }
}
