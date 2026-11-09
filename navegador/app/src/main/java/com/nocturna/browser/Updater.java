package com.nocturna.browser;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Busca versiones nuevas de la app en GitHub y, si hay una, muestra un aviso a pantalla completa
 * con las novedades, «Actualizar ahora» y «Más tarde». Al actualizar descarga el APK y
 * se lo pasa al instalador de Android (que pide confirmar).
 * <p>
 * La versión de la app es el número de compilación, igual al de la versión publicada (navegador-N).
 * Para encontrarla primero pregunta a la API de GitHub y, como esa tiene un límite por conexión
 * (con datos móviles mucha gente comparte la misma y se agota), también prueba directamente los
 * enlaces de descarga de las versiones siguientes, que no tienen límite. Las novedades vienen en el
 * archivo novedades.txt de cada versión publicada.
 */
final class Updater {
    private static final String REPO = "santiagogd2513-cpu/Minecraft";
    private static final String RELEASES = "https://api.github.com/repos/" + REPO + "/releases?per_page=100";
    private static final String DOWNLOAD = "https://github.com/" + REPO + "/releases/download/";
    private static final String PREFIX = "navegador-";
    private static final String ASSET = "Nocturna.apk";
    private static final String APP = "Nocturna";
    private static final int THEME = R.style.Theme_Nocturna_Dialog;
    private static final String ACTION_STATUS = "com.nocturna.browser.UPDATE_STATUS";
    private static final int LOOK_AHEAD = 30;                      // versiones que se prueban hacia adelante

    private static boolean checking, installing, pendingPermission;
    // el aviso sale cada vez que se abre la app; si lo cierras, no vuelve hasta que salgas y vuelvas a entrar
    private static boolean showing, closedThisVisit, watching;
    private static int started;
    private static Release pending;

    private record Release(int number, String url, long size, String notes) {}

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

    /** Llamar al abrir o volver a la app. Revisa en segundo plano y, si hay versión nueva, avisa. */
    static void check(Activity a) {
        watch(a);
        if (checking || installing || showing || closedThisVisit) return;
        int current = currentVersion(a);
        if (current <= 1) return;      // compilación local, no de GitHub
        checking = true;
        new Thread(() -> {
            try {
                Release r = latest(current);
                if (r != null && r.number > current) {
                    new Handler(Looper.getMainLooper()).post(() -> offer(a, r, current));
                }
            } catch (Exception ignored) {
                // sin internet: se vuelve a intentar la próxima vez que se abra la app
            } finally {
                checking = false;
            }
        }, "buscar-actualizacion").start();
    }

    /** Sabe cuándo la app pasa a segundo plano (todas sus pantallas ocultas) para volver a avisar al abrirla. */
    private static void watch(Activity a) {
        if (watching) return;
        watching = true;
        started = 1;   // la pantalla que llama ya está visible
        a.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity x) { started++; }
            @Override public void onActivityStopped(Activity x) {
                if (--started <= 0) {
                    started = 0;
                    closedThisVisit = false;
                }
            }
            @Override public void onActivityCreated(Activity x, Bundle b) {}
            @Override public void onActivityResumed(Activity x) {}
            @Override public void onActivityPaused(Activity x) {}
            @Override public void onActivitySaveInstanceState(Activity x, Bundle b) {}
            @Override public void onActivityDestroyed(Activity x) {}
        });
    }

    /** Llamar en onResume: si se fue a dar permiso de instalar, sigue con la actualización. */
    static void resume(Activity a) {
        if (pendingPermission && pending != null && a.getPackageManager().canRequestPackageInstalls()) {
            pendingPermission = false;
            download(a, pending);
        }
    }

    // ------------------------------------------------------------------ buscar

    private static Release latest(int current) throws Exception {
        int best = -1;
        long size = -1;
        boolean apiOk = false;
        try {
            JSONArray arr = new JSONArray(text(RELEASES, true));
            apiOk = true;
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
                if (num <= best) continue;
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int k = 0; k < assets.length(); k++) {
                    JSONObject as = assets.getJSONObject(k);
                    if (ASSET.equals(as.optString("name"))) {
                        best = num;
                        size = as.optLong("size");
                    }
                }
            }
        } catch (Exception apiFailed) {
            // límite de la API agotado u otro problema: se prueba directo
        }
        // prueba directa de los enlaces de descarga (sin límite); si la API respondió, solo un poco más allá
        int from = Math.max(current, best) + 1, to = apiOk ? from + 3 : current + LOOK_AHEAD;
        boolean reached = apiOk;
        for (int k = from; k <= to; k++) {
            int code = head(DOWNLOAD + PREFIX + k + "/" + ASSET);
            if (code < 0) continue;
            reached = true;
            if (code >= 200 && code < 400) {
                best = k;
                size = -1;
            }
        }
        if (!reached) throw new Exception("sin conexión");
        if (best <= current) return null;
        String notes = "";
        try {
            notes = text(DOWNLOAD + PREFIX + best + "/novedades.txt", false);
        } catch (Exception ignored) {
            // las versiones viejas no traen novedades
        }
        return new Release(best, DOWNLOAD + PREFIX + best + "/" + ASSET, size, notes);
    }

    /** Código HTTP de una petición HEAD sin seguir redirecciones (-1 si no hubo conexión). */
    private static int head(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", APP);
            return c.getResponseCode();
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String text(String url, boolean api) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        if (api) c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", APP);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 4_000_000) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------------ aviso a pantalla completa

    /** Una versión de las novedades: número, título y puntos. */
    private record Section(int number, String title, List<String> items) {}

    /** Lee novedades.txt («## 26», un título, y líneas con «- ») y deja las versiones que el usuario no tiene. */
    private static List<Section> sections(String notes, int current) {
        List<Section> out = new ArrayList<>();
        if (notes == null) return out;
        Section cur = null;
        for (String raw : notes.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                int n;
                try {
                    n = Integer.parseInt(line.substring(3).trim().split("\\s+")[0].replace("#", ""));
                } catch (NumberFormatException e) {
                    n = Integer.MAX_VALUE;
                }
                cur = n > current ? new Section(n, null, new ArrayList<>()) : null;
                if (cur != null) out.add(cur);
            } else if (cur != null && !line.isEmpty()) {
                if (line.startsWith("- ") || line.startsWith("• ")) cur.items.add(line.substring(2).trim());
                else if (cur.title == null && cur.items.isEmpty()) out.set(out.size() - 1, cur = new Section(cur.number, line, cur.items));
                else cur.items.add(line);
            }
        }
        return out;
    }

    private static void offer(Activity a, Release r, int current) {
        if (a.isFinishing() || a.isDestroyed() || showing || closedThisVisit || installing) return;
        showing = true;
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.setPadding(px(dp, 22), px(dp, 26), px(dp, 22), px(dp, 18));

        // cabecera
        TextView badge = new TextView(a);
        badge.setText("NUEVA VERSIÓN");
        badge.setTextColor(0xFF06101F);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setLetterSpacing(0.12f);
        badge.setPadding(px(dp, 12), px(dp, 5), px(dp, 12), px(dp, 5));
        badge.setBackground(Ui.gradient(px(dp, 20), Ui.ACCENT, Ui.ACCENT2));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        root.addView(badge, bl);
        TextView title = new TextView(a);
        title.setText(APP + " se actualizó");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, px(dp, 14), 0, px(dp, 4));
        root.addView(title);
        TextView sub = new TextView(a);
        sub.setText("Versión #" + r.number + "  ·  tienes la #" + current + (r.size > 0 ? "  ·  " + size(r.size) : ""));
        sub.setTextColor(Ui.SUB);
        sub.setTextSize(14);
        root.addView(sub);
        TextView h = new TextView(a);
        h.setText("Novedades");
        h.setTextColor(Ui.ACCENT2);
        h.setTextSize(13);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setLetterSpacing(0.08f);
        h.setPadding(0, px(dp, 22), 0, px(dp, 8));
        root.addView(h);

        // novedades
        ScrollView scroll = new ScrollView(a);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        List<Section> secs = sections(r.notes, current);
        if (secs.isEmpty()) secs.add(new Section(r.number, "Mejoras y arreglos", List.of("Esta versión trae mejoras y arreglos para que todo funcione mejor.")));
        for (Section s : secs) {
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(px(dp, 16), px(dp, 14), px(dp, 16), px(dp, 14));
            card.setBackground(Ui.round(Ui.SURFACE2, px(dp, 16)));
            TextView ct = new TextView(a);
            ct.setText((s.number == Integer.MAX_VALUE ? "" : "#" + s.number + "  ") + (s.title == null ? "Novedades" : s.title));
            ct.setTextColor(Ui.TEXT);
            ct.setTextSize(16.5f);
            ct.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(ct);
            for (String it : s.items) {
                LinearLayout row = new LinearLayout(a);
                row.setPadding(0, px(dp, 8), 0, 0);
                TextView dot = new TextView(a);
                dot.setText("●");
                dot.setTextColor(Ui.ACCENT);
                dot.setTextSize(9);
                dot.setPadding(0, px(dp, 4), px(dp, 10), 0);
                row.addView(dot);
                TextView t = new TextView(a);
                t.setText(it);
                t.setTextColor(0xFFC9D2E0);
                t.setTextSize(14.5f);
                t.setLineSpacing(0, 1.15f);
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row);
            }
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
            cl.setMargins(0, 0, 0, px(dp, 12));
            list.addView(card, cl);
        }
        TextView note = new TextView(a);
        note.setText("Se descarga desde aquí mismo y Android te pide confirmar la instalación. No se borra nada de lo tuyo.");
        note.setTextColor(Ui.SUB);
        note.setTextSize(12.5f);
        note.setPadding(px(dp, 4), px(dp, 4), px(dp, 4), px(dp, 8));
        list.addView(note);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // botones
        TextView go = new TextView(a);
        go.setText("Actualizar ahora");
        go.setTextColor(0xFF06101F);
        go.setTextSize(17);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, px(dp, 16), 0, px(dp, 16));
        go.setBackground(Ui.gradient(px(dp, 28), Ui.ACCENT, Ui.ACCENT2));
        go.setOnClickListener(v -> {
            closedThisVisit = true;
            d.dismiss();
            start(a, r);
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.setMargins(0, px(dp, 10), 0, 0);
        root.addView(go, gl);
        TextView later = flat(a, dp, "Más tarde");
        later.setPadding(0, px(dp, 14), 0, px(dp, 6));
        later.setOnClickListener(v -> d.dismiss());
        root.addView(later, new LinearLayout.LayoutParams(-1, -2));

        d.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        d.setOnDismissListener(x -> {
            showing = false;
            closedThisVisit = true;
        });
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(Ui.BG);
            d.getWindow().setNavigationBarColor(Ui.BG);
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.show();
    }

    private static TextView flat(Context a, float dp, String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, px(dp, 14), 0, px(dp, 14));
        return t;
    }

    private static int px(float density, float v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------------ instalar

    private static void start(Activity a, Release r) {
        pending = r;
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            // la primera vez Android pide permitir que la app instale actualizaciones
            pendingPermission = true;
            new AlertDialog.Builder(a, THEME)
                    .setTitle("Un permiso, una sola vez")
                    .setMessage("Para que " + APP + " pueda instalar su propia actualización, activa «Permitir de esta fuente» "
                            + "en la pantalla que se abre y vuelve con el botón atrás.")
                    .setPositiveButton("Abrir", (dd, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            pendingPermission = false;
                            Toast.makeText(a, "No se pudo abrir el permiso", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancelar", (dd, w) -> pendingPermission = false)
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
                                long dd = done;
                                main.post(() -> {
                                    if (total > 0) bar.setProgress((int) (dd * 1000 / total));
                                    info.setText(size(dd) + (total > 0 ? " de " + size(total) : ""));
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
