package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** La ventana de «Reducir peso»: busca duplicados y deja elegir entre dejarlos o borrarlos. */
final class DuplicatesDialog {
    private DuplicatesDialog() {}

    static void show(Activity a, String path, boolean isFolder, Runnable onChanged) {
        ArchivePass.ensureForRead(a, path, isFolder, () -> run(a, path, isFolder, onChanged));
    }

    private static void run(Activity a, String path, boolean isFolder, Runnable onChanged) {
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        AtomicBoolean cancel = new AtomicBoolean(false);
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView status = Ui.text(a, L.t("Buscando…"), 14, Ui.SUB, false);
        box.addView(status);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Reducir peso"))
                .setView(sv)
                .setPositiveButton(L.t("Dejar como está"), null)
                .setNegativeButton(L.t("Borrar duplicados"), null)
                .create();
        d.setCancelable(false);
        d.show();
        android.widget.Button keep = d.getButton(AlertDialog.BUTTON_POSITIVE), del = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        del.setEnabled(false);
        keep.setOnClickListener(v -> {
            cancel.set(true);
            d.dismiss();
            io.shutdownNow();
        });
        Duplicates.Progress say = t -> main.post(() -> status.setText(t));
        io.execute(() -> {
            List<Duplicates.Group> groups;
            try {
                groups = isFolder ? Duplicates.scanFolder(a, path, say, cancel) : Duplicates.scanArchive(a, path, say, cancel);
            } catch (OutOfMemoryError oom) {
                main.post(() -> {
                    status.setTextColor(Ui.BAD);
                    status.setText(L.t("Esta carpeta es demasiado grande para revisarla de una vez. Prueba con una carpeta más chica."));
                });
                return;
            } catch (Exception e) {
                main.post(() -> {
                    status.setTextColor(Ui.BAD);
                    status.setText(e.getMessage() == null ? e.toString() : e.getMessage());
                });
                return;
            }
            List<Duplicates.Group> gs = groups;
            main.post(() -> {
                if (gs.isEmpty()) {
                    status.setTextColor(Ui.GOOD);
                    status.setText(L.t("No hay duplicados: todo lo que tiene es distinto."));
                    return;
                }
                int copies = 0;
                for (Duplicates.Group g : gs) copies += g.names.size() - 1;
                status.setTextColor(Ui.TEXT);
                status.setText(copies + (copies == 1 ? L.t(" copia repetida en ") : L.t(" copias repetidas en ")) + gs.size() + (gs.size() == 1 ? L.t(" grupo. Se ahorrarían ") : L.t(" grupos. Se ahorrarían ")) + Ui.size(Duplicates.totalSaving(gs)) + ".");
                int shown = 0;
                for (Duplicates.Group g : gs) {
                    if (++shown > 40) {
                        box.addView(note(a, "… " + L.t("y más grupos"), Ui.SUB));
                        break;
                    }
                    StringBuilder b = new StringBuilder();
                    b.append(Ui.size(g.size)).append(" × ").append(g.names.size()).append('\n');
                    for (int i = 0; i < g.names.size(); i++) {
                        b.append(i == 0 ? "✓ " : "✗ ").append(isFolder ? rel(path, g.names.get(i)) : g.names.get(i)).append('\n');
                    }
                    box.addView(note(a, b.toString().trim(), Ui.SUB));
                }
                boolean can = isFolder || Duplicates.canRewrite(path);
                String low = path.toLowerCase(java.util.Locale.ROOT);
                for (String w : new String[]{"/versions", "/libraries", "/mods", "/assets", "/runtimes", "/android/data", "/android/obb"}) {
                    if (isFolder && low.contains(w + "/") || isFolder && low.endsWith(w)) {
                        box.addView(note(a, "⚠ " + L.t("Esta parece una carpeta de un juego o de una app. Algunas copias las necesita el programa con su propio nombre: si las borras, puede dejar de abrir."), Ui.BAD));
                        break;
                    }
                }
                box.addView(note(a, "✓ " + L.t("se conserva  ·  ✗ se quita (la copia más antigua es la que queda)"), Ui.SUB));
                if (isFolder) {
                    box.addView(note(a, "ℹ " + L.t("Las copias van a la papelera: puedes recuperarlas 30 días."), Ui.WARN));
                } else if (can) {
                    box.addView(note(a, "ℹ " + L.t("Tu archivo no se toca: se crea otro .zip nuevo, al lado, sin las copias."), Ui.WARN));
                } else {
                    box.addView(note(a, "⚠ " + L.t("Por ahora solo se puede crear la versión sin duplicados de un .zip. Este formato solo se puede revisar."), Ui.BAD));
                }
                del.setEnabled(can);
                del.setOnClickListener(v -> {
                    if (!isFolder && Archive.hasPassword(path)) {
                        new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                                .setTitle(L.t("Este archivo tiene contraseña"))
                                .setMessage(L.t("¿Qué hago con la contraseña en el archivo nuevo sin duplicados?"))
                                .setPositiveButton(L.t("Dejar contraseña"), (x, w) -> work(a, path, isFolder, gs, d, keep, del, status, io, main, say, onChanged, Archive.passwordOf(path)))
                                .setNeutralButton(L.t("Quitar contraseña"), (x, w) -> work(a, path, isFolder, gs, d, keep, del, status, io, main, say, onChanged, null))
                                .setNegativeButton(L.t("Cancelar"), null)
                                .show();
                        return;
                    }
                    work(a, path, isFolder, gs, d, keep, del, status, io, main, say, onChanged, null);
                });
            });
        });
    }

    private static void work(Activity a, String path, boolean isFolder, List<Duplicates.Group> gs, AlertDialog d,
                             android.widget.Button keep, android.widget.Button del, TextView status, ExecutorService io,
                             Handler main, Duplicates.Progress say, Runnable onChanged, char[] keepPassword) {
        {
            {
                {
                    del.setEnabled(false);
                    keep.setEnabled(false);
                    status.setText(L.t("Trabajando…"));
                    io.execute(() -> {
                        String msg;
                        boolean ok = true;
                        try {
                            if (isFolder) {
                                int[] kept = {0};
                                int moved = Duplicates.trashExtras(gs, kept);
                                msg = moved + L.t(" copias a la papelera.") + (kept[0] > 0 ? " " + kept[0] + L.t(" no se pudieron mover y se dejaron.") : "");
                            } else {
                                String dest = Duplicates.rewriteZip(a, path, gs, say, keepPassword);
                                msg = L.t("Listo: «") + Paths.name(dest) + L.t("» (tu archivo original sigue igual).");
                            }
                        } catch (Exception e) {
                            ok = false;
                            msg = e.getMessage() == null ? e.toString() : e.getMessage();
                        }
                        String fm = msg;
                        boolean fo = ok;
                        main.post(() -> {
                            status.setTextColor(fo ? Ui.GOOD : Ui.BAD);
                            status.setText(fm);
                            keep.setEnabled(true);
                            keep.setText(L.t("Cerrar"));
                            if (fo && onChanged != null) onChanged.run();
                        });
                    });
                }
            }
        }
    }

    private static String rel(String root, String p) {
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : p;
    }

    private static TextView note(Activity a, String s, int color) {
        TextView t = Ui.text(a, s, 12.5f, color, false);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 6), 0, Ui.dp(a, 4));
        t.setTextIsSelectable(false);
        return t;
    }
}
