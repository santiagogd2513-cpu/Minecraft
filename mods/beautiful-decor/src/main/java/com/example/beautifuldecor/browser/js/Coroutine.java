package com.example.beautifuldecor.browser.js;

import java.util.concurrent.Semaphore;

/**
 * Una corrutina sobre un hilo: sirve para generadores y funciones async. Solo corre un hilo a la vez (el que reanuda espera a que
 * la corrutina ceda o termine), así el motor sigue siendo de un solo hilo lógico.
 */
final class Coroutine {
    static final int NEXT = 0;
    static final int THROW = 1;
    static final int RETURN = 2;
    private static final long STACK_BYTES = 24L * 1024 * 1024;

    interface Body {
        Object run(Coroutine co);
    }

    /** Se lanza dentro del generador al pedirle {@code return()}: sale ejecutando los finally. */
    static final class Return extends Error {
        private static final long serialVersionUID = 1L;
        final transient Object value;

        Return(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    /** Se lanza al cerrar el motor para que el hilo termine. */
    static final class Killed extends Error {
        private static final long serialVersionUID = 1L;

        Killed() {
            super(null, null, false, false);
        }
    }

    private final Realm realm;
    private final Body body;
    private final Semaphore resumeSem = new Semaphore(0);
    private final Semaphore yieldSem = new Semaphore(0);
    private boolean started;
    boolean finished;
    private int mode;
    private Object in = Js.UNDEFINED;
    private Object out = Js.UNDEFINED;
    private Throwable error;

    Coroutine(Realm realm, Body body) {
        this.realm = realm;
        this.body = body;
    }

    boolean started() {
        return this.started;
    }

    /** Marca como terminada una corrutina que nunca llegó a empezar. */
    void abandon() {
        this.finished = true;
    }

    /** Reanuda la corrutina. Devuelve lo que cede o devuelve; mira {@link #finished} para saber cuál de las dos. */
    Object resume(int m, Object value) {
        this.mode = m;
        this.in = value;
        Realm r = this.realm;
        int savedDepth = r.depth;
        if (!this.started) {
            this.started = true;
            Thread t = new Thread(null, this::threadMain, "bd-js-coroutine", STACK_BYTES);
            t.setDaemon(true);
            synchronized (r.coroutines) {
                r.coroutines.add(this);
            }
            t.start();
        } else {
            this.resumeSem.release();
        }
        this.yieldSem.acquireUninterruptibly();
        r.depth = savedDepth;
        if (this.finished) {
            synchronized (r.coroutines) {
                r.coroutines.remove(this);
            }
        }
        Throwable e = this.error;
        if (e != null) {
            this.error = null;
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw (Error) e;
        }
        return this.out;
    }

    private void threadMain() {
        Realm.CUR.set(this.realm);
        this.realm.depth = 0;
        try {
            if (this.realm.dead) {
                throw new Killed();
            }
            this.out = this.body.run(this);
        } catch (Return r) {
            this.out = r.value;
        } catch (Killed k) {
            this.out = Js.UNDEFINED;
        } catch (RuntimeException | Error e) {
            this.error = e;
        }
        this.finished = true;
        this.yieldSem.release();
    }

    /** Desde dentro de la corrutina: entrega un valor y espera a que la reanuden. Devuelve lo que se le envíe. */
    Object yield(Object v) {
        this.out = v;
        int myDepth = this.realm.depth;
        this.yieldSem.release();
        this.resumeSem.acquireUninterruptibly();
        this.realm.depth = myDepth;
        if (this.realm.dead) {
            throw new Killed();
        }
        switch (this.mode) {
            case THROW:
                throw new JsException(this.in);
            case RETURN:
                throw new Return(this.in);
            default:
                return this.in;
        }
    }

    /** Despierta al hilo para que termine (al cerrar el motor). */
    void kill() {
        if (this.started && !this.finished) {
            this.resumeSem.release();
        }
    }
}
