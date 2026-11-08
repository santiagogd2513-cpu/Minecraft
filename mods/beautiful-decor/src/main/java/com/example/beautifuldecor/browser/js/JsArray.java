package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Un arreglo. Los huecos se guardan como {@code null} de Java (el null de JavaScript es {@link Js#NULL}). */
public class JsArray extends JsObject {
    private static final int MAX_GAP = 50_000_000;
    public final ArrayList<Object> list;
    boolean frozen;

    public JsArray(JsObject proto) {
        super(proto);
        this.list = new ArrayList<>();
    }

    public JsArray(JsObject proto, ArrayList<Object> list) {
        super(proto);
        this.list = list;
    }

    @Override
    public String className() {
        return "Array";
    }

    public final int length() {
        return this.list.size();
    }

    public final Object getIndex(int i) {
        if (i >= 0 && i < this.list.size()) {
            Object v = this.list.get(i);
            if (v != null) {
                return v;
            }
        }
        return Js.UNDEFINED;
    }

    public final boolean setIndex(int i, Object v) {
        if (this.frozen) {
            return false;
        }
        int n = this.list.size();
        if (i < n) {
            if (this.list.get(i) == null && !this.extensible) {
                return false;
            }
            this.list.set(i, v);
            return true;
        }
        if (!this.extensible) {
            return false;
        }
        if (i - n > MAX_GAP) {
            throw Js.rangeError("Invalid array length");
        }
        this.list.ensureCapacity(i + 1);
        while (this.list.size() < i) {
            this.list.add(null);
        }
        this.list.add(v);
        return true;
    }

    public final void add(Object v) {
        this.list.add(v);
    }

    public final boolean setLength(Object value) {
        double d = Js.toNumber(value);
        if (d < 0 || d != Math.floor(d) || d > 4294967295.0) {
            throw Js.rangeError("Invalid array length");
        }
        if (this.frozen) {
            return false;
        }
        int len = (int) Math.min(d, Integer.MAX_VALUE);
        int n = this.list.size();
        if (len < n) {
            this.list.subList(len, n).clear();
        } else if (len > n) {
            if (len - n > MAX_GAP) {
                throw Js.rangeError("Invalid array length");
            }
            this.list.ensureCapacity(len);
            while (this.list.size() < len) {
                this.list.add(null);
            }
        }
        return true;
    }

    @Override
    public Prop getOwn(Object key) {
        if (key instanceof String s) {
            int i = Js.arrayIndex(s);
            if (i >= 0) {
                if (i < this.list.size()) {
                    Object v = this.list.get(i);
                    if (v != null) {
                        return new Prop(v, this.frozen ? Prop.E : Prop.ALL);
                    }
                }
                return null;
            }
            if (s.equals("length")) {
                return new Prop(Js.num(this.list.size()), this.frozen ? 0 : Prop.W);
            }
        }
        return super.getOwn(key);
    }

    @Override
    public boolean defineOwn(Object key, Prop p) {
        if (key instanceof String s) {
            int i = Js.arrayIndex(s);
            if (i >= 0) {
                if (p.accessor) {
                    throw Js.typeError("Los accesores en índices de arreglo no están soportados");
                }
                return setIndex(i, p.value);
            }
            if (s.equals("length")) {
                return p.accessor || setLength(p.value);
            }
        }
        return super.defineOwn(key, p);
    }

    @Override
    protected boolean writeOwn(Object key, Prop p, Object value) {
        if (key instanceof String s) {
            int i = Js.arrayIndex(s);
            if (i >= 0) {
                return setIndex(i, value);
            }
            if (s.equals("length")) {
                return setLength(value);
            }
        }
        return super.writeOwn(key, p, value);
    }

    @Override
    public boolean deleteOwn(Object key) {
        if (key instanceof String s) {
            int i = Js.arrayIndex(s);
            if (i >= 0) {
                if (this.frozen) {
                    return false;
                }
                if (i < this.list.size()) {
                    this.list.set(i, null);
                }
                return true;
            }
            if (s.equals("length")) {
                return false;
            }
        }
        return super.deleteOwn(key);
    }

    @Override
    public List<Object> ownKeys() {
        ArrayList<Object> out = new ArrayList<>(this.list.size() + 1);
        for (int i = 0; i < this.list.size(); i++) {
            if (this.list.get(i) != null) {
                out.add(Integer.toString(i));
            }
        }
        out.add("length");
        out.addAll(super.ownKeys());
        return out;
    }
}
