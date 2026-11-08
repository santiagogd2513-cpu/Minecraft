package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Medidas reales para los scripts (V2.10.0): getBoundingClientRect, offsetWidth, clientHeight, scrollHeight, scrollTop,
 * scrollIntoView, document.elementFromPoint... Salen de la última página que el navegador acomodó ({@code DomHost.Env.rect}),
 * así que un elemento recién agregado no tiene medidas hasta que la pantalla vuelva a acomodar (unas décimas de segundo).
 */
final class DomGeometry {
    private DomGeometry() {
    }

    // ------------------------------------------------------------------ medidas

    static boolean connected(DomHost h, Node n) {
        for (Node p = n; p != null; p = p.parent) {
            if (p == h.doc) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRoot(Node n) {
        return n.is("html") || n.is("body");
    }

    /** {x, y, ancho, alto} en coordenadas de la página, o null si el elemento no se dibujó. */
    static double[] page(DomHost h, Node n) {
        if (n == null || !n.isElement() || !connected(h, n)) {
            return null;
        }
        int[] ps = h.env.pageSize();
        if (ps == null) {
            return null;
        }
        if (isRoot(n)) {
            return new double[] {0, 0, ps[0], ps[1]};
        }
        int[] r = h.env.rect(n);
        return r == null ? null : new double[] {r[0], r[1], r[2], r[3]};
    }

    /** Lo mismo pero respecto a la ventana (restando el desplazamiento), como getBoundingClientRect. */
    static double[] client(DomHost h, Node n) {
        double[] p = page(h, n);
        if (p == null) {
            return null;
        }
        int[] vp = h.env.viewport();
        return new double[] {p[0] - vp[2], p[1] - vp[3], p[2], p[3]};
    }

    private static double size(DomHost h, Node n, int idx) {
        double[] p = page(h, n);
        return p == null ? 0 : p[idx];
    }

    private static Object count(double v) {
        return Js.num(Math.round(v));
    }

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject el = h.elementProto;
        JsObject he = h.htmlElementProto;

        h.method(el, "getBoundingClientRect", 0, (n, a) -> {
            double[] c = client(h, n);
            return c == null ? DomNodes.rect(h, 0, 0, 0, 0) : DomNodes.rect(h, c[0], c[1], c[2], c[3]);
        });
        h.method(el, "getClientRects", 0, (n, a) -> {
            double[] c = client(h, n);
            List<Object> out = new ArrayList<>();
            if (c != null) {
                out.add(DomNodes.rect(h, c[0], c[1], c[2], c[3]));
            }
            return Lib.array(r, out);
        });

        // tamaño dentro del elemento
        h.getter(el, "clientWidth", n -> {
            if (n.is("html")) {
                return count(h.env.viewport()[0]);
            }
            return count(size(h, n, 2));
        });
        h.getter(el, "clientHeight", n -> {
            if (n.is("html")) {
                return count(h.env.viewport()[1]);
            }
            return count(size(h, n, 3));
        });
        h.getter(el, "clientTop", n -> Js.num(0));
        h.getter(el, "clientLeft", n -> Js.num(0));
        h.getter(el, "scrollWidth", n -> {
            if (n.is("html")) {
                return count(Math.max(size(h, n, 2), h.env.viewport()[0]));
            }
            return count(size(h, n, 2));
        });
        h.getter(el, "scrollHeight", n -> {
            if (n.is("html")) {
                return count(Math.max(size(h, n, 3), h.env.viewport()[1]));
            }
            return count(size(h, n, 3));
        });

        // tamaño y lugar por fuera del elemento (HTMLElement)
        h.getter(he, "offsetWidth", n -> count(size(h, n, 2)));
        h.getter(he, "offsetHeight", n -> count(size(h, n, 3)));
        h.getter(he, "offsetLeft", n -> isRoot(n) ? Js.num(0) : count(size(h, n, 0)));
        h.getter(he, "offsetTop", n -> isRoot(n) ? Js.num(0) : count(size(h, n, 1)));
        h.getter(he, "offsetParent", n -> {
            if (isRoot(n) || page(h, n) == null) {
                return Js.NULL;
            }
            Node body = DomDocument.bodyOf(h);
            return body == null ? Js.NULL : h.w(body);
        });

        // desplazamiento: solo la página entera (html y body) se desplaza; el resto de los elementos no tiene barras propias
        h.accessor(el, "scrollTop", n -> isRoot(n) ? Js.num(h.env.viewport()[3]) : Js.num(0), (n, v) -> {
            double d = Js.toNumber(v);
            if (isRoot(n) && !Double.isNaN(d)) {
                h.env.scrollTo(h.env.viewport()[2], (int) Math.max(0, Math.min(d, 1e9)));
            }
        });
        h.accessor(el, "scrollLeft", n -> isRoot(n) ? Js.num(h.env.viewport()[2]) : Js.num(0), (n, v) -> {
            double d = Js.toNumber(v);
            if (isRoot(n) && !Double.isNaN(d)) {
                h.env.scrollTo((int) Math.max(0, Math.min(d, 1e9)), h.env.viewport()[3]);
            }
        });
        for (String m : new String[] {"scroll", "scrollTo", "scrollBy"}) {
            boolean by = m.equals("scrollBy");
            h.method(el, m, 2, (n, a) -> {
                if (isRoot(n)) {
                    Object f = r.global.get(by ? "scrollBy" : "scrollTo");
                    if (f instanceof JsFunction fn) {
                        fn.call(r.global, a);
                    }
                }
                return Js.UNDEFINED;
            });
        }
        h.method(el, "scrollIntoView", 0, (n, a) -> {
            intoView(h, n, Js.arg(a, 0), false, 0);
            return Js.UNDEFINED;
        });
        h.method(el, "scrollIntoViewIfNeeded", 0, (n, a) -> {
            Object c = Js.arg(a, 0);
            boolean center = c == Js.UNDEFINED || Js.truthy(c);
            intoView(h, n, center ? "center" : "nearest", true, 0);
            return Js.UNDEFINED;
        });

        // qué hay en un punto de la ventana
        JsObject doc = h.documentProto;
        h.method(doc, "elementFromPoint", 2, (n, a) -> {
            Node t = pointNode(h, Js.toNumber(Js.arg(a, 0)), Js.toNumber(Js.arg(a, 1)));
            return t == null ? Js.NULL : h.w(t);
        });
        h.method(doc, "elementsFromPoint", 2, (n, a) -> {
            List<Object> out = new ArrayList<>();
            Node t = pointNode(h, Js.toNumber(Js.arg(a, 0)), Js.toNumber(Js.arg(a, 1)));
            for (Node p = t; p != null && p != h.doc; p = p.parent) {
                if (p.isElement()) {
                    out.add(h.w(p));
                }
            }
            return Lib.array(r, out);
        });
    }

    private static Node pointNode(DomHost h, double x, double y) {
        int[] ps = h.env.pageSize();
        int[] vp = h.env.viewport();
        if (ps == null || Double.isNaN(x) || Double.isNaN(y) || x < 0 || y < 0 || x >= vp[0] || y >= vp[1]) {
            return null;
        }
        Node t = h.env.nodeAt((int) x + vp[2], (int) y + vp[3]);
        while (t != null && !t.isElement()) {
            t = t.parent;
        }
        if (t == null || !connected(h, t)) {
            t = DomDocument.bodyOf(h);
            if (t == null) {
                t = DomDocument.htmlOf(h);
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ scrollIntoView

    private static final long[] RETRY_MS = {50, 120, 250, 500};

    /**
     * Desplaza la página hasta dejar el elemento a la vista. Si el elemento es nuevo y la pantalla todavía no lo acomodó, espera y
     * vuelve a intentar unas cuantas veces (lo normal es "agregar un mensaje y mostrarlo").
     */
    private static void intoView(DomHost h, Node n, Object arg, boolean onlyIfNeeded, int attempt) {
        double[] c = page(h, n);
        if (c == null) {
            int[] ps = h.env.pageSize();
            boolean stale = ps == null || ps[2] == 0;
            if (stale && attempt < RETRY_MS.length && connected(h, n) && n.isElement()) {
                h.env.schedule(() -> {
                    intoView(h, n, arg, onlyIfNeeded, attempt + 1);
                    h.engine.runJobs();
                }, RETRY_MS[attempt]);
            }
            return;
        }
        int[] vp = h.env.viewport();
        double vh = vp[1];
        double sy = vp[3];
        double top = c[1];
        double bottom = c[1] + c[3];
        String block = "start";
        if (arg instanceof JsObject o) {
            Object b = o.get("block");
            if (b != Js.UNDEFINED) {
                block = Js.toStr(b);
            }
        } else if (arg instanceof String s) {
            block = s;
        } else if (arg instanceof Boolean b && !b) {
            block = "end";
        }
        boolean visible = top >= sy && bottom <= sy + vh;
        if (onlyIfNeeded && visible) {
            return;
        }
        double target;
        switch (block) {
            case "center":
                target = top + c[3] / 2 - vh / 2;
                break;
            case "end":
                target = bottom - vh;
                break;
            case "nearest":
                if (visible || (top < sy && bottom > sy + vh)) {
                    return;
                }
                target = top < sy ? top : bottom - vh;
                break;
            default:
                target = top;
                break;
        }
        h.env.scrollTo(vp[2], (int) Math.max(0, Math.round(target)));
    }
}
