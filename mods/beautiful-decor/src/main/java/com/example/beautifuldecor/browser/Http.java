package com.example.beautifuldecor.browser;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Descarga por HTTP/HTTPS con redirecciones, gzip y un pequeño guardado de cookies de sesión. */
public final class Http {
    public static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:115.0) Gecko/20100101 Firefox/115.0 HSBrowser/2.4.1";
    private static final int MAX_REDIRECTS = 10;
    private static final Map<String, Map<String, String>> COOKIES = new ConcurrentHashMap<>();

    private Http() {
    }

    /** Respuesta abierta: hay que cerrarla. */
    public static final class Response implements Closeable {
        public final String url;
        public final int status;
        /** Tipo de contenido en minúsculas y sin parámetros ("text/html"), o "" si no lo dijeron. */
        public final String mime;
        public final String charset;
        /** Tamaño en bytes si se conoce (sin compresión), o -1 (una transmisión en vivo no tiene tamaño). */
        public final long length;
        public final String disposition;
        public final InputStream body;
        /** Cierto si el servidor es de radio en vivo (Icecast/Shoutcast: manda cabeceras icy-*). */
        public final boolean icy;
        /** Cabeceras de la respuesta (nombres en minúsculas). Vacío si no se conocen. */
        public Map<String, List<String>> headers = Map.of();
        /** Nombre de la emisora (cabecera icy-name), o null. */
        public final String icyName;
        private final HttpURLConnection conn;
        private final Closeable closer;

        Response(String url, int status, String mime, String charset, long length, String disposition, InputStream body,
                 HttpURLConnection conn, Closeable closer, boolean icy, String icyName) {
            this.url = url;
            this.status = status;
            this.mime = mime;
            this.charset = charset;
            this.length = length;
            this.disposition = disposition;
            this.body = body;
            this.conn = conn;
            this.closer = closer;
            this.icy = icy;
            this.icyName = icyName == null || icyName.isBlank() ? null : icyName.trim();
        }

        /**
         * Cierto si parece una transmisión en vivo (radio): no tiene tamaño y, o es de un servidor Icecast/Shoutcast, o la dirección
         * no termina en un nombre de audio (.mp3, .ogg, .wav). Un .mp3 sin tamaño (respuesta por trozos) se trata como archivo normal.
         */
        public boolean isLiveStream() {
            return this.length < 0 && (this.icy || !Url.isAudioName(Url.fileName(this.url, this.disposition, null)));
        }

        @Override
        public void close() {
            try {
                this.body.close();
            } catch (IOException ignored) {
                // ya está cerrada
            }
            if (this.conn != null) {
                this.conn.disconnect();
            }
            if (this.closer != null) {
                try {
                    this.closer.close();
                } catch (IOException ignored) {
                    // ya está cerrada
                }
            }
        }
    }

    public static Response get(String url, String referer) throws IOException {
        return request("GET", url, referer, null, null);
    }

    /**
     * Pide una dirección con el método dado (GET, POST, HEAD, PUT, DELETE...). {@code headers} se suman a las de siempre (y
     * pueden cambiarlas), {@code data} es el cuerpo de la petición o null. Sigue las redirecciones: 301/302/303 pasan a GET.
     */
    public static Response request(String method, String url, String referer, Map<String, String> headers, byte[] data) throws IOException {
        String current = url;
        String verb = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        byte[] payload = data;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(current).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(25000);
            boolean patch = verb.equals("PATCH");
            conn.setRequestMethod(patch ? "POST" : verb);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,audio/*;q=0.8,*/*;q=0.5");
            conn.setRequestProperty("Accept-Language", "es,en;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (referer != null && Url.isWeb(referer)) {
                conn.setRequestProperty("Referer", referer);
            }
            String cookie = cookieHeader(Url.hostOf(current));
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            if (patch) {
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            }
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    if (h.getKey() != null && h.getValue() != null && !h.getKey().equalsIgnoreCase("cookie")) {
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                }
            }
            if (payload != null && !verb.equals("GET") && !verb.equals("HEAD")) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(payload.length);
                try (java.io.OutputStream out = conn.getOutputStream()) {
                    out.write(payload);
                } catch (IOException e) {
                    conn.disconnect();
                    throw e;
                }
            }
            int status;
            boolean plainHttp = current.regionMatches(true, 0, "http://", 0, 7);
            try {
                status = conn.getResponseCode();
            } catch (IOException e) {
                conn.disconnect();
                String m = e.getMessage() == null ? "" : e.getMessage();
                if (plainHttp && verb.equals("GET") && (m.contains("Invalid Http response") || m.contains("Unexpected status line") || m.contains("ICY"))) {
                    return rawGet(current, referer, 0);          // Shoutcast viejo: responde "ICY 200 OK"
                }
                throw e;
            }
            if (status == -1 && plainHttp && verb.equals("GET")) {
                conn.disconnect();                               // el JDK no entendió la primera línea (-1): se lee a mano
                return rawGet(current, referer, 0);
            }
            storeCookies(Url.hostOf(current), headerValues(conn, "Set-Cookie"));
            if (status >= 300 && status < 400 && status != 304) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                String next = location == null ? null : Url.resolve(current, location);
                if (next == null || !Url.isWeb(next)) {
                    throw new IOException("Redirección no válida (" + location + ")");
                }
                current = Url.stripFragment(next);
                if (status != 307 && status != 308 && !verb.equals("GET") && !verb.equals("HEAD")) {
                    verb = "GET";
                    payload = null;
                    if (headers != null && headers.containsKey("Content-Type")) {
                        headers = new java.util.HashMap<>(headers);
                        headers.remove("Content-Type");
                    }
                }
                continue;
            }
            InputStream raw;
            try {
                raw = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            } catch (IOException e) {
                conn.disconnect();
                throw e;
            }
            if (raw == null) {
                raw = InputStream.nullInputStream();
            }
            String encoding = conn.getContentEncoding();
            long length = conn.getContentLengthLong();
            InputStream body = raw;
            if (encoding != null) {
                String e = encoding.toLowerCase(Locale.ROOT).trim();
                if (e.equals("gzip") || e.equals("x-gzip")) {
                    body = new GZIPInputStream(raw, 8192);
                    length = -1;
                } else if (e.equals("deflate")) {
                    body = new InflaterInputStream(raw);
                    length = -1;
                } else if (!e.equals("identity")) {
                    conn.disconnect();
                    throw new IOException("Compresión no admitida (" + encoding + ")");
                }
            }
            String type = conn.getContentType();
            String mime = "";
            String charset = null;
            if (type != null) {
                String[] parts = type.split(";");
                mime = parts[0].trim().toLowerCase(Locale.ROOT);
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                        charset = p.substring(8).replace("\"", "").trim();
                    }
                }
            }
            boolean icy = false;
            for (String key : conn.getHeaderFields().keySet()) {
                if (key != null && key.toLowerCase(Locale.ROOT).startsWith("icy-")) {
                    icy = true;
                    break;
                }
            }
            Response res = new Response(current, status, mime, charset, length, conn.getHeaderField("Content-Disposition"), body, conn, null,
                    icy, conn.getHeaderField("icy-name"));
            Map<String, List<String>> all = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                if (e.getKey() != null) {
                    all.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
                }
            }
            res.headers = all;
            return res;
        }
        throw new IOException("Demasiadas redirecciones");
    }


    /** Cliente mínimo para servidores de radio Shoutcast antiguos, que responden "ICY 200 OK" en vez de "HTTP/1.1 200 OK". Solo http. */
    private static Response rawGet(String url, String referer, int depth) throws IOException {
        if (depth > 5) {
            throw new IOException("Demasiadas redirecciones");
        }
        java.net.URI uri = java.net.URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        java.net.Socket socket = new java.net.Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), 12000);
            socket.setSoTimeout(25000);
            StringBuilder req = new StringBuilder("GET ").append(path).append(" HTTP/1.0\r\nHost: ").append(host);
            if (port != 80) {
                req.append(':').append(port);
            }
            req.append("\r\nUser-Agent: ").append(USER_AGENT).append("\r\nAccept: */*\r\nConnection: close\r\n");
            if (referer != null && Url.isWeb(referer)) {
                req.append("Referer: ").append(referer).append("\r\n");
            }
            req.append("\r\n");
            socket.getOutputStream().write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = new java.io.BufferedInputStream(socket.getInputStream(), 16384);
            String statusLine = readHeaderLine(in);
            String[] parts = statusLine.split("\\s+");
            if (parts.length < 2 || !(parts[0].startsWith("ICY") || parts[0].startsWith("HTTP/"))) {
                throw new IOException("Respuesta no v\u00e1lida del servidor");
            }
            int status;
            try {
                status = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Respuesta no v\u00e1lida del servidor");
            }
            Map<String, String> headers = new java.util.HashMap<>();
            boolean icy = parts[0].startsWith("ICY");
            for (int i = 0; i < 100; i++) {
                String line = readHeaderLine(in);
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    headers.putIfAbsent(name, line.substring(colon + 1).trim());
                    icy |= name.startsWith("icy-");
                }
            }
            if (status >= 300 && status < 400 && headers.containsKey("location")) {
                socket.close();
                String next = Url.resolve(url, headers.get("location"));
                if (next == null || !Url.isWeb(next)) {
                    throw new IOException("Redirecci\u00f3n no v\u00e1lida");
                }
                return next.regionMatches(true, 0, "http://", 0, 7) ? rawGet(Url.stripFragment(next), referer, depth + 1)
                        : get(Url.stripFragment(next), referer);
            }
            String type = headers.getOrDefault("content-type", "");
            String mime = type.split(";")[0].trim().toLowerCase(Locale.ROOT);
            long length = -1;
            try {
                if (headers.containsKey("content-length")) {
                    length = Long.parseLong(headers.get("content-length").trim());
                }
            } catch (NumberFormatException ignored) {
                // sin tama\u00f1o conocido
            }
            return new Response(url, status, mime, null, length, headers.get("content-disposition"), in, null, socket, icy,
                    headers.get("icy-name"));
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ya estaba cerrado
            }
            throw e instanceof IOException io ? io : new IOException(e);
        }
    }

    private static String readHeaderLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                sb.append((char) c);
            }
            if (sb.length() > 8192) {
                throw new IOException("Cabecera demasiado larga");
            }
        }
        if (c < 0 && sb.length() == 0) {
            throw new IOException("El servidor cerr\u00f3 la conexi\u00f3n");
        }
        return sb.toString();
    }

    /** Valores de una cabecera sin fijarse en mayúsculas (algunos servidores mandan "set-cookie"). */
    private static List<String> headerValues(HttpURLConnection conn, String name) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                out.addAll(e.getValue());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ cookies

    /** Las cookies que se mandarían a esa dirección ("a=1; b=2"), para {@code document.cookie}. */
    public static String cookiesFor(String url) {
        return cookieHeader(Url.hostOf(url));
    }

    /** Guarda una cookie puesta por un script ({@code document.cookie = "a=1; max-age=60"}). */
    public static void setCookie(String url, String cookie) {
        storeCookies(Url.hostOf(url), List.of(cookie));
    }

    private static String cookieHeader(String host) {
        if (host.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : COOKIES.entrySet()) {
            String domain = e.getKey();
            if (host.equals(domain) || host.endsWith("." + domain)) {
                for (Map.Entry<String, String> c : e.getValue().entrySet()) {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(c.getKey()).append('=').append(c.getValue());
                }
            }
        }
        return sb.toString();
    }

    private static void storeCookies(String host, List<String> headers) {
        if (headers == null || host.isEmpty()) {
            return;
        }
        for (String header : headers) {
            String[] parts = header.split(";");
            int eq = parts[0].indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = parts[0].substring(0, eq).trim();
            String value = parts[0].substring(eq + 1).trim();
            String domain = host;
            boolean expired = false;
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim().toLowerCase(Locale.ROOT);
                if (p.startsWith("domain=")) {
                    String d = p.substring(7);
                    if (d.startsWith(".")) {
                        d = d.substring(1);
                    }
                    if (!d.isEmpty() && (host.equals(d) || host.endsWith("." + d))) {
                        domain = d;
                    }
                } else if (p.startsWith("expires=")) {
                    expired |= p.contains("1970") || p.contains("1969");
                } else if (p.startsWith("max-age=")) {
                    try {
                        expired |= Long.parseLong(p.substring(8).trim()) <= 0;
                    } catch (NumberFormatException ignored) {
                        // se ignora
                    }
                }
            }
            if (COOKIES.size() > 200 && !COOKIES.containsKey(domain)) {
                continue;
            }
            Map<String, String> jar = COOKIES.computeIfAbsent(domain, k -> new ConcurrentHashMap<>());
            if (expired || value.isEmpty()) {
                jar.remove(name);
            } else if (jar.size() < 60) {
                jar.put(name, value);
            }
        }
    }

    // ------------------------------------------------------------------ lectura

    /** Lee como máximo max bytes. truncated[0] pasa a true si había más. */
    public static byte[] readLimited(InputStream in, int max, boolean[] truncated) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 1 << 16));
        byte[] buf = new byte[16384];
        int total = 0;
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            if (total + n > max) {
                out.write(buf, 0, max - total);
                if (truncated != null) {
                    truncated[0] = true;
                }
                break;
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    private static final Pattern META_CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_.:-]+)", Pattern.CASE_INSENSITIVE);

    /** Convierte los bytes de una página a texto: BOM, charset del encabezado, charset de la etiqueta meta, o UTF-8 / Windows-1252. */
    public static String decode(byte[] data, String headerCharset, boolean sniffMeta) {
        int offset = 0;
        Charset cs = null;
        if (data.length >= 3 && (data[0] & 255) == 0xEF && (data[1] & 255) == 0xBB && (data[2] & 255) == 0xBF) {
            offset = 3;
            cs = StandardCharsets.UTF_8;
        } else if (data.length >= 2 && (data[0] & 255) == 0xFE && (data[1] & 255) == 0xFF) {
            offset = 2;
            cs = StandardCharsets.UTF_16BE;
        } else if (data.length >= 2 && (data[0] & 255) == 0xFF && (data[1] & 255) == 0xFE) {
            offset = 2;
            cs = StandardCharsets.UTF_16LE;
        }
        if (cs == null && headerCharset != null) {
            cs = charset(headerCharset);
        }
        if (cs == null && sniffMeta) {
            String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            if (m.find()) {
                cs = charset(m.group(1));
            }
        }
        if (cs == null) {
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
            } catch (CharacterCodingException e) {
                cs = charset("windows-1252");
                if (cs == null) {
                    cs = StandardCharsets.ISO_8859_1;
                }
            }
        }
        return new String(data, offset, data.length - offset, cs);
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Para quien arma páginas a mano: escapa texto para ponerlo dentro de HTML. */
    public static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
