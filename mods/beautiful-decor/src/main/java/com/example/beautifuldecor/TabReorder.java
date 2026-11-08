package com.example.beautifuldecor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V4.0.2: la parte de {@link TabOrder} que no depende de Minecraft (para poder probarla aparte). Busca, entre los campos estáticos de la clase
 * que guarda las pestañas, las listas que contienen la primera pestaña del mod y las reordena para que las del mod queden al final.
 * <p>
 * Forge guarda la lista ordenada en un campo y una vista de solo lectura de esa misma lista en otro. Antes se intentaba vaciar la vista
 * y el aviso «UnsupportedOperationException» cortaba todo; ahora cada campo se trata por separado, a la vista no se le toca nada (se
 * actualiza sola cuando se reordena la lista de verdad) y al final se comprueba el resultado en todos los campos.
 */
public final class TabReorder {
    private TabReorder() {
    }

    /**
     * @param found  cuántas listas contenían las pestañas del mod
     * @param failed los campos que, después de intentarlo, siguen sin tenerlas al final (vacío = todo bien)
     */
    public record Result(int found, List<String> failed) {
    }

    public static <T> Result moveToEnd(Class<?> holder, List<T> last) {
        T anchor = last.get(0);
        for (Field field : holder.getDeclaredFields()) {
            List<T> list = listOf(field, anchor);
            if (list == null || endsWith(list, last)) {
                continue;
            }
            try {
                if (!Modifier.isFinal(field.getModifiers())) {
                    List<T> reordered = new ArrayList<>(list);
                    reordered.removeAll(last);
                    reordered.addAll(last);
                    field.set(null, Collections.unmodifiableList(reordered));
                } else {
                    list.removeAll(last);        // si es de solo lectura, ya la primera operación falla y la lista queda intacta
                    list.addAll(last);
                }
            } catch (UnsupportedOperationException | IllegalAccessException | ClassCastException ignored) {
                // vista de solo lectura (o lista inmutable): se comprueba al final si la lista de verdad quedó bien
            }
        }
        int found = 0;
        List<String> failed = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            List<T> list = listOf(field, anchor);
            if (list != null) {
                found++;
                if (!endsWith(list, last)) {
                    failed.add(field.getName());
                }
            }
        }
        return new Result(found, failed);
    }

    /** El valor del campo, si es una lista estática que contiene {@code anchor}; si no, null. */
    @SuppressWarnings("unchecked")
    private static <T> List<T> listOf(Field field, T anchor) {
        if (!Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
            return null;
        }
        try {
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof List<?> list && list.contains(anchor) ? (List<T>) list : null;
        } catch (RuntimeException | IllegalAccessException e) {
            return null;
        }
    }

    public static <T> boolean endsWith(List<T> list, List<T> tail) {
        int from = list.size() - tail.size();
        return from >= 0 && list.subList(from, list.size()).equals(tail);
    }
}
