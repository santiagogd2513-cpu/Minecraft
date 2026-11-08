package com.example.beautifuldecor.browser;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lector de HTML tolerante (no es el estándar completo): cierra solo los párrafos, listas y tablas más comunes, entiende
 * las entidades habituales y no se rompe con etiquetas mal cerradas. Limita la cantidad de nodos y la profundidad.
 */
public final class HtmlParser {
    public static final int MAX_NODES = 60000;
    private static final int MAX_DEPTH = 200;

    private static final Set<String> VOID = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
            "command", "keygen", "basefont", "frame"));
    private static final Set<String> RAW = new HashSet<>(Arrays.asList("script", "style", "textarea", "title", "xmp", "noframes"));
    private static final Set<String> CLOSES_P = new HashSet<>(Arrays.asList(
            "address", "article", "aside", "blockquote", "center", "details", "dialog", "dir", "div", "dl", "fieldset",
            "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "main",
            "menu", "nav", "ol", "p", "pre", "section", "summary", "table", "ul", "li", "dt", "dd"));
    private static final Map<String, String> ENTITIES = new HashMap<>();

    static {
        String[] pairs = {
                "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ", "copy", "©", "reg", "®",
                "trade", "™", "hellip", "…", "mdash", "—", "ndash", "–", "lsquo", "‘", "rsquo", "’",
                "ldquo", "“", "rdquo", "”", "sbquo", "‚", "bdquo", "„", "laquo", "«", "raquo", "»",
                "bull", "•", "middot", "·", "deg", "°", "plusmn", "±", "times", "×", "divide", "÷",
                "euro", "€", "pound", "£", "yen", "¥", "cent", "¢", "sect", "§", "para", "¶",
                "larr", "←", "rarr", "→", "uarr", "↑", "darr", "↓", "harr", "↔", "hearts", "♥",
                "check", "✓", "star", "☆", "infin", "∞", "ne", "≠", "le", "≤", "ge", "≥",
                "frac12", "½", "frac14", "¼", "frac34", "¾", "sup1", "¹", "sup2", "²", "sup3", "³",
                "micro", "µ", "iexcl", "¡", "iquest", "¿", "ensp", " ", "emsp", " ", "thinsp", " ",
                "zwnj", "‌", "zwj", "‍", "shy", "", "dagger", "†", "Dagger", "‡", "permil", "‰",
                "prime", "′", "Prime", "″", "oline", "‾", "minus", "−", "radic", "√", "asymp", "≈",
                "alpha", "α", "beta", "β", "gamma", "γ", "delta", "δ", "pi", "π", "sigma", "σ",
                "omega", "ω", "lambda", "λ", "mu", "μ", "Omega", "Ω", "Delta", "Δ",
                "agrave", "à", "aacute", "á", "acirc", "â", "atilde", "ã", "auml", "ä", "aring", "å",
                "aelig", "æ", "ccedil", "ç", "egrave", "è", "eacute", "é", "ecirc", "ê", "euml", "ë",
                "igrave", "ì", "iacute", "í", "icirc", "î", "iuml", "ï", "ntilde", "ñ", "ograve", "ò",
                "oacute", "ó", "ocirc", "ô", "otilde", "õ", "ouml", "ö", "oslash", "ø", "ugrave", "ù",
                "uacute", "ú", "ucirc", "û", "uuml", "ü", "yacute", "ý", "yuml", "ÿ", "szlig", "ß",
                "Agrave", "À", "Aacute", "Á", "Acirc", "Â", "Atilde", "Ã", "Auml", "Ä", "Aring", "Å",
                "AElig", "Æ", "Ccedil", "Ç", "Egrave", "È", "Eacute", "É", "Ecirc", "Ê", "Euml", "Ë",
                "Igrave", "Ì", "Iacute", "Í", "Icirc", "Î", "Iuml", "Ï", "Ntilde", "Ñ", "Ograve", "Ò",
                "Oacute", "Ó", "Ocirc", "Ô", "Otilde", "Õ", "Ouml", "Ö", "Oslash", "Ø", "Ugrave", "Ù",
                "Uacute", "Ú", "Ucirc", "Û", "Uuml", "Ü", "Yacute", "Ý"};
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ENTITIES.put(pairs[i], pairs[i + 1]);
        }
    }

    private final String src;
    private int pos;
    private int nodes;
    private final Node root;
    private final boolean keepRootBlank;
    private final ArrayDeque<Node> stack = new ArrayDeque<>();

    private HtmlParser(String src, Node root, boolean keepRootBlank) {
        this.src = src;
        this.root = root;
        this.keepRootBlank = keepRootBlank;
    }

    public static Node parse(String html) {
        HtmlParser p = new HtmlParser(html == null ? "" : html, Node.element("#document", null), false);
        p.stack.push(p.root);
        p.run();
        return p.root;
    }

    /** Lee un trozo de HTML suelto (lo que se asigna a innerHTML) y devuelve un fragmento con sus nodos. */
    public static Node parseFragment(String html) {
        HtmlParser p = new HtmlParser(html == null ? "" : html, Node.fragment(), true);
        p.stack.push(p.root);
        p.run();
        return p.root;
    }

    // ------------------------------------------------------------------ entidades

    public static String decodeEntities(String s) {
        int amp = s.indexOf('&');
        if (amp < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semi = s.indexOf(';', i + 1);
            if (semi > 0 && semi - i <= 10) {
                String name = s.substring(i + 1, semi);
                String value = null;
                if (name.startsWith("#")) {
                    try {
                        int cp = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X')
                                ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                        if (cp > 0 && cp <= 0x10FFFF && !(cp >= 0xD800 && cp <= 0xDFFF)) {
                            value = new String(Character.toChars(cp));
                        }
                    } catch (NumberFormatException ignored) {
                        // no es una entidad numérica
                    }
                } else {
                    value = ENTITIES.get(name);
                }
                if (value != null) {
                    out.append(value);
                    i = semi + 1;
                    continue;
                }
            }
            out.append('&');
            i++;
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ lectura

    private void run() {
        int n = this.src.length();
        StringBuilder text = new StringBuilder();
        while (this.pos < n && this.nodes < MAX_NODES) {
            char c = this.src.charAt(this.pos);
            if (c != '<') {
                int next = this.src.indexOf('<', this.pos);
                if (next < 0) {
                    next = n;
                }
                text.append(this.src, this.pos, next);
                this.pos = next;
                continue;
            }
            if (this.pos + 1 >= n) {
                text.append(c);
                this.pos++;
                continue;
            }
            char d = this.src.charAt(this.pos + 1);
            if (d == '!' || d == '?') {
                flushText(text);
                skipDeclaration();
            } else if (d == '/') {
                int close = this.src.indexOf('>', this.pos);
                if (close < 0) {
                    break;
                }
                String name = this.src.substring(this.pos + 2, close).trim().toLowerCase();
                int sp = indexOfSpace(name);
                if (sp >= 0) {
                    name = name.substring(0, sp);
                }
                if (isNameStart(name)) {
                    flushText(text);
                    endTag(name);
                } else {
                    text.append(this.src, this.pos, close + 1);
                }
                this.pos = close + 1;
            } else if (Character.isLetter(d)) {
                flushText(text);
                startTag();
            } else {
                text.append(c);
                this.pos++;
            }
        }
        flushText(text);
    }

    private static boolean isNameStart(String name) {
        return !name.isEmpty() && Character.isLetter(name.charAt(0));
    }

    private static int indexOfSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == '/') {
                return i;
            }
        }
        return -1;
    }

    private void skipDeclaration() {
        if (this.src.startsWith("<!--", this.pos)) {
            int end = this.src.indexOf("-->", this.pos + 4);
            this.pos = end < 0 ? this.src.length() : end + 3;
            return;
        }
        if (this.src.startsWith("<![CDATA[", this.pos)) {
            int end = this.src.indexOf("]]>", this.pos);
            this.pos = end < 0 ? this.src.length() : end + 3;
            return;
        }
        int end = this.src.indexOf('>', this.pos);
        this.pos = end < 0 ? this.src.length() : end + 1;
    }

    private void flushText(StringBuilder text) {
        if (text.length() == 0) {
            return;
        }
        String s = decodeEntities(text.toString());
        text.setLength(0);
        Node top = this.stack.peek();
        if (s.isEmpty() || (isBlank(s) && top == this.root && !this.keepRootBlank)) {
            return;
        }
        top.add(Node.text(s));
        this.nodes++;
    }

    private static boolean isBlank(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void startTag() {
        int n = this.src.length();
        int p = this.pos + 1;
        int nameStart = p;
        while (p < n && !Character.isWhitespace(this.src.charAt(p)) && this.src.charAt(p) != '>' && this.src.charAt(p) != '/') {
            p++;
        }
        String name = this.src.substring(nameStart, p).toLowerCase();
        Map<String, String> attrs = new LinkedHashMap<>();
        boolean selfClose = false;
        while (p < n) {
            char c = this.src.charAt(p);
            if (c == '>') {
                p++;
                break;
            }
            if (Character.isWhitespace(c)) {
                p++;
                continue;
            }
            if (c == '/') {
                selfClose = true;
                p++;
                continue;
            }
            selfClose = false;
            int as = p;
            while (p < n) {
                char k = this.src.charAt(p);
                if (Character.isWhitespace(k) || k == '=' || k == '>' || k == '/') {
                    break;
                }
                p++;
            }
            String key = this.src.substring(as, p).toLowerCase();
            while (p < n && Character.isWhitespace(this.src.charAt(p))) {
                p++;
            }
            String value = "";
            if (p < n && this.src.charAt(p) == '=') {
                p++;
                while (p < n && Character.isWhitespace(this.src.charAt(p))) {
                    p++;
                }
                if (p < n && (this.src.charAt(p) == '"' || this.src.charAt(p) == '\'')) {
                    char q = this.src.charAt(p);
                    int e = this.src.indexOf(q, p + 1);
                    if (e < 0) {
                        e = n;
                    }
                    value = this.src.substring(p + 1, e);
                    p = Math.min(n, e + 1);
                } else {
                    int vs = p;
                    while (p < n && !Character.isWhitespace(this.src.charAt(p)) && this.src.charAt(p) != '>') {
                        p++;
                    }
                    value = this.src.substring(vs, p);
                }
            }
            if (!key.isEmpty() && !attrs.containsKey(key)) {
                attrs.put(key, decodeEntities(value));
            }
        }
        this.pos = p;
        openElement(name, attrs, selfClose);
    }

    private void openElement(String name, Map<String, String> attrs, boolean selfClose) {
        impliedCloses(name);
        Node el = Node.element(name, attrs);
        this.stack.peek().add(el);
        this.nodes++;
        boolean isVoid = VOID.contains(name);
        if (RAW.contains(name) && !selfClose) {
            int end = findClosing(name);
            String raw = this.src.substring(this.pos, end);
            if (!raw.isEmpty()) {
                el.add(Node.text(name.equals("title") || name.equals("textarea") ? decodeEntities(raw) : raw));
            }
            int close = this.src.indexOf('>', end);
            this.pos = close < 0 ? this.src.length() : close + 1;
            return;
        }
        if (!isVoid && !selfClose && this.stack.size() < MAX_DEPTH) {
            this.stack.push(el);
        }
    }

    private int findClosing(String name) {
        String lower = "</" + name;
        int from = this.pos;
        int n = this.src.length();
        while (true) {
            int i = this.src.indexOf("</", from);
            if (i < 0) {
                return n;
            }
            if (this.src.regionMatches(true, i, lower, 0, lower.length())) {
                return i;
            }
            from = i + 2;
        }
    }

    private boolean inStack(String name) {
        for (Node n : this.stack) {
            if (n.tag.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Cierra a mano lo que HTML cierra solo (un párrafo al empezar otro bloque, un li al empezar otro li, etc.). */
    private void impliedCloses(String name) {
        if (CLOSES_P.contains(name)) {
            closeInScope("p", "table", "td", "th", "button", "li", "dd", "dt", "blockquote", "div");
        }
        switch (name) {
            case "li" -> closeInScope("li", "ul", "ol", "menu");
            case "dt", "dd" -> {
                closeInScope("dt", "dl");
                closeInScope("dd", "dl");
            }
            case "tr" -> {
                closeInScope("td", "table");
                closeInScope("th", "table");
                closeInScope("tr", "table");
            }
            case "td", "th" -> {
                closeInScope("td", "tr", "table");
                closeInScope("th", "tr", "table");
            }
            case "thead", "tbody", "tfoot" -> {
                closeInScope("td", "table");
                closeInScope("th", "table");
                closeInScope("tr", "table");
                closeInScope("thead", "table");
                closeInScope("tbody", "table");
                closeInScope("tfoot", "table");
            }
            case "option" -> closeInScope("option", "select", "datalist");
            case "optgroup" -> {
                closeInScope("option", "select");
                closeInScope("optgroup", "select");
            }
            default -> { }
        }
    }

    /** Si el elemento "name" está abierto sin que haya un límite de por medio, lo cierra (con todo lo que tenga adentro). */
    private void closeInScope(String name, String... limits) {
        for (Node n : this.stack) {
            if (n.tag.equals(name)) {
                while (this.stack.peek() != n) {
                    this.stack.pop();
                }
                this.stack.pop();
                return;
            }
            for (String limit : limits) {
                if (n.tag.equals(limit)) {
                    return;
                }
            }
        }
    }

    private void endTag(String name) {
        if (VOID.contains(name)) {
            if (name.equals("br")) {
                openElement("br", new LinkedHashMap<>(), false);
            }
            return;
        }
        if (!inStack(name)) {
            if (name.equals("p")) {
                openElement("p", new LinkedHashMap<>(), false);
                this.stack.pop();
            }
            return;
        }
        while (this.stack.size() > 1) {
            Node top = this.stack.pop();
            if (top.tag.equals(name)) {
                break;
            }
        }
    }
}
