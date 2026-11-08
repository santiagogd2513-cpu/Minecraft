package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;

/** Conversiones y operadores de JavaScript. */
public final class Js {
    private static final class Undefined {
        @Override
        public String toString() {
            return "undefined";
        }
    }

    private static final class Null {
        @Override
        public String toString() {
            return "null";
        }
    }

    public static final Object UNDEFINED = new Undefined();
    public static final Object NULL = new Null();
    public static final Object[] EMPTY = new Object[0];
    public static final Double NAN = Double.NaN;

    private Js() {
    }

    public static Double num(double d) {
        return d;
    }

    public static Object arg(Object[] a, int i) {
        return i < a.length ? a[i] : UNDEFINED;
    }

    public static boolean isNullish(Object v) {
        return v == UNDEFINED || v == NULL;
    }

    // ------------------------------------------------------------------ errores

    public static JsException typeError(String msg) {
        return Realm.cur().error("TypeError", msg);
    }

    public static JsException rangeError(String msg) {
        return Realm.cur().error("RangeError", msg);
    }

    public static JsException referenceError(String msg) {
        return Realm.cur().error("ReferenceError", msg);
    }

    public static JsException syntaxError(String msg) {
        return Realm.cur().error("SyntaxError", msg);
    }

    /** "Nombre: mensaje" de un valor lanzado. */
    public static String describeError(Object v) {
        if (v instanceof JsObject o) {
            Object stack = o.get("stack");
            Object name = o.get("name");
            Object msg = o.get("message");
            if (o.hasProperty("message") || o.hasProperty("stack")) {
                String n = name == UNDEFINED ? "Error" : toStr(name);
                String m = msg == UNDEFINED ? "" : toStr(msg);
                if (stack instanceof String && o.getOwn("stack") != null && m.isEmpty() && n.isEmpty()) {
                    return (String) stack;
                }
                return m.isEmpty() ? n : n.isEmpty() ? m : n + ": " + m;
            }
        }
        if (v instanceof JsSymbol) {
            return v.toString();
        }
        return toStr(v);
    }

    // ------------------------------------------------------------------ conversiones

    public static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Double d) {
            double x = d;
            return x != 0 && x == x;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v == UNDEFINED || v == NULL) {
            return false;
        }
        if (v instanceof Number n) {
            double x = n.doubleValue();
            return x != 0 && x == x;
        }
        return true;
    }

    public static Object toPrimitive(Object v, String hint) {
        if (!(v instanceof JsObject o)) {
            return v;
        }
        Object ex = o.get(JsSymbol.TO_PRIMITIVE);
        if (ex != UNDEFINED && ex != NULL) {
            if (!(ex instanceof JsFunction f)) {
                throw typeError("Symbol.toPrimitive is not a function");
            }
            Object r = f.call(o, new Object[] {hint});
            if (!(r instanceof JsObject)) {
                return r;
            }
            throw typeError("Cannot convert object to primitive value");
        }
        String first = hint.equals("string") ? "toString" : "valueOf";
        String second = hint.equals("string") ? "valueOf" : "toString";
        Object f = o.get(first);
        if (f instanceof JsFunction fn) {
            Object r = fn.call(o, EMPTY);
            if (!(r instanceof JsObject)) {
                return r;
            }
        }
        f = o.get(second);
        if (f instanceof JsFunction fn) {
            Object r = fn.call(o, EMPTY);
            if (!(r instanceof JsObject)) {
                return r;
            }
        }
        throw typeError("Cannot convert object to primitive value");
    }

    public static double toNumber(Object v) {
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof String s) {
            return NumFmt.parse(s);
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v == UNDEFINED) {
            return Double.NaN;
        }
        if (v == NULL) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof JsSymbol) {
            throw typeError("Cannot convert a Symbol value to a number");
        }
        return toNumber(toPrimitive(v, "number"));
    }

    public static String toStr(Object v) {
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof Double d) {
            return NumFmt.toString(d);
        }
        if (v instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (v == UNDEFINED) {
            return "undefined";
        }
        if (v == NULL) {
            return "null";
        }
        if (v instanceof Number n) {
            return NumFmt.toString(n.doubleValue());
        }
        if (v instanceof JsSymbol) {
            throw typeError("Cannot convert a Symbol value to a string");
        }
        return toStr(toPrimitive(v, "string"));
    }

    public static String numToString(double d) {
        return NumFmt.toString(d);
    }

    /** Clave de propiedad: texto o Symbol. */
    public static Object toKey(Object v) {
        if (v instanceof String || v instanceof JsSymbol || v instanceof PrivateName) {
            return v;
        }
        if (v instanceof Double d) {
            double x = d;
            int i = (int) x;
            if (i == x && i >= 0) {
                return Integer.toString(i);
            }
            return NumFmt.toString(x);
        }
        if (v instanceof JsObject) {
            Object p = toPrimitive(v, "string");
            return p instanceof JsSymbol ? p : toStr(p);
        }
        return toStr(v);
    }

    public static JsObject toObject(Realm r, Object v) {
        if (v instanceof JsObject o) {
            return o;
        }
        if (v == UNDEFINED || v == NULL) {
            throw typeError("Cannot convert undefined or null to object");
        }
        if (v instanceof String) {
            return new JsWrapper(r.stringProto, v);
        }
        if (v instanceof Boolean) {
            return new JsWrapper(r.booleanProto, v);
        }
        if (v instanceof JsSymbol) {
            return new JsWrapper(r.symbolProto, v);
        }
        return new JsWrapper(r.numberProto, v);
    }

    public static int toInt32(double d) {
        int i = (int) d;
        if (i == d) {
            return i;
        }
        if (d != d || Double.isInfinite(d)) {
            return 0;
        }
        d = d < 0 ? Math.ceil(d) : Math.floor(d);
        d = d % 4294967296.0;
        return (int) (long) d;
    }

    public static int toInt32(Object v) {
        return toInt32(toNumber(v));
    }

    public static double toUint32(double d) {
        return toInt32(d) & 0xFFFFFFFFL;
    }

    /** ToIntegerOrInfinity. */
    public static double toInteger(Object v) {
        double d = toNumber(v);
        if (d != d) {
            return 0;
        }
        if (Double.isInfinite(d)) {
            return d;
        }
        return d < 0 ? Math.ceil(d) : Math.floor(d);
    }

    /** Un índice relativo (como el de slice): negativos cuentan desde el final; el resultado queda en [0, len]. */
    public static int relIndex(Object v, int len, int dflt) {
        if (v == UNDEFINED) {
            return dflt;
        }
        double d = toInteger(v);
        if (d < 0) {
            d = Math.max(0, len + d);
        } else {
            d = Math.min(d, len);
        }
        return (int) d;
    }

    /** Si el texto es un índice de arreglo canónico (0 a 2^31-2) devuelve el número; si no, -1. */
    public static int arrayIndex(String s) {
        int n = s.length();
        if (n == 0 || n > 10) {
            return -1;
        }
        char c0 = s.charAt(0);
        if (c0 < '0' || c0 > '9' || (c0 == '0' && n > 1)) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v <= Integer.MAX_VALUE - 1 ? (int) v : -1;
    }

    public static String typeOf(Object v) {
        if (v instanceof String) {
            return "string";
        }
        if (v instanceof Double || v instanceof Number) {
            return "number";
        }
        if (v instanceof Boolean) {
            return "boolean";
        }
        if (v == UNDEFINED) {
            return "undefined";
        }
        if (v == NULL) {
            return "object";
        }
        if (v instanceof JsSymbol) {
            return "symbol";
        }
        if (v instanceof JsFunction) {
            return "function";
        }
        return "object";
    }

    // ------------------------------------------------------------------ comparaciones

    public static boolean strictEquals(Object a, Object b) {
        if (a instanceof Double x) {
            return b instanceof Double y && x.doubleValue() == y.doubleValue();
        }
        if (a == b) {
            return true;
        }
        if (a instanceof String x) {
            return b instanceof String y && x.equals(y);
        }
        if (a instanceof Boolean x) {
            return b instanceof Boolean y && x.booleanValue() == y.booleanValue();
        }
        return false;
    }

    public static boolean looseEquals(Object a, Object b) {
        if (a instanceof Double || a instanceof String || a instanceof Boolean) {
            if (b instanceof Double || b instanceof String || b instanceof Boolean) {
                if (a.getClass() == b.getClass()) {
                    return strictEquals(a, b);
                }
                return toNumber(a) == toNumber(b);
            }
        }
        if (a == b) {
            return !(a instanceof Double d) || !d.isNaN();
        }
        boolean an = isNullish(a);
        boolean bn = isNullish(b);
        if (an || bn) {
            return an && bn;
        }
        if (a instanceof JsObject && b instanceof JsObject) {
            return false;
        }
        if (a instanceof JsObject) {
            return b instanceof JsSymbol ? looseEquals(toPrimitive(a, "default"), b) : looseEquals(toPrimitive(a, "default"), b);
        }
        if (b instanceof JsObject) {
            return looseEquals(a, toPrimitive(b, "default"));
        }
        if (a instanceof JsSymbol || b instanceof JsSymbol) {
            return false;
        }
        return toNumber(a) == toNumber(b);
    }

    public static boolean sameValueZero(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            double p = x;
            double q = y;
            return p == q || (p != p && q != q);
        }
        return strictEquals(a, b);
    }

    public static boolean sameValue(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x.equals(y);
        }
        return strictEquals(a, b);
    }

    public static boolean lt(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x < y;
        }
        a = toPrimitive(a, "number");
        b = toPrimitive(b, "number");
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y) < 0;
        }
        return toNumber(a) < toNumber(b);
    }

    public static boolean gt(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x > y;
        }
        a = toPrimitive(a, "number");
        b = toPrimitive(b, "number");
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y) > 0;
        }
        return toNumber(a) > toNumber(b);
    }

    public static boolean le(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x <= y;
        }
        a = toPrimitive(a, "number");
        b = toPrimitive(b, "number");
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y) <= 0;
        }
        return toNumber(a) <= toNumber(b);
    }

    public static boolean ge(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x >= y;
        }
        a = toPrimitive(a, "number");
        b = toPrimitive(b, "number");
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y) >= 0;
        }
        return toNumber(a) >= toNumber(b);
    }

    public static Object add(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return x + y;
        }
        if (a instanceof String x && b instanceof String y) {
            return x.concat(y);
        }
        a = toPrimitive(a, "default");
        b = toPrimitive(b, "default");
        if (a instanceof String || b instanceof String) {
            return toStr(a).concat(toStr(b));
        }
        return toNumber(a) + toNumber(b);
    }

    // ------------------------------------------------------------------ acceso a propiedades

    private static String keyText(Object key) {
        return key instanceof JsSymbol ? key.toString() : String.valueOf(key);
    }

    static Object privateGet(Object base, PrivateName pn) {
        if (base instanceof JsObject o && o.findProp(pn) != null) {
            return o.get(pn, o);
        }
        throw typeError("Cannot read private member " + pn + " from an object whose class did not declare it");
    }

    public static Object getMember(Realm r, Object base, Object key) {
        if (key instanceof PrivateName pn) {
            return privateGet(base, pn);
        }
        if (base instanceof JsObject o) {
            if (key instanceof Double d && o instanceof JsArray a) {
                double x = d;
                int i = (int) x;
                if (i == x && i >= 0) {
                    return a.getIndex(i);
                }
            }
            return o.get(toKey(key), o);
        }
        if (base instanceof String s) {
            if (key instanceof Double d) {
                double x = d;
                int i = (int) x;
                if (i == x && i >= 0) {
                    return i < s.length() ? String.valueOf(s.charAt(i)) : UNDEFINED;
                }
            }
            Object k = toKey(key);
            if (k instanceof String ks) {
                if (ks.equals("length")) {
                    return num(s.length());
                }
                int i = arrayIndex(ks);
                if (i >= 0) {
                    return i < s.length() ? String.valueOf(s.charAt(i)) : UNDEFINED;
                }
            }
            return r.stringProto.get(k, base);
        }
        if (base == UNDEFINED || base == NULL) {
            if (toKey(key) == JsSymbol.ITERATOR) {
                throw typeError((base == NULL ? "object null" : "undefined") + " is not iterable (cannot read property Symbol(Symbol.iterator))");
            }
            throw typeError("Cannot read properties of " + base + " (reading '" + keyText(toKey(key)) + "')");
        }
        Object k = toKey(key);
        if (base instanceof Boolean) {
            return r.booleanProto.get(k, base);
        }
        if (base instanceof JsSymbol) {
            return r.symbolProto.get(k, base);
        }
        return r.numberProto.get(k, base);
    }

    /** Lectura con nombre fijo (a.b). */
    public static Object getProp(Realm r, Object base, String name) {
        if (base instanceof JsObject o) {
            return o.get(name, o);
        }
        return getMember(r, base, name);
    }

    public static void setMember(Realm r, Object base, Object key, Object value, boolean strict) {
        if (key instanceof PrivateName pn) {
            if (!(base instanceof JsObject po) || po.findProp(pn) == null) {
                throw typeError("Cannot write private member " + pn + " to an object whose class did not declare it");
            }
            po.set(pn, value, po);
            return;
        }
        if (base instanceof JsObject o) {
            if (key instanceof Double d && o instanceof JsArray a) {
                double x = d;
                int i = (int) x;
                if (i == x && i >= 0) {
                    if (!a.setIndex(i, value) && strict) {
                        throw typeError("Cannot assign to read only property '" + i + "' of object '[object Array]'");
                    }
                    return;
                }
            }
            Object k = toKey(key);
            if (!o.set(k, value, o) && strict) {
                throw typeError("Cannot assign to read only property '" + keyText(k) + "' of object '" + objectLabel(o) + "'");
            }
            return;
        }
        if (base == UNDEFINED || base == NULL) {
            throw typeError("Cannot set properties of " + base + " (setting '" + keyText(toKey(key)) + "')");
        }
        // sobre un primitivo: solo funcionan los setters del prototipo
        Object k = toKey(key);
        JsObject proto = base instanceof String ? r.stringProto : base instanceof Boolean ? r.booleanProto
                : base instanceof JsSymbol ? r.symbolProto : r.numberProto;
        Prop p = proto.findProp(k);
        if (p != null && p.accessor && p.setter != null) {
            p.setter.call(base, new Object[] {value});
            return;
        }
        if (strict) {
            throw typeError("Cannot create property '" + keyText(k) + "' on " + typeOf(base) + " '" + toStr(base) + "'");
        }
    }

    static String objectLabel(JsObject o) {
        if (o instanceof JsFunction) {
            return "function";
        }
        return o instanceof JsArray ? "[object Array]" : "#<Object>";
    }

    public static boolean deleteMember(Realm r, Object base, Object key, boolean strict) {
        if (base instanceof JsObject o) {
            boolean ok = o.delete(toKey(key));
            if (!ok && strict) {
                throw typeError("Cannot delete property '" + keyText(toKey(key)) + "' of " + objectLabel(o));
            }
            return ok;
        }
        if (base == UNDEFINED || base == NULL) {
            throw typeError("Cannot convert undefined or null to object");
        }
        return true;
    }

    public static boolean hasProperty(Object obj, Object key) {
        if (!(obj instanceof JsObject o)) {
            throw typeError("Cannot use 'in' operator to search for '" + keyText(toKey(key)) + "' in " + toStr(obj));
        }
        return o.hasProperty(toKey(key));
    }

    // ------------------------------------------------------------------ llamadas

    public static Object call(Object f, Object thiz, Object... args) {
        if (f instanceof JsFunction fn) {
            return fn.call(thiz, args);
        }
        throw typeError(display(f) + " is not a function");
    }

    public static Object construct(Object f, Object... args) {
        if (f instanceof JsFunction fn && fn.isConstructor()) {
            return fn.construct(args, fn);
        }
        throw typeError(display(f) + " is not a constructor");
    }

    /** Texto corto de un valor para los mensajes de error. */
    public static String display(Object v) {
        if (v instanceof String s) {
            return "\"" + s + "\"";
        }
        if (v instanceof JsFunction f) {
            return f.fname.isEmpty() ? "function" : f.fname;
        }
        if (v instanceof JsArray) {
            return "[object Array]";
        }
        if (v instanceof JsObject) {
            return "#<Object>";
        }
        if (v instanceof JsSymbol) {
            return v.toString();
        }
        return toStr(v);
    }

    public static boolean instanceOf(Object v, Object target) {
        if (!(target instanceof JsObject t)) {
            throw typeError("Right-hand side of 'instanceof' is not an object");
        }
        Object h = t.get(JsSymbol.HAS_INSTANCE);
        if (h instanceof JsFunction hf) {
            return truthy(hf.call(t, new Object[] {v}));
        }
        if (!(target instanceof JsFunction f)) {
            throw typeError("Right-hand side of 'instanceof' is not callable");
        }
        return ordinaryHasInstance(f, v);
    }

    public static boolean ordinaryHasInstance(JsFunction f, Object v) {
        if (f instanceof BoundFunction b) {
            return instanceOf(v, b.target);
        }
        if (!(v instanceof JsObject o)) {
            return false;
        }
        Object p = f.get("prototype");
        if (!(p instanceof JsObject proto)) {
            throw typeError("Function has non-object prototype '" + display(p) + "' in instanceof check");
        }
        for (JsObject x = o.proto; x != null; x = x.proto) {
            if (x == proto) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ iteración

    /** El iterador de un valor iterable (con atajos para arreglos y textos). */
    /** Mensaje que da V8 cuando una función interna recibe algo que no es iterable. */
    private static String notIterableLibrary(Object v) {
        String t;
        if (v == UNDEFINED) {
            t = "undefined";
        } else if (v == NULL) {
            t = "object null";
        } else if (v instanceof JsFunction) {
            t = "function";
        } else if (v instanceof JsObject) {
            t = "object";
        } else {
            t = typeOf(v) + " " + display(v);
        }
        return t + " is not iterable (cannot read property Symbol(Symbol.iterator))";
    }

    public static IterRec iterate(Object iterable) {
        return iterate(iterable, null);
    }

    /** Como {@link #iterate(Object)}; {@code label} es cómo nombrar el valor en el error (el texto del código). */
    public static IterRec iterate(Object iterable, String label) {
        Realm r = Realm.cur();
        if (iterable instanceof JsArray a && a.get(JsSymbol.ITERATOR) == r.arrayValues) {
            return new IterRec() {
                private int i;

                @Override
                public Object next() {
                    if (this.i >= a.list.size()) {
                        return DONE;
                    }
                    return a.getIndex(this.i++);
                }
            };
        }
        if (iterable instanceof String s) {
            return new IterRec() {
                private int i;

                @Override
                public Object next() {
                    if (this.i >= s.length()) {
                        return DONE;
                    }
                    int cp = s.codePointAt(this.i);
                    int n = Character.charCount(cp);
                    String out = s.substring(this.i, this.i + n);
                    this.i += n;
                    return out;
                }
            };
        }
        if (isNullish(iterable)) {
            throw typeError(label != null ? label + " is not iterable" : notIterableLibrary(iterable));
        }
        Object m = getMember(r, iterable, JsSymbol.ITERATOR);
        if (!(m instanceof JsFunction mf)) {
            throw typeError(label != null ? label + " is not iterable" : notIterableLibrary(iterable));
        }
        Object it = mf.call(iterable, EMPTY);
        if (!(it instanceof JsObject io)) {
            throw typeError("Result of the Symbol.iterator method is not an object");
        }
        Object next = io.get("next");
        return new IterRec() {
            private boolean done;

            @Override
            public Object next() {
                if (this.done) {
                    return DONE;
                }
                Object res;
                try {
                    res = call(next, io);
                } catch (RuntimeException e) {
                    this.done = true;
                    throw e;
                }
                if (!(res instanceof JsObject ro)) {
                    this.done = true;
                    throw typeError("Iterator result " + display(res) + " is not an object");
                }
                if (truthy(ro.get("done"))) {
                    this.done = true;
                    return DONE;
                }
                return ro.get("value");
            }

            @Override
            public void close() {
                if (this.done) {
                    return;
                }
                this.done = true;
                Object ret = io.get("return");
                if (ret instanceof JsFunction rf) {
                    rf.call(io, EMPTY);
                }
            }
        };
    }

    public static ArrayList<Object> toList(Object iterable) {
        ArrayList<Object> out = new ArrayList<>();
        IterRec it = iterate(iterable);
        for (Object v = it.next(); v != IterRec.DONE; v = it.next()) {
            out.add(v);
        }
        return out;
    }

    // ------------------------------------------------------------------ objetos nuevos

    /** Copia las propiedades propias enumerables de {@code src} en {@code target} (para {...obj} y el resto de un patrón). */
    static void copyDataProps(Realm r, JsObject target, Object src, java.util.Set<Object> exclude) {
        if (src == UNDEFINED || src == NULL) {
            return;
        }
        JsObject from = toObject(r, src);
        for (Object k : from.ownKeys()) {
            if (exclude != null && exclude.contains(k)) {
                continue;
            }
            Prop p = from.getOwn(k);
            if (p == null || !p.enumerable()) {
                continue;
            }
            target.defineOwn(k, new Prop(from.get(k), Prop.ALL));
        }
    }

    /** Las claves que recorre {@code for (k in obj)}. */
    static java.util.List<String> forInKeys(JsObject o) {
        ArrayList<String> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (JsObject x = o; x != null; x = x.proto) {
            for (Object k : x.ownKeys()) {
                if (!(k instanceof String s) || !seen.add(s)) {
                    continue;
                }
                Prop p = x.getOwn(s);
                if (p != null && p.enumerable()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static JsObject newObject() {
        return new JsObject(Realm.cur().objectProto);
    }

    public static JsArray newArray() {
        return new JsArray(Realm.cur().arrayProto);
    }

    public static JsArray newArray(java.util.List<?> items) {
        return new JsArray(Realm.cur().arrayProto, new ArrayList<Object>(items));
    }

    /** Resultado de iterador {@code {value, done}}. */
    public static JsObject iterResult(Object value, boolean done) {
        JsObject o = newObject();
        o.setData("value", value);
        o.setData("done", done);
        return o;
    }
}
