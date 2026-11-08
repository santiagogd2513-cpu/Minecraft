package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Css;
import com.example.beautifuldecor.browser.Http;
import com.example.beautifuldecor.browser.InternalPages;
import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** window: location, history, navigator, almacenamiento, ventanas emergentes de aviso y lo demás que cuelga del objeto global. */
final class DomWindow {
    /** localStorage de cada sitio (origen). Solo vive mientras el navegador está abierto. */
    private static final ConcurrentHashMap<String, Map<String, String>> LOCAL = new ConcurrentHashMap<>();
    private static final int QUOTA = 5_000_000;
    private static volatile String clipboard = "";
    private static final Pattern RANGE = Pattern.compile("(width|height)\\s*(>=|<=|>|<|=)\\s*(.+)");

    private DomWindow() {
    }

    static String t(String es, String en) {
        return InternalPages.t(es, en);
    }

    // ------------------------------------------------------------------ Storage

    static final class StorageObj extends JsObject {
        final DomHost h;
        final Map<String, String> map;

        StorageObj(DomHost h, JsObject proto, Map<String, String> map) {
            super(proto);
            this.h = h;
            this.map = map;
        }

        @Override
        public String className() {
            return "Storage";
        }

        @Override
        public Prop getOwn(Object key) {
            Prop p = super.getOwn(key);
            if (p != null || !(key instanceof String s)) {
                return p;
            }
            if (this.proto != null && this.proto.hasProperty(s)) {
                return null;
            }
            String v;
            synchronized (this.map) {
                v = this.map.get(s);
            }
            return v == null ? null : new Prop(v, Prop.ALL);
        }

        @Override
        public boolean defineOwn(Object key, Prop p) {
            if (key instanceof String s && !p.accessor) {
                setItem(s, Js.toStr(p.value));
                return true;
            }
            return super.defineOwn(key, p);
        }

        @Override
        protected boolean writeOwn(Object key, Prop p, Object value) {
            if (key instanceof String s && super.getOwn(key) == null) {
                setItem(s, Js.toStr(value));
                return true;
            }
            return super.writeOwn(key, p, value);
        }

        @Override
        public boolean deleteOwn(Object key) {
            if (key instanceof String s && super.getOwn(key) == null) {
                synchronized (this.map) {
                    this.map.remove(s);
                }
                return true;
            }
            return super.deleteOwn(key);
        }

        @Override
        public List<Object> ownKeys() {
            List<Object> out = new ArrayList<>();
            synchronized (this.map) {
                out.addAll(this.map.keySet());
            }
            out.addAll(super.ownKeys());
            return out;
        }

        void setItem(String k, String v) {
            synchronized (this.map) {
                long size = k.length() + v.length();
                for (Map.Entry<String, String> e : this.map.entrySet()) {
                    if (!e.getKey().equals(k)) {
                        size += e.getKey().length() + e.getValue().length();
                    }
                }
                if (size * 2 > QUOTA) {
                    throw this.h.domError("QuotaExceededError", "Failed to execute 'setItem' on 'Storage': Setting the value of '" + k
                            + "' exceeded the quota.");
                }
                this.map.put(k, v);
            }
        }
    }

    private static StorageObj storage(Object t) {
        if (t instanceof StorageObj s) {
            return s;
        }
        throw Js.typeError("Illegal invocation");
    }

    // ------------------------------------------------------------------ medios (matchMedia)

    private static double len(String v) {
        double d = Css.length(v.trim().toLowerCase(Locale.ROOT), 16, 16);
        return Double.isNaN(d) ? Double.NaN : d;
    }

    private static boolean compare(double a, String op, double b) {
        switch (op) {
            case ">=":
                return a >= b;
            case "<=":
                return a <= b;
            case ">":
                return a > b;
            case "<":
                return a < b;
            default:
                return a == b;
        }
    }

    private static boolean cond(String c, int[] vp) {
        Matcher range = RANGE.matcher(c);
        if (range.matches()) {
            double v = len(range.group(3));
            return !Double.isNaN(v) && compare(range.group(1).equals("width") ? vp[0] : vp[1], range.group(2), v);
        }
        int colon = c.indexOf(':');
        String name = (colon < 0 ? c : c.substring(0, colon)).trim();
        String value = colon < 0 ? "" : c.substring(colon + 1).trim();
        switch (name) {
            case "min-width":
                return vp[0] >= len(value);
            case "max-width":
                return vp[0] <= len(value);
            case "min-height":
                return vp[1] >= len(value);
            case "max-height":
                return vp[1] <= len(value);
            case "width":
                return vp[0] == len(value);
            case "height":
                return vp[1] == len(value);
            case "orientation":
                return value.equals(vp[0] >= vp[1] ? "landscape" : "portrait");
            case "prefers-color-scheme":
                return value.equals("dark");
            case "prefers-reduced-motion":
                return value.equals("no-preference");
            case "prefers-contrast":
                return value.equals("no-preference");
            case "hover", "any-hover":
                return value.isEmpty() || value.equals("hover");
            case "pointer", "any-pointer":
                return value.isEmpty() || value.equals("fine");
            case "display-mode":
                return value.equals("browser");
            case "color":
                return true;
            case "min-resolution", "-webkit-min-device-pixel-ratio": {
                double n = len(value.replaceAll("[a-z]+$", "").trim() + "px");
                return !Double.isNaN(n) && n <= (value.contains("dpi") ? 96 : 1);
            }
            case "max-resolution", "-webkit-max-device-pixel-ratio": {
                double n = len(value.replaceAll("[a-z]+$", "").trim() + "px");
                return !Double.isNaN(n) && n >= (value.contains("dpi") ? 96 : 1);
            }
            default:
                return false;
        }
    }

    private static boolean oneQuery(String q, int[] vp) {
        boolean not = false;
        String s = q.trim();
        if (s.startsWith("not ")) {
            not = true;
            s = s.substring(4).trim();
        }
        if (s.startsWith("only ")) {
            s = s.substring(5).trim();
        }
        boolean ok = true;
        for (String part : s.split("\\s+and\\s+")) {
            String p = part.trim();
            if (p.isEmpty() || p.equals("screen") || p.equals("all")) {
                continue;
            }
            if (p.equals("print") || p.equals("speech")) {
                ok = false;
            } else if (p.startsWith("(") && p.endsWith(")")) {
                ok &= cond(p.substring(1, p.length() - 1).trim(), vp);
            } else {
                ok = false;
            }
        }
        return ok != not;
    }

    static boolean media(DomHost h, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return true;
        }
        int[] vp = h.env.viewport();
        for (String one : q.split(",")) {
            if (oneQuery(one, vp)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ utilidades

    private static JsPromise resolved(DomHost h, Object v) {
        JsPromise p = new JsPromise(h.r);
        p.resolve(v);
        return p;
    }

    private static Object cloneValue(DomHost h, Object v) {
        Object f = h.r.global.get("structuredClone");
        if (f instanceof JsFunction fn) {
            return fn.call(Js.UNDEFINED, new Object[] {v});
        }
        return v;
    }

    private static void stubClass(DomHost h, String name, String... methods) {
        Realm r = h.r;
        JsObject proto = new JsObject(r.objectProto);
        Lib.tag(proto, name);
        Lib.ctor(r, name, 1, proto, (t, a) -> {
            throw Js.typeError("Class constructor " + name + " cannot be invoked without 'new'");
        }, (a, nt) -> new JsObject(Lib.protoFrom(nt, proto)));
        for (String m : methods) {
            Lib.method(r, proto, m, 0, (t, a) -> m.equals("takeRecords") ? Lib.array(r, new ArrayList<Object>()) : Js.UNDEFINED);
        }
    }

    private static String origin(DomHost h) {
        return DomUrl.parse(h.env.url()).origin();
    }

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject g = r.global;
        h.windowProto = h.iface("Window", h.eventTargetProto);
        g.proto = h.windowProto;
        JsObject wp = h.windowProto;
        for (String n : new String[] {"window", "self", "top", "parent", "frames"}) {
            g.setHidden(n, g);
        }
        g.setHidden("name", "");
        g.setHidden("closed", false);
        g.setHidden("opener", Js.NULL);
        g.setHidden("frameElement", Js.NULL);
        g.setHidden("length", Js.num(0));

        installLocation(h);
        installHistory(h);
        installNavigator(h);
        installStorage(h);
        installMisc(h);

        // tamaño y posición
        Lib.getter(r, wp, "innerWidth", (t, a) -> Js.num(h.env.viewport()[0]));
        Lib.getter(r, wp, "innerHeight", (t, a) -> Js.num(h.env.viewport()[1]));
        Lib.getter(r, wp, "outerWidth", (t, a) -> Js.num(h.env.viewport()[0]));
        Lib.getter(r, wp, "outerHeight", (t, a) -> Js.num(h.env.viewport()[1]));
        Lib.getter(r, wp, "scrollX", (t, a) -> Js.num(h.env.viewport()[2]));
        Lib.getter(r, wp, "pageXOffset", (t, a) -> Js.num(h.env.viewport()[2]));
        Lib.getter(r, wp, "scrollY", (t, a) -> Js.num(h.env.viewport()[3]));
        Lib.getter(r, wp, "pageYOffset", (t, a) -> Js.num(h.env.viewport()[3]));
        for (String n : new String[] {"screenX", "screenY", "screenLeft", "screenTop"}) {
            Lib.getter(r, wp, n, (t, a) -> Js.num(0));
        }
        Lib.getter(r, wp, "devicePixelRatio", (t, a) -> Js.num(1));
        Lib.getter(r, wp, "origin", (t, a) -> origin(h));
        Lib.getter(r, wp, "isSecureContext", (t, a) -> h.env.url().startsWith("https:"));
        for (String m : new String[] {"scrollTo", "scroll"}) {
            Lib.method(r, wp, m, 2, (t, a) -> {
                scroll(h, a, false);
                return Js.UNDEFINED;
            });
        }
        Lib.method(r, wp, "scrollBy", 2, (t, a) -> {
            scroll(h, a, true);
            return Js.UNDEFINED;
        });

        // avisos
        Lib.method(r, wp, "alert", 0, (t, a) -> {
            h.env.alert(a.length == 0 ? "" : Js.toStr(a[0]));
            return Js.UNDEFINED;
        });
        Lib.method(r, wp, "confirm", 0, (t, a) -> h.env.confirm(a.length == 0 ? "" : Js.toStr(a[0])));
        Lib.method(r, wp, "prompt", 0, (t, a) -> {
            String v = h.env.prompt(a.length == 0 ? "" : Js.toStr(a[0]), a.length > 1 && a[1] != Js.UNDEFINED ? Js.toStr(a[1]) : "");
            return v == null ? Js.NULL : v;
        });
        Lib.method(r, wp, "open", 0, (t, a) -> {
            if (a.length > 0 && a[0] != Js.UNDEFINED && !Js.toStr(a[0]).isBlank()) {
                String abs = h.resolveUrl(Js.toStr(a[0]));
                if (abs.startsWith("http") || abs.startsWith("hs:")) {
                    h.env.follow(abs, false);
                } else {
                    h.env.status(t("No se puede abrir esa dirección.", "That address cannot be opened."));
                }
            }
            return Js.NULL;
        });
        for (String m : new String[] {"close", "focus", "blur", "print", "stop", "moveTo", "moveBy", "resizeTo", "resizeBy"}) {
            Lib.method(r, wp, m, 0, (t, a) -> Js.UNDEFINED);
        }
        Lib.method(r, wp, "getComputedStyle", 1, (t, a) -> {
            Node n = DomHost.asNode(Js.arg(a, 0));
            if (n == null || !n.isElement()) {
                throw Js.typeError("Failed to execute 'getComputedStyle' on 'Window': parameter 1 is not of type 'Element'.");
            }
            return DomStyle.computed(h, n);
        });
        Lib.method(r, wp, "matchMedia", 1, (t, a) -> {
            String q = Js.toStr(Js.arg(a, 0));
            JsObject mql = new JsObject(h.protos.get("MediaQueryList"));
            Lib.getter(r, mql, "matches", (x, b) -> media(h, q));
            mql.setData("media", q);
            mql.setData("onchange", Js.NULL);
            return mql;
        });
        JsObject mqlProto = new JsObject(h.eventTargetProto);
        Lib.tag(mqlProto, "MediaQueryList");
        h.protos.put("MediaQueryList", mqlProto);
        for (String m : new String[] {"addListener", "removeListener"}) {
            Lib.method(r, mqlProto, m, 1, (t, a) -> Js.UNDEFINED);
        }
        Lib.method(r, wp, "getSelection", 0, (t, a) -> {
            JsObject sel = Js.newObject();
            Lib.method(r, sel, "toString", 0, (x, b) -> h.focused == null ? "" : DomForms.selectedText(h.focused));
            sel.setHidden("rangeCount", Js.num(0));
            sel.setHidden("isCollapsed", true);
            sel.setHidden("anchorNode", Js.NULL);
            sel.setHidden("focusNode", Js.NULL);
            sel.setHidden("type", "None");
            for (String m : new String[] {"removeAllRanges", "addRange", "collapse", "empty", "selectAllChildren", "removeRange", "collapseToStart",
                "collapseToEnd", "extend", "deleteFromDocument", "setBaseAndExtent"}) {
                Lib.method(r, sel, m, 0, (x, b) -> Js.UNDEFINED);
            }
            Lib.method(r, sel, "getRangeAt", 1, (x, b) -> {
                throw h.domError("IndexSizeError", "Failed to execute 'getRangeAt' on 'Selection': 0 is not a valid index.");
            });
            return sel;
        });
        Lib.method(r, wp, "postMessage", 1, (t, a) -> {
            Object data = cloneValue(h, Js.arg(a, 0));
            String org = origin(h);
            h.env.post(() -> {
                DomEvents.DomEvent e = DomEvents.create(h, "MessageEvent", "message", false, false);
                DomEvents.put(e, "data", data);
                DomEvents.put(e, "origin", org);
                DomEvents.put(e, "source", g);
                DomEvents.put(e, "lastEventId", "");
                DomEvents.put(e, "ports", Lib.array(r, new ArrayList<Object>()));
                DomEvents.dispatch(h, g, e);
                h.engine.runJobs();
            });
            return Js.UNDEFINED;
        });
    }

    private static void scroll(DomHost h, Object[] a, boolean by) {
        int[] vp = h.env.viewport();
        double x = vp[2];
        double y = vp[3];
        if (a.length > 0 && a[0] instanceof JsObject o) {
            Object left = o.get("left");
            Object top = o.get("top");
            if (left != Js.UNDEFINED) {
                x = (by ? x : 0) + Js.toNumber(left);
            }
            if (top != Js.UNDEFINED) {
                y = (by ? y : 0) + Js.toNumber(top);
            }
        } else if (a.length >= 2) {
            x = (by ? x : 0) + Js.toNumber(a[0]);
            y = (by ? y : 0) + Js.toNumber(a[1]);
        } else {
            return;
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return;
        }
        h.env.scrollTo((int) Math.max(0, x), (int) Math.max(0, y));
    }

    // ------------------------------------------------------------------ location

    private static void go(DomHost h, String ref, boolean replace) {
        String t = ref.trim();
        if (t.regionMatches(true, 0, "javascript:", 0, 11)) {
            h.runScript(null, t.substring(11));
            return;
        }
        String abs = h.resolveUrl(t);
        if (!abs.startsWith("http") && !abs.startsWith("hs:")) {
            h.env.status(t("No se puede abrir esa dirección.", "That address cannot be opened."));
            return;
        }
        h.env.navigate(abs, replace);
    }

    private static void installLocation(DomHost h) {
        Realm r = h.r;
        JsObject loc = new JsObject(r.objectProto);
        Lib.tag(loc, "Location");
        h.locationObj = loc;
        for (String part : new String[] {"protocol", "host", "hostname", "port", "pathname", "search", "hash", "username", "password"}) {
            loc.defineOwn(part, new Prop(new NativeFunction(r, "get " + part, 0, (t, a) -> DomUrl.parse(h.env.url()).get(part)),
                    new NativeFunction(r, "set " + part, 1, (t, a) -> {
                        DomUrl.Parts p = DomUrl.parse(h.env.url());
                        p.set(part, Js.toStr(Js.arg(a, 0)));
                        go(h, p.href(), false);
                        return Js.UNDEFINED;
                    }), Prop.E));
        }
        loc.defineOwn("origin", new Prop(new NativeFunction(r, "get origin", 0, (t, a) -> origin(h)), null, Prop.E));
        loc.defineOwn("href", new Prop(new NativeFunction(r, "get href", 0, (t, a) -> h.env.url()),
                new NativeFunction(r, "set href", 1, (t, a) -> {
                    go(h, Js.toStr(Js.arg(a, 0)), false);
                    return Js.UNDEFINED;
                }), Prop.E));
        loc.setHidden("ancestorOrigins", Lib.array(r, new ArrayList<Object>()));
        Lib.method(r, loc, "assign", 1, (t, a) -> {
            go(h, DomHost.str(a, 0), false);
            return Js.UNDEFINED;
        });
        Lib.method(r, loc, "replace", 1, (t, a) -> {
            go(h, DomHost.str(a, 0), true);
            return Js.UNDEFINED;
        });
        Lib.method(r, loc, "reload", 0, (t, a) -> {
            h.env.reload();
            return Js.UNDEFINED;
        });
        Lib.method(r, loc, "toString", 0, (t, a) -> h.env.url());
        Lib.method(r, loc, "valueOf", 0, (t, a) -> loc);
        Lib.accessor(r, h.windowProto, "location", (t, a) -> loc, (t, a) -> {
            go(h, Js.toStr(Js.arg(a, 0)), false);
            return Js.UNDEFINED;
        });
    }

    // ------------------------------------------------------------------ history

    private static void installHistory(DomHost h) {
        Realm r = h.r;
        JsObject hist = new JsObject(r.objectProto);
        Lib.tag(hist, "History");
        Lib.getter(r, hist, "length", (t, a) -> Js.num(h.env.historyLength()));
        Lib.getter(r, hist, "state", (t, a) -> {
            Object s = h.env.historyState();
            return s == null ? Js.NULL : s;
        });
        Lib.accessor(r, hist, "scrollRestoration", (t, a) -> "auto", (t, a) -> Js.UNDEFINED);
        Lib.method(r, hist, "back", 0, (t, a) -> {
            h.env.historyGo(-1);
            return Js.UNDEFINED;
        });
        Lib.method(r, hist, "forward", 0, (t, a) -> {
            h.env.historyGo(1);
            return Js.UNDEFINED;
        });
        Lib.method(r, hist, "go", 0, (t, a) -> {
            double d = Js.toInteger(Js.arg(a, 0));
            if (d == 0) {
                h.env.reload();
            } else {
                h.env.historyGo((int) Math.max(-1000, Math.min(1000, d)));
            }
            return Js.UNDEFINED;
        });
        for (String m : new String[] {"pushState", "replaceState"}) {
            boolean replace = m.equals("replaceState");
            Lib.method(r, hist, m, 2, (t, a) -> {
                Object st = cloneValue(h, Js.arg(a, 0));
                String target = null;
                Object u = Js.arg(a, 2);
                if (u != Js.UNDEFINED && u != Js.NULL) {
                    String abs = h.resolveUrl(Js.toStr(u));
                    DomUrl.Parts np = DomUrl.parseOrNull(abs);
                    DomUrl.Parts cur = DomUrl.parse(h.env.url());
                    if (np == null || !np.origin().equals(cur.origin())) {
                        throw h.domError("SecurityError", "Failed to execute '" + m + "' on 'History': A history state object with URL '"
                                + Js.toStr(u) + "' cannot be created in a document with origin '" + cur.origin() + "' and URL '"
                                + h.env.url() + "'.");
                    }
                    target = np.href();
                }
                h.env.historyPush(target, st, replace);
                return Js.UNDEFINED;
            });
        }
        Lib.accessor(r, h.windowProto, "history", (t, a) -> hist, (t, a) -> Js.UNDEFINED);
    }

    // ------------------------------------------------------------------ navigator y screen

    private static void installNavigator(DomHost h) {
        Realm r = h.r;
        JsObject nav = new JsObject(r.objectProto);
        Lib.tag(nav, "Navigator");
        String lang = t("es-ES", "en-US");
        nav.setHidden("userAgent", Http.USER_AGENT);
        nav.setHidden("appName", "Netscape");
        nav.setHidden("appCodeName", "Mozilla");
        nav.setHidden("appVersion", Http.USER_AGENT.substring(Http.USER_AGENT.indexOf('/') + 1));
        nav.setHidden("platform", "Linux x86_64");
        nav.setHidden("product", "Gecko");
        nav.setHidden("productSub", "20100101");
        nav.setHidden("vendor", "");
        nav.setHidden("vendorSub", "");
        nav.setHidden("language", lang);
        ArrayList<Object> langs = new ArrayList<>();
        langs.add(lang);
        nav.setHidden("languages", Lib.array(r, langs));
        nav.setHidden("cookieEnabled", true);
        nav.setHidden("onLine", true);
        nav.setHidden("hardwareConcurrency", Js.num(4));
        nav.setHidden("maxTouchPoints", Js.num(0));
        nav.setHidden("webdriver", false);
        nav.setHidden("doNotTrack", Js.NULL);
        nav.setHidden("plugins", Lib.array(r, new ArrayList<Object>()));
        nav.setHidden("mimeTypes", Lib.array(r, new ArrayList<Object>()));
        nav.setHidden("pdfViewerEnabled", false);
        Lib.method(r, nav, "javaEnabled", 0, (t, a) -> false);
        Lib.method(r, nav, "sendBeacon", 1, (t, a) -> false);
        JsObject clip = new JsObject(r.objectProto);
        Lib.tag(clip, "Clipboard");
        Lib.method(r, clip, "writeText", 1, (t, a) -> {
            clipboard = Js.toStr(Js.arg(a, 0));
            h.env.status(t("Texto copiado.", "Text copied."));
            return resolved(h, Js.UNDEFINED);
        });
        Lib.method(r, clip, "readText", 0, (t, a) -> resolved(h, clipboard));
        nav.setHidden("clipboard", clip);
        Lib.accessor(r, h.windowProto, "navigator", (t, a) -> nav, (t, a) -> Js.UNDEFINED);

        JsObject screen = new JsObject(r.objectProto);
        Lib.tag(screen, "Screen");
        Lib.getter(r, screen, "width", (t, a) -> Js.num(h.env.viewport()[0]));
        Lib.getter(r, screen, "height", (t, a) -> Js.num(h.env.viewport()[1]));
        Lib.getter(r, screen, "availWidth", (t, a) -> Js.num(h.env.viewport()[0]));
        Lib.getter(r, screen, "availHeight", (t, a) -> Js.num(h.env.viewport()[1]));
        screen.setHidden("colorDepth", Js.num(24));
        screen.setHidden("pixelDepth", Js.num(24));
        JsObject orient = Js.newObject();
        orient.setData("type", "landscape-primary");
        orient.setData("angle", Js.num(0));
        screen.setHidden("orientation", orient);
        Lib.accessor(r, h.windowProto, "screen", (t, a) -> screen, (t, a) -> Js.UNDEFINED);
    }

    // ------------------------------------------------------------------ localStorage y sessionStorage

    private static void installStorage(DomHost h) {
        Realm r = h.r;
        JsObject sp = new JsObject(r.objectProto);
        Lib.tag(sp, "Storage");
        Lib.ctor(r, "Storage", 0, sp, (t, a) -> {
            throw Js.typeError("Illegal constructor");
        }, (a, nt) -> {
            throw Js.typeError("Illegal constructor");
        });
        Lib.getter(r, sp, "length", (t, a) -> {
            StorageObj s = storage(t);
            synchronized (s.map) {
                return Js.num(s.map.size());
            }
        });
        Lib.method(r, sp, "getItem", 1, (t, a) -> {
            StorageObj s = storage(t);
            synchronized (s.map) {
                String v = s.map.get(DomHost.str(a, 0));
                return v == null ? Js.NULL : v;
            }
        });
        Lib.method(r, sp, "setItem", 2, (t, a) -> {
            storage(t).setItem(DomHost.str(a, 0), DomHost.str(a, 1));
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "removeItem", 1, (t, a) -> {
            StorageObj s = storage(t);
            synchronized (s.map) {
                s.map.remove(DomHost.str(a, 0));
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "clear", 0, (t, a) -> {
            StorageObj s = storage(t);
            synchronized (s.map) {
                s.map.clear();
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "key", 1, (t, a) -> {
            StorageObj s = storage(t);
            double i = Js.toInteger(Js.arg(a, 0));
            synchronized (s.map) {
                if (i < 0 || i >= s.map.size()) {
                    return Js.NULL;
                }
                return new ArrayList<>(s.map.keySet()).get((int) i);
            }
        });
        Map<String, String> local = LOCAL.computeIfAbsent(origin(h), k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        StorageObj ls = new StorageObj(h, sp, local);
        StorageObj ss = new StorageObj(h, sp, Collections.synchronizedMap(new LinkedHashMap<>()));
        Lib.accessor(r, h.windowProto, "localStorage", (t, a) -> ls, (t, a) -> Js.UNDEFINED);
        Lib.accessor(r, h.windowProto, "sessionStorage", (t, a) -> ss, (t, a) -> Js.UNDEFINED);
    }

    // ------------------------------------------------------------------ lo demás

    private static void installMisc(DomHost h) {
        Realm r = h.r;
        JsObject wp = h.windowProto;
        JsObject perf = new JsObject(r.objectProto);
        Lib.tag(perf, "Performance");
        double origin = System.currentTimeMillis();
        perf.setHidden("timeOrigin", Js.num(origin));
        Lib.method(r, perf, "now", 0, (t, a) -> Js.num(h.now()));
        for (String m : new String[] {"mark", "measure", "clearMarks", "clearMeasures", "clearResourceTimings"}) {
            Lib.method(r, perf, m, 0, (t, a) -> Js.UNDEFINED);
        }
        for (String m : new String[] {"getEntries", "getEntriesByType", "getEntriesByName"}) {
            Lib.method(r, perf, m, 0, (t, a) -> Lib.array(r, new ArrayList<Object>()));
        }
        r.global.setHidden("performance", perf);

        JsObject crypto = new JsObject(r.objectProto);
        Lib.tag(crypto, "Crypto");
        Lib.method(r, crypto, "randomUUID", 0, (t, a) -> UUID.randomUUID().toString());
        Lib.method(r, crypto, "getRandomValues", 1, (t, a) -> {
            Object arr = Js.arg(a, 0);
            if (arr instanceof JsArray ja) {
                java.util.Random rnd = new java.security.SecureRandom();
                for (int i = 0; i < ja.length(); i++) {
                    ja.setIndex(i, Js.num((double) (rnd.nextInt() & 0xffffffffL)));
                }
            }
            return arr;
        });
        r.global.setHidden("crypto", crypto);

        JsObject ce = new JsObject(r.objectProto);
        Lib.tag(ce, "CustomElementRegistry");
        Lib.method(r, ce, "define", 2, (t, a) -> Js.UNDEFINED);
        Lib.method(r, ce, "get", 1, (t, a) -> Js.UNDEFINED);
        Lib.method(r, ce, "whenDefined", 1, (t, a) -> resolved(h, Js.UNDEFINED));
        Lib.method(r, ce, "upgrade", 1, (t, a) -> Js.UNDEFINED);
        r.global.setHidden("customElements", ce);

        stubClass(h, "MutationObserver", "observe", "disconnect", "takeRecords");
        stubClass(h, "PerformanceObserver", "observe", "disconnect", "takeRecords");
    }
}
