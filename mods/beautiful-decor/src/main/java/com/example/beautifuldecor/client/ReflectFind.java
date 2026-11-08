package com.example.beautifuldecor.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Busca campos y métodos privados de Minecraft por su FORMA (tipo y parámetros) y no por su nombre: el juego normal usa nombres internos distintos a los del
 * entorno de desarrollo, y buscar por nombre falla (con un error en el registro cada vez). Si hay más de un candidato no adivina y devuelve null.
 */
final class ReflectFind {
    private ReflectFind() {
    }

    /** El único campo estático de {@code owner} del tipo {@code type} (si alguno se llama {@code preferred}, ese), ya accesible; o null. */
    static Field staticField(Class<?> owner, Class<?> type, String preferred) {
        Field found = null;
        int count = 0;
        for (Field f : owner.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                if (f.getName().equals(preferred)) {
                    found = f;
                    count = 1;
                    break;
                }
                found = f;
                count++;
            }
        }
        return count == 1 ? open(found) : null;
    }

    /** El único método de instancia de {@code owner} sin resultado y con un solo parámetro del tipo {@code param} (si alguno se llama {@code preferred}, ese), accesible; o null. */
    static Method voidMethod(Class<?> owner, Class<?> param, String preferred) {
        Method found = null;
        int count = 0;
        for (Method m : owner.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class && p.length == 1 && p[0] == param) {
                if (m.getName().equals(preferred)) {
                    found = m;
                    count = 1;
                    break;
                }
                found = m;
                count++;
            }
        }
        return count == 1 ? open(found) : null;
    }

    private static Field open(Field f) {
        try {
            f.setAccessible(true);
            return f;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Method open(Method m) {
        try {
            m.setAccessible(true);
            return m;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
