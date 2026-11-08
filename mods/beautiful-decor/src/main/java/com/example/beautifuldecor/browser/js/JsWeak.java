package com.example.beautifuldecor.browser.js;

import java.util.WeakHashMap;

/** WeakMap / WeakSet / WeakRef: guardan sus claves de forma débil. */
final class JsWeak extends JsObject {
    final String kind;
    final WeakHashMap<Object, Object> map = new WeakHashMap<>();
    Object target;

    JsWeak(JsObject proto, String kind) {
        super(proto);
        this.kind = kind;
    }

    @Override
    public String className() {
        return this.kind;
    }
}
