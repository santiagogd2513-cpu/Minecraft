package com.forja.formateador;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** La política de privacidad y seguridad y los términos y condiciones (textos en assets/legal). */
final class Legal {
    static final String PRIVACY = "privacidad", TERMS = "terminos";

    private Legal() {}

    /** Los dos botones, uno debajo del otro, para poner en «Acerca de». */
    static LinearLayout buttons(Activity a) {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(dp, 20), px(dp, 6), px(dp, 20), 0);
        col.addView(button(a, dp, "🔒  " + "Política de privacidad y seguridad", () -> show(a, PRIVACY)));
        col.addView(button(a, dp, "📄  " + "Términos y condiciones", () -> show(a, TERMS)));
        return col;
    }

    private static TextView button(Activity a, float dp, String s, Runnable r) {
        TextView t = Ui.text(a, s, 15, Ui.TEXT, true);
        t.setPadding(px(dp, 16), px(dp, 14), px(dp, 16), px(dp, 14));
        t.setBackground(Ui.rippleRound(Ui.SURFACE, px(dp, 14)));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(dp, 8), 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private static List<String> read(Activity a, String name) {
        List<String> out = new ArrayList<>();
        String file = "legal/" + name + ".txt";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(a.getAssets().open(file), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) out.add(s);
        } catch (Exception e) {
            out.add(e.toString());
        }
        return out;
    }

    static void show(Activity a, String name) {
        float dp = a.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(a, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        ScrollView sc = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(dp, 22), px(dp, 24), px(dp, 22), px(dp, 24));
        sc.addView(col);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        StringBuilder para = new StringBuilder();
        List<String> lines = read(a, name);
        lines.add("");
        boolean first = true;
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("- ")) {
                if (para.length() > 0) {
                    TextView p = Ui.text(a, para.toString().trim(), 14.5f, 0xFFC9D2E0, false);
                    p.setLineSpacing(0, 1.2f);
                    p.setPadding(0, 0, 0, px(dp, 10));
                    col.addView(p);
                    para.setLength(0);
                }
            }
            if (s.isEmpty()) continue;
            if (s.startsWith("## ")) {
                TextView h = Ui.text(a, s.substring(3), 16.5f, Ui.ACCENT2, true);
                h.setPadding(0, px(dp, 14), 0, px(dp, 6));
                col.addView(h);
            } else if (s.startsWith("# ")) {
                TextView h = Ui.text(a, s.substring(2), 24, Ui.TEXT, true);
                col.addView(h);
            } else if (s.startsWith("- ")) {
                LinearLayout row = new LinearLayout(a);
                row.setPadding(px(dp, 4), 0, 0, px(dp, 7));
                row.addView(Ui.text(a, "•", 14.5f, Ui.ACCENT, true), new LinearLayout.LayoutParams(px(dp, 16), -2));
                TextView t = Ui.text(a, s.substring(2), 14.5f, 0xFFC9D2E0, false);
                t.setLineSpacing(0, 1.2f);
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
                col.addView(row);
            } else if (first) {
                TextView t = Ui.text(a, s, 12.5f, Ui.SUB, false);
                t.setPadding(0, px(dp, 4), 0, px(dp, 14));
                col.addView(t);
            } else {
                para.append(s).append(' ');
            }
            if (!s.startsWith("# ")) first = false;
        }

        TextView close = Ui.text(a, "Cerrar", 16, 0xFF0B0D14, true);
        close.setGravity(Gravity.CENTER);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setPadding(0, px(dp, 15), 0, px(dp, 15));
        close.setBackground(Ui.rippleRound(Ui.ACCENT2, px(dp, 26)));
        close.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(px(dp, 20), px(dp, 8), px(dp, 20), px(dp, 16));
        root.addView(close, cl);

        d.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(Ui.BG);
            d.getWindow().setNavigationBarColor(Ui.BG);
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.show();
    }

    private static int px(float dp, float v) {
        return Math.round(v * dp);
    }
}
