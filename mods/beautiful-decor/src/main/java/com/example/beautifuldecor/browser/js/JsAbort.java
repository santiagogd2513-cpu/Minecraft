package com.example.beautifuldecor.browser.js;

/** El script se detuvo desde fuera (tiempo agotado o motor cerrado): JavaScript no puede atraparlo con try/catch. */
public final class JsAbort extends Error {
    private static final long serialVersionUID = 1L;

    public JsAbort(String message) {
        super(message, null, false, false);
    }
}
