package com.example.beautifuldecor.browser.js;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Locale;

/** String y sus métodos. */
final class LibString {
    private static final String WS = " \t\n\u000B\f\r ﻿            "
            + "    　";

    private LibString() {
    }

    private static boolean isWs(char c) {
        return WS.indexOf(c) >= 0;
    }

    static String thisStr(Object t, String method) {
        if (t == Js.UNDEFINED || t == Js.NULL) {
            throw Js.typeError("String.prototype." + method + " called on null or undefined");
        }
        if (t instanceof String s) {
            return s;
        }
        return Js.toStr(t);
    }

    private static int clamp(Object v, int len, int dflt) {
        if (v == Js.UNDEFINED) {
            return dflt;
        }
        double d = Js.toInteger(v);
        return (int) Math.max(0, Math.min(d, len));
    }

    private static String noRegExp(Object v, String method) {
        if (v instanceof JsRegExp) {
            throw Js.typeError("First argument to String.prototype." + method + " must not be a regular expression");
        }
        return Js.toStr(v);
    }

    static String trim(String s, boolean start, boolean end) {
        int a = 0;
        int b = s.length();
        if (start) {
            while (a < b && isWs(s.charAt(a))) {
                a++;
            }
        }
        if (end) {
            while (b > a && isWs(s.charAt(b - 1))) {
                b--;
            }
        }
        return s.substring(a, b);
    }

    private static String pad(String s, Object lenArg, Object fillArg, boolean atStart) {
        double target = Js.toInteger(lenArg);
        String fill = fillArg == Js.UNDEFINED ? " " : Js.toStr(fillArg);
        if (target <= s.length() || fill.isEmpty()) {
            return s;
        }
        if (target > 536870888) {
            throw Js.rangeError("Invalid string length");
        }
        int need = (int) target - s.length();
        StringBuilder sb = new StringBuilder(need);
        while (sb.length() < need) {
            sb.append(fill);
        }
        sb.setLength(need);
        return atStart ? sb + s : s + sb;
    }

    static void install(Realm r) {
        JsObject sp = r.stringProto;
        NativeFunction sc = Lib.ctor(r, "String", 1, sp, (t, a) -> {
            if (a.length == 0) {
                return "";
            }
            if (a[0] instanceof JsSymbol sym) {
                return "Symbol(" + sym.description + ")";
            }
            return Js.toStr(a[0]);
        }, (a, nt) -> new JsWrapper(Lib.protoFrom(nt, sp), a.length == 0 ? "" : Js.toStr(a[0])));
        Lib.method(r, sc, "fromCharCode", 1, (t, a) -> {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                sb.append((char) (Js.toInt32(Js.toNumber(o)) & 0xFFFF));
            }
            return sb.toString();
        });
        Lib.method(r, sc, "fromCodePoint", 1, (t, a) -> {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                double d = Js.toNumber(o);
                if (d != Math.floor(d) || d < 0 || d > 0x10FFFF || Double.isNaN(d)) {
                    throw Js.rangeError("Invalid code point " + Js.toStr(o));
                }
                sb.appendCodePoint((int) d);
            }
            return sb.toString();
        });
        Lib.method(r, sc, "raw", 1, (t, a) -> {
            Object cooked = Js.arg(a, 0);
            Object raw = Js.getMember(r, cooked, "raw");
            long n = Lib.toLength(Js.getMember(r, raw, "length"));
            StringBuilder sb = new StringBuilder();
            for (long i = 0; i < n; i++) {
                sb.append(Js.toStr(Js.getMember(r, raw, Long.toString(i))));
                if (i + 1 < n && i + 1 < a.length) {
                    sb.append(Js.toStr(a[(int) i + 1]));
                }
            }
            return sb.toString();
        });

        Lib.method(r, sp, "toString", 0, (t, a) -> thisPrim(t, "toString"));
        Lib.method(r, sp, "valueOf", 0, (t, a) -> thisPrim(t, "valueOf"));
        Lib.method(r, sp, "charAt", 1, (t, a) -> {
            String s = thisStr(t, "charAt");
            double i = Js.toInteger(Js.arg(a, 0));
            return i < 0 || i >= s.length() ? "" : String.valueOf(s.charAt((int) i));
        });
        Lib.method(r, sp, "charCodeAt", 1, (t, a) -> {
            String s = thisStr(t, "charCodeAt");
            double i = Js.toInteger(Js.arg(a, 0));
            return i < 0 || i >= s.length() ? Js.NAN : Js.num(s.charAt((int) i));
        });
        Lib.method(r, sp, "codePointAt", 1, (t, a) -> {
            String s = thisStr(t, "codePointAt");
            double i = Js.toInteger(Js.arg(a, 0));
            return i < 0 || i >= s.length() ? Js.UNDEFINED : (Object) Js.num(s.codePointAt((int) i));
        });
        Lib.method(r, sp, "at", 1, (t, a) -> {
            String s = thisStr(t, "at");
            double i = Js.toInteger(Js.arg(a, 0));
            if (i < 0) {
                i += s.length();
            }
            return i < 0 || i >= s.length() ? Js.UNDEFINED : (Object) String.valueOf(s.charAt((int) i));
        });
        Lib.method(r, sp, "indexOf", 1, (t, a) -> {
            String s = thisStr(t, "indexOf");
            String q = Js.toStr(Js.arg(a, 0));
            return Js.num(s.indexOf(q, clamp(Js.arg(a, 1), s.length(), 0)));
        });
        Lib.method(r, sp, "lastIndexOf", 1, (t, a) -> {
            String s = thisStr(t, "lastIndexOf");
            String q = Js.toStr(Js.arg(a, 0));
            double n = Js.toNumber(Js.arg(a, 1));
            int from = Double.isNaN(n) ? s.length() : (int) Math.max(0, Math.min(Js.toInteger(n), s.length()));
            return Js.num(s.lastIndexOf(q, from));
        });
        Lib.method(r, sp, "includes", 1, (t, a) -> {
            String s = thisStr(t, "includes");
            String q = noRegExp(Js.arg(a, 0), "includes");
            return s.indexOf(q, clamp(Js.arg(a, 1), s.length(), 0)) >= 0;
        });
        Lib.method(r, sp, "startsWith", 1, (t, a) -> {
            String s = thisStr(t, "startsWith");
            String q = noRegExp(Js.arg(a, 0), "startsWith");
            return s.startsWith(q, clamp(Js.arg(a, 1), s.length(), 0));
        });
        Lib.method(r, sp, "endsWith", 1, (t, a) -> {
            String s = thisStr(t, "endsWith");
            String q = noRegExp(Js.arg(a, 0), "endsWith");
            int end = clamp(Js.arg(a, 1), s.length(), s.length());
            return end - q.length() >= 0 && s.startsWith(q, end - q.length());
        });
        Lib.method(r, sp, "slice", 2, (t, a) -> {
            String s = thisStr(t, "slice");
            int from = Js.relIndex(Js.arg(a, 0), s.length(), 0);
            int to = Js.relIndex(Js.arg(a, 1), s.length(), s.length());
            return from >= to ? "" : s.substring(from, to);
        });
        Lib.method(r, sp, "substring", 2, (t, a) -> {
            String s = thisStr(t, "substring");
            int from = clamp(Js.arg(a, 0), s.length(), 0);
            int to = clamp(Js.arg(a, 1), s.length(), s.length());
            return s.substring(Math.min(from, to), Math.max(from, to));
        });
        Lib.method(r, sp, "substr", 2, (t, a) -> {
            String s = thisStr(t, "substr");
            int from = Js.relIndex(Js.arg(a, 0), s.length(), 0);
            double len = Js.arg(a, 1) == Js.UNDEFINED ? s.length() : Js.toInteger(Js.arg(a, 1));
            int to = (int) Math.min(from + Math.max(len, 0), s.length());
            return from >= to ? "" : s.substring(from, to);
        });
        Lib.method(r, sp, "toUpperCase", 0, (t, a) -> thisStr(t, "toUpperCase").toUpperCase(Locale.ROOT));
        Lib.method(r, sp, "toLowerCase", 0, (t, a) -> thisStr(t, "toLowerCase").toLowerCase(Locale.ROOT));
        Lib.method(r, sp, "toLocaleUpperCase", 0, (t, a) -> thisStr(t, "toLocaleUpperCase").toUpperCase(Locale.ROOT));
        Lib.method(r, sp, "toLocaleLowerCase", 0, (t, a) -> thisStr(t, "toLocaleLowerCase").toLowerCase(Locale.ROOT));
        Lib.method(r, sp, "trim", 0, (t, a) -> trim(thisStr(t, "trim"), true, true));
        NativeFunction ts = new NativeFunction(r, "trimStart", 0, (t, a) -> trim(thisStr(t, "trimStart"), true, false));
        NativeFunction te = new NativeFunction(r, "trimEnd", 0, (t, a) -> trim(thisStr(t, "trimEnd"), false, true));
        sp.setHidden("trimStart", ts);
        sp.setHidden("trimLeft", ts);
        sp.setHidden("trimEnd", te);
        sp.setHidden("trimRight", te);
        Lib.method(r, sp, "padStart", 1, (t, a) -> pad(thisStr(t, "padStart"), Js.arg(a, 0), Js.arg(a, 1), true));
        Lib.method(r, sp, "padEnd", 1, (t, a) -> pad(thisStr(t, "padEnd"), Js.arg(a, 0), Js.arg(a, 1), false));
        Lib.method(r, sp, "repeat", 1, (t, a) -> {
            String s = thisStr(t, "repeat");
            double n = Js.toInteger(Js.arg(a, 0));
            if (n < 0 || Double.isInfinite(n)) {
                throw Js.rangeError("Invalid count value: " + Js.toStr(Js.arg(a, 0)));
            }
            if (n * s.length() > 536870888) {
                throw Js.rangeError("Invalid string length");
            }
            return s.repeat((int) n);
        });
        Lib.method(r, sp, "concat", 1, (t, a) -> {
            StringBuilder sb = new StringBuilder(thisStr(t, "concat"));
            for (Object o : a) {
                sb.append(Js.toStr(o));
            }
            return sb.toString();
        });
        Lib.method(r, sp, "split", 2, (t, a) -> LibRegExp.split(r, thisStr(t, "split"), Js.arg(a, 0), Js.arg(a, 1)));
        Lib.method(r, sp, "match", 1, (t, a) -> LibRegExp.match(r, thisStr(t, "match"), Js.arg(a, 0)));
        Lib.method(r, sp, "matchAll", 1, (t, a) -> LibRegExp.matchAll(r, thisStr(t, "matchAll"), Js.arg(a, 0)));
        Lib.method(r, sp, "search", 1, (t, a) -> LibRegExp.search(r, thisStr(t, "search"), Js.arg(a, 0)));
        Lib.method(r, sp, "replace", 2, (t, a) -> LibRegExp.replace(r, thisStr(t, "replace"), Js.arg(a, 0), Js.arg(a, 1), false));
        Lib.method(r, sp, "replaceAll", 2, (t, a) -> LibRegExp.replace(r, thisStr(t, "replaceAll"), Js.arg(a, 0), Js.arg(a, 1), true));
        Lib.method(r, sp, "localeCompare", 1, (t, a) -> {
            String s = thisStr(t, "localeCompare");
            String o = Js.toStr(Js.arg(a, 0));
            Collator c = Collator.getInstance(Locale.ENGLISH);
            c.setStrength(Collator.TERTIARY);
            int v = c.compare(s, o);
            return Js.num(Integer.compare(v, 0));
        });
        Lib.method(r, sp, "normalize", 0, (t, a) -> {
            String s = thisStr(t, "normalize");
            String form = Js.arg(a, 0) == Js.UNDEFINED ? "NFC" : Js.toStr(Js.arg(a, 0));
            switch (form) {
                case "NFC":
                    return Normalizer.normalize(s, Normalizer.Form.NFC);
                case "NFD":
                    return Normalizer.normalize(s, Normalizer.Form.NFD);
                case "NFKC":
                    return Normalizer.normalize(s, Normalizer.Form.NFKC);
                case "NFKD":
                    return Normalizer.normalize(s, Normalizer.Form.NFKD);
                default:
                    throw Js.rangeError("The normalization form should be one of NFC, NFD, NFKC, NFKD.");
            }
        });
        Lib.method(r, sp, "isWellFormed", 0, (t, a) -> {
            String s = thisStr(t, "isWellFormed");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    i++;
                } else if (Character.isSurrogate(c)) {
                    return false;
                }
            }
            return true;
        });
        Lib.method(r, sp, "toWellFormed", 0, (t, a) -> {
            String s = thisStr(t, "toWellFormed");
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    sb.append(c).append(s.charAt(++i));
                } else if (Character.isSurrogate(c)) {
                    sb.append('\uFFFD');
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
        String[][] html = {{"anchor", "a", "name"}, {"big", "big", null}, {"blink", "blink", null}, {"bold", "b", null},
            {"fixed", "tt", null}, {"fontcolor", "font", "color"}, {"fontsize", "font", "size"}, {"italics", "i", null},
            {"link", "a", "href"}, {"small", "small", null}, {"strike", "strike", null}, {"sub", "sub", null}, {"sup", "sup", null}};
        for (String[] h : html) {
            Lib.method(r, sp, h[0], h[2] == null ? 0 : 1, (t, a) -> {
                String s = thisStr(t, h[0]);
                StringBuilder sb = new StringBuilder("<").append(h[1]);
                if (h[2] != null) {
                    sb.append(' ').append(h[2]).append("=\"").append(Js.toStr(Js.arg(a, 0)).replace("\"", "&quot;")).append('"');
                }
                return sb.append('>').append(s).append("</").append(h[1]).append('>').toString();
            });
        }
        JsObject si = LibIter.makeProto(r, "String Iterator");
        Lib.method(r, sp, JsSymbol.ITERATOR, "[Symbol.iterator]", 0, (t, a) -> {
            String s = thisStr(t, "[Symbol.iterator]");
            IterRec it = new IterRec() {
                private int pos;

                @Override
                public Object next() {
                    if (this.pos >= s.length()) {
                        return DONE;
                    }
                    int cp = s.codePointAt(this.pos);
                    int n = Character.charCount(cp);
                    String out = s.substring(this.pos, this.pos + n);
                    this.pos += n;
                    return out;
                }
            };
            return new JsIterObject(si, it, "String Iterator");
        });
    }

    private static Object thisPrim(Object t, String m) {
        if (t instanceof String) {
            return t;
        }
        if (t instanceof JsWrapper w && w.prim instanceof String) {
            return w.prim;
        }
        throw Js.typeError("String.prototype." + m + " requires that 'this' be a String");
    }
}
