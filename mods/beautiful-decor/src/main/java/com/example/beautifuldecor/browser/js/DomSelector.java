package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selectores CSS para querySelector, matches y closest. */
final class DomSelector {
    private DomSelector() {
    }

    static final class AttrTest {
        String name;
        char op;
        String val = "";
        boolean ci;
    }

    static final class Pseudo {
        String name;
        Sel[] args;
        int a;
        int b;
    }

    static final class Compound {
        String tag;
        String id;
        final List<String> classes = new ArrayList<>();
        final List<AttrTest> attrs = new ArrayList<>();
        final List<Pseudo> pseudos = new ArrayList<>();
        boolean never;
    }

    static final class Sel {
        Compound[] parts;
        char[] combs;
    }

    // ------------------------------------------------------------------ lectura del selector

    private static final class P {
        final DomHost h;
        final String s;
        int i;

        P(DomHost h, String s) {
            this.h = h;
            this.s = s;
        }

        JsException error() {
            return this.h.domError("SyntaxError", "'" + this.s + "' is not a valid selector.");
        }

        boolean more() {
            return this.i < this.s.length();
        }

        char peek() {
            return this.s.charAt(this.i);
        }

        void ws() {
            while (more() && Character.isWhitespace(peek())) {
                this.i++;
            }
        }

        static boolean identStart(char c) {
            return Character.isLetter(c) || c == '_' || c == '-' || c == '\\' || c > 127;
        }

        static boolean identPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '\\' || c > 127;
        }

        String ident() {
            StringBuilder sb = new StringBuilder();
            while (more()) {
                char c = peek();
                if (c == '\\') {
                    this.i++;
                    if (!more()) {
                        break;
                    }
                    char e = peek();
                    if (Character.digit(e, 16) >= 0) {
                        int j = this.i;
                        while (j < this.s.length() && j - this.i < 6 && Character.digit(this.s.charAt(j), 16) >= 0) {
                            j++;
                        }
                        sb.appendCodePoint(Integer.parseInt(this.s.substring(this.i, j), 16));
                        this.i = j;
                        if (more() && Character.isWhitespace(peek())) {
                            this.i++;
                        }
                    } else {
                        sb.append(e);
                        this.i++;
                    }
                } else if (identPart(c)) {
                    sb.append(c);
                    this.i++;
                } else {
                    break;
                }
            }
            if (sb.length() == 0) {
                throw error();
            }
            return sb.toString();
        }

        String quoted() {
            char q = peek();
            this.i++;
            StringBuilder sb = new StringBuilder();
            while (more() && peek() != q) {
                char c = peek();
                if (c == '\\' && this.i + 1 < this.s.length()) {
                    this.i++;
                    c = peek();
                }
                sb.append(c);
                this.i++;
            }
            if (!more()) {
                throw error();
            }
            this.i++;
            return sb.toString();
        }
    }

    static Sel[] parse(DomHost h, String text) {
        P p = new P(h, text);
        List<Sel> list = new ArrayList<>();
        p.ws();
        while (true) {
            list.add(complex(p, false));
            p.ws();
            if (!p.more()) {
                break;
            }
            if (p.peek() == ',') {
                p.i++;
                p.ws();
                continue;
            }
            throw p.error();
        }
        return list.toArray(new Sel[0]);
    }

    /** Un selector compuesto por piezas unidas con combinadores. {@code relative}: puede empezar con un combinador (para :has). */
    private static Sel complex(P p, boolean relative) {
        List<Compound> parts = new ArrayList<>();
        List<Character> combs = new ArrayList<>();
        if (relative) {
            Compound scope = new Compound();
            Pseudo ps = new Pseudo();
            ps.name = "scope";
            scope.pseudos.add(ps);
            parts.add(scope);
            char c = ' ';
            if (p.more() && (p.peek() == '>' || p.peek() == '+' || p.peek() == '~')) {
                c = p.peek();
                p.i++;
                p.ws();
            }
            combs.add(c);
        }
        parts.add(compound(p));
        while (true) {
            int save = p.i;
            boolean space = false;
            while (p.more() && Character.isWhitespace(p.peek())) {
                p.i++;
                space = true;
            }
            if (!p.more()) {
                break;
            }
            char c = p.peek();
            if (c == ',' || c == ')') {
                break;
            }
            char comb;
            if (c == '>' || c == '+' || c == '~') {
                comb = c;
                p.i++;
                p.ws();
            } else if (space) {
                comb = ' ';
            } else {
                p.i = save;
                throw p.error();
            }
            combs.add(comb);
            parts.add(compound(p));
        }
        Sel s = new Sel();
        s.parts = parts.toArray(new Compound[0]);
        s.combs = new char[combs.size()];
        for (int i = 0; i < s.combs.length; i++) {
            s.combs[i] = combs.get(i);
        }
        return s;
    }

    private static Compound compound(P p) {
        Compound c = new Compound();
        boolean any = false;
        if (p.more()) {
            char ch = p.peek();
            if (ch == '*') {
                p.i++;
                c.tag = "*";
                any = true;
            } else if (P.identStart(ch) && ch != '\\' || ch == '\\') {
                c.tag = p.ident().toLowerCase(Locale.ROOT);
                any = true;
            }
        }
        while (p.more()) {
            char ch = p.peek();
            if (ch == '#') {
                p.i++;
                c.id = p.ident();
            } else if (ch == '.') {
                p.i++;
                c.classes.add(p.ident());
            } else if (ch == '[') {
                p.i++;
                c.attrs.add(attrTest(p));
            } else if (ch == ':') {
                p.i++;
                if (p.more() && p.peek() == ':') {
                    p.i++;
                    p.ident();
                    if (p.more() && p.peek() == '(') {
                        skipParens(p);
                    }
                    c.never = true;
                } else {
                    c.pseudos.add(pseudo(p, c));
                }
            } else {
                break;
            }
            any = true;
        }
        if (!any) {
            throw p.error();
        }
        return c;
    }

    private static void skipParens(P p) {
        int depth = 0;
        while (p.more()) {
            char c = p.peek();
            p.i++;
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return;
                }
            }
        }
        throw p.error();
    }

    private static AttrTest attrTest(P p) {
        AttrTest t = new AttrTest();
        p.ws();
        t.name = p.ident().toLowerCase(Locale.ROOT);
        p.ws();
        if (!p.more()) {
            throw p.error();
        }
        char c = p.peek();
        if (c == ']') {
            p.i++;
            t.op = 0;
            return t;
        }
        if (c == '=') {
            t.op = '=';
            p.i++;
        } else if ("~|^$*".indexOf(c) >= 0 && p.i + 1 < p.s.length() && p.s.charAt(p.i + 1) == '=') {
            t.op = c;
            p.i += 2;
        } else {
            throw p.error();
        }
        p.ws();
        if (!p.more()) {
            throw p.error();
        }
        t.val = p.peek() == '"' || p.peek() == '\'' ? p.quoted() : p.ident();
        p.ws();
        if (p.more() && (p.peek() == 'i' || p.peek() == 'I')) {
            t.ci = true;
            p.i++;
            p.ws();
        } else if (p.more() && (p.peek() == 's' || p.peek() == 'S')) {
            p.i++;
            p.ws();
        }
        if (!p.more() || p.peek() != ']') {
            throw p.error();
        }
        p.i++;
        return t;
    }

    private static Pseudo pseudo(P p, Compound owner) {
        Pseudo ps = new Pseudo();
        ps.name = p.ident().toLowerCase(Locale.ROOT);
        boolean paren = p.more() && p.peek() == '(';
        switch (ps.name) {
            case "before", "after", "first-line", "first-letter" -> {
                owner.never = true;
                return ps;
            }
            default -> { }
        }
        if (!paren) {
            switch (ps.name) {
                case "first-child", "last-child", "only-child", "first-of-type", "last-of-type", "only-of-type", "empty", "root",
                     "checked", "disabled", "enabled", "required", "optional", "read-only", "read-write", "focus", "hover", "active",
                     "visited", "link", "any-link", "target", "focus-within", "focus-visible", "placeholder-shown", "indeterminate",
                     "default", "valid", "invalid", "in-range", "out-of-range", "scope", "defined", "fullscreen", "first", "left",
                     "right", "selected", "visible", "hidden" -> {
                }
                default -> throw p.error();
            }
            if (ps.name.equals("selected") || ps.name.equals("visible") || ps.name.equals("hidden")) {
                throw p.error();
            }
            return ps;
        }
        p.i++;
        p.ws();
        switch (ps.name) {
            case "not", "is", "where", "matches", "-webkit-any", "-moz-any", "has" -> {
                boolean rel = ps.name.equals("has");
                List<Sel> list = new ArrayList<>();
                while (true) {
                    p.ws();
                    list.add(complex(p, rel));
                    p.ws();
                    if (p.more() && p.peek() == ',') {
                        p.i++;
                        continue;
                    }
                    break;
                }
                ps.args = list.toArray(new Sel[0]);
            }
            case "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type" -> {
                int end = p.i;
                int depth = 1;
                while (end < p.s.length() && depth > 0) {
                    char c = p.s.charAt(end);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                    end++;
                }
                String expr = p.s.substring(p.i, end).trim().toLowerCase(Locale.ROOT);
                int of = expr.indexOf(" of ");
                if (of >= 0) {
                    expr = expr.substring(0, of).trim();
                }
                int[] ab = nth(expr);
                if (ab == null) {
                    throw p.error();
                }
                ps.a = ab[0];
                ps.b = ab[1];
                p.i = end;
            }
            case "lang", "dir" -> {
                int end = p.s.indexOf(')', p.i);
                if (end < 0) {
                    throw p.error();
                }
                ps.args = null;
                p.i = end;
                owner.never = ps.name.equals("lang") ? owner.never : owner.never;
            }
            default -> throw p.error();
        }
        p.ws();
        if (!p.more() || p.peek() != ')') {
            throw p.error();
        }
        p.i++;
        return ps;
    }

    /** "2n+1", "odd", "even", "3", "-n+3" -> {a, b}. */
    static int[] nth(String e) {
        String s = e.replace(" ", "");
        if (s.equals("odd")) {
            return new int[] {2, 1};
        }
        if (s.equals("even")) {
            return new int[] {2, 0};
        }
        try {
            int n = s.indexOf('n');
            if (n < 0) {
                return new int[] {0, Integer.parseInt(s)};
            }
            String as = s.substring(0, n);
            int a = as.isEmpty() || as.equals("+") ? 1 : as.equals("-") ? -1 : Integer.parseInt(as);
            String bs = s.substring(n + 1);
            int b = bs.isEmpty() ? 0 : Integer.parseInt(bs.startsWith("+") ? bs.substring(1) : bs);
            return new int[] {a, b};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ coincidencia

    static boolean matches(DomHost h, Node el, Sel[] list, Node scope) {
        for (Sel s : list) {
            if (match(h, el, s, s.parts.length - 1, scope)) {
                return true;
            }
        }
        return false;
    }

    private static boolean match(DomHost h, Node el, Sel s, int idx, Node scope) {
        if (!compoundMatches(h, el, s.parts[idx], scope)) {
            return false;
        }
        if (idx == 0) {
            return true;
        }
        switch (s.combs[idx - 1]) {
            case '>': {
                Node p = el.parent;
                return p != null && p.isElement() && match(h, p, s, idx - 1, scope);
            }
            case '+': {
                Node prev = prevElement(el);
                return prev != null && match(h, prev, s, idx - 1, scope);
            }
            case '~': {
                for (Node q = prevElement(el); q != null; q = prevElement(q)) {
                    if (match(h, q, s, idx - 1, scope)) {
                        return true;
                    }
                }
                return false;
            }
            default: {
                for (Node p = el.parent; p != null; p = p.parent) {
                    if (p.isElement() && match(h, p, s, idx - 1, scope)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    static Node prevElement(Node n) {
        if (n.parent == null) {
            return null;
        }
        List<Node> sib = n.parent.children;
        int i = n.parent.indexOfChild(n);
        for (int k = i - 1; k >= 0; k--) {
            if (sib.get(k).isElement()) {
                return sib.get(k);
            }
        }
        return null;
    }

    static Node nextElement(Node n) {
        if (n.parent == null) {
            return null;
        }
        List<Node> sib = n.parent.children;
        int i = n.parent.indexOfChild(n);
        for (int k = i + 1; k < sib.size(); k++) {
            if (sib.get(k).isElement()) {
                return sib.get(k);
            }
        }
        return null;
    }

    private static boolean compoundMatches(DomHost h, Node el, Compound c, Node scope) {
        if (c.never || !el.isElement()) {
            return false;
        }
        if (c.tag != null && !c.tag.equals("*") && !c.tag.equals(el.tag)) {
            return false;
        }
        if (c.id != null && !c.id.equals(el.attr("id"))) {
            return false;
        }
        for (String cl : c.classes) {
            boolean found = false;
            for (String x : el.classes()) {
                if (x.equals(cl)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        for (AttrTest t : c.attrs) {
            if (!attrMatches(el, t)) {
                return false;
            }
        }
        for (Pseudo ps : c.pseudos) {
            if (!pseudoMatches(h, el, ps, scope)) {
                return false;
            }
        }
        return true;
    }

    private static boolean attrMatches(Node el, AttrTest t) {
        String v = el.attr(t.name);
        if (v == null) {
            return false;
        }
        if (t.op == 0) {
            return true;
        }
        String a = t.ci ? v.toLowerCase(Locale.ROOT) : v;
        String b = t.ci ? t.val.toLowerCase(Locale.ROOT) : t.val;
        switch (t.op) {
            case '=':
                return a.equals(b);
            case '~':
                if (b.isEmpty() || b.chars().anyMatch(Character::isWhitespace)) {
                    return false;
                }
                for (String w : a.trim().split("\\s+")) {
                    if (w.equals(b)) {
                        return true;
                    }
                }
                return false;
            case '|':
                return a.equals(b) || a.startsWith(b + "-");
            case '^':
                return !b.isEmpty() && a.startsWith(b);
            case '$':
                return !b.isEmpty() && a.endsWith(b);
            case '*':
                return !b.isEmpty() && a.contains(b);
            default:
                return false;
        }
    }

    static boolean isDisabled(Node el) {
        if (!(el.is("input") || el.is("button") || el.is("select") || el.is("textarea") || el.is("fieldset") || el.is("option")
                || el.is("optgroup"))) {
            return false;
        }
        if (el.hasAttr("disabled")) {
            return true;
        }
        for (Node p = el.parent; p != null; p = p.parent) {
            if (p.is("fieldset") && p.hasAttr("disabled")) {
                return true;
            }
            if (p.is("optgroup") && p.hasAttr("disabled") && el.is("option")) {
                return true;
            }
        }
        return false;
    }

    private static boolean pseudoMatches(DomHost h, Node el, Pseudo ps, Node scope) {
        switch (ps.name) {
            case "scope":
                return scope == null ? el.parent == h.doc : el == scope;
            case "root":
                return el.parent == h.doc;
            case "first-child":
                return prevElement(el) == null;
            case "last-child":
                return nextElement(el) == null;
            case "only-child":
                return prevElement(el) == null && nextElement(el) == null;
            case "first-of-type":
                return nthOfType(el, false) == 1;
            case "last-of-type":
                return nthOfType(el, true) == 1;
            case "only-of-type":
                return nthOfType(el, false) == 1 && nthOfType(el, true) == 1;
            case "nth-child":
                return nthMatch(ps, position(el, false, false));
            case "nth-last-child":
                return nthMatch(ps, position(el, true, false));
            case "nth-of-type":
                return nthMatch(ps, nthOfType(el, false));
            case "nth-last-of-type":
                return nthMatch(ps, nthOfType(el, true));
            case "empty":
                for (Node c : el.children) {
                    if (c.isElement() || (c.isText() && !c.text.isEmpty())) {
                        return false;
                    }
                }
                return true;
            case "not":
                return !matches(h, el, ps.args, scope);
            case "is", "where", "matches", "-webkit-any", "-moz-any":
                return matches(h, el, ps.args, scope);
            case "has":
                return hasMatch(h, el, ps.args);
            case "checked":
                return (el.is("input") && ("checkbox".equals(inputType(el)) || "radio".equals(inputType(el))) && el.isChecked())
                        || (el.is("option") && el.isChecked());
            case "disabled":
                return isDisabled(el);
            case "enabled":
                return (el.is("input") || el.is("button") || el.is("select") || el.is("textarea") || el.is("fieldset")) && !isDisabled(el);
            case "required":
                return (el.is("input") || el.is("select") || el.is("textarea")) && el.hasAttr("required");
            case "optional":
                return (el.is("input") || el.is("select") || el.is("textarea")) && !el.hasAttr("required");
            case "read-write":
                return isEditable(el);
            case "read-only":
                return !isEditable(el);
            case "focus", "focus-visible":
                return h.focused == el;
            case "focus-within":
                for (Node f = h.focused; f != null; f = f.parent) {
                    if (f == el) {
                        return true;
                    }
                }
                return false;
            case "link", "any-link":
                return (el.is("a") || el.is("area")) && el.hasAttr("href");
            case "placeholder-shown":
                return (el.is("input") || el.is("textarea")) && el.hasAttr("placeholder") && el.currentValue().isEmpty();
            case "valid", "in-range", "defined":
                return true;
            default:
                return false;
        }
    }

    static String inputType(Node el) {
        String t = el.attr("type");
        return t == null ? "text" : t.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isEditable(Node el) {
        if (el.is("textarea")) {
            return !el.hasAttr("readonly") && !isDisabled(el);
        }
        if (el.is("input")) {
            String t = inputType(el);
            boolean text = DomForms.isTextLike(t);
            return text && !el.hasAttr("readonly") && !isDisabled(el);
        }
        String ce = el.attr("contenteditable");
        return ce != null && !ce.equalsIgnoreCase("false");
    }

    private static boolean hasMatch(DomHost h, Node el, Sel[] args) {
        List<Node> cands = new ArrayList<>();
        for (Sel s : args) {
            char first = s.combs.length > 0 ? s.combs[0] : ' ';
            cands.clear();
            if (first == '+' || first == '~') {
                if (el.parent != null) {
                    int i = el.parent.indexOfChild(el);
                    for (int k = i + 1; k < el.parent.children.size(); k++) {
                        Node sib = el.parent.children.get(k);
                        if (sib.isElement()) {
                            cands.add(sib);
                            DomNodes.collect(sib, cands, n -> n.isElement());
                        }
                    }
                }
            } else {
                DomNodes.collect(el, cands, n -> n.isElement());
            }
            for (Node d : cands) {
                if (match(h, d, s, s.parts.length - 1, el)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int position(Node el, boolean fromEnd, boolean sameType) {
        if (el.parent == null) {
            return 1;
        }
        int pos = 0;
        List<Node> sib = el.parent.children;
        if (!fromEnd) {
            for (Node c : sib) {
                if (c.isElement() && (!sameType || c.tag.equals(el.tag))) {
                    pos++;
                }
                if (c == el) {
                    return pos;
                }
            }
        } else {
            for (int k = sib.size() - 1; k >= 0; k--) {
                Node c = sib.get(k);
                if (c.isElement() && (!sameType || c.tag.equals(el.tag))) {
                    pos++;
                }
                if (c == el) {
                    return pos;
                }
            }
        }
        return pos;
    }

    private static int nthOfType(Node el, boolean fromEnd) {
        return position(el, fromEnd, true);
    }

    private static boolean nthMatch(Pseudo ps, int pos) {
        if (ps.a == 0) {
            return pos == ps.b;
        }
        int d = pos - ps.b;
        return d % ps.a == 0 && d / ps.a >= 0;
    }

    // ------------------------------------------------------------------ búsqueda

    static List<Node> all(DomHost h, Node root, Sel[] list, boolean firstOnly) {
        List<Node> out = new ArrayList<>();
        scan(h, root, root, list, firstOnly, out);
        return out;
    }

    private static boolean scan(DomHost h, Node n, Node root, Sel[] list, boolean firstOnly, List<Node> out) {
        for (Node c : n.children) {
            if (c.isElement()) {
                if (matches(h, c, list, root.isElement() ? root : null)) {
                    out.add(c);
                    if (firstOnly) {
                        return true;
                    }
                }
                if (scan(h, c, root, list, firstOnly, out)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Node closest(DomHost h, Node el, Sel[] list) {
        for (Node n = el; n != null; n = n.parent) {
            if (n.isElement() && matches(h, n, list, el)) {
                return n;
            }
        }
        return null;
    }
}
