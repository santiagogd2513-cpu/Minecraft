package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Css;
import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** element.style (CSSStyleDeclaration sobre el atributo style), getComputedStyle y el objeto CSS. */
final class DomStyle {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList((
            "align-content align-items align-self all animation animation-delay animation-direction animation-duration "
            + "animation-fill-mode animation-iteration-count animation-name animation-play-state animation-timing-function appearance "
            + "aspect-ratio backface-visibility background background-attachment background-blend-mode background-clip background-color "
            + "background-image background-origin background-position background-position-x background-position-y background-repeat "
            + "background-size border border-bottom border-bottom-color border-bottom-left-radius border-bottom-right-radius "
            + "border-bottom-style border-bottom-width border-collapse border-color border-image border-left border-left-color "
            + "border-left-style border-left-width border-radius border-right border-right-color border-right-style border-right-width "
            + "border-spacing border-style border-top border-top-color border-top-left-radius border-top-right-radius border-top-style "
            + "border-top-width border-width bottom box-shadow box-sizing caption-side caret-color clear clip clip-path color "
            + "column-count column-gap column-rule columns content counter-increment counter-reset cursor direction display empty-cells "
            + "filter flex flex-basis flex-direction flex-flow flex-grow flex-shrink flex-wrap float font font-family "
            + "font-feature-settings font-kerning font-size font-size-adjust font-stretch font-style font-variant font-weight gap grid "
            + "grid-area grid-auto-columns grid-auto-flow grid-auto-rows grid-column grid-column-end grid-column-gap grid-column-start "
            + "grid-gap grid-row grid-row-end grid-row-gap grid-row-start grid-template grid-template-areas grid-template-columns "
            + "grid-template-rows height hyphens image-rendering inset isolation justify-content justify-items justify-self left "
            + "letter-spacing line-break line-height list-style list-style-image list-style-position list-style-type margin margin-bottom "
            + "margin-left margin-right margin-top mask max-height max-width min-height min-width mix-blend-mode object-fit "
            + "object-position opacity order orphans outline outline-color outline-offset outline-style outline-width overflow "
            + "overflow-wrap overflow-x overflow-y padding padding-bottom padding-left padding-right padding-top page-break-after "
            + "page-break-before page-break-inside perspective perspective-origin place-content place-items place-self pointer-events "
            + "position quotes resize right row-gap scroll-behavior scroll-margin scroll-padding scrollbar-width tab-size table-layout "
            + "text-align text-align-last text-decoration text-decoration-color text-decoration-line text-decoration-style text-indent "
            + "text-overflow text-shadow text-transform top touch-action transform transform-origin transform-style transition "
            + "transition-delay transition-duration transition-property transition-timing-function unicode-bidi user-select "
            + "vertical-align visibility white-space widows width will-change word-break word-spacing word-wrap writing-mode z-index "
            + "zoom").split(" ")));

    private static final Set<String> INHERITED = new HashSet<>(Arrays.asList(
            "color", "font-family", "font-size", "font-style", "font-weight", "line-height", "text-align", "text-transform",
            "visibility", "cursor", "white-space", "list-style-type", "letter-spacing", "direction", "word-spacing", "text-indent"));

    private static final Set<String> BLOCKS = new HashSet<>(Arrays.asList(
            "html", "body", "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dl", "dt", "dd", "pre", "blockquote",
            "form", "fieldset", "section", "article", "aside", "header", "footer", "nav", "main", "figure", "figcaption",
            "address", "hr", "details", "summary", "center", "dialog", "menu", "legend", "optgroup"));

    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "head", "script", "style", "meta", "link", "title", "template", "base", "noscript", "datalist", "area", "param", "source", "track"));

    private DomStyle() {
    }

    // ------------------------------------------------------------------ nombres

    static String cssName(String s) {
        if (s.equals("cssFloat")) {
            return "float";
        }
        if (s.startsWith("--")) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        String r = sb.toString();
        if (r.startsWith("webkit-") || r.startsWith("moz-") || r.startsWith("ms-")) {
            r = "-" + r;
        }
        return r;
    }

    static boolean known(String kebab) {
        return KNOWN.contains(kebab) || kebab.startsWith("-webkit-") || kebab.startsWith("-moz-") || kebab.startsWith("-ms-");
    }

    // ------------------------------------------------------------------ declaraciones

    /** Lee "a: b; c: d !important" respetando paréntesis y comillas. Cada entrada: {nombre, valor, prioridad}. */
    static List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ';';
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
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ';' && depth == 0) {
                addDecl(out, text.substring(start, Math.min(i, text.length())));
                start = i + 1;
            }
        }
        return out;
    }

    private static void addDecl(List<String[]> out, String decl) {
        int colon = decl.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String name = decl.substring(0, colon).trim();
        String value = decl.substring(colon + 1).trim();
        String prio = "";
        if (value.toLowerCase(Locale.ROOT).endsWith("!important")) {
            value = value.substring(0, value.length() - 10).trim();
            prio = "important";
        }
        if (name.isEmpty() || value.isEmpty()) {
            return;
        }
        if (!name.startsWith("--")) {
            name = name.toLowerCase(Locale.ROOT);
        }
        for (String[] d : out) {
            if (d[0].equals(name)) {
                d[1] = value;
                d[2] = prio;
                return;
            }
        }
        out.add(new String[] {name, value, prio});
    }

    static String serialize(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] d : list) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(d[0]).append(": ").append(d[1]);
            if (!d[2].isEmpty()) {
                sb.append(" !important");
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static final String[] SIDES = {"top", "right", "bottom", "left"};

    private static boolean boxShorthand(String p) {
        return p.equals("margin") || p.equals("padding");
    }

    // ------------------------------------------------------------------ objetos

    /** CSSStyleDeclaration: element.style (editable) o el resultado de getComputedStyle (solo lectura). */
    abstract static class Decl extends DomObj {
        final DomHost h;

        Decl(DomHost h, JsObject proto) {
            super(proto, null);
            this.h = h;
        }

        abstract String read(String prop);

        abstract String priority(String prop);

        abstract List<String> names();

        /** Cambia una propiedad (valor vacío la quita). Falso si es de solo lectura. */
        abstract boolean write(String prop, String value, String priority);

        abstract String cssText();

        abstract boolean setCssText(String text);

        boolean readOnly() {
            return false;
        }

        @Override
        public Prop getOwn(Object key) {
            Prop p = super.getOwn(key);
            if (p != null || !(key instanceof String s) || s.isEmpty()) {
                return p;
            }
            int idx = Js.arrayIndex(s);
            if (idx >= 0) {
                List<String> ns = names();
                return idx < ns.size() ? new Prop(ns.get(idx), Prop.E) : null;
            }
            if (this.proto != null && this.proto.hasProperty(s)) {
                return null;
            }
            String k = cssName(s);
            if (!known(k)) {
                return null;
            }
            return new Prop(read(k), readOnly() ? Prop.E : Prop.W | Prop.E | Prop.C);
        }

        @Override
        protected boolean writeOwn(Object key, Prop p, Object value) {
            if (key instanceof String s && super.getOwn(key) == null) {
                String k = cssName(s);
                if (known(k)) {
                    return write(k, value == Js.NULL ? "" : Js.toStr(value), "");
                }
            }
            return super.writeOwn(key, p, value);
        }
    }

    /** element.style */
    static final class StyleObj extends Decl {
        final Node owner;

        StyleObj(DomHost h, JsObject proto, Node owner) {
            super(h, proto);
            this.owner = owner;
        }

        private List<String[]> list() {
            return parse(this.owner.attr("style"));
        }

        private void save(List<String[]> l) {
            this.owner.setAttr("style", serialize(l));
            Node.touch();
        }

        @Override
        String read(String prop) {
            List<String[]> l = list();
            for (String[] d : l) {
                if (d[0].equals(prop)) {
                    return d[1];
                }
            }
            for (String box : new String[] {"margin", "padding"}) {
                if (prop.startsWith(box + "-")) {
                    for (String[] d : l) {
                        if (d[0].equals(box)) {
                            String[] t = d[1].trim().split("\\s+");
                            String top = t[0];
                            String right = t.length > 1 ? t[1] : t[0];
                            String bottom = t.length > 2 ? t[2] : t[0];
                            String left = t.length > 3 ? t[3] : right;
                            String side = prop.substring(box.length() + 1);
                            return side.equals("top") ? top : side.equals("right") ? right : side.equals("bottom") ? bottom : side.equals("left") ? left : "";
                        }
                    }
                }
            }
            return "";
        }

        @Override
        String priority(String prop) {
            for (String[] d : list()) {
                if (d[0].equals(prop)) {
                    return d[2];
                }
            }
            return "";
        }

        @Override
        List<String> names() {
            List<String> out = new ArrayList<>();
            for (String[] d : list()) {
                out.add(d[0]);
            }
            return out;
        }

        @Override
        boolean write(String prop, String value, String priority) {
            List<String[]> l = list();
            String v = value.trim();
            if (boxShorthand(prop)) {
                for (String side : SIDES) {
                    String lp = prop + "-" + side;
                    l.removeIf(d -> d[0].equals(lp));
                }
            }
            String[] hit = null;
            for (String[] d : l) {
                if (d[0].equals(prop)) {
                    hit = d;
                    break;
                }
            }
            if (v.isEmpty()) {
                if (hit != null) {
                    l.remove(hit);
                    save(l);
                }
                return true;
            }
            if (v.toLowerCase(Locale.ROOT).endsWith("!important")) {
                v = v.substring(0, v.length() - 10).trim();
                priority = "important";
            }
            if (hit != null) {
                hit[1] = v;
                hit[2] = priority;
            } else {
                l.add(new String[] {prop, v, priority});
            }
            save(l);
            return true;
        }

        @Override
        String cssText() {
            return serialize(list());
        }

        @Override
        boolean setCssText(String text) {
            save(parse(text));
            return true;
        }
    }

    /** getComputedStyle(el): valores finales de solo lectura. */
    static final class ComputedObj extends Decl {
        final Node el;
        private final IdentityHashMap<Node, Map<String, String>> declared = new IdentityHashMap<>();
        private final IdentityHashMap<Node, Double> sizes = new IdentityHashMap<>();

        ComputedObj(DomHost h, JsObject proto, Node el) {
            super(h, proto);
            this.el = el;
        }

        @Override
        boolean readOnly() {
            return true;
        }

        private Map<String, String> decls(Node n) {
            Map<String, String> m = this.declared.get(n);
            if (m == null) {
                Css.Sheet sheet = this.h.env.sheet();
                if (sheet != null) {
                    m = sheet.match(n);
                } else {
                    String inline = n.attr("style");
                    m = inline == null ? new HashMap<>() : Css.parseDeclarations(inline, null);
                }
                this.declared.put(n, m);
            }
            return m;
        }

        private static Node parentElement(Node n) {
            Node p = n.parent;
            return p != null && p.isElement() ? p : null;
        }

        double fontSize(Node n) {
            Double cached = this.sizes.get(n);
            if (cached != null) {
                return cached;
            }
            Node p = parentElement(n);
            double parent = p == null ? 16 : fontSize(p);
            double size = parent;
            String v = decls(n).get("font-size");
            if (v != null) {
                size = resolveSize(v.trim(), parent, size);
            } else {
                switch (n.tag) {
                    case "h1":
                        size = parent * 2;
                        break;
                    case "h2":
                        size = parent * 1.5;
                        break;
                    case "h3":
                        size = parent * 1.17;
                        break;
                    case "h5":
                        size = parent * 0.83;
                        break;
                    case "h6":
                        size = parent * 0.67;
                        break;
                    case "small", "sub", "sup", "big":
                        size = n.tag.equals("big") ? parent * 1.2 : parent * 0.83;
                        break;
                    default:
                        break;
                }
            }
            this.sizes.put(n, size);
            return size;
        }

        private static double resolveSize(String v, double parent, double dflt) {
            switch (v) {
                case "xx-small":
                    return 9;
                case "x-small":
                    return 10;
                case "small":
                    return 13;
                case "medium":
                    return 16;
                case "large":
                    return 18;
                case "x-large":
                    return 24;
                case "xx-large":
                    return 32;
                case "smaller":
                    return parent / 1.2;
                case "larger":
                    return parent * 1.2;
                case "inherit", "unset", "initial":
                    return parent;
                default:
                    break;
            }
            if (v.endsWith("%")) {
                try {
                    return parent * Double.parseDouble(v.substring(0, v.length() - 1)) / 100;
                } catch (NumberFormatException e) {
                    return dflt;
                }
            }
            double d = Css.length(v, parent, 16);
            return Double.isNaN(d) ? dflt : d;
        }

        private static String px(double d) {
            return Js.numToString(Math.round(d * 1000.0) / 1000.0) + "px";
        }

        private String lookup(Node n, String prop) {
            Map<String, String> m = decls(n);
            String v = m.get(prop);
            if (v != null && (v.equals("inherit") || v.equals("unset") || v.equals("initial"))) {
                if (v.equals("initial")) {
                    return null;
                }
                Node p = parentElement(n);
                return p == null ? null : lookup(p, prop);
            }
            if (v == null && INHERITED.contains(prop)) {
                Node p = parentElement(n);
                if (p != null) {
                    return lookup(p, prop);
                }
            }
            return v;
        }

        private String display(Node n) {
            String d = decls(n).get("display");
            if (d != null && !d.equals("inherit") && !d.equals("initial") && !d.equals("unset")) {
                return d;
            }
            String t = n.tag;
            if (HIDDEN.contains(t)) {
                return "none";
            }
            if (BLOCKS.contains(t)) {
                return "block";
            }
            switch (t) {
                case "li":
                    return "list-item";
                case "table":
                    return "table";
                case "tr":
                    return "table-row";
                case "td", "th":
                    return "table-cell";
                case "thead":
                    return "table-header-group";
                case "tbody":
                    return "table-row-group";
                case "tfoot":
                    return "table-footer-group";
                case "caption":
                    return "table-caption";
                case "input", "button", "select", "textarea", "meter", "progress":
                    return "inline-block";
                default:
                    return "inline";
            }
        }

        @Override
        String read(String prop) {
            Node n = this.el;
            String v;
            switch (prop) {
                case "display":
                    return display(n);
                case "font-size":
                    return px(fontSize(n));
                case "font-weight": {
                    v = lookup(n, prop);
                    if (v == null) {
                        return switch (n.tag) {
                            case "b", "strong", "th", "h1", "h2", "h3", "h4", "h5", "h6" -> "700";
                            default -> "400";
                        };
                    }
                    return v.equals("bold") || v.equals("bolder") ? "700" : v.equals("normal") ? "400" : v.equals("lighter") ? "300" : v;
                }
                case "font-style": {
                    v = lookup(n, prop);
                    return v != null ? v : n.tag.equals("i") || n.tag.equals("em") || n.tag.equals("cite") || n.tag.equals("var") ? "italic" : "normal";
                }
                case "color": {
                    v = lookup(n, prop);
                    return v == null ? (n.is("a") ? "rgb(0, 0, 238)" : "rgb(0, 0, 0)") : color(v);
                }
                case "background-color": {
                    v = decls(n).get(prop);
                    if (v == null) {
                        v = decls(n).get("background");
                        if (v != null) {
                            for (String t : v.split("\\s+")) {
                                String c = color(t);
                                if (c.startsWith("rgb")) {
                                    return c;
                                }
                            }
                        }
                        return "rgba(0, 0, 0, 0)";
                    }
                    return color(v);
                }
                case "visibility":
                    v = lookup(n, prop);
                    return v == null ? "visible" : v;
                case "line-height": {
                    v = lookup(n, prop);
                    if (v == null || v.equals("normal")) {
                        return "normal";
                    }
                    double d = Css.length(v, fontSize(n), 16);
                    if (!Double.isNaN(d) && (v.endsWith("px") || v.endsWith("em") || v.endsWith("pt") || v.endsWith("rem"))) {
                        return px(d);
                    }
                    try {
                        return px(Double.parseDouble(v) * fontSize(n));
                    } catch (NumberFormatException e) {
                        return v;
                    }
                }
                case "text-align":
                    v = lookup(n, prop);
                    return v == null ? "start" : v;
                case "font-family":
                    v = lookup(n, prop);
                    return v == null ? "serif" : v;
                case "width", "height", "top", "left", "right", "bottom", "min-width", "min-height", "max-width", "max-height": {
                    v = decls(n).get(prop);
                    if ((prop.equals("width") || prop.equals("height")) && (v == null || v.equals("auto") || v.endsWith("%"))) {
                        double[] box = DomGeometry.page(this.h, n);                 // el tamaño real que le dio la disposición
                        if (box != null) {
                            return px(prop.equals("width") ? box[2] : box[3]);
                        }
                    }
                    if (v == null) {
                        return prop.startsWith("max-") ? "none" : prop.startsWith("min-") ? "0px" : "auto";
                    }
                    double d = Css.length(v, fontSize(n), 16);
                    return Double.isNaN(d) ? v : px(d);
                }
                case "margin-top", "margin-right", "margin-bottom", "margin-left", "padding-top", "padding-right", "padding-bottom",
                        "padding-left", "border-top-width", "border-right-width", "border-bottom-width", "border-left-width": {
                    v = decls(n).get(prop);
                    if (v == null) {
                        return "0px";
                    }
                    double d = Css.length(v, fontSize(n), 16);
                    return Double.isNaN(d) ? "0px" : px(d);
                }
                case "opacity":
                    v = decls(n).get(prop);
                    return v == null ? "1" : v;
                case "position":
                    v = decls(n).get(prop);
                    return v == null ? "static" : v;
                case "overflow", "overflow-x", "overflow-y":
                    v = decls(n).get(prop);
                    if (v == null) {
                        v = decls(n).get("overflow");
                    }
                    return v == null ? "visible" : v;
                case "z-index":
                    v = decls(n).get(prop);
                    return v == null ? "auto" : v;
                case "float":
                    v = decls(n).get("float");
                    return v == null ? "none" : v;
                case "box-sizing":
                    v = decls(n).get(prop);
                    return v == null ? "content-box" : v;
                case "white-space":
                    v = lookup(n, prop);
                    return v == null ? (n.is("pre") ? "pre" : "normal") : v;
                case "cursor":
                    v = lookup(n, prop);
                    return v == null ? "auto" : v;
                case "list-style-type":
                    v = lookup(n, prop);
                    return v == null ? "disc" : v;
                case "text-decoration", "text-decoration-line":
                    v = decls(n).get("text-decoration-line");
                    return v == null ? (n.is("a") || n.is("u") ? "underline" : "none") : v;
                case "text-transform":
                    v = lookup(n, prop);
                    return v == null ? "none" : v;
                case "transform", "filter", "box-shadow", "text-shadow", "background-image":
                    v = decls(n).get(prop);
                    return v == null ? "none" : v;
                case "pointer-events":
                    v = lookup(n, prop);
                    return v == null ? "auto" : v;
                case "direction":
                    v = lookup(n, prop);
                    return v == null ? "ltr" : v;
                default:
                    v = lookup(n, prop);
                    return v == null ? "" : v;
            }
        }

        @Override
        String priority(String prop) {
            return "";
        }

        @Override
        List<String> names() {
            List<String> out = new ArrayList<>(decls(this.el).keySet());
            return out;
        }

        @Override
        boolean write(String prop, String value, String priority) {
            return false;
        }

        @Override
        String cssText() {
            return "";
        }

        @Override
        boolean setCssText(String text) {
            return false;
        }
    }

    // ------------------------------------------------------------------ colores

    private static final Map<String, String> NAMED = new HashMap<>();

    static {
        String[] t = {"black", "0,0,0", "white", "255,255,255", "red", "255,0,0", "green", "0,128,0", "blue", "0,0,255",
            "yellow", "255,255,0", "gray", "128,128,128", "grey", "128,128,128", "orange", "255,165,0", "purple", "128,0,128",
            "silver", "192,192,192", "maroon", "128,0,0", "navy", "0,0,128", "teal", "0,128,128", "lime", "0,255,0",
            "aqua", "0,255,255", "cyan", "0,255,255", "fuchsia", "255,0,255", "magenta", "255,0,255", "olive", "128,128,0",
            "pink", "255,192,203", "brown", "165,42,42", "gold", "255,215,0", "indigo", "75,0,130", "violet", "238,130,238",
            "coral", "255,127,80", "crimson", "220,20,60", "tomato", "255,99,71", "salmon", "250,128,114", "khaki", "240,230,140",
            "lightgray", "211,211,211", "lightgrey", "211,211,211", "darkgray", "169,169,169", "darkgrey", "169,169,169",
            "dimgray", "105,105,105", "whitesmoke", "245,245,245", "gainsboro", "220,220,220", "skyblue", "135,206,235",
            "royalblue", "65,105,225", "steelblue", "70,130,180", "darkblue", "0,0,139", "darkgreen", "0,100,0", "darkred", "139,0,0"};
        for (int i = 0; i < t.length; i += 2) {
            NAMED.put(t[i], t[i + 1]);
        }
    }

    private static int hex(String s) {
        return Integer.parseInt(s, 16);
    }

    /** Un color CSS como lo devuelve getComputedStyle: rgb(r, g, b) o rgba(r, g, b, a). Si no se entiende, se deja igual. */
    static String color(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.equals("transparent")) {
                return "rgba(0, 0, 0, 0)";
            }
            String named = NAMED.get(s);
            if (named != null) {
                return "rgb(" + named.replace(",", ", ") + ")";
            }
            if (s.startsWith("#")) {
                String h = s.substring(1);
                if (h.length() == 3 || h.length() == 4) {
                    StringBuilder b = new StringBuilder();
                    for (char c : h.toCharArray()) {
                        b.append(c).append(c);
                    }
                    h = b.toString();
                }
                if (h.length() == 6) {
                    return "rgb(" + hex(h.substring(0, 2)) + ", " + hex(h.substring(2, 4)) + ", " + hex(h.substring(4, 6)) + ")";
                }
                if (h.length() == 8) {
                    double a = Math.round(hex(h.substring(6, 8)) / 255.0 * 100.0) / 100.0;
                    return "rgba(" + hex(h.substring(0, 2)) + ", " + hex(h.substring(2, 4)) + ", " + hex(h.substring(4, 6)) + ", "
                            + Js.numToString(a) + ")";
                }
            }
            if (s.startsWith("rgb")) {
                int open = s.indexOf('(');
                int close = s.lastIndexOf(')');
                if (open > 0 && close > open) {
                    String[] p = s.substring(open + 1, close).trim().split("[\\s,/]+");
                    if (p.length >= 3) {
                        int[] c = new int[3];
                        for (int i = 0; i < 3; i++) {
                            c[i] = p[i].endsWith("%") ? (int) Math.round(Double.parseDouble(p[i].replace("%", "")) * 2.55)
                                    : (int) Math.round(Double.parseDouble(p[i]));
                        }
                        if (p.length >= 4 && !p[3].equals("1")) {
                            return "rgba(" + c[0] + ", " + c[1] + ", " + c[2] + ", " + p[3] + ")";
                        }
                        return "rgb(" + c[0] + ", " + c[1] + ", " + c[2] + ")";
                    }
                }
            }
        } catch (NumberFormatException ignored) {
            // no es un color que se entienda: se devuelve tal cual
        }
        return v;
    }

    // ------------------------------------------------------------------ API para el resto del DOM

    static JsObject declaration(DomHost h, Node n) {
        return new StyleObj(h, h.protos.get("CSSStyleDeclaration"), n);
    }

    static JsObject computed(DomHost h, Node n) {
        return new ComputedObj(h, h.protos.get("CSSStyleDeclaration"), n);
    }

    private static Decl self(Object t) {
        if (t instanceof Decl d) {
            return d;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static String normName(String name) {
        return name.startsWith("--") ? name : name.trim().toLowerCase(Locale.ROOT);
    }

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject p = h.iface("CSSStyleDeclaration", null);
        Lib.method(r, p, "getPropertyValue", 1, (t, a) -> self(t).read(cssName(normName(DomHost.str(a, 0)))));
        Lib.method(r, p, "getPropertyPriority", 1, (t, a) -> self(t).priority(normName(DomHost.str(a, 0))));
        Lib.method(r, p, "setProperty", 2, (t, a) -> {
            Decl d = self(t);
            String name = normName(DomHost.str(a, 0));
            Object v = Js.arg(a, 1);
            String prio = Js.arg(a, 2) == Js.UNDEFINED ? "" : Js.toStr(Js.arg(a, 2)).toLowerCase(Locale.ROOT);
            if (!d.readOnly() && (name.startsWith("--") || known(name))) {
                d.write(name, v == Js.NULL ? "" : Js.toStr(v), prio.equals("important") ? "important" : "");
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, p, "removeProperty", 1, (t, a) -> {
            Decl d = self(t);
            String name = normName(DomHost.str(a, 0));
            String old = d.read(name);
            if (!d.readOnly()) {
                d.write(name, "", "");
            }
            return old;
        });
        Lib.method(r, p, "item", 1, (t, a) -> {
            List<String> ns = self(t).names();
            double i = Js.toInteger(Js.arg(a, 0));
            return i >= 0 && i < ns.size() ? ns.get((int) i) : "";
        });
        Lib.getter(r, p, "length", (t, a) -> Js.num(self(t).names().size()));
        Lib.getter(r, p, "parentRule", (t, a) -> Js.NULL);
        Lib.accessor(r, p, "cssText", (t, a) -> self(t).cssText(), (t, a) -> {
            self(t).setCssText(Js.toStr(Js.arg(a, 0)));
            return Js.UNDEFINED;
        });
        p.setHidden(JsSymbol.ITERATOR, new NativeFunction(r, "values", 0, (t, a) -> {
            List<Object> names = new ArrayList<>(self(t).names());
            return r.arrayValues.call(new JsArray(r.arrayProto, new ArrayList<>(names)), Js.EMPTY);
        }));

        // CSS.escape / CSS.supports
        JsObject css = new JsObject(r.objectProto);
        Lib.tag(css, "CSS");
        Lib.method(r, css, "escape", 1, (t, a) -> escape(DomHost.str(a, 0)));
        Lib.method(r, css, "supports", 2, (t, a) -> {
            if (a.length >= 2) {
                String name = normName(Js.toStr(a[0]));
                return name.startsWith("--") || known(name);
            }
            String cond = DomHost.str(a, 0);
            int colon = cond.indexOf(':');
            if (colon < 0) {
                return false;
            }
            String name = cond.substring(0, colon).replace("(", "").trim().toLowerCase(Locale.ROOT);
            return name.startsWith("--") || known(name);
        });
        r.global.setHidden("CSS", css);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) {
                sb.append('�');
            } else if ((c >= '0' && c <= '9') && (i == 0 || (i == 1 && s.charAt(0) == '-'))) {
                sb.append('\\').append(Integer.toHexString(c)).append(' ');
            } else if (c == '-' && s.length() == 1) {
                sb.append("\\-");
            } else if (c >= 0x80 || c == '-' || c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                sb.append(c);
            } else if (c < 0x20 || c == 0x7f) {
                sb.append('\\').append(Integer.toHexString(c)).append(' ');
            } else {
                sb.append('\\').append(c);
            }
        }
        return sb.toString();
    }
}
