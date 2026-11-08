package com.example.beautifuldecor.browser.js;

import java.util.ArrayDeque;

/** Un generador asíncrono ({@code async function*}). */
final class JsAsyncGenerator extends JsObject {
    private static final class Req {
        final int mode;
        final Object value;
        final JsPromise promise;

        Req(int mode, Object value, JsPromise promise) {
            this.mode = mode;
            this.value = value;
            this.promise = promise;
        }
    }

    private final Realm realm;
    private final Coroutine co;
    private final ArrayDeque<Req> queue = new ArrayDeque<>();
    private boolean running;

    JsAsyncGenerator(JsObject proto, Realm realm, Coroutine co) {
        super(proto);
        this.realm = realm;
        this.co = co;
    }

    @Override
    public String className() {
        return "AsyncGenerator";
    }

    Object enqueue(int mode, Object value) {
        JsPromise p = new JsPromise(this.realm);
        this.queue.add(new Req(mode, value, p));
        if (!this.running) {
            drain();
        }
        return p;
    }

    private void drain() {
        while (!this.running && !this.queue.isEmpty()) {
            Req rq = this.queue.peek();
            if (this.co.finished || (!this.co.started() && rq.mode != Coroutine.NEXT)) {
                if (!this.co.started() && !this.co.finished) {
                    this.co.abandon();
                }
                this.queue.poll();
                if (rq.mode == Coroutine.NEXT) {
                    rq.promise.resolve(Js.iterResult(Js.UNDEFINED, true));
                } else if (rq.mode == Coroutine.RETURN) {
                    JsPromise.from(this.realm, rq.value).thenJava(v -> rq.promise.resolve(Js.iterResult(v, true)), rq.promise::reject);
                } else {
                    rq.promise.reject(rq.value);
                }
                continue;
            }
            step(rq.mode, rq.value);
        }
    }

    private void step(int mode, Object v) {
        this.running = true;
        Object y;
        try {
            y = this.co.resume(mode, v);
        } catch (JsException e) {
            this.running = false;
            Req rq = this.queue.poll();
            if (rq != null) {
                rq.promise.reject(e.value);
            }
            return;
        }
        if (!this.co.finished && y instanceof AwaitMarker am) {
            JsPromise aw;
            try {
                aw = JsPromise.from(this.realm, am.value);
            } catch (JsException e) {
                step(Coroutine.THROW, e.value);
                return;
            }
            aw.thenJava(val -> {
                step(Coroutine.NEXT, val);
                drain();
            }, err -> {
                step(Coroutine.THROW, err);
                drain();
            });
            return;
        }
        this.running = false;
        Req rq = this.queue.poll();
        if (rq == null) {
            return;
        }
        if (this.co.finished) {
            rq.promise.resolve(Js.iterResult(y, true));
        } else {
            rq.promise.resolve(Js.iterResult(y, false));
        }
    }
}
