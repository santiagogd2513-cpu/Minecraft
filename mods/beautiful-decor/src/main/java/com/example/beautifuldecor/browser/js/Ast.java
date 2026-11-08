package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** El árbol del programa y su evaluación. */
final class Ast {
    static final int NORMAL = 0;
    static final int BREAK = 1;
    static final int CONTINUE = 2;
    static final int RETURN = 3;

    static final int M_ASSIGN = 0;
    static final int M_VAR = 1;
    static final int M_LET = 2;
    static final int M_CONST = 3;
    static final int M_PARAM = 4;

    /** Resultado interno de una cadena opcional (a?.b) que se cortó. */
    static final Object SHORT = new Object();
    static final String[] NO_NAMES = new String[0];
    static final Expr[] NO_EXPRS = new Expr[0];
    static final Stmt[] NO_STMTS = new Stmt[0];

    private Ast() {
    }

    abstract static class Expr {
        int line;

        abstract Object eval(Scope s);
    }

    abstract static class Stmt {
        int line;

        abstract int exec(Scope s);
    }

    /** Algo a lo que se puede asignar: variable, propiedad o patrón de desestructuración. */
    interface Target {
        void assign(Scope s, Object v, int mode);

        void names(List<String> out);
    }

    // ================================================================== datos de funciones y programas

    static final class FuncNode {
        String name = "";
        /** Nombre de la propiedad/variable a la que se asignó (para funciones anónimas). */
        boolean named;
        Target[] params = new Target[0];
        Expr[] defaults = NO_EXPRS;
        String[] simpleParams;
        int restIndex = -1;
        int length;
        Stmt[] body = NO_STMTS;
        Expr exprBody;
        String[] varNames = NO_NAMES;
        FuncDecl[] funcs = new FuncDecl[0];
        String[] lexNames = NO_NAMES;
        boolean[] lexConst = new boolean[0];
        boolean isArrow;
        boolean isAsync;
        boolean isGen;
        boolean isMethod;
        boolean isClassCtor;
        boolean isDerived;
        boolean strict;
        boolean usesArgs;
        boolean hasAwait;
        String source;
        int start;
        int end;
    }

    static final class Program {
        Stmt[] body = NO_STMTS;
        String[] varNames = NO_NAMES;
        FuncDecl[] funcs = new FuncDecl[0];
        String[] lexNames = NO_NAMES;
        boolean[] lexConst = new boolean[0];
        boolean strict;
    }

    // ================================================================== expresiones simples

    /** Cómo nombra V8 una expresión en mensajes como "x is not a function". */
    static String describe(Expr e) {
        if (e instanceof Ident id) {
            return id.name;
        }
        if (e instanceof This) {
            return "this";
        }
        if (e instanceof Lit l) {
            if (l.v instanceof String str) {
                return "\"" + str + "\"";
            }
            if (l.v instanceof Double || l.v instanceof Boolean || l.v == Js.NULL || l.v == Js.UNDEFINED) {
                return Js.toStr(l.v);
            }
            return "(intermediate value)";
        }
        if (e instanceof OptChain oc) {
            return describe(oc.inner);
        }
        if (e instanceof Member m) {
            String base = describe(m.obj);
            String q = m.optional ? "?." : ".";
            if (m.keyConst != null) {
                return base + q + m.keyConst;
            }
            if (m.key instanceof Lit kl && kl.v instanceof String ks) {
                return base + q + ks;
            }
            return base + (m.optional ? "?.[" : "[") + describe(m.key) + "]";
        }
        if (e instanceof Call c) {
            return describe(c.callee) + "(...)";
        }
        if (e instanceof ObjectLit ol && ol.props.length == 0) {
            return "{}";
        }
        if (e instanceof Unary u && u.op == U_VOID) {
            return "(void " + describe(u.e) + ")";
        }
        return "(intermediate value)";
    }

    static final class Lit extends Expr {
        final Object v;

        Lit(Object v) {
            this.v = v;
        }

        @Override
        Object eval(Scope s) {
            return this.v;
        }
    }

    static final class TemplateLit extends Expr {
        final String[] strs;
        final Expr[] exprs;

        TemplateLit(String[] strs, Expr[] exprs) {
            this.strs = strs;
            this.exprs = exprs;
        }

        @Override
        Object eval(Scope s) {
            StringBuilder sb = new StringBuilder(this.strs[0]);
            for (int i = 0; i < this.exprs.length; i++) {
                sb.append(Js.toStr(this.exprs[i].eval(s)));
                sb.append(this.strs[i + 1]);
            }
            return sb.toString();
        }
    }

    static final class TaggedTemplate extends Expr {
        final Expr tag;
        final String[] cooked;
        final String[] raws;
        final Expr[] exprs;
        private JsArray cache;

        TaggedTemplate(Expr tag, String[] cooked, String[] raws, Expr[] exprs) {
            this.tag = tag;
            this.cooked = cooked;
            this.raws = raws;
            this.exprs = exprs;
        }

        @Override
        Object eval(Scope s) {
            Object thiz = Js.UNDEFINED;
            Object f;
            if (this.tag instanceof Member m) {
                Object o = m.obj.eval(s);
                f = Js.getMember(s.realm, o, m.keyConst != null ? m.keyConst : m.key.eval(s));
                thiz = o;
            } else {
                f = this.tag.eval(s);
            }
            if (this.cache == null) {
                JsArray strs = new JsArray(s.realm.arrayProto);
                JsArray raw = new JsArray(s.realm.arrayProto);
                for (int i = 0; i < this.cooked.length; i++) {
                    strs.add(this.cooked[i] == null ? Js.UNDEFINED : this.cooked[i]);
                    raw.add(this.raws[i]);
                }
                raw.frozen = true;
                raw.extensible = false;
                strs.setConst("raw", raw);
                strs.frozen = true;
                strs.extensible = false;
                this.cache = strs;
            }
            Object[] args = new Object[this.exprs.length + 1];
            args[0] = this.cache;
            for (int i = 0; i < this.exprs.length; i++) {
                args[i + 1] = this.exprs[i].eval(s);
            }
            if (!(f instanceof JsFunction fn)) {
                throw Js.typeError("tag is not a function");
            }
            return fn.call(thiz, args);
        }
    }

    static final class RegexLit extends Expr {
        final String pattern;
        final String flags;

        RegexLit(String pattern, String flags) {
            this.pattern = pattern;
            this.flags = flags;
        }

        @Override
        Object eval(Scope s) {
            return LibRegExp.create(s.realm, this.pattern, this.flags);
        }
    }

    static final class Ident extends Expr implements Target {
        final String name;
        boolean strict;

        Ident(String name) {
            this.name = name;
        }

        @Override
        Object eval(Scope s) {
            Scope.Binding b = s.find(this.name);
            if (b != null) {
                if (!b.init) {
                    throw Js.referenceError("Cannot access '" + this.name + "' before initialization");
                }
                return b.value;
            }
            return s.realm.globalGet(this.name);
        }

        Object evalTypeof(Scope s) {
            Scope.Binding b = s.find(this.name);
            if (b != null) {
                if (!b.init) {
                    throw Js.referenceError("Cannot access '" + this.name + "' before initialization");
                }
                return b.value;
            }
            Prop p = s.realm.global.findProp(this.name);
            if (p == null) {
                return Js.UNDEFINED;
            }
            return p.accessor ? (p.getter == null ? Js.UNDEFINED : p.getter.call(s.realm.global, Js.EMPTY)) : p.value;
        }

        @Override
        public void assign(Scope s, Object v, int mode) {
            switch (mode) {
                case M_LET:
                case M_CONST: {
                    Scope.Binding b = s.vars == null ? null : s.vars.get(this.name);
                    if (b == null) {
                        b = s.declare(this.name, v);
                        b.mutable = mode == M_LET;
                    } else {
                        b.value = v;
                        b.init = true;
                    }
                    return;
                }
                case M_PARAM:
                    s.declare(this.name, v);
                    return;
                default:
                    set(s, v);
            }
        }

        void set(Scope s, Object v) {
            Scope.Binding b = s.find(this.name);
            if (b != null) {
                if (!b.init) {
                    throw Js.referenceError("Cannot access '" + this.name + "' before initialization");
                }
                if (!b.mutable) {
                    throw Js.typeError("Assignment to constant variable.");
                }
                b.value = v;
                return;
            }
            Realm r = s.realm;
            if (this.strict && !r.global.hasProperty(this.name)) {
                throw Js.referenceError(this.name + " is not defined");
            }
            if (!r.global.set(this.name, v, r.global) && this.strict) {
                throw Js.typeError("Cannot assign to read only property '" + this.name + "' of object '#<Window>'");
            }
        }

        @Override
        public void names(List<String> out) {
            out.add(this.name);
        }
    }

    static final class This extends Expr {
        @Override
        Object eval(Scope s) {
            Frame tf = s.frame.thisFrame;
            if (!tf.thisInit) {
                throw Js.referenceError("Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
            }
            return tf.thisVal;
        }
    }

    static final class NewTarget extends Expr {
        @Override
        Object eval(Scope s) {
            return s.frame.thisFrame.newTarget;
        }
    }

    static final class Spread extends Expr {
        final Expr e;

        Spread(Expr e) {
            this.e = e;
        }

        @Override
        Object eval(Scope s) {
            throw Js.syntaxError("Unexpected token '...'");
        }
    }

    /** Fin de una cadena opcional: si algún eslabón se cortó, todo vale undefined. */
    static final class OptChain extends Expr {
        final Expr inner;

        OptChain(Expr inner) {
            this.inner = inner;
        }

        @Override
        Object eval(Scope s) {
            Object r = this.inner.eval(s);
            return r == SHORT ? Js.UNDEFINED : r;
        }
    }

    static final class Member extends Expr implements Target {
        final Expr obj;
        /** Nombre fijo (String o PrivateName) o null si la clave se calcula. */
        final Object keyConst;
        final Expr key;
        final boolean optional;
        boolean strict;

        Member(Expr obj, Object keyConst, Expr key, boolean optional) {
            this.obj = obj;
            this.keyConst = keyConst;
            this.key = key;
            this.optional = optional;
        }

        @Override
        Object eval(Scope s) {
            Object o = this.obj.eval(s);
            if (o == SHORT) {
                return SHORT;
            }
            if (this.optional && (o == Js.UNDEFINED || o == Js.NULL)) {
                return SHORT;
            }
            if (this.keyConst != null) {
                if (o instanceof JsObject jo && !(this.keyConst instanceof PrivateName)) {
                    return jo.get(this.keyConst, jo);
                }
                return Js.getMember(s.realm, o, this.keyConst);
            }
            Object k = this.key.eval(s);
            return Js.getMember(s.realm, o, k);
        }

        @Override
        public void assign(Scope s, Object v, int mode) {
            Object o = this.obj.eval(s);
            Js.setMember(s.realm, o, this.keyConst != null ? this.keyConst : this.key.eval(s), v, this.strict);
        }

        @Override
        public void names(List<String> out) {
        }
    }

    static final class SuperMember extends Expr implements Target {
        final Object keyConst;
        final Expr key;
        boolean strict;

        SuperMember(Object keyConst, Expr key) {
            this.keyConst = keyConst;
            this.key = key;
        }

        JsObject base(Scope s) {
            JsObject home = s.frame.thisFrame.home;
            if (home == null) {
                throw Js.syntaxError("'super' keyword unexpected here");
            }
            return home.proto;
        }

        Object keyOf(Scope s) {
            return this.keyConst != null ? this.keyConst : Js.toKey(this.key.eval(s));
        }

        @Override
        Object eval(Scope s) {
            JsObject b = base(s);
            Object k = keyOf(s);
            return b == null ? Js.UNDEFINED : b.get(k, s.frame.thisFrame.thisVal);
        }

        @Override
        public void assign(Scope s, Object v, int mode) {
            Js.setMember(s.realm, s.frame.thisFrame.thisVal, keyOf(s), v, this.strict);
        }

        @Override
        public void names(List<String> out) {
        }
    }

    static Object[] evalArgs(Scope s, Expr[] args, boolean spread) {
        if (!spread) {
            if (args.length == 0) {
                return Js.EMPTY;
            }
            Object[] out = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                out[i] = args[i].eval(s);
            }
            return out;
        }
        ArrayList<Object> l = new ArrayList<>();
        for (Expr a : args) {
            if (a instanceof Spread sp) {
                IterRec it = Js.iterate(sp.e.eval(s), describe(sp.e));
                for (Object v = it.next(); v != IterRec.DONE; v = it.next()) {
                    l.add(v);
                }
            } else {
                l.add(a.eval(s));
            }
        }
        return l.toArray();
    }

    static final class Call extends Expr {
        final Expr callee;
        final Expr[] args;
        final boolean spread;
        final boolean optional;
        final String text;
        boolean strict;
        final boolean maybeEval;

        Call(Expr callee, Expr[] args, boolean spread, boolean optional, String text) {
            this.callee = callee;
            this.args = args;
            this.spread = spread;
            this.optional = optional;
            this.text = text;
            this.maybeEval = callee instanceof Ident id && id.name.equals("eval");
        }

        @Override
        Object eval(Scope s) {
            Object thiz = Js.UNDEFINED;
            Object f;
            Expr c = this.callee;
            if (c instanceof Member m) {
                Object o = m.obj.eval(s);
                if (o == SHORT) {
                    return SHORT;
                }
                if (m.optional && (o == Js.UNDEFINED || o == Js.NULL)) {
                    return SHORT;
                }
                if (m.keyConst != null) {
                    f = o instanceof JsObject jo && !(m.keyConst instanceof PrivateName) ? jo.get(m.keyConst, jo) : Js.getMember(s.realm, o, m.keyConst);
                } else {
                    f = Js.getMember(s.realm, o, m.key.eval(s));
                }
                thiz = o;
            } else if (c instanceof SuperMember sm) {
                f = sm.eval(s);
                thiz = s.frame.thisFrame.thisVal;
            } else {
                f = c.eval(s);
                if (f == SHORT) {
                    return SHORT;
                }
            }
            if (this.optional && (f == Js.UNDEFINED || f == Js.NULL)) {
                return SHORT;
            }
            Object[] a = evalArgs(s, this.args, this.spread);
            if (f instanceof JsFunction fn) {
                if (this.maybeEval && fn == s.realm.evalFn) {
                    return s.realm.directEval(a.length > 0 ? a[0] : Js.UNDEFINED, s, this.strict);
                }
                return fn.call(thiz, a);
            }
            throw Js.typeError(this.text + " is not a function");
        }
    }

    static final class SuperCall extends Expr {
        final Expr[] args;
        final boolean spread;

        SuperCall(Expr[] args, boolean spread) {
            this.args = args;
            this.spread = spread;
        }

        @Override
        Object eval(Scope s) {
            Frame tf = s.frame.thisFrame;
            Closure fn = tf.func;
            if (fn == null) {
                throw Js.syntaxError("'super' keyword unexpected here");
            }
            if (tf.thisInit) {
                throw Js.referenceError("Super constructor may only be called once");
            }
            JsObject parent = fn.proto;
            if (!(parent instanceof JsFunction pf) || !pf.isConstructor()) {
                throw Js.typeError("Super constructor " + Js.display(parent) + " of anonymous class is not a constructor");
            }
            Object[] a = evalArgs(s, this.args, this.spread);
            Object res = pf.construct(a, tf.newTarget instanceof JsObject nt ? nt : fn);
            tf.thisVal = res;
            tf.thisInit = true;
            if (fn.fields != null && res instanceof JsObject ro) {
                fn.initFields(ro);
            }
            return res;
        }
    }

    static final class New extends Expr {
        final Expr callee;
        final Expr[] args;
        final boolean spread;
        final String text;

        New(Expr callee, Expr[] args, boolean spread, String text) {
            this.callee = callee;
            this.args = args;
            this.spread = spread;
            this.text = text;
        }

        @Override
        Object eval(Scope s) {
            Object f = this.callee.eval(s);
            Object[] a = evalArgs(s, this.args, this.spread);
            if (f instanceof JsFunction fn && fn.isConstructor()) {
                return fn.construct(a, fn);
            }
            throw Js.typeError(this.text + " is not a constructor");
        }
    }

    // ================================================================== operadores

    static final int OP_ADD = 1;
    static final int OP_SUB = 2;
    static final int OP_MUL = 3;
    static final int OP_DIV = 4;
    static final int OP_MOD = 5;
    static final int OP_EXP = 6;
    static final int OP_EQ = 7;
    static final int OP_NE = 8;
    static final int OP_SEQ = 9;
    static final int OP_SNE = 10;
    static final int OP_LT = 11;
    static final int OP_GT = 12;
    static final int OP_LE = 13;
    static final int OP_GE = 14;
    static final int OP_BAND = 15;
    static final int OP_BOR = 16;
    static final int OP_BXOR = 17;
    static final int OP_SHL = 18;
    static final int OP_SHR = 19;
    static final int OP_USHR = 20;
    static final int OP_IN = 21;
    static final int OP_INSTANCEOF = 22;
    static final int OP_AND = 23;
    static final int OP_OR = 24;
    static final int OP_NULLISH = 25;

    static Object binary(int op, Object a, Object b) {
        switch (op) {
            case OP_ADD:
                if (a instanceof Double x && b instanceof Double y) {
                    return x + y;
                }
                return Js.add(a, b);
            case OP_SUB:
                if (a instanceof Double x && b instanceof Double y) {
                    return x - y;
                }
                return Js.toNumber(a) - Js.toNumber(b);
            case OP_MUL:
                if (a instanceof Double x && b instanceof Double y) {
                    return x * y;
                }
                return Js.toNumber(a) * Js.toNumber(b);
            case OP_DIV:
                return Js.toNumber(a) / Js.toNumber(b);
            case OP_MOD:
                return Js.toNumber(a) % Js.toNumber(b);
            case OP_EXP:
                return pow(Js.toNumber(a), Js.toNumber(b));
            case OP_EQ:
                return Js.looseEquals(a, b);
            case OP_NE:
                return !Js.looseEquals(a, b);
            case OP_SEQ:
                return Js.strictEquals(a, b);
            case OP_SNE:
                return !Js.strictEquals(a, b);
            case OP_LT:
                return Js.lt(a, b);
            case OP_GT:
                return Js.gt(a, b);
            case OP_LE:
                return Js.le(a, b);
            case OP_GE:
                return Js.ge(a, b);
            case OP_BAND:
                return (double) (Js.toInt32(a) & Js.toInt32(b));
            case OP_BOR:
                return (double) (Js.toInt32(a) | Js.toInt32(b));
            case OP_BXOR:
                return (double) (Js.toInt32(a) ^ Js.toInt32(b));
            case OP_SHL:
                return (double) (Js.toInt32(a) << (Js.toInt32(b) & 31));
            case OP_SHR:
                return (double) (Js.toInt32(a) >> (Js.toInt32(b) & 31));
            case OP_USHR:
                return (double) ((Js.toInt32(a) & 0xFFFFFFFFL) >>> (Js.toInt32(b) & 31));
            case OP_IN:
                return Js.hasProperty(b, a);
            case OP_INSTANCEOF:
                return Js.instanceOf(a, b);
            default:
                throw new IllegalStateException("operador " + op);
        }
    }

    static double pow(double a, double b) {
        if (b != b) {
            return Double.NaN;
        }
        if ((a == 1 || a == -1) && Double.isInfinite(b)) {
            return Double.NaN;
        }
        return V8Math.pow(a, b);
    }

    static final class Binary extends Expr {
        final int op;
        final Expr left;
        final Expr right;

        Binary(int op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        Object eval(Scope s) {
            Object a = this.left.eval(s);
            Object b = this.right.eval(s);
            return binary(this.op, a, b);
        }
    }

    /** {@code #x in obj}: ¿tiene el objeto ese miembro privado? */
    static final class PrivateIn extends Expr {
        final PrivateName name;
        final Expr right;

        PrivateIn(PrivateName name, Expr right) {
            this.name = name;
            this.right = right;
        }

        @Override
        Object eval(Scope s) {
            Object o = this.right.eval(s);
            if (!(o instanceof JsObject jo)) {
                throw Js.typeError("Cannot use 'in' operator to search for '" + this.name + "' in " + Js.display(o));
            }
            return jo.findProp(this.name) != null;
        }
    }

    static final class Logical extends Expr {
        final int op;
        final Expr left;
        final Expr right;

        Logical(int op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        Object eval(Scope s) {
            Object a = this.left.eval(s);
            switch (this.op) {
                case OP_AND:
                    return Js.truthy(a) ? this.right.eval(s) : a;
                case OP_OR:
                    return Js.truthy(a) ? a : this.right.eval(s);
                default:
                    return a == Js.UNDEFINED || a == Js.NULL ? this.right.eval(s) : a;
            }
        }
    }

    static final int U_NOT = 1;
    static final int U_NEG = 2;
    static final int U_POS = 3;
    static final int U_BNOT = 4;
    static final int U_TYPEOF = 5;
    static final int U_VOID = 6;
    static final int U_DELETE = 7;

    static final class Unary extends Expr {
        final int op;
        final Expr e;
        boolean strict;

        Unary(int op, Expr e) {
            this.op = op;
            this.e = e;
        }

        @Override
        Object eval(Scope s) {
            switch (this.op) {
                case U_NOT:
                    return !Js.truthy(this.e.eval(s));
                case U_NEG: {
                    Object v = this.e.eval(s);
                    return v instanceof Double d ? -d : -Js.toNumber(v);
                }
                case U_POS:
                    return Js.toNumber(this.e.eval(s));
                case U_BNOT:
                    return (double) ~Js.toInt32(this.e.eval(s));
                case U_TYPEOF: {
                    Object v = this.e instanceof Ident id ? id.evalTypeof(s) : this.e.eval(s);
                    return Js.typeOf(v);
                }
                case U_VOID:
                    this.e.eval(s);
                    return Js.UNDEFINED;
                default: {
                    Expr t = this.e instanceof OptChain oc ? oc.inner : this.e;
                    if (t instanceof Member m) {
                        Object o = m.obj.eval(s);
                        if (o == SHORT || (m.optional && (o == Js.UNDEFINED || o == Js.NULL))) {
                            return true;
                        }
                        return Js.deleteMember(s.realm, o, m.keyConst != null ? m.keyConst : m.key.eval(s), this.strict);
                    }
                    if (t instanceof Ident) {
                        return false;
                    }
                    t.eval(s);
                    return true;
                }
            }
        }
    }

    static final class Update extends Expr {
        final Target target;
        final boolean prefix;
        final double delta;

        Update(Target target, boolean prefix, double delta) {
            this.target = target;
            this.prefix = prefix;
            this.delta = delta;
        }

        @Override
        Object eval(Scope s) {
            if (this.target instanceof Ident id) {
                Object old = id.eval(s);
                double o = old instanceof Double d ? d : Js.toNumber(old);
                double n = o + this.delta;
                id.set(s, n);
                return this.prefix ? n : o;
            }
            Member m = (Member) this.target;
            Object obj = m.obj.eval(s);
            Object key = m.keyConst != null ? m.keyConst : m.key.eval(s);
            Object old = Js.getMember(s.realm, obj, key);
            double o = Js.toNumber(old);
            double n = o + this.delta;
            Js.setMember(s.realm, obj, key, n, m.strict);
            return this.prefix ? n : o;
        }
    }

    static final class Assign extends Expr {
        /** 0 = asignación simple; si no, el código del operador (OP_*; OP_AND/OP_OR/OP_NULLISH para &&=, ||=, ??=). */
        final int op;
        final Target target;
        final Expr value;

        Assign(int op, Target target, Expr value) {
            this.op = op;
            this.target = target;
            this.value = value;
        }

        @Override
        Object eval(Scope s) {
            Target t = this.target;
            if (this.op == 0) {
                if (t instanceof Ident id) {
                    Object v = this.value.eval(s);
                    id.set(s, v);
                    return v;
                }
                if (t instanceof Member m) {
                    Object o = m.obj.eval(s);
                    Object k = m.keyConst != null ? m.keyConst : m.key.eval(s);
                    Object v = this.value.eval(s);
                    Js.setMember(s.realm, o, k, v, m.strict);
                    return v;
                }
                Object v = this.value.eval(s);
                t.assign(s, v, M_ASSIGN);
                return v;
            }
            if (t instanceof Ident id) {
                Object old = id.eval(s);
                if (this.op >= OP_AND) {
                    if (!shouldAssign(old)) {
                        return old;
                    }
                    Object v = this.value.eval(s);
                    id.set(s, v);
                    return v;
                }
                Object v = binary(this.op, old, this.value.eval(s));
                id.set(s, v);
                return v;
            }
            if (t instanceof Member m) {
                Object o = m.obj.eval(s);
                Object k = m.keyConst != null ? m.keyConst : m.key.eval(s);
                Object old = Js.getMember(s.realm, o, k);
                Object v;
                if (this.op >= OP_AND) {
                    if (!shouldAssign(old)) {
                        return old;
                    }
                    v = this.value.eval(s);
                } else {
                    v = binary(this.op, old, this.value.eval(s));
                }
                Js.setMember(s.realm, o, k, v, m.strict);
                return v;
            }
            SuperMember sm = (SuperMember) t;
            Object old = sm.eval(s);
            Object v;
            if (this.op >= OP_AND) {
                if (!shouldAssign(old)) {
                    return old;
                }
                v = this.value.eval(s);
            } else {
                v = binary(this.op, old, this.value.eval(s));
            }
            sm.assign(s, v, M_ASSIGN);
            return v;
        }

        private boolean shouldAssign(Object old) {
            switch (this.op) {
                case OP_AND:
                    return Js.truthy(old);
                case OP_OR:
                    return !Js.truthy(old);
                default:
                    return old == Js.UNDEFINED || old == Js.NULL;
            }
        }
    }

    static final class Cond extends Expr {
        final Expr test;
        final Expr yes;
        final Expr no;

        Cond(Expr test, Expr yes, Expr no) {
            this.test = test;
            this.yes = yes;
            this.no = no;
        }

        @Override
        Object eval(Scope s) {
            return Js.truthy(this.test.eval(s)) ? this.yes.eval(s) : this.no.eval(s);
        }
    }

    static final class Seq extends Expr {
        final Expr[] items;

        Seq(Expr[] items) {
            this.items = items;
        }

        @Override
        Object eval(Scope s) {
            Object v = Js.UNDEFINED;
            for (Expr e : this.items) {
                v = e.eval(s);
            }
            return v;
        }
    }

    // ================================================================== literales compuestos

    static final class ArrayLit extends Expr {
        final Expr[] elems;
        final boolean spread;

        ArrayLit(Expr[] elems, boolean spread) {
            this.elems = elems;
            this.spread = spread;
        }

        @Override
        Object eval(Scope s) {
            ArrayList<Object> l = new ArrayList<>(this.elems.length);
            for (Expr e : this.elems) {
                if (e == null) {
                    l.add(null);
                } else if (e instanceof Spread sp) {
                    IterRec it = Js.iterate(sp.e.eval(s), describe(sp.e));
                    for (Object v = it.next(); v != IterRec.DONE; v = it.next()) {
                        l.add(v);
                    }
                } else {
                    l.add(e.eval(s));
                }
            }
            return new JsArray(s.realm.arrayProto, l);
        }
    }

    static final int P_INIT = 0;
    static final int P_GET = 1;
    static final int P_SET = 2;
    static final int P_SPREAD = 3;
    static final int P_PROTO = 4;
    static final int P_METHOD = 5;

    static final class PropDef {
        int kind;
        Object keyConst;
        Expr keyExpr;
        Expr value;
        FuncNode fn;
    }

    static final class ObjectLit extends Expr {
        final PropDef[] props;

        ObjectLit(PropDef[] props) {
            this.props = props;
        }

        @Override
        Object eval(Scope s) {
            Realm r = s.realm;
            JsObject o = new JsObject(r.objectProto);
            for (PropDef p : this.props) {
                if (p.kind == P_SPREAD) {
                    Js.copyDataProps(r, o, p.value.eval(s), null);
                    continue;
                }
                if (p.kind == P_PROTO) {
                    Object v = p.value.eval(s);
                    if (v instanceof JsObject po) {
                        o.proto = po;
                    } else if (v == Js.NULL) {
                        o.proto = null;
                    }
                    continue;
                }
                Object key = p.keyConst != null ? p.keyConst : Js.toKey(p.keyExpr.eval(s));
                switch (p.kind) {
                    case P_INIT:
                        o.defineOwn(key, new Prop(p.value.eval(s), Prop.ALL));
                        break;
                    case P_METHOD: {
                        Closure c = new Closure(r, p.fn, s);
                        c.home = o;
                        if (p.keyConst == null) {
                            c.fname = Closure.keyName(key);
                        }
                        o.defineOwn(key, new Prop(c, Prop.ALL));
                        break;
                    }
                    default: {
                        Closure c = new Closure(r, p.fn, s);
                        c.home = o;
                        c.fname = Closure.keyName(key);
                        Closure.mergeAccessor(o, key, p.kind == P_GET ? c : null, p.kind == P_SET ? c : null, Prop.E | Prop.C);
                    }
                }
            }
            return o;
        }
    }

    static final class FuncExpr extends Expr {
        final FuncNode fn;
        /** Función con nombre propio que se ve desde su cuerpo. */
        final boolean selfBind;

        FuncExpr(FuncNode fn, boolean selfBind) {
            this.fn = fn;
            this.selfBind = selfBind;
        }

        @Override
        Object eval(Scope s) {
            if (this.selfBind) {
                Scope inner = new Scope(s, s.frame, s.realm);
                Closure c = new Closure(s.realm, this.fn, inner);
                inner.declare(this.fn.name, c);
                return c;
            }
            return new Closure(s.realm, this.fn, s);
        }
    }

    static final class Yield extends Expr {
        final Expr arg;
        final boolean delegate;

        Yield(Expr arg, boolean delegate) {
            this.arg = arg;
            this.delegate = delegate;
        }

        @Override
        Object eval(Scope s) {
            Coroutine co = s.frame.co;
            Object v = this.arg == null ? Js.UNDEFINED : this.arg.eval(s);
            if (co == null) {
                throw Js.syntaxError("Unexpected yield");
            }
            if (s.frame.asyncGen) {
                if (!this.delegate) {
                    Object av = awaitValue(s, v);
                    try {
                        return co.yield(av);
                    } catch (Coroutine.Return rr) {
                        throw new Coroutine.Return(awaitValue(s, rr.value));
                    }
                }
                AsyncIter ai = new AsyncIter(s, v);
                for (Object x = ai.next(); x != IterRec.DONE; x = ai.next()) {
                    try {
                        co.yield(x);
                    } catch (RuntimeException | Error e) {
                        ai.close();
                        throw e;
                    }
                }
                return Js.UNDEFINED;
            }
            if (!this.delegate) {
                return co.yield(v);
            }
            return delegateSync(s, co, v);
        }

        /** yield* en un generador normal: reenvía next/throw/return al iterador interno y devuelve su valor final. */
        private static Object delegateSync(Scope s, Coroutine co, Object v) {
            Realm r = s.realm;
            Object im = Js.getMember(r, v, JsSymbol.ITERATOR);
            if (!(im instanceof JsFunction imf)) {
                throw Js.typeError(Js.display(v) + " is not iterable");
            }
            Object iterObj = imf.call(v, Js.EMPTY);
            if (!(iterObj instanceof JsObject)) {
                throw Js.typeError("Result of the Symbol.iterator method is not an object");
            }
            Object nextFn = Js.getMember(r, iterObj, "next");
            Object received = Js.UNDEFINED;
            int mode = Coroutine.NEXT;
            while (true) {
                Object res;
                if (mode == Coroutine.NEXT) {
                    res = Js.call(nextFn, iterObj, received);
                } else if (mode == Coroutine.THROW) {
                    Object th = Js.getMember(r, iterObj, "throw");
                    if (th instanceof JsFunction tf) {
                        res = tf.call(iterObj, new Object[] {received});
                    } else {
                        Object rt = Js.getMember(r, iterObj, "return");
                        if (rt instanceof JsFunction rf) {
                            rf.call(iterObj, Js.EMPTY);
                        }
                        throw Js.typeError("The iterator does not provide a 'throw' method");
                    }
                } else {
                    Object rt = Js.getMember(r, iterObj, "return");
                    if (!(rt instanceof JsFunction rf)) {
                        throw new Coroutine.Return(received);
                    }
                    res = rf.call(iterObj, new Object[] {received});
                }
                if (!(res instanceof JsObject ro)) {
                    throw Js.typeError("Iterator result " + Js.display(res) + " is not an object");
                }
                if (Js.truthy(ro.get("done"))) {
                    Object val = ro.get("value");
                    if (mode == Coroutine.RETURN) {
                        throw new Coroutine.Return(val);
                    }
                    return val;
                }
                try {
                    received = co.yield(ro.get("value"));
                    mode = Coroutine.NEXT;
                } catch (JsException e) {
                    received = e.value;
                    mode = Coroutine.THROW;
                } catch (Coroutine.Return rr) {
                    received = rr.value;
                    mode = Coroutine.RETURN;
                }
            }
        }
    }

    static final class Await extends Expr {
        final Expr arg;

        Await(Expr arg) {
            this.arg = arg;
        }

        @Override
        Object eval(Scope s) {
            return awaitValue(s, this.arg.eval(s));
        }
    }

    /** Espera un valor dentro de una función async o de un generador async. */
    static Object awaitValue(Scope s, Object v) {
        Coroutine co = s.frame.co;
        if (co == null) {
            throw Js.syntaxError("await is only valid in async functions");
        }
        return co.yield(s.frame.asyncGen ? new AwaitMarker(v) : v);
    }

    /** Iteración asíncrona (for await y yield* en generadores async). */
    static final class AsyncIter {
        private final Scope scope;
        private Object iterObj;
        private Object nextFn;
        private IterRec sync;

        AsyncIter(Scope scope, Object iterable) {
            this.scope = scope;
            Object m = Js.getMember(scope.realm, iterable, JsSymbol.ASYNC_ITERATOR);
            if (m instanceof JsFunction mf) {
                this.iterObj = mf.call(iterable, Js.EMPTY);
                this.nextFn = Js.getMember(scope.realm, this.iterObj, "next");
            } else {
                this.sync = Js.iterate(iterable);
            }
        }

        Object next() {
            if (this.sync != null) {
                Object v = this.sync.next();
                if (v == IterRec.DONE) {
                    awaitValue(this.scope, Js.UNDEFINED);
                    awaitValue(this.scope, Js.UNDEFINED);
                    return v;
                }
                Object got = awaitValue(this.scope, v);
                awaitValue(this.scope, Js.UNDEFINED);
                return got;
            }
            Object res = awaitValue(this.scope, Js.call(this.nextFn, this.iterObj));
            if (!(res instanceof JsObject ro)) {
                throw Js.typeError("Iterator result " + Js.display(res) + " is not an object");
            }
            if (Js.truthy(ro.get("done"))) {
                return IterRec.DONE;
            }
            return ro.get("value");
        }

        void close() {
            if (this.sync != null) {
                this.sync.close();
                return;
            }
            Object ret = Js.getMember(this.scope.realm, this.iterObj, "return");
            if (ret instanceof JsFunction rf) {
                awaitValue(this.scope, rf.call(this.iterObj, Js.EMPTY));
            }
        }
    }

    // ================================================================== desestructuración

    static final class ArrayPattern implements Target {
        final Target[] elems;
        final Expr[] defaults;
        final Target rest;

        ArrayPattern(Target[] elems, Expr[] defaults, Target rest) {
            this.elems = elems;
            this.defaults = defaults;
            this.rest = rest;
        }

        @Override
        public void assign(Scope s, Object v, int mode) {
            IterRec it = Js.iterate(v, Js.display(v));
            boolean done = false;
            try {
                for (int i = 0; i < this.elems.length; i++) {
                    Object x = Js.UNDEFINED;
                    if (!done) {
                        Object n = it.next();
                        if (n == IterRec.DONE) {
                            done = true;
                        } else {
                            x = n;
                        }
                    }
                    if (this.elems[i] == null) {
                        continue;
                    }
                    if (x == Js.UNDEFINED && this.defaults[i] != null) {
                        x = this.defaults[i].eval(s);
                    }
                    this.elems[i].assign(s, x, mode);
                }
                if (this.rest != null) {
                    ArrayList<Object> l = new ArrayList<>();
                    while (!done) {
                        Object n = it.next();
                        if (n == IterRec.DONE) {
                            done = true;
                        } else {
                            l.add(n);
                        }
                    }
                    this.rest.assign(s, new JsArray(s.realm.arrayProto, l), mode);
                }
            } catch (RuntimeException | Error e) {
                if (!done) {
                    try {
                        it.close();
                    } catch (RuntimeException ignored) {
                        // el error original manda
                    }
                }
                throw e;
            }
            if (!done) {
                it.close();
            }
        }

        @Override
        public void names(List<String> out) {
            for (Target t : this.elems) {
                if (t != null) {
                    t.names(out);
                }
            }
            if (this.rest != null) {
                this.rest.names(out);
            }
        }
    }

    static final class PatProp {
        Object keyConst;
        Expr keyExpr;
        Target target;
        Expr dflt;
    }

    static final class ObjectPattern implements Target {
        final PatProp[] props;
        final Target rest;

        ObjectPattern(PatProp[] props, Target rest) {
            this.props = props;
            this.rest = rest;
        }

        @Override
        public void assign(Scope s, Object v, int mode) {
            if (v == Js.UNDEFINED || v == Js.NULL) {
                String first = this.props.length > 0 && this.props[0].keyConst instanceof String k ? "property '" + k + "' of " : "";
                throw Js.typeError("Cannot destructure " + first + "'" + v + "' as it is " + v + ".");
            }
            HashSet<Object> used = this.rest != null ? new HashSet<>() : null;
            for (PatProp p : this.props) {
                Object key = p.keyConst != null ? p.keyConst : Js.toKey(p.keyExpr.eval(s));
                if (used != null) {
                    used.add(key);
                }
                Object x = Js.getMember(s.realm, v, key);
                if (x == Js.UNDEFINED && p.dflt != null) {
                    x = p.dflt.eval(s);
                }
                p.target.assign(s, x, mode);
            }
            if (this.rest != null) {
                JsObject o = new JsObject(s.realm.objectProto);
                Js.copyDataProps(s.realm, o, v, used);
                this.rest.assign(s, o, mode);
            }
        }

        @Override
        public void names(List<String> out) {
            for (PatProp p : this.props) {
                p.target.names(out);
            }
            if (this.rest != null) {
                this.rest.names(out);
            }
        }
    }

    // ================================================================== sentencias

    static int run(Stmt[] body, Scope s) {
        for (Stmt st : body) {
            int c = st.exec(s);
            if (c != NORMAL) {
                return c;
            }
        }
        return NORMAL;
    }

    /** Declara en {@code sc} lo que un bloque o función necesita antes de ejecutarse: let/const sin inicializar y funciones. */
    static void hoistBlock(Scope sc, String[] lexNames, boolean[] lexConst, FuncDecl[] funcs) {
        for (int i = 0; i < lexNames.length; i++) {
            sc.declareLex(lexNames[i], lexConst[i]);
        }
        for (FuncDecl f : funcs) {
            sc.declare(f.fn.name, new Closure(sc.realm, f.fn, sc));
        }
    }

    static final class ExprStmt extends Stmt {
        final Expr e;

        ExprStmt(Expr e) {
            this.e = e;
        }

        @Override
        int exec(Scope s) {
            s.frame.last = this.e.eval(s);
            return NORMAL;
        }
    }

    static final class Empty extends Stmt {
        @Override
        int exec(Scope s) {
            return NORMAL;
        }
    }

    static final class VarDecl extends Stmt {
        final int kind;
        final Target[] targets;
        final Expr[] inits;

        VarDecl(int kind, Target[] targets, Expr[] inits) {
            this.kind = kind;
            this.targets = targets;
            this.inits = inits;
        }

        @Override
        int exec(Scope s) {
            for (int i = 0; i < this.targets.length; i++) {
                Expr init = this.inits[i];
                if (init == null) {
                    if (this.kind == M_LET) {
                        this.targets[i].assign(s, Js.UNDEFINED, M_LET);
                    }
                    continue;
                }
                this.targets[i].assign(s, init.eval(s), this.kind);
            }
            return NORMAL;
        }
    }

    static final class FuncDecl extends Stmt {
        final FuncNode fn;
        boolean annexB;

        FuncDecl(FuncNode fn) {
            this.fn = fn;
        }

        @Override
        int exec(Scope s) {
            if (this.annexB) {
                Scope inner = s.findScope(this.fn.name);
                if (inner != null) {
                    Object v = inner.vars.get(this.fn.name).value;
                    Scope outer = inner.parent == null ? null : inner.parent.findScope(this.fn.name);
                    if (outer != null) {
                        outer.vars.get(this.fn.name).value = v;
                    } else {
                        s.realm.global.put(this.fn.name, v);
                    }
                }
            }
            return NORMAL;
        }
    }

    static final class Block extends Stmt {
        final Stmt[] body;
        final String[] lexNames;
        final boolean[] lexConst;
        final FuncDecl[] funcs;

        Block(Stmt[] body, String[] lexNames, boolean[] lexConst, FuncDecl[] funcs) {
            this.body = body;
            this.lexNames = lexNames;
            this.lexConst = lexConst;
            this.funcs = funcs;
        }

        @Override
        int exec(Scope s) {
            if (this.lexNames.length == 0 && this.funcs.length == 0) {
                return run(this.body, s);
            }
            Scope bs = new Scope(s, s.frame, s.realm);
            hoistBlock(bs, this.lexNames, this.lexConst, this.funcs);
            return run(this.body, bs);
        }
    }

    static final class If extends Stmt {
        final Expr test;
        final Stmt yes;
        final Stmt no;

        If(Expr test, Stmt yes, Stmt no) {
            this.test = test;
            this.yes = yes;
            this.no = no;
        }

        @Override
        int exec(Scope s) {
            if (Js.truthy(this.test.eval(s))) {
                return this.yes.exec(s);
            }
            return this.no == null ? NORMAL : this.no.exec(s);
        }
    }

    abstract static class Loop extends Stmt {
        String[] labels = NO_NAMES;

        private boolean hasLabel(String l) {
            for (String x : this.labels) {
                if (x.equals(l)) {
                    return true;
                }
            }
            return false;
        }

        /** Qué hacer tras el cuerpo: -1 sigue el ciclo; otro valor es el código con el que termina la sentencia. */
        final int after(int c, Frame f) {
            if (c == NORMAL) {
                return -1;
            }
            if (c == BREAK) {
                if (f.label == null) {
                    return NORMAL;
                }
                if (hasLabel(f.label)) {
                    f.label = null;
                    return NORMAL;
                }
                return BREAK;
            }
            if (c == CONTINUE) {
                if (f.label == null) {
                    return -1;
                }
                if (hasLabel(f.label)) {
                    f.label = null;
                    return -1;
                }
                return CONTINUE;
            }
            return c;
        }
    }

    static final class While extends Loop {
        final Expr test;
        final Stmt body;

        While(Expr test, Stmt body) {
            this.test = test;
            this.body = body;
        }

        @Override
        int exec(Scope s) {
            Frame f = s.frame;
            Realm r = s.realm;
            while (Js.truthy(this.test.eval(s))) {
                int c = after(this.body.exec(s), f);
                if (c >= 0) {
                    return c;
                }
                r.tick();
            }
            return NORMAL;
        }
    }

    static final class DoWhile extends Loop {
        final Expr test;
        final Stmt body;

        DoWhile(Expr test, Stmt body) {
            this.test = test;
            this.body = body;
        }

        @Override
        int exec(Scope s) {
            Frame f = s.frame;
            Realm r = s.realm;
            do {
                int c = after(this.body.exec(s), f);
                if (c >= 0) {
                    return c;
                }
                r.tick();
            } while (Js.truthy(this.test.eval(s)));
            return NORMAL;
        }
    }

    static final class For extends Loop {
        Stmt init;
        Expr test;
        Expr update;
        Stmt body;
        String[] letNames = NO_NAMES;
        boolean letConst;
        boolean perIteration;

        @Override
        int exec(Scope s) {
            Frame f = s.frame;
            Realm r = s.realm;
            Scope ls = s;
            if (this.letNames.length > 0) {
                ls = new Scope(s, f, r);
                for (String n : this.letNames) {
                    ls.declareLex(n, this.letConst);
                }
            }
            if (this.init != null) {
                this.init.exec(ls);
            }
            if (this.perIteration) {
                ls = copyScope(ls, s);
            }
            while (true) {
                if (this.test != null && !Js.truthy(this.test.eval(ls))) {
                    break;
                }
                int c = after(this.body.exec(ls), f);
                if (c >= 0) {
                    return c;
                }
                r.tick();
                if (this.perIteration) {
                    ls = copyScope(ls, s);
                }
                if (this.update != null) {
                    this.update.eval(ls);
                }
            }
            return NORMAL;
        }

        private Scope copyScope(Scope from, Scope parent) {
            Scope n = new Scope(parent, from.frame, from.realm);
            for (String name : this.letNames) {
                Scope.Binding b = from.vars.get(name);
                Scope.Binding c = n.declare(name, b.value);
                c.mutable = b.mutable;
                c.init = b.init;
            }
            return n;
        }
    }

    abstract static class ForEach extends Loop {
        /** 0 = asignar a un destino existente; si no M_VAR, M_LET o M_CONST. */
        int kind;
        Target target;
        String[] names = NO_NAMES;
        Expr obj;
        Stmt body;
        boolean closures;

        final Scope newIterScope(Scope s) {
            Scope is = new Scope(s, s.frame, s.realm);
            for (String n : this.names) {
                is.declareLex(n, this.kind == M_CONST);
            }
            return is;
        }

        final void bind(Scope is, Object v) {
            this.target.assign(is, v, this.kind == 0 ? M_ASSIGN : this.kind);
        }
    }

    static final class ForIn extends ForEach {
        @Override
        int exec(Scope s) {
            Object o = this.obj.eval(s);
            if (o == Js.UNDEFINED || o == Js.NULL) {
                return NORMAL;
            }
            JsObject jo = Js.toObject(s.realm, o);
            List<String> keys = Js.forInKeys(jo);
            Frame f = s.frame;
            Realm r = s.realm;
            boolean lex = this.kind == M_LET || this.kind == M_CONST;
            Scope shared = lex && !this.closures ? newIterScope(s) : null;
            for (String k : keys) {
                if (!jo.hasProperty(k)) {
                    continue;
                }
                Scope is = s;
                if (lex) {
                    is = shared != null ? shared : newIterScope(s);
                }
                bind(is, k);
                int c = after(this.body.exec(is), f);
                if (c >= 0) {
                    return c;
                }
                r.tick();
            }
            return NORMAL;
        }
    }

    static final class ForOf extends ForEach {
        boolean isAwait;

        private int execAwait(Scope s) {
            AsyncIter it = new AsyncIter(s, this.obj.eval(s));
            Frame f = s.frame;
            Realm r = s.realm;
            boolean lex = this.kind == M_LET || this.kind == M_CONST;
            Scope shared = lex && !this.closures ? newIterScope(s) : null;
            while (true) {
                Object v = it.next();
                if (v == IterRec.DONE) {
                    return NORMAL;
                }
                int c;
                try {
                    Scope is = s;
                    if (lex) {
                        is = shared != null ? shared : newIterScope(s);
                    }
                    bind(is, v);
                    c = after(this.body.exec(is), f);
                } catch (RuntimeException | Error e) {
                    try {
                        it.close();
                    } catch (RuntimeException ignored) {
                        // el error original manda
                    }
                    throw e;
                }
                if (c >= 0) {
                    it.close();
                    return c;
                }
                r.tick();
            }
        }

        @Override
        int exec(Scope s) {
            if (this.isAwait) {
                return execAwait(s);
            }
            IterRec it = Js.iterate(this.obj.eval(s), describe(this.obj));
            Frame f = s.frame;
            Realm r = s.realm;
            boolean lex = this.kind == M_LET || this.kind == M_CONST;
            Scope shared = lex && !this.closures ? newIterScope(s) : null;
            while (true) {
                Object v = it.next();
                if (v == IterRec.DONE) {
                    return NORMAL;
                }
                int c;
                try {
                    Scope is = s;
                    if (lex) {
                        is = shared != null ? shared : newIterScope(s);
                    }
                    bind(is, v);
                    c = after(this.body.exec(is), f);
                } catch (RuntimeException | Error e) {
                    try {
                        it.close();
                    } catch (RuntimeException ignored) {
                        // el error original manda
                    }
                    throw e;
                }
                if (c >= 0) {
                    it.close();
                    return c;
                }
                r.tick();
            }
        }
    }

    static final class Return extends Stmt {
        final Expr e;

        Return(Expr e) {
            this.e = e;
        }

        @Override
        int exec(Scope s) {
            Object rv = this.e == null ? Js.UNDEFINED : this.e.eval(s);
            if (s.frame.asyncGen && this.e != null) {
                rv = awaitValue(s, rv);
            }
            s.frame.ret = rv;
            return RETURN;
        }
    }

    static final class Break extends Stmt {
        final String label;

        Break(String label) {
            this.label = label;
        }

        @Override
        int exec(Scope s) {
            s.frame.label = this.label;
            return BREAK;
        }
    }

    static final class Continue extends Stmt {
        final String label;

        Continue(String label) {
            this.label = label;
        }

        @Override
        int exec(Scope s) {
            s.frame.label = this.label;
            return CONTINUE;
        }
    }

    static final class Throw extends Stmt {
        final Expr e;

        Throw(Expr e) {
            this.e = e;
        }

        @Override
        int exec(Scope s) {
            throw new JsException(this.e.eval(s));
        }
    }

    static final class Labeled extends Stmt {
        final String label;
        final Stmt body;

        Labeled(String label, Stmt body) {
            this.label = label;
            this.body = body;
        }

        @Override
        int exec(Scope s) {
            int c = this.body.exec(s);
            if (c == BREAK && this.label.equals(s.frame.label)) {
                s.frame.label = null;
                return NORMAL;
            }
            return c;
        }
    }

    static final class Try extends Stmt {
        final Block block;
        final Target param;
        final Block handler;
        final Block fin;

        Try(Block block, Target param, Block handler, Block fin) {
            this.block = block;
            this.param = param;
            this.handler = handler;
            this.fin = fin;
        }

        @Override
        int exec(Scope s) {
            if (this.fin == null) {
                return tryCatch(s);
            }
            int code;
            Throwable pending = null;
            try {
                code = tryCatch(s);
            } catch (RuntimeException | Error t) {
                pending = t;
                code = NORMAL;
            }
            int fc = this.fin.exec(s);
            if (fc != NORMAL) {
                return fc;
            }
            if (pending instanceof RuntimeException re) {
                throw re;
            }
            if (pending != null) {
                throw (Error) pending;
            }
            return code;
        }

        private int tryCatch(Scope s) {
            if (this.handler == null) {
                return this.block.exec(s);
            }
            Object thrown;
            try {
                return this.block.exec(s);
            } catch (JsException e) {
                thrown = e.value;
            } catch (StackOverflowError e) {
                thrown = Js.rangeError("Maximum call stack size exceeded").value;
            }
            Scope cs = new Scope(s, s.frame, s.realm);
            if (this.param != null) {
                this.param.assign(cs, thrown, M_LET);
            }
            return this.handler.exec(cs);
        }
    }

    static final class Switch extends Stmt {
        final Expr disc;
        final Expr[] tests;
        final Stmt[][] bodies;
        final String[] lexNames;
        final boolean[] lexConst;
        final FuncDecl[] funcs;

        Switch(Expr disc, Expr[] tests, Stmt[][] bodies, String[] lexNames, boolean[] lexConst, FuncDecl[] funcs) {
            this.disc = disc;
            this.tests = tests;
            this.bodies = bodies;
            this.lexNames = lexNames;
            this.lexConst = lexConst;
            this.funcs = funcs;
        }

        @Override
        int exec(Scope s) {
            Object d = this.disc.eval(s);
            Scope ss = s;
            if (this.lexNames.length > 0 || this.funcs.length > 0) {
                ss = new Scope(s, s.frame, s.realm);
                hoistBlock(ss, this.lexNames, this.lexConst, this.funcs);
            }
            int start = -1;
            int dflt = -1;
            for (int i = 0; i < this.tests.length; i++) {
                if (this.tests[i] == null) {
                    dflt = i;
                } else if (Js.strictEquals(d, this.tests[i].eval(ss))) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                start = dflt;
            }
            if (start < 0) {
                return NORMAL;
            }
            for (int i = start; i < this.bodies.length; i++) {
                int c = run(this.bodies[i], ss);
                if (c == BREAK) {
                    return s.frame.label == null ? NORMAL : BREAK;
                }
                if (c != NORMAL) {
                    return c;
                }
            }
            return NORMAL;
        }
    }

    // ================================================================== clases

    static final int C_METHOD = 0;
    static final int C_GETTER = 1;
    static final int C_SETTER = 2;
    static final int C_FIELD = 3;
    static final int C_STATIC_BLOCK = 4;

    static final class ClassMember {
        int kind;
        boolean isStatic;
        Object keyConst;
        Expr keyExpr;
        FuncNode fn;
        Expr value;
    }

    static final class ClassNode {
        String name;
        String inferred;
        Expr superClass;
        FuncNode ctor;
        ClassMember[] members = new ClassMember[0];
        String source;
        int start;
        int end;
    }

    static final class ClassExpr extends Expr {
        final ClassNode node;

        ClassExpr(ClassNode node) {
            this.node = node;
        }

        @Override
        Object eval(Scope s) {
            Realm r = s.realm;
            ClassNode n = this.node;
            Scope cs = new Scope(s, s.frame, r);
            if (n.name != null) {
                cs.declareLex(n.name, true);
            }
            JsObject protoParent = r.objectProto;
            JsObject ctorParent = r.functionProto;
            if (n.superClass != null) {
                Object sc = n.superClass.eval(cs);
                if (sc == Js.NULL) {
                    protoParent = null;
                } else if (sc instanceof JsFunction sf && sf.isConstructor()) {
                    Object pp = sf.get("prototype");
                    if (pp == Js.NULL) {
                        protoParent = null;
                    } else if (pp instanceof JsObject ppo) {
                        protoParent = ppo;
                    } else {
                        throw Js.typeError("Class extends value does not have valid prototype property " + Js.display(pp));
                    }
                    ctorParent = sf;
                } else {
                    throw Js.typeError("Class extends value " + Js.display(sc) + " is not a constructor or null");
                }
            }
            JsObject proto = new JsObject(protoParent);
            Closure ctor = new Closure(r, n.ctor, cs);
            ctor.proto = ctorParent;
            ctor.home = proto;
            ctor.fname = n.name != null ? n.name : n.inferred != null ? n.inferred : "";
            ctor.classSource = n.source == null ? null : n.source.substring(n.start, n.end);
            ctor.setConst("prototype", proto);
            ctor.protoDone = true;
            proto.setHidden("constructor", ctor);
            ArrayList<Object[]> statics = new ArrayList<>();
            for (ClassMember m : n.members) {
                Object key = m.keyConst != null ? m.keyConst : m.keyExpr != null ? Js.toKey(m.keyExpr.eval(cs)) : null;
                JsObject target = m.isStatic ? ctor : proto;
                switch (m.kind) {
                    case C_METHOD: {
                        Closure c = new Closure(r, m.fn, cs);
                        c.home = target;
                        c.fname = Closure.keyName(key);
                        target.setHidden(key, c);
                        break;
                    }
                    case C_GETTER:
                    case C_SETTER: {
                        Closure c = new Closure(r, m.fn, cs);
                        c.home = target;
                        c.fname = Closure.keyName(key);
                        Closure.mergeAccessor(target, key, m.kind == C_GETTER ? c : null, m.kind == C_SETTER ? c : null, Prop.C);
                        break;
                    }
                    case C_FIELD:
                        if (m.isStatic) {
                            statics.add(new Object[] {key, m.value});
                        } else {
                            ctor.addField(key, m.value, cs, proto);
                        }
                        break;
                    default: {
                        Closure c = new Closure(r, m.fn, cs);
                        c.home = ctor;
                        statics.add(new Object[] {null, c});
                    }
                }
            }
            if (n.name != null) {
                Scope.Binding b = cs.vars.get(n.name);
                b.value = ctor;
                b.init = true;
            }
            for (Object[] st : statics) {
                if (st[0] == null && st[1] instanceof Closure blk) {
                    blk.call(ctor, Js.EMPTY);
                } else {
                    Frame ff = new Frame();
                    ff.thisVal = ctor;
                    ff.home = ctor;
                    Scope fs = new Scope(cs, ff, r);
                    Object v = st[1] == null ? Js.UNDEFINED : ((Expr) st[1]).eval(fs);
                    ctor.defineOwn(st[0], new Prop(v, Prop.ALL));
                }
            }
            return ctor;
        }
    }

    static final class ClassDecl extends Stmt {
        final ClassExpr cls;
        final String name;

        ClassDecl(ClassExpr cls, String name) {
            this.cls = cls;
            this.name = name;
        }

        @Override
        int exec(Scope s) {
            Object c = this.cls.eval(s);
            Scope.Binding b = s.vars == null ? null : s.vars.get(this.name);
            if (b == null) {
                b = s.declare(this.name, c);
            }
            b.value = c;
            b.init = true;
            return NORMAL;
        }
    }
}
