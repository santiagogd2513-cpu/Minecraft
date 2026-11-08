package com.example.beautifuldecor.browser;

import com.example.beautifuldecor.browser.js.DomHost;
import com.example.beautifuldecor.browser.js.JsAbort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * La parte "viva" de una página: su documento, su motor de JavaScript y el hilo donde corren los scripts y los eventos.
 * Todo lo que toque el DOM pasa por ese hilo; quien dibuja la página usa {@link #tryLock()} para no leer el árbol mientras cambia.
 */
public final class PageRuntime {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final long STACK = 128L << 20;
    private static final int SCRIPT_MS = 4000;
    private static final int EVENT_MS = 1500;
    private static final int BOOT_MS = 12000;
    private static final int MAX_SCRIPT = 2 << 20;
    private static final int MAX_SCRIPTS = 24;
    private static final int MAX_FETCH = 8 << 20;

    private final int id = IDS.incrementAndGet();
    private final BrowserSession session;
    private final Node doc;
    private final ReentrantLock lock = new ReentrantLock();
    private final Object qlock = new Object();
    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private long seq;
    private Thread thread;
    private volatile boolean closed;
    private volatile DomHost host;
    private volatile String url;
    private volatile String base;
    private final String referer;
    private volatile Css.Sheet sheet;
    private final Map<String, String> scripts = new ConcurrentHashMap<>();
    final Map<String, String> cssCache;
    final AtomicInteger sheetGen = new AtomicInteger();
    private final CountDownLatch ready = new CountDownLatch(1);
    private boolean hasScripts;
    private int seenMutations = -1;
    private String styleSig = "";
    private volatile Node focus;
    private volatile boolean textFocus;
    private volatile boolean keyListeners;
    private volatile boolean moveListeners;
    private final AtomicBoolean movePending = new AtomicBoolean();
    private volatile boolean busy;
    private volatile boolean paused;

    private static final class Task implements Comparable<Task> {
        final long due;
        final long seq;
        final Runnable run;
        final int limit;

        Task(long due, long seq, Runnable run, int limit) {
            this.due = due;
            this.seq = seq;
            this.run = run;
            this.limit = limit;
        }

        @Override
        public int compareTo(Task o) {
            int c = Long.compare(this.due, o.due);
            return c != 0 ? c : Long.compare(this.seq, o.seq);
        }
    }

    private PageRuntime(BrowserSession session, Node doc, String url, String base, String referer, Css.Sheet sheet,
                        Map<String, String> cssCache) {
        this.cssCache = cssCache;
        this.session = session;
        this.doc = doc;
        this.url = url;
        this.base = base;
        this.referer = referer;
        this.sheet = sheet;
    }

    /** Cierto si la página trae algo que ejecutar (un script o un atributo onclick="...") o si tiene formularios que atender. */
    public static boolean worthRunning(Node doc) {
        return scan(doc, 0);
    }

    private static boolean scan(Node n, int depth) {
        if (depth > 300) {
            return false;
        }
        for (Node c : n.children) {
            if (!c.isElement()) {
                continue;
            }
            String t = c.tag;
            if (t.equals("script") || t.equals("form") || t.equals("input") || t.equals("textarea") || t.equals("select")
                    || t.equals("button") || t.equals("details")) {
                return true;
            }
            for (String a : c.attrs.keySet()) {
                if (a.startsWith("on")) {
                    return true;
                }
            }
            if (scan(c, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyScript(Node n, int depth) {
        if (depth > 300) {
            return false;
        }
        for (Node c : n.children) {
            if (!c.isElement()) {
                continue;
            }
            if (c.tag.equals("script") && DomHost.runnable(c)) {
                return true;
            }
            for (String a : c.attrs.keySet()) {
                if (a.startsWith("on")) {
                    return true;
                }
            }
            if (anyScript(c, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void scriptNodes(Node n, List<Node> out, int depth) {
        if (depth > 300 || out.size() > 400) {
            return;
        }
        for (Node c : n.children) {
            if (!c.isElement()) {
                continue;
            }
            if (c.tag.equals("script") && DomHost.runnable(c)) {
                out.add(c);
            }
            scriptNodes(c, out, depth + 1);
        }
    }

    /** Prepara la página para ejecutarla: baja los scripts externos y arranca el hilo. Devuelve null si no se pudo. */
    public static PageRuntime create(BrowserSession session, Node doc, String url, String base, String referer, Css.Sheet sheet,
                                     Map<String, String> cssCache) {
        PageRuntime rt = new PageRuntime(session, doc, url, base, referer, sheet, cssCache);
        rt.hasScripts = anyScript(doc, 0);
        rt.prefetch();
        Thread t = new Thread(null, rt::loop, "HS Page " + rt.id, STACK);
        t.setDaemon(true);
        rt.thread = t;
        t.start();
        try {
            if (!rt.ready.await(15, TimeUnit.SECONDS) || rt.host == null) {
                rt.close();
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rt.close();
            return null;
        }
        return rt;
    }

    private void prefetch() {
        List<Node> nodes = new ArrayList<>();
        scriptNodes(this.doc, nodes, 0);
        List<String> urls = new ArrayList<>();
        List<Future<String>> jobs = new ArrayList<>();
        for (Node n : nodes) {
            String src = n.attr("src");
            if (src == null || src.isBlank() || urls.size() >= MAX_SCRIPTS) {
                continue;
            }
            String abs = Url.resolve(this.base, src);
            if (abs == null || !Url.isWeb(abs) || urls.contains(abs)) {
                continue;
            }
            urls.add(abs);
            String ref = this.referer == null ? this.url : this.referer;
            jobs.add(BrowserSession.POOL.submit(() -> BrowserSession.fetchText(abs, ref, MAX_SCRIPT)));
        }
        long end = System.currentTimeMillis() + 10000;
        for (int i = 0; i < urls.size(); i++) {
            try {
                String text = jobs.get(i).get(Math.max(100, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (text != null) {
                    this.scripts.put(urls.get(i), text);
                }
            } catch (Exception e) {
                jobs.get(i).cancel(true);
            }
        }
    }

    // ------------------------------------------------------------------ datos de la página

    public int id() {
        return this.id;
    }

    public String url() {
        return this.url;
    }

    void setUrl(String u) {
        this.url = u;
    }

    public String base() {
        return this.base;
    }

    public Css.Sheet sheet() {
        return this.sheet;
    }

    void setSheet(Css.Sheet s) {
        this.sheet = s;
    }

    public Node doc() {
        return this.doc;
    }

    /** El elemento con el foco (o null). */
    public Node focusedNode() {
        return this.focus;
    }

    /** Cierto si hay un campo de texto con foco: la pantalla le manda todas las teclas. */
    public boolean textFocused() {
        return this.textFocus;
    }

    /** Cierto si la página escucha el teclado o hay un campo de texto con foco. */
    public boolean wantsKeys() {
        return this.textFocus || this.keyListeners;
    }

    public boolean wantsMoves() {
        return this.moveListeners;
    }

    /** Cierto mientras se ejecuta algo de la página (un clic en un enlace entonces se atiende sin esperar). */
    public boolean busy() {
        return this.busy;
    }

    public boolean closed() {
        return this.closed;
    }

    // ------------------------------------------------------------------ bloqueo del árbol

    /** Quien dibuja la página lo pide antes de leer el árbol; si falla, la página está cambiando y se espera al siguiente cuadro. */
    public boolean tryLock() {
        return this.lock.tryLock();
    }

    public void unlock() {
        this.lock.unlock();
    }

    // ------------------------------------------------------------------ cola de tareas

    /** Pone una tarea en la cola del hilo de los scripts. */
    public void post(Runnable r) {
        enqueue(r, 0, EVENT_MS);
    }

    void enqueue(Runnable r, long delayMs, int limitMs) {
        if (this.closed) {
            return;
        }
        synchronized (this.qlock) {
            this.queue.add(new Task(System.nanoTime() / 1_000_000L + Math.max(0, delayMs), ++this.seq, r, limitMs));
            this.qlock.notifyAll();
        }
    }

    // ------------------------------------------------------------------ medidas (V2.10.0)

    private final java.util.concurrent.atomic.AtomicBoolean geoQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean geoScrolled;
    private volatile boolean geoResized;

    /** La ventana se movió o cambió de tamaño: la página recibe scroll / resize y los observadores revisan sus elementos. */
    public void viewportChanged(boolean scrolled, boolean resized) {
        if (scrolled) {
            this.geoScrolled = true;
        }
        if (resized) {
            this.geoResized = true;
        }
        queueGeometry();
    }

    /** La pantalla publicó una página acomodada nueva: los observadores revisan sus elementos. */
    public void layoutChanged() {
        queueGeometry();
    }

    private void queueGeometry() {
        if (this.closed || this.host == null) {
            return;
        }
        if (this.geoQueued.compareAndSet(false, true)) {
            enqueue(() -> {
                this.geoQueued.set(false);
                boolean s = this.geoScrolled;
                boolean r = this.geoResized;
                this.geoScrolled = false;
                this.geoResized = false;
                DomHost h = this.host;
                if (h != null) {
                    h.viewportChanged(s, r);
                }
            }, 0, EVENT_MS);
        }
    }

    /** Mientras el navegador no está en pantalla, los temporizadores y las respuestas de red esperan (no se gasta el procesador). */
    public void pause(boolean p) {
        synchronized (this.qlock) {
            this.paused = p;
            this.qlock.notifyAll();
        }
    }

    private <T> T call(Callable<T> c, T fallback, long timeoutMs) {
        if (this.closed) {
            return fallback;
        }
        CompletableFuture<T> f = new CompletableFuture<>();
        enqueue(() -> {
            try {
                f.complete(c.call());
            } catch (Exception e) {
                f.complete(fallback);
            }
        }, 0, EVENT_MS);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void loop() {
        try {
            DomHost h = new DomHost(this.doc, new RtEnv());
            this.doc.scripting = this.hasScripts;
            this.host = h;
            this.styleSig = styleSignature();
            this.seenMutations = Node.mutations();
        } catch (Throwable t) {
            this.closed = true;
            this.ready.countDown();
            return;
        }
        this.ready.countDown();
        while (!this.closed) {
            Task t;
            synchronized (this.qlock) {
                while (true) {
                    if (this.closed) {
                        return;
                    }
                    if (this.paused) {
                        try {
                            this.qlock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                        continue;
                    }
                    t = this.queue.peek();
                    long now = System.nanoTime() / 1_000_000L;
                    if (t == null) {
                        try {
                            this.qlock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    } else if (t.due <= now) {
                        this.queue.poll();
                        break;
                    } else {
                        try {
                            this.qlock.wait(t.due - now);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }
            runTask(t);
        }
    }

    private void runTask(Task t) {
        this.lock.lock();
        this.busy = true;
        try {
            if (this.closed) {
                return;
            }
            if (t.limit > 0) {
                this.host.setDeadline(t.limit);
            }
            t.run.run();
        } catch (JsAbort a) {
            this.session.log("error", "Script stopped: " + a.getMessage());
        } catch (Throwable e) {
            this.session.log("error", "Internal error: " + e);
        } finally {
            try {
                this.host.setDeadline(0);
                afterTask();
            } catch (Throwable ignored) {
                // la página ya no se puede usar; se sigue con lo demás
            }
            this.busy = false;
            this.lock.unlock();
        }
    }

    /** Tras cada tarea: avisa si el documento cambió y deja a mano lo que la pantalla necesita saber. */
    private void afterTask() {
        DomHost h = this.host;
        this.focus = h.focusedNode();
        this.textFocus = h.textFocused();
        this.keyListeners = h.keyListeners;
        this.moveListeners = h.moveListeners;
        int m = Node.mutations();
        if (m != this.seenMutations) {
            this.seenMutations = m;
            String sig = styleSignature();
            if (!sig.equals(this.styleSig)) {
                this.styleSig = sig;
                this.session.styleChanged(this, BrowserSession.sheetSources(this.doc, this.base));
            }
            this.session.contentChanged(this);
        }
    }

    private String styleSignature() {
        List<Node> sources = new ArrayList<>();
        BrowserSession.collectStyles(this.doc, sources, 0);
        StringBuilder sb = new StringBuilder();
        for (Node n : sources) {
            if (n.is("style")) {
                String t = n.rawText();
                sb.append('s').append(t.length()).append(':').append(t.hashCode());
            } else {
                sb.append('l').append(n.attr("href"));
            }
            sb.append('|');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ arranque y cierre

    /** Ejecuta los scripts de la página en orden y dispara DOMContentLoaded y load. Espera a que termine (con un tope de tiempo). */
    public void boot() {
        CountDownLatch done = new CountDownLatch(1);
        enqueue(() -> {
            try {
                runBoot();
            } finally {
                done.countDown();
            }
        }, 0, 0);
        try {
            done.await(BOOT_MS + 4000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runBoot() {
        DomHost h = this.host;
        long end = System.currentTimeMillis() + BOOT_MS;
        boolean moduleWarned = false;
        for (int guard = 0; guard < 600; guard++) {
            List<Node> pending = h.pendingScripts();
            if (pending.isEmpty()) {
                break;
            }
            Node s = pending.get(0);
            if (!DomHost.runnable(s)) {
                h.markDone(s);
                String type = s.attr("type");
                if (!moduleWarned && type != null && type.trim().equalsIgnoreCase("module")) {
                    moduleWarned = true;
                    this.session.log("warn", "Module scripts (type=module) are not supported.");
                }
                continue;
            }
            String code;
            String src = s.attr("src");
            if (src != null && !src.isBlank()) {
                String abs = Url.resolve(this.base, src);
                code = abs == null ? null : this.scripts.get(abs);
                if (code == null) {
                    h.markDone(s);
                    this.session.log("error", "Failed to load script " + src);
                    continue;
                }
            } else {
                code = s.rawText();
            }
            if (System.currentTimeMillis() > end) {
                h.markDone(s);
                continue;
            }
            h.setDeadline(SCRIPT_MS);
            try {
                h.runScript(s, code);
            } catch (JsAbort a) {
                this.session.log("error", "Script stopped: " + a.getMessage());
            }
            h.setDeadline(0);
            afterTask();
        }
        h.setDeadline(EVENT_MS);
        try {
            h.setReadyState("interactive");
            h.fire(false, "DOMContentLoaded");
            h.setReadyState("complete");
            h.fire(true, "load");
        } catch (JsAbort a) {
            this.session.log("error", "Script stopped: " + a.getMessage());
        }
        h.setDeadline(0);
    }

    /** Cierra la página: el motor se detiene y el hilo termina. */
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        DomHost h = this.host;
        if (h != null) {
            try {
                h.close();
            } catch (RuntimeException ignored) {
                // ya estaba cerrado
            }
        }
        synchronized (this.qlock) {
            this.queue.clear();
            this.qlock.notifyAll();
        }
    }

    // ------------------------------------------------------------------ entradas del usuario

    /** Un clic sobre un elemento (null: sobre el fondo de la página). Las coordenadas son de la ventana de la página. */
    public void click(Node target, int x, int y, int button, int mods) {
        enqueue(() -> this.host.click(target, x, y, button, mods), 0, EVENT_MS);
    }

    public void mouseMove(Node target, int x, int y, int mods) {
        if (!this.moveListeners || !this.movePending.compareAndSet(false, true)) {
            return;
        }
        enqueue(() -> {
            this.movePending.set(false);
            this.host.mouseMove(target, x, y, mods);
        }, 0, EVENT_MS);
    }

    /** Una tecla pulsada. Verdadero si la página o el campo la usaron. Espera un momento la respuesta. */
    public boolean keyDown(String key, String code, int keyCode, int mods, boolean repeat) {
        return call(() -> this.host.keyDown(key, code, keyCode, mods, repeat), false, 150);
    }

    public void keyUp(String key, String code, int keyCode, int mods) {
        enqueue(() -> this.host.keyUp(key, code, keyCode, mods), 0, EVENT_MS);
    }

    public boolean typeText(String text, int mods) {
        return call(() -> this.host.typeText(text, mods), false, 150);
    }

    public void paste(String text) {
        enqueue(() -> this.host.paste(text), 0, EVENT_MS);
    }

    /** El texto seleccionado en el campo con foco (para copiar). */
    public String selectedText() {
        return call(() -> this.host.selectedText(), "", 120);
    }

    public void chooseOption(Node select, int index) {
        enqueue(() -> this.host.chooseOption(select, index), 0, EVENT_MS);
    }

    public void blurFocus() {
        enqueue(() -> this.host.blurFocus(), 0, EVENT_MS);
    }

    /** Las opciones de un select para mostrar la lista, o null si ahora no se puede leer. */
    public SelectInfo selectInfo(Node select) {
        if (!this.lock.tryLock()) {
            return null;
        }
        try {
            List<Node> opts = DomHost.selectOptions(select);
            SelectInfo info = new SelectInfo(select, opts.size(), select.hasAttr("multiple"));
            for (int i = 0; i < opts.size(); i++) {
                Node o = opts.get(i);
                String label = o.attr("label");
                if (label == null || label.isBlank()) {
                    label = o.textContent().trim();
                }
                info.labels[i] = label;
                info.selected[i] = DomHost.optionSelected(o);
                boolean dis = o.hasAttr("disabled") || (o.parent != null && o.parent.is("optgroup") && o.parent.hasAttr("disabled"));
                info.disabled[i] = dis;
            }
            return info;
        } finally {
            this.lock.unlock();
        }
    }

    /** Lo que la pantalla necesita para mostrar la lista de un select. */
    public static final class SelectInfo {
        public final Node select;
        public final String[] labels;
        public final boolean[] selected;
        public final boolean[] disabled;
        public final boolean multiple;

        SelectInfo(Node select, int n, boolean multiple) {
            this.select = select;
            this.labels = new String[n];
            this.selected = new boolean[n];
            this.disabled = new boolean[n];
            this.multiple = multiple;
        }
    }

    // ------------------------------------------------------------------ avisos de la sesión

    /** La sesión cambió la dirección sin cargar otra página (volver a una entrada creada con pushState, o un ancla). */
    void sameDocument(String newUrl, String oldUrl, Object state, boolean popstate) {
        enqueue(() -> {
            this.url = newUrl;
            if (popstate) {
                this.host.firePopState(state);
            }
            if (oldUrl != null && !java.util.Objects.equals(Url.fragment(oldUrl), Url.fragment(newUrl))) {
                this.host.fireHashChange(oldUrl, newUrl);
            }
        }, 0, EVENT_MS);
    }

    /** Entorno del DOM: lo que el documento pide al navegador. */
    private final class RtEnv implements DomHost.Env {
        @Override
        public String url() {
            return PageRuntime.this.url;
        }

        @Override
        public String baseUrl() {
            return PageRuntime.this.base;
        }

        @Override
        public String referrer() {
            return PageRuntime.this.referer == null ? "" : PageRuntime.this.referer;
        }

        @Override
        public Css.Sheet sheet() {
            return PageRuntime.this.sheet;
        }

        @Override
        public void navigate(String u, boolean replace) {
            PageRuntime.this.session.scriptNavigate(PageRuntime.this, u, replace);
        }

        @Override
        public void follow(String u, boolean download) {
            PageRuntime.this.session.scriptNavigate(PageRuntime.this, u, false);
        }

        @Override
        public void reload() {
            PageRuntime.this.session.scriptReload(PageRuntime.this);
        }

        @Override
        public void historyGo(int delta) {
            PageRuntime.this.session.scriptHistoryGo(PageRuntime.this, delta);
        }

        @Override
        public int historyLength() {
            return PageRuntime.this.session.historySize();
        }

        @Override
        public void historyPush(String u, Object state, boolean replace) {
            PageRuntime.this.session.scriptPush(PageRuntime.this, u, state, replace);
        }

        @Override
        public Object historyState() {
            return PageRuntime.this.session.scriptState(PageRuntime.this);
        }

        @Override
        public void submit(DomHost.Submission s) {
            PageRuntime.this.session.scriptSubmit(PageRuntime.this, s);
        }

        @Override
        public void alert(String message) {
            PageRuntime.this.session.scriptDialog(PageRuntime.this, "alert", message);
        }

        @Override
        public boolean confirm(String message) {
            PageRuntime.this.session.scriptDialog(PageRuntime.this, "confirm", message);
            return true;
        }

        @Override
        public String prompt(String message, String initial) {
            PageRuntime.this.session.scriptDialog(PageRuntime.this, "prompt", message);
            return initial;
        }

        @Override
        public String cookie() {
            return Http.cookiesFor(PageRuntime.this.url);
        }

        @Override
        public void setCookie(String cookie) {
            Http.setCookie(PageRuntime.this.url, cookie);
        }

        @Override
        public void titleChanged(String title) {
            PageRuntime.this.session.scriptTitle(PageRuntime.this, title);
        }

        @Override
        public void console(String level, String text) {
            PageRuntime.this.session.log(level, text);
        }

        @Override
        public int[] viewport() {
            return PageRuntime.this.session.viewport();
        }

        @Override
        public void scrollTo(int x, int y) {
            PageRuntime.this.session.scriptScroll(PageRuntime.this, y);
        }

        @Override
        public int[] rect(Node n) {
            Geometry g = PageRuntime.this.session.geometry();
            return g == null ? null : g.rect(n);
        }

        @Override
        public Node nodeAt(int x, int y) {
            Geometry g = PageRuntime.this.session.geometry();
            return g == null ? null : g.nodeAt(x, y);
        }

        @Override
        public int[] pageSize() {
            Geometry g = PageRuntime.this.session.geometry();
            return g == null ? null : new int[] {g.width(), g.height(), g.fresh() ? 1 : 0};
        }

        @Override
        public void loadScript(String u, Consumer<String> done) {
            String abs = Url.resolve(PageRuntime.this.base, u);
            if (abs == null || !Url.isWeb(abs)) {
                done.accept(null);
                return;
            }
            String ref = PageRuntime.this.url;
            BrowserSession.POOL.execute(() -> {
                String text = BrowserSession.fetchText(abs, ref, MAX_SCRIPT);
                enqueue(() -> done.accept(text), 0, SCRIPT_MS);
            });
        }

        @Override
        public void post(Runnable task) {
            PageRuntime.this.enqueue(task, 0, EVENT_MS);
        }

        @Override
        public void status(String text) {
            PageRuntime.this.session.scriptStatus(PageRuntime.this, text);
        }

        @Override
        public boolean schedule(Runnable task, long delayMs) {
            if (PageRuntime.this.closed) {
                return false;
            }
            PageRuntime.this.enqueue(task, delayMs, EVENT_MS);
            return true;
        }

        @Override
        public Runnable fetch(DomHost.NetRequest q, Consumer<DomHost.NetResult> done) {
            boolean[] cancelled = {false};
            Http.Response[] open = new Http.Response[1];
            String ref = PageRuntime.this.url;
            BrowserSession.POOL.execute(() -> {
                DomHost.NetResult res = request(q, ref, cancelled, open);
                if (!cancelled[0]) {
                    enqueue(() -> done.accept(res), 0, EVENT_MS);
                }
            });
            return () -> {
                cancelled[0] = true;
                Http.Response r;
                synchronized (open) {
                    r = open[0];
                }
                if (r != null) {
                    r.close();
                }
            };
        }

        @Override
        public DomHost.NetResult fetchSync(DomHost.NetRequest q) {
            return request(q, PageRuntime.this.url, new boolean[1], new Http.Response[1]);
        }
    }

    /** Hace la petición de un script (en el hilo que la llame) y lee la respuesta completa, con tope de tamaño. */
    private static DomHost.NetResult request(DomHost.NetRequest q, String referer, boolean[] cancelled, Http.Response[] holder) {
        DomHost.NetResult out = new DomHost.NetResult();
        out.url = q.url;
        try {
            Http.Response resp = Http.request(q.method, q.url, referer, new LinkedHashMap<>(q.headers), q.body);
            synchronized (holder) {
                holder[0] = resp;
            }
            try {
                if (cancelled[0]) {
                    out.error = "cancelled";
                    return out;
                }
                boolean[] cut = {false};
                byte[] bytes = Http.readLimited(resp.body, MAX_FETCH, cut);
                if (cut[0]) {
                    out.error = "response too large (more than " + (MAX_FETCH >> 20) + " MB)";
                    return out;
                }
                out.status = resp.status;
                out.url = resp.url;
                out.body = bytes;
                out.headers.putAll(resp.headers);
            } finally {
                resp.close();
            }
        } catch (IOException e) {
            out.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } catch (RuntimeException e) {
            out.error = e.toString();
        }
        return out;
    }
}
