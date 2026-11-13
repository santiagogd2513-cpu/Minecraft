package com.boveda.archivos;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Las pestañas de Bóveda (de 2 a 8, o apagadas), compartidas entre el explorador y MediaFire:
 * cada una recuerda dónde está (una carpeta, el inicio o MediaFire).
 */
final class Tabs {
    static final int MAX = 8;
    /** Cuántas hay ahora (1 si están apagadas). */
    private static int active = 3;

    static int count() {
        return active;
    }

    /** Lee de los ajustes cuántas pestañas usar; si la actual queda fuera, vuelve a la primera. */
    static void configure(Prefs p) {
        active = p.tabsEnabled() ? p.tabCount() : 1;
        if (current >= active) current = 0;
    }
    /** Marca de una pestaña que está en MediaFire. */
    static final String MEDIAFIRE = "/mediafire";

    static final String[] path = new String[MAX];   // null = inicio
    /** Qué sección del Inicio tiene abierta cada pestaña (Favoritas, Nube y red…); null = el Inicio principal. */
    static final String[] home = new String[MAX];
    static int current;
    /** Carpeta de MediaFire abierta en cada pestaña ({clave, nombre} por nivel). */
    @SuppressWarnings("unchecked")
    static final List<String[]>[] mfStack = new List[MAX];
    /** A dónde vuelve cada pestaña al salir de MediaFire con «atrás». */
    static final String[] mfReturn = new String[MAX];

    private Tabs() {
    }

    static boolean isMediaFire(int t) {
        return MEDIAFIRE.equals(path[t]);
    }

    /** Nombre corto para la pestaña (el explorador pone nombres más bonitos a los volúmenes). */
    static String label(String p) {
        if (p == null) return L.t("Inicio");
        if (MEDIAFIRE.equals(p)) return "MediaFire";
        if (p.equals(Paths.ROOT)) return L.t("Memoria interna");
        if (DriveFs.isDrive(p) && p.equals(DriveFs.ROOT)) return "Google Drive";
        if (p.equals(MegaFs.ROOT)) return "MEGA";
        if (p.equals(OneDriveFs.ROOT)) return "OneDrive";
        return Paths.name(p);
    }

    static LinearLayout strip(Context c, TextView[] views, IntConsumer onTap) {
        LinearLayout outer = Ui.row(c);
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 4));
        // hasta 4 caben a lo ancho; con más, la barra se desliza de lado
        boolean scroll = count() > 4;
        if (scroll) {
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(row);
            outer.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        } else {
            outer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        java.util.Arrays.fill(views, null);
        for (int i = 0; i < count(); i++) {
            final int t = i;
            TextView tv = Ui.text(c, "", 12.5f, Ui.SUB, true);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
            tv.setOnClickListener(v -> onTap.accept(t));
            LinearLayout.LayoutParams tl = scroll ? new LinearLayout.LayoutParams(-2, -2) : new LinearLayout.LayoutParams(0, -2, 1);
            if (scroll) tv.setMinWidth(Ui.dp(c, 96));
            if (i > 0) tl.setMargins(Ui.dp(c, 6), 0, 0, 0);
            row.addView(tv, tl);
            views[i] = tv;
        }
        outer.setVisibility(count() > 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        return outer;
    }

    static void paint(Context c, TextView[] views, String[] names) {
        for (int i = 0; i < count(); i++) {
            TextView tv = views[i];
            if (tv == null) continue;
            boolean on = i == current;
            boolean mf = isMediaFire(i);
            tv.setText((i + 1) + " · " + names[i]);
            int accent = mf ? 0xFF1E88FF : Ui.ACCENT;
            tv.setTextColor(on ? (mf ? 0xFFFFFFFF : 0xFF06101F) : Ui.SUB);
            tv.setBackground(on ? Ui.round(accent, Ui.dp(c, 14)) : Ui.roundStroke(Ui.SURFACE, Ui.dp(c, 14), Ui.dp(c, 1), Ui.STROKE));
        }
    }

    /** Contenedor que reconoce el deslizamiento horizontal: onSwipe recibe +1 (siguiente) o -1 (anterior). */
    static final class SwipeFrame extends FrameLayout {
        private float downX, downY;
        private boolean swiping;
        private final int slop;
        private final IntConsumer onSwipe;
        boolean enabled = true;

        SwipeFrame(Context c, IntConsumer onSwipe) {
            super(c);
            this.onSwipe = onSwipe;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!enabled || count() < 2) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    swiping = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 2) {
                        swiping = true;
                        return true;
                    }
                    break;
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!swiping) return super.onTouchEvent(e);
            float dx = e.getX() - downX;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    setTranslationX(dx * 0.35f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    swiping = false;
                    int dir = dx < 0 ? 1 : -1;
                    int target = current + dir;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dx) > Ui.dp(getContext(), 70)
                            && target >= 0 && target < count()) {
                        setTranslationX(0);
                        onSwipe.accept(dir);
                    } else {
                        animate().translationX(0).setDuration(150).start();
                    }
                    break;
            }
            return true;
        }
    }
}
