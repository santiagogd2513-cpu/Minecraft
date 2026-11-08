package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Ayudas para definir la librería estándar. */
final class Lib {
    private Lib() {
    }

    static NativeFunction fn(Realm r, String name, int len, NativeFunction.Fn f) {
        return new NativeFunction(r, name, len, f);
    }

    /** Método oculto (no enumerable) con nombre de texto. */
    static void method(Realm r, JsObject target, String name, int len, NativeFunction.Fn f) {
        target.setHidden(name, new NativeFunction(r, name, len, f));
    }

    static void method(Realm r, JsObject target, JsSymbol sym, String name, int len, NativeFunction.Fn f) {
        target.setHidden(sym, new NativeFunction(r, name, len, f));
    }

    static void getter(Realm r, JsObject target, Object key, NativeFunction.Fn f) {
        String n = key instanceof JsSymbol s ? "[" + s.description + "]" : String.valueOf(key);
        target.defineOwn(key, new Prop(new NativeFunction(r, "get " + n, 0, f), null, Prop.C));
    }

    static void accessor(Realm r, JsObject target, Object key, NativeFunction.Fn get, NativeFunction.Fn set) {
        String n = String.valueOf(key);
        target.defineOwn(key, new Prop(new NativeFunction(r, "get " + n, 0, get), new NativeFunction(r, "set " + n, 1, set), Prop.C));
    }

    /** Crea un constructor: enlaza prototype/constructor y lo publica como variable global. */
    static NativeFunction ctor(Realm r, String name, int len, JsObject proto, NativeFunction.Fn call, NativeFunction.Ctor construct) {
        NativeFunction c = new NativeFunction(r, name, len, call, construct);
        c.setConst("prototype", proto);
        proto.setHidden("constructor", c);
        r.global.setHidden(name, c);
        return c;
    }

    static JsObject protoFrom(JsObject newTarget, JsObject dflt) {
        if (newTarget != null) {
            Object p = newTarget.get("prototype");
            if (p instanceof JsObject po) {
                return po;
            }
        }
        return dflt;
    }

    static void tag(JsObject o, String name) {
        o.defineOwn(JsSymbol.TO_STRING_TAG, new Prop(name, Prop.C));
    }

    /** Los elementos de un objeto "como arreglo" (con length). */
    static List<Object> listFromArrayLike(Object o) {
        if (o == Js.UNDEFINED || o == Js.NULL) {
            return new ArrayList<>();
        }
        if (o instanceof JsArray a) {
            ArrayList<Object> out = new ArrayList<>(a.list.size());
            for (int i = 0; i < a.list.size(); i++) {
                out.add(a.getIndex(i));
            }
            return out;
        }
        if (!(o instanceof JsObject jo)) {
            throw Js.typeError("CreateListFromArrayLike called on non-object");
        }
        long n = toLength(jo.get("length"));
        ArrayList<Object> out = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            out.add(jo.get(Long.toString(i)));
        }
        return out;
    }

    static long toLength(Object v) {
        double d = Js.toInteger(v);
        if (d <= 0) {
            return 0;
        }
        return (long) Math.min(d, 9007199254740991.0);
    }

    static JsFunction callable(Object f, String what) {
        if (f instanceof JsFunction fn) {
            return fn;
        }
        throw Js.typeError(Js.display(f) + " is not a function" + (what == null ? "" : " (" + what + ")"));
    }

    static JsArray array(Realm r, List<?> items) {
        return new JsArray(r.arrayProto, new ArrayList<Object>(items));
    }
}
