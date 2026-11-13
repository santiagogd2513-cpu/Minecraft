package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.SAXParserFactory;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Un servidor WebDAV (Nextcloud, ownCloud, NAS…) como una carpeta más de Bóveda: /dav/<id>/…
 * Listar, copiar, mover, borrar, renombrar, comprimir y extraer funcionan igual que en el celular.
 */
final class DavFs implements Fs {
    private final NetServers.Server server;
    private final String root;
    private final File cacheDir;
    private final OkHttpClient http, noRedirect;
    private final HttpUrl base;
    private final String auth;
    private final Map<String, Object[]> cache = new HashMap<>();   // carpeta → {hora, lista}

    DavFs(NetServers.Server s, File cacheDir) throws IOException {
        server = s;
        root = s.root();
        this.cacheDir = cacheDir;
        String a = s.address.trim();
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "https://" + a;
        HttpUrl u = HttpUrl.parse(a);
        if (u == null) throw new IOException(L.t("La dirección del servidor no es válida"));
        base = u;
        auth = s.user.isEmpty() ? null : Credentials.basic(s.user, s.pass, java.nio.charset.StandardCharsets.UTF_8);
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true).followSslRedirects(true);
        if (s.insecure) {
            try {
                X509TrustManager all = new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String t) {
                    }

                    public void checkServerTrusted(X509Certificate[] c, String t) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                };
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{all}, new SecureRandom());
                b.sslSocketFactory(ctx.getSocketFactory(), all).hostnameVerifier((h, ss) -> true);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        http = b.build();
        noRedirect = http.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }

    // ------------------------------------------------------------------ direcciones

    /** "/dav/12/a/b" → ["a","b"] */
    private List<String> segments(String path) {
        String r = path.length() <= root.length() ? "" : path.substring(root.length());
        List<String> out = new ArrayList<>();
        for (String p : r.split("/")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private HttpUrl url(String path, boolean dir) {
        HttpUrl.Builder b = base.newBuilder();
        for (String seg : segments(path)) b.addPathSegment(seg);
        if (dir) b.addPathSegment("");   // termina en «/»
        return b.build();
    }

    private Request.Builder req(HttpUrl url) {
        Request.Builder b = new Request.Builder().url(url);
        if (auth != null) b.header("Authorization", auth);
        b.header("User-Agent", "Boveda");
        return b;
    }

    private Response exec(Request r) throws IOException {
        try {
            return http.newCall(r).execute();
        } catch (javax.net.ssl.SSLException e) {
            throw new IOException(L.t("No se pudo hacer la conexión segura. Si el servidor es tuyo y usa un certificado propio, activa «Aceptar certificado no confiable» en sus ajustes."), e);
        } catch (java.net.UnknownHostException e) {
            throw new IOException(L.t("No encuentro el servidor ") + base.host(), e);
        }
    }

    private Response execNoRedirect(Request r) throws IOException {
        try {
            return noRedirect.newCall(r).execute();
        } catch (javax.net.ssl.SSLException e) {
            throw new IOException(L.t("No se pudo hacer la conexión segura. Si el servidor es tuyo y usa un certificado propio, activa «Aceptar certificado no confiable» en sus ajustes."), e);
        } catch (java.net.UnknownHostException e) {
            throw new IOException(L.t("No encuentro el servidor ") + base.host(), e);
        }
    }

    private static IOException fail(Response r, String what) {
        int c = r.code();
        String m = c == 401 ? L.t("Usuario o contraseña incorrectos")
                : c == 403 ? L.t("El servidor no te deja hacer esto (permisos)")
                : c == 404 ? L.t("No existe en el servidor")
                : c == 405 ? L.t("El servidor no admite esta operación o ya existe")
                : c == 423 ? L.t("Está bloqueado por otro programa en el servidor")
                : c == 507 ? L.t("El servidor no tiene espacio") : L.t("Error ") + c + " " + r.message();
        return new IOException(what + ": " + m);
    }

    // ------------------------------------------------------------------ PROPFIND

    private static final String PROPFIND = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>"
            + "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>";

    private static final class Item {
        String href = "";
        boolean dir;
        long size, time;
    }

    private List<Item> propfind(String path, boolean dir, int depth) throws IOException {
        HttpUrl target = url(path, dir);
        Response first = null;
        // PROPFIND no se puede dejar en manos de OkHttp: en una redirección lo cambiaría por GET. Se sigue a mano.
        for (int hop = 0; hop < 4; hop++) {
            Request r = req(target).method("PROPFIND", RequestBody.create(PROPFIND, MediaType.parse("application/xml")))
                    .header("Depth", String.valueOf(depth)).build();
            Response rr = execNoRedirect(r);
            int c = rr.code();
            String loc = rr.header("Location");
            if ((c == 301 || c == 302 || c == 307 || c == 308) && loc != null) {
                HttpUrl next = rr.request().url().resolve(loc);
                rr.close();
                if (next == null) throw new IOException(L.t("El servidor mandó a una dirección que no entiendo"));
                target = next;
                continue;
            }
            first = rr;
            break;
        }
        if (first == null) throw new IOException(L.t("El servidor da demasiadas vueltas al pedir esta carpeta"));
        try (Response resp = first) {
            if (resp.code() == 404) throw new NotFound();
            if (resp.code() != 207 && resp.code() != 200) throw fail(resp, L.t("No pude leer la carpeta"));
            final List<Item> items = new ArrayList<>();
            try (InputStream in = resp.body().byteStream()) {
                SAXParserFactory f = SAXParserFactory.newInstance();
                f.setNamespaceAware(true);
                f.newSAXParser().parse(in, new org.xml.sax.helpers.DefaultHandler() {
                    Item cur;
                    final StringBuilder text = new StringBuilder();
                    final SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

                    @Override
                    public void startElement(String uri, String local, String q, org.xml.sax.Attributes a) {
                        text.setLength(0);
                        if (local.equals("response")) cur = new Item();
                        else if (local.equals("collection") && cur != null) cur.dir = true;
                    }

                    @Override
                    public void characters(char[] ch, int s, int l) {
                        text.append(ch, s, l);
                    }

                    @Override
                    public void endElement(String uri, String local, String q) {
                        if (cur == null) return;
                        String t = text.toString().trim();
                        switch (local) {
                            case "href" -> cur.href = t;
                            case "getcontentlength" -> {
                                try {
                                    cur.size = Long.parseLong(t);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            case "getlastmodified" -> {
                                try {
                                    fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
                                    cur.time = fmt.parse(t).getTime();
                                } catch (Exception ignored) {
                                }
                            }
                            case "response" -> {
                                items.add(cur);
                                cur = null;
                            }
                            default -> {
                            }
                        }
                    }
                });
            } catch (org.xml.sax.SAXException | javax.xml.parsers.ParserConfigurationException e) {
                throw new IOException(L.t("La respuesta del servidor no se entiende: ") + e.getMessage(), e);
            }
            return items;
        }
    }

    private static final class NotFound extends IOException {
        NotFound() {
            super("404");
        }
    }

    /** El nombre (decodificado) de la última parte de un href, o "" si es la raíz. */
    private static String lastName(String href) {
        String p = href;
        try {
            p = new java.net.URI(href).getPath();
        } catch (Exception ignored) {
        }
        if (p == null) return "";
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public List<Entry> list(String dir) throws IOException {
        List<Item> items;
        try {
            items = propfind(dir, true, 1);
        } catch (NotFound e) {
            throw new IOException(L.t("La carpeta ya no existe en el servidor"));
        }
        List<Entry> out = new ArrayList<>();
        // el primero es la propia carpeta: se salta (el que tenga el mismo nombre que ella)
        String selfName = lastName(url(dir, true).encodedPath());
        boolean skippedSelf = false;
        for (Item it : items) {
            String name = lastName(it.href);
            if (name.isEmpty()) continue;
            if (!skippedSelf && it.dir && name.equals(selfName) && isSelf(it.href, dir)) {
                skippedSelf = true;
                continue;
            }
            out.add(new Entry(Paths.join(dir, name), name, it.dir, it.dir ? 0 : it.size, it.time));
        }
        synchronized (cache) {
            cache.put(dir, new Object[]{System.currentTimeMillis(), out});
        }
        return out;
    }

    /** ¿Este href es el de la carpeta que se pidió (y no uno de sus hijos)? */
    private boolean isSelf(String href, String dir) {
        String want = url(dir, true).encodedPath();
        String got = href;
        try {
            got = new java.net.URI(href).getRawPath();
        } catch (Exception ignored) {
        }
        if (got == null) return false;
        return trimSlash(got).equals(trimSlash(want)) || trimSlash(decode(got)).equals(trimSlash(decode(want)));
    }

    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String decode(String s) {
        try {
            return new java.net.URI(s).getPath();
        } catch (Exception e) {
            return s;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Entry> cachedList(String dir) throws IOException {
        synchronized (cache) {
            Object[] c = cache.get(dir);
            if (c != null && System.currentTimeMillis() - (Long) c[0] < 3000) return (List<Entry>) c[1];
        }
        return list(dir);
    }

    /** Una consulta directa por archivo (no se lista toda la carpeta cada vez: con miles de archivos sería eterno). */
    @Override
    public Entry stat(String path) throws IOException {
        if (path.equals(root) || segments(path).isEmpty()) return new Entry(path, server.name, true, 0, 0);
        try {
            List<Item> items = propfind(path, false, 0);
            if (items.isEmpty()) return null;
            Item it = items.get(0);
            return new Entry(path, Paths.name(path), it.dir, it.dir ? 0 : it.size, it.time);
        } catch (NotFound e) {
            return null;
        }
    }

    @Override
    public InputStream in(String path) throws IOException {
        Response r = exec(req(url(path, false)).get().build());
        if (!r.isSuccessful()) {
            try {
                throw fail(r, L.t("No pude abrir ") + Paths.name(path));
            } finally {
                r.close();
            }
        }
        return new FilterInputStream(r.body().byteStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    r.close();
                }
            }
        };
    }

    /** Se escribe a un archivo temporal y, al cerrar, se sube entero (así una falla no deja un archivo a medias en el servidor). */
    @Override
    public OutputStream out(String path) throws IOException {
        forget(path);
        File tmp = File.createTempFile("dav", ".tmp", cacheDir);
        return new FileOutputStream(tmp) {
            boolean done;

            @Override
            public void close() throws IOException {
                if (done) return;
                done = true;
                super.close();
                try {
                    Request r = req(url(path, false)).put(RequestBody.create(tmp, MediaType.parse("application/octet-stream"))).build();
                    try (Response resp = exec(r)) {
                        if (!resp.isSuccessful()) throw fail(resp, L.t("No pude guardar ") + Paths.name(path));
                    }
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    forget(path);
                }
            }
        };
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException(L.t("En WebDAV solo se puede abrir para leer"));
        File dir = new File(cacheDir, "dav");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, server.id + "_" + Integer.toHexString(path.hashCode()) + "_" + Paths.name(path));
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
        forget(path);
        try (Response r = exec(req(url(path, true)).method("MKCOL", null).build())) {
            if (!r.isSuccessful()) throw fail(r, L.t("No pude crear la carpeta"));
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Entry e = stat(path);
        if (e == null) return;
        try (Response r = exec(req(url(path, e.dir)).delete().build())) {
            if (!r.isSuccessful() && r.code() != 404) throw fail(r, L.t("No pude borrar ") + Paths.name(path));
        }
        forget(path);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        forget(from);
        forget(to);
        Entry e = stat(from);
        boolean dir = e != null && e.dir;
        Request r = req(url(from, dir)).method("MOVE", null).header("Destination", url(to, dir).toString()).header("Overwrite", "F").build();
        try (Response resp = exec(r)) {
            if (!resp.isSuccessful()) throw fail(resp, L.t("No pude mover ") + Paths.name(from));
        } finally {
            forget(from);
            forget(to);
        }
    }

    @Override
    public void setModified(String path, long time) {
        // WebDAV no deja cambiar la fecha de forma estándar: se queda la que pone el servidor
    }

    @Override
    public int maxParallel() {
        return 6;
    }

    @Override
    public String key(String path) {
        return "dav:" + server.id;
    }

    private void forget(String path) {
        synchronized (cache) {
            cache.remove(Paths.parent(path));
            cache.remove(path);
        }
    }

    /** Para probar la conexión al guardar el servidor. */
    void test() throws IOException {
        propfind(root, true, 0);
    }
}
