package com.example.beautifuldecor.browser.js;

/** Un objeto generador (resultado de llamar a una {@code function*}). */
final class JsGenerator extends JsObject {
    private final Coroutine co;
    private boolean running;

    JsGenerator(JsObject proto, Coroutine co) {
        super(proto);
        this.co = co;
    }

    @Override
    public String className() {
        return "Generator";
    }

    Object resume(int mode, Object v) {
        if (this.running) {
            throw Js.typeError("Generator is already running");
        }
        if (this.co.finished) {
            if (mode == Coroutine.THROW) {
                throw new JsException(v);
            }
            return Js.iterResult(mode == Coroutine.RETURN ? v : Js.UNDEFINED, true);
        }
        if (!this.co.started()) {
            if (mode == Coroutine.THROW) {
                this.co.abandon();
                throw new JsException(v);
            }
            if (mode == Coroutine.RETURN) {
                this.co.abandon();
                return Js.iterResult(v, true);
            }
        }
        this.running = true;
        try {
            Object r = this.co.resume(mode, v);
            return Js.iterResult(r, this.co.finished);
        } finally {
            this.running = false;
        }
    }
}
