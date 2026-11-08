package com.example.beautifuldecor.browser.js;

/** Iteradores nativos y generadores. */
final class LibIter {
    private LibIter() {
    }

    static JsObject makeProto(Realm r, String tag) {
        JsObject p = new JsObject(r.iteratorProto);
        Lib.method(r, p, "next", 0, (t, a) -> {
            if (!(t instanceof JsIterObject it)) {
                throw Js.typeError("next method called on incompatible receiver " + Js.display(t));
            }
            Object v = it.src.next();
            return v == IterRec.DONE ? Js.iterResult(Js.UNDEFINED, true) : Js.iterResult(v, false);
        });
        Lib.tag(p, tag);
        return p;
    }

    static void install(Realm r) {
        Lib.method(r, r.iteratorProto, JsSymbol.ITERATOR, "[Symbol.iterator]", 0, (t, a) -> t);
        Lib.method(r, r.arrayIteratorProto, "next", 0, (t, a) -> {
            if (!(t instanceof JsIterObject it)) {
                throw Js.typeError("next method called on incompatible receiver " + Js.display(t));
            }
            Object v = it.src.next();
            return v == IterRec.DONE ? Js.iterResult(Js.UNDEFINED, true) : Js.iterResult(v, false);
        });
        Lib.tag(r.arrayIteratorProto, "Array Iterator");
        JsObject gp = r.generatorProto;
        Lib.method(r, gp, "next", 1, (t, a) -> gen(t).resume(Coroutine.NEXT, Js.arg(a, 0)));
        Lib.method(r, gp, "return", 1, (t, a) -> gen(t).resume(Coroutine.RETURN, Js.arg(a, 0)));
        Lib.method(r, gp, "throw", 1, (t, a) -> gen(t).resume(Coroutine.THROW, Js.arg(a, 0)));
        Lib.tag(gp, "Generator");
        Lib.method(r, r.asyncIteratorProto, JsSymbol.ASYNC_ITERATOR, "[Symbol.asyncIterator]", 0, (t, a) -> t);
        JsObject ap = r.asyncGeneratorProto;
        Lib.method(r, ap, "next", 1, (t, a) -> asyncGen(r, t, "next").enqueue(Coroutine.NEXT, Js.arg(a, 0)));
        Lib.method(r, ap, "return", 1, (t, a) -> asyncGen(r, t, "return").enqueue(Coroutine.RETURN, Js.arg(a, 0)));
        Lib.method(r, ap, "throw", 1, (t, a) -> asyncGen(r, t, "throw").enqueue(Coroutine.THROW, Js.arg(a, 0)));
        Lib.tag(ap, "AsyncGenerator");
    }

    private static JsAsyncGenerator asyncGen(Realm r, Object t, String m) {
        if (t instanceof JsAsyncGenerator g) {
            return g;
        }
        throw Js.typeError(m + " method called on incompatible receiver " + Js.display(t));
    }

    private static JsGenerator gen(Object t) {
        if (t instanceof JsGenerator g) {
            return g;
        }
        throw Js.typeError("next method called on incompatible receiver " + Js.display(t));
    }
}
