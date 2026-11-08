package com.example.beautifuldecor.browser.js;

/** El motor de JavaScript: un mundo ({@link Realm}) y la forma de ejecutar código en él. */
public final class JsEngine {
    private final Realm realm;

    /** Crea un motor nuevo. */
    public JsEngine() {
        this.realm = new Realm();
    }

    public Realm realm() {
        return this.realm;
    }

    /** Ejecuta un script en el ámbito global. Lanza {@link JsException} si el script lanza un error. */
    public Object eval(String source) {
        Realm prev = Realm.CUR.get();
        Realm.CUR.set(this.realm);
        try {
            return this.realm.runProgram(this.realm.parse(source, false));
        } finally {
            Realm.CUR.set(prev);
        }
    }

    /** Define una función global escrita en Java. */
    public void defineFunction(String name, int length, NativeFunction.Fn fn) {
        this.realm.global.setHidden(name, new NativeFunction(this.realm, name, length, fn));
    }

    /** A dónde van los mensajes de console. */
    public void setConsole(Realm.Console console) {
        this.realm.console = console;
    }

    public JsObject global() {
        return this.realm.global;
    }

    public void runJobs() {
        Realm prev = Realm.CUR.get();
        Realm.CUR.set(this.realm);
        try {
            this.realm.runJobs();
        } finally {
            Realm.CUR.set(prev);
        }
    }

    public void close() {
        this.realm.close();
    }
}
