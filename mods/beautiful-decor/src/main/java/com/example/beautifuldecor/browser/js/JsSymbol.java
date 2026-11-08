package com.example.beautifuldecor.browser.js;

/** Un Symbol de JavaScript. */
public final class JsSymbol {
    public static final JsSymbol ITERATOR = new JsSymbol("Symbol.iterator");
    public static final JsSymbol ASYNC_ITERATOR = new JsSymbol("Symbol.asyncIterator");
    public static final JsSymbol HAS_INSTANCE = new JsSymbol("Symbol.hasInstance");
    public static final JsSymbol TO_PRIMITIVE = new JsSymbol("Symbol.toPrimitive");
    public static final JsSymbol TO_STRING_TAG = new JsSymbol("Symbol.toStringTag");
    public static final JsSymbol SPECIES = new JsSymbol("Symbol.species");

    public final String description;

    public JsSymbol(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Symbol(" + (this.description == null ? "" : this.description) + ")";
    }
}
