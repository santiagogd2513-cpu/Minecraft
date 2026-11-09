package com.boveda.archivos;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * OneDrive como una carpeta más de Bóveda (/onedrive/…). Microsoft Graph trabaja con rutas igual
 * que Bóveda, así que cada ruta se traduce directo. Borrar manda a la papelera de OneDrive.
 */
final class OneDriveFs implements Fs {
    static final String ROOT = "/onedrive";
    private static OneDriveFs instance;
    private final OneDrive od;
    private final File cacheDir;

    static synchronized OneDriveFs get(Context c) {
        if (instance == null) instance = new OneDriveFs(c.getApplicationContext());
        return instance;
    }

    static boolean isOneDrive(String path) {
        return path.equals(ROOT) || path.startsWith(ROOT + "/");
    }

    private OneDriveFs(Context c) {
        od = OneDrive.get(c);
        cacheDir = new File(c.getCacheDir(), "onedrive");
    }

    /** /onedrive/a/b → /me/drive/root:/a/b: (cada parte codificada). */
    private static String item(String path) {
        if (path.equals(ROOT)) return OneDrive.GRAPH + "/me/drive/root";
        StringBuilder b = new StringBuilder(OneDrive.GRAPH + "/me/drive/root:");
        for (String part : path.substring(ROOT.length() + 1).split("/")) {
            if (part.isEmpty()) continue;
            b.append('/').append(Drive.enc(part).replace("+", "%20"));
        }
        return b.append(':').toString();
    }

    /** Dirección de una «acción» sobre el elemento (children, content, createUploadSession…). */
    private static String action(String path, String what) {
        return path.equals(ROOT) ? item(path) + "/" + what : item(path) + "/" + what;
    }

    private static Entry entry(String path, JSONObject o) {
        boolean dir = o.has("folder");
        long modified = 0;
        try {
            modified = Instant.parse(o.optString("lastModifiedDateTime")).toEpochMilli();
        } catch (Exception ignored) {
        }
        return new Entry(path, o.optString("name"), dir, dir ? 0 : o.optLong("size"), modified);
    }

    @Override
    public List<Entry> list(String dir) throws IOException {
        List<Entry> out = new ArrayList<>();
        String url = action(dir, "children") + "?$top=200&$select=name,size,folder,file,lastModifiedDateTime";
        while (url != null) {
            JSONObject r = od.json("GET", url, null);
            JSONArray v = r.optJSONArray("value");
            if (v != null) {
                for (int i = 0; i < v.length(); i++) {
                    JSONObject o = v.optJSONObject(i);
                    out.add(entry(Paths.join(dir, o.optString("name")), o));
                }
            }
            String next = r.optString("@odata.nextLink", "");
            url = next.isEmpty() ? null : next;
        }
        return out;
    }

    @Override
    public Entry stat(String path) throws IOException {
        try {
            JSONObject o = od.json("GET", item(path), null);
            Entry e = entry(path, o);
            return path.equals(ROOT) ? new Entry(path, "OneDrive", true, 0, e.modified) : e;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().equals(L.t("Ya no existe en OneDrive"))) return null;
            throw e;
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        HttpURLConnection c = od.open("GET", action(path, "content"));
        c.setReadTimeout(120000);
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException(OneDrive.errorText(code, Drive.read(c.getErrorStream())));
        return c.getInputStream();
    }

    /** Se escribe a un archivo temporal y, al cerrar, se sube (por partes si es grande). */
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
        long total = file.length();
        if (total < 4 * 1024 * 1024) {
            // archivo chico: de una vez
            HttpURLConnection c = od.open("PUT", action(path, "content"));
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode((int) total);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            try (OutputStream o = c.getOutputStream(); FileInputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException(OneDrive.errorText(code, Drive.read(c.getErrorStream())));
            c.disconnect();
            return;
        }
        // archivo grande: sesión de subida por partes de 10 MB (múltiplo de 320 KB, como pide Microsoft)
        String session;
        try {
            JSONObject body = new JSONObject().put("item", new JSONObject().put("@microsoft.graph.conflictBehavior", "replace"));
            session = od.json("POST", action(path, "createUploadSession"), body).optString("uploadUrl");
        } catch (JSONException e) {
            throw new IOException(e);
        }
        if (session.isEmpty()) throw new IOException(L.t("OneDrive no abrió la subida"));
        int chunk = 32 * 327680;
        byte[] buf = new byte[chunk];
        long pos = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (pos < total) {
                int len = 0;
                while (len < chunk && pos + len < total) {
                    int r = in.read(buf, len, (int) Math.min(chunk - len, total - pos - len));
                    if (r < 0) break;
                    len += r;
                }
                IOException last = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    try {
                        HttpURLConnection u = (HttpURLConnection) new URL(session).openConnection();   // sin token: la dirección ya lo trae
                        u.setRequestMethod("PUT");
                        u.setDoOutput(true);
                        u.setConnectTimeout(20000);
                        u.setReadTimeout(120000);
                        u.setFixedLengthStreamingMode(len);
                        u.setRequestProperty("Content-Range", "bytes " + pos + "-" + (pos + len - 1) + "/" + total);
                        try (OutputStream o = u.getOutputStream()) {
                            o.write(buf, 0, len);
                        }
                        int rc = u.getResponseCode();
                        if (rc >= 400) {
                            String err = Drive.read(u.getErrorStream());
                            if (rc >= 500) throw new IOException(L.t("OneDrive respondió ") + rc);
                            throw new IllegalStateException(OneDrive.errorText(rc, err));
                        }
                        u.disconnect();
                        last = null;
                        break;
                    } catch (IllegalStateException e) {
                        throw new IOException(e.getMessage());
                    } catch (IOException e) {
                        last = e;
                        try {
                            Thread.sleep(1000L << attempt);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                if (last != null) throw last;
                pos += len;
            }
        }
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException(L.t("En OneDrive solo se puede abrir para leer"));
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
        if (stat(path) != null) return;
        try {
            JSONObject body = new JSONObject().put("name", Paths.name(path)).put("folder", new JSONObject())
                    .put("@microsoft.graph.conflictBehavior", "fail");
            od.json("POST", action(Paths.parent(path), "children"), body);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** A la papelera de OneDrive (se puede recuperar). */
    @Override
    public void delete(String path) throws IOException {
        if (path.equals(ROOT)) return;
        HttpURLConnection c = od.open("DELETE", item(path));
        int code = c.getResponseCode();
        if (code >= 400 && code != 404) throw new IOException(OneDrive.errorText(code, Drive.read(c.getErrorStream())));
        c.disconnect();
    }

    @Override
    public void rename(String from, String to) throws IOException {
        try {
            JSONObject body = new JSONObject().put("name", Paths.name(to));
            if (!Paths.parent(from).equals(Paths.parent(to))) {
                String parent = Paths.parent(to);
                String ref = parent.equals(ROOT) ? "/drive/root:" : "/drive/root:" + parent.substring(ROOT.length());
                body.put("parentReference", new JSONObject().put("path", ref));
            }
            od.json("PATCH", item(from), body);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void setModified(String path, long time) {
    }

    @Override
    public String key(String path) {
        return "onedrive";
    }
}
