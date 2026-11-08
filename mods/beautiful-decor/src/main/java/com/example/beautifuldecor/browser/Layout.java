package com.example.beautifuldecor.browser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Acomoda un documento HTML en una {@link Page}: texto que se parte en líneas, bloques con sus separaciones, listas, tablas
 * sencillas, imágenes, enlaces y controles de formularios (solo se ven). No usa nada de Minecraft: el ancho del texto lo
 * da {@link Metrics} y el tamaño de las imágenes ya cargadas lo da {@link Images}.
 * <p>
 * Los colores de la página no se usan: quien dibuja pinta todo en modo oscuro según el "rol" de cada pieza.
 */
public final class Layout {
    public interface Metrics {
        /** Ancho en píxeles del texto con esos estilos (flags de {@link Page}) y tamaño (porcentaje). */
        int width(String text, int flags, int scale);

        /** Alto de una línea de texto con ese tamaño. */
        int lineHeight(int scale);
    }

    public interface Images {
        /** Tamaño natural {ancho, alto} de la imagen, null si todavía no se sabe, {0, 0} si no se pudo cargar. */
        int[] size(String src);
    }

    /** Un píxel CSS en píxeles de pantalla del juego (el texto de Minecraft es más chico que el de una web). */
    private static final double CSS_PX = 0.55;
    private static final int MAX_ITEMS = 90000;
    private static final int MAX_TABLE_DEPTH = 4;

    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "script", "style", "head", "template", "svg", "canvas", "noembed", "noframes", "map", "datalist", "object",
            "embed", "math", "link", "meta", "base", "title", "param", "track", "source", "option", "optgroup", "colgroup",
            "col", "frame", "frameset", "select-menu", "#comment", "#fragment"));
    private static final Set<String> BLOCK = new HashSet<>(Arrays.asList(
            "#document", "html", "body", "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd",
            "blockquote", "pre", "form", "fieldset", "section", "article", "aside", "nav", "header", "footer", "main",
            "figure", "figcaption", "address", "details", "summary", "hr", "center", "legend", "menu", "dir", "caption",
            "noscript", "table", "tr", "td", "th", "tbody", "thead", "tfoot", "dialog", "listing", "xmp"));
    private static final Set<String> DISPLAY_BLOCK = new HashSet<>(Arrays.asList(
            "block", "flex", "grid", "list-item", "table", "flow-root", "table-row", "table-cell", "table-row-group"));
    private static final Set<String> DISPLAY_INLINE = new HashSet<>(Arrays.asList(
            "inline", "inline-block", "inline-flex", "inline-grid", "contents", "inline-table"));

    private static final int T_TEXT = 0;
    private static final int T_SPACE = 1;
    private static final int T_IMAGE = 2;
    private static final int T_CONTROL = 3;

    private Layout() {
    }

    // ------------------------------------------------------------------ estilo calculado

    private static final class St {
        int flags;
        int role;
        int scale = 100;
        int align;
        boolean pre;
        int transform;
        String href;
        boolean listNone;
        Node node;

        St() {
        }

        St(St o) {
            this.flags = o.flags;
            this.role = o.role;
            this.scale = o.scale;
            this.align = o.align;
            this.pre = o.pre;
            this.transform = o.transform;
            this.href = o.href;
            this.listNone = o.listNone;
            this.node = o.node;
        }
    }

    private static final class Frag {
        int type;
        String text;
        int w;
        int h;
        int flags;
        int scale;
        int role;
        String href;
        String src;
        String alt;
        int fill;
        String control;
        Node node;
    }

    private static final class Cell {
        Node node;
        int col;
        int span;
        int row;
    }

    // ------------------------------------------------------------------ entrada

    public static Page layout(Node doc, String baseUrl, Css.Sheet sheet, int width, Metrics metrics, Images images, int zoomPct) {
        Layout.Builder b = new Layout.Builder(new Page(), baseUrl, sheet, metrics, images, Math.max(50, zoomPct));
        Node baseTag = doc.find("base");
        if (baseTag != null && baseTag.attr("href") != null) {
            String nb = Url.resolve(baseUrl, baseTag.attr("href"));
            if (nb != null && Url.isWeb(nb)) {
                b.base = nb;
            }
        }
        Node title = doc.find("title");
        b.page.title = title == null ? "" : title.textContent();
        b.page.width = width;
        b.scripting = doc.scripting;
        b.cx = 6;
        b.cw = Math.max(60, width - 12);
        b.out = b.page.items;
        b.walkChildren(doc, new St());
        b.flushLine();
        b.page.height = b.y + 12;
        return b.page;
    }

    // ------------------------------------------------------------------ el que acomoda

    private static final class Builder {
        final Page page;
        String base;
        final Css.Sheet sheet;
        final Metrics m;
        final Images images;
        final int zoom;

        List<Page.Item> out;
        int y;
        int gap;
        int cx;
        int cw;
        int align;
        int extent;
        boolean measuring;
        int tableDepth;
        boolean skipNewline;
        boolean scripting;
        final List<int[]> lists = new ArrayList<>();
        List<Frag> line = new ArrayList<>();
        int lineW;

        Builder(Page page, String base, Css.Sheet sheet, Metrics m, Images images, int zoom) {
            this.page = page;
            this.base = base;
            this.sheet = sheet;
            this.m = m;
            this.images = images;
            this.zoom = zoom;
        }

        // ---------------------------------------------------------- utilidades

        int eff(St s) {
            return Math.max(40, Math.round(s.scale * this.zoom / 100f));
        }

        boolean full() {
            return this.out.size() > MAX_ITEMS;
        }

        void applyGap() {
            this.y += this.gap;
            this.gap = 0;
        }

        /** Apunta la zona de un elemento (para los clics que caen donde no hay texto). */
        void box(Node n, int x, int yy, int w, int h) {
            if (!this.measuring && h > 0 && w > 0 && n.isElement() && this.page.boxes.size() < MAX_ITEMS) {
                this.page.boxes.add(new Page.Box(n, x, yy, w, h));
            }
        }

        boolean hasContent() {
            for (Frag f : this.line) {
                if (f.type != T_SPACE) {
                    return true;
                }
            }
            return false;
        }

        Page.Item add(int kind, int x, int yy, int w, int h) {
            Page.Item it = new Page.Item();
            it.kind = kind;
            it.x = x;
            it.y = yy;
            it.w = w;
            it.h = h;
            this.out.add(it);
            return it;
        }

        static int css(Map<String, String> d, String key, int dflt) {
            String v = d.get(key);
            if (v == null) {
                return dflt;
            }
            double px = Css.length(v.trim(), 16, 16);
            if (Double.isNaN(px)) {
                return dflt;
            }
            return (int) Math.max(0, Math.min(40, Math.round(px * CSS_PX)));
        }

        static int cssIndent(Map<String, String> d) {
            return css(d, "margin-left", 0) + css(d, "padding-left", 0);
        }

        String resolve(String ref) {
            return Url.resolve(this.base, ref);
        }

        void noteAnchor(Node n) {
            if (this.measuring) {
                return;
            }
            String id = n.attr("id");
            if (id != null && !id.isEmpty()) {
                this.page.anchors.putIfAbsent(id, this.y + this.gap);
            }
            if (n.is("a")) {
                String name = n.attr("name");
                if (name != null && !name.isEmpty()) {
                    this.page.anchors.putIfAbsent(name, this.y + this.gap);
                }
            }
        }

        // ---------------------------------------------------------- estilo

        St style(Node n, St parent, Map<String, String> d) {
            St s = new St(parent);
            s.node = n;
            String tag = n.tag;
            switch (tag) {
                case "b", "strong" -> s.flags |= Page.BOLD;
                case "th" -> {
                    s.flags |= Page.BOLD;
                    s.align = 1;
                }
                case "i", "em", "cite", "dfn", "var", "address" -> s.flags |= Page.ITALIC;
                case "u", "ins" -> s.flags |= Page.UNDERLINE;
                case "s", "strike", "del" -> s.flags |= Page.STRIKE;
                case "code", "kbd", "samp", "tt" -> s.role = Page.ROLE_CODE;
                case "pre", "listing", "xmp" -> {
                    s.role = Page.ROLE_CODE;
                    s.pre = true;
                }
                case "small" -> s.scale = s.scale * 85 / 100;
                case "big" -> s.scale = s.scale * 120 / 100;
                case "sub", "sup" -> s.scale = s.scale * 75 / 100;
                case "h1" -> heading(s, 180);
                case "h2" -> heading(s, 145);
                case "h3" -> heading(s, 125);
                case "h4" -> heading(s, 110);
                case "h5" -> heading(s, 100);
                case "h6" -> heading(s, 90);
                case "mark" -> s.role = Page.ROLE_MARK;
                case "figcaption" -> s.role = Page.ROLE_MUTED;
                case "caption" -> {
                    s.flags |= Page.BOLD;
                    s.align = 1;
                }
                case "summary", "legend" -> s.flags |= Page.BOLD;
                case "center" -> s.align = 1;
                default -> { }
            }
            String al = n.attr("align");
            if (al != null) {
                switch (al.toLowerCase(Locale.ROOT)) {
                    case "center", "middle" -> s.align = 1;
                    case "right" -> s.align = 2;
                    case "left" -> s.align = 0;
                    default -> { }
                }
            }
            applyCss(s, d, parent);
            return s;
        }

        static void heading(St s, int pct) {
            s.flags |= Page.BOLD;
            s.role = Page.ROLE_HEADING;
            s.scale = s.scale * pct / 100;
        }

        void applyCss(St s, Map<String, String> d, St parent) {
            if (d.isEmpty()) {
                return;
            }
            String v = d.get("font-weight");
            if (v != null) {
                boolean bold = v.equals("bold") || v.equals("bolder");
                if (!bold && v.matches("\\d+")) {
                    bold = Integer.parseInt(v) >= 600;
                }
                if (bold) {
                    s.flags |= Page.BOLD;
                } else if (v.equals("normal") || v.equals("lighter") || v.matches("\\d+")) {
                    s.flags &= ~Page.BOLD;
                }
            }
            v = d.get("font-style");
            if (v != null) {
                if (v.startsWith("italic") || v.startsWith("oblique")) {
                    s.flags |= Page.ITALIC;
                } else if (v.equals("normal")) {
                    s.flags &= ~Page.ITALIC;
                }
            }
            v = d.get("text-decoration-line");
            if (v != null) {
                if (v.contains("none")) {
                    s.flags &= ~(Page.UNDERLINE | Page.STRIKE);
                }
                if (v.contains("underline")) {
                    s.flags |= Page.UNDERLINE;
                }
                if (v.contains("line-through")) {
                    s.flags |= Page.STRIKE;
                }
            }
            v = d.get("text-align");
            if (v != null) {
                switch (v) {
                    case "center", "-webkit-center" -> s.align = 1;
                    case "right", "end" -> s.align = 2;
                    case "left", "start", "justify" -> s.align = 0;
                    default -> { }
                }
            }
            v = d.get("white-space");
            if (v != null) {
                s.pre = v.equals("pre") || v.equals("pre-wrap") || v.equals("break-spaces");
            }
            v = d.get("text-transform");
            if (v != null) {
                s.transform = switch (v) {
                    case "uppercase" -> 1;
                    case "lowercase" -> 2;
                    case "capitalize" -> 3;
                    default -> 0;
                };
            }
            v = d.get("list-style-type");
            if (v != null) {
                s.listNone = v.equals("none");
            }
            v = d.get("font-size");
            if (v != null) {
                int pct = fontPercent(v, parent.scale);
                if (pct > 0) {
                    s.scale = Math.max(50, Math.min(300, pct));
                }
            }
        }

        static int fontPercent(String v, int parentScale) {
            switch (v) {
                case "xx-small" -> {
                    return 60;
                }
                case "x-small" -> {
                    return 70;
                }
                case "small" -> {
                    return 85;
                }
                case "medium" -> {
                    return 100;
                }
                case "large" -> {
                    return 115;
                }
                case "x-large" -> {
                    return 130;
                }
                case "xx-large", "xxx-large" -> {
                    return 160;
                }
                case "smaller" -> {
                    return parentScale * 85 / 100;
                }
                case "larger" -> {
                    return parentScale * 118 / 100;
                }
                default -> { }
            }
            if (v.endsWith("%")) {
                try {
                    return (int) Math.round(parentScale * Double.parseDouble(v.substring(0, v.length() - 1)) / 100.0);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            if (v.endsWith("rem")) {
                double px = Css.length(v, 16, 16);
                return Double.isNaN(px) ? -1 : (int) Math.round(px / 16.0 * 100.0);
            }
            if (v.endsWith("em")) {
                double em = Css.length(v, 1, 1);
                return Double.isNaN(em) ? -1 : (int) Math.round(parentScale * em);
            }
            double px = Css.length(v, 16, 16);
            return Double.isNaN(px) ? -1 : (int) Math.round(px / 16.0 * 100.0);
        }

        // ---------------------------------------------------------- recorrido

        void walkChildren(Node n, St s) {
            for (Node c : n.children) {
                if (full()) {
                    return;
                }
                walk(c, s);
            }
        }

        boolean isBlock(String tag, String display) {
            if (display != null) {
                if (DISPLAY_BLOCK.contains(display)) {
                    return true;
                }
                if (DISPLAY_INLINE.contains(display)) {
                    return false;
                }
            }
            return BLOCK.contains(tag);
        }

        void walk(Node n, St parent) {
            if (n.isText()) {
                text(n.text, parent);
                return;
            }
            String tag = n.tag;
            if (SKIP.contains(tag) || n.attr("hidden") != null) {
                return;
            }
            if (tag.equals("dialog") && n.attr("open") == null) {
                return;
            }
            if (this.scripting && tag.equals("noscript")) {
                return;
            }
            Map<String, String> d = this.sheet == null ? Map.of() : this.sheet.match(n);
            String display = d.get("display");
            if ("none".equals(display) || "hidden".equals(d.get("visibility")) || "collapse".equals(d.get("visibility"))
                    || "0".equals(d.get("opacity"))) {
                return;
            }
            St s = style(n, parent, d);
            noteAnchor(n);
            switch (tag) {
                case "br" -> lineBreak(s);
                case "img" -> image(n, s, d);
                case "input", "button", "select", "textarea" -> control(n, s);
                case "hr" -> rule();
                case "audio" -> audio(n, s);
                case "video" -> video(n, s);
                case "iframe" -> iframe(n, s);
                case "table" -> table(n, s, d);
                case "a" -> {
                    String href = n.attr("href") == null ? null : resolve(n.attr("href"));
                    if (href != null && n.attr("download") != null && Url.isWeb(href)) {
                        href = "hs:download?url=" + Url.encode(href);
                    }
                    if (href != null) {
                        s.href = href;
                        s.flags |= Page.UNDERLINE;
                        s.role = Page.ROLE_LINK;
                        applyLinkCss(s, d);
                    }
                    genericElement(n, s, d, display);
                }
                case "ul", "ol", "menu", "dir" -> list(n, s, d);
                case "li" -> {
                    if (isBlock("li", display)) {
                        listItem(n, s, d);
                    } else {
                        walkChildren(n, s);
                    }
                }
                case "pre", "listing", "xmp" -> preBlock(n, s, d);
                case "blockquote" -> quote(n, s, d);
                case "p" -> block(n, s, d, 6, 6, 0, true);
                case "h1" -> block(n, s, d, 9, 6, 0, true);
                case "h2" -> block(n, s, d, 8, 5, 0, true);
                case "h3" -> block(n, s, d, 7, 4, 0, true);
                case "h4", "h5", "h6" -> block(n, s, d, 6, 4, 0, true);
                case "dl" -> block(n, s, d, 4, 4, 0, true);
                case "dt" -> block(n, s, d, 3, 0, 0, true);
                case "dd" -> block(n, s, d, 1, 1, 14, true);
                case "figure" -> block(n, s, d, 6, 6, 0, true);
                case "form", "fieldset" -> block(n, s, d, 4, 4, 0, true);
                default -> genericElement(n, s, d, display);
            }
        }

        /** Tras aplicar el CSS, un enlace conserva el color de enlace; solo se respeta si le quitan el subrayado. */
        void applyLinkCss(St s, Map<String, String> d) {
            String v = d.get("text-decoration-line");
            if (v != null && v.contains("none")) {
                s.flags &= ~Page.UNDERLINE;
            }
        }

        void genericElement(Node n, St s, Map<String, String> d, String display) {
            if (isBlock(n.tag, display)) {
                block(n, s, d, 0, 0, 0, true);
            } else {
                walkChildren(n, s);
            }
        }

        // ---------------------------------------------------------- bloques

        void block(Node n, St s, Map<String, String> d, int top, int bottom, int indent, boolean cssIndent) {
            flushLine();
            top = css(d, "margin-top", top);
            bottom = css(d, "margin-bottom", bottom);
            if (cssIndent) {
                indent += cssIndent(d);
            }
            this.gap = Math.max(this.gap, top);
            int oldCx = this.cx;
            int oldCw = this.cw;
            int oldAlign = this.align;
            int startY = this.y + this.gap;
            this.cx += indent;
            this.cw = Math.max(40, this.cw - indent);
            this.align = s.align;
            walkChildren(n, s);
            flushLine();
            box(n, oldCx, startY, oldCw, this.y - startY);
            this.cx = oldCx;
            this.cw = oldCw;
            this.align = oldAlign;
            this.gap = Math.max(this.gap, bottom);
        }

        void quote(Node n, St s, Map<String, String> d) {
            flushLine();
            this.gap = Math.max(this.gap, css(d, "margin-top", 6));
            applyGap();
            Page.Item bar = add(Page.RECT, this.cx + 2, this.y, 2, 0);
            bar.fill = Page.FILL_QUOTE;
            int boxX = this.cx;
            int boxW = this.cw;
            int oldCx = this.cx;
            int oldCw = this.cw;
            int oldAlign = this.align;
            this.cx += 12;
            this.cw = Math.max(40, this.cw - 12);
            this.align = s.align;
            walkChildren(n, s);
            flushLine();
            bar.h = Math.max(2, this.y - bar.y);
            box(n, boxX, bar.y, boxW, bar.h);
            this.cx = oldCx;
            this.cw = oldCw;
            this.align = oldAlign;
            this.gap = Math.max(this.gap, css(d, "margin-bottom", 6));
        }

        void preBlock(Node n, St s, Map<String, String> d) {
            flushLine();
            this.gap = Math.max(this.gap, 6);
            applyGap();
            Page.Item bg = add(Page.RECT, this.cx, this.y, this.cw, 0);
            bg.fill = Page.FILL_CODE;
            int oldCx = this.cx;
            int oldCw = this.cw;
            int oldAlign = this.align;
            this.cx += 4;
            this.cw = Math.max(40, this.cw - 8);
            this.align = 0;
            this.y += 3;
            this.skipNewline = true;
            walkChildren(n, s);
            this.skipNewline = false;
            flushLine();
            this.y += 3;
            bg.h = this.y - bg.y;
            box(n, bg.x, bg.y, bg.w, bg.h);
            this.cx = oldCx;
            this.cw = oldCw;
            this.align = oldAlign;
            this.gap = Math.max(this.gap, 6);
        }

        void rule() {
            flushLine();
            this.gap = Math.max(this.gap, 5);
            applyGap();
            Page.Item r = add(Page.RECT, this.cx, this.y, this.cw, 1);
            r.fill = Page.FILL_RULE;
            this.y += 1;
            this.gap = 5;
        }

        void list(Node n, St s, Map<String, String> d) {
            boolean ordered = n.is("ol");
            int start = 1;
            try {
                if (n.attr("start") != null) {
                    start = Integer.parseInt(n.attr("start").trim());
                }
            } catch (NumberFormatException ignored) {
                // se queda en 1
            }
            this.lists.add(new int[]{ordered ? 1 : 0, start});
            int indent = s.listNone ? 0 : 14;
            if (d.containsKey("padding-left") || d.containsKey("margin-left")) {
                indent = cssIndent(d);
            }
            int depth = this.lists.size();
            block(n, s, d, depth == 1 ? 4 : 1, depth == 1 ? 4 : 1, indent, false);
            this.lists.remove(this.lists.size() - 1);
        }

        void listItem(Node n, St s, Map<String, String> d) {
            flushLine();
            this.gap = Math.max(this.gap, 1);
            applyGap();
            int[] list = this.lists.isEmpty() ? null : this.lists.get(this.lists.size() - 1);
            String label = null;
            if (!s.listNone) {
                label = list != null && list[0] == 1 ? (list[1]++) + "." : "•";
            }
            if (label != null) {
                int sc = eff(s);
                int bw = this.m.width(label, 0, sc);
                Page.Item it = add(Page.TEXT, this.cx - bw - 4, this.y, bw, this.m.lineHeight(sc));
                it.text = label;
                it.scale = sc;
                it.role = Page.ROLE_TEXT;
                it.node = n;
                this.extent = Math.max(this.extent, it.x + it.w);
            }
            block(n, s, d, 0, 1, 0, true);
        }

        // ---------------------------------------------------------- texto en línea

        static boolean isCjk(int cp) {
            return (cp >= 0x2E80 && cp <= 0x9FFF) || (cp >= 0xAC00 && cp <= 0xD7AF) || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0xFF00 && cp <= 0xFFEF) || (cp >= 0x3000 && cp <= 0x30FF);
        }

        static boolean isZeroWidth(char c) {
            return (c >= 0x200B && c <= 0x200F) || c == 0x2060 || c == 0xFEFF || c == 0x00AD;
        }

        void text(String raw, St s) {
            String t = raw;
            boolean dirty = false;
            for (int i = 0; i < t.length() && !dirty; i++) {
                dirty = isZeroWidth(t.charAt(i));
            }
            if (dirty) {
                StringBuilder sb = new StringBuilder(t.length());
                for (int i = 0; i < t.length(); i++) {
                    if (!isZeroWidth(t.charAt(i))) {
                        sb.append(t.charAt(i));
                    }
                }
                t = sb.toString();
            }
            if (s.pre) {
                preText(t, s);
                return;
            }
            int n = t.length();
            int i = 0;
            while (i < n && !full()) {
                char c = t.charAt(i);
                if (Character.isWhitespace(c)) {
                    while (i < n && Character.isWhitespace(t.charAt(i))) {
                        i++;
                    }
                    addSpace(s);
                    continue;
                }
                int j = i;
                int cp = t.codePointAt(i);
                if (isCjk(cp)) {
                    j = i + Character.charCount(cp);
                } else {
                    while (j < n && !Character.isWhitespace(t.charAt(j)) && !isCjk(t.codePointAt(j))) {
                        j += Character.charCount(t.codePointAt(j));
                    }
                }
                addWord(transform(t.substring(i, j), s), s);
                i = j;
            }
        }

        static String transform(String w, St s) {
            switch (s.transform) {
                case 1:
                    return w.toUpperCase();
                case 2:
                    return w.toLowerCase();
                case 3:
                    return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
                default:
                    return w;
            }
        }

        void preText(String t, St s) {
            if (this.skipNewline) {
                this.skipNewline = false;
                if (t.startsWith("\r\n")) {
                    t = t.substring(2);
                } else if (t.startsWith("\n")) {
                    t = t.substring(1);
                }
            }
            StringBuilder seg = new StringBuilder();
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\n') {
                    if (seg.length() > 0) {
                        addWord(seg.toString(), s);
                        seg.setLength(0);
                    }
                    lineBreak(s);
                } else if (c == '\t') {
                    seg.append("    ");
                } else if (c != '\r') {
                    seg.append(c == ' ' ? ' ' : c);
                }
            }
            if (seg.length() > 0) {
                addWord(seg.toString(), s);
            }
        }

        void addSpace(St s) {
            if (this.line.isEmpty() || this.line.get(this.line.size() - 1).type == T_SPACE) {
                return;
            }
            int sc = eff(s);
            Frag f = new Frag();
            f.type = T_SPACE;
            f.text = " ";
            f.flags = s.flags;
            f.scale = sc;
            f.role = s.role;
            f.href = s.href;
            f.node = s.node;
            f.w = this.m.width(" ", s.flags, sc);
            f.h = this.m.lineHeight(sc);
            this.line.add(f);
            this.lineW += f.w;
        }

        void addWord(String word, St s) {
            if (word.isEmpty()) {
                return;
            }
            int sc = eff(s);
            int w = this.m.width(word, s.flags, sc);
            if (this.lineW + w > this.cw && hasContent()) {
                flushLine();
            }
            if (w > this.cw) {
                int i = 0;
                while (i < word.length()) {
                    int end = i;
                    int acc = 0;
                    while (end < word.length()) {
                        int step = Character.charCount(word.codePointAt(end));
                        int cwid = this.m.width(word.substring(end, end + step), s.flags, sc);
                        if (acc + cwid > this.cw - this.lineW && end > i) {
                            break;
                        }
                        acc += cwid;
                        end += step;
                    }
                    if (end == i) {
                        end = i + Character.charCount(word.codePointAt(i));
                    }
                    pushText(word.substring(i, end), s, sc);
                    i = end;
                    if (i < word.length()) {
                        flushLine();
                    }
                }
                return;
            }
            pushText(word, s, sc);
        }

        void pushText(String piece, St s, int sc) {
            Frag f = new Frag();
            f.type = T_TEXT;
            f.text = piece;
            f.flags = s.flags;
            f.scale = sc;
            f.role = s.role;
            f.href = s.href;
            f.node = s.node;
            f.w = this.m.width(piece, s.flags, sc);
            f.h = this.m.lineHeight(sc);
            this.line.add(f);
            this.lineW += f.w;
        }

        void addInline(Frag f) {
            if (this.lineW + f.w > this.cw && hasContent()) {
                flushLine();
            }
            this.line.add(f);
            this.lineW += f.w;
        }

        void lineBreak(St s) {
            if (hasContent()) {
                flushLine();
            } else {
                this.line.clear();
                this.lineW = 0;
                applyGap();
                this.y += this.m.lineHeight(eff(s));
            }
        }

        void flushLine() {
            while (!this.line.isEmpty() && this.line.get(this.line.size() - 1).type == T_SPACE) {
                this.lineW -= this.line.remove(this.line.size() - 1).w;
            }
            if (this.line.isEmpty()) {
                this.lineW = 0;
                return;
            }
            applyGap();
            int lineH = 0;
            for (Frag f : this.line) {
                lineH = Math.max(lineH, f.h);
            }
            int total = this.lineW;
            int x = this.cx;
            if (this.align == 1) {
                x += Math.max(0, (this.cw - total) / 2);
            } else if (this.align == 2) {
                x += Math.max(0, this.cw - total);
            }
            this.extent = Math.max(this.extent, x + total);
            Page.Item cur = null;
            StringBuilder sb = null;
            for (Frag f : this.line) {
                if (f.type == T_TEXT || f.type == T_SPACE) {
                    if (cur != null && cur.flags == f.flags && cur.scale == f.scale && cur.role == f.role
                            && cur.node == f.node && java.util.Objects.equals(cur.href, f.href)) {
                        sb.append(f.text);
                        cur.w += f.w;
                    } else {
                        if (cur != null) {
                            cur.text = sb.toString();
                        }
                        cur = add(Page.TEXT, x, this.y + lineH - f.h, f.w, f.h);
                        cur.flags = f.flags;
                        cur.scale = f.scale;
                        cur.role = f.role;
                        cur.href = f.href;
                        cur.node = f.node;
                        sb = new StringBuilder(f.text);
                    }
                } else {
                    if (cur != null) {
                        cur.text = sb.toString();
                        cur = null;
                    }
                    Page.Item it = add(f.type == T_IMAGE ? Page.IMAGE : Page.CONTROL, x, this.y + lineH - f.h, f.w, f.h);
                    it.src = f.control != null ? f.control : f.src;
                    it.alt = f.alt;
                    it.text = f.text;
                    it.fill = f.fill;
                    it.href = f.href;
                    it.flags = f.flags;
                    it.scale = f.scale;
                    it.role = f.role;
                    it.node = f.node;
                }
                x += f.w;
            }
            if (cur != null) {
                cur.text = sb.toString();
            }
            this.y += lineH;
            this.line = new ArrayList<>();
            this.lineW = 0;
        }

        // ---------------------------------------------------------- imágenes, controles y multimedia

        static int attrInt(Node n, String name) {
            String v = n.attr(name);
            if (v == null) {
                return -1;
            }
            v = v.trim().toLowerCase(Locale.ROOT);
            if (v.endsWith("%") || v.endsWith("auto")) {
                return -1;
            }
            if (v.endsWith("px")) {
                v = v.substring(0, v.length() - 2);
            }
            try {
                return (int) Math.round(Double.parseDouble(v));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        String imageSource(Node n) {
            for (String key : new String[]{"src", "data-src", "data-lazy-src", "data-original"}) {
                String v = n.attr(key);
                if (v != null && !v.isBlank() && !v.trim().toLowerCase(Locale.ROOT).startsWith("data:")) {
                    return resolve(v);
                }
            }
            String set = n.attr("srcset");
            if (set == null) {
                set = n.attr("data-srcset");
            }
            if (set != null && !set.isBlank()) {
                String first = set.trim().split(",")[0].trim().split("\\s+")[0];
                if (!first.isEmpty() && !first.toLowerCase(Locale.ROOT).startsWith("data:")) {
                    return resolve(first);
                }
            }
            return null;
        }

        void image(Node n, St s, Map<String, String> d) {
            String alt = n.attr("alt") == null ? "" : n.attr("alt").trim();
            String src = imageSource(n);
            int aw = attrInt(n, "width");
            int ah = attrInt(n, "height");
            if (aw < 0) {
                aw = pxOf(d.get("width"));
            }
            if (ah < 0) {
                ah = pxOf(d.get("height"));
            }
            if ((aw >= 0 && aw <= 2) || (ah >= 0 && ah <= 2)) {
                return;                                          // píxeles de seguimiento
            }
            if (src == null || !Url.isWeb(src)) {
                altText(alt, s);
                return;
            }
            int[] nat = this.images.size(src);
            if (!this.measuring) {
                this.page.imageSrcs.add(src);
            }
            if (nat != null && nat[0] == 0) {
                altText(alt, s);
                return;
            }
            int w;
            int h;
            if (aw > 0 && ah > 0) {
                w = aw;
                h = ah;
            } else if (nat != null) {
                if (aw > 0) {
                    w = aw;
                    h = Math.max(1, Math.round(nat[1] * (float) aw / nat[0]));
                } else if (ah > 0) {
                    h = ah;
                    w = Math.max(1, Math.round(nat[0] * (float) ah / nat[1]));
                } else {
                    w = nat[0];
                    h = nat[1];
                }
            } else if (aw > 0) {
                w = aw;
                h = Math.max(8, aw * 3 / 4);
            } else if (ah > 0) {
                h = ah;
                w = ah;
            } else {
                w = 96;
                h = 64;
            }
            w = Math.max(1, (int) Math.round(w * CSS_PX * this.zoom / 100.0));
            h = Math.max(1, (int) Math.round(h * CSS_PX * this.zoom / 100.0));
            if (w > this.cw) {
                h = Math.max(1, Math.round(h * (float) this.cw / w));
                w = this.cw;
            }
            if (h > 1600) {
                w = Math.max(1, Math.round(w * 1600f / h));
                h = 1600;
            }
            Frag f = new Frag();
            f.type = T_IMAGE;
            f.w = w;
            f.h = h;
            f.src = src;
            f.alt = alt;
            f.href = s.href;
            f.node = s.node;
            f.scale = eff(s);
            addInline(f);
        }

        static int pxOf(String v) {
            if (v == null) {
                return -1;
            }
            double px = Css.length(v.trim(), 16, 16);
            return Double.isNaN(px) ? -1 : (int) Math.round(px);
        }

        void altText(String alt, St s) {
            if (alt == null || alt.isBlank()) {
                return;
            }
            St muted = new St(s);
            muted.role = s.href != null ? Page.ROLE_LINK : Page.ROLE_MUTED;
            muted.flags |= Page.ITALIC;
            text("[" + alt + "]", muted);
        }

        void control(Node n, St s) {
            String tag = n.tag;
            String type = n.attr("type") == null ? "text" : n.attr("type").toLowerCase(Locale.ROOT);
            int sc = eff(s);
            int lh = this.m.lineHeight(sc);
            Frag f = new Frag();
            f.type = T_CONTROL;
            f.scale = sc;
            f.flags = 0;
            f.role = Page.ROLE_TEXT;
            f.href = s.href;
            f.node = n;
            String label;
            switch (tag) {
                case "button" -> {
                    label = n.textContent();
                    if (label.isEmpty()) {
                        label = "Botón";
                    }
                    f.control = "button";
                    f.fill = Page.FILL_BUTTON;
                }
                case "select" -> {
                    Node chosen = null;
                    Node first = null;
                    for (Node o : optionsOf(n)) {
                        first = first == null ? o : first;
                        if (o.isChecked()) {
                            chosen = o;
                        }
                    }
                    Node opt = chosen != null ? chosen : first;
                    label = (opt == null ? "" : opt.textContent()) + " ▾";
                    f.control = "select";
                    f.fill = Page.FILL_FIELD;
                }
                case "textarea" -> {
                    label = tailLines(n.currentValue(), 3);
                    f.control = "textarea";
                    f.fill = Page.FILL_FIELD;
                }
                default -> {
                    switch (type) {
                        case "hidden" -> {
                            return;
                        }
                        case "checkbox", "radio" -> {
                            f.control = type + (n.isChecked() ? "-on" : "");
                            f.fill = Page.FILL_FIELD;
                            f.w = Math.max(8, lh - 2);
                            f.h = f.w;
                            f.text = "";
                            addInline(f);
                            return;
                        }
                        case "submit", "button", "reset", "image" -> {
                            String v = n.attr("value");
                            if (v == null || v.isBlank()) {
                                v = type.equals("reset") ? "Restablecer" : type.equals("image") ? (n.attr("alt") == null ? "Enviar" : n.attr("alt")) : "Enviar";
                            }
                            label = v;
                            f.control = "button";
                            f.fill = Page.FILL_BUTTON;
                        }
                        default -> {
                            String v = n.currentValue();
                            boolean placeholder = v.isEmpty();
                            if (placeholder) {
                                v = n.attr("placeholder") == null ? "" : n.attr("placeholder");
                            }
                            if (type.equals("password") && !placeholder) {
                                v = "•".repeat(Math.min(12, v.length()));
                            }
                            label = v;
                            f.control = placeholder ? "field-hint" : "field";
                            f.fill = Page.FILL_FIELD;
                        }
                    }
                }
            }
            label = clip(label);
            f.text = label;
            int textW = this.m.width(label, 0, sc);
            if (f.fill == Page.FILL_BUTTON) {
                f.w = textW + 12;
                f.h = lh + 6;
            } else {
                int size = attrInt(n, "size");
                int base = size > 0 ? Math.min(size, 60) * 5 : 130;
                if (n.is("textarea")) {
                    f.h = lh * 3 + 6;
                    base = Math.max(base, 150);
                } else {
                    f.h = lh + 6;
                }
                f.w = Math.max(40, Math.min(Math.max(base, textW + 10), this.cw));
            }
            f.w = Math.min(f.w, this.cw);
            addInline(f);
        }

        /** Los option de un select (también los que están dentro de un optgroup). */
        static List<Node> optionsOf(Node select) {
            List<Node> list = new ArrayList<>();
            for (Node o : select.children) {
                if (o.is("option")) {
                    list.add(o);
                } else if (o.is("optgroup")) {
                    for (Node p : o.children) {
                        if (p.is("option")) {
                            list.add(p);
                        }
                    }
                }
            }
            return list;
        }

        /** Las últimas {@code max} líneas del texto (cada una recortada). */
        static String tailLines(String v, int max) {
            String[] lines = v.replace("\r", "").split("\n", -1);
            int from = Math.max(0, lines.length - max);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < lines.length; i++) {
                if (i > from) {
                    sb.append('\n');
                }
                sb.append(clip(lines[i]));
            }
            return sb.toString();
        }

        static String clip(String label) {
            if (label.length() > 60) {
                return label.substring(0, 60) + "…";
            }
            return label;
        }

        void audio(Node n, St s) {
            String src = n.attr("src");
            if (src == null) {
                for (Node c : n.children) {
                    if (c.is("source") && c.attr("src") != null) {
                        src = c.attr("src");
                        break;
                    }
                }
            }
            String abs = src == null ? null : resolve(src);
            if (abs == null || !Url.isWeb(abs)) {
                return;
            }
            St link = new St(s);
            link.href = "hs:play?src=" + Url.encode(abs);
            String name = Url.fileName(abs, null, null);
            fakeButton("▶ " + name, link);
        }

        void video(Node n, St s) {
            String src = n.attr("src");
            if (src == null) {
                for (Node c : n.children) {
                    if (c.is("source") && c.attr("src") != null) {
                        src = c.attr("src");
                        break;
                    }
                }
            }
            String abs = src == null ? null : resolve(src);
            flushLine();
            if (abs == null || !Url.isWeb(abs)) {
                St muted = new St(s);
                muted.role = Page.ROLE_MUTED;
                muted.flags |= Page.ITALIC;
                text("[Video]", muted);
                flushLine();
                return;
            }
            St play = new St(s);
            play.href = "hs:playvideo?src=" + Url.encode(abs);
            fakeButton("\u25b6 " + Url.fileName(abs, null, null), play);
            St link = new St(s);
            link.href = "hs:download?url=" + Url.encode(abs);
            link.role = Page.ROLE_LINK;
            link.flags |= Page.UNDERLINE;
            addSpace(s);
            text("Descargar", link);
            flushLine();
        }

        void iframe(Node n, St s) {
            String abs = n.attr("src") == null ? null : resolve(n.attr("src"));
            if (abs == null || !Url.isWeb(abs)) {
                return;
            }
            St link = new St(s);
            link.href = abs;
            link.role = Page.ROLE_LINK;
            link.flags |= Page.UNDERLINE;
            text("[Contenido incrustado: " + Url.hostOf(abs) + "]", link);
        }

        void fakeButton(String label, St s) {
            int sc = eff(s);
            Frag f = new Frag();
            f.type = T_CONTROL;
            f.control = "button";
            f.fill = Page.FILL_BUTTON;
            f.text = label.length() > 48 ? label.substring(0, 48) + "…" : label;
            f.scale = sc;
            f.href = s.href;
            f.node = s.node;
            f.w = Math.min(this.cw, this.m.width(f.text, 0, sc) + 12);
            f.h = this.m.lineHeight(sc) + 6;
            addInline(f);
        }

        // ---------------------------------------------------------- tablas

        void table(Node t, St s, Map<String, String> d) {
            flushLine();
            List<List<Cell>> rows = new ArrayList<>();
            Node[] caption = new Node[1];
            List<Node> stray = new ArrayList<>();
            int ncols = collectRows(t, rows, caption, stray);
            if (this.tableDepth >= MAX_TABLE_DEPTH || ncols == 0) {
                block(t, s, d, 4, 4, 0, true);
                return;
            }
            this.gap = Math.max(this.gap, 5);
            if (caption[0] != null) {
                walk(caption[0], s);
                flushLine();
            }
            for (Node extra : stray) {
                walk(extra, s);
            }
            flushLine();
            int pad = 3;
            boolean border = false;
            String b = t.attr("border");
            if (b != null) {
                try {
                    border = Integer.parseInt(b.trim()) > 0;
                } catch (NumberFormatException e) {
                    border = true;
                }
            }
            this.tableDepth++;
            int[] pref = new int[ncols];
            int[] min = new int[ncols];
            for (List<Cell> row : rows) {
                for (Cell c : row) {
                    if (c.span == 1) {
                        int nat = measureNatural(c.node, s) + 2 * pad;
                        pref[c.col] = Math.max(pref[c.col], nat);
                        min[c.col] = Math.max(min[c.col], Math.min(nat, longestWord(c.node, s) + 2 * pad));
                    }
                }
            }
            for (List<Cell> row : rows) {
                for (Cell c : row) {
                    if (c.span > 1) {
                        int need = measureNatural(c.node, s) + 2 * pad;
                        int have = 0;
                        for (int k = 0; k < c.span; k++) {
                            have += pref[c.col + k];
                        }
                        if (need > have) {
                            int extra = (need - have) / c.span + 1;
                            for (int k = 0; k < c.span; k++) {
                                pref[c.col + k] += extra;
                            }
                        }
                    }
                }
            }
            for (int k = 0; k < ncols; k++) {
                pref[k] = Math.max(pref[k], 2 * pad + 4);
                min[k] = Math.min(Math.max(min[k], 2 * pad + 4), pref[k]);
            }
            int avail = this.cw;
            int[] widths = fitColumns(pref, min, avail, wantsFullWidth(t, d));
            this.tableDepth--;
            this.tableDepth++;
            applyGap();
            int tableX = this.cx;
            int tableW = 0;
            for (int w : widths) {
                tableW += w;
            }
            if (s.align == 1 || t.is("center")) {
                tableX += Math.max(0, (avail - tableW) / 2);
            }
            int rowTop = this.y;
            int tableTop = rowTop;
            for (List<Cell> row : rows) {
                int rowH = 0;
                int[] heights = new int[row.size()];
                for (int i = 0; i < row.size(); i++) {
                    Cell c = row.get(i);
                    int x = tableX;
                    for (int k = 0; k < c.col; k++) {
                        x += widths[k];
                    }
                    int w = 0;
                    for (int k = 0; k < c.span; k++) {
                        w += widths[c.col + k];
                    }
                    heights[i] = layoutCell(c.node, s, x + pad, Math.max(10, w - 2 * pad), rowTop + pad) + 2 * pad;
                    rowH = Math.max(rowH, heights[i]);
                }
                for (Cell c : row) {
                    int x = tableX;
                    for (int k = 0; k < c.col; k++) {
                        x += widths[k];
                    }
                    int w = 0;
                    for (int k = 0; k < c.span; k++) {
                        w += widths[c.col + k];
                    }
                    box(c.node, x, rowTop, w, rowH);
                    if (border) {
                        Page.Item r = add(Page.RECT, x, rowTop, w, rowH);
                        r.fill = Page.FILL_BORDER;
                        r.flags = Page.OUTLINE;
                    }
                }
                rowTop += rowH;
            }
            box(t, tableX, tableTop, tableW, rowTop - tableTop);
            this.y = rowTop;
            this.extent = Math.max(this.extent, tableX + tableW);
            this.tableDepth--;
            this.gap = 5;
        }

        boolean wantsFullWidth(Node t, Map<String, String> d) {
            String w = t.attr("width");
            String cw = d.get("width");
            return "100%".equals(w == null ? null : w.trim()) || "100%".equals(cw == null ? null : cw.trim());
        }

        static int[] fitColumns(int[] pref, int[] min, int avail, boolean fill) {
            int n = pref.length;
            int[] w = pref.clone();
            int total = 0;
            int totalMin = 0;
            for (int i = 0; i < n; i++) {
                total += pref[i];
                totalMin += min[i];
            }
            if (total <= avail) {
                if (fill && total > 0) {
                    int extra = avail - total;
                    int given = 0;
                    for (int i = 0; i < n; i++) {
                        int add = (int) ((long) extra * pref[i] / total);
                        w[i] += add;
                        given += add;
                    }
                    w[n - 1] += extra - given;
                }
                return w;
            }
            if (totalMin >= avail) {
                int given = 0;
                for (int i = 0; i < n; i++) {
                    w[i] = Math.max(8, (int) ((long) avail * min[i] / Math.max(1, totalMin)));
                    given += w[i];
                }
                return w;
            }
            int room = avail - totalMin;
            int flex = total - totalMin;
            int given = 0;
            for (int i = 0; i < n; i++) {
                w[i] = min[i] + (flex == 0 ? 0 : (int) ((long) room * (pref[i] - min[i]) / flex));
                given += w[i];
            }
            w[n - 1] += Math.max(0, avail - given);
            return w;
        }

        int collectRows(Node t, List<List<Cell>> rows, Node[] caption, List<Node> stray) {
            List<Node[]> raw = new ArrayList<>();
            gather(t, raw, caption, stray);
            int[] occupied = new int[64];
            int ncols = 0;
            int rowIndex = 0;
            for (Node[] cells : raw) {
                List<Cell> row = new ArrayList<>();
                int col = 0;
                for (Node cellNode : cells) {
                    while (col < 63 && occupied[col] > 0) {
                        col++;
                    }
                    Cell c = new Cell();
                    c.node = cellNode;
                    c.col = Math.min(col, 62);
                    c.span = Math.max(1, Math.min(attrIntOr(cellNode, "colspan", 1), 12));
                    if (c.col + c.span > 63) {
                        c.span = 63 - c.col;
                    }
                    c.row = rowIndex;
                    int rs = Math.max(1, Math.min(attrIntOr(cellNode, "rowspan", 1), 20));
                    for (int k = 0; k < c.span; k++) {
                        occupied[c.col + k] = rs;
                    }
                    col = c.col + c.span;
                    ncols = Math.max(ncols, col);
                    row.add(c);
                }
                for (int k = 0; k < occupied.length; k++) {
                    if (occupied[k] > 0) {
                        occupied[k]--;
                    }
                }
                // las celdas con rowspan que se acaban de poner ya no cuentan para esta fila
                for (Cell c : row) {
                    int rs = Math.max(1, Math.min(attrIntOr(c.node, "rowspan", 1), 20));
                    for (int k = 0; k < c.span; k++) {
                        occupied[c.col + k] = Math.max(occupied[c.col + k], rs - 1);
                    }
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
                rowIndex++;
            }
            return ncols;
        }

        static int attrIntOr(Node n, String name, int dflt) {
            int v = attrInt(n, name);
            return v <= 0 ? dflt : v;
        }

        static boolean strayWorthy(Node c) {
            if (c.isText()) {
                return !c.text.isBlank();
            }
            return !SKIP.contains(c.tag) && !c.tag.equals("tr") && !c.tag.equals("td") && !c.tag.equals("th");
        }

        /** Junta las filas de la tabla; lo que quedó suelto entre filas (texto, divs) sube arriba como en un navegador. */
        void gather(Node n, List<Node[]> rows, Node[] caption, List<Node> stray) {
            for (Node c : n.children) {
                if (c.isText()) {
                    if (strayWorthy(c)) {
                        stray.add(c);
                    }
                    continue;
                }
                switch (c.tag) {
                    case "tr" -> {
                        List<Node> cells = new ArrayList<>();
                        for (Node cell : c.children) {
                            if (!cell.isText() && (cell.is("td") || cell.is("th"))) {
                                cells.add(cell);
                            } else if (strayWorthy(cell)) {
                                stray.add(cell);
                            }
                        }
                        if (!cells.isEmpty()) {
                            rows.add(cells.toArray(new Node[0]));
                        }
                    }
                    case "thead", "tbody", "tfoot" -> gather(c, rows, caption, stray);
                    case "caption" -> caption[0] = c;
                    case "td", "th" -> rows.add(new Node[]{c});
                    default -> {
                        if (strayWorthy(c)) {
                            stray.add(c);
                        }
                    }
                }
            }
        }

        int longestWord(Node cell, St s) {
            int best = 0;
            for (String word : cell.textContent().split("\\s+")) {
                if (!word.isEmpty()) {
                    best = Math.max(best, this.m.width(word, s.flags, eff(s)));
                }
            }
            return Math.min(best, 220);
        }

        /** Ancho que ocuparía el contenido de la celda en una sola línea. */
        int measureNatural(Node cell, St s) {
            if (this.tableDepth >= 3) {
                return Math.min(300, this.m.width(cell.textContent(), s.flags, eff(s)));
            }
            Saved saved = save();
            this.out = new ArrayList<>();
            this.measuring = true;
            this.y = 0;
            this.gap = 0;
            this.cx = 0;
            this.cw = 100000;
            this.extent = 0;
            this.align = 0;
            walkCell(cell, s);
            flushLine();
            int w = this.extent;
            restore(saved);
            return Math.min(w, 900);
        }

        /** Acomoda una celda en la caja dada y devuelve su alto. */
        int layoutCell(Node cell, St tableStyle, int x, int w, int top) {
            Saved saved = save();
            this.cx = x;
            this.cw = w;
            this.y = top;
            this.gap = 0;
            this.align = 0;
            this.extent = 0;
            walkCell(cell, tableStyle);
            flushLine();
            int h = this.y - top;
            int extentHere = this.extent;
            restore(saved);
            this.extent = Math.max(this.extent, extentHere);
            return h;
        }

        void walkCell(Node cell, St tableStyle) {
            Map<String, String> d = this.sheet == null ? Map.of() : this.sheet.match(cell);
            if ("none".equals(d.get("display"))) {
                return;
            }
            St s = style(cell, tableStyle, d);
            noteAnchor(cell);
            this.align = s.align;
            walkChildren(cell, s);
        }

        // ---------------------------------------------------------- guardar y devolver el estado

        private static final class Saved {
            List<Page.Item> out;
            int y;
            int gap;
            int cx;
            int cw;
            int align;
            int extent;
            boolean measuring;
            List<Frag> line;
            int lineW;
            boolean skipNewline;
        }

        Saved save() {
            Saved s = new Saved();
            s.out = this.out;
            s.y = this.y;
            s.gap = this.gap;
            s.cx = this.cx;
            s.cw = this.cw;
            s.align = this.align;
            s.extent = this.extent;
            s.measuring = this.measuring;
            s.line = this.line;
            s.lineW = this.lineW;
            s.skipNewline = this.skipNewline;
            this.line = new ArrayList<>();
            this.lineW = 0;
            return s;
        }

        void restore(Saved s) {
            this.out = s.out;
            this.y = s.y;
            this.gap = s.gap;
            this.cx = s.cx;
            this.cw = s.cw;
            this.align = s.align;
            this.extent = s.extent;
            this.measuring = s.measuring;
            this.line = s.line;
            this.lineW = s.lineW;
            this.skipNewline = s.skipNewline;
        }
    }
}
