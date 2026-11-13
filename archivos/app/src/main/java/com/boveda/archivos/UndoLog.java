package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * El historial de «Deshacer»: lo último que hiciste con tus archivos (mover, copiar, comprimir, extraer, renombrar,
 * crear) y cómo revertirlo. Se guarda en el celular; lo borrado va a la papelera, que ya lo restaura aparte.
 */
final class UndoLog {
    private UndoLog() {}

    static final class Action {
        String label = "";
        /** Mover o renombrar: pares {lugar de antes, lugar de ahora}. */
        final List<String[]> pairs = new ArrayList<>();
        /** Cosas que esta acción creó (copias, comprimidos, carpetas nuevas): deshacer las manda a la papelera. */
        final List<String> created = new ArrayList<>();
        long time;
    }

    private static final int MAX = 25;
    private static final List<Action> LOG = new ArrayList<>();   // la más nueva al final
    private static boolean loaded;
    private static Runnable listener;

    static synchronized void setListener(Runnable r) {
        listener = r;
    }

    private static void ensure(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            String s = c.getSharedPreferences("boveda", Context.MODE_PRIVATE).getString("undo_log", "[]");
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Action a = new Action();
                a.label = o.optString("label");
                a.time = o.optLong("time");
                JSONArray p = o.optJSONArray("pairs");
                if (p != null) for (int k = 0; k < p.length(); k++) a.pairs.add(new String[]{p.getJSONArray(k).getString(0), p.getJSONArray(k).getString(1)});
                JSONArray cr = o.optJSONArray("created");
                if (cr != null) for (int k = 0; k < cr.length(); k++) a.created.add(cr.getString(k));
                LOG.add(a);
            }
        } catch (Exception ignored) {
        }
    }

    private static void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (Action a : LOG) {
                JSONObject o = new JSONObject();
                o.put("label", a.label);
                o.put("time", a.time);
                JSONArray p = new JSONArray();
                for (String[] x : a.pairs) p.put(new JSONArray().put(x[0]).put(x[1]));
                o.put("pairs", p);
                o.put("created", new JSONArray(a.created));
                arr.put(o);
            }
            SharedPreferences sp = c.getSharedPreferences("boveda", Context.MODE_PRIVATE);
            sp.edit().putString("undo_log", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized void add(Context c, Action a) {
        if (a.pairs.isEmpty() && a.created.isEmpty()) return;
        ensure(c);
        a.time = System.currentTimeMillis();
        LOG.add(a);
        Diario.record(c, a);
        for (String[] pr : a.pairs) Capsules.move(c, pr[0], pr[1]);
        while (LOG.size() > MAX) LOG.remove(0);
        save(c);
        if (listener != null) listener.run();
    }

    static synchronized List<Action> recent(Context c) {
        ensure(c);
        List<Action> out = new ArrayList<>(LOG);
        java.util.Collections.reverse(out);
        return out;
    }

    static synchronized Action last(Context c) {
        ensure(c);
        return LOG.isEmpty() ? null : LOG.get(LOG.size() - 1);
    }

    static synchronized void remove(Context c, Action a) {
        ensure(c);
        LOG.remove(a);
        save(c);
    }

    // ------------------------------------------------------------------ anotar lo que hace cada tarea

    static void recordJob(Context c, TransferEngine.Job j) {
        Action a = new Action();
        int n = Math.max(j.undoMoves.size(), j.undoCreated.size());
        String what = n == 1 ? "1 elemento" : n + " elementos";
        switch (j.kind) {
            case MOVE -> {
                a.label = L.t("Movido: ") + what;
                a.pairs.addAll(j.undoMoves);
            }
            case COPY -> {
                a.label = L.t("Copiado: ") + what;
                a.created.addAll(j.undoCreated);
            }
            case ZIP -> {
                a.label = L.t("Comprimido: ") + (j.undoCreated.isEmpty() ? "" : Paths.name(j.undoCreated.get(0)));
                a.created.addAll(j.undoCreated);
            }
            case EXTRACT -> {
                a.label = L.t("Extraído en: ") + (j.undoCreated.isEmpty() ? "" : Paths.name(j.undoCreated.get(0)));
                a.created.addAll(j.undoCreated);
            }
            default -> {
                return;
            }
        }
        add(c, a);
    }

    static void recordRename(Context c, String from, String to) {
        Action a = new Action();
        a.label = L.t("Renombrado: ") + Paths.name(from) + " → " + Paths.name(to);
        a.pairs.add(new String[]{from, to});
        add(c, a);
    }

    static void recordCreated(Context c, String path, boolean dir) {
        Action a = new Action();
        a.label = (dir ? L.t("Carpeta creada: ") : L.t("Archivo creado: ")) + Paths.name(path);
        a.created.add(path);
        add(c, a);
    }

    // ------------------------------------------------------------------ deshacer

    /** Revierte una acción. Devuelve un mensaje para mostrar. Debe llamarse fuera del hilo principal. */
    static String undo(Context c, Action a) throws IOException {
        int moved = 0, trashed = 0;
        List<String> problems = new ArrayList<>();
        // lo creado va a la papelera (así tampoco se pierde si cambias de idea)
        for (String p : a.created) {
            try {
                Fs f = FsRouter.get(c, p);
                if (f.stat(p) == null) {
                    problems.add(Paths.name(p) + L.t(": ya no está"));
                    continue;
                }
                if (Trash.supported(p)) Trash.put(p);
                else f.delete(p);
                trashed++;
            } catch (IOException e) {
                problems.add(Paths.name(p) + ": " + e.getMessage());
            }
        }
        for (String[] pr : a.pairs) {
            String before = pr[0], now = pr[1];
            try {
                Fs nf = FsRouter.get(c, now);
                if (nf.stat(now) == null) {
                    problems.add(Paths.name(now) + L.t(": ya no está donde lo dejó la acción"));
                    continue;
                }
                Fs bf = FsRouter.get(c, before);
                if (bf.stat(before) != null) {
                    problems.add(Paths.name(before) + L.t(": ya hay algo en su sitio de antes"));
                    continue;
                }
                if (!nf.key(now).equals(bf.key(before))) {
                    // entre almacenamientos distintos: se mueve con la tarea de siempre (con barra de progreso)
                    String parent = Paths.parent(before);
                    String target = now;
                    if (!Paths.name(now).equals(Paths.name(before))) {
                        target = Paths.join(Paths.parent(now), Paths.name(before));
                        if (nf.stat(target) != null) {
                            problems.add(Paths.name(before) + L.t(": no pude devolverle su nombre"));
                            continue;
                        }
                        nf.rename(now, target);
                    }
                    TransferEngine.get(c).submitQuiet(TransferEngine.Kind.MOVE, java.util.Collections.singletonList(target), parent, TransferEngine.Conflict.SKIP);
                } else {
                    bf.rename(now, before);
                }
                moved++;
            } catch (IOException e) {
                problems.add(Paths.name(before) + ": " + e.getMessage());
            }
        }
        StringBuilder msg = new StringBuilder();
        if (moved > 0) msg.append(moved == 1 ? L.t("Devuelto a su sitio: 1 elemento. ") : L.t("Devueltos a su sitio: ") + moved + L.t(" elementos. "));
        if (trashed > 0) msg.append(trashed == 1 ? L.t("1 elemento a la papelera. ") : trashed + L.t(" elementos a la papelera. "));
        if (!problems.isEmpty()) msg.append(L.t("No se pudo: ")).append(String.join("; ", problems));
        if (moved + trashed == 0 && !problems.isEmpty()) throw new IOException(msg.toString());
        return msg.toString().trim();
    }
}
