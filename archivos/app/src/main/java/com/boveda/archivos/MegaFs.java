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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

/**
 * MEGA como una carpeta más de Bóveda (/mega/…). Se baja el árbol de archivos de la cuenta, se
 * descifran los nombres con la llave maestra, y cada archivo se descifra al bajarlo y se cifra al
 * subirlo (AES, como hace la app de MEGA). Borrar manda a la papelera de MEGA.
 */
final class MegaFs implements Fs {
    static final String ROOT = "/mega";
    private static MegaFs instance;
    private static final long REFRESH = 20_000;

    private final Mega mega;
    private final File cacheDir;
    private final Map<String, Node> nodes = new HashMap<>();
    private String rootHandle, trashHandle;
    private long loadedAt;

    /** Un archivo o carpeta de MEGA, ya descifrado. */
    private static final class Node {
        String h, parent, name;
        boolean dir;
        long size, ts;
        int[] key;        // archivos: 8 palabras; carpetas: 4
        final List<Node> kids = new ArrayList<>();

        byte[] aesKey() {
            if (!dir && key.length == 8) {
                return Mega.fromA32(new int[]{key[0] ^ key[4], key[1] ^ key[5], key[2] ^ key[6], key[3] ^ key[7]});
            }
            return Mega.fromA32(new int[]{key[0], key[1], key[2], key[3]});
        }

        byte[] nonce() {
            return Mega.fromA32(new int[]{key[4], key[5]});
        }
    }

    static synchronized MegaFs get(Context c) {
        if (instance == null) instance = new MegaFs(c.getApplicationContext());
        return instance;
    }

    static synchronized void reset() {
        if (instance != null) {
            synchronized (instance) {
                instance.nodes.clear();
                instance.loadedAt = 0;
            }
        }
    }

    static boolean isMega(String path) {
        return path.equals(ROOT) || path.startsWith(ROOT + "/");
    }

    private MegaFs(Context c) {
        mega = Mega.get(c);
        cacheDir = new File(c.getCacheDir(), "mega");
    }

    // ------------------------------------------------------------------ árbol de archivos

    private synchronized void load(boolean force) throws IOException {
        if (!force && !nodes.isEmpty() && System.currentTimeMillis() - loadedAt < REFRESH) return;
        try {
            JSONObject r = (JSONObject) mega.api(new JSONObject().put("a", "f").put("c", 1));
            JSONArray f = r.optJSONArray("f");
            Map<String, Node> fresh = new HashMap<>();
            String root = null, trash = null;
            if (f != null) {
                for (int i = 0; i < f.length(); i++) {
                    JSONObject o = f.getJSONObject(i);
                    int t = o.optInt("t");
                    Node n = new Node();
                    n.h = o.optString("h");
                    n.parent = o.optString("p");
                    n.dir = t != 0;
                    n.size = o.optLong("s");
                    n.ts = o.optLong("ts") * 1000L;
                    if (t == 2) root = n.h;
                    else if (t == 4) trash = n.h;
                    if (t >= 2) {
                        n.name = "";
                        fresh.put(n.h, n);
                        continue;
                    }
                    n.key = nodeKey(o.optString("k"));
                    n.name = n.key == null ? null : attrName(o.optString("a"), n.aesKey());
                    if (n.name == null) continue;   // compartido por otra persona o sin llave: se omite
                    fresh.put(n.h, n);
                }
            }
            for (Node n : fresh.values()) {
                Node p = fresh.get(n.parent);
                if (p != null && n.name != null && !n.name.isEmpty()) p.kids.add(n);
            }
            nodes.clear();
            nodes.putAll(fresh);
            rootHandle = root;
            trashHandle = trash;
            loadedAt = System.currentTimeMillis();
        } catch (JSONException e) {
            throw new IOException("Respuesta inesperada de MEGA");
        }
    }

    /** La llave del nodo para este usuario («usuario:llave/otro:llave»), descifrada con la llave maestra. */
    private int[] nodeKey(String k) {
        try {
            for (String part : k.split("/")) {
                int c = part.indexOf(':');
                if (c < 0) continue;
                if (!part.substring(0, c).equals(mega.user())) continue;
                byte[] enc = Mega.b64d(part.substring(c + 1));
                if (enc.length % 16 != 0) return null;
                return Mega.toA32(Mega.ecb(false, mega.master(), enc));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String attrName(String a, byte[] key) {
        try {
            String s = new String(Mega.cbc(false, key, Mega.b64d(a)), StandardCharsets.UTF_8);
            if (!s.startsWith("MEGA{")) return null;
            int end = s.lastIndexOf('}');
            return new JSONObject(s.substring(4, end + 1)).optString("n", null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String attr(String name, byte[] key) throws IOException {
        try {
            byte[] b = ("MEGA" + new JSONObject().put("n", name)).getBytes(StandardCharsets.UTF_8);
            byte[] padded = new byte[(b.length + 15) / 16 * 16];
            System.arraycopy(b, 0, padded, 0, b.length);
            return Mega.b64e(Mega.cbc(true, key, padded));
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** El nodo de una ruta, o null si no existe. */
    private synchronized Node node(String path) throws IOException {
        load(false);
        if (rootHandle == null) throw new IOException("No se encontró la carpeta principal de MEGA");
        Node cur = nodes.get(rootHandle);
        if (path.equals(ROOT)) return cur;
        for (String part : path.substring(ROOT.length() + 1).split("/")) {
            if (part.isEmpty()) continue;
            Node next = null;
            for (Node k : cur.kids) if (part.equals(k.name)) { next = k; break; }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    private Node need(String path) throws IOException {
        Node n = node(path);
        if (n == null) throw new IOException("No existe en MEGA: " + Paths.name(path));
        return n;
    }

    // ------------------------------------------------------------------ Fs

    @Override
    public List<Entry> list(String dir) throws IOException {
        if (dir.equals(ROOT)) load(true);
        Node d = need(dir);
        List<Entry> out = new ArrayList<>();
        for (Node k : d.kids) out.add(new Entry(Paths.join(dir, k.name), k.name, k.dir, k.dir ? 0 : k.size, k.ts));
        return out;
    }

    @Override
    public Entry stat(String path) throws IOException {
        Node n = node(path);
        if (n == null) return null;
        return new Entry(path, path.equals(ROOT) ? "MEGA" : n.name, n.dir, n.dir ? 0 : n.size, n.ts);
    }

    @Override
    public InputStream in(String path) throws IOException {
        Node n = need(path);
        if (n.dir) throw new IOException("Es una carpeta");
        try {
            JSONObject r = (JSONObject) mega.api(new JSONObject().put("a", "g").put("g", 1).put("ssl", 2).put("n", n.h));
            String url = r.optString("g");
            if (url.isEmpty()) throw new IOException("MEGA no dio el enlace de descarga");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            if (c.getResponseCode() >= 400) {
                int code = c.getResponseCode();
                c.disconnect();
                throw new IOException(code == 509 ? "Se acabó la cuota de descarga de MEGA (espera un rato)" : "MEGA respondió " + code);
            }
            Cipher dec = Mega.ctr(n.aesKey(), n.nonce(), 0);
            return new CipherInputStream(c.getInputStream(), dec);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /** Se escribe a un archivo temporal y, al cerrar, se cifra y se sube a MEGA por partes. */
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

    /** Tamaños de los pedazos de MEGA: 128 KB, 256 KB, … hasta 1 MB, y luego de 1 MB. */
    static long chunkSize(int index) {
        return Math.min(index + 1, 8) * 131072L;
    }

    private void upload(String path, File file) throws IOException {
        Node parent = need(Paths.parent(path));
        Node old = node(path);
        long size = file.length();
        int[] k = Mega.toA32(Mega.random(24));          // 4 de llave + 2 de nonce
        byte[] key = Mega.fromA32(new int[]{k[0], k[1], k[2], k[3]});
        byte[] nonce = Mega.fromA32(new int[]{k[4], k[5]});
        try {
            JSONObject u = (JSONObject) mega.api(new JSONObject().put("a", "u").put("s", size).put("ssl", 2));
            String url = u.optString("p");
            if (url.isEmpty()) throw new IOException("MEGA no abrió la subida");
            Cipher enc = Mega.ctr(key, nonce, 0);
            Cipher block = Cipher.getInstance("AES/ECB/NoPadding");
            block.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"));
            byte[] fileMac = new byte[16];
            byte[] macIv = Mega.fromA32(new int[]{k[4], k[5], k[4], k[5]});
            String handle = null;
            long pos = 0;
            int index = 0;
            try (FileInputStream in = new FileInputStream(file)) {
                do {
                    int len = (int) Math.min(chunkSize(index++), size - pos);
                    byte[] plain = new byte[len];
                    int got = 0;
                    while (got < len) {
                        int r = in.read(plain, got, len - got);
                        if (r < 0) throw new IOException("El archivo cambió mientras se subía");
                        got += r;
                    }
                    // MAC del pedazo (CBC-MAC con el nonce repetido) y MAC del archivo
                    byte[] mac = macIv.clone();
                    for (int i = 0; i < len; i += 16) {
                        for (int j = 0; j < 16; j++) mac[j] ^= i + j < len ? plain[i + j] : 0;
                        mac = block.doFinal(mac);
                    }
                    for (int j = 0; j < 16; j++) fileMac[j] ^= mac[j];
                    fileMac = block.doFinal(fileMac);
                    byte[] data = enc.update(plain);
                    if (data == null) data = new byte[0];
                    String res = post(url + "/" + pos, data);
                    pos += len;
                    if (pos >= size) handle = res;
                    else if (!res.isEmpty() && res.startsWith("-")) throw new Mega.ApiError(Integer.parseInt(res));
                } while (pos < size);
            }
            if (handle == null || handle.isEmpty() || handle.startsWith("-")) {
                throw handle != null && handle.startsWith("-") ? new Mega.ApiError(Integer.parseInt(handle.trim())) : new IOException("MEGA no terminó la subida");
            }
            int[] m = Mega.toA32(fileMac);
            int meta0 = m[0] ^ m[1], meta1 = m[2] ^ m[3];
            int[] packed = {k[0] ^ k[4], k[1] ^ k[5], k[2] ^ meta0, k[3] ^ meta1, k[4], k[5], meta0, meta1};
            JSONObject n = new JSONObject().put("h", handle).put("t", 0).put("a", attr(Paths.name(path), key))
                    .put("k", Mega.b64e(Mega.ecb(true, mega.master(), Mega.fromA32(packed))));
            mega.api(new JSONObject().put("a", "p").put("t", parent.h).put("n", new JSONArray().put(n)));
            // si ya había uno con ese nombre, el viejo se va a la papelera
            if (old != null && !old.dir && trashHandle != null) {
                mega.api(new JSONObject().put("a", "m").put("n", old.h).put("t", trashHandle));
            }
            load(true);
        } catch (JSONException e) {
            throw new IOException(e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String post(String url, byte[] data) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(20000);
                c.setReadTimeout(120000);
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream o = c.getOutputStream()) {
                    o.write(data);
                }
                int code = c.getResponseCode();
                String body = Mega.read(code >= 400 ? c.getErrorStream() : c.getInputStream()).trim();
                c.disconnect();
                if (code >= 500) throw new IOException("MEGA respondió " + code);
                if (code >= 400) throw new IOException("MEGA rechazó la subida (" + code + ")");
                return body;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(1000L << attempt);
                } catch (InterruptedException ignored) {
                }
            }
        }
        throw last;
    }

    @Override
    public ParcelFileDescriptor pfd(String path, String mode) throws IOException {
        if (mode.contains("w")) throw new IOException("En MEGA solo se puede abrir para leer");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        Node n = need(path);
        File f = new File(cacheDir, n.h + "_" + Paths.name(path));
        if (!(f.exists() && f.length() == n.size)) {
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
        if (node(path) != null) return;
        Node parent = need(Paths.parent(path));
        byte[] key = Mega.random(16);
        try {
            JSONObject n = new JSONObject().put("h", "xxxxxxxx").put("t", 1).put("a", attr(Paths.name(path), key))
                    .put("k", Mega.b64e(Mega.ecb(true, mega.master(), key)));
            mega.api(new JSONObject().put("a", "p").put("t", parent.h).put("n", new JSONArray().put(n)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        load(true);
    }

    /** A la papelera de MEGA (se puede recuperar desde la app o la web de MEGA). */
    @Override
    public void delete(String path) throws IOException {
        Node n = node(path);
        if (n == null || path.equals(ROOT)) return;
        try {
            if (trashHandle != null) mega.api(new JSONObject().put("a", "m").put("n", n.h).put("t", trashHandle));
            else mega.api(new JSONObject().put("a", "d").put("n", n.h));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        load(true);
    }

    @Override
    public void rename(String from, String to) throws IOException {
        Node n = need(from);
        try {
            if (!Paths.parent(from).equals(Paths.parent(to))) {
                Node np = need(Paths.parent(to));
                mega.api(new JSONObject().put("a", "m").put("n", n.h).put("t", np.h));
            }
            if (!Paths.name(from).equals(Paths.name(to))) {
                byte[] full = Mega.fromA32(n.key);
                mega.api(new JSONObject().put("a", "a").put("n", n.h).put("attr", attr(Paths.name(to), n.aesKey()))
                        .put("key", Mega.b64e(Mega.ecb(true, mega.master(), full))));
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
        load(true);
    }

    @Override
    public void setModified(String path, long time) {
    }

    @Override
    public String key(String path) {
        return "mega";
    }
}
