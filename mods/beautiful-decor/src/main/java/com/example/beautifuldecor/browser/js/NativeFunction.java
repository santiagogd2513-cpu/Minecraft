package com.example.beautifuldecor.browser.js;

/** Una función escrita en Java. */
public final class NativeFunction extends JsFunction {
    /** El cuerpo de la función: {@code thiz} es el this y {@code args} los argumentos reales. */
    @FunctionalInterface
    public interface Fn {
        Object call(Object thiz, Object[] args);
    }

    /** El cuerpo al usarla con {@code new}. */
    @FunctionalInterface
    public interface Ctor {
        Object construct(Object[] args, JsObject newTarget);
    }

    private final Fn fn;
    private final Ctor ctor;

    public NativeFunction(Realm realm, String name, int length, Fn fn) {
        this(realm, name, length, fn, null);
    }

    public NativeFunction(Realm realm, String name, int length, Fn fn, Ctor ctor) {
        super(realm.functionProto);
        this.fname = name;
        this.flen = length;
        this.fn = fn;
        this.ctor = ctor;
    }

    @Override
    public Object call(Object thiz, Object[] args) {
        Object r = this.fn.call(thiz, args);
        if (r instanceof Number n && !(r instanceof Double)) {
            return Double.valueOf(n.doubleValue());
        }
        return r;
    }

    @Override
    public Object construct(Object[] args, JsObject newTarget) {
        if (this.ctor == null) {
            return super.construct(args, newTarget);
        }
        return this.ctor.construct(args, newTarget);
    }

    @Override
    public boolean isConstructor() {
        return this.ctor != null;
    }
}
