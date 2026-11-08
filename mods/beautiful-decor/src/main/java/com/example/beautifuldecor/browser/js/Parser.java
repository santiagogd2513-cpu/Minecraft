package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Lee los tokens y construye el árbol del programa. */
final class Parser {
    private static final Set<String> RESERVED = Set.of("break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if", "import", "in",
            "instanceof", "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
            "while", "with");

    private static final class BlockCtx {
        final boolean fnTop;
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<Boolean> consts = new ArrayList<>();
        final ArrayList<Ast.FuncDecl> funcs = new ArrayList<>();

        BlockCtx(boolean fnTop) {
            this.fnTop = fnTop;
        }

        void addLex(String n, boolean isConst, int line) {
            if (this.names.contains(n)) {
                throw new JsSyntaxError("Identifier '" + n + "' has already been declared", line);
            }
            this.names.add(n);
            this.consts.add(isConst);
        }

        String[] nameArray() {
            return this.names.toArray(new String[0]);
        }

        boolean[] constArray() {
            boolean[] r = new boolean[this.consts.size()];
            for (int i = 0; i < r.length; i++) {
                r[i] = this.consts.get(i);
            }
            return r;
        }

        Ast.FuncDecl[] funcArray() {
            return this.funcs.toArray(new Ast.FuncDecl[0]);
        }
    }

    private static final class FnCtx {
        final FnCtx parent;
        final boolean isArrow;
        final boolean isAsync;
        final boolean isGen;
        final boolean isFunction;
        boolean strict;
        boolean usesArgs;
        boolean hasAwait;
        int loops;
        int switches;
        final ArrayList<String> labels = new ArrayList<>();
        final LinkedHashSet<String> vars = new LinkedHashSet<>();

        FnCtx(FnCtx parent, boolean isArrow, boolean isAsync, boolean isGen, boolean isFunction, boolean strict) {
            this.parent = parent;
            this.isArrow = isArrow;
            this.isAsync = isAsync;
            this.isGen = isGen;
            this.isFunction = isFunction;
            this.strict = strict;
        }
    }

    /** Un valor de la forma {@code {a = 1}}: solo es válido si luego resulta ser un patrón de desestructuración. */
    private static final class CoverInit extends Ast.Expr {
        final Ast.Ident id;
        final Ast.Expr dflt;

        CoverInit(Ast.Ident id, Ast.Expr dflt) {
            this.id = id;
            this.dflt = dflt;
        }

        @Override
        Object eval(Scope s) {
            throw Js.syntaxError("Invalid shorthand property initializer");
        }
    }

    private final String src;
    private List<Token> toks;
    private int i;
    private Token t;
    private int prevEnd;
    private FnCtx fn;
    private BlockCtx blk;
    private int funcCounter;

    // resultado de parsePropertyKey
    private Object pkConst;
    private Ast.Expr pkExpr;
    private boolean pkIdent;

    private Parser(String src, List<Token> toks) {
        this.src = src;
        this.toks = toks;
        this.i = 0;
        this.t = toks.get(0);
    }

    // ------------------------------------------------------------------ entradas

    static Ast.Program parseProgram(String source, boolean strict) {
        Parser p = new Parser(source, new Lexer(source).tokenize());
        return p.program(strict);
    }

    /** El código de {@code new Function(params, body)}. */
    static Ast.FuncNode parseFunctionCtor(String params, String body) {
        return parseFunctionCtor(params, body, "function");
    }

    static Ast.FuncNode parseFunctionCtor(String params, String body, String kind) {
        String text = "(" + kind + " anonymous(" + params + "\n) {\n" + body + "\n})";
        Ast.Program prog = parseProgram(text, false);
        if (prog.body.length != 1 || !(prog.body[0] instanceof Ast.ExprStmt es) || !(es.e instanceof Ast.FuncExpr fe)) {
            throw new JsSyntaxError("Unexpected token", 1);
        }
        fe.fn.start = 1;
        fe.fn.end = text.length() - 1;
        return fe.fn;
    }

    private Ast.Program program(boolean strict) {
        this.fn = new FnCtx(null, false, false, false, false, strict);
        this.blk = new BlockCtx(true);
        directives();
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        while (this.t.type != Token.EOF) {
            body.add(statement());
        }
        Ast.Program p = new Ast.Program();
        p.body = body.toArray(new Ast.Stmt[0]);
        p.varNames = this.fn.vars.toArray(new String[0]);
        p.funcs = this.blk.funcArray();
        p.lexNames = this.blk.nameArray();
        p.lexConst = this.blk.constArray();
        p.strict = this.fn.strict;
        return p;
    }

    // ------------------------------------------------------------------ utilidades

    private JsSyntaxError err(String msg) {
        return new JsSyntaxError(msg, this.t.line);
    }

    private JsSyntaxError unexpected() {
        Token k = this.t;
        switch (k.type) {
            case Token.EOF:
                return err("Unexpected end of input");
            case Token.NUM:
                return err("Unexpected number");
            case Token.STR:
                return err("Unexpected string");
            case Token.TEMPLATE:
                return err("Unexpected template string");
            case Token.NAME:
                return err(RESERVED.contains(k.text) ? "Unexpected token '" + k.text + "'" : "Unexpected identifier '" + k.text + "'");
            default:
                return err("Unexpected token '" + k.text + "'");
        }
    }

    private void advance() {
        this.prevEnd = this.t.end;
        if (this.i < this.toks.size() - 1) {
            this.i++;
        }
        this.t = this.toks.get(this.i);
    }

    private Token peek(int n) {
        int k = this.i + n;
        return k < this.toks.size() ? this.toks.get(k) : this.toks.get(this.toks.size() - 1);
    }

    private boolean isP(String p) {
        return this.t.type == Token.PUNCT && this.t.text.equals(p);
    }

    private boolean eatP(String p) {
        if (isP(p)) {
            advance();
            return true;
        }
        return false;
    }

    private void expectP(String p) {
        if (!eatP(p)) {
            throw unexpected();
        }
    }

    private boolean isKw(String k) {
        return this.t.type == Token.NAME && !this.t.escaped && this.t.text.equals(k);
    }

    private boolean eatKw(String k) {
        if (isKw(k)) {
            advance();
            return true;
        }
        return false;
    }

    private void expectKw(String k) {
        if (!eatKw(k)) {
            throw unexpected();
        }
    }

    private void semicolon() {
        if (eatP(";")) {
            return;
        }
        if (isP("}") || this.t.type == Token.EOF || this.t.nl) {
            return;
        }
        throw unexpected();
    }

    private static boolean tokIs(Token k, String... ps) {
        if (k.type != Token.PUNCT) {
            return false;
        }
        for (String p : ps) {
            if (k.text.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentifier(Token k) {
        if (k.type != Token.NAME) {
            return false;
        }
        if (k.escaped) {
            return true;
        }
        if (RESERVED.contains(k.text)) {
            return false;
        }
        if (k.text.equals("yield") && (this.fn.isGen || this.fn.strict)) {
            return false;
        }
        return !(k.text.equals("await") && this.fn.isAsync);
    }

    private String identifier() {
        if (!isIdentifier(this.t)) {
            throw unexpected();
        }
        String n = this.t.text;
        advance();
        return n;
    }

    private void markArguments() {
        for (FnCtx c = this.fn; c != null; c = c.parent) {
            c.usesArgs = true;
            if (!c.isArrow) {
                break;
            }
        }
    }

    private Ast.Ident ident(String name) {
        Ast.Ident id = new Ast.Ident(name);
        id.strict = this.fn.strict;
        id.line = this.t.line;
        if (name.equals("arguments")) {
            markArguments();
        }
        return id;
    }

    private void directives() {
        while (this.t.type == Token.STR) {
            Token nx = peek(1);
            boolean end = tokIs(nx, ";", "}") || nx.type == Token.EOF || nx.nl;
            if (!end) {
                return;
            }
            if (!this.t.escaped && this.t.text.equals("use strict")) {
                this.fn.strict = true;
            }
            return;
        }
    }

    private void nameAnon(Ast.Expr e, String name) {
        if (e instanceof Ast.FuncExpr fe && fe.fn.name.isEmpty()) {
            fe.fn.name = name;
        } else if (e instanceof Ast.ClassExpr ce && ce.node.name == null && ce.node.inferred == null) {
            ce.node.inferred = name;
        }
    }

    // ------------------------------------------------------------------ sentencias

    private Ast.Stmt statement() {
        int line = this.t.line;
        Ast.Stmt s = statement0();
        s.line = line;
        return s;
    }

    private Ast.Stmt statement0() {
        Token k = this.t;
        if (k.type == Token.PUNCT) {
            if (k.text.equals("{")) {
                return block();
            }
            if (k.text.equals(";")) {
                advance();
                return new Ast.Empty();
            }
        }
        if (k.type == Token.NAME && !k.escaped) {
            switch (k.text) {
                case "var":
                    return varStatement(Ast.M_VAR);
                case "const":
                    return varStatement(Ast.M_CONST);
                case "let": {
                    Token nx = peek(1);
                    if ((nx.type == Token.NAME && !nx.text.equals("in") && !nx.text.equals("instanceof") && !nx.text.equals("of"))
                            || tokIs(nx, "[", "{")) {
                        return varStatement(Ast.M_LET);
                    }
                    break;
                }
                case "function":
                    return functionDeclaration(false);
                case "async":
                    if (peek(1).isName("function") && !peek(1).nl) {
                        return functionDeclaration(true);
                    }
                    break;
                case "class":
                    return classDeclaration();
                case "if":
                    return ifStatement();
                case "for":
                    return forStatement();
                case "while": {
                    advance();
                    expectP("(");
                    Ast.Expr test = expression(false);
                    expectP(")");
                    return new Ast.While(test, loopBody());
                }
                case "do": {
                    advance();
                    Ast.Stmt body = loopBody();
                    expectKw("while");
                    expectP("(");
                    Ast.Expr test = expression(false);
                    expectP(")");
                    eatP(";");
                    return new Ast.DoWhile(test, body);
                }
                case "return": {
                    if (!this.fn.isFunction) {
                        throw err("Illegal return statement");
                    }
                    advance();
                    Ast.Expr e = null;
                    if (!isP(";") && !isP("}") && this.t.type != Token.EOF && !this.t.nl) {
                        e = expression(false);
                    }
                    semicolon();
                    return new Ast.Return(e);
                }
                case "break":
                case "continue": {
                    boolean isBreak = k.text.equals("break");
                    advance();
                    String label = null;
                    if (this.t.type == Token.NAME && !this.t.nl && !RESERVED.contains(this.t.text)) {
                        label = this.t.text;
                        if (!this.fn.labels.contains(label)) {
                            throw err("Undefined label '" + label + "'");
                        }
                        advance();
                    } else if (isBreak ? this.fn.loops == 0 && this.fn.switches == 0 : this.fn.loops == 0) {
                        throw err(isBreak ? "Illegal break statement" : "Illegal continue statement: no surrounding iteration statement");
                    }
                    semicolon();
                    return isBreak ? new Ast.Break(label) : new Ast.Continue(label);
                }
                case "throw": {
                    advance();
                    if (this.t.nl) {
                        throw err("Illegal newline after throw");
                    }
                    Ast.Expr e = expression(false);
                    semicolon();
                    return new Ast.Throw(e);
                }
                case "try":
                    return tryStatement();
                case "switch":
                    return switchStatement();
                case "debugger":
                    advance();
                    semicolon();
                    return new Ast.Empty();
                case "import":
                    if (!tokIs(peek(1), "(", ".")) {
                        throw err("Cannot use import statement outside a module");
                    }
                    break;
                case "export":
                    throw err("Unexpected token 'export'");
                case "with":
                    throw err("Strict mode code may not include a with statement");
                default:
                    break;
            }
            if (isIdentifier(k) && tokIs(peek(1), ":")) {
                return labeled();
            }
        }
        Ast.Expr e = expression(false);
        semicolon();
        return new Ast.ExprStmt(e);
    }

    private Ast.Stmt labeled() {
        ArrayList<String> labels = new ArrayList<>();
        while (this.t.type == Token.NAME && !RESERVED.contains(this.t.text) && tokIs(peek(1), ":")) {
            labels.add(this.t.text);
            advance();
            advance();
        }
        this.fn.labels.addAll(labels);
        Ast.Stmt body;
        try {
            body = statement();
        } finally {
            for (int k = 0; k < labels.size(); k++) {
                this.fn.labels.remove(this.fn.labels.size() - 1);
            }
        }
        if (body instanceof Ast.Loop lp) {
            ArrayList<String> all = new ArrayList<>(List.of(lp.labels));
            all.addAll(labels);
            lp.labels = all.toArray(new String[0]);
        }
        for (int k = labels.size() - 1; k >= 0; k--) {
            body = new Ast.Labeled(labels.get(k), body);
        }
        return body;
    }

    private Ast.Stmt loopBody() {
        this.fn.loops++;
        try {
            return statement();
        } finally {
            this.fn.loops--;
        }
    }

    private Ast.Block block() {
        expectP("{");
        BlockCtx saved = this.blk;
        this.blk = new BlockCtx(false);
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        while (!isP("}")) {
            if (this.t.type == Token.EOF) {
                throw unexpected();
            }
            body.add(statement());
        }
        advance();
        Ast.Block b = new Ast.Block(body.toArray(new Ast.Stmt[0]), this.blk.nameArray(), this.blk.constArray(), this.blk.funcArray());
        this.blk = saved;
        return b;
    }

    private Ast.Stmt ifStatement() {
        advance();
        expectP("(");
        Ast.Expr test = expression(false);
        expectP(")");
        Ast.Stmt yes = statement();
        Ast.Stmt no = null;
        if (eatKw("else")) {
            no = statement();
        }
        return new Ast.If(test, yes, no);
    }

    private Ast.Stmt tryStatement() {
        advance();
        Ast.Block block = block();
        Ast.Target param = null;
        Ast.Block handler = null;
        Ast.Block fin = null;
        if (eatKw("catch")) {
            if (eatP("(")) {
                param = bindingTarget();
                expectP(")");
            }
            handler = block();
        }
        if (eatKw("finally")) {
            fin = block();
        }
        if (handler == null && fin == null) {
            throw err("Missing catch or finally after try");
        }
        return new Ast.Try(block, param, handler, fin);
    }

    private Ast.Stmt switchStatement() {
        advance();
        expectP("(");
        Ast.Expr disc = expression(false);
        expectP(")");
        expectP("{");
        BlockCtx saved = this.blk;
        this.blk = new BlockCtx(false);
        ArrayList<Ast.Expr> tests = new ArrayList<>();
        ArrayList<Ast.Stmt[]> bodies = new ArrayList<>();
        this.fn.switches++;
        while (!isP("}")) {
            if (eatKw("case")) {
                tests.add(expression(false));
            } else if (eatKw("default")) {
                tests.add(null);
            } else {
                throw unexpected();
            }
            expectP(":");
            ArrayList<Ast.Stmt> body = new ArrayList<>();
            while (!isP("}") && !isKw("case") && !isKw("default")) {
                if (this.t.type == Token.EOF) {
                    throw unexpected();
                }
                body.add(statement());
            }
            bodies.add(body.toArray(new Ast.Stmt[0]));
        }
        this.fn.switches--;
        advance();
        Ast.Switch sw = new Ast.Switch(disc, tests.toArray(new Ast.Expr[0]), bodies.toArray(new Ast.Stmt[0][]), this.blk.nameArray(),
                this.blk.constArray(), this.blk.funcArray());
        this.blk = saved;
        return sw;
    }

    private Ast.Stmt forStatement() {
        advance();
        boolean isAwaitLoop = false;
        if (isKw("await")) {
            if (!this.fn.isAsync) {
                throw err("Unexpected reserved word");
            }
            this.fn.hasAwait = true;
            isAwaitLoop = true;
            advance();
        }
        expectP("(");
        int closuresBefore = this.funcCounter;
        BlockCtx saved = this.blk;
        this.blk = new BlockCtx(false);
        try {
            Ast.Stmt init = null;
            if (isP(";")) {
                return plainFor(null, closuresBefore);
            }
            boolean decl = isKw("var") || isKw("const")
                    || (isKw("let") && (peek(1).type == Token.NAME && !peek(1).text.equals("in") && !peek(1).text.equals("of") || tokIs(peek(1), "[", "{")));
            if (decl) {
                int kind = isKw("var") ? Ast.M_VAR : isKw("const") ? Ast.M_CONST : Ast.M_LET;
                advance();
                ArrayList<Ast.Target> targets = new ArrayList<>();
                ArrayList<Ast.Expr> inits = new ArrayList<>();
                while (true) {
                    Ast.Target tg = bindingTarget();
                    registerBinding(tg, kind);
                    Ast.Expr in = null;
                    if (eatP("=")) {
                        in = assign(true);
                        if (tg instanceof Ast.Ident id) {
                            nameAnon(in, id.name);
                        }
                    }
                    targets.add(tg);
                    inits.add(in);
                    if (targets.size() == 1 && (isKw("of") || isKw("in"))) {
                        boolean isOf = isKw("of");
                        advance();
                        Ast.ForEach fe = isOf ? new Ast.ForOf() : new Ast.ForIn();
                        if (isAwaitLoop && fe instanceof Ast.ForOf fo) {
                            fo.isAwait = true;
                        }
                        fe.kind = kind;
                        fe.target = tg;
                        ArrayList<String> names = new ArrayList<>();
                        tg.names(names);
                        fe.names = kind == Ast.M_VAR ? Ast.NO_NAMES : names.toArray(new String[0]);
                        fe.obj = isOf ? assign(false) : expression(false);
                        expectP(")");
                        fe.body = loopBody();
                        fe.closures = this.funcCounter != closuresBefore;
                        return fe;
                    }
                    if (!eatP(",")) {
                        break;
                    }
                }
                init = new Ast.VarDecl(kind, targets.toArray(new Ast.Target[0]), inits.toArray(new Ast.Expr[0]));
                Ast.For f = (Ast.For) plainFor(init, closuresBefore);
                f.letNames = kind == Ast.M_VAR ? Ast.NO_NAMES : this.blk.nameArray();
                f.letConst = kind == Ast.M_CONST;
                f.perIteration = f.letNames.length > 0 && !f.letConst && this.funcCounter != closuresBefore;
                return f;
            }
            Ast.Expr e = expression(true);
            if (isKw("of") || isKw("in")) {
                boolean isOf = isKw("of");
                advance();
                Ast.ForEach fe = isOf ? new Ast.ForOf() : new Ast.ForIn();
                if (isAwaitLoop && fe instanceof Ast.ForOf fo) {
                    fo.isAwait = true;
                }
                fe.kind = 0;
                fe.target = toTarget(e, true);
                fe.obj = isOf ? assign(false) : expression(false);
                expectP(")");
                fe.body = loopBody();
                fe.closures = this.funcCounter != closuresBefore;
                return fe;
            }
            return plainFor(new Ast.ExprStmt(e), closuresBefore);
        } finally {
            this.blk = saved;
        }
    }

    /** El resto de un {@code for(;;)} desde el primer punto y coma. */
    private Ast.Stmt plainFor(Ast.Stmt init, int closuresBefore) {
        Ast.For f = new Ast.For();
        f.init = init;
        expectP(";");
        if (!isP(";")) {
            f.test = expression(false);
        }
        expectP(";");
        if (!isP(")")) {
            f.update = expression(false);
        }
        expectP(")");
        f.body = loopBody();
        f.perIteration = false;
        return f;
    }

    // ------------------------------------------------------------------ declaraciones

    private void registerBinding(Ast.Target tg, int kind) {
        ArrayList<String> names = new ArrayList<>();
        tg.names(names);
        for (String n : names) {
            if (kind == Ast.M_VAR) {
                this.fn.vars.add(n);
            } else {
                this.blk.addLex(n, kind == Ast.M_CONST, this.t.line);
            }
        }
    }

    private Ast.Stmt varStatement(int kind) {
        advance();
        Ast.VarDecl d = varDeclarations(kind, false);
        semicolon();
        return d;
    }

    private Ast.VarDecl varDeclarations(int kind, boolean noIn) {
        ArrayList<Ast.Target> targets = new ArrayList<>();
        ArrayList<Ast.Expr> inits = new ArrayList<>();
        do {
            Ast.Target tg = bindingTarget();
            registerBinding(tg, kind);
            Ast.Expr in = null;
            if (eatP("=")) {
                in = assign(noIn);
                if (tg instanceof Ast.Ident id) {
                    nameAnon(in, id.name);
                }
            } else if (kind == Ast.M_CONST) {
                throw err("Missing initializer in const declaration");
            } else if (!(tg instanceof Ast.Ident)) {
                throw err("Missing initializer in destructuring declaration");
            }
            targets.add(tg);
            inits.add(in);
        } while (eatP(","));
        return new Ast.VarDecl(kind, targets.toArray(new Ast.Target[0]), inits.toArray(new Ast.Expr[0]));
    }

    private Ast.Target bindingTarget() {
        if (isP("[")) {
            return arrayPattern();
        }
        if (isP("{")) {
            return objectPattern();
        }
        return ident(identifier());
    }

    private Ast.Target arrayPattern() {
        expectP("[");
        ArrayList<Ast.Target> elems = new ArrayList<>();
        ArrayList<Ast.Expr> defaults = new ArrayList<>();
        Ast.Target rest = null;
        while (!isP("]")) {
            if (isP(",")) {
                advance();
                elems.add(null);
                defaults.add(null);
                continue;
            }
            if (eatP("...")) {
                rest = bindingTarget();
                break;
            }
            Ast.Target tg = bindingTarget();
            Ast.Expr d = null;
            if (eatP("=")) {
                d = assign(false);
                if (tg instanceof Ast.Ident id) {
                    nameAnon(d, id.name);
                }
            }
            elems.add(tg);
            defaults.add(d);
            if (!isP("]")) {
                expectP(",");
            }
        }
        expectP("]");
        return new Ast.ArrayPattern(elems.toArray(new Ast.Target[0]), defaults.toArray(new Ast.Expr[0]), rest);
    }

    private Ast.Target objectPattern() {
        expectP("{");
        ArrayList<Ast.PatProp> props = new ArrayList<>();
        Ast.Target rest = null;
        while (!isP("}")) {
            if (eatP("...")) {
                rest = bindingTarget();
                break;
            }
            Ast.PatProp p = new Ast.PatProp();
            Token keyTok = this.t;
            propertyKey();
            p.keyConst = this.pkConst;
            p.keyExpr = this.pkExpr;
            if (eatP(":")) {
                p.target = bindingTarget();
            } else {
                if (!this.pkIdent || !isIdentifier(keyTok)) {
                    throw unexpected();
                }
                p.target = ident(keyTok.text);
            }
            if (eatP("=")) {
                p.dflt = assign(false);
                if (p.target instanceof Ast.Ident id) {
                    nameAnon(p.dflt, id.name);
                }
            }
            props.add(p);
            if (!isP("}")) {
                expectP(",");
            }
        }
        expectP("}");
        return new Ast.ObjectPattern(props.toArray(new Ast.PatProp[0]), rest);
    }

    private Ast.Stmt functionDeclaration(boolean isAsync) {
        int start = this.t.pos;
        if (isAsync) {
            advance();
        }
        advance();
        boolean gen = eatP("*");
        String name = identifier();
        Ast.FuncNode f = functionRest(name, start, false, isAsync, gen, false);
        Ast.FuncDecl d = new Ast.FuncDecl(f);
        this.blk.funcs.add(d);
        if (!this.blk.fnTop && !this.fn.strict) {
            d.annexB = true;
            this.fn.vars.add(name);
        }
        return d;
    }

    private Ast.Stmt classDeclaration() {
        Ast.ClassExpr c = classExpr();
        this.blk.addLex(c.node.name, false, this.t.line);
        return new Ast.ClassDecl(c, c.node.name);
    }

    // ------------------------------------------------------------------ funciones

    /**
     * Lee parámetros y cuerpo de una función ({@code (a, b) { ... }}); el token actual debe ser el paréntesis de apertura.
     */
    private Ast.FuncNode functionRest(String name, int start, boolean isMethod, boolean isAsync, boolean isGen, boolean isCtor) {
        FnCtx ctx = new FnCtx(this.fn, false, isAsync, isGen, true, this.fn.strict);
        FnCtx savedFn = this.fn;
        BlockCtx savedBlk = this.blk;
        this.fn = ctx;
        this.blk = new BlockCtx(true);
        this.funcCounter++;
        Ast.FuncNode f = new Ast.FuncNode();
        f.name = name == null ? "" : name;
        f.isMethod = isMethod;
        f.isAsync = isAsync;
        f.isGen = isGen;
        parseParams(f);
        expectP("{");
        directives();
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        while (!isP("}")) {
            if (this.t.type == Token.EOF) {
                throw unexpected();
            }
            body.add(statement());
        }
        int end = this.t.end;
        advance();
        f.body = body.toArray(new Ast.Stmt[0]);
        finishFunc(f, ctx, start, end);
        this.fn = savedFn;
        this.blk = savedBlk;
        return f;
    }

    private void finishFunc(Ast.FuncNode f, FnCtx ctx, int start, int end) {
        f.varNames = ctx.vars.toArray(new String[0]);
        f.lexNames = this.blk.nameArray();
        f.lexConst = this.blk.constArray();
        f.funcs = this.blk.funcArray();
        f.usesArgs = ctx.usesArgs;
        f.hasAwait = ctx.hasAwait;
        f.strict = ctx.strict;
        f.source = this.src;
        f.start = start;
        f.end = end;
    }

    private void parseParams(Ast.FuncNode f) {
        expectP("(");
        ArrayList<Ast.Target> params = new ArrayList<>();
        ArrayList<Ast.Expr> defaults = new ArrayList<>();
        boolean simple = true;
        int length = 0;
        boolean sawDefault = false;
        while (!isP(")")) {
            if (eatP("...")) {
                params.add(bindingTarget());
                defaults.add(null);
                f.restIndex = params.size() - 1;
                simple = false;
                sawDefault = true;
                break;
            }
            Ast.Target tg = bindingTarget();
            Ast.Expr d = null;
            if (eatP("=")) {
                d = assign(false);
                if (tg instanceof Ast.Ident id) {
                    nameAnon(d, id.name);
                }
                simple = false;
                sawDefault = true;
            } else if (!(tg instanceof Ast.Ident)) {
                simple = false;
            }
            if (!sawDefault) {
                length++;
            }
            params.add(tg);
            defaults.add(d);
            if (!isP(")")) {
                expectP(",");
            }
        }
        expectP(")");
        f.params = params.toArray(new Ast.Target[0]);
        f.defaults = defaults.toArray(new Ast.Expr[0]);
        f.length = length;
        if (simple) {
            String[] names = new String[params.size()];
            for (int k = 0; k < names.length; k++) {
                names[k] = ((Ast.Ident) params.get(k)).name;
            }
            f.simpleParams = names;
        }
    }

    /** Función flecha: el token actual es el primer parámetro (sin paréntesis) o el paréntesis de apertura. */
    private Ast.Expr arrowFunction(boolean isAsync, int start) {
        FnCtx ctx = new FnCtx(this.fn, true, isAsync, false, true, this.fn.strict);
        FnCtx savedFn = this.fn;
        BlockCtx savedBlk = this.blk;
        this.fn = ctx;
        this.blk = new BlockCtx(true);
        this.funcCounter++;
        Ast.FuncNode f = new Ast.FuncNode();
        f.isArrow = true;
        f.isAsync = isAsync;
        if (isP("(")) {
            parseParams(f);
        } else {
            Ast.Ident id = ident(identifier());
            f.params = new Ast.Target[] {id};
            f.defaults = new Ast.Expr[] {null};
            f.simpleParams = new String[] {id.name};
            f.length = 1;
        }
        if (!isP("=>") || this.t.nl) {
            throw unexpected();
        }
        advance();
        int end;
        if (isP("{")) {
            advance();
            directives();
            ArrayList<Ast.Stmt> body = new ArrayList<>();
            while (!isP("}")) {
                if (this.t.type == Token.EOF) {
                    throw unexpected();
                }
                body.add(statement());
            }
            end = this.t.end;
            advance();
            f.body = body.toArray(new Ast.Stmt[0]);
        } else {
            f.exprBody = assign(false);
            end = this.prevEnd;
        }
        finishFunc(f, ctx, start, end);
        this.fn = savedFn;
        this.blk = savedBlk;
        return new Ast.FuncExpr(f, false);
    }


    // ------------------------------------------------------------------ expresiones

    private Ast.Expr expression(boolean noIn) {
        Ast.Expr e = assign(noIn);
        if (isP(",")) {
            ArrayList<Ast.Expr> items = new ArrayList<>();
            items.add(e);
            while (eatP(",")) {
                items.add(assign(noIn));
            }
            return new Ast.Seq(items.toArray(new Ast.Expr[0]));
        }
        return e;
    }

    private static int assignOp(String s) {
        switch (s) {
            case "=":
                return 0;
            case "+=":
                return Ast.OP_ADD;
            case "-=":
                return Ast.OP_SUB;
            case "*=":
                return Ast.OP_MUL;
            case "/=":
                return Ast.OP_DIV;
            case "%=":
                return Ast.OP_MOD;
            case "**=":
                return Ast.OP_EXP;
            case "<<=":
                return Ast.OP_SHL;
            case ">>=":
                return Ast.OP_SHR;
            case ">>>=":
                return Ast.OP_USHR;
            case "&=":
                return Ast.OP_BAND;
            case "|=":
                return Ast.OP_BOR;
            case "^=":
                return Ast.OP_BXOR;
            case "&&=":
                return Ast.OP_AND;
            case "||=":
                return Ast.OP_OR;
            case "??=":
                return Ast.OP_NULLISH;
            default:
                return -1;
        }
    }

    /** Cierto si el paréntesis de apertura en {@code i + off} cierra justo antes de un {@code =>}. */
    private boolean arrowAhead(int off) {
        int depth = 0;
        for (int j = this.i + off; j < this.toks.size(); j++) {
            Token k = this.toks.get(j);
            if (k.type == Token.EOF) {
                return false;
            }
            if (k.type != Token.PUNCT) {
                continue;
            }
            String s = k.text;
            if (s.equals("(") || s.equals("[") || s.equals("{")) {
                depth++;
            } else if (s.equals(")") || s.equals("]") || s.equals("}")) {
                depth--;
                if (depth == 0) {
                    Token nx = j + 1 < this.toks.size() ? this.toks.get(j + 1) : null;
                    return nx != null && nx.is("=>") && !nx.nl;
                }
            }
        }
        return false;
    }

    private Ast.Expr assign(boolean noIn) {
        Token k = this.t;
        if (k.type == Token.NAME && !k.escaped) {
            if (k.text.equals("yield") && this.fn.isGen) {
                return yieldExpr(noIn);
            }
            if (k.text.equals("async")) {
                Token n1 = peek(1);
                if (!n1.nl) {
                    if (n1.type == Token.NAME && isIdentifier(n1) && tokIs(peek(2), "=>")) {
                        advance();
                        return arrowFunction(true, k.pos);
                    }
                    if (tokIs(n1, "(") && arrowAhead(1)) {
                        advance();
                        return arrowFunction(true, k.pos);
                    }
                }
            }
            if (isIdentifier(k) && tokIs(peek(1), "=>") && !peek(1).nl) {
                return arrowFunction(false, k.pos);
            }
        } else if (k.type == Token.PUNCT && k.text.equals("(") && arrowAhead(0)) {
            return arrowFunction(false, k.pos);
        }
        Ast.Expr left = conditional(noIn);
        if (this.t.type == Token.PUNCT) {
            int op = assignOp(this.t.text);
            if (op >= 0) {
                Ast.Target tg = toTarget(left, op == 0);
                advance();
                Ast.Expr right = assign(noIn);
                if (tg instanceof Ast.Ident id) {
                    nameAnon(right, id.name);
                }
                Ast.Assign a = new Ast.Assign(op, tg, right);
                a.line = k.line;
                return a;
            }
        }
        return left;
    }

    private Ast.Expr yieldExpr(boolean noIn) {
        advance();
        boolean delegate = false;
        Ast.Expr arg = null;
        if (!this.t.nl && !tokIs(this.t, ")", "]", "}", ",", ";", ":") && this.t.type != Token.EOF && !isKw("in") && !isKw("of")) {
            if (eatP("*")) {
                delegate = true;
            }
            arg = assign(noIn);
        }
        return new Ast.Yield(arg, delegate);
    }

    private Ast.Expr conditional(boolean noIn) {
        Ast.Expr c = binary(1, noIn);
        if (isP("?")) {
            advance();
            Ast.Expr a = assign(false);
            expectP(":");
            Ast.Expr b = assign(noIn);
            return new Ast.Cond(c, a, b);
        }
        return c;
    }

    private int binPrec(Token k, boolean noIn) {
        if (k.type == Token.PUNCT) {
            switch (k.text) {
                case "??":
                    return 1;
                case "||":
                    return 2;
                case "&&":
                    return 3;
                case "|":
                    return 4;
                case "^":
                    return 5;
                case "&":
                    return 6;
                case "==":
                case "!=":
                case "===":
                case "!==":
                    return 7;
                case "<":
                case ">":
                case "<=":
                case ">=":
                    return 8;
                case "<<":
                case ">>":
                case ">>>":
                    return 9;
                case "+":
                case "-":
                    return 10;
                case "*":
                case "/":
                case "%":
                    return 11;
                case "**":
                    return 12;
                default:
                    return -1;
            }
        }
        if (k.type == Token.NAME && !k.escaped) {
            if (k.text.equals("instanceof")) {
                return 8;
            }
            if (k.text.equals("in") && !noIn) {
                return 8;
            }
        }
        return -1;
    }

    private static int binOp(String s) {
        switch (s) {
            case "+":
                return Ast.OP_ADD;
            case "-":
                return Ast.OP_SUB;
            case "*":
                return Ast.OP_MUL;
            case "/":
                return Ast.OP_DIV;
            case "%":
                return Ast.OP_MOD;
            case "**":
                return Ast.OP_EXP;
            case "==":
                return Ast.OP_EQ;
            case "!=":
                return Ast.OP_NE;
            case "===":
                return Ast.OP_SEQ;
            case "!==":
                return Ast.OP_SNE;
            case "<":
                return Ast.OP_LT;
            case ">":
                return Ast.OP_GT;
            case "<=":
                return Ast.OP_LE;
            case ">=":
                return Ast.OP_GE;
            case "&":
                return Ast.OP_BAND;
            case "|":
                return Ast.OP_BOR;
            case "^":
                return Ast.OP_BXOR;
            case "<<":
                return Ast.OP_SHL;
            case ">>":
                return Ast.OP_SHR;
            case ">>>":
                return Ast.OP_USHR;
            case "in":
                return Ast.OP_IN;
            case "instanceof":
                return Ast.OP_INSTANCEOF;
            case "&&":
                return Ast.OP_AND;
            case "||":
                return Ast.OP_OR;
            default:
                return Ast.OP_NULLISH;
        }
    }

    private Ast.Expr binary(int minPrec, boolean noIn) {
        Ast.Expr left;
        if (this.t.type == Token.PRIVATE && peek(1).type == Token.NAME && peek(1).text.equals("in") && !noIn) {
            PrivateName pn = PrivateName.of(this.t.text);
            int line = this.t.line;
            advance();
            int prec = binPrec(this.t, noIn);
            advance();
            Ast.Expr right = binary(prec + 1, noIn);
            left = new Ast.PrivateIn(pn, right);
            left.line = line;
        } else {
            left = unary();
        }
        while (true) {
            int prec = binPrec(this.t, noIn);
            if (prec < 0 || prec < minPrec) {
                return left;
            }
            String op = this.t.text;
            int line = this.t.line;
            advance();
            Ast.Expr right = binary(op.equals("**") ? prec : prec + 1, noIn);
            int code = binOp(op);
            Ast.Expr e = code >= Ast.OP_AND ? new Ast.Logical(code, left, right) : new Ast.Binary(code, left, right);
            e.line = line;
            left = e;
        }
    }

    private Ast.Expr unary() {
        Token k = this.t;
        if (k.type == Token.PUNCT) {
            switch (k.text) {
                case "!":
                    advance();
                    return new Ast.Unary(Ast.U_NOT, unary());
                case "-": {
                    advance();
                    Ast.Expr e = unary();
                    if (e instanceof Ast.Lit l && l.v instanceof Double d) {
                        return new Ast.Lit(-d);
                    }
                    return new Ast.Unary(Ast.U_NEG, e);
                }
                case "+":
                    advance();
                    return new Ast.Unary(Ast.U_POS, unary());
                case "~":
                    advance();
                    return new Ast.Unary(Ast.U_BNOT, unary());
                case "++":
                case "--": {
                    advance();
                    Ast.Expr e = unary();
                    Ast.Update u = new Ast.Update(toTarget(e, false), true, k.text.equals("++") ? 1 : -1);
                    u.line = k.line;
                    return u;
                }
                default:
                    break;
            }
        } else if (k.type == Token.NAME && !k.escaped) {
            switch (k.text) {
                case "typeof":
                    advance();
                    return new Ast.Unary(Ast.U_TYPEOF, unary());
                case "void":
                    advance();
                    return new Ast.Unary(Ast.U_VOID, unary());
                case "delete": {
                    advance();
                    Ast.Unary u = new Ast.Unary(Ast.U_DELETE, unary());
                    u.strict = this.fn.strict;
                    return u;
                }
                case "await":
                    if (this.fn.isAsync) {
                        advance();
                        this.fn.hasAwait = true;
                        return new Ast.Await(unary());
                    }
                    break;
                default:
                    break;
            }
        }
        return postfix();
    }

    private Ast.Expr postfix() {
        Ast.Expr e = leftHandSide();
        if (this.t.type == Token.PUNCT && (this.t.text.equals("++") || this.t.text.equals("--")) && !this.t.nl) {
            int line = this.t.line;
            Ast.Target tg = toTarget(e, false);
            boolean inc = this.t.text.equals("++");
            advance();
            Ast.Update u = new Ast.Update(tg, false, inc ? 1 : -1);
            u.line = line;
            return u;
        }
        return e;
    }

    private String sourceText(int start, int end) {
        String s = this.src.substring(Math.max(0, Math.min(start, this.src.length())), Math.max(0, Math.min(end, this.src.length())));
        s = s.replaceAll("\\s+", " ");
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    private Ast.Expr leftHandSide() {
        int start = this.t.pos;
        Ast.Expr e;
        if (isKw("new")) {
            e = newExpr();
        } else {
            e = primary();
        }
        return callTail(e, true, start);
    }

    private Ast.Expr newExpr() {
        int start = this.t.pos;
        int line = this.t.line;
        advance();
        if (isP(".")) {
            advance();
            if (!this.t.isName("target")) {
                throw unexpected();
            }
            advance();
            return new Ast.NewTarget();
        }
        int calleeStart = this.t.pos;
        Ast.Expr callee = isKw("new") ? newExpr() : primary();
        callee = callTail(callee, false, calleeStart);
        String text = Ast.describe(callee);
        Ast.Expr[] args = Ast.NO_EXPRS;
        boolean spread = false;
        if (isP("(")) {
            ArrayList<Ast.Expr> list = new ArrayList<>();
            spread = arguments(list);
            args = list.toArray(new Ast.Expr[0]);
        }
        Ast.New n = new Ast.New(callee, args, spread, text);
        n.line = line;
        if (start < 0) {
            throw unexpected();
        }
        return n;
    }

    /** Lee {@code (a, b, ...c)} y devuelve si hay algún spread. */
    private boolean arguments(List<Ast.Expr> out) {
        expectP("(");
        boolean spread = false;
        while (!isP(")")) {
            if (eatP("...")) {
                out.add(new Ast.Spread(assign(false)));
                spread = true;
            } else {
                out.add(assign(false));
            }
            if (!isP(")")) {
                expectP(",");
            }
        }
        expectP(")");
        return spread;
    }

    private Ast.Expr callTail(Ast.Expr e, boolean allowCall, int start) {
        boolean chain = false;
        while (true) {
            Token k = this.t;
            if (k.type == Token.PUNCT) {
                if (k.text.equals(".")) {
                    advance();
                    e = dotMember(e, false);
                    continue;
                }
                if (k.text.equals("?.")) {
                    if (!allowCall) {
                        throw err("Invalid optional chain from new expression");
                    }
                    chain = true;
                    advance();
                    if (isP("(")) {
                        ArrayList<Ast.Expr> list = new ArrayList<>();
                        boolean spread = arguments(list);
                        Ast.Call c = new Ast.Call(e, list.toArray(new Ast.Expr[0]), spread, true, Ast.describe(e));
                        c.strict = this.fn.strict;
                        c.line = k.line;
                        e = c;
                    } else if (isP("[")) {
                        advance();
                        Ast.Expr key = expression(false);
                        expectP("]");
                        e = member(e, key, true);
                    } else {
                        e = dotMember(e, true);
                    }
                    continue;
                }
                if (k.text.equals("[")) {
                    advance();
                    Ast.Expr key = expression(false);
                    expectP("]");
                    e = member(e, key, false);
                    continue;
                }
                if (k.text.equals("(") && allowCall) {
                    String text = Ast.describe(e);
                    ArrayList<Ast.Expr> list = new ArrayList<>();
                    boolean spread = arguments(list);
                    Ast.Call c = new Ast.Call(e, list.toArray(new Ast.Expr[0]), spread, false, text);
                    c.strict = this.fn.strict;
                    c.line = k.line;
                    e = c;
                    continue;
                }
            } else if (k.type == Token.TEMPLATE) {
                advance();
                String[] cooked = k.cooked.toArray(new String[0]);
                String[] raws = k.raws.toArray(new String[0]);
                Ast.Expr[] exprs = new Ast.Expr[k.exprs.size()];
                for (int j = 0; j < exprs.length; j++) {
                    exprs[j] = subExpression(k.exprs.get(j));
                }
                e = new Ast.TaggedTemplate(e, cooked, raws, exprs);
                continue;
            }
            break;
        }
        return chain ? new Ast.OptChain(e) : e;
    }

    private Ast.Expr dotMember(Ast.Expr obj, boolean optional) {
        Token k = this.t;
        Object key;
        if (k.type == Token.NAME) {
            key = k.text;
        } else if (k.type == Token.PRIVATE) {
            key = PrivateName.of(k.text);
        } else {
            throw unexpected();
        }
        advance();
        Ast.Member m = new Ast.Member(obj, key, null, optional);
        m.strict = this.fn.strict;
        m.line = k.line;
        return m;
    }

    private Ast.Expr member(Ast.Expr obj, Ast.Expr key, boolean optional) {
        Ast.Member m;
        if (key instanceof Ast.Lit l && l.v instanceof String s && Js.arrayIndex(s) < 0) {
            m = new Ast.Member(obj, s, null, optional);
        } else {
            m = new Ast.Member(obj, null, key, optional);
        }
        m.strict = this.fn.strict;
        m.line = this.t.line;
        return m;
    }

    private Ast.Expr subExpression(List<Token> sub) {
        List<Token> savedToks = this.toks;
        int savedI = this.i;
        Token savedT = this.t;
        int savedPrev = this.prevEnd;
        this.toks = sub;
        this.i = 0;
        this.t = sub.get(0);
        try {
            Ast.Expr e = expression(false);
            if (this.t.type != Token.EOF) {
                throw unexpected();
            }
            return e;
        } finally {
            this.toks = savedToks;
            this.i = savedI;
            this.t = savedT;
            this.prevEnd = savedPrev;
        }
    }

    private Ast.Expr primary() {
        Token k = this.t;
        int line = k.line;
        switch (k.type) {
            case Token.NUM:
                advance();
                return new Ast.Lit(k.num);
            case Token.STR:
                advance();
                return new Ast.Lit(k.text);
            case Token.TEMPLATE: {
                advance();
                String[] strs = new String[k.cooked.size()];
                for (int j = 0; j < strs.length; j++) {
                    String c = k.cooked.get(j);
                    if (c == null) {
                        throw err("Invalid escape sequence in template");
                    }
                    strs[j] = c;
                }
                Ast.Expr[] exprs = new Ast.Expr[k.exprs.size()];
                for (int j = 0; j < exprs.length; j++) {
                    exprs[j] = subExpression(k.exprs.get(j));
                }
                return new Ast.TemplateLit(strs, exprs);
            }
            case Token.REGEX:
                advance();
                return new Ast.RegexLit(k.text, k.flags);
            case Token.PUNCT:
                if (k.text.equals("(")) {
                    advance();
                    Ast.Expr e = expression(false);
                    expectP(")");
                    return e;
                }
                if (k.text.equals("[")) {
                    return arrayLiteral();
                }
                if (k.text.equals("{")) {
                    return objectLiteral();
                }
                throw unexpected();
            case Token.NAME:
                break;
            default:
                throw unexpected();
        }
        if (!k.escaped) {
            switch (k.text) {
                case "this":
                    advance();
                    return new Ast.This();
                case "null":
                    advance();
                    return new Ast.Lit(Js.NULL);
                case "true":
                    advance();
                    return new Ast.Lit(Boolean.TRUE);
                case "false":
                    advance();
                    return new Ast.Lit(Boolean.FALSE);
                case "function": {
                    int start = k.pos;
                    advance();
                    return functionExpr(false, start);
                }
                case "async":
                    if (peek(1).isName("function") && !peek(1).nl) {
                        int start = k.pos;
                        advance();
                        advance();
                        return functionExpr(true, start);
                    }
                    break;
                case "class":
                    return classExpr();
                case "super": {
                    advance();
                    if (isP("(")) {
                        ArrayList<Ast.Expr> list = new ArrayList<>();
                        boolean spread = arguments(list);
                        Ast.SuperCall sc = new Ast.SuperCall(list.toArray(new Ast.Expr[0]), spread);
                        sc.line = line;
                        return sc;
                    }
                    Ast.SuperMember sm;
                    if (eatP(".")) {
                        if (this.t.type != Token.NAME) {
                            throw unexpected();
                        }
                        sm = new Ast.SuperMember(this.t.text, null);
                        advance();
                    } else if (eatP("[")) {
                        Ast.Expr key = expression(false);
                        expectP("]");
                        sm = new Ast.SuperMember(null, key);
                    } else {
                        throw err("'super' keyword unexpected here");
                    }
                    sm.strict = this.fn.strict;
                    return sm;
                }
                case "import":
                    throw err("Cannot use import outside a module");
                case "undefined":
                    advance();
                    return new Ast.Lit(Js.UNDEFINED);
                default:
                    break;
            }
        }
        if (!isIdentifier(k)) {
            throw unexpected();
        }
        advance();
        return ident(k.text);
    }

    private Ast.Expr functionExpr(boolean isAsync, int start) {
        boolean gen = eatP("*");
        String name = null;
        if (!isP("(")) {
            FnCtx tmp = new FnCtx(this.fn, false, isAsync, gen, true, this.fn.strict);
            FnCtx saved = this.fn;
            this.fn = tmp;
            try {
                name = identifier();
            } finally {
                this.fn = saved;
            }
        }
        Ast.FuncNode f = functionRest(name, start, false, isAsync, gen, false);
        return new Ast.FuncExpr(f, name != null);
    }

    private Ast.Expr arrayLiteral() {
        expectP("[");
        ArrayList<Ast.Expr> elems = new ArrayList<>();
        boolean spread = false;
        while (!isP("]")) {
            if (isP(",")) {
                advance();
                elems.add(null);
                continue;
            }
            if (eatP("...")) {
                elems.add(new Ast.Spread(assign(false)));
                spread = true;
            } else {
                elems.add(assign(false));
            }
            if (!isP("]")) {
                expectP(",");
            }
        }
        expectP("]");
        return new Ast.ArrayLit(elems.toArray(new Ast.Expr[0]), spread);
    }

    private boolean nextStartsKey(Token n1) {
        return !tokIs(n1, "(", ":", ",", "}", "=", ";") && n1.type != Token.EOF;
    }

    /** Lee una clave de propiedad y deja el resultado en {@code pkConst}/{@code pkExpr}/{@code pkIdent}. */
    private void propertyKey() {
        this.pkConst = null;
        this.pkExpr = null;
        this.pkIdent = false;
        Token k = this.t;
        switch (k.type) {
            case Token.NAME:
                this.pkConst = k.text;
                this.pkIdent = true;
                advance();
                return;
            case Token.STR:
                this.pkConst = k.text;
                advance();
                return;
            case Token.NUM:
                this.pkConst = NumFmt.toString(k.num);
                advance();
                return;
            case Token.PRIVATE:
                this.pkConst = PrivateName.of(k.text);
                advance();
                return;
            default:
                if (isP("[")) {
                    advance();
                    this.pkExpr = assign(false);
                    expectP("]");
                    return;
                }
                throw unexpected();
        }
    }

    private Ast.Expr objectLiteral() {
        expectP("{");
        ArrayList<Ast.PropDef> props = new ArrayList<>();
        while (!isP("}")) {
            Ast.PropDef p = new Ast.PropDef();
            if (eatP("...")) {
                p.kind = Ast.P_SPREAD;
                p.value = assign(false);
            } else {
                int mstart = this.t.pos;
                boolean isAsync = false;
                boolean isGen = false;
                int kind = Ast.P_INIT;
                if (isKw("async") && nextStartsKey(peek(1)) && !peek(1).nl) {
                    isAsync = true;
                    advance();
                }
                if (isP("*")) {
                    isGen = true;
                    advance();
                }
                if (!isAsync && !isGen && (isKw("get") || isKw("set")) && nextStartsKey(peek(1))) {
                    kind = isKw("get") ? Ast.P_GET : Ast.P_SET;
                    advance();
                }
                Token keyTok = this.t;
                propertyKey();
                p.keyConst = this.pkConst;
                p.keyExpr = this.pkExpr;
                boolean ident = this.pkIdent;
                if (kind == Ast.P_GET || kind == Ast.P_SET) {
                    p.kind = kind;
                    p.fn = functionRest(p.keyConst == null ? "" : String.valueOf(p.keyConst), mstart, true, false, false, false);
                } else if (isP("(")) {
                    p.kind = Ast.P_METHOD;
                    p.fn = functionRest(p.keyConst == null ? "" : String.valueOf(p.keyConst), mstart, true, isAsync, isGen, false);
                } else if (isAsync || isGen) {
                    throw unexpected();
                } else if (eatP(":")) {
                    p.value = assign(false);
                    if (p.keyConst instanceof String ks) {
                        nameAnon(p.value, ks);
                        if (ks.equals("__proto__")) {
                            p.kind = Ast.P_PROTO;
                        }
                    }
                } else {
                    if (!ident || !isIdentifier(keyTok)) {
                        throw unexpected();
                    }
                    Ast.Ident id = ident(keyTok.text);
                    if (isP("=")) {
                        advance();
                        Ast.Expr d = assign(false);
                        nameAnon(d, id.name);
                        p.value = new CoverInit(id, d);
                    } else {
                        p.value = id;
                    }
                }
            }
            props.add(p);
            if (!isP("}")) {
                expectP(",");
            }
        }
        expectP("}");
        return new Ast.ObjectLit(props.toArray(new Ast.PropDef[0]));
    }

    // ------------------------------------------------------------------ clases

    private Ast.ClassExpr classExpr() {
        int start = this.t.pos;
        advance();
        String name = null;
        if (this.t.type == Token.NAME && !isKw("extends") && isIdentifier(this.t)) {
            name = this.t.text;
            advance();
        }
        Ast.ClassNode cn = new Ast.ClassNode();
        cn.name = name;
        cn.source = this.src;
        cn.start = start;
        boolean savedStrict = this.fn.strict;
        this.fn.strict = true;
        this.funcCounter++;
        if (eatKw("extends")) {
            cn.superClass = leftHandSide();
        }
        expectP("{");
        ArrayList<Ast.ClassMember> members = new ArrayList<>();
        Ast.FuncNode ctor = null;
        while (!isP("}")) {
            if (eatP(";")) {
                continue;
            }
            Ast.ClassMember m = new Ast.ClassMember();
            int mstart = this.t.pos;
            boolean isStatic = false;
            if (isKw("static") && !tokIs(peek(1), "(", "=", ";", "}")) {
                advance();
                isStatic = true;
                if (isP("{")) {
                    m.kind = Ast.C_STATIC_BLOCK;
                    m.isStatic = true;
                    m.fn = staticBlock(mstart);
                    members.add(m);
                    continue;
                }
            }
            m.isStatic = isStatic;
            boolean isAsync = false;
            boolean isGen = false;
            int kind = Ast.C_METHOD;
            if (isKw("async") && nextStartsKey(peek(1)) && !peek(1).nl) {
                isAsync = true;
                advance();
            }
            if (isP("*")) {
                isGen = true;
                advance();
            }
            if (!isAsync && !isGen && (isKw("get") || isKw("set")) && nextStartsKey(peek(1))) {
                kind = isKw("get") ? Ast.C_GETTER : Ast.C_SETTER;
                advance();
            }
            propertyKey();
            m.keyConst = this.pkConst;
            m.keyExpr = this.pkExpr;
            if (isP("(")) {
                boolean isCtor = !isStatic && kind == Ast.C_METHOD && "constructor".equals(m.keyConst) && !isAsync && !isGen;
                m.kind = kind;
                Ast.FuncNode f = functionRest(m.keyConst == null ? "" : String.valueOf(m.keyConst), mstart, !isCtor, isAsync, isGen, isCtor);
                if (isCtor) {
                    f.isClassCtor = true;
                    f.isDerived = cn.superClass != null;
                    f.isMethod = false;
                    f.name = name == null ? "" : name;
                    ctor = f;
                    continue;
                }
                m.fn = f;
            } else {
                m.kind = Ast.C_FIELD;
                if (eatP("=")) {
                    m.value = fieldInit();
                    if (m.keyConst instanceof String ks) {
                        nameAnon(m.value, ks);
                    }
                }
                if (!eatP(";") && !isP("}") && !this.t.nl) {
                    throw unexpected();
                }
            }
            members.add(m);
        }
        cn.end = this.t.end;
        advance();
        this.fn.strict = savedStrict;
        cn.members = members.toArray(new Ast.ClassMember[0]);
        cn.ctor = ctor != null ? ctor : defaultCtor(name, cn.superClass != null);
        cn.ctor.source = this.src;
        cn.ctor.start = start;
        cn.ctor.end = cn.end;
        return new Ast.ClassExpr(cn);
    }

    private Ast.FuncNode defaultCtor(String name, boolean derived) {
        Ast.FuncNode f = new Ast.FuncNode();
        f.name = name == null ? "" : name;
        f.isClassCtor = true;
        f.isDerived = derived;
        f.strict = true;
        if (derived) {
            Ast.Ident args = new Ast.Ident("args");
            args.strict = true;
            f.params = new Ast.Target[] {args};
            f.defaults = new Ast.Expr[] {null};
            f.restIndex = 0;
            Ast.Ident use = new Ast.Ident("args");
            use.strict = true;
            f.body = new Ast.Stmt[] {new Ast.ExprStmt(new Ast.SuperCall(new Ast.Expr[] {new Ast.Spread(use)}, true))};
        } else {
            f.simpleParams = new String[0];
        }
        return f;
    }

    private Ast.Expr fieldInit() {
        FnCtx ctx = new FnCtx(this.fn, false, false, false, true, true);
        FnCtx savedFn = this.fn;
        BlockCtx savedBlk = this.blk;
        this.fn = ctx;
        this.blk = new BlockCtx(true);
        try {
            return assign(false);
        } finally {
            this.fn = savedFn;
            this.blk = savedBlk;
        }
    }

    private Ast.FuncNode staticBlock(int start) {
        FnCtx ctx = new FnCtx(this.fn, false, false, false, true, true);
        FnCtx savedFn = this.fn;
        BlockCtx savedBlk = this.blk;
        this.fn = ctx;
        this.blk = new BlockCtx(true);
        this.funcCounter++;
        Ast.FuncNode f = new Ast.FuncNode();
        f.isMethod = true;
        f.simpleParams = new String[0];
        expectP("{");
        ArrayList<Ast.Stmt> body = new ArrayList<>();
        while (!isP("}")) {
            if (this.t.type == Token.EOF) {
                throw unexpected();
            }
            body.add(statement());
        }
        int end = this.t.end;
        advance();
        f.body = body.toArray(new Ast.Stmt[0]);
        finishFunc(f, ctx, start, end);
        this.fn = savedFn;
        this.blk = savedBlk;
        return f;
    }

    // ------------------------------------------------------------------ patrones de asignación

    private Ast.Target toTarget(Ast.Expr e, boolean allowPattern) {
        if (e instanceof Ast.Ident id) {
            return id;
        }
        if (e instanceof Ast.Member m) {
            return m;
        }
        if (e instanceof Ast.SuperMember sm) {
            return sm;
        }
        if (allowPattern && e instanceof Ast.ArrayLit al) {
            ArrayList<Ast.Target> elems = new ArrayList<>();
            ArrayList<Ast.Expr> defaults = new ArrayList<>();
            Ast.Target rest = null;
            for (int j = 0; j < al.elems.length; j++) {
                Ast.Expr el = al.elems[j];
                if (el == null) {
                    elems.add(null);
                    defaults.add(null);
                } else if (el instanceof Ast.Spread sp) {
                    if (j != al.elems.length - 1) {
                        throw err("Rest element must be last element");
                    }
                    rest = toTarget(sp.e, true);
                } else if (el instanceof Ast.Assign a && a.op == 0) {
                    elems.add(a.target);
                    defaults.add(a.value);
                } else {
                    elems.add(toTarget(el, true));
                    defaults.add(null);
                }
            }
            return new Ast.ArrayPattern(elems.toArray(new Ast.Target[0]), defaults.toArray(new Ast.Expr[0]), rest);
        }
        if (allowPattern && e instanceof Ast.ObjectLit ol) {
            ArrayList<Ast.PatProp> props = new ArrayList<>();
            Ast.Target rest = null;
            for (Ast.PropDef p : ol.props) {
                if (p.kind == Ast.P_SPREAD) {
                    rest = toTarget(p.value, true);
                    continue;
                }
                if (p.kind != Ast.P_INIT && p.kind != Ast.P_PROTO) {
                    throw err("Invalid destructuring assignment target");
                }
                Ast.PatProp pp = new Ast.PatProp();
                pp.keyConst = p.keyConst;
                pp.keyExpr = p.keyExpr;
                if (p.value instanceof CoverInit ci) {
                    pp.target = ci.id;
                    pp.dflt = ci.dflt;
                } else if (p.value instanceof Ast.Assign a && a.op == 0) {
                    pp.target = a.target;
                    pp.dflt = a.value;
                } else {
                    pp.target = toTarget(p.value, true);
                }
                props.add(pp);
            }
            return new Ast.ObjectPattern(props.toArray(new Ast.PatProp[0]), rest);
        }
        throw err("Invalid left-hand side in assignment");
    }
}
