package com.ventoy.nooficial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * El administrador de ISOs: abre la USB (la partición «Ventoy», que Android monta como cualquier memoria)
 * con el selector de carpetas de Android y deja ver, copiar desde el celular, renombrar y borrar los
 * archivos de arranque. No necesita permisos raros: todo pasa por el selector del sistema.
 */
final class IsoManager {
    static final int REQ_TREE = 31, REQ_ADD = 32;
    private static final String PREFS = "isos", KEY_TREE = "tree";
    private static final int PURPLE_DARK = 0xFF2A1458, PURPLE = 0xFF8E5BFF, PURPLE_LIGHT = 0xFFB388FF;
    private static final Set<String> BOOT = Set.of("iso", "img", "wim", "vhd", "vhdx", "efi", "vtoy");

    private static final class Doc {
        String id, name, mime;
        long size, modified;
        boolean dir;

        String ext() {
            int i = name.lastIndexOf('.');
            return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
        }

        boolean boot() {
            return BOOT.contains(ext());
        }
    }

    private final Activity a;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Uri tree;
    private final List<Doc> path = new ArrayList<>();
    private List<Doc> items = new ArrayList<>();
    private boolean showAll, loading;
    private String loadError;
    private Dialog dlg;
    private LinearLayout box;
    private TextView title, sub;
    private volatile boolean cancelCopy;
    private Dialog progressDlg;

    IsoManager(Activity a) {
        this.a = a;
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    private SharedPreferences prefs() {
        return a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ abrir la USB

    void open() {
        String s = prefs().getString(KEY_TREE, null);
        if (s != null) {
            Uri u = Uri.parse(s);
            if (hasPermission(u)) {
                startAt(u);
                return;
            }
        }
        new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                .setTitle("Elige tu USB")
                .setMessage("Se abre el selector de carpetas de Android:\n\n"
                        + "1. Toca el menú ☰ (arriba a la izquierda) y escoge tu USB.\n"
                        + "2. Toca «Usar esta carpeta» y permite el acceso.\n\n"
                        + "Si tu USB no aparece, es que su partición está en NTFS o ext4, que Android no abre. "
                        + "Con FAT32 o exFAT sí funciona.")
                .setPositiveButton("Elegir", (d, w) -> pickTree())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean hasPermission(Uri u) {
        for (android.content.UriPermission p : a.getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(u) && p.isReadPermission() && p.isWritePermission()) return true;
        }
        return false;
    }

    private void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            a.startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            toast("Este celular no tiene selector de carpetas");
        }
    }

    /** Devuelve true si el resultado era de este administrador. */
    boolean onResult(int req, int res, Intent data) {
        if (req == REQ_TREE) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri u = data.getData();
                try {
                    a.getContentResolver().takePersistableUriPermission(u,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    toast("Android no me dejó guardar el permiso: tendrás que elegir la USB cada vez");
                }
                prefs().edit().putString(KEY_TREE, u.toString()).apply();
                startAt(u);
            }
            return true;
        }
        if (req == REQ_ADD) {
            if (res == Activity.RESULT_OK && data != null) {
                List<Uri> uris = new ArrayList<>();
                ClipData cd = data.getClipData();
                if (cd != null) {
                    for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
                if (!uris.isEmpty()) copyAll(uris);
            }
            return true;
        }
        return false;
    }

    private void startAt(Uri t) {
        tree = t;
        path.clear();
        Doc root = new Doc();
        root.id = DocumentsContract.getTreeDocumentId(t);
        root.name = "USB";
        root.dir = true;
        path.add(root);
        showDialog();
        loadRootName();
        load();
    }

    private void loadRootName() {
        io.execute(() -> {
            try (Cursor c = a.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree, path.get(0).id),
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) {
                    String n = c.getString(0);
                    main.post(() -> {
                        if (!path.isEmpty()) path.get(0).name = n;
                        header();
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------------ pantalla

    private void showDialog() {
        if (dlg != null && dlg.isShowing()) dlg.dismiss();
        dlg = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{PURPLE_DARK, Ui.BG, Ui.BG}));
        sv.setFitsSystemWindows(true);
        LinearLayout col = Ui.column(a);
        col.setPadding(dp(16), dp(12), dp(16), dp(28));
        sv.addView(col);

        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(a, R.drawable.ic_back, 42, v -> goBack()));
        LinearLayout titles = Ui.column(a);
        titles.setPadding(dp(8), 0, 0, 0);
        title = Ui.text(a, "Administrador de ISOs", 21, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        sub = Ui.text(a, "", 12.5f, Ui.SUB, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(title);
        titles.addView(sub);
        bar.addView(titles, Ui.weight(1));
        bar.addView(Ui.iconButton(a, R.drawable.ic_refresh, 42, v -> load()));
        col.addView(bar);

        box = Ui.column(a);
        col.addView(box, new LinearLayout.LayoutParams(-1, -2));
        dlg.setContentView(sv, new ViewGroup.LayoutParams(-1, -1));
        if (dlg.getWindow() != null) {
            dlg.getWindow().setStatusBarColor(PURPLE_DARK);
            dlg.getWindow().setNavigationBarColor(Ui.BG);
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dlg.setOnKeyListener((d, key, ev) -> {
            if (key == KeyEvent.KEYCODE_BACK && ev.getAction() == KeyEvent.ACTION_UP) {
                goBack();
                return true;
            }
            return false;
        });
        dlg.show();
        header();
    }

    private void goBack() {
        if (path.size() > 1) {
            path.remove(path.size() - 1);
            load();
        } else if (dlg != null) {
            dlg.dismiss();
        }
    }

    private void header() {
        if (title == null || path.isEmpty()) return;
        Doc cur = path.get(path.size() - 1);
        title.setText(path.size() == 1 ? "Administrador de ISOs" : cur.name);
        StringBuilder p = new StringBuilder();
        for (Doc d : path) p.append(p.length() == 0 ? "" : " › ").append(d.name);
        sub.setText(p);
    }

    private void load() {
        header();
        loading = true;
        loadError = null;
        render();
        final Doc folder = path.get(path.size() - 1);
        io.execute(() -> {
            List<Doc> out = new ArrayList<>();
            String err = null;
            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id);
            try (Cursor c = a.getContentResolver().query(kids, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    Doc d = new Doc();
                    d.id = c.getString(0);
                    d.name = c.getString(1) == null ? "?" : c.getString(1);
                    d.mime = c.getString(2);
                    d.size = c.isNull(3) ? -1 : c.getLong(3);
                    d.modified = c.isNull(4) ? 0 : c.getLong(4);
                    d.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(d.mime);
                    out.add(d);
                }
            } catch (Exception e) {
                err = "No pude leer la USB (¿la desconectaste?): " + e.getMessage();
            }
            out.sort(Comparator.<Doc, Boolean>comparing(d -> !d.dir).thenComparing(d -> d.name.toLowerCase(Locale.ROOT)));
            final String fe = err;
            main.post(() -> {
                if (path.isEmpty() || path.get(path.size() - 1) != folder) return;
                items = out;
                loading = false;
                loadError = fe;
                render();
            });
        });
    }

    private void render() {
        if (box == null) return;
        box.removeAllViews();
        header();
        Doc cur = path.get(path.size() - 1);

        // espacio de la USB y botón de agregar
        long[] sp = space();
        if (sp != null) {
            TextView t = Ui.text(a, "Espacio libre: " + Ui.size(sp[0]) + " de " + Ui.size(sp[1]), 13, Ui.SUB, false);
            t.setPadding(dp(4), dp(10), 0, 0);
            box.addView(t);
            ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress(sp[1] > 0 ? (int) (1000 - sp[0] * 1000 / sp[1]) : 0);
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2140));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(8));
            pl.setMargins(0, dp(6), 0, dp(4));
            box.addView(pb, pl);
        }

        LinearLayout actions = Ui.row(a);
        actions.setPadding(0, dp(12), 0, dp(10));
        TextView add = Ui.text(a, "➕  Agregar desde el celular", 15, 0xFF12081F, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14), dp(13), dp(14), dp(13));
        add.setBackground(Ui.ripple(Ui.gradient(dp(18), PURPLE, PURPLE_LIGHT), Ui.round(Color.WHITE, dp(18))));
        add.setOnClickListener(v -> pickFiles());
        actions.addView(add, Ui.weight(1));
        TextView filter = Ui.text(a, showAll ? "Todos" : "Solo arranque", 12.5f, PURPLE_LIGHT, true);
        filter.setGravity(Gravity.CENTER);
        filter.setPadding(dp(12), dp(13), dp(12), dp(13));
        filter.setBackground(Ui.roundStroke(0x00000000, dp(18), dp(1), 0xFF4A3A78));
        filter.setOnClickListener(v -> {
            showAll = !showAll;
            render();
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
        fl.setMargins(dp(8), 0, 0, 0);
        actions.addView(filter, fl);
        box.addView(actions);

        if (loading) {
            box.addView(Ui.text(a, "Leyendo la USB…", 14, Ui.SUB, false));
            return;
        }
        if (loadError != null) {
            TextView e = Ui.text(a, loadError, 14, Ui.BAD, false);
            e.setPadding(dp(4), dp(10), 0, 0);
            box.addView(e);
            return;
        }
        int shown = 0;
        long total = 0;
        for (Doc d : items) {
            if (!d.dir && !showAll && !d.boot()) continue;
            shown++;
            if (!d.dir && d.size > 0) total += d.size;
            box.addView(row(d), rowParams());
        }
        if (shown == 0) {
            TextView e = Ui.text(a, items.isEmpty()
                    ? "Esta carpeta está vacía.\n\nToca «Agregar desde el celular» para copiar ISOs a la USB."
                    : "Aquí no hay archivos de arranque (ISO, IMG, WIM, VHD, EFI).\n\nToca «Solo arranque» para ver todos los archivos.",
                    14, Ui.SUB, false);
            e.setLineSpacing(dp(3), 1f);
            e.setPadding(dp(4), dp(14), 0, 0);
            box.addView(e);
        } else {
            TextView t = Ui.text(a, shown + (shown == 1 ? " elemento" : " elementos") + (total > 0 ? " · " + Ui.size(total) : ""), 12, 0xFF6C6585, false);
            t.setPadding(dp(4), dp(10), 0, 0);
            box.addView(t);
        }
        if (cur.dir && path.size() == 1 && items.isEmpty()) {
            // nada más
        }
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(8));
        return p;
    }

    private View row(Doc d) {
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(13), dp(14), dp(13));
        r.setBackground(Ui.ripple(Ui.roundStroke(0xFF181228, dp(18), dp(1), 0xFF2E2347), Ui.round(Color.WHITE, dp(18))));
        TextView ic = Ui.text(a, d.dir ? "📁" : d.boot() ? "💿" : "📄", 24, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), -2));
        LinearLayout t = Ui.column(a);
        t.setPadding(dp(10), 0, 0, 0);
        TextView n = Ui.text(a, d.name, 15.5f, d.dir || d.boot() ? Ui.TEXT : Ui.SUB, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        String s = d.dir ? "Carpeta" : (d.size >= 0 ? Ui.size(d.size) : "") + (d.modified > 0 ? "  ·  " + Ui.date(d.modified) : "");
        t.addView(Ui.text(a, s, 12.5f, Ui.SUB, false));
        r.addView(t, Ui.weight(1));
        r.addView(Ui.text(a, d.dir ? "›" : "⋮", 22, Ui.SUB, false));
        r.setOnClickListener(v -> {
            if (d.dir) {
                path.add(d);
                load();
            } else {
                fileMenu(d);
            }
        });
        r.setOnLongClickListener(v -> {
            fileMenu(d);
            return true;
        });
        return r;
    }

    // ------------------------------------------------------------------ acciones sobre un archivo

    private Uri uriOf(Doc d) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, d.id);
    }

    private void fileMenu(Doc d) {
        Kit.choose(a, d.name, d.dir ? "Carpeta" : Ui.size(d.size),
                new String[]{"✏️  Renombrar", "🗑️  Borrar", "ℹ️  Información"},
                new String[]{"Cambia el nombre en la USB", d.dir ? "Borra la carpeta y todo lo que tiene" : "Se borra de la USB para siempre", ""},
                -1, i -> {
                    if (i == 0) rename(d);
                    else if (i == 1) confirmDelete(d);
                    else info(d);
                });
    }

    private void info(Doc d) {
        new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                .setTitle(d.name)
                .setMessage((d.dir ? "Carpeta" : "Tamaño: " + Ui.size(d.size)
                        + (d.size >= 0 ? "  (" + String.format(Locale.US, "%,d", d.size) + " bytes)" : ""))
                        + (d.modified > 0 ? "\nModificado: " + Ui.date(d.modified) : "")
                        + (d.dir || d.boot() ? "" : "\n\nNo es un archivo de arranque de Ventoy."))
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void rename(Doc d) {
        EditText e = new EditText(a);
        e.setText(d.name);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setSelectAllOnFocus(true);
        int p = dp(20);
        LinearLayout wrap = Ui.column(a);
        wrap.setPadding(p, dp(8), p, 0);
        wrap.addView(e);
        new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                .setTitle("Renombrar")
                .setView(wrap)
                .setPositiveButton("Cambiar", (x, w) -> {
                    String nn = e.getText().toString().trim();
                    if (nn.isEmpty() || nn.equals(d.name)) return;
                    io.execute(() -> {
                        String err = null;
                        try {
                            if (DocumentsContract.renameDocument(a.getContentResolver(), uriOf(d), nn) == null) err = "La USB no dejó cambiar el nombre";
                        } catch (Exception ex) {
                            err = "No pude renombrar: " + ex.getMessage();
                        }
                        final String fe = err;
                        main.post(() -> {
                            if (fe != null) toast(fe);
                            load();
                        });
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmDelete(Doc d) {
        new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                .setTitle("Borrar «" + d.name + "»")
                .setMessage(d.dir ? "Se borra la carpeta y todo lo que tiene, de la USB, para siempre."
                        : "Se borra de la USB para siempre (no va a una papelera).")
                .setPositiveButton("Borrar", (x, w) -> io.execute(() -> {
                    String err = null;
                    try {
                        if (!DocumentsContract.deleteDocument(a.getContentResolver(), uriOf(d))) err = "La USB no dejó borrarlo";
                    } catch (Exception ex) {
                        err = "No pude borrar: " + ex.getMessage();
                    }
                    final String fe = err;
                    main.post(() -> {
                        if (fe != null) toast(fe);
                        load();
                    });
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ copiar desde el celular

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            a.startActivityForResult(i, REQ_ADD);
        } catch (Exception e) {
            toast("Este celular no tiene selector de archivos");
        }
    }

    private static final class Pick {
        Uri uri;
        String name;
        long size;
    }

    private void copyAll(List<Uri> uris) {
        final Doc folder = path.get(path.size() - 1);
        final ContentResolver cr = a.getContentResolver();
        // nombre y tamaño de cada archivo
        List<Pick> picks = new ArrayList<>();
        for (Uri u : uris) {
            Pick p = new Pick();
            p.uri = u;
            p.name = "archivo";
            p.size = -1;
            try (Cursor c = cr.query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                    if (ni >= 0 && c.getString(ni) != null) p.name = c.getString(ni);
                    if (si >= 0 && !c.isNull(si)) p.size = c.getLong(si);
                }
            } catch (Exception ignored) {
            }
            picks.add(p);
        }
        long need = 0;
        for (Pick p : picks) need += Math.max(0, p.size);
        long[] sp = space();
        if (sp != null && need > sp[0]) {
            new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog)
                    .setTitle("No cabe en la USB")
                    .setMessage("Quieres copiar " + Ui.size(need) + " y a la USB le quedan " + Ui.size(sp[0]) + ".")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        cancelCopy = false;
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressDlg = progressDialog();
        final TextView name = (TextView) progressDlg.findViewById(1001), detail = (TextView) progressDlg.findViewById(1002);
        final ProgressBar bar = (ProgressBar) progressDlg.findViewById(1003);
        progressDlg.show();
        io.execute(() -> {
            int ok = 0;
            String err = null;
            for (int i = 0; i < picks.size() && !cancelCopy; i++) {
                Pick p = picks.get(i);
                final int idx = i;
                main.post(() -> name.setText((picks.size() > 1 ? "(" + (idx + 1) + " de " + picks.size() + ")  " : "") + p.name));
                Uri target = null;
                try {
                    String ext = p.name.lastIndexOf('.') < 0 ? "" : p.name.substring(p.name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    if (mime == null) mime = "application/octet-stream";
                    target = DocumentsContract.createDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, folder.id), mime, p.name);
                    if (target == null) throw new java.io.IOException("la USB no dejó crear el archivo");
                    long done = 0, t0 = System.currentTimeMillis(), last = 0;
                    try (InputStream in = cr.openInputStream(p.uri); OutputStream out = cr.openOutputStream(target, "w")) {
                        if (in == null || out == null) throw new java.io.IOException("no pude abrir el archivo");
                        byte[] buf = new byte[1 << 20];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (cancelCopy) break;
                            out.write(buf, 0, n);
                            done += n;
                            long now = System.currentTimeMillis();
                            if (now - last > 250) {
                                last = now;
                                final long dn = done;
                                final double speed = dn / Math.max(1.0, (now - t0) / 1000.0);
                                final long total = p.size;
                                main.post(() -> {
                                    if (total > 0) bar.setProgress((int) (dn * 1000 / total));
                                    detail.setText(Ui.size(dn) + (total > 0 ? " de " + Ui.size(total) : "") + "  ·  " + Ui.speed(speed)
                                            + (total > 0 ? "  ·  faltan " + Ui.eta((long) ((total - dn) / Math.max(1.0, speed))) : ""));
                                });
                            }
                        }
                        out.flush();
                    }
                    if (cancelCopy) {
                        try {
                            DocumentsContract.deleteDocument(cr, target);
                        } catch (Exception ignored) {
                        }
                    } else {
                        ok++;
                    }
                } catch (Exception ex) {
                    if (target != null) {
                        try {
                            DocumentsContract.deleteDocument(cr, target);
                        } catch (Exception ignored) {
                        }
                    }
                    String m = String.valueOf(ex.getMessage());
                    err = "No pude copiar «" + p.name + "»: " + m
                            + (p.size > 4L * 1024 * 1024 * 1024 - 1 ? "\n\nEse archivo pesa más de 4 GB: si la USB está en FAT32, no cabe. Reinstala Ventoy con exFAT o NTFS."
                            : "\n\nSi la USB se desconectó, vuelve a conectarla y a elegirla.");
                    break;
                }
            }
            final int fok = ok;
            final String ferr = err;
            main.post(() -> {
                a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (progressDlg != null) progressDlg.dismiss();
                if (ferr != null) {
                    new AlertDialog.Builder(a, R.style.Theme_Ventoy_Dialog).setTitle("Algo salió mal").setMessage(ferr)
                            .setPositiveButton("Entendido", null).show();
                } else if (cancelCopy) {
                    toast("Copia cancelada");
                } else if (fok > 0) {
                    toast(fok == 1 ? "Listo: 1 archivo copiado" : "Listo: " + fok + " archivos copiados");
                }
                load();
            });
        });
    }

    private Dialog progressDialog() {
        Dialog d = new Dialog(a, R.style.Theme_Ventoy_Dialog);
        d.setCancelable(false);
        LinearLayout c = Ui.column(a);
        c.setPadding(dp(22), dp(20), dp(22), dp(16));
        c.setBackground(Ui.round(0xFF1A1430, dp(22)));
        TextView t = Ui.text(a, "Copiando a la USB…", 16, Ui.TEXT, true);
        c.addView(t);
        TextView name = Ui.text(a, "", 13.5f, PURPLE_LIGHT, false);
        name.setId(1001);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setPadding(0, dp(6), 0, 0);
        c.addView(name);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setId(1003);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2A2140));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(10));
        bl.setMargins(0, dp(14), 0, dp(10));
        c.addView(bar, bl);
        TextView detail = Ui.text(a, "Empezando…", 12.5f, Ui.SUB, false);
        detail.setId(1002);
        c.addView(detail);
        TextView warn = Ui.text(a, "⚠  No desconectes la USB hasta que termine.", 12, Ui.WARN, false);
        warn.setPadding(0, dp(10), 0, dp(12));
        c.addView(warn);
        TextView cancel = Ui.text(a, "Cancelar", 14, 0xFFFF8A96, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(8), dp(10), dp(8), dp(4));
        cancel.setOnClickListener(v -> {
            cancelCopy = true;
            cancel.setText("Cancelando…");
        });
        c.addView(cancel);
        d.setContentView(c);
        return d;
    }

    // ------------------------------------------------------------------ espacio libre

    /** {libre, total} de la USB elegida, o null si Android no lo dice. */
    private long[] space() {
        try {
            if (tree == null || Build.VERSION.SDK_INT < 30) return null;
            String id = DocumentsContract.getTreeDocumentId(tree);
            String vol = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
            StorageManager sm = (StorageManager) a.getSystemService(Activity.STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes()) {
                String uuid = v.isPrimary() ? "primary" : v.getUuid();
                if (uuid != null && uuid.equalsIgnoreCase(vol) && v.getDirectory() != null) {
                    StatFs s = new StatFs(v.getDirectory().getPath());
                    return new long[]{s.getAvailableBytes(), s.getTotalBytes()};
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void toast(String s) {
        Toast.makeText(a, s, Toast.LENGTH_LONG).show();
    }
}
