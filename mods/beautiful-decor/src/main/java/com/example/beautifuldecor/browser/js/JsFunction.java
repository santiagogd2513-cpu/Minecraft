package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Una función invocable de JavaScript. */
public abstract class JsFunction extends JsObject {
    String fname = "";
    int flen;

    protected JsFunction(JsObject proto) {
        super(proto);
    }

    @Override
    public String className() {
        return "Function";
    }

    public abstract Object call(Object thiz, Object[] args);

    public Object construct(Object[] args, JsObject newTarget) {
        throw Js.typeError((this.fname.isEmpty() ? "anonymous" : this.fname) + " is not a constructor");
    }

    public boolean isConstructor() {
        return false;
    }

    public final String name() {
        return this.fname;
    }

    public final void setName(String name) {
        this.fname = name;
    }

    @Override
    public Prop getOwn(Object key) {
        Prop p = super.getOwn(key);
        if (p == null && key instanceof String s) {
            if (s.equals("name")) {
                return new Prop(this.fname, Prop.C);
            }
            if (s.equals("length")) {
                return new Prop(Js.num(this.flen), Prop.C);
            }
        }
        return p;
    }

    @Override
    public List<Object> ownKeys() {
        ArrayList<Object> out = new ArrayList<>();
        out.add("length");
        out.add("name");
        for (Object k : super.ownKeys()) {
            if (!"length".equals(k) && !"name".equals(k)) {
                out.add(k);
            }
        }
        return out;
    }
}
