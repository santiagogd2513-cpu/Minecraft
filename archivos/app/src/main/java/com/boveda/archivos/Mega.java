package com.boveda.archivos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cuenta de MEGA: inicio de sesión con correo y contraseña (y el código de verificación en dos
 * pasos si está activado), y llamadas a la API. MEGA cifra todo en el celular: la contraseña nunca
 * sale de aquí, solo una huella derivada de ella. Se guarda la sesión y la llave maestra (cifradas
 * con {@link Secure}), nunca la contraseña.
 */
final class Mega {
    private static final String API = "https://g.api.mega.co.nz/cs";
    private static Mega instance;
    private static final SecureRandom RND = new SecureRandom();

    private final SharedPreferences prefs;
    private String sid, user;
    private byte[] master;
    private long seq = RND.nextInt(Integer.MAX_VALUE);

    /** Error de la API de MEGA (código negativo). */
    static final class ApiError extends IOException {
        final int code;

        ApiError(int code) {
            super(text(code));
            this.code = code;
        }
    }

    static synchronized Mega get(Context c) {
        if (instance == null) instance = new Mega(c.getApplicationContext());
        return instance;
    }

    private Mega(Context c) {
        prefs = c.getSharedPreferences("mega", Context.MODE_PRIVATE);
        sid = Secure.decrypt(prefs.getString("sesion", ""));
        String m = Secure.decrypt(prefs.getString("llave", ""));
        master = m.isEmpty() ? null : b64d(m);
        user = prefs.getString("usuario", "");
    }

    boolean loggedIn() {
        return sid != null && !sid.isEmpty() && master != null;
    }

    String email() {
        return prefs.getString("correo", "");
    }

    byte[] master() {
        return master;
    }

    String user() {
        return user;
    }

    void logout() {
        try {
            if (loggedIn()) api(new JSONObject().put("a", "sml"));
        } catch (Exception ignored) {
        }
        sid = "";
        master = null;
        user = "";
        prefs.edit().clear().apply();
        MegaFs.reset();
    }

    // ------------------------------------------------------------------ iniciar sesión

    /**
     * Inicia sesión. Si la cuenta tiene verificación en dos pasos y no se pasó el código, lanza
     * ApiError(-26) para pedirlo.
     */
    void login(String email, String password, String mfa) throws IOException {
        try {
            email = email.trim().toLowerCase(java.util.Locale.ROOT);
            JSONObject pre = (JSONObject) api(new JSONObject().put("a", "us0").put("user", email), false);
            byte[] passKey;
            String uh;
            if (pre.optInt("v", 1) == 2) {
                byte[] derived = pbkdf2(password.getBytes(StandardCharsets.UTF_8), b64d(pre.getString("s")), 100000, 32);
                passKey = java.util.Arrays.copyOfRange(derived, 0, 16);
                uh = b64e(java.util.Arrays.copyOfRange(derived, 16, 32));
            } else {
                passKey = legacyKey(password);
                uh = legacyHash(email, passKey);
            }
            JSONObject req = new JSONObject().put("a", "us").put("user", email).put("uh", uh);
            if (mfa != null && !mfa.isEmpty()) req.put("mfa", mfa.trim());
            JSONObject r = (JSONObject) api(req, false);
            byte[] mk = ecb(false, passKey, b64d(r.getString("k")));
            String session;
            if (r.has("csid")) {
                byte[] privk = ecb(false, mk, b64d(r.getString("privk")));
                BigInteger[] parts = new BigInteger[4];
                int pos = 0;
                for (int i = 0; i < 4; i++) {
                    int bits = (privk[pos] & 0xFF) << 8 | (privk[pos + 1] & 0xFF);
                    int len = (bits + 7) / 8;
                    parts[i] = new BigInteger(1, java.util.Arrays.copyOfRange(privk, pos + 2, pos + 2 + len));
                    pos += 2 + len;
                }
                BigInteger p = parts[0], q = parts[1], d = parts[2];
                byte[] cs = b64d(r.getString("csid"));
                int bits = (cs[0] & 0xFF) << 8 | (cs[1] & 0xFF);
                BigInteger c = new BigInteger(1, java.util.Arrays.copyOfRange(cs, 2, 2 + (bits + 7) / 8));
                byte[] m = c.modPow(d, p.multiply(q)).toByteArray();
                int start = 0;
                while (start < m.length - 1 && m[start] == 0) start++;
                session = b64e(java.util.Arrays.copyOfRange(m, start, start + 43));
            } else if (r.has("tsid")) {
                session = r.getString("tsid");
            } else {
                throw new IOException("MEGA no devolvió una sesión");
            }
            sid = session;
            master = mk;
            JSONObject ug = (JSONObject) api(new JSONObject().put("a", "ug"));
            user = ug.optString("u");
            prefs.edit().putString("sesion", Secure.encrypt(sid)).putString("llave", Secure.encrypt(b64e(master)))
                    .putString("usuario", user).putString("correo", ug.optString("email", email)).apply();
            MegaFs.reset();
        } catch (JSONException e) {
            throw new IOException("Respuesta inesperada de MEGA: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ API

    Object api(JSONObject cmd) throws IOException {
        return api(cmd, true);
    }

    /** Manda un comando y devuelve su respuesta (objeto, arreglo, texto o número ≥ 0). */
    synchronized Object api(JSONObject cmd, boolean withSession) throws IOException {
        String hashcash = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            String url = API + "?id=" + (seq++) + (withSession && sid != null && !sid.isEmpty() ? "&sid=" + sid : "");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Boveda");
            if (hashcash != null) c.setRequestProperty("X-Hashcash", hashcash);
            byte[] body = new JSONArray().put(cmd).toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            int code = c.getResponseCode();
            if (code == 402 && c.getHeaderField("X-Hashcash") != null) {
                // MEGA pide una prueba de trabajo antes de dejar entrar (protección contra robots)
                hashcash = solveHashcash(c.getHeaderField("X-Hashcash"));
                c.disconnect();
                continue;
            }
            if (code >= 500) {
                c.disconnect();
                sleep(attempt);
                continue;
            }
            String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream()).trim();
            c.disconnect();
            if (code >= 400) throw new IOException("MEGA respondió " + code);
            Object r;
            try {
                Object top = new org.json.JSONTokener(text).nextValue();
                r = top instanceof JSONArray ? ((JSONArray) top).opt(0) : top;
            } catch (JSONException e) {
                throw new IOException("Respuesta inesperada de MEGA");
            }
            if (r instanceof Number && ((Number) r).intValue() < 0) {
                int e = ((Number) r).intValue();
                if (e == -3 || e == -4 || e == -18) {
                    sleep(attempt);
                    continue;
                }
                if (e == -15 && withSession) {
                    sid = "";
                    prefs.edit().remove("sesion").apply();
                }
                throw new ApiError(e);
            }
            return r;
        }
        throw new IOException("MEGA está ocupado, inténtalo en un rato");
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(Math.min(8000, 500L << attempt));
        } catch (InterruptedException ignored) {
        }
    }

    static String text(int code) {
        switch (code) {
            case -2: return "MEGA rechazó la petición";
            case -9: return "Correo o contraseña incorrectos (o el archivo ya no existe)";
            case -11: return "MEGA no dio permiso";
            case -13: return "La subida quedó incompleta";
            case -14: return "Error de cifrado";
            case -15: return "La sesión de MEGA se cerró: vuelve a iniciar sesión";
            case -16: return "La cuenta de MEGA está bloqueada";
            case -17: return "Se acabó el espacio o la cuota de transferencia de MEGA";
            case -26: return "Falta el código de verificación en dos pasos";
            default: return "MEGA respondió con el error " + code;
        }
    }

    static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    // ------------------------------------------------------------------ prueba de trabajo (X-Hashcash)

    static String solveHashcash(String header) throws IOException {
        String[] p = header.split(":");
        if (p.length < 4 || !p[0].equals("1")) throw new IOException("MEGA pidió una verificación desconocida");
        int e = Integer.parseInt(p[1]);
        long threshold = ((long) (((e & 63) << 1) + 1)) << ((e >> 6) * 7 + 3);
        byte[] token = b64d(p[3]);
        byte[] buf = new byte[4 + 262144 * token.length];
        for (int i = 0; i < 262144; i++) System.arraycopy(token, 0, buf, 4 + i * token.length, token.length);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (long n = 0; n < 1L << 32; n++) {
                buf[0] = (byte) (n >>> 24);
                buf[1] = (byte) (n >>> 16);
                buf[2] = (byte) (n >>> 8);
                buf[3] = (byte) n;
                byte[] h = sha.digest(buf);
                long v = (h[0] & 0xFFL) << 24 | (h[1] & 0xFFL) << 16 | (h[2] & 0xFFL) << 8 | (h[3] & 0xFFL);
                if (v <= threshold) return "1:" + p[3] + ":" + b64e(java.util.Arrays.copyOf(buf, 4));
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        throw new IOException("No se pudo resolver la verificación de MEGA");
    }

    // ------------------------------------------------------------------ cifrado

    static byte[] pbkdf2(byte[] password, byte[] salt, int iterations, int len) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA512"));
            byte[] out = new byte[len];
            int blocks = (len + 63) / 64;
            for (int b = 1; b <= blocks; b++) {
                mac.update(salt);
                mac.update(new byte[]{(byte) (b >>> 24), (byte) (b >>> 16), (byte) (b >>> 8), (byte) b});
                byte[] u = mac.doFinal(), t = u.clone();
                for (int i = 1; i < iterations; i++) {
                    u = mac.doFinal(u);
                    for (int k = 0; k < t.length; k++) t[k] ^= u[k];
                }
                System.arraycopy(t, 0, out, (b - 1) * 64, Math.min(64, len - (b - 1) * 64));
            }
            return out;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /** Llave de las cuentas viejas (antes de 2017): 65 536 rondas de AES sobre la contraseña. */
    static byte[] legacyKey(String password) throws IOException {
        int[] pw = toA32(password.getBytes(StandardCharsets.UTF_8));
        byte[] pkey = fromA32(new int[]{0x93C467E3, 0x7DB0C7A4, 0xD1BE3F81, 0x0152CB56});
        try {
            Cipher[] ciphers = new Cipher[(pw.length + 3) / 4];
            for (int j = 0; j < ciphers.length; j++) {
                int[] k = new int[4];
                for (int i = 0; i < 4; i++) if (j * 4 + i < pw.length) k[i] = pw[j * 4 + i];
                ciphers[j] = Cipher.getInstance("AES/ECB/NoPadding");
                ciphers[j].init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fromA32(k), "AES"));
            }
            for (int r = 0; r < 65536; r++) for (Cipher c : ciphers) pkey = c.doFinal(pkey);
            return pkey;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    static String legacyHash(String email, byte[] key) throws IOException {
        int[] s = toA32(email.getBytes(StandardCharsets.UTF_8));
        int[] h = new int[4];
        for (int i = 0; i < s.length; i++) h[i % 4] ^= s[i];
        byte[] hb = fromA32(h);
        for (int r = 0; r < 0x4000; r++) hb = ecb(true, key, hb);
        int[] w = toA32(hb);
        return b64e(fromA32(new int[]{w[0], w[2]}));
    }

    static byte[] ecb(boolean encrypt, byte[] key, byte[] data) throws IOException {
        try {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
            c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(data);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    static byte[] cbc(boolean encrypt, byte[] key, byte[] data) throws IOException {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
            return c.doFinal(data);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /** AES-CTR de MEGA: contador = nonce de 8 bytes + número de bloque de 8 bytes (empieza en offset/16). */
    static Cipher ctr(byte[] key, byte[] nonce, long offset) throws IOException {
        try {
            byte[] iv = new byte[16];
            System.arraycopy(nonce, 0, iv, 0, 8);
            long block = offset / 16;
            for (int i = 0; i < 8; i++) iv[15 - i] = (byte) (block >>> (8 * i));
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            int skip = (int) (offset % 16);
            if (skip > 0) c.update(new byte[skip]);
            return c;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    static byte[] random(int n) {
        byte[] b = new byte[n];
        RND.nextBytes(b);
        return b;
    }

    static int[] toA32(byte[] b) {
        int[] r = new int[(b.length + 3) / 4];
        for (int i = 0; i < b.length; i++) r[i / 4] |= (b[i] & 0xFF) << (24 - 8 * (i % 4));
        return r;
    }

    static byte[] fromA32(int[] a) {
        byte[] b = new byte[a.length * 4];
        for (int i = 0; i < a.length; i++) {
            b[i * 4] = (byte) (a[i] >>> 24);
            b[i * 4 + 1] = (byte) (a[i] >>> 16);
            b[i * 4 + 2] = (byte) (a[i] >>> 8);
            b[i * 4 + 3] = (byte) a[i];
        }
        return b;
    }

    static byte[] b64d(String s) {
        s = s.replace('-', '+').replace('_', '/').replace(",", "");
        while (s.length() % 4 != 0) s += "=";
        return Base64.getDecoder().decode(s);
    }

    static String b64e(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
