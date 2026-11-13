package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Identificar aplicaciones: todas las apps del celular, en «Usuario» y «Sistema», con su peso, sus rutas y las carpetas
 * donde tienen permiso. Todo se lee en el celular; no sale nada a internet.
 */
final class AppsInspector {
    private AppsInspector() {}

    private static final class AppRow {
        String label, pkg;
        boolean system;
        boolean factoryUpdated; // de fábrica pero ya actualizada (Play Store o parcheada)
        long apkBytes, installed, updated;
        String sourceDir, versionName, dataDir;
        long versionCode;
        int targetSdk;
        List<String> requested = new ArrayList<>();
    }

    static void show(MainActivity a) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(Ui.BG);
            d.getWindow().setNavigationBarColor(Ui.BG);
        }
        LinearLayout root = Ui.column(a);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 4));
        head.addView(Ui.iconButton(a, R.drawable.ic_back, 46, v -> d.dismiss()));
        head.addView(Ui.text(a, L.t("Identificar aplicaciones"), 20, Ui.TEXT, true), Ui.weight(1));
        root.addView(head);

        LinearLayout tabs = Ui.row(a);
        tabs.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 6));
        TextView tUser = tab(a, L.t("Usuario"), true), tUpd = tab(a, L.t("Actualizadas"), false), tSys = tab(a, L.t("Sistema"), false);
        tabs.addView(tUser, Ui.weight(1));
        tabs.addView(new View(a), new LinearLayout.LayoutParams(Ui.dp(a, 6), 1));
        tabs.addView(tUpd, Ui.weight(1));
        tabs.addView(new View(a), new LinearLayout.LayoutParams(Ui.dp(a, 6), 1));
        tabs.addView(tSys, Ui.weight(1));
        root.addView(tabs);

        EditText search = new EditText(a);
        search.setHint(L.t("Buscar por nombre o paquete"));
        search.setHintTextColor(0xFF5E6884);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setTextSize(14.5f);
        search.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        search.setBackground(Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 14), Ui.dp(a, 1), Ui.STROKE));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 6));
        root.addView(search, sp);

        TextView status = Ui.text(a, L.t("Leyendo las aplicaciones…"), 13, Ui.SUB, false);
        status.setPadding(Ui.dp(a, 18), Ui.dp(a, 4), Ui.dp(a, 18), Ui.dp(a, 6));
        root.addView(status);

        ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 20));
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        d.show();

        List<AppRow> all = new ArrayList<>();
        List<AppRow> shown = new ArrayList<>();
        int[] cat = {0}; // 0 usuario, 1 de fábrica actualizadas, 2 sistema
        PackageManager pm = a.getPackageManager();
        Handler main = new Handler(Looper.getMainLooper());
        BaseAdapter ad = new BaseAdapter() {
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
                AppRow r = shown.get(i);
                LinearLayout row = Ui.row(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
                row.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 16), Ui.dp(a, 1), Ui.STROKE), Ui.round(Color.WHITE, Ui.dp(a, 16))));
                ImageView ic = new ImageView(a);
                try {
                    ic.setImageDrawable(pm.getApplicationIcon(r.pkg));
                } catch (Exception ignored) {
                }
                row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
                LinearLayout t = Ui.column(a);
                t.setPadding(Ui.dp(a, 12), 0, 0, 0);
                TextView n = Ui.text(a, r.label, 15.5f, Ui.TEXT, true);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                t.addView(n);
                TextView p = Ui.text(a, r.pkg, 11.5f, Ui.SUB, false);
                p.setSingleLine(true);
                p.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                t.addView(p);
                row.addView(t, Ui.weight(1));
                row.addView(Ui.text(a, Ui.size(r.apkBytes), 12.5f, Ui.ACCENT2, true));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, Ui.dp(a, 8));
                row.setLayoutParams(lp);
                row.setOnClickListener(x -> options(a, r));
                // el LinearLayout dentro del ListView necesita un contenedor para respetar los márgenes
                LinearLayout cell = new LinearLayout(a);
                cell.addView(row);
                return cell;
            }
        };
        lv.setAdapter(ad);
        Runnable refilter = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            for (AppRow r : all) {
                if ((r.system ? 2 : r.factoryUpdated ? 1 : 0) != cat[0]) continue;
                if (!q.isEmpty() && !r.label.toLowerCase(Locale.ROOT).contains(q) && !r.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
                shown.add(r);
            }
            long total = 0;
            for (AppRow r : shown) total += r.apkBytes;
            status.setText(shown.size() + L.t(" aplicaciones · ") + Ui.size(total));
            ad.notifyDataSetChanged();
        };
        View.OnClickListener tabClick = v -> {
            cat[0] = v == tUser ? 0 : v == tUpd ? 1 : 2;
            style(a, tUser, cat[0] == 0);
            style(a, tUpd, cat[0] == 1);
            style(a, tSys, cat[0] == 2);
            refilter.run();
        };
        tUser.setOnClickListener(tabClick);
        tUpd.setOnClickListener(tabClick);
        tSys.setOnClickListener(tabClick);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            public void afterTextChanged(Editable e) {
                refilter.run();
            }
        });

        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            List<AppRow> found = new ArrayList<>();
            try {
                for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai == null) continue;
                    AppRow r = new AppRow();
                    r.pkg = pi.packageName;
                    r.label = String.valueOf(pm.getApplicationLabel(ai));
                    boolean preinstalled = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    // una app de fábrica que ya se actualizó (YouTube, Chrome…) la instaló/cambió el usuario: va en «Usuario»
                    r.factoryUpdated = preinstalled && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    r.system = preinstalled && !r.factoryUpdated;
                    r.sourceDir = ai.sourceDir;
                    r.dataDir = ai.dataDir;
                    r.versionName = pi.versionName == null ? "?" : pi.versionName;
                    r.versionCode = pi.getLongVersionCode();
                    r.targetSdk = ai.targetSdkVersion;
                    r.installed = pi.firstInstallTime;
                    r.updated = pi.lastUpdateTime;
                    long size = ai.sourceDir == null ? 0 : new File(ai.sourceDir).length();
                    if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) size += new File(s).length();
                    r.apkBytes = size;
                    if (pi.requestedPermissions != null) for (String p : pi.requestedPermissions) r.requested.add(p);
                    found.add(r);
                }
            } catch (Exception ignored) {
            }
            found.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
            main.post(() -> {
                all.addAll(found);
                int u = 0, s = 0, up = 0;
                for (AppRow r : found) if (r.system) s++;
                else if (r.factoryUpdated) up++;
                else u++;
                tUser.setText(L.t("Usuario") + " · " + u);
                tUpd.setText(L.t("Actualizadas") + " · " + up);
                tSys.setText(L.t("Sistema") + " · " + s);
                refilter.run();
            });
        });
        d.setOnDismissListener(x -> io.shutdown());
    }

    private static TextView tab(Activity a, String s, boolean on) {
        TextView t = Ui.text(a, s, 13f, on ? 0xFF0A0E16 : Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, 11), Ui.dp(a, 10), Ui.dp(a, 11));
        style(a, t, on);
        return t;
    }

    private static void style(Activity a, TextView t, boolean on) {
        t.setTextColor(on ? 0xFF0A0E16 : Ui.TEXT);
        t.setBackground(on ? Ui.gradient(Ui.dp(a, 20), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 20), Ui.dp(a, 1), Ui.STROKE));
    }

    // ------------------------------------------------------------------ una app

    private static void options(MainActivity a, AppRow r) {
        Dialog d = Kit.sheet(a);
        LinearLayout list = Kit.sheetBody(a, d, r.label, r.pkg);
        Kit.option(a, list, "ℹ️", 0xFF4C9DFF, 0xFF6FD6FF, L.t("Ver información de la app"), L.t("Peso del APK, versión y dónde está"), false, null, () -> {
            d.dismiss();
            info(a, r);
        });
        Kit.option(a, list, "🗂️", 0xFFFFB547, 0xFFF07C2C, L.t("Ver rutas de almacenamiento"), L.t("Todas sus carpetas y si tiene permiso en alguna"), false, null, () -> {
            d.dismiss();
            paths(a, r);
        });
        Kit.option(a, list, "📁", 0xFF35D08A, 0xFF1EA97C, L.t("Ver directorio de la app"), L.t("Su carpeta en Android/data y Android/obb"), false, null, () -> {
            d.dismiss();
            directories(a, r);
        });
        d.show();
    }

    private static void info(Activity a, AppRow r) {
        StringBuilder b = new StringBuilder();
        b.append(L.t("Tipo: ")).append(r.system ? L.t("de sistema") : r.factoryUpdated ? L.t("de fábrica, ya actualizada") : L.t("de usuario")).append('\n');
        b.append(L.t("Versión: ")).append(r.versionName).append(" (").append(r.versionCode).append(")\n");
        b.append(L.t("Peso del APK: ")).append(Ui.size(r.apkBytes)).append("  (").append(String.format(Locale.US, "%,d", r.apkBytes)).append(" bytes)\n");
        b.append(L.t("Instalada: ")).append(Ui.date(r.installed)).append('\n');
        b.append(L.t("Actualizada: ")).append(Ui.date(r.updated)).append('\n');
        b.append(L.t("Compilada para Android API ")).append(r.targetSdk).append("\n\n");
        b.append(L.t("Directorio del APK:\n")).append(r.sourceDir == null ? "?" : new File(r.sourceDir).getParent());
        dialog(a, r.label, b.toString(), r.sourceDir == null ? null : new File(r.sourceDir).getParent(), null);
    }

    private static void paths(MainActivity a, AppRow r) {
        StringBuilder b = new StringBuilder();
        b.append(L.t("APK:\n")).append(r.sourceDir == null ? "?" : r.sourceDir).append("\n\n");
        b.append(L.t("Datos privados (solo la app los ve):\n")).append(r.dataDir).append("\n\n");
        String root = Paths.ROOT;
        b.append(L.t("Datos en el almacenamiento:\n")).append(root).append("/Android/data/").append(r.pkg).append("\n\n");
        File obb = new File(root + "/Android/obb/" + r.pkg);
        b.append(L.t("OBB: ")).append(obb.exists() ? obb.getPath() : L.t("no tiene (o Android no la deja ver)")).append("\n\n");
        b.append(L.t("Multimedia:\n")).append(root).append("/Android/media/").append(r.pkg).append("\n\n");
        // permisos que Bóveda le dio a esta app
        StringBuilder g = new StringBuilder();
        for (Grants.Grant gr : Grants.active(a)) {
            if (gr.pkg.equals(r.pkg)) {
                g.append("• ").append(gr.root);
                if (gr.target != null) g.append(L.t("  (apunta a ")).append(gr.target).append(')');
                g.append('\n');
            }
        }
        b.append(L.t("Carpetas donde Bóveda le dio permiso:\n")).append(g.length() == 0 ? L.t("ninguna\n") : g.toString());
        boolean allFiles = r.requested.contains("android.permission.MANAGE_EXTERNAL_STORAGE");
        b.append('\n').append(allFiles ? L.t("Esta app pide acceso a todos los archivos del celular.") : L.t("Esta app no pide acceso a todos los archivos."));
        dialog(a, r.label, b.toString(), null, null);
    }

    private static void directories(MainActivity a, AppRow r) {
        Dialog d = Kit.sheet(a);
        LinearLayout list = Kit.sheetBody(a, d, r.label, L.t("Elige una carpeta"));
        String root = Paths.ROOT;
        List<String> dirs = new ArrayList<>();
        dirs.add(root + "/Android/data/" + r.pkg);
        if (new File(root + "/Android/obb/" + r.pkg).exists()) dirs.add(root + "/Android/obb/" + r.pkg);
        dirs.add(root + "/Android/media/" + r.pkg);
        for (String dir : dirs) {
            String kind = dir.contains("/obb/") ? "OBB" : dir.contains("/media/") ? L.t("Multimedia") : L.t("Datos");
            LinearLayout box = Ui.column(a);
            box.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            box.setBackground(Ui.round(Ui.SURFACE2, Ui.dp(a, 16)));
            box.addView(Ui.text(a, kind, 14.5f, Ui.TEXT, true));
            TextView p = Ui.text(a, dir, 12.5f, Ui.SUB, false);
            p.setTextIsSelectable(true);
            p.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 8));
            box.addView(p);
            LinearLayout btns = Ui.row(a);
            TextView copy = pill(a, "📋  " + L.t("Copiar ruta"));
            copy.setOnClickListener(v -> {
                ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ruta", dir));
                Toast.makeText(a, L.t("Ruta copiada"), Toast.LENGTH_SHORT).show();
            });
            TextView open = pill(a, "📂  " + L.t("Abrir directorio"));
            open.setOnClickListener(v -> {
                d.dismiss();
                a.open(dir);
            });
            btns.addView(copy, Ui.weight(1));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, -2, 1);
            gp.setMargins(Ui.dp(a, 8), 0, 0, 0);
            btns.addView(open, gp);
            box.addView(btns);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, Ui.dp(a, 8));
            list.addView(box, lp);
        }
        TextView note = Ui.text(a, L.t("Android esconde estas carpetas desde la versión 11. Bóveda las abre con el acceso que diste en Ajustes → Acceso a carpetas (Shizuku)."), 12, Ui.SUB, false);
        note.setPadding(Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4), 0);
        list.addView(note);
        d.show();
    }

    private static TextView pill(Activity a, String s) {
        TextView t = Ui.text(a, s, 13.5f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10));
        t.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE3, Ui.dp(a, 16), Ui.dp(a, 1), Ui.STROKE), Ui.round(Color.WHITE, Ui.dp(a, 16))));
        return t;
    }

    /** Un cuadro con texto que se puede seleccionar y, si hay ruta, el botón de copiarla. */
    private static void dialog(Activity a, String title, String text, String copyPath, Runnable unused) {
        TextView t = Ui.text(a, text, 13.5f, Ui.TEXT, false);
        t.setTextIsSelectable(true);
        t.setLineSpacing(Ui.dp(a, 2), 1f);
        t.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(t);
        AlertDialog.Builder b = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog).setTitle(title).setView(sv)
                .setPositiveButton(L.t("Cerrar"), null);
        if (copyPath != null) {
            b.setNeutralButton(L.t("Copiar ruta"), (x, w) -> {
                ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ruta", copyPath));
                Toast.makeText(a, L.t("Ruta copiada"), Toast.LENGTH_SHORT).show();
            });
        }
        b.show();
    }
}
