package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;

/** Error y sus tipos. */
final class LibError {
    private LibError() {
    }

    private static JsObject create(Realm r, JsObject proto, Object[] a, int msgIndex) {
        JsError e = new JsError(proto);
        Object msg = Js.arg(a, msgIndex);
        String m = "";
        if (msg != Js.UNDEFINED) {
            m = Js.toStr(msg);
            e.setHidden("message", m);
        }
        Object opts = Js.arg(a, msgIndex + 1);
        if (opts instanceof JsObject oo && oo.hasProperty("cause")) {
            e.setHidden("cause", oo.get("cause"));
        }
        Object name = proto.get("name");
        String n = name == Js.UNDEFINED ? "Error" : Js.toStr(name);
        e.setHidden("stack", (m.isEmpty() ? n : n + ": " + m) + "\n    at <anonymous>");
        return e;
    }

    static void install(Realm r) {
        JsObject ep = r.errorProto;
        NativeFunction error = Lib.ctor(r, "Error", 1, ep, (t, a) -> create(r, ep, a, 0), (a, nt) -> create(r, Lib.protoFrom(nt, ep), a, 0));
        ep.setHidden("name", "Error");
        ep.setHidden("message", "");
        Lib.method(r, ep, "toString", 0, (t, a) -> {
            if (!(t instanceof JsObject o)) {
                throw Js.typeError("Error.prototype.toString requires that 'this' be an Object");
            }
            Object nv = o.get("name");
            Object mv = o.get("message");
            String n = nv == Js.UNDEFINED ? "Error" : Js.toStr(nv);
            String m = mv == Js.UNDEFINED ? "" : Js.toStr(mv);
            return n.isEmpty() ? m : m.isEmpty() ? n : n + ": " + m;
        });
        Lib.method(r, error, "captureStackTrace", 1, (t, a) -> {
            if (Js.arg(a, 0) instanceof JsObject o) {
                o.setHidden("stack", Js.describeError(o) + "\n    at <anonymous>");
            }
            return Js.UNDEFINED;
        });
        error.setData("stackTraceLimit", 10.0);
        r.errorProtos.put("Error", ep);
        for (String name : new String[] {"TypeError", "RangeError", "SyntaxError", "ReferenceError", "EvalError", "URIError"}) {
            JsObject proto = new JsObject(ep);
            NativeFunction c = Lib.ctor(r, name, 1, proto, (t, a) -> create(r, proto, a, 0), (a, nt) -> create(r, Lib.protoFrom(nt, proto), a, 0));
            c.proto = error;
            proto.setHidden("name", name);
            proto.setHidden("message", "");
            r.errorProtos.put(name, proto);
        }
        JsObject ap = new JsObject(ep);
        NativeFunction agg = Lib.ctor(r, "AggregateError", 2, ap, (t, a) -> aggregate(r, ap, a), (a, nt) -> aggregate(r, Lib.protoFrom(nt, ap), a));
        agg.proto = error;
        ap.setHidden("name", "AggregateError");
        ap.setHidden("message", "");
        r.errorProtos.put("AggregateError", ap);
    }

    private static Object aggregate(Realm r, JsObject proto, Object[] a) {
        JsObject e = create(r, proto, a, 1);
        ArrayList<Object> errs = Js.toList(Js.arg(a, 0));
        e.setHidden("errors", Lib.array(r, errs));
        return e;
    }
}
