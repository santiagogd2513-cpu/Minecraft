package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Una función definida en JavaScript (función, flecha, método, constructor de clase...). */
final class Closure extends JsFunction {
    /** Un campo de clase de instancia. */
    static final class FieldDef {
        final Object key;
        final Ast.Expr init;
        final Scope scope;
        final JsObject home;

        FieldDef(Object key, Ast.Expr init, Scope scope, JsObject home) {
            this.key = key;
            this.init = init;
            this.scope = scope;
            this.home = home;
        }
    }

    final Ast.FuncNode node;
    final Scope scope;
    final Realm realm;
    JsObject home;
    ArrayList<FieldDef> fields;
    boolean protoDone;
    String classSource;

    Closure(Realm r, Ast.FuncNode node, Scope scope) {
        super(r.functionProto);
        this.realm = r;
        this.node = node;
        this.scope = scope;
        this.fname = node.name;
        this.flen = node.length;
        if (node.isAsync || node.isGen) {
            this.proto = node.isAsync && node.isGen ? r.asyncGeneratorFunctionProto
                    : (node.isAsync ? r.asyncFunctionProto : r.generatorFunctionProto);
        }
    }

    static String keyName(Object key) {
        if (key instanceof JsSymbol sym) {
            return sym.description == null ? "" : "[" + sym.description + "]";
        }
        return String.valueOf(key);
    }

    static void mergeAccessor(JsObject o, Object key, JsFunction getter, JsFunction setter, int flags) {
        Prop old = o.getOwn(key);
        if (old != null && old.accessor) {
            o.defineOwn(key, new Prop(getter != null ? getter : old.getter, setter != null ? setter : old.setter, flags));
        } else {
            o.defineOwn(key, new Prop(getter, setter, flags));
        }
    }

    void addField(Object key, Ast.Expr init, Scope cs, JsObject protoHome) {
        if (this.fields == null) {
            this.fields = new ArrayList<>();
        }
        this.fields.add(new FieldDef(key, init, cs, protoHome));
    }

    void initFields(JsObject obj) {
        for (FieldDef d : this.fields) {
            Object v = Js.UNDEFINED;
            if (d.init != null) {
                Frame ff = new Frame();
                ff.thisVal = obj;
                ff.home = d.home;
                v = d.init.eval(new Scope(d.scope, ff, this.realm));
            }
            obj.defineOwn(d.key, new Prop(v, d.key instanceof PrivateName ? Prop.W : Prop.ALL));
        }
    }

    String sourceText() {
        if (this.classSource != null) {
            return this.classSource;
        }
        if (this.node.source != null && this.node.end > this.node.start) {
            return this.node.source.substring(this.node.start, this.node.end);
        }
        return "function " + this.fname + "() { [native code] }";
    }

    @Override
    public boolean isConstructor() {
        Ast.FuncNode n = this.node;
        return !n.isArrow && !n.isMethod && !n.isGen && !n.isAsync;
    }

    @Override
    public Prop getOwn(Object key) {
        Prop p = super.getOwn(key);
        if (p == null && !this.protoDone && "prototype".equals(key) && (isConstructor() || this.node.isGen)) {
            this.protoDone = true;
            JsObject proto = new JsObject(this.node.isGen ? (this.node.isAsync ? this.realm.asyncGeneratorProto : this.realm.generatorProto)
                    : this.realm.objectProto);
            if (!this.node.isGen) {
                proto.setHidden("constructor", this);
            }
            defineOwn("prototype", new Prop(proto, Prop.W));
            p = super.getOwn(key);
        }
        return p;
    }

    @Override
    public List<Object> ownKeys() {
        getOwn("prototype");
        return super.ownKeys();
    }

    // ------------------------------------------------------------------ llamadas

    private Frame newFrame(Object thiz, Object[] args, Object newTarget) {
        Frame f = new Frame();
        f.func = this;
        f.args = args;
        f.home = this.home;
        if (this.node.isArrow) {
            f.thisFrame = this.scope.frame.thisFrame;
        } else {
            f.newTarget = newTarget;
            if (!this.node.strict) {
                if (thiz == Js.UNDEFINED || thiz == Js.NULL) {
                    thiz = this.realm.global;
                } else if (!(thiz instanceof JsObject)) {
                    thiz = Js.toObject(this.realm, thiz);
                }
            }
            f.thisVal = thiz;
        }
        return f;
    }

    private Scope prepare(Frame f, Object[] args) {
        Ast.FuncNode n = this.node;
        Scope sc = new Scope(this.scope, f, this.realm);
        if (n.usesArgs && !n.isArrow) {
            ArrayList<Object> l = new ArrayList<>(args.length);
            for (Object a : args) {
                l.add(a);
            }
            JsArguments ao = new JsArguments(this.realm.objectProto, l);
            ao.setHidden(JsSymbol.ITERATOR, this.realm.arrayValues);
            sc.declare("arguments", ao);
        }
        String[] sp = n.simpleParams;
        if (sp != null) {
            for (int i = 0; i < sp.length; i++) {
                sc.declare(sp[i], i < args.length ? args[i] : Js.UNDEFINED);
            }
        } else {
            for (int i = 0; i < n.params.length; i++) {
                Object v;
                if (i == n.restIndex) {
                    ArrayList<Object> rest = new ArrayList<>();
                    for (int j = i; j < args.length; j++) {
                        rest.add(args[j]);
                    }
                    v = new JsArray(this.realm.arrayProto, rest);
                } else {
                    v = i < args.length ? args[i] : Js.UNDEFINED;
                    if (v == Js.UNDEFINED && n.defaults[i] != null) {
                        v = n.defaults[i].eval(sc);
                    }
                }
                n.params[i].assign(sc, v, Ast.M_PARAM);
            }
        }
        for (String v : n.varNames) {
            sc.declareVar(v);
        }
        Ast.hoistBlock(sc, n.lexNames, n.lexConst, n.funcs);
        return sc;
    }

    private Object body(Scope sc, Frame f) {
        if (this.node.exprBody != null) {
            return this.node.exprBody.eval(sc);
        }
        int c = Ast.run(this.node.body, sc);
        return c == Ast.RETURN ? f.ret : Js.UNDEFINED;
    }

    @Override
    public Object call(Object thiz, Object[] args) {
        Ast.FuncNode n = this.node;
        if (n.isClassCtor) {
            throw Js.typeError("Class constructor " + this.fname + " cannot be invoked without 'new'");
        }
        Realm r = this.realm;
        if (n.isGen) {
            Frame f = newFrame(thiz, args, Js.UNDEFINED);
            Scope sc = prepare(f, args);
            Object pr = get("prototype");
            if (n.isAsync) {
                f.asyncGen = true;
                Coroutine aco = new Coroutine(r, c -> {
                    f.co = c;
                    return body(sc, f);
                });
                return new JsAsyncGenerator(pr instanceof JsObject po ? po : r.asyncGeneratorProto, r, aco);
            }
            Coroutine co = new Coroutine(r, c -> {
                f.co = c;
                return body(sc, f);
            });
            return new JsGenerator(pr instanceof JsObject po ? po : r.generatorProto, co);
        }
        if (++r.depth > r.maxDepth) {
            r.depth--;
            throw Js.rangeError("Maximum call stack size exceeded");
        }
        try {
            r.tick();
            if (n.isAsync) {
                return startAsync(thiz, args);
            }
            Frame f = newFrame(thiz, args, Js.UNDEFINED);
            return body(prepare(f, args), f);
        } catch (StackOverflowError e) {
            throw Js.rangeError("Maximum call stack size exceeded");
        } finally {
            r.depth--;
        }
    }

    private Object startAsync(Object thiz, Object[] args) {
        JsPromise p = new JsPromise(this.realm);
        try {
            Frame f = newFrame(thiz, args, Js.UNDEFINED);
            Scope sc = prepare(f, args);
            if (!this.node.hasAwait) {
                p.resolve(body(sc, f));
            } else {
                Coroutine co = new Coroutine(this.realm, c -> {
                    f.co = c;
                    return body(sc, f);
                });
                JsPromise.drive(this.realm, co, p, Coroutine.NEXT, Js.UNDEFINED);
            }
        } catch (JsException e) {
            p.reject(e.value);
        }
        return p;
    }

    @Override
    public Object construct(Object[] args, JsObject newTarget) {
        if (!isConstructor()) {
            return super.construct(args, newTarget);
        }
        Realm r = this.realm;
        if (++r.depth > r.maxDepth) {
            r.depth--;
            throw Js.rangeError("Maximum call stack size exceeded");
        }
        try {
            r.tick();
            Frame f = new Frame();
            f.func = this;
            f.args = args;
            f.home = this.home;
            f.newTarget = newTarget;
            if (this.node.isDerived) {
                f.thisInit = false;
            } else {
                Object p = newTarget.get("prototype");
                JsObject obj = new JsObject(p instanceof JsObject po ? po : r.objectProto);
                f.thisVal = obj;
                if (this.fields != null) {
                    initFields(obj);
                }
            }
            Object ret = body(prepare(f, args), f);
            if (ret instanceof JsObject) {
                return ret;
            }
            if (!f.thisInit) {
                throw Js.referenceError("Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
            }
            return f.thisVal;
        } catch (StackOverflowError e) {
            throw Js.rangeError("Maximum call stack size exceeded");
        } finally {
            r.depth--;
        }
    }
}
