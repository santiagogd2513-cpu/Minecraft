package com.boveda.archivos;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import java.security.MessageDigest;

/**
 * El sello de autenticidad: la app revisa con qué llave fue firmada. Las versiones oficiales de Forja
 * Estudios están firmadas con una llave que solo tiene el estudio; si alguien copia la app, le mete
 * cosas y la vuelve a firmar, la huella cambia y la app avisa que es una copia modificada.
 */
final class Integrity {
    /** Huella SHA-256 del certificado oficial. */
    private static final String OFFICIAL = "66ffb6f68a3192da46a3657c42d07c0606a9877a1b992cd3409b469014502a8d";
    private static boolean shown;

    private Integrity() {}

    static boolean genuine(Activity a) {
        try {
            PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            // las compilaciones de prueba (versión 1) no se revisan
            if (pi.getLongVersionCode() <= 1) return true;
            if (pi.signingInfo == null) return false;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : pi.signingInfo.getApkContentsSigners()) {
                StringBuilder sb = new StringBuilder();
                for (byte b : md.digest(s.toByteArray())) sb.append(String.format("%02x", b));
                if (sb.toString().equals(OFFICIAL)) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** Si no es la original, avisa a pantalla completa (una vez por sesión). */
    static void check(Activity a) {
        if (shown || genuine(a)) return;
        shown = true;
        float dp = a.getResources().getDisplayMetrics().density;
        android.app.Dialog d = new android.app.Dialog(a, android.R.style.Theme_Material_NoActionBar);
        android.widget.LinearLayout root = new android.widget.LinearLayout(a);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D14);
        root.setFitsSystemWindows(true);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(24 * dp);
        root.setPadding(pad, Math.round(40 * dp), pad, pad);
        // el escudo, centrado y latiendo
        android.widget.TextView icon = text(a, "🛡️", 64, 0xFFFFFFFF, false);
        icon.setGravity(android.view.Gravity.CENTER);
        root.addView(icon, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.animation.ObjectAnimator beat = android.animation.ObjectAnimator.ofPropertyValuesHolder(icon,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f, 1f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f, 1f));
        beat.setDuration(1300);
        beat.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        beat.start();
        android.widget.TextView badge = text(a, L.t("COPIA MODIFICADA"), 14, 0xFF1A0505, true);
        badge.setLetterSpacing(0.14f);
        badge.setPadding(Math.round(14 * dp), Math.round(6 * dp), Math.round(14 * dp), Math.round(6 * dp));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFF5D6C);
        bg.setCornerRadius(99 * dp);
        badge.setBackground(bg);
        android.widget.LinearLayout.LayoutParams bl = new android.widget.LinearLayout.LayoutParams(-2, -2);
        bl.setMargins(0, Math.round(16 * dp), 0, Math.round(14 * dp));
        root.addView(badge, bl);
        android.widget.TextView title = text(a, L.t("Esta no es la app original"), 26, 0xFFFFFFFF, true);
        title.setGravity(android.view.Gravity.CENTER);
        root.addView(title);
        android.widget.TextView msg = text(a, L.t("Esta app no es la versión original de Forja Estudios: alguien la modificó y la volvió a firmar. Puede traer anuncios, virus o robar tus datos.") + "\n\n"
                + L.t("Desinstálala y descarga la original, gratis y sin anuncios, en santiagogd2513-cpu.github.io"), 16, 0xFFB8C2D4, false);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setLineSpacing(0, 1.15f);
        android.widget.LinearLayout.LayoutParams ml = new android.widget.LinearLayout.LayoutParams(-1, 0, 1);
        ml.setMargins(0, Math.round(16 * dp), 0, 0);
        root.addView(msg, ml);
        android.widget.TextView go = text(a, L.t("Descargar la original"), 17, 0xFF1A0505, true);
        go.setGravity(android.view.Gravity.CENTER);
        go.setPadding(0, Math.round(16 * dp), 0, Math.round(16 * dp));
        android.graphics.drawable.GradientDrawable gb = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFF5D6C, 0xFFFF9F43});
        gb.setCornerRadius(99 * dp);
        go.setBackground(gb);
        go.setOnClickListener(v -> {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://santiagogd2513-cpu.github.io/")));
            } catch (Exception ignored) {
            }
        });
        root.addView(go, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.TextView later = text(a, L.t("Seguir de todos modos"), 15, 0xFF8B97AB, false);
        later.setGravity(android.view.Gravity.CENTER);
        later.setPadding(0, Math.round(16 * dp), 0, Math.round(8 * dp));
        later.setOnClickListener(v -> d.dismiss());
        root.addView(later, new android.widget.LinearLayout.LayoutParams(-1, -2));
        // franja de arriba con los colores de la app, moviéndose sin parar
        android.widget.LinearLayout outer = new android.widget.LinearLayout(a);
        outer.setOrientation(android.widget.LinearLayout.VERTICAL);
        outer.setBackgroundColor(0xFF0B0D14);
        outer.setFitsSystemWindows(true);
        root.setFitsSystemWindows(false);
        outer.addView(new Band(a, new int[]{0xFF7FD4FF, 0xFF4A6A8A, 0xFF6FD6FF, 0xFF2A4A6A, 0xFF7FD4FF}), new android.widget.LinearLayout.LayoutParams(-1, Math.round(8 * dp)));
        outer.addView(root, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
        d.setContentView(outer, new android.view.ViewGroup.LayoutParams(-1, -1));
        d.setOnDismissListener(x -> beat.cancel());
        d.setCancelable(false);
        if (d.getWindow() != null) {
            d.getWindow().setStatusBarColor(0xFF0B0D14);
            d.getWindow().setNavigationBarColor(0xFF0B0D14);
            d.getWindow().setLayout(-1, -1);
        }
        d.show();
    }

    private static android.widget.TextView text(Activity a, String s, float sp, int color, boolean bold) {
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Franja con un degradado que se desliza de lado a lado, con un brillo debajo. */
    private static final class Band extends android.view.View {
        private final int[] colors;
        private final android.graphics.Paint p = new android.graphics.Paint();
        private final android.graphics.Matrix m = new android.graphics.Matrix();
        private android.graphics.LinearGradient g;

        Band(android.content.Context c, int[] colors) {
            super(c);
            this.colors = colors;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            g = new android.graphics.LinearGradient(0, 0, Math.max(1, w), 0, colors, null, android.graphics.Shader.TileMode.REPEAT);
            p.setShader(g);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            if (g == null) return;
            float shift = (android.os.SystemClock.uptimeMillis() % 2600) / 2600f * getWidth();
            m.setTranslate(shift, 0);
            g.setLocalMatrix(m);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            postInvalidateOnAnimation();
        }
    }
}
