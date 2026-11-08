package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;

/** Objeto envoltorio de un valor primitivo (new String("a"), Object(5)...). */
public final class JsWrapper extends JsObject {
    public final Object prim;

    public JsWrapper(JsObject proto, Object prim) {
        super(proto);
        this.prim = prim;
    }

    @Override
    public String className() {
        if (this.prim instanceof String) {
            return "String";
        }
        if (this.prim instanceof Boolean) {
            return "Boolean";
        }
        if (this.prim instanceof JsSymbol) {
            return "Symbol";
        }
        return "Number";
    }

    @Override
    public Prop getOwn(Object key) {
        if (this.prim instanceof String s && key instanceof String k) {
            int i = Js.arrayIndex(k);
            if (i >= 0 && i < s.length()) {
                return new Prop(String.valueOf(s.charAt(i)), Prop.E);
            }
            if (k.equals("length")) {
                return new Prop(Js.num(s.length()), 0);
            }
        }
        return super.getOwn(key);
    }

    @Override
    public List<Object> ownKeys() {
        if (this.prim instanceof String s) {
            ArrayList<Object> out = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                out.add(Integer.toString(i));
            }
            out.add("length");
            out.addAll(super.ownKeys());
            return out;
        }
        return super.ownKeys();
    }
}
