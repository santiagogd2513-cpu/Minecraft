package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Css;
import com.example.beautifuldecor.browser.Node;
import com.example.beautifuldecor.browser.Url;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * El DOM para JavaScript: envuelve el árbol de {@link Node} de una página en objetos (document, elementos, eventos, formularios...)
 * y se los da a un {@link JsEngine}. Todo se usa desde un solo hilo (el de los scripts de la página).
 */
public final class DomHost {
    /** Lo que el DOM necesita del navegador. Todo tiene un valor por defecto para poder probarlo sin navegador. */
    public interface Env {
        /** Dirección de la página. */
        default String url() {
            return "about:blank";
        }

        /** Dirección base para resolver los enlaces. */
        default String baseUrl() {
            return url();
        }

        default String referrer() {
            return "";
        }

        /** La hoja de estilo de la página (para getComputedStyle), o null. */
        default Css.Sheet sheet() {
            return null;
        }

        /** Ir a otra dirección (location.href = ...). */
        default void navigate(String url, boolean replace) {
        }

        /** Un clic en un enlace: {@code download} si el enlace tiene el atributo download. */
        default void follow(String url, boolean download) {
            navigate(url, false);
        }

        default void reload() {
        }

        default void historyGo(int delta) {
        }

        default int historyLength() {
            return 1;
        }

        /** history.pushState / replaceState: cambia la dirección sin cargar nada. */
        default void historyPush(String url, Object state, boolean replace) {
        }

        default Object historyState() {
            return Js.NULL;
        }

        /** Envía un formulario (el navegador arma la petición y carga la respuesta). */
        default void submit(Submission s) {
        }

        default void alert(String message) {
        }

        default boolean confirm(String message) {
            return true;
        }

        default String prompt(String message, String initial) {
            return initial;
        }

        default String cookie() {
            return "";
        }

        default void setCookie(String cookie) {
        }

        default void titleChanged(String title) {
        }

        /** Un mensaje de console. */
        default void console(String level, String text) {
        }

        /** {ancho, alto, scrollX, scrollY} de la ventana. */
        default int[] viewport() {
            return new int[] {1024, 768, 0, 0};
        }

        default void scrollTo(int x, int y) {
        }

        /** {x, y, ancho, alto} del elemento en coordenadas de la página según la última disposición, o null si no se dibuja. */
        default int[] rect(Node n) {
            return null;
        }

        /** El elemento más profundo en ese punto de la página (coordenadas de la página), o null. */
        default Node nodeAt(int x, int y) {
            return null;
        }

        /** {ancho, alto, 1 si la disposición está al día con el documento} de la página entera, o null si todavía no hay disposición. */
        default int[] pageSize() {
            return null;
        }

        /** Descarga un script de forma asíncrona; {@code done} se llama (en el hilo de los scripts) con el texto o null si falló. */
        default void loadScript(String url, Consumer<String> done) {
            done.accept(null);
        }

        /** Pone una tarea en la cola del hilo de los scripts. */
        default void post(Runnable task) {
            task.run();
        }

        /** Un mensaje corto para el usuario (barra de estado). */
        default void status(String text) {
        }

        /**
         * Pide que la tarea se ejecute en el hilo de los scripts dentro de {@code delayMs} milisegundos (temporizadores).
         * Falso si no se puede (la página ya no existe): entonces la tarea no se ejecutará nunca.
         */
        default boolean schedule(Runnable task, long delayMs) {
            return false;
        }

        /**
         * Hace una petición de red sin bloquear: {@code done} se llama después, en el hilo de los scripts, con la respuesta
         * (o con el error). Devuelve algo que la cancela (la respuesta ya no se entrega).
         */
        default Runnable fetch(NetRequest request, Consumer<NetResult> done) {
            NetResult r = new NetResult();
            r.error = "sin red";
            done.accept(r);
            return () -> { };
        }

        /** Lo mismo pero esperando la respuesta (XMLHttpRequest síncrono). */
        default NetResult fetchSync(NetRequest request) {
            NetResult r = new NetResult();
            r.error = "sin red";
            return r;
        }
    }

    /** Una petición de red que pide un script (fetch o XMLHttpRequest). La dirección ya es absoluta. */
    public static final class NetRequest {
        public String method = "GET";
        public String url = "";
        public final java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        public byte[] body;
    }

    /** La respuesta a una {@link NetRequest}; si {@code error} no es null, no hubo respuesta. */
    public static final class NetResult {
        public int status;
        public String statusText = "";
        public String url = "";
        /** Cabeceras con los nombres en minúsculas, en el orden en que llegaron. */
        public final java.util.LinkedHashMap<String, List<String>> headers = new java.util.LinkedHashMap<>();
        public byte[] body = new byte[0];
        public String error;
    }

    /** Un formulario listo para enviar. */
    public static final class Submission {
        public String method = "GET";
        public String action = "";
        public String enctype = "application/x-www-form-urlencoded";
        public String target = "";
        public final List<String[]> fields = new ArrayList<>();

        /** Los campos como "a=1&b=2" (application/x-www-form-urlencoded). */
        public String encoded() {
            StringBuilder sb = new StringBuilder();
            for (String[] f : this.fields) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(Url.encode(f[0])).append('=').append(Url.encode(f[1]));
            }
            return sb.toString();
        }

        /** Los campos como cuerpo multipart/form-data. */
        public byte[] multipart(String boundary) {
            StringBuilder sb = new StringBuilder();
            for (String[] f : this.fields) {
                sb.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
                        .append(f[0].replace("\"", "%22").replace("\r", "").replace("\n", "%0A")).append("\"\r\n\r\n").append(f[1]).append("\r\n");
            }
            sb.append("--").append(boundary).append("--\r\n");
            return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    interface NFn {
        Object call(Node n, Object[] a);
    }

    @FunctionalInterface
    interface NGet {
        Object get(Node n);
    }

    @FunctionalInterface
    interface NSet {
        void set(Node n, Object v);
    }

    static final Set<String> HEAD_TAGS = new HashSet<>(Arrays.asList("title", "meta", "link", "style", "script", "base", "noscript", "template"));

    final JsEngine engine;
    final Realm r;
    final Node doc;
    final Env env;
    final long startNanos = System.nanoTime();

    JsObject eventTargetProto;
    JsObject nodeProto;
    JsObject elementProto;
    JsObject htmlElementProto;
    JsObject svgElementProto;
    JsObject characterDataProto;
    JsObject textProto;
    JsObject commentProto;
    JsObject documentProto;
    JsObject fragmentProto;
    JsObject domExceptionProto;
    JsObject windowProto;
    final HashMap<String, JsObject> tagProtos = new HashMap<>();
    final HashMap<String, JsObject> protos = new HashMap<>();
    final IdentityHashMap<Object, ArrayList<DomEvents.Listener>> listeners = new IdentityHashMap<>();

    final DomTimers.State timers = new DomTimers.State();
    final DomObservers.State observers = new DomObservers.State();
    /** Cancelaciones de las peticiones de red en curso (al cerrar la página se cancelan todas). */
    final java.util.Set<Runnable> netCancels = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile long limitMs;

    Node currentScript;
    /** El elemento con el foco. */
    Node focused;
    Node writeFor;
    Node writeLast;
    JsObject locationObj;
    final HashMap<String, DomSelector.Sel[]> selCache = new HashMap<>();
    String readyState = "loading";
    /** Cierto si algún script registró un evento de teclado (entonces la pantalla se los manda). */
    public volatile boolean keyListeners;
    public volatile boolean moveListeners;
    final java.util.IdentityHashMap<Node, Boolean> scriptsDone = new java.util.IdentityHashMap<>();
    final java.util.IdentityHashMap<Node, String> focusValues = new java.util.IdentityHashMap<>();
    final java.util.IdentityHashMap<Node, String> customValidity = new java.util.IdentityHashMap<>();

    public DomHost(Node doc, Env env) {
        this.env = env;
        normalize(doc);
        this.doc = doc;
        doc.scripting = true;
        this.engine = new JsEngine();
        this.r = this.engine.realm();
        Realm.CUR.set(this.r);
        this.engine.setConsole(env::console);
        install();
    }

    private void install() {
        DomEvents.install(this);
        DomNodes.install(this);
        DomElements.install(this);
        DomForms.install(this);
        DomDocument.install(this);
        DomGeometry.install(this);
        DomStyle.install(this);
        DomWindow.install(this);
        DomUrl.install(this);
        DomTimers.install(this);
        DomNet.install(this);
        DomObservers.install(this);
        this.r.globalFallback = this::idLookup;
    }

    // ------------------------------------------------------------------ API para quien maneja la página

    public Node document() {
        return this.doc;
    }

    /** El elemento con el foco (o null). */
    public Node focusedNode() {
        return this.focused;
    }

    public JsEngine engine() {
        return this.engine;
    }

    /** Fija el plazo (en milisegundos desde ahora) del código que se ejecute a continuación; 0 lo quita. */
    public void setDeadline(long millis) {
        this.limitMs = millis;
        this.r.setDeadline(millis);
    }

    /** Vuelve a darle al código en marcha su plazo completo (tras esperar a la red de forma síncrona). */
    void rearmDeadline() {
        if (this.limitMs > 0) {
            this.r.setDeadline(this.limitMs);
        }
    }

    /** Las opciones de un select (para que la pantalla muestre la lista). */
    public static List<Node> selectOptions(Node select) {
        return DomForms.optionsOf(select);
    }

    /** Cuáles opciones de un select están elegidas. */
    public static boolean optionSelected(Node option) {
        return DomForms.optionSelected(option);
    }

    /** Ejecuta las promesas pendientes. */
    public void runJobs() {
        this.engine.runJobs();
    }

    /** Los {@code <script>} del documento, en orden, que todavía no se ejecutaron. */
    public List<Node> pendingScripts() {
        List<Node> out = new ArrayList<>();
        DomNodes.collect(this.doc, out, n -> n.is("script") && !this.scriptsDone.containsKey(n));
        return out;
    }

    /** Si el script es de los que este navegador ejecuta (JavaScript clásico, no módulos). */
    public static boolean runnable(Node script) {
        String type = script.attr("type");
        if (type == null || type.isBlank()) {
            String lang = script.attr("language");
            return lang == null || lang.isBlank() || lang.toLowerCase(java.util.Locale.ROOT).contains("javascript");
        }
        String t = type.trim().toLowerCase(java.util.Locale.ROOT);
        return t.equals("text/javascript") || t.equals("application/javascript") || t.equals("text/ecmascript")
                || t.equals("application/ecmascript") || t.equals("text/jscript") || t.equals("application/x-javascript")
                || t.equals("text/x-javascript") || t.equals("text/livescript");
    }

    /** Marca que el script ya se procesó (no se ejecutará otra vez). */
    public void markDone(Node script) {
        this.scriptsDone.put(script, Boolean.TRUE);
    }

    /** Ejecuta código como un script de la página. Falso si lanzó un error (que se avisa en la consola). */
    public boolean runScript(Node script, String code) {
        Node prev = this.currentScript;
        if (script != null) {
            this.currentScript = script;
            this.scriptsDone.put(script, Boolean.TRUE);
        }
        try {
            this.engine.eval(code);
            return true;
        } catch (JsException e) {
            reportError(e);
            return false;
        } catch (JsSyntaxError e) {
            this.env.console("error", "Uncaught SyntaxError: " + e.getMessage());
            return false;
        } finally {
            this.currentScript = prev;
            this.engine.runJobs();
        }
    }

    void reportError(JsException e) {
        this.env.console("error", "Uncaught " + Js.describeError(e.value));
    }

    public void setReadyState(String state) {
        this.readyState = state;
        DomEvents.fireSimple(this, this.doc, "readystatechange", false, false);
    }

    /** Dispara un evento sencillo en el documento ("DOMContentLoaded") o en la ventana ("load"). */
    public void fire(boolean onWindow, String type) {
        if (onWindow) {
            DomEvents.fireSimple(this, this.r.global, type, false, false);
        } else {
            DomEvents.fireSimple(this, this.doc, type, true, false);
        }
        this.engine.runJobs();
    }

    // ------------------------------------------------------------------ entradas del usuario (siempre desde el hilo de los scripts)

    public static final int MOD_CTRL = DomEvents.CTRL;
    public static final int MOD_SHIFT = DomEvents.SHIFT;
    public static final int MOD_ALT = DomEvents.ALT;
    public static final int MOD_META = DomEvents.META;

    private Node hover;

    /** Un clic del usuario sobre un nodo (button: 0 izquierdo, 1 central, 2 derecho). */
    public void click(Node target, int x, int y, int button, int mods) {
        DomForms.userClick(this, target, x, y, button, mods);
    }

    /** El puntero se movió sobre un nodo. Solo hace algo si la página escucha eventos de movimiento. */
    public void mouseMove(Node target, int x, int y, int mods) {
        if (!this.moveListeners) {
            return;
        }
        Node old = this.hover;
        if (old != target) {
            if (old != null) {
                DomEvents.dispatch(this, old, DomEvents.mouse(this, "mouseout", true, true, x, y, 0, 0, mods, target));
                DomEvents.dispatch(this, old, DomEvents.mouse(this, "mouseleave", false, false, x, y, 0, 0, mods, target));
            }
            this.hover = target;
            if (target != null) {
                DomEvents.dispatch(this, target, DomEvents.mouse(this, "mouseover", true, true, x, y, 0, 0, mods, old));
                DomEvents.dispatch(this, target, DomEvents.mouse(this, "mouseenter", false, false, x, y, 0, 0, mods, old));
            }
        }
        if (target != null) {
            DomEvents.dispatch(this, target, DomEvents.mouse(this, "pointermove", true, true, x, y, 0, 0, mods, null));
            DomEvents.dispatch(this, target, DomEvents.mouse(this, "mousemove", true, true, x, y, 0, 0, mods, null));
        }
        this.engine.runJobs();
    }

    private Node keyTarget() {
        if (this.focused != null) {
            return this.focused;
        }
        return DomDocument.bodyOf(this);
    }

    /** Una tecla pulsada. Verdadero si la página o el campo con foco la usaron (la pantalla no debe hacer nada más con ella). */
    public boolean keyDown(String key, String code, int keyCode, int mods, boolean repeat) {
        Node target = keyTarget();
        if (target == null) {
            return false;
        }
        DomEvents.DomEvent e = DomEvents.key(this, "keydown", key, code, keyCode, mods, repeat);
        boolean ok = DomEvents.dispatch(this, target, e);
        boolean used = !ok;
        if (ok) {
            if (key.equals("Tab")) {
                DomForms.focusNext(this, (mods & DomEvents.SHIFT) != 0);
                used = this.focused != null;
            } else if (DomForms.isTextControl(this.focused)) {
                used = DomForms.editKey(this, key, mods);
            } else {
                used = DomForms.keyActivate(this, key);
            }
        }
        this.engine.runJobs();
        return used;
    }

    public void keyUp(String key, String code, int keyCode, int mods) {
        Node target = keyTarget();
        if (target != null) {
            DomEvents.dispatch(this, target, DomEvents.key(this, "keyup", key, code, keyCode, mods, false));
            this.engine.runJobs();
        }
    }

    /** Texto escrito (un carácter normalmente). Verdadero si un campo de texto lo recibió o la página lo cancelaron. */
    public boolean typeText(String text, int mods) {
        Node target = keyTarget();
        if (target == null || text.isEmpty()) {
            return false;
        }
        DomEvents.DomEvent e = DomEvents.key(this, "keypress", text, "", text.codePointAt(0), mods, false);
        boolean ok = DomEvents.dispatch(this, target, e);
        boolean used = !ok;
        if (ok && DomForms.editable(this.focused)) {
            used = DomForms.typeText(this, text);
        }
        this.engine.runJobs();
        return used;
    }

    /** Pegar texto en el campo con foco. */
    public boolean paste(String text) {
        boolean ok = DomForms.paste(this, text);
        this.engine.runJobs();
        return ok;
    }

    /** El texto seleccionado en el campo con foco ("" si no hay). */
    public String selectedText() {
        return this.focused == null ? "" : DomForms.selectedText(this.focused);
    }

    /** Cierto si hay un campo de texto con foco (la pantalla le manda las teclas). */
    public boolean textFocused() {
        return DomForms.isTextControl(this.focused);
    }

    /** El usuario eligió una opción de un select. */
    public void chooseOption(Node select, int index) {
        DomForms.chooseOption(this, select, index);
    }

    /** Quita el foco al elemento actual. */
    public void blurFocus() {
        DomForms.blur(this);
        this.engine.runJobs();
    }

    /** popstate: el usuario volvió a una entrada del historial que creó pushState. */
    public void firePopState(Object state) {
        DomEvents.DomEvent e = DomEvents.create(this, "PopStateEvent", "popstate", false, false);
        DomEvents.put(e, "state", state == null ? Js.NULL : state);
        DomEvents.dispatch(this, this.r.global, e);
        this.engine.runJobs();
    }

    /** La ventana se desplazó o cambió de tamaño: la página recibe scroll / resize y los observadores revisan sus elementos. */
    public void viewportChanged(boolean scrolled, boolean resized) {
        if (resized) {
            DomEvents.fireSimple(this, this.r.global, "resize", false, false);
        }
        if (scrolled) {
            DomEvents.fireSimple(this, this.doc, "scroll", true, false);
        }
        this.engine.runJobs();
        DomObservers.check(this);
    }

    /** hashchange: cambió solo el ancla de la dirección. */
    public void fireHashChange(String oldUrl, String newUrl) {
        DomEvents.DomEvent e = DomEvents.create(this, "HashChangeEvent", "hashchange", false, false);
        DomEvents.put(e, "oldURL", oldUrl);
        DomEvents.put(e, "newURL", newUrl);
        DomEvents.dispatch(this, this.r.global, e);
        this.engine.runJobs();
    }

    public void close() {
        for (Runnable c : new ArrayList<>(this.netCancels)) {
            try {
                c.run();
            } catch (RuntimeException ignored) {
                // ya estaba cancelada
            }
        }
        this.netCancels.clear();
        this.engine.close();
    }

    // ------------------------------------------------------------------ envolturas

    JsObject protoFor(Node n) {
        if (n.isText()) {
            return this.textProto;
        }
        switch (n.tag) {
            case "#document":
                return this.documentProto;
            case "#fragment":
                return this.fragmentProto;
            case "#comment":
                return this.commentProto;
            default:
                JsObject p = this.tagProtos.get(n.tag);
                return p != null ? p : this.htmlElementProto;
        }
    }

    JsObject wrap(Node n) {
        if (n.host instanceof JsObject o) {
            return o;
        }
        DomObj d = n.is("form") ? new DomForms.FormObj(this, protoFor(n), n) : new DomObj(protoFor(n), n);
        n.host = d;
        return d;
    }

    Object w(Node n) {
        return n == null ? Js.NULL : wrap(n);
    }

    static Node nodeOf(Object t) {
        if (t instanceof DomObj d && d.nat instanceof Node n) {
            return n;
        }
        throw Js.typeError("Illegal invocation");
    }

    /** El nodo que envuelve el valor, o null si no es un nodo. */
    static Node asNode(Object v) {
        if (v instanceof DomObj d && d.nat instanceof Node n) {
            return n;
        }
        return null;
    }

    static Node nodeArg(Object[] a, int i, String method) {
        Node n = asNode(Js.arg(a, i));
        if (n == null) {
            throw Js.typeError("Failed to execute '" + method + "' on 'Node': parameter " + (i + 1) + " is not of type 'Node'.");
        }
        return n;
    }

    static String str(Object[] a, int i) {
        return Js.toStr(Js.arg(a, i));
    }

    JsException domError(String name, String msg) {
        JsError e = new JsError(this.domExceptionProto);
        e.setHidden("message", msg);
        e.setHidden("name", name);
        e.setHidden("code", Js.num(domCode(name)));
        e.setHidden("stack", name + ": " + msg + "\n    at <anonymous>");
        return new JsException(e);
    }

    static int domCode(String name) {
        switch (name) {
            case "IndexSizeError":
                return 1;
            case "HierarchyRequestError":
                return 3;
            case "WrongDocumentError":
                return 4;
            case "InvalidCharacterError":
                return 5;
            case "NotFoundError":
                return 8;
            case "NotSupportedError":
                return 9;
            case "InvalidStateError":
                return 11;
            case "SyntaxError":
                return 12;
            case "SecurityError":
                return 18;
            case "NetworkError":
                return 19;
            case "AbortError":
                return 20;
            case "TimeoutError":
                return 23;
            case "DataCloneError":
                return 25;
            default:
                return 0;
        }
    }

    // ------------------------------------------------------------------ definir propiedades

    void method(JsObject proto, String name, int len, NFn f) {
        Lib.method(this.r, proto, name, len, (t, a) -> f.call(nodeOf(t), a));
    }

    void getter(JsObject proto, String name, NGet g) {
        Lib.getter(this.r, proto, name, (t, a) -> g.get(nodeOf(t)));
    }

    void accessor(JsObject proto, String name, NGet g, NSet s) {
        Lib.accessor(this.r, proto, name, (t, a) -> g.get(nodeOf(t)), (t, a) -> {
            s.set(nodeOf(t), Js.arg(a, 0));
            return Js.UNDEFINED;
        });
    }

    /** Propiedad de texto que refleja un atributo. */
    void attrStr(JsObject proto, String prop, String attr) {
        accessor(proto, prop, n -> {
            String v = n.attr(attr);
            return v == null ? "" : v;
        }, (n, v) -> {
            n.setAttr(attr, Js.toStr(v));
            Node.touch();
        });
    }

    /** Propiedad de texto que refleja un atributo y se devuelve en minúsculas con un valor por defecto (type, method...). */
    void attrEnum(JsObject proto, String prop, String attr, String dflt, String... valid) {
        accessor(proto, prop, n -> {
            String v = n.attr(attr);
            if (v == null) {
                return dflt;
            }
            String l = v.trim().toLowerCase(java.util.Locale.ROOT);
            for (String s : valid) {
                if (s.equals(l)) {
                    return l;
                }
            }
            return dflt;
        }, (n, v) -> {
            n.setAttr(attr, Js.toStr(v));
            Node.touch();
        });
    }

    void attrBool(JsObject proto, String prop, String attr) {
        accessor(proto, prop, n -> n.hasAttr(attr), (n, v) -> {
            if (Js.truthy(v)) {
                n.setAttr(attr, "");
            } else {
                n.removeAttr(attr);
            }
            Node.touch();
        });
    }

    void attrInt(JsObject proto, String prop, String attr, int dflt) {
        accessor(proto, prop, n -> {
            String v = n.attr(attr);
            if (v == null) {
                return Js.num(dflt);
            }
            try {
                return Js.num((int) Double.parseDouble(v.trim()));
            } catch (NumberFormatException e) {
                return Js.num(dflt);
            }
        }, (n, v) -> {
            n.setAttr(attr, Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
    }

    /** Propiedad con una dirección: se lee ya resuelta respecto a la base de la página. */
    void attrUrl(JsObject proto, String prop, String attr) {
        accessor(proto, prop, n -> {
            String v = n.attr(attr);
            return v == null ? "" : resolveUrl(v);
        }, (n, v) -> {
            n.setAttr(attr, Js.toStr(v));
            Node.touch();
        });
    }

    /** Dirección absoluta de ref; si no se puede resolver (javascript:, mailto:...) se devuelve tal cual. */
    String resolveUrl(String ref) {
        String t = ref.trim();
        if (t.isEmpty()) {
            return Url.stripFragment(this.env.url());
        }
        if (t.startsWith("#")) {
            return Url.stripFragment(this.env.url()) + t;
        }
        String abs = Url.resolve(this.env.baseUrl(), t);
        return abs == null ? ref : abs;
    }

    /** Crea una clase (constructor + prototipo) que no se puede construir con new. */
    JsObject iface(String name, JsObject parent) {
        JsObject proto = new JsObject(parent == null ? this.r.objectProto : parent);
        NativeFunction c = Lib.ctor(this.r, name, 0, proto, (t, a) -> {
            throw Js.typeError("Illegal constructor");
        }, (a, nt) -> {
            throw Js.typeError("Illegal constructor");
        });
        if (parent != null && parent.get("constructor") instanceof JsObject pc) {
            c.proto = pc;
        }
        Lib.tag(proto, name);
        this.protos.put(name, proto);
        return proto;
    }

    /** Variable global para un elemento con id (como hacen los navegadores). */
    private Object idLookup(String name) {
        Node[] found = new Node[1];
        DomNodes.collect(this.doc, null, n -> {
            if (found[0] == null && n.isElement() && name.equals(n.attr("id"))) {
                found[0] = n;
            }
            return false;
        });
        return found[0] == null ? null : wrap(found[0]);
    }

    // ------------------------------------------------------------------ el documento completo (html > head + body)

    /** Deja el documento como lo ve un script: {@code #document > html > (head, body)}. */
    static void normalize(Node doc) {
        Node html = null;
        for (Node c : doc.children) {
            if (c.is("html")) {
                html = c;
                break;
            }
        }
        if (html == null) {
            html = Node.element("html", null);
            List<Node> kids = new ArrayList<>(doc.children);
            doc.children.clear();
            for (Node k : kids) {
                html.add(k);
            }
            doc.add(html);
        } else {
            final Node keep = html;
            List<Node> others = new ArrayList<>();
            for (Node c : doc.children) {
                if (c != keep) {
                    others.add(c);
                }
            }
            doc.children.removeIf(c -> c != keep);
            for (Node o : others) {
                keep.add(o);
            }
        }
        Node head = null;
        Node body = null;
        for (Node c : html.children) {
            if (c.is("head") && head == null) {
                head = c;
            } else if ((c.is("body") || c.is("frameset")) && body == null) {
                body = c;
            }
        }
        if (head != null && body == null) {
            for (Node c : new ArrayList<>(head.children)) {
                if (c.is("body")) {
                    head.removeChild(c);
                    body = c;
                    break;
                }
            }
        }
        List<Node> loose = new ArrayList<>();
        for (Node c : html.children) {
            if (c != head && c != body) {
                loose.add(c);
            }
        }
        html.children.clear();
        boolean inHead = head == null;
        if (head == null) {
            head = Node.element("head", null);
        }
        if (body == null) {
            body = Node.element("body", null);
        }
        html.add(head);
        html.add(body);
        for (Node c : loose) {
            boolean blank = c.isText() && c.text.isBlank();
            if (blank) {
                continue;
            }
            if (inHead && c.isElement() && HEAD_TAGS.contains(c.tag)) {
                head.add(c);
            } else {
                inHead = false;
                body.add(c);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades de tiempo

    double now() {
        return (System.nanoTime() - this.startNanos) / 1_000_000.0;
    }
}
