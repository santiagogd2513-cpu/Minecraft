package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Una promesa. */
public final class JsPromise extends JsObject {
    static final int PENDING = 0;
    static final int FULFILLED = 1;
    static final int REJECTED = 2;

    private final Realm realm;
    int state;
    Object value = Js.UNDEFINED;
    boolean handled;
    private boolean locked;
    private ArrayList<Consumer<JsPromise>> reactions;

    JsPromise(Realm realm) {
        super(realm.promiseProto);
        this.realm = realm;
    }

    JsPromise(Realm realm, JsObject proto) {
        super(proto);
        this.realm = realm;
    }

    @Override
    public String className() {
        return "Promise";
    }

    public int state() {
        return this.state;
    }

    public Object result() {
        return this.value;
    }

    /** Resuelve la promesa con un valor (si es otra promesa o "thenable", la sigue). */
    public void resolve(Object v) {
        if (this.locked) {
            return;
        }
        this.locked = true;
        adopt(v);
    }

    public void reject(Object reason) {
        if (this.locked) {
            return;
        }
        this.locked = true;
        settle(REJECTED, reason);
    }

    private void adopt(Object v) {
        if (v == this) {
            settle(REJECTED, this.realm.error("TypeError", "Chaining cycle detected for promise #<Promise>").value);
            return;
        }
        if (v instanceof JsObject o) {
            Object then;
            try {
                then = o.get("then");
            } catch (JsException e) {
                settle(REJECTED, e.value);
                return;
            }
            if (then instanceof JsFunction tf) {
                this.realm.enqueue(() -> {
                    boolean[] done = {false};
                    NativeFunction res = new NativeFunction(this.realm, "", 1, (t, a) -> {
                        if (!done[0]) {
                            done[0] = true;
                            adopt(Js.arg(a, 0));
                        }
                        return Js.UNDEFINED;
                    });
                    NativeFunction rej = new NativeFunction(this.realm, "", 1, (t, a) -> {
                        if (!done[0]) {
                            done[0] = true;
                            settle(REJECTED, Js.arg(a, 0));
                        }
                        return Js.UNDEFINED;
                    });
                    try {
                        tf.call(o, new Object[] {res, rej});
                    } catch (JsException e) {
                        if (!done[0]) {
                            done[0] = true;
                            settle(REJECTED, e.value);
                        }
                    }
                });
                return;
            }
        }
        settle(FULFILLED, v);
    }

    private void settle(int st, Object v) {
        if (this.state != PENDING) {
            return;
        }
        this.state = st;
        this.value = v;
        if (st == REJECTED && !this.handled) {
            this.realm.noteRejection(this);
        }
        ArrayList<Consumer<JsPromise>> rs = this.reactions;
        this.reactions = null;
        if (rs != null) {
            for (Consumer<JsPromise> r : rs) {
                this.realm.enqueue(() -> r.accept(this));
            }
        }
    }

    /** Registra una reacción que corre (como tarea) cuando la promesa se cumple o se rechaza. */
    void whenSettled(Consumer<JsPromise> reaction) {
        this.handled = true;
        if (this.state != PENDING) {
            this.realm.enqueue(() -> reaction.accept(this));
            return;
        }
        if (this.reactions == null) {
            this.reactions = new ArrayList<>();
        }
        this.reactions.add(reaction);
    }

    /** Equivale a {@code then(onFulfilled, onRejected)} con funciones de Java. */
    void thenJava(Consumer<Object> onFulfilled, Consumer<Object> onRejected) {
        whenSettled(p -> {
            if (p.state == FULFILLED) {
                onFulfilled.accept(p.value);
            } else {
                onRejected.accept(p.value);
            }
        });
    }

    /** {@code promise.then(a, b)} con funciones de JavaScript (o cualquier otra cosa, que se ignora). */
    JsPromise then(Object onFulfilled, Object onRejected) {
        JsPromise next = new JsPromise(this.realm);
        whenSettled(p -> {
            Object h = p.state == FULFILLED ? onFulfilled : onRejected;
            if (h instanceof JsFunction hf) {
                try {
                    next.resolve(hf.call(Js.UNDEFINED, new Object[] {p.value}));
                } catch (JsException e) {
                    next.reject(e.value);
                }
            } else if (p.state == FULFILLED) {
                next.resolve(p.value);
            } else {
                next.reject(p.value);
            }
        });
        return next;
    }

    /** PromiseResolve: una promesa que vale lo mismo que {@code v}. */
    static JsPromise from(Realm r, Object v) {
        if (v instanceof JsPromise p) {
            return p;
        }
        JsPromise p = new JsPromise(r);
        p.resolve(v);
        return p;
    }

    /** Avanza una función async: reanuda la corrutina y espera lo que ceda. */
    static void drive(Realm r, Coroutine co, JsPromise result, int mode, Object val) {
        Object y;
        try {
            y = co.resume(mode, val);
        } catch (JsException e) {
            result.reject(e.value);
            return;
        }
        if (co.finished) {
            result.resolve(y);
            return;
        }
        JsPromise awaited;
        try {
            awaited = from(r, y);
        } catch (JsException e) {
            drive(r, co, result, Coroutine.THROW, e.value);
            return;
        }
        awaited.thenJava(v -> drive(r, co, result, Coroutine.NEXT, v), e -> drive(r, co, result, Coroutine.THROW, e));
    }
}
