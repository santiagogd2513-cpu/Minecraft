package com.example.beautifuldecor.browser;

import java.util.IdentityHashMap;

/**
 * Medidas de la página ya acomodada (V2.10.0), para los scripts: dónde quedó cada elemento (getBoundingClientRect, offsetTop,
 * IntersectionObserver...) y qué elemento hay bajo un punto. Se arma a partir de una {@link Page}; el índice se calcula la primera
 * vez que se pregunta, así que una página que no usa medidas no paga nada.
 */
public final class Geometry {
    private final Page page;
    private IdentityHashMap<Node, int[]> bounds;

    public Geometry(Page page) {
        this.page = page;
    }

    public Page page() {
        return this.page;
    }

    public int width() {
        return this.page.width;
    }

    public int height() {
        return this.page.height;
    }

    /** ¿La página se acomodó con el documento tal como está ahora? */
    public boolean fresh() {
        return this.page.domVersion == Node.mutations();
    }

    /** {x, y, ancho, alto} del elemento en coordenadas de la página, o null si no se dibujó (no existía al acomodar o está oculto). */
    public synchronized int[] rect(Node n) {
        if (n == null) {
            return null;
        }
        if (this.bounds == null) {
            this.bounds = index();
        }
        int[] b = this.bounds.get(n);
        return b == null ? null : new int[] {b[0], b[1], b[2] - b[0], b[3] - b[1]};
    }

    /** El elemento más profundo bajo ese punto de la página, o null. */
    public Node nodeAt(int x, int y) {
        return this.page.nodeAt(x, y);
    }

    private IdentityHashMap<Node, int[]> index() {
        IdentityHashMap<Node, int[]> map = new IdentityHashMap<>();
        for (Page.Box b : this.page.boxes) {
            add(map, b.node, b.x, b.y, b.x + b.w, b.y + b.h);
        }
        for (Page.Item it : this.page.items) {
            if (it.node != null && it.w >= 0 && it.h >= 0) {
                add(map, it.node, it.x, it.y, it.x + it.w, it.y + it.h);
            }
        }
        return map;
    }

    /** Suma el rectángulo al elemento y a todos sus ancestros. */
    private static void add(IdentityHashMap<Node, int[]> map, Node node, int x0, int y0, int x1, int y1) {
        int guard = 0;
        for (Node n = node; n != null && guard++ < 4000; n = n.parent) {
            int[] b = map.get(n);
            if (b == null) {
                map.put(n, new int[] {x0, y0, x1, y1});
            } else {
                if (x0 < b[0]) {
                    b[0] = x0;
                }
                if (y0 < b[1]) {
                    b[1] = y0;
                }
                if (x1 > b[2]) {
                    b[2] = x1;
                }
                if (y1 > b[3]) {
                    b[3] = y1;
                }
            }
        }
    }
}
