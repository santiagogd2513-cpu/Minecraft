package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Máquina del tiempo: lo que pasó con tus archivos (de una carpeta o de todo), día por día, con botón para recuperarlo. */
final class TimeMachine {
    private TimeMachine() {}

    private static final class Row {
        long time;
        String icon, title, where, extra;
        String actionLabel;
        Trash.Item trash;
        UndoLog.Action undo;
    }

    private static boolean under(String p, String folder) {
        return folder == null || (p != null && (p.equals(folder) || p.startsWith(folder + "/")));
    }

    /** Muestra el historial. folder = null para todo; si no, solo esa carpeta y lo que tiene dentro. */
    static void show(Activity act, String folder, Runnable changed) {
        Handler main = new Handler(Looper.getMainLooper());
        Dialog dlg = new Dialog(act, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = Ui.column(act);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(act);
        head.setPadding(Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 6));
        head.addView(Ui.iconButton(act, R.drawable.ic_back, 44, v -> dlg.dismiss()));
        LinearLayout ht = Ui.column(act);
        ht.setPadding(Ui.dp(act, 6), 0, 0, 0);
        ht.addView(Ui.text(act, "⏳ " + L.t("Máquina del tiempo"), 20, Ui.TEXT, true));
        ht.addView(Ui.text(act, folder == null ? L.t("Todo lo que ha pasado con tus archivos") : Paths.name(folder), 12.5f, Ui.SUB, false));
        head.addView(ht, Ui.weight(1));
        root.addView(head);
        TextView note = Ui.text(act, L.t("Toca algo para recuperarlo o devolverlo a su sitio. Lo borrado se guarda 30 días en la papelera."), 13, Ui.SUB, false);
        note.setPadding(Ui.dp(act, 18), 0, Ui.dp(act, 18), Ui.dp(act, 8));
        root.addView(note);
        ScrollView sc = new ScrollView(act);
        LinearLayout box = Ui.column(act);
        box.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), Ui.dp(act, 30));
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        dlg.setContentView(root);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> new Thread(() -> {
            List<Row> rows = new ArrayList<>();
            for (Trash.Item it : Trash.list(act)) {
                if (!under(it.original, folder)) continue;
                Row r = new Row();
                r.time = it.deleted;
                r.icon = "🗑️";
                r.title = L.t("Borrado: ") + it.name();
                r.where = Paths.parent(it.original).replace(Paths.ROOT, L.t("Interno"));
                r.extra = Ui.size(it.size) + "  ·  " + (it.daysLeft() == 1 ? L.t("queda 1 día") : L.t("quedan ") + it.daysLeft() + L.t(" días"));
                r.actionLabel = L.t("Recuperar");
                r.trash = it;
                rows.add(r);
            }
            for (Diario.Event e : Diario.all(act)) {
                if (!under(e.to, folder) && !under(e.from, folder)) continue;
                Row r = new Row();
                r.time = e.time;
                UndoLog.Action a = new UndoLog.Action();
                switch (e.kind) {
                    case "move" -> {
                        r.icon = "📦";
                        r.title = L.t("Movido: ") + Paths.name(e.to);
                        r.where = Paths.parent(e.from).replace(Paths.ROOT, L.t("Interno")) + " → " + Paths.parent(e.to).replace(Paths.ROOT, L.t("Interno"));
                        r.actionLabel = L.t("Devolver a su sitio");
                        a.pairs.add(new String[]{e.from, e.to});
                    }
                    case "rename" -> {
                        r.icon = "✏️";
                        r.title = L.t("Renombrado: ") + Paths.name(e.from) + " → " + Paths.name(e.to);
                        r.where = Paths.parent(e.to).replace(Paths.ROOT, L.t("Interno"));
                        r.actionLabel = L.t("Devolver el nombre");
                        a.pairs.add(new String[]{e.from, e.to});
                    }
                    default -> {
                        r.icon = "✨";
                        r.title = L.t("Creado: ") + Paths.name(e.to);
                        r.where = Paths.parent(e.to).replace(Paths.ROOT, L.t("Interno"));
                        r.actionLabel = L.t("Mandar a la papelera");
                        a.created.add(e.to);
                    }
                }
                r.undo = a;
                rows.add(r);
            }
            Collections.sort(rows, (x, y) -> Long.compare(y.time, x.time));
            main.post(() -> {
                box.removeAllViews();
                if (rows.isEmpty()) {
                    TextView t = Ui.text(act, "⏳\n\n" + L.t("Todavía no hay nada que mostrar.\nAquí irá apareciendo lo que muevas, renombres, crees o borres."), 15, Ui.SUB, false);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(0, Ui.dp(act, 80), 0, 0);
                    box.addView(t);
                    return;
                }
                String lastDay = "";
                for (Row r : rows) {
                    String day = dayLabel(r.time);
                    if (!day.equals(lastDay)) {
                        lastDay = day;
                        TextView h = Ui.text(act, day, 13, Ui.ACCENT2, true);
                        h.setPadding(Ui.dp(act, 4), Ui.dp(act, 14), 0, Ui.dp(act, 6));
                        box.addView(h);
                    }
                    LinearLayout row = Ui.row(act);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(act, 12), Ui.dp(act, 11), Ui.dp(act, 12), Ui.dp(act, 11));
                    row.setBackground(Ui.rippleRound(Ui.SURFACE, Ui.dp(act, 16)));
                    TextView ic = Ui.text(act, r.icon, 20, Ui.TEXT, false);
                    row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(act, 32), -2));
                    LinearLayout tx = Ui.column(act);
                    TextView n = Ui.text(act, r.title, 15, Ui.TEXT, true);
                    n.setSingleLine(true);
                    n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    tx.addView(n);
                    TextView w = Ui.text(act, r.where, 12, Ui.SUB, false);
                    w.setSingleLine(true);
                    w.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    tx.addView(w);
                    tx.addView(Ui.text(act, new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date(r.time))
                            + (r.extra == null ? "" : "  ·  " + r.extra), 11.5f, 0xFF6E7A8E, false));
                    row.addView(tx, Ui.weight(1));
                    row.setOnClickListener(v -> new AlertDialog.Builder(act, R.style.Theme_Boveda_Dialog)
                            .setTitle(r.title)
                            .setMessage(r.where)
                            .setPositiveButton(r.actionLabel, (d, wh) -> new Thread(() -> {
                                String msg;
                                try {
                                    if (r.trash != null) {
                                        Trash.restore(r.trash);
                                        msg = L.t("Recuperado");
                                    } else {
                                        msg = UndoLog.undo(act, r.undo);
                                    }
                                } catch (IOException ex) {
                                    msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                                }
                                final String m = msg;
                                main.post(() -> {
                                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                                    if (changed != null) changed.run();
                                    refresh[0].run();
                                });
                            }).start())
                            .setNegativeButton(L.t("Cerrar"), null).show());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.setMargins(0, 0, 0, Ui.dp(act, 6));
                    box.addView(row, lp);
                }
            });
        }).start();
        refresh[0].run();
        dlg.show();
    }

    private static String dayLabel(long t) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis(), day = 24L * 60 * 60 * 1000;
        if (t >= today) return L.t("Hoy");
        if (t >= today - day) return L.t("Ayer");
        return new java.text.SimpleDateFormat("EEEE d 'de' MMMM", new java.util.Locale("es")).format(new java.util.Date(t));
    }
}
