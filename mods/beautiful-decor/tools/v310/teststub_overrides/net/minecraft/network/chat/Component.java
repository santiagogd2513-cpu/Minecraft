package net.minecraft.network.chat;

/** Para las pruebas: el texto es la clave de traducción y sus argumentos ("clave|arg1|arg2"), así se ve qué aviso salió. */
public class Component {
    private final String text;

    private Component(String text) {
        this.text = text;
    }

    public static Component translatable(String key, Object... args) {
        StringBuilder b = new StringBuilder(key);
        for (Object a : args) {
            b.append('|').append(a);
        }
        return new Component(b.toString());
    }

    public String getString() {
        return this.text;
    }
}
