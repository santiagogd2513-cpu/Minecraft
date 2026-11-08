package com.example.beautifuldecor.browser.js;

/** Un Map de JavaScript. */
final class JsMap extends JsObject {
    final OrderedTable table = new OrderedTable();

    JsMap(JsObject proto) {
        super(proto);
    }

    @Override
    public String className() {
        return "Map";
    }
}
