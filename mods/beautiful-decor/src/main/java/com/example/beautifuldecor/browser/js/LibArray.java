package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Array. */
final class LibArray {
    private static final long MAX_LEN = 50_000_000L;

    private LibArray() {
    }

    /** El arreglo sobre el que actúa un método: el mismo si ya es arreglo; si no, una copia del objeto "como arreglo". */
    static JsArray thisArray(Realm r, Object t) {
        if (t instanceof JsArray a) {
            return a;
        }
        if (t == Js.UNDEFINED || t == Js.NULL) {
            throw Js.typeError("Array.prototype method called on null or undefined");
        }
        if (t instanceof String s) {
            ArrayList<Object> l = new ArrayList<>(s.length());
            for (int i = 0; i < s.length(); i++) {
                l.add(String.valueOf(s.charAt(i)));
            }
            return new JsArray(r.arrayProto, l);
        }
        JsObject o = Js.toObject(r, t);
        long n = Lib.toLength(o.get("length"));
        if (n > MAX_LEN) {
            throw Js.rangeError("Invalid array length");
        }
        ArrayList<Object> l = new ArrayList<>((int) n);
        for (long i = 0; i < n; i++) {
            String k = Long.toString(i);
            l.add(o.hasProperty(k) ? o.get(k) : null);
        }
        return new JsArray(r.arrayProto, l);
    }

    private static JsArray mutable(Object t, String name) {
        if (t instanceof JsArray a) {
            if (a.frozen) {
                throw Js.typeError("Cannot assign to read only property '0' of object '[object Array]'");
            }
            return a;
        }
        throw Js.typeError("Array.prototype." + name + " solo funciona con arreglos de verdad");
    }

    private static Object create(Realm r, JsObject proto, Object[] a) {
        JsArray arr = new JsArray(proto);
        if (a.length == 1 && a[0] instanceof Double d) {
            double x = d;
            if (x != Math.floor(x) || x < 0 || x > 4294967295.0) {
                throw Js.rangeError("Invalid array length");
            }
            arr.setLength(d);
            return arr;
        }
        for (Object v : a) {
            arr.add(v);
        }
        return arr;
    }

    static void install(Realm r) {
        JsObject ap = r.arrayProto;
        NativeFunction array = Lib.ctor(r, "Array", 1, ap, (t, a) -> create(r, ap, a), (a, nt) -> create(r, Lib.protoFrom(nt, ap), a));
        Lib.method(r, array, "isArray", 1, (t, a) -> Js.arg(a, 0) instanceof JsArray && !(Js.arg(a, 0) instanceof JsArguments));
        Lib.method(r, array, "of", 0, (t, a) -> {
            JsArray out = new JsArray(r.arrayProto);
            for (Object v : a) {
                out.add(v);
            }
            return out;
        });
        Lib.method(r, array, "from", 1, (t, a) -> {
            Object items = Js.arg(a, 0);
            Object mapFn = Js.arg(a, 1);
            Object thisArg = Js.arg(a, 2);
            JsFunction mf = mapFn == Js.UNDEFINED ? null : Lib.callable(mapFn, null);
            ArrayList<Object> out = new ArrayList<>();
            if (Js.isNullish(items)) {
                throw Js.typeError(items + " is not iterable");
            }
            boolean iterable = items instanceof String || (items instanceof JsObject io && io.get(JsSymbol.ITERATOR) instanceof JsFunction);
            if (iterable) {
                IterRec it = Js.iterate(items);
                int i = 0;
                for (Object v = it.next(); v != IterRec.DONE; v = it.next()) {
                    out.add(mf == null ? v : mf.call(thisArg, new Object[] {v, Js.num(i)}));
                    i++;
                }
            } else {
                JsObject o = Js.toObject(r, items);
                long n = Lib.toLength(o.get("length"));
                if (n > MAX_LEN) {
                    throw Js.rangeError("Invalid array length");
                }
                for (long i = 0; i < n; i++) {
                    Object v = o.get(Long.toString(i));
                    out.add(mf == null ? v : mf.call(thisArg, new Object[] {v, Js.num(i)}));
                }
            }
            return new JsArray(r.arrayProto, out);
        });
        array.defineOwn(JsSymbol.SPECIES, new Prop(array, null, Prop.C));
        installProto(r, ap);
    }

    // ------------------------------------------------------------------ ordenar

    private static final class SortKey {
        final String s;
        final Object v;

        SortKey(String s, Object v) {
            this.s = s;
            this.v = v;
        }
    }

    /** Ordenación estable que tolera comparadores incoherentes (como {@code () => Math.random() - 0.5}). */
    static void mergeSort(Object[] a, Comparator<Object> c) {
        if (a.length < 2) {
            return;
        }
        Object[] tmp = new Object[a.length];
        sortRange(a, tmp, 0, a.length, c);
    }

    private static void sortRange(Object[] a, Object[] tmp, int lo, int hi, Comparator<Object> c) {
        if (hi - lo < 2) {
            return;
        }
        if (hi - lo <= 8) {
            for (int i = lo + 1; i < hi; i++) {
                Object x = a[i];
                int j = i - 1;
                while (j >= lo && c.compare(a[j], x) > 0) {
                    a[j + 1] = a[j];
                    j--;
                }
                a[j + 1] = x;
            }
            return;
        }
        int mid = (lo + hi) >>> 1;
        sortRange(a, tmp, lo, mid, c);
        sortRange(a, tmp, mid, hi, c);
        if (c.compare(a[mid - 1], a[mid]) <= 0) {
            return;
        }
        System.arraycopy(a, lo, tmp, lo, hi - lo);
        int i = lo;
        int j = mid;
        int k = lo;
        while (i < mid && j < hi) {
            if (c.compare(tmp[j], tmp[i]) < 0) {
                a[k++] = tmp[j++];
            } else {
                a[k++] = tmp[i++];
            }
        }
        while (i < mid) {
            a[k++] = tmp[i++];
        }
        while (j < hi) {
            a[k++] = tmp[j++];
        }
    }

    /** Devuelve los elementos ordenados (sin huecos ni undefined; esos van al final y los añade quien llama). */
    static List<Object> sorted(List<Object> items, Object cmp) {
        ArrayList<Object> defined = new ArrayList<>();
        int undef = 0;
        for (Object v : items) {
            if (v == null) {
                continue;
            }
            if (v == Js.UNDEFINED) {
                undef++;
            } else {
                defined.add(v);
            }
        }
        Object[] arr;
        if (cmp == Js.UNDEFINED) {
            arr = new Object[defined.size()];
            for (int i = 0; i < arr.length; i++) {
                Object v = defined.get(i);
                arr[i] = new SortKey(Js.toStr(v), v);
            }
            mergeSort(arr, (x, y) -> ((SortKey) x).s.compareTo(((SortKey) y).s));
            for (int i = 0; i < arr.length; i++) {
                arr[i] = ((SortKey) arr[i]).v;
            }
        } else {
            JsFunction f = Lib.callable(cmp, "The comparison function must be either a function or undefined");
            arr = defined.toArray();
            mergeSort(arr, (x, y) -> {
                double d = Js.toNumber(f.call(Js.UNDEFINED, new Object[] {x, y}));
                return d < 0 ? -1 : d > 0 ? 1 : 0;
            });
        }
        ArrayList<Object> out = new ArrayList<>(items.size());
        for (Object v : arr) {
            out.add(v);
        }
        for (int i = 0; i < undef; i++) {
            out.add(Js.UNDEFINED);
        }
        return out;
    }

    // ------------------------------------------------------------------ métodos

    private static Object join(Realm r, JsArray a, String sep) {
        for (Object o : r.joinStack) {
            if (o == a) {
                return "";
            }
        }
        r.joinStack.add(a);
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.list.size(); i++) {
                if (i > 0) {
                    sb.append(sep);
                }
                Object v = a.list.get(i);
                if (v != null && v != Js.UNDEFINED && v != Js.NULL) {
                    sb.append(Js.toStr(v));
                }
            }
            return sb.toString();
        } finally {
            r.joinStack.remove(r.joinStack.size() - 1);
        }
    }

    private static void flatten(Realm r, List<Object> src, double depth, ArrayList<Object> out) {
        for (Object v : src) {
            if (v == null) {
                continue;
            }
            if (depth >= 1 && v instanceof JsArray inner && !(v instanceof JsArguments)) {
                flatten(r, inner.list, depth - 1, out);
            } else {
                out.add(v);
            }
        }
    }

    private static JsIterObject iter(Realm r, JsArray a, int kind) {
        IterRec it = new IterRec() {
            private int i;

            @Override
            public Object next() {
                if (this.i >= a.list.size()) {
                    this.i = Integer.MAX_VALUE - 1;
                    return DONE;
                }
                int k = this.i++;
                Object v = a.getIndex(k);
                return kind == 0 ? (Object) Js.num(k) : kind == 1 ? v : Lib.array(r, List.of(Js.num(k), v));
            }
        };
        return new JsIterObject(r.arrayIteratorProto, it, "Array Iterator");
    }

    private interface Visit {
        /** Devuelve falso para parar. */
        boolean on(Object v, int i, Object result);
    }

    private static void installProto(Realm r, JsObject ap) {
        Lib.method(r, ap, "push", 1, (t, a) -> {
            if (t instanceof JsArray arr) {
                mutable(arr, "push");
                if (!arr.isExtensible()) {
                    throw Js.typeError("Cannot add property " + arr.length() + ", object is not extensible");
                }
                for (Object v : a) {
                    arr.list.add(v);
                }
                return Js.num(arr.list.size());
            }
            JsObject o = Js.toObject(r, t);
            long n = Lib.toLength(o.get("length"));
            for (Object v : a) {
                o.put(Long.toString(n++), v);
            }
            o.put("length", Js.num(n));
            return Js.num(n);
        });
        Lib.method(r, ap, "pop", 0, (t, a) -> {
            if (!(t instanceof JsArray)) {
                JsObject o = Js.toObject(r, t);
                long n = Lib.toLength(o.get("length"));
                if (n == 0) {
                    o.put("length", 0.0);
                    return Js.UNDEFINED;
                }
                String k = Long.toString(n - 1);
                Object v = o.get(k);
                o.delete(k);
                o.put("length", Js.num(n - 1));
                return v;
            }
            JsArray arr = mutable(t, "pop");
            if (arr.list.isEmpty()) {
                return Js.UNDEFINED;
            }
            Object v = arr.list.remove(arr.list.size() - 1);
            return v == null ? Js.UNDEFINED : v;
        });
        Lib.method(r, ap, "shift", 0, (t, a) -> {
            JsArray arr = mutable(t, "shift");
            if (arr.list.isEmpty()) {
                return Js.UNDEFINED;
            }
            Object v = arr.list.remove(0);
            return v == null ? Js.UNDEFINED : v;
        });
        Lib.method(r, ap, "unshift", 1, (t, a) -> {
            JsArray arr = mutable(t, "unshift");
            arr.list.addAll(0, java.util.Arrays.asList(a));
            return Js.num(arr.list.size());
        });
        Lib.method(r, ap, "slice", 2, (t, a) -> {
            JsArray arr = thisArray(r, t);
            int len = arr.list.size();
            int from = Js.relIndex(Js.arg(a, 0), len, 0);
            int to = Js.relIndex(Js.arg(a, 1), len, len);
            ArrayList<Object> out = new ArrayList<>(Math.max(0, to - from));
            for (int i = from; i < to; i++) {
                out.add(arr.list.get(i));
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "splice", 2, (t, a) -> {
            JsArray arr = mutable(t, "splice");
            int len = arr.list.size();
            int start = Js.relIndex(Js.arg(a, 0), len, 0);
            int del;
            if (a.length == 0) {
                del = 0;
            } else if (a.length == 1) {
                del = len - start;
            } else {
                del = (int) Math.min(Math.max(Js.toInteger(a[1]), 0), len - start);
            }
            List<Object> sub = arr.list.subList(start, start + del);
            ArrayList<Object> removed = new ArrayList<>(sub);
            sub.clear();
            if (a.length > 2) {
                arr.list.addAll(start, java.util.Arrays.asList(a).subList(2, a.length));
            }
            return new JsArray(r.arrayProto, removed);
        });
        Lib.method(r, ap, "concat", 1, (t, a) -> {
            ArrayList<Object> out = new ArrayList<>();
            Object[] all = new Object[a.length + 1];
            all[0] = t;
            System.arraycopy(a, 0, all, 1, a.length);
            for (Object v : all) {
                if (v instanceof JsArray src && !(v instanceof JsArguments)) {
                    out.addAll(src.list);
                } else {
                    out.add(v instanceof JsObject || Js.isNullish(v) ? v : v);
                }
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "join", 1, (t, a) -> join(r, thisArray(r, t), Js.arg(a, 0) == Js.UNDEFINED ? "," : Js.toStr(a[0])));
        Lib.method(r, ap, "toString", 0, (t, a) -> {
            if (t instanceof JsArray arr) {
                return join(r, arr, ",");
            }
            return LibObject.objectToString(t);
        });
        Lib.method(r, ap, "toLocaleString", 0, (t, a) -> join(r, thisArray(r, t), ","));
        Lib.method(r, ap, "reverse", 0, (t, a) -> {
            JsArray arr = mutable(t, "reverse");
            java.util.Collections.reverse(arr.list);
            return arr;
        });
        Lib.method(r, ap, "indexOf", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            int len = arr.list.size();
            int from = Js.relIndex(Js.arg(a, 1), len, 0);
            Object x = Js.arg(a, 0);
            for (int i = from; i < len; i++) {
                Object v = arr.list.get(i);
                if (v != null && Js.strictEquals(v, x)) {
                    return Js.num(i);
                }
            }
            return Js.num(-1);
        });
        Lib.method(r, ap, "lastIndexOf", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            int len = arr.list.size();
            int from = len - 1;
            if (a.length > 1) {
                double d = Js.toInteger(a[1]);
                from = (int) (d < 0 ? len + d : Math.min(d, len - 1));
            }
            Object x = Js.arg(a, 0);
            for (int i = from; i >= 0; i--) {
                Object v = arr.list.get(i);
                if (v != null && Js.strictEquals(v, x)) {
                    return Js.num(i);
                }
            }
            return Js.num(-1);
        });
        Lib.method(r, ap, "includes", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            int len = arr.list.size();
            int from = Js.relIndex(Js.arg(a, 1), len, 0);
            Object x = Js.arg(a, 0);
            for (int i = from; i < len; i++) {
                Object v = arr.list.get(i);
                if (Js.sameValueZero(v == null ? Js.UNDEFINED : v, x)) {
                    return true;
                }
            }
            return false;
        });
        Lib.method(r, ap, "at", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            double d = Js.toInteger(Js.arg(a, 0));
            if (d < 0) {
                d += arr.list.size();
            }
            return d < 0 || d >= arr.list.size() ? Js.UNDEFINED : arr.getIndex((int) d);
        });
        Lib.method(r, ap, "fill", 1, (t, a) -> {
            JsArray arr = mutable(t, "fill");
            int len = arr.list.size();
            int from = Js.relIndex(Js.arg(a, 1), len, 0);
            int to = Js.relIndex(Js.arg(a, 2), len, len);
            for (int i = from; i < to; i++) {
                arr.list.set(i, Js.arg(a, 0));
            }
            return arr;
        });
        Lib.method(r, ap, "copyWithin", 2, (t, a) -> {
            JsArray arr = mutable(t, "copyWithin");
            int len = arr.list.size();
            int target = Js.relIndex(Js.arg(a, 0), len, 0);
            int from = Js.relIndex(Js.arg(a, 1), len, 0);
            int to = Js.relIndex(Js.arg(a, 2), len, len);
            int count = Math.min(to - from, len - target);
            if (count > 0) {
                ArrayList<Object> copy = new ArrayList<>(arr.list.subList(from, from + count));
                for (int i = 0; i < count; i++) {
                    arr.list.set(target + i, copy.get(i));
                }
            }
            return arr;
        });
        Lib.method(r, ap, "sort", 1, (t, a) -> {
            JsArray arr = mutable(t, "sort");
            Object cmp = Js.arg(a, 0);
            if (cmp != Js.UNDEFINED && !(cmp instanceof JsFunction)) {
                throw Js.typeError("The comparison function must be either a function or undefined: " + Js.display(cmp));
            }
            List<Object> s = sorted(arr.list, cmp);
            int holes = arr.list.size() - s.size();
            arr.list.clear();
            arr.list.addAll(s);
            for (int i = 0; i < holes; i++) {
                arr.list.add(null);
            }
            return arr;
        });
        Lib.method(r, ap, "toSorted", 1, (t, a) -> {
            Object cmp = Js.arg(a, 0);
            List<Object> s = sorted(thisArray(r, t).list, cmp);
            return new JsArray(r.arrayProto, new ArrayList<>(s));
        });
        Lib.method(r, ap, "toReversed", 0, (t, a) -> {
            ArrayList<Object> l = new ArrayList<>(thisArray(r, t).list);
            java.util.Collections.reverse(l);
            return new JsArray(r.arrayProto, l);
        });
        Lib.method(r, ap, "with", 2, (t, a) -> {
            ArrayList<Object> l = new ArrayList<>(thisArray(r, t).list);
            double d = Js.toInteger(Js.arg(a, 0));
            if (d < 0) {
                d += l.size();
            }
            if (d < 0 || d >= l.size()) {
                throw Js.rangeError("Invalid index : " + Js.toStr(Js.arg(a, 0)));
            }
            l.set((int) d, Js.arg(a, 1));
            return new JsArray(r.arrayProto, l);
        });
        Lib.method(r, ap, "toSpliced", 2, (t, a) -> {
            ArrayList<Object> l = new ArrayList<>(thisArray(r, t).list);
            int len = l.size();
            int start = Js.relIndex(Js.arg(a, 0), len, 0);
            int del = a.length == 0 ? 0 : a.length == 1 ? len - start : (int) Math.min(Math.max(Js.toInteger(a[1]), 0), len - start);
            l.subList(start, start + del).clear();
            if (a.length > 2) {
                l.addAll(start, java.util.Arrays.asList(a).subList(2, a.length));
            }
            return new JsArray(r.arrayProto, l);
        });
        Lib.method(r, ap, "flat", 0, (t, a) -> {
            double depth = Js.arg(a, 0) == Js.UNDEFINED ? 1 : Js.toInteger(a[0]);
            ArrayList<Object> out = new ArrayList<>();
            flatten(r, thisArray(r, t).list, depth, out);
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "flatMap", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            ArrayList<Object> out = new ArrayList<>();
            for (int i = 0; i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v == null) {
                    continue;
                }
                Object m = f.call(Js.arg(a, 1), new Object[] {v, Js.num(i), arr});
                if (m instanceof JsArray inner && !(m instanceof JsArguments)) {
                    for (Object x : inner.list) {
                        if (x != null) {
                            out.add(x);
                        }
                    }
                } else {
                    out.add(m);
                }
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "forEach", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            Object thisArg = Js.arg(a, 1);
            int len = arr.list.size();
            for (int i = 0; i < len && i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v != null) {
                    f.call(thisArg, new Object[] {v, Js.num(i), arr});
                }
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, ap, "map", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            Object thisArg = Js.arg(a, 1);
            int len = arr.list.size();
            ArrayList<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                Object v = i < arr.list.size() ? arr.list.get(i) : null;
                out.add(v == null ? null : f.call(thisArg, new Object[] {v, Js.num(i), arr}));
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "filter", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            Object thisArg = Js.arg(a, 1);
            int len = arr.list.size();
            ArrayList<Object> out = new ArrayList<>();
            for (int i = 0; i < len && i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v != null && Js.truthy(f.call(thisArg, new Object[] {v, Js.num(i), arr}))) {
                    out.add(v);
                }
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, ap, "some", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            int len = arr.list.size();
            for (int i = 0; i < len && i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v != null && Js.truthy(f.call(Js.arg(a, 1), new Object[] {v, Js.num(i), arr}))) {
                    return true;
                }
            }
            return false;
        });
        Lib.method(r, ap, "every", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            int len = arr.list.size();
            for (int i = 0; i < len && i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v != null && !Js.truthy(f.call(Js.arg(a, 1), new Object[] {v, Js.num(i), arr}))) {
                    return false;
                }
            }
            return true;
        });
        for (String name : new String[] {"find", "findIndex", "findLast", "findLastIndex"}) {
            boolean last = name.startsWith("findLast");
            boolean index = name.endsWith("Index");
            Lib.method(r, ap, name, 1, (t, a) -> {
                JsArray arr = thisArray(r, t);
                JsFunction f = Lib.callable(Js.arg(a, 0), null);
                int len = arr.list.size();
                for (int n = 0; n < len; n++) {
                    int i = last ? len - 1 - n : n;
                    Object v = arr.getIndex(i);
                    if (Js.truthy(f.call(Js.arg(a, 1), new Object[] {v, Js.num(i), arr}))) {
                        return index ? (Object) Js.num(i) : v;
                    }
                }
                return index ? (Object) Js.num(-1) : Js.UNDEFINED;
            });
        }
        Lib.method(r, ap, "reduce", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            int len = arr.list.size();
            int i = 0;
            Object acc;
            if (a.length > 1) {
                acc = a[1];
            } else {
                while (i < len && arr.list.get(i) == null) {
                    i++;
                }
                if (i >= len) {
                    throw Js.typeError("Reduce of empty array with no initial value");
                }
                acc = arr.list.get(i++);
            }
            for (; i < len && i < arr.list.size(); i++) {
                Object v = arr.list.get(i);
                if (v != null) {
                    acc = f.call(Js.UNDEFINED, new Object[] {acc, v, Js.num(i), arr});
                }
            }
            return acc;
        });
        Lib.method(r, ap, "reduceRight", 1, (t, a) -> {
            JsArray arr = thisArray(r, t);
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            int i = arr.list.size() - 1;
            Object acc;
            if (a.length > 1) {
                acc = a[1];
            } else {
                while (i >= 0 && arr.list.get(i) == null) {
                    i--;
                }
                if (i < 0) {
                    throw Js.typeError("Reduce of empty array with no initial value");
                }
                acc = arr.list.get(i--);
            }
            for (; i >= 0; i--) {
                Object v = i < arr.list.size() ? arr.list.get(i) : null;
                if (v != null) {
                    acc = f.call(Js.UNDEFINED, new Object[] {acc, v, Js.num(i), arr});
                }
            }
            return acc;
        });
        Lib.method(r, ap, "keys", 0, (t, a) -> iter(r, thisArray(r, t), 0));
        Lib.method(r, ap, "entries", 0, (t, a) -> iter(r, thisArray(r, t), 2));
        NativeFunction values = Lib.fn(r, "values", 0, (t, a) -> iter(r, thisArray(r, t), 1));
        ap.setHidden("values", values);
        ap.setHidden(JsSymbol.ITERATOR, values);
        r.arrayValues = values;
    }
}
