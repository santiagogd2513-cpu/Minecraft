package com.example.beautifuldecor.browser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nodo del árbol HTML: un elemento (tag != null) o un trozo de texto (tag == null). */
public final class Node {
    public final String tag;
    /** Texto de un nodo de texto, o los datos de un comentario. Solo cambia desde el DOM de JavaScript. */
    public String text;
    public final Map<String, String> attrs;
    public final List<Node> children = new ArrayList<>();
    public Node parent;
    /** El objeto de JavaScript que representa a este nodo (lo pone el puente DOM; el navegador no lo mira). */
    public Object host;
    /** Valor escrito en un campo (null = nadie lo ha tocado: vale el atributo value o el texto de adentro). */
    public String formValue;
    /** Casilla o botón de opción (o {@code <option>}) marcado a mano (null = vale el atributo checked/selected). */
    public Boolean formChecked;
    /** Dónde está el cursor dentro del valor de un campo de texto (-1 = al final). */
    public int caret = -1;
    /** Donde empezó la selección de texto de un campo (-1 = no hay selección; la selección va de aquí al cursor). */
    public int selAnchor = -1;
    /** Solo en el nodo del documento: el documento tiene scripts en marcha (entonces {@code <noscript>} no se muestra). */
    public boolean scripting;

    private static volatile int mutations;

    /** Sube cada vez que un script cambia algo del documento (el navegador lo mira para volver a acomodar la página). */
    public static int mutations() {
        return mutations;
    }

    public static synchronized void touch() {
        mutations++;
    }

    private Node(String tag, String text, Map<String, String> attrs) {
        this.tag = tag;
        this.text = text;
        this.attrs = attrs;
    }

    public static Node element(String tag, Map<String, String> attrs) {
        return new Node(tag, null, attrs == null ? new LinkedHashMap<>() : attrs);
    }

    public static Node text(String text) {
        return new Node(null, text, null);
    }

    /** Un comentario de HTML (solo existe si un script lo crea). */
    public static Node comment(String data) {
        return new Node("#comment", data, new LinkedHashMap<>());
    }

    /** Un fragmento de documento: contenedor temporal cuyos hijos se pasan a otro nodo. */
    public static Node fragment() {
        return Node.element("#fragment", null);
    }

    public boolean isText() {
        return this.tag == null;
    }

    public boolean isComment() {
        return "#comment".equals(this.tag);
    }

    /** Un elemento de verdad (ni texto, ni comentario, ni documento, ni fragmento). */
    public boolean isElement() {
        return this.tag != null && !this.tag.startsWith("#");
    }

    public boolean is(String name) {
        return name.equals(this.tag);
    }

    public String attr(String name) {
        return this.attrs == null ? null : this.attrs.get(name);
    }

    private String[] classCache;

    public boolean hasAttr(String name) {
        return this.attrs != null && this.attrs.containsKey(name);
    }

    public void setAttr(String name, String value) {
        if (this.attrs == null) {
            return;
        }
        this.attrs.put(name, value);
        if (name.equals("class")) {
            this.classCache = null;
        }
    }

    public void removeAttr(String name) {
        if (this.attrs == null) {
            return;
        }
        this.attrs.remove(name);
        if (name.equals("class")) {
            this.classCache = null;
        }
    }

    /** Clases del elemento (se calculan una sola vez). */
    public String[] classes() {
        if (this.classCache == null) {
            String value = attr("class");
            this.classCache = value == null || value.isBlank() ? new String[0] : value.trim().split("\\s+");
        }
        return this.classCache;
    }

    public boolean hasClass(String cls) {
        for (String part : classes()) {
            if (part.equals(cls)) {
                return true;
            }
        }
        return false;
    }

    public void add(Node child) {
        child.parent = this;
        this.children.add(child);
    }

    /** Mete el hijo en esa posición (0 = primero). */
    public void insert(int index, Node child) {
        child.parent = this;
        this.children.add(Math.max(0, Math.min(index, this.children.size())), child);
    }

    /** Saca el hijo (si lo es) y devuelve su posición anterior, o -1. */
    public int removeChild(Node child) {
        int i = indexOfChild(child);
        if (i >= 0) {
            this.children.remove(i);
            child.parent = null;
        }
        return i;
    }

    public int indexOfChild(Node child) {
        for (int i = 0; i < this.children.size(); i++) {
            if (this.children.get(i) == child) {
                return i;
            }
        }
        return -1;
    }

    public Node root() {
        Node n = this;
        while (n.parent != null) {
            n = n.parent;
        }
        return n;
    }

    /** Valor actual de un campo: lo escrito a mano, o el atributo value, o el texto de adentro (textarea) o del option. */
    public String currentValue() {
        if (this.formValue != null) {
            return this.formValue;
        }
        if (is("textarea")) {
            return rawText();
        }
        if (is("option")) {
            String v = attr("value");
            return v != null ? v : textContent();
        }
        String v = attr("value");
        return v == null ? "" : v;
    }

    /** Si una casilla, botón de opción u {@code <option>} está marcado. */
    public boolean isChecked() {
        if (this.formChecked != null) {
            return this.formChecked;
        }
        return attr(is("option") ? "selected" : "checked") != null;
    }

    /** Primer descendiente con esa etiqueta (en orden del documento) o null. */
    public Node find(String name) {
        for (Node child : this.children) {
            if (child.isText()) {
                continue;
            }
            if (child.tag.equals(name)) {
                return child;
            }
            Node deeper = child.find(name);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    /** Todo el texto de adentro, sin formato (los espacios se juntan). */
    public String textContent() {
        StringBuilder sb = new StringBuilder();
        collectText(this, sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static void collectText(Node n, StringBuilder sb) {
        if (n.isText()) {
            sb.append(n.text);
            return;
        }
        if (n.tag.equals("script") || n.tag.equals("style")) {
            return;
        }
        for (Node c : n.children) {
            collectText(c, sb);
        }
    }

    /** Todo el texto de adentro tal cual, también el de script y style (sirve para sacar las hojas de estilo). */
    public String rawText() {
        StringBuilder sb = new StringBuilder();
        for (Node c : this.children) {
            if (c.isText()) {
                sb.append(c.text);
            } else {
                sb.append(c.rawText());
            }
        }
        return sb.toString();
    }
}
