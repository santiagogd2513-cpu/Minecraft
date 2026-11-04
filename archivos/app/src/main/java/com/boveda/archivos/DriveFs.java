package com.boveda.archivos;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Drive como una carpeta más de Bóveda (/gdrive/…). Drive trabaja con identificadores,
 * así que cada ruta se traduce a su identificador (y se recuerda). Borrar manda a la papelera de Drive.
 */
final class DriveFs implements Fs {
    static final String ROOT = "/gdrive";
    private static final String API = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String FOLDER = "application/vnd.google-apps.folder";
    private static final String FIELDS = "id,name,mimeType,size,modifiedTime";
    private static DriveFs instance;
    private static final Map<String, String> ids = new ConcurrentHashMap<>();   // ruta → id

    private final Drive drive;
    private final File cacheDir;

    static synchronized DriveFs get(Context c) {
        if (instance == null) instance = new DriveFs(c.getApplicationContext());
        return instance;
    }

    static void reset() {
        ids.clear();
    }

    static boolean isDrive(String path) {
        return path.equals(ROOT) || path.startsWith(ROOT + "/");
    }

    private DriveFs(Context c) {
        drive = Drive.get(c);
        cacheDir = new File(c.getCacheDir(), "gdrive");
    }

    // ------------------------------------------------------------------ rutas ↔ identificadores

    private static String q(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** Identificador de una ruta, o null si no existe. */
    private String id(String path) throws IOException {
        if (path.equals(ROOT)) return "root";
        String known = ids.get(path);
        if (known != null) return known;
        String parent = id(Paths.parent(path));
        if (parent == null) return null;
        String query = "'" + parent + "' in parents and name = '" + q(Paths.name(path)) + "' and trashed = false";
        JSONObject r = drive.json("GET", API + "?pageSize=10&fields=files(" + FIELDS + ")&q=" + Drive.enc(query), null);
        JSONArray files = r.optJSONArray("files");
        if (files == null || files.length() == 0) return null;
        String id = files.optJSONObject(0).optString("id");
        ids.put(path, id);
        return id;
    }

    private String need(String path) throws IOException {
        String id = id(path);
        if (id == null) throw new IOException("Ya no existe en Google Drive: " + Paths.name(path));
        return id;
    }

    private static long time(String rfc) {
        if (rfc == null || rfc.isEmpty()) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(rfc.substring(0, 19)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private Entry entry(String path, JSONObject o) {
        boolean dir = FOLDER.equals(o.optString("mimeType"));
        return new Entry(path, o.optString("name"), dir, dir ? 0 : o.optLong("size", 0), time(o.optString("modifiedTime")));
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public List<Entry> list(String dir) throws IOException {
        String id = need(dir);
        List<Entry> out = new ArrayList<>();
        String token = null;
        do {
            String query = "'" + id + "' in parents and trashed = false";
            String url = API + "?pageSize=1000&fields=nextPageToken,files(" + FIELDS + ")&q=" + Drive.enc(query)
                    + (token == null ? "" : "&pageToken=" + Drive.enc(token));
            JSONObject r = drive.json("GET", url, null);
            JSONArray files = r.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject o = files.optJSONObject(i);
                    if (o == null) continue;
                    String p = Paths.join(dir, o.optString("name").replace('/', '_'));
                    ids.putIfAbsent(p, o.optString("id"));
                    out.add(entry(p, o));
                }
            }
            token = r.optString("nextPageToken", null);
            if (token != null && token.isEmpty()) token = null;
        } while (token != null);
        return out;
    }

    @Override
    public Entry stat(String path) throws IOException {
        if (path.equals(ROOT)) return new Entry(ROOT, "Google Drive", true, 0, 0);
        String id = id(path);
        if (id == null) return null;
        try {
            return entry(path, drive.json("GET", API + "/" + id + "?fields=" + FIELDS, null));
        } catch (IOException e) {
            ids.remove(path);
            return null;
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        String id = need(path);
        HttpURLConnection c = drive.open("GET", API + "/" + id + "?alt=media");
        int code = c.getResponseCode();
        if (code >= 400) {
            String body = Drive.read(c.getErrorStream());
            if (code == 403 && body.contains("fileNotDownloadable")) {
                throw new IOException("Los documentos de Google (Docs, Hojas, Presentaciones) se abren en la web");
            }
            throw new IOException(Drive.errorText(code, body));
        }
        return c.getInputStream();
    }

    /** Se escribe a un archivo temporal y, al cerrar, se sube a Drive por partes (reanudable). */
    @Override
    public OutputStream out(String path) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        File tmp = File.createTempFile("subida", ".tmp", cacheDir);
        return new FilterOutputStream(new FileOutputStream(tmp)) {
            boolean done;

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                if (done) return;
                done = true;
                try {
                    super.close();
                    upload(path, tmp);
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            }
        };
    }

    private void upload(String path, File file) throws IOException {
        String existing = id(path);
        JSONObject meta = new JSONObject();
        try {
            meta.put("name", Paths.name(path));
            if (existing == null) meta.put("parents", new JSONArray().put(need(Paths.parent(path))));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        // 1) se pide una sesión de subida
        String url = existing == null ? UPLOAD + "?uploadType=resumable&fields=id" : UPLOAD + "/" + existing + "?uploadType=resumable&fields=id";
        HttpURLConnection c = drive.open(existing == null ? "POST" : "PATCH", url);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("X-Upload-Content-Length", String.valueOf(file.length()));
        try (OutputStream o = c.getOutputStream()) {
            o.write(meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException(Drive.errorText(code, Drive.read(c.getErrorStream())));
        String session = c.getHeaderField("Location");
        c.disconnect();
        if (session == null) throw new IOException("Google Drive no abrió la subida");
        // 2) se manda por partes de 8 MB
        long total = file.length(), pos = 0;
        int chunk = 8 * 1024 * 1024;
        byte[] buf = new byte[chunk];
        try (FileInputStream in = new FileInputStream(file)) {
            while (true) {
                int len = 0;
                while (len < chunk) {
                    int r = in.read(buf, len, chunk - len);
                    if (r < 0) break;
                    len += r;
                }
                HttpURLConnection u = (HttpURLConnection) new java.net.URL(session).openConnection();
                u.setRequestMethod("PUT");
                u.setDoOutput(true);
                u.setConnectTimeout(20000);
                u.setReadTimeout(120000);
                u.setFixedLengthStreamingMode(len);
                u.setRequestProperty("Content-Range", total == 0 ? "bytes */0" : "bytes " + pos + "-" + (pos + len - 1) + "/" + total);
                try (OutputStream o = u.getOutputStream()) {
                    o.write(buf, 0, len);
                }
                int rc = u.getResponseCode();
                if (rc == 200 || rc == 201) {
                    String body = Drive.read(u.getInputStream());
                    u.disconnect();
                    try {
                        ids.put(path, new JSONObject(body).optString("id"));
                    } catch (org.json.JSONException ignored) {
                    }
                    return;
                }
                if (rc != 308) throw new IOException(Drive.errorText(rc, Drive.read(u.getErrorStream())));
                u.disconnect();
                pos += len;
                if (len == 0) throw new IOException("Google Drive no terminó la subida");
            }
        }
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException("En Google Drive solo se puede abrir para leer");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        File f = new File(cacheDir, Integer.toHexString(path.hashCode()) + "_" + Paths.name(path));
        Entry e = stat(path);
        if (!(f.exists() && e != null && f.length() == e.size)) {
            try (InputStream in = in(path); FileOutputStream o = new FileOutputStream(f)) {
                byte[] buf = new byte[1 << 20];
                int r;
                while ((r = in.read(buf)) >= 0) o.write(buf, 0, r);
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public void mkdir(String path) throws IOException {
        if (id(path) != null) return;
        try {
            JSONObject body = new JSONObject().put("name", Paths.name(path)).put("mimeType", FOLDER)
                    .put("parents", new JSONArray().put(need(Paths.parent(path))));
            ids.put(path, drive.json("POST", API + "?fields=id", body).optString("id"));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** A la papelera de Drive (se puede recuperar). */
    @Override
    public void delete(String path) throws IOException {
        String id = id(path);
        if (id == null) return;
        try {
            drive.json("PATCH", API + "/" + id, new JSONObject().put("trashed", true));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        forget(path);
    }

    private static void forget(String path) {
        ids.keySet().removeIf(k -> k.equals(path) || k.startsWith(path + "/"));
    }

    @Override
    public void rename(String from, String to) throws IOException {
        String id = need(from);
        String oldParent = Paths.parent(from), newParent = Paths.parent(to);
        String url = API + "/" + id + "?fields=id";
        if (!oldParent.equals(newParent)) url += "&addParents=" + need(newParent) + "&removeParents=" + need(oldParent);
        try {
            drive.json("PATCH", url, new JSONObject().put("name", Paths.name(to)));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        forget(from);
        ids.put(to, id);
    }

    @Override
    public void setModified(String path, long time) {
        try {
            String id = id(path);
            if (id == null) return;
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            drive.json("PATCH", API + "/" + id, new JSONObject().put("modifiedTime", f.format(time)));
        } catch (Exception ignored) {
        }
    }

    @Override
    public String key(String path) {
        return "gdrive";
    }
}
