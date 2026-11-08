package com.example.beautifuldecor.browser.js;

import java.util.concurrent.ThreadLocalRandom;

/** Number, Boolean, Symbol y Math. */
final class LibNumber {
    private LibNumber() {
    }

    private static double thisNum(Object t, String name) {
        if (t instanceof Double d) {
            return d;
        }
        if (t instanceof JsWrapper w && w.prim instanceof Double d) {
            return d;
        }
        throw Js.typeError("Number.prototype." + name + " requires that 'this' be a Number");
    }

    private static JsSymbol thisSym(Object t) {
        if (t instanceof JsSymbol s) {
            return s;
        }
        if (t instanceof JsWrapper w && w.prim instanceof JsSymbol s) {
            return s;
        }
        throw Js.typeError("Symbol.prototype method requires that 'this' be a Symbol");
    }

    static double round(double x) {
        if (x != x || Double.isInfinite(x)) {
            return x;
        }
        if (x == 0) {
            return x;
        }
        double f = Math.floor(x);
        double r = x - f >= 0.5 ? f + 1 : f;
        if (r == 0 && x < 0) {
            return -0.0;
        }
        return r;
    }

    static void install(Realm r) {
        installNumber(r);
        installBoolean(r);
        installSymbol(r);
        installMath(r);
    }

    private static void installNumber(Realm r) {
        JsObject np = r.numberProto;
        NativeFunction number = Lib.ctor(r, "Number", 1, np, (t, a) -> a.length == 0 ? 0.0 : Js.toNumber(a[0]),
                (a, nt) -> new JsWrapper(Lib.protoFrom(nt, np), a.length == 0 ? 0.0 : Js.toNumber(a[0])));
        number.setConst("MAX_SAFE_INTEGER", 9007199254740991.0);
        number.setConst("MIN_SAFE_INTEGER", -9007199254740991.0);
        number.setConst("MAX_VALUE", Double.MAX_VALUE);
        number.setConst("MIN_VALUE", Double.MIN_VALUE);
        number.setConst("EPSILON", Math.ulp(1.0));
        number.setConst("NaN", Double.NaN);
        number.setConst("POSITIVE_INFINITY", Double.POSITIVE_INFINITY);
        number.setConst("NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY);
        Lib.method(r, number, "isInteger", 1, (t, a) -> Js.arg(a, 0) instanceof Double d && !d.isInfinite() && d == Math.floor(d));
        Lib.method(r, number, "isSafeInteger", 1,
                (t, a) -> Js.arg(a, 0) instanceof Double d && !d.isInfinite() && d == Math.floor(d) && Math.abs(d) <= 9007199254740991.0);
        Lib.method(r, number, "isFinite", 1, (t, a) -> Js.arg(a, 0) instanceof Double d && !d.isInfinite() && !d.isNaN());
        Lib.method(r, number, "isNaN", 1, (t, a) -> Js.arg(a, 0) instanceof Double d && d.isNaN());
        NativeFunction pf = Lib.fn(r, "parseFloat", 1, (t, a) -> NumFmt.parseFloat(Js.toStr(Js.arg(a, 0))));
        NativeFunction pi = Lib.fn(r, "parseInt", 2, (t, a) -> {
            String s = Js.toStr(Js.arg(a, 0));
            int radix = Js.toInt32(Js.arg(a, 1));
            return NumFmt.parseInt(s, radix);
        });
        number.setHidden("parseFloat", pf);
        number.setHidden("parseInt", pi);
        r.global.setHidden("parseFloat", pf);
        r.global.setHidden("parseInt", pi);
        Lib.method(r, np, "toString", 1, (t, a) -> {
            double d = thisNum(t, "toString");
            Object rd = Js.arg(a, 0);
            if (rd == Js.UNDEFINED) {
                return NumFmt.toString(d);
            }
            double radix = Js.toInteger(rd);
            if (radix < 2 || radix > 36) {
                throw Js.rangeError("toString() radix argument must be between 2 and 36");
            }
            return radix == 10 ? NumFmt.toString(d) : NumFmt.toRadix(d, (int) radix);
        });
        Lib.method(r, np, "toFixed", 1, (t, a) -> {
            double d = thisNum(t, "toFixed");
            double f = Js.toInteger(Js.arg(a, 0));
            if (f < 0 || f > 100) {
                throw Js.rangeError("toFixed() digits argument must be between 0 and 100");
            }
            return NumFmt.toFixed(d, (int) f);
        });
        Lib.method(r, np, "toPrecision", 1, (t, a) -> {
            double d = thisNum(t, "toPrecision");
            if (Js.arg(a, 0) == Js.UNDEFINED) {
                return NumFmt.toString(d);
            }
            double p = Js.toInteger(a[0]);
            if (d != d || Double.isInfinite(d)) {
                return NumFmt.toString(d);
            }
            if (p < 1 || p > 100) {
                throw Js.rangeError("toPrecision() argument must be between 1 and 100");
            }
            return NumFmt.toPrecision(d, (int) p);
        });
        Lib.method(r, np, "toExponential", 1, (t, a) -> {
            double d = thisNum(t, "toExponential");
            Object fa = Js.arg(a, 0);
            double f = fa == Js.UNDEFINED ? -1 : Js.toInteger(fa);
            if (d != d || Double.isInfinite(d)) {
                return NumFmt.toString(d);
            }
            if (fa != Js.UNDEFINED && (f < 0 || f > 100)) {
                throw Js.rangeError("toExponential() argument must be between 0 and 100");
            }
            return NumFmt.toExponential(d, (int) f);
        });
        Lib.method(r, np, "valueOf", 0, (t, a) -> thisNum(t, "valueOf"));
        Lib.method(r, np, "toLocaleString", 0, (t, a) -> localeNumber(thisNum(t, "toLocaleString"), Js.arg(a, 1)));
    }

    /** {@code toLocaleString} / {@code Intl.NumberFormat} en inglés de EE. UU. con las opciones más usadas. */
    static String localeNumber(double d, Object options) {
        int minFrac = 0;
        int maxFrac = 3;
        boolean grouping = true;
        String style = "decimal";
        String currency = "USD";
        if (options instanceof JsObject o) {
            Object st = o.get("style");
            if (st instanceof String s) {
                style = s;
            }
            if (style.equals("currency")) {
                minFrac = 2;
                maxFrac = 2;
                Object cu = o.get("currency");
                if (cu instanceof String s) {
                    currency = s.toUpperCase(java.util.Locale.ROOT);
                }
            } else if (style.equals("percent")) {
                maxFrac = 0;
            }
            Object mn = o.get("minimumFractionDigits");
            Object mx = o.get("maximumFractionDigits");
            if (mn != Js.UNDEFINED) {
                minFrac = (int) Math.max(0, Math.min(20, Js.toInteger(mn)));
                if (maxFrac < minFrac) {
                    maxFrac = minFrac;
                }
            }
            if (mx != Js.UNDEFINED) {
                maxFrac = (int) Math.max(0, Math.min(20, Js.toInteger(mx)));
                if (minFrac > maxFrac) {
                    minFrac = maxFrac;
                }
            }
            Object ug = o.get("useGrouping");
            if (ug != Js.UNDEFINED) {
                grouping = Js.truthy(ug);
            }
        }
        double v = style.equals("percent") ? d * 100 : d;
        String num = NumFmt.localeString(v, minFrac, maxFrac, grouping);
        switch (style) {
            case "percent":
                return num + "%";
            case "currency": {
                String sym = currency.equals("USD") ? "$" : currency.equals("EUR") ? "€" : currency.equals("GBP") ? "£"
                        : currency.equals("JPY") ? "¥" : currency + " ";
                return num.startsWith("-") ? "-" + sym + num.substring(1) : sym + num;
            }
            default:
                return num;
        }
    }

    private static void installBoolean(Realm r) {
        JsObject bp = r.booleanProto;
        Lib.ctor(r, "Boolean", 1, bp, (t, a) -> Js.truthy(Js.arg(a, 0)), (a, nt) -> new JsWrapper(Lib.protoFrom(nt, bp), Js.truthy(Js.arg(a, 0))));
        Lib.method(r, bp, "toString", 0, (t, a) -> Js.toStr(thisBool(t)));
        Lib.method(r, bp, "valueOf", 0, (t, a) -> thisBool(t));
    }

    private static Boolean thisBool(Object t) {
        if (t instanceof Boolean b) {
            return b;
        }
        if (t instanceof JsWrapper w && w.prim instanceof Boolean b) {
            return b;
        }
        throw Js.typeError("Boolean.prototype method requires that 'this' be a Boolean");
    }

    private static void installSymbol(Realm r) {
        JsObject sp = r.symbolProto;
        NativeFunction symbol = Lib.ctor(r, "Symbol", 0, sp, (t, a) -> new JsSymbol(Js.arg(a, 0) == Js.UNDEFINED ? null : Js.toStr(a[0])), null);
        symbol.setConst("iterator", JsSymbol.ITERATOR);
        symbol.setConst("asyncIterator", JsSymbol.ASYNC_ITERATOR);
        symbol.setConst("hasInstance", JsSymbol.HAS_INSTANCE);
        symbol.setConst("toPrimitive", JsSymbol.TO_PRIMITIVE);
        symbol.setConst("toStringTag", JsSymbol.TO_STRING_TAG);
        symbol.setConst("species", JsSymbol.SPECIES);
        for (String n : new String[] {"match", "matchAll", "replace", "search", "split", "isConcatSpreadable", "unscopables"}) {
            symbol.setConst(n, new JsSymbol("Symbol." + n));
        }
        Lib.method(r, symbol, "for", 1, (t, a) -> {
            String key = Js.toStr(Js.arg(a, 0));
            return r.symbolRegistry.computeIfAbsent(key, JsSymbol::new);
        });
        Lib.method(r, symbol, "keyFor", 1, (t, a) -> {
            if (!(Js.arg(a, 0) instanceof JsSymbol s)) {
                throw Js.typeError(Js.toStr(Js.arg(a, 0)) + " is not a symbol");
            }
            return s.description != null && r.symbolRegistry.get(s.description) == s ? s.description : Js.UNDEFINED;
        });
        Lib.method(r, sp, "toString", 0, (t, a) -> thisSym(t).toString());
        Lib.method(r, sp, "valueOf", 0, (t, a) -> thisSym(t));
        Lib.getter(r, sp, "description", (t, a) -> {
            JsSymbol s = thisSym(t);
            return s.description == null ? Js.UNDEFINED : s.description;
        });
        Lib.method(r, sp, JsSymbol.TO_PRIMITIVE, "[Symbol.toPrimitive]", 1, (t, a) -> thisSym(t));
        Lib.tag(sp, "Symbol");
    }

    private static double num(Object[] a, int i) {
        return Js.toNumber(Js.arg(a, i));
    }

    private interface D1 {
        double f(double x);
    }

    private static void m1(Realm r, JsObject math, String name, D1 f) {
        Lib.method(r, math, name, 1, (t, a) -> f.f(num(a, 0)));
    }

    private static void installMath(Realm r) {
        JsObject math = new JsObject(r.objectProto);
        r.global.setHidden("Math", math);
        Lib.tag(math, "Math");
        math.setConst("PI", Math.PI);
        math.setConst("E", Math.E);
        math.setConst("LN2", 0.6931471805599453);
        math.setConst("LN10", 2.302585092994046);
        math.setConst("LOG2E", 1.4426950408889634);
        math.setConst("LOG10E", 0.4342944819032518);
        math.setConst("SQRT2", 1.4142135623730951);
        math.setConst("SQRT1_2", 0.7071067811865476);
        m1(r, math, "abs", Math::abs);
        m1(r, math, "acos", StrictMath::acos);
        m1(r, math, "acosh", V8Math::acosh);
        m1(r, math, "asin", StrictMath::asin);
        m1(r, math, "asinh", V8Math::asinh);
        m1(r, math, "atan", StrictMath::atan);
        m1(r, math, "atanh", V8Math::atanh);
        m1(r, math, "cbrt", V8Math::cbrt);
        m1(r, math, "ceil", Math::ceil);
        m1(r, math, "cos", StrictMath::cos);
        m1(r, math, "cosh", V8Math::cosh);
        m1(r, math, "exp", V8Math::exp);
        m1(r, math, "expm1", StrictMath::expm1);
        m1(r, math, "floor", Math::floor);
        m1(r, math, "fround", x -> (double) (float) x);
        m1(r, math, "log", StrictMath::log);
        m1(r, math, "log1p", StrictMath::log1p);
        m1(r, math, "log10", StrictMath::log10);
        m1(r, math, "log2", V8Math::log2);
        m1(r, math, "round", LibNumber::round);
        m1(r, math, "sign", x -> x != x || x == 0 ? x : Math.signum(x));
        m1(r, math, "sin", StrictMath::sin);
        m1(r, math, "sinh", V8Math::sinh);
        m1(r, math, "sqrt", Math::sqrt);
        m1(r, math, "tan", StrictMath::tan);
        m1(r, math, "tanh", StrictMath::tanh);
        m1(r, math, "trunc", x -> x < 0 ? Math.ceil(x) : Math.floor(x));
        Lib.method(r, math, "atan2", 2, (t, a) -> StrictMath.atan2(num(a, 0), num(a, 1)));
        Lib.method(r, math, "pow", 2, (t, a) -> Ast.pow(num(a, 0), num(a, 1)));
        Lib.method(r, math, "random", 0, (t, a) -> ThreadLocalRandom.current().nextDouble());
        Lib.method(r, math, "clz32", 1, (t, a) -> (double) Integer.numberOfLeadingZeros(Js.toInt32(Js.arg(a, 0))));
        Lib.method(r, math, "imul", 2, (t, a) -> (double) (Js.toInt32(Js.arg(a, 0)) * Js.toInt32(Js.arg(a, 1))));
        Lib.method(r, math, "hypot", 2, (t, a) -> {
            double[] v = new double[a.length];
            for (int i = 0; i < a.length; i++) {
                v[i] = Js.toNumber(a[i]);
            }
            return V8Math.hypot(v);
        });
        Lib.method(r, math, "max", 2, (t, a) -> {
            double m = Double.NEGATIVE_INFINITY;
            boolean nan = false;
            for (Object v : a) {
                double d = Js.toNumber(v);
                if (d != d) {
                    nan = true;
                } else if (d > m || (d == 0 && m == 0 && 1 / m < 0)) {
                    m = d;
                }
            }
            return nan ? Double.NaN : m;
        });
        Lib.method(r, math, "min", 2, (t, a) -> {
            double m = Double.POSITIVE_INFINITY;
            boolean nan = false;
            for (Object v : a) {
                double d = Js.toNumber(v);
                if (d != d) {
                    nan = true;
                } else if (d < m || (d == 0 && m == 0 && 1 / d < 0)) {
                    m = d;
                }
            }
            return nan ? Double.NaN : m;
        });
    }
}
