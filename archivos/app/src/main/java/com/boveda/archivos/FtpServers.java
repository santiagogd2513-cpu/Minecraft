package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Servidores FTP guardados. Cada uno aparece en Bóveda como la carpeta /ftp/<id>. */
final class FtpServers {
    static final String PREFIX = "/ftp/";

    static final class Server {
        String id, name, host, user, pass, startDir = "/";
        int port = 21, maxConnections = 2;
        boolean passive = true, latin1;

        String root() {
            return PREFIX + id;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("host", host).put("port", port).put("user", user)
                    .put("pass", Secure.encrypt(pass)).put("start", startDir).put("max", maxConnections)
                    .put("passive", passive).put("latin1", latin1);
        }

        static Server fromJson(JSONObject o) {
            Server s = new Server();
            s.id = o.optString("id");
            s.name = o.optString("name");
            s.host = o.optString("host");
            s.port = o.optInt("port", 21);
            s.user = o.optString("user");
            s.pass = Secure.decrypt(o.optString("pass"));
            s.startDir = o.optString("start", "/");
            s.maxConnections = o.optInt("max", 2);
            s.passive = o.optBoolean("passive", true);
            s.latin1 = o.optBoolean("latin1", false);
            return s;
        }
    }

    private static FtpServers instance;
    private final SharedPreferences prefs;
    private final List<Server> servers = new ArrayList<>();
    private final Map<String, FtpFs> fss = new HashMap<>();

    static synchronized FtpServers get(Context c) {
        if (instance == null) instance = new FtpServers(c.getApplicationContext());
        return instance;
    }

    private FtpServers(Context c) {
        prefs = c.getSharedPreferences("ftp", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(prefs.getString("servidores", "[]"));
            for (int i = 0; i < a.length(); i++) servers.add(Server.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
    }

    synchronized List<Server> all() {
        return new ArrayList<>(servers);
    }

    synchronized Server byId(String id) {
        for (Server s : servers) if (s.id.equals(id)) return s;
        return null;
    }

    synchronized void save(Server s) {
        if (s.id == null) s.id = String.valueOf(System.currentTimeMillis() % 100000000L);
        servers.removeIf(x -> x.id.equals(s.id));
        servers.add(s);
        FtpFs old = fss.remove(s.id);
        if (old != null) old.closeAll();
        persist();
    }

    synchronized void remove(String id) {
        servers.removeIf(x -> x.id.equals(id));
        FtpFs old = fss.remove(id);
        if (old != null) old.closeAll();
        persist();
    }

    private void persist() {
        JSONArray a = new JSONArray();
        try {
            for (Server s : servers) a.put(s.toJson());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("servidores", a.toString()).apply();
    }

    /** "/ftp/123/Hdd1/x" → el sistema de archivos de ese servidor. */
    synchronized FtpFs fsFor(String path) throws java.io.IOException {
        String rest = path.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        Server s = byId(id);
        if (s == null) throw new java.io.IOException(L.t("Ese servidor FTP ya no está guardado"));
        FtpFs f = fss.get(id);
        if (f == null) {
            f = new FtpFs(s);
            fss.put(id, f);
        }
        return f;
    }

    static boolean isFtp(String path) {
        return path.startsWith(PREFIX);
    }
}
