package com.example.beautifuldecor.browser.js;

import java.util.HashMap;

/** Tabla con orden de inserción que tolera cambios mientras se recorre (base de Map y Set). */
final class OrderedTable {
    static final class Entry {
        Object key;
        Object value;
        Entry prev;
        Entry next;
        boolean removed;
    }

    private final HashMap<Object, Entry> index = new HashMap<>();
    private final Entry head = new Entry();
    private Entry tail = this.head;

    static Object norm(Object k) {
        if (k instanceof Number n) {
            double d = n.doubleValue();
            return d == 0 ? Js.num(0.0) : Js.num(d);
        }
        return k;
    }

    int size() {
        return this.index.size();
    }

    Entry find(Object key) {
        return this.index.get(norm(key));
    }

    void put(Object key, Object value) {
        Object k = norm(key);
        Entry e = this.index.get(k);
        if (e != null) {
            e.value = value;
            return;
        }
        e = new Entry();
        e.key = k;
        e.value = value;
        e.prev = this.tail;
        this.tail.next = e;
        this.tail = e;
        this.index.put(k, e);
    }

    boolean remove(Object key) {
        Entry e = this.index.remove(norm(key));
        if (e == null) {
            return false;
        }
        e.removed = true;
        e.prev.next = e.next;
        if (e.next != null) {
            e.next.prev = e.prev;
        } else {
            this.tail = e.prev;
        }
        return true;
    }

    void clear() {
        for (Entry e : this.index.values()) {
            e.removed = true;
            e.prev = this.head;
        }
        this.index.clear();
        this.head.next = null;
        this.tail = this.head;
    }

    /** Un cursor que sigue funcionando aunque se agreguen o quiten elementos. */
    final class Cursor {
        private Entry cur = OrderedTable.this.head;

        /** Un cursor independiente en la misma posición (para mirar lo que queda sin consumirlo). */
        Cursor copy() {
            Cursor n = new Cursor();
            n.cur = this.cur;
            return n;
        }

        /** Siguiente entrada viva, o null al terminar. */
        Entry next() {
            Entry c = this.cur;
            while (c.removed) {
                c = c.prev;
            }
            Entry n = c.next;
            if (n == null) {
                this.cur = c;
                return null;
            }
            this.cur = n;
            return n;
        }
    }

    Cursor cursor() {
        return new Cursor();
    }
}
