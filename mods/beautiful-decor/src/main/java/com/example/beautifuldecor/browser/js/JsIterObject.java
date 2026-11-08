package com.example.beautifuldecor.browser.js;

/** Un iterador nativo (de arreglos, Map, Set, textos...) respaldado por un {@link IterRec}. */
final class JsIterObject extends JsObject {
    final IterRec src;
    private final String tag;
    /** Para console.log: lo que queda por recorrer, sin consumirlo (solo iteradores de Map y Set). */
    java.util.function.Supplier<java.util.List<Object>> preview;
    boolean pairs;

    JsIterObject(JsObject proto, IterRec src, String tag) {
        super(proto);
        this.src = src;
        this.tag = tag;
    }

    @Override
    public String className() {
        return this.tag;
    }
}
