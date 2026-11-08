package com.example.beautifuldecor.browser.js;

/** Error al leer el código fuente (antes de ejecutarlo). */
public final class JsSyntaxError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public final int line;

    public JsSyntaxError(String message, int line) {
        super(message, null, false, false);
        this.line = line;
    }
}
