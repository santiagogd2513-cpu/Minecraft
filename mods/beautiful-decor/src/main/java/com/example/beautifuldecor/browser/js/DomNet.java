package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.Http;
import com.example.beautifuldecor.browser.Url;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Red para los scripts (V2.9.0): fetch, Headers, Request, Response, AbortController, AbortSignal y XMLHttpRequest.
 * Las peticiones las hace el navegador (DomHost.Env.fetch) en otro hilo; la respuesta vuelve al hilo de los scripts.
 * Sin ArrayBuffer ni Blob (el motor no los tiene): los cuerpos son texto, JSON o formularios.
 */
final class DomNet {
    private static final Pattern TOKEN = Pattern.compile("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$");

    private DomNet() {
    }

    static String statusText(int code) {
        return switch (code) {
            case 100 -> "Continue";
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 418 -> "I'm a teapot";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ AbortController y AbortSignal

    static final class SignalState {
        boolean aborted;
        Object reason = Js.UNDEFINED;
        final ArrayList<Runnable> hooks = new ArrayList<>();
    }

    private static SignalState signalState(Object t) {
        if (t instanceof DomObj d && d.nat instanceof SignalState s) {
            return s;
        }
        throw Js.typeError("Illegal invocation");
    }

    static void abort(DomHost h, DomObj sig, Object reason) {
        SignalState st = (SignalState) sig.nat;
        if (st.aborted) {
            return;
        }
        st.aborted = true;
        st.reason = reason == Js.UNDEFINED ? h.domError("AbortError", "signal is aborted without reason").value : reason;
        for (Runnable hook : new ArrayList<>(st.hooks)) {
            hook.run();
        }
        st.hooks.clear();
        DomEvents.fireSimple(h, sig, "abort", false, false);
    }

    private static void installAbort(DomHost h) {
        Realm r = h.r;
        JsObject sp = new JsObject(h.eventTargetProto);
        Lib.tag(sp, "AbortSignal");
        h.protos.put("AbortSignal", sp);
        NativeFunction sc = Lib.ctor(r, "AbortSignal", 0, sp, (t, a) -> {
            throw Js.typeError("Illegal constructor");
        }, (a, nt) -> {
            throw Js.typeError("Illegal constructor");
        });
        Lib.getter(r, sp, "aborted", (t, a) -> signalState(t).aborted);
        Lib.getter(r, sp, "reason", (t, a) -> signalState(t).reason);
        Lib.method(r, sp, "throwIfAborted", 0, (t, a) -> {
            SignalState st = signalState(t);
            if (st.aborted) {
                throw new JsException(st.reason);
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, sc, "abort", 0, (t, a) -> {
            DomObj s = newSignal(h);
            abort(h, s, Js.arg(a, 0));
            return s;
        });
        Lib.method(r, sc, "timeout", 1, (t, a) -> {
            double ms = Js.toNumber(Js.arg(a, 0));
            DomObj s = newSignal(h);
            h.env.schedule(() -> {
                abort(h, s, h.domError("TimeoutError", "signal timed out").value);
                h.engine.runJobs();
            }, Double.isNaN(ms) || ms < 0 ? 0 : (long) ms);
            return s;
        });
        Lib.method(r, sc, "any", 1, (t, a) -> {
            DomObj any = newSignal(h);
            List<Object> sources = Js.toList(Js.arg(a, 0));
            for (Object o : sources) {
                SignalState st = signalState(o);
                if (st.aborted) {
                    abort(h, any, st.reason);
                    return any;
                }
            }
            for (Object o : sources) {
                SignalState st = signalState(o);
                st.hooks.add(() -> abort(h, any, st.reason));
            }
            return any;
        });

        JsObject cp = new JsObject(r.objectProto);
        Lib.tag(cp, "AbortController");
        Lib.ctor(r, "AbortController", 0, cp, (t, a) -> {
            throw Js.typeError("Class constructor AbortController cannot be invoked without 'new'");
        }, (a, nt) -> new DomObj(Lib.protoFrom(nt, cp), newSignal(h)));
        Lib.getter(r, cp, "signal", (t, a) -> {
            if (t instanceof DomObj d && d.nat instanceof DomObj s) {
                return s;
            }
            throw Js.typeError("Illegal invocation");
        });
        Lib.method(r, cp, "abort", 0, (t, a) -> {
            if (t instanceof DomObj d && d.nat instanceof DomObj s) {
                abort(h, s, Js.arg(a, 0));
                return Js.UNDEFINED;
            }
            throw Js.typeError("Illegal invocation");
        });
    }

    private static DomObj newSignal(DomHost h) {
        return new DomObj(h.protos.get("AbortSignal"), new SignalState());
    }

    // ------------------------------------------------------------------ Headers

    static final class HeadersObj extends JsObject {
        /** nombre en minúsculas -> {nombre, valor} */
        final LinkedHashMap<String, String[]> map = new LinkedHashMap<>();

        HeadersObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "Headers";
        }

        void append(String name, String value) {
            String k = name.toLowerCase(Locale.ROOT);
            String[] e = this.map.get(k);
            if (e == null) {
                this.map.put(k, new String[] {name, value});
            } else {
                e[1] = e[1] + ", " + value;
            }
        }

        void set(String name, String value) {
            this.map.put(name.toLowerCase(Locale.ROOT), new String[] {name, value});
        }

        String get(String name) {
            String[] e = this.map.get(name.toLowerCase(Locale.ROOT));
            return e == null ? null : e[1];
        }

        List<String[]> sorted() {
            ArrayList<String[]> out = new ArrayList<>();
            for (Map.Entry<String, String[]> e : this.map.entrySet()) {
                out.add(new String[] {e.getKey(), e.getValue()[1]});
            }
            out.sort((x, y) -> x[0].compareTo(y[0]));
            return out;
        }
    }

    private static HeadersObj headers(Object t) {
        if (t instanceof HeadersObj ho) {
            return ho;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static String headerName(String ctx, String name) {
        if (!TOKEN.matcher(name).matches()) {
            throw Js.typeError("Failed to execute '" + ctx + "' on 'Headers': Invalid name");
        }
        return name;
    }

    private static String headerValue(String ctx, String value) {
        String v = value.strip();
        if (v.indexOf('\0') >= 0 || v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0) {
            throw Js.typeError("Failed to execute '" + ctx + "' on 'Headers': Invalid value");
        }
        return v;
    }

    private static void fillHeaders(HeadersObj dst, Object init, String ctx) {
        if (init == Js.UNDEFINED || init == Js.NULL) {
            return;
        }
        if (init instanceof HeadersObj src) {
            for (String[] e : src.sorted()) {
                dst.append(e[0], e[1]);
            }
            return;
        }
        if (!(init instanceof JsObject o)) {
            throw Js.typeError("Failed to construct '" + ctx + "': The provided value is not of type '(record<ByteString, ByteString> or sequence<sequence<ByteString>>)'.");
        }
        if (o.hasProperty(JsSymbol.ITERATOR)) {
            for (Object item : Js.toList(o)) {
                List<Object> pair = Lib.listFromArrayLike(item);
                if (pair.size() != 2) {
                    throw Js.typeError("Failed to construct '" + ctx + "': Invalid value");
                }
                dst.append(headerName(ctx, Js.toStr(pair.get(0))), headerValue(ctx, Js.toStr(pair.get(1))));
            }
            return;
        }
        for (Object k : o.ownKeys()) {
            Prop pr = o.getOwn(k);
            if (k instanceof String name && pr != null && pr.enumerable()) {
                dst.append(headerName(ctx, name), headerValue(ctx, Js.toStr(o.get(name))));
            }
        }
    }

    private static void installHeaders(DomHost h) {
        Realm r = h.r;
        JsObject hp = new JsObject(r.objectProto);
        Lib.tag(hp, "Headers");
        h.protos.put("Headers", hp);
        Lib.ctor(r, "Headers", 0, hp, (t, a) -> {
            throw Js.typeError("Failed to construct 'Headers': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
        }, (a, nt) -> {
            HeadersObj ho = new HeadersObj(Lib.protoFrom(nt, hp));
            fillHeaders(ho, Js.arg(a, 0), "Headers");
            return ho;
        });
        Lib.method(r, hp, "append", 2, (t, a) -> {
            headers(t).append(headerName("append", DomHost.str(a, 0)), headerValue("append", DomHost.str(a, 1)));
            return Js.UNDEFINED;
        });
        Lib.method(r, hp, "set", 2, (t, a) -> {
            headers(t).set(headerName("set", DomHost.str(a, 0)), headerValue("set", DomHost.str(a, 1)));
            return Js.UNDEFINED;
        });
        Lib.method(r, hp, "get", 1, (t, a) -> {
            String v = headers(t).get(headerName("get", DomHost.str(a, 0)));
            return v == null ? Js.NULL : v;
        });
        Lib.method(r, hp, "has", 1, (t, a) -> headers(t).get(headerName("has", DomHost.str(a, 0))) != null);
        Lib.method(r, hp, "delete", 1, (t, a) -> {
            headers(t).map.remove(headerName("delete", DomHost.str(a, 0)).toLowerCase(Locale.ROOT));
            return Js.UNDEFINED;
        });
        Lib.method(r, hp, "getSetCookie", 0, (t, a) -> Lib.array(r, new ArrayList<Object>()));
        Lib.method(r, hp, "forEach", 1, (t, a) -> {
            JsFunction f = Lib.callable(Js.arg(a, 0), null);
            for (String[] e : headers(t).sorted()) {
                f.call(Js.arg(a, 1), new Object[] {e[1], e[0], t});
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, hp, "keys", 0, (t, a) -> iterOf(r, headers(t), 0));
        Lib.method(r, hp, "values", 0, (t, a) -> iterOf(r, headers(t), 1));
        Lib.method(r, hp, "entries", 0, (t, a) -> iterOf(r, headers(t), 2));
        hp.setHidden(JsSymbol.ITERATOR, hp.get("entries"));
    }

    private static Object iterOf(Realm r, HeadersObj ho, int mode) {
        ArrayList<Object> items = new ArrayList<>();
        for (String[] e : ho.sorted()) {
            items.add(mode == 0 ? e[0] : mode == 1 ? e[1] : Lib.array(r, List.of(e[0], e[1])));
        }
        return r.arrayValues.call(Lib.array(r, items), Js.EMPTY);
    }

    // ------------------------------------------------------------------ cuerpos, Request y Response

    abstract static class BodyObj extends JsObject {
        byte[] body;
        boolean used;
        HeadersObj headers;

        BodyObj(JsObject proto) {
            super(proto);
        }
    }

    static final class ResponseObj extends BodyObj {
        int status = 200;
        String statusText = "";
        String url = "";
        boolean redirected;
        String type = "default";

        ResponseObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "Response";
        }
    }

    static final class RequestObj extends BodyObj {
        String method = "GET";
        String url = "";
        Object signal = Js.NULL;
        String credentials = "same-origin";
        String mode = "cors";
        String cache = "default";
        String redirect = "follow";
        String referrer = "about:client";

        RequestObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "Request";
        }
    }

    private static boolean isFormData(DomHost h, DomUrl.ParamsObj p) {
        JsObject fd = h.protos.get("FormData");
        for (JsObject o = p.proto; o != null; o = o.proto) {
            if (o == fd) {
                return true;
            }
        }
        return false;
    }

    /** El cuerpo como bytes; {@code type[0]} recibe el Content-Type que le corresponde por omisión. */
    private static byte[] bytesOf(DomHost h, Object body, String[] type) {
        if (body instanceof DomUrl.ParamsObj p) {
            if (isFormData(h, p)) {
                String boundary = "----HSBrowserFormBoundary" + Long.toHexString(System.nanoTime());
                DomHost.Submission s = new DomHost.Submission();
                s.fields.addAll(p.list);
                type[0] = "multipart/form-data; boundary=" + boundary;
                return s.multipart(boundary);
            }
            type[0] = "application/x-www-form-urlencoded;charset=UTF-8";
            return p.toString().getBytes(StandardCharsets.UTF_8);
        }
        type[0] = "text/plain;charset=UTF-8";
        return Js.toStr(body).getBytes(StandardCharsets.UTF_8);
    }

    private static String charsetOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                return p.substring(8).replace("\"", "").trim();
            }
        }
        return null;
    }

    static String decode(byte[] body, String contentType) {
        return Http.decode(body == null ? new byte[0] : body, charsetOf(contentType), false);
    }

    private static BodyObj body(Object t, String cls) {
        if (t instanceof BodyObj b && b.className().equals(cls)) {
            return b;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static JsPromise rejected(DomHost h, Object reason) {
        JsPromise p = new JsPromise(h.r);
        p.reject(reason);
        return p;
    }

    private static void installBodyMethods(DomHost h, JsObject proto, String cls) {
        Realm r = h.r;
        for (String m : new String[] {"text", "json", "arrayBuffer", "blob", "formData", "bytes"}) {
            Lib.method(r, proto, m, 0, (t, a) -> {
                BodyObj b;
                try {
                    b = body(t, cls);
                } catch (JsException e) {
                    return rejected(h, e.value);
                }
                if (b.used) {
                    return rejected(h, Js.typeError("Failed to execute '" + m + "' on '" + cls + "': body stream already read").value);
                }
                b.used = true;
                String type = b.headers == null ? null : b.headers.get("content-type");
                JsPromise p = new JsPromise(r);
                switch (m) {
                    case "text" -> p.resolve(decode(b.body, type));
                    case "json" -> {
                        try {
                            p.resolve(LibJson.parse(r, decode(b.body, type)));
                        } catch (JsException e) {
                            p.reject(e.value);
                        }
                    }
                    case "formData" -> {
                        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                            DomUrl.ParamsObj fd = new DomUrl.ParamsObj(h.protos.get("FormData"));
                            fd.parse(decode(b.body, type));
                            p.resolve(fd);
                        } else {
                            p.reject(Js.typeError("Failed to fetch: formData() only works with application/x-www-form-urlencoded bodies in this browser").value);
                        }
                    }
                    default -> p.reject(Js.typeError(m + "() is not supported in this browser (it has no ArrayBuffer or Blob); use text() or json()").value);
                }
                return p;
            });
        }
        Lib.getter(r, proto, "bodyUsed", (t, a) -> body(t, cls).used);
        Lib.getter(r, proto, "body", (t, a) -> Js.NULL);
        Lib.getter(r, proto, "headers", (t, a) -> body(t, cls).headers);
    }

    private static ResponseObj newResponse(DomHost h, JsObject proto, int status, String statusText, String url, HeadersObj hd, byte[] body) {
        ResponseObj res = new ResponseObj(proto);
        res.status = status;
        res.statusText = statusText;
        res.url = url;
        res.headers = hd;
        res.body = body;
        return res;
    }

    private static ResponseObj response(Object t) {
        if (t instanceof ResponseObj ro) {
            return ro;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static RequestObj request(Object t) {
        if (t instanceof RequestObj ro) {
            return ro;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static void installResponse(DomHost h) {
        Realm r = h.r;
        JsObject rp = new JsObject(r.objectProto);
        Lib.tag(rp, "Response");
        h.protos.put("Response", rp);
        JsObject hp = h.protos.get("Headers");
        NativeFunction rc = Lib.ctor(r, "Response", 0, rp, (t, a) -> {
            throw Js.typeError("Failed to construct 'Response': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
        }, (a, nt) -> {
            Object bodyArg = Js.arg(a, 0);
            Object init = Js.arg(a, 1);
            int status = 200;
            String st = "";
            HeadersObj hd = new HeadersObj(hp);
            if (init instanceof JsObject io) {
                Object sv = io.get("status");
                if (sv != Js.UNDEFINED) {
                    double d = Js.toNumber(sv);
                    if (!(d >= 200 && d <= 599)) {
                        throw Js.rangeError("Failed to construct 'Response': The status provided (" + Js.toStr(sv) + ") is outside the range [200, 599].");
                    }
                    status = (int) d;
                }
                Object stx = io.get("statusText");
                if (stx != Js.UNDEFINED) {
                    st = Js.toStr(stx);
                }
                fillHeaders(hd, io.get("headers"), "Response");
            }
            byte[] bytes = null;
            if (bodyArg != Js.UNDEFINED && bodyArg != Js.NULL) {
                String[] type = new String[1];
                bytes = bytesOf(h, bodyArg, type);
                if (type[0] != null && hd.get("content-type") == null) {
                    hd.set("Content-Type", type[0]);
                }
            }
            return newResponse(h, Lib.protoFrom(nt, rp), status, st, "", hd, bytes);
        });
        installBodyMethods(h, rp, "Response");
        Lib.getter(r, rp, "status", (t, a) -> Js.num(response(t).status));
        Lib.getter(r, rp, "ok", (t, a) -> response(t).status >= 200 && response(t).status <= 299);
        Lib.getter(r, rp, "statusText", (t, a) -> response(t).statusText);
        Lib.getter(r, rp, "url", (t, a) -> response(t).url);
        Lib.getter(r, rp, "redirected", (t, a) -> response(t).redirected);
        Lib.getter(r, rp, "type", (t, a) -> response(t).type);
        Lib.method(r, rp, "clone", 0, (t, a) -> {
            ResponseObj src = response(t);
            if (src.used) {
                throw Js.typeError("Failed to execute 'clone' on 'Response': Response body is already used");
            }
            HeadersObj hd = new HeadersObj(hp);
            fillHeaders(hd, src.headers, "Response");
            ResponseObj copy = newResponse(h, src.proto, src.status, src.statusText, src.url, hd, src.body);
            copy.redirected = src.redirected;
            copy.type = src.type;
            return copy;
        });
        Lib.method(r, rc, "error", 0, (t, a) -> {
            ResponseObj e = newResponse(h, rp, 0, "", "", new HeadersObj(hp), null);
            e.type = "error";
            return e;
        });
        Lib.method(r, rc, "json", 1, (t, a) -> {
            Object json = ((JsObject) r.global.get("JSON")).get("stringify");
            Object text = json instanceof JsFunction f ? f.call(r.global.get("JSON"), new Object[] {Js.arg(a, 0)}) : Js.UNDEFINED;
            if (text == Js.UNDEFINED) {
                throw Js.typeError("Failed to execute 'json' on 'Response': The data is not JSON serializable");
            }
            HeadersObj hd = new HeadersObj(hp);
            int status = 200;
            String st = "";
            if (Js.arg(a, 1) instanceof JsObject io) {
                if (io.get("status") != Js.UNDEFINED) {
                    status = (int) Js.toNumber(io.get("status"));
                }
                if (io.get("statusText") != Js.UNDEFINED) {
                    st = Js.toStr(io.get("statusText"));
                }
                fillHeaders(hd, io.get("headers"), "Response");
            }
            if (hd.get("content-type") == null) {
                hd.set("Content-Type", "application/json");
            }
            return newResponse(h, rp, status, st, "", hd, Js.toStr(text).getBytes(StandardCharsets.UTF_8));
        });
        Lib.method(r, rc, "redirect", 1, (t, a) -> {
            HeadersObj hd = new HeadersObj(hp);
            hd.set("Location", h.resolveUrl(DomHost.str(a, 0)));
            double code = Js.arg(a, 1) == Js.UNDEFINED ? 302 : Js.toNumber(Js.arg(a, 1));
            return newResponse(h, rp, (int) code, "", "", hd, null);
        });
    }

    private static void installRequest(DomHost h) {
        Realm r = h.r;
        JsObject qp = new JsObject(r.objectProto);
        Lib.tag(qp, "Request");
        h.protos.put("Request", qp);
        Lib.ctor(r, "Request", 1, qp, (t, a) -> {
            throw Js.typeError("Failed to construct 'Request': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
        }, (a, nt) -> {
            if (a.length == 0) {
                throw Js.typeError("Failed to construct 'Request': 1 argument required, but only 0 present.");
            }
            RequestObj req = makeRequest(h, Js.arg(a, 0), Js.arg(a, 1));
            req.proto = Lib.protoFrom(nt, qp);
            return req;
        });
        installBodyMethods(h, qp, "Request");
        Lib.getter(r, qp, "url", (t, a) -> request(t).url);
        Lib.getter(r, qp, "method", (t, a) -> request(t).method);
        Lib.getter(r, qp, "signal", (t, a) -> request(t).signal);
        Lib.getter(r, qp, "credentials", (t, a) -> request(t).credentials);
        Lib.getter(r, qp, "mode", (t, a) -> request(t).mode);
        Lib.getter(r, qp, "cache", (t, a) -> request(t).cache);
        Lib.getter(r, qp, "redirect", (t, a) -> request(t).redirect);
        Lib.getter(r, qp, "referrer", (t, a) -> request(t).referrer);
        Lib.method(r, qp, "clone", 0, (t, a) -> {
            RequestObj src = request(t);
            if (src.used) {
                throw Js.typeError("Failed to execute 'clone' on 'Request': Request body is already used");
            }
            RequestObj copy = new RequestObj(src.proto);
            copy.method = src.method;
            copy.url = src.url;
            copy.signal = src.signal;
            copy.credentials = src.credentials;
            copy.mode = src.mode;
            copy.cache = src.cache;
            copy.redirect = src.redirect;
            copy.referrer = src.referrer;
            copy.body = src.body;
            copy.headers = new HeadersObj(src.headers.proto);
            fillHeaders(copy.headers, src.headers, "Request");
            return copy;
        });
    }

    private static String normalizeMethod(String m) {
        String u = m.toUpperCase(Locale.ROOT);
        switch (u) {
            case "GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH":
                return u;
            default:
                if (!TOKEN.matcher(m).matches()) {
                    throw Js.typeError("Failed to construct 'Request': '" + m + "' is not a valid HTTP method.");
                }
                return m;
        }
    }

    private static RequestObj makeRequest(DomHost h, Object input, Object init) {
        JsObject hp = h.protos.get("Headers");
        RequestObj req = new RequestObj(h.protos.get("Request"));
        req.headers = new HeadersObj(hp);
        if (input instanceof RequestObj src) {
            req.method = src.method;
            req.url = src.url;
            req.signal = src.signal;
            req.credentials = src.credentials;
            req.mode = src.mode;
            req.cache = src.cache;
            req.redirect = src.redirect;
            req.referrer = src.referrer;
            fillHeaders(req.headers, src.headers, "Request");
            if (src.used) {
                throw Js.typeError("Failed to construct 'Request': Cannot construct a Request with a Request object that has already been used.");
            }
            req.body = src.body;
        } else {
            String u = Js.toStr(input);
            req.url = Url.stripFragment(h.resolveUrl(u));
        }
        if (init instanceof JsObject io) {
            Object v = io.get("method");
            if (v != Js.UNDEFINED) {
                req.method = normalizeMethod(Js.toStr(v));
            }
            v = io.get("headers");
            if (v != Js.UNDEFINED) {
                req.headers = new HeadersObj(hp);
                fillHeaders(req.headers, v, "Request");
            }
            for (String name : new String[] {"credentials", "mode", "cache", "redirect", "referrer"}) {
                v = io.get(name);
                if (v != Js.UNDEFINED) {
                    String s = Js.toStr(v);
                    switch (name) {
                        case "credentials" -> req.credentials = s;
                        case "mode" -> req.mode = s;
                        case "cache" -> req.cache = s;
                        case "redirect" -> req.redirect = s;
                        default -> req.referrer = s;
                    }
                }
            }
            v = io.get("signal");
            if (v != Js.UNDEFINED) {
                if (v != Js.NULL && !(v instanceof DomObj d && d.nat instanceof SignalState)) {
                    throw Js.typeError("Failed to construct 'Request': Failed to read the 'signal' property from 'RequestInit': Failed to convert value to 'AbortSignal'.");
                }
                req.signal = v;
            }
            v = io.get("body");
            if (v != Js.UNDEFINED && v != Js.NULL) {
                if (req.method.equals("GET") || req.method.equals("HEAD")) {
                    throw Js.typeError("Failed to construct 'Request': Request with GET/HEAD method cannot have body.");
                }
                String[] type = new String[1];
                req.body = bytesOf(h, v, type);
                if (type[0] != null && req.headers.get("content-type") == null) {
                    req.headers.set("Content-Type", type[0]);
                }
            } else if (v == Js.NULL) {
                req.body = null;
            }
        }
        if (req.body != null && (req.method.equals("GET") || req.method.equals("HEAD"))) {
            throw Js.typeError("Failed to construct 'Request': Request with GET/HEAD method cannot have body.");
        }
        return req;
    }

    // ------------------------------------------------------------------ fetch

    private static DomHost.NetRequest netRequest(String method, String url, HeadersObj hd, byte[] body) {
        DomHost.NetRequest nr = new DomHost.NetRequest();
        nr.method = method;
        nr.url = url;
        boolean accept = false;
        for (String[] e : hd.map.values()) {
            nr.headers.put(e[0], e[1]);
            if (e[0].equalsIgnoreCase("accept")) {
                accept = true;
            }
        }
        if (!accept) {
            nr.headers.put("Accept", "*/*");
        }
        nr.body = body;
        return nr;
    }

    private static HeadersObj headersOf(DomHost h, DomHost.NetResult res) {
        HeadersObj hd = new HeadersObj(h.protos.get("Headers"));
        for (Map.Entry<String, List<String>> e : res.headers.entrySet()) {
            String name = e.getKey();
            if (name.equals("set-cookie") || name.equals("set-cookie2") || !TOKEN.matcher(name).matches()) {
                continue;
            }
            for (String v : e.getValue()) {
                if (v != null) {
                    hd.append(name, v.strip());
                }
            }
        }
        return hd;
    }

    private static void installFetch(DomHost h) {
        Realm r = h.r;
        Lib.method(r, h.windowProto, "fetch", 1, (t, a) -> {
            JsPromise p = new JsPromise(r);
            RequestObj req;
            try {
                if (a.length == 0) {
                    throw Js.typeError("Failed to execute 'fetch' on 'Window': 1 argument required, but only 0 present.");
                }
                req = makeRequest(h, a[0], Js.arg(a, 1));
            } catch (JsException e) {
                p.reject(e.value);
                return p;
            }
            DomObj sig = req.signal instanceof DomObj d ? d : null;
            SignalState st = sig == null ? null : (SignalState) sig.nat;
            if (st != null && st.aborted) {
                p.reject(st.reason);
                return p;
            }
            if (!Url.isWeb(req.url)) {
                String scheme = req.url.contains(":") ? req.url.substring(0, req.url.indexOf(':')) : req.url;
                h.env.console("error", "Fetch API cannot load " + req.url + ". URL scheme \"" + scheme + "\" is not supported.");
                p.reject(Js.typeError("Failed to fetch").value);
                return p;
            }
            req.used = true;
            DomHost.NetRequest nr = netRequest(req.method, req.url, req.headers, req.body);
            boolean[] done = {false};
            Runnable[] cancel = new Runnable[1];
            Runnable closer = () -> {
                done[0] = true;
                if (cancel[0] != null) {
                    cancel[0].run();
                }
            };
            h.netCancels.add(closer);
            if (st != null) {
                st.hooks.add(() -> {
                    if (done[0]) {
                        return;
                    }
                    closer.run();
                    h.netCancels.remove(closer);
                    p.reject(st.reason);
                });
            }
            cancel[0] = h.env.fetch(nr, res -> {
                h.netCancels.remove(closer);
                if (done[0]) {
                    return;
                }
                done[0] = true;
                if (res.error != null) {
                    h.env.console("error", "Fetch failed loading: " + req.method + " \"" + req.url + "\" (" + res.error + ")");
                    p.reject(Js.typeError("Failed to fetch").value);
                } else {
                    ResponseObj resp = newResponse(h, h.protos.get("Response"), res.status,
                            res.statusText == null || res.statusText.isEmpty() ? statusText(res.status) : res.statusText,
                            res.url == null || res.url.isEmpty() ? req.url : res.url, headersOf(h, res), res.body);
                    resp.redirected = !Url.stripFragment(resp.url).equals(Url.stripFragment(req.url));
                    resp.type = sameOrigin(h, resp.url) ? "basic" : "cors";
                    p.resolve(resp);
                }
                h.engine.runJobs();
            });
            return p;
        });
    }

    private static boolean sameOrigin(DomHost h, String url) {
        return DomUrl.parse(url).origin().equals(DomUrl.parse(h.env.url()).origin());
    }

    // ------------------------------------------------------------------ XMLHttpRequest

    static final class XhrObj extends JsObject {
        int state;
        String method = "GET";
        String url = "";
        boolean async = true;
        boolean sent;
        boolean errorFlag;
        final ArrayList<String[]> reqHeaders = new ArrayList<>();
        long timeout;
        String responseType = "";
        boolean withCredentials;
        int status;
        String statusText = "";
        String responseUrl = "";
        HeadersObj respHeaders;
        byte[] body = new byte[0];
        Runnable cancel;
        Runnable closer;
        Object jsonValue;
        boolean jsonDone;
        JsObject upload;
        int gen;

        XhrObj(JsObject proto) {
            super(proto);
        }

        @Override
        public String className() {
            return "XMLHttpRequest";
        }
    }

    private static XhrObj xhr(Object t) {
        if (t instanceof XhrObj x) {
            return x;
        }
        throw Js.typeError("Illegal invocation");
    }

    private static void setState(DomHost h, XhrObj x, int state) {
        x.state = state;
        DomEvents.fireSimple(h, x, "readystatechange", false, false);
    }

    private static void progress(DomHost h, XhrObj x, String type, long loaded, long total) {
        DomEvents.DomEvent e = DomEvents.create(h, "ProgressEvent", type, false, false);
        DomEvents.put(e, "lengthComputable", total > 0);
        DomEvents.put(e, "loaded", Js.num((double) loaded));
        DomEvents.put(e, "total", Js.num((double) total));
        DomEvents.dispatch(h, x, e);
    }

    private static void detach(DomHost h, XhrObj x) {
        if (x.closer != null) {
            h.netCancels.remove(x.closer);
        }
        x.closer = null;
        x.cancel = null;
    }

    private static void fail(DomHost h, XhrObj x, String type) {
        int g = x.gen;
        x.errorFlag = true;
        x.status = 0;
        x.statusText = "";
        x.body = new byte[0];
        setState(h, x, 4);
        if (x.gen != g) {
            return;
        }
        progress(h, x, type, 0, 0);
        if (x.gen == g) {
            progress(h, x, "loadend", 0, 0);
        }
    }

    private static void fill(DomHost h, XhrObj x, DomHost.NetResult res) {
        x.status = res.status;
        x.statusText = res.statusText == null || res.statusText.isEmpty() ? statusText(res.status) : res.statusText;
        x.responseUrl = res.url == null ? "" : res.url;
        x.respHeaders = headersOf(h, res);
        x.body = res.body == null ? new byte[0] : res.body;
        x.jsonDone = false;
    }

    private static void complete(DomHost h, XhrObj x, DomHost.NetResult res, int g) {
        detach(h, x);
        if (x.gen != g) {
            return;
        }
        if (res.error != null) {
            h.env.console("error", "XMLHttpRequest failed loading: " + x.method + " \"" + x.url + "\" (" + res.error + ")");
            fail(h, x, "error");
            return;
        }
        fill(h, x, res);
        setState(h, x, 2);
        if (x.gen != g) {
            return;
        }
        setState(h, x, 3);
        if (x.gen != g) {
            return;
        }
        progress(h, x, "progress", x.body.length, x.body.length);
        if (x.gen != g) {
            return;
        }
        setState(h, x, 4);
        if (x.gen != g) {
            return;
        }
        progress(h, x, "load", x.body.length, x.body.length);
        if (x.gen == g) {
            progress(h, x, "loadend", x.body.length, x.body.length);
        }
    }

    private static void resetResponse(XhrObj x) {
        x.status = 0;
        x.statusText = "";
        x.responseUrl = "";
        x.respHeaders = null;
        x.body = new byte[0];
        x.errorFlag = false;
        x.jsonDone = false;
        x.jsonValue = Js.NULL;
    }

    private static String xhrText(DomHost h, XhrObj x) {
        if (x.state < 3 || x.errorFlag) {
            return "";
        }
        return decode(x.body, x.respHeaders == null ? null : x.respHeaders.get("content-type"));
    }

    private static void installXhr(DomHost h) {
        Realm r = h.r;
        JsObject xp = new JsObject(h.eventTargetProto);
        Lib.tag(xp, "XMLHttpRequest");
        h.protos.put("XMLHttpRequest", xp);
        NativeFunction xc = Lib.ctor(r, "XMLHttpRequest", 0, xp, (t, a) -> {
            throw Js.typeError("Failed to construct 'XMLHttpRequest': Please use the 'new' operator, this DOM object constructor cannot be called as a function.");
        }, (a, nt) -> new XhrObj(Lib.protoFrom(nt, xp)));
        String[] names = {"UNSENT", "OPENED", "HEADERS_RECEIVED", "LOADING", "DONE"};
        for (int i = 0; i < names.length; i++) {
            xc.setConst(names[i], Js.num(i));
            xp.setConst(names[i], Js.num(i));
        }
        Lib.getter(r, xp, "readyState", (t, a) -> Js.num(xhr(t).state));
        Lib.getter(r, xp, "status", (t, a) -> Js.num(xhr(t).status));
        Lib.getter(r, xp, "statusText", (t, a) -> xhr(t).statusText);
        Lib.getter(r, xp, "responseURL", (t, a) -> xhr(t).responseUrl);
        Lib.getter(r, xp, "responseXML", (t, a) -> Js.NULL);
        Lib.accessor(r, xp, "timeout", (t, a) -> Js.num((double) xhr(t).timeout), (t, a) -> {
            double d = Js.toNumber(Js.arg(a, 0));
            xhr(t).timeout = Double.isNaN(d) || d < 0 ? 0 : (long) d;
            return Js.UNDEFINED;
        });
        Lib.accessor(r, xp, "withCredentials", (t, a) -> xhr(t).withCredentials, (t, a) -> {
            xhr(t).withCredentials = Js.truthy(Js.arg(a, 0));
            return Js.UNDEFINED;
        });
        Lib.accessor(r, xp, "responseType", (t, a) -> xhr(t).responseType, (t, a) -> {
            XhrObj x = xhr(t);
            String v = Js.toStr(Js.arg(a, 0));
            if (x.state >= 3) {
                throw h.domError("InvalidStateError", "Failed to set the 'responseType' property on 'XMLHttpRequest': The response type cannot be set if the object's state is LOADING or DONE.");
            }
            if (v.isEmpty() || v.equals("text") || v.equals("json") || v.equals("arraybuffer") || v.equals("blob") || v.equals("document")) {
                x.responseType = v;
            }
            return Js.UNDEFINED;
        });
        Lib.getter(r, xp, "upload", (t, a) -> {
            XhrObj x = xhr(t);
            if (x.upload == null) {
                x.upload = new JsObject(h.eventTargetProto);
                Lib.tag(x.upload, "XMLHttpRequestUpload");
            }
            return x.upload;
        });
        Lib.getter(r, xp, "responseText", (t, a) -> {
            XhrObj x = xhr(t);
            if (!(x.responseType.isEmpty() || x.responseType.equals("text"))) {
                throw h.domError("InvalidStateError", "Failed to read the 'responseText' property from 'XMLHttpRequest': The value is only accessible if the object's 'responseType' is '' or 'text' (was '" + x.responseType + "').");
            }
            return xhrText(h, x);
        });
        Lib.getter(r, xp, "response", (t, a) -> {
            XhrObj x = xhr(t);
            switch (x.responseType) {
                case "", "text":
                    return xhrText(h, x);
                case "json":
                    if (x.state != 4 || x.errorFlag) {
                        return Js.NULL;
                    }
                    if (!x.jsonDone) {
                        x.jsonDone = true;
                        try {
                            x.jsonValue = LibJson.parse(r, xhrText(h, x));
                        } catch (JsException e) {
                            x.jsonValue = Js.NULL;
                        }
                    }
                    return x.jsonValue;
                default:
                    return Js.NULL;
            }
        });
        Lib.method(r, xp, "open", 2, (t, a) -> {
            XhrObj x = xhr(t);
            if (a.length < 2) {
                throw Js.typeError("Failed to execute 'open' on 'XMLHttpRequest': 2 arguments required, but only " + a.length + " present.");
            }
            String method = normalizeMethod(DomHost.str(a, 0));
            String abs = h.resolveUrl(DomHost.str(a, 1));
            boolean async = a.length < 3 || a[2] == Js.UNDEFINED || Js.truthy(a[2]);
            x.gen++;
            if (x.cancel != null) {
                x.cancel.run();
            }
            detach(h, x);
            x.method = method;
            x.url = Url.stripFragment(abs);
            x.async = async;
            x.sent = false;
            x.reqHeaders.clear();
            resetResponse(x);
            setState(h, x, 1);
            return Js.UNDEFINED;
        });
        Lib.method(r, xp, "setRequestHeader", 2, (t, a) -> {
            XhrObj x = xhr(t);
            if (x.state != 1 || x.sent) {
                throw h.domError("InvalidStateError", "Failed to execute 'setRequestHeader' on 'XMLHttpRequest': The object's state must be OPENED.");
            }
            String name = headerName("setRequestHeader", DomHost.str(a, 0));
            String value = headerValue("setRequestHeader", DomHost.str(a, 1));
            for (String[] e : x.reqHeaders) {
                if (e[0].equalsIgnoreCase(name)) {
                    e[1] = e[1] + ", " + value;
                    return Js.UNDEFINED;
                }
            }
            x.reqHeaders.add(new String[] {name, value});
            return Js.UNDEFINED;
        });
        Lib.method(r, xp, "overrideMimeType", 1, (t, a) -> Js.UNDEFINED);
        Lib.method(r, xp, "getResponseHeader", 1, (t, a) -> {
            XhrObj x = xhr(t);
            if (x.state < 2 || x.errorFlag || x.respHeaders == null) {
                return Js.NULL;
            }
            String v = x.respHeaders.get(DomHost.str(a, 0));
            return v == null ? Js.NULL : v;
        });
        Lib.method(r, xp, "getAllResponseHeaders", 0, (t, a) -> {
            XhrObj x = xhr(t);
            if (x.state < 2 || x.errorFlag || x.respHeaders == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String[] e : x.respHeaders.map.values()) {
                sb.append(e[0].toLowerCase(Locale.ROOT)).append(": ").append(e[1]).append("\r\n");
            }
            return sb.toString();
        });
        Lib.method(r, xp, "abort", 0, (t, a) -> {
            XhrObj x = xhr(t);
            boolean inFlight = (x.state == 1 && x.sent) || x.state == 2 || x.state == 3;
            if (inFlight) {
                x.gen++;
                int g = x.gen;
                if (x.cancel != null) {
                    x.cancel.run();
                }
                detach(h, x);
                x.sent = false;
                x.errorFlag = true;
                x.status = 0;
                x.statusText = "";
                x.body = new byte[0];
                setState(h, x, 4);
                if (x.gen == g) {
                    progress(h, x, "abort", 0, 0);
                }
                if (x.gen == g) {
                    progress(h, x, "loadend", 0, 0);
                }
            }
            x.state = 0;
            return Js.UNDEFINED;
        });
        Lib.method(r, xp, "send", 0, (t, a) -> {
            XhrObj x = xhr(t);
            if (x.state != 1 || x.sent) {
                throw h.domError("InvalidStateError", "Failed to execute 'send' on 'XMLHttpRequest': The object's state must be OPENED.");
            }
            Object bodyArg = Js.arg(a, 0);
            HeadersObj hd = new HeadersObj(h.protos.get("Headers"));
            for (String[] e : x.reqHeaders) {
                hd.append(e[0], e[1]);
            }
            byte[] bytes = null;
            if (bodyArg != Js.UNDEFINED && bodyArg != Js.NULL && !x.method.equals("GET") && !x.method.equals("HEAD")) {
                String[] type = new String[1];
                bytes = bytesOf(h, bodyArg, type);
                if (hd.get("content-type") == null) {
                    hd.set("Content-Type", type[0]);
                }
            }
            if (!Url.isWeb(x.url)) {
                h.env.console("error", "XMLHttpRequest cannot load " + x.url + ". Only http and https are supported.");
                x.sent = true;
                if (!x.async) {
                    throw h.domError("NetworkError", "Failed to execute 'send' on 'XMLHttpRequest': Failed to load '" + x.url + "'.");
                }
                int g = x.gen;
                h.env.schedule(() -> {
                    if (x.gen == g) {
                        fail(h, x, "error");
                        h.engine.runJobs();
                    }
                }, 0);
                return Js.UNDEFINED;
            }
            x.sent = true;
            DomHost.NetRequest nr = netRequest(x.method, x.url, hd, bytes);
            if (!x.async) {
                DomHost.NetResult res = h.env.fetchSync(nr);
                h.rearmDeadline();
                if (res.error != null) {
                    h.env.console("error", "XMLHttpRequest failed loading: " + x.method + " \"" + x.url + "\" (" + res.error + ")");
                    x.errorFlag = true;
                    x.sent = false;
                    setState(h, x, 4);
                    throw h.domError("NetworkError", "Failed to execute 'send' on 'XMLHttpRequest': Failed to load '" + x.url + "'.");
                }
                fill(h, x, res);
                x.sent = false;
                setState(h, x, 4);
                return Js.UNDEFINED;
            }
            int g = x.gen;
            progress(h, x, "loadstart", 0, 0);
            if (x.gen != g) {
                return Js.UNDEFINED;
            }
            Runnable closer = () -> {
                if (x.gen == g) {
                    x.gen++;
                }
                if (x.cancel != null) {
                    x.cancel.run();
                }
            };
            x.closer = closer;
            h.netCancels.add(closer);
            x.cancel = h.env.fetch(nr, res -> {
                complete(h, x, res, g);
                h.engine.runJobs();
            });
            if (x.timeout > 0) {
                h.env.schedule(() -> {
                    if (x.gen != g || x.state == 4 || x.state == 0) {
                        return;
                    }
                    x.gen++;
                    int g2 = x.gen;
                    if (x.cancel != null) {
                        x.cancel.run();
                    }
                    detach(h, x);
                    x.sent = false;
                    x.errorFlag = true;
                    x.status = 0;
                    x.statusText = "";
                    x.body = new byte[0];
                    setState(h, x, 4);
                    if (x.gen == g2) {
                        progress(h, x, "timeout", 0, 0);
                    }
                    if (x.gen == g2) {
                        progress(h, x, "loadend", 0, 0);
                    }
                    h.engine.runJobs();
                }, x.timeout);
            }
            return Js.UNDEFINED;
        });
    }

    // ------------------------------------------------------------------ instalación

    static void install(DomHost h) {
        installAbort(h);
        installHeaders(h);
        installResponse(h);
        installRequest(h);
        installFetch(h);
        installXhr(h);
    }
}
