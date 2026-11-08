package com.example.beautifuldecor.browser.js;

/** Un Set de JavaScript. */
final class JsSet extends JsObject {
    final OrderedTable table = new OrderedTable();

    JsSet(JsObject proto) {
        super(proto);
    }

    @Override
    public String className() {
        return "Set";
    }
}
