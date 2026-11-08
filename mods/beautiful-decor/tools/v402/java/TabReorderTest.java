import com.example.beautifuldecor.TabReorder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** V4.0.2: prueba de TabReorder con clases que imitan cómo guarda Forge las pestañas (lista de verdad + vista de solo lectura). */
public final class TabReorderTest {
    static int fails;
    static final List<String> MINE = List.of("decor", "tech", "vehicles");

    static void check(boolean cond, String msg) {
        if (!cond) {
            fails++;
            System.out.println("FALLA: " + msg);
        }
    }

    static List<String> start() {
        return new ArrayList<>(List.of("a", "b", "decor", "c", "tech", "vehicles", "d"));
    }

    static final List<String> EXPECTED = List.of("a", "b", "c", "d", "decor", "tech", "vehicles");

    /** Como Forge: la lista de verdad primero y su vista de solo lectura después. */
    static final class RealThenView {
        private static final List<String> SORTED = start();
        private static final List<String> VIEW = Collections.unmodifiableList(SORTED);
    }

    /** Lo mismo pero con la vista declarada antes (el orden de los campos no debe importar). */
    static final class ViewThenReal {
        private static final List<String> VIEW;
        private static final List<String> SORTED;

        static {
            SORTED = start();
            VIEW = Collections.unmodifiableList(SORTED);
        }
    }

    static final class NotFinal {
        private static List<String> tabs = start();
        private static final String NAME = "no soy una lista";
    }

    static final class OnlyImmutable {
        private static final List<String> TABS = List.of("a", "b", "decor", "tech", "vehicles", "d");
    }

    static final class Unrelated {
        private static final List<String> OTHER = new ArrayList<>(List.of("x", "y"));
        private static final String TEXT = "hola";
    }

    static final class AlreadyAtEnd {
        private static final List<String> SORTED = new ArrayList<>(List.of("a", "decor", "tech", "vehicles"));
        private static final List<String> VIEW = Collections.unmodifiableList(SORTED);
    }

    @SuppressWarnings("unchecked")
    static List<String> get(Class<?> holder, String name) throws Exception {
        Field f = holder.getDeclaredField(name);
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    /** La versión anterior (V4.0.1 y antes), para comprobar que esta prueba sí reproduce el aviso del log. */
    @SuppressWarnings("unchecked")
    static void oldMoveToEnd(Class<?> holder) throws Exception {
        for (Field field : holder.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof List<?> list) || !list.contains("decor")) {
                continue;
            }
            List<String> reordered = new ArrayList<>((List<String>) list);
            reordered.removeAll(MINE);
            reordered.addAll(MINE);
            if (!Modifier.isFinal(field.getModifiers())) {
                field.set(null, Collections.unmodifiableList(reordered));
            } else {
                List<String> mutable = (List<String>) list;
                mutable.clear();
                mutable.addAll(reordered);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // el aviso del log, reproducido con la versión anterior
        boolean threw = false;
        try {
            oldMoveToEnd(RealThenView.class);
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        check(threw, "la versión anterior debía fallar con UnsupportedOperationException al tocar la vista (es el aviso del log)");

        // lo nuevo: lista de verdad + vista
        class Case {
            void run(Class<?> holder, String real, String view, String label) throws Exception {
                TabReorder.Result r = TabReorder.moveToEnd(holder, MINE);
                check(r.found() == 2, label + ": debía encontrar 2 listas (" + r.found() + ")");
                check(r.failed().isEmpty(), label + ": no debía fallar nada (" + r.failed() + ")");
                check(get(holder, real).equals(EXPECTED), label + ": la lista de verdad quedó " + get(holder, real));
                check(get(holder, view).equals(EXPECTED), label + ": la vista quedó " + get(holder, view));
            }
        }
        new Case().run(RealThenView.class, "SORTED", "VIEW", "real y luego vista");
        new Case().run(ViewThenReal.class, "SORTED", "VIEW", "vista y luego real");

        // repetirlo no cambia nada
        TabReorder.Result again = TabReorder.moveToEnd(RealThenView.class, MINE);
        check(again.failed().isEmpty() && get(RealThenView.class, "SORTED").equals(EXPECTED), "repetir no debe cambiar el resultado");

        // campo que no es final: se reemplaza por la lista ordenada
        TabReorder.Result nf = TabReorder.moveToEnd(NotFinal.class, MINE);
        check(nf.found() == 1 && nf.failed().isEmpty() && get(NotFinal.class, "tabs").equals(EXPECTED), "campo no final: " + get(NotFinal.class, "tabs"));

        // lista inmutable sin otra lista de verdad: no revienta, avisa con el nombre del campo y deja la lista intacta
        TabReorder.Result im = TabReorder.moveToEnd(OnlyImmutable.class, MINE);
        check(im.found() == 1 && im.failed().equals(List.of("TABS")), "lista inmutable: debía fallar sin excepción (" + im + ")");
        check(get(OnlyImmutable.class, "TABS").equals(List.of("a", "b", "decor", "tech", "vehicles", "d")), "lista inmutable: no debe cambiar");

        // nada que ver con las pestañas
        TabReorder.Result un = TabReorder.moveToEnd(Unrelated.class, MINE);
        check(un.found() == 0 && un.failed().isEmpty() && get(Unrelated.class, "OTHER").equals(List.of("x", "y")), "campos ajenos: no se tocan");

        // ya estaban al final
        TabReorder.Result ae = TabReorder.moveToEnd(AlreadyAtEnd.class, MINE);
        check(ae.found() == 2 && ae.failed().isEmpty() && get(AlreadyAtEnd.class, "SORTED").equals(List.of("a", "decor", "tech", "vehicles")),
                "ya al final: no se toca");

        check(TabReorder.endsWith(List.of("a", "decor", "tech", "vehicles"), MINE), "endsWith positivo");
        check(!TabReorder.endsWith(List.of("decor", "tech", "a", "vehicles"), MINE), "endsWith negativo");
        check(!TabReorder.endsWith(List.of("tech", "vehicles"), MINE), "endsWith con lista corta");

        if (fails > 0) {
            System.exit(1);
        }
        System.out.println("TAB REORDER OK");
    }
}
