package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Busca versiones nuevas de la app en GitHub. Si hay una, muestra un cartel con «Ahora no»,
 * «Más tarde» y «Actualizar»; al actualizar descarga el APK nuevo y se lo pasa al instalador
 * de Android (que pide confirmar). La versión de la app es el número de compilación, igual al
 * número de la versión publicada (archivos-N).
 */
final class Updater {
    private static final String RELEASES = "https://api.github.com/repos/santiagogd2513-cpu/Minecraft/releases?per_page=60";
    private static final String PREFIX = "archivos-";
    private static final String ASSET = "Boveda.apk";
    private static final String APP = "Bóveda";
    private static final int THEME = R.style.Theme_Boveda_Dialog;
    private static final String ACTION_STATUS = "com.boveda.archivos.UPDATE_STATUS";
    private static final long CHECK_EVERY = 60 * 60 * 1000L;      // como mucho una vez por hora
    private static final long LATER = 24 * 60 * 60 * 1000L;        // «Más tarde»: mañana

    private static boolean checking, installing, pendingPermission;
    private static Release pending;

    private record Release(int number, String url, long size, String title) {}

    private Updater() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("actualizaciones", Context.MODE_PRIVATE);
    }

    private static int currentVersion(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Llamar al abrir la app. Revisa en segundo plano y, si hay versión nueva, muestra el cartel. */
    static void check(Activity a) {
        SharedPreferences p = prefs(a);
        long now = System.currentTimeMillis();
        if (checking || installing || now < p.getLong("later_until", 0) || now - p.getLong("last_check", 0) < CHECK_EVERY) return;
        checking = true;
        int current = currentVersion(a);
        if (current <= 1) {      // compilación local, no de GitHub
            checking = false;
            return;
        }
        new Thread(() -> {
            try {
                Release r = latest();
                p.edit().putLong("last_check", System.currentTimeMillis()).apply();
                if (r != null && r.number > current && r.number != p.getInt("skip", 0)) {
                    new Handler(Looper.getMainLooper()).post(() -> offer(a, r, current));
                }
            } catch (Exception ignored) {
                // sin internet o GitHub no respondió: se intenta la próxima vez
            } finally {
                checking = false;
            }
        }, "buscar-actualizacion").start();
    }

    /** Llamar en onResume: si se fue a dar permiso de instalar, sigue con la actualización. */
    static void resume(Activity a) {
        if (pendingPermission && pending != null && a.getPackageManager().canRequestPackageInstalls()) {
            pendingPermission = false;
            download(a, pending);
        }
    }

    private static Release latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(RELEASES).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", APP);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            JSONArray arr = new JSONArray(b.toString("UTF-8"));
            Release best = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                String tag = r.optString("tag_name");
                if (!tag.startsWith(PREFIX) || r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                int num;
                try {
                    num = Integer.parseInt(tag.substring(PREFIX.length()));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (best != null && num <= best.number) continue;
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int k = 0; k < assets.length(); k++) {
                    JSONObject as = assets.getJSONObject(k);
                    if (ASSET.equals(as.optString("name"))) {
                        best = new Release(num, as.optString("browser_download_url"), as.optLong("size"), r.optString("name"));
                    }
                }
            }
            return best;
        } finally {
            c.disconnect();
        }
    }

    private static void offer(Activity a, Release r, int current) {
        if (a.isFinishing() || a.isDestroyed()) return;
        new AlertDialog.Builder(a, THEME)
                .setTitle("¡Hay una versión nueva!")
                .setMessage(APP + " #" + r.number + " ya está disponible (tienes la #" + current + ").\n\n"
                        + "Se descarga desde aquí mismo (" + size(r.size) + ") y Android te pedirá confirmar la instalación. "
                        + "No se borra nada de lo tuyo.\n\n"
                        + "«Ahora no» no vuelve a avisar de esta versión; «Más tarde» te recuerda mañana.")
                .setPositiveButton("Actualizar", (d, w) -> start(a, r))
                .setNeutralButton("Más tarde", (d, w) ->
                        prefs(a).edit().putLong("later_until", System.currentTimeMillis() + LATER).apply())
                .setNegativeButton("Ahora no", (d, w) -> prefs(a).edit().putInt("skip", r.number).apply())
                .show();
    }

    private static void start(Activity a, Release r) {
        pending = r;
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            // la primera vez Android pide permitir que la app instale actualizaciones
            pendingPermission = true;
            new AlertDialog.Builder(a, THEME)
                    .setTitle("Un permiso, una sola vez")
                    .setMessage("Para que " + APP + " pueda instalar su propia actualización, activa «Permitir de esta fuente» "
                            + "en la pantalla que se abre y vuelve con el botón atrás.")
                    .setPositiveButton("Abrir", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            pendingPermission = false;
                            Toast.makeText(a, "No se pudo abrir el permiso", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancelar", (d, w) -> pendingPermission = false)
                    .show();
            return;
        }
        download(a, r);
    }

    private static void download(Activity a, Release r) {
        if (installing) return;
        installing = true;
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        box.addView(bar);
        TextView info = new TextView(a);
        info.setPadding(0, pad / 2, 0, 0);
        info.setText("Conectando…");
        box.addView(info);
        AlertDialog dlg = new AlertDialog.Builder(a, THEME)
                .setTitle("Descargando " + APP + " #" + r.number)
                .setView(box)
                .setCancelable(false)
                .show();
        Handler main = new Handler(Looper.getMainLooper());
        Context app = a.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.getPackageName());
                int id = pi.createSession(params);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection c = (HttpURLConnection) new URL(r.url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", APP);
                    long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : r.size;
                    try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("app.apk", 0, total > 0 ? total : -1)) {
                        byte[] buf = new byte[65536];
                        long done = 0, lastUi = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                            long now = System.currentTimeMillis();
                            if (now - lastUi > 150) {
                                lastUi = now;
                                long d = done;
                                main.post(() -> {
                                    if (total > 0) bar.setProgress((int) (d * 1000 / total));
                                    info.setText(size(d) + (total > 0 ? " de " + size(total) : ""));
                                });
                            }
                        }
                        s.fsync(out);
                    } finally {
                        c.disconnect();
                    }
                    main.post(() -> info.setText("Abriendo el instalador…"));
                    registerStatus(app);
                    Intent cb = new Intent(ACTION_STATUS).setPackage(app.getPackageName());
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getBroadcast(app, id, cb, flags).getIntentSender());
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String err = error;
            main.post(() -> {
                try {
                    dlg.dismiss();
                } catch (Exception ignored) {
                }
                if (err != null) {
                    installing = false;
                    if (!a.isFinishing()) new AlertDialog.Builder(a, THEME)
                            .setTitle("No se pudo actualizar")
                            .setMessage(err + "\n\nRevisa tu internet e inténtalo otra vez.")
                            .setPositiveButton("Entendido", null).show();
                }
            });
        }, "descargar-actualizacion").start();
    }

    private static boolean registered;

    /** Lo que responde el instalador: pedir confirmación, listo o error. */
    private static void registerStatus(Context app) {
        if (registered) return;
        registered = true;
        BroadcastReceiver rcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                            : i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(confirm);
                    }
                    return;
                }
                installing = false;
                if (status != PackageInstaller.STATUS_SUCCESS) {
                    String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    Toast.makeText(c, "No se instaló la actualización" + (msg == null ? "" : ": " + msg), Toast.LENGTH_LONG).show();
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(rcv, f);
    }

    private static String size(long b) {
        if (b <= 0) return "?";
        if (b < 1024 * 1024) return String.format(Locale.US, "%d KB", b / 1024);
        return String.format(Locale.US, "%.1f MB", b / 1048576.0).replace('.', ',');
    }
}
