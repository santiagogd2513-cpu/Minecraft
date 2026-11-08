package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;

/** El objeto {@code arguments} de una función. */
final class JsArguments extends JsArray {
    JsArguments(JsObject proto, ArrayList<Object> items) {
        super(proto, items);
    }

    @Override
    public String className() {
        return "Arguments";
    }
}
