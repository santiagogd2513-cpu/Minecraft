package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;

/** Promise. */
final class LibPromise {
    private LibPromise() {
    }

    private static JsPromise thisPromise(Object t, String m) {
        if (t instanceof JsPromise p) {
            return p;
        }
        throw Js.typeError("Method Promise.prototype." + m + " called on incompatible receiver " + Js.display(t));
    }

    private static Object makeAggregate(Realm r, ArrayList<Object> errors) {
        JsException ex = r.error("AggregateError", "All promises were rejected");
        if (ex.value instanceof JsObject eo) {
            eo.setHidden("errors", new JsArray(r.arrayProto, errors));
        }
        return ex.value;
    }

    static void install(Realm r) {
        JsObject pp = r.promiseProto;
        NativeFunction pc = Lib.ctor(r, "Promise", 1, pp, (t, a) -> {
            throw Js.typeError("Promise constructor cannot be invoked without 'new'");
        }, (a, nt) -> {
            Object ex = Js.arg(a, 0);
            if (!(ex instanceof JsFunction exf)) {
                throw Js.typeError("Promise resolver " + Js.display(ex) + " is not a function");
            }
            JsPromise p = new JsPromise(r, Lib.protoFrom(nt, pp));
            NativeFunction res = new NativeFunction(r, "", 1, (t, b) -> {
                p.resolve(Js.arg(b, 0));
                return Js.UNDEFINED;
            });
            NativeFunction rej = new NativeFunction(r, "", 1, (t, b) -> {
                p.reject(Js.arg(b, 0));
                return Js.UNDEFINED;
            });
            try {
                exf.call(Js.UNDEFINED, new Object[] {res, rej});
            } catch (JsException e) {
                p.reject(e.value);
            }
            return p;
        });
        r.promiseCtor = pc;
        pc.defineOwn(JsSymbol.SPECIES, new Prop(pc, null, Prop.C));
        Lib.tag(pp, "Promise");

        Lib.method(r, pp, "then", 2, (t, a) -> thisPromise(t, "then").then(Js.arg(a, 0), Js.arg(a, 1)));
        Lib.method(r, pp, "catch", 1, (t, a) -> {
            Object then = Js.getMember(r, t, "then");
            return Lib.callable(then, "then").call(t, new Object[] {Js.UNDEFINED, Js.arg(a, 0)});
        });
        Lib.method(r, pp, "finally", 1, (t, a) -> {
            Object f = Js.arg(a, 0);
            Object then = Js.getMember(r, t, "then");
            JsFunction thenFn = Lib.callable(then, "then");
            if (!(f instanceof JsFunction ff)) {
                return thenFn.call(t, new Object[] {f, f});
            }
            NativeFunction onOk = new NativeFunction(r, "", 1, (th, b) -> {
                Object value = Js.arg(b, 0);
                Object result = ff.call(Js.UNDEFINED, Js.EMPTY);
                return JsPromise.from(r, result).then(new NativeFunction(r, "", 0, (x, y) -> value), Js.UNDEFINED);
            });
            NativeFunction onErr = new NativeFunction(r, "", 1, (th, b) -> {
                Object reason = Js.arg(b, 0);
                Object result = ff.call(Js.UNDEFINED, Js.EMPTY);
                return JsPromise.from(r, result).then(new NativeFunction(r, "", 0, (x, y) -> {
                    throw new JsException(reason);
                }), Js.UNDEFINED);
            });
            return thenFn.call(t, new Object[] {onOk, onErr});
        });

        Lib.method(r, pc, "resolve", 1, (t, a) -> {
            Object v = Js.arg(a, 0);
            if (v instanceof JsPromise vp && vp.get("constructor") == t) {
                return vp;
            }
            JsPromise p = new JsPromise(r);
            p.resolve(v);
            return p;
        });
        Lib.method(r, pc, "reject", 1, (t, a) -> {
            JsPromise p = new JsPromise(r);
            p.reject(Js.arg(a, 0));
            return p;
        });
        Lib.method(r, pc, "withResolvers", 0, (t, a) -> {
            JsPromise p = new JsPromise(r);
            JsObject o = new JsObject(r.objectProto);
            o.setData("promise", p);
            o.setData("resolve", new NativeFunction(r, "", 1, (x, b) -> {
                p.resolve(Js.arg(b, 0));
                return Js.UNDEFINED;
            }));
            o.setData("reject", new NativeFunction(r, "", 1, (x, b) -> {
                p.reject(Js.arg(b, 0));
                return Js.UNDEFINED;
            }));
            return o;
        });
        Lib.method(r, pc, "try", 1, (t, a) -> {
            JsPromise p = new JsPromise(r);
            try {
                Object[] rest = a.length > 1 ? java.util.Arrays.copyOfRange(a, 1, a.length) : Js.EMPTY;
                p.resolve(Lib.callable(Js.arg(a, 0), null).call(Js.UNDEFINED, rest));
            } catch (JsException e) {
                p.reject(e.value);
            }
            return p;
        });
        Lib.method(r, pc, "all", 1, (t, a) -> {
            JsPromise result = new JsPromise(r);
            try {
                ArrayList<Object> values = new ArrayList<>();
                int[] remaining = {1};
                IterRec it = Js.iterate(Js.arg(a, 0));
                int idx = 0;
                for (Object item = it.next(); item != IterRec.DONE; item = it.next()) {
                    int my = idx++;
                    values.add(Js.UNDEFINED);
                    remaining[0]++;
                    JsPromise.from(r, item).thenJava(v -> {
                        values.set(my, v);
                        if (--remaining[0] == 0) {
                            result.resolve(new JsArray(r.arrayProto, values));
                        }
                    }, result::reject);
                }
                if (--remaining[0] == 0) {
                    result.resolve(new JsArray(r.arrayProto, values));
                }
            } catch (JsException e) {
                result.reject(e.value);
            }
            return result;
        });
        Lib.method(r, pc, "allSettled", 1, (t, a) -> {
            JsPromise result = new JsPromise(r);
            try {
                ArrayList<Object> values = new ArrayList<>();
                int[] remaining = {1};
                IterRec it = Js.iterate(Js.arg(a, 0));
                int idx = 0;
                for (Object item = it.next(); item != IterRec.DONE; item = it.next()) {
                    int my = idx++;
                    values.add(Js.UNDEFINED);
                    remaining[0]++;
                    JsPromise.from(r, item).thenJava(v -> {
                        JsObject o = new JsObject(r.objectProto);
                        o.setData("status", "fulfilled");
                        o.setData("value", v);
                        values.set(my, o);
                        if (--remaining[0] == 0) {
                            result.resolve(new JsArray(r.arrayProto, values));
                        }
                    }, e -> {
                        JsObject o = new JsObject(r.objectProto);
                        o.setData("status", "rejected");
                        o.setData("reason", e);
                        values.set(my, o);
                        if (--remaining[0] == 0) {
                            result.resolve(new JsArray(r.arrayProto, values));
                        }
                    });
                }
                if (--remaining[0] == 0) {
                    result.resolve(new JsArray(r.arrayProto, values));
                }
            } catch (JsException e) {
                result.reject(e.value);
            }
            return result;
        });
        Lib.method(r, pc, "race", 1, (t, a) -> {
            JsPromise result = new JsPromise(r);
            try {
                IterRec it = Js.iterate(Js.arg(a, 0));
                for (Object item = it.next(); item != IterRec.DONE; item = it.next()) {
                    JsPromise.from(r, item).thenJava(result::resolve, result::reject);
                }
            } catch (JsException e) {
                result.reject(e.value);
            }
            return result;
        });
        Lib.method(r, pc, "any", 1, (t, a) -> {
            JsPromise result = new JsPromise(r);
            try {
                ArrayList<Object> errors = new ArrayList<>();
                int[] remaining = {1};
                IterRec it = Js.iterate(Js.arg(a, 0));
                int idx = 0;
                for (Object item = it.next(); item != IterRec.DONE; item = it.next()) {
                    int my = idx++;
                    errors.add(Js.UNDEFINED);
                    remaining[0]++;
                    JsPromise.from(r, item).thenJava(result::resolve, e -> {
                        errors.set(my, e);
                        if (--remaining[0] == 0) {
                            result.reject(makeAggregate(r, errors));
                        }
                    });
                }
                if (--remaining[0] == 0) {
                    result.reject(makeAggregate(r, errors));
                }
            } catch (JsException e) {
                result.reject(e.value);
            }
            return result;
        });
    }
}
