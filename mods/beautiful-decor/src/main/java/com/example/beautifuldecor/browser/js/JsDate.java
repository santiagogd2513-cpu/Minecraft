package com.example.beautifuldecor.browser.js;

/** Un objeto Date: guarda milisegundos desde 1970 (NaN = fecha inválida). */
final class JsDate extends JsObject {
    double time;

    JsDate(JsObject proto, double time) {
        super(proto);
        this.time = time;
    }

    @Override
    public String className() {
        return "Date";
    }
}
