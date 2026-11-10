package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Las carpetas que Bóveda les dio a otras apps (emuladores y demás).
 * Cada permiso es: la app, la carpeta que la app cree tener («root») y, si se cambió, la carpeta de
 * verdad a la que ahora apunta («target»). Las apps no saben que se cambió: Bóveda traduce las rutas.
 * Quitar un permiso lo bloquea en Bóveda (la app ya no puede leer ni escribir nada de esa carpeta).
 */
final class Grants {
    static final class Grant {
        String pkg, root, target;
        boolean blocked;
        long since;

        String real() {
            return target != null ? target : root;
        }
    }

    private static final String PREFS = "permisos", KEY = "lista";
    private static List<Grant> cache;

    private Grants() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Grant> all(Context c) {
        if (cache != null) return cache;
        cache = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Grant g = new Grant();
                g.pkg = o.getString("pkg");
                g.root = o.getString("root");
                g.target = o.has("target") ? o.getString("target") : null;
                g.blocked = o.optBoolean("blocked");
                g.since = o.optLong("since");
                cache.add(g);
            }
        } catch (Exception ignored) {
        }
        return cache;
    }

    private static void save(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Grant g : cache) {
                JSONObject o = new JSONObject();
                o.put("pkg", g.pkg);
                o.put("root", g.root);
                if (g.target != null) o.put("target", g.target);
                if (g.blocked) o.put("blocked", true);
                o.put("since", g.since);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY, a.toString()).apply();
    }

    static boolean under(String path, String dir) {
        return path.equals(dir) || path.startsWith(dir.endsWith("/") ? dir : dir + "/");
    }

    /** Apps del sistema que solo muestran el selector: esas no cuentan como «apps con carpeta». */
    private static boolean ignored(Context c, String pkg) {
        return pkg == null || pkg.equals(c.getPackageName()) || pkg.contains("documentsui") || pkg.equals("com.android.externalstorage")
                || pkg.equals("android") || pkg.equals("com.android.providers.media") || pkg.equals("com.android.mtp");
    }

    /** El permiso de esta app que cubre esta ruta (la más específica), o null. */
    static synchronized Grant find(Context c, String pkg, String path) {
        if (ignored(c, pkg) || path == null) return null;
        Grant best = null;
        for (Grant g : all(c)) {
            if (g.pkg.equals(pkg) && under(path, g.root) && (best == null || g.root.length() > best.root.length())) best = g;
        }
        return best;
    }

    /** Bóveda le dio esta carpeta a la app (desde su propio selector). */
    static synchronized void granted(Context c, String pkg, String dir) {
        if (ignored(c, pkg)) return;
        List<Grant> l = all(c);
        for (Grant g : l) {
            if (g.pkg.equals(pkg) && g.root.equals(dir)) {
                g.blocked = false;
                g.target = null;
                g.since = System.currentTimeMillis();
                save(c);
                return;
            }
        }
        Grant g = new Grant();
        g.pkg = pkg;
        g.root = dir;
        g.since = System.currentTimeMillis();
        l.add(g);
        save(c);
    }

    /**
     * Una app entró a una carpeta de Bóveda (por el selector del sistema, donde Bóveda no ve a quién
     * se le dio). Se anota la carpeta más alta a la que entró: esa es la que le dieron.
     */
    static synchronized void seen(Context c, String pkg, String dir) {
        if (ignored(c, pkg) || dir == null) return;
        List<Grant> l = all(c);
        for (Grant g : l) if (g.pkg.equals(pkg) && under(dir, g.root)) return;
        boolean changed = false;
        for (Grant g : l) {
            if (g.pkg.equals(pkg) && !g.blocked && g.target == null && under(g.root, dir)) {
                g.root = dir;
                changed = true;
            }
        }
        if (!changed) granted(c, pkg, dir);
        else save(c);
    }

    static synchronized void change(Context c, Grant g, String newDir) {
        g.target = newDir.equals(g.root) ? null : newDir;
        g.blocked = false;
        save(c);
    }

    static synchronized void revoke(Context c, Grant g) {
        g.blocked = true;
        save(c);
    }

    /** Las que se muestran en la lista (las quitadas quedan guardadas solo para bloquearlas). */
    static synchronized List<Grant> active(Context c) {
        List<Grant> out = new ArrayList<>();
        for (Grant g : all(c)) if (!g.blocked) out.add(g);
        out.sort((x, y) -> Long.compare(y.since, x.since));
        return out;
    }
}
