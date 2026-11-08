package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Eventos: objetos Event, addEventListener y el recorrido captura / objetivo / burbuja. */
final class DomEvents {
    static final int NONE = 0;
    static final int CAPTURING = 1;
    static final int AT_TARGET = 2;
    static final int BUBBLING = 3;

    static final int CTRL = 1;
    static final int SHIFT = 2;
    static final int ALT = 4;
    static final int META = 8;

    private DomEvents() {
    }

    /** Un oyente registrado. */
    static final class Listener {
        String type;
        JsObject fn;
        boolean capture;
        boolean once;
        boolean passive;
        boolean removed;
        /** Es el "onclick" (propiedad o atributo): hay uno solo por tipo. */
        boolean slot;
        /** Texto del atributo del que salió (null si lo puso un script con la propiedad). */
        String source;
    }

    /** Un evento. */
    static final class DomEvent extends JsObject {
        String type = "";
        boolean bubbles;
        boolean cancelable;
        boolean composed;
        boolean trusted;
        boolean defaultPrevented;
        boolean stopProp;
        boolean stopImmediate;
        boolean passiveNow;
        boolean dispatching;
        int phase;
        double timeStamp;
        JsObject target;
        JsObject currentTarget;
        ArrayList<Object> path = new ArrayList<>();

        DomEvent(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "Event";
        }

        void preventDefault() {
            if (this.cancelable && !this.passiveNow) {
                this.defaultPrevented = true;
            }
        }
    }

    /** nombre -> {padre, campos}. Campos: nombre=tipo (b booleano, n número, s texto, o objeto). */
    private static final String[][] CLASSES = {
        {"Event", "", ""},
        {"UIEvent", "Event", "detail=n,view=o"},
        {"MouseEvent", "UIEvent", "screenX=n,screenY=n,clientX=n,clientY=n,pageX=n,pageY=n,offsetX=n,offsetY=n,x=n,y=n,movementX=n,"
            + "movementY=n,ctrlKey=b,shiftKey=b,altKey=b,metaKey=b,button=n,buttons=n,relatedTarget=o"},
        {"PointerEvent", "MouseEvent", "pointerId=n,width=n,height=n,pressure=n,pointerType=s,isPrimary=b"},
        {"WheelEvent", "MouseEvent", "deltaX=n,deltaY=n,deltaZ=n,deltaMode=n"},
        {"KeyboardEvent", "UIEvent", "key=s,code=s,location=n,ctrlKey=b,shiftKey=b,altKey=b,metaKey=b,repeat=b,isComposing=b,keyCode=n,"
            + "charCode=n,which=n"},
        {"FocusEvent", "UIEvent", "relatedTarget=o"},
        {"InputEvent", "UIEvent", "data=o,inputType=s,isComposing=b"},
        {"TouchEvent", "UIEvent", "ctrlKey=b,shiftKey=b,altKey=b,metaKey=b"},
        {"CustomEvent", "Event", "detail=o"},
        {"SubmitEvent", "Event", "submitter=o"},
        {"HashChangeEvent", "Event", "oldURL=s,newURL=s"},
        {"PopStateEvent", "Event", "state=o"},
        {"StorageEvent", "Event", "key=o,oldValue=o,newValue=o,url=s,storageArea=o"},
        {"MessageEvent", "Event", "data=o,origin=s,lastEventId=s,source=o,ports=o"},
        {"ErrorEvent", "Event", "message=s,filename=s,lineno=n,colno=n,error=o"},
        {"ProgressEvent", "Event", "lengthComputable=b,loaded=n,total=n"},
        {"ClipboardEvent", "Event", "clipboardData=o"},
        {"PageTransitionEvent", "Event", "persisted=b"},
    };

    private static final Map<String, String[]> TABLE = new HashMap<>();

    static {
        for (String[] c : CLASSES) {
            TABLE.put(c[0], c);
        }
    }

    private static final String[] WINDOW_ATTRS = {"load", "resize", "scroll", "hashchange", "popstate", "beforeunload", "unload",
        "pageshow", "pagehide", "message", "storage", "online", "offline", "error", "focus", "blur"};

    static final String[] ON_NAMES = {"click", "dblclick", "auxclick", "mousedown", "mouseup", "mousemove", "mouseover", "mouseout",
        "mouseenter", "mouseleave", "contextmenu", "wheel", "keydown", "keyup", "keypress", "focus", "blur", "focusin", "focusout",
        "input", "change", "submit", "reset", "invalid", "select", "load", "error", "abort", "scroll", "resize", "hashchange",
        "popstate", "beforeunload", "unload", "pagehide", "pageshow", "message", "storage", "online", "offline", "touchstart",
        "touchend", "touchmove", "touchcancel", "pointerdown", "pointerup", "pointermove", "pointerover", "pointerout",
        "pointerenter", "pointerleave", "pointercancel", "dragstart", "drag", "dragend", "dragenter", "dragover", "dragleave",
        "drop", "copy", "cut", "paste", "readystatechange", "toggle", "animationend", "animationstart", "transitionend",
        "play", "pause", "ended", "timeupdate", "volumechange", "canplay", "loadeddata", "loadedmetadata", "progress", "seeked",
        "seeking", "beforeinput", "selectionchange", "visibilitychange", "fullscreenchange", "languagechange", "loadstart", "loadend",
        "timeout"};

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        Realm r = h.r;
        // EventTarget
        JsObject et = new JsObject(r.objectProto);
        h.eventTargetProto = et;
        NativeFunction etc = Lib.ctor(r, "EventTarget", 0, et, (t, a) -> {
            throw Js.typeError("Class constructor EventTarget cannot be invoked without 'new'");
        }, (a, nt) -> new DomObj(Lib.protoFrom(nt, et), new Object()));
        Lib.tag(et, "EventTarget");
        h.protos.put("EventTarget", et);
        Lib.method(r, et, "addEventListener", 2, (t, a) -> {
            add(h, keyOf(h, t), Js.toStr(Js.arg(a, 0)), Js.arg(a, 1), Js.arg(a, 2));
            return Js.UNDEFINED;
        });
        Lib.method(r, et, "removeEventListener", 2, (t, a) -> {
            remove(h, keyOf(h, t), Js.toStr(Js.arg(a, 0)), Js.arg(a, 1), Js.arg(a, 2));
            return Js.UNDEFINED;
        });
        Lib.method(r, et, "dispatchEvent", 1, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof DomEvent ev)) {
                throw Js.typeError("Failed to execute 'dispatchEvent' on 'EventTarget': parameter 1 is not of type 'Event'.");
            }
            if (ev.dispatching) {
                throw h.domError("InvalidStateError", "The event is already being dispatched.");
            }
            return dispatch(h, keyOf(h, t), ev);
        });
        for (String name : ON_NAMES) {
            String prop = "on" + name;
            Lib.accessor(r, et, prop, (t, a) -> {
                Listener l = slot(h, keyOf(h, t), name);
                return l == null || l.fn == null ? Js.NULL : l.fn;
            }, (t, a) -> {
                Object v = Js.arg(a, 0);
                Object key = keyOf(h, t);
                if (v instanceof JsFunction f) {
                    setSlot(h, key, name, f, null);
                    noteType(h, name);
                } else {
                    Listener l = slot(h, key, name);
                    if (l != null) {
                        remove(h, key, l);
                    }
                }
                return Js.UNDEFINED;
            });
        }
        et.setHidden("constructor", etc);
        // Event y compañía
        JsObject base = null;
        for (String[] c : CLASSES) {
            JsObject parent = c[1].isEmpty() ? null : h.protos.get(c[1]);
            JsObject proto = new JsObject(parent == null ? r.objectProto : parent);
            String name = c[0];
            NativeFunction ctor = Lib.ctor(r, name, 1, proto, (t, a) -> {
                throw Js.typeError("Failed to construct '" + name + "': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
            }, (a, nt) -> {
                if (a.length == 0) {
                    throw Js.typeError("Failed to construct '" + name + "': 1 argument required, but only 0 present.");
                }
                return make(h, name, Lib.protoFrom(nt, proto), Js.toStr(a[0]), Js.arg(a, 1), false);
            });
            if (parent != null && parent.get("constructor") instanceof JsObject pc) {
                ctor.proto = pc;
            }
            Lib.tag(proto, name);
            h.protos.put(name, proto);
            if (base == null) {
                base = proto;
            }
        }
        JsObject ep = base;
        NativeFunction evCtor = (NativeFunction) ep.get("constructor");
        for (String[] k : new String[][] {{"NONE", "0"}, {"CAPTURING_PHASE", "1"}, {"AT_TARGET", "2"}, {"BUBBLING_PHASE", "3"}}) {
            evCtor.setConst(k[0], Js.num(Integer.parseInt(k[1])));
            ep.setConst(k[0], Js.num(Integer.parseInt(k[1])));
        }
        Lib.getter(r, ep, "type", (t, a) -> ev(t).type);
        Lib.getter(r, ep, "target", (t, a) -> ev(t).target == null ? Js.NULL : ev(t).target);
        Lib.getter(r, ep, "srcElement", (t, a) -> ev(t).target == null ? Js.NULL : ev(t).target);
        Lib.getter(r, ep, "currentTarget", (t, a) -> ev(t).currentTarget == null ? Js.NULL : ev(t).currentTarget);
        Lib.getter(r, ep, "eventPhase", (t, a) -> Js.num(ev(t).phase));
        Lib.getter(r, ep, "bubbles", (t, a) -> ev(t).bubbles);
        Lib.getter(r, ep, "cancelable", (t, a) -> ev(t).cancelable);
        Lib.getter(r, ep, "composed", (t, a) -> ev(t).composed);
        Lib.getter(r, ep, "defaultPrevented", (t, a) -> ev(t).defaultPrevented);
        Lib.getter(r, ep, "isTrusted", (t, a) -> ev(t).trusted);
        Lib.getter(r, ep, "timeStamp", (t, a) -> Js.num(ev(t).timeStamp));
        Lib.accessor(r, ep, "returnValue", (t, a) -> !ev(t).defaultPrevented, (t, a) -> {
            if (!Js.truthy(Js.arg(a, 0))) {
                ev(t).preventDefault();
            }
            return Js.UNDEFINED;
        });
        Lib.accessor(r, ep, "cancelBubble", (t, a) -> ev(t).stopProp, (t, a) -> {
            if (Js.truthy(Js.arg(a, 0))) {
                ev(t).stopProp = true;
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, ep, "preventDefault", 0, (t, a) -> {
            ev(t).preventDefault();
            return Js.UNDEFINED;
        });
        Lib.method(r, ep, "stopPropagation", 0, (t, a) -> {
            ev(t).stopProp = true;
            return Js.UNDEFINED;
        });
        Lib.method(r, ep, "stopImmediatePropagation", 0, (t, a) -> {
            ev(t).stopProp = true;
            ev(t).stopImmediate = true;
            return Js.UNDEFINED;
        });
        Lib.method(r, ep, "composedPath", 0, (t, a) -> {
            DomEvent e = ev(t);
            ArrayList<Object> out = new ArrayList<>();
            if (e.dispatching) {
                for (Object k : e.path) {
                    out.add(jsOf(h, k));
                }
            }
            return Lib.array(r, out);
        });
        Lib.method(r, ep, "initEvent", 1, (t, a) -> {
            DomEvent e = ev(t);
            if (!e.dispatching) {
                e.type = Js.toStr(Js.arg(a, 0));
                e.bubbles = Js.truthy(Js.arg(a, 1));
                e.cancelable = Js.truthy(Js.arg(a, 2));
            }
            return Js.UNDEFINED;
        });
        JsObject cp = h.protos.get("CustomEvent");
        Lib.method(r, cp, "initCustomEvent", 4, (t, a) -> {
            DomEvent e = ev(t);
            if (!e.dispatching) {
                e.type = Js.toStr(Js.arg(a, 0));
                e.bubbles = Js.truthy(Js.arg(a, 1));
                e.cancelable = Js.truthy(Js.arg(a, 2));
                e.setHidden("detail", Js.arg(a, 3) == Js.UNDEFINED ? Js.NULL : Js.arg(a, 3));
            }
            return Js.UNDEFINED;
        });
        JsObject mp = h.protos.get("MouseEvent");
        Lib.method(r, mp, "initMouseEvent", 15, (t, a) -> {
            DomEvent e = ev(t);
            if (!e.dispatching) {
                e.type = Js.toStr(Js.arg(a, 0));
                e.bubbles = Js.truthy(Js.arg(a, 1));
                e.cancelable = Js.truthy(Js.arg(a, 2));
                String[] names = {"detail", "screenX", "screenY", "clientX", "clientY"};
                for (int i = 0; i < names.length; i++) {
                    e.setHidden(names[i], Js.num(Js.toNumber(Js.arg(a, 4 + i))));
                }
                e.setHidden("ctrlKey", Js.truthy(Js.arg(a, 9)));
                e.setHidden("altKey", Js.truthy(Js.arg(a, 10)));
                e.setHidden("shiftKey", Js.truthy(Js.arg(a, 11)));
                e.setHidden("metaKey", Js.truthy(Js.arg(a, 12)));
                e.setHidden("button", Js.num(Js.toNumber(Js.arg(a, 13))));
            }
            return Js.UNDEFINED;
        });
    }

    private static DomEvent ev(Object t) {
        if (t instanceof DomEvent e) {
            return e;
        }
        throw Js.typeError("Illegal invocation");
    }

    // ------------------------------------------------------------------ crear eventos

    private static void fieldsOf(String cls, ArrayList<String> out) {
        String[] c = TABLE.get(cls);
        if (c == null) {
            return;
        }
        if (!c[1].isEmpty()) {
            fieldsOf(c[1], out);
        }
        if (!c[2].isEmpty()) {
            for (String f : c[2].split(",")) {
                out.add(f);
            }
        }
    }

    /** Crea un evento de esa clase con los valores por defecto y los que traiga {@code init}. */
    static DomEvent make(DomHost h, String cls, JsObject proto, String type, Object init, boolean trusted) {
        DomEvent e = new DomEvent(proto);
        e.type = type;
        e.trusted = trusted;
        e.timeStamp = h.now();
        ArrayList<String> fields = new ArrayList<>();
        fieldsOf(cls, fields);
        JsObject io = init instanceof JsObject o ? o : null;
        if (io != null) {
            e.bubbles = Js.truthy(io.get("bubbles"));
            e.cancelable = Js.truthy(io.get("cancelable"));
            e.composed = Js.truthy(io.get("composed"));
        }
        for (String f : fields) {
            int eq = f.indexOf('=');
            String name = f.substring(0, eq);
            char kind = f.charAt(eq + 1);
            Object dflt = kind == 'b' ? (Object) Boolean.FALSE : kind == 'n' ? (Object) Js.num(0) : kind == 's' ? (Object) "" : Js.NULL;
            Object v = dflt;
            if (io != null && io.hasProperty(name)) {
                Object given = io.get(name);
                if (kind == 'b') {
                    v = Js.truthy(given);
                } else if (kind == 'n') {
                    v = Js.num(Js.toNumber(given));
                } else if (kind == 's') {
                    v = given == Js.UNDEFINED ? "" : Js.toStr(given);
                } else {
                    v = given == Js.UNDEFINED ? Js.NULL : given;
                }
            }
            e.setHidden(name, v);
        }
        if (e.getOwn("pageX") != null && (io == null || !io.hasProperty("pageX"))) {
            e.setHidden("pageX", e.get("clientX"));
            e.setHidden("pageY", e.get("clientY"));
            e.setHidden("x", e.get("clientX"));
            e.setHidden("y", e.get("clientY"));
        }
        if (e.getOwn("keyCode") != null && (io == null || !io.hasProperty("which"))) {
            e.setHidden("which", e.get("keyCode"));
        }
        return e;
    }

    /** Un evento creado por el navegador (de confianza). */
    static DomEvent create(DomHost h, String cls, String type, boolean bubbles, boolean cancelable) {
        DomEvent e = make(h, cls, h.protos.get(cls), type, null, true);
        e.bubbles = bubbles;
        e.cancelable = cancelable;
        return e;
    }

    static void put(DomEvent e, String name, Object value) {
        if (value instanceof Integer i) {
            value = Js.num(i);
        }
        e.setHidden(name, value);
    }

    static DomEvent mouse(DomHost h, String type, boolean bubbles, boolean cancelable, int x, int y, int button, int buttons, int mods,
                          Node related) {
        String cls = type.startsWith("pointer") ? "PointerEvent" : "MouseEvent";
        DomEvent e = create(h, cls, type, bubbles, cancelable);
        put(e, "clientX", x);
        put(e, "clientY", y);
        put(e, "screenX", x);
        put(e, "screenY", y);
        put(e, "offsetX", x);
        put(e, "offsetY", y);
        put(e, "pageX", x + h.env.viewport()[2]);
        put(e, "pageY", y + h.env.viewport()[3]);
        put(e, "x", x);
        put(e, "y", y);
        put(e, "button", button);
        put(e, "buttons", buttons);
        put(e, "ctrlKey", (mods & CTRL) != 0);
        put(e, "shiftKey", (mods & SHIFT) != 0);
        put(e, "altKey", (mods & ALT) != 0);
        put(e, "metaKey", (mods & META) != 0);
        put(e, "detail", type.equals("click") || type.equals("mousedown") || type.equals("mouseup") ? 1 : 0);
        put(e, "relatedTarget", related == null ? Js.NULL : h.wrap(related));
        if (cls.equals("PointerEvent")) {
            put(e, "pointerType", "mouse");
            put(e, "isPrimary", true);
            put(e, "pointerId", 1);
        }
        return e;
    }

    static DomEvent key(DomHost h, String type, String key, String code, int keyCode, int mods, boolean repeat) {
        DomEvent e = create(h, "KeyboardEvent", type, true, true);
        put(e, "key", key);
        put(e, "code", code);
        put(e, "keyCode", keyCode);
        put(e, "which", keyCode);
        put(e, "charCode", type.equals("keypress") ? keyCode : 0);
        put(e, "ctrlKey", (mods & CTRL) != 0);
        put(e, "shiftKey", (mods & SHIFT) != 0);
        put(e, "altKey", (mods & ALT) != 0);
        put(e, "metaKey", (mods & META) != 0);
        put(e, "repeat", repeat);
        return e;
    }

    /** Dispara un evento sin datos especiales. Devuelve falso si alguien lo canceló. */
    static boolean fireSimple(DomHost h, Object target, String type, boolean bubbles, boolean cancelable) {
        String cls = type.equals("focus") || type.equals("blur") || type.equals("focusin") || type.equals("focusout") ? "FocusEvent" : "Event";
        DomEvent e = create(h, cls, type, bubbles, cancelable);
        return dispatch(h, target, e);
    }

    // ------------------------------------------------------------------ oyentes

    /** La clave con la que se guardan los oyentes: el nodo mismo, o el objeto (la ventana, una petición...). */
    static Object keyOf(DomHost h, Object t) {
        if (t instanceof DomObj d && d.nat instanceof Node n) {
            return n;
        }
        if (t == Js.UNDEFINED || t == Js.NULL || t == h.r.global) {
            return h.r.global;
        }
        if (t instanceof JsObject o) {
            return o;
        }
        throw Js.typeError("Illegal invocation");
    }

    static JsObject jsOf(DomHost h, Object key) {
        if (key instanceof Node n) {
            return h.wrap(n);
        }
        return (JsObject) key;
    }

    static void noteType(DomHost h, String type) {
        switch (type) {
            case "keydown", "keyup", "keypress" -> h.keyListeners = true;
            case "mousemove", "mouseover", "mouseout", "mouseenter", "mouseleave", "pointermove", "pointerover", "pointerout" -> h.moveListeners = true;
            default -> { }
        }
    }

    static void add(DomHost h, Object key, String type, Object cb, Object options) {
        if (cb == Js.NULL || cb == Js.UNDEFINED) {
            return;
        }
        if (!(cb instanceof JsObject fn)) {
            throw Js.typeError("Failed to execute 'addEventListener' on 'EventTarget': parameter 2 is not of type 'Object'.");
        }
        boolean capture = false;
        boolean once = false;
        boolean passive = false;
        if (options instanceof JsObject o) {
            capture = Js.truthy(o.get("capture"));
            once = Js.truthy(o.get("once"));
            passive = Js.truthy(o.get("passive"));
            if (o.get("signal") instanceof JsObject sig && Js.truthy(sig.get("aborted"))) {
                return;
            }
        } else {
            capture = Js.truthy(options);
        }
        ArrayList<Listener> list = h.listeners.computeIfAbsent(key, k -> new ArrayList<>());
        for (Listener l : list) {
            if (!l.slot && l.fn == fn && l.capture == capture && l.type.equals(type)) {
                return;
            }
        }
        Listener l = new Listener();
        l.type = type;
        l.fn = fn;
        l.capture = capture;
        l.once = once;
        l.passive = passive;
        list.add(l);
        noteType(h, type);
        if (options instanceof JsObject o && o.get("signal") instanceof JsObject sig) {
            Object add = sig.get("addEventListener");
            if (add instanceof JsFunction af) {
                final Listener fl = l;
                af.call(sig, new Object[] {"abort", new NativeFunction(h.r, "", 0, (t, a) -> {
                    remove(h, key, fl);
                    return Js.UNDEFINED;
                })});
            }
        }
    }

    static void remove(DomHost h, Object key, String type, Object cb, Object options) {
        ArrayList<Listener> list = h.listeners.get(key);
        if (list == null || !(cb instanceof JsObject fn)) {
            return;
        }
        boolean capture = options instanceof JsObject o ? Js.truthy(o.get("capture")) : Js.truthy(options);
        for (Listener l : list) {
            if (!l.slot && l.fn == fn && l.capture == capture && l.type.equals(type)) {
                remove(h, key, l);
                return;
            }
        }
    }

    static void remove(DomHost h, Object key, Listener l) {
        l.removed = true;
        ArrayList<Listener> list = h.listeners.get(key);
        if (list != null) {
            list.remove(l);
        }
    }

    static Listener slot(DomHost h, Object key, String type) {
        ArrayList<Listener> list = h.listeners.get(key);
        if (list != null) {
            for (Listener l : list) {
                if (l.slot && l.type.equals(type)) {
                    return l;
                }
            }
        }
        return null;
    }

    static void setSlot(DomHost h, Object key, String type, JsObject fn, String source) {
        Listener l = slot(h, key, type);
        if (l != null) {
            l.fn = fn;
            l.source = source;
            return;
        }
        l = new Listener();
        l.type = type;
        l.fn = fn;
        l.slot = true;
        l.source = source;
        ArrayList<Listener> list = h.listeners.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(source != null ? 0 : list.size(), l);
    }

    /** Compila el texto de un atributo onclick="..." como una función. */
    private static JsFunction compileInline(DomHost h, String code) {
        try {
            Object f = h.engine.eval("(function(event){\n" + code + "\n})");
            return f instanceof JsFunction fn ? fn : null;
        } catch (JsException e) {
            h.reportError(e);
            return null;
        } catch (JsSyntaxError e) {
            h.env.console("error", "Uncaught SyntaxError: " + e.getMessage());
            return null;
        }
    }

    /** Si el nodo (o el body, para los eventos de la ventana) tiene un atributo onXXX nuevo o cambiado, lo prepara. */
    private static void syncInline(DomHost h, Object key, String type) {
        Node holder = null;
        if (key instanceof Node n) {
            holder = n.isElement() ? n : null;
        } else if (key == h.r.global) {
            for (String w : WINDOW_ATTRS) {
                if (w.equals(type)) {
                    holder = bodyOf(h);
                    break;
                }
            }
        }
        if (holder == null) {
            return;
        }
        String code = holder.attr("on" + type);
        Listener existing = slot(h, key, type);
        if (code == null) {
            if (existing != null && existing.source != null) {
                remove(h, key, existing);
            }
            return;
        }
        if (existing != null && (existing.source == null || existing.source.equals(code))) {
            return;
        }
        JsFunction f = compileInline(h, code);
        if (f != null) {
            setSlot(h, key, type, f, code);
        }
    }

    static Node bodyOf(DomHost h) {
        for (Node c : h.doc.children) {
            if (c.is("html")) {
                for (Node b : c.children) {
                    if (b.is("body")) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ recorrido

    /** Lleva el evento por el árbol. Falso si alguien llamó a preventDefault. */
    static boolean dispatch(DomHost h, Object targetKey, DomEvent ev) {
        ArrayList<Object> path = new ArrayList<>();
        if (targetKey instanceof Node n) {
            Node last = n;
            for (Node p = n; p != null; p = p.parent) {
                path.add(p);
                last = p;
            }
            if (last == h.doc) {
                path.add(h.r.global);
            }
        } else {
            path.add(targetKey);
        }
        ev.path = path;
        ev.target = jsOf(h, targetKey);
        ev.dispatching = true;
        ev.stopProp = false;
        ev.stopImmediate = false;
        try {
            for (int i = path.size() - 1; i >= 1 && !ev.stopProp; i--) {
                invoke(h, path.get(i), ev, CAPTURING, 1);
            }
            if (!ev.stopProp) {
                invoke(h, path.get(0), ev, AT_TARGET, 1);
                if (!ev.stopImmediate) {
                    invoke(h, path.get(0), ev, AT_TARGET, 2);
                }
            }
            if (ev.bubbles) {
                for (int i = 1; i < path.size() && !ev.stopProp; i++) {
                    invoke(h, path.get(i), ev, BUBBLING, 2);
                }
            }
        } finally {
            ev.dispatching = false;
            ev.phase = NONE;
            ev.currentTarget = null;
            ev.passiveNow = false;
        }
        return !ev.defaultPrevented;
    }

    /** filter: 1 = solo los de captura, 2 = solo los que no son de captura. */
    private static void invoke(DomHost h, Object key, DomEvent ev, int phase, int filter) {
        if (filter == 2) {
            syncInline(h, key, ev.type);
        }
        ArrayList<Listener> list = h.listeners.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        JsObject cur = jsOf(h, key);
        ev.currentTarget = cur;
        ev.phase = phase;
        for (Listener l : new ArrayList<>(list)) {
            if (l.removed || !l.type.equals(ev.type) || l.fn == null) {
                continue;
            }
            if (filter == 1 && !l.capture) {
                continue;
            }
            if (filter == 2 && l.capture) {
                continue;
            }
            if (l.once) {
                remove(h, key, l);
            }
            ev.passiveNow = l.passive;
            try {
                Object result;
                if (l.fn instanceof JsFunction f) {
                    result = f.call(cur, new Object[] {ev});
                } else {
                    Object he = l.fn.get("handleEvent");
                    result = he instanceof JsFunction hf ? hf.call(l.fn, new Object[] {ev}) : Js.UNDEFINED;
                }
                if (l.slot && result == Boolean.FALSE && !ev.type.equals("beforeunload")) {
                    ev.passiveNow = false;
                    ev.preventDefault();
                }
            } catch (JsException e) {
                h.reportError(e);
            } finally {
                ev.passiveNow = false;
            }
            h.engine.runJobs();
            if (ev.stopImmediate) {
                break;
            }
        }
    }
}
