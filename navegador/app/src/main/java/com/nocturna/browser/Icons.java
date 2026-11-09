package com.nocturna.browser;

import android.content.Context;

/**
 * Cambia el ícono de la app en la pantalla de inicio (con activity-alias). Cada ícono de
 * temporada se agrega aquí cuando ya está dibujado; mientras no haya ninguno, siempre queda el
 * original y no se toca nada.
 */
final class Icons {
    private Icons() {}

    /** Si el ícono que toca ahora es distinto del que tiene la app puesto. */
    static boolean wouldChange(Context c) {
        return false;
    }

    /** Pone el ícono que toca ahora. */
    static void apply(Context c) {
    }
}
