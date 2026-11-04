package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Ajustes, favoritas y ancladas. */
final class Prefs {
    static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2, SORT_TYPE = 3;
    static final String[] SORT_NAMES = {"Nombre", "Fecha", "Tamaño", "Tipo"};

    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("boveda", Context.MODE_PRIVATE);
    }

    boolean showHidden() { return p.getBoolean("hidden", false); }
    void setShowHidden(boolean b) { p.edit().putBoolean("hidden", b).apply(); }

    int sort() { return p.getInt("sort", SORT_NAME); }
    void setSort(int s) { p.edit().putInt("sort", s).apply(); }

    boolean sortDesc() { return p.getBoolean("desc", false); }
    void setSortDesc(boolean b) { p.edit().putBoolean("desc", b).apply(); }

    /** Volver a leer cada archivo copiado y compararlo con el original. */
    boolean verify() { return p.getBoolean("verify", true); }
    void setVerify(boolean b) { p.edit().putBoolean("verify", b).apply(); }

    List<String> favorites() { return list("favs"); }
    List<String> pinned() { return list("pins"); }

    boolean isFavorite(String path) { return favorites().contains(path); }
    boolean isPinned(String path) { return pinned().contains(path); }

    void toggleFavorite(String path) { toggle("favs", path); }
    void togglePinned(String path) { toggle("pins", path); }

    private void toggle(String key, String path) {
        List<String> l = list(key);
        if (!l.remove(path)) l.add(path);
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        p.edit().putString(key, a.toString()).apply();
    }

    private List<String> list(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }
}
