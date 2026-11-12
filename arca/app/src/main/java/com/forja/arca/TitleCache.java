package com.forja.arca;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lo que Arca ya aprendió de los juegos: el nombre y el ícono de cada uno (los trae cada paquete por dentro),
 * guardados en el celular para no volver a leerlos y poder mostrarlos aunque no haya internet.
 */
final class TitleCache {
    private final SharedPreferences prefs;
    private final File dir;
    private final LruCache<String, Bitmap> icons = new LruCache<>(80);
    private final Map<String, Boolean> missing = new HashMap<>();

    TitleCache(Context c) {
        prefs = c.getSharedPreferences("juegos", Context.MODE_PRIVATE);
        dir = new File(c.getFilesDir(), "iconos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Nombre del juego (o del perfil) por su ID de 8 letras/números, o null si no se conoce. */
    String name(String hexId) {
        return prefs.getString("n_" + hexId.toUpperCase(java.util.Locale.US), null);
    }

    boolean knows(String hexId) {
        return name(hexId) != null;
    }

    int count() {
        int n = 0;
        for (String k : prefs.getAll().keySet()) if (k.startsWith("n_")) n++;
        return n;
    }

    void put(String hexId, String name, byte[] png) {
        String id = hexId.toUpperCase(java.util.Locale.US);
        prefs.edit().putString("n_" + id, name).apply();
        if (png != null && png.length > 8) {
            try (FileOutputStream o = new FileOutputStream(new File(dir, id + ".png"))) {
                o.write(png);
            } catch (Exception ignored) {
            }
            icons.remove(id);
            missing.remove(id);
        }
    }

    Bitmap icon(String hexId) {
        String id = hexId.toUpperCase(java.util.Locale.US);
        Bitmap b = icons.get(id);
        if (b != null || missing.containsKey(id)) return b;
        File f = new File(dir, id + ".png");
        if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) icons.put(id, b);
        else missing.put(id, true);
        return b;
    }

    /** Un nombre guardado para un perfil (por su carpeta, de 16 caracteres). */
    String profile(String id16) {
        return prefs.getString("p_" + id16.toUpperCase(java.util.Locale.US), null);
    }

    void putProfile(String id16, String gamertag) {
        prefs.edit().putString("p_" + id16.toUpperCase(java.util.Locale.US), gamertag).apply();
    }

    void clear() {
        prefs.edit().clear().apply();
        File[] f = dir.listFiles();
        if (f != null) for (File x : f) //noinspection ResultOfMethodCallIgnored
            x.delete();
        icons.evictAll();
        missing.clear();
    }
}
