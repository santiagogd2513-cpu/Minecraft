package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Convierte nodos en texto HTML (innerHTML, outerHTML). */
final class DomHtml {
    private static final Set<String> VOID = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr", "basefont",
            "frame", "keygen", "command"));
    private static final Set<String> RAW = new HashSet<>(Arrays.asList("script", "style", "xmp", "iframe", "noembed", "noframes", "plaintext"));

    private DomHtml() {
    }

    static String inner(Node n) {
        StringBuilder sb = new StringBuilder();
        for (Node c : n.children) {
            write(c, sb, 0, n);
        }
        return sb.toString();
    }

    static String outer(Node n) {
        StringBuilder sb = new StringBuilder();
        write(n, sb, 0, n.parent);
        return sb.toString();
    }

    private static void write(Node n, StringBuilder sb, int depth, Node parent) {
        if (depth > 400 || sb.length() > (8 << 20)) {
            return;
        }
        if (n.isText()) {
            if (parent != null && parent.tag != null && RAW.contains(parent.tag)) {
                sb.append(n.text);
            } else {
                sb.append(escText(n.text));
            }
            return;
        }
        if (n.isComment()) {
            sb.append("<!--").append(n.text).append("-->");
            return;
        }
        if (n.tag.equals("#document") || n.tag.equals("#fragment")) {
            for (Node c : n.children) {
                write(c, sb, depth + 1, n);
            }
            return;
        }
        sb.append('<').append(n.tag);
        for (Map.Entry<String, String> a : n.attrs.entrySet()) {
            sb.append(' ').append(a.getKey()).append("=\"").append(escAttr(a.getValue())).append('"');
        }
        sb.append('>');
        if (VOID.contains(n.tag)) {
            return;
        }
        for (Node c : n.children) {
            write(c, sb, depth + 1, n);
        }
        sb.append("</").append(n.tag).append('>');
    }

    static String escText(String s) {
        if (s.indexOf('&') < 0 && s.indexOf('<') < 0 && s.indexOf('>') < 0 && s.indexOf(' ') < 0) {
            return s;
        }
        return s.replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String escAttr(String s) {
        if (s.indexOf('&') < 0 && s.indexOf('"') < 0 && s.indexOf(' ') < 0) {
            return s;
        }
        return s.replace("&", "&amp;").replace(" ", "&nbsp;").replace("\"", "&quot;");
    }
}
