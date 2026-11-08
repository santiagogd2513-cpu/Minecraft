package com.example.beautifuldecor.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** ReflectFind con clases de mentira: nombres "internos" (f_1_, m_2_) como en el juego normal, y con los nombres de desarrollo. */
public class ReflectFindTest {
    static class Tab {
    }

    /** Como CreativeModeInventoryScreen en el juego normal: nombres internos. */
    static class Prod {
        private static final Object CONTAINER = new Object();
        private static final String LOCATION = "x";
        private static Tab f_98507_ = new Tab();
        private int scroll;

        private void m_98560_(Tab t) {
            this.scroll = 7;
        }

        private void m_5_(Object a, Tab t) {
        }

        private int m_6_(Tab t) {
            return 1;
        }

        private boolean m_7_(Tab t) {
            return true;
        }
    }

    /** Como en el entorno de desarrollo: nombres de Mojang. */
    static class Dev {
        private static Tab selectedTab = new Tab();
        private static Tab other = new Tab();                // dos candidatos: gana el que se llama "selectedTab"
        private int scroll;

        private void selectTab(Tab t) {
            this.scroll = 9;
        }

        private void refresh(Tab t) {
        }
    }

    /** Ambigua: dos campos y dos métodos posibles, ninguno con el nombre esperado: no se adivina. */
    static class Ambiguous {
        private static Tab a = new Tab();
        private static Tab b = new Tab();

        private void x(Tab t) {
        }

        private void y(Tab t) {
        }
    }

    static class Empty {
        private static Object nothing;
    }

    static int failures;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok  " : "  MAL ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        Field f = ReflectFind.staticField(Prod.class, Tab.class, "selectedTab");
        check(f != null && f.getName().equals("f_98507_") && f.get(null) instanceof Tab, "juego normal: el campo estático del tipo Tab se encuentra aunque se llame f_98507_");
        Method m = ReflectFind.voidMethod(Prod.class, Tab.class, "selectTab");
        Prod p = new Prod();
        check(m != null && m.getName().equals("m_98560_"), "juego normal: el único método void(Tab) se encuentra aunque se llame m_98560_");
        m.invoke(p, new Tab());
        check(p.scroll == 7, "juego normal: el método encontrado se puede llamar");

        f = ReflectFind.staticField(Dev.class, Tab.class, "selectedTab");
        check(f != null && f.getName().equals("selectedTab"), "desarrollo: con dos candidatos gana el que se llama selectedTab");
        m = ReflectFind.voidMethod(Dev.class, Tab.class, "selectTab");
        check(m != null && m.getName().equals("selectTab"), "desarrollo: con dos métodos gana el que se llama selectTab");

        check(ReflectFind.staticField(Ambiguous.class, Tab.class, "selectedTab") == null, "ambiguo: dos campos sin el nombre esperado -> null (no adivina)");
        check(ReflectFind.voidMethod(Ambiguous.class, Tab.class, "selectTab") == null, "ambiguo: dos métodos sin el nombre esperado -> null (no adivina)");
        check(ReflectFind.staticField(Empty.class, Tab.class, "selectedTab") == null, "sin candidatos -> null");
        check(ReflectFind.voidMethod(Empty.class, Tab.class, "selectTab") == null, "sin candidatos -> null (método)");
        System.out.println(failures == 0 ? "REFLECT OK" : "REFLECT FALLO");
        System.exit(failures == 0 ? 0 : 1);
    }
}
