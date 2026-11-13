package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * El menú de comprimir: nombre, formato, nivel, contraseña y dos casillas que siempre empiezan apagadas. Lo que un
 * formato no puede hacer se ve apagado y con un cartel que dice por qué, en vez de esconderse.
 */
final class CompressDialog {
    interface Callback {
        void onChosen(String fileName, CompressOptions options);
    }

    private CompressDialog() {}

    static void show(Activity a, String suggestedBase, Callback cb) {
        CompressOptions o = new CompressOptions();
        int dp4 = Ui.dp(a, 4);
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 4));

        // nombre
        EditText name = new EditText(a);
        name.setText(suggestedBase);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        name.setSelectAllOnFocus(true);
        LinearLayout nameRow = Ui.row(a);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.addView(name, Ui.weight(1));
        TextView ext = Ui.text(a, o.format.ext, 15, Ui.ACCENT2, true);
        nameRow.addView(ext);
        box.addView(label(a, L.t("Nombre")));
        box.addView(nameRow);

        // formato
        box.addView(label(a, L.t("Formato")));
        LinearLayout formats = Ui.row(a);
        CompressOptions.Format[] fs = CompressOptions.Format.values();
        TextView[] fTabs = new TextView[fs.length];
        for (int i = 0; i < fs.length; i++) {
            fTabs[i] = chip(a, fs[i].label);
            formats.addView(fTabs[i], chipParams(a));
        }
        box.addView(scroll(a, formats));

        // nivel
        box.addView(label(a, L.t("Nivel de compresión")));
        LinearLayout levels = Ui.row(a);
        TextView[] lTabs = new TextView[CompressOptions.LEVELS.length];
        for (int i = 0; i < lTabs.length; i++) {
            lTabs[i] = chip(a, L.t(CompressOptions.LEVELS[i]));
            levels.addView(lTabs[i], chipParams(a));
        }
        box.addView(scroll(a, levels));

        // contraseña
        Switch usePass = sw(a, L.t("Proteger con contraseña"));
        EditText pass = pass(a, L.t("Contraseña"));
        EditText pass2 = pass(a, L.t("Repite la contraseña"));
        box.addView(usePass, switchParams(a));
        box.addView(pass);
        box.addView(pass2);

        // lo que depende del formato
        Switch hide = sw(a, L.t("Ocultar los nombres de los archivos"));
        hide.setEnabled(false);
        Switch solid = sw(a, L.t("Crear archivo sólido"));
        box.addView(hide, switchParams(a));
        box.addView(solid, switchParams(a));
        LinearLayout notes = Ui.column(a);
        notes.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 2));
        box.addView(notes);

        // eliminar original
        Switch del = sw(a, L.t("Eliminar el original"));
        box.addView(del, switchParams(a));
        box.addView(small(a, L.t("Los originales van a la papelera, y solo después de comprobar que el archivo nuevo quedó bien."), Ui.SUB));

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            ext.setText(o.format.ext);
            for (int i = 0; i < fs.length; i++) mark(a, fTabs[i], fs[i] == o.format);
            for (int i = 0; i < lTabs.length; i++) mark(a, lTabs[i], i == o.level);
            boolean canPass = o.format.password();
            usePass.setAlpha(canPass ? 1f : 0.5f);
            if (!canPass) usePass.setChecked(false);
            usePass.setEnabled(canPass);
            int vis = usePass.isChecked() ? View.VISIBLE : View.GONE;
            pass.setVisibility(vis);
            pass2.setVisibility(vis);
            hide.setAlpha(0.5f);
            hide.setChecked(false);
            solid.setEnabled(false);
            solid.setAlpha(o.format.solid() ? 0.9f : 0.5f);
            solid.setChecked(o.format.solid());
            notes.removeAllViews();
            if (!canPass) notes.addView(small(a, "⚠ " + L.t("Este formato no admite contraseña. Usa .zip o .7z."), Ui.BAD));
            if (usePass.isChecked()) {
                notes.addView(small(a, "ℹ " + L.t("Con contraseña, el contenido queda cifrado (AES-256), pero los nombres de los archivos se siguen viendo."), Ui.WARN));
            }
            notes.addView(small(a, "⚠ " + L.t("Ocultar los nombres no se puede en ningún formato por ahora: la librería que usa Bóveda no lo permite."), Ui.BAD));
            if (o.format.solid()) {
                notes.addView(small(a, "✓ " + L.t("Los .tar.gz y .tar.xz ya son sólidos: comprimen todo como un solo bloque."), Ui.GOOD));
            } else {
                notes.addView(small(a, "⚠ " + L.t("«Sólido» solo existe en .tar.gz y .tar.xz. El .zip y el .7z de Bóveda guardan cada archivo por separado."), Ui.BAD));
            }
            if (o.level == 4) notes.addView(small(a, "ℹ " + L.t("Ultra gasta mucha memoria y tarda más. Si falla, usa «Alta»."), Ui.WARN));
        };
        for (int i = 0; i < fs.length; i++) {
            final int k = i;
            fTabs[i].setOnClickListener(v -> {
                o.format = fs[k];
                refresh[0].run();
            });
        }
        for (int i = 0; i < lTabs.length; i++) {
            final int k = i;
            lTabs[i].setOnClickListener(v -> {
                o.level = k;
                refresh[0].run();
            });
        }
        usePass.setOnCheckedChangeListener((b, on) -> refresh[0].run());
        refresh[0].run();

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_Boveda_Dialog)
                .setTitle(L.t("Comprimir"))
                .setView(sv)
                .setPositiveButton(L.t("Comprimir"), null)
                .setNegativeButton(L.t("Cancelar"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String base = name.getText().toString().trim();
            for (CompressOptions.Format f : fs) {
                if (base.toLowerCase().endsWith(f.ext)) base = base.substring(0, base.length() - f.ext.length());
            }
            if (!Paths.validName(base + o.format.ext)) {
                name.setError(L.t("Nombre no válido"));
                return;
            }
            if (usePass.isChecked()) {
                String p1 = pass.getText().toString(), p2 = pass2.getText().toString();
                if (p1.length() < 4) {
                    pass.setError(L.t("Usa al menos 4 caracteres"));
                    return;
                }
                if (!p1.equals(p2)) {
                    pass2.setError(L.t("No coinciden"));
                    return;
                }
                o.password = p1.toCharArray();
            } else {
                o.password = null;
            }
            o.deleteOriginal = del.isChecked();
            d.dismiss();
            cb.onChosen(base + o.format.ext, o);
        }));
        d.show();
        name.requestFocus();
    }

    // ------------------------------------------------------------------ piezas

    private static TextView label(Activity a, String s) {
        TextView t = Ui.text(a, s.toUpperCase(), 11.5f, Ui.ACCENT2, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 14), 0, Ui.dp(a, 6));
        return t;
    }

    private static TextView small(Activity a, String s, int color) {
        TextView t = Ui.text(a, s, 12, color, false);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 3), 0, Ui.dp(a, 3));
        t.setLineSpacing(Ui.dp(a, 1), 1f);
        return t;
    }

    private static TextView chip(Activity a, String s) {
        TextView t = Ui.text(a, s, 13.5f, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 9), Ui.dp(a, 14), Ui.dp(a, 9));
        return t;
    }

    private static void mark(Activity a, TextView t, boolean on) {
        t.setTextColor(on ? 0xFF0A0E16 : Ui.TEXT);
        t.setBackground(on ? Ui.gradient(Ui.dp(a, 18), Ui.ACCENT2, Ui.ACCENT) : Ui.roundStroke(Ui.SURFACE, Ui.dp(a, 18), Ui.dp(a, 1), Ui.STROKE));
    }

    private static LinearLayout.LayoutParams chipParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, 0, Ui.dp(a, 8), 0);
        return p;
    }

    private static HorizontalScrollView scroll(Activity a, View v) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }

    private static Switch sw(Activity a, String s) {
        Switch w = new Switch(a);
        w.setText(s);
        w.setTextColor(Ui.TEXT);
        w.setTextSize(14.5f);
        w.setChecked(false);
        return w;
    }

    private static LinearLayout.LayoutParams switchParams(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, Ui.dp(a, 10), 0, Ui.dp(a, 2));
        return p;
    }

    private static EditText pass(Activity a, String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(0xFF5E6884);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.passwordEye(e);
        return e;
    }
}
