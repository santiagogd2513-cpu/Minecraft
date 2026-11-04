package com.nocturna.browser;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Traductor de páginas (servicio gratuito de Google Traductor). El idioma de origen se detecta solo.
 */
final class Translator {
    /** Idiomas a los que se puede traducir. */
    static final String[][] TARGETS = {
            {"es", "Español"},
            {"en", "Inglés"},
            {"ja", "Japonés"},
            {"zh-CN", "Chino (simplificado)"},
            {"zh-TW", "Chino (tradicional)"},
            {"de", "Alemán"},
            {"pt", "Portugués (Brasil)"},
            {"pt-PT", "Portugués (Portugal)"},
    };

    private static String js = "";

    private Translator() {}

    static void init(Context c) {
        if (!js.isEmpty()) return;
        try (InputStream in = c.getAssets().open("traductor.js")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            js = bo.toString("UTF-8");
        } catch (IOException ignored) {
        }
    }

    static String script() {
        return js;
    }

    static String name(String code) {
        if (code == null) return "?";
        for (String[] t : TARGETS) if (t[0].equalsIgnoreCase(code)) return t[1].toLowerCase();
        return switch (code.toLowerCase().split("-")[0]) {
            case "es" -> "español";
            case "en" -> "inglés";
            case "ja" -> "japonés";
            case "zh" -> "chino";
            case "de" -> "alemán";
            case "pt" -> "portugués";
            case "fr" -> "francés";
            case "it" -> "italiano";
            case "ru" -> "ruso";
            case "ko" -> "coreano";
            case "ar" -> "árabe";
            default -> code;
        };
    }

    /** Traduce una tanda de textos. detected[0] queda con el idioma que más aparece. */
    static List<String> translate(List<String> texts, String target, String[] detected) throws IOException {
        URL url = new URL("https://translate.googleapis.com/translate_a/t?client=gtx&sl=auto&tl="
                + URLEncoder.encode(target, "UTF-8") + "&format=text");
        StringBuilder body = new StringBuilder();
        for (String t : texts) {
            if (body.length() > 0) body.append('&');
            body.append("q=").append(URLEncoder.encode(t, "UTF-8"));
        }
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("El traductor respondió " + code);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        } finally {
            c.disconnect();
        }
        try {
            JSONArray a = new JSONArray(bo.toString("UTF-8"));
            List<String> out = new ArrayList<>();
            java.util.Map<String, Integer> langs = new java.util.HashMap<>();
            // con varios textos: [[traducción, idioma], …]; con uno puede venir [traducción, idioma]
            if (texts.size() == 1 && a.length() == 2 && a.opt(0) instanceof String) {
                out.add(a.getString(0));
                langs.merge(a.optString(1), 1, Integer::sum);
            } else {
                for (int i = 0; i < a.length(); i++) {
                    Object o = a.get(i);
                    if (o instanceof JSONArray arr) {
                        out.add(arr.optString(0));
                        if (arr.length() > 1) langs.merge(arr.optString(1), 1, Integer::sum);
                    } else {
                        out.add(String.valueOf(o));
                    }
                }
            }
            String best = null;
            int bestN = 0;
            for (java.util.Map.Entry<String, Integer> e : langs.entrySet()) {
                if (e.getValue() > bestN) {
                    best = e.getKey();
                    bestN = e.getValue();
                }
            }
            if (detected != null && best != null) detected[0] = best;
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException("Respuesta rara del traductor", e);
        }
    }

    /** Plan B: la página traducida por el proxy de Google (otra dirección). */
    static String proxyUrl(String url, String target) {
        Uri u = Uri.parse(url);
        String host = u.getHost();
        if (host == null) return null;
        String h = host.replace("-", "--").replace(".", "-") + ".translate.goog";
        return u.buildUpon().scheme("https").authority(h)
                .appendQueryParameter("_x_tr_sl", "auto")
                .appendQueryParameter("_x_tr_tl", target)
                .appendQueryParameter("_x_tr_hl", "es")
                .appendQueryParameter("_x_tr_pto", "wapp")
                .build().toString();
    }
}
