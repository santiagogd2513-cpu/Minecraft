package com.nocturna.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Ajustes, favoritos, historial y pestañas guardadas. */
final class Store {
    static final String[][] ENGINES = {
            {"Google", "https://www.google.com/search?q="},
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"Brave", "https://search.brave.com/search?q="},
    };

    record Entry(String title, String url, long time) {}

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("nocturna", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ ajustes

    int darkMode() { return p.getInt("dark", DarkMode.FORCED); }
    void setDarkMode(int m) { p.edit().putInt("dark", m).apply(); }

    int engine() { return p.getInt("engine", 0); }
    void setEngine(int e) { p.edit().putInt("engine", e).apply(); }

    boolean adBlock() { return p.getBoolean("adblock", true); }
    void setAdBlock(boolean b) { p.edit().putBoolean("adblock", b).apply(); }

    boolean captchaDodge() { return p.getBoolean("captcha_dodge", true); }
    void setCaptchaDodge(boolean b) { p.edit().putBoolean("captcha_dodge", b).apply(); }

    int connections() { return p.getInt("conns", 8); }
    void setConnections(int n) { p.edit().putInt("conns", n).apply(); }

    String searchUrl(String q) {
        try {
            return ENGINES[engine()][1] + java.net.URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            return ENGINES[0][1] + q;
        }
    }

    // ------------------------------------------------------------------ listas

    List<Entry> list(String key) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.optString("t"), o.optString("u"), o.optLong("w")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void save(String key, List<Entry> l) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : l) a.put(new JSONObject().put("t", e.title()).put("u", e.url()).put("w", e.time()));
        } catch (Exception ignored) {
        }
        p.edit().putString(key, a.toString()).apply();
    }

    List<Entry> bookmarks() { return list("bookmarks"); }

    boolean isBookmarked(String url) {
        for (Entry e : bookmarks()) if (e.url().equals(url)) return true;
        return false;
    }

    void toggleBookmark(String title, String url) {
        List<Entry> l = bookmarks();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).url().equals(url)) {
                l.remove(i);
                save("bookmarks", l);
                return;
            }
        }
        l.add(0, new Entry(title == null || title.isEmpty() ? url : title, url, System.currentTimeMillis()));
        save("bookmarks", l);
    }

    void removeBookmark(String url) {
        List<Entry> l = bookmarks();
        l.removeIf(e -> e.url().equals(url));
        save("bookmarks", l);
    }

    List<Entry> history() { return list("history"); }

    void addHistory(String title, String url) {
        if (url == null || !url.startsWith("http")) return;
        List<Entry> l = history();
        l.removeIf(e -> e.url().equals(url));
        l.add(0, new Entry(title == null || title.isEmpty() ? url : title, url, System.currentTimeMillis()));
        while (l.size() > 500) l.remove(l.size() - 1);
        save("history", l);
    }

    void clearHistory() { p.edit().remove("history").apply(); }

    // ------------------------------------------------------------------ pestañas abiertas

    void saveTabs(List<String> urls, int current) {
        JSONArray a = new JSONArray();
        for (String u : urls) a.put(u == null ? "" : u);
        p.edit().putString("tabs", a.toString()).putInt("tab", current).apply();
    }

    List<String> tabs() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("tabs", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    int currentTab() { return p.getInt("tab", 0); }
}
