package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;

/** Map, Set, WeakMap, WeakSet, WeakRef. */
final class LibCollections {
    private LibCollections() {
    }

    private static JsMap thisMap(Object t, String m) {
        if (t instanceof JsMap x) {
            return x;
        }
        throw Js.typeError("Method Map.prototype." + m + " called on incompatible receiver " + Js.display(t));
    }

    private static JsSet thisSet(Object t, String m) {
        if (t instanceof JsSet x) {
            return x;
        }
        throw Js.typeError("Method Set.prototype." + m + " called on incompatible receiver " + Js.display(t));
    }

    private static JsWeak thisWeak(Object t, String kind, String m) {
        if (t instanceof JsWeak w && w.kind.equals(kind)) {
            return w;
        }
        throw Js.typeError("Method " + kind + ".prototype." + m + " called on incompatible receiver " + Js.display(t));
    }

    private static JsObject iterProto(Realm r, String tag) {
        JsObject p = r.collectionIterProtos.get(tag);
        if (p == null) {
            p = LibIter.makeProto(r, tag);
            r.collectionIterProtos.put(tag, p);
        }
        return p;
    }

    /** kind: 0 claves, 1 valores, 2 pares. */
    private static JsIterObject iter(Realm r, OrderedTable table, int kind, String tag) {
        OrderedTable.Cursor c = table.cursor();
        IterRec it = new IterRec() {
            @Override
            public Object next() {
                OrderedTable.Entry e = c.next();
                if (e == null) {
                    return DONE;
                }
                if (kind == 0) {
                    return e.key;
                }
                if (kind == 1) {
                    return e.value;
                }
                ArrayList<Object> pair = new ArrayList<>(2);
                pair.add(e.key);
                pair.add(e.value);
                return new JsArray(r.arrayProto, pair);
            }
        };
        JsIterObject io = new JsIterObject(iterProto(r, tag), it, tag);
        setPreview(io, c, kind, r);
        return io;
    }

    private static void setPreview(JsIterObject io, OrderedTable.Cursor c, int kind, Realm r) {
        io.pairs = kind == 2;
        io.preview = () -> {
            OrderedTable.Cursor cc = c.copy();
            ArrayList<Object> out = new ArrayList<>();
            for (OrderedTable.Entry e = cc.next(); e != null; e = cc.next()) {
                if (kind == 0) {
                    out.add(e.key);
                } else if (kind == 1) {
                    out.add(e.value);
                } else {
                    ArrayList<Object> pair = new ArrayList<>(2);
                    pair.add(e.key);
                    pair.add(e.value);
                    out.add(new JsArray(r.arrayProto, pair));
                }
            }
            return out;
        };
    }

    static void install(Realm r) {
        installMap(r);
        installSet(r);
        installWeak(r);
    }

    private static void installMap(Realm r) {
        JsObject mp = r.mapProto;
        NativeFunction mc = Lib.ctor(r, "Map", 0, mp, (t, a) -> {
            throw Js.typeError("Constructor Map requires 'new'");
        }, (a, nt) -> {
            JsMap m = new JsMap(Lib.protoFrom(nt, mp));
            Object src = Js.arg(a, 0);
            if (!Js.isNullish(src)) {
                Object adder = m.get("set");
                JsFunction add = Lib.callable(adder, "set");
                IterRec it = Js.iterate(src);
                try {
                    for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                        if (!(e instanceof JsObject eo)) {
                            throw Js.typeError("Iterator value " + Js.display(e) + " is not an entry object");
                        }
                        add.call(m, new Object[] {eo.get("0"), eo.get("1")});
                    }
                } catch (JsException ex) {
                    it.close();
                    throw ex;
                }
            }
            return m;
        });
        mc.defineOwn(JsSymbol.SPECIES, new Prop(mc, null, Prop.C));
        Lib.method(r, mc, "groupBy", 2, (t, a) -> {
            JsFunction f = Lib.callable(Js.arg(a, 1), "callback");
            JsMap m = new JsMap(mp);
            IterRec it = Js.iterate(Js.arg(a, 0));
            double i = 0;
            for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                Object k = f.call(Js.UNDEFINED, new Object[] {e, Js.num(i++)});
                OrderedTable.Entry en = m.table.find(k);
                if (en == null) {
                    m.table.put(k, new JsArray(r.arrayProto, new ArrayList<Object>()));
                    en = m.table.find(k);
                }
                ((JsArray) en.value).add(e);
            }
            return m;
        });
        Lib.method(r, mp, "get", 1, (t, a) -> {
            OrderedTable.Entry e = thisMap(t, "get").table.find(Js.arg(a, 0));
            return e == null ? Js.UNDEFINED : e.value;
        });
        Lib.method(r, mp, "set", 2, (t, a) -> {
            thisMap(t, "set").table.put(Js.arg(a, 0), Js.arg(a, 1));
            return t;
        });
        Lib.method(r, mp, "has", 1, (t, a) -> thisMap(t, "has").table.find(Js.arg(a, 0)) != null);
        Lib.method(r, mp, "delete", 1, (t, a) -> thisMap(t, "delete").table.remove(Js.arg(a, 0)));
        Lib.method(r, mp, "clear", 0, (t, a) -> {
            thisMap(t, "clear").table.clear();
            return Js.UNDEFINED;
        });
        Lib.getter(r, mp, "size", (t, a) -> Js.num(thisMap(t, "size").table.size()));
        Lib.method(r, mp, "forEach", 1, (t, a) -> {
            JsMap m = thisMap(t, "forEach");
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            OrderedTable.Cursor c = m.table.cursor();
            for (OrderedTable.Entry e = c.next(); e != null; e = c.next()) {
                f.call(Js.arg(a, 1), new Object[] {e.value, e.key, m});
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, mp, "keys", 0, (t, a) -> iter(r, thisMap(t, "keys").table, 0, "Map Iterator"));
        Lib.method(r, mp, "values", 0, (t, a) -> iter(r, thisMap(t, "values").table, 1, "Map Iterator"));
        NativeFunction entries = new NativeFunction(r, "entries", 0, (t, a) -> iter(r, thisMap(t, "entries").table, 2, "Map Iterator"));
        mp.setHidden("entries", entries);
        mp.setHidden(JsSymbol.ITERATOR, entries);
        Lib.tag(mp, "Map");
    }

    private static boolean setLikeHas(Object other, Object v) {
        if (other instanceof JsSet s) {
            return s.table.find(v) != null;
        }
        Object has = Js.getMember(Realm.cur(), other, "has");
        return Js.truthy(Lib.callable(has, "has").call(other, new Object[] {v}));
    }

    private static ArrayList<Object> setLikeKeys(Object other) {
        if (other instanceof JsSet s) {
            ArrayList<Object> out = new ArrayList<>();
            OrderedTable.Cursor c = s.table.cursor();
            for (OrderedTable.Entry e = c.next(); e != null; e = c.next()) {
                out.add(e.key);
            }
            return out;
        }
        Object keys = Js.getMember(Realm.cur(), other, "keys");
        Object itObj = Lib.callable(keys, "keys").call(other, Js.EMPTY);
        Object next = Js.getMember(Realm.cur(), itObj, "next");
        ArrayList<Object> out = new ArrayList<>();
        while (true) {
            Object res = Lib.callable(next, "next").call(itObj, Js.EMPTY);
            if (Js.truthy(Js.getMember(Realm.cur(), res, "done"))) {
                return out;
            }
            out.add(Js.getMember(Realm.cur(), res, "value"));
        }
    }

    private static void installSet(Realm r) {
        JsObject sp = r.setProto;
        NativeFunction sc = Lib.ctor(r, "Set", 0, sp, (t, a) -> {
            throw Js.typeError("Constructor Set requires 'new'");
        }, (a, nt) -> {
            JsSet s = new JsSet(Lib.protoFrom(nt, sp));
            Object src = Js.arg(a, 0);
            if (!Js.isNullish(src)) {
                JsFunction add = Lib.callable(s.get("add"), "add");
                IterRec it = Js.iterate(src);
                try {
                    for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                        add.call(s, new Object[] {e});
                    }
                } catch (JsException ex) {
                    it.close();
                    throw ex;
                }
            }
            return s;
        });
        sc.defineOwn(JsSymbol.SPECIES, new Prop(sc, null, Prop.C));
        Lib.method(r, sp, "add", 1, (t, a) -> {
            JsSet s = thisSet(t, "add");
            if (s.table.find(Js.arg(a, 0)) == null) {
                s.table.put(Js.arg(a, 0), Js.arg(a, 0));
            }
            return t;
        });
        Lib.method(r, sp, "has", 1, (t, a) -> thisSet(t, "has").table.find(Js.arg(a, 0)) != null);
        Lib.method(r, sp, "delete", 1, (t, a) -> thisSet(t, "delete").table.remove(Js.arg(a, 0)));
        Lib.method(r, sp, "clear", 0, (t, a) -> {
            thisSet(t, "clear").table.clear();
            return Js.UNDEFINED;
        });
        Lib.getter(r, sp, "size", (t, a) -> Js.num(thisSet(t, "size").table.size()));
        Lib.method(r, sp, "forEach", 1, (t, a) -> {
            JsSet s = thisSet(t, "forEach");
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            OrderedTable.Cursor c = s.table.cursor();
            for (OrderedTable.Entry e = c.next(); e != null; e = c.next()) {
                f.call(Js.arg(a, 1), new Object[] {e.key, e.key, s});
            }
            return Js.UNDEFINED;
        });
        NativeFunction values = new NativeFunction(r, "values", 0, (t, a) -> iter(r, thisSet(t, "values").table, 0, "Set Iterator"));
        sp.setHidden("values", values);
        sp.setHidden("keys", values);
        sp.setHidden(JsSymbol.ITERATOR, values);
        Lib.method(r, sp, "entries", 0, (t, a) -> {
            JsSet s = thisSet(t, "entries");
            OrderedTable.Cursor c = s.table.cursor();
            IterRec it = new IterRec() {
                @Override
                public Object next() {
                    OrderedTable.Entry e = c.next();
                    if (e == null) {
                        return DONE;
                    }
                    ArrayList<Object> pair = new ArrayList<>(2);
                    pair.add(e.key);
                    pair.add(e.key);
                    return new JsArray(r.arrayProto, pair);
                }
            };
            JsIterObject io = new JsIterObject(iterProto(r, "Set Iterator"), it, "Set Iterator");
            io.pairs = true;
            io.preview = () -> {
                OrderedTable.Cursor cc = c.copy();
                ArrayList<Object> out = new ArrayList<>();
                for (OrderedTable.Entry e = cc.next(); e != null; e = cc.next()) {
                    ArrayList<Object> pair = new ArrayList<>(2);
                    pair.add(e.key);
                    pair.add(e.key);
                    out.add(new JsArray(r.arrayProto, pair));
                }
                return out;
            };
            return io;
        });
        Lib.method(r, sp, "union", 1, (t, a) -> {
            JsSet s = thisSet(t, "union");
            JsSet out = new JsSet(sp);
            for (Object k : setLikeKeys(s)) {
                out.table.put(k, k);
            }
            for (Object k : setLikeKeys(Js.arg(a, 0))) {
                out.table.put(k, k);
            }
            return out;
        });
        Lib.method(r, sp, "intersection", 1, (t, a) -> {
            JsSet s = thisSet(t, "intersection");
            JsSet out = new JsSet(sp);
            for (Object k : setLikeKeys(s)) {
                if (setLikeHas(Js.arg(a, 0), k)) {
                    out.table.put(k, k);
                }
            }
            return out;
        });
        Lib.method(r, sp, "difference", 1, (t, a) -> {
            JsSet s = thisSet(t, "difference");
            JsSet out = new JsSet(sp);
            for (Object k : setLikeKeys(s)) {
                if (!setLikeHas(Js.arg(a, 0), k)) {
                    out.table.put(k, k);
                }
            }
            return out;
        });
        Lib.method(r, sp, "symmetricDifference", 1, (t, a) -> {
            JsSet s = thisSet(t, "symmetricDifference");
            JsSet out = new JsSet(sp);
            for (Object k : setLikeKeys(s)) {
                if (!setLikeHas(Js.arg(a, 0), k)) {
                    out.table.put(k, k);
                }
            }
            for (Object k : setLikeKeys(Js.arg(a, 0))) {
                if (s.table.find(k) == null) {
                    out.table.put(k, k);
                }
            }
            return out;
        });
        Lib.method(r, sp, "isSubsetOf", 1, (t, a) -> {
            for (Object k : setLikeKeys(thisSet(t, "isSubsetOf"))) {
                if (!setLikeHas(Js.arg(a, 0), k)) {
                    return false;
                }
            }
            return true;
        });
        Lib.method(r, sp, "isSupersetOf", 1, (t, a) -> {
            JsSet s = thisSet(t, "isSupersetOf");
            for (Object k : setLikeKeys(Js.arg(a, 0))) {
                if (s.table.find(k) == null) {
                    return false;
                }
            }
            return true;
        });
        Lib.method(r, sp, "isDisjointFrom", 1, (t, a) -> {
            for (Object k : setLikeKeys(thisSet(t, "isDisjointFrom"))) {
                if (setLikeHas(Js.arg(a, 0), k)) {
                    return false;
                }
            }
            return true;
        });
        Lib.tag(sp, "Set");
    }

    private static void installWeak(Realm r) {
        JsObject wm = r.weakMapProto;
        Lib.ctor(r, "WeakMap", 0, wm, (t, a) -> {
            throw Js.typeError("Constructor WeakMap requires 'new'");
        }, (a, nt) -> {
            JsWeak w = new JsWeak(Lib.protoFrom(nt, wm), "WeakMap");
            Object src = Js.arg(a, 0);
            if (!Js.isNullish(src)) {
                JsFunction add = Lib.callable(w.get("set"), "set");
                IterRec it = Js.iterate(src);
                for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                    if (!(e instanceof JsObject eo)) {
                        throw Js.typeError("Iterator value " + Js.display(e) + " is not an entry object");
                    }
                    add.call(w, new Object[] {eo.get("0"), eo.get("1")});
                }
            }
            return w;
        });
        Lib.method(r, wm, "get", 1, (t, a) -> {
            Object v = thisWeak(t, "WeakMap", "get").map.get(Js.arg(a, 0));
            return v == null ? Js.UNDEFINED : v;
        });
        Lib.method(r, wm, "set", 2, (t, a) -> {
            JsWeak w = thisWeak(t, "WeakMap", "set");
            if (!(Js.arg(a, 0) instanceof JsObject)) {
                throw Js.typeError("Invalid value used as weak map key");
            }
            w.map.put(a[0], Js.arg(a, 1));
            return t;
        });
        Lib.method(r, wm, "has", 1, (t, a) -> thisWeak(t, "WeakMap", "has").map.containsKey(Js.arg(a, 0)));
        Lib.method(r, wm, "delete", 1, (t, a) -> {
            JsWeak w = thisWeak(t, "WeakMap", "delete");
            boolean had = w.map.containsKey(Js.arg(a, 0));
            w.map.remove(Js.arg(a, 0));
            return had;
        });
        Lib.tag(wm, "WeakMap");

        JsObject ws = r.weakSetProto;
        Lib.ctor(r, "WeakSet", 0, ws, (t, a) -> {
            throw Js.typeError("Constructor WeakSet requires 'new'");
        }, (a, nt) -> {
            JsWeak w = new JsWeak(Lib.protoFrom(nt, ws), "WeakSet");
            Object src = Js.arg(a, 0);
            if (!Js.isNullish(src)) {
                JsFunction add = Lib.callable(w.get("add"), "add");
                IterRec it = Js.iterate(src);
                for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                    add.call(w, new Object[] {e});
                }
            }
            return w;
        });
        Lib.method(r, ws, "add", 1, (t, a) -> {
            JsWeak w = thisWeak(t, "WeakSet", "add");
            if (!(Js.arg(a, 0) instanceof JsObject)) {
                throw Js.typeError("Invalid value used in weak set");
            }
            w.map.put(a[0], Boolean.TRUE);
            return t;
        });
        Lib.method(r, ws, "has", 1, (t, a) -> thisWeak(t, "WeakSet", "has").map.containsKey(Js.arg(a, 0)));
        Lib.method(r, ws, "delete", 1, (t, a) -> {
            JsWeak w = thisWeak(t, "WeakSet", "delete");
            boolean had = w.map.containsKey(Js.arg(a, 0));
            w.map.remove(Js.arg(a, 0));
            return had;
        });
        Lib.tag(ws, "WeakSet");

        JsObject wr = new JsObject(r.objectProto);
        Lib.ctor(r, "WeakRef", 1, wr, (t, a) -> {
            throw Js.typeError("Constructor WeakRef requires 'new'");
        }, (a, nt) -> {
            if (!(Js.arg(a, 0) instanceof JsObject)) {
                throw Js.typeError("WeakRef: target must be an object");
            }
            JsWeak w = new JsWeak(Lib.protoFrom(nt, wr), "WeakRef");
            w.target = a[0];
            return w;
        });
        Lib.method(r, wr, "deref", 0, (t, a) -> thisWeak(t, "WeakRef", "deref").target);
        Lib.tag(wr, "WeakRef");

        JsObject fr = new JsObject(r.objectProto);
        Lib.ctor(r, "FinalizationRegistry", 1, fr, (t, a) -> {
            throw Js.typeError("Constructor FinalizationRegistry requires 'new'");
        }, (a, nt) -> new JsWeak(Lib.protoFrom(nt, fr), "FinalizationRegistry"));
        Lib.method(r, fr, "register", 2, (t, a) -> Js.UNDEFINED);
        Lib.method(r, fr, "unregister", 1, (t, a) -> false);
        Lib.tag(fr, "FinalizationRegistry");
    }
}
