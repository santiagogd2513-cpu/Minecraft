package com.nocturna.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Piezas de la pantalla de ajustes (tarjetas, interruptores, listas para elegir) dibujadas en
 * código con los colores de la app. Las comparten los ajustes normales y los de temporada.
 */
final class Kit {
    private Kit() {}

    /** Traduce un texto fijo de la interfaz (en esta app, todo está en español). */
    static String tr(String s) {
        return s;
    }

    static int dp(Activity a, float v) {
        return Ui.dp(a, v);
    }

    // ------------------------------------------------------------------ tarjetas

    static void section(Activity a, LinearLayout col, String title) {
        TextView t = Ui.text(a, title.toUpperCase(), 12, Ui.ACCENT2, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(a, 6), dp(a, 20), 0, dp(a, 8));
        col.addView(t);
    }

    static LinearLayout card(Activity a, String title, String sub) {
        LinearLayout r = Ui.row(a);
        r.setPadding(dp(a, 16), dp(a, 13), dp(a, 12), dp(a, 13));
        r.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(a, 16), dp(a, 1), Ui.STROKE), Ui.round(Color.WHITE, dp(a, 16))));
        LinearLayout texts = Ui.column(a);
        texts.addView(Ui.text(a, title, 15, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(a, sub, 12.5f, Ui.SUB, false);
            s.setPadding(0, dp(a, 2), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, Ui.weight(1));
        return r;
    }

    static void add(Activity a, LinearLayout col, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(a, 8));
        col.addView(card, lp);
    }

    static ImageView chevron(Activity a) {
        ImageView ch = new ImageView(a);
        ch.setImageDrawable(Ui.icon(a, R.drawable.ic_chevron, 0xFF4A5670));
        return ch;
    }

    /** Fila que abre algo al tocarla (o solo informa, si no hace nada). */
    static LinearLayout row(Activity a, LinearLayout col, String title, String sub, Runnable action) {
        LinearLayout r = card(a, title, sub);
        if (action != null) {
            r.addView(chevron(a), new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
            r.setOnClickListener(v -> action.run());
        } else {
            r.setBackground(Ui.roundStroke(Ui.SURFACE, dp(a, 16), dp(a, 1), Ui.STROKE));
        }
        add(a, col, r);
        return r;
    }

    static Switch makeSwitch(Activity a, boolean on, int color) {
        Switch sw = new Switch(a);
        sw.setChecked(on);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{color, 0xFF3A4458}));
        return sw;
    }

    /** Fila con interruptor: tocar cualquier parte lo cambia. */
    static Switch toggle(Activity a, LinearLayout col, String title, String sub, boolean on, Consumer<Boolean> set) {
        LinearLayout r = card(a, title, sub);
        Switch sw = makeSwitch(a, on, Ui.ACCENT);
        sw.setOnCheckedChangeListener((b, c) -> set.accept(c));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMargins(dp(a, 10), 0, 0, 0);
        r.addView(sw, sl);
        r.setOnClickListener(v -> sw.toggle());
        add(a, col, r);
        return sw;
    }

    /**
     * Fila con interruptor y menú: el interruptor enciende o apaga, y tocar el resto de la fila
     * abre el menú. Una rayita separa las dos zonas para que se note que son distintas.
     */
    static Switch toggleWithMenu(Activity a, LinearLayout col, String title, String sub, boolean on,
                                 Consumer<Boolean> set, Runnable menu) {
        LinearLayout r = card(a, title, sub);
        r.addView(chevron(a), new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18)));
        View bar = new View(a);
        bar.setBackgroundColor(Ui.STROKE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(a, 1), dp(a, 30));
        bl.setMargins(dp(a, 10), 0, dp(a, 4), 0);
        r.addView(bar, bl);
        Switch sw = makeSwitch(a, on, Ui.ACCENT);
        sw.setOnCheckedChangeListener((b, c) -> set.accept(c));
        sw.setPadding(dp(a, 6), dp(a, 6), 0, dp(a, 6));
        r.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        r.setOnClickListener(v -> menu.run());
        add(a, col, r);
        return sw;
    }

    // ------------------------------------------------------------------ hojas que suben desde abajo

    static Dialog sheet(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.6f);
        }
        return d;
    }

    /** Arma el contenido de la hoja: fondo redondeado arriba, rayita para arrastrar, título y lista. */
    static LinearLayout sheetBody(Activity a, Dialog d, String title, String sub) {
        LinearLayout box = Ui.column(a);
        box.setPadding(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 22));
        GradientDrawable bg = Ui.round(Ui.SURFACE, 0);
        float r = dp(a, 26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(a, 1), Ui.STROKE);
        box.setBackground(bg);
        View handle = new View(a);
        handle.setBackground(Ui.round(0xFF33405A, dp(a, 3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(a, 40), dp(a, 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.setMargins(0, 0, 0, dp(a, 14));
        box.addView(handle, hl);
        TextView t = Ui.text(a, title, 20, Ui.TEXT, true);
        t.setPadding(dp(a, 4), 0, 0, sub == null ? dp(a, 12) : dp(a, 2));
        box.addView(t);
        if (sub != null) {
            TextView s = Ui.text(a, sub, 13, Ui.SUB, false);
            s.setPadding(dp(a, 4), 0, 0, dp(a, 12));
            box.addView(s);
        }
        ScrollView sv = new ScrollView(a);
        LinearLayout list = Ui.column(a);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // que la hoja no tape toda la pantalla si la lista es larga
        int max = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        sv.post(() -> {
            if (sv.getHeight() > max) sv.setLayoutParams(new LinearLayout.LayoutParams(-1, max));
        });
        return list;
    }

    /**
     * Una opción de una lista: insignia redonda (con emoji o ícono), título, texto chico y una
     * palomita si es la elegida. Si {@code tag} no es null se muestra como etiqueta (p. ej.
     * «Próximamente») y la opción no se puede elegir.
     */
    static LinearLayout option(Activity a, LinearLayout list, String badge, int c1, int c2, String title, String sub,
                               boolean selected, String tag, Runnable pick) {
        LinearLayout r = Ui.row(a);
        r.setPadding(dp(a, 12), dp(a, 11), dp(a, 12), dp(a, 11));
        int bg = selected ? Ui.SURFACE3 : Ui.SURFACE2;
        GradientDrawable shape = Ui.round(bg, dp(a, 16));
        if (selected) shape.setStroke(dp(a, 2), c1);
        r.setBackground(tag == null ? Ui.ripple(shape, Ui.round(Color.WHITE, dp(a, 16))) : shape);
        FrameLayout b = new FrameLayout(a);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        circle.setShape(GradientDrawable.OVAL);
        b.setBackground(circle);
        TextView e = Ui.text(a, badge, 19, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        b.addView(e, new FrameLayout.LayoutParams(-1, -1));
        r.addView(b, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));
        LinearLayout texts = Ui.column(a);
        texts.setPadding(dp(a, 14), 0, dp(a, 8), 0);
        texts.addView(Ui.text(a, title, 15, tag == null ? Ui.TEXT : Ui.SUB, true));
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(a, sub, 12, Ui.SUB, false);
            s.setPadding(0, dp(a, 2), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, Ui.weight(1));
        if (tag != null) {
            TextView t = Ui.text(a, tag, 10.5f, Ui.WARN, true);
            t.setPadding(dp(a, 8), dp(a, 3), dp(a, 8), dp(a, 3));
            t.setBackground(Ui.round(0x22FFB547, dp(a, 10)));
            r.addView(t);
            r.setAlpha(0.75f);
        } else if (selected) {
            ImageView ok = new ImageView(a);
            ok.setImageDrawable(Ui.icon(a, R.drawable.ic_check, c1));
            r.addView(ok, new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22)));
        }
        if (tag == null) r.setOnClickListener(v -> pick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(a, 8));
        list.addView(r, lp);
        return r;
    }

    /** Lista sencilla para elegir una opción (reemplaza los diálogos viejos de radio). */
    static void choose(Activity a, String title, String sub, String[] names, String[] subs, int selected, IntConsumer pick) {
        Dialog d = sheet(a);
        LinearLayout list = sheetBody(a, d, title, sub);
        for (int i = 0; i < names.length; i++) {
            final int w = i;
            String letter = names[i].isEmpty() ? "•" : names[i].substring(0, 1).toUpperCase();
            option(a, list, letter, Ui.ACCENT, Ui.ACCENT2, names[i], subs == null ? null : subs[i], i == selected, null, () -> {
                d.dismiss();
                pick.accept(w);
            });
        }
        d.show();
    }

    // ------------------------------------------------------------------ pantalla de ajustes completa

    /** Una categoría de los ajustes: su fila en la lista y lo que muestra al abrirla. */
    static final class Page {
        final String title, sub;
        final int icon, c1, c2;
        final PageFill fill;

        Page(String title, String sub, int icon, int c1, int c2, PageFill fill) {
            this.title = title;
            this.sub = sub;
            this.icon = icon;
            this.c1 = c1;
            this.c2 = c2;
            this.fill = fill;
        }
    }

    interface PageFill {
        /** Llena la columna; {@code refresh} la vuelve a dibujar después de un cambio. */
        void fill(LinearLayout col, Runnable refresh);
    }

    /**
     * Ajustes a pantalla completa: primero la lista de categorías, y al tocar una, sus opciones.
     * «Atrás» vuelve a la lista y, desde la lista, cierra.
     */
    static Dialog settings(Activity a, String title, List<Page> pages) {
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window win = d.getWindow();
        if (win != null) {
            win.setStatusBarColor(Ui.BG);
            win.setNavigationBarColor(Ui.BG);
        }
        LinearLayout root = Ui.column(a);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        LinearLayout head = Ui.row(a);
        head.setPadding(dp(a, 4), dp(a, 8), dp(a, 12), dp(a, 6));
        TextView name = Ui.text(a, title, 21, Ui.TEXT, true);
        ScrollView sv = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        col.setPadding(dp(a, 14), dp(a, 4), dp(a, 14), dp(a, 32));
        sv.addView(col);
        int[] page = {-1};
        Runnable[] home = new Runnable[1];
        home[0] = () -> {
            page[0] = -1;
            name.setText(title);
            col.removeAllViews();
            for (int i = 0; i < pages.size(); i++) {
                final int c = i;
                Page p = pages.get(i);
                LinearLayout r = Ui.row(a);
                r.setPadding(dp(a, 14), dp(a, 13), dp(a, 12), dp(a, 13));
                r.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(a, 16), dp(a, 1), Ui.STROKE), Ui.round(Color.WHITE, dp(a, 16))));
                FrameLayout badge = new FrameLayout(a);
                badge.setBackground(Ui.gradient(dp(a, 12), p.c1, p.c2));
                ImageView ic = new ImageView(a);
                ic.setImageDrawable(Ui.icon(a, p.icon, 0xFF0A0E16));
                badge.addView(ic, new FrameLayout.LayoutParams(dp(a, 22), dp(a, 22), Gravity.CENTER));
                r.addView(badge, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
                LinearLayout texts = Ui.column(a);
                texts.setPadding(dp(a, 14), 0, 0, 0);
                texts.addView(Ui.text(a, p.title, 15.5f, Ui.TEXT, true));
                TextView s = Ui.text(a, p.sub, 12.5f, Ui.SUB, false);
                s.setPadding(0, dp(a, 2), 0, 0);
                texts.addView(s);
                r.addView(texts, Ui.weight(1));
                r.addView(chevron(a), new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));
                r.setOnClickListener(v -> {
                    page[0] = c;
                    name.setText(p.title);
                    Runnable[] refresh = new Runnable[1];
                    refresh[0] = () -> {
                        col.removeAllViews();
                        p.fill.fill(col, refresh[0]);
                    };
                    refresh[0].run();
                    sv.scrollTo(0, 0);
                });
                add(a, col, r);
            }
            add(a, col, Studio.row(a, null));
            sv.scrollTo(0, 0);
        };
        head.addView(Ui.iconButton(a, R.drawable.ic_back, 46, v -> {
            if (page[0] >= 0) home[0].run();
            else d.dismiss();
        }));
        head.addView(name, Ui.weight(1));
        root.addView(head);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(root);
        d.setOnKeyListener((dd, key, ev) -> {
            if (key == KeyEvent.KEYCODE_BACK && ev.getAction() == KeyEvent.ACTION_UP && page[0] >= 0) {
                home[0].run();
                return true;
            }
            return false;
        });
        home[0].run();
        d.show();
        return d;
    }
}
