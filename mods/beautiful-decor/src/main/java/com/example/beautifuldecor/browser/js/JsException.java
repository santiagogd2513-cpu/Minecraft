package com.example.beautifuldecor.browser.js;

/** Un valor lanzado con {@code throw} en JavaScript (o un error del propio motor, ya convertido en objeto Error). */
public final class JsException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** El valor lanzado. */
    public final transient Object value;

    public JsException(Object value) {
        super(null, null, false, false);
        this.value = value;
    }

    @Override
    public String getMessage() {
        try {
            return Js.describeError(this.value);
        } catch (RuntimeException e) {
            return "error de JavaScript";
        }
    }
}
