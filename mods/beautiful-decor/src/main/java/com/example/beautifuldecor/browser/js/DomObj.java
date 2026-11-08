package com.example.beautifuldecor.browser.js;

import java.util.HashMap;
import java.util.function.Supplier;

/** Un objeto de JavaScript que envuelve algo del navegador (un nodo, una lista de nodos, una dirección...). */
class DomObj extends JsObject {
    /** Lo envuelto: normalmente un {@link com.example.beautifuldecor.browser.Node}. */
    final Object nat;
    private HashMap<String, Object> cache;

    DomObj(JsObject proto, Object nat) {
        super(proto);
        this.nat = nat;
    }

    /** Un objeto auxiliar que debe ser siempre el mismo (classList, style, dataset...). */
    @SuppressWarnings("unchecked")
    <T> T cached(String key, Supplier<T> make) {
        if (this.cache == null) {
            this.cache = new HashMap<>();
        }
        Object v = this.cache.get(key);
        if (v == null) {
            v = make.get();
            this.cache.put(key, v);
        }
        return (T) v;
    }
}
