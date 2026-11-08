package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Texto legible de un valor, al estilo de util.inspect de Node. */
final class Inspect {
    private static final int ARRAY_EXTRAS = 1;
    private static final int OBJECT_EXTRAS = 0;
    private static final int MAX_ARRAY = 100;

    private final Realm r;
    private final int depth;
    private final int breakLength = 80;
    private final ArrayList<Object> seen = new ArrayList<>();
    private final IdentityHashMap<Object, Integer> circular = new IdentityHashMap<>();
    private int indentationLvl;
    private int currentDepth;
    private boolean showHidden;

    private Inspect(Realm r, int depth) {
        this.r = r;
        this.depth = depth;
    }

    static String inspect(Realm r, Object v, int depth) {
        return new Inspect(r, depth).format(v, 0);
    }

    /** Como {@link #inspect} pero mostrando también las propiedades no enumerables (para %o). */
    static String inspectHidden(Realm r, Object v, int depth) {
        Inspect in = new Inspect(r, depth);
        in.showHidden = true;
        return in.format(v, 0);
    }

    // ------------------------------------------------------------------ primitivos

    static String quote(String s) {
        char q = '\'';
        if (s.indexOf('\'') >= 0) {
            if (s.indexOf('"') < 0) {
                q = '"';
            } else if (s.indexOf('`') < 0 && !s.contains("${")) {
                q = '`';
            }
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append(q);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == q || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\b') {
                sb.append("\\b");
            } else if (c == '\f') {
                sb.append("\\f");
            } else if (c == 0x0B) {
                sb.append("\\v");
            } else if (c < 0x20 || (c >= 0x7f && c <= 0x9f)) {
                sb.append(String.format("\\x%02X", (int) c));
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                sb.append(c).append(s.charAt(++i));
            } else if (Character.isSurrogate(c)) {
                sb.append(String.format("\\u%04X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append(q).toString();
    }

    private String primitive(Object v) {
        if (v instanceof String s) {
            if (s.length() > 16 && s.length() > this.breakLength - this.indentationLvl - 4 && s.indexOf('\n') >= 0
                    && s.indexOf('\n') < s.length() - 1) {
                ArrayList<String> lines = new ArrayList<>();
                int from = 0;
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) == '\n') {
                        lines.add(s.substring(from, i + 1));
                        from = i + 1;
                    }
                }
                if (from < s.length()) {
                    lines.add(s.substring(from));
                }
                StringBuilder sb = new StringBuilder();
                String sep = " +\n" + " ".repeat(this.indentationLvl + 2);
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) {
                        sb.append(sep);
                    }
                    sb.append(quote(lines.get(i)));
                }
                return sb.toString();
            }
            return quote(s);
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == 0 && 1 / d < 0) {
                return "-0";
            }
            return Js.numToString(d);
        }
        if (v instanceof JsSymbol sym) {
            return "Symbol(" + (sym.description == null ? "" : sym.description) + ")";
        }
        return Js.toStr(v);
    }

    // ------------------------------------------------------------------ objetos

    private static String fnName(Object f) {
        if (f instanceof JsFunction fn) {
            Object n = fn.get("name");
            return n instanceof String s ? s : "";
        }
        return "";
    }

    private String ctorName(JsObject o) {
        for (JsObject obj = o; obj != null; obj = obj.proto) {
            Prop d = obj.getOwn("constructor");
            if (d != null && !d.accessor && d.value instanceof JsFunction f && !fnName(f).isEmpty()) {
                try {
                    if (Js.ordinaryHasInstance(f, o)) {
                        return fnName(f);
                    }
                } catch (JsException e) {
                    // se ignora: se sigue buscando
                }
            }
        }
        return null;
    }

    private static String prefix(String ctor, String tag, String fallback, String size) {
        if (ctor == null) {
            if (!tag.isEmpty() && !fallback.equals(tag)) {
                return "[" + fallback + size + ": null prototype] [" + tag + "] ";
            }
            return "[" + fallback + size + ": null prototype] ";
        }
        if (!tag.isEmpty() && !ctor.equals(tag)) {
            return ctor + size + " [" + tag + "] ";
        }
        return ctor + size + " ";
    }

    private String format(Object v, int recurseTimes) {
        if (!(v instanceof JsObject o)) {
            return primitive(v);
        }
        for (Object s : this.seen) {
            if (s == o) {
                Integer idx = this.circular.get(o);
                if (idx == null) {
                    idx = this.circular.size() + 1;
                    this.circular.put(o, idx);
                }
                return "[Circular *" + idx + "]";
            }
        }
        return raw(o, recurseTimes);
    }

    private List<Object> keysOf(JsObject o, boolean skipIndices) {
        ArrayList<Object> out = new ArrayList<>();
        for (Object k : o.ownKeys()) {
            if (k instanceof String ks) {
                if (skipIndices && Js.arrayIndex(ks) >= 0) {
                    continue;
                }
                Prop p = o.getOwn(ks);
                if (p != null && (p.enumerable() || this.showHidden)) {
                    out.add(ks);
                }
            } else if (k instanceof JsSymbol) {
                Prop p = o.getOwn(k);
                if (p != null && (p.enumerable() || this.showHidden)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    private String functionBase(JsFunction f, String ctor, String tag) {
        if (f instanceof Closure c && c.node.isClassCtor) {
            StringBuilder sb = new StringBuilder("[class");
            String name = fnName(f);
            sb.append(name.isEmpty() ? " (anonymous)" : " " + name);
            if (ctor != null && !ctor.equals("Function")) {
                sb.append(" [").append(ctor).append(']');
            }
            if (!tag.isEmpty() && (ctor == null || !ctor.equals(tag))) {
                sb.append(" [").append(tag).append(']');
            }
            if (ctor != null) {
                String sup = f.proto == null ? "" : fnName(f.proto);
                if (!sup.isEmpty()) {
                    sb.append(" extends ").append(sup);
                }
            } else {
                sb.append(" extends [null prototype]");
            }
            return sb.append(']').toString();
        }
        String type = "Function";
        if (f instanceof Closure c) {
            if (c.node.isAsync && c.node.isGen) {
                type = "AsyncGeneratorFunction";
            } else if (c.node.isAsync) {
                type = "AsyncFunction";
            } else if (c.node.isGen) {
                type = "GeneratorFunction";
            }
        }
        StringBuilder sb = new StringBuilder("[").append(type);
        if (ctor == null) {
            sb.append(" (null prototype)");
        }
        String name = fnName(f);
        sb.append(name.isEmpty() ? " (anonymous)" : ": " + name).append(']');
        if (ctor != null && !ctor.equals(type) && !ctor.equals("Function")) {
            sb.append(' ').append(ctor);
        }
        if (!tag.isEmpty() && (ctor == null || !ctor.equals(tag))) {
            sb.append(" [").append(tag).append(']');
        }
        return sb.toString();
    }

    private String errorText(JsObject e) {
        Object st = e.get("stack");
        String text;
        if (st instanceof String s && !s.isEmpty()) {
            text = s;
            int at = text.indexOf("\n    at <anonymous>");
            if (at >= 0) {
                text = text.substring(0, at) + text.substring(at + "\n    at <anonymous>".length());
            }
        } else {
            text = Js.describeError(e);
        }
        return text;
    }

    private String raw(JsObject o, int recurseTimes) {
        String ctor = ctorName(o);
        String tag = "";
        try {
            Object t = o.get(JsSymbol.TO_STRING_TAG);
            Prop own = o.getOwn(JsSymbol.TO_STRING_TAG);
            if (t instanceof String ts && !ts.isEmpty() && !(own != null && own.enumerable())) {
                tag = ts;
            }
        } catch (JsException e) {
            // etiqueta ilegible: se omite
        }
        if (ctor != null && ctor.equals(tag)) {
            tag = "";
        }
        String base = "";
        String[] braces = {"{", "}"};
        int extras = OBJECT_EXTRAS;
        List<Object> keys;
        int kind = 0; // 1 arreglo, 2 Set, 3 Map, 4 Promise, 5 débil, 6 arguments
        if (o instanceof JsArguments) {
            keys = keysOf(o, false);
            braces[0] = "[Arguments] {";
            if (keys.isEmpty()) {
                return "[Arguments] {}";
            }
        } else if (o instanceof JsArray a) {
            keys = keysOf(o, true);
            String p = !"Array".equals(ctor) || !tag.isEmpty() ? prefix(ctor, tag, "Array", "(" + a.list.size() + ")") : "";
            braces[0] = p + "[";
            braces[1] = "]";
            if (a.list.isEmpty() && keys.isEmpty()) {
                return braces[0] + "]";
            }
            extras = ARRAY_EXTRAS;
            kind = 1;
        } else if (o instanceof JsSet s) {
            keys = keysOf(o, false);
            String p = prefix(ctor, tag, "Set", "(" + s.table.size() + ")");
            if (s.table.size() == 0 && keys.isEmpty()) {
                return p + "{}";
            }
            braces[0] = p + "{";
            kind = 2;
        } else if (o instanceof JsMap m) {
            keys = keysOf(o, false);
            String p = prefix(ctor, tag, "Map", "(" + m.table.size() + ")");
            if (m.table.size() == 0 && keys.isEmpty()) {
                return p + "{}";
            }
            braces[0] = p + "{";
            kind = 3;
        } else {
            keys = keysOf(o, o instanceof JsWrapper w && w.prim instanceof String);
            if (o instanceof JsFunction f) {
                base = functionBase(f, ctor, tag);
                if (keys.isEmpty()) {
                    return base;
                }
            } else if (o instanceof JsRegExp re) {
                base = "/" + re.source + "/" + re.flags;
                if (keys.size() > 0 && keys.contains("lastIndex")) {
                    keys.remove("lastIndex");
                }
                if (keys.isEmpty()) {
                    return base;
                }
            } else if (o instanceof JsDate d) {
                base = Double.isNaN(d.time) ? "Invalid Date" : DateUtil.iso(d.time);
                if (keys.isEmpty()) {
                    return base;
                }
            } else if (o instanceof JsError) {
                base = errorText(o);
                if (base.contains("\n    at")) {
                    if (this.indentationLvl > 0) {
                        base = base.replace("\n", "\n" + " ".repeat(this.indentationLvl));
                    }
                } else if (recurseTimes > 0 || this.indentationLvl > 0) {
                    base = "[" + base + "]";
                }
                keys.remove("stack");
                keys.remove("message");
                if (keys.isEmpty()) {
                    return base;
                }
            } else if (o instanceof JsWrapper w) {
                String type = w.prim instanceof String ? "String" : (w.prim instanceof Boolean ? "Boolean"
                        : (w.prim instanceof JsSymbol ? "Symbol" : "Number"));
                base = "[" + type + (ctor != null && !ctor.equals(type) ? " (" + ctor + ")" : "") + ": " + primitive(w.prim) + "]";
                if (keys.isEmpty()) {
                    return base;
                }
            } else if (o instanceof JsIterObject io && io.preview != null) {
                String type = io.className().startsWith("Map") ? "Map" : "Set";
                braces[0] = "[" + type + (io.pairs ? " Entries" : " Iterator") + "] {";
                kind = 7;
            } else if (o instanceof JsPromise) {
                braces[0] = prefix(ctor, tag, "Promise", "") + "{";
                kind = 4;
            } else if (o instanceof JsWeak wk) {
                if (wk.kind.equals("WeakRef")) {
                    braces[0] = prefix(ctor, tag, "WeakRef", "") + "{";
                } else {
                    return prefix(ctor, tag, wk.kind, "") + "{ <items unknown> }";
                }
            } else if ("Object".equals(ctor)) {
                if (!tag.isEmpty()) {
                    braces[0] = prefix(ctor, tag, "Object", "") + "{";
                }
                if (keys.isEmpty()) {
                    return braces[0] + "}";
                }
            } else {
                braces[0] = prefix(ctor, tag, "Object", "") + "{";
                if (keys.isEmpty()) {
                    return braces[0] + "}";
                }
            }
        }
        if (recurseTimes > this.depth) {
            String name = ctor == null ? "Object: null prototype" : ctor;
            if (o instanceof JsArray && !(o instanceof JsArguments)) {
                name = ctor == null ? "Array: null prototype" : ctor;
            }
            if (!tag.isEmpty() && (ctor == null || !ctor.equals(tag))) {
                name += " [" + tag + "]";
            }
            return "[" + name + "]";
        }
        int level = recurseTimes + 1;
        this.seen.add(o);
        this.currentDepth = level;
        ArrayList<String> output = new ArrayList<>();
        try {
            switch (kind) {
                case 1:
                    formatArray((JsArray) o, level, output);
                    break;
                case 2:
                    this.indentationLvl += 2;
                    OrderedTable.Cursor cs = ((JsSet) o).table.cursor();
                    for (OrderedTable.Entry e = cs.next(); e != null; e = cs.next()) {
                        output.add(format(e.key, level));
                    }
                    this.indentationLvl -= 2;
                    break;
                case 3:
                    this.indentationLvl += 2;
                    OrderedTable.Cursor cm = ((JsMap) o).table.cursor();
                    for (OrderedTable.Entry e = cm.next(); e != null; e = cm.next()) {
                        output.add(format(e.key, level) + " => " + format(e.value, level));
                    }
                    this.indentationLvl -= 2;
                    break;
                case 7:
                    this.indentationLvl += 2;
                    for (Object item : ((JsIterObject) o).preview.get()) {
                        output.add(format(item, level));
                    }
                    this.indentationLvl -= 2;
                    break;
                case 4: {
                    JsPromise p = (JsPromise) o;
                    if (p.state == JsPromise.PENDING) {
                        output.add("<pending>");
                    } else {
                        this.indentationLvl += 2;
                        String str = format(p.value, level);
                        this.indentationLvl -= 2;
                        output.add(p.state == JsPromise.REJECTED ? "<rejected> " + str : str);
                    }
                    break;
                }
                default:
                    break;
            }
            for (Object k : keys) {
                output.add(property(o, level, k, extras));
            }
        } finally {
            this.seen.remove(this.seen.size() - 1);
        }
        Integer ref = this.circular.get(o);
        if (ref != null) {
            String reference = "<ref *" + ref + ">";
            base = base.isEmpty() ? reference : reference + " " + base;
        }
        return reduce(output, base, braces, extras, level, o);
    }

    private void formatArray(JsArray a, int level, ArrayList<String> output) {
        int n = a.list.size();
        int shown = Math.min(n, MAX_ARRAY);
        int i = 0;
        int emitted = 0;
        while (i < n && emitted < shown) {
            if (a.list.get(i) == null) {
                int j = i;
                while (j < n && a.list.get(j) == null) {
                    j++;
                }
                int cnt = j - i;
                output.add("<" + cnt + " empty item" + (cnt > 1 ? "s" : "") + ">");
                i = j;
            } else {
                this.indentationLvl += 2;
                output.add(format(a.list.get(i), level));
                this.indentationLvl -= 2;
                i++;
            }
            emitted++;
        }
        if (i < n) {
            int rest = n - i;
            output.add("... " + rest + " more item" + (rest > 1 ? "s" : ""));
        }
    }

    private static boolean isIdent(String k) {
        if (k.isEmpty()) {
            return false;
        }
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || (i > 0 && c >= '0' && c <= '9');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private String property(JsObject o, int level, Object key, int type) {
        Prop d = o.getOwn(key);
        String str;
        if (d == null) {
            str = "undefined";
        } else if (!d.accessor) {
            this.indentationLvl += 2;
            str = format(d.value, level);
            this.indentationLvl -= 2;
        } else if (d.getter != null) {
            str = d.setter != null ? "[Getter/Setter]" : "[Getter]";
        } else if (d.setter != null) {
            str = "[Setter]";
        } else {
            str = "undefined";
        }
        String name;
        if (key instanceof JsSymbol sym) {
            name = "[Symbol(" + (sym.description == null ? "" : sym.description) + ")]";
        } else {
            String ks = (String) key;
            if (ks.equals("__proto__")) {
                name = "['__proto__']";
            } else if (d != null && !d.enumerable()) {
                name = "[" + ks + "]";
            } else if (isIdent(ks)) {
                name = ks;
            } else {
                name = quote(ks);
            }
        }
        return name + ": " + str;
    }

    private static int width(String s) {
        return s.codePointCount(0, s.length());
    }

    private ArrayList<String> group(ArrayList<String> output, JsObject value) {
        int totalLength = 0;
        int maxLength = 0;
        int outputLength = output.size();
        if (value instanceof JsArray a && a.list.size() > MAX_ARRAY && outputLength > MAX_ARRAY) {
            outputLength--;
        }
        int sep = 2;
        int[] dataLen = new int[outputLength];
        for (int i = 0; i < outputLength; i++) {
            int len = width(output.get(i));
            dataLen[i] = len;
            totalLength += len + sep;
            if (maxLength < len) {
                maxLength = len;
            }
        }
        int actualMax = maxLength + sep;
        if (actualMax * 3 + this.indentationLvl < this.breakLength
                && ((double) totalLength / actualMax > 5 || maxLength <= 6)) {
            double averageBias = Math.sqrt(actualMax - (double) totalLength / output.size());
            double biasedMax = Math.max(actualMax - 3 - averageBias, 1);
            int columns = (int) Math.min(Math.min(Math.round(Math.sqrt(2.5 * biasedMax * outputLength) / biasedMax),
                    Math.floor((double) (this.breakLength - this.indentationLvl) / actualMax)), Math.min(3 * 4, 15));
            if (columns <= 1) {
                return output;
            }
            ArrayList<String> tmp = new ArrayList<>();
            int[] maxLineLength = new int[columns];
            for (int i = 0; i < columns; i++) {
                int lineLength = 0;
                for (int j = i; j < outputLength; j += columns) {
                    if (dataLen[j] > lineLength) {
                        lineLength = dataLen[j];
                    }
                }
                maxLineLength[i] = lineLength + sep;
            }
            boolean padStart = true;
            if (value instanceof JsArray a) {
                for (int i = 0; i < output.size() && i < a.list.size(); i++) {
                    Object el = a.list.get(i);
                    if (!(el instanceof Number)) {
                        padStart = false;
                        break;
                    }
                }
            }
            for (int i = 0; i < outputLength; i += columns) {
                int max = Math.min(i + columns, outputLength);
                StringBuilder str = new StringBuilder();
                int j = i;
                for (; j < max - 1; j++) {
                    String cell = output.get(j) + ", ";
                    int padding = maxLineLength[j - i];
                    str.append(padStart ? padLeft(cell, padding) : padRight(cell, padding));
                }
                if (padStart) {
                    int padding = maxLineLength[j - i] - sep;
                    str.append(padLeft(output.get(j), padding));
                } else {
                    str.append(output.get(j));
                }
                tmp.add(str.toString());
            }
            if (outputLength < output.size()) {
                tmp.add(output.get(outputLength));
            }
            return tmp;
        }
        return output;
    }

    private static String padLeft(String s, int w) {
        int n = width(s);
        return n >= w ? s : " ".repeat(w - n) + s;
    }

    private static String padRight(String s, int w) {
        int n = width(s);
        return n >= w ? s : s + " ".repeat(w - n);
    }

    private boolean belowBreakLength(ArrayList<String> output, int start, String base) {
        int total = output.size() + start;
        if (total + output.size() > this.breakLength) {
            return false;
        }
        for (String s : output) {
            total += s.length();
            if (total > this.breakLength) {
                return false;
            }
        }
        return base.isEmpty() || base.indexOf('\n') < 0;
    }

    private String reduce(ArrayList<String> output0, String base, String[] braces, int extras, int level, JsObject value) {
        ArrayList<String> output = output0;
        int entries = output.size();
        if (extras == ARRAY_EXTRAS && entries > 6) {
            output = group(output, value);
        }
        if (this.currentDepth - level < 3 && entries == output.size()) {
            int start = output.size() + this.indentationLvl + braces[0].length() + base.length() + 10;
            if (belowBreakLength(output, start, base)) {
                String joined = String.join(", ", output);
                if (joined.indexOf('\n') < 0) {
                    return (base.isEmpty() ? "" : base + " ") + braces[0] + " " + joined + " " + braces[1];
                }
            }
        }
        String indentation = "\n" + " ".repeat(this.indentationLvl);
        return (base.isEmpty() ? "" : base + " ") + braces[0] + indentation + "  "
                + String.join("," + indentation + "  ", output) + indentation + braces[1];
    }

    // ------------------------------------------------------------------ util.format

    static String formatArgs(Realm r, Object[] a) {
        StringBuilder sb = new StringBuilder();
        int next = 0;
        if (a.length > 0 && a[0] instanceof String f) {
            next = 1;
            if (a.length == 1 || f.indexOf('%') < 0) {
                sb.append(f);
            } else {
                int n = f.length();
                int last = 0;
                for (int i = 0; i < n - 1; i++) {
                    if (f.charAt(i) != '%') {
                        continue;
                    }
                    char c = f.charAt(i + 1);
                    if (c == '%') {
                        sb.append(f, last, i).append('%');
                        last = i + 2;
                        i++;
                        continue;
                    }
                    if (next >= a.length || "sdifjoOc".indexOf(c) < 0) {
                        continue;
                    }
                    Object arg = a[next++];
                    sb.append(f, last, i);
                    switch (c) {
                        case 's':
                            if (arg instanceof JsObject ao && !(arg instanceof JsFunction)) {
                                Object ts = ao.get("toString");
                                boolean custom = ts instanceof JsFunction && ts != r.objectProto.get("toString")
                                        && !(ao instanceof JsError) && !(ao instanceof JsDate);
                                sb.append(custom || ao instanceof JsError || ao instanceof JsDate ? Js.toStr(arg) : inspect(r, arg, 0));
                            } else if (arg instanceof Number nn) {
                                double dd = nn.doubleValue();
                                sb.append(dd == 0 && 1 / dd < 0 ? "-0" : Js.numToString(dd));
                            } else if (arg instanceof JsSymbol) {
                                sb.append(new Inspect(r, 0).primitive(arg));
                            } else {
                                sb.append(arg instanceof JsFunction ? inspect(r, arg, 0) : Js.toStr(arg));
                            }
                            break;
                        case 'd':
                            if (arg instanceof JsObject) {
                                sb.append("NaN");
                            } else if (arg instanceof JsSymbol) {
                                sb.append("NaN");
                            } else {
                                double dd = Js.toNumber(arg);
                                sb.append(dd == 0 && 1 / dd < 0 ? "-0" : Js.numToString(dd));
                            }
                            break;
                        case 'i':
                            if (arg instanceof JsObject || arg instanceof JsSymbol) {
                                sb.append("NaN");
                            } else {
                                sb.append(Js.toStr(Js.call(r.global.get("parseInt"), Js.UNDEFINED, Js.toStr(arg))));
                            }
                            break;
                        case 'f':
                            if (arg instanceof JsSymbol) {
                                sb.append("NaN");
                            } else {
                                sb.append(Js.toStr(Js.call(r.global.get("parseFloat"), Js.UNDEFINED, Js.toStr(arg))));
                            }
                            break;
                        case 'j': {
                            Object js = LibJson.stringify(r, arg, Js.UNDEFINED, Js.UNDEFINED);
                            sb.append(Js.toStr(js));
                            break;
                        }
                        case 'o':
                            sb.append(inspectHidden(r, arg, 4));
                            break;
                        case 'O':
                            sb.append(inspect(r, arg, 2));
                            break;
                        default:
                            break;
                    }
                    last = i + 2;
                    i++;
                }
                sb.append(f, last, n);
            }
        }
        for (int i = next; i < a.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(a[i] instanceof String s ? s : inspect(r, a[i], 2));
        }
        return sb.toString();
    }
}
