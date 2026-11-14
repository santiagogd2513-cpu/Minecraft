package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Celular a celular, por cable. Un celular comparte (con las carpetas que él elige) y el otro mira, baja y sube archivos.
 * Usa el modo accesorio USB de Android: los dos celulares tienen que tener Bóveda abierta en esta pantalla.
 */
public class LinkActivity extends Activity {
    private static final int REQ_UPLOAD = 61;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private LinearLayout host;
    private UsbManager usb;
    private boolean sharing;
    private volatile LinkWire.Pipe pipe;
    private volatile LinkServer server;
    private volatile LinkClient client;
    private final AtomicBoolean waiting = new AtomicBoolean();
    private UsbAccessory pendingAccessory;
    private String deviceName;

    // lo que se elige compartir
    private static final String[][] FOLDERS = {
            {"Descargas", "Download"}, {"Cámara", "DCIM"}, {"Imágenes", "Pictures"}, {"Documentos", "Documents"},
            {"Música", "Music"}, {"Videos", "Movies"}};
    private final List<CheckBox> folderChecks = new ArrayList<>();
    private CheckBox receiveCheck, appsCheck;

    // navegación al mirar
    private String cur = "";
    private final List<LinkVfs.Item> items = new ArrayList<>();
    private ListView list;
    private TextView pathText;
    private String uploadDir;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        usb = getSystemService(UsbManager.class);
        deviceName = android.os.Build.MODEL == null ? "Celular" : android.os.Build.MODEL;
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        host = Ui.column(this);
        host.setBackgroundColor(Ui.BG);
        host.setFitsSystemWindows(true);
        setContentView(host);
        takeAccessory(getIntent());
        roleScreen();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        takeAccessory(i);
    }

    private void takeAccessory(Intent i) {
        if (i != null && UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(i.getAction())) {
            UsbAccessory a = i.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (a != null) pendingAccessory = a;
        }
    }

    // ------------------------------------------------------------------ pantallas

    private LinearLayout screen(String title, boolean back) {
        host.removeAllViews();
        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(10), dp(12), dp(6));
        if (back) head.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> onBackPressed()));
        TextView t = Ui.text(this, title, 20, Ui.TEXT, true);
        t.setPadding(dp(back ? 6 : 14), 0, 0, 0);
        head.addView(t, Ui.weight(1));
        host.addView(head);
        ScrollView sc = new ScrollView(this);
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(16), dp(6), dp(16), dp(30));
        sc.addView(box);
        host.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        return box;
    }

    private TextView button(String s, int color, View.OnClickListener l) {
        TextView b = Ui.text(this, s, 15.5f, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(16), dp(14), dp(16));
        b.setBackground(Ui.ripple(Ui.round(color, dp(16)), Ui.round(0x33FFFFFF, dp(16))));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void roleScreen() {
        stopAll();
        LinearLayout box = screen("📱↔📱 " + L.t("Celular a celular"), false);
        if (!getPackageManager().hasSystemFeature("android.hardware.usb.host") && !getPackageManager().hasSystemFeature("android.hardware.usb.accessory")) {
            box.addView(Ui.text(this, L.t("Este celular no deja usar el cable USB para esto."), 15, Ui.BAD, false));
            return;
        }
        TextView intro = Ui.text(this, L.t("Conecta los dos celulares con un cable USB (si hace falta, con un adaptador OTG en uno). Abre esta misma pantalla en los dos y elige qué hace cada uno."), 14, Ui.SUB, false);
        intro.setPadding(0, 0, 0, dp(10));
        box.addView(intro);
        box.addView(button("📤 " + L.t("Este celular comparte"), 0xFF1E88E5, v -> shareScreen()));
        box.addView(Ui.text(this, L.t("El otro podrá ver solo las carpetas que tú elijas."), 12.5f, Ui.SUB, false));
        box.addView(button("📥 " + L.t("Este celular mira al otro"), 0xFF00897B, v -> wait(false)));
        box.addView(Ui.text(this, L.t("Podrás bajar sus archivos y apps, y enviarle los tuyos."), 12.5f, Ui.SUB, false));
        TextView note = Ui.text(this, L.t("Lo que se puede pasar: archivos, fotos, música y los instaladores (APK) de las apps. Las cuentas, las contraseñas y los datos de las apps no: Android no deja a ninguna app copiarlos."), 12.5f, Ui.SUB, false);
        note.setPadding(0, dp(18), 0, 0);
        box.addView(note);
    }

    private void shareScreen() {
        LinearLayout box = screen(L.t("¿Qué compartes?"), true);
        folderChecks.clear();
        box.addView(Ui.text(this, L.t("Marca lo que el otro celular podrá ver. Lo demás no existe para él."), 13.5f, Ui.SUB, false));
        for (String[] f : FOLDERS) {
            CheckBox c = new CheckBox(this);
            c.setText(L.t(f[0]));
            c.setTextColor(Ui.TEXT);
            c.setTextSize(16);
            c.setPadding(dp(4), dp(8), 0, dp(8));
            folderChecks.add(c);
            box.addView(c);
        }
        appsCheck = new CheckBox(this);
        appsCheck.setText(L.t("Aplicaciones instaladas (los instaladores APK)"));
        appsCheck.setTextColor(Ui.TEXT);
        appsCheck.setTextSize(16);
        appsCheck.setPadding(dp(4), dp(8), 0, dp(8));
        box.addView(appsCheck);
        receiveCheck = new CheckBox(this);
        receiveCheck.setText(L.t("Dejar que me envíen archivos (llegan a Descargas/Desde otro celular)"));
        receiveCheck.setTextColor(Ui.TEXT);
        receiveCheck.setTextSize(16);
        receiveCheck.setPadding(dp(4), dp(8), 0, dp(8));
        box.addView(receiveCheck);
        box.addView(button("🔌 " + L.t("Esperar el cable"), 0xFF1E88E5, v -> {
            boolean any = appsCheck.isChecked() || receiveCheck.isChecked();
            for (CheckBox c : folderChecks) any |= c.isChecked();
            if (!any) {
                Toast.makeText(this, L.t("Marca al menos una carpeta"), Toast.LENGTH_SHORT).show();
                return;
            }
            wait(true);
        }));
    }

    private LinkVfs buildVfs() {
        LinkVfs vfs = new LinkVfs();
        for (int i = 0; i < FOLDERS.length; i++) {
            if (!folderChecks.get(i).isChecked()) continue;
            File dir = new File(Paths.ROOT, FOLDERS[i][1]);
            if (dir.isDirectory()) vfs.add(new LinkVfs.FolderSource(FOLDERS[i][0], dir, false));
        }
        if (receiveCheck.isChecked()) {
            File in = new File(Paths.ROOT, "Download/Desde otro celular");
            //noinspection ResultOfMethodCallIgnored
            in.mkdirs();
            vfs.add(new LinkVfs.FolderSource("Recibidos", in, true));
        }
        if (appsCheck.isChecked()) vfs.add(new LinkApps(this));
        return vfs;
    }

    private String summary(LinkVfs vfs) {
        StringBuilder s = new StringBuilder();
        for (LinkVfs.Source x : vfs.sources()) s.append("• ").append(x.name()).append(x.writable() ? L.t(" (puede enviarte archivos)") : "").append('\n');
        return s.toString().trim();
    }

    // ------------------------------------------------------------------ esperar el cable

    private LinkVfs sharedVfs;

    private void wait(boolean share) {
        sharing = share;
        if (share) sharedVfs = buildVfs();
        LinearLayout box = screen(share ? L.t("Esperando el cable…") : L.t("Esperando el cable…"), true);
        TextView t = Ui.text(this, "🔌\n\n" + L.t("Conecta el cable entre los dos celulares.\nEl otro celular también debe tener abierta esta pantalla.\n\nSi Android pregunta, acepta que Bóveda use el cable USB."), 15, Ui.SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(50), 0, 0);
        box.addView(t);
        status = Ui.text(this, "", 13, Ui.SUB, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(20), 0, 0);
        box.addView(status);
        waiting.set(true);
        io.execute(this::findCable);
    }

    private TextView status;

    private void say(String s) {
        main.post(() -> {
            if (status != null) status.setText(s);
        });
    }

    /** Mira el cable cada segundo: si llega el otro celular como «accesorio», o si Android nos dio el rol de anfitrión. */
    private void findCable() {
        long switched = 0;
        String asked = "";
        while (waiting.get()) {
            try {
                // 1) este celular es el «accesorio»: Android ya nos entregó al anfitrión
                UsbAccessory acc = pendingAccessory;
                if (acc == null) {
                    UsbAccessory[] list = usb.getAccessoryList();
                    if (list != null && list.length > 0) acc = list[0];
                }
                if (acc != null) {
                    if (!usb.hasPermission(acc)) {
                        if (!asked.equals("acc")) {
                            asked = "acc";
                            usb.requestPermission(acc, LinkUsb.permissionIntent(this));
                            say(L.t("Acepta el permiso del cable en la pantalla"));
                        }
                    } else {
                        LinkWire.Pipe p = new LinkUsb.AccessoryPipe(usb, acc);
                        waiting.set(false);
                        connected(p);
                        return;
                    }
                }
                // 2) este celular es el «anfitrión»: hay que pedirle al otro que pase a modo accesorio
                for (UsbDevice d : usb.getDeviceList().values()) {
                    if (LinkUsb.isAccessoryMode(d)) {
                        if (!usb.hasPermission(d)) {
                            if (!asked.equals("dev" + d.getDeviceName())) {
                                asked = "dev" + d.getDeviceName();
                                usb.requestPermission(d, LinkUsb.permissionIntent(this));
                                say(L.t("Acepta el permiso del cable en la pantalla"));
                            }
                            continue;
                        }
                        LinkWire.Pipe p = LinkUsb.HostPipe.open(usb, d);
                        waiting.set(false);
                        connected(p);
                        return;
                    }
                    if (d.getDeviceClass() == 9) continue;   // un hub
                    if (System.currentTimeMillis() - switched < 5000) continue;
                    if (!usb.hasPermission(d)) {
                        if (!asked.equals("sw" + d.getDeviceName())) {
                            asked = "sw" + d.getDeviceName();
                            usb.requestPermission(d, LinkUsb.permissionIntent(this));
                            say(L.t("Acepta el permiso del cable en la pantalla"));
                        }
                        continue;
                    }
                    switched = System.currentTimeMillis();
                    say(L.t("Hablando con el otro celular…"));
                    if (!LinkUsb.switchToAccessory(usb, d)) say(L.t("Hay algo conectado, pero no es un celular compatible (o no está desbloqueado)."));
                }
            } catch (IOException | RuntimeException e) {
                say(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ conectados

    private void connected(LinkWire.Pipe p) {
        pipe = p;
        io.execute(() -> {
            try {
                // los dos dicen qué hace cada uno; si eligieron lo mismo, no se puede
                OutputStreamHolder.write(p, sharing ? 'S' : 'V');
                int other = OutputStreamHolder.read(p);
                if (other < 0) throw new IOException(L.t("el otro celular cerró la conexión"));
                if ((other == 'S') == sharing) throw new IOException(sharing ? L.t("Los dos celulares eligieron compartir: uno tiene que mirar.") : L.t("Los dos celulares eligieron mirar: uno tiene que compartir."));
                if (sharing) serve(p);
                else browse(p);
            } catch (IOException e) {
                fail(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    /** Intercambio de un byte antes de empezar, para que cada lado sepa qué hace el otro. */
    private static final class OutputStreamHolder {
        static void write(LinkWire.Pipe p, int b) throws IOException {
            OutputStream o = p.out();
            o.write(new byte[]{(byte) b});
            o.flush();
        }

        static int read(LinkWire.Pipe p) throws IOException {
            byte[] one = new byte[1];
            int n = p.in().read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }
    }

    private void fail(String msg) {
        main.post(() -> {
            stopAll();
            new AlertDialog.Builder(this).setTitle(L.t("No se pudo seguir")).setMessage(msg)
                    .setPositiveButton("OK", (d, w) -> roleScreen()).setOnCancelListener(d -> roleScreen()).show();
        });
    }

    private void serve(LinkWire.Pipe p) {
        final LinkVfs vfs = sharedVfs;
        main.post(() -> {
            LinearLayout box = screen(L.t("Compartiendo"), false);
            TextView t = Ui.text(this, "📤\n\n" + L.t("Conectado con el otro celular.\nEstás compartiendo:") + "\n\n" + summary(vfs), 15, Ui.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(30), 0, 0);
            box.addView(t);
            status = Ui.text(this, "", 13, Ui.SUB, false);
            status.setGravity(Gravity.CENTER);
            status.setPadding(0, dp(20), 0, 0);
            box.addView(status);
            box.addView(button("✋ " + L.t("Desconectar"), 0xFFC62828, v -> roleScreen()));
        });
        LinkServer s = new LinkServer(p, vfs, deviceName, new LinkServer.Listener() {
            @Override
            public boolean approve(String name) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicBoolean ok = new AtomicBoolean();
                main.post(() -> new AlertDialog.Builder(LinkActivity.this).setTitle(L.t("¿Dejar entrar a «") + name + "»?")
                        .setMessage(L.t("Podrá ver:\n") + summary(vfs))
                        .setPositiveButton(L.t("Sí, dejar"), (d, w) -> {
                            ok.set(true);
                            latch.countDown();
                        }).setNegativeButton(L.t("No"), (d, w) -> latch.countDown())
                        .setOnCancelListener(d -> latch.countDown()).show());
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    return false;
                }
                return ok.get();
            }

            @Override
            public void onActivity(String what) {
                say(what);
            }

            @Override
            public void onClosed(String why) {
                main.post(() -> {
                    if (server != null) {
                        Toast.makeText(LinkActivity.this, why, Toast.LENGTH_LONG).show();
                        roleScreen();
                    }
                });
            }
        });
        server = s;
        s.run();
    }

    // ------------------------------------------------------------------ mirar al otro

    private void browse(LinkWire.Pipe p) throws IOException {
        LinkClient c = new LinkClient(p, deviceName);
        client = c;
        cur = "";
        main.post(() -> {
            host.removeAllViews();
            LinearLayout head = Ui.row(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(8), dp(10), dp(12), dp(6));
            head.addView(Ui.iconButton(this, R.drawable.ic_back, 44, v -> onBackPressed()));
            LinearLayout col = Ui.column(this);
            col.setPadding(dp(6), 0, 0, 0);
            col.addView(Ui.text(this, "📱 " + c.peerName, 18, Ui.TEXT, true));
            pathText = Ui.text(this, "", 12.5f, Ui.SUB, false);
            col.addView(pathText);
            head.addView(col, Ui.weight(1));
            host.addView(head);
            LinearLayout actions = Ui.row(this);
            actions.setPadding(dp(14), 0, dp(14), dp(6));
            TextView down = button("⬇ " + L.t("Bajar esta carpeta"), 0xFF00897B, v -> downloadFolder(cur));
            TextView up = button("⬆ " + L.t("Subir un archivo"), 0xFF1E88E5, v -> pickUpload());
            for (TextView b : new TextView[]{down, up}) {
                b.setTextSize(13.5f);
                b.setPadding(dp(8), dp(11), dp(8), dp(11));
                LinearLayout.LayoutParams lp = Ui.weight(1);
                lp.setMargins(dp(3), 0, dp(3), 0);
                actions.addView(b, lp);
            }
            host.addView(actions);
            list = new ListView(this);
            list.setDivider(null);
            list.setAdapter(new BaseAdapter() {
                public int getCount() {
                    return items.size();
                }

                public Object getItem(int i) {
                    return items.get(i);
                }

                public long getItemId(int i) {
                    return i;
                }

                public View getView(int i, View v, ViewGroup parent) {
                    LinkVfs.Item it = items.get(i);
                    LinearLayout r = Ui.column(LinkActivity.this);
                    r.setPadding(dp(18), dp(11), dp(18), dp(11));
                    r.addView(Ui.text(LinkActivity.this, (it.dir ? "📁 " : "📄 ") + it.name, 15.5f, Ui.TEXT, false));
                    if (!it.dir) r.addView(Ui.text(LinkActivity.this, Ui.size(it.size) + (it.modified > 0 ? "  ·  " + Ui.date(it.modified) : ""), 12, Ui.SUB, false));
                    return r;
                }
            });
            list.setOnItemClickListener((a, v, i, id) -> {
                LinkVfs.Item it = items.get(i);
                String path = cur.isEmpty() ? it.name : cur + "/" + it.name;
                if (it.dir) go(path);
                else downloadFile(path, it);
            });
            host.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            go("");
        });
    }

    private void go(String path) {
        io.execute(() -> {
            try {
                List<LinkVfs.Item> l = client.list(path);
                Collections.sort(l, (a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
                main.post(() -> {
                    cur = path;
                    items.clear();
                    items.addAll(l);
                    pathText.setText(path.isEmpty() ? L.t("Carpetas compartidas") : path);
                    ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                    list.setSelection(0);
                });
            } catch (IOException e) {
                main.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private File destRoot() {
        File d = new File(Paths.ROOT, "Download/Desde " + (client == null ? "otro celular" : client.peerName.replaceAll("[\\\\/:*?\"<>|]", "_")));
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** Un cuadro con el avance y un botón para cancelar. */
    private final class Job {
        final AtomicBoolean cancel = new AtomicBoolean();
        final TextView text;
        final AlertDialog dlg;
        long bytes;
        int files;
        long lastUi;

        Job(String title) {
            text = Ui.text(LinkActivity.this, "…", 14, Ui.TEXT, false);
            text.setPadding(dp(24), dp(16), dp(24), dp(8));
            dlg = new AlertDialog.Builder(LinkActivity.this).setTitle(title).setView(text).setCancelable(false)
                    .setNegativeButton(L.t("Cancelar"), (d, w) -> cancel.set(true)).create();
            dlg.show();
        }

        void update(String what) {
            long now = System.currentTimeMillis();
            if (now - lastUi < 250) return;
            lastUi = now;
            final String s = what + "\n" + files + L.t(" archivos · ") + Ui.size(bytes);
            main.post(() -> text.setText(s));
        }

        void done(String msg) {
            main.post(() -> {
                dlg.dismiss();
                Toast.makeText(LinkActivity.this, msg, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void downloadFile(String path, LinkVfs.Item it) {
        Job job = new Job(it.name);
        io.execute(() -> {
            try {
                File out = fetch(path, it, destRoot(), job);
                job.done(L.t("Guardado en: ") + out.getParent().replace(Paths.ROOT, L.t("Interno")));
            } catch (IOException e) {
                job.done(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private File fetch(String path, LinkVfs.Item it, File destDir, Job job) throws IOException {
        File dest = uniqueFile(destDir, it.name);
        File tmp = new File(destDir, "." + dest.getName() + ".bajando");
        try (OutputStream o = new FileOutputStream(tmp)) {
            long base = job.bytes;
            client.read(path, 0, -1, o, (done, total) -> {
                job.bytes = base + done;
                job.update(it.name);
                return !job.cancel.get();
            }, it.size);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (!tmp.renameTo(dest)) throw new IOException(L.t("No se pudo guardar el archivo"));
        if (it.modified > 0) //noinspection ResultOfMethodCallIgnored
            dest.setLastModified(it.modified);
        job.files++;
        UndoLog.recordCreated(this, dest.getPath(), false);
        return dest;
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        int dot = name.lastIndexOf('.');
        for (int i = 1; f.exists(); i++) f = new File(dir, (dot > 0 ? name.substring(0, dot) : name) + " (" + i + ")" + (dot > 0 ? name.substring(dot) : ""));
        return f;
    }

    private void downloadFolder(String path) {
        if (path.isEmpty()) {
            Toast.makeText(this, L.t("Entra primero a una carpeta"), Toast.LENGTH_SHORT).show();
            return;
        }
        Job job = new Job(L.t("Bajando carpeta"));
        io.execute(() -> {
            try {
                File root = destRoot();
                String name = path.substring(path.lastIndexOf('/') + 1);
                walk(path, new File(root, name), job);
                job.done(L.t("Listo: ") + job.files + L.t(" archivos en ") + root.getPath().replace(Paths.ROOT, L.t("Interno")));
            } catch (IOException e) {
                job.done(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private void walk(String remote, File local, Job job) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        local.mkdirs();
        for (LinkVfs.Item it : client.list(remote)) {
            if (job.cancel.get()) throw new IOException(L.t("Cancelado"));
            String p = remote + "/" + it.name;
            if (it.dir) walk(p, new File(local, it.name), job);
            else fetch(p, it, local, job);
        }
    }

    private void pickUpload() {
        if (cur.isEmpty()) {
            Toast.makeText(this, L.t("Entra primero a una carpeta que acepte archivos (Recibidos)"), Toast.LENGTH_LONG).show();
            return;
        }
        uploadDir = cur;
        Intent i = new Intent(this, PickerActivity.class).setAction(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_UPLOAD);
        } catch (Exception e) {
            Toast.makeText(this, L.t("No se pudo abrir el selector de archivos"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_UPLOAD || res != RESULT_OK || data == null) return;
        List<android.net.Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        final String dir = uploadDir;
        Job job = new Job(L.t("Enviando"));
        io.execute(() -> {
            try {
                for (android.net.Uri u : uris) {
                    String name = "archivo";
                    long size = -1;
                    try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME), s = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                            if (n >= 0) name = c.getString(n);
                            if (s >= 0) size = c.getLong(s);
                        }
                    }
                    try (InputStream in = getContentResolver().openInputStream(u)) {
                        if (in == null) throw new IOException(L.t("No se pudo abrir ") + name);
                        final String nm = name;
                        long base = job.bytes;
                        client.put(dir + "/" + name, in, size, 0, (done, total) -> {
                            job.bytes = base + done;
                            job.update(nm);
                            return !job.cancel.get();
                        });
                        job.files++;
                    }
                }
                job.done(L.t("Enviados: ") + job.files + L.t(" archivos"));
            } catch (IOException e) {
                job.done(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ salir

    private void stopAll() {
        waiting.set(false);
        LinkServer s = server;
        server = null;
        if (s != null) s.stop();
        LinkClient c = client;
        client = null;
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
        LinkWire.Pipe p = pipe;
        pipe = null;
        if (p != null) {
            try {
                p.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (client != null && !cur.isEmpty()) {
            int i = cur.lastIndexOf('/');
            go(i < 0 ? "" : cur.substring(0, i));
            return;
        }
        if (client != null || server != null || waiting.get()) {
            roleScreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
        io.shutdownNow();
    }
}
