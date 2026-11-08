package com.example.beautifuldecor.browser.js;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/** Funciones y objetos globales: console, URI, Reflect, structuredClone, Intl básico... */
final class LibGlobal {
    private static final String URI_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()";
    private static final String URI_RESERVED = ";/?:@&=+$,#";

    private LibGlobal() {
    }

    static void install(Realm r) {
        JsObject g = r.global;
        g.setHidden("globalThis", g);
        g.defineOwn("NaN", new Prop(Js.NAN, 0));
        g.defineOwn("Infinity", new Prop(Js.num(Double.POSITIVE_INFINITY), 0));
        g.defineOwn("undefined", new Prop(Js.UNDEFINED, 0));
        Lib.method(r, g, "isNaN", 1, (t, a) -> Double.isNaN(Js.toNumber(Js.arg(a, 0))));
        Lib.method(r, g, "isFinite", 1, (t, a) -> {
            double d = Js.toNumber(Js.arg(a, 0));
            return !Double.isNaN(d) && !Double.isInfinite(d);
        });
        NativeFunction ev = new NativeFunction(r, "eval", 1, (t, a) -> r.indirectEval(Js.arg(a, 0)));
        g.setHidden("eval", ev);
        r.evalFn = ev;
        installUri(r);
        installConsole(r);
        installReflect(r);
        installMisc(r);
        installIntl(r);
    }

    // ------------------------------------------------------------------ URI

    private static JsException uriError(Realm r) {
        return r.error("URIError", "URI malformed");
    }

    private static String encode(Realm r, String s, String keep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (keep.indexOf(c) >= 0) {
                sb.append(c);
                continue;
            }
            int cp = c;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    throw uriError(r);
                }
                cp = Character.toCodePoint(c, s.charAt(++i));
            } else if (Character.isLowSurrogate(c)) {
                throw uriError(r);
            }
            byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                sb.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    private static int hexAt(String s, int i) {
        if (i + 3 > s.length()) {
            return -1;
        }
        int h = Character.digit(s.charAt(i + 1), 16);
        int l = Character.digit(s.charAt(i + 2), 16);
        return h < 0 || l < 0 ? -1 : h * 16 + l;
    }

    private static String decode(Realm r, String s, String reservedKeep) {
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            int start = i;
            int b = hexAt(s, i);
            if (b < 0) {
                throw uriError(r);
            }
            i += 2;
            if (b < 0x80) {
                char ch = (char) b;
                if (reservedKeep.indexOf(ch) >= 0) {
                    sb.append(s, start, i + 1);
                } else {
                    sb.append(ch);
                }
                continue;
            }
            int count;
            if ((b & 0xE0) == 0xC0) {
                count = 2;
            } else if ((b & 0xF0) == 0xE0) {
                count = 3;
            } else if ((b & 0xF8) == 0xF0) {
                count = 4;
            } else {
                throw uriError(r);
            }
            byte[] bytes = new byte[count];
            bytes[0] = (byte) b;
            for (int k = 1; k < count; k++) {
                if (i + 1 >= n || s.charAt(i + 1) != '%') {
                    throw uriError(r);
                }
                int cb = hexAt(s, i + 1);
                if (cb < 0 || (cb & 0xC0) != 0x80) {
                    throw uriError(r);
                }
                bytes[k] = (byte) cb;
                i += 3;
            }
            String dec = new String(bytes, StandardCharsets.UTF_8);
            if (dec.indexOf('�') >= 0) {
                throw uriError(r);
            }
            sb.append(dec);
        }
        return sb.toString();
    }

    private static void installUri(Realm r) {
        JsObject g = r.global;
        Lib.method(r, g, "encodeURIComponent", 1, (t, a) -> encode(r, Js.toStr(Js.arg(a, 0)), URI_UNRESERVED));
        Lib.method(r, g, "encodeURI", 1, (t, a) -> encode(r, Js.toStr(Js.arg(a, 0)), URI_UNRESERVED + URI_RESERVED));
        Lib.method(r, g, "decodeURIComponent", 1, (t, a) -> decode(r, Js.toStr(Js.arg(a, 0)), ""));
        Lib.method(r, g, "decodeURI", 1, (t, a) -> decode(r, Js.toStr(Js.arg(a, 0)), URI_RESERVED));
        Lib.method(r, g, "escape", 1, (t, a) -> {
            String s = Js.toStr(Js.arg(a, 0));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "@*_+-./".indexOf(c) >= 0) {
                    sb.append(c);
                } else if (c < 256) {
                    sb.append(String.format("%%%02X", (int) c));
                } else {
                    sb.append(String.format("%%u%04X", (int) c));
                }
            }
            return sb.toString();
        });
        Lib.method(r, g, "unescape", 1, (t, a) -> {
            String s = Js.toStr(Js.arg(a, 0));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '%') {
                    if (s.startsWith("u", i + 1) && isHex(s, i + 2, 4)) {
                        sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 5;
                        continue;
                    }
                    if (i + 2 < s.length() && isHex(s, i + 1, 2)) {
                        sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    }
                }
                sb.append(c);
            }
            return sb.toString();
        });
    }

    private static boolean isHex(String s, int from, int count) {
        if (from + count > s.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ console

    private static void installConsole(Realm r) {
        JsObject con = new JsObject(r.objectProto);
        r.global.setHidden("console", con);
        int[] indent = {0};
        HashMap<String, Integer> counts = new HashMap<>();
        HashMap<String, Long> timers = new HashMap<>();
        java.util.function.BiConsumer<String, String> emit = (level, text) -> {
            String s = text;
            if (indent[0] > 0) {
                String pad = " ".repeat(indent[0]);
                s = pad + s.replace("\n", "\n" + pad);
            }
            r.console.print(level, s);
        };
        String[][] levels = {{"log", "log"}, {"info", "info"}, {"debug", "debug"}, {"warn", "warn"}, {"error", "error"}};
        for (String[] lv : levels) {
            Lib.method(r, con, lv[0], 0, (t, a) -> {
                emit.accept(lv[1], Inspect.formatArgs(r, a));
                return Js.UNDEFINED;
            });
        }
        Lib.method(r, con, "trace", 0, (t, a) -> {
            emit.accept("error", "Trace: " + Inspect.formatArgs(r, a) + "\n    at <anonymous>");
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "dir", 0, (t, a) -> {
            int depth = 2;
            if (Js.arg(a, 1) instanceof JsObject opts) {
                Object d = opts.get("depth");
                if (d == Js.NULL) {
                    depth = Integer.MAX_VALUE / 2;
                } else if (d instanceof Number n) {
                    depth = Double.isInfinite(n.doubleValue()) ? Integer.MAX_VALUE / 2 : (int) n.doubleValue();
                }
            }
            emit.accept("log", Inspect.inspect(r, Js.arg(a, 0), depth));
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "table", 1, (t, a) -> {
            emit.accept("log", Inspect.formatArgs(r, new Object[] {Js.arg(a, 0)}));
            return Js.UNDEFINED;
        });
        NativeFunction group = new NativeFunction(r, "group", 0, (t, a) -> {
            if (a.length > 0) {
                emit.accept("log", Inspect.formatArgs(r, a));
            }
            indent[0] += 2;
            return Js.UNDEFINED;
        });
        con.setHidden("group", group);
        con.setHidden("groupCollapsed", group);
        Lib.method(r, con, "groupEnd", 0, (t, a) -> {
            indent[0] = Math.max(0, indent[0] - 2);
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "assert", 0, (t, a) -> {
            if (!Js.truthy(Js.arg(a, 0))) {
                Object[] rest = a.length > 1 ? java.util.Arrays.copyOfRange(a, 1, a.length) : Js.EMPTY;
                String msg = rest.length == 0 ? "" : Inspect.formatArgs(r, rest);
                emit.accept("error", rest.length == 0 ? "Assertion failed" : "Assertion failed: " + msg);
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "count", 0, (t, a) -> {
            String label = Js.arg(a, 0) == Js.UNDEFINED ? "default" : Js.toStr(a[0]);
            int n = counts.merge(label, 1, Integer::sum);
            emit.accept("log", label + ": " + n);
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "countReset", 0, (t, a) -> {
            counts.remove(Js.arg(a, 0) == Js.UNDEFINED ? "default" : Js.toStr(a[0]));
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "time", 0, (t, a) -> {
            timers.put(Js.arg(a, 0) == Js.UNDEFINED ? "default" : Js.toStr(a[0]), System.nanoTime());
            return Js.UNDEFINED;
        });
        NativeFunction timeLog = new NativeFunction(r, "timeLog", 0, (t, a) -> {
            String label = Js.arg(a, 0) == Js.UNDEFINED ? "default" : Js.toStr(a[0]);
            Long st = timers.get(label);
            if (st != null) {
                double ms = (System.nanoTime() - st) / 1e6;
                emit.accept("log", label + ": " + NumFmt.toFixed(ms, 3) + "ms");
            }
            return Js.UNDEFINED;
        });
        con.setHidden("timeLog", timeLog);
        Lib.method(r, con, "timeEnd", 0, (t, a) -> {
            String label = Js.arg(a, 0) == Js.UNDEFINED ? "default" : Js.toStr(a[0]);
            timeLog.call(t, a);
            timers.remove(label);
            return Js.UNDEFINED;
        });
        Lib.method(r, con, "clear", 0, (t, a) -> Js.UNDEFINED);
    }

    // ------------------------------------------------------------------ Reflect

    private static JsObject target(Object o, String m) {
        if (o instanceof JsObject jo) {
            return jo;
        }
        throw Js.typeError("Reflect." + m + " called on non-object");
    }

    private static void installReflect(Realm r) {
        JsObject rf = new JsObject(r.objectProto);
        r.global.setHidden("Reflect", rf);
        Lib.tag(rf, "Reflect");
        Lib.method(r, rf, "apply", 3, (t, a) -> Lib.callable(Js.arg(a, 0), null).call(Js.arg(a, 1),
                Lib.listFromArrayLike(Js.arg(a, 2)).toArray()));
        Lib.method(r, rf, "construct", 2, (t, a) -> {
            Object f = Js.arg(a, 0);
            if (!(f instanceof JsFunction fn) || !fn.isConstructor()) {
                throw Js.typeError(Js.display(f) + " is not a constructor");
            }
            Object nt = a.length > 2 ? a[2] : f;
            if (!(nt instanceof JsFunction nf) || !nf.isConstructor()) {
                throw Js.typeError(Js.display(nt) + " is not a constructor");
            }
            return fn.construct(Lib.listFromArrayLike(Js.arg(a, 1)).toArray(), nf);
        });
        Lib.method(r, rf, "defineProperty", 3, (t, a) -> {
            try {
                LibObject.defineProperty(r, target(Js.arg(a, 0), "defineProperty"), Js.toKey(Js.arg(a, 1)), Js.arg(a, 2));
                return true;
            } catch (JsException e) {
                return false;
            }
        });
        Lib.method(r, rf, "deleteProperty", 2, (t, a) -> target(Js.arg(a, 0), "deleteProperty").delete(Js.toKey(Js.arg(a, 1))));
        Lib.method(r, rf, "get", 2, (t, a) -> {
            JsObject o = target(Js.arg(a, 0), "get");
            return o.get(Js.toKey(Js.arg(a, 1)), a.length > 2 ? a[2] : o);
        });
        Lib.method(r, rf, "set", 3, (t, a) -> {
            JsObject o = target(Js.arg(a, 0), "set");
            return o.set(Js.toKey(Js.arg(a, 1)), Js.arg(a, 2), a.length > 3 ? a[3] : o);
        });
        Lib.method(r, rf, "has", 2, (t, a) -> target(Js.arg(a, 0), "has").hasProperty(Js.toKey(Js.arg(a, 1))));
        Lib.method(r, rf, "ownKeys", 1, (t, a) -> Lib.array(r, target(Js.arg(a, 0), "ownKeys").ownKeys()));
        Lib.method(r, rf, "getOwnPropertyDescriptor", 2, (t, a) -> {
            Prop p = target(Js.arg(a, 0), "getOwnPropertyDescriptor").getOwn(Js.toKey(Js.arg(a, 1)));
            return p == null ? Js.UNDEFINED : LibObject.descriptorOf(r, p);
        });
        Lib.method(r, rf, "getPrototypeOf", 1, (t, a) -> {
            JsObject p = target(Js.arg(a, 0), "getPrototypeOf").proto;
            return p == null ? Js.NULL : p;
        });
        Lib.method(r, rf, "setPrototypeOf", 2, (t, a) -> {
            JsObject o = target(Js.arg(a, 0), "setPrototypeOf");
            Object p = Js.arg(a, 1);
            if (p != Js.NULL && !(p instanceof JsObject)) {
                throw Js.typeError("Object prototype may only be an Object or null: " + Js.display(p));
            }
            try {
                LibObject.setProto(o, p == Js.NULL ? null : (JsObject) p);
                return true;
            } catch (JsException e) {
                return false;
            }
        });
        Lib.method(r, rf, "isExtensible", 1, (t, a) -> target(Js.arg(a, 0), "isExtensible").isExtensible());
        Lib.method(r, rf, "preventExtensions", 1, (t, a) -> {
            target(Js.arg(a, 0), "preventExtensions").preventExtensions();
            return true;
        });
    }

    // ------------------------------------------------------------------ varios

    private static Object clone(Realm r, Object v, IdentityHashMap<Object, Object> seen) {
        if (!(v instanceof JsObject o)) {
            if (v instanceof JsSymbol) {
                throw dataClone(r, Js.display(v) + " could not be cloned.");
            }
            return v;
        }
        Object done = seen.get(o);
        if (done != null) {
            return done;
        }
        if (o instanceof JsFunction) {
            throw dataClone(r, Inspect.inspect(r, o, 0) + " could not be cloned.");
        }
        if (o instanceof JsDate d) {
            JsDate c = new JsDate(r.dateProto, d.time);
            seen.put(o, c);
            return c;
        }
        if (o instanceof JsRegExp re) {
            JsRegExp c = LibRegExp.create(r, re.source, re.flags);
            seen.put(o, c);
            return c;
        }
        if (o instanceof JsWrapper w) {
            JsWrapper c = new JsWrapper(w.proto, w.prim);
            seen.put(o, c);
            return c;
        }
        if (o instanceof JsMap m) {
            JsMap c = new JsMap(r.mapProto);
            seen.put(o, c);
            OrderedTable.Cursor cur = m.table.cursor();
            for (OrderedTable.Entry e = cur.next(); e != null; e = cur.next()) {
                c.table.put(clone(r, e.key, seen), clone(r, e.value, seen));
            }
            return c;
        }
        if (o instanceof JsSet s) {
            JsSet c = new JsSet(r.setProto);
            seen.put(o, c);
            OrderedTable.Cursor cur = s.table.cursor();
            for (OrderedTable.Entry e = cur.next(); e != null; e = cur.next()) {
                Object k = clone(r, e.key, seen);
                c.table.put(k, k);
            }
            return c;
        }
        if (o instanceof JsArray a) {
            JsArray c = new JsArray(r.arrayProto, new ArrayList<Object>());
            seen.put(o, c);
            for (int i = 0; i < a.list.size(); i++) {
                Object el = a.list.get(i);
                c.list.add(el == null ? null : clone(r, el, seen));
            }
            return c;
        }
        if (o instanceof JsError) {
            JsObject c = new JsObject(o.proto);
            seen.put(o, c);
            for (String k : new String[] {"stack", "message", "cause"}) {
                Prop p = o.getOwn(k);
                if (p != null && !p.accessor) {
                    c.setHidden(k, clone(r, p.value, seen));
                }
            }
            return c;
        }
        if (o instanceof JsPromise || o instanceof JsWeak || o instanceof JsGenerator) {
            throw dataClone(r, "#<" + o.className() + "> could not be cloned.");
        }
        JsObject c = new JsObject(r.objectProto);
        seen.put(o, c);
        for (Object k : LibObject.enumerableKeys(o, true, false)) {
            c.setData(k, clone(r, o.get(k), seen));
        }
        return c;
    }

    private static JsException dataClone(Realm r, String msg) {
        JsException e = r.error("Error", msg);
        if (e.value instanceof JsObject eo) {
            eo.setHidden("name", "DataCloneError");
            eo.setHidden("code", Js.num(25));
        }
        return e;
    }

    private static JsException invalidChar(Realm r, String msg) {
        JsException e = r.error("Error", msg);
        if (e.value instanceof JsObject eo) {
            eo.setHidden("name", "InvalidCharacterError");
            eo.setHidden("code", Js.num(5));
        }
        return e;
    }

    private static void installMisc(Realm r) {
        JsObject g = r.global;
        Lib.method(r, g, "structuredClone", 1, (t, a) -> clone(r, Js.arg(a, 0), new IdentityHashMap<>()));
        Lib.method(r, g, "queueMicrotask", 1, (t, a) -> {
            JsFunction f = Lib.callable(Js.arg(a, 0), "callback");
            r.enqueue(() -> {
                try {
                    f.call(Js.UNDEFINED, Js.EMPTY);
                } catch (JsException e) {
                    r.console.print("error", "Uncaught " + Js.describeError(e.value));
                }
            });
            return Js.UNDEFINED;
        });
        Lib.method(r, g, "btoa", 1, (t, a) -> {
            String s = Js.toStr(Js.arg(a, 0));
            byte[] b = new byte[s.length()];
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) > 255) {
                    throw invalidChar(r, "Invalid character");
                }
                b[i] = (byte) s.charAt(i);
            }
            return Base64.getEncoder().encodeToString(b);
        });
        Lib.method(r, g, "atob", 1, (t, a) -> {
            String s = Js.toStr(Js.arg(a, 0)).replaceAll("[\\t\\n\\f\\r ]", "");
            if (s.length() % 4 == 0 && s.endsWith("==")) {
                s = s.substring(0, s.length() - 2);
            } else if (s.length() % 4 == 0 && s.endsWith("=")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.length() % 4 == 1 || !s.matches("[A-Za-z0-9+/]*")) {
                throw invalidChar(r, "The string to be decoded is not correctly encoded.");
            }
            byte[] b = Base64.getDecoder().decode(s);
            StringBuilder sb = new StringBuilder(b.length);
            for (byte x : b) {
                sb.append((char) (x & 0xFF));
            }
            return sb.toString();
        });
    }

    // ------------------------------------------------------------------ Intl

    private static String lang(Object locales) {
        String tag = LibDate.localeTag(locales);
        return tag.indexOf('-') > 0 ? tag.substring(0, tag.indexOf('-')) : tag;
    }

    static String localizeNumber(String enUs, String lang, String tag) {
        if (lang.equals("en")) {
            return enUs;
        }
        String group;
        String dec = ",";
        switch (lang) {
            case "de":
            case "it":
            case "nl":
            case "pt":
            case "id":
            case "tr":
            case "da":
            case "es":
                group = ".";
                break;
            case "fr":
                group = " ";
                break;
            case "ru":
            case "pl":
            case "cs":
            case "sv":
            case "nb":
            case "fi":
            case "uk":
                group = " ";
                break;
            case "ja":
            case "zh":
            case "ko":
                return enUs;
            default:
                return enUs;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : enUs.toCharArray()) {
            sb.append(c == ',' ? group : (c == '.' ? dec : c));
        }
        String out = sb.toString();
        if (lang.equals("es")) {
            // es no agrupa números de cuatro cifras
            String digits = out.replaceAll("[^0-9.,]", "");
            String intPart = digits.split(",")[0].replace(".", "");
            if (intPart.length() == 4) {
                out = out.replaceFirst("(\\d)\\.(\\d{3})", "$1$2");
            }
        }
        return out;
    }

    static String formatNumber(double d, Object locales, Object options) {
        String en = LibNumber.localeNumber(d, options);
        String lg = lang(locales);
        if (lg.equals("en")) {
            return en;
        }
        String style = options instanceof JsObject o && o.get("style") instanceof String s ? s : "decimal";
        if (style.equals("currency")) {
            // "$1,234.50" -> número + símbolo al final en la mayoría de los idiomas europeos
            int i = 0;
            boolean neg = en.startsWith("-");
            String body = neg ? en.substring(1) : en;
            while (i < body.length() && !Character.isDigit(body.charAt(i))) {
                i++;
            }
            String sym = body.substring(0, i).trim();
            String num = localizeNumber(body.substring(i), lg, LibDate.localeTag(locales));
            boolean suffix = lg.equals("de") || lg.equals("es") || lg.equals("fr") || lg.equals("it") || lg.equals("ru")
                    || lg.equals("pl");
            String res = suffix ? num + " " + sym : sym + " " + num;
            return neg ? "-" + res : res;
        }
        String res = localizeNumber(en, lg, LibDate.localeTag(locales));
        if (style.equals("percent")) {
            res = res.replace("%", "") + (lg.equals("de") || lg.equals("fr") || lg.equals("es") ? " %" : "%");
        }
        return res;
    }

    private static JsObject intlProto(Realm r, JsObject intl, String name, int len, NativeFunction.Ctor unused) {
        JsObject proto = new JsObject(r.objectProto);
        Lib.tag(proto, "Intl." + name);
        return proto;
    }

    private static void installIntl(Realm r) {
        JsObject intl = new JsObject(r.objectProto);
        r.global.setHidden("Intl", intl);
        Lib.tag(intl, "Intl");

        JsObject nfp = intlProto(r, intl, "NumberFormat", 0, (a, nt) -> null);
        // los constructores reales se definen a continuación (necesitan el prototipo)
        NativeFunction.Ctor nfMake = (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, nfp));
            o.setHidden("__locales", Js.arg(a, 0));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        };
        installIntlClass(r, intl, "NumberFormat", nfp, nfMake);
        Lib.getter(r, nfp, "format", (t, a) -> {
            JsObject self = (JsObject) t;
            return new NativeFunction(r, "", 1, (x, b) -> formatNumber(Js.toNumber(Js.arg(b, 0)), self.get("__locales"), self.get("__options")));
        });
        Lib.method(r, nfp, "resolvedOptions", 0, (t, a) -> {
            JsObject o = new JsObject(r.objectProto);
            o.setData("locale", LibDate.localeTag(((JsObject) t).get("__locales")));
            o.setData("numberingSystem", "latn");
            return o;
        });

        JsObject dfp = intlProto(r, intl, "DateTimeFormat", 0, (a, nt) -> null);
        NativeFunction.Ctor dfMake = (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, dfp));
            o.setHidden("__locales", Js.arg(a, 0));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        };
        installIntlClass(r, intl, "DateTimeFormat", dfp, dfMake);
        Lib.getter(r, dfp, "format", (t, a) -> {
            JsObject self = (JsObject) t;
            return new NativeFunction(r, "", 1, (x, b) -> {
                Object d = Js.arg(b, 0);
                double tm = d == Js.UNDEFINED ? (double) System.currentTimeMillis()
                        : (d instanceof JsDate jd ? jd.time : Js.toNumber(d));
                if (Double.isNaN(tm)) {
                    throw Js.rangeError("Invalid time value");
                }
                return LibDate.localeFormat(tm, self.get("__locales"), self.get("__options"), 1);
            });
        });
        Lib.method(r, dfp, "resolvedOptions", 0, (t, a) -> {
            JsObject o = new JsObject(r.objectProto);
            o.setData("locale", LibDate.localeTag(((JsObject) t).get("__locales")));
            o.setData("calendar", "gregory");
            o.setData("numberingSystem", "latn");
            Object opt = ((JsObject) t).get("__options");
            Object tz = opt instanceof JsObject oo ? oo.get("timeZone") : Js.UNDEFINED;
            o.setData("timeZone", tz == Js.UNDEFINED ? DateUtil.zone().getId() : tz);
            return o;
        });

        JsObject cp = intlProto(r, intl, "Collator", 0, (a, nt) -> null);
        installIntlClass(r, intl, "Collator", cp, (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, cp));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        });
        Lib.getter(r, cp, "compare", (t, a) -> {
            JsObject self = (JsObject) t;
            return new NativeFunction(r, "", 2, (x, b) -> {
                java.text.Collator c = java.text.Collator.getInstance(java.util.Locale.ENGLISH);
                Object opt = self.get("__options");
                boolean base = opt instanceof JsObject oo && "base".equals(oo.get("sensitivity"));
                c.setStrength(base ? java.text.Collator.PRIMARY : java.text.Collator.TERTIARY);
                String s1 = Js.toStr(Js.arg(b, 0));
                String s2 = Js.toStr(Js.arg(b, 1));
                if (opt instanceof JsObject oo && Js.truthy(oo.get("numeric"))) {
                    return Js.num(Integer.signum(numericCompare(c, s1, s2)));
                }
                return Js.num(Integer.signum(c.compare(s1, s2)));
            });
        });

        JsObject pp = intlProto(r, intl, "PluralRules", 0, (a, nt) -> null);
        installIntlClass(r, intl, "PluralRules", pp, (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, pp));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        });
        Lib.method(r, pp, "select", 1, (t, a) -> {
            double n = Js.toNumber(Js.arg(a, 0));
            Object opt = ((JsObject) t).get("__options");
            boolean ordinal = opt instanceof JsObject oo && "ordinal".equals(oo.get("type"));
            if (!ordinal) {
                return n == 1 ? "one" : "other";
            }
            double m10 = n % 10;
            double m100 = n % 100;
            if (m10 == 1 && m100 != 11) {
                return "one";
            }
            if (m10 == 2 && m100 != 12) {
                return "two";
            }
            if (m10 == 3 && m100 != 13) {
                return "few";
            }
            return "other";
        });

        JsObject lfp = intlProto(r, intl, "ListFormat", 0, (a, nt) -> null);
        installIntlClass(r, intl, "ListFormat", lfp, (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, lfp));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        });
        Lib.method(r, lfp, "format", 1, (t, a) -> {
            List<Object> items = Js.toList(Js.arg(a, 0));
            Object opt = ((JsObject) t).get("__options");
            String type = opt instanceof JsObject oo && oo.get("type") instanceof String s ? s : "conjunction";
            String word = type.equals("disjunction") ? "or" : "and";
            ArrayList<String> s = new ArrayList<>();
            for (Object o : items) {
                s.add(Js.toStr(o));
            }
            if (type.equals("unit")) {
                return String.join(", ", s);
            }
            if (s.size() <= 1) {
                return s.isEmpty() ? "" : s.get(0);
            }
            if (s.size() == 2) {
                return s.get(0) + " " + word + " " + s.get(1);
            }
            return String.join(", ", s.subList(0, s.size() - 1)) + ", " + word + " " + s.get(s.size() - 1);
        });

        JsObject rtp = intlProto(r, intl, "RelativeTimeFormat", 0, (a, nt) -> null);
        installIntlClass(r, intl, "RelativeTimeFormat", rtp, (a, nt) -> {
            JsObject o = new JsObject(Lib.protoFrom(nt, rtp));
            o.setHidden("__options", Js.arg(a, 1));
            return o;
        });
        Lib.method(r, rtp, "format", 2, (t, a) -> {
            double v = Js.toNumber(Js.arg(a, 0));
            String unit = Js.toStr(Js.arg(a, 1));
            if (unit.endsWith("s")) {
                unit = unit.substring(0, unit.length() - 1);
            }
            Object opt = ((JsObject) t).get("__options");
            boolean auto = opt instanceof JsObject oo && "auto".equals(oo.get("numeric"));
            if (auto) {
                if (v == 0) {
                    return unit.equals("day") ? "today" : (unit.equals("second") ? "now" : "this " + unit);
                }
                if (v == 1 && unit.equals("day")) {
                    return "tomorrow";
                }
                if (v == -1 && unit.equals("day")) {
                    return "yesterday";
                }
                if (v == 1 && !unit.equals("second") && !unit.equals("minute") && !unit.equals("hour")) {
                    return "next " + unit;
                }
                if (v == -1 && !unit.equals("second") && !unit.equals("minute") && !unit.equals("hour")) {
                    return "last " + unit;
                }
            }
            double av = Math.abs(v);
            String num = formatNumber(av, "en-US", Js.UNDEFINED);
            String u = unit + (av == 1 ? "" : "s");
            return v < 0 || (v == 0 && 1 / v < 0) ? num + " " + u + " ago" : "in " + num + " " + u;
        });
    }

    private static int numericCompare(java.text.Collator c, String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int i2 = i;
                int j2 = j;
                while (i2 < a.length() && Character.isDigit(a.charAt(i2))) {
                    i2++;
                }
                while (j2 < b.length() && Character.isDigit(b.charAt(j2))) {
                    j2++;
                }
                java.math.BigInteger p = new java.math.BigInteger(a.substring(i, i2));
                java.math.BigInteger q = new java.math.BigInteger(b.substring(j, j2));
                int cmp = p.compareTo(q);
                if (cmp != 0) {
                    return cmp;
                }
                i = i2;
                j = j2;
            } else {
                int cmp = c.compare(String.valueOf(x), String.valueOf(y));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static void installIntlClass(Realm r, JsObject intl, String name, JsObject proto, NativeFunction.Ctor make) {
        NativeFunction c = new NativeFunction(r, name, 0, (t, a) -> make.construct(a, null), make);
        c.setConst("prototype", proto);
        proto.setHidden("constructor", c);
        intl.setHidden(name, c);
        Lib.method(r, c, "supportedLocalesOf", 1, (t, a) -> Js.arg(a, 0) instanceof String s ? Lib.array(r, List.of(s))
                : (Js.arg(a, 0) instanceof JsArray ? a[0] : Lib.array(r, List.of())));
    }
}
