package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servidores WebDAV (Nextcloud, ownCloud, un NAS…) y carpetas compartidas de red SMB (Windows, Samba, NAS) guardados.
 * Cada uno aparece en Bóveda como una carpeta más: /dav/<id> o /smb/<id>.
 */
final class NetServers {
    static final String DAV = "/dav/", SMB = "/smb/";

    static final class Server {
        String id, type = "dav", name = "", user = "", pass = "";
        /** WebDAV: la dirección completa (https://…). SMB: el nombre o la IP del equipo. */
        String address = "";
        /** SMB */
        String share = "", domain = "";
        int port = 445;
        /** WebDAV: aceptar un certificado que el celular no reconoce (servidores de casa). */
        boolean insecure;

        boolean isDav() {
            return type.equals("dav");
        }

        String root() {
            return (isDav() ? DAV : SMB) + id;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("address", address).put("user", user)
                    .put("pass", Secure.encrypt(pass)).put("share", share).put("domain", domain).put("port", port).put("insecure", insecure);
        }

        static Server fromJson(JSONObject o) {
            Server s = new Server();
            s.id = o.optString("id");
            s.type = o.optString("type", "dav");
            s.name = o.optString("name");
            s.address = o.optString("address");
            s.user = o.optString("user");
            s.pass = Secure.decrypt(o.optString("pass"));
            s.share = o.optString("share");
            s.domain = o.optString("domain");
            s.port = o.optInt("port", 445);
            s.insecure = o.optBoolean("insecure", false);
            return s;
        }

        Server copy() {
            Server s = new Server();
            s.id = id; s.type = type; s.name = name; s.user = user; s.pass = pass; s.address = address;
            s.share = share; s.domain = domain; s.port = port; s.insecure = insecure;
            return s;
        }
    }

    private static NetServers instance;
    private final SharedPreferences prefs;
    private final Context app;
    private final List<Server> servers = new ArrayList<>();
    private final Map<String, Fs> fss = new HashMap<>();

    static synchronized NetServers get(Context c) {
        if (instance == null) instance = new NetServers(c.getApplicationContext());
        return instance;
    }

    private NetServers(Context c) {
        app = c;
        prefs = c.getSharedPreferences("red", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(prefs.getString("servidores", "[]"));
            for (int i = 0; i < a.length(); i++) servers.add(Server.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
    }

    synchronized List<Server> all(String type) {
        List<Server> out = new ArrayList<>();
        for (Server s : servers) if (s.type.equals(type)) out.add(s);
        return out;
    }

    synchronized Server byId(String id) {
        for (Server s : servers) if (s.id.equals(id)) return s;
        return null;
    }

    synchronized void save(Server s) {
        if (s.id == null) s.id = String.valueOf(System.currentTimeMillis() % 100000000L);
        servers.removeIf(x -> x.id.equals(s.id));
        servers.add(s);
        closeFs(s.id);
        persist();
    }

    synchronized void remove(String id) {
        servers.removeIf(x -> x.id.equals(id));
        closeFs(id);
        persist();
    }

    private void closeFs(String id) {
        Fs old = fss.remove(id);
        if (old instanceof SmbFs smb) smb.close();
    }

    private void persist() {
        JSONArray a = new JSONArray();
        try {
            for (Server s : servers) a.put(s.toJson());
        } catch (Exception ignored) {
        }
        prefs.edit().putString("servidores", a.toString()).apply();
    }

    static boolean isDav(String path) {
        return path.startsWith(DAV);
    }

    static boolean isSmb(String path) {
        return path.startsWith(SMB);
    }

    static boolean isNet(String path) {
        return isDav(path) || isSmb(path);
    }

    /** "/dav/123/carpeta/x" → el sistema de archivos de ese servidor. */
    synchronized Fs fsFor(String path) throws IOException {
        String rest = path.substring(isDav(path) ? DAV.length() : SMB.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        Server s = byId(id);
        if (s == null) throw new IOException(L.t("Ese servidor ya no está guardado"));
        Fs f = fss.get(id);
        if (f == null) {
            f = s.isDav() ? new DavFs(s, app.getCacheDir()) : new SmbFs(s, app.getCacheDir());
            fss.put(id, f);
        }
        return f;
    }
}
