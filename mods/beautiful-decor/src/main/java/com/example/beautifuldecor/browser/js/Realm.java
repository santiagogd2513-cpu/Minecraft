package com.example.beautifuldecor.browser.js;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** El mundo de un motor: objetos base, objeto global, cola de tareas y límites. */
public final class Realm {
    static final ThreadLocal<Realm> CUR = new ThreadLocal<>();

    /** Dónde van los mensajes de {@code console}. */
    public interface Console {
        void print(String level, String text);
    }

    public JsObject objectProto;
    public JsFunction functionProto;
    public JsObject arrayProto;
    public JsObject stringProto;
    public JsObject numberProto;
    public JsObject booleanProto;
    public JsObject symbolProto;
    public JsObject errorProto;
    public JsObject iteratorProto;
    public JsObject arrayIteratorProto;
    public JsObject generatorProto;
    public JsObject asyncIteratorProto;
    public JsObject asyncFunctionProto;
    public JsObject generatorFunctionProto;
    public JsObject asyncGeneratorFunctionProto;
    public JsObject asyncGeneratorProto;
    public JsObject promiseProto;
    public JsObject regexpProto;
    public JsObject dateProto;
    public JsObject mapProto;
    public JsObject setProto;
    public JsObject weakMapProto;
    public JsObject weakSetProto;
    public JsObject global;
    final HashMap<String, JsObject> errorProtos = new HashMap<>();
    final HashMap<String, JsSymbol> symbolRegistry = new HashMap<>();
    JsFunction arrayValues;
    JsFunction evalFn;
    JsFunction promiseCtor;
    Scope globalScope;
    Frame globalFrame;

    public Console console = (level, text) -> { };
    int depth;
    int maxDepth = 4000;
    private long ticks;
    private long deadlineNanos;
    volatile boolean dead;
    final HashSet<Coroutine> coroutines = new HashSet<>();
    final ArrayList<Object> joinStack = new ArrayList<>();
    private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
    private final ArrayList<JsPromise> rejections = new ArrayList<>();

    /** Crea el mundo. Debe llamarse con este hilo ya marcado como activo (lo hace {@link JsEngine}). */
    Realm() {
        Realm prev = CUR.get();
        CUR.set(this);
        try {
            setup();
        } finally {
            CUR.set(prev);
        }
    }

    private void setup() {
        this.objectProto = new JsObject(null);
        this.functionProto = new JsFunction(this.objectProto) {
            @Override
            public Object call(Object thiz, Object[] args) {
                return Js.UNDEFINED;
            }
        };
        this.arrayProto = new JsArray(this.objectProto);
        this.stringProto = new JsWrapper(this.objectProto, "");
        this.numberProto = new JsWrapper(this.objectProto, 0.0);
        this.booleanProto = new JsWrapper(this.objectProto, Boolean.FALSE);
        this.symbolProto = new JsObject(this.objectProto);
        this.errorProto = new JsObject(this.objectProto);
        this.iteratorProto = new JsObject(this.objectProto);
        this.arrayIteratorProto = new JsObject(this.iteratorProto);
        this.generatorProto = new JsObject(this.iteratorProto);
        this.asyncFunctionProto = new JsObject(this.functionProto);
        this.generatorFunctionProto = new JsObject(this.functionProto);
        this.asyncGeneratorFunctionProto = new JsObject(this.functionProto);
        this.asyncIteratorProto = new JsObject(this.objectProto);
        this.asyncGeneratorProto = new JsObject(this.asyncIteratorProto);
        this.promiseProto = new JsObject(this.objectProto);
        this.regexpProto = new JsObject(this.objectProto);
        this.dateProto = new JsObject(this.objectProto);
        this.mapProto = new JsObject(this.objectProto);
        this.setProto = new JsObject(this.objectProto);
        this.weakMapProto = new JsObject(this.objectProto);
        this.weakSetProto = new JsObject(this.objectProto);
        this.global = new JsObject(this.objectProto);
        this.globalFrame = new Frame();
        this.globalFrame.thisVal = this.global;
        this.globalScope = new Scope(null, this.globalFrame, this);
        LibObject.install(this);
        LibError.install(this);
        LibIter.install(this);
        LibArray.install(this);
        LibString.install(this);
        LibNumber.install(this);
        LibJson.install(this);
        LibRegExp.install(this);
        LibDate.install(this);
        LibCollections.install(this);
        LibPromise.install(this);
        LibGlobal.install(this);
    }

    final HashMap<String, JsObject> collectionIterProtos = new HashMap<>();
    // última coincidencia de una expresión regular (RegExp.$1, RegExp.lastMatch...)
    String reInput = "";
    String[] reGroups = new String[0];
    int reStart;
    int reEnd;
    private JsObject regexpStringIter;

    JsObject regexpStringIteratorProto() {
        if (this.regexpStringIter == null) {
            this.regexpStringIter = LibIter.makeProto(this, "RegExp String Iterator");
        }
        return this.regexpStringIter;
    }

    static Realm cur() {
        Realm r = CUR.get();
        if (r == null) {
            throw new IllegalStateException("No hay un motor de JavaScript activo en este hilo");
        }
        return r;
    }

    // ------------------------------------------------------------------ errores

    JsObject makeError(String type, String msg) {
        JsObject proto = this.errorProtos.getOrDefault(type, this.errorProto);
        JsError e = new JsError(proto);
        e.setHidden("message", msg);
        e.setHidden("stack", type + ": " + msg + "\n    at <anonymous>");
        return e;
    }

    JsException error(String type, String msg) {
        return new JsException(makeError(type, msg));
    }

    // ------------------------------------------------------------------ variables globales

    /** Para variables globales que no existen como propiedad (el DOM: un elemento con id es una variable global). */
    public interface GlobalFallback {
        /** El valor de la variable, o null si no existe. */
        Object lookup(String name);
    }

    public GlobalFallback globalFallback;

    Object globalGet(String name) {
        Prop p = this.global.findProp(name);
        if (p == null) {
            if (this.globalFallback != null) {
                Object v = this.globalFallback.lookup(name);
                if (v != null) {
                    return v;
                }
            }
            throw Js.referenceError(name + " is not defined");
        }
        if (p.accessor) {
            return p.getter == null ? Js.UNDEFINED : p.getter.call(this.global, Js.EMPTY);
        }
        return p.value;
    }

    // ------------------------------------------------------------------ ejecutar código

    Ast.Program parse(String src, boolean strict) {
        try {
            return Parser.parseProgram(src, strict);
        } catch (JsSyntaxError e) {
            throw error("SyntaxError", e.getMessage());
        }
    }

    /** Ejecuta un script en el ámbito global y devuelve el valor de su última sentencia. */
    Object runProgram(Ast.Program p) {
        for (String v : p.varNames) {
            if (this.global.getOwn(v) == null) {
                this.global.defineOwn(v, new Prop(Js.UNDEFINED, Prop.W | Prop.E));
            }
        }
        for (int i = 0; i < p.lexNames.length; i++) {
            this.globalScope.declareLex(p.lexNames[i], p.lexConst[i]);
        }
        for (Ast.FuncDecl f : p.funcs) {
            this.global.defineOwn(f.fn.name, new Prop(new Closure(this, f.fn, this.globalScope), Prop.W | Prop.E));
        }
        Frame f = this.globalFrame;
        Object saved = f.last;
        f.last = Js.UNDEFINED;
        try {
            Ast.run(p.body, this.globalScope);
            return f.last;
        } finally {
            f.last = saved;
        }
    }

    Object directEval(Object code, Scope s, boolean strict) {
        if (!(code instanceof String src)) {
            return code;
        }
        Ast.Program p = parse(src, strict);
        Scope es = new Scope(s, s.frame, this);
        for (String v : p.varNames) {
            es.declareVar(v);
        }
        Ast.hoistBlock(es, p.lexNames, p.lexConst, p.funcs);
        Object saved = s.frame.last;
        s.frame.last = Js.UNDEFINED;
        try {
            Ast.run(p.body, es);
            return s.frame.last;
        } finally {
            s.frame.last = saved;
        }
    }

    Object indirectEval(Object code) {
        if (!(code instanceof String src)) {
            return code;
        }
        return runProgram(parse(src, false));
    }

    // ------------------------------------------------------------------ tareas, límites

    void enqueue(Runnable job) {
        this.jobs.add(job);
    }

    void noteRejection(JsPromise p) {
        this.rejections.add(p);
    }

    /** Ejecuta las tareas pendientes (promesas) hasta que no quede ninguna. */
    public void runJobs() {
        Runnable r;
        while ((r = this.jobs.poll()) != null) {
            try {
                r.run();
            } catch (JsException e) {
                this.console.print("error", "Uncaught " + Js.describeError(e.value));
            }
        }
        if (!this.rejections.isEmpty()) {
            ArrayList<JsPromise> list = new ArrayList<>(this.rejections);
            this.rejections.clear();
            for (JsPromise p : list) {
                if (!p.handled) {
                    this.console.print("error", "Uncaught (in promise) " + Js.describeError(p.value));
                }
            }
        }
    }

    public boolean hasJobs() {
        return !this.jobs.isEmpty();
    }

    void tick() {
        if ((++this.ticks & 0x3FF) == 0) {
            checkLimits();
        }
    }

    void checkLimits() {
        if (this.dead) {
            throw new JsAbort("motor cerrado");
        }
        if (this.deadlineNanos != 0 && System.nanoTime() - this.deadlineNanos > 0) {
            throw new JsAbort("tiempo agotado");
        }
    }

    /** Fija un plazo (en milisegundos desde ahora) tras el cual el script en marcha se aborta; 0 lo quita. */
    public void setDeadline(long millis) {
        this.deadlineNanos = millis <= 0 ? 0 : System.nanoTime() + millis * 1_000_000L;
        if (millis > 0 && this.deadlineNanos == 0) {
            this.deadlineNanos = 1;
        }
    }

    public void setMaxDepth(int d) {
        this.maxDepth = d;
    }

    void close() {
        this.dead = true;
        ArrayList<Coroutine> list;
        synchronized (this.coroutines) {
            list = new ArrayList<>(this.coroutines);
        }
        for (Coroutine c : list) {
            c.kill();
        }
    }
}
