package com.example.beautifuldecor.browser.js;

import java.util.HashMap;

/** Un ámbito de variables. */
final class Scope {
    /** Una variable. */
    static final class Binding {
        Object value;
        boolean mutable = true;
        boolean init = true;
    }

    final Scope parent;
    final Frame frame;
    final Realm realm;
    HashMap<String, Binding> vars;

    Scope(Scope parent, Frame frame, Realm realm) {
        this.parent = parent;
        this.frame = frame;
        this.realm = realm;
    }

    Binding find(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.vars != null) {
                Binding b = s.vars.get(name);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    Scope findScope(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.vars != null && s.vars.containsKey(name)) {
                return s;
            }
        }
        return null;
    }

    /** Declara (o redeclara) una variable inicializada. */
    Binding declare(String name, Object value) {
        if (this.vars == null) {
            this.vars = new HashMap<>();
        }
        Binding b = this.vars.get(name);
        if (b == null) {
            b = new Binding();
            this.vars.put(name, b);
        }
        b.value = value;
        b.init = true;
        return b;
    }

    /** Declara una variable {@code var}: si ya existe no le toca el valor. */
    void declareVar(String name) {
        if (this.vars == null) {
            this.vars = new HashMap<>();
        }
        if (!this.vars.containsKey(name)) {
            Binding b = new Binding();
            b.value = Js.UNDEFINED;
            this.vars.put(name, b);
        }
    }

    /** Declara let/const/class: no se puede usar hasta que se ejecute su declaración. */
    void declareLex(String name, boolean isConst) {
        if (this.vars == null) {
            this.vars = new HashMap<>();
        }
        Binding b = new Binding();
        b.value = Js.UNDEFINED;
        b.init = false;
        b.mutable = !isConst;
        this.vars.put(name, b);
    }
}
