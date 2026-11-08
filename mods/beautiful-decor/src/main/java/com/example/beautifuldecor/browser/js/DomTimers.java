package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Temporizadores de la página (V2.9.0): setTimeout, setInterval, requestAnimationFrame y requestIdleCallback. Cada uno es una
 * tarea en la cola del hilo de los scripts (DomHost.Env.schedule), así que nunca corren dos a la vez ni a la mitad de un clic.
 */
final class DomTimers {
    /** Tope de temporizadores vivos a la vez, para que una página descontrolada no llene la memoria. */
    private static final int MAX_TIMERS = 5000;
    /** Los intervalos no bajan de 4 ms (como en los navegadores cuando están anidados). */
    private static final long MIN_INTERVAL = 4;
    private static final long FRAME_MS = 16;

    private DomTimers() {
    }

    static final class Timer {
        final int id;
        final boolean repeat;
        final long delay;
        final int level;
        final Object handler;
        final Object[] args;

        Timer(int id, boolean repeat, long delay, int level, Object handler, Object[] args) {
            this.id = id;
            this.repeat = repeat;
            this.delay = delay;
            this.level = level;
            this.handler = handler;
            this.args = args;
        }
    }

    static final class State {
        int nextTimer = 1;
        int nextFrame = 1;
        int nextIdle = 1;
        int nesting;
        boolean frameQueued;
        boolean warned;
        final HashMap<Integer, Timer> timers = new HashMap<>();
        final LinkedHashMap<Integer, JsFunction> frames = new LinkedHashMap<>();
        final HashMap<Integer, JsFunction> idles = new HashMap<>();
    }

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject wp = h.windowProto;
        for (boolean repeat : new boolean[] {false, true}) {
            String name = repeat ? "setInterval" : "setTimeout";
            Lib.method(r, wp, name, 1, (t, a) -> {
                Object handler = Js.arg(a, 0);
                if (!(handler instanceof JsFunction)) {
                    handler = handler == Js.UNDEFINED ? "" : Js.toStr(handler);       // el texto se ejecuta como código
                }
                double d = Js.toNumber(Js.arg(a, 1));
                long delay = Double.isNaN(d) || d < 0 ? 0 : (long) Math.min(d, 2_147_483_647.0);
                Object[] extra = a.length > 2 ? java.util.Arrays.copyOfRange(a, 2, a.length) : new Object[0];
                return Js.num(start(h, repeat, delay, handler, extra));
            });
        }
        for (String m : new String[] {"clearTimeout", "clearInterval"}) {
            Lib.method(r, wp, m, 1, (t, a) -> {
                double d = Js.toNumber(Js.arg(a, 0));
                if (!Double.isNaN(d)) {
                    h.timers.timers.remove((int) d);
                }
                return Js.UNDEFINED;
            });
        }

        Lib.method(r, wp, "requestAnimationFrame", 1, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsFunction f)) {
                throw Js.typeError("Failed to execute 'requestAnimationFrame' on 'Window': The callback provided as parameter 1 is not a function.");
            }
            State s = h.timers;
            if (s.frames.size() >= MAX_TIMERS) {
                return Js.num(0);
            }
            int id = s.nextFrame++;
            s.frames.put(id, f);
            queueFrame(h);
            return Js.num(id);
        });
        Lib.method(r, wp, "cancelAnimationFrame", 1, (t, a) -> {
            double d = Js.toNumber(Js.arg(a, 0));
            if (!Double.isNaN(d)) {
                h.timers.frames.remove((int) d);
            }
            return Js.UNDEFINED;
        });

        Lib.method(r, wp, "requestIdleCallback", 1, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsFunction f)) {
                throw Js.typeError("Failed to execute 'requestIdleCallback' on 'Window': The callback provided as parameter 1 is not a function.");
            }
            State s = h.timers;
            if (s.idles.size() >= MAX_TIMERS) {
                return Js.num(0);
            }
            int id = s.nextIdle++;
            s.idles.put(id, f);
            double queued = h.now();
            h.env.schedule(() -> {
                JsFunction cb = s.idles.remove(id);
                if (cb == null) {
                    return;
                }
                JsObject deadline = new JsObject(r.objectProto);
                deadline.setHidden("didTimeout", false);
                Lib.method(r, deadline, "timeRemaining", 0, (x, b) -> Js.num(Math.max(0, 50 - (h.now() - queued))));
                runCallback(h, cb, new Object[] {deadline});
            }, 1);
            return Js.num(id);
        });
        Lib.method(r, wp, "cancelIdleCallback", 1, (t, a) -> {
            double d = Js.toNumber(Js.arg(a, 0));
            if (!Double.isNaN(d)) {
                h.timers.idles.remove((int) d);
            }
            return Js.UNDEFINED;
        });
    }

    // ------------------------------------------------------------------ setTimeout / setInterval

    private static int start(DomHost h, boolean repeat, long delay, Object handler, Object[] args) {
        State s = h.timers;
        if (s.timers.size() >= MAX_TIMERS) {
            if (!s.warned) {
                s.warned = true;
                h.env.console("error", "Too many timers: the page asked for more than " + MAX_TIMERS + " at once.");
            }
            return 0;
        }
        int level = s.nesting + 1;
        long d = delay;
        if (level > 5 || repeat) {
            d = Math.max(d, MIN_INTERVAL);
        }
        Timer timer = new Timer(s.nextTimer++, repeat, d, level, handler, args);
        s.timers.put(timer.id, timer);
        arm(h, timer, d);
        return timer.id;
    }

    private static void arm(DomHost h, Timer timer, long delay) {
        if (!h.env.schedule(() -> fire(h, timer), delay)) {
            h.timers.timers.remove(timer.id);
        }
    }

    private static void fire(DomHost h, Timer timer) {
        State s = h.timers;
        if (s.timers.get(timer.id) != timer) {
            return;                                                     // se canceló
        }
        if (!timer.repeat) {
            s.timers.remove(timer.id);
        }
        int saved = s.nesting;
        s.nesting = timer.level;
        try {
            if (timer.handler instanceof JsFunction f) {
                f.call(h.r.global, timer.args);
            } else {
                h.engine.eval((String) timer.handler);
            }
        } catch (JsException e) {
            h.reportError(e);
        } catch (JsSyntaxError e) {
            h.env.console("error", "Uncaught SyntaxError: " + e.getMessage());
        } catch (JsAbort a) {
            s.timers.remove(timer.id);                                 // se pasó de tiempo: ese intervalo no sigue
            throw a;
        } finally {
            s.nesting = saved;
        }
        h.engine.runJobs();
        if (timer.repeat && s.timers.get(timer.id) == timer) {
            arm(h, timer, timer.delay);
        }
    }

    // ------------------------------------------------------------------ requestAnimationFrame

    private static void queueFrame(DomHost h) {
        State s = h.timers;
        if (s.frameQueued) {
            return;
        }
        s.frameQueued = true;
        if (!h.env.schedule(() -> runFrame(h), FRAME_MS)) {
            s.frameQueued = false;
            s.frames.clear();
        }
    }

    private static void runFrame(DomHost h) {
        State s = h.timers;
        s.frameQueued = false;
        List<Integer> ids = new ArrayList<>(s.frames.keySet());       // los que se pidan durante el cuadro esperan al siguiente
        double stamp = h.now();
        for (int id : ids) {
            JsFunction cb = s.frames.remove(id);
            if (cb != null) {
                runCallback(h, cb, new Object[] {Js.num(stamp)});
            }
        }
    }

    private static void runCallback(DomHost h, JsFunction cb, Object[] args) {
        try {
            cb.call(h.r.global, args);
        } catch (JsException e) {
            h.reportError(e);
        }
        h.engine.runJobs();
    }
}
