package com.example.beautifuldecor.browser.js;

import java.util.HashMap;

/** Nombre privado de clase ({@code #x}): clave de propiedad que no aparece al enumerar. */
final class PrivateName {
    private static final HashMap<String, PrivateName> TABLE = new HashMap<>();
    final String name;

    private PrivateName(String name) {
        this.name = name;
    }

    static synchronized PrivateName of(String name) {
        return TABLE.computeIfAbsent(name, PrivateName::new);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
