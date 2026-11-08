package com.example.beautifuldecor.browser.js;

import java.util.regex.Pattern;

/** Una expresión regular. */
final class JsRegExp extends JsObject {
    /** Una expresión ya traducida a java.util.regex. */
    static final class Compiled {
        final Pattern pattern;
        final String[] names;

        Compiled(Pattern pattern, String[] names) {
            this.pattern = pattern;
            this.names = names;
        }
    }

    final String source;
    final String flags;
    final Compiled compiled;
    final boolean global;
    final boolean sticky;

    JsRegExp(JsObject proto, String source, String flags, Compiled compiled) {
        super(proto);
        this.source = source;
        this.flags = flags;
        this.compiled = compiled;
        this.global = flags.indexOf('g') >= 0;
        this.sticky = flags.indexOf('y') >= 0;
        defineOwn("lastIndex", new Prop(0.0, Prop.W));
    }

    @Override
    public String className() {
        return "RegExp";
    }

    int lastIndex() {
        double d = Js.toInteger(get("lastIndex"));
        return d < 0 ? 0 : (int) Math.min(d, Integer.MAX_VALUE);
    }

    void setLastIndex(int i) {
        put("lastIndex", Js.num(i));
    }
}
