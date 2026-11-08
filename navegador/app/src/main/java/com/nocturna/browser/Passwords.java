package com.nocturna.browser;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Contraseñas guardadas. Cada contraseña va cifrada (AES-GCM) con una llave que vive en el
 * chip de seguridad del celular (Android Keystore) y nunca sale de ahí; el archivo está en la
 * carpeta que no entra en las copias de seguridad, así que no viaja a ningún lado.
 */
final class Passwords {
    static final class Entry {
        String host;      // sitio, sin «www.»
        String user;
        String enc;       // contraseña cifrada: base64(iv + datos)
        long created, used;
    }

    private static final String KEY_ALIAS = "nocturna_claves";
    private static final Set<String> SHARED = Set.of("github.io", "gitlab.io", "vercel.app", "netlify.app", "pages.dev",
            "web.app", "firebaseapp.com", "herokuapp.com", "blogspot.com", "glitch.me", "wordpress.com", "wixsite.com",
            "neocities.org", "000webhostapp.com", "onrender.com", "repl.co");
    private static Passwords instance;

    private final File file;
    private final List<Entry> items = new ArrayList<>();
    private final Set<String> never = new LinkedHashSet<>();

    static synchronized Passwords get(Context c) {
        if (instance == null) instance = new Passwords(c.getApplicationContext());
        return instance;
    }

    private Passwords(Context c) {
        file = new File(c.getNoBackupFilesDir(), "claves.json");
        load();
    }

    // ------------------------------------------------------------------ sitios

    /** Sitio de una dirección: el nombre del servidor en minúsculas y sin «www.» (null si no es web). */
    static String host(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        String h = Uri.parse(url).getHost();
        if (h == null || h.isEmpty() || h.equals("nocturna.inicio")) return null;
        h = h.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    /**
     * Dominio principal: «accounts.google.com» → «google.com», «login.live.com» → «live.com»,
     * «banco.com.co» → «banco.com.co». Así la cuenta guardada en un subdominio sirve en el resto.
     */
    static String base(String host) {
        if (host == null) return null;
        if (host.matches("[0-9.]+") || host.contains(":")) return host;   // dirección IP
        String[] p = host.split("\\.");
        if (p.length <= 2) return host;
        String last = p[p.length - 1], second = p[p.length - 2];
        boolean twoLevel = last.length() == 2 && (second.length() <= 3 || second.equals("gob") || second.equals("gov"));
        // sitios donde cada subdominio es de un dueño distinto: no se mezclan
        if (SHARED.contains(second + "." + last)) twoLevel = true;
        int keep = twoLevel ? 3 : 2;
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, p.length - keep); i < p.length; i++) {
            if (b.length() > 0) b.append('.');
            b.append(p[i]);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ consultas

    synchronized List<Entry> all() {
        List<Entry> l = new ArrayList<>(items);
        l.sort(Comparator.comparing((Entry e) -> base(e.host)).thenComparing(e -> e.user.toLowerCase(Locale.ROOT)));
        return l;
    }

    synchronized int size() {
        return items.size();
    }

    /** Cuentas que sirven para este sitio: primero las del mismo servidor, luego las más usadas. */
    synchronized List<Entry> forHost(String host) {
        List<Entry> l = new ArrayList<>();
        if (host == null) return l;
        String b = base(host);
        for (Entry e : items) if (e.host.equals(host) || base(e.host).equals(b)) l.add(e);
        l.sort((x, y) -> {
            boolean ex = x.host.equals(host), ey = y.host.equals(host);
            if (ex != ey) return ex ? -1 : 1;
            return Long.compare(Math.max(y.used, y.created), Math.max(x.used, x.created));
        });
        return l;
    }

    synchronized Entry find(String host, String user) {
        for (Entry e : forHost(host)) if (e.user.equals(user)) return e;
        return null;
    }

    // ------------------------------------------------------------------ cambios

    synchronized Entry save(String host, String user, String pass) throws Exception {
        Entry e = null;
        for (Entry x : items) if (x.host.equals(host) && x.user.equals(user)) e = x;
        if (e == null) {
            e = new Entry();
            e.host = host;
            e.user = user;
            e.created = System.currentTimeMillis();
            items.add(e);
        }
        e.enc = encrypt(pass);
        e.used = System.currentTimeMillis();
        never.remove(host);
        store();
        return e;
    }

    synchronized void update(Entry e, String host, String user, String pass) throws Exception {
        e.host = host;
        e.user = user;
        e.enc = encrypt(pass);
        store();
    }

    synchronized void remove(Entry e) {
        items.remove(e);
        store();
    }

    synchronized void touch(Entry e) {
        e.used = System.currentTimeMillis();
        store();
    }

    synchronized boolean isNever(String host) {
        return never.contains(host);
    }

    synchronized void setNever(String host, boolean on) {
        if (on) never.add(host);
        else never.remove(host);
        store();
    }

    synchronized List<String> neverList() {
        return new ArrayList<>(never);
    }

    // ------------------------------------------------------------------ cifrado

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + data.length + 1];
        all[0] = (byte) iv.length;
        System.arraycopy(iv, 0, all, 1, iv.length);
        System.arraycopy(data, 0, all, 1 + iv.length, data.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    /** La contraseña en claro, o null si no se puede leer (por ejemplo, tras restaurar el celular). */
    String password(Entry e) {
        try {
            byte[] all = Base64.decode(e.enc, Base64.NO_WRAP);
            int n = all[0];
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[n];
            System.arraycopy(all, 1, iv, 0, n);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(all, 1 + n, all.length - 1 - n), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ archivo

    private void load() {
        try {
            if (!file.exists()) return;
            JSONObject o = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONArray a = o.optJSONArray("items");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                Entry e = new Entry();
                e.host = j.getString("h");
                e.user = j.optString("u");
                e.enc = j.getString("p");
                e.created = j.optLong("c");
                e.used = j.optLong("t");
                items.add(e);
            }
            JSONArray n = o.optJSONArray("never");
            if (n != null) for (int i = 0; i < n.length(); i++) never.add(n.getString(i));
        } catch (Exception ignored) {
        }
    }

    private void store() {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : items) {
                a.put(new JSONObject().put("h", e.host).put("u", e.user).put("p", e.enc).put("c", e.created).put("t", e.used));
            }
            JSONObject o = new JSONObject().put("v", 1).put("items", a).put("never", new JSONArray(never));
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ importar / exportar

    /**
     * Lee un CSV exportado por Google (Chrome, el gestor de contraseñas de Google), Samsung Internet,
     * Firefox, Edge o Brave: busca las columnas de dirección, usuario y contraseña por su nombre.
     * Devuelve {nuevas, actualizadas, omitidas}.
     */
    synchronized int[] importCsv(String text) throws Exception {
        List<List<String>> rows = parseCsv(text);
        if (rows.isEmpty()) throw new Exception("El archivo está vacío");
        List<String> head = rows.get(0);
        int iu = -1, iuser = -1, ipass = -1;
        for (int i = 0; i < head.size(); i++) {
            String h = head.get(i).trim().toLowerCase(Locale.ROOT).replace("﻿", "");
            if (iu < 0 && (h.equals("url") || h.equals("origin") || h.equals("website") || h.equals("sitio") || h.equals("login_uri") || h.equals("web site"))) iu = i;
            else if (iuser < 0 && (h.equals("username") || h.equals("user") || h.equals("login") || h.equals("usuario") || h.equals("login_username") || h.equals("email"))) iuser = i;
            else if (ipass < 0 && (h.equals("password") || h.equals("contraseña") || h.equals("login_password") || h.equals("pass"))) ipass = i;
        }
        if (iu < 0 || ipass < 0) throw new Exception("No se reconocen las columnas. Debe tener al menos «url» y «password».");
        int added = 0, updated = 0, skipped = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() <= Math.max(iu, ipass)) { skipped++; continue; }
            String url = row.get(iu).trim();
            if (!url.contains("://")) url = "https://" + url;
            String host = host(url);
            String user = iuser >= 0 && iuser < row.size() ? row.get(iuser) : "";
            String pass = row.get(ipass);
            if (host == null || pass.isEmpty()) { skipped++; continue; }   // apps de Android, filas vacías
            Entry old = null;
            for (Entry x : items) if (x.host.equals(host) && x.user.equals(user)) old = x;
            if (old != null && pass.equals(password(old))) { skipped++; continue; }
            Entry e = old;
            if (e == null) {
                e = new Entry();
                e.host = host;
                e.user = user;
                e.created = System.currentTimeMillis();
                items.add(e);
                added++;
            } else {
                updated++;
            }
            e.enc = encrypt(pass);
        }
        store();
        return new int[]{added, updated, skipped};
    }

    /** CSV con el formato de Google (name,url,username,password,note), que casi todos los gestores leen. */
    synchronized String exportCsv() {
        StringBuilder b = new StringBuilder("name,url,username,password,note\n");
        for (Entry e : all()) {
            String p = password(e);
            if (p == null) continue;
            b.append(csv(e.host)).append(',').append(csv("https://" + e.host + "/")).append(',')
                    .append(csv(e.user)).append(',').append(csv(p)).append(",\n");
        }
        return b.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static List<List<String>> parseCsv(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { f.append('"'); i++; }
                    else q = false;
                } else f.append(c);
            } else if (c == '"') {
                q = true;
            } else if (c == ',') {
                row.add(f.toString());
                f.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                row.add(f.toString());
                f.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else f.append(c);
        }
        if (f.length() > 0 || !row.isEmpty()) {
            row.add(f.toString());
            rows.add(row);
        }
        return rows;
    }
}
