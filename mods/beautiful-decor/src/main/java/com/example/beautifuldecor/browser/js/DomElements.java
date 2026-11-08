package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** HTMLElement y las clases de cada etiqueta (menos los controles de formulario, que están en {@link DomForms}). */
final class DomElements {
    private DomElements() {
    }

    private static JsPromise resolved(DomHost h, Object v) {
        JsPromise p = new JsPromise(h.r);
        p.resolve(v);
        return p;
    }

    static boolean focusable(Node n) {
        if (!n.isElement()) {
            return false;
        }
        if (n.hasAttr("tabindex")) {
            return true;
        }
        switch (n.tag) {
            case "a", "area":
                return n.hasAttr("href");
            case "button", "select", "textarea":
                return !DomSelector.isDisabled(n);
            case "input":
                return !DomSelector.isDisabled(n) && !"hidden".equals(DomSelector.inputType(n));
            case "summary":
                return true;
            default:
                String ce = n.attr("contenteditable");
                return ce != null && !ce.equalsIgnoreCase("false");
        }
    }

    /** Define una interfaz para varias etiquetas. */
    private static JsObject tags(DomHost h, String iface, String... tags) {
        JsObject p = h.iface(iface, h.htmlElementProto);
        for (String t : tags) {
            h.tagProtos.put(t, p);
        }
        return p;
    }

    static Node templateContent(DomHost h, Node t) {
        DomObj w = (DomObj) h.wrap(t);
        return w.cached("content", () -> {
            Node frag = Node.fragment();
            for (Node c : new ArrayList<>(t.children)) {
                t.removeChild(c);
                frag.add(c);
            }
            DomNodes.markScriptsDone(h, frag);
            return frag;
        });
    }

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject he = h.htmlElementProto;
        h.getter(he, "style", n -> ((DomObj) h.wrap(n)).cached("style", () -> DomStyle.declaration(h, n)));
        h.getter(he, "dataset", n -> ((DomObj) h.wrap(n)).cached("dataset", () -> new DomLists.DomDataset(h, n)));
        h.attrBool(he, "hidden", "hidden");
        h.attrStr(he, "title", "title");
        h.attrStr(he, "lang", "lang");
        h.attrStr(he, "dir", "dir");
        h.attrStr(he, "accessKey", "accesskey");
        h.attrStr(he, "nonce", "nonce");
        h.attrStr(he, "inputMode", "inputmode");
        h.attrStr(he, "enterKeyHint", "enterkeyhint");
        h.attrBool(he, "autofocus", "autofocus");
        h.attrBool(he, "inert", "inert");
        h.accessor(he, "draggable", n -> "true".equals(n.attr("draggable")), (n, v) -> {
            n.setAttr("draggable", Js.truthy(v) ? "true" : "false");
            Node.touch();
        });
        h.accessor(he, "spellcheck", n -> !"false".equals(n.attr("spellcheck")), (n, v) -> {
            n.setAttr("spellcheck", Js.truthy(v) ? "true" : "false");
            Node.touch();
        });
        h.accessor(he, "translate", n -> !"no".equals(n.attr("translate")), (n, v) -> {
            n.setAttr("translate", Js.truthy(v) ? "yes" : "no");
            Node.touch();
        });
        h.accessor(he, "tabIndex", n -> {
            String v = n.attr("tabindex");
            if (v != null) {
                try {
                    return Js.num(Integer.parseInt(v.trim()));
                } catch (NumberFormatException e) {
                    return Js.num(-1);
                }
            }
            return Js.num(focusable(n) ? 0 : -1);
        }, (n, v) -> {
            n.setAttr("tabindex", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.accessor(he, "contentEditable", n -> {
            String v = n.attr("contenteditable");
            return v == null ? "inherit" : v.isEmpty() ? "true" : v;
        }, (n, v) -> {
            n.setAttr("contenteditable", Js.toStr(v));
            Node.touch();
        });
        h.getter(he, "isContentEditable", n -> {
            for (Node p = n; p != null; p = p.parent) {
                String v = p.attr("contenteditable");
                if (v != null) {
                    return !v.equalsIgnoreCase("false");
                }
            }
            return false;
        });
        for (String p : new String[] {"innerText", "outerText"}) {
            h.accessor(he, p, n -> DomNodes.innerTextOf(n), (n, v) -> {
                DomNodes.clearChildren(h, n);
                String[] lines = Js.toStr(v).split("\r?\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        n.add(Node.element("br", null));
                    }
                    if (!lines[i].isEmpty()) {
                        n.add(Node.text(lines[i]));
                    }
                }
                Node.touch();
            });
        }
        h.method(he, "attachInternals", 0, (n, a) -> Js.newObject());
        h.method(he, "animate", 2, (n, a) -> {
            JsObject anim = Js.newObject();
            anim.setData("finished", resolved(h, anim));
            for (String m : new String[] {"cancel", "finish", "pause", "play", "reverse"}) {
                Lib.method(r, anim, m, 0, (t, b) -> Js.UNDEFINED);
            }
            return anim;
        });

        // etiquetas sencillas
        tags(h, "HTMLDivElement", "div");
        tags(h, "HTMLSpanElement", "span");
        tags(h, "HTMLParagraphElement", "p");
        tags(h, "HTMLHeadingElement", "h1", "h2", "h3", "h4", "h5", "h6");
        tags(h, "HTMLBRElement", "br");
        tags(h, "HTMLHRElement", "hr");
        tags(h, "HTMLHeadElement", "head");
        tags(h, "HTMLHtmlElement", "html");
        tags(h, "HTMLBodyElement", "body");
        tags(h, "HTMLTitleElement", "title");
        tags(h, "HTMLUListElement", "ul");
        tags(h, "HTMLDListElement", "dl");
        tags(h, "HTMLMenuElement", "menu");
        tags(h, "HTMLLegendElement", "legend");
        tags(h, "HTMLPictureElement", "picture");
        tags(h, "HTMLSlotElement", "slot");
        tags(h, "HTMLParamElement", "param");
        tags(h, "HTMLTrackElement", "track");
        tags(h, "HTMLDataListElement", "datalist");
        tags(h, "HTMLTimeElement", "time");
        tags(h, "HTMLDataElement", "data");
        tags(h, "HTMLMapElement", "map");
        tags(h, "HTMLAreaElement", "area");
        JsObject title = h.tagProtos.get("title");
        h.accessor(title, "text", n -> n.textContent(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));

        JsObject pre = tags(h, "HTMLPreElement", "pre", "listing", "xmp");
        h.attrInt(pre, "width", "width", 0);
        JsObject quote = tags(h, "HTMLQuoteElement", "blockquote", "q");
        h.attrUrl(quote, "cite", "cite");
        JsObject mod = tags(h, "HTMLModElement", "ins", "del");
        h.attrUrl(mod, "cite", "cite");
        h.attrStr(mod, "dateTime", "datetime");

        JsObject ol = tags(h, "HTMLOListElement", "ol");
        h.attrInt(ol, "start", "start", 1);
        h.attrBool(ol, "reversed", "reversed");
        h.attrStr(ol, "type", "type");
        JsObject li = tags(h, "HTMLLIElement", "li");
        h.attrInt(li, "value", "value", 0);

        // enlaces
        JsObject a = tags(h, "HTMLAnchorElement", "a");
        h.attrUrl(a, "href", "href");
        h.attrStr(a, "target", "target");
        h.attrStr(a, "rel", "rel");
        h.attrStr(a, "download", "download");
        h.attrStr(a, "hreflang", "hreflang");
        h.attrStr(a, "type", "type");
        h.attrStr(a, "ping", "ping");
        h.attrStr(a, "referrerPolicy", "referrerpolicy");
        h.accessor(a, "text", n -> n.textContent(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
        h.getter(a, "relList", n -> ((DomObj) h.wrap(n)).cached("relList", () -> new DomLists.DomTokens(h, n, "rel")));
        for (String part : new String[] {"protocol", "host", "hostname", "port", "pathname", "search", "hash", "origin", "username", "password"}) {
            h.accessor(a, part, n -> {
                String href = n.attr("href");
                if (href == null) {
                    return "";
                }
                return DomUrl.parse(h.resolveUrl(href)).get(part);
            }, (n, v) -> {
                String href = n.attr("href");
                if (href == null) {
                    return;
                }
                DomUrl.Parts parts = DomUrl.parse(h.resolveUrl(href));
                parts.set(part, Js.toStr(v));
                n.setAttr("href", parts.href());
                Node.touch();
            });
        }
        Lib.method(r, a, "toString", 0, (t, b) -> {
            String href = DomHost.nodeOf(t).attr("href");
            return href == null ? "" : h.resolveUrl(href);
        });
        JsObject area = h.tagProtos.get("area");
        h.attrUrl(area, "href", "href");
        h.attrStr(area, "target", "target");
        h.attrStr(area, "alt", "alt");

        // imágenes
        JsObject img = tags(h, "HTMLImageElement", "img");
        h.attrUrl(img, "src", "src");
        h.attrStr(img, "alt", "alt");
        h.attrStr(img, "srcset", "srcset");
        h.attrStr(img, "sizes", "sizes");
        h.attrStr(img, "loading", "loading");
        h.attrStr(img, "decoding", "decoding");
        h.attrStr(img, "crossOrigin", "crossorigin");
        h.attrStr(img, "useMap", "usemap");
        h.attrBool(img, "isMap", "ismap");
        h.accessor(img, "width", n -> Js.num(attrNum(n, "width")), (n, v) -> {
            n.setAttr("width", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.accessor(img, "height", n -> Js.num(attrNum(n, "height")), (n, v) -> {
            n.setAttr("height", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.getter(img, "naturalWidth", n -> Js.num(0));
        h.getter(img, "naturalHeight", n -> Js.num(0));
        h.getter(img, "complete", n -> true);
        h.getter(img, "currentSrc", n -> {
            String s = n.attr("src");
            return s == null ? "" : h.resolveUrl(s);
        });
        h.getter(img, "x", n -> Js.num(0));
        h.getter(img, "y", n -> Js.num(0));
        h.method(img, "decode", 0, (n, b) -> resolved(h, Js.UNDEFINED));
        NativeFunction imageCtor = Lib.ctor(r, "Image", 0, img, (t, b) -> {
            throw Js.typeError("Class constructor Image cannot be invoked without 'new'");
        }, (b, nt) -> {
            Node n = Node.element("img", null);
            if (b.length > 0) {
                n.setAttr("width", Js.numToString(Js.toInteger(b[0])));
            }
            if (b.length > 1) {
                n.setAttr("height", Js.numToString(Js.toInteger(b[1])));
            }
            DomObj d = new DomObj(Lib.protoFrom(nt, img), n);
            n.host = d;
            return d;
        });
        r.global.setHidden("HTMLImageElement", imageCtor);

        // scripts y hojas de estilo
        JsObject script = tags(h, "HTMLScriptElement", "script");
        h.attrUrl(script, "src", "src");
        h.attrStr(script, "type", "type");
        h.attrStr(script, "charset", "charset");
        h.attrStr(script, "crossOrigin", "crossorigin");
        h.attrBool(script, "async", "async");
        h.attrBool(script, "defer", "defer");
        h.attrBool(script, "noModule", "nomodule");
        h.accessor(script, "text", n -> n.rawText(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
        h.method(script, "supports", 1, (n, b) -> false);
        JsObject style = tags(h, "HTMLStyleElement", "style");
        h.attrStr(style, "media", "media");
        h.attrStr(style, "type", "type");
        h.attrBool(style, "disabled", "disabled");
        h.getter(style, "sheet", n -> Js.NULL);
        JsObject link = tags(h, "HTMLLinkElement", "link");
        h.attrUrl(link, "href", "href");
        h.attrStr(link, "rel", "rel");
        h.attrStr(link, "type", "type");
        h.attrStr(link, "media", "media");
        h.attrStr(link, "as", "as");
        h.attrStr(link, "crossOrigin", "crossorigin");
        h.attrStr(link, "hreflang", "hreflang");
        h.attrBool(link, "disabled", "disabled");
        h.getter(link, "sheet", n -> Js.NULL);
        JsObject meta = tags(h, "HTMLMetaElement", "meta");
        h.attrStr(meta, "name", "name");
        h.attrStr(meta, "content", "content");
        h.attrStr(meta, "httpEquiv", "http-equiv");
        h.attrStr(meta, "charset", "charset");
        JsObject base = tags(h, "HTMLBaseElement", "base");
        h.attrUrl(base, "href", "href");
        h.attrStr(base, "target", "target");

        // tablas
        JsObject table = tags(h, "HTMLTableElement", "table");
        h.getter(table, "rows", n -> ((DomObj) h.wrap(n)).cached("rows", () -> DomLists.list(h, "HTMLCollection", true, () -> rowsOf(n))));
        h.getter(table, "tBodies", n -> ((DomObj) h.wrap(n)).cached("tBodies", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("tbody")) {
                    out.add(c);
                }
            }
            return out;
        })));
        h.getter(table, "tHead", n -> h.w(DomDocument.child(n, "thead")));
        h.getter(table, "tFoot", n -> h.w(DomDocument.child(n, "tfoot")));
        h.getter(table, "caption", n -> h.w(DomDocument.child(n, "caption")));
        h.attrStr(table, "border", "border");
        h.attrStr(table, "cellPadding", "cellpadding");
        h.attrStr(table, "cellSpacing", "cellspacing");
        h.attrStr(table, "width", "width");
        h.method(table, "insertRow", 0, (n, b) -> {
            Node row = Node.element("tr", null);
            List<Node> rows = rowsOf(n);
            int idx = b.length == 0 || b[0] == Js.UNDEFINED ? -1 : (int) Js.toInteger(b[0]);
            if (idx < -1 || idx > rows.size()) {
                throw h.domError("IndexSizeError", "The provided index (" + idx + ") is greater than the number of rows in the table (" + rows.size() + ").");
            }
            if (idx == -1 || idx == rows.size()) {
                Node section = rows.isEmpty() ? null : rows.get(rows.size() - 1).parent;
                if (section == null) {
                    section = DomDocument.child(n, "tbody");
                    if (section == null) {
                        section = Node.element("tbody", null);
                        DomNodes.insertNode(h, n, section, null);
                    }
                }
                DomNodes.insertNode(h, section, row, null);
            } else {
                Node ref = rows.get(idx);
                DomNodes.insertNode(h, ref.parent, row, ref);
            }
            return h.wrap(row);
        });
        h.method(table, "deleteRow", 1, (n, b) -> {
            List<Node> rows = rowsOf(n);
            int idx = (int) Js.toInteger(Js.arg(b, 0));
            if (idx == -1) {
                idx = rows.size() - 1;
            }
            if (idx < 0 || idx >= rows.size()) {
                if (rows.isEmpty() && idx == -1) {
                    return Js.UNDEFINED;
                }
                throw h.domError("IndexSizeError", "The provided index (" + idx + ") is outside the range of rows.");
            }
            DomNodes.detach(h, rows.get(idx));
            return Js.UNDEFINED;
        });
        h.method(table, "createTBody", 0, (n, b) -> {
            Node tb = Node.element("tbody", null);
            DomNodes.insertNode(h, n, tb, null);
            return h.wrap(tb);
        });
        h.method(table, "createCaption", 0, (n, b) -> {
            Node c = DomDocument.child(n, "caption");
            if (c == null) {
                c = Node.element("caption", null);
                DomNodes.insertNode(h, n, c, n.children.isEmpty() ? null : n.children.get(0));
            }
            return h.wrap(c);
        });
        JsObject section = tags(h, "HTMLTableSectionElement", "thead", "tbody", "tfoot");
        h.getter(section, "rows", n -> ((DomObj) h.wrap(n)).cached("rows", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("tr")) {
                    out.add(c);
                }
            }
            return out;
        })));
        h.method(section, "insertRow", 0, (n, b) -> {
            Node row = Node.element("tr", null);
            int count = 0;
            for (Node c : n.children) {
                if (c.is("tr")) {
                    count++;
                }
            }
            int idx = b.length == 0 || b[0] == Js.UNDEFINED ? -1 : (int) Js.toInteger(b[0]);
            Node ref = null;
            if (idx >= 0 && idx < count) {
                int k = 0;
                for (Node c : n.children) {
                    if (c.is("tr") && k++ == idx) {
                        ref = c;
                        break;
                    }
                }
            }
            DomNodes.insertNode(h, n, row, ref);
            return h.wrap(row);
        });
        h.method(section, "deleteRow", 1, (n, b) -> {
            int idx = (int) Js.toInteger(Js.arg(b, 0));
            List<Node> trs = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("tr")) {
                    trs.add(c);
                }
            }
            if (idx == -1) {
                idx = trs.size() - 1;
            }
            if (idx >= 0 && idx < trs.size()) {
                DomNodes.detach(h, trs.get(idx));
            }
            return Js.UNDEFINED;
        });
        JsObject tr = tags(h, "HTMLTableRowElement", "tr");
        h.getter(tr, "cells", n -> ((DomObj) h.wrap(n)).cached("cells", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("td") || c.is("th")) {
                    out.add(c);
                }
            }
            return out;
        })));
        h.getter(tr, "rowIndex", n -> {
            Node t = n.parent;
            while (t != null && !t.is("table")) {
                t = t.parent;
            }
            return Js.num(t == null ? -1 : rowsOf(t).indexOf(n));
        });
        h.getter(tr, "sectionRowIndex", n -> {
            int k = 0;
            if (n.parent != null) {
                for (Node c : n.parent.children) {
                    if (c == n) {
                        return Js.num(k);
                    }
                    if (c.is("tr")) {
                        k++;
                    }
                }
            }
            return Js.num(-1);
        });
        h.method(tr, "insertCell", 0, (n, b) -> {
            Node cell = Node.element("td", null);
            List<Node> cells = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("td") || c.is("th")) {
                    cells.add(c);
                }
            }
            int idx = b.length == 0 || b[0] == Js.UNDEFINED ? -1 : (int) Js.toInteger(b[0]);
            Node ref = idx >= 0 && idx < cells.size() ? cells.get(idx) : null;
            DomNodes.insertNode(h, n, cell, ref);
            return h.wrap(cell);
        });
        h.method(tr, "deleteCell", 1, (n, b) -> {
            List<Node> cells = new ArrayList<>();
            for (Node c : n.children) {
                if (c.is("td") || c.is("th")) {
                    cells.add(c);
                }
            }
            int idx = (int) Js.toInteger(Js.arg(b, 0));
            if (idx == -1) {
                idx = cells.size() - 1;
            }
            if (idx >= 0 && idx < cells.size()) {
                DomNodes.detach(h, cells.get(idx));
            }
            return Js.UNDEFINED;
        });
        JsObject cell = tags(h, "HTMLTableCellElement", "td", "th");
        h.accessor(cell, "colSpan", n -> Js.num(Math.max(1, (int) attrNum(n, "colspan"))), (n, v) -> {
            n.setAttr("colspan", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.accessor(cell, "rowSpan", n -> Js.num(Math.max(1, (int) attrNum(n, "rowspan"))), (n, v) -> {
            n.setAttr("rowspan", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.attrStr(cell, "headers", "headers");
        h.attrStr(cell, "abbr", "abbr");
        h.attrStr(cell, "scope", "scope");
        h.getter(cell, "cellIndex", n -> {
            int k = 0;
            if (n.parent != null) {
                for (Node c : n.parent.children) {
                    if (c == n) {
                        return Js.num(k);
                    }
                    if (c.is("td") || c.is("th")) {
                        k++;
                    }
                }
            }
            return Js.num(-1);
        });
        tags(h, "HTMLTableCaptionElement", "caption");
        tags(h, "HTMLTableColElement", "col", "colgroup");

        // otros
        JsObject iframe = tags(h, "HTMLIFrameElement", "iframe");
        h.attrUrl(iframe, "src", "src");
        h.attrStr(iframe, "srcdoc", "srcdoc");
        h.attrStr(iframe, "name", "name");
        h.attrStr(iframe, "width", "width");
        h.attrStr(iframe, "height", "height");
        h.attrStr(iframe, "allow", "allow");
        h.attrStr(iframe, "loading", "loading");
        h.getter(iframe, "contentWindow", n -> Js.NULL);
        h.getter(iframe, "contentDocument", n -> Js.NULL);
        JsObject canvas = tags(h, "HTMLCanvasElement", "canvas");
        h.accessor(canvas, "width", n -> Js.num(n.hasAttr("width") ? attrNum(n, "width") : 300), (n, v) -> {
            n.setAttr("width", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.accessor(canvas, "height", n -> Js.num(n.hasAttr("height") ? attrNum(n, "height") : 150), (n, v) -> {
            n.setAttr("height", Js.numToString(Js.toInteger(v)));
            Node.touch();
        });
        h.method(canvas, "getContext", 1, (n, b) -> Js.NULL);
        h.method(canvas, "toDataURL", 0, (n, b) -> "data:,");
        h.method(canvas, "toBlob", 1, (n, b) -> Js.UNDEFINED);
        JsObject media = tags(h, "HTMLMediaElement", "audio", "video");
        h.tagProtos.put("audio", h.iface("HTMLAudioElement", media));
        h.tagProtos.put("video", h.iface("HTMLVideoElement", media));
        h.attrUrl(media, "src", "src");
        h.attrBool(media, "controls", "controls");
        h.attrBool(media, "autoplay", "autoplay");
        h.attrBool(media, "loop", "loop");
        h.attrBool(media, "muted", "muted");
        h.attrStr(media, "preload", "preload");
        h.getter(media, "currentSrc", n -> {
            String s = n.attr("src");
            return s == null ? "" : h.resolveUrl(s);
        });
        h.getter(media, "paused", n -> true);
        h.getter(media, "ended", n -> false);
        h.getter(media, "readyState", n -> Js.num(0));
        h.getter(media, "duration", n -> Js.NAN);
        h.accessor(media, "currentTime", n -> Js.num(0), (n, v) -> { });
        h.accessor(media, "volume", n -> Js.num(1), (n, v) -> { });
        h.accessor(media, "playbackRate", n -> Js.num(1), (n, v) -> { });
        h.method(media, "play", 0, (n, b) -> resolved(h, Js.UNDEFINED));
        h.method(media, "pause", 0, (n, b) -> Js.UNDEFINED);
        h.method(media, "load", 0, (n, b) -> Js.UNDEFINED);
        h.method(media, "canPlayType", 1, (n, b) -> "");
        JsObject source = tags(h, "HTMLSourceElement", "source");
        h.attrUrl(source, "src", "src");
        h.attrStr(source, "type", "type");
        h.attrStr(source, "media", "media");
        h.attrStr(source, "srcset", "srcset");
        JsObject embed = tags(h, "HTMLEmbedElement", "embed");
        h.attrUrl(embed, "src", "src");
        h.attrStr(embed, "type", "type");
        JsObject object = tags(h, "HTMLObjectElement", "object");
        h.attrUrl(object, "data", "data");
        h.attrStr(object, "type", "type");

        JsObject tpl = tags(h, "HTMLTemplateElement", "template");
        h.getter(tpl, "content", n -> h.wrap(templateContent(h, n)));
        h.accessor(tpl, "innerHTML", n -> DomHtml.inner(templateContent(h, n)), (n, v) -> DomNodes.setInnerHtml(h, templateContent(h, n), Js.toStr(v)));
        JsObject details = tags(h, "HTMLDetailsElement", "details");
        h.attrBool(details, "open", "open");
        JsObject dialog = tags(h, "HTMLDialogElement", "dialog");
        h.attrBool(dialog, "open", "open");
        h.accessor(dialog, "returnValue", n -> {
            String v = n.attr("data-return-value");
            return v == null ? "" : v;
        }, (n, v) -> n.setAttr("data-return-value", Js.toStr(v)));
        h.method(dialog, "show", 0, (n, b) -> {
            n.setAttr("open", "");
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(dialog, "showModal", 0, (n, b) -> {
            n.setAttr("open", "");
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(dialog, "close", 0, (n, b) -> {
            if (n.hasAttr("open")) {
                n.removeAttr("open");
                Node.touch();
                DomEvents.fireSimple(h, n, "close", false, false);
            }
            return Js.UNDEFINED;
        });
        JsObject progress = tags(h, "HTMLProgressElement", "progress");
        h.accessor(progress, "value", n -> Js.num(attrNum(n, "value")), (n, v) -> {
            n.setAttr("value", Js.numToString(Js.toNumber(v)));
            Node.touch();
        });
        h.accessor(progress, "max", n -> Js.num(n.hasAttr("max") ? attrNum(n, "max") : 1), (n, v) -> {
            n.setAttr("max", Js.numToString(Js.toNumber(v)));
            Node.touch();
        });
        JsObject meter = tags(h, "HTMLMeterElement", "meter");
        h.accessor(meter, "value", n -> Js.num(attrNum(n, "value")), (n, v) -> {
            n.setAttr("value", Js.numToString(Js.toNumber(v)));
            Node.touch();
        });
        h.accessor(meter, "min", n -> Js.num(attrNum(n, "min")), (n, v) -> {
            n.setAttr("min", Js.numToString(Js.toNumber(v)));
            Node.touch();
        });
        h.accessor(meter, "max", n -> Js.num(n.hasAttr("max") ? attrNum(n, "max") : 1), (n, v) -> {
            n.setAttr("max", Js.numToString(Js.toNumber(v)));
            Node.touch();
        });
    }

    static double attrNum(Node n, String name) {
        String v = n.attr(name);
        if (v == null) {
            return 0;
        }
        try {
            return Double.parseDouble(v.trim().toLowerCase(Locale.ROOT).replace("px", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Las filas de una tabla en orden: thead, tbody..., tfoot. */
    static List<Node> rowsOf(Node table) {
        List<Node> head = new ArrayList<>();
        List<Node> body = new ArrayList<>();
        List<Node> foot = new ArrayList<>();
        for (Node c : table.children) {
            if (c.is("tr")) {
                body.add(c);
            } else if (c.is("thead") || c.is("tbody") || c.is("tfoot")) {
                List<Node> dest = c.is("thead") ? head : c.is("tfoot") ? foot : body;
                for (Node r : c.children) {
                    if (r.is("tr")) {
                        dest.add(r);
                    }
                }
            }
        }
        head.addAll(body);
        head.addAll(foot);
        return head;
    }
}
