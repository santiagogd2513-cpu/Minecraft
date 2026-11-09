package com.boveda.archivos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;

/**
 * Cuenta de Google Drive: inicio de sesión OAuth (con el ID de cliente que crea el usuario en
 * Google Cloud, código PKCE y un servidor local que recibe la respuesta de Google en 127.0.0.1)
 * y llamadas autenticadas a la API (renueva el acceso solo).
 */
final class Drive {
    static final String SCOPE = "https://www.googleapis.com/auth/drive";
    private static Drive instance;

    private final SharedPreferences prefs;
    private String clientId, secret, refresh;
    private volatile String access;
    private volatile long accessUntil;

    static synchronized Drive get(Context c) {
        if (instance == null) instance = new Drive(c.getApplicationContext());
        return instance;
    }

    private Drive(Context c) {
        prefs = c.getSharedPreferences("gdrive", Context.MODE_PRIVATE);
        clientId = prefs.getString("cliente", "");
        secret = Secure.decrypt(prefs.getString("secreto", ""));
        refresh = Secure.decrypt(prefs.getString("renovar", ""));
    }

    boolean configured() {
        return !clientId.isEmpty();
    }

    boolean connected() {
        return configured() && !refresh.isEmpty();
    }

    String clientId() {
        return clientId;
    }

    String email() {
        return prefs.getString("correo", "");
    }

    void setClient(String id, String sec) {
        clientId = id.trim();
        secret = sec.trim();
        prefs.edit().putString("cliente", clientId).putString("secreto", Secure.encrypt(secret)).apply();
    }

    void disconnect() {
        refresh = "";
        access = null;
        prefs.edit().remove("renovar").remove("correo").apply();
        DriveFs.reset();
    }

    void forgetClient() {
        disconnect();
        clientId = "";
        secret = "";
        prefs.edit().clear().apply();
    }

    // ------------------------------------------------------------------ iniciar sesión

    interface Done {
        void done(String error);
    }

    /** Abre el navegador para que el usuario autorice; cuando Google responde, guarda el acceso. */
    void login(Activity a, Done cb) {
        String verifier = random();
        String challenge;
        try {
            challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            cb.done(e.getMessage());
            return;
        }
        String state = random();
        ServerSocket ss;
        try {
            ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            ss.setSoTimeout(5 * 60 * 1000);
        } catch (IOException e) {
            cb.done("No se pudo preparar el inicio de sesión: " + e.getMessage());
            return;
        }
        String redirect = "http://127.0.0.1:" + ss.getLocalPort();
        String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&access_type=offline&prompt=consent"
                + "&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirect) + "&scope=" + enc(SCOPE)
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=" + state;
        new Thread(() -> {
            String err;
            try (ServerSocket server = ss; Socket s = server.accept()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String first = r.readLine();
                String code = null, gotState = null, error = null;
                if (first != null && first.startsWith("GET ")) {
                    String target = first.split(" ")[1];
                    int q = target.indexOf('?');
                    if (q >= 0) {
                        for (String kv : target.substring(q + 1).split("&")) {
                            int eq = kv.indexOf('=');
                            if (eq < 0) continue;
                            String k = kv.substring(0, eq), v = URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
                            if (k.equals("code")) code = v;
                            if (k.equals("state")) gotState = v;
                            if (k.equals("error")) error = v;
                        }
                    }
                }
                boolean ok = code != null && state.equals(gotState);
                String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                        + "<style>body{background:#0b0d14;color:#e8ecf4;font-family:sans-serif;display:flex;align-items:center;"
                        + "justify-content:center;height:100vh;margin:0;text-align:center}h1{color:" + (ok ? "#35d08a" : "#ff5d6c") + "}</style>"
                        + "</head><body><div><h1>" + (ok ? "¡Listo!" : "No se pudo") + "</h1><p>"
                        + (ok ? "Google Drive quedó conectado. Vuelve a Bóveda." : "Vuelve a Bóveda e inténtalo otra vez.")
                        + "</p></div></body></html>";
                byte[] body = html.getBytes(StandardCharsets.UTF_8);
                OutputStream o = s.getOutputStream();
                o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                o.write(body);
                o.flush();
                if (!ok) throw new IOException(error != null ? (error.equals("access_denied") ? "No diste permiso" : error)
                        : "Google no respondió bien");
                exchange(code, verifier, redirect);
                fetchEmail();
                err = null;
            } catch (java.net.SocketTimeoutException e) {
                err = "Se acabó el tiempo para iniciar sesión";
            } catch (Exception e) {
                err = e.getMessage();
            }
            String fe = err;
            a.runOnUiThread(() -> cb.done(fe));
        }, "drive-login").start();
        openBrowser(a, url);
    }

    /** Google no deja iniciar sesión dentro de navegadores tipo WebView (como Nocturna): se usa Chrome u otro. */
    static void openBrowser(Activity a, String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PackageManager pm = a.getPackageManager();
        String[] preferred = {"com.android.chrome", "com.chrome.beta", "com.sec.android.app.sbrowser", "org.mozilla.firefox",
                "com.microsoft.emmx", "com.brave.browser", "com.opera.browser"};
        for (String p : preferred) {
            try {
                pm.getPackageInfo(p, 0);
                a.startActivity(new Intent(i).setPackage(p));
                return;
            } catch (Exception ignored) {
            }
        }
        List<ResolveInfo> all = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (p.equals("com.nocturna.browser") || p.equals(a.getPackageName())) continue;
            a.startActivity(new Intent(i).setPackage(p));
            return;
        }
        a.startActivity(i);
    }

    private void exchange(String code, String verifier, String redirect) throws IOException {
        JSONObject r = tokenRequest("code=" + enc(code) + "&client_id=" + enc(clientId) + "&client_secret=" + enc(secret)
                + "&redirect_uri=" + enc(redirect) + "&grant_type=authorization_code&code_verifier=" + enc(verifier));
        access = r.optString("access_token");
        accessUntil = System.currentTimeMillis() + (r.optLong("expires_in", 3600) - 60) * 1000;
        String rt = r.optString("refresh_token", "");
        if (rt.isEmpty()) throw new IOException("Google no dio acceso permanente (vuelve a intentarlo)");
        refresh = rt;
        prefs.edit().putString("renovar", Secure.encrypt(rt)).apply();
    }

    private JSONObject tokenRequest(String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://oauth2.googleapis.com/token").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        try {
            JSONObject r = new JSONObject(text);
            if (code >= 400) {
                String e = r.optString("error");
                if (e.equals("invalid_grant")) {
                    refresh = "";
                    prefs.edit().remove("renovar").apply();
                    throw new IOException("Google pidió iniciar sesión de nuevo (toca Google Drive en el inicio)");
                }
                if (e.equals("invalid_client")) throw new IOException("El ID de cliente o el secreto no son válidos");
                throw new IOException("Google: " + r.optString("error_description", e));
            }
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException("Google respondió algo inesperado (" + code + ")");
        }
    }

    private void fetchEmail() {
        try {
            JSONObject r = json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", null);
            prefs.edit().putString("correo", r.getJSONObject("user").optString("emailAddress")).apply();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ llamadas

    synchronized String accessToken() throws IOException {
        if (!connected()) throw new IOException("Conecta Google Drive desde el inicio de Bóveda");
        if (access == null || System.currentTimeMillis() > accessUntil) {
            JSONObject r = tokenRequest("client_id=" + enc(clientId) + "&client_secret=" + enc(secret)
                    + "&refresh_token=" + enc(refresh) + "&grant_type=refresh_token");
            access = r.optString("access_token");
            accessUntil = System.currentTimeMillis() + (r.optLong("expires_in", 3600) - 60) * 1000;
        }
        return access;
    }

    /** Abre una conexión autenticada (PATCH viaja como POST con X-HTTP-Method-Override). */
    HttpURLConnection open(String method, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        if (method.equals("PATCH")) {
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        } else {
            c.setRequestMethod(method);
        }
        c.setRequestProperty("Authorization", "Bearer " + accessToken());
        return c;
    }

    JSONObject json(String method, String url, JSONObject body) throws IOException {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection c = open(method, url);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            if (code == 401 && attempt == 0) {
                access = null;
                c.disconnect();
                continue;
            }
            String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            c.disconnect();
            if (code >= 400) throw new IOException(errorText(code, text));
            try {
                return text.isEmpty() ? new JSONObject() : new JSONObject(text);
            } catch (org.json.JSONException e) {
                throw new IOException("Respuesta rara de Google Drive");
            }
        }
        throw new IOException("Google Drive no aceptó el acceso");
    }

    static String errorText(int code, String body) {
        String msg = "";
        try {
            msg = new JSONObject(body).getJSONObject("error").optString("message");
        } catch (Exception ignored) {
        }
        return switch (code) {
            case 403 -> "Google Drive no lo permite" + (msg.isEmpty() ? "" : ": " + msg);
            case 404 -> "Ya no existe en Google Drive";
            case 507 -> "No hay espacio en tu Google Drive";
            default -> "Google Drive (" + code + ")" + (msg.isEmpty() ? "" : ": " + msg);
        };
    }

    static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        in.close();
        return bo.toString("UTF-8");
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String random() {
        byte[] b = new byte[48];
        new SecureRandom().nextBytes(b);
        return b64(b);
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
