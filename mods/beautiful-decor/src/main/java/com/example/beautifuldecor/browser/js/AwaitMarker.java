package com.example.beautifuldecor.browser.js;

/** Dentro de un generador async, distingue un {@code await} (esto) de un {@code yield} (valor normal). */
final class AwaitMarker {
    final Object value;

    AwaitMarker(Object value) {
        this.value = value;
    }
}
