package com.example.beautifuldecor.browser.js;

/** Una propiedad: valor o par getter/setter, más sus atributos. */
public final class Prop {
    public static final int W = 1;
    public static final int E = 2;
    public static final int C = 4;
    public static final int ALL = 7;

    public Object value;
    public JsFunction getter;
    public JsFunction setter;
    public int flags;
    public boolean accessor;

    public Prop(Object value, int flags) {
        this.value = value;
        this.flags = flags;
    }

    public Prop(JsFunction getter, JsFunction setter, int flags) {
        this.getter = getter;
        this.setter = setter;
        this.flags = flags & ~W;
        this.accessor = true;
    }

    public boolean enumerable() {
        return (this.flags & E) != 0;
    }

    public boolean writable() {
        return (this.flags & W) != 0;
    }

    public boolean configurable() {
        return (this.flags & C) != 0;
    }
}
