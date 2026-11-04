package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cliente de la API de MediaFire (versión 1.5): iniciar sesión, listar, crear carpetas, renombrar,
 * mover, borrar (a la papelera), enlaces, descargar y subir (simple, por partes y «al instante»).
 * La contraseña se guarda cifrada con el almacén de claves de Android.
 */
final class MediaFire {
    static final String BASE = "https://www.mediafire.com/api/1.5/";
    static final String DEFAULT_APP_ID = "42511";
    static final String ROOT = "myfiles";
    private static MediaFire instance;

    private final SharedPreferences prefs;
    private String email, password, appId = DEFAULT_APP_ID, apiKey = "";
    private volatile String token;

    /** Un archivo o carpeta en MediaFire. */
    static final class Item {
        final String key;       // folderkey o quickkey
        final String name;
        final boolean folder;
        final long size;
        final long created;
        final String hash;      // SHA-256 del archivo (para verificar descargas)
        final int count;        // carpetas: cuántas cosas tiene

        Item(String key, String name, boolean folder, long size, long created, String hash, int count) {
            this.key = key;
            this.name = name;
            this.folder = folder;
            this.size = size;
            this.created = created;
            this.hash = hash;
            this.count = count;
        }

        /** Para mostrarlo con los mismos íconos que los archivos locales. */
        Entry asEntry() {
            return new Entry("mf:" + key, name, folder, size, created);
        }
    }

    static final class Account {
        final String name, email;
        final long used, limit;

        Account(String name, String email, long used, long limit) {
            this.name = name;
            this.email = email;
            this.used = used;
            this.limit = limit;
        }
    }

    static synchronized MediaFire get(Context c) {
        if (instance == null) instance = new MediaFire(c.getApplicationContext());
        return instance;
    }

    private MediaFire(Context c) {
        prefs = c.getSharedPreferences("mediafire", Context.MODE_PRIVATE);
        email = prefs.getString("email", null);
        appId = prefs.getString("app", DEFAULT_APP_ID);
        apiKey = prefs.getString("key", "");
        String enc = prefs.getString("clave", null);
        if (enc != null) {
            try {
                password = decrypt(enc);
            } catch (Exception e) {
                password = null;
            }
        }
    }

    boolean loggedIn() {
        return email != null && password != null;
    }

    String email() {
        return email;
    }

    // ------------------------------------------------------------------ sesión

    void login(String mail, String pass, String app, String key) throws IOException {
        String a = app == null || app.trim().isEmpty() ? DEFAULT_APP_ID : app.trim();
        String k = key == null ? "" : key.trim();
        String t = requestToken(mail.trim(), pass, a, k);
        email = mail.trim();
        password = pass;
        appId = a;
        apiKey = k;
        token = t;
        try {
            prefs.edit().putString("email", email).putString("clave", encrypt(pass))
                    .putString("app", appId).putString("key", apiKey).apply();
        } catch (Exception e) {
            throw new IOException("No se pudo guardar la sesión de forma segura", e);
        }
    }

    void logout() {
        email = null;
        password = null;
        token = null;
        prefs.edit().clear().apply();
    }

    private String requestToken(String mail, String pass, String app, String key) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("email", mail);
        p.put("password", pass);
        p.put("application_id", app);
        p.put("signature", sha1(mail + pass + app + key));
        p.put("token_version", "1");
        JSONObject r = post("user/get_session_token.php", p);
        String t = r.optString("session_token", "");
        if (t.isEmpty()) throw new IOException("MediaFire no devolvió la sesión");
        return t;
    }

    private synchronized String token() throws IOException {
        if (token == null) {
            if (!loggedIn()) throw new IOException("Primero inicia sesión en MediaFire");
            token = requestToken(email, password, appId, apiKey);
        }
        return token;
    }

    /** Llama a la API con la sesión; si la sesión venció, inicia sesión otra vez y reintenta. */
    JSONObject call(String action, Map<String, String> params) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, String> p = new LinkedHashMap<>(params);
            p.put("session_token", token());
            try {
                return post(action, p);
            } catch (ApiError e) {
                if (attempt == 0 && e.sessionProblem()) {
                    synchronized (this) {
                        token = null;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw new IOException("MediaFire no respondió");
    }

    static final class ApiError extends IOException {
        final int code;

        ApiError(int code, String msg) {
            super(msg);
            this.code = code;
        }

        boolean sessionProblem() {
            String m = getMessage() == null ? "" : getMessage().toLowerCase();
            return code == 105 || code == 106 || code == 127 || m.contains("session") || m.contains("token");
        }
    }

    private JSONObject post(String action, Map<String, String> params) throws IOException {
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("response_format", "json");
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + action).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(40000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("User-Agent", "Boveda/1.0 (Android)");
        try (OutputStream o = c.getOutputStream()) {
            o.write(form(p).getBytes(StandardCharsets.UTF_8));
        }
        return parse(c);
    }

    private static String form(Map<String, String> p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private static JSONObject parse(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = in == null ? "" : readAll(in);
        c.disconnect();
        try {
            JSONObject r = new JSONObject(body).getJSONObject("response");
            if ("Error".equalsIgnoreCase(r.optString("result"))) {
                throw new ApiError(r.optInt("error"), friendly(r.optInt("error"), r.optString("message", "Error de MediaFire")));
            }
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException("MediaFire respondió algo inesperado (" + code + ")");
        }
    }

    private static String friendly(int code, String msg) {
        return switch (code) {
            case 107 -> "Correo o contraseña incorrectos";
            case 105, 106, 127 -> "La sesión venció";
            case 114 -> "Esa carpeta ya no existe";
            case 110, 111 -> "Ese archivo ya no existe";
            case 129 -> "No hay espacio suficiente en tu cuenta";
            default -> msg + " (código " + code + ")";
        };
    }

    private static JSONObject obj(JSONObject r, String k) {
        JSONObject o = r.optJSONObject(k);
        return o == null ? new JSONObject() : o;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        in.close();
        return bo.toString("UTF-8");
    }

    // ------------------------------------------------------------------ cuenta y carpetas

    Account account() throws IOException {
        JSONObject u = obj(call("user/get_info.php", new LinkedHashMap<>()), "user_info");
        return new Account(u.optString("display_name", u.optString("first_name", "")), u.optString("email", email),
                u.optLong("used_storage_size"), u.optLong("storage_limit"));
    }

    List<Item> list(String folderKey) throws IOException {
        List<Item> out = new ArrayList<>();
        for (String type : new String[]{"folders", "files"}) {
            for (int chunk = 1; chunk < 1000; chunk++) {
                Map<String, String> p = new LinkedHashMap<>();
                if (folderKey != null && !ROOT.equals(folderKey)) p.put("folder_key", folderKey);
                p.put("content_type", type);
                p.put("chunk", String.valueOf(chunk));
                p.put("chunk_size", "1000");
                JSONObject fc = obj(call("folder/get_content.php", p), "folder_content");
                JSONArray a = fc.optJSONArray(type);
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o == null) continue;
                        if (type.equals("folders")) {
                            out.add(new Item(o.optString("folderkey"), o.optString("name"), true, 0, date(o.optString("created")), null,
                                    o.optInt("file_count") + o.optInt("folder_count")));
                        } else {
                            out.add(new Item(o.optString("quickkey"), o.optString("filename"), false, o.optLong("size"),
                                    date(o.optString("created")), o.optString("hash", null), 0));
                        }
                    }
                }
                if (!"yes".equalsIgnoreCase(fc.optString("more_chunks"))) break;
            }
        }
        return out;
    }

    String createFolder(String parentKey, String name) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("foldername", name);
        if (parentKey != null && !ROOT.equals(parentKey)) p.put("parent_key", parentKey);
        p.put("action_on_duplicate", "keep");
        JSONObject r = call("folder/create.php", p);
        String k = r.optString("folder_key", r.optString("folderkey", ""));
        if (k.isEmpty()) throw new IOException("MediaFire no creó la carpeta");
        return k;
    }

    void rename(Item it, String name) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        if (it.folder) {
            p.put("folder_key", it.key);
            p.put("foldername", name);
            call("folder/update.php", p);
        } else {
            p.put("quick_key", it.key);
            p.put("filename", name);
            call("file/update.php", p);
        }
    }

    /** A la papelera de MediaFire (se puede recuperar desde la web). */
    void delete(Item it) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        if (it.folder) {
            p.put("folder_key", it.key);
            call("folder/delete.php", p);
        } else {
            p.put("quick_key", it.key);
            call("file/delete.php", p);
        }
    }

    void move(Item it, String destFolderKey) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        String dest = destFolderKey == null ? ROOT : destFolderKey;
        if (it.folder) {
            p.put("folder_key_src", it.key);
            p.put("folder_key_dst", dest);
            call("folder/move.php", p);
        } else {
            p.put("quick_key", it.key);
            p.put("folder_key", dest);
            call("file/move.php", p);
        }
    }

    /** Enlace para compartir (la página de descarga de MediaFire). */
    String shareLink(Item it) throws IOException {
        if (it.folder) return "https://www.mediafire.com/folder/" + it.key;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("quick_key", it.key);
        p.put("link_type", "normal_download");
        JSONArray links = call("file/get_links.php", p).optJSONArray("links");
        if (links != null && links.length() > 0) {
            String l = links.optJSONObject(0).optString("normal_download", "");
            if (!l.isEmpty()) return l;
        }
        return "https://www.mediafire.com/file/" + it.key;
    }

    /** Enlace directo para bajar el archivo. Si la cuenta no lo da, se saca de la página de descarga. */
    String downloadLink(Item it) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("quick_key", it.key);
        p.put("link_type", "direct_download");
        try {
            JSONArray links = call("file/get_links.php", p).optJSONArray("links");
            if (links != null && links.length() > 0) {
                String l = links.optJSONObject(0).optString("direct_download", "");
                if (l.startsWith("http")) return l;
            }
        } catch (ApiError ignored) {
            // algunas cuentas no tienen enlace directo: se usa la página normal
        }
        String page = shareLink(it);
        HttpURLConnection c = (HttpURLConnection) new URL(page).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        String html = readAll(c.getInputStream());
        c.disconnect();
        Matcher m = Pattern.compile("href=\"(https?://download[^\"]+)\"").matcher(html);
        if (m.find()) return m.group(1).replace("&amp;", "&");
        throw new IOException("MediaFire no dio el enlace de descarga de «" + it.name + "»");
    }

    // ------------------------------------------------------------------ subir

    static final long SIMPLE_LIMIT = 4L * 1024 * 1024;

    static final class UploadCheck {
        boolean hashExists;
        int units;
        long unitSize;
        int[] bitmap = new int[0];
        String resumeKey;
    }

    UploadCheck check(String folderKey, String name, long size, String sha256) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("filename", name);
        if (folderKey != null && !ROOT.equals(folderKey)) p.put("folder_key", folderKey);
        p.put("size", String.valueOf(size));
        p.put("hash", sha256);
        p.put("resumable", "yes");
        JSONObject r = call("upload/check.php", p);
        UploadCheck u = new UploadCheck();
        u.hashExists = "yes".equalsIgnoreCase(r.optString("hash_exists"));
        JSONObject ru = r.optJSONObject("resumable_upload");
        if (ru != null) {
            u.units = ru.optInt("number_of_units");
            u.unitSize = ru.optLong("unit_size");
            u.resumeKey = ru.optString("upload_key", null);
            JSONObject bm = ru.optJSONObject("bitmap");
            if (bm != null) {
                JSONArray w = bm.optJSONArray("words");
                if (w != null) {
                    u.bitmap = new int[w.length()];
                    for (int i = 0; i < w.length(); i++) u.bitmap[i] = w.optInt(i);
                }
            }
        }
        return u;
    }

    /** Si MediaFire ya tiene ese archivo (mismo contenido), se agrega a la cuenta sin subirlo. */
    String instant(String folderKey, String name, long size, String sha256) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("hash", sha256);
        p.put("size", String.valueOf(size));
        p.put("filename", name);
        if (folderKey != null && !ROOT.equals(folderKey)) p.put("folder_key", folderKey);
        p.put("action_on_duplicate", "keep");
        return call("upload/instant.php", p).optString("quickkey", "");
    }

    private String uploadUrl(String action, String folderKey) throws IOException {
        StringBuilder u = new StringBuilder(BASE).append(action).append("?response_format=json&action_on_duplicate=keep&session_token=")
                .append(URLEncoder.encode(token(), "UTF-8"));
        if (folderKey != null && !ROOT.equals(folderKey)) u.append("&folder_key=").append(URLEncoder.encode(folderKey, "UTF-8"));
        return u.toString();
    }

    private static String header(String s) {
        // los encabezados HTTP van en Latin-1: el nombre en UTF-8 viaja así
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    interface Progress {
        void sent(long bytes) throws IOException;
    }

    /** Sube un archivo pequeño de una vez. Devuelve la clave para consultar cuándo termina. */
    String uploadSimple(String folderKey, String name, long size, String sha256, InputStream data, Progress pr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(uploadUrl("upload/simple.php", folderKey)).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(size);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setRequestProperty("x-filename", header(name));
        c.setRequestProperty("x-filesize", String.valueOf(size));
        c.setRequestProperty("x-filehash", sha256);
        sendBody(c, data, size, pr);
        return uploadKey(parse(c));
    }

    /** Sube una parte de un archivo grande. */
    String uploadUnit(String folderKey, String name, long size, String sha256, int unit, byte[] buf, int len, Progress pr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(uploadUrl("upload/resumable.php", folderKey)).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(len);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setRequestProperty("x-filename", header(name));
        c.setRequestProperty("x-filesize", String.valueOf(size));
        c.setRequestProperty("x-filehash", sha256);
        c.setRequestProperty("x-unit-id", String.valueOf(unit));
        c.setRequestProperty("x-unit-hash", hex(MessageDigestHolder.sha256(buf, len)));
        c.setRequestProperty("x-unit-size", String.valueOf(len));
        try (OutputStream o = c.getOutputStream()) {
            int off = 0;
            while (off < len) {
                int n = Math.min(256 * 1024, len - off);
                o.write(buf, off, n);
                off += n;
                if (pr != null) pr.sent(n);
            }
        }
        return uploadKey(parse(c));
    }

    private static void sendBody(HttpURLConnection c, InputStream data, long size, Progress pr) throws IOException {
        try (OutputStream o = c.getOutputStream()) {
            byte[] buf = new byte[256 * 1024];
            long left = size;
            int r;
            while (left > 0 && (r = data.read(buf, 0, (int) Math.min(buf.length, left))) > 0) {
                o.write(buf, 0, r);
                left -= r;
                if (pr != null) pr.sent(r);
            }
            if (left > 0) throw new IOException("El archivo cambió mientras se subía");
        }
    }

    private static String uploadKey(JSONObject r) throws IOException {
        JSONObject d = r.optJSONObject("doupload");
        String k = d == null ? "" : d.optString("key", "");
        if (k.isEmpty()) {
            String res = d == null ? "?" : d.optString("result");
            throw new IOException("MediaFire rechazó la subida (resultado " + res + ")");
        }
        return k;
    }

    /** Espera a que MediaFire termine de procesar la subida. Devuelve la clave del archivo nuevo. */
    String waitUpload(String key) throws IOException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("key", key);
        for (int i = 0; i < 600; i++) {
            JSONObject d = call("upload/poll_upload.php", p).optJSONObject("doupload");
            if (d != null) {
                String err = d.optString("fileerror", "");
                if (!err.isEmpty() && !err.equals("0")) throw new IOException("MediaFire no aceptó el archivo (error " + err + ")");
                if (d.optInt("status") == 99) return d.optString("quickkey", "");
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                throw new IOException("Cancelado");
            }
        }
        throw new IOException("MediaFire tardó demasiado en procesar la subida");
    }

    // ------------------------------------------------------------------ utilidades

    static final class MessageDigestHolder {
        static byte[] sha256(byte[] b, int len) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(b, 0, len);
                return md.digest();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
        return sb.toString();
    }

    private static String sha1(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long date(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("America/Chicago")); // la hora de los servidores de MediaFire
            return f.parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ cifrado de la contraseña

    private static final String ALIAS = "boveda_mediafire";

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return g.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP);
    }

    private static String decrypt(String stored) throws Exception {
        String[] parts = stored.split(":");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
