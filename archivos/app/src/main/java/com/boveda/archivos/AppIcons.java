package com.boveda.archivos;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * En Android/data, Android/obb y Android/media cada carpeta se llama como el paquete de su app
 * (com.algo.app). Aquí se busca el ícono y el nombre de esa app para mostrarlos.
 */
final class AppIcons {
    static final class App {
        final String label;
        final Drawable icon;

        App(String label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private static final App MISSING = new App(null, null);
    private static final Map<String, App> cache = new HashMap<>();

    private AppIcons() {}

    /** ¿Esta carpeta guarda una carpeta por app? (…/Android/data, …/Android/obb, …/Android/media) */
    static boolean isAppsFolder(String dir) {
        if (dir == null) return false;
        String n = Paths.name(dir);
        return (n.equals("data") || n.equals("obb") || n.equals("media")) && Paths.name(Paths.parent(dir)).equals("Android");
    }

    /** La app dueña de esta carpeta, o null si no está instalada. */
    static synchronized App of(Context c, String packageName) {
        App a = cache.get(packageName);
        if (a == null) {
            try {
                PackageManager pm = c.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                a = new App(String.valueOf(pm.getApplicationLabel(ai)), pm.getApplicationIcon(ai));
            } catch (Exception e) {
                a = MISSING;
            }
            cache.put(packageName, a);
        }
        return a == MISSING ? null : a;
    }

    static synchronized void clear() {
        cache.clear();
    }
}
