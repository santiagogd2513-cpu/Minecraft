package com.example.beautifuldecor.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilidades de direcciones y de nombres de archivo. */
public final class Url {
    public static final String HOME = "hs:home";
    private static final Pattern DOMAIN = Pattern.compile("^(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d+)?(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL = Pattern.compile("^(?:localhost|\\d{1,3}(?:\\.\\d{1,3}){3})(?::\\d+)?(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);

    private Url() {
    }

    public static boolean isWeb(String u) {
        if (u == null) {
            return false;
        }
        String l = u.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }

    public static boolean isInternal(String u) {
        return u != null && u.toLowerCase(Locale.ROOT).startsWith("hs:");
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return s;
        }
    }

    /** Deja una dirección escrita a la ligera (espacios, tildes...) en una forma que URI acepta. */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 255;
            if (c <= 32 || c >= 127 || "\"<>\\^`{|}".indexOf(c) >= 0) {
                sb.append('%').append(String.format("%02X", c));
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    /**
     * Dirección absoluta de ref respecto a base. Devuelve null si no es navegable (javascript:, mailto:, data:...),
     * "#ancla" si solo es un ancla de la misma página, y deja pasar las direcciones internas hs: solo si la página de origen también es interna.
     */
    public static String resolve(String base, String ref) {
        if (ref == null) {
            return null;
        }
        String r = ref.trim();
        if (r.isEmpty()) {
            return null;
        }
        String lower = r.toLowerCase(Locale.ROOT);
        if (lower.startsWith("hs:")) {
            // las direcciones internas solo valen en las páginas del propio navegador, nunca en las de la web
            return base == null || isInternal(base) ? r : null;
        }
        if (r.startsWith("#")) {
            return r;
        }
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:")
                || lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("about:")
                || lower.startsWith("file:") || lower.startsWith("sms:")) {
            return null;
        }
        try {
            URI target = new URI(escape(r));
            URI result;
            if (target.isAbsolute()) {
                result = target;
            } else if (base != null && isWeb(base)) {
                URI b = new URI(escape(stripFragment(base)));
                if (b.getPath() == null || b.getPath().isEmpty()) {
                    b = new URI(b.getScheme(), b.getAuthority(), "/", b.getQuery(), null);
                }
                result = b.resolve(target);
            } else {
                return null;
            }
            String s = result.toString();
            if (!isWeb(s) || result.getHost() == null) {
                return null;
            }
            return unwrapRedirect(s);
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    public static String stripFragment(String u) {
        int i = u.indexOf('#');
        return i < 0 ? u : u.substring(0, i);
    }

    public static String fragment(String u) {
        int i = u.indexOf('#');
        return i < 0 ? null : decode(u.substring(i + 1));
    }

    /** Los resultados de DuckDuckGo pasan por un redirector (duckduckgo.com/l/?uddg=...): se va directo al destino. */
    public static String unwrapRedirect(String url) {
        String l = url.toLowerCase(Locale.ROOT);
        if (l.contains("duckduckgo.com/l/") && l.contains("uddg=")) {
            Matcher m = Pattern.compile("[?&]uddg=([^&#]+)").matcher(url);
            if (m.find()) {
                String target = decode(m.group(1));
                if (isWeb(target)) {
                    return target;
                }
            }
        }
        return url;
    }

    public static String hostOf(String u) {
        try {
            String h = new URI(escape(u)).getHost();
            return h == null ? "" : h;
        } catch (URISyntaxException | RuntimeException e) {
            return "";
        }
    }

    public static String searchUrl(String query) {
        return "https://html.duckduckgo.com/html/?q=" + encode(query.trim());
    }

    /** Lo que se escribe en la barra de direcciones: una dirección, un dominio suelto o una búsqueda. */
    public static String normalizeInput(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return HOME;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("hs:") || isWeb(t)) {
            return t;
        }
        if (t.indexOf(' ') < 0 && DOMAIN.matcher(t).matches()) {
            return "https://" + t;
        }
        if (t.indexOf(' ') < 0 && LOCAL.matcher(t).matches()) {
            return "http://" + t;
        }
        return searchUrl(t);
    }

    // ------------------------------------------------------------------ nombres de archivo

    public static String sanitizeFileName(String name) {
        String n = name == null ? "" : name;
        n = n.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").trim();
        while (n.endsWith(".") || n.endsWith(" ")) {
            n = n.substring(0, n.length() - 1);
        }
        while (n.startsWith(".")) {
            n = n.substring(1);
        }
        if (n.isEmpty()) {
            n = "archivo";
        }
        if (n.length() > 100) {
            String ext = extension(n);
            n = n.substring(0, Math.max(1, 100 - (ext.isEmpty() ? 0 : ext.length() + 1))) + (ext.isEmpty() ? "" : "." + ext);
        }
        String stem = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (stem.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM\\d|LPT\\d")) {
            n = "_" + n;
        }
        return n;
    }

    /** Extensión en minúsculas, sin el punto ("" si no tiene). */
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1 || name.length() - dot > 8) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String extensionForMime(String mime) {
        if (mime == null) {
            return "";
        }
        return switch (mime) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a";
            case "video/mp4", "video/m4v", "video/x-m4v" -> "mp4";
            case "video/quicktime" -> "mov";
            case "audio/flac" -> "flac";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "application/zip" -> "zip";
            case "text/plain" -> "txt";
            case "text/html" -> "html";
            case "video/webm" -> "webm";
            default -> "";
        };
    }

    /** Nombre con el que se guarda una descarga: el del encabezado Content-Disposition, o el de la dirección. */
    public static String fileName(String url, String disposition, String mime) {
        String name = null;
        if (disposition != null) {
            Matcher star = Pattern.compile("filename\\*\\s*=\\s*[^']*'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE).matcher(disposition);
            Matcher plain = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(disposition);
            if (star.find()) {
                name = decode(star.group(1).trim());
            } else if (plain.find()) {
                name = plain.group(1).trim();
            }
        }
        if (name == null || name.isBlank()) {
            String path = url;
            int q = path.indexOf('?');
            if (q >= 0) {
                path = path.substring(0, q);
            }
            path = stripFragment(path);
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            name = decode(last);
            if (name.isBlank() || (slash >= 0 && path.endsWith("//")) || name.contains("://")) {
                name = "descarga";
            }
        }
        name = sanitizeFileName(name);
        if (extension(name).isEmpty()) {
            String ext = extensionForMime(mime);
            if (!ext.isEmpty()) {
                name = name + "." + ext;
            }
        }
        return name;
    }

    public static boolean isAudioName(String name) {
        String e = extension(name == null ? "" : name);
        return e.equals("mp3") || e.equals("ogg") || e.equals("wav") || e.equals("oga");
    }

    public static boolean isAudioMime(String mime) {
        return mime != null && (mime.equals("audio/mpeg") || mime.equals("audio/mp3") || mime.equals("audio/ogg")
                || mime.equals("application/ogg") || mime.equals("audio/wav") || mime.equals("audio/x-wav")
                || mime.equals("audio/wave") || mime.equals("audio/vnd.wave") || mime.equals("audio/x-mpeg"));
    }

    /** Videos MP4 (y MOV, que tiene la misma estructura). */
    public static boolean isVideoName(String name) {
        String e = extension(name == null ? "" : name);
        return e.equals("mp4") || e.equals("m4v") || e.equals("mov");
    }

    public static boolean isVideoMime(String mime) {
        return mime != null && (mime.equals("video/mp4") || mime.equals("video/x-m4v") || mime.equals("video/quicktime")
                || mime.equals("video/m4v"));
    }

    /** Listas de reproducción de radios (.m3u, .pls) y listas HLS (.m3u8). */
    public static boolean isPlaylistMime(String mime) {
        return mime != null && (mime.equals("audio/x-mpegurl") || mime.equals("audio/mpegurl") || mime.equals("application/x-mpegurl")
                || mime.equals("application/vnd.apple.mpegurl") || mime.equals("audio/x-scpls") || mime.equals("application/pls+xml"));
    }

    public static boolean isPlaylistName(String name) {
        String e = extension(name == null ? "" : name);
        return e.equals("m3u") || e.equals("m3u8") || e.equals("pls");
    }

    /** Audio AAC (muchas radios): el navegador no lo puede decodificar. */
    public static boolean isAacMime(String mime) {
        return mime != null && (mime.equals("audio/aac") || mime.equals("audio/aacp") || mime.equals("audio/x-aac") || mime.equals("audio/mp4a-latm"));
    }
}
