package com.example.beautifuldecor.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Página ya acomodada: una lista de piezas con posición (texto, rectángulos, imágenes y controles) lista para dibujar. */
public final class Page {
    public static final int TEXT = 0;
    public static final int RECT = 1;
    public static final int IMAGE = 2;
    public static final int CONTROL = 3;

    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;
    public static final int STRIKE = 8;
    /** Solo el borde del rectángulo (1 px). */
    public static final int OUTLINE = 16;

    public static final int ROLE_TEXT = 0;
    public static final int ROLE_HEADING = 1;
    public static final int ROLE_LINK = 2;
    public static final int ROLE_MUTED = 3;
    public static final int ROLE_CODE = 4;
    public static final int ROLE_MARK = 5;

    public static final int FILL_CODE = 0;
    public static final int FILL_QUOTE = 1;
    public static final int FILL_BORDER = 2;
    public static final int FILL_RULE = 3;
    public static final int FILL_BUTTON = 4;
    public static final int FILL_FIELD = 5;

    public static final class Item {
        public int kind;
        public int x;
        public int y;
        public int w;
        public int h;
        public String text;
        public int flags;
        /** Tamaño del texto en porcentaje (100 = normal). */
        public int scale = 100;
        public int role;
        public int fill;
        /** Enlace al que lleva (ya resuelto), o null. */
        public String href;
        public String src;
        public String alt;
        /** Para que quien dibuja guarde algo ya preparado (no lo usa el motor). */
        public Object cache;
        /** El elemento del que viene esta pieza (el más profundo), o null. Sirve para los clics y los formularios. */
        public Node node;

        public boolean contains(int px, int py) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    /** Zona rectangular de un elemento de bloque (para saber a quién le toca un clic que cae en un hueco). */
    public static final class Box {
        public final Node node;
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        public Box(Node node, int x, int y, int w, int h) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean contains(int px, int py) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    /** El elemento que está bajo el punto (coordenadas de la página), o null si no hay ninguno. */
    public Node nodeAt(int px, int py) {
        for (int i = this.items.size() - 1; i >= 0; i--) {
            Item it = this.items.get(i);
            if (it.kind != RECT && it.node != null && it.contains(px, py)) {
                return it.node;
            }
        }
        Box best = null;
        long bestArea = Long.MAX_VALUE;
        for (Box b : this.boxes) {
            if (b.contains(px, py)) {
                long area = (long) b.w * b.h;
                if (area <= bestArea) {
                    best = b;
                    bestArea = area;
                }
            }
        }
        return best == null ? null : best.node;
    }

    public final List<Item> items = new ArrayList<>();
    /** Zonas de los elementos de bloque, de afuera hacia adentro (el último que contiene un punto es el más profundo). */
    public final List<Box> boxes = new ArrayList<>();
    public int height;
    public int width;
    /** El contador de cambios del documento ({@link Node#mutations()}) cuando se acomodó: sirve para saber si la página ya está al día. */
    public int domVersion;
    public String title = "";
    public final Map<String, Integer> anchors = new HashMap<>();
    /** Direcciones absolutas de las imágenes de la página, en orden de aparición. */
    public final Set<String> imageSrcs = new LinkedHashSet<>();
}
