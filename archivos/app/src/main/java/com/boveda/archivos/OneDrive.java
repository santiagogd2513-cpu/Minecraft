package com.boveda.archivos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Cuenta de OneDrive (Microsoft): inicio de sesión OAuth con el ID de aplicación que el usuario
 * registra en Microsoft (sin secreto, con código PKCE y un servidor local en localhost), y llamadas
 * autenticadas a Microsoft Graph (renueva el acceso solo).
 */
final class OneDrive {
    static final String SCOPE = "Files.ReadWrite.All User.Read offline_access";
    private static final String AUTH = "https://login.microsoftonline.com/common/oauth2/v2.0/";
    static final String GRAPH = "https://graph.microsoft.com/v1.0";
    private static OneDrive instance;

    private final SharedPreferences prefs;
    private String clientId, refresh;
    private volatile String access;
    private volatile long accessUntil;

    static synchronized OneDrive get(Context c) {
        if (instance == null) instance = new OneDrive(c.getApplicationContext());
        return instance;
    }

    private OneDrive(Context c) {
        prefs = c.getSharedPreferences("onedrive", Context.MODE_PRIVATE);
        clientId = prefs.getString("cliente", "");
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

    void setClient(String id) {
        clientId = id.trim();
        prefs.edit().putString("cliente", clientId).apply();
    }

    void disconnect() {
        refresh = "";
        access = null;
        prefs.edit().remove("renovar").remove("correo").apply();
    }

    void forgetClient() {
        disconnect();
        clientId = "";
        prefs.edit().clear().apply();
    }

    // ------------------------------------------------------------------ iniciar sesión

    void login(Activity a, Drive.Done cb) {
        String verifier = random(), challenge, state = random();
        try {
            challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            cb.done(e.getMessage());
            return;
        }
        ServerSocket ss;
        try {
            ss = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            ss.setSoTimeout(5 * 60 * 1000);
        } catch (IOException e) {
            cb.done(L.t("No se pudo preparar el inicio de sesión: ") + e.getMessage());
            return;
        }
        // Microsoft deja cualquier puerto si la dirección registrada es http://localhost
        String redirect = "http://localhost:" + ss.getLocalPort();
        String url = AUTH + "authorize?response_type=code&prompt=select_account&response_mode=query"
                + "&client_id=" + Drive.enc(clientId) + "&redirect_uri=" + Drive.enc(redirect) + "&scope=" + Drive.enc(SCOPE)
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=" + state;
        new Thread(() -> {
            String err;
            try (ServerSocket server = ss; Socket s = server.accept()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String first = r.readLine();
                String code = null, gotState = null, error = null, desc = null;
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
                            if (k.equals("error_description")) desc = v;
                        }
                    }
                }
                boolean ok = code != null && state.equals(gotState);
                String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                        + "<style>body{background:#0b0d14;color:#e8ecf4;font-family:sans-serif;display:flex;align-items:center;"
                        + "justify-content:center;height:100vh;margin:0;text-align:center}h1{color:" + (ok ? "#35d08a" : "#ff5d6c") + "}</style>"
                        + "</head><body><div><h1>" + (ok ? L.t("¡Listo!") : L.t("No se pudo")) + "</h1><p>"
                        + (ok ? L.t("OneDrive quedó conectado. Vuelve a Bóveda.") : L.t("Vuelve a Bóveda e inténtalo otra vez."))
                        + "</p></div></body></html>";
                byte[] body = html.getBytes(StandardCharsets.UTF_8);
                OutputStream o = s.getOutputStream();
                o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                o.write(body);
                o.flush();
                if (!ok) {
                    if ("access_denied".equals(error)) throw new IOException(L.t("No diste permiso"));
                    throw new IOException(desc != null ? L.t("Microsoft: ") + desc : error != null ? error : L.t("Microsoft no respondió bien"));
                }
                JSONObject t = tokenRequest("client_id=" + Drive.enc(clientId) + "&scope=" + Drive.enc(SCOPE) + "&code=" + Drive.enc(code)
                        + "&redirect_uri=" + Drive.enc(redirect) + "&grant_type=authorization_code&code_verifier=" + Drive.enc(verifier));
                keep(t);
                if (refresh.isEmpty()) throw new IOException(L.t("Microsoft no dio acceso permanente (vuelve a intentarlo)"));
                try {
                    JSONObject me = json("GET", GRAPH + "/me?$select=userPrincipalName,mail", null);
                    String mail = me.optString("mail", "");
                    prefs.edit().putString("correo", mail.isEmpty() || mail.equals("null") ? me.optString("userPrincipalName") : mail).apply();
                } catch (Exception ignored) {
                }
                err = null;
            } catch (java.net.SocketTimeoutException e) {
                err = L.t("Se acabó el tiempo para iniciar sesión");
            } catch (Exception e) {
                err = e.getMessage();
            }
            String fe = err;
            a.runOnUiThread(() -> cb.done(fe));
        }, "onedrive-login").start();
        Drive.openBrowser(a, url);
    }

    private void keep(JSONObject t) {
        access = t.optString("access_token");
        accessUntil = System.currentTimeMillis() + (t.optLong("expires_in", 3600) - 60) * 1000;
        String rt = t.optString("refresh_token", "");
        if (!rt.isEmpty()) {
            refresh = rt;
            prefs.edit().putString("renovar", Secure.encrypt(rt)).apply();
        }
    }

    private JSONObject tokenRequest(String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(AUTH + "token").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String text = Drive.read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        try {
            JSONObject r = new JSONObject(text);
            if (code >= 400) {
                String e = r.optString("error");
                if (e.equals("invalid_grant")) {
                    refresh = "";
                    prefs.edit().remove("renovar").apply();
                    throw new IOException(L.t("Microsoft pidió iniciar sesión de nuevo (toca OneDrive en el inicio)"));
                }
                String d = r.optString("error_description", e);
                if (d.contains("AADSTS700016") || e.equals("unauthorized_client")) throw new IOException(L.t("Microsoft no reconoce ese ID de aplicación"));
                if (d.contains("AADSTS50011")) throw new IOException("Falta la dirección http://localhost en la app registrada en Microsoft");
                throw new IOException(L.t("Microsoft: ") + d.split("\\r?\\n")[0]);
            }
            return r;
        } catch (org.json.JSONException e) {
            throw new IOException(L.t("Microsoft respondió algo inesperado (") + code + ")");
        }
    }

    // ------------------------------------------------------------------ llamadas

    synchronized String accessToken() throws IOException {
        if (!connected()) throw new IOException(L.t("Conecta OneDrive desde el inicio de Bóveda"));
        if (access == null || System.currentTimeMillis() > accessUntil) {
            keep(tokenRequest("client_id=" + Drive.enc(clientId) + "&scope=" + Drive.enc(SCOPE)
                    + "&refresh_token=" + Drive.enc(refresh) + "&grant_type=refresh_token"));
        }
        return access;
    }

    HttpURLConnection open(String method, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        try {
            c.setRequestMethod(method);
        } catch (ProtocolException e) {
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", method);
        }
        c.setRequestProperty("Authorization", "Bearer " + accessToken());
        return c;
    }

    JSONObject json(String method, String url, JSONObject body) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
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
            if ((code == 429 || code == 503) && attempt < 2) {
                c.disconnect();
                try {
                    Thread.sleep(2000L << attempt);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            String text = Drive.read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            c.disconnect();
            if (code >= 400) throw new IOException(errorText(code, text));
            try {
                return text.isEmpty() ? new JSONObject() : new JSONObject(text);
            } catch (org.json.JSONException e) {
                throw new IOException(L.t("Respuesta rara de OneDrive"));
            }
        }
        throw new IOException(L.t("OneDrive no aceptó el acceso"));
    }

    static String errorText(int code, String body) {
        String msg = "";
        try {
            msg = new JSONObject(body).getJSONObject("error").optString("message");
        } catch (Exception ignored) {
        }
        return switch (code) {
            case 403 -> "OneDrive no lo permite" + (msg.isEmpty() ? "" : ": " + msg);
            case 404 -> "Ya no existe en OneDrive";
            case 409 -> "Ya existe algo con ese nombre en OneDrive";
            case 507 -> "No hay espacio en tu OneDrive";
            default -> L.t("OneDrive (") + code + ")" + (msg.isEmpty() ? "" : ": " + msg);
        };
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
