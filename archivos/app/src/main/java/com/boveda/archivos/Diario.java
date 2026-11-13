package com.boveda.archivos;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** El diario de la Máquina del tiempo: qué se movió, renombró o creó, y cuándo (los borrados salen de la papelera). */
final class Diario {
    private Diario() {}

    static final class Event {
        String kind;   // move, rename, create
        String from, to;
        long time;
    }

    private static final int MAX = 600;

    private static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("boveda", Context.MODE_PRIVATE);
    }

    static synchronized void record(Context c, UndoLog.Action a) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString("diario", "[]"));
            for (String[] p : a.pairs) {
                boolean same = Paths.parent(p[0]).equals(Paths.parent(p[1]));
                arr.put(new JSONObject().put("k", same ? "rename" : "move").put("f", p[0]).put("t", p[1]).put("w", a.time));
            }
            for (String p : a.created) arr.put(new JSONObject().put("k", "create").put("t", p).put("w", a.time));
            while (arr.length() > MAX) arr.remove(0);
            sp(c).edit().putString("diario", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized List<Event> all(Context c) {
        List<Event> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("diario", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Event e = new Event();
                e.kind = o.optString("k");
                e.from = o.optString("f", null);
                e.to = o.optString("t");
                e.time = o.optLong("w");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
