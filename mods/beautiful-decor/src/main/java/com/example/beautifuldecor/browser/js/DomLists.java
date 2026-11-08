package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Listas del DOM: NodeList, HTMLCollection, classList, dataset y attributes. */
final class DomLists {
    private DomLists() {
    }

    @FunctionalInterface
    interface Source {
        List<Node> get();
    }

    /** NodeList o HTMLCollection. Las "vivas" se recalculan cuando el documento cambió. */
    static final class DomList extends DomObj {
        final DomHost h;
        final Source src;
        final boolean live;
        final boolean named;
        /** En form.elements, un nombre repetido (casillas, radios) da una RadioNodeList. */
        boolean radios;
        private List<Node> cache;
        private int version = -1;

        DomList(DomHost h, JsObject proto, Source src, boolean live, boolean named) {
            super(proto, null);
            this.h = h;
            this.src = src;
            this.live = live;
            this.named = named;
        }

        List<Node> items() {
            if (!this.live) {
                if (this.cache == null) {
                    this.cache = this.src.get();
                }
                return this.cache;
            }
            int v = Node.mutations();
            if (this.cache == null || v != this.version) {
                this.cache = this.src.get();
                this.version = v;
            }
            return this.cache;
        }

        @Override
        public Prop getOwn(Object key) {
            if (key instanceof String s) {
                int idx = Js.arrayIndex(s);
                if (idx >= 0) {
                    List<Node> l = items();
                    return idx < l.size() ? new Prop(this.h.wrap(l.get(idx)), Prop.E) : null;
                }
                if (s.equals("length")) {
                    return new Prop(Js.num(items().size()), 0);
                }
                if (this.named && !s.isEmpty() && (this.proto == null || !this.proto.hasProperty(s))) {
                    if (this.radios) {
                        ArrayList<Node> hits = new ArrayList<>();
                        for (Node n : items()) {
                            if (s.equals(n.attr("id")) || s.equals(n.attr("name"))) {
                                hits.add(n);
                            }
                        }
                        if (hits.size() > 1) {
                            return new Prop(list(this.h, "RadioNodeList", false, () -> hits), Prop.C);
                        }
                    }
                    Node n = byName(s);
                    if (n != null) {
                        return new Prop(this.h.wrap(n), Prop.C);
                    }
                }
            }
            return super.getOwn(key);
        }

        Node byName(String name) {
            for (Node n : items()) {
                if (name.equals(n.attr("id")) || name.equals(n.attr("name"))) {
                    return n;
                }
            }
            return null;
        }

        @Override
        public List<Object> ownKeys() {
            List<Object> out = new ArrayList<>();
            int n = items().size();
            for (int i = 0; i < n; i++) {
                out.add(Integer.toString(i));
            }
            out.addAll(super.ownKeys());
            return out;
        }

        JsArray snapshot() {
            ArrayList<Object> out = new ArrayList<>();
            for (Node n : items()) {
                out.add(this.h.wrap(n));
            }
            return new JsArray(this.h.r.arrayProto, out);
        }
    }

    static DomList list(DomHost h, String kind, boolean live, Source src) {
        JsObject proto = h.protos.get(kind);
        DomList l = new DomList(h, proto, src, live, !kind.equals("NodeList"));
        l.radios = kind.equals("HTMLFormControlsCollection");
        return l;
    }

    private static DomList self(Object t) {
        if (t instanceof DomList l) {
            return l;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static Object iter(DomHost h, JsArray arr) {
        return h.r.arrayValues.call(arr, Js.EMPTY);
    }

    static void install(DomHost h) {
        Realm r = h.r;
        for (String kind : new String[] {"NodeList", "HTMLCollection"}) {
            JsObject p = h.iface(kind, null);
            Lib.method(r, p, "item", 1, (t, a) -> {
                List<Node> l = self(t).items();
                double d = Js.toInteger(Js.arg(a, 0));
                return d >= 0 && d < l.size() ? h.wrap(l.get((int) d)) : Js.NULL;
            });
            p.setHidden(JsSymbol.ITERATOR, new NativeFunction(r, "values", 0, (t, a) -> iter(h, self(t).snapshot())));
            if (kind.equals("NodeList")) {
                Lib.method(r, p, "forEach", 1, (t, a) -> {
                    JsFunction f = Lib.callable(Js.arg(a, 0), null);
                    JsArray snap = self(t).snapshot();
                    for (int i = 0; i < snap.length(); i++) {
                        f.call(Js.arg(a, 1), new Object[] {snap.getIndex(i), Js.num(i), t});
                    }
                    return Js.UNDEFINED;
                });
                Lib.method(r, p, "values", 0, (t, a) -> iter(h, self(t).snapshot()));
                Lib.method(r, p, "keys", 0, (t, a) -> {
                    ArrayList<Object> ks = new ArrayList<>();
                    for (int i = 0; i < self(t).items().size(); i++) {
                        ks.add(Js.num(i));
                    }
                    return iter(h, Lib.array(r, ks));
                });
                Lib.method(r, p, "entries", 0, (t, a) -> {
                    ArrayList<Object> es = new ArrayList<>();
                    JsArray snap = self(t).snapshot();
                    for (int i = 0; i < snap.length(); i++) {
                        ArrayList<Object> pair = new ArrayList<>();
                        pair.add(Js.num(i));
                        pair.add(snap.getIndex(i));
                        es.add(Lib.array(r, pair));
                    }
                    return iter(h, Lib.array(r, es));
                });
            } else {
                Lib.method(r, p, "namedItem", 1, (t, a) -> {
                    Node n = self(t).byName(Js.toStr(Js.arg(a, 0)));
                    return h.w(n);
                });
            }
        }
        h.iface("HTMLOptionsCollection", h.protos.get("HTMLCollection"));
        JsObject fcc = h.iface("HTMLFormControlsCollection", h.protos.get("HTMLCollection"));
        JsObject rnl = h.iface("RadioNodeList", h.protos.get("NodeList"));
        Lib.accessor(r, rnl, "value", (t, a) -> {
            for (Node n : self(t).items()) {
                if (n.isChecked() && n.is("input")) {
                    String v = n.attr("value");
                    return v == null ? "on" : v;
                }
            }
            return "";
        }, (t, a) -> {
            String want = Js.toStr(Js.arg(a, 0));
            for (Node n : self(t).items()) {
                if (n.is("input")) {
                    String v = n.attr("value");
                    boolean on = (v == null ? "on" : v).equals(want);
                    DomForms.setChecked(h, n, on);
                }
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, fcc, "namedItem", 1, (t, a) -> {
            DomList l = self(t);
            String name = Js.toStr(Js.arg(a, 0));
            ArrayList<Node> hits = new ArrayList<>();
            for (Node n : l.items()) {
                if (name.equals(n.attr("id")) || name.equals(n.attr("name"))) {
                    hits.add(n);
                }
            }
            if (hits.isEmpty()) {
                return Js.NULL;
            }
            if (hits.size() == 1) {
                return h.wrap(hits.get(0));
            }
            final List<Node> fixed = hits;
            return list(h, "RadioNodeList", false, () -> fixed);
        });
        DomTokens.install(h);
        DomDataset.install(h);
        DomAttrs.install(h);
    }

    // ------------------------------------------------------------------ classList

    static final class DomTokens extends DomObj {
        final DomHost h;
        final Node node;
        final String attr;

        DomTokens(DomHost h, Node node, String attr) {
            super(h.protos.get("DOMTokenList"), null);
            this.h = h;
            this.node = node;
            this.attr = attr;
        }

        List<String> tokens() {
            String v = this.node.attr(this.attr);
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (v != null) {
                for (String s : v.trim().split("\\s+")) {
                    if (!s.isEmpty()) {
                        set.add(s);
                    }
                }
            }
            return new ArrayList<>(set);
        }

        void store(List<String> list) {
            if (list.isEmpty() && this.node.attr(this.attr) == null) {
                return;
            }
            this.node.setAttr(this.attr, String.join(" ", list));
            Node.touch();
        }

        @Override
        public Prop getOwn(Object key) {
            if (key instanceof String s) {
                int idx = Js.arrayIndex(s);
                if (idx >= 0) {
                    List<String> l = tokens();
                    return idx < l.size() ? new Prop(l.get(idx), Prop.E) : null;
                }
                if (s.equals("length")) {
                    return new Prop(Js.num(tokens().size()), 0);
                }
            }
            return super.getOwn(key);
        }

        @Override
        public List<Object> ownKeys() {
            List<Object> out = new ArrayList<>();
            int n = tokens().size();
            for (int i = 0; i < n; i++) {
                out.add(Integer.toString(i));
            }
            out.addAll(super.ownKeys());
            return out;
        }

        String check(String t) {
            if (t.isEmpty()) {
                throw this.h.domError("SyntaxError", "The token provided must not be empty.");
            }
            for (int i = 0; i < t.length(); i++) {
                if (Character.isWhitespace(t.charAt(i))) {
                    throw this.h.domError("InvalidCharacterError", "The token provided ('" + t + "') contains HTML space characters, which are not valid in tokens.");
                }
            }
            return t;
        }

        static DomTokens of(Object t) {
            if (t instanceof DomTokens d) {
                return d;
            }
            throw Js.typeError("Illegal invocation");
        }

        static void install(DomHost h) {
            Realm r = h.r;
            JsObject p = h.iface("DOMTokenList", null);
            Lib.method(r, p, "contains", 1, (t, a) -> of(t).tokens().contains(Js.toStr(Js.arg(a, 0))));
            Lib.method(r, p, "add", 0, (t, a) -> {
                DomTokens d = of(t);
                List<String> l = d.tokens();
                for (Object o : a) {
                    String s = d.check(Js.toStr(o));
                    if (!l.contains(s)) {
                        l.add(s);
                    }
                }
                d.store(l);
                return Js.UNDEFINED;
            });
            Lib.method(r, p, "remove", 0, (t, a) -> {
                DomTokens d = of(t);
                List<String> l = d.tokens();
                for (Object o : a) {
                    l.remove(d.check(Js.toStr(o)));
                }
                d.store(l);
                return Js.UNDEFINED;
            });
            Lib.method(r, p, "toggle", 1, (t, a) -> {
                DomTokens d = of(t);
                String s = d.check(Js.toStr(Js.arg(a, 0)));
                List<String> l = d.tokens();
                boolean has = l.contains(s);
                boolean want = Js.arg(a, 1) == Js.UNDEFINED ? !has : Js.truthy(Js.arg(a, 1));
                if (want && !has) {
                    l.add(s);
                    d.store(l);
                } else if (!want && has) {
                    l.remove(s);
                    d.store(l);
                }
                return want;
            });
            Lib.method(r, p, "replace", 2, (t, a) -> {
                DomTokens d = of(t);
                String from = d.check(Js.toStr(Js.arg(a, 0)));
                String to = d.check(Js.toStr(Js.arg(a, 1)));
                List<String> l = d.tokens();
                int i = l.indexOf(from);
                if (i < 0) {
                    return false;
                }
                if (l.contains(to)) {
                    l.remove(i);
                } else {
                    l.set(i, to);
                }
                d.store(l);
                return true;
            });
            Lib.method(r, p, "item", 1, (t, a) -> {
                List<String> l = of(t).tokens();
                double i = Js.toInteger(Js.arg(a, 0));
                return i >= 0 && i < l.size() ? l.get((int) i) : Js.NULL;
            });
            Lib.method(r, p, "supports", 1, (t, a) -> true);
            Lib.method(r, p, "toString", 0, (t, a) -> {
                String v = of(t).node.attr(of(t).attr);
                return v == null ? "" : v;
            });
            Lib.accessor(r, p, "value", (t, a) -> {
                String v = of(t).node.attr(of(t).attr);
                return v == null ? "" : v;
            }, (t, a) -> {
                of(t).node.setAttr(of(t).attr, Js.toStr(Js.arg(a, 0)));
                Node.touch();
                return Js.UNDEFINED;
            });
            Lib.method(r, p, "forEach", 1, (t, a) -> {
                JsFunction f = Lib.callable(Js.arg(a, 0), null);
                List<String> l = of(t).tokens();
                for (int i = 0; i < l.size(); i++) {
                    f.call(Js.arg(a, 1), new Object[] {l.get(i), Js.num(i), t});
                }
                return Js.UNDEFINED;
            });
            NativeFunction values = new NativeFunction(r, "values", 0, (t, a) -> iter(h, Lib.array(r, of(t).tokens())));
            p.setHidden("values", values);
            p.setHidden(JsSymbol.ITERATOR, values);
            Lib.method(r, p, "keys", 0, (t, a) -> {
                ArrayList<Object> ks = new ArrayList<>();
                for (int i = 0; i < of(t).tokens().size(); i++) {
                    ks.add(Js.num(i));
                }
                return iter(h, Lib.array(r, ks));
            });
        }
    }

    // ------------------------------------------------------------------ dataset

    static String kebab(String camel) {
        StringBuilder sb = new StringBuilder("data-");
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String camel(String attr) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (int i = 5; i < attr.length(); i++) {
            char c = attr.charAt(i);
            if (c == '-' && i + 1 < attr.length() && Character.isLowerCase(attr.charAt(i + 1))) {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    static final class DomDataset extends DomObj {
        final Node node;

        DomDataset(DomHost h, Node node) {
            super(h.protos.get("DOMStringMap"), null);
            this.node = node;
        }

        @Override
        public Prop getOwn(Object key) {
            if (key instanceof String s) {
                String v = this.node.attr(kebab(s));
                if (v != null) {
                    return new Prop(v, Prop.ALL);
                }
            }
            return super.getOwn(key);
        }

        @Override
        public boolean set(Object key, Object value, Object receiver) {
            if (key instanceof String s) {
                this.node.setAttr(kebab(s), Js.toStr(value));
                Node.touch();
                return true;
            }
            return super.set(key, value, receiver);
        }

        @Override
        public boolean defineOwn(Object key, Prop p) {
            if (key instanceof String s && !p.accessor) {
                this.node.setAttr(kebab(s), Js.toStr(p.value));
                Node.touch();
                return true;
            }
            return super.defineOwn(key, p);
        }

        @Override
        public boolean deleteOwn(Object key) {
            if (key instanceof String s) {
                this.node.removeAttr(kebab(s));
                Node.touch();
                return true;
            }
            return super.deleteOwn(key);
        }

        @Override
        public List<Object> ownKeys() {
            List<Object> out = new ArrayList<>();
            for (String k : this.node.attrs.keySet()) {
                if (k.startsWith("data-") && k.length() > 5) {
                    out.add(camel(k));
                }
            }
            return out;
        }

        static void install(DomHost h) {
            h.iface("DOMStringMap", null);
        }
    }

    // ------------------------------------------------------------------ attributes

    static final class AttrRec {
        final Node el;
        final String name;

        AttrRec(Node el, String name) {
            this.el = el;
            this.name = name;
        }
    }

    static JsObject attrObject(DomHost h, Node el, String name) {
        return new DomObj(h.protos.get("Attr"), new AttrRec(el, name));
    }

    private static AttrRec rec(Object t) {
        if (t instanceof DomObj d && d.nat instanceof AttrRec a) {
            return a;
        }
        throw Js.typeError("Illegal invocation");
    }

    static final class DomAttrs extends DomObj {
        final DomHost h;
        final Node node;

        DomAttrs(DomHost h, Node node) {
            super(h.protos.get("NamedNodeMap"), null);
            this.h = h;
            this.node = node;
        }

        List<String> names() {
            return new ArrayList<>(this.node.attrs.keySet());
        }

        @Override
        public Prop getOwn(Object key) {
            if (key instanceof String s) {
                int idx = Js.arrayIndex(s);
                if (idx >= 0) {
                    List<String> l = names();
                    return idx < l.size() ? new Prop(attrObject(this.h, this.node, l.get(idx)), Prop.E) : null;
                }
                if (s.equals("length")) {
                    return new Prop(Js.num(this.node.attrs.size()), 0);
                }
                if (this.node.hasAttr(s) && (this.proto == null || !this.proto.hasProperty(s))) {
                    return new Prop(attrObject(this.h, this.node, s), Prop.C);
                }
            }
            return super.getOwn(key);
        }

        @Override
        public List<Object> ownKeys() {
            List<Object> out = new ArrayList<>();
            int n = this.node.attrs.size();
            for (int i = 0; i < n; i++) {
                out.add(Integer.toString(i));
            }
            out.addAll(super.ownKeys());
            return out;
        }

        static DomAttrs of(Object t) {
            if (t instanceof DomAttrs d) {
                return d;
            }
            throw Js.typeError("Illegal invocation");
        }

        static void install(DomHost h) {
            Realm r = h.r;
            JsObject ap = h.iface("Attr", h.nodeProto);
            Lib.getter(r, ap, "name", (t, a) -> rec(t).name);
            Lib.getter(r, ap, "localName", (t, a) -> rec(t).name);
            Lib.getter(r, ap, "nodeName", (t, a) -> rec(t).name);
            Lib.getter(r, ap, "nodeType", (t, a) -> Js.num(2));
            Lib.getter(r, ap, "specified", (t, a) -> true);
            Lib.getter(r, ap, "namespaceURI", (t, a) -> Js.NULL);
            Lib.getter(r, ap, "prefix", (t, a) -> Js.NULL);
            Lib.getter(r, ap, "ownerElement", (t, a) -> h.w(rec(t).el));
            Lib.accessor(r, ap, "value", (t, a) -> {
                String v = rec(t).el.attr(rec(t).name);
                return v == null ? "" : v;
            }, (t, a) -> {
                rec(t).el.setAttr(rec(t).name, Js.toStr(Js.arg(a, 0)));
                Node.touch();
                return Js.UNDEFINED;
            });
            Lib.accessor(r, ap, "nodeValue", (t, a) -> {
                String v = rec(t).el.attr(rec(t).name);
                return v == null ? "" : v;
            }, (t, a) -> {
                rec(t).el.setAttr(rec(t).name, Js.toStr(Js.arg(a, 0)));
                Node.touch();
                return Js.UNDEFINED;
            });
            JsObject p = h.iface("NamedNodeMap", null);
            Lib.method(r, p, "item", 1, (t, a) -> {
                DomAttrs d = of(t);
                List<String> l = d.names();
                double i = Js.toInteger(Js.arg(a, 0));
                return i >= 0 && i < l.size() ? attrObject(h, d.node, l.get((int) i)) : Js.NULL;
            });
            Lib.method(r, p, "getNamedItem", 1, (t, a) -> {
                DomAttrs d = of(t);
                String n = Js.toStr(Js.arg(a, 0)).toLowerCase(Locale.ROOT);
                return d.node.hasAttr(n) ? attrObject(h, d.node, n) : Js.NULL;
            });
            Lib.method(r, p, "removeNamedItem", 1, (t, a) -> {
                DomAttrs d = of(t);
                String n = Js.toStr(Js.arg(a, 0)).toLowerCase(Locale.ROOT);
                if (!d.node.hasAttr(n)) {
                    throw h.domError("NotFoundError", "Failed to execute 'removeNamedItem' on 'NamedNodeMap': No item with name '" + n + "' was found.");
                }
                JsObject old = attrObject(h, d.node, n);
                d.node.removeAttr(n);
                Node.touch();
                return old;
            });
            Lib.method(r, p, "setNamedItem", 1, (t, a) -> {
                DomAttrs d = of(t);
                AttrRec ar = rec(Js.arg(a, 0));
                String v = ar.el.attr(ar.name);
                d.node.setAttr(ar.name, v == null ? "" : v);
                Node.touch();
                return Js.NULL;
            });
            p.setHidden(JsSymbol.ITERATOR, new NativeFunction(r, "values", 0, (t, a) -> {
                DomAttrs d = of(t);
                ArrayList<Object> out = new ArrayList<>();
                for (String n : d.names()) {
                    out.add(attrObject(h, d.node, n));
                }
                return iter(h, Lib.array(r, out));
            }));
        }
    }

    static Map<String, String> attrsOf(Node n) {
        return n.attrs;
    }
}
