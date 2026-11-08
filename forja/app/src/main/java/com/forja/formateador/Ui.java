package com.forja.formateador;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colores y piezas visuales de la app (todo se dibuja en código, sin XML de diseño). */
final class Ui {
    static final int BG = 0xFF0A0A0E;
    static final int SURFACE = 0xFF16161E;
    static final int SURFACE2 = 0xFF1F1F2A;
    static final int SURFACE3 = 0xFF262E42;
    static final int STROKE = 0xFF2A2A36;
    static final int ACCENT = 0xFFFF8A3D;
    static final int ACCENT2 = 0xFFFFC857;
    static final int VIOLET = 0xFFFF3D6E;
    static final int TEXT = 0xFFF2EEE8;
    static final int SUB = 0xFF9A96A6;
    static final int GOOD = 0xFF35D08A;
    static final int WARN = 0xFFFFB547;
    static final int BAD = 0xFFFF5D6C;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable roundStroke(int color, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = round(color, radiusPx);
        g.setStroke(strokePx, strokeColor);
        return g;
    }

    static GradientDrawable gradient(float radiusPx, int... colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Fondo con efecto de onda al tocar. */
    static Drawable ripple(Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FF8A3D), content, mask);
    }

    static Drawable rippleRound(int color, float radiusPx) {
        return ripple(round(color, radiusPx), round(Color.WHITE, radiusPx));
    }

    static Drawable icon(Context c, int res, int tint) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(tint);
        return d;
    }

    static ImageView iconButton(Context c, int res, int sizeDp, View.OnClickListener l) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(icon(c, res, TEXT));
        int p = dp(c, 10);
        v.setPadding(p, p, p, p);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setBackground(ripple(null, round(Color.WHITE, dp(c, 24))));
        v.setOnClickListener(l);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    static String size(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        double k = bytes / 1024.0;
        if (k < 1024) return String.format(java.util.Locale.US, "%.0f KB", k);
        double m = k / 1024.0;
        if (m < 1024) return String.format(java.util.Locale.US, m < 10 ? "%.1f MB" : "%.0f MB", m);
        return String.format(java.util.Locale.US, "%.2f GB", m / 1024.0);
    }

    static String speed(double bytesPerSec) {
        double m = bytesPerSec / (1024.0 * 1024.0);
        if (m >= 1) return String.format(java.util.Locale.US, "%.1f MB/s", m);
        return String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024.0);
    }

    static String eta(long seconds) {
        if (seconds < 0 || seconds > 360000) return "--";
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return (seconds / 60) + " min " + (seconds % 60) + " s";
        return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
    }

    static String date(long t) {
        if (t <= 0) return "";
        return new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", new java.util.Locale("es")).format(new java.util.Date(t));
    }
}
