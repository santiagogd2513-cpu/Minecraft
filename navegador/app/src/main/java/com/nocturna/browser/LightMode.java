package com.nocturna.browser;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * El «modo claro» experimental de Nocturna. Se ve cómo la interfaz se va aclarando por partes: primero
 * el fondo, luego las letras, los interruptores y los íconos… y justo en el 99 % la GPU «no aguanta»,
 * sale el error y todo vuelve a la oscuridad. Nada queda cambiado: se guarda cómo estaba cada cosa y
 * se devuelve tal cual.
 */
final class LightMode {
    private static final int WHITE = 0xFFF4F6FA, INK = 0xFF15181E, TRACK = 0xFFCDD3DD, THUMB = 0xFF3D8BFF, ICON = 0xFF3A4352;
    private static final ArgbEvaluator MIX = new ArgbEvaluator();
    private static boolean running;

    private LightMode() {}

    /** Lo que había antes en cada vista, para devolverlo. */
    private static final class Saved {
        final View v;
        ColorStateList bgTint, text, hint, thumb, track;
        PorterDuff.Mode bgMode;
        ColorFilter filter;
        int text0, thumb0 = Color.WHITE, track0 = 0xFF3A4458;
        boolean hadBg;

        Saved(View v) {
            this.v = v;
        }
    }

    static void run(Activity a, WebView web) {
        if (running) return;
        running = true;
        Window win = a.getWindow();
        View decor = win.getDecorView();
        List<Saved> saved = new ArrayList<>();
        collect(decor, saved);
        int status0 = win.getStatusBarColor(), nav0 = win.getNavigationBarColor();
        int ui0 = decor.getSystemUiVisibility();

        // el contador de arriba y una capa que no deja tocar nada mientras tanto
        FrameLayout content = a.findViewById(android.R.id.content);
        FrameLayout shield = new FrameLayout(a);
        shield.setClickable(true);
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 12));
        pill.setBackground(Ui.round(0xEE141824, dp(a, 18)));
        TextView label = Ui.text(a, "☀️  Activando modo claro…  0 %", 14, Color.WHITE, true);
        pill.addView(label);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(0xFFFFC94A));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(a, 6));
        bl.topMargin = dp(a, 8);
        pill.addView(bar, bl);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(a, 280), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pl.topMargin = dp(a, 70);
        shield.addView(pill, pl);
        content.addView(shield, new FrameLayout.LayoutParams(-1, -1));

        float[] lastWeb = {-1};
        ValueAnimator up = ValueAnimator.ofFloat(0f, 0.99f);
        up.setDuration(10_000);
        up.setInterpolator(new AccelerateDecelerateInterpolator());
        up.addUpdateListener(an -> {
            float p = (float) an.getAnimatedValue();
            apply(saved, win, decor, status0, nav0, ui0, p);
            int pct = Math.min(99, Math.round(p * 100));
            label.setText(step(pct) + pct + " %");
            bar.setProgress(pct);
            float bg = clamp(p / 0.35f);
            if (web != null && Math.abs(bg - lastWeb[0]) > 0.04f) {
                lastWeb[0] = bg;
                webFilter(web, bg);
            }
        });
        up.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // se queda pegado en el 99 %… y la GPU se rinde
                label.setText("☀️  Aplicando los últimos detalles…  99 %");
                decor.postDelayed(() -> fail(a, saved, win, decor, status0, nav0, ui0, web, content, shield), 2200);
            }
        });
        up.start();
    }

    private static String step(int pct) {
        if (pct < 33) return "☀️  Aclarando el fondo…  ";
        if (pct < 58) return "✏️  Pintando las letras…  ";
        if (pct < 75) return "🔘  Ajustando los interruptores…  ";
        return "🎨  Retocando los íconos…  ";
    }

    private static void fail(Activity a, List<Saved> saved, Window win, View decor, int status0, int nav0, int ui0,
                             WebView web, FrameLayout content, FrameLayout shield) {
        ValueAnimator down = ValueAnimator.ofFloat(0.99f, 0f);
        down.setDuration(450);
        down.addUpdateListener(an -> apply(saved, win, decor, status0, nav0, ui0, (float) an.getAnimatedValue()));
        down.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                restore(saved, win, decor, status0, nav0, ui0);
                if (web != null) webFilter(web, 0);
                content.removeView(shield);
                running = false;
            }
        });
        down.start();
        new AlertDialog.Builder(a, R.style.Theme_Nocturna_Dialog)
                .setTitle("❌ Error de la GPU")
                .setMessage("La GPU no pudo soportar este proceso.\n\n"
                        + "Por favor, espere a que la función esté terminada o consiga un dispositivo más potente.")
                .setPositiveButton("Entendido 🌙", null)
                .setCancelable(false)
                .show();
    }

    // ------------------------------------------------------------------ pintar

    private static void collect(View v, List<Saved> out) {
        Saved s = new Saved(v);
        s.hadBg = v.getBackground() != null;
        s.bgTint = v.getBackgroundTintList();
        s.bgMode = v.getBackgroundTintMode();
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            s.text = t.getTextColors();
            s.hint = t.getHintTextColors();
            s.text0 = t.getCurrentTextColor();
        }
        if (v instanceof Switch) {
            Switch sw = (Switch) v;
            s.thumb = sw.getThumbTintList();
            s.track = sw.getTrackTintList();
            if (s.thumb != null) s.thumb0 = s.thumb.getColorForState(sw.getDrawableState(), s.thumb.getDefaultColor());
            if (s.track != null) s.track0 = s.track.getColorForState(sw.getDrawableState(), s.track.getDefaultColor());
        }
        if (v instanceof ImageView) s.filter = ((ImageView) v).getColorFilter();
        out.add(s);
        // las páginas web no son de la interfaz: se aclaran aparte, con un filtro
        if (v instanceof ViewGroup && !(v instanceof WebView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    private static void apply(List<Saved> saved, Window win, View decor, int status0, int nav0, int ui0, float p) {
        float bg = clamp(p / 0.35f), text = clamp((p - 0.3f) / 0.3f), sw = clamp((p - 0.55f) / 0.2f), icon = clamp((p - 0.7f) / 0.25f);
        int veil = Color.argb(Math.round(bg * 255), Color.red(WHITE), Color.green(WHITE), Color.blue(WHITE));
        for (Saved s : saved) {
            View v = s.v;
            if (s.hadBg || v == decor) {
                v.setBackgroundTintMode(PorterDuff.Mode.SRC_ATOP);
                v.setBackgroundTintList(ColorStateList.valueOf(veil));
            }
            if (v instanceof Switch) {
                Switch w = (Switch) v;
                w.setThumbTintList(ColorStateList.valueOf(mix(s.thumb0, THUMB, sw)));
                w.setTrackTintList(ColorStateList.valueOf(mix(s.track0, TRACK, sw)));
            }
            if (v instanceof TextView && s.text != null) {
                ((TextView) v).setTextColor(mix(s.text0, INK, text));
            }
            if (v instanceof ImageView) {
                int c = Color.argb(Math.round(icon * 255), Color.red(ICON), Color.green(ICON), Color.blue(ICON));
                if (icon <= 0) ((ImageView) v).setColorFilter(s.filter);
                else ((ImageView) v).setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_ATOP));
            }
        }
        win.setStatusBarColor(mix(status0, WHITE, bg));
        win.setNavigationBarColor(mix(nav0, WHITE, bg));
        decor.setSystemUiVisibility(bg > 0.5f ? ui0 | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : ui0);
    }

    private static void restore(List<Saved> saved, Window win, View decor, int status0, int nav0, int ui0) {
        for (Saved s : saved) {
            View v = s.v;
            v.setBackgroundTintList(s.bgTint);
            v.setBackgroundTintMode(s.bgMode);
            if (v instanceof TextView && s.text != null) {
                ((TextView) v).setTextColor(s.text);
                if (s.hint != null) ((TextView) v).setHintTextColor(s.hint);
            }
            if (v instanceof Switch) {
                ((Switch) v).setThumbTintList(s.thumb);
                ((Switch) v).setTrackTintList(s.track);
            }
            if (v instanceof ImageView) ((ImageView) v).setColorFilter(s.filter);
        }
        win.setStatusBarColor(status0);
        win.setNavigationBarColor(nav0);
        decor.setSystemUiVisibility(ui0);
    }

    /** La página de inicio y las páginas abiertas también se aclaran (y vuelven), con un filtro. */
    private static void webFilter(WebView web, float t) {
        // sobre el body (el modo oscuro de las páginas usa el filtro del html), y se guarda el que tenía
        String js = t <= 0
                ? "(function(){var b=document.body;if(!b||window.__nlm===undefined)return;b.style.removeProperty('filter');"
                + "b.style.filter=window.__nlm;delete window.__nlm;})()"
                : "(function(){var b=document.body;if(!b)return;if(window.__nlm===undefined)window.__nlm=b.style.filter||'';"
                + "b.style.setProperty('filter','invert(" + (t * 0.9f) + ") hue-rotate(" + (t * 180) + "deg)','important');})()";
        web.evaluateJavascript(js, null);
    }

    private static int mix(int a, int b, float t) {
        return (Integer) MIX.evaluate(t, a, b);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
