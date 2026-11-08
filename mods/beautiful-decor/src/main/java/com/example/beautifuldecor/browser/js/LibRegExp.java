package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** RegExp y los métodos de texto que la usan. */
final class LibRegExp {
    private static final HashMap<String, JsRegExp.Compiled> CACHE = new HashMap<>();
    private static final String WS = " \\t\\n\\u000B\\f\\r\\u00a0\\ufeff\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000";
    private static final String LT = "\\n\\r\\u2028\\u2029";
    private static final String W = "A-Za-z0-9_";

    private LibRegExp() {
    }

    // ------------------------------------------------------------------ traducción

    private static JsRegExp.Compiled compile(String src, String flags) {
        String key = flags + "/" + src;
        synchronized (CACHE) {
            JsRegExp.Compiled c = CACHE.get(key);
            if (c != null) {
                return c;
            }
        }
        ArrayList<String> names = new ArrayList<>();
        names.add(null);
        translate(src, flags, names, null);
        ArrayList<String> finalNames = new ArrayList<>();
        finalNames.add(null);
        String java = translate(src, flags, finalNames, names);
        int f = 0;
        if (flags.indexOf('i') >= 0) {
            f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        Pattern p;
        try {
            p = Pattern.compile(java, f);
        } catch (PatternSyntaxException e) {
            String why = e.getDescription();
            if (why.startsWith("Unclosed group")) {
                why = "Unterminated group";
            } else if (why.startsWith("Dangling meta character")) {
                why = "Nothing to repeat";
            } else if (why.startsWith("Unclosed character class")) {
                why = "Unterminated character class";
            }
            throw Js.syntaxError("Invalid regular expression: /" + src + "/" + flags + ": " + why);
        } catch (StackOverflowError e) {
            throw Js.syntaxError("Invalid regular expression: /" + src + "/: too complex");
        }
        JsRegExp.Compiled c = new JsRegExp.Compiled(p, finalNames.toArray(new String[0]));
        synchronized (CACHE) {
            if (CACHE.size() > 500) {
                CACHE.clear();
            }
            CACHE.put(key, c);
        }
        return c;
    }

    private static boolean isQuantifier(String s, int p) {
        int n = s.length();
        int i = p + 1;
        int d = 0;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
            d++;
        }
        if (d == 0) {
            return false;
        }
        if (i < n && s.charAt(i) == ',') {
            i++;
            while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
            }
        }
        return i < n && s.charAt(i) == '}';
    }

    /** Traduce la sintaxis de JavaScript a la de Java. {@code names} recibe los nombres de grupo; {@code known} los de una pasada previa. */
    private static String translate(String src, String flags, List<String> names, List<String> known) {
        boolean multiline = flags.indexOf('m') >= 0;
        boolean dotAll = flags.indexOf('s') >= 0;
        boolean unicode = flags.indexOf('u') >= 0;
        StringBuilder out = new StringBuilder();
        int n = src.length();
        boolean inClass = false;
        boolean prevClassEscape = false;
        int p = 0;
        while (p < n) {
            char c = src.charAt(p);
            if (c == '\\') {
                if (p + 1 >= n) {
                    throw Js.syntaxError("Invalid regular expression: /" + src + "/: \\ at end of pattern");
                }
                char e = src.charAt(p + 1);
                p += 2;
                boolean classEsc = false;
                switch (e) {
                    case 'd':
                    case 'D':
                    case 'w':
                    case 'W':
                        out.append('\\').append(e);
                        classEsc = true;
                        break;
                    case 's':
                        out.append(inClass ? WS : "[" + WS + "]");
                        classEsc = true;
                        break;
                    case 'S':
                        out.append(inClass ? "\\S" : "[^" + WS + "]");
                        classEsc = true;
                        break;
                    case 'b':
                        out.append(inClass ? "\\x08"
                                : "(?:(?<=[" + W + "])(?![" + W + "])|(?<![" + W + "])(?=[" + W + "]))");
                        break;
                    case 'B':
                        out.append(inClass ? "B" : "(?:(?<=[" + W + "])(?=[" + W + "])|(?<![" + W + "])(?![" + W + "]))");
                        break;
                    case 'n':
                    case 'r':
                    case 't':
                    case 'f':
                        out.append('\\').append(e);
                        break;
                    case 'v':
                        out.append("\\x0B");
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9': {
                        int q = p;
                        while (q < n && src.charAt(q) >= '0' && src.charAt(q) <= '9') {
                            q++;
                        }
                        String digits = e + src.substring(p, q);
                        int groupTotal = (known != null ? known : names).size() - 1;
                        if (e != '0' && !inClass && Integer.parseInt(digits.length() > 6 ? "999999" : digits) <= groupTotal) {
                            p = q;
                            out.append("(?:\\").append(digits).append(')');
                            break;
                        }
                        if (e >= '8') {
                            out.append(e);
                            break;
                        }
                        int val = e - '0';
                        int used = 0;
                        while (used < 2 && p + used < n && src.charAt(p + used) >= '0' && src.charAt(p + used) <= '7'
                                && val * 8 + (src.charAt(p + used) - '0') <= 0377) {
                            val = val * 8 + (src.charAt(p + used) - '0');
                            used++;
                        }
                        p += used;
                        out.append(String.format("\\x{%x}", val));
                        break;
                    }
                    case 'c':
                        if (p < n && Character.isLetter(src.charAt(p))) {
                            out.append("\\c").append(src.charAt(p));
                            p++;
                        } else {
                            out.append("\\\\c");
                        }
                        break;
                    case 'x':
                        if (p + 2 <= n && isHex(src, p, 2)) {
                            out.append("\\x").append(src, p, p + 2);
                            p += 2;
                        } else {
                            out.append('x');
                        }
                        break;
                    case 'u':
                        if (p < n && src.charAt(p) == '{' && unicode) {
                            int close = src.indexOf('}', p);
                            if (close < 0) {
                                throw Js.syntaxError("Invalid regular expression: /" + src + "/: Invalid Unicode escape");
                            }
                            out.append("\\x{").append(src, p + 1, close).append('}');
                            p = close + 1;
                        } else if (p + 4 <= n && isHex(src, p, 4)) {
                            out.append("\\u").append(src, p, p + 4);
                            p += 4;
                        } else {
                            out.append('u');
                        }
                        break;
                    case 'k': {
                        int close = p < n && src.charAt(p) == '<' ? src.indexOf('>', p) : -1;
                        if (close < 0) {
                            out.append('k');
                            break;
                        }
                        String name = src.substring(p + 1, close);
                        p = close + 1;
                        List<String> look = known != null ? known : names;
                        int idx = look.indexOf(name);
                        if (idx < 0) {
                            if (known == null) {
                                break;
                            }
                            throw Js.syntaxError("Invalid regular expression: /" + src + "/: Invalid named reference");
                        }
                        out.append("(?:\\").append(idx).append(')');
                        break;
                    }
                    case 'p':
                    case 'P': {
                        if (unicode && p < n && src.charAt(p) == '{') {
                            int close = src.indexOf('}', p);
                            if (close < 0) {
                                throw Js.syntaxError("Invalid regular expression: /" + src + "/: Invalid property name");
                            }
                            out.append(unicodeProperty(e == 'P', src.substring(p + 1, close), inClass));
                            p = close + 1;
                            classEsc = true;
                        } else {
                            out.append(e);
                        }
                        break;
                    }
                    default:
                        if (Character.isLetterOrDigit(e) && e < 128) {
                            out.append(e);
                        } else if (e == '\n' || e == '\r') {
                            out.append(e);
                        } else {
                            out.append('\\').append(e);
                        }
                }
                prevClassEscape = classEsc;
                continue;
            }
            if (inClass) {
                p++;
                if (c == ']') {
                    inClass = false;
                    out.append(']');
                    prevClassEscape = false;
                } else if (c == '[') {
                    out.append("\\[");
                    prevClassEscape = false;
                } else if (c == '&') {
                    out.append("\\&");
                    prevClassEscape = false;
                } else if (c == '-') {
                    boolean nextEsc = p + 1 < n && src.charAt(p) == '\\' && "dDwWsSpP".indexOf(src.charAt(p + 1)) >= 0;
                    boolean last = p < n && src.charAt(p) == ']';
                    out.append(prevClassEscape || nextEsc || last ? "\\-" : "-");
                    prevClassEscape = false;
                } else {
                    out.append(c);
                    prevClassEscape = false;
                }
                continue;
            }
            switch (c) {
                case '[':
                    if (src.startsWith("[^]", p)) {
                        out.append("[\\s\\S]");
                        p += 3;
                    } else if (src.startsWith("[]", p)) {
                        out.append("(?!)");
                        p += 2;
                    } else {
                        inClass = true;
                        prevClassEscape = false;
                        out.append('[');
                        p++;
                        if (p < n && src.charAt(p) == '^') {
                            out.append('^');
                            p++;
                        }
                        if (p < n && src.charAt(p) == ']') {
                            // "[]" ya se trató; aquí no puede pasar
                            out.append("\\]");
                            p++;
                        }
                    }
                    break;
                case '(':
                    if (src.startsWith("(?<", p) && p + 3 < n && src.charAt(p + 3) != '=' && src.charAt(p + 3) != '!') {
                        int close = src.indexOf('>', p);
                        if (close < 0) {
                            throw Js.syntaxError("Invalid regular expression: /" + src + "/: Invalid capture group name");
                        }
                        names.add(src.substring(p + 3, close));
                        out.append('(');
                        p = close + 1;
                    } else if (src.startsWith("(?", p)) {
                        out.append("(?");
                        p += 2;
                    } else {
                        names.add(null);
                        out.append('(');
                        p++;
                    }
                    break;
                case '.':
                    out.append(dotAll ? "[\\s\\S]" : "[^" + LT + "]");
                    p++;
                    break;
                case '^':
                    out.append(multiline ? "(?<![^" + LT + "])" : "\\A");
                    p++;
                    break;
                case '$':
                    out.append(multiline ? "(?![^" + LT + "])" : "\\z");
                    p++;
                    break;
                case '{':
                    if (isQuantifier(src, p)) {
                        int close = src.indexOf('}', p);
                        out.append(src, p, close + 1);
                        p = close + 1;
                    } else {
                        out.append("\\{");
                        p++;
                    }
                    break;
                case '}':
                    out.append("\\}");
                    p++;
                    break;
                case ']':
                    out.append("\\]");
                    p++;
                    break;
                default:
                    out.append(c);
                    p++;
            }
        }
        if (inClass) {
            throw Js.syntaxError("Invalid regular expression: /" + src + "/: Unterminated character class");
        }
        return out.toString();
    }

    private static boolean isHex(String s, int from, int count) {
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String unicodeProperty(boolean negate, String body, boolean inClass) {
        String name = body;
        int eq = body.indexOf('=');
        String kind = "";
        if (eq >= 0) {
            kind = body.substring(0, eq);
            name = body.substring(eq + 1);
        }
        String java;
        if (kind.equals("Script") || kind.equals("sc") || kind.equals("Script_Extensions") || kind.equals("scx")) {
            java = "Is" + name;
        } else {
            switch (name) {
                case "Letter":
                    java = "L";
                    break;
                case "Lowercase_Letter":
                    java = "Ll";
                    break;
                case "Uppercase_Letter":
                    java = "Lu";
                    break;
                case "Number":
                    java = "N";
                    break;
                case "Decimal_Number":
                    java = "Nd";
                    break;
                case "Punctuation":
                    java = "P";
                    break;
                case "Symbol":
                    java = "S";
                    break;
                case "Separator":
                    java = "Z";
                    break;
                case "Alphabetic":
                    java = "IsAlphabetic";
                    break;
                case "Uppercase":
                    java = "IsUppercase";
                    break;
                case "Lowercase":
                    java = "IsLowercase";
                    break;
                case "White_Space":
                    java = "IsWhite_Space";
                    break;
                case "Emoji":
                case "Emoji_Presentation":
                case "Extended_Pictographic": {
                    String set = "\\x{1F000}-\\x{1FAFF}\\u2600-\\u27BF\\u2B00-\\u2BFF\\u2300-\\u23FF";
                    return inClass && !negate ? set : (negate ? "[^" : "[") + set + "]";
                }
                default:
                    java = name;
            }
        }
        return (negate ? "\\P{" : "\\p{") + java + "}";
    }

    // ------------------------------------------------------------------ creación

    static JsRegExp create(Realm r, String pattern, String flags) {
        return build(r, r.regexpProto, pattern, flags);
    }

    private static JsRegExp build(Realm r, JsObject proto, String pattern, String flags) {
        for (int i = 0; i < flags.length(); i++) {
            char c = flags.charAt(i);
            if ("dgimsuy".indexOf(c) < 0 || flags.indexOf(c) != i) {
                throw Js.syntaxError("Invalid flags supplied to RegExp constructor '" + flags + "'");
            }
        }
        return new JsRegExp(proto, escapeSource(pattern), flags, compile(pattern, flags));
    }

    /** Como EscapeRegExpPattern: escapa "/" fuera de clases y los saltos de línea. */
    private static String escapeSource(String p) {
        if (p.indexOf('/') < 0 && p.indexOf('\n') < 0 && p.indexOf('\r') < 0 && p.indexOf('\u2028') < 0 && p.indexOf('\u2029') < 0) {
            return p;
        }
        StringBuilder sb = new StringBuilder();
        boolean inClass = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '\\' && i + 1 < p.length()) {
                char d = p.charAt(i + 1);
                if (d == '\n') {
                    sb.append("\\n");
                } else if (d == '\r') {
                    sb.append("\\r");
                } else {
                    sb.append(c).append(d);
                }
                i++;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            }
            if (c == '/' && !inClass) {
                sb.append("\\/");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\u2028') {
                sb.append("\\u2028");
            } else if (c == '\u2029') {
                sb.append("\\u2029");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static JsRegExp thisRe(Object t) {
        if (t instanceof JsRegExp re) {
            return re;
        }
        throw Js.typeError("Method RegExp.prototype method called on incompatible receiver " + Js.display(t));
    }

    private static Object ctorImpl(Realm r, JsObject proto, Object[] a) {
        Object pat = Js.arg(a, 0);
        Object fl = Js.arg(a, 1);
        String source;
        String flags;
        if (pat instanceof JsRegExp re) {
            source = re.source;
            flags = fl == Js.UNDEFINED ? re.flags : Js.toStr(fl);
        } else {
            source = pat == Js.UNDEFINED ? "" : Js.toStr(pat);
            flags = fl == Js.UNDEFINED ? "" : Js.toStr(fl);
        }
        return build(r, proto, source, flags);
    }

    static void install(Realm r) {
        JsObject rp = r.regexpProto;
        NativeFunction re = Lib.ctor(r, "RegExp", 2, rp, (t, a) -> {
            if (Js.arg(a, 0) instanceof JsRegExp && Js.arg(a, 1) == Js.UNDEFINED) {
                return a[0];
            }
            return ctorImpl(r, rp, a);
        }, (a, nt) -> ctorImpl(r, Lib.protoFrom(nt, rp), a));
        re.defineOwn(JsSymbol.SPECIES, new Prop(re, null, Prop.C));
        for (int i = 1; i <= 9; i++) {
            int gi = i;
            Lib.getter(r, re, "$" + i, (t, a) -> gi < r.reGroups.length && r.reGroups[gi] != null ? r.reGroups[gi] : "");
        }
        NativeFunction.Fn lastMatch = (t, a) -> r.reGroups.length > 0 && r.reGroups[0] != null ? r.reGroups[0] : "";
        NativeFunction.Fn input = (t, a) -> r.reInput;
        NativeFunction.Fn lastParen = (t, a) -> r.reGroups.length > 1 && r.reGroups[r.reGroups.length - 1] != null ? r.reGroups[r.reGroups.length - 1] : "";
        NativeFunction.Fn left = (t, a) -> r.reInput.substring(0, Math.min(r.reStart, r.reInput.length()));
        NativeFunction.Fn right = (t, a) -> r.reInput.substring(Math.min(r.reEnd, r.reInput.length()));
        for (String nm : new String[] {"lastMatch", "$&"}) {
            Lib.getter(r, re, nm, lastMatch);
        }
        for (String nm : new String[] {"input", "$_"}) {
            Lib.getter(r, re, nm, input);
        }
        for (String nm : new String[] {"lastParen", "$+"}) {
            Lib.getter(r, re, nm, lastParen);
        }
        for (String nm : new String[] {"leftContext", "$`"}) {
            Lib.getter(r, re, nm, left);
        }
        for (String nm : new String[] {"rightContext", "$'"}) {
            Lib.getter(r, re, nm, right);
        }
        Lib.method(r, rp, "exec", 1, (t, a) -> exec(r, thisRe(t), Js.toStr(Js.arg(a, 0))));
        Lib.method(r, rp, "test", 1, (t, a) -> exec(r, thisRe(t), Js.toStr(Js.arg(a, 0))) != Js.NULL);
        Lib.method(r, rp, "toString", 0, (t, a) -> {
            if (t instanceof JsRegExp x) {
                return "/" + (x.source.isEmpty() ? "(?:)" : x.source) + "/" + x.flags;
            }
            return "/" + Js.toStr(Js.getMember(r, t, "source")) + "/" + Js.toStr(Js.getMember(r, t, "flags"));
        });
        Lib.method(r, rp, "compile", 2, (t, a) -> t);
        Lib.getter(r, rp, "source", (t, a) -> t instanceof JsRegExp x ? (x.source.isEmpty() ? "(?:)" : x.source) : "(?:)");
        Lib.getter(r, rp, "flags", (t, a) -> {
            if (t instanceof JsRegExp x) {
                StringBuilder sb = new StringBuilder();
                for (char c : "dgimsuy".toCharArray()) {
                    if (x.flags.indexOf(c) >= 0) {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }
            return "";
        });
        String[][] flagProps = {{"global", "g"}, {"ignoreCase", "i"}, {"multiline", "m"}, {"dotAll", "s"}, {"unicode", "u"},
            {"sticky", "y"}, {"hasIndices", "d"}, {"unicodeSets", "v"}};
        for (String[] fp : flagProps) {
            Lib.getter(r, rp, fp[0], (t, a) -> t instanceof JsRegExp x ? (Object) (x.flags.indexOf(fp[1].charAt(0)) >= 0) : Js.UNDEFINED);
        }
    }

    // ------------------------------------------------------------------ ejecutar

    static void record(Realm r, Matcher m, String input) {
        int gc = m.groupCount();
        String[] g = new String[gc + 1];
        for (int i = 0; i <= gc; i++) {
            g[i] = m.group(i);
        }
        r.reGroups = g;
        r.reInput = input;
        r.reStart = m.start();
        r.reEnd = m.end();
    }

    private static Object matchResult(Realm r, JsRegExp re, Matcher m, String input) {
        record(r, m, input);
        int gc = m.groupCount();
        ArrayList<Object> items = new ArrayList<>(gc + 1);
        for (int i = 0; i <= gc; i++) {
            String g = m.group(i);
            items.add(g == null ? Js.UNDEFINED : g);
        }
        JsArray arr = new JsArray(r.arrayProto, items);
        arr.setData("index", Js.num(m.start()));
        arr.setData("input", input);
        String[] names = re.compiled.names;
        boolean hasNames = false;
        for (String s : names) {
            if (s != null) {
                hasNames = true;
                break;
            }
        }
        if (hasNames) {
            JsObject groups = new JsObject(null);
            for (int i = 1; i < names.length && i <= gc; i++) {
                if (names[i] != null) {
                    groups.setData(names[i], items.get(i));
                }
            }
            arr.setData("groups", groups);
        } else {
            arr.setData("groups", Js.UNDEFINED);
        }
        return arr;
    }

    static Object exec(Realm r, JsRegExp re, String input) {
        boolean useLast = re.global || re.sticky;
        int last = useLast ? re.lastIndex() : 0;
        if (last > input.length()) {
            if (useLast) {
                re.setLastIndex(0);
            }
            return Js.NULL;
        }
        Matcher m = re.compiled.pattern.matcher(input);
        boolean found;
        try {
            if (re.sticky) {
                m.region(last, input.length());
                m.useAnchoringBounds(false);
                m.useTransparentBounds(true);
                found = m.lookingAt();
            } else {
                found = m.find(last);
            }
        } catch (StackOverflowError e) {
            throw Js.rangeError("Maximum call stack size exceeded");
        }
        if (!found) {
            if (useLast) {
                re.setLastIndex(0);
            }
            return Js.NULL;
        }
        if (useLast) {
            re.setLastIndex(m.end());
        }
        return matchResult(r, re, m, input);
    }

    // ------------------------------------------------------------------ métodos de String

    static Object match(Realm r, String s, Object arg) {
        JsRegExp re = arg instanceof JsRegExp x ? x : build(r, r.regexpProto, arg == Js.UNDEFINED ? "" : Js.toStr(arg), "");
        if (!re.global) {
            return exec(r, re, s);
        }
        re.setLastIndex(0);
        ArrayList<Object> out = new ArrayList<>();
        Matcher m = re.compiled.pattern.matcher(s);
        int from = 0;
        while (from <= s.length() && m.find(from)) {
            out.add(m.group());
            record(r, m, s);
            from = m.end() == m.start() ? m.end() + 1 : m.end();
        }
        return out.isEmpty() ? Js.NULL : new JsArray(r.arrayProto, out);
    }

    static Object matchAll(Realm r, String s, Object arg) {
        JsRegExp re;
        if (arg instanceof JsRegExp x) {
            if (!x.global) {
                throw Js.typeError("String.prototype.matchAll called with a non-global RegExp argument");
            }
            re = x;
        } else {
            re = build(r, r.regexpProto, arg == Js.UNDEFINED ? "" : Js.toStr(arg), "g");
        }
        Matcher m = re.compiled.pattern.matcher(s);
        JsRegExp rr = re;
        IterRec it = new IterRec() {
            private int from = rr.lastIndex();
            private boolean done;

            @Override
            public Object next() {
                if (this.done || this.from > s.length() || !m.find(this.from)) {
                    this.done = true;
                    return DONE;
                }
                this.from = m.end() == m.start() ? m.end() + 1 : m.end();
                return matchResult(r, rr, m, s);
            }
        };
        return new JsIterObject(r.regexpStringIteratorProto(), it, "RegExp String Iterator");
    }

    static Object search(Realm r, String s, Object arg) {
        JsRegExp re = arg instanceof JsRegExp x ? x : build(r, r.regexpProto, arg == Js.UNDEFINED ? "" : Js.toStr(arg), "");
        Matcher m = re.compiled.pattern.matcher(s);
        return Js.num(m.find(0) ? m.start() : -1);
    }

    private static String expand(String repl, String matched, String str, int pos, String[] caps, JsObject named) {
        if (repl.indexOf('$') < 0) {
            return repl;
        }
        StringBuilder sb = new StringBuilder();
        int n = repl.length();
        for (int i = 0; i < n; i++) {
            char c = repl.charAt(i);
            if (c != '$' || i + 1 >= n) {
                sb.append(c);
                continue;
            }
            char d = repl.charAt(i + 1);
            if (d == '$') {
                sb.append('$');
                i++;
            } else if (d == '&') {
                sb.append(matched);
                i++;
            } else if (d == '`') {
                sb.append(str, 0, pos);
                i++;
            } else if (d == '\'') {
                sb.append(str.substring(Math.min(pos + matched.length(), str.length())));
                i++;
            } else if (d >= '0' && d <= '9') {
                int num = d - '0';
                int used = 1;
                if (i + 2 < n && repl.charAt(i + 2) >= '0' && repl.charAt(i + 2) <= '9') {
                    int two = num * 10 + (repl.charAt(i + 2) - '0');
                    if (two >= 1 && two <= caps.length) {
                        num = two;
                        used = 2;
                    }
                }
                if (num >= 1 && num <= caps.length) {
                    if (caps[num - 1] != null) {
                        sb.append(caps[num - 1]);
                    }
                    i += used;
                } else {
                    sb.append('$');
                }
            } else if (d == '<' && named != null) {
                int close = repl.indexOf('>', i + 2);
                if (close < 0) {
                    sb.append('$');
                } else {
                    Object v = named.get(repl.substring(i + 2, close));
                    if (v != Js.UNDEFINED) {
                        sb.append(Js.toStr(v));
                    }
                    i = close;
                }
            } else {
                sb.append('$');
            }
        }
        return sb.toString();
    }

    static String replace(Realm r, String s, Object pattern, Object replacement, boolean all) {
        JsFunction fn = replacement instanceof JsFunction f ? f : null;
        String repl = fn == null ? Js.toStr(replacement) : null;
        if (pattern instanceof JsRegExp re) {
            if (all && !re.global) {
                throw Js.typeError("replaceAll must be called with a global RegExp");
            }
            Matcher m = re.compiled.pattern.matcher(s);
            StringBuilder sb = new StringBuilder();
            int pos = 0;
            int from = re.global ? 0 : (re.sticky ? re.lastIndex() : 0);
            if (re.global) {
                re.setLastIndex(0);
            }
            boolean any = false;
            while (from <= s.length()) {
                boolean found;
                if (re.sticky) {
                    m.region(from, s.length());
                    m.useAnchoringBounds(false);
                    m.useTransparentBounds(true);
                    found = m.lookingAt();
                } else {
                    found = m.find(from);
                }
                if (!found) {
                    break;
                }
                any = true;
                record(r, m, s);
                int gc = m.groupCount();
                String[] caps = new String[gc];
                for (int i = 1; i <= gc; i++) {
                    caps[i - 1] = m.group(i);
                }
                JsObject groups = null;
                String[] names = re.compiled.names;
                for (int i = 1; i < names.length && i <= gc; i++) {
                    if (names[i] != null) {
                        if (groups == null) {
                            groups = new JsObject(null);
                        }
                        groups.setData(names[i], caps[i - 1] == null ? Js.UNDEFINED : caps[i - 1]);
                    }
                }
                sb.append(s, pos, m.start());
                String matched = m.group();
                if (fn != null) {
                    ArrayList<Object> args = new ArrayList<>();
                    args.add(matched);
                    for (String c : caps) {
                        args.add(c == null ? Js.UNDEFINED : c);
                    }
                    args.add(Js.num(m.start()));
                    args.add(s);
                    if (groups != null) {
                        args.add(groups);
                    }
                    sb.append(Js.toStr(fn.call(Js.UNDEFINED, args.toArray())));
                } else {
                    sb.append(expand(repl, matched, s, m.start(), caps, groups));
                }
                pos = m.end();
                if (!re.global) {
                    if (re.sticky) {
                        re.setLastIndex(m.end());
                    }
                    break;
                }
                from = m.end() == m.start() ? m.end() + 1 : m.end();
            }
            if (!any && re.sticky) {
                re.setLastIndex(0);
            }
            sb.append(s.substring(Math.min(pos, s.length())));
            return sb.toString();
        }
        String pat = Js.toStr(pattern);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int from = 0;
        while (true) {
            int idx = s.indexOf(pat, from);
            if (idx < 0) {
                break;
            }
            sb.append(s, pos, idx);
            if (fn != null) {
                sb.append(Js.toStr(fn.call(Js.UNDEFINED, new Object[] {pat, Js.num(idx), s})));
            } else {
                sb.append(expand(repl, pat, s, idx, new String[0], null));
            }
            pos = idx + pat.length();
            if (!all) {
                break;
            }
            from = pat.isEmpty() ? idx + 1 : pos;
            if (from > s.length()) {
                break;
            }
        }
        sb.append(s.substring(Math.min(pos, s.length())));
        return sb.toString();
    }

    static Object split(Realm r, String s, Object sep, Object limit) {
        long lim = limit == Js.UNDEFINED ? 4294967295L : (long) Js.toUint32(Js.toNumber(limit));
        ArrayList<Object> out = new ArrayList<>();
        if (lim == 0) {
            return new JsArray(r.arrayProto, out);
        }
        if (sep == Js.UNDEFINED) {
            out.add(s);
            return new JsArray(r.arrayProto, out);
        }
        if (sep instanceof JsRegExp re) {
            Matcher m = re.compiled.pattern.matcher(s);
            int size = s.length();
            if (size == 0) {
                if (!m.matches()) {
                    out.add(s);
                }
                return new JsArray(r.arrayProto, out);
            }
            int p = 0;
            int q = 0;
            while (q < size) {
                if (!m.find(q)) {
                    break;
                }
                int ms = m.start();
                int e = m.end();
                if (ms >= size) {
                    break;
                }
                if (e == p || (e == ms && ms == p)) {
                    q = ms + 1;
                    continue;
                }
                out.add(s.substring(p, ms));
                if (out.size() >= lim) {
                    return new JsArray(r.arrayProto, out);
                }
                for (int i = 1; i <= m.groupCount(); i++) {
                    String g = m.group(i);
                    out.add(g == null ? Js.UNDEFINED : g);
                    if (out.size() >= lim) {
                        return new JsArray(r.arrayProto, out);
                    }
                }
                p = e;
                q = p == ms ? p + 1 : p;
            }
            out.add(s.substring(p));
            return new JsArray(r.arrayProto, out);
        }
        String sp = Js.toStr(sep);
        if (sp.isEmpty()) {
            for (int i = 0; i < s.length() && out.size() < lim; i++) {
                out.add(String.valueOf(s.charAt(i)));
            }
            return new JsArray(r.arrayProto, out);
        }
        int from = 0;
        while (true) {
            int idx = s.indexOf(sp, from);
            if (idx < 0) {
                break;
            }
            out.add(s.substring(from, idx));
            if (out.size() >= lim) {
                return new JsArray(r.arrayProto, out);
            }
            from = idx + sp.length();
        }
        out.add(s.substring(from));
        return new JsArray(r.arrayProto, out);
    }
}
