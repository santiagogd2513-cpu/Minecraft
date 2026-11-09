package com.forja.formateador;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** La ficha de Forja Estudios: el estudio que hace esta app, y sus dos autores. */
final class Studio {
    static final String NAME = "Forja Estudios";
    static final String AUTHORS = "santiagogd2513-cpu · Claude de Anthropic";
    private static final int ORANGE = 0xFFFF8A3D, GOLD = 0xFFFFC857;

    private Studio() {}

    static void show(Activity a) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(px(dp, 24), px(dp, 36), px(dp, 24), px(dp, 24));
        scroll.addView(col);

        // 1. el estudio, en grande
        ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.ic_studio);
        col.addView(icon, new LinearLayout.LayoutParams(px(dp, 96), px(dp, 96)));
        TextView name = text(a, NAME, 30, Ui.TEXT, true);
        name.setPadding(0, px(dp, 16), 0, 0);
        name.setGravity(Gravity.CENTER);
        col.addView(name);
        TextView tag = text(a, "ESTUDIO INDEPENDIENTE · COLOMBIA", 11.5f, ORANGE, true);
        tag.setLetterSpacing(0.14f);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, px(dp, 6), 0, 0);
        col.addView(tag);

        // 2. qué es el estudio
        TextView about = text(a, "Hacemos apps para Android y mods para Minecraft desde cero: herramientas útiles, "
                + "oscuras y livianas, sin anuncios. Cada versión se prueba en un celular de verdad antes de salir, "
                + "y cada error que aparece se busca hasta arreglarlo.", 15, 0xFFC9D2E0, false);
        about.setGravity(Gravity.CENTER);
        about.setLineSpacing(0, 1.2f);
        about.setPadding(0, px(dp, 18), 0, px(dp, 6));
        col.addView(about);
        TextView projects = text(a, "Bóveda · Nocturna · Yunque · Ventoy no oficial · Arnis no oficial · Arsenal Orbital · Arsenal Atómico",
                12.5f, Ui.SUB, false);
        projects.setGravity(Gravity.CENTER);
        projects.setPadding(0, px(dp, 6), 0, 0);
        col.addView(projects);

        // 3. autores
        TextView h = text(a, "AUTORES", 12, GOLD, true);
        h.setLetterSpacing(0.14f);
        h.setPadding(px(dp, 4), px(dp, 28), 0, px(dp, 10));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        col.addView(h, hl);
        col.addView(author(a, dp, "S", "santiagogd2513-cpu", "Pruebas de cada actualización e identificación de errores",
                0xFF3D8BFF, 0xFF36D1FF), cardParams(dp));
        col.addView(author(a, dp, "C", "Claude de Anthropic", "Código y corrección de errores", 0xFFD97757, 0xFFFFB38A), cardParams(dp));

        TextView close = text(a, "Cerrar", 16, 0xFF1A120C, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, px(dp, 14), 0, px(dp, 14));
        close.setBackground(Ui.gradient(px(dp, 26), ORANGE, GOLD));
        close.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(0, px(dp, 24), 0, 0);
        col.addView(close, cl);

        d.setContentView(scroll, new ViewGroup.LayoutParams(-1, -1));
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(Ui.BG);
            d.getWindow().setNavigationBarColor(Ui.BG);
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.show();
    }

    private static LinearLayout author(Activity a, float dp, String initial, String name, String role, int c1, int c2) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(dp, 14), px(dp, 14), px(dp, 16), px(dp, 14));
        row.setBackground(Ui.round(Ui.SURFACE2, px(dp, 18)));
        TextView av = text(a, initial, 20, 0xFF0A0E16, true);
        av.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        av.setBackground(g);
        row.addView(av, new LinearLayout.LayoutParams(px(dp, 48), px(dp, 48)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(dp, 14), 0, 0, 0);
        texts.addView(text(a, name, 16.5f, Ui.TEXT, true));
        TextView r = text(a, role, 13.5f, Ui.SUB, false);
        r.setPadding(0, px(dp, 3), 0, 0);
        texts.addView(r);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private static LinearLayout.LayoutParams cardParams(float dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, px(dp, 10));
        return p;
    }

    /** Fila para pantallas de «Acerca de»: ícono, el nombre del estudio y los autores en chiquito. */
    static LinearLayout row(Activity a, Runnable onClick) {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(dp, 14), px(dp, 12), px(dp, 14), px(dp, 12));
        row.setBackground(Ui.round(Ui.SURFACE2, px(dp, 16)));
        ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.ic_studio);
        row.addView(icon, new LinearLayout.LayoutParams(px(dp, 40), px(dp, 40)));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(dp, 12), 0, 0, 0);
        texts.addView(text(a, NAME, 16, Ui.TEXT, true));
        texts.addView(text(a, AUTHORS, 12, Ui.SUB, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(a, "›", 22, Ui.SUB, false));
        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
            show(a);
        });
        return row;
    }

    private static TextView text(Activity a, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static int px(float density, float v) {
        return Math.round(v * density);
    }
}
