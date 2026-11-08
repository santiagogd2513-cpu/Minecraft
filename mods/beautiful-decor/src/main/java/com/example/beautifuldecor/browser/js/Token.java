package com.example.beautifuldecor.browser.js;

import java.util.List;

/** Una pieza del código fuente. */
final class Token {
    static final int EOF = 0;
    static final int NUM = 1;
    static final int STR = 2;
    static final int TEMPLATE = 3;
    static final int REGEX = 4;
    static final int NAME = 5;
    static final int PUNCT = 6;
    static final int PRIVATE = 7;

    int type;
    String text = "";
    double num;
    int pos;
    int end;
    int line;
    /** Hay un salto de línea entre el token anterior y este. */
    boolean nl;
    /** El texto o nombre usó secuencias de escape (no vale como directiva ni palabra reservada). */
    boolean escaped;
    /** Plantillas: textos ya interpretados (null si hay un escape inválido), textos crudos y las expresiones de cada ${}. */
    List<String> cooked;
    List<String> raws;
    List<List<Token>> exprs;
    /** Regex: banderas. */
    String flags = "";

    boolean is(String p) {
        return this.type == PUNCT && this.text.equals(p);
    }

    boolean isName(String n) {
        return this.type == NAME && !this.escaped && this.text.equals(n);
    }

    @Override
    public String toString() {
        return this.type == EOF ? "fin del código" : this.text;
    }
}
