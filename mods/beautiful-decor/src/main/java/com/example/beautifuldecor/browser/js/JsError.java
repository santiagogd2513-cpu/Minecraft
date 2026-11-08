package com.example.beautifuldecor.browser.js;

/** Un objeto de error (Error, TypeError...). */
public final class JsError extends JsObject {
    public JsError(JsObject proto) {
        super(proto);
    }

    @Override
    public String className() {
        return "Error";
    }
}
