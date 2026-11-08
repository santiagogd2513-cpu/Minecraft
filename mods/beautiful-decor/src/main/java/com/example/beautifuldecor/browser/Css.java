package com.example.beautifuldecor.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSS básico: lee hojas de estilo y atributos style, y calcula qué declaraciones le tocan a cada elemento (cascada por
 * especificidad). Entiende selectores de etiqueta, clase, id, atributo, "*", combinadores de descendiente y de hijo, y
 * @media. Los selectores con pseudo-clases o hermanos (:hover, +, ~) se ignoran por completo.
 * Los colores no se usan: el navegador siempre dibuja en modo oscuro.
 */
public final class Css {
    /** Ancho de pantalla que se finge para evaluar @media (como una pantalla de escritorio). */
    private static final int VIEWPORT = 1024;

    private Css() {
    }

    // ------------------------------------------------------------------ selectores

    private static final class Compound {
        String tag;
        String id;
        final List<String> classes = new ArrayList<>();
        final List<String[]> attrs = new ArrayList<>();       // {nombre, operador, valor}
    }

    private static final class Sel {
        Compound[] parts;
        char[] combs;
        int specificity;
    }

    public static final class Rule {
        private Sel sel;
        private Map<String, String> decls;
        private Map<String, Boolean> important;
        private int order;
    }

    // ------------------------------------------------------------------ hoja de estilo

    public static final class Sheet {
        private final Map<String, List<Rule>> byId = new HashMap<>();
        private final Map<String, List<Rule>> byClass = new HashMap<>();
        private final Map<String, List<Rule>> byTag = new HashMap<>();
        private final List<Rule> universal = new ArrayList<>();
        private int nextOrder;

        public int size() {
            return this.nextOrder;
        }

        /** Añade las reglas de un texto CSS (varias hojas se pueden sumar en el orden en que aparecen). */
        public void add(String css) {
            for (Rule r : parseRules(css, this.nextOrder)) {
                this.nextOrder = Math.max(this.nextOrder, r.order + 1);
                Compound last = r.sel.parts[r.sel.parts.length - 1];
                if (last.id != null) {
                    this.byId.computeIfAbsent(last.id, k -> new ArrayList<>()).add(r);
                } else if (!last.classes.isEmpty()) {
                    this.byClass.computeIfAbsent(last.classes.get(0), k -> new ArrayList<>()).add(r);
                } else if (last.tag != null) {
                    this.byTag.computeIfAbsent(last.tag, k -> new ArrayList<>()).add(r);
                } else {
                    this.universal.add(r);
                }
            }
        }

        /** Declaraciones que le tocan al elemento (propiedad en minúsculas -> valor). */
        public Map<String, String> match(Node el) {
            List<Rule> hits = new ArrayList<>();
            String id = el.attr("id");
            if (id != null) {
                collect(this.byId.get(id), el, hits);
            }
            for (String c : el.classes()) {
                collect(this.byClass.get(c), el, hits);
            }
            collect(this.byTag.get(el.tag), el, hits);
            collect(this.universal, el, hits);
            Map<String, String> out = new LinkedHashMap<>();
            if (!hits.isEmpty()) {
                hits.sort((a, b) -> a.sel.specificity != b.sel.specificity
                        ? Integer.compare(a.sel.specificity, b.sel.specificity) : Integer.compare(a.order, b.order));
                Map<String, Boolean> fixed = new HashMap<>();
                for (Rule r : hits) {
                    for (Map.Entry<String, String> e : r.decls.entrySet()) {
                        boolean imp = r.important.getOrDefault(e.getKey(), false);
                        if (fixed.getOrDefault(e.getKey(), false) && !imp) {
                            continue;
                        }
                        out.put(e.getKey(), e.getValue());
                        if (imp) {
                            fixed.put(e.getKey(), true);
                        }
                    }
                }
            }
            String inline = el.attr("style");
            if (inline != null && !inline.isBlank()) {
                out.putAll(parseDeclarations(inline, null));
            }
            return out;
        }

        private static void collect(List<Rule> candidates, Node el, List<Rule> hits) {
            if (candidates == null) {
                return;
            }
            for (Rule r : candidates) {
                if (matches(r.sel, el)) {
                    hits.add(r);
                }
            }
        }
    }

    private static boolean matches(Sel sel, Node el) {
        int i = sel.parts.length - 1;
        if (!matchCompound(sel.parts[i], el)) {
            return false;
        }
        return matchUp(sel, i, el);
    }

    private static boolean matchUp(Sel sel, int i, Node el) {
        if (i == 0) {
            return true;
        }
        char comb = sel.combs[i - 1];
        Node p = el.parent;
        if (comb == '>') {
            return p != null && p.tag != null && matchCompound(sel.parts[i - 1], p) && matchUp(sel, i - 1, p);
        }
        while (p != null && p.tag != null) {
            if (matchCompound(sel.parts[i - 1], p) && matchUp(sel, i - 1, p)) {
                return true;
            }
            p = p.parent;
        }
        return false;
    }

    private static boolean matchCompound(Compound c, Node el) {
        if (c.tag != null && !c.tag.equals(el.tag)) {
            return false;
        }
        if (c.id != null && !c.id.equals(el.attr("id"))) {
            return false;
        }
        for (String cls : c.classes) {
            if (!el.hasClass(cls)) {
                return false;
            }
        }
        for (String[] a : c.attrs) {
            String v = el.attr(a[0]);
            if (v == null) {
                return false;
            }
            switch (a[1]) {
                case "=" -> {
                    if (!v.equals(a[2])) {
                        return false;
                    }
                }
                case "~=" -> {
                    boolean found = false;
                    for (String part : v.split("\\s+")) {
                        found |= part.equals(a[2]);
                    }
                    if (!found) {
                        return false;
                    }
                }
                case "^=" -> {
                    if (!v.startsWith(a[2])) {
                        return false;
                    }
                }
                case "$=" -> {
                    if (!v.endsWith(a[2])) {
                        return false;
                    }
                }
                case "*=" -> {
                    if (!v.contains(a[2])) {
                        return false;
                    }
                }
                default -> { }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ lectura de selectores

    private static Sel parseSelector(String text) {
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        List<Compound> parts = new ArrayList<>();
        List<Character> combs = new ArrayList<>();
        int spec = 0;
        int i = 0;
        int n = s.length();
        char pending = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (pending == 0) {
                    pending = ' ';
                }
                i++;
                continue;
            }
            if (c == '>') {
                pending = '>';
                i++;
                continue;
            }
            if (c == '+' || c == '~' || c == ':' || c == '|') {
                return null;
            }
            Compound comp = new Compound();
            boolean any = false;
            while (i < n) {
                c = s.charAt(i);
                if (Character.isWhitespace(c) || c == '>') {
                    break;
                }
                if (c == '+' || c == '~' || c == ':' || c == '|' || c == '(') {
                    return null;
                }
                if (c == '*') {
                    i++;
                    any = true;
                } else if (c == '#') {
                    int e = identEnd(s, i + 1);
                    comp.id = s.substring(i + 1, e);
                    spec += 10000;
                    i = e;
                    any = true;
                } else if (c == '.') {
                    int e = identEnd(s, i + 1);
                    comp.classes.add(s.substring(i + 1, e));
                    spec += 100;
                    i = e;
                    any = true;
                } else if (c == '[') {
                    int e = s.indexOf(']', i);
                    if (e < 0) {
                        return null;
                    }
                    String body = s.substring(i + 1, e).trim();
                    String[] t = parseAttrTest(body);
                    if (t == null) {
                        return null;
                    }
                    comp.attrs.add(t);
                    spec += 100;
                    i = e + 1;
                    any = true;
                } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    int e = identEnd(s, i);
                    comp.tag = s.substring(i, e).toLowerCase();
                    spec += 1;
                    i = e;
                    any = true;
                } else {
                    return null;
                }
            }
            if (!any) {
                return null;
            }
            if (!parts.isEmpty()) {
                combs.add(pending == 0 ? ' ' : pending);
            }
            parts.add(comp);
            pending = 0;
        }
        if (parts.isEmpty() || pending == '>') {
            return null;
        }
        Sel sel = new Sel();
        sel.parts = parts.toArray(new Compound[0]);
        sel.combs = new char[combs.size()];
        for (int k = 0; k < sel.combs.length; k++) {
            sel.combs[k] = combs.get(k);
        }
        sel.specificity = spec;
        return sel;
    }

    private static int identEnd(String s, int from) {
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '\\') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static String[] parseAttrTest(String body) {
        String[] ops = {"~=", "^=", "$=", "*=", "="};
        for (String op : ops) {
            int k = body.indexOf(op);
            if (k > 0) {
                String name = body.substring(0, k).trim().toLowerCase();
                String value = body.substring(k + op.length()).trim();
                if (value.length() >= 2 && (value.startsWith("\"") || value.startsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return new String[]{name, op, value};
            }
        }
        if (body.indexOf('|') >= 0 || body.isEmpty()) {
            return null;
        }
        return new String[]{body.toLowerCase(), "", ""};
    }

    // ------------------------------------------------------------------ lectura de hojas

    private static String stripComments(String css) {
        StringBuilder sb = new StringBuilder(css.length());
        int i = 0;
        int n = css.length();
        while (i < n) {
            if (css.startsWith("/*", i)) {
                int e = css.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 2;
            } else {
                sb.append(css.charAt(i++));
            }
        }
        return sb.toString();
    }

    static List<Rule> parseRules(String css, int firstOrder) {
        List<Rule> out = new ArrayList<>();
        parseBlock(stripComments(css), out, new int[]{firstOrder}, 0);
        return out;
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return s.length();
    }

    private static void parseBlock(String css, List<Rule> out, int[] order, int depth) {
        int i = 0;
        int n = css.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(css.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            if (css.charAt(i) == '@') {
                int brace = css.indexOf('{', i);
                int semi = css.indexOf(';', i);
                if (brace < 0 || (semi >= 0 && semi < brace)) {
                    i = semi < 0 ? n : semi + 1;               // @import, @charset...
                    continue;
                }
                String head = css.substring(i + 1, brace).trim().toLowerCase();
                int close = matchingBrace(css, brace);
                if (depth < 4 && (head.startsWith("media") && mediaMatches(head.substring(5))
                        || head.startsWith("supports") || head.startsWith("layer") || head.startsWith("document"))) {
                    parseBlock(css.substring(brace + 1, Math.min(close, n)), out, order, depth + 1);
                }
                i = close + 1;
                continue;
            }
            int brace = css.indexOf('{', i);
            if (brace < 0) {
                break;
            }
            int close = matchingBrace(css, brace);
            String selectors = css.substring(i, brace);
            String body = css.substring(brace + 1, Math.min(close, n));
            i = close + 1;
            Map<String, Boolean> important = new HashMap<>();
            Map<String, String> decls = parseDeclarations(body, important);
            if (decls.isEmpty()) {
                continue;
            }
            for (String one : splitTopLevel(selectors, ',')) {
                Sel sel = parseSelector(one);
                if (sel == null) {
                    continue;
                }
                Rule r = new Rule();
                r.sel = sel;
                r.decls = decls;
                r.important = important;
                r.order = order[0]++;
                out.add(r);
            }
        }
    }

    private static List<String> splitTopLevel(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == sep && depth <= 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    // ------------------------------------------------------------------ @media

    private static boolean mediaMatches(String query) {
        String q = query.trim();
        if (q.isEmpty()) {
            return true;
        }
        for (String alt : splitTopLevel(q, ',')) {
            if (oneMediaMatches(alt.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean oneMediaMatches(String q) {
        if (q.contains("print") || q.contains("speech") || q.contains("not all")) {
            return false;
        }
        int i = 0;
        while ((i = q.indexOf('(', i)) >= 0) {
            int e = q.indexOf(')', i);
            if (e < 0) {
                break;
            }
            String feature = q.substring(i + 1, e).trim();
            i = e + 1;
            int colon = feature.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = feature.substring(0, colon).trim();
            String value = feature.substring(colon + 1).trim();
            switch (name) {
                case "min-width" -> {
                    if (px(value, VIEWPORT) > VIEWPORT) {
                        return false;
                    }
                }
                case "max-width" -> {
                    if (px(value, VIEWPORT) < VIEWPORT) {
                        return false;
                    }
                }
                case "prefers-color-scheme" -> {
                    if (value.equals("light")) {
                        return false;
                    }
                }
                case "hover" -> {
                    if (value.equals("none")) {
                        return false;
                    }
                }
                default -> { }
            }
        }
        return true;
    }

    /** Longitud CSS (px, em, rem, pt) a píxeles; -1 si no se entiende. */
    public static int px(String value, int emSize) {
        double v = length(value.trim().toLowerCase(), emSize, 16);
        return Double.isNaN(v) ? -1 : (int) Math.round(v);
    }

    /** Longitud CSS a píxeles. Devuelve NaN si no es una longitud (auto, %, calc...). */
    public static double length(String v, double parentEm, double rootEm) {
        if (v.endsWith("!important")) {
            v = v.substring(0, v.length() - 10).trim();
        }
        try {
            if (v.endsWith("px")) {
                return Double.parseDouble(v.substring(0, v.length() - 2));
            }
            if (v.endsWith("rem")) {
                return Double.parseDouble(v.substring(0, v.length() - 3)) * rootEm;
            }
            if (v.endsWith("em")) {
                return Double.parseDouble(v.substring(0, v.length() - 2)) * parentEm;
            }
            if (v.endsWith("pt")) {
                return Double.parseDouble(v.substring(0, v.length() - 2)) * 4.0 / 3.0;
            }
            if (v.equals("0")) {
                return 0;
            }
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ declaraciones

    /** Lee "a: b; c: d !important" (el contenido de un atributo style o de un bloque). */
    public static Map<String, String> parseDeclarations(String body, Map<String, Boolean> important) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String decl : splitTopLevel(body, ';')) {
            int colon = decl.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = decl.substring(0, colon).trim().toLowerCase();
            String value = decl.substring(colon + 1).trim();
            boolean imp = false;
            if (value.toLowerCase().endsWith("!important")) {
                value = value.substring(0, value.length() - 10).trim();
                imp = true;
            }
            if (name.isEmpty() || value.isEmpty() || name.startsWith("--")) {
                continue;
            }
            put(out, important, name, value.toLowerCase(), imp);
        }
        return out;
    }

    private static void put(Map<String, String> out, Map<String, Boolean> important, String name, String value, boolean imp) {
        switch (name) {
            case "margin", "padding" -> {
                String[] t = value.split("\\s+");
                String top = t[0];
                String right = t.length > 1 ? t[1] : t[0];
                String bottom = t.length > 2 ? t[2] : t[0];
                String left = t.length > 3 ? t[3] : right;
                set(out, important, name + "-top", top, imp);
                set(out, important, name + "-right", right, imp);
                set(out, important, name + "-bottom", bottom, imp);
                set(out, important, name + "-left", left, imp);
            }
            case "font" -> {
                for (String t : value.split("\\s+")) {
                    String word = t.contains("/") ? t.substring(0, t.indexOf('/')) : t;
                    if (word.equals("italic") || word.equals("oblique")) {
                        set(out, important, "font-style", "italic", imp);
                    } else if (word.equals("bold") || word.equals("bolder") || word.matches("[6-9]00")) {
                        set(out, important, "font-weight", "bold", imp);
                    } else if (!Double.isNaN(length(word, 16, 16)) && word.matches(".*(px|em|rem|pt)")
                            || word.matches("x?x?-?(small|large)|medium|smaller|larger")) {
                        set(out, important, "font-size", word, imp);
                    }
                }
            }
            case "list-style" -> {
                if (value.contains("none")) {
                    set(out, important, "list-style-type", "none", imp);
                }
            }
            case "text-decoration" -> set(out, important, "text-decoration-line", value, imp);
            default -> set(out, important, name, value, imp);
        }
    }

    private static void set(Map<String, String> out, Map<String, Boolean> important, String name, String value, boolean imp) {
        out.put(name, value);
        if (important != null && imp) {
            important.put(name, true);
        }
    }
}
