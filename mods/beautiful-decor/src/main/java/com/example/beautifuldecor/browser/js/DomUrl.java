package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Url;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Las clases URL y URLSearchParams, y el análisis de direcciones que usan los enlaces y location. */
final class DomUrl {
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):(.*)$", Pattern.DOTALL);
    private static final Map<String, String> SPECIAL = new HashMap<>();

    static {
        SPECIAL.put("http", "80");
        SPECIAL.put("https", "443");
        SPECIAL.put("ftp", "21");
        SPECIAL.put("ws", "80");
        SPECIAL.put("wss", "443");
        SPECIAL.put("file", "");
    }

    private DomUrl() {
    }

    // ------------------------------------------------------------------ partes de una dirección

    static final class Parts {
        boolean ok = true;
        String raw = "";
        String protocol = "";
        boolean authority;
        String username = "";
        String password = "";
        String hostname = "";
        String port = "";
        String pathname = "";
        String search = "";
        String hash = "";

        Parts copy() {
            Parts p = new Parts();
            p.ok = this.ok;
            p.raw = this.raw;
            p.protocol = this.protocol;
            p.authority = this.authority;
            p.username = this.username;
            p.password = this.password;
            p.hostname = this.hostname;
            p.port = this.port;
            p.pathname = this.pathname;
            p.search = this.search;
            p.hash = this.hash;
            return p;
        }

        String scheme() {
            return this.protocol.endsWith(":") ? this.protocol.substring(0, this.protocol.length() - 1) : this.protocol;
        }

        boolean special() {
            return SPECIAL.containsKey(scheme());
        }

        String host() {
            return this.port.isEmpty() ? this.hostname : this.hostname + ":" + this.port;
        }

        String origin() {
            String s = scheme();
            if (!this.ok || !SPECIAL.containsKey(s) || s.equals("file")) {
                return "null";
            }
            return this.protocol + "//" + host();
        }

        String get(String name) {
            if (!this.ok) {
                return "";
            }
            switch (name) {
                case "protocol":
                    return this.protocol;
                case "username":
                    return this.username;
                case "password":
                    return this.password;
                case "host":
                    return host();
                case "hostname":
                    return this.hostname;
                case "port":
                    return this.port;
                case "pathname":
                    return this.pathname;
                case "search":
                    return this.search.length() <= 1 ? "" : this.search;
                case "hash":
                    return this.hash.length() <= 1 ? "" : this.hash;
                case "origin":
                    return origin();
                case "href":
                    return href();
                default:
                    return "";
            }
        }

        void set(String name, String value) {
            if (!this.ok) {
                return;
            }
            switch (name) {
                case "protocol": {
                    int colon = value.indexOf(':');
                    String v = (colon < 0 ? value : value.substring(0, colon)).toLowerCase(Locale.ROOT);
                    if (v.matches("[a-z][a-z0-9+.\\-]*") && SPECIAL.containsKey(v) == special()) {
                        this.protocol = v + ":";
                        if (this.port.equals(SPECIAL.get(v))) {
                            this.port = "";
                        }
                    }
                    break;
                }
                case "username":
                    this.username = encode(value, "\"<>`#?{}/:;=@[\\]^|", false);
                    break;
                case "password":
                    this.password = encode(value, "\"<>`#?{}/:;=@[\\]^|", false);
                    break;
                case "host": {
                    int c = value.lastIndexOf(':');
                    if (c > 0 && value.indexOf(']') < c) {
                        setHost(value.substring(0, c));
                        setPort(value.substring(c + 1));
                    } else {
                        setHost(value);
                    }
                    break;
                }
                case "hostname":
                    setHost(value);
                    break;
                case "port":
                    setPort(value);
                    break;
                case "pathname":
                    if (this.authority) {
                        String v = value.replace('\\', '/');
                        if (!v.startsWith("/")) {
                            v = "/" + v;
                        }
                        this.pathname = normalizePath(encode(v, "?#", false));
                    }
                    break;
                case "search": {
                    String v = value.startsWith("?") ? value.substring(1) : value;
                    this.search = v.isEmpty() ? "" : "?" + encode(v, "#", false);
                    break;
                }
                case "hash": {
                    String v = value.startsWith("#") ? value.substring(1) : value;
                    this.hash = v.isEmpty() ? "" : "#" + encode(v, "", false);
                    break;
                }
                default:
                    break;
            }
        }

        private void setHost(String v) {
            String h = cleanHost(v.trim(), special());
            if (h != null && (!h.isEmpty() || !special())) {
                this.hostname = h;
            }
        }

        private void setPort(String v) {
            String digits = v.trim();
            int end = 0;
            while (end < digits.length() && Character.isDigit(digits.charAt(end))) {
                end++;
            }
            if (end == 0) {
                if (digits.isEmpty()) {
                    this.port = "";
                }
                return;
            }
            try {
                int n = Integer.parseInt(digits.substring(0, end));
                if (n > 65535) {
                    return;
                }
                this.port = Integer.toString(n).equals(SPECIAL.get(scheme())) ? "" : Integer.toString(n);
            } catch (NumberFormatException e) {
                // demasiado grande: se ignora
            }
        }

        String href() {
            if (!this.ok) {
                return this.raw;
            }
            StringBuilder sb = new StringBuilder(this.protocol);
            if (this.authority) {
                sb.append("//");
                if (!this.username.isEmpty() || !this.password.isEmpty()) {
                    sb.append(this.username);
                    if (!this.password.isEmpty()) {
                        sb.append(':').append(this.password);
                    }
                    sb.append('@');
                }
                sb.append(host());
            }
            sb.append(this.pathname).append(this.search).append(this.hash);
            return sb.toString();
        }
    }

    /** Escapa lo que una dirección no admite: espacios, controles, no ASCII y los caracteres de {@code extra}. */
    static String encode(String s, String extra, boolean plusForSpace) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 255;
            if (c <= 32 || c >= 127 || c == '"' || c == '<' || c == '>' || c == '`' || extra.indexOf(c) >= 0) {
                if (c == ' ' && plusForSpace) {
                    sb.append('+');
                } else {
                    sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16))).append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
                }
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private static String cleanHost(String h, boolean special) {
        if (h.startsWith("[")) {
            return h.endsWith("]") ? h.toLowerCase(Locale.ROOT) : null;
        }
        if (special) {
            String l = h.toLowerCase(Locale.ROOT);
            for (int i = 0; i < l.length(); i++) {
                if (l.charAt(i) >= 0x80) {
                    try {
                        l = IDN.toASCII(l, IDN.ALLOW_UNASSIGNED);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    break;
                }
            }
            for (int i = 0; i < l.length(); i++) {
                char c = l.charAt(i);
                if (c <= 32 || "#%/:<>?@[\\]^|".indexOf(c) >= 0) {
                    return null;
                }
            }
            return l;
        }
        return h;
    }

    static String normalizePath(String path) {
        if (path.isEmpty()) {
            return "";
        }
        String[] segs = path.split("/", -1);
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < segs.length; i++) {
            String sg = segs[i];
            String low = sg.toLowerCase(Locale.ROOT);
            boolean dot = low.equals(".") || low.equals("%2e");
            boolean dd = low.equals("..") || low.equals(".%2e") || low.equals("%2e.") || low.equals("%2e%2e");
            if (i == 0 && sg.isEmpty()) {
                out.add("");
                continue;
            }
            if (dd) {
                if (out.size() > 1) {
                    out.remove(out.size() - 1);
                }
                if (i == segs.length - 1) {
                    out.add("");
                }
            } else if (dot) {
                if (i == segs.length - 1) {
                    out.add("");
                }
            } else {
                out.add(sg);
            }
        }
        return String.join("/", out);
    }

    /** Analiza una dirección absoluta; null si no lo es o está mal escrita. */
    static Parts parseOrNull(String url) {
        String s = url.strip();
        s = s.replace("\t", "").replace("\n", "").replace("\r", "");
        Matcher m = SCHEME.matcher(s);
        if (!m.matches()) {
            return null;
        }
        Parts p = new Parts();
        p.protocol = m.group(1).toLowerCase(Locale.ROOT) + ":";
        String rest = m.group(2);
        boolean special = p.special();
        if (special) {
            rest = rest.replace('\\', '/');
        }
        String hash = "";
        int hi = rest.indexOf('#');
        if (hi >= 0) {
            hash = rest.substring(hi + 1);
            rest = rest.substring(0, hi);
        }
        String query = null;
        int qi = rest.indexOf('?');
        if (qi >= 0) {
            query = rest.substring(qi + 1);
            rest = rest.substring(0, qi);
        }
        if (rest.startsWith("//")) {
            p.authority = true;
            String auth = rest.substring(2);
            String path = "";
            int slash = auth.indexOf('/');
            if (slash >= 0) {
                path = auth.substring(slash);
                auth = auth.substring(0, slash);
            }
            int at = auth.lastIndexOf('@');
            if (at >= 0) {
                String info = auth.substring(0, at);
                auth = auth.substring(at + 1);
                int colon = info.indexOf(':');
                p.username = encode(colon < 0 ? info : info.substring(0, colon), "\"<>`#?{}/:;=@[\\]^|", false);
                p.password = colon < 0 ? "" : encode(info.substring(colon + 1), "\"<>`#?{}/:;=@[\\]^|", false);
            }
            String host = auth;
            String port = "";
            int pc = auth.lastIndexOf(':');
            if (pc >= 0 && auth.indexOf(']') < pc) {
                host = auth.substring(0, pc);
                port = auth.substring(pc + 1);
            }
            String h = cleanHost(host, special);
            if (h == null || (special && h.isEmpty() && !p.scheme().equals("file"))) {
                return null;
            }
            p.hostname = h;
            if (!port.isEmpty()) {
                if (!port.chars().allMatch(Character::isDigit) || port.length() > 5) {
                    return null;
                }
                int n = Integer.parseInt(port);
                if (n > 65535) {
                    return null;
                }
                p.port = Integer.toString(n).equals(SPECIAL.get(p.scheme())) ? "" : Integer.toString(n);
            }
            p.pathname = normalizePath(encode(path, "", false));
            if (p.pathname.isEmpty() && special) {
                p.pathname = "/";
            }
        } else {
            if (special && !p.scheme().equals("file")) {
                return null;
            }
            p.pathname = rest.startsWith("/") ? normalizePath(encode(rest, "", false)) : encodeOpaque(rest);
        }
        p.search = query == null ? "" : "?" + encode(query, special ? "'" : "", false);
        p.hash = hash.isEmpty() ? "" : "#" + encode(hash, "", false);
        return p;
    }

    private static String encodeOpaque(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c == 127) {
                sb.append('%').append(String.format("%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Como parseOrNull, pero nunca devuelve null: una dirección rara queda como texto sin partes. */
    static Parts parse(String url) {
        Parts p = parseOrNull(url);
        if (p == null) {
            p = new Parts();
            p.ok = false;
            p.raw = url;
        }
        return p;
    }

    /** Resuelve ref respecto a base (que puede ser null); null si no se puede. */
    static Parts resolve(String ref, Parts base) {
        String r = ref.strip().replace("\t", "").replace("\n", "").replace("\r", "");
        Matcher m = SCHEME.matcher(r);
        if (m.matches()) {
            Parts abs = parseOrNull(r);
            if (abs != null && base != null && base.ok && abs.special() && !abs.authority) {
                return null;
            }
            return abs;
        }
        if (base == null || !base.ok) {
            return null;
        }
        boolean special = base.special();
        if (special) {
            r = r.replace('\\', '/');
        }
        if (r.startsWith("//")) {
            return parseOrNull(base.protocol + r);
        }
        if (!base.authority && !base.pathname.startsWith("/")) {
            if (r.startsWith("#")) {
                Parts p = base.copy();
                p.hash = r.length() > 1 ? "#" + encode(r.substring(1), "", false) : "";
                return p;
            }
            return null;
        }
        Parts p = base.copy();
        String hash = "";
        int hi = r.indexOf('#');
        if (hi >= 0) {
            hash = r.substring(hi + 1);
            r = r.substring(0, hi);
        }
        p.hash = hash.isEmpty() ? "" : "#" + encode(hash, "", false);
        if (r.isEmpty()) {
            return p;
        }
        String query = null;
        int qi = r.indexOf('?');
        if (qi >= 0) {
            query = r.substring(qi + 1);
            r = r.substring(0, qi);
        }
        p.search = query == null ? (r.isEmpty() ? base.search : "") : "?" + encode(query, special ? "'" : "", false);
        if (!r.isEmpty()) {
            String path;
            if (r.startsWith("/")) {
                path = r;
            } else {
                int slash = base.pathname.lastIndexOf('/');
                path = (slash >= 0 ? base.pathname.substring(0, slash + 1) : "/") + r;
            }
            p.pathname = normalizePath(encode(path, "", false));
        }
        return p;
    }

    // ------------------------------------------------------------------ URLSearchParams

    static final class ParamsObj extends JsObject {
        final ArrayList<String[]> list = new ArrayList<>();
        UrlObj owner;

        ParamsObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "URLSearchParams";
        }

        void parse(String query) {
            this.list.clear();
            String q = query.startsWith("?") ? query.substring(1) : query;
            if (q.isEmpty()) {
                return;
            }
            for (String part : q.split("&")) {
                if (part.isEmpty()) {
                    continue;
                }
                int eq = part.indexOf('=');
                String k = eq < 0 ? part : part.substring(0, eq);
                String v = eq < 0 ? "" : part.substring(eq + 1);
                this.list.add(new String[] {Url.decode(k), Url.decode(v)});
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (String[] e : this.list) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(Url.encode(e[0])).append('=').append(Url.encode(e[1]));
            }
            return sb.toString();
        }

        void changed() {
            if (this.owner != null) {
                String s = toString();
                this.owner.parts.search = s.isEmpty() ? "" : "?" + s;
            }
        }
    }

    static final class UrlObj extends JsObject {
        Parts parts;
        ParamsObj params;

        UrlObj(JsObject proto, Parts parts) {
            super(proto);
            this.parts = parts;
        }

        @Override
        public String className() {
            return "URL";
        }
    }

    private static ParamsObj params(Object t) {
        if (t instanceof ParamsObj p) {
            return p;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static UrlObj url(Object t) {
        if (t instanceof UrlObj u) {
            return u;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static JsArray pair(Realm r, String k, String v) {
        ArrayList<Object> l = new ArrayList<>();
        l.add(k);
        l.add(v);
        return new JsArray(r.arrayProto, l);
    }

    private static JsArray all(Realm r, ParamsObj p, int mode) {
        ArrayList<Object> l = new ArrayList<>();
        for (String[] e : p.list) {
            l.add(mode == 0 ? e[0] : mode == 1 ? e[1] : pair(r, e[0], e[1]));
        }
        return new JsArray(r.arrayProto, l);
    }

    private static void fill(ParamsObj p, Object init) {
        if (init == Js.UNDEFINED || init == Js.NULL) {
            return;
        }
        if (init instanceof ParamsObj o) {
            for (String[] e : o.list) {
                p.list.add(new String[] {e[0], e[1]});
            }
            return;
        }
        if (init instanceof JsObject o) {
            if (o.hasProperty(JsSymbol.ITERATOR)) {
                for (Object item : Js.toList(o)) {
                    List<Object> kv = Lib.listFromArrayLike(item);
                    if (kv.size() != 2) {
                        throw Js.typeError("Failed to construct 'URLSearchParams': Sequence initializer must only contain pair elements");
                    }
                    p.list.add(new String[] {Js.toStr(kv.get(0)), Js.toStr(kv.get(1))});
                }
            } else {
                for (Object k : o.ownKeys()) {
                    Prop pr = o.getOwn(k);
                    if (k instanceof String s && pr != null && (pr.flags & Prop.E) != 0) {
                        p.list.add(new String[] {s, Js.toStr(o.get(s))});
                    }
                }
            }
            return;
        }
        p.parse(Js.toStr(init));
    }

    static void install(DomHost h) {
        Realm r = h.r;

        // URLSearchParams
        JsObject sp = new JsObject(r.objectProto);
        h.protos.put("URLSearchParams", sp);
        Lib.tag(sp, "URLSearchParams");
        Lib.ctor(r, "URLSearchParams", 0, sp, (t, a) -> {
            throw Js.typeError("Class constructor URLSearchParams cannot be invoked without 'new'");
        }, (a, nt) -> {
            ParamsObj p = new ParamsObj(Lib.protoFrom(nt, sp));
            fill(p, Js.arg(a, 0));
            return p;
        });
        Lib.method(r, sp, "append", 2, (t, a) -> {
            ParamsObj p = params(t);
            p.list.add(new String[] {DomHost.str(a, 0), DomHost.str(a, 1)});
            p.changed();
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "delete", 1, (t, a) -> {
            ParamsObj p = params(t);
            String k = DomHost.str(a, 0);
            boolean withValue = a.length > 1 && a[1] != Js.UNDEFINED;
            String v = withValue ? Js.toStr(a[1]) : null;
            p.list.removeIf(e -> e[0].equals(k) && (v == null || e[1].equals(v)));
            p.changed();
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "get", 1, (t, a) -> {
            String k = DomHost.str(a, 0);
            for (String[] e : params(t).list) {
                if (e[0].equals(k)) {
                    return e[1];
                }
            }
            return Js.NULL;
        });
        Lib.method(r, sp, "getAll", 1, (t, a) -> {
            String k = DomHost.str(a, 0);
            ArrayList<Object> out = new ArrayList<>();
            for (String[] e : params(t).list) {
                if (e[0].equals(k)) {
                    out.add(e[1]);
                }
            }
            return new JsArray(r.arrayProto, out);
        });
        Lib.method(r, sp, "has", 1, (t, a) -> {
            String k = DomHost.str(a, 0);
            boolean withValue = a.length > 1 && a[1] != Js.UNDEFINED;
            for (String[] e : params(t).list) {
                if (e[0].equals(k) && (!withValue || e[1].equals(Js.toStr(a[1])))) {
                    return true;
                }
            }
            return false;
        });
        Lib.method(r, sp, "set", 2, (t, a) -> {
            ParamsObj p = params(t);
            String k = DomHost.str(a, 0);
            String v = DomHost.str(a, 1);
            boolean done = false;
            for (int i = 0; i < p.list.size(); i++) {
                String[] e = p.list.get(i);
                if (e[0].equals(k)) {
                    if (!done) {
                        e[1] = v;
                        done = true;
                    } else {
                        p.list.remove(i--);
                    }
                }
            }
            if (!done) {
                p.list.add(new String[] {k, v});
            }
            p.changed();
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "sort", 0, (t, a) -> {
            ParamsObj p = params(t);
            p.list.sort((x, y) -> x[0].compareTo(y[0]));
            p.changed();
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "toString", 0, (t, a) -> params(t).toString());
        Lib.method(r, sp, "forEach", 1, (t, a) -> {
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            for (String[] e : new ArrayList<>(params(t).list)) {
                f.call(Js.arg(a, 1), new Object[] {e[1], e[0], t});
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, sp, "keys", 0, (t, a) -> r.arrayValues.call(all(r, params(t), 0), Js.EMPTY));
        Lib.method(r, sp, "values", 0, (t, a) -> r.arrayValues.call(all(r, params(t), 1), Js.EMPTY));
        Lib.method(r, sp, "entries", 0, (t, a) -> r.arrayValues.call(all(r, params(t), 2), Js.EMPTY));
        sp.setHidden(JsSymbol.ITERATOR, sp.get("entries"));
        Lib.getter(r, sp, "size", (t, a) -> Js.num(params(t).list.size()));

        // URL
        JsObject up = new JsObject(r.objectProto);
        Lib.tag(up, "URL");
        NativeFunction ctor = Lib.ctor(r, "URL", 1, up, (t, a) -> {
            throw Js.typeError("Class constructor URL cannot be invoked without 'new'");
        }, (a, nt) -> {
            Parts parts = build(Js.toStr(Js.arg(a, 0)), Js.arg(a, 1));
            if (parts == null) {
                throw Js.typeError("Failed to construct 'URL': Invalid URL");
            }
            return new UrlObj(Lib.protoFrom(nt, up), parts);
        });
        Lib.method(r, ctor, "canParse", 1, (t, a) -> build(DomHost.str(a, 0), Js.arg(a, 1)) != null);
        for (String part : new String[] {"protocol", "username", "password", "host", "hostname", "port", "pathname", "search", "hash"}) {
            Lib.accessor(r, up, part, (t, a) -> url(t).parts.get(part), (t, a) -> {
                UrlObj u = url(t);
                u.parts.set(part, Js.toStr(Js.arg(a, 0)));
                if (part.equals("search") && u.params != null) {
                    u.params.parse(u.parts.get("search"));
                }
                return Js.UNDEFINED;
            });
        }
        Lib.getter(r, up, "origin", (t, a) -> url(t).parts.origin());
        Lib.accessor(r, up, "href", (t, a) -> url(t).parts.href(), (t, a) -> {
            UrlObj u = url(t);
            Parts p = parseOrNull(Js.toStr(Js.arg(a, 0)));
            if (p == null) {
                throw Js.typeError("Failed to set the 'href' property on 'URL': Invalid URL");
            }
            u.parts = p;
            if (u.params != null) {
                u.params.parse(p.get("search"));
            }
            return Js.UNDEFINED;
        });
        Lib.getter(r, up, "searchParams", (t, a) -> {
            UrlObj u = url(t);
            if (u.params == null) {
                u.params = new ParamsObj(sp);
                u.params.parse(u.parts.get("search"));
                u.params.owner = u;
            }
            return u.params;
        });
        Lib.method(r, up, "toString", 0, (t, a) -> url(t).parts.href());
        Lib.method(r, up, "toJSON", 0, (t, a) -> url(t).parts.href());

        // FormData: pares nombre/valor (los archivos no existen en este navegador, todo es texto)
        JsObject fp = new JsObject(r.objectProto);
        h.protos.put("FormData", fp);
        Lib.tag(fp, "FormData");
        Lib.ctor(r, "FormData", 0, fp, (t, a) -> {
            throw Js.typeError("Failed to construct 'FormData': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
        }, (a, nt) -> {
            ParamsObj p = new ParamsObj(Lib.protoFrom(nt, fp));
            Object form = Js.arg(a, 0);
            if (form != Js.UNDEFINED && form != Js.NULL) {
                com.example.beautifuldecor.browser.Node f = DomHost.asNode(form);
                if (f == null || !f.is("form")) {
                    throw Js.typeError("Failed to construct 'FormData': parameter 1 is not of type 'HTMLFormElement'.");
                }
                com.example.beautifuldecor.browser.Node sub = DomHost.asNode(Js.arg(a, 1));
                for (String[] e : DomForms.entries(h, f, sub)) {
                    p.list.add(new String[] {e[0], e[1]});
                }
            }
            return p;
        });
        for (String m : new String[] {"append", "delete", "get", "getAll", "has", "set", "forEach", "keys", "values", "entries"}) {
            fp.setHidden(m, sp.get(m));
        }
        fp.setHidden(JsSymbol.ITERATOR, sp.get("entries"));
    }

    private static Parts build(String url, Object base) {
        if (base != Js.UNDEFINED && base != Js.NULL) {
            Parts b = base instanceof UrlObj bu ? bu.parts : parseOrNull(Js.toStr(base));
            if (b == null) {
                return null;
            }
            return resolve(url, b);
        }
        return parseOrNull(url);
    }
}
