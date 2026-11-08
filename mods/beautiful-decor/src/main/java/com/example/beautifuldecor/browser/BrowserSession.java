package com.example.beautifuldecor.browser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import com.example.beautifuldecor.browser.js.DomHost;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/**
 * Lo que recuerda un navegador abierto: historial, la página actual ya leída (árbol HTML y estilos) y las cargas en curso.
 * No sabe dibujar nada; quien la usa (la pantalla) acomoda la página con {@link Layout} cuando cambia {@link #version()}.
 */
public final class BrowserSession {
    /** Lo que el navegador le pide al reproductor de audio del juego. */
    public interface Audio {
        void playUrl(String url, String title);

        void playFile(Path file);

        /** Un video MP4: se baja completo y se reproduce con su sonido. */
        void playVideoUrl(String url, String title);

        void playVideoFile(Path file);
    }

    public static final class Entry {
        public volatile String url;
        public String title = "";
        public int scroll;
        /** history.state: solo vale mientras siga viva la página que lo creó. */
        Object state;
        /** La página viva que creó esta entrada (0 si ninguna). */
        int docId;

        Entry(String url) {
            this.url = url;
        }
    }

    /** Una petición que no es un simple GET (el envío de un formulario por POST). */
    private static final class Request {
        final String method;
        final byte[] body;
        final String contentType;

        Request(String method, byte[] body, String contentType) {
            this.method = method;
            this.body = body;
            this.contentType = contentType;
        }
    }

    /** Un estilo de la página: el texto de un style o la dirección de una hoja externa. */
    static final class SheetSrc {
        final String text;
        final String href;

        SheetSrc(String text, String href) {
            this.text = text;
            this.href = href;
        }
    }

    /** Si se ejecutan los scripts de las páginas. Ctrl+J lo cambia; vale para las páginas que se carguen después. */
    public static volatile boolean scripting = true;

    private static final int MAX_HTML = 3 << 20;
    private static final int MAX_TEXT = 1 << 20;
    private static final int MAX_CSS = 400 << 10;
    private static final int MAX_SHEETS = 6;
    private static final int MAX_HISTORY = 100;
    private static final Pattern REFRESH = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*[;,]\\s*(?:url\\s*=\\s*)?['\"]?([^'\"]+)['\"]?\\s*$", Pattern.CASE_INSENSITIVE);

    static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "HS Browser");
        t.setDaemon(true);
        return t;
    });

    private final Audio audio;
    private final List<Entry> history = new ArrayList<>();
    private int index = -1;
    private int gen;

    private volatile Node doc;
    private volatile Css.Sheet sheet;
    private volatile String baseUrl = "";
    private volatile String title = "";
    private volatile int version;
    private volatile int navigation;
    private volatile boolean loading;
    private boolean paused;
    private volatile String status = "";
    private volatile int scrollTarget = -1;
    private volatile String anchorTarget;
    private volatile PageRuntime runtime;
    private volatile int[] viewport = {800, 600, 0, 0};
    private volatile int scrollRequest = -1;
    private volatile Geometry geometry;
    private final ArrayList<String> consoleLog = new ArrayList<>();

    public BrowserSession(Audio audio) {
        this.audio = audio;
        this.doc = HtmlParser.parse(InternalPages.home());
        this.sheet = new Css.Sheet();
        this.baseUrl = Url.HOME;
        this.title = "HS Browser";
        Entry e = new Entry(Url.HOME);
        e.title = this.title;
        this.history.add(e);
        this.index = 0;
        this.version = 1;
        this.navigation = 1;
    }

    private void bump() {
        synchronized (this) {
            this.version++;
        }
    }

    // ------------------------------------------------------------------ lo que ve la pantalla

    /** La parte viva de la página mostrada (sus scripts y formularios), o null si no tiene. */
    public PageRuntime runtime() {
        return this.runtime;
    }

    /** La pantalla avisa si el navegador está en pantalla: si no, los temporizadores de la página esperan. */
    public synchronized void setPaused(boolean p) {
        this.paused = p;
        if (this.runtime != null) {
            this.runtime.pause(p);
        }
    }

    /** La pantalla avisa de su tamaño y del desplazamiento (lo que ven window.innerWidth y scrollY). */
    public void setViewport(int w, int h, int scrollX, int scrollY) {
        int[] old = this.viewport;
        this.viewport = new int[] {w, h, scrollX, scrollY};
        boolean resized = old[0] != w || old[1] != h;
        boolean scrolled = old[2] != scrollX || old[3] != scrollY;
        PageRuntime rt = this.runtime;
        if (rt != null && (resized || scrolled)) {
            rt.viewportChanged(scrolled, resized);
        }
    }

    /** La pantalla acomodó la página: desde ahora los scripts miden con ella (getBoundingClientRect, observadores...). */
    public void publishLayout(Page page) {
        this.geometry = page == null ? null : new Geometry(page);
        PageRuntime rt = this.runtime;
        if (rt != null && page != null) {
            rt.layoutChanged();
        }
    }

    /** Las medidas de la última página acomodada, o null si todavía no hay. */
    Geometry geometry() {
        return this.geometry;
    }

    int[] viewport() {
        return this.viewport;
    }

    /** Un desplazamiento pedido por un script (window.scrollTo), o -1. Se lee una vez. */
    public int takeScrollRequest() {
        int v = this.scrollRequest;
        this.scrollRequest = -1;
        return v;
    }

    /** Cambia si se ejecutan los scripts; devuelve el valor nuevo. */
    public static boolean toggleScripting() {
        scripting = !scripting;
        return scripting;
    }

    public void setStatus(String text) {
        this.status = text;
    }

    /** Los mensajes de consola de los scripts (los últimos 300). */
    public List<String> consoleLines() {
        synchronized (this.consoleLog) {
            return new ArrayList<>(this.consoleLog);
        }
    }

    void log(String level, String text) {
        synchronized (this.consoleLog) {
            this.consoleLog.add(level + ": " + text);
            while (this.consoleLog.size() > 300) {
                this.consoleLog.remove(0);
            }
        }
        if (level.equals("error")) {
            this.status = "JS: " + shorten(text);
        }
    }

    private static String shorten(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 110 ? t.substring(0, 110) + "\u2026" : t;
    }

    public Node doc() {
        return this.doc;
    }

    public Css.Sheet sheet() {
        return this.sheet;
    }

    /** Dirección base para resolver los enlaces de la página mostrada. */
    public String baseUrl() {
        return this.baseUrl;
    }

    public String title() {
        return this.title;
    }

    /** Sube cada vez que cambia el contenido mostrado. */
    public int version() {
        return this.version;
    }

    /** Sube solo cuando se muestra otra página (no al refrescar la lista de descargas). */
    public int navigation() {
        return this.navigation;
    }

    public boolean loading() {
        return this.loading;
    }

    public String status() {
        return this.status;
    }

    public synchronized String url() {
        return this.index >= 0 ? this.history.get(this.index).url : "";
    }

    public synchronized boolean canBack() {
        return this.index > 0;
    }

    public synchronized boolean canForward() {
        return this.index < this.history.size() - 1;
    }

    /** Dónde quedó el scroll de esta página al volver a ella (-1 si es una página nueva). Se lee una vez. */
    public int takeScrollTarget() {
        int v = this.scrollTarget;
        this.scrollTarget = -1;
        return v;
    }

    public String takeAnchor() {
        String a = this.anchorTarget;
        this.anchorTarget = null;
        return a;
    }

    public synchronized void setScroll(int y) {
        if (this.index >= 0) {
            this.history.get(this.index).scroll = Math.max(0, y);
        }
    }

    // ------------------------------------------------------------------ navegar

    /** Lo que se escribió en la barra de direcciones. */
    public void go(String input) {
        navigate(Url.normalizeInput(input), true);
    }

    /** Un enlace en el que se hizo clic (ya resuelto) o un ancla "#x". */
    public void open(String target) {
        navigate(target, false);
    }

    private void navigate(String raw, boolean typed) {
        navigate(raw, typed, false);
    }

    private void navigate(String raw, boolean typed, boolean replace) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String target = Url.unwrapRedirect(raw.trim());
        String cur = url();
        boolean anchorOnly = target.startsWith("#");
        if (anchorOnly || (Url.isWeb(target) && target.indexOf('#') >= 0 && Url.isWeb(cur)
                && Url.stripFragment(target).equals(Url.stripFragment(cur)))) {
            gotoFragment(anchorOnly ? Url.stripFragment(cur) + target : target);
            return;
        }
        if (Url.isInternal(target) && !Url.isInternal(cur) && !allowedFromWeb(target)) {
            return;
        }
        String referer = !typed && Url.isWeb(cur) ? cur : null;
        addEntry(new Entry(target), replace);
        start(target, referer, false, null);
    }

    private void addEntry(Entry e, boolean replace) {
        synchronized (this) {
            if (replace && this.index >= 0) {
                this.history.set(this.index, e);
                return;
            }
            while (this.history.size() > this.index + 1) {
                this.history.remove(this.history.size() - 1);
            }
            this.history.add(e);
            while (this.history.size() > MAX_HISTORY) {
                this.history.remove(0);
            }
            this.index = this.history.size() - 1;
        }
    }

    /** Un enlace a otro punto de la misma página: no se carga nada, solo se baja hasta el ancla (y la página se entera). */
    private void gotoFragment(String full) {
        PageRuntime rt = this.runtime;
        if (rt != null && !rt.closed() && !this.loading) {
            String old = url();
            if (!old.equals(full)) {
                Entry e = new Entry(full);
                e.docId = rt.id();
                e.title = this.title;
                addEntry(e, false);
                rt.setUrl(full);
                rt.sameDocument(full, old, null, false);
            }
        }
        this.anchorTarget = Url.fragment(full);
        bump();
    }

    private static boolean allowedFromWeb(String target) {
        String l = target.toLowerCase(Locale.ROOT);
        return l.startsWith("hs:play") || l.startsWith("hs:download") || l.equals("hs:home") || l.equals("hs:downloads");
    }

    public void back() {
        jump(-1);
    }

    public void forward() {
        jump(1);
    }

    /** Se mueve por el historial. Si la entrada es de la misma página viva no se carga nada: la página recibe popstate. */
    void jump(int delta) {
        Entry e;
        Entry from;
        synchronized (this) {
            int to = this.index + delta;
            if (delta == 0 || to < 0 || to >= this.history.size()) {
                return;
            }
            from = this.history.get(this.index);
            this.index = to;
            e = this.history.get(to);
        }
        PageRuntime rt = this.runtime;
        if (rt != null && !rt.closed() && !this.loading && from.docId == rt.id() && e.docId == rt.id()) {
            rt.setUrl(e.url);
            rt.sameDocument(e.url, from.url, e.state, true);
            String frag = Url.fragment(e.url);
            if (frag != null && !frag.isEmpty()) {
                this.anchorTarget = frag;
            } else {
                this.scrollRequest = Math.max(0, e.scroll);
            }
            bump();
            return;
        }
        start(e.url, null, true, null);
    }

    public void reload() {
        Entry e;
        synchronized (this) {
            if (this.index < 0) {
                return;
            }
            e = this.history.get(this.index);
        }
        start(e.url, null, true, null);
    }

    public void stop() {
        synchronized (this) {
            this.gen++;
        }
        if (this.loading) {
            this.loading = false;
            this.status = InternalPages.t("Detenido", "Stopped");
        }
    }

    /** Si se está viendo la lista de descargas, la mantiene al día mientras algo se baja. */
    public void tick() {
        if ("hs:downloads".equals(url()) && !this.loading && Downloads.anyRunning()) {
            this.doc = HtmlParser.parse(InternalPages.downloads());
            bump();
        }
    }

    // ------------------------------------------------------------------ cargar

    private static final class Loaded {
        String url;
        String base;
        Node doc;
        Css.Sheet sheet = new Css.Sheet();
        String title = "";
        String status = "";
        PageRuntime runtime;
    }

    private void start(String target, String referer, boolean restore, Request req) {
        final int my;
        synchronized (this) {
            my = ++this.gen;
        }
        this.loading = true;
        String host = Url.isWeb(target) ? Url.hostOf(target) : "";
        this.status = InternalPages.t("Cargando ", "Loading ") + (host.isEmpty() ? target : host) + "\u2026";
        POOL.execute(() -> {
            Loaded result;
            try {
                result = fetch(target, referer, 0, req);
            } catch (Throwable e) {
                result = errorPage(target, e);
            }
            apply(my, target, result, restore);
        });
    }

    private void apply(int my, String target, Loaded r, boolean restore) {
        PageRuntime old;
        synchronized (this) {
            if (my != this.gen) {
                if (r.runtime != null) {
                    r.runtime.close();
                }
                return;
            }
            Entry entry = this.index >= 0 ? this.history.get(this.index) : null;
            int scroll = -1;
            if (entry != null) {
                if (restore) {
                    scroll = entry.scroll;
                }
                if (!r.url.equals(entry.url)) {
                    Entry replaced = new Entry(r.url);
                    replaced.scroll = entry.scroll;
                    this.history.set(this.index, replaced);
                    entry = replaced;
                }
                entry.title = r.title;
                entry.docId = r.runtime == null ? 0 : r.runtime.id();
                entry.state = null;
            }
            String fragment = Url.isWeb(target) ? Url.fragment(target) : null;
            old = this.runtime;
            this.runtime = r.runtime;
            this.doc = r.doc;
            this.sheet = r.runtime != null ? r.runtime.sheet() : r.sheet;
            this.baseUrl = r.base;
            this.title = r.title;
            this.scrollTarget = scroll;
            this.anchorTarget = restore ? null : fragment;
            this.status = r.status;
            this.loading = false;
            this.navigation++;
            this.version++;
            this.geometry = null;
            if (r.runtime != null) {
                r.runtime.pause(this.paused);
            }
        }
        if (old != null && old != r.runtime) {
            old.close();
        }
        if (r.runtime != null) {
            r.runtime.setUrl(r.url);
        }
    }

    private static Loaded page(String url, String html, String title, String status) {
        Loaded l = new Loaded();
        l.url = url;
        l.base = url;
        l.doc = HtmlParser.parse(html);
        l.title = title;
        l.status = status;
        return l;
    }

    private static Loaded errorPage(String target, Throwable e) {
        String msg;
        if (e instanceof UnknownHostException) {
            msg = InternalPages.t("No se encontr\u00f3 el servidor. Revisa la direcci\u00f3n o tu conexi\u00f3n.",
                    "Server not found. Check the address or your connection.");
        } else if (e instanceof SocketTimeoutException) {
            msg = InternalPages.t("El servidor tard\u00f3 demasiado en responder.", "The server took too long to answer.");
        } else if (e instanceof ConnectException) {
            msg = InternalPages.t("No se pudo conectar con el servidor.", "Could not connect to the server.");
        } else if (e instanceof SSLException) {
            msg = InternalPages.t("Error de seguridad en la conexi\u00f3n HTTPS: ", "HTTPS security error: ") + e.getMessage();
        } else if (e instanceof OutOfMemoryError) {
            msg = InternalPages.t("La p\u00e1gina es demasiado grande.", "The page is too big.");
        } else {
            String m = e.getMessage();
            msg = m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
        }
        return page(target, InternalPages.error(target, msg), InternalPages.t("Error", "Error"),
                InternalPages.t("Error: ", "Error: ") + msg);
    }

    private Loaded fetch(String target, String referer, int depth, Request req) throws Exception {
        if (Url.isInternal(target)) {
            return internal(target);
        }
        if (!Url.isWeb(target)) {
            throw new IOException(InternalPages.t("Direcci\u00f3n no v\u00e1lida", "Invalid address"));
        }
        String requested = Url.stripFragment(target);
        Http.Response r = req == null ? Http.get(requested, referer)
                : Http.request(req.method, requested, referer, Map.of("Content-Type", req.contentType), req.body);
        boolean keep = false;
        try {
            String mime = r.mime == null ? "" : r.mime.toLowerCase(Locale.ROOT);
            String name = Url.fileName(r.url, r.disposition, r.mime);
            String ext = Url.extension(name);
            boolean octet = mime.isEmpty() || mime.equals("application/octet-stream") || mime.equals("binary/octet-stream");
            boolean attachment = r.disposition != null && r.disposition.toLowerCase(Locale.ROOT).contains("attachment");
            String finalUrl = r.url;
            String shown = finalUrl;
            if (target.indexOf('#') >= 0 && Url.stripFragment(finalUrl).equals(requested)) {
                shown = finalUrl + target.substring(target.indexOf('#'));
            }

            if (attachment) {
                keep = true;
                Downloads.adopt(r);
                return downloadsPage();
            }
            if (Url.isPlaylistMime(mime) || (Url.isPlaylistName(name) && (octet || mime.startsWith("text/") || mime.startsWith("audio/")
                    || mime.startsWith("application/")))) {
                return playlist(r, name);
            }
            boolean live = r.isLiveStream();
            if (Url.isAacMime(mime) && live) {
                return page(finalUrl, InternalPages.message(InternalPages.t("Radio en AAC", "AAC radio"),
                        InternalPages.t("Esta transmisión está en AAC y el navegador solo puede reproducir MP3. Busca el enlace MP3 de la misma emisora.",
                                "This stream is AAC and the browser can only play MP3. Look for the MP3 link of the same station.")),
                        name, "AAC");
            }
            if (Url.isVideoMime(mime) || (Url.isVideoName(name) && (octet || mime.startsWith("video/") || mime.startsWith("application/")))) {
                r.close();
                keep = true;
                this.audio.playVideoUrl(finalUrl, name);
                return page("hs:video?src=" + Url.encode(finalUrl), InternalPages.video(name, finalUrl), name,
                        InternalPages.t("Descargando video", "Downloading video"));
            }
            if (Url.isAudioMime(mime) || (Url.isAudioName(name) && (octet || mime.startsWith("audio/") || mime.startsWith("application/")))
                    || (r.icy && !mime.startsWith("text/"))) {
                String label = r.icyName != null ? r.icyName : name;
                r.close();
                keep = true;
                this.audio.playUrl(finalUrl, label);
                return page("hs:track?src=" + Url.encode(finalUrl) + (live ? "&live=1" : ""),
                        InternalPages.track(label, finalUrl, live), label, InternalPages.t("Reproduciendo", "Playing"));
            }
            if (mime.startsWith("image/") || (octet && isImageExt(ext))) {
                return page(shown, InternalPages.image(finalUrl), name, statusOf(r));
            }
            boolean html = mime.equals("text/html") || mime.equals("application/xhtml+xml") || mime.isEmpty()
                    || (octet && (ext.equals("html") || ext.equals("htm")));
            if (html) {
                return htmlPage(r, shown, referer, depth);
            }
            if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("javascript")
                    || (octet && isTextExt(ext))) {
                boolean[] cut = new boolean[1];
                byte[] data = Http.readLimited(r.body, MAX_TEXT, cut);
                String text = Http.decode(data, r.charset, false);
                return page(shown, InternalPages.text(finalUrl, text, cut[0]), name, statusOf(r));
            }
            keep = true;
            Downloads.adopt(r);
            return downloadsPage();
        } finally {
            if (!keep) {
                r.close();
            }
        }
    }

    /** Lista de reproducción de radio (.m3u o .pls): toma la primera dirección y la reproduce. Las listas HLS no se pueden. */
    private Loaded playlist(Http.Response r, String name) throws IOException {
        byte[] data = Http.readLimited(r.body, 65536, new boolean[1]);
        String text = Http.decode(data, r.charset, false);
        String finalUrl = r.url;
        if (text.contains("#EXT-X-")) {
            return page(finalUrl, InternalPages.message("HLS", InternalPages.t(
                    "Esta radio usa HLS (una lista de trozos) y el navegador no lo soporta. Busca el enlace MP3 directo de la emisora.",
                    "This station uses HLS (a list of segments), which the browser does not support. Look for the direct MP3 link of the station.")),
                    name, "HLS");
        }
        String target = null;
        String title = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                if (comma >= 0 && comma + 1 < line.length() && title == null) {
                    title = line.substring(comma + 1).trim();
                }
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (lower.startsWith("title1=") && title == null) {
                title = line.substring(7).trim();
                continue;
            }
            String candidate = null;
            if (lower.startsWith("file1=")) {
                candidate = line.substring(6).trim();
            } else if (!lower.contains("=") || lower.startsWith("http")) {
                candidate = line;
            }
            if (candidate != null && target == null) {
                String abs = Url.resolve(finalUrl, candidate);
                if (abs != null && Url.isWeb(abs)) {
                    target = abs;
                }
            }
        }
        if (target == null) {
            return page(finalUrl, InternalPages.message(InternalPages.t("Lista vac\u00eda", "Empty list"), InternalPages.t(
                    "La lista no trae ninguna direcci\u00f3n que se pueda reproducir.", "The list has no playable address.")), name, "");
        }
        String label = title == null || title.isEmpty() ? Url.fileName(target, null, null) : title;
        this.audio.playUrl(target, label);
        return page("hs:track?src=" + Url.encode(target) + "&live=1", InternalPages.track(label, target, true), label,
                InternalPages.t("Reproduciendo", "Playing"));
    }

    private static boolean isImageExt(String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("bmp");
    }

    private static boolean isTextExt(String ext) {
        return ext.equals("txt") || ext.equals("log") || ext.equals("md") || ext.equals("json") || ext.equals("xml")
                || ext.equals("csv") || ext.equals("css") || ext.equals("js");
    }

    private static String statusOf(Http.Response r) {
        return r.status >= 400 ? "HTTP " + r.status : InternalPages.t("Listo", "Done");
    }

    private static Loaded downloadsPage() {
        return page("hs:downloads", InternalPages.downloads(), InternalPages.t("Descargas", "Downloads"),
                InternalPages.t("Descarga iniciada", "Download started"));
    }

    private Loaded internal(String target) {
        String l = target.toLowerCase(Locale.ROOT);
        String query = "";
        int q = target.indexOf('?');
        if (q >= 0) {
            query = target.substring(q + 1);
        }
        String route = q >= 0 ? l.substring(0, q) : l;
        switch (route) {
            case "hs:downloads":
                return page("hs:downloads", InternalPages.downloads(), InternalPages.t("Descargas", "Downloads"), "");
            case "hs:console": {
                String text = String.join("\n", consoleLines());
                return page("hs:console", InternalPages.text("hs:console", text.isEmpty() ? InternalPages.t("(vac\u00edo)", "(empty)") : text, false),
                        "Console", "");
            }
            case "hs:download": {
                String url = param(query, "url");
                if (url == null || !Url.isWeb(url)) {
                    return page("hs:home", InternalPages.home(), "HS Browser", "");
                }
                Downloads.start(url, null);
                return downloadsPage();
            }
            case "hs:cancel": {
                try {
                    Downloads.Job j = Downloads.find(Integer.parseInt(param(query, "id")));
                    if (j != null) {
                        j.cancel();
                    }
                } catch (RuntimeException ignored) {
                    // número malo: no se hace nada
                }
                return page("hs:downloads", InternalPages.downloads(), InternalPages.t("Descargas", "Downloads"), "");
            }
            case "hs:play": {
                String src = param(query, "src");
                if (src == null || !Url.isWeb(src)) {
                    return page("hs:home", InternalPages.home(), "HS Browser", "");
                }
                boolean live = "1".equals(param(query, "live"));
                String name = Url.fileName(src, null, null);
                this.audio.playUrl(src, name);
                return page("hs:track?src=" + Url.encode(src) + (live ? "&live=1" : ""), InternalPages.track(name, src, live), name,
                        InternalPages.t("Reproduciendo", "Playing"));
            }
            case "hs:playvideo": {
                String src = param(query, "src");
                if (src == null || !Url.isWeb(src)) {
                    return page("hs:home", InternalPages.home(), "HS Browser", "");
                }
                String name = Url.fileName(src, null, null);
                this.audio.playVideoUrl(src, name);
                return page("hs:video?src=" + Url.encode(src), InternalPages.video(name, src), name,
                        InternalPages.t("Descargando video", "Downloading video"));
            }
            case "hs:video": {
                String src = param(query, "src");
                String fileName = param(query, "file");
                if (src == null && fileName != null && Downloads.resolveFile(fileName) != null) {
                    return page("hs:video?file=" + Url.encode(fileName), InternalPages.videoFile(fileName), fileName, "");
                }
                if (src == null || !Url.isWeb(src)) {
                    return page("hs:home", InternalPages.home(), "HS Browser", "");
                }
                String name = Url.fileName(src, null, null);
                return page("hs:video?src=" + Url.encode(src), InternalPages.video(name, src), name, "");
            }
            case "hs:track": {
                String src = param(query, "src");
                if (src == null || !Url.isWeb(src)) {
                    return page("hs:home", InternalPages.home(), "HS Browser", "");
                }
                boolean live = "1".equals(param(query, "live"));
                String name = Url.fileName(src, null, null);
                return page("hs:track?src=" + Url.encode(src) + (live ? "&live=1" : ""), InternalPages.track(name, src, live), name, "");
            }
            case "hs:file": {
                Path file = Downloads.resolveFile(param(query, "name"));
                if (file == null) {
                    return page("hs:downloads", InternalPages.downloads(), InternalPages.t("Descargas", "Downloads"),
                            InternalPages.t("No se encontr\u00f3 el archivo", "File not found"));
                }
                if (Url.isVideoName(file.getFileName().toString())) {
                    this.audio.playVideoFile(file);
                    return page("hs:video?file=" + Url.encode(file.getFileName().toString()),
                            InternalPages.videoFile(file.getFileName().toString()), file.getFileName().toString(), "");
                }
                this.audio.playFile(file);
                return page("hs:downloads", InternalPages.downloads(), InternalPages.t("Descargas", "Downloads"),
                        InternalPages.t("Reproduciendo ", "Playing ") + file.getFileName());
            }
            default:
                return page(Url.HOME, InternalPages.home(), "HS Browser", "");
        }
    }

    private static String param(String query, String key) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return Url.decode(part.substring(eq + 1));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ páginas HTML

    private Loaded htmlPage(Http.Response r, String shown, String referer, int depth) throws Exception {
        boolean[] cut = new boolean[1];
        byte[] data = Http.readLimited(r.body, MAX_HTML, cut);
        String html = Http.decode(data, r.charset, true);
        Node doc = HtmlParser.parse(html);
        String base = r.url;
        Node baseTag = doc.find("base");
        if (baseTag != null && baseTag.attr("href") != null) {
            String nb = Url.resolve(base, baseTag.attr("href"));
            if (nb != null && Url.isWeb(nb)) {
                base = nb;
            }
        }
        if (depth < 3) {
            String next = metaRefresh(doc, base);
            if (next != null && !Url.stripFragment(next).equals(Url.stripFragment(r.url))) {
                return fetch(next, r.url, depth + 1, null);
            }
        }
        Loaded l = new Loaded();
        l.url = shown;
        l.base = base;
        l.doc = doc;
        Map<String, String> cssCache = new ConcurrentHashMap<>();
        l.sheet = buildSheet(doc, base, r.url, cssCache);
        if (scripting && Url.isWeb(shown) && PageRuntime.worthRunning(doc)) {
            PageRuntime rt = PageRuntime.create(this, doc, shown, base, referer, l.sheet, cssCache);
            if (rt != null) {
                rt.boot();
                l.runtime = rt;
            }
        }
        Node t = doc.find("title");
        String title = t == null ? "" : t.textContent();
        l.title = title.isEmpty() ? Url.hostOf(r.url) : title;
        l.status = r.status >= 400 ? "HTTP " + r.status : InternalPages.t("Listo", "Done");
        if (cut[0]) {
            l.status += InternalPages.t(" (p\u00e1gina recortada)", " (page cut)");
        }
        return l;
    }

    private static String metaRefresh(Node doc, String base) {
        for (Node meta : findAll(doc, "meta")) {
            String eq = meta.attr("http-equiv");
            if (eq != null && eq.equalsIgnoreCase("refresh") && meta.attr("content") != null) {
                Matcher m = REFRESH.matcher(meta.attr("content"));
                if (m.matches()) {
                    try {
                        if (Double.parseDouble(m.group(1)) <= 5) {
                            String u = Url.resolve(base, m.group(2).trim());
                            if (u != null && Url.isWeb(u)) {
                                return u;
                            }
                        }
                    } catch (NumberFormatException ignored) {
                        // se ignora
                    }
                }
            }
        }
        return null;
    }

    private static List<Node> findAll(Node n, String tag) {
        List<Node> out = new ArrayList<>();
        collect(n, tag, out, 0);
        return out;
    }

    private static void collect(Node n, String tag, List<Node> out, int depth) {
        if (depth > 200 || out.size() > 400) {
            return;
        }
        for (Node c : n.children) {
            if (c.isText()) {
                continue;
            }
            if (c.tag.equals(tag)) {
                out.add(c);
            }
            collect(c, tag, out, depth + 1);
        }
    }

    /** Junta los estilos de la p\u00e1gina (los de adentro y hasta 6 hojas externas) en el orden en que aparecen. */
    private static Css.Sheet buildSheet(Node doc, String base, String referer, Map<String, String> cache) {
        return assemble(sheetSources(doc, base), referer, cache);
    }

    /** Anota qu\u00e9 estilos tiene la p\u00e1gina ahora mismo (para poder armar la hoja sin tocar el \u00e1rbol). */
    static List<SheetSrc> sheetSources(Node doc, String base) {
        List<Node> sources = new ArrayList<>();
        collectStyles(doc, sources, 0);
        List<SheetSrc> out = new ArrayList<>();
        int sheets = 0;
        for (Node n : sources) {
            if (n.is("link")) {
                if (sheets < MAX_SHEETS) {
                    String href = Url.resolve(base, n.attr("href"));
                    if (href != null && Url.isWeb(href)) {
                        sheets++;
                        out.add(new SheetSrc(null, href));
                    }
                }
            } else {
                out.add(new SheetSrc(n.rawText(), null));
            }
        }
        return out;
    }

    static Css.Sheet assemble(List<SheetSrc> srcs, String referer, Map<String, String> cache) {
        List<Future<String>> fetched = new ArrayList<>();
        for (SheetSrc src : srcs) {
            if (src.href == null) {
                fetched.add(null);
                continue;
            }
            String cached = cache == null ? null : cache.get(src.href);
            if (cached != null) {
                fetched.add(CompletableFuture.completedFuture(cached));
            } else {
                String href = src.href;
                fetched.add(POOL.submit(() -> fetchCss(href, referer)));
            }
        }
        Css.Sheet sheet = new Css.Sheet();
        long deadline = System.currentTimeMillis() + 8000;
        for (int i = 0; i < srcs.size(); i++) {
            SheetSrc src = srcs.get(i);
            String css = src.text;
            if (css == null && fetched.get(i) != null) {
                try {
                    css = fetched.get(i).get(Math.max(100, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                    if (css != null && cache != null) {
                        cache.put(src.href, css);
                    }
                } catch (Exception e) {
                    fetched.get(i).cancel(true);
                }
            }
            if (css != null && !css.isEmpty()) {
                try {
                    sheet.add(css);
                } catch (RuntimeException ignored) {
                    // una hoja rota no tumba la p\u00e1gina
                }
            }
        }
        return sheet;
    }

    static void collectStyles(Node n, List<Node> out, int depth) {
        if (depth > 200 || out.size() > 60) {
            return;
        }
        for (Node c : n.children) {
            if (c.isText()) {
                continue;
            }
            if (c.is("style")) {
                String media = c.attr("media");
                if (media == null || !media.toLowerCase(Locale.ROOT).contains("print")) {
                    out.add(c);
                }
            } else if (c.is("link")) {
                String rel = c.attr("rel");
                String media = c.attr("media");
                if (rel != null && rel.toLowerCase(Locale.ROOT).contains("stylesheet") && !rel.toLowerCase(Locale.ROOT).contains("alternate")
                        && (media == null || !media.toLowerCase(Locale.ROOT).contains("print")) && c.attr("href") != null) {
                    out.add(c);
                }
            } else {
                collectStyles(c, out, depth + 1);
            }
        }
    }

    private static String fetchCss(String href, String referer) {
        return fetchText(href, referer, MAX_CSS);
    }

    /** Baja un texto (una hoja de estilo, un script): null si falla o responde con error. */
    static String fetchText(String href, String referer, int max) {
        try (Http.Response r = Http.get(href, referer)) {
            if (r.status >= 400) {
                return null;
            }
            byte[] data = Http.readLimited(r.body, max, new boolean[1]);
            return Http.decode(data, r.charset, false);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ lo que piden los scripts de la página

    void styleChanged(PageRuntime rt, List<SheetSrc> srcs) {
        int gen = rt.sheetGen.incrementAndGet();
        POOL.execute(() -> {
            Css.Sheet s;
            try {
                s = assemble(srcs, rt.url(), rt.cssCache);
            } catch (RuntimeException e) {
                return;
            }
            if (rt.closed() || rt.sheetGen.get() != gen) {
                return;
            }
            rt.setSheet(s);
            synchronized (BrowserSession.this) {
                if (this.runtime == rt) {
                    this.sheet = s;
                    this.version++;
                }
            }
        });
    }

    void contentChanged(PageRuntime rt) {
        if (rt == this.runtime) {
            bump();
        }
    }

    void scriptNavigate(PageRuntime rt, String url, boolean replace) {
        if (rt == this.runtime) {
            navigate(url, false, replace);
        }
    }

    void scriptReload(PageRuntime rt) {
        if (rt == this.runtime) {
            reload();
        }
    }

    void scriptHistoryGo(PageRuntime rt, int delta) {
        if (rt == this.runtime) {
            jump(delta);
        }
    }

    synchronized int historySize() {
        return this.history.size();
    }

    void scriptPush(PageRuntime rt, String u, Object state, boolean replace) {
        if (rt != this.runtime) {
            return;
        }
        synchronized (this) {
            if (this.index < 0) {
                return;
            }
            Entry cur = this.history.get(this.index);
            if (replace) {
                if (u != null) {
                    cur.url = u;
                }
                cur.state = state;
                cur.docId = rt.id();
            } else {
                Entry e = new Entry(u != null ? u : cur.url);
                e.state = state;
                e.docId = rt.id();
                e.title = cur.title;
                e.scroll = 0;
                while (this.history.size() > this.index + 1) {
                    this.history.remove(this.history.size() - 1);
                }
                this.history.add(e);
                while (this.history.size() > MAX_HISTORY) {
                    this.history.remove(0);
                }
                this.index = this.history.size() - 1;
            }
        }
        if (u != null) {
            rt.setUrl(u);
        }
    }

    synchronized Object scriptState(PageRuntime rt) {
        if (rt != this.runtime || this.index < 0) {
            return null;
        }
        Entry e = this.history.get(this.index);
        return e.docId == rt.id() ? e.state : null;
    }

    void scriptDialog(PageRuntime rt, String kind, String message) {
        log("info", "[" + kind + "] " + message);
        if (rt == this.runtime) {
            this.status = "[" + kind + "] " + shorten(message);
        }
    }

    void scriptTitle(PageRuntime rt, String title) {
        if (rt != this.runtime) {
            return;
        }
        synchronized (this) {
            this.title = title;
            if (this.index >= 0) {
                this.history.get(this.index).title = title;
            }
        }
    }

    void scriptScroll(PageRuntime rt, int y) {
        if (rt == this.runtime) {
            int top = Math.max(0, y);
            Geometry g = this.geometry;
            int[] vp = this.viewport;
            if (g != null) {
                top = Math.min(top, Math.max(0, g.height() - vp[1]));
            }
            this.scrollRequest = top;
            if (vp[3] != top) {
                this.viewport = new int[] {vp[0], vp[1], vp[2], top};      // scrollY ya lee el valor nuevo
                rt.viewportChanged(true, false);
            }
        }
    }

    void scriptStatus(PageRuntime rt, String text) {
        if (rt == this.runtime) {
            this.status = text;
        }
    }

    /** Env\u00eda un formulario: GET va en la direcci\u00f3n; POST abre una entrada nueva del historial y manda el cuerpo. */
    void scriptSubmit(PageRuntime rt, DomHost.Submission s) {
        if (rt != this.runtime) {
            return;
        }
        String action = s.action;
        if (action == null || !Url.isWeb(action)) {
            this.status = InternalPages.t("No se puede enviar el formulario a esa direcci\u00f3n.", "The form cannot be sent to that address.");
            return;
        }
        if (s.method.equals("GET")) {
            String base = Url.stripFragment(action);
            int q = base.indexOf('?');
            if (q >= 0) {
                base = base.substring(0, q);
            }
            String enc = s.encoded();
            navigate(base + (enc.isEmpty() ? "" : "?" + enc), false, false);
            return;
        }
        byte[] body;
        String type;
        switch (s.enctype) {
            case "multipart/form-data" -> {
                String boundary = "----HSBoundary" + Long.toHexString(System.nanoTime());
                body = s.multipart(boundary);
                type = "multipart/form-data; boundary=" + boundary;
            }
            case "text/plain" -> {
                StringBuilder sb = new StringBuilder();
                for (String[] f : s.fields) {
                    sb.append(f[0]).append('=').append(f[1]).append("\r\n");
                }
                body = sb.toString().getBytes(StandardCharsets.UTF_8);
                type = "text/plain";
            }
            default -> {
                body = s.encoded().getBytes(StandardCharsets.UTF_8);
                type = "application/x-www-form-urlencoded";
            }
        }
        String cur = url();
        String target = Url.stripFragment(action);
        addEntry(new Entry(target), false);
        start(target, Url.isWeb(cur) ? cur : null, false, new Request("POST", body, type));
    }
}
