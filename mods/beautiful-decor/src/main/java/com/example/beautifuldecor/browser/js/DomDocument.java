package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.HtmlParser;
import com.example.beautifuldecor.browser.Node;
import com.example.beautifuldecor.browser.Url;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** El objeto document. */
final class DomDocument {
    private DomDocument() {
    }

    static Node htmlOf(DomHost h) {
        for (Node c : h.doc.children) {
            if (c.is("html")) {
                return c;
            }
        }
        return null;
    }

    static Node child(Node html, String tag) {
        if (html != null) {
            for (Node c : html.children) {
                if (c.is(tag)) {
                    return c;
                }
            }
        }
        return null;
    }

    static Node bodyOf(DomHost h) {
        return child(htmlOf(h), "body");
    }

    static Node headOf(DomHost h) {
        return child(htmlOf(h), "head");
    }

    private static String eventClass(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "event", "events", "htmlevents", "svgevents":
                return "Event";
            case "uievent", "uievents":
                return "UIEvent";
            case "mouseevent", "mouseevents":
                return "MouseEvent";
            case "keyboardevent":
                return "KeyboardEvent";
            case "customevent":
                return "CustomEvent";
            case "focusevent":
                return "FocusEvent";
            case "messageevent":
                return "MessageEvent";
            case "hashchangeevent":
                return "HashChangeEvent";
            case "inputevent":
                return "InputEvent";
            default:
                return null;
        }
    }

    private static String elementName(DomHost h, String name, String method) {
        boolean ok = !name.isEmpty() && (Character.isLetter(name.charAt(0)) || name.charAt(0) == '_' || name.charAt(0) == ':');
        for (int i = 0; ok && i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '<' || c == '"' || c == '\'' || c == '=') {
                ok = false;
            }
        }
        if (!ok) {
            throw h.domError("InvalidCharacterError", "Failed to execute '" + method + "' on 'Document': The tag name provided ('" + name + "') is not a valid name.");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /** document.write: el texto se mete justo después del script que está corriendo. */
    static void write(DomHost h, String html) {
        Node frag = HtmlParser.parseFragment(html);
        List<Node> kids = new ArrayList<>(frag.children);
        Node anchor = h.currentScript;
        if (anchor != null && anchor.parent != null) {
            if (h.writeFor != anchor || h.writeLast == null || h.writeLast.parent == null) {
                h.writeFor = anchor;
                h.writeLast = anchor;
            }
            Node par = h.writeLast.parent;
            for (Node c : kids) {
                frag.removeChild(c);
                int i = par.indexOfChild(h.writeLast);
                Node next = i + 1 < par.children.size() ? par.children.get(i + 1) : null;
                DomNodes.insertNode(h, par, c, next);
                h.writeLast = c;
            }
            return;
        }
        Node body = bodyOf(h);
        if (body != null) {
            for (Node c : kids) {
                frag.removeChild(c);
                DomNodes.insertNode(h, body, c, null);
            }
        }
    }

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject doc = h.iface("Document", h.nodeProto);
        h.documentProto = doc;
        DomNodes.installParentNode(h, doc);
        Lib.ctor(r, "Document", 0, doc, (t, a) -> {
            throw Js.typeError("Class constructor Document cannot be invoked without 'new'");
        }, (a, nt) -> {
            Node n = Node.element("#document", null);
            DomObj d = new DomObj(Lib.protoFrom(nt, doc), n);
            n.host = d;
            return d;
        });
        JsObject document = h.wrap(h.doc);
        r.global.setHidden("document", document);

        h.getter(doc, "documentElement", n -> h.w(htmlOf(h)));
        h.getter(doc, "head", n -> h.w(headOf(h)));
        h.accessor(doc, "body", n -> h.w(bodyOf(h)), (n, v) -> {
            Node nb = DomHost.asNode(v);
            Node html = htmlOf(h);
            if (nb == null || !(nb.is("body") || nb.is("frameset"))) {
                throw h.domError("HierarchyRequestError", "The new body element is of type '" + (nb == null ? "null" : nb.tag) + "'. It must be either a 'BODY' or 'FRAMESET' element.");
            }
            Node old = bodyOf(h);
            if (old == nb || html == null) {
                return;
            }
            if (old == null) {
                DomNodes.insertNode(h, html, nb, null);
            } else {
                int i = html.indexOfChild(old);
                DomNodes.detach(h, old);
                DomNodes.detach(h, nb);
                html.insert(i, nb);
                Node.touch();
            }
        });
        h.accessor(doc, "title", n -> {
            List<Node> t = new ArrayList<>();
            DomNodes.collect(h.doc, t, x -> x.is("title"));
            return t.isEmpty() ? "" : t.get(0).textContent();
        }, (n, v) -> {
            String s = Js.toStr(v);
            List<Node> t = new ArrayList<>();
            DomNodes.collect(h.doc, t, x -> x.is("title"));
            Node title;
            if (t.isEmpty()) {
                Node head = headOf(h);
                if (head == null) {
                    return;
                }
                title = Node.element("title", null);
                head.add(title);
            } else {
                title = t.get(0);
            }
            DomNodes.setText(h, title, s);
            h.env.titleChanged(s);
        });
        h.accessor(doc, "cookie", n -> h.env.cookie(), (n, v) -> h.env.setCookie(Js.toStr(v)));
        h.getter(doc, "readyState", n -> h.readyState);
        h.getter(doc, "URL", n -> h.env.url());
        h.getter(doc, "documentURI", n -> h.env.url());
        h.getter(doc, "referrer", n -> h.env.referrer());
        h.getter(doc, "domain", n -> Url.hostOf(h.env.url()));
        h.getter(doc, "characterSet", n -> "UTF-8");
        h.getter(doc, "charset", n -> "UTF-8");
        h.getter(doc, "inputEncoding", n -> "UTF-8");
        h.getter(doc, "contentType", n -> "text/html");
        h.getter(doc, "compatMode", n -> "CSS1Compat");
        h.getter(doc, "doctype", n -> Js.NULL);
        h.getter(doc, "defaultView", n -> r.global);
        h.getter(doc, "currentScript", n -> h.currentScript == null ? Js.NULL : h.wrap(h.currentScript));
        h.getter(doc, "activeElement", n -> {
            if (h.focused != null) {
                return h.wrap(h.focused);
            }
            return h.w(bodyOf(h));
        });
        h.getter(doc, "visibilityState", n -> "visible");
        h.getter(doc, "hidden", n -> false);
        h.getter(doc, "fullscreenEnabled", n -> false);
        h.getter(doc, "fullscreenElement", n -> Js.NULL);
        h.getter(doc, "pointerLockElement", n -> Js.NULL);
        h.getter(doc, "scrollingElement", n -> h.w(htmlOf(h)));
        h.getter(doc, "lastModified", n -> "01/01/2000 00:00:00");
        h.getter(doc, "styleSheets", n -> Lib.array(r, new ArrayList<Object>()));
        h.accessor(doc, "dir", n -> "", (n, v) -> { });
        h.accessor(doc, "designMode", n -> "off", (n, v) -> { });
        h.accessor(doc, "location", n -> h.locationObj == null ? Js.NULL : h.locationObj, (n, v) -> {
            if (h.locationObj != null) {
                h.locationObj.put("href", v);
            }
        });
        String[][] colls = {
            {"forms", "form"}, {"images", "img"}, {"scripts", "script"}, {"embeds", "embed"}, {"applets", "applet"},
            {"plugins", "embed"}};
        for (String[] c : colls) {
            h.getter(doc, c[0], n -> ((DomObj) h.wrap(n)).cached(c[0], () -> DomLists.list(h, "HTMLCollection", true, () -> {
                List<Node> out = new ArrayList<>();
                DomNodes.collect(h.doc, out, x -> x.is(c[1]));
                return out;
            })));
        }
        h.getter(doc, "links", n -> ((DomObj) h.wrap(n)).cached("links", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            DomNodes.collect(h.doc, out, x -> (x.is("a") || x.is("area")) && x.hasAttr("href"));
            return out;
        })));
        h.getter(doc, "anchors", n -> ((DomObj) h.wrap(n)).cached("anchors", () -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            DomNodes.collect(h.doc, out, x -> x.is("a") && x.hasAttr("name"));
            return out;
        })));
        h.getter(doc, "fonts", n -> ((DomObj) h.wrap(n)).cached("fonts", () -> {
            JsObject f = Js.newObject();
            JsPromise ready = new JsPromise(r);
            ready.resolve(f);
            f.setData("ready", ready);
            f.setData("status", "loaded");
            Lib.method(r, f, "load", 1, (t, a) -> {
                JsPromise p = new JsPromise(r);
                p.resolve(Lib.array(r, new ArrayList<Object>()));
                return p;
            });
            Lib.method(r, f, "check", 1, (t, a) -> true);
            Lib.method(r, f, "addEventListener", 2, (t, a) -> Js.UNDEFINED);
            return f;
        }));

        h.method(doc, "getElementById", 1, (n, a) -> {
            String id = DomHost.str(a, 0);
            List<Node> hit = new ArrayList<>();
            Node[] first = new Node[1];
            DomNodes.collect(h.doc, null, x -> {
                if (first[0] == null && x.isElement() && id.equals(x.attr("id"))) {
                    first[0] = x;
                }
                return false;
            });
            hit.clear();
            return h.w(first[0]);
        });
        h.method(doc, "getElementsByTagName", 1, (n, a) -> {
            String name = DomHost.str(a, 0).toLowerCase(Locale.ROOT);
            boolean all = name.equals("*");
            return DomLists.list(h, "HTMLCollection", true, () -> {
                List<Node> out = new ArrayList<>();
                DomNodes.collect(h.doc, out, x -> x.isElement() && (all || x.tag.equals(name)));
                return out;
            });
        });
        h.method(doc, "getElementsByTagNameNS", 2, (n, a) -> {
            String name = DomHost.str(a, 1).toLowerCase(Locale.ROOT);
            boolean all = name.equals("*");
            return DomLists.list(h, "HTMLCollection", true, () -> {
                List<Node> out = new ArrayList<>();
                DomNodes.collect(h.doc, out, x -> x.isElement() && (all || x.tag.equals(name)));
                return out;
            });
        });
        h.method(doc, "getElementsByClassName", 1, (n, a) -> DomNodes.classCollection(h, h.doc, DomHost.str(a, 0)));
        h.method(doc, "getElementsByName", 1, (n, a) -> {
            String name = DomHost.str(a, 0);
            return DomLists.list(h, "NodeList", true, () -> {
                List<Node> out = new ArrayList<>();
                DomNodes.collect(h.doc, out, x -> x.isElement() && name.equals(x.attr("name")));
                return out;
            });
        });
        h.method(doc, "createElement", 1, (n, a) -> {
            Node e = Node.element(elementName(h, DomHost.str(a, 0), "createElement"), null);
            return h.wrap(e);
        });
        h.method(doc, "createElementNS", 2, (n, a) -> {
            Node e = Node.element(elementName(h, DomHost.str(a, 1), "createElementNS"), null);
            return h.wrap(e);
        });
        h.method(doc, "createTextNode", 1, (n, a) -> h.wrap(Node.text(DomHost.str(a, 0))));
        h.method(doc, "createComment", 1, (n, a) -> h.wrap(Node.comment(DomHost.str(a, 0))));
        h.method(doc, "createDocumentFragment", 0, (n, a) -> h.wrap(Node.fragment()));
        h.method(doc, "createEvent", 1, (n, a) -> {
            String cls = eventClass(DomHost.str(a, 0));
            if (cls == null) {
                throw h.domError("NotSupportedError", "The provided event type ('" + DomHost.str(a, 0) + "') is invalid.");
            }
            return DomEvents.make(h, cls, h.protos.get(cls), "", null, false);
        });
        h.method(doc, "importNode", 1, (n, a) -> h.wrap(DomNodes.clone(h, DomHost.nodeArg(a, 0, "importNode"), Js.truthy(Js.arg(a, 1)))));
        h.method(doc, "adoptNode", 1, (n, a) -> {
            Node x = DomHost.nodeArg(a, 0, "adoptNode");
            DomNodes.detach(h, x);
            return h.wrap(x);
        });
        h.method(doc, "createRange", 0, (n, a) -> {
            JsObject range = Js.newObject();
            Lib.method(r, range, "createContextualFragment", 1, (t, b) -> {
                Node frag = HtmlParser.parseFragment(DomHost.str(b, 0));
                return h.wrap(frag);
            });
            for (String m : new String[] {"setStart", "setEnd", "setStartBefore", "setStartAfter", "setEndBefore", "setEndAfter",
                "selectNode", "selectNodeContents", "collapse", "detach", "deleteContents"}) {
                Lib.method(r, range, m, 0, (t, b) -> Js.UNDEFINED);
            }
            Lib.method(r, range, "getBoundingClientRect", 0, (t, b) -> DomNodes.rect(h, 0, 0, 0, 0));
            range.setData("collapsed", true);
            return range;
        });
        h.method(doc, "hasFocus", 0, (n, a) -> true);
        h.method(doc, "execCommand", 1, (n, a) -> false);
        h.method(doc, "queryCommandSupported", 1, (n, a) -> false);
        h.method(doc, "queryCommandEnabled", 1, (n, a) -> false);
        h.method(doc, "queryCommandState", 1, (n, a) -> false);
        h.method(doc, "exitFullscreen", 0, (n, a) -> {
            JsPromise p = new JsPromise(r);
            p.resolve(Js.UNDEFINED);
            return p;
        });
        h.method(doc, "getSelection", 0, (n, a) -> r.global.get("getSelection") instanceof JsFunction f ? f.call(r.global, Js.EMPTY) : Js.NULL);
        h.method(doc, "write", 1, (n, a) -> {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                sb.append(Js.toStr(o));
            }
            write(h, sb.toString());
            return Js.UNDEFINED;
        });
        h.method(doc, "writeln", 1, (n, a) -> {
            StringBuilder sb = new StringBuilder();
            for (Object o : a) {
                sb.append(Js.toStr(o));
            }
            write(h, sb.append('\n').toString());
            return Js.UNDEFINED;
        });
        h.method(doc, "open", 0, (n, a) -> {
            if (h.currentScript == null) {
                Node html = htmlOf(h);
                if (html != null) {
                    for (Node c : html.children) {
                        if (c.is("body") || c.is("head")) {
                            DomNodes.clearChildren(h, c);
                        }
                    }
                }
            }
            return h.wrap(h.doc);
        });
        h.method(doc, "close", 0, (n, a) -> Js.UNDEFINED);
    }
}
