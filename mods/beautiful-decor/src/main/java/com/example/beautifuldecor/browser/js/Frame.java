package com.example.beautifuldecor.browser.js;

/** El contexto de una llamada a función (this, argumentos, valor devuelto...). Las funciones flecha usan el de la función que las contiene. */
final class Frame {
    Object thisVal = Js.UNDEFINED;
    boolean thisInit = true;
    Frame thisFrame;
    Closure func;
    Object[] args = Js.EMPTY;
    Object newTarget = Js.UNDEFINED;
    JsObject home;
    Object ret = Js.UNDEFINED;
    String label;
    Object last = Js.UNDEFINED;
    Coroutine co;
    boolean asyncGen;

    Frame() {
        this.thisFrame = this;
    }
}
