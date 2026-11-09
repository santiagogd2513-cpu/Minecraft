package com.ventoy.nooficial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla única: elegir la memoria USB, ver qué tiene e instalar o actualizar Ventoy. */
public class MainActivity extends Activity {
    private static final String ACTION_PERM = "com.ventoy.nooficial.PERMISO_USB";

    private UsbManager um;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AssetFiles files;

    private UsbDevice device;
    private String deviceName;
    private VentoyInstaller.Info info;
    private String error;
    private boolean busy, checking;
    /** Formato de la partición «Ventoy» al instalar. */
    private com.forja.formateador.FsType partFs = com.forja.formateador.FsType.EXFAT;

    private LinearLayout deviceCard, actions, progressCard, resultCard;
    private TextView progressStep, progressPct;
    private ProgressBar progressBar;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onResume() {
        super.onResume();
        // ¿hay versión nueva de la app?
        Updater.resume(this);
        Updater.check(this);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        um = getSystemService(UsbManager.class);
        files = new AssetFiles(getAssets());
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

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            UsbDevice d = usbExtra(i);
            switch (String.valueOf(i.getAction())) {
                case ACTION_PERM -> {
                    if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) select(d);
                    else {
                        error = "Sin el permiso, la app no puede escribir en la USB.";
                        render();
                    }
                }
                case UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (!busy && d != null && UsbDisk.storageInterface(d) != null) select(d);
                }
                case UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (d != null && device != null && d.getDeviceId() == device.getDeviceId()) {
                        if (busy) error = "Se desconectó la USB en medio del proceso. Vuelve a conectarla e instala otra vez.";
                        device = null;
                        info = null;
                        render();
                    }
                }
                default -> {
                }
            }
        }
    };

    // ------------------------------------------------------------------ USB

    @SuppressWarnings("deprecation")
    private static UsbDevice usbExtra(Intent i) {
        return Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                : (UsbDevice) i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private void scan() {
        if (busy) return;
        List<UsbDevice> list = UsbDisk.find(um);
        if (list.isEmpty()) {
            device = null;
            info = null;
            render();
        } else if (list.size() == 1) {
            select(list.get(0));
        } else {
            String[] names = new String[list.size()];
            for (int i = 0; i < names.length; i++) names[i] = label(list.get(i));
            new AlertDialog.Builder(this, R.style.Theme_Ventoy_Dialog)
                    .setTitle("¿Cuál memoria USB?")
                    .setItems(names, (dd, w) -> select(list.get(w)))
                    .show();
        }
    }

    private static String label(UsbDevice d) {
        String n = d.getProductName();
        String m = d.getManufacturerName();
        if (n == null) n = "Memoria USB";
        return m != null && !n.toLowerCase().contains(m.toLowerCase()) ? m + " " + n : n;
    }

    private void select(UsbDevice d) {
        if (busy) return;
        device = d;
        deviceName = label(d);
        info = null;
        error = null;
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
            VentoyInstaller.Info got = null;
            String err = null, name = null;
            UsbDisk disk = null;
            try {
                disk = new UsbDisk(um, d);
                name = disk.name();
                got = VentoyInstaller.inspect(disk);
            } catch (Exception e) {
                err = e.getMessage();
            } finally {
                if (disk != null) disk.close();
            }
            final VentoyInstaller.Info fi = got;
            final String fe = err, fn = name;
            main.post(() -> {
                checking = false;
                if (device == null || device.getDeviceId() != d.getDeviceId()) return;
                info = fi;
                error = fe;
                if (fn != null && !fn.isEmpty()) deviceName = fn;
                render();
            });
        });
    }

    // ------------------------------------------------------------------ instalar / actualizar

    private void confirmInstall() {
        if (device == null || info == null) return;
        String size = Ui.size(info.diskSectors * 512);
        new AlertDialog.Builder(this, R.style.Theme_Ventoy_Dialog)
                .setTitle("¿Borrar todo e instalar Ventoy?")
                .setMessage("Se va a BORRAR TODO lo que hay en:\n\n" + deviceName + " · " + size
                        + "\n\nFotos, documentos, ISOs… todo. Esto no se puede deshacer.\n\nPartición «Ventoy» en " + partFs.title + ".")
                .setPositiveButton("Borrar e instalar", (d, w) -> new AlertDialog.Builder(this, R.style.Theme_Ventoy_Dialog)
                        .setTitle("Última confirmación")
                        .setMessage("¿Seguro que es la USB correcta?\n" + deviceName + " · " + size)
                        .setPositiveButton("Sí, instalar", (d2, w2) -> run(true))
                        .setNegativeButton("Cancelar", null)
                        .show())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmUpdate() {
        if (info == null) return;
        String from = info.version == null ? "desconocida" : info.version;
        new AlertDialog.Builder(this, R.style.Theme_Ventoy_Dialog)
                .setTitle("Actualizar Ventoy")
                .setMessage("Ventoy " + from + "  →  " + files.version()
                        + "\n\nSolo se cambia el arranque. Tus ISOs y archivos no se tocan.")
                .setPositiveButton("Actualizar", (d, w) -> run(false))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void run(boolean install) {
        UsbDevice d = device;
        if (d == null) return;
        final com.forja.formateador.FsType fsAtStart = partFs;
        busy = true;
        error = null;
        done = null;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
        setProgress(0f, install ? "Preparando la instalación…" : "Preparando la actualización…");
        io.execute(() -> {
            String err = null;
            VentoyInstaller.Info after = null;
            UsbDisk disk = null;
            long start = System.currentTimeMillis();
            try {
                disk = new UsbDisk(um, d);
                Progress p = new Progress() {
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
                };
                if (install) VentoyInstaller.install(disk, files, fsAtStart, p);
                else VentoyInstaller.update(disk, files, p);
                after = VentoyInstaller.inspect(disk);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                if (disk != null) disk.close();
            }
            final String fe = err;
            final VentoyInstaller.Info fa = after;
            final long secs = (System.currentTimeMillis() - start) / 1000;
            main.post(() -> {
                busy = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (fe != null) {
                    error = (install ? "No se pudo instalar: " : "No se pudo actualizar: ") + fe;
                } else {
                    info = fa;
                    done = (install ? "¡Ventoy " + files.version() + " instalado!" : "¡Ventoy actualizado a " + files.version() + "!")
                            + "  (" + secs + " s)";
                }
                render();
            });
        });
    }

    private String done;

    // ------------------------------------------------------------------ interfaz

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF101533, Ui.BG, Ui.BG}));
        sv.setFitsSystemWindows(true);
        LinearLayout col = Ui.column(this);
        col.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(col);

        // encabezado
        LinearLayout head = Ui.row(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = Ui.column(this);
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(Ui.text(this, "Ventoy no oficial", 23, Ui.TEXT, true));
        titles.addView(Ui.text(this, "Ventoy " + files.version() + " incluido · sin anuncios · sin internet", 12.5f, Ui.SUB, false));
        head.addView(titles, Ui.weight(1));
        head.addView(Ui.iconButton(this, R.drawable.ic_info, 42, v -> showAbout()));
        col.addView(head);

        deviceCard = Ui.column(this);
        col.addView(deviceCard, margins(0, 22, 0, 0));
        actions = Ui.column(this);
        col.addView(actions, margins(0, 14, 0, 0));

        progressCard = card();
        progressStep = Ui.text(this, "", 14.5f, Ui.TEXT, true);
        progressPct = Ui.text(this, "0 %", 34, Ui.ACCENT2, true);
        progressPct.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF232A44));
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        progressCard.addView(progressPct);
        progressCard.addView(progressStep);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(10));
        pl.setMargins(0, dp(14), 0, dp(12));
        progressCard.addView(progressBar, pl);
        progressCard.addView(Ui.text(this, "⚠  No desconectes la USB ni cierres la app hasta que termine.", 12.5f, Ui.WARN, false));
        col.addView(progressCard, margins(0, 14, 0, 0));

        resultCard = Ui.column(this);
        col.addView(resultCard, margins(0, 14, 0, 0));

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = Ui.text(this, "Basado en Ventoy de longpanda (GPLv3) · ventoy.net\nApp no oficial: no la hicieron los autores de Ventoy.", 11.5f, 0xFF5E6884, false);
        foot.setGravity(Gravity.CENTER);
        col.addView(foot, margins(0, 28, 0, 0));
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
        if (f < 0) {
            progressBar.setIndeterminate(true);
        } else {
            progressBar.setIndeterminate(false);
            int v = Math.round(Math.max(0, Math.min(1, f)) * 1000);
            progressBar.setProgress(v);
            progressPct.setText((v / 10) + " %");
        }
    }

    private void render() {
        renderDevice();
        renderActions();
        progressCard.setVisibility(busy ? View.VISIBLE : View.GONE);
        resultCard.removeAllViews();
        if (!busy && (done != null || error != null)) {
            boolean ok = error == null;
            LinearLayout c = card();
            c.setBackground(Ui.roundStroke(ok ? 0xFF0F2A22 : 0xFF2C1218, dp(22), dp(1), ok ? 0xFF1F6B52 : 0xFF6B2232));
            c.addView(Ui.text(this, ok ? done : "Algo salió mal", 16, ok ? Ui.GOOD : Ui.BAD, true));
            TextView t = Ui.text(this, ok
                    ? "Desconecta la USB y vuelve a conectarla. Luego copia tus ISOs a la partición «Ventoy» (con Bóveda o cualquier gestor) y arranca la PC desde la USB."
                    : error, 13.5f, Ui.TEXT, false);
            t.setPadding(0, dp(6), 0, 0);
            c.addView(t);
            resultCard.addView(c);
        }
    }

    private void renderDevice() {
        deviceCard.removeAllViews();
        LinearLayout c = card();
        c.setBackground(Ui.roundStroke(0xFF111830, dp(24), dp(1), 0xFF2C3770));
        LinearLayout top = Ui.row(this);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.gradient(dp(16), Ui.ACCENT2, Ui.ACCENT, Ui.VIOLET));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(Ui.icon(this, R.drawable.ic_download, 0xFF07090F));
        badge.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        top.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout texts = Ui.column(this);
        texts.setPadding(dp(14), 0, 0, 0);
        String title, sub;
        if (device == null) {
            title = "Conecta una memoria USB";
            sub = "Por OTG (adaptador o cable USB-C). Se detecta sola.";
        } else if (!um.hasPermission(device)) {
            title = deviceName;
            sub = "Toca «Permitir» en el aviso de Android";
        } else if (checking) {
            title = deviceName;
            sub = "Leyendo la USB…";
        } else if (info == null) {
            title = deviceName;
            sub = error != null ? "No se pudo leer la USB" : "—";
        } else {
            title = deviceName;
            sub = Ui.size(info.diskSectors * 512);
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

        if (info != null && device != null) {
            LinearLayout chips = Ui.row(this);
            chips.setPadding(0, dp(16), 0, 0);
            if (info.hasVentoy) {
                boolean old = info.version != null && newer(files.version(), info.version);
                chips.addView(chip(old ? "Ventoy " + info.version + " · hay versión nueva" : "Ventoy " + (info.version == null ? "" : info.version) + " instalado",
                        old ? Ui.WARN : Ui.GOOD));
                if (info.gpt) chips.addView(chip("GPT", Ui.SUB), chipMargin());
            } else {
                chips.addView(chip("Sin Ventoy", Ui.SUB));
            }
            c.addView(chips);
            if (info.hasVentoy) {
                TextView parts = Ui.text(this, "Partición 1 «Ventoy» (" + (info.part1Fs == null ? "?" : info.part1Fs.title) + "): " + Ui.size(info.part1Sectors * 512)
                        + "\nPartición 2 «VTOYEFI»: " + Ui.size(info.part2Sectors * 512), 12.5f, Ui.SUB, false);
                parts.setPadding(0, dp(10), 0, 0);
                c.addView(parts);
            }
        }
        if (checking) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setIndeterminate(true);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
            c.addView(pb, margins(0, 12, 0, 0));
        }
        deviceCard.addView(c);
    }

    private LinearLayout.LayoutParams chipMargin() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(8), 0, 0, 0);
        return p;
    }

    private TextView chip(String s, int color) {
        TextView t = Ui.text(this, s, 12.5f, color, true);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(Ui.round((color & 0x00FFFFFF) | 0x26000000, dp(20)));
        return t;
    }

    private void renderActions() {
        actions.removeAllViews();
        if (busy || device == null || info == null) {
            if (!busy && device == null) {
                TextView how = Ui.text(this, "1.  Conecta la USB al celular.\n2.  Toca «Permitir».\n3.  Instala Ventoy.\n4.  Copia tus ISOs y arranca la PC desde la USB.",
                        13.5f, Ui.SUB, false);
                how.setLineSpacing(dp(4), 1f);
                how.setPadding(dp(6), dp(4), 0, 0);
                actions.addView(how);
            }
            return;
        }
        // formato de la partición «Ventoy» (para instalar o reinstalar)
        TextView fl = Ui.text(this, "FORMATO DE LA PARTICIÓN «VENTOY» AL INSTALAR", 11.5f, Ui.ACCENT2, true);
        fl.setLetterSpacing(0.06f);
        fl.setPadding(dp(4), dp(4), 0, dp(8));
        actions.addView(fl);
        LinearLayout fsRow = Ui.row(this);
        com.forja.formateador.FsType[] opts = {com.forja.formateador.FsType.EXFAT, com.forja.formateador.FsType.NTFS,
                com.forja.formateador.FsType.FAT32, com.forja.formateador.FsType.EXT4};
        long part1 = (info.diskSectors - VentoyInstaller.PART2_SECTORS - 2048) * 512;
        for (com.forja.formateador.FsType t : opts) {
            boolean ok = t.problem(part1, 512) == null;
            boolean on = t == partFs;
            TextView c = Ui.text(this, t.title, 14, on ? 0xFF07090F : ok ? Ui.TEXT : 0xFF4E566E, true);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(6), dp(10), dp(6), dp(10));
            c.setBackground(on ? Ui.gradient(dp(16), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
            if (ok) c.setOnClickListener(v -> {
                partFs = t;
                render();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            if (t != opts[0]) cp.setMargins(dp(8), 0, 0, 0);
            fsRow.addView(c, cp);
        }
        actions.addView(fsRow);
        String why = switch (partFs) {
            case EXFAT -> "Recomendado. Archivos de cualquier tamaño. Las PCs con Windows XP sin actualizar no lo leen.";
            case NTFS -> "Lo lee cualquier Windows desde XP, y admite ISOs de más de 4 GB.";
            case FAT32 -> "Lo lee cualquier PC, por vieja que sea. Ojo: no admite archivos de más de 4 GB (muchas ISOs de Windows 10/11 pesan más).";
            case EXT4 -> "Para Linux. Windows no lo lee sin programas extra.";
        };
        TextView wt = Ui.text(this, why, 12.5f, partFs == com.forja.formateador.FsType.FAT32 ? Ui.WARN : Ui.SUB, false);
        wt.setPadding(dp(4), dp(8), 0, dp(14));
        actions.addView(wt);

        if (info.hasVentoy && !info.gpt) {
            actions.addView(bigButton("Actualizar Ventoy a " + files.version(), "No borra tus ISOs", true, this::confirmUpdate));
            actions.addView(bigButton("Reinstalar desde cero en " + partFs.title, "Borra todo lo que hay en la USB", false, this::confirmInstall), margins(0, 10, 0, 0));
        } else {
            actions.addView(bigButton("Instalar Ventoy " + files.version(), "Partición «Ventoy» en " + partFs.title + " · borra todo lo que hay en la USB", true, this::confirmInstall));
        }
    }

    private View bigButton(String title, String sub, boolean primary, Runnable r) {
        LinearLayout b = Ui.column(this);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(15), dp(16), dp(15));
        b.setBackground(primary
                ? Ui.ripple(Ui.gradient(dp(20), Ui.ACCENT2, Ui.ACCENT, Ui.VIOLET), Ui.round(Color.WHITE, dp(20)))
                : Ui.ripple(Ui.roundStroke(0x00000000, dp(20), dp(1), 0xFF6B2232), Ui.round(Color.WHITE, dp(20))));
        TextView t = Ui.text(this, title, 16, primary ? 0xFF07090F : 0xFFFF8A96, true);
        t.setGravity(Gravity.CENTER);
        b.addView(t);
        TextView s = Ui.text(this, sub, 12, primary ? 0xCC07090F : 0xFF8B6670, false);
        s.setGravity(Gravity.CENTER);
        b.addView(s);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void showAbout() {
        new AlertDialog.Builder(this, R.style.Theme_Ventoy_Dialog)
                .setTitle("Ventoy no oficial")
                .setMessage("Instala Ventoy " + files.version() + " en una memoria USB desde el celular, sin root, sin anuncios y sin internet.\n\n"
                        + "Hace lo mismo que Ventoy2Disk en la PC (estilo MBR, arranque seguro activado): partición «Ventoy» en exFAT para tus ISOs y partición «VTOYEFI» de 32 MB con los archivos oficiales de arranque, sin modificar.\n\n"
                        + "Ventoy es obra de longpanda y se distribuye con licencia GPLv3. Código y créditos: github.com/ventoy/Ventoy · ventoy.net\n\n"
                        + "Esta app no es oficial ni está hecha por los autores de Ventoy.\n\n"
                        + "Adaptación para Android: " + Studio.NAME + "\n" + Studio.AUTHORS)
                .setView(Legal.buttons(this))
                .setNeutralButton(Studio.NAME, (dd, w) -> Studio.show(this))
                .setPositiveButton("Cerrar", null)
                .show();
    }

    /** ¿a es una versión más nueva que b? («1.1.10» > «1.1.9») */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ archivos de Ventoy

    /** boot.img, core.img y ventoy.disk.img oficiales, guardados en la app (assets/ventoy). */
    static final class AssetFiles implements VentoyInstaller.Files {
        private final AssetManager am;
        private String version;

        AssetFiles(AssetManager am) {
            this.am = am;
        }

        @Override
        public byte[] bootImg() throws IOException {
            try (InputStream in = am.open("ventoy/boot.img")) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int r;
                while ((r = in.read(b)) > 0) o.write(b, 0, r);
                return o.toByteArray();
            }
        }

        @Override
        public InputStream coreImg() throws IOException {
            return am.open("ventoy/core.img");
        }

        @Override
        public InputStream diskImg() throws IOException {
            return am.open("ventoy/ventoy.disk.img");
        }

        @Override
        public String version() {
            if (version == null) {
                try (InputStream in = am.open("ventoy/version")) {
                    byte[] b = new byte[64];
                    int n = in.read(b);
                    version = new String(b, 0, Math.max(0, n)).trim();
                } catch (IOException e) {
                    version = "?";
                }
            }
            return version;
        }
    }
}
