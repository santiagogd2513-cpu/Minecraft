package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.HtmlParser;
import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Node, Element, Text, Comment, DocumentFragment: lo que se puede hacer con el árbol. */
final class DomNodes {
    private static final Set<String> BLOCKISH = new HashSet<>(Arrays.asList(
            "div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "tr", "table", "section", "article", "header", "footer", "nav",
            "form", "pre", "blockquote", "aside", "main", "dl", "dt", "dd", "fieldset", "figure", "figcaption", "address", "hr", "details",
            "summary", "caption"));

    private DomNodes() {
    }

    // ------------------------------------------------------------------ recorrer

    /** Agrega a {@code out} (si no es null) los descendientes (sin contar a root) que cumplen la condición, en orden del documento. */
    static void collect(Node root, List<Node> out, Predicate<Node> test) {
        for (Node c : root.children) {
            if (test.test(c) && out != null) {
                out.add(c);
            }
            if (!c.children.isEmpty()) {
                collect(c, out, test);
            }
        }
    }

    static int nodeType(Node n) {
        if (n.isText()) {
            return 3;
        }
        switch (n.tag) {
            case "#comment":
                return 8;
            case "#document":
                return 9;
            case "#fragment":
                return 11;
            default:
                return 1;
        }
    }

    static String nodeName(Node n) {
        if (n.isText()) {
            return "#text";
        }
        switch (n.tag) {
            case "#fragment":
                return "#document-fragment";
            case "#comment", "#document":
                return n.tag;
            default:
                return n.tag.toUpperCase(Locale.ROOT);
        }
    }

    static DomSelector.Sel[] sel(DomHost h, String text) {
        DomSelector.Sel[] s = h.selCache.get(text);
        if (s == null) {
            s = DomSelector.parse(h, text);
            if (h.selCache.size() > 300) {
                h.selCache.clear();
            }
            h.selCache.put(text, s);
        }
        return s;
    }

    // ------------------------------------------------------------------ cambios en el árbol

    private static boolean container(Node p) {
        return p.isElement() || p.is("#document") || p.is("#fragment");
    }

    static void checkInsert(DomHost h, Node parent, Node child) {
        if (!container(parent)) {
            throw h.domError("HierarchyRequestError", "This node type does not support this method.");
        }
        if (child.is("#document")) {
            throw h.domError("HierarchyRequestError", "Nodes of type '#document' may not be inserted inside nodes of type '" + nodeName(parent) + "'.");
        }
        for (Node p = parent; p != null; p = p.parent) {
            if (p == child) {
                throw h.domError("HierarchyRequestError", "The new child element contains the parent.");
            }
        }
        if (parent.is("#document") && child.isText()) {
            throw h.domError("HierarchyRequestError", "Nodes of type '#text' may not be inserted inside nodes of type '#document'.");
        }
    }

    /** Saca el nodo de su padre (si lo tiene). */
    static void detach(DomHost h, Node n) {
        if (n.parent != null) {
            if (h.focused != null && isInclusiveAncestor(n, h.focused)) {
                h.focused = null;
            }
            n.parent.removeChild(n);
            Node.touch();
        }
    }

    static boolean isInclusiveAncestor(Node anc, Node n) {
        for (Node p = n; p != null; p = p.parent) {
            if (p == anc) {
                return true;
            }
        }
        return false;
    }

    /** Mete el nodo (o los hijos de un fragmento) en el padre, antes de {@code ref} (null = al final). */
    static void insertNode(DomHost h, Node parent, Node child, Node ref) {
        checkInsert(h, parent, child);
        List<Node> nodes = new ArrayList<>();
        if (child.is("#fragment")) {
            nodes.addAll(child.children);
            for (Node n : nodes) {
                child.removeChild(n);
            }
        } else {
            nodes.add(child);
            if (ref == child) {
                int i = parent.indexOfChild(child);
                ref = i >= 0 && i + 1 < parent.children.size() ? parent.children.get(i + 1) : null;
            }
            detach(h, child);
        }
        int index = ref == null ? parent.children.size() : parent.indexOfChild(ref);
        if (index < 0) {
            index = parent.children.size();
        }
        for (Node n : nodes) {
            parent.insert(index++, n);
        }
        Node.touch();
        afterInsert(h, nodes);
    }

    static void afterInsert(DomHost h, List<Node> nodes) {
        for (Node n : nodes) {
            if (n.root() != h.doc || !n.isElement()) {
                continue;
            }
            List<Node> scripts = new ArrayList<>();
            if (n.is("script")) {
                scripts.add(n);
            }
            collect(n, scripts, x -> x.is("script"));
            for (Node s : scripts) {
                processScript(h, s);
            }
        }
    }

    /** Un script que acaba de entrar al documento: se ejecuta (o se descarga y se ejecuta). */
    static void processScript(DomHost h, Node s) {
        if (h.scriptsDone.containsKey(s)) {
            return;
        }
        h.scriptsDone.put(s, Boolean.TRUE);
        if (!DomHost.runnable(s)) {
            return;
        }
        String src = s.attr("src");
        if (src != null && !src.isBlank()) {
            String url = h.resolveUrl(src);
            h.env.loadScript(url, text -> {
                if (text == null) {
                    DomEvents.fireSimple(h, s, "error", false, false);
                } else {
                    h.runScript(s, text);
                    DomEvents.fireSimple(h, s, "load", false, false);
                }
            });
        } else {
            String code = s.rawText();
            if (!code.isBlank()) {
                h.runScript(s, code);
            }
        }
    }

    static void markScriptsDone(DomHost h, Node root) {
        if (root.is("script")) {
            h.scriptsDone.put(root, Boolean.TRUE);
        }
        collect(root, null, x -> {
            if (x.is("script")) {
                h.scriptsDone.put(x, Boolean.TRUE);
            }
            return false;
        });
    }

    static void clearChildren(DomHost h, Node n) {
        if (n.children.isEmpty()) {
            return;
        }
        if (h.focused != null && isInclusiveAncestor(n, h.focused) && h.focused != n) {
            h.focused = null;
        }
        for (Node c : new ArrayList<>(n.children)) {
            n.removeChild(c);
        }
        Node.touch();
    }

    static String textOf(Node n) {
        if (n.isText() || n.isComment()) {
            return n.text;
        }
        return n.rawText();
    }

    static void setText(DomHost h, Node n, String text) {
        if (n.isText() || n.isComment()) {
            n.text = text;
            Node.touch();
            return;
        }
        if (!container(n)) {
            return;
        }
        clearChildren(h, n);
        if (!text.isEmpty()) {
            n.add(Node.text(text));
        }
        Node.touch();
    }

    static void setInnerHtml(DomHost h, Node n, String html) {
        Node frag = HtmlParser.parseFragment(html);
        markScriptsDone(h, frag);
        clearChildren(h, n);
        for (Node c : new ArrayList<>(frag.children)) {
            frag.removeChild(c);
            n.add(c);
        }
        Node.touch();
    }

    static Node clone(DomHost h, Node n, boolean deep) {
        Node c;
        if (n.isText()) {
            c = Node.text(n.text);
        } else if (n.isComment()) {
            c = Node.comment(n.text);
        } else {
            c = Node.element(n.tag, new LinkedHashMap<>(n.attrs));
        }
        c.formValue = n.formValue;
        c.formChecked = n.formChecked;
        if (n.is("script") && h.scriptsDone.containsKey(n)) {
            h.scriptsDone.put(c, Boolean.TRUE);
        }
        if (deep) {
            for (Node k : n.children) {
                c.add(clone(h, k, true));
            }
        }
        return c;
    }

    static boolean isEqual(Node a, Node b) {
        if (a.isText() != b.isText() || !java.util.Objects.equals(a.tag, b.tag)) {
            return false;
        }
        if (a.isText() || a.isComment()) {
            return a.text.equals(b.text);
        }
        if (!a.attrs.equals(b.attrs) || a.children.size() != b.children.size()) {
            return false;
        }
        for (int i = 0; i < a.children.size(); i++) {
            if (!isEqual(a.children.get(i), b.children.get(i))) {
                return false;
            }
        }
        return true;
    }

    static int position(Node a, Node b) {
        if (a == b) {
            return 0;
        }
        if (a.root() != b.root()) {
            return 1 | 32 | 2;
        }
        if (isInclusiveAncestor(b, a)) {
            return 8 | 2;
        }
        if (isInclusiveAncestor(a, b)) {
            return 16 | 4;
        }
        List<Node> pa = new ArrayList<>();
        List<Node> pb = new ArrayList<>();
        for (Node p = a; p != null; p = p.parent) {
            pa.add(0, p);
        }
        for (Node p = b; p != null; p = p.parent) {
            pb.add(0, p);
        }
        int i = 0;
        while (i < pa.size() && i < pb.size() && pa.get(i) == pb.get(i)) {
            i++;
        }
        Node common = pa.get(i - 1);
        return common.indexOfChild(pa.get(i)) < common.indexOfChild(pb.get(i)) ? 4 : 2;
    }

    /** Argumentos de append/before/...: nodos tal cual, lo demás como texto. */
    static List<Node> fromArgs(DomHost h, Object[] a) {
        List<Node> out = new ArrayList<>();
        for (Object o : a) {
            Node n = DomHost.asNode(o);
            out.add(n != null ? n : Node.text(Js.toStr(o)));
        }
        return out;
    }

    private static Node elementSibling(Node n, boolean next) {
        return next ? DomSelector.nextElement(n) : DomSelector.prevElement(n);
    }

    static String innerText(Node n) {
        StringBuilder sb = new StringBuilder();
        innerText(n, sb, false);
        String s = sb.toString();
        while (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static void innerText(Node n, StringBuilder sb, boolean pre) {
        for (Node c : n.children) {
            if (c.isText()) {
                sb.append(pre ? c.text : c.text.replaceAll("[ \\t\\r\\n]+", " "));
            } else if (c.isElement()) {
                if (c.is("script") || c.is("style") || c.is("head") || c.is("template") || c.hasAttr("hidden")) {
                    continue;
                }
                if (c.is("br")) {
                    sb.append('\n');
                    continue;
                }
                boolean block = BLOCKISH.contains(c.tag);
                if (block && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                innerText(c, sb, pre || c.is("pre"));
                if (block && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
        }
    }

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject node = h.iface("Node", h.eventTargetProto);
        h.nodeProto = node;
        NativeFunction nodeCtor = (NativeFunction) node.get("constructor");
        String[] cn = {"ELEMENT_NODE", "ATTRIBUTE_NODE", "TEXT_NODE", "CDATA_SECTION_NODE", "ENTITY_REFERENCE_NODE", "ENTITY_NODE",
            "PROCESSING_INSTRUCTION_NODE", "COMMENT_NODE", "DOCUMENT_NODE", "DOCUMENT_TYPE_NODE", "DOCUMENT_FRAGMENT_NODE", "NOTATION_NODE"};
        for (int i = 0; i < cn.length; i++) {
            nodeCtor.setConst(cn[i], Js.num(i + 1));
            node.setConst(cn[i], Js.num(i + 1));
        }
        String[] pn = {"DOCUMENT_POSITION_DISCONNECTED", "DOCUMENT_POSITION_PRECEDING", "DOCUMENT_POSITION_FOLLOWING",
            "DOCUMENT_POSITION_CONTAINS", "DOCUMENT_POSITION_CONTAINED_BY", "DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC"};
        for (int i = 0; i < pn.length; i++) {
            nodeCtor.setConst(pn[i], Js.num(1 << i));
            node.setConst(pn[i], Js.num(1 << i));
        }
        DomLists.install(h);

        // DOMException
        JsObject dep = new JsObject(r.errorProto);
        h.domExceptionProto = dep;
        NativeFunction dec = Lib.ctor(r, "DOMException", 0, dep, (t, a) -> {
            throw Js.typeError("Class constructor DOMException cannot be invoked without 'new'");
        }, (a, nt) -> {
            String name = a.length > 1 && a[1] != Js.UNDEFINED ? Js.toStr(a[1]) : "Error";
            JsError e = new JsError(Lib.protoFrom(nt, dep));
            String msg = a.length > 0 && a[0] != Js.UNDEFINED ? Js.toStr(a[0]) : "";
            e.setHidden("message", msg);
            e.setHidden("name", name);
            e.setHidden("code", Js.num(DomHost.domCode(name)));
            e.setHidden("stack", name + ": " + msg + "\n    at <anonymous>");
            return e;
        });
        dep.setHidden("name", "Error");
        dep.setHidden("message", "");
        Lib.tag(dep, "DOMException");

        h.getter(node, "nodeType", n -> Js.num(nodeType(n)));
        h.getter(node, "nodeName", n -> nodeName(n));
        h.getter(node, "baseURI", n -> h.env.baseUrl());
        h.getter(node, "isConnected", n -> n.root() == h.doc);
        h.getter(node, "ownerDocument", n -> n == h.doc ? Js.NULL : h.wrap(h.doc));
        h.getter(node, "parentNode", n -> h.w(n.parent));
        h.getter(node, "parentElement", n -> n.parent != null && n.parent.isElement() ? h.wrap(n.parent) : Js.NULL);
        h.getter(node, "firstChild", n -> h.w(n.children.isEmpty() ? null : n.children.get(0)));
        h.getter(node, "lastChild", n -> h.w(n.children.isEmpty() ? null : n.children.get(n.children.size() - 1)));
        h.getter(node, "previousSibling", n -> {
            if (n.parent == null) {
                return Js.NULL;
            }
            int i = n.parent.indexOfChild(n);
            return i > 0 ? h.wrap(n.parent.children.get(i - 1)) : Js.NULL;
        });
        h.getter(node, "nextSibling", n -> {
            if (n.parent == null) {
                return Js.NULL;
            }
            int i = n.parent.indexOfChild(n);
            return i >= 0 && i + 1 < n.parent.children.size() ? h.wrap(n.parent.children.get(i + 1)) : Js.NULL;
        });
        h.getter(node, "childNodes", n -> ((DomObj) h.wrap(n)).cached("childNodes", () -> DomLists.list(h, "NodeList", true,
                () -> new ArrayList<>(n.children))));
        h.accessor(node, "nodeValue", n -> n.isText() || n.isComment() ? n.text : Js.NULL, (n, v) -> {
            if (n.isText() || n.isComment()) {
                n.text = v == Js.NULL ? "" : Js.toStr(v);
                Node.touch();
            }
        });
        h.accessor(node, "textContent", n -> n.is("#document") ? Js.NULL : textOf(n), (n, v) -> setText(h, n, v == Js.NULL ? "" : Js.toStr(v)));
        h.method(node, "hasChildNodes", 0, (n, a) -> !n.children.isEmpty());
        h.method(node, "contains", 1, (n, a) -> {
            Node o = DomHost.asNode(Js.arg(a, 0));
            return o != null && isInclusiveAncestor(n, o);
        });
        h.method(node, "getRootNode", 0, (n, a) -> h.wrap(n.root()));
        h.method(node, "appendChild", 1, (n, a) -> {
            Node c = DomHost.nodeArg(a, 0, "appendChild");
            insertNode(h, n, c, null);
            return h.wrap(c);
        });
        h.method(node, "insertBefore", 2, (n, a) -> {
            Node c = DomHost.nodeArg(a, 0, "insertBefore");
            Node ref = DomHost.asNode(Js.arg(a, 1));
            if (ref != null && ref.parent != n) {
                throw h.domError("NotFoundError", "Failed to execute 'insertBefore' on 'Node': The node before which the new node is to be inserted is not a child of this node.");
            }
            insertNode(h, n, c, ref);
            return h.wrap(c);
        });
        h.method(node, "removeChild", 1, (n, a) -> {
            Node c = DomHost.nodeArg(a, 0, "removeChild");
            if (c.parent != n) {
                throw h.domError("NotFoundError", "Failed to execute 'removeChild' on 'Node': The node to be removed is not a child of this node.");
            }
            detach(h, c);
            return h.wrap(c);
        });
        h.method(node, "replaceChild", 2, (n, a) -> {
            Node nn = DomHost.nodeArg(a, 0, "replaceChild");
            Node old = DomHost.nodeArg(a, 1, "replaceChild");
            if (old.parent != n) {
                throw h.domError("NotFoundError", "Failed to execute 'replaceChild' on 'Node': The node to be replaced is not a child of this node.");
            }
            checkInsert(h, n, nn);
            Node ref = old.parent.indexOfChild(old) + 1 < n.children.size() ? n.children.get(n.indexOfChild(old) + 1) : null;
            if (ref == nn) {
                int i = n.indexOfChild(nn);
                ref = i + 1 < n.children.size() ? n.children.get(i + 1) : null;
            }
            detach(h, old);
            insertNode(h, n, nn, ref);
            return h.wrap(old);
        });
        h.method(node, "cloneNode", 0, (n, a) -> {
            Node c = clone(h, n, Js.truthy(Js.arg(a, 0)));
            return h.wrap(c);
        });
        h.method(node, "normalize", 0, (n, a) -> {
            normalize(n);
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(node, "isEqualNode", 1, (n, a) -> {
            Node o = DomHost.asNode(Js.arg(a, 0));
            return o != null && isEqual(n, o);
        });
        h.method(node, "isSameNode", 1, (n, a) -> DomHost.asNode(Js.arg(a, 0)) == n);
        h.method(node, "compareDocumentPosition", 1, (n, a) -> Js.num(position(n, DomHost.nodeArg(a, 0, "compareDocumentPosition"))));
        h.method(node, "lookupNamespaceURI", 1, (n, a) -> Js.NULL);
        h.method(node, "isDefaultNamespace", 1, (n, a) -> true);

        // CharacterData, Text, Comment
        JsObject cd = h.iface("CharacterData", node);
        h.characterDataProto = cd;
        installChildNode(h, cd);
        h.accessor(cd, "data", n -> n.text, (n, v) -> {
            n.text = Js.toStr(v);
            Node.touch();
        });
        h.getter(cd, "length", n -> Js.num(n.text.length()));
        h.getter(cd, "previousElementSibling", n -> h.w(elementSibling(n, false)));
        h.getter(cd, "nextElementSibling", n -> h.w(elementSibling(n, true)));
        h.method(cd, "substringData", 2, (n, a) -> {
            int off = (int) Js.toInteger(Js.arg(a, 0));
            int cnt = (int) Js.toInteger(Js.arg(a, 1));
            if (off < 0 || off > n.text.length()) {
                throw h.domError("IndexSizeError", "The offset " + off + " is greater than the node's length (" + n.text.length() + ").");
            }
            return n.text.substring(off, Math.min(n.text.length(), off + Math.max(0, cnt)));
        });
        h.method(cd, "appendData", 1, (n, a) -> {
            n.text = n.text + DomHost.str(a, 0);
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(cd, "insertData", 2, (n, a) -> {
            int off = (int) Js.toInteger(Js.arg(a, 0));
            if (off < 0 || off > n.text.length()) {
                throw h.domError("IndexSizeError", "The offset " + off + " is greater than the node's length (" + n.text.length() + ").");
            }
            n.text = n.text.substring(0, off) + DomHost.str(a, 1) + n.text.substring(off);
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(cd, "deleteData", 2, (n, a) -> {
            int off = (int) Js.toInteger(Js.arg(a, 0));
            int cnt = (int) Js.toInteger(Js.arg(a, 1));
            if (off < 0 || off > n.text.length()) {
                throw h.domError("IndexSizeError", "The offset " + off + " is greater than the node's length (" + n.text.length() + ").");
            }
            n.text = n.text.substring(0, off) + n.text.substring(Math.min(n.text.length(), off + Math.max(0, cnt)));
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(cd, "replaceData", 3, (n, a) -> {
            int off = (int) Js.toInteger(Js.arg(a, 0));
            int cnt = (int) Js.toInteger(Js.arg(a, 1));
            if (off < 0 || off > n.text.length()) {
                throw h.domError("IndexSizeError", "The offset " + off + " is greater than the node's length (" + n.text.length() + ").");
            }
            n.text = n.text.substring(0, off) + DomHost.str(a, 2) + n.text.substring(Math.min(n.text.length(), off + Math.max(0, cnt)));
            Node.touch();
            return Js.UNDEFINED;
        });
        JsObject text = h.iface("Text", cd);
        h.textProto = text;
        h.getter(text, "wholeText", n -> n.text);
        h.method(text, "splitText", 1, (n, a) -> {
            int off = (int) Js.toInteger(Js.arg(a, 0));
            if (off < 0 || off > n.text.length()) {
                throw h.domError("IndexSizeError", "The offset " + off + " is larger than the node's length (" + n.text.length() + ").");
            }
            Node tail = Node.text(n.text.substring(off));
            n.text = n.text.substring(0, off);
            if (n.parent != null) {
                n.parent.insert(n.parent.indexOfChild(n) + 1, tail);
            }
            Node.touch();
            return h.wrap(tail);
        });
        Lib.ctor(r, "Text", 0, text, (t, a) -> {
            throw Js.typeError("Class constructor Text cannot be invoked without 'new'");
        }, (a, nt) -> {
            Node n = Node.text(a.length > 0 ? Js.toStr(a[0]) : "");
            DomObj d = new DomObj(Lib.protoFrom(nt, text), n);
            n.host = d;
            return d;
        });
        JsObject comment = h.iface("Comment", cd);
        h.commentProto = comment;
        Lib.ctor(r, "Comment", 0, comment, (t, a) -> {
            throw Js.typeError("Class constructor Comment cannot be invoked without 'new'");
        }, (a, nt) -> {
            Node n = Node.comment(a.length > 0 ? Js.toStr(a[0]) : "");
            DomObj d = new DomObj(Lib.protoFrom(nt, comment), n);
            n.host = d;
            return d;
        });

        // Fragmento
        JsObject frag = h.iface("DocumentFragment", node);
        h.fragmentProto = frag;
        installParentNode(h, frag);
        h.method(frag, "getElementById", 1, (n, a) -> {
            String id = DomHost.str(a, 0);
            List<Node> hit = new ArrayList<>();
            collect(n, hit, x -> x.isElement() && id.equals(x.attr("id")));
            return h.w(hit.isEmpty() ? null : hit.get(0));
        });
        Lib.ctor(r, "DocumentFragment", 0, frag, (t, a) -> {
            throw Js.typeError("Class constructor DocumentFragment cannot be invoked without 'new'");
        }, (a, nt) -> {
            Node n = Node.fragment();
            DomObj d = new DomObj(Lib.protoFrom(nt, frag), n);
            n.host = d;
            return d;
        });

        // Elemento
        JsObject el = h.iface("Element", node);
        h.elementProto = el;
        installParentNode(h, el);
        installChildNode(h, el);
        installElement(h, el);
        JsObject html = h.iface("HTMLElement", el);
        h.htmlElementProto = html;
        h.svgElementProto = h.iface("SVGElement", el);
        for (String t : new String[] {"svg", "path", "g", "circle", "rect", "line", "polyline", "polygon", "ellipse", "defs", "use",
            "symbol", "tspan", "lineargradient", "radialgradient", "stop", "clippath", "mask", "pattern", "image", "foreignobject"}) {
            h.tagProtos.put(t, h.svgElementProto);
        }
    }

    static void normalize(Node n) {
        for (int i = 0; i < n.children.size(); i++) {
            Node c = n.children.get(i);
            if (c.isText()) {
                if (c.text.isEmpty()) {
                    n.removeChild(c);
                    i--;
                } else if (i + 1 < n.children.size() && n.children.get(i + 1).isText()) {
                    c.text = c.text + n.children.get(i + 1).text;
                    n.removeChild(n.children.get(i + 1));
                    i--;
                }
            } else {
                normalize(c);
            }
        }
    }

    /** Lo de ParentNode: children, append, prepend, querySelector... */
    static void installParentNode(DomHost h, JsObject p) {
        h.getter(p, "children", n -> ((DomObj) h.wrap(n)).cached("children", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            for (Node c : n.children) {
                if (c.isElement()) {
                    out.add(c);
                }
            }
            return out;
        })));
        h.getter(p, "childElementCount", n -> {
            int k = 0;
            for (Node c : n.children) {
                if (c.isElement()) {
                    k++;
                }
            }
            return Js.num(k);
        });
        h.getter(p, "firstElementChild", n -> {
            for (Node c : n.children) {
                if (c.isElement()) {
                    return h.wrap(c);
                }
            }
            return Js.NULL;
        });
        h.getter(p, "lastElementChild", n -> {
            for (int i = n.children.size() - 1; i >= 0; i--) {
                if (n.children.get(i).isElement()) {
                    return h.wrap(n.children.get(i));
                }
            }
            return Js.NULL;
        });
        h.method(p, "append", 0, (n, a) -> {
            for (Node c : fromArgs(h, a)) {
                insertNode(h, n, c, null);
            }
            return Js.UNDEFINED;
        });
        h.method(p, "prepend", 0, (n, a) -> {
            Node first = n.children.isEmpty() ? null : n.children.get(0);
            for (Node c : fromArgs(h, a)) {
                insertNode(h, n, c, first);
            }
            return Js.UNDEFINED;
        });
        h.method(p, "replaceChildren", 0, (n, a) -> {
            List<Node> nodes = fromArgs(h, a);
            for (Node c : nodes) {
                checkInsert(h, n, c);
            }
            clearChildren(h, n);
            for (Node c : nodes) {
                insertNode(h, n, c, null);
            }
            return Js.UNDEFINED;
        });
        h.method(p, "querySelector", 1, (n, a) -> {
            List<Node> hit = DomSelector.all(h, n, sel(h, DomHost.str(a, 0)), true);
            return h.w(hit.isEmpty() ? null : hit.get(0));
        });
        h.method(p, "querySelectorAll", 1, (n, a) -> {
            List<Node> hit = DomSelector.all(h, n, sel(h, DomHost.str(a, 0)), false);
            return DomLists.list(h, "NodeList", false, () -> hit);
        });
    }

    /** Lo de ChildNode: before, after, replaceWith, remove. */
    static void installChildNode(DomHost h, JsObject p) {
        h.method(p, "remove", 0, (n, a) -> {
            detach(h, n);
            return Js.UNDEFINED;
        });
        h.method(p, "before", 0, (n, a) -> {
            if (n.parent != null) {
                for (Node c : fromArgs(h, a)) {
                    insertNode(h, n.parent, c, n);
                }
            }
            return Js.UNDEFINED;
        });
        h.method(p, "after", 0, (n, a) -> {
            if (n.parent != null) {
                List<Node> nodes = fromArgs(h, a);
                Node par = n.parent;
                Node ref = n;
                for (Node c : nodes) {
                    int i = par.indexOfChild(ref);
                    Node next = i + 1 < par.children.size() ? par.children.get(i + 1) : null;
                    insertNode(h, par, c, next);
                    ref = c.is("#fragment") ? ref : c;
                }
            }
            return Js.UNDEFINED;
        });
        h.method(p, "replaceWith", 0, (n, a) -> {
            Node par = n.parent;
            if (par != null) {
                List<Node> nodes = fromArgs(h, a);
                int i = par.indexOfChild(n);
                Node next = i + 1 < par.children.size() ? par.children.get(i + 1) : null;
                detach(h, n);
                for (Node c : nodes) {
                    if (c == next) {
                        int j = par.indexOfChild(c);
                        next = j + 1 < par.children.size() ? par.children.get(j + 1) : null;
                    }
                    insertNode(h, par, c, next);
                }
            }
            return Js.UNDEFINED;
        });
    }

    private static String attrName(DomHost h, String name, String method) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '=' || c == '"' || c == '\'' || c == '<') {
                throw h.domError("InvalidCharacterError", "Failed to execute '" + method + "' on 'Element': '" + name + "' is not a valid attribute name.");
            }
        }
        if (name.isEmpty()) {
            throw h.domError("InvalidCharacterError", "Failed to execute '" + method + "' on 'Element': '' is not a valid attribute name.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static void insertAdjacent(DomHost h, Node el, String where, List<Node> nodes) {
        String w = where.toLowerCase(Locale.ROOT);
        Node par;
        Node ref;
        switch (w) {
            case "beforebegin":
                par = el.parent;
                ref = el;
                break;
            case "afterbegin":
                par = el;
                ref = el.children.isEmpty() ? null : el.children.get(0);
                break;
            case "beforeend":
                par = el;
                ref = null;
                break;
            case "afterend":
                par = el.parent;
                int i = par == null ? -1 : par.indexOfChild(el);
                ref = par != null && i + 1 < par.children.size() ? par.children.get(i + 1) : null;
                break;
            default:
                throw h.domError("SyntaxError", "The value provided ('" + where + "') is not one of 'beforeBegin', 'afterBegin', 'beforeEnd', or 'afterEnd'.");
        }
        if (par == null) {
            if (w.equals("beforebegin") || w.equals("afterend")) {
                return;
            }
            throw h.domError("NoModificationAllowedError", "The element has no parent.");
        }
        for (Node n : nodes) {
            insertNode(h, par, n, ref);
        }
    }

    private static void installElement(DomHost h, JsObject el) {
        Realm r = h.r;
        h.getter(el, "tagName", n -> nodeName(n));
        h.getter(el, "localName", n -> n.tag);
        h.getter(el, "prefix", n -> Js.NULL);
        h.getter(el, "namespaceURI", n -> "http://www.w3.org/1999/xhtml");
        h.attrStr(el, "id", "id");
        h.attrStr(el, "className", "class");
        h.attrStr(el, "slot", "slot");
        h.getter(el, "classList", n -> ((DomObj) h.wrap(n)).cached("classList", () -> new DomLists.DomTokens(h, n, "class")));
        h.getter(el, "attributes", n -> ((DomObj) h.wrap(n)).cached("attributes", () -> new DomLists.DomAttrs(h, n)));
        h.getter(el, "previousElementSibling", n -> h.w(elementSibling(n, false)));
        h.getter(el, "nextElementSibling", n -> h.w(elementSibling(n, true)));
        h.method(el, "getAttribute", 1, (n, a) -> {
            String v = n.attr(DomHost.str(a, 0).toLowerCase(Locale.ROOT));
            return v == null ? Js.NULL : v;
        });
        h.method(el, "getAttributeNS", 2, (n, a) -> {
            String v = n.attr(DomHost.str(a, 1).toLowerCase(Locale.ROOT));
            return v == null ? Js.NULL : v;
        });
        h.method(el, "setAttribute", 2, (n, a) -> {
            String name = attrName(h, DomHost.str(a, 0), "setAttribute");
            n.setAttr(name, DomHost.str(a, 1));
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(el, "setAttributeNS", 3, (n, a) -> {
            String q = DomHost.str(a, 1);
            String name = attrName(h, q, "setAttributeNS");
            n.setAttr(name, DomHost.str(a, 2));
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(el, "removeAttribute", 1, (n, a) -> {
            n.removeAttr(DomHost.str(a, 0).toLowerCase(Locale.ROOT));
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(el, "removeAttributeNS", 2, (n, a) -> {
            n.removeAttr(DomHost.str(a, 1).toLowerCase(Locale.ROOT));
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(el, "hasAttribute", 1, (n, a) -> n.hasAttr(DomHost.str(a, 0).toLowerCase(Locale.ROOT)));
        h.method(el, "hasAttributeNS", 2, (n, a) -> n.hasAttr(DomHost.str(a, 1).toLowerCase(Locale.ROOT)));
        h.method(el, "hasAttributes", 0, (n, a) -> !n.attrs.isEmpty());
        h.method(el, "getAttributeNames", 0, (n, a) -> Lib.array(r, new ArrayList<Object>(n.attrs.keySet())));
        h.method(el, "toggleAttribute", 1, (n, a) -> {
            String name = attrName(h, DomHost.str(a, 0), "toggleAttribute");
            boolean has = n.hasAttr(name);
            boolean want = Js.arg(a, 1) == Js.UNDEFINED ? !has : Js.truthy(Js.arg(a, 1));
            if (want && !has) {
                n.setAttr(name, "");
            } else if (!want && has) {
                n.removeAttr(name);
            }
            Node.touch();
            return want;
        });
        h.method(el, "getAttributeNode", 1, (n, a) -> {
            String name = DomHost.str(a, 0).toLowerCase(Locale.ROOT);
            return n.hasAttr(name) ? DomLists.attrObject(h, n, name) : Js.NULL;
        });
        h.accessor(el, "innerHTML", n -> DomHtml.inner(n), (n, v) -> setInnerHtml(h, n, v == Js.NULL ? "" : Js.toStr(v)));
        h.accessor(el, "outerHTML", n -> DomHtml.outer(n), (n, v) -> {
            Node par = n.parent;
            if (par == null) {
                throw h.domError("NoModificationAllowedError", "Failed to set the 'outerHTML' property on 'Element': This element has no parent node.");
            }
            Node frag = HtmlParser.parseFragment(Js.toStr(v));
            markScriptsDone(h, frag);
            int i = par.indexOfChild(n);
            detach(h, n);
            List<Node> kids = new ArrayList<>(frag.children);
            for (Node c : kids) {
                frag.removeChild(c);
                par.insert(i++, c);
            }
            Node.touch();
        });
        h.method(el, "insertAdjacentHTML", 2, (n, a) -> {
            Node frag = HtmlParser.parseFragment(DomHost.str(a, 1));
            markScriptsDone(h, frag);
            insertAdjacent(h, n, DomHost.str(a, 0), new ArrayList<>(Arrays.asList(frag)));
            return Js.UNDEFINED;
        });
        h.method(el, "insertAdjacentText", 2, (n, a) -> {
            insertAdjacent(h, n, DomHost.str(a, 0), new ArrayList<>(Arrays.asList(Node.text(DomHost.str(a, 1)))));
            return Js.UNDEFINED;
        });
        h.method(el, "insertAdjacentElement", 2, (n, a) -> {
            Node e = DomHost.nodeArg(a, 1, "insertAdjacentElement");
            insertAdjacent(h, n, DomHost.str(a, 0), new ArrayList<>(Arrays.asList(e)));
            return h.wrap(e);
        });
        NativeFunction.Fn matchesFn = (t, a) -> {
            Node n = DomHost.nodeOf(t);
            return DomSelector.matches(h, n, sel(h, DomHost.str(a, 0)), n);
        };
        for (String m : new String[] {"matches", "webkitMatchesSelector", "msMatchesSelector", "mozMatchesSelector"}) {
            Lib.method(r, el, m, 1, matchesFn);
        }
        h.method(el, "closest", 1, (n, a) -> h.w(DomSelector.closest(h, n, sel(h, DomHost.str(a, 0)))));
        h.method(el, "getElementsByTagName", 1, (n, a) -> {
            String name = DomHost.str(a, 0).toLowerCase(Locale.ROOT);
            boolean all = name.equals("*");
            return DomLists.list(h, "HTMLCollection", true, () -> {
                List<Node> out = new ArrayList<>();
                collect(n, out, x -> x.isElement() && (all || x.tag.equals(name)));
                return out;
            });
        });
        h.method(el, "getElementsByTagNameNS", 2, (n, a) -> {
            String name = DomHost.str(a, 1).toLowerCase(Locale.ROOT);
            boolean all = name.equals("*");
            return DomLists.list(h, "HTMLCollection", true, () -> {
                List<Node> out = new ArrayList<>();
                collect(n, out, x -> x.isElement() && (all || x.tag.equals(name)));
                return out;
            });
        });
        h.method(el, "getElementsByClassName", 1, (n, a) -> classCollection(h, n, DomHost.str(a, 0)));
        h.method(el, "attachShadow", 1, (n, a) -> {
            throw h.domError("NotSupportedError", "Failed to execute 'attachShadow' on 'Element': Shadow DOM is not supported.");
        });
        h.getter(el, "shadowRoot", n -> Js.NULL);
        for (String m : new String[] {"setPointerCapture", "releasePointerCapture", "requestFullscreen"}) {
            h.method(el, m, 0, (n, a) -> Js.UNDEFINED);
        }
        h.method(el, "hasPointerCapture", 1, (n, a) -> false);
        h.method(el, "checkVisibility", 0, (n, a) -> true);
        h.method(el, "getAnimations", 0, (n, a) -> Lib.array(r, new ArrayList<Object>()));
    }

    static JsObject rect(DomHost h, double x, double y, double w, double hh) {
        JsObject o = Js.newObject();
        o.setData("x", Js.num(x));
        o.setData("y", Js.num(y));
        o.setData("width", Js.num(w));
        o.setData("height", Js.num(hh));
        o.setData("top", Js.num(y));
        o.setData("right", Js.num(x + w));
        o.setData("bottom", Js.num(y + hh));
        o.setData("left", Js.num(x));
        o.setHidden("toJSON", new NativeFunction(h.r, "toJSON", 0, (t, a) -> rect(h, x, y, w, hh)));
        return o;
    }

    static Object classCollection(DomHost h, Node root, String names) {
        String[] want = names.trim().isEmpty() ? new String[0] : names.trim().split("\\s+");
        return DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            if (want.length == 0) {
                return out;
            }
            collect(root, out, x -> {
                if (!x.isElement()) {
                    return false;
                }
                List<String> have = Arrays.asList(x.classes());
                for (String w : want) {
                    if (!have.contains(w)) {
                        return false;
                    }
                }
                return true;
            });
            return out;
        });
    }

    static String innerTextOf(Node n) {
        return innerText(n);
    }
}
