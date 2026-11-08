package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IntersectionObserver y ResizeObserver (V2.10.0). Miden con la última página que acomodó el navegador y revisan sus elementos
 * cada vez que hay una disposición nueva o la ventana se desplaza o cambia de tamaño. Un elemento nuevo que todavía no está
 * acomodado no se reporta hasta que lo esté, para no avisar "no se ve" por error.
 */
final class DomObservers {
    private static final int MAX_OBSERVERS = 500;
    private static final int MAX_TARGETS = 5000;

    private DomObservers() {
    }

    static final class State {
        final List<IoObj> io = new ArrayList<>();
        final List<RoObj> ro = new ArrayList<>();
        boolean queued;
        JsObject ioEntry;
        JsObject roEntry;
    }

    /** Lo último que se le dijo al script de cada elemento observado. */
    static final class Seen {
        int index = -1;
        boolean intersecting;
        double w;
        double hh;
    }

    static final class IoObj extends JsObject {
        JsFunction callback;
        Node root;
        double[] margin = new double[4];
        boolean[] percent = new boolean[4];
        String marginText = "0px 0px 0px 0px";
        double[] thresholds = {0};
        final LinkedHashMap<Node, Seen> targets = new LinkedHashMap<>();

        IoObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "IntersectionObserver";
        }
    }

    static final class RoObj extends JsObject {
        JsFunction callback;
        final LinkedHashMap<Node, Seen> targets = new LinkedHashMap<>();

        RoObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "ResizeObserver";
        }
    }

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        installIntersection(h);
        installResize(h);
    }

    private static Node element(Object v, String cls, String method) {
        if (v instanceof DomObj d && d.nat instanceof Node n && n.isElement()) {
            return n;
        }
        throw Js.typeError("Failed to execute '" + method + "' on '" + cls + "': parameter 1 is not of type 'Element'.");
    }

    private static void queue(DomHost h) {
        State s = h.observers;
        if (s.queued) {
            return;
        }
        s.queued = true;
        if (!h.env.schedule(() -> {
            s.queued = false;
            check(h);
        }, 0)) {
            s.queued = false;
        }
    }

    private static void installIntersection(DomHost h) {
        Realm r = h.r;
        JsObject proto = new JsObject(r.objectProto);
        Lib.tag(proto, "IntersectionObserver");
        Lib.ctor(r, "IntersectionObserver", 1, proto, (t, a) -> {
            throw Js.typeError("Class constructor IntersectionObserver cannot be invoked without 'new'");
        }, (a, nt) -> {
            if (!(Js.arg(a, 0) instanceof JsFunction f)) {
                throw Js.typeError("Failed to construct 'IntersectionObserver': The callback provided as parameter 1 is not a function.");
            }
            IoObj o = new IoObj(Lib.protoFrom(nt, proto));
            o.callback = f;
            if (Js.arg(a, 1) instanceof JsObject opts) {
                Object root = opts.get("root");
                if (root instanceof DomObj d && d.nat instanceof Node n && n.isElement()) {
                    o.root = n;
                } else if (root != Js.UNDEFINED && root != Js.NULL && !(root instanceof DomObj d2 && d2.nat == h.doc)) {
                    throw Js.typeError("Failed to construct 'IntersectionObserver': Failed to read the 'root' property from 'IntersectionObserverInit': "
                            + "The provided value is not of type '(Document or Element)'.");
                }
                Object rm = opts.get("rootMargin");
                if (rm != Js.UNDEFINED) {
                    parseMargin(h, o, Js.toStr(rm));
                }
                Object th = opts.get("threshold");
                if (th != Js.UNDEFINED) {
                    List<Object> list = th instanceof JsArray ? Js.toList(th) : List.of(th);
                    double[] ts = new double[list.size()];
                    for (int i = 0; i < ts.length; i++) {
                        double d = Js.toNumber(list.get(i));
                        if (Double.isNaN(d) || d < 0 || d > 1) {
                            throw Js.rangeError("Failed to construct 'IntersectionObserver': Threshold values must be numbers between 0 and 1.");
                        }
                        ts[i] = d;
                    }
                    Arrays.sort(ts);
                    o.thresholds = ts.length == 0 ? new double[] {0} : ts;
                }
            }
            return o;
        });
        Lib.getter(r, proto, "root", (t, a) -> {
            IoObj o = io(t);
            return o.root == null ? Js.NULL : h.w(o.root);
        });
        Lib.getter(r, proto, "rootMargin", (t, a) -> io(t).marginText);
        Lib.getter(r, proto, "thresholds", (t, a) -> {
            List<Object> out = new ArrayList<>();
            for (double d : io(t).thresholds) {
                out.add(Js.num(d));
            }
            return Lib.array(r, out);
        });
        Lib.method(r, proto, "observe", 1, (t, a) -> {
            IoObj o = io(t);
            Node n = element(Js.arg(a, 0), "IntersectionObserver", "observe");
            if (o.targets.containsKey(n) || o.targets.size() >= MAX_TARGETS) {
                return Js.UNDEFINED;
            }
            o.targets.put(n, new Seen());
            State s = h.observers;
            if (!s.io.contains(o) && s.io.size() < MAX_OBSERVERS) {
                s.io.add(o);
            }
            queue(h);
            return Js.UNDEFINED;
        });
        Lib.method(r, proto, "unobserve", 1, (t, a) -> {
            IoObj o = io(t);
            o.targets.remove(element(Js.arg(a, 0), "IntersectionObserver", "unobserve"));
            return Js.UNDEFINED;
        });
        Lib.method(r, proto, "disconnect", 0, (t, a) -> {
            IoObj o = io(t);
            o.targets.clear();
            h.observers.io.remove(o);
            return Js.UNDEFINED;
        });
        Lib.method(r, proto, "takeRecords", 0, (t, a) -> {
            io(t);
            return Lib.array(r, new ArrayList<Object>());           // los avisos salen en cuanto se calculan
        });

        JsObject entry = new JsObject(r.objectProto);
        Lib.tag(entry, "IntersectionObserverEntry");
        Lib.ctor(r, "IntersectionObserverEntry", 1, entry, (t, a) -> {
            throw Js.typeError("Illegal constructor");
        }, (a, nt) -> {
            throw Js.typeError("Illegal constructor");
        });
        h.observers.ioEntry = entry;
    }

    private static IoObj io(Object t) {
        if (t instanceof IoObj o) {
            return o;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static RoObj ro(Object t) {
        if (t instanceof RoObj o) {
            return o;
        }
        throw Js.typeError("Illegal invocation");
    }

    /** "10px 5%" como en CSS margin: de 1 a 4 valores en px o %. */
    private static void parseMargin(DomHost h, IoObj o, String text) {
        String[] parts = text.trim().isEmpty() ? new String[] {"0px"} : text.trim().split("\\s+");
        if (parts.length > 4) {
            throw h.domError("SyntaxError", "Failed to construct 'IntersectionObserver': Extra text found at the end of rootMargin.");
        }
        double[] v = new double[parts.length];
        boolean[] pc = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            try {
                if (p.endsWith("px")) {
                    v[i] = Double.parseDouble(p.substring(0, p.length() - 2));
                } else if (p.endsWith("%")) {
                    v[i] = Double.parseDouble(p.substring(0, p.length() - 1));
                    pc[i] = true;
                } else if (p.equals("0")) {
                    v[i] = 0;
                } else {
                    throw new NumberFormatException(p);
                }
            } catch (NumberFormatException e) {
                throw h.domError("SyntaxError", "Failed to construct 'IntersectionObserver': rootMargin must be specified in pixels or percent.");
            }
        }
        // arriba, derecha, abajo, izquierda (si faltan se repiten como en CSS)
        int[] pick = switch (parts.length) {
            case 1 -> new int[] {0, 0, 0, 0};
            case 2 -> new int[] {0, 1, 0, 1};
            case 3 -> new int[] {0, 1, 2, 1};
            default -> new int[] {0, 1, 2, 3};
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            o.margin[i] = v[pick[i]];
            o.percent[i] = pc[pick[i]];
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Js.numToString(o.margin[i])).append(o.percent[i] ? "%" : "px");
        }
        o.marginText = sb.toString();
    }

    private static void installResize(DomHost h) {
        Realm r = h.r;
        JsObject proto = new JsObject(r.objectProto);
        Lib.tag(proto, "ResizeObserver");
        Lib.ctor(r, "ResizeObserver", 1, proto, (t, a) -> {
            throw Js.typeError("Class constructor ResizeObserver cannot be invoked without 'new'");
        }, (a, nt) -> {
            if (!(Js.arg(a, 0) instanceof JsFunction f)) {
                throw Js.typeError("Failed to construct 'ResizeObserver': The callback provided as parameter 1 is not a function.");
            }
            RoObj o = new RoObj(Lib.protoFrom(nt, proto));
            o.callback = f;
            return o;
        });
        Lib.method(r, proto, "observe", 1, (t, a) -> {
            RoObj o = ro(t);
            Node n = element(Js.arg(a, 0), "ResizeObserver", "observe");
            if (o.targets.containsKey(n) || o.targets.size() >= MAX_TARGETS) {
                return Js.UNDEFINED;
            }
            o.targets.put(n, new Seen());
            State s = h.observers;
            if (!s.ro.contains(o) && s.ro.size() < MAX_OBSERVERS) {
                s.ro.add(o);
            }
            queue(h);
            return Js.UNDEFINED;
        });
        Lib.method(r, proto, "unobserve", 1, (t, a) -> {
            RoObj o = ro(t);
            o.targets.remove(element(Js.arg(a, 0), "ResizeObserver", "unobserve"));
            return Js.UNDEFINED;
        });
        Lib.method(r, proto, "disconnect", 0, (t, a) -> {
            RoObj o = ro(t);
            o.targets.clear();
            h.observers.ro.remove(o);
            return Js.UNDEFINED;
        });

        JsObject entry = new JsObject(r.objectProto);
        Lib.tag(entry, "ResizeObserverEntry");
        Lib.ctor(r, "ResizeObserverEntry", 2, entry, (t, a) -> {
            throw Js.typeError("Illegal constructor");
        }, (a, nt) -> {
            throw Js.typeError("Illegal constructor");
        });
        h.observers.roEntry = entry;
    }

    // ------------------------------------------------------------------ revisar

    /** Revisa todos los elementos observados con las medidas de la última página acomodada y avisa de lo que cambió. */
    static void check(DomHost h) {
        State s = h.observers;
        if (s.io.isEmpty() && s.ro.isEmpty()) {
            return;
        }
        int[] ps = h.env.pageSize();
        if (ps == null) {
            return;                                         // todavía no hay disposición: se revisa cuando llegue
        }
        boolean fresh = ps[2] == 1;
        for (IoObj o : new ArrayList<>(s.io)) {
            checkIntersection(h, o, fresh);
        }
        for (RoObj o : new ArrayList<>(s.ro)) {
            checkResize(h, o, fresh);
        }
    }

    private static void deliver(DomHost h, JsFunction cb, JsObject observer, List<Object> entries) {
        try {
            cb.call(observer, new Object[] {Lib.array(h.r, entries), observer});
        } catch (JsException e) {
            h.reportError(e);
        }
        h.engine.runJobs();
    }

    private static void checkIntersection(DomHost h, IoObj o, boolean fresh) {
        if (o.targets.isEmpty()) {
            return;
        }
        int[] vp = h.env.viewport();
        double[] root;
        if (o.root == null) {
            root = new double[] {vp[2], vp[3], vp[0], vp[1]};
        } else {
            root = DomGeometry.page(h, o.root);
            if (root == null) {
                root = new double[] {0, 0, 0, 0};
            }
        }
        // el rectángulo de la raíz agrandado (o achicado) por rootMargin
        double top = o.margin[0] * (o.percent[0] ? root[3] / 100 : 1);
        double right = o.margin[1] * (o.percent[1] ? root[2] / 100 : 1);
        double bottom = o.margin[2] * (o.percent[2] ? root[3] / 100 : 1);
        double left = o.margin[3] * (o.percent[3] ? root[2] / 100 : 1);
        double rx0 = root[0] - left;
        double ry0 = root[1] - top;
        double rx1 = root[0] + root[2] + right;
        double ry1 = root[1] + root[3] + bottom;

        List<Object> entries = new ArrayList<>();
        for (Map.Entry<Node, Seen> e : new ArrayList<>(o.targets.entrySet())) {
            Node n = e.getKey();
            Seen seen = e.getValue();
            double[] pr = DomGeometry.page(h, n);
            if (pr == null && !fresh && DomGeometry.connected(h, n)) {
                continue;                                   // es nuevo y todavía no se acomoda: no se sabe
            }
            double tx = pr == null ? 0 : pr[0];
            double ty = pr == null ? 0 : pr[1];
            double tw = pr == null ? 0 : pr[2];
            double th = pr == null ? 0 : pr[3];
            double ix0 = Math.max(tx, rx0);
            double iy0 = Math.max(ty, ry0);
            double ix1 = Math.min(tx + tw, rx1);
            double iy1 = Math.min(ty + th, ry1);
            boolean inter = pr != null && ix0 <= ix1 && iy0 <= iy1;
            double area = tw * th;
            double inArea = inter ? Math.max(0, ix1 - ix0) * Math.max(0, iy1 - iy0) : 0;
            double ratio = area > 0 ? Math.min(1, inArea / area) : inter ? 1 : 0;
            int index = 0;
            if (inter) {
                for (double t : o.thresholds) {
                    if (t <= ratio + 1e-9) {
                        index++;
                    }
                }
            }
            if (index == seen.index && inter == seen.intersecting) {
                continue;
            }
            seen.index = index;
            seen.intersecting = inter;
            JsObject en = new JsObject(h.observers.ioEntry);
            en.setData("time", Js.num(h.now()));
            en.setData("rootBounds", DomNodes.rect(h, rx0 - vp[2], ry0 - vp[3], rx1 - rx0, ry1 - ry0));
            en.setData("boundingClientRect", DomNodes.rect(h, tx - vp[2], ty - vp[3], tw, th));
            en.setData("intersectionRect", inter
                    ? DomNodes.rect(h, ix0 - vp[2], iy0 - vp[3], Math.max(0, ix1 - ix0), Math.max(0, iy1 - iy0))
                    : DomNodes.rect(h, 0, 0, 0, 0));
            en.setData("isIntersecting", inter);
            en.setData("intersectionRatio", Js.num(ratio));
            en.setData("target", h.w(n));
            entries.add(en);
        }
        if (!entries.isEmpty()) {
            deliver(h, o.callback, o, entries);
        }
    }

    private static void checkResize(DomHost h, RoObj o, boolean fresh) {
        if (o.targets.isEmpty()) {
            return;
        }
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<Node, Seen> e : new ArrayList<>(o.targets.entrySet())) {
            Node n = e.getKey();
            Seen seen = e.getValue();
            double[] pr = DomGeometry.page(h, n);
            if (pr == null && !fresh && DomGeometry.connected(h, n)) {
                continue;
            }
            double w = pr == null ? 0 : pr[2];
            double hh = pr == null ? 0 : pr[3];
            if (w == seen.w && hh == seen.hh) {
                continue;
            }
            seen.w = w;
            seen.hh = hh;
            JsObject en = new JsObject(h.observers.roEntry);
            en.setData("target", h.w(n));
            en.setData("contentRect", DomNodes.rect(h, 0, 0, w, hh));
            for (String box : new String[] {"contentBoxSize", "borderBoxSize", "devicePixelContentBoxSize"}) {
                JsObject size = Js.newObject();
                size.setData("inlineSize", Js.num(w));
                size.setData("blockSize", Js.num(hh));
                List<Object> one = new ArrayList<>();
                one.add(size);
                en.setData(box, Lib.array(h.r, one));
            }
            entries.add(en);
        }
        if (!entries.isEmpty()) {
            deliver(h, o.callback, o, entries);
        }
    }
}
