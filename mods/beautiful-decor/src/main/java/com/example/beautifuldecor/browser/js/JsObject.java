package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Un objeto de JavaScript. Los objetos "exóticos" (arreglos, objetos del DOM...) sobrescriben los métodos de propiedades propias. */
public class JsObject {
    public JsObject proto;
    LinkedHashMap<Object, Prop> props;
    boolean extensible = true;
    private boolean indexKeys;

    public JsObject(JsObject proto) {
        this.proto = proto;
    }

    /** Nombre para Object.prototype.toString ("Object", "Array", "Date"...). */
    public String className() {
        return "Object";
    }

    public boolean isExtensible() {
        return this.extensible;
    }

    public void preventExtensions() {
        this.extensible = false;
    }

    // ------------------------------------------------------------------ propiedades propias

    public Prop getOwn(Object key) {
        return this.props == null ? null : this.props.get(key);
    }

    /** Pone o reemplaza una propiedad propia sin más validación; falso si el objeto no admite propiedades nuevas. */
    public boolean defineOwn(Object key, Prop p) {
        if (this.props == null) {
            this.props = new LinkedHashMap<>();
        }
        if (!this.extensible && !this.props.containsKey(key)) {
            return false;
        }
        if (!this.indexKeys && key instanceof String s && Js.arrayIndex(s) >= 0) {
            this.indexKeys = true;
        }
        this.props.put(key, p);
        return true;
    }

    public boolean deleteOwn(Object key) {
        if (this.props == null) {
            return true;
        }
        Prop p = this.props.get(key);
        if (p == null) {
            return true;
        }
        if ((p.flags & Prop.C) == 0) {
            return false;
        }
        this.props.remove(key);
        return true;
    }

    /** Todas las claves propias (también las no enumerables): índices en orden, luego textos, luego símbolos. */
    public List<Object> ownKeys() {
        ArrayList<Object> out = new ArrayList<>();
        if (this.props == null) {
            return out;
        }
        ArrayList<String> idx = null;
        ArrayList<Object> syms = null;
        for (Object k : this.props.keySet()) {
            if (k instanceof String s) {
                if (this.indexKeys && Js.arrayIndex(s) >= 0) {
                    if (idx == null) {
                        idx = new ArrayList<>();
                    }
                    idx.add(s);
                } else {
                    out.add(s);
                }
            } else if (k instanceof JsSymbol) {
                if (syms == null) {
                    syms = new ArrayList<>();
                }
                syms.add(k);
            }
        }
        if (idx != null) {
            idx.sort((a, b) -> Integer.compare(Js.arrayIndex(a), Js.arrayIndex(b)));
            out.addAll(0, idx);
        }
        if (syms != null) {
            out.addAll(syms);
        }
        return out;
    }

    // ------------------------------------------------------------------ acceso

    public final Object get(Object key) {
        return get(key, this);
    }

    public Object get(Object key, Object receiver) {
        for (JsObject o = this; o != null; o = o.proto) {
            Prop p = o.getOwn(key);
            if (p != null) {
                if (!p.accessor) {
                    return p.value;
                }
                return p.getter == null ? Js.UNDEFINED : p.getter.call(receiver, Js.EMPTY);
            }
        }
        return Js.UNDEFINED;
    }

    /** Busca la propiedad en esta cadena de prototipos. */
    public final Prop findProp(Object key) {
        for (JsObject o = this; o != null; o = o.proto) {
            Prop p = o.getOwn(key);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    public boolean hasProperty(Object key) {
        for (JsObject o = this; o != null; o = o.proto) {
            if (o.getOwn(key) != null) {
                return true;
            }
        }
        return false;
    }

    public final boolean hasOwn(Object key) {
        return getOwn(key) != null;
    }

    /** Asignación ordinaria ({@code obj[key] = value}); falso si no se pudo (propiedad de solo lectura, sin setter...). */
    public boolean set(Object key, Object value, Object receiver) {
        for (JsObject o = this; o != null; o = o.proto) {
            Prop p = o.getOwn(key);
            if (p != null) {
                if (p.accessor) {
                    if (p.setter == null) {
                        return false;
                    }
                    p.setter.call(receiver, new Object[] {value});
                    return true;
                }
                if ((p.flags & Prop.W) == 0) {
                    return false;
                }
                if (o == receiver) {
                    return o.writeOwn(key, p, value);
                }
                break;
            }
        }
        if (receiver instanceof JsObject r) {
            Prop mine = r == this ? null : r.getOwn(key);
            if (mine != null) {
                return !mine.accessor && (mine.flags & Prop.W) != 0 && r.writeOwn(key, mine, value);
            }
            return r.defineOwn(key, new Prop(value, Prop.ALL));
        }
        return false;
    }

    /** Cambia el valor de una propiedad propia de datos ya existente. */
    protected boolean writeOwn(Object key, Prop p, Object value) {
        p.value = value;
        return true;
    }

    public final void put(Object key, Object value) {
        set(key, value, this);
    }

    public boolean delete(Object key) {
        return deleteOwn(key);
    }

    // ------------------------------------------------------------------ atajos

    /** Propiedad normal (se puede enumerar, escribir y borrar). */
    public final void setData(Object key, Object value) {
        defineOwn(key, new Prop(value, Prop.ALL));
    }

    /** Propiedad oculta (como los métodos de las clases): no se enumera. */
    public final void setHidden(Object key, Object value) {
        defineOwn(key, new Prop(value, Prop.W | Prop.C));
    }

    /** Propiedad fija: ni se enumera, ni se escribe, ni se borra. */
    public final void setConst(Object key, Object value) {
        defineOwn(key, new Prop(value, 0));
    }
}
