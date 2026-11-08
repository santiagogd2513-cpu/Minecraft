package com.example.beautifuldecor.browser.js;

/** Un iterador en uso desde Java: {@link #next()} da el valor siguiente o {@link #DONE}. */
public abstract class IterRec {
    public static final Object DONE = new Object();

    public abstract Object next();

    /** Cierra el iterador antes de tiempo (llama a {@code return()} del iterador de JavaScript). */
    public void close() {
    }
}
