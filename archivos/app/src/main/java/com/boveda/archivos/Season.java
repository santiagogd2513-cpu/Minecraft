package com.boveda.archivos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Temas e íconos de temporada.
 *
 * <p>El calendario: Navidad del 1 de diciembre al 6 de enero (salvo el 7 de diciembre, que es el
 * Día de las Velitas), San Valentín del 1 al 14 de febrero, Amor y Amistad todo septiembre y
 * Halloween todo octubre.
 *
 * <p>Dos interruptores, los dos encendidos de fábrica: «Temas de temporada» y «Íconos de
 * temporada». Cada uno tiene su menú: en «Automático» sigue la fecha, y también se puede dejar
 * fijo un tema o un ícono para usarlo todo el año.
 */
final class Season {
    private Season() {}

    static final int NONE = -1;
    static final int AUTO = -2;
    /** Solo para los íconos: el de siempre. */
    static final int ORIGINAL = -3;

    static final int NAVIDAD = 0, VELITAS = 1, VALENTIN = 2, AMISTAD = 3, HALLOWEEN = 4;
    static final int COUNT = 5;

    static final String[] EMOJI = {"🎄", "🕯️", "💘", "🤝", "🎃"};
    static final String[] NAME = {"Navidad", "Día de las Velitas", "San Valentín", "Amor y Amistad", "Halloween"};
    static final String[] DATES = {"Del 1 de diciembre al 6 de enero", "7 de diciembre", "Del 1 al 14 de febrero",
            "Todo septiembre", "Todo octubre"};
    /** Los dos colores de cada temporada (para insignias y degradados). */
    static final int[][] COLORS = {
            {0xFFE63946, 0xFF2DC653},
            {0xFFFFC23D, 0xFFFF7B00},
            {0xFFFF4D8D, 0xFFC9184A},
            {0xFFFFC857, 0xFFFF6B8A},
            {0xFFFF7A00, 0xFF7B2CBF},
    };
    /**
     * Si el tema de cada temporada ya trae decoraciones. Mientras sea false, el tema se puede
     * elegir pero todavía no cambia nada en pantalla.
     */
    static final boolean[] THEME_READY = {true, true, false, false, false};
    /** Si ya está dibujado el ícono de cada temporada. */
    static final boolean[] ICON_READY = {true, true, false, false, false};

    /** Lo avisa la app para volver a dibujar su pantalla cuando cambia el tema. */
    static Runnable listener;

    private static void changed(Activity a) {
        SeasonFx.attach(a);
        if (listener != null) listener.run();
        checkIcon(a);
    }

    // ------------------------------------------------------------------ preferencias

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("temporada", Context.MODE_PRIVATE);
    }

    static boolean themesOn(Context c) {
        return p(c).getBoolean("temas", true);
    }

    static void setThemesOn(Context c, boolean on) {
        p(c).edit().putBoolean("temas", on).apply();
    }

    /** {@link #AUTO} o una temporada fija. */
    static int theme(Context c) {
        int t = p(c).getInt("tema", AUTO);
        return t == AUTO || (t >= 0 && t < COUNT) ? t : AUTO;
    }

    static void setTheme(Context c, int t) {
        p(c).edit().putInt("tema", t).apply();
    }

    static boolean iconsOn(Context c) {
        return p(c).getBoolean("iconos", true);
    }

    static void setIconsOn(Context c, boolean on) {
        p(c).edit().putBoolean("iconos", on).apply();
    }

    /** {@link #AUTO}, {@link #ORIGINAL} o el ícono de una temporada. */
    static int icon(Context c) {
        int i = p(c).getInt("icono", AUTO);
        return i == AUTO || i == ORIGINAL || (i >= 0 && i < COUNT) ? i : AUTO;
    }

    static void setIcon(Context c, int i) {
        p(c).edit().putInt("icono", i).apply();
    }

    // ------------------------------------------------------------------ calendario

    /** La temporada de una fecha, o {@link #NONE}. */
    static int byDate(Calendar c) {
        int m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        if (m == Calendar.DECEMBER && d == 7) return VELITAS;
        if (m == Calendar.DECEMBER || (m == Calendar.JANUARY && d <= 6)) return NAVIDAD;
        if (m == Calendar.FEBRUARY && d <= 14) return VALENTIN;
        if (m == Calendar.SEPTEMBER) return AMISTAD;
        if (m == Calendar.OCTOBER) return HALLOWEEN;
        return NONE;
    }

    static int today() {
        return byDate(Calendar.getInstance());
    }

    /** El tema que se ve ahora (o {@link #NONE}). */
    static int current(Context c) {
        if (!themesOn(c)) return NONE;
        int t = theme(c);
        return t == AUTO ? today() : t;
    }

    /** El ícono que toca ahora: una temporada, o {@link #ORIGINAL}. */
    static int currentIcon(Context c) {
        if (!iconsOn(c)) return ORIGINAL;
        int i = icon(c);
        if (i == AUTO) {
            // «combina con la temporada»: el mismo del tema, o el de la fecha si los temas están apagados
            int t = themesOn(c) ? current(c) : today();
            i = t == NONE ? ORIGINAL : t;
        }
        return i >= 0 && !ICON_READY[i] ? ORIGINAL : i;
    }

    /** Cuántos días faltan para la próxima temporada y cuál es: {días, temporada}. */
    static int[] next() {
        Calendar c = Calendar.getInstance();
        int now = byDate(c);
        for (int i = 1; i <= 370; i++) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            int s = byDate(c);
            if (s != NONE && s != now) return new int[]{i, s};
        }
        return new int[]{0, NAVIDAD};
    }

    // ------------------------------------------------------------------ ajustes

    static String tr(String s) {
        return Kit.tr(s);
    }

    private static String themeLabel(Context c) {
        int t = theme(c);
        if (t == AUTO) {
            int now = today();
            return tr("Automático (según la fecha)") + (now == NONE ? "" : " · " + EMOJI[now] + " " + tr(NAME[now]));
        }
        return EMOJI[t] + " " + tr(NAME[t]) + " · " + tr("todo el año");
    }

    private static String iconLabel(Context c) {
        int i = icon(c);
        if (i == AUTO) return tr("Automático · combina con la temporada");
        if (i == ORIGINAL) return tr("El original");
        return EMOJI[i] + " " + tr(NAME[i]);
    }

    /** Agrega la sección «Temas de temporada» a una pantalla de ajustes. */
    static void settings(Activity a, LinearLayout col, Runnable refresh) {
        col.addView(banner(a));
        Kit.section(a, col, tr("Temas"));
        Kit.toggleWithMenu(a, col, tr("Temas de temporada"), themesOn(a) ? themeLabel(a) : tr("Apagados: siempre el diseño de siempre"),
                themesOn(a), on -> {
                    setThemesOn(a, on);
                    changed(a);
                    refresh.run();
                }, () -> chooseTheme(a, refresh));
        Kit.section(a, col, tr("Íconos"));
        Kit.toggleWithMenu(a, col, tr("Íconos de temporada"), iconsOn(a) ? iconLabel(a) : tr("Apagados: siempre el ícono original"),
                iconsOn(a), on -> {
                    setIconsOn(a, on);
                    iconChanged(a, refresh);
                }, () -> chooseIcon(a, refresh));
        TextView tip = Ui.text(a, tr("Toca el nombre para elegir; el interruptor enciende o apaga."), 12, Ui.SUB, false);
        tip.setPadding(Kit.dp(a, 6), Kit.dp(a, 4), Kit.dp(a, 6), 0);
        col.addView(tip);
        boolean ready = true;
        for (boolean b : THEME_READY) ready &= b;
        if (!ready) {
            TextView soon = Ui.text(a, tr("🎨 Navidad y el Día de las Velitas ya tienen sus decoraciones. Las de San Valentín, Amor y Amistad y Halloween llegan en las próximas actualizaciones."),
                    12.5f, Ui.WARN, false);
            soon.setPadding(Kit.dp(a, 14), Kit.dp(a, 12), Kit.dp(a, 14), Kit.dp(a, 12));
            soon.setBackground(Ui.round(0x1AFFB547, Kit.dp(a, 14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, Kit.dp(a, 16), 0, 0);
            col.addView(soon, lp);
        }
    }

    /** Tarjeta de arriba: qué temporada se ve ahora, o cuánto falta para la próxima. */
    private static LinearLayout banner(Activity a) {
        int now = current(a);
        LinearLayout b = Ui.row(a);
        b.setPadding(Kit.dp(a, 16), Kit.dp(a, 16), Kit.dp(a, 16), Kit.dp(a, 16));
        int c1, c2;
        String big, small;
        if (now != NONE) {
            c1 = COLORS[now][0];
            c2 = COLORS[now][1];
            big = EMOJI[now];
            small = tr("Tema activo");
            String title = tr(NAME[now]);
            b.setBackground(Ui.gradient(Kit.dp(a, 20), darken(c1), darken(c2)));
            addBannerTexts(a, b, big, small, title, theme(a) == AUTO ? tr(DATES[now]) : tr("Elegido para todo el año"));
        } else {
            int[] nx = next();
            c1 = COLORS[nx[1]][0];
            b.setBackground(Ui.roundStroke(Ui.SURFACE, Kit.dp(a, 20), Kit.dp(a, 1), Ui.STROKE));
            String when = nx[0] == 1 ? tr("Empieza mañana") : "⏳ " + nx[0] + " " + tr("días para que empiece");
            addBannerTexts(a, b, themesOn(a) ? "📅" : "💤", themesOn(a) ? tr("Ahora no hay temporada") : tr("Temas apagados"),
                    tr("Próxima: ") + EMOJI[nx[1]] + " " + tr(NAME[nx[1]]), when);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Kit.dp(a, 8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private static void addBannerTexts(Activity a, LinearLayout b, String emoji, String small, String title, String sub) {
        TextView e = Ui.text(a, emoji, 34, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        b.addView(e, new LinearLayout.LayoutParams(Kit.dp(a, 54), -2));
        LinearLayout t = Ui.column(a);
        t.setPadding(Kit.dp(a, 12), 0, 0, 0);
        TextView s = Ui.text(a, small.toUpperCase(), 11, 0xCCFFFFFF, true);
        s.setLetterSpacing(0.08f);
        t.addView(s);
        t.addView(Ui.text(a, title, 19, Color.WHITE, true));
        t.addView(Ui.text(a, sub, 12.5f, 0xCCFFFFFF, false));
        b.addView(t, Ui.weight(1));
    }

    private static int darken(int c) {
        return Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10);
    }

    /** Menú «Elegir tema»: Automático y luego cada temporada, para dejarla fija todo el año. */
    static void chooseTheme(Activity a, Runnable refresh) {
        Dialog d = Kit.sheet(a);
        LinearLayout list = Kit.sheetBody(a, d, tr("Elegir tema"), tr("En «Automático» cambia solo según la fecha"));
        int sel = themesOn(a) ? theme(a) : NONE;
        int now = today();
        Kit.option(a, list, "📅", Ui.ACCENT, Ui.ACCENT2, tr("Automático (según la fecha)"),
                now == NONE ? tr("Ahora no hay temporada") : tr("Ahora: ") + EMOJI[now] + " " + tr(NAME[now]),
                sel == AUTO, null, () -> pickTheme(a, d, AUTO, refresh));
        for (int i = 0; i < COUNT; i++) {
            final int s = i;
            Kit.option(a, list, EMOJI[i], COLORS[i][0], COLORS[i][1], tr(NAME[i]), tr(DATES[i]) + " · " + tr("o todo el año"),
                    sel == i, THEME_READY[i] ? null : tr("Próximamente"), () -> pickTheme(a, d, s, refresh));
        }
        d.show();
    }

    private static void pickTheme(Activity a, Dialog d, int t, Runnable refresh) {
        d.dismiss();
        setTheme(a, t);
        setThemesOn(a, true); // elegir un tema lo enciende
        changed(a);
        refresh.run();
    }

    /** Menú de íconos: Automático («Combina con la temporada»), el original y los de cada temporada. */
    static void chooseIcon(Activity a, Runnable refresh) {
        Dialog d = Kit.sheet(a);
        LinearLayout list = Kit.sheetBody(a, d, tr("Elegir ícono"), tr("El ícono de la app en la pantalla de inicio"));
        int sel = iconsOn(a) ? icon(a) : ORIGINAL;
        Kit.option(a, list, "✨", Ui.ACCENT, Ui.ACCENT2, tr("Automático"), tr("Combina con la temporada"),
                sel == AUTO, null, () -> pickIcon(a, d, AUTO, refresh));
        Kit.option(a, list, "⭐", Ui.SURFACE3, Ui.STROKE, tr("El original"), tr("El de siempre"),
                sel == ORIGINAL, null, () -> pickIcon(a, d, ORIGINAL, refresh));
        for (int i = 0; i < COUNT; i++) {
            final int s = i;
            Kit.option(a, list, EMOJI[i], COLORS[i][0], COLORS[i][1], tr(NAME[i]), null,
                    sel == i, ICON_READY[i] ? null : tr("Próximamente"), () -> pickIcon(a, d, s, refresh));
        }
        d.show();
    }

    private static void pickIcon(Activity a, Dialog d, int i, Runnable refresh) {
        d.dismiss();
        setIcon(a, i);
        setIconsOn(a, i != ORIGINAL || iconsOn(a));
        iconChanged(a, refresh);
    }

    /**
     * Cambia el ícono de verdad si hace falta. La primera vez avisa que el acceso directo de la
     * pantalla de inicio puede desaparecer (Android lo quita al cambiar de ícono).
     */
    static void iconChanged(Activity a, Runnable refresh) {
        iconChanged(a, refresh, false);
    }

    /**
     * Para llamar en onResume: si la fecha cambió de temporada y el ícono en «Automático» tiene
     * que cambiar, lo deja listo (con el aviso de la primera vez).
     */
    static void checkIcon(Activity a) {
        iconChanged(a, () -> { }, true);
    }

    private static void iconChanged(Activity a, Runnable refresh, boolean quiet) {
        refresh.run();
        if (!Icons.wouldChange(a)) return;
        if (p(a).getBoolean("aviso_icono", false)) {
            Icons.apply(a);
            if (!quiet) android.widget.Toast.makeText(a, tr("El ícono cambia cuando salgas de la app"), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(a, DIALOG_THEME)
                .setTitle(tr("Antes de cambiar el ícono"))
                .setMessage(tr("Al cambiar el ícono, Android puede quitar el acceso directo de la app de tu pantalla de inicio. La app no se borra: la encuentras en el cajón de apps y la vuelves a poner donde quieras.\n\nEste aviso solo sale esta vez."))
                .setPositiveButton(tr("Entendido"), (dd, w) -> {
                    p(a).edit().putBoolean("aviso_icono", true).apply();
                    Icons.apply(a);
                    android.widget.Toast.makeText(a, tr("El ícono cambia cuando salgas de la app"), android.widget.Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private static final int DIALOG_THEME = R.style.Theme_Boveda_Dialog;
}
