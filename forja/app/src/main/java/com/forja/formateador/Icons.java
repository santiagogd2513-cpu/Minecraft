package com.forja.formateador;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Cambia el ícono de la app en la pantalla de inicio con activity-alias: «.Inicio» es el de
 * siempre y cada temporada que ya tiene ícono dibujado tiene el suyo. Solo uno queda prendido.
 *
 * <p>El cambio se hace al salir de la app ({@link #flush}), porque en algunos celulares apagar el
 * alias con el que se abrió la app la cierra de golpe.
 */
final class Icons {
    private Icons() {}

    private static final String ORIGINAL = "Inicio";

    private static String alias(int icon) {
        return switch (icon) {
            case Season.NAVIDAD -> "InicioNavidad";
            case Season.VELITAS -> "InicioVelitas";
            default -> ORIGINAL;
        };
    }

    private static final String[] ALL = {ORIGINAL, "InicioNavidad", "InicioVelitas"};

    private static ComponentName cn(Context c, String name) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + "." + name);
    }

    /** El alias que está prendido ahora. */
    static String current(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String a : ALL) {
            int s = pm.getComponentEnabledSetting(cn(c, a));
            boolean on = s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && a.equals(ORIGINAL));
            if (on) return a;
        }
        return ORIGINAL;
    }

    /** Si el ícono que toca ahora es distinto del que tiene la app puesto. */
    static boolean wouldChange(Context c) {
        return !alias(Season.currentIcon(c)).equals(current(c));
    }

    private static boolean pending;

    /** Deja listo el cambio de ícono: se hace al salir de la app. */
    static void apply(Context c) {
        pending = true;
    }

    /** Llamar en onStop: si hay un cambio pendiente, lo hace. */
    static void flush(Context c) {
        if (!pending) return;
        pending = false;
        String want = alias(Season.currentIcon(c));
        if (want.equals(current(c))) return;
        PackageManager pm = c.getPackageManager();
        try {
            // primero se prende el nuevo, para que nunca quede la app sin ícono
            pm.setComponentEnabledSetting(cn(c, want), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String a : ALL) {
                if (!a.equals(want)) {
                    pm.setComponentEnabledSetting(cn(c, a), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
