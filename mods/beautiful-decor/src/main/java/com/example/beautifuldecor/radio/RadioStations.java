package com.example.beautifuldecor.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * V3.5.0: las emisoras de la radio de los vehículos, sin nada de Minecraft (se prueba fuera del juego: tools/v350/java/RadioTest.java).
 * <p>
 * Trae una lista de emisoras de internet en MP3 (las que el reproductor del mod sabe tocar en vivo) y las reglas para las que agrega
 * cada jugador: nombre corto, dirección http o https, como mucho {@link #MAX_CUSTOM}. Las propias se guardan en el celular/PC de cada
 * jugador, una por línea ("nombre", tabulador, "dirección").
 */
public final class RadioStations {
    /** Una emisora: su nombre para el menú, su dirección (MP3 en vivo) y el género que va al lado. */
    public record Station(String name, String url, String genre) {
    }

    public static final int MAX_NAME = 32;
    public static final int MAX_URL = 400;
    public static final int MAX_CUSTOM = 30;
    public static final int DEFAULT_VOLUME = 70;
    public static final int VOLUME_STEP = 10;

    /** Emisoras que vienen con el mod (MP3 en vivo, de servidores Icecast que mandan el título de la canción). */
    public static final List<Station> BUILT_IN = List.of(
            new Station("Radio Paradise", "https://stream.radioparadise.com/mp3-128", "Rock / Pop"),
            new Station("Radio Paradise Mellow", "https://stream.radioparadise.com/mellow-128", "Chill"),
            new Station("Radio Paradise Rock", "https://stream.radioparadise.com/rock-128", "Rock"),
            new Station("Radio Paradise Global", "https://stream.radioparadise.com/global-128", "World"),
            new Station("SomaFM Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", "Chill / Electronic"),
            new Station("SomaFM Underground 80s", "https://ice1.somafm.com/u80s-128-mp3", "80s"),
            new Station("SomaFM Indie Pop Rocks", "https://ice1.somafm.com/indiepop-128-mp3", "Indie"),
            new Station("SomaFM Beat Blender", "https://ice1.somafm.com/beatblender-128-mp3", "House"),
            new Station("SomaFM PopTron", "https://ice1.somafm.com/poptron-128-mp3", "Electropop"),
            new Station("SomaFM DEF CON Radio", "https://ice1.somafm.com/defcon-128-mp3", "Electronic"),
            new Station("SomaFM Lush", "https://ice1.somafm.com/lush-128-mp3", "Vocal chill"),
            new Station("SomaFM Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3", "Lounge"));

    private RadioStations() {
    }

    /** Nombre limpio: sin caracteres de control ni espacios de más, hasta {@link #MAX_NAME} letras ("" si no queda nada). */
    public static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                space = b.length() > 0;
                continue;
            }
            if (space) {
                b.append(' ');
                space = false;
            }
            b.append(c);
        }
        String s = b.toString();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    /**
     * Dirección limpia, o null si no sirve: tiene que empezar con http:// o https://, tener un servidor, no tener espacios ni caracteres de
     * control y medir hasta {@link #MAX_URL}.
     */
    public static String cleanUrl(String url) {
        if (url == null) {
            return null;
        }
        String s = url.trim();
        if (s.isEmpty() || s.length() > MAX_URL) {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        int hostStart;
        if (lower.startsWith("https://")) {
            hostStart = 8;
        } else if (lower.startsWith("http://")) {
            hostStart = 7;
        } else {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '"' || c == '<' || c == '>' || c == '\\' || c == '^' || c == '`' || c == '{' || c == '}' || c == '|') {
                return null;
            }
        }
        String host = hostOf(s, hostStart);
        if (host.isEmpty() || host.startsWith(".") || host.startsWith("-")) {
            return null;
        }
        return s;
    }

    /** Un nombre para una emisora sin nombre: su servidor ("stream.radioparadise.com"). */
    public static String nameFromUrl(String url) {
        String clean = cleanUrl(url);
        if (clean == null) {
            return "";
        }
        int start = clean.toLowerCase(Locale.ROOT).startsWith("https://") ? 8 : 7;
        return cleanName(hostOf(clean, start));
    }

    private static String hostOf(String url, int start) {
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = url.substring(start, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            return close > 0 ? host.substring(0, close + 1) : "";
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    /** Volumen entre 0 y 100. */
    public static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }

    /** Las emisoras propias guardadas (una por línea: nombre, tabulador, dirección); las líneas que no sirven se saltan. */
    public static List<Station> parseCustom(List<String> lines) {
        List<Station> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            if (line == null || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int tab = line.indexOf('\t');
            String name = tab >= 0 ? line.substring(0, tab) : "";
            String url = cleanUrl(tab >= 0 ? line.substring(tab + 1) : line);
            if (url == null || containsUrl(out, url)) {
                continue;
            }
            String cleanName = cleanName(name);
            out.add(new Station(cleanName.isEmpty() ? nameFromUrl(url) : cleanName, url, ""));
            if (out.size() >= MAX_CUSTOM) {
                break;
            }
        }
        return out;
    }

    /** Las emisoras propias como líneas para guardarlas (lo contrario de {@link #parseCustom}). */
    public static List<String> formatCustom(List<Station> stations) {
        List<String> out = new ArrayList<>();
        out.add("# BeautifulDecor - emisoras propias de la radio de los vehiculos (nombre<TAB>direccion)");
        for (Station s : stations) {
            String url = cleanUrl(s.url());
            if (url != null) {
                out.add(cleanName(s.name()) + "\t" + url);
            }
        }
        return out;
    }

    /** ¿Ya hay una emisora con esa dirección? (sin distinguir mayúsculas en el esquema y el servidor) */
    public static boolean containsUrl(List<Station> stations, String url) {
        for (Station s : stations) {
            if (s.url().equalsIgnoreCase(url)) {
                return true;
            }
        }
        return false;
    }
}
