package com.boveda.archivos;

import android.content.Context;
import android.content.res.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Idiomas de Bóveda. Los textos están escritos en español en el código; {@link #t} los cambia por su
 * traducción (assets/lang/«código».txt, una línea «número|texto» por cada línea de keys.txt).
 * Si falta una traducción, se queda en español.
 */
final class L {
    /** {código, nombre en su idioma}; «auto» = el del teléfono. */
    static final String[][] LANGS = {
            {"auto", "Automático"},
            {"es", "Español"},
            {"en", "English (US)"},
            {"pt-BR", "Português (Brasil)"},
            {"pt-PT", "Português (Portugal)"},
            {"de", "Deutsch"},
            {"fr", "Français"},
            {"ru", "Русский"},
            {"zh-TW", "中文（繁體）"},
            {"zh-CN", "中文（简体）"},
            {"ja", "日本語"},
            {"ko", "한국어"},
    };

    private static Map<String, String> map = new HashMap<>();
    private static String code = "es";

    private L() {}

    static String t(String spanish) {
        String r = map.get(spanish);
        return r != null ? r : spanish;
    }

    static String code() {
        return code;
    }

    /** El idioma de verdad para «auto»: el del teléfono si Bóveda lo tiene, si no español. */
    static String resolve(String pref, Context c) {
        if (pref != null && !pref.equals("auto")) return pref;
        Configuration cfg = c.getResources().getConfiguration();
        Locale l = cfg.getLocales().get(0);
        String lang = l.getLanguage(), region = l.getCountry(), script = l.getScript();
        switch (lang) {
            case "en": return "en";
            case "de": return "de";
            case "fr": return "fr";
            case "ru": return "ru";
            case "ja": return "ja";
            case "ko": return "ko";
            case "pt": return region.equals("PT") ? "pt-PT" : "pt-BR";
            case "zh":
                return script.equals("Hant") || region.equals("TW") || region.equals("HK") || region.equals("MO") ? "zh-TW" : "zh-CN";
            default: return "es";
        }
    }

    /** Carga el idioma elegido en los ajustes (llamar al iniciar la app). */
    static void init(Context c, String pref) {
        code = resolve(pref, c);
        Map<String, String> m = new HashMap<>();
        if (!code.equals("es")) {
            try {
                List<String> keys = lines(c, "lang/keys.txt");
                for (String line : lines(c, "lang/" + code + ".txt")) {
                    int tab = line.indexOf('|');
                    if (tab <= 0) continue;
                    int i;
                    try {
                        i = Integer.parseInt(line.substring(0, tab).trim()) - 1;
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (i < 0 || i >= keys.size()) continue;
                    String key = unescape(keys.get(i)), val = unescape(line.substring(tab + 1));
                    if (val.trim().isEmpty() || val.trim().equals("=")) continue;
                    m.put(key, pad(key, val.trim()));
                }
            } catch (Exception ignored) {
                // sin archivo de idioma: queda en español
            }
        }
        map = m;
    }

    /** La traducción con los mismos espacios y saltos de línea al principio y al final que el original. */
    private static String pad(String key, String val) {
        int a = 0, b = key.length();
        while (a < b && Character.isWhitespace(key.charAt(a))) a++;
        while (b > a && Character.isWhitespace(key.charAt(b - 1))) b--;
        return key.substring(0, a) + val + key.substring(b);
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static List<String> lines(Context c, String asset) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open(asset), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) out.add(s);
        }
        return out;
    }
}
