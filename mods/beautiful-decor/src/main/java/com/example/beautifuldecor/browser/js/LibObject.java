package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Object y Function. */
final class LibObject {
    private LibObject() {
    }

    static void install(Realm r) {
        JsObject op = r.objectProto;
        NativeFunction object = Lib.ctor(r, "Object", 1, op, (t, a) -> {
            Object v = Js.arg(a, 0);
            return Js.isNullish(v) ? new JsObject(r.objectProto) : Js.toObject(r, v);
        }, (a, nt) -> {
            Object v = Js.arg(a, 0);
            if (nt != null && nt != r.global.get("Object")) {
                return new JsObject(Lib.protoFrom(nt, r.objectProto));
            }
            return Js.isNullish(v) ? new JsObject(r.objectProto) : Js.toObject(r, v);
        });
        installPrototype(r, op);
        installStatics(r, object);
        installFunction(r);
    }

    private static void installPrototype(Realm r, JsObject op) {
        Lib.method(r, op, "hasOwnProperty", 1, (t, a) -> Js.toObject(r, t).getOwn(Js.toKey(Js.arg(a, 0))) != null);
        Lib.method(r, op, "isPrototypeOf", 1, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsObject o) || !(t instanceof JsObject self)) {
                return false;
            }
            for (JsObject x = o.proto; x != null; x = x.proto) {
                if (x == self) {
                    return true;
                }
            }
            return false;
        });
        Lib.method(r, op, "propertyIsEnumerable", 1, (t, a) -> {
            Prop p = Js.toObject(r, t).getOwn(Js.toKey(Js.arg(a, 0)));
            return p != null && p.enumerable();
        });
        Lib.method(r, op, "toString", 0, (t, a) -> objectToString(t));
        Lib.method(r, op, "toLocaleString", 0, (t, a) -> Js.call(Js.getMember(r, t, "toString"), t));
        Lib.method(r, op, "valueOf", 0, (t, a) -> Js.toObject(r, t));
        Lib.accessor(r, op, "__proto__", (t, a) -> {
            JsObject o = Js.toObject(r, t);
            return o.proto == null ? Js.NULL : o.proto;
        }, (t, a) -> {
            Object p = Js.arg(a, 0);
            if (t instanceof JsObject o && (p instanceof JsObject || p == Js.NULL)) {
                setProto(o, p == Js.NULL ? null : (JsObject) p);
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, op, "__defineGetter__", 2, (t, a) -> {
            LibObject.defineAccessorPart(r, Js.toObject(r, t), Js.toKey(Js.arg(a, 0)), Js.arg(a, 1), true);
            return Js.UNDEFINED;
        });
        Lib.method(r, op, "__defineSetter__", 2, (t, a) -> {
            LibObject.defineAccessorPart(r, Js.toObject(r, t), Js.toKey(Js.arg(a, 0)), Js.arg(a, 1), false);
            return Js.UNDEFINED;
        });
    }

    private static void defineAccessorPart(Realm r, JsObject o, Object key, Object f, boolean getter) {
        JsFunction fn = Lib.callable(f, null);
        Closure.mergeAccessor(o, key, getter ? fn : null, getter ? null : fn, Prop.E | Prop.C);
    }

    static String objectToString(Object t) {
        if (t == Js.UNDEFINED) {
            return "[object Undefined]";
        }
        if (t == Js.NULL) {
            return "[object Null]";
        }
        Realm r = Realm.cur();
        JsObject o = Js.toObject(r, t);
        Object tag = o.get(JsSymbol.TO_STRING_TAG);
        if (tag instanceof String s) {
            return "[object " + s + "]";
        }
        String cn = o.className();
        switch (cn) {
            case "Array":
            case "Function":
            case "Error":
            case "Boolean":
            case "Number":
            case "String":
            case "Date":
            case "RegExp":
            case "Arguments":
                return "[object " + cn + "]";
            default:
                return "[object Object]";
        }
    }

    static void setProto(JsObject o, JsObject p) {
        if (o.proto == p) {
            return;
        }
        if (!o.extensible) {
            throw Js.typeError("#<Object> is not extensible");
        }
        for (JsObject x = p; x != null; x = x.proto) {
            if (x == o) {
                throw Js.typeError("Cyclic __proto__ value");
            }
        }
        o.proto = p;
    }

    // ------------------------------------------------------------------ Object.*

    static List<Object> enumerableKeys(JsObject o, boolean strings, boolean symbols) {
        ArrayList<Object> out = new ArrayList<>();
        if (o instanceof JsArray a && o.props == null && strings && !symbols && a.getClass() == JsArray.class) {
            for (int i = 0; i < a.list.size(); i++) {
                if (a.list.get(i) != null) {
                    out.add(Integer.toString(i));
                }
            }
            return out;
        }
        for (Object k : o.ownKeys()) {
            if ((k instanceof String && strings) || (k instanceof JsSymbol && symbols)) {
                Prop p = o.getOwn(k);
                if (p != null && p.enumerable()) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    static JsObject descriptorOf(Realm r, Prop p) {
        JsObject d = new JsObject(r.objectProto);
        if (p.accessor) {
            d.setData("get", p.getter == null ? Js.UNDEFINED : p.getter);
            d.setData("set", p.setter == null ? Js.UNDEFINED : p.setter);
        } else {
            d.setData("value", p.value);
            d.setData("writable", p.writable());
        }
        d.setData("enumerable", p.enumerable());
        d.setData("configurable", p.configurable());
        return d;
    }

    static void defineProperty(Realm r, JsObject o, Object key, Object descObj) {
        if (!(descObj instanceof JsObject d)) {
            throw Js.typeError("Property description must be an object: " + Js.display(descObj));
        }
        boolean hasGet = d.hasProperty("get");
        boolean hasSet = d.hasProperty("set");
        boolean hasValue = d.hasProperty("value");
        boolean hasW = d.hasProperty("writable");
        boolean hasE = d.hasProperty("enumerable");
        boolean hasC = d.hasProperty("configurable");
        if ((hasGet || hasSet) && (hasValue || hasW)) {
            throw Js.typeError("Invalid property descriptor. Cannot both specify accessors and a value or writable attribute");
        }
        JsFunction g = null;
        JsFunction s = null;
        if (hasGet) {
            Object gv = d.get("get");
            if (gv != Js.UNDEFINED) {
                g = Lib.callable(gv, "Getter must be a function");
            }
        }
        if (hasSet) {
            Object sv = d.get("set");
            if (sv != Js.UNDEFINED) {
                s = Lib.callable(sv, "Setter must be a function");
            }
        }
        boolean w = hasW && Js.truthy(d.get("writable"));
        boolean e = hasE && Js.truthy(d.get("enumerable"));
        boolean c = hasC && Js.truthy(d.get("configurable"));
        Object value = hasValue ? d.get("value") : Js.UNDEFINED;
        Prop cur = o.getOwn(key);
        String kn = key instanceof JsSymbol ? key.toString() : String.valueOf(key);
        if (cur == null) {
            if (!o.isExtensible()) {
                throw Js.typeError("Cannot define property " + kn + ", object is not extensible");
            }
            int flags = (e ? Prop.E : 0) | (c ? Prop.C : 0) | (w ? Prop.W : 0);
            o.defineOwn(key, hasGet || hasSet ? new Prop(g, s, flags) : new Prop(value, flags));
            return;
        }
        if (!cur.configurable()) {
            boolean ok = !(hasC && c) && !(hasE && e != cur.enumerable());
            if (cur.accessor) {
                if (hasValue || hasW || (hasGet && g != cur.getter) || (hasSet && s != cur.setter)) {
                    ok = false;
                }
            } else {
                if (hasGet || hasSet) {
                    ok = false;
                }
                if (!cur.writable() && ((hasW && w) || (hasValue && !Js.sameValue(value, cur.value)))) {
                    ok = false;
                }
            }
            if (!ok) {
                throw Js.typeError("Cannot redefine property: " + kn);
            }
        }
        int flags = cur.flags;
        if (hasE) {
            flags = e ? flags | Prop.E : flags & ~Prop.E;
        }
        if (hasC) {
            flags = c ? flags | Prop.C : flags & ~Prop.C;
        }
        if (hasGet || hasSet) {
            JsFunction ng = hasGet ? g : cur.accessor ? cur.getter : null;
            JsFunction ns = hasSet ? s : cur.accessor ? cur.setter : null;
            o.defineOwn(key, new Prop(ng, ns, flags & ~Prop.W));
        } else if (cur.accessor && !hasValue && !hasW) {
            cur.flags = flags;
            o.defineOwn(key, cur);
        } else {
            if (hasW) {
                flags = w ? flags | Prop.W : flags & ~Prop.W;
            } else if (cur.accessor) {
                flags &= ~Prop.W;
            }
            o.defineOwn(key, new Prop(hasValue ? value : cur.accessor ? Js.UNDEFINED : cur.value, flags));
        }
    }

    private static void freeze(JsObject o, boolean full) {
        o.preventExtensions();
        if (o.props != null) {
            for (Prop p : o.props.values()) {
                p.flags &= ~Prop.C;
                if (full && !p.accessor) {
                    p.flags &= ~Prop.W;
                }
            }
        }
        if (o instanceof JsArray a && full) {
            a.frozen = true;
        }
    }

    private static boolean isFrozen(JsObject o, boolean full) {
        if (o.isExtensible()) {
            return false;
        }
        if (o instanceof JsArray a && !a.list.isEmpty() && full && !a.frozen) {
            return false;
        }
        if (o.props != null) {
            for (Prop p : o.props.values()) {
                if (p.configurable() || (full && !p.accessor && p.writable())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void installStatics(Realm r, NativeFunction object) {
        Lib.method(r, object, "groupBy", 2, (t, a) -> {
            JsFunction f = Lib.callable(Js.arg(a, 1), "callback");
            JsObject out = new JsObject(null);
            IterRec it = Js.iterate(Js.arg(a, 0));
            double idx = 0;
            for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                Object k = Js.toKey(f.call(Js.UNDEFINED, new Object[] {e, Js.num(idx++)}));
                Object cur = out.get(k);
                if (cur instanceof JsArray arr) {
                    arr.add(e);
                } else {
                    out.setData(k, new JsArray(r.arrayProto, new ArrayList<Object>(List.of(e))));
                }
            }
            return out;
        });
        Lib.method(r, object, "keys", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            return Lib.array(r, enumerableKeys(o, true, false));
        });
        Lib.method(r, object, "values", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            ArrayList<Object> out = new ArrayList<>();
            for (Object k : enumerableKeys(o, true, false)) {
                out.add(o.get(k));
            }
            return Lib.array(r, out);
        });
        Lib.method(r, object, "entries", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            ArrayList<Object> out = new ArrayList<>();
            for (Object k : enumerableKeys(o, true, false)) {
                out.add(Lib.array(r, List.of(k, o.get(k))));
            }
            return Lib.array(r, out);
        });
        Lib.method(r, object, "assign", 2, (t, a) -> {
            JsObject target = Js.toObject(r, Js.arg(a, 0));
            for (int i = 1; i < a.length; i++) {
                if (Js.isNullish(a[i])) {
                    continue;
                }
                JsObject src = Js.toObject(r, a[i]);
                for (Object k : enumerableKeys(src, true, true)) {
                    if (!target.set(k, src.get(k), target)) {
                        throw Js.typeError("Cannot assign to read only property '" + k + "' of object");
                    }
                }
            }
            return target;
        });
        Lib.method(r, object, "create", 2, (t, a) -> {
            Object p = Js.arg(a, 0);
            if (!(p instanceof JsObject) && p != Js.NULL) {
                throw Js.typeError("Object prototype may only be an Object or null: " + Js.display(p));
            }
            JsObject o = new JsObject(p == Js.NULL ? null : (JsObject) p);
            if (Js.arg(a, 1) != Js.UNDEFINED) {
                defineProps(r, o, Js.arg(a, 1));
            }
            return o;
        });
        Lib.method(r, object, "defineProperty", 3, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsObject o)) {
                throw Js.typeError("Object.defineProperty called on non-object");
            }
            defineProperty(r, o, Js.toKey(Js.arg(a, 1)), Js.arg(a, 2));
            return o;
        });
        Lib.method(r, object, "defineProperties", 2, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsObject o)) {
                throw Js.typeError("Object.defineProperties called on non-object");
            }
            defineProps(r, o, Js.arg(a, 1));
            return o;
        });
        Lib.method(r, object, "getOwnPropertyNames", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            ArrayList<Object> out = new ArrayList<>();
            for (Object k : o.ownKeys()) {
                if (k instanceof String) {
                    out.add(k);
                }
            }
            return Lib.array(r, out);
        });
        Lib.method(r, object, "getOwnPropertySymbols", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            ArrayList<Object> out = new ArrayList<>();
            for (Object k : o.ownKeys()) {
                if (k instanceof JsSymbol) {
                    out.add(k);
                }
            }
            return Lib.array(r, out);
        });
        Lib.method(r, object, "getOwnPropertyDescriptor", 2, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            Prop p = o.getOwn(Js.toKey(Js.arg(a, 1)));
            return p == null ? Js.UNDEFINED : descriptorOf(r, p);
        });
        Lib.method(r, object, "getOwnPropertyDescriptors", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            JsObject out = new JsObject(r.objectProto);
            for (Object k : o.ownKeys()) {
                Prop p = o.getOwn(k);
                if (p != null) {
                    out.setData(k, descriptorOf(r, p));
                }
            }
            return out;
        });
        Lib.method(r, object, "getPrototypeOf", 1, (t, a) -> {
            JsObject o = Js.toObject(r, Js.arg(a, 0));
            return o.proto == null ? Js.NULL : o.proto;
        });
        Lib.method(r, object, "setPrototypeOf", 2, (t, a) -> {
            Object o = Js.arg(a, 0);
            Object p = Js.arg(a, 1);
            if (Js.isNullish(o)) {
                throw Js.typeError("Object.setPrototypeOf called on null or undefined");
            }
            if (!(p instanceof JsObject) && p != Js.NULL) {
                throw Js.typeError("Object prototype may only be an Object or null: " + Js.display(p));
            }
            if (o instanceof JsObject jo) {
                setProto(jo, p == Js.NULL ? null : (JsObject) p);
            }
            return o;
        });
        Lib.method(r, object, "freeze", 1, (t, a) -> {
            if (Js.arg(a, 0) instanceof JsObject o) {
                freeze(o, true);
            }
            return Js.arg(a, 0);
        });
        Lib.method(r, object, "seal", 1, (t, a) -> {
            if (Js.arg(a, 0) instanceof JsObject o) {
                freeze(o, false);
            }
            return Js.arg(a, 0);
        });
        Lib.method(r, object, "preventExtensions", 1, (t, a) -> {
            if (Js.arg(a, 0) instanceof JsObject o) {
                o.preventExtensions();
            }
            return Js.arg(a, 0);
        });
        Lib.method(r, object, "isFrozen", 1, (t, a) -> !(Js.arg(a, 0) instanceof JsObject o) || isFrozen(o, true));
        Lib.method(r, object, "isSealed", 1, (t, a) -> !(Js.arg(a, 0) instanceof JsObject o) || isFrozen(o, false));
        Lib.method(r, object, "isExtensible", 1, (t, a) -> Js.arg(a, 0) instanceof JsObject o && o.isExtensible());
        Lib.method(r, object, "is", 2, (t, a) -> Js.sameValue(Js.arg(a, 0), Js.arg(a, 1)));
        Lib.method(r, object, "hasOwn", 2, (t, a) -> Js.toObject(r, Js.arg(a, 0)).getOwn(Js.toKey(Js.arg(a, 1))) != null);
        Lib.method(r, object, "fromEntries", 1, (t, a) -> {
            JsObject out = new JsObject(r.objectProto);
            IterRec it = Js.iterate(Js.arg(a, 0));
            for (Object e = it.next(); e != IterRec.DONE; e = it.next()) {
                if (!(e instanceof JsObject eo)) {
                    throw Js.typeError("Iterator value " + Js.display(e) + " is not an entry object");
                }
                out.setData(Js.toKey(Js.getMember(r, eo, 0.0)), Js.getMember(r, eo, 1.0));
            }
            return out;
        });
    }

    private static void defineProps(Realm r, JsObject o, Object propsObj) {
        JsObject props = Js.toObject(r, propsObj);
        for (Object k : enumerableKeys(props, true, true)) {
            defineProperty(r, o, k, props.get(k));
        }
    }

    // ------------------------------------------------------------------ Function

    private static void installFunction(Realm r) {
        JsFunction fp = r.functionProto;
        NativeFunction function = Lib.ctor(r, "Function", 1, fp, (t, a) -> makeFunction(r, a), (a, nt) -> makeFunction(r, a));
        function.setConst("length", 1.0);
        kindCtor(r, function, r.asyncFunctionProto, "AsyncFunction", "async function");
        kindCtor(r, function, r.generatorFunctionProto, "GeneratorFunction", "function*");
        kindCtor(r, function, r.asyncGeneratorFunctionProto, "AsyncGeneratorFunction", "async function*");
        r.generatorFunctionProto.defineOwn("prototype", new Prop(r.generatorProto, Prop.C));
        r.asyncGeneratorFunctionProto.defineOwn("prototype", new Prop(r.asyncGeneratorProto, Prop.C));
        r.generatorProto.defineOwn("constructor", new Prop(r.generatorFunctionProto, Prop.C));
        r.asyncGeneratorProto.defineOwn("constructor", new Prop(r.asyncGeneratorFunctionProto, Prop.C));
        Lib.method(r, fp, "call", 1, (t, a) -> {
            JsFunction f = Lib.callable(t, null);
            Object[] rest = a.length > 1 ? java.util.Arrays.copyOfRange(a, 1, a.length) : Js.EMPTY;
            return f.call(Js.arg(a, 0), rest);
        });
        Lib.method(r, fp, "apply", 2, (t, a) -> {
            JsFunction f = Lib.callable(t, null);
            return f.call(Js.arg(a, 0), Lib.listFromArrayLike(Js.arg(a, 1)).toArray());
        });
        Lib.method(r, fp, "bind", 1, (t, a) -> {
            JsFunction f = Lib.callable(t, null);
            Object[] rest = a.length > 1 ? java.util.Arrays.copyOfRange(a, 1, a.length) : Js.EMPTY;
            return new BoundFunction(r, f, Js.arg(a, 0), rest);
        });
        Lib.method(r, fp, "toString", 0, (t, a) -> {
            if (t instanceof Closure c) {
                return c.sourceText();
            }
            if (t instanceof JsFunction f) {
                return "function " + f.fname + "() { [native code] }";
            }
            throw Js.typeError("Function.prototype.toString requires that 'this' be a Function");
        });
        Lib.method(r, fp, JsSymbol.HAS_INSTANCE, "[Symbol.hasInstance]", 1,
                (t, a) -> t instanceof JsFunction f && Js.ordinaryHasInstance(f, Js.arg(a, 0)));
        fp.deleteOwn(JsSymbol.HAS_INSTANCE);
        fp.fname = "";
    }

    private static Object makeFunction(Realm r, Object[] a) {
        return makeFunction(r, a, "function");
    }

    private static void kindCtor(Realm r, NativeFunction function, JsObject proto, String name, String kind) {
        NativeFunction c = new NativeFunction(r, name, 1, (t, a) -> makeFunction(r, a, kind), (a, nt) -> makeFunction(r, a, kind));
        c.proto = function;
        c.setConst("prototype", proto);
        proto.setHidden("constructor", c);
        Lib.tag(proto, name);
    }

    private static Object makeFunction(Realm r, Object[] a, String kind) {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < a.length - 1; i++) {
            if (i > 0) {
                params.append(',');
            }
            params.append(Js.toStr(a[i]));
        }
        String body = a.length == 0 ? "" : Js.toStr(a[a.length - 1]);
        Ast.FuncNode node;
        try {
            node = Parser.parseFunctionCtor(params.toString(), body, kind);
        } catch (JsSyntaxError e) {
            throw Js.syntaxError(e.getMessage());
        }
        return new Closure(r, node, r.globalScope);
    }
}
