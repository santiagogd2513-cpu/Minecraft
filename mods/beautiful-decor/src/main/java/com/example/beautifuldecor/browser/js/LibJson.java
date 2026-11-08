package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** JSON.parse y JSON.stringify. */
final class LibJson {
    private LibJson() {
    }

    // ------------------------------------------------------------------ parse

    private static final class Reader {
        final Realm r;
        final String s;
        int p;

        Reader(Realm r, String s) {
            this.r = r;
            this.s = s;
        }

        /** Posición legible como la da V8: "at position 5 (line 1 column 6)". */
        String loc(int pos) {
            int line = 1;
            int lineStart = 0;
            for (int i = 0; i < pos && i < this.s.length(); i++) {
                char c = this.s.charAt(i);
                if (c == '\n' || (c == '\r' && !(i + 1 < this.s.length() && this.s.charAt(i + 1) == '\n'))) {
                    line++;
                    lineStart = i + 1;
                }
            }
            return "at position " + pos + " (line " + line + " column " + (pos - lineStart + 1) + ")";
        }

        JsException errAt(String what, int pos) {
            return Js.syntaxError(what + " in JSON " + loc(pos));
        }

        JsException fail() {
            int n = this.s.length();
            if (this.p >= n) {
                return Js.syntaxError("Unexpected end of JSON input");
            }
            char c = this.s.charAt(this.p);
            if (c == '"') {
                return errAt("Unexpected string", this.p);
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return errAt("Unexpected number", this.p);
            }
            if (this.s.equals("NaN") || this.s.equals("Infinity") || this.s.equals("undefined") || this.s.equals("[object Object]")) {
                return Js.syntaxError("\"" + this.s + "\" is not valid JSON");
            }
            String head = "Unexpected token '" + c + "', ";
            if (n < 21) {
                return Js.syntaxError(head + "\"" + this.s + "\" is not valid JSON");
            }
            if (this.p < 10) {
                return Js.syntaxError(head + "\"" + this.s.substring(0, this.p + 10) + "\"... is not valid JSON");
            }
            if (this.p < n - 10) {
                return Js.syntaxError(head + "...\"" + this.s.substring(this.p - 10, this.p + 10) + "\"... is not valid JSON");
            }
            return Js.syntaxError(head + "...\"" + this.s.substring(this.p - 10) + "\" is not valid JSON");
        }

        private static int hexDigit(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            return -1;
        }

        void ws() {
            while (this.p < this.s.length()) {
                char c = this.s.charAt(this.p);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    this.p++;
                } else {
                    break;
                }
            }
        }

        Object value(int depth) {
            if (depth > 2000) {
                throw Js.rangeError("Maximum call stack size exceeded");
            }
            ws();
            if (this.p >= this.s.length()) {
                throw fail();
            }
            char c = this.s.charAt(this.p);
            switch (c) {
                case '{': {
                    this.p++;
                    JsObject o = new JsObject(this.r.objectProto);
                    ws();
                    if (this.p < this.s.length() && this.s.charAt(this.p) == '}') {
                        this.p++;
                        return o;
                    }
                    boolean first = true;
                    while (true) {
                        ws();
                        if (this.p >= this.s.length() || this.s.charAt(this.p) != '"') {
                            throw errAt(first ? "Expected property name or '}'" : "Expected double-quoted property name", this.p);
                        }
                        first = false;
                        String k = string();
                        ws();
                        if (this.p >= this.s.length() || this.s.charAt(this.p) != ':') {
                            throw errAt("Expected ':' after property name", this.p);
                        }
                        this.p++;
                        Object v = value(depth + 1);
                        o.defineOwn(k, new Prop(v, Prop.ALL));
                        ws();
                        char d = this.p < this.s.length() ? this.s.charAt(this.p) : 0;
                        if (d == ',') {
                            this.p++;
                        } else if (d == '}') {
                            this.p++;
                            return o;
                        } else {
                            throw errAt("Expected ',' or '}' after property value", this.p);
                        }
                    }
                }
                case '[': {
                    this.p++;
                    ArrayList<Object> items = new ArrayList<>();
                    ws();
                    if (this.p < this.s.length() && this.s.charAt(this.p) == ']') {
                        this.p++;
                        return new JsArray(this.r.arrayProto, items);
                    }
                    while (true) {
                        items.add(value(depth + 1));
                        ws();
                        char d = this.p < this.s.length() ? this.s.charAt(this.p) : 0;
                        if (d == ',') {
                            this.p++;
                        } else if (d == ']') {
                            this.p++;
                            return new JsArray(this.r.arrayProto, items);
                        } else {
                            throw errAt("Expected ',' or ']' after array element", this.p);
                        }
                    }
                }
                case '"':
                    return string();
                case 't':
                    return lit("true", Boolean.TRUE);
                case 'f':
                    return lit("false", Boolean.FALSE);
                case 'n':
                    return lit("null", Js.NULL);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
                    throw fail();
            }
        }

        Object lit(String word, Object v) {
            for (int i = 0; i < word.length(); i++) {
                if (this.p + i >= this.s.length() || this.s.charAt(this.p + i) != word.charAt(i)) {
                    this.p += i;
                    throw fail();
                }
            }
            this.p += word.length();
            return v;
        }

        Object number() {
            int start = this.p;
            if (this.s.charAt(this.p) == '-') {
                this.p++;
            }
            char c = this.p < this.s.length() ? this.s.charAt(this.p) : 0;
            if (c == '0') {
                this.p++;
                if (this.p < this.s.length() && this.s.charAt(this.p) >= '0' && this.s.charAt(this.p) <= '9') {
                    throw errAt("Unexpected number", this.p);
                }
            } else if (c >= '1' && c <= '9') {
                while (this.p < this.s.length() && this.s.charAt(this.p) >= '0' && this.s.charAt(this.p) <= '9') {
                    this.p++;
                }
            } else {
                throw errAt("No number after minus sign", this.p);
            }
            if (this.p < this.s.length() && this.s.charAt(this.p) == '.') {
                this.p++;
                int d = this.p;
                while (this.p < this.s.length() && this.s.charAt(this.p) >= '0' && this.s.charAt(this.p) <= '9') {
                    this.p++;
                }
                if (d == this.p) {
                    throw errAt("Unterminated fractional number", this.p);
                }
            }
            if (this.p < this.s.length() && (this.s.charAt(this.p) == 'e' || this.s.charAt(this.p) == 'E')) {
                this.p++;
                if (this.p < this.s.length() && (this.s.charAt(this.p) == '+' || this.s.charAt(this.p) == '-')) {
                    this.p++;
                }
                int d = this.p;
                while (this.p < this.s.length() && this.s.charAt(this.p) >= '0' && this.s.charAt(this.p) <= '9') {
                    this.p++;
                }
                if (d == this.p) {
                    throw errAt("Exponent part is missing a number", this.p);
                }
            }
            return Js.num(Double.parseDouble(this.s.substring(start, this.p)));
        }

        String string() {
            this.p++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (this.p >= this.s.length()) {
                    throw errAt("Unterminated string", this.p);
                }
                char c = this.s.charAt(this.p++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    this.p--;
                    throw errAt("Bad control character in string literal", this.p);
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (this.p >= this.s.length()) {
                    throw fail();
                }
                char e = this.s.charAt(this.p++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u': {
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = this.p + i < this.s.length() ? hexDigit(this.s.charAt(this.p + i)) : -1;
                            if (d < 0) {
                                throw errAt("Bad Unicode escape", Math.min(this.p + i, this.s.length()));
                            }
                            v = v * 16 + d;
                        }
                        this.p += 4;
                        sb.append((char) v);
                        break;
                    }
                    default:
                        this.p--;
                        throw errAt("Bad escaped character", this.p);
                }
            }
        }
    }

    static Object parse(Realm r, String text) {
        Reader rd = new Reader(r, text);
        Object v = rd.value(0);
        rd.ws();
        if (rd.p < text.length()) {
            throw Js.syntaxError("Unexpected non-whitespace character after JSON " + rd.loc(rd.p));
        }
        return v;
    }

    private static Object revive(Realm r, JsObject holder, String key, JsFunction reviver) {
        Object val = holder.get(key);
        if (val instanceof JsArray arr) {
            for (int i = 0; i < arr.list.size(); i++) {
                String k = Integer.toString(i);
                Object nv = revive(r, arr, k, reviver);
                if (nv == Js.UNDEFINED) {
                    arr.deleteOwn(k);
                } else {
                    arr.defineOwn(k, new Prop(nv, Prop.ALL));
                }
            }
        } else if (val instanceof JsObject o) {
            for (Object k : o.ownKeys()) {
                if (k instanceof String ks) {
                    Prop p = o.getOwn(ks);
                    if (p == null || (p.flags & Prop.E) == 0) {
                        continue;
                    }
                    Object nv = revive(r, o, ks, reviver);
                    if (nv == Js.UNDEFINED) {
                        o.deleteOwn(ks);
                    } else {
                        o.defineOwn(ks, new Prop(nv, Prop.ALL));
                    }
                }
            }
        }
        return reviver.call(holder, new Object[] {key, val});
    }

    // ------------------------------------------------------------------ stringify

    static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                        sb.append(c).append(s.charAt(i + 1));
                        i++;
                    } else if (Character.isSurrogate(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static final class Writer {
        final Realm r;
        JsFunction replacer;
        List<String> allow;
        String gap = "";
        final ArrayList<Object> stack = new ArrayList<>();

        Writer(Realm r) {
            this.r = r;
        }

        /** Devuelve null si el valor no se puede serializar (undefined, función, símbolo). */
        String ser(JsObject holder, String key, Object value, String indent) {
            Object v = value;
            if (v instanceof JsObject) {
                Object tj = Js.getMember(this.r, v, "toJSON");
                if (tj instanceof JsFunction f) {
                    v = f.call(v, new Object[] {key});
                }
            }
            if (this.replacer != null) {
                v = this.replacer.call(holder, new Object[] {key, v});
            }
            if (v instanceof JsWrapper w) {
                if (w.prim instanceof Double) {
                    v = Js.num(Js.toNumber(v));
                } else if (w.prim instanceof String) {
                    v = Js.toStr(v);
                } else if (w.prim instanceof Boolean) {
                    v = w.prim;
                }
            }
            if (v == Js.NULL) {
                return "null";
            }
            if (v instanceof Boolean b) {
                return b ? "true" : "false";
            }
            if (v instanceof String s) {
                return quote(s);
            }
            if (v instanceof Number n) {
                double d = n.doubleValue();
                return Double.isNaN(d) || Double.isInfinite(d) ? "null" : Js.numToString(d);
            }
            if (v instanceof JsObject o && !(v instanceof JsFunction)) {
                for (Object s : this.stack) {
                    if (s == o) {
                        throw Js.typeError("Converting circular structure to JSON\n    --> starting at object with constructor 'Object'\n    --- property '"
                                + key + "' closes the circle");
                    }
                }
                this.stack.add(o);
                try {
                    return o instanceof JsArray a ? array(a, indent) : object(o, indent);
                } finally {
                    this.stack.remove(this.stack.size() - 1);
                }
            }
            return null;
        }

        String array(JsArray a, String indent) {
            int n = a.list.size();
            if (n == 0) {
                return "[]";
            }
            String inner = indent + this.gap;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                if (!this.gap.isEmpty()) {
                    sb.append('\n').append(inner);
                }
                Object el = a.get(Integer.toString(i));
                String s = ser(a, Integer.toString(i), el, inner);
                sb.append(s == null ? "null" : s);
            }
            if (!this.gap.isEmpty()) {
                sb.append('\n').append(indent);
            }
            return sb.append(']').toString();
        }

        String object(JsObject o, String indent) {
            List<String> keys = new ArrayList<>();
            if (this.allow != null) {
                keys.addAll(this.allow);
            } else {
                for (Object k : o.ownKeys()) {
                    if (k instanceof String ks) {
                        Prop p = o.getOwn(ks);
                        if (p != null && (p.flags & Prop.E) != 0) {
                            keys.add(ks);
                        }
                    }
                }
            }
            String inner = indent + this.gap;
            StringBuilder sb = new StringBuilder("{");
            boolean any = false;
            for (String k : keys) {
                String s = ser(o, k, o.get(k), inner);
                if (s == null) {
                    continue;
                }
                if (any) {
                    sb.append(',');
                }
                any = true;
                if (!this.gap.isEmpty()) {
                    sb.append('\n').append(inner);
                }
                sb.append(quote(k)).append(':');
                if (!this.gap.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(s);
            }
            if (!any) {
                return "{}";
            }
            if (!this.gap.isEmpty()) {
                sb.append('\n').append(indent);
            }
            return sb.append('}').toString();
        }
    }

    static Object stringify(Realm r, Object value, Object replacer, Object space) {
        Writer w = new Writer(r);
        if (replacer instanceof JsFunction f) {
            w.replacer = f;
        } else if (replacer instanceof JsArray ra) {
            w.allow = new ArrayList<>();
            for (int i = 0; i < ra.list.size(); i++) {
                Object e = ra.get(Integer.toString(i));
                String k = null;
                if (e instanceof String s) {
                    k = s;
                } else if (e instanceof Number) {
                    k = Js.toStr(e);
                } else if (e instanceof JsWrapper) {
                    k = Js.toStr(e);
                }
                if (k != null && !w.allow.contains(k)) {
                    w.allow.add(k);
                }
            }
        }
        Object sp = space;
        if (sp instanceof JsWrapper sw) {
            sp = sw.prim instanceof String ? Js.toStr(sp) : (sw.prim instanceof Double ? (Object) Js.num(Js.toNumber(sp)) : sp);
        }
        if (sp instanceof Number n) {
            int k = (int) Math.max(0, Math.min(10, Js.toInteger(n)));
            w.gap = " ".repeat(k);
        } else if (sp instanceof String s) {
            w.gap = s.length() > 10 ? s.substring(0, 10) : s;
        }
        JsObject holder = new JsObject(r.objectProto);
        holder.setData("", value);
        String out;
        try {
            out = w.ser(holder, "", value, "");
        } catch (StackOverflowError e) {
            throw Js.rangeError("Maximum call stack size exceeded");
        }
        return out == null ? Js.UNDEFINED : out;
    }

    static void install(Realm r) {
        JsObject json = new JsObject(r.objectProto);
        Lib.tag(json, "JSON");
        r.global.setHidden("JSON", json);
        Lib.method(r, json, "parse", 2, (t, a) -> {
            Object v = parse(r, Js.toStr(Js.arg(a, 0)));
            if (Js.arg(a, 1) instanceof JsFunction rev) {
                JsObject holder = new JsObject(r.objectProto);
                holder.setData("", v);
                return revive(r, holder, "", rev);
            }
            return v;
        });
        Lib.method(r, json, "stringify", 3, (t, a) -> stringify(r, Js.arg(a, 0), Js.arg(a, 1), Js.arg(a, 2)));
    }
}
