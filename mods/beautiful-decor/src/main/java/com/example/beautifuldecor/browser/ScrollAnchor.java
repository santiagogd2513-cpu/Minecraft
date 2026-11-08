package com.example.beautifuldecor.browser;

import java.util.List;

/**
 * Ancla de desplazamiento (V2.10.0): cuando la página cambia de tamaño o de contenido (un script agrega algo arriba, carga
 * una imagen), el navegador recuerda cuál era la primera pieza visible y la vuelve a poner en el mismo lugar de la ventana,
 * para que lo que se está leyendo no salte.
 */
public final class ScrollAnchor {
    /** El elemento de la primera pieza visible. */
    public final Node node;
    /** Cuál de las piezas de ese elemento era (un párrafo largo ocupa varias líneas). */
    public final int ordinal;
    /** Cuánto estaba la pieza por debajo del borde de arriba de la ventana (puede ser negativo). */
    public final int offset;

    private ScrollAnchor(Node node, int ordinal, int offset) {
        this.node = node;
        this.ordinal = ordinal;
        this.offset = offset;
    }

    /** Elige el ancla de la página mostrada con ese desplazamiento, o null si no hace falta (arriba del todo) o no hay dónde anclar. */
    public static ScrollAnchor capture(Page page, float scroll) {
        if (page == null || scroll < 1f) {
            return null;
        }
        List<Page.Item> items = page.items;
        Page.Item best = null;
        for (Page.Item it : items) {
            if (it.kind == Page.RECT || it.node == null || it.y + it.h <= scroll) {
                continue;
            }
            if (best == null || it.y < best.y) {
                best = it;
            }
        }
        if (best == null) {
            return null;
        }
        int ordinal = 0;
        for (Page.Item it : items) {
            if (it == best) {
                break;
            }
            if (it.kind != Page.RECT && it.node == best.node) {
                ordinal++;
            }
        }
        return new ScrollAnchor(best.node, ordinal, Math.round(best.y - scroll));
    }

    /** El desplazamiento que deja el ancla en su lugar en la página nueva, o -1 si el elemento ya no está. */
    public float restore(Page page) {
        if (page == null) {
            return -1f;
        }
        Page.Item found = null;
        int seen = 0;
        for (Page.Item it : page.items) {
            if (it.kind == Page.RECT || it.node != this.node) {
                continue;
            }
            found = it;
            if (seen == this.ordinal) {
                break;
            }
            seen++;
        }
        return found == null ? -1f : found.y - this.offset;
    }
}
