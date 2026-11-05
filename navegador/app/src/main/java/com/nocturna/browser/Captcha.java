package com.nocturna.browser;

import android.net.Uri;

import java.net.URLEncoder;

/**
 * Cuando Google cree que la red es un robot (pasa mucho con datos móviles, donde muchas personas
 * comparten la misma IP) manda a /sorry/ con un CAPTCHA. No se resuelve ni se salta: la misma
 * búsqueda se hace en otro buscador.
 */
final class Captcha {
    static final String NAME = "DuckDuckGo";
    private static final String SEARCH = "https://duckduckgo.com/?q=";

    private Captcha() {
    }

    /** Si la dirección es la página de CAPTCHA de Google, la búsqueda en el otro buscador; si no, null. */
    static String alternative(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            String path = u.getPath();
            if (host == null || path == null || !host.contains("google.") || !path.startsWith("/sorry")) return null;
            String q = null;
            String cont = u.getQueryParameter("continue");
            if (cont != null) q = Uri.parse(cont).getQueryParameter("q");
            if (q == null) q = u.getQueryParameter("q");
            if (q == null || q.trim().isEmpty()) return null;
            return SEARCH + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
