package com.forja.formateador;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Forja: elegir la unidad USB, el formato, la tabla de particiones y el clúster, y formatear. */
public class MainActivity extends Activity {
    private static final String ACTION_PERM = "com.forja.formateador.PERMISO_USB";

    private UsbManager um;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // unidad
    private UsbDevice device;
    private String deviceName, currentLayout, error, done;
    private long diskBytes;
    private int sectorSize;
    private boolean busy, checking;

    // opciones
    private FsType fs = FsType.EXFAT;
    private Forja.Table table = Forja.Table.MBR;
    private int cluster;          // 0 = el recomendado
    private String label = "";
    private boolean fullErase;

    private LinearLayout deviceBox, optionsBox, progressCard, resultBox;
    private TextView progressStep, progressPct;
    private ProgressBar progressBar;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installCrashLog();
        um = getSystemService(UsbManager.class);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_PERM);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbReceiver, f);
        handleIntent(getIntent());
        showLastCrash();
    }

    // ------------------------------------------------------------------ errores

    private java.io.File crashFile() {
        return new java.io.File(getFilesDir(), "ultimo_error.txt");
    }

    /** Si la app se cierra por un error, lo guarda para mostrarlo la próxima vez. */
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
                w.println("Forja " + version + " · Android " + Build.VERSION.RELEASE + " · " + Build.MODEL);
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
        new AlertDialog.Builder(this, R.style.Theme_Forja_Dialog)
                .setTitle("Forja se cerró la última vez")
                .setMessage("Copia esto y mándaselo a quien te ayuda con la app:\n\n" + t)
                .setPositiveButton("Copiar", (d, w) -> {
                    getSystemService(android.content.ClipboardManager.class)
                            .setPrimaryClip(android.content.ClipData.newPlainText("error", t));
                    android.widget.Toast.makeText(this, "Copiado", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** Texto de un error para la pantalla: el mensaje, o el tipo y dónde pasó si no trae mensaje. */
    private static String describeError(Throwable e) {
        if (e instanceof OutOfMemoryError) return "el celular se quedó sin memoria (" + e.getMessage() + ")";
        String m = e.getMessage();
        if (e instanceof java.io.IOException && m != null) return m;
        StringBuilder b = new StringBuilder(e.getClass().getSimpleName());
        if (m != null) b.append(": ").append(m);
        StackTraceElement[] st = e.getStackTrace();
        for (int i = 0; i < Math.min(3, st.length); i++) b.append("\n  en ").append(st[i].getClassName().replaceFirst(".*\\.", ""))
                .append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
        return b.toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        UsbDevice d = i == null ? null : usbExtra(i);
        if (d != null && UsbDisk.storageInterface(d) != null) select(d);
        else scan();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private static UsbDevice usbExtra(Intent i) {
        return Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                : (UsbDevice) i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            UsbDevice d = usbExtra(i);
            switch (String.valueOf(i.getAction())) {
                case ACTION_PERM -> {
                    if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) select(d);
                    else {
                        error = "Sin el permiso, Forja no puede formatear la unidad.";
                        render();
                    }
                }
                case UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (!busy && d != null && UsbDisk.storageInterface(d) != null) select(d);
                }
                case UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (d != null && device != null && d.getDeviceId() == device.getDeviceId()) {
                        if (busy) error = "Se desconectó la unidad en medio del formato. Conéctala y formatea otra vez.";
                        device = null;
                        diskBytes = 0;
                        render();
                    }
                }
                default -> {
                }
            }
        }
    };

    // ------------------------------------------------------------------ unidad

    private void scan() {
        if (busy) return;
        List<UsbDevice> list = UsbDisk.find(um);
        if (list.isEmpty()) {
            device = null;
            diskBytes = 0;
            render();
        } else if (list.size() == 1) {
            select(list.get(0));
        } else {
            String[] names = new String[list.size()];
            for (int i = 0; i < names.length; i++) names[i] = label(list.get(i));
            new AlertDialog.Builder(this, R.style.Theme_Forja_Dialog)
                    .setTitle("¿Cuál unidad?")
                    .setItems(names, (dd, w) -> select(list.get(w)))
                    .show();
        }
    }

    private static String label(UsbDevice d) {
        String n = d.getProductName(), m = d.getManufacturerName();
        if (n == null) n = "Unidad USB";
        return m != null && !n.toLowerCase().contains(m.toLowerCase()) ? m + " " + n : n;
    }

    private void select(UsbDevice d) {
        if (busy) return;
        device = d;
        deviceName = label(d);
        diskBytes = 0;
        error = null;
        done = null;
        if (!um.hasPermission(d)) {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PERM).setPackage(getPackageName()),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            um.requestPermission(d, pi);
            render();
            return;
        }
        checking = true;
        render();
        io.execute(() -> {
            String err = null, name = null, layout = null;
            long bytes = 0;
            int ss = 512;
            UsbDisk disk = null;
            try {
                disk = new UsbDisk(um, d);
                name = disk.name();
                ss = disk.sectorSize();
                bytes = disk.sectors() * ss;
                layout = describe(disk);
            } catch (Throwable e) {
                err = describeError(e);
            } finally {
                if (disk != null) disk.close();
            }
            final String fe = err, fn = name, fl = layout;
            final long fb = bytes;
            final int fs2 = ss;
            main.post(() -> {
                checking = false;
                if (device == null || device.getDeviceId() != d.getDeviceId()) return;
                error = fe;
                if (fn != null && !fn.isEmpty()) deviceName = fn;
                diskBytes = fb;
                sectorSize = fs2;
                currentLayout = fl;
                // más de 2 TB: MBR no alcanza
                if (diskBytes > Partitioner.mbrLimit(sectorSize)) table = Forja.Table.GPT;
                cluster = 0;
                render();
            });
        });
    }

    /** Qué tiene ahora la unidad (tabla de particiones), en palabras. */
    private static String describe(BlockDevice d) {
        try {
            byte[] s0 = new byte[d.sectorSize()];
            d.read(0, s0, 0, 1);
            if ((s0[510] & 0xFF) != 0x55 || (s0[511] & 0xFF) != 0xAA) return "Sin tabla de particiones";
            if ((s0[450] & 0xFF) == 0xEE) return "Tabla GPT";
            int n = 0;
            for (int i = 0; i < 4; i++) if ((s0[446 + i * 16 + 4] & 0xFF) != 0) n++;
            if (n == 0) return "Sin particiones (o formato sin tabla)";
            return "Tabla MBR · " + n + (n == 1 ? " partición" : " particiones");
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ formatear

    private long partBytes() {
        return Forja.partitionBytes(diskBytes, sectorSize, table);
    }

    private int chosenCluster() {
        List<Integer> ok = fs.clusterChoices(partBytes(), sectorSize);
        if (cluster > 0 && ok.contains(cluster)) return cluster;
        return fs.defaultCluster(partBytes(), sectorSize);
    }

    private void confirm() {
        String size = FsType.sizeText(diskBytes);
        String what = fs.title + " · " + table + " · " + fs.unitName() + " de " + FsType.sizeText(chosenCluster())
                + (fs.cleanLabel(label).isEmpty() ? "" : " · «" + fs.cleanLabel(label) + "»");
        new AlertDialog.Builder(this, R.style.Theme_Forja_Dialog)
                .setTitle("¿Formatear la unidad?")
                .setMessage("Se va a BORRAR TODO lo que hay en:\n\n" + deviceName + " · " + size
                        + "\n\nQuedará así: " + what + "\n\nEsto no se puede deshacer.")
                .setPositiveButton("Formatear", (d, w) -> new AlertDialog.Builder(this, R.style.Theme_Forja_Dialog)
                        .setTitle("Última confirmación")
                        .setMessage("¿Seguro que es la unidad correcta?\n" + deviceName + " · " + size)
                        .setPositiveButton("Sí, formatear", (d2, w2) -> run())
                        .setNegativeButton("Cancelar", null)
                        .show())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void run() {
        UsbDevice d = device;
        if (d == null) return;
        Forja.Options o = new Forja.Options();
        o.fs = fs;
        o.table = table;
        o.clusterBytes = chosenCluster();
        o.label = label;
        o.fullErase = fullErase;
        busy = true;
        error = null;
        done = null;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
        setProgress(0f, "Preparando…");
        io.execute(() -> {
            String err = null;
            UsbDisk disk = null;
            long start = System.currentTimeMillis();
            try {
                disk = new UsbDisk(um, d);
                Forja.format(disk, o, new Progress() {
                    long last;

                    @Override
                    public void update(float fraction, String step) {
                        long now = System.currentTimeMillis();
                        if (now - last < 120 && fraction < 1f) return;
                        last = now;
                        main.post(() -> setProgress(fraction, step));
                    }

                    @Override
                    public boolean cancelled() {
                        return false;
                    }
                });
            } catch (Throwable e) {
                err = describeError(e);
            } finally {
                if (disk != null) disk.close();
            }
            final String fe = err;
            final long secs = (System.currentTimeMillis() - start) / 1000;
            main.post(() -> {
                busy = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (fe != null) error = "No se pudo formatear: " + fe;
                else {
                    done = "¡Formateada en " + o.fs.title + "!  (" + time(secs) + ")";
                    currentLayout = "Tabla " + o.table + " · 1 partición " + o.fs.title;
                }
                render();
            });
        });
    }

    private static String time(long s) {
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }

    // ------------------------------------------------------------------ interfaz

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF22120C, Ui.BG, Ui.BG}));
        sv.setFitsSystemWindows(true);
        LinearLayout col = Ui.column(this);
        col.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(col);

        LinearLayout head = Ui.row(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(Ui.text(this, "Forja", 26, Ui.TEXT, true));
        titles.addView(Ui.text(this, "Formateador de unidades · sin root · sin anuncios", 12.5f, Ui.SUB, false));
        head.addView(titles, Ui.weight(1));
        head.addView(Ui.iconButton(this, R.drawable.ic_info, 42, v -> showAbout()));
        col.addView(head);

        deviceBox = Ui.column(this);
        col.addView(deviceBox, margins(0, 22, 0, 0));
        optionsBox = Ui.column(this);
        col.addView(optionsBox, margins(0, 6, 0, 0));

        progressCard = card();
        progressPct = Ui.text(this, "0 %", 34, Ui.ACCENT2, true);
        progressPct.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        progressStep = Ui.text(this, "", 14.5f, Ui.TEXT, true);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E2420));
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progressCard.addView(progressPct);
        progressCard.addView(progressStep);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(10));
        pl.setMargins(0, dp(14), 0, dp(12));
        progressCard.addView(progressBar, pl);
        progressCard.addView(Ui.text(this, "⚠  No desconectes la unidad ni cierres la app hasta que termine.", 12.5f, Ui.WARN, false));
        col.addView(progressCard, margins(0, 14, 0, 0));

        resultBox = Ui.column(this);
        col.addView(resultBox, margins(0, 14, 0, 0));

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = Ui.text(this, "FAT32 · exFAT · NTFS · ext4\nMBR y GPT · discos de 1 GB a 4 TB · sectores de 512 B y 4 KB", 11.5f, 0xFF6A6474, false);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, margins(0, 26, 0, 0));
        setContentView(sv);
        render();
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout card() {
        LinearLayout c = Ui.column(this);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setBackground(Ui.roundStroke(Ui.SURFACE, dp(22), dp(1), Ui.STROKE));
        return c;
    }

    private void setProgress(float f, String step) {
        progressStep.setText(step);
        if (f < 0) progressBar.setIndeterminate(true);
        else {
            progressBar.setIndeterminate(false);
            int v = Math.round(Math.max(0, Math.min(1, f)) * 1000);
            progressBar.setProgress(v);
            progressPct.setText((v / 10) + " %");
        }
    }

    private void render() {
        renderDevice();
        renderOptions();
        progressCard.setVisibility(busy ? View.VISIBLE : View.GONE);
        resultBox.removeAllViews();
        if (!busy && (done != null || (error != null && diskBytes > 0))) {
            boolean ok = error == null;
            LinearLayout c = card();
            c.setBackground(Ui.roundStroke(ok ? 0xFF10261C : 0xFF2C1218, dp(22), dp(1), ok ? 0xFF1F6B52 : 0xFF6B2232));
            c.addView(Ui.text(this, ok ? done : "Algo salió mal", 16, ok ? Ui.GOOD : Ui.BAD, true));
            TextView t = Ui.text(this, ok ? "Desconecta la unidad y vuelve a conectarla para usarla en el celular, o llévala a la PC, la consola o la TV."
                    : error, 13.5f, Ui.TEXT, false);
            t.setPadding(0, dp(6), 0, 0);
            c.addView(t);
            resultBox.addView(c);
        }
    }

    private void renderDevice() {
        deviceBox.removeAllViews();
        LinearLayout c = card();
        c.setBackground(Ui.roundStroke(0xFF1A1418, dp(24), dp(1), 0xFF4A2A1E));
        LinearLayout top = Ui.row(this);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.gradient(dp(16), Ui.ACCENT2, Ui.ACCENT, Ui.VIOLET));
        TextView glyph = Ui.text(this, "⛁", 22, 0xFF0A0A0E, true);
        glyph.setGravity(Gravity.CENTER);
        badge.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        top.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(14), 0, 0, 0);
        String title, sub;
        if (device == null) {
            title = "Conecta una unidad USB";
            sub = "Memoria, disco externo o lector de tarjetas, por OTG.";
        } else if (!um.hasPermission(device)) {
            title = deviceName;
            sub = "Toca «Permitir» en el aviso de Android";
        } else if (checking) {
            title = deviceName;
            sub = "Leyendo la unidad…";
        } else if (diskBytes == 0) {
            title = deviceName;
            sub = error != null ? error : "—";
        } else {
            title = deviceName;
            sub = FsType.sizeText(diskBytes) + (sectorSize == 4096 ? " · sectores de 4 KB" : "")
                    + (currentLayout != null ? "\nAhora: " + currentLayout : "");
        }
        TextView tt = Ui.text(this, title, 17, Ui.TEXT, true);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(tt);
        texts.addView(Ui.text(this, sub, 13, Ui.SUB, false));
        top.addView(texts, Ui.weight(1));
        if (!busy) top.addView(Ui.iconButton(this, R.drawable.ic_refresh, 42, v -> {
            if (device != null) select(device);
            else scan();
        }));
        c.addView(top);
        if (checking) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setIndeterminate(true);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            c.addView(pb, margins(0, 12, 0, 0));
        }
        deviceBox.addView(c);
    }

    private TextView section(String s) {
        TextView t = Ui.text(this, s.toUpperCase(), 12, Ui.ACCENT2, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(18), 0, dp(8));
        return t;
    }

    private void renderOptions() {
        optionsBox.removeAllViews();
        if (busy || device == null || diskBytes == 0) {
            if (!busy && device == null) {
                TextView how = Ui.text(this, "1.  Conecta la unidad al celular (OTG).\n2.  Toca «Permitir».\n3.  Elige formato, tabla y clúster.\n4.  Formatea.",
                        13.5f, Ui.SUB, false);
                how.setLineSpacing(dp(4), 1f);
                how.setPadding(dp(6), dp(16), 0, 0);
                optionsBox.addView(how);
            }
            return;
        }
        long pb = partBytes();

        // formato
        optionsBox.addView(section("Formato"));
        LinearLayout grid = Ui.column(this);
        FsType[] all = FsType.values();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = Ui.row(this);
            for (int k = 0; k < 2; k++) {
                FsType t = all[r * 2 + k];
                String prob = t.problem(Forja.partitionBytes(diskBytes, sectorSize, Forja.Table.GPT), sectorSize);
                View tile = tile(t.title, prob != null ? prob : t.usage, t == fs, prob == null, () -> {
                    fs = t;
                    cluster = 0;
                    if (label.length() > fs.labelMax) label = label.substring(0, fs.labelMax);
                    render();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                if (k == 1) lp.setMargins(dp(10), 0, 0, 0);
                row.addView(tile, lp);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            if (r == 1) rl.setMargins(0, dp(10), 0, 0);
            grid.addView(row, rl);
        }
        optionsBox.addView(grid);

        // tabla de particiones
        optionsBox.addView(section("Tabla de particiones"));
        boolean mbrOk = diskBytes <= Partitioner.mbrLimit(sectorSize);
        LinearLayout trow = Ui.row(this);
        View mbr = tile("MBR", mbrOk ? "Funciona en todo: PCs viejas, consolas, TVs" : "Solo llega a " + FsType.sizeText(Partitioner.mbrLimit(sectorSize)),
                table == Forja.Table.MBR, mbrOk, () -> {
                    table = Forja.Table.MBR;
                    render();
                });
        View gpt = tile("GPT", "Discos de más de 2 TB y PCs modernas (UEFI)", table == Forja.Table.GPT, true, () -> {
            table = Forja.Table.GPT;
            render();
        });
        trow.addView(mbr, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(0, -1, 1);
        gl.setMargins(dp(10), 0, 0, 0);
        trow.addView(gpt, gl);
        optionsBox.addView(trow);

        // clúster
        optionsBox.addView(section("Tamaño de " + fs.unitName()));
        List<Integer> choices = fs.clusterChoices(pb, sectorSize);
        int rec = fs.defaultCluster(pb, sectorSize);
        int sel = chosenCluster();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(this);
        for (int cl : choices) {
            boolean on = cl == sel;
            TextView chip = Ui.text(this, FsType.sizeText(cl) + (cl == rec ? "  ★" : ""), 14, on ? 0xFF0A0A0E : Ui.TEXT, true);
            chip.setPadding(dp(16), dp(10), dp(16), dp(10));
            chip.setBackground(on ? Ui.gradient(dp(20), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, dp(20), dp(1), Ui.STROKE));
            chip.setOnClickListener(v -> {
                cluster = cl;
                render();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.setMargins(0, 0, dp(8), 0);
            chips.addView(chip, cp);
        }
        hs.addView(chips);
        optionsBox.addView(hs);
        TextView hint = Ui.text(this, "★ recomendado. Más chico: menos espacio desperdiciado con archivos pequeños. Más grande: un poco más rápido con archivos grandes.",
                12, Ui.SUB, false);
        hint.setPadding(dp(4), dp(8), 0, 0);
        optionsBox.addView(hint);

        // etiqueta
        optionsBox.addView(section("Nombre de la unidad"));
        EditText name = new EditText(this);
        name.setText(label);
        name.setHint(fs == FsType.FAT32 ? "Ej.: JUEGOS (máx. 11)" : "Ej.: Mis cosas (máx. " + fs.labelMax + ")");
        name.setHintTextColor(0xFF5E5868);
        name.setTextColor(Ui.TEXT);
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(fs.labelMax)});
        name.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        name.setPadding(dp(16), dp(12), dp(16), dp(12));
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                label = s.toString();
            }
        });
        optionsBox.addView(name);

        // borrado completo
        LinearLayout er = Ui.row(this);
        er.setPadding(dp(16), dp(14), dp(10), dp(14));
        er.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        LinearLayout et = Ui.column(this);
        et.addView(Ui.text(this, "Borrado completo", 15, Ui.TEXT, true));
        long mins = Math.max(1, diskBytes / (25L * 1024 * 1024) / 60);
        et.addView(Ui.text(this, "Escribe ceros en toda la unidad. Muy lento: unos " + (mins > 120 ? (mins / 60) + " horas" : mins + " min") + ".", 12.5f, Ui.SUB, false));
        er.addView(et, Ui.weight(1));
        Switch sw = new Switch(this);
        sw.setChecked(fullErase);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, 0xFF3A3444}));
        sw.setOnCheckedChangeListener((b, c) -> fullErase = c);
        er.addView(sw);
        er.setOnClickListener(v -> sw.toggle());
        optionsBox.addView(er, margins(0, 14, 0, 0));

        // botón
        String prob = fs.problem(pb, sectorSize);
        if (table == Forja.Table.MBR && !mbrOk) prob = "Con MBR no se puede usar todo el disco: elige GPT";
        LinearLayout go = Ui.column(this);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), dp(16), dp(16), dp(16));
        boolean enabled = prob == null;
        go.setBackground(enabled ? Ui.ripple(Ui.gradient(dp(22), Ui.ACCENT2, Ui.ACCENT, Ui.VIOLET), Ui.round(Color.WHITE, dp(22)))
                : Ui.roundStroke(0xFF1B1A22, dp(22), dp(1), Ui.STROKE));
        TextView gt = Ui.text(this, enabled ? "Formatear en " + fs.title : prob, 17, enabled ? 0xFF0A0A0E : Ui.SUB, true);
        gt.setGravity(Gravity.CENTER);
        go.addView(gt);
        if (enabled) {
            TextView gs = Ui.text(this, table + " · " + fs.unitName() + " de " + FsType.sizeText(sel) + " · borra todo", 12, 0xCC0A0A0E, false);
            gs.setGravity(Gravity.CENTER);
            go.addView(gs);
            go.setOnClickListener(v -> confirm());
        }
        optionsBox.addView(go, margins(0, 18, 0, 0));
    }

    private View tile(String title, String sub, boolean on, boolean enabled, Runnable r) {
        LinearLayout t = Ui.column(this);
        t.setPadding(dp(14), dp(13), dp(12), dp(13));
        if (on) t.setBackground(Ui.roundStroke(0xFF2A1A12, dp(18), dp(2), Ui.ACCENT));
        else t.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE), Ui.round(Color.WHITE, dp(18))));
        TextView a = Ui.text(this, title, 16, !enabled ? 0xFF5E5868 : on ? Ui.ACCENT2 : Ui.TEXT, true);
        t.addView(a);
        TextView b = Ui.text(this, sub, 11.5f, enabled ? Ui.SUB : 0xFF5E5868, false);
        b.setPadding(0, dp(3), 0, 0);
        t.addView(b);
        if (enabled) t.setOnClickListener(v -> r.run());
        return t;
    }

    private void showAbout() {
        new AlertDialog.Builder(this, R.style.Theme_Forja_Dialog)
                .setTitle("Forja")
                .setMessage("Formatea memorias USB, discos externos y tarjetas (por lector USB) desde el celular, sin root y sin anuncios.\n\n"
                        + "• FAT32, exFAT, NTFS y ext4 (Linux), con tabla MBR o GPT y el tamaño de clúster que elijas (solo te deja los que sirven).\n"
                        + "• Discos de 1 GB a 4 TB, con sectores de 512 B o 4 KB.\n"
                        + "• NTFS se crea con mkntfs de ntfs-3g (GPLv2+), el mismo de Linux, compilado dentro de la app.\n\n"
                        + "Cada formato se prueba con las herramientas oficiales de Linux (fsck.fat, fsck.exfat, e2fsck, ntfsfix, sgdisk) antes de publicarse.\n\n"
                        + "Un disco grande de escritorio necesita su propia fuente de corriente: el celular no puede alimentarlo.\n\n"
                        + "Hecho por santiagogd2513-cpu (idea, diseño y pruebas) y Claude de Anthropic (código).")
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
