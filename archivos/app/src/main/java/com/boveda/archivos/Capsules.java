package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Cápsula del tiempo: una carpeta o un archivo que Bóveda no deja abrir hasta una fecha. Es un candado «suave»: vale dentro
 * de Bóveda (no se abre, no se ve la miniatura); otras apps que lean la carpeta sí podrían verlo.
 */
final class Capsules {
    private Capsules() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("boveda", Context.MODE_PRIVATE);
    }

    private static synchronized JSONObject all(Context c) {
        try {
            return new JSONObject(sp(c).getString("capsules", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Cuándo se abre (milisegundos); 0 si no es una cápsula o ya se pasó la fecha. */
    static long lockedUntil(Context c, String path) {
        if (path == null) return 0;
        JSONObject o = all(c);
        long now = System.currentTimeMillis();
        long best = 0;
        // el elemento o cualquier carpeta que lo contenga
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            if (path.equals(k) || path.startsWith(k + "/")) best = Math.max(best, o.optLong(k));
        }
        return best > now ? best : 0;
    }

    static synchronized void set(Context c, String path, long openAt) {
        try {
            JSONObject o = all(c);
            o.put(path, openAt);
            sp(c).edit().putString("capsules", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void remove(Context c, String path) {
        JSONObject o = all(c);
        o.remove(path);
        sp(c).edit().putString("capsules", o.toString()).apply();
    }

    /** Si algo se renombra o se mueve, su cápsula lo sigue. */
    static synchronized void move(Context c, String from, String to) {
        try {
            JSONObject o = all(c);
            if (!o.has(from)) return;
            long t = o.getLong(from);
            o.remove(from);
            o.put(to, t);
            sp(c).edit().putString("capsules", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static String when(long t) {
        return new java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", new java.util.Locale("es")).format(new java.util.Date(t));
    }

    static String remaining(long t) {
        long days = (t - System.currentTimeMillis()) / (24L * 60 * 60 * 1000);
        if (days <= 0) return L.t("menos de un día");
        return days == 1 ? L.t("1 día") : days + L.t(" días");
    }
}
