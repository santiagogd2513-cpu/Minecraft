package com.example.beautifuldecor.browser.js;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Números a texto y texto a números, con las reglas de JavaScript. */
final class NumFmt {
    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

    private NumFmt() {
    }

    /** Number::toString (base 10). */
    static String toString(double d) {
        if (d != d) {
            return "NaN";
        }
        if (d == 0) {
            return "0";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        boolean neg = d < 0;
        double a = Math.abs(d);
        String s = Double.toString(a);
        int e = s.indexOf('E');
        int exp = 0;
        String m = s;
        if (e >= 0) {
            exp = Integer.parseInt(s.substring(e + 1));
            m = s.substring(0, e);
        }
        int dot = m.indexOf('.');
        String all;
        int n;
        if (dot < 0) {
            all = m;
            n = m.length() + exp;
        } else {
            all = m.substring(0, dot) + m.substring(dot + 1);
            n = dot + exp;
        }
        int lead = 0;
        while (lead < all.length() - 1 && all.charAt(lead) == '0') {
            lead++;
            n--;
        }
        all = all.substring(lead);
        int end = all.length();
        while (end > 1 && all.charAt(end - 1) == '0') {
            end--;
        }
        all = all.substring(0, end);
        BigDecimal exact = null;
        for (int k = all.length() - 1; k >= 1; k--) {
            if (exact == null) {
                exact = new BigDecimal(a);
            }
            BigDecimal r = exact.round(new MathContext(k, RoundingMode.HALF_EVEN));
            if (r.doubleValue() != a) {
                break;
            }
            String u = r.unscaledValue().toString();
            int ue = u.length();
            while (ue > 1 && u.charAt(ue - 1) == '0') {
                ue--;
            }
            all = u.substring(0, ue);
            n = r.precision() - r.scale();
        }
        String body = layout(all, n);
        return neg ? "-" + body : body;
    }

    private static String layout(String digits, int n) {
        int k = digits.length();
        StringBuilder sb = new StringBuilder();
        if (k <= n && n <= 21) {
            sb.append(digits);
            for (int i = 0; i < n - k; i++) {
                sb.append('0');
            }
        } else if (0 < n && n <= 21) {
            sb.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            sb.append("0.");
            for (int i = 0; i < -n; i++) {
                sb.append('0');
            }
            sb.append(digits);
        } else {
            int e = n - 1;
            sb.append(digits.charAt(0));
            if (k > 1) {
                sb.append('.').append(digits, 1, k);
            }
            sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        }
        return sb.toString();
    }

    /** Number.prototype.toString(radix) con base distinta de 10. */
    static String toRadix(double value, int radix) {
        if (value != value) {
            return "NaN";
        }
        if (value == 0) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        boolean neg = value < 0;
        double v = Math.abs(value);
        double integer = Math.floor(v);
        double fraction = v - integer;
        double delta = 0.5 * (Math.nextUp(v) - v);
        delta = Math.max(Math.nextUp(0.0), delta);
        StringBuilder frac = new StringBuilder();
        if (fraction >= delta) {
            do {
                fraction *= radix;
                delta *= radix;
                int digit = (int) fraction;
                frac.append(DIGITS.charAt(digit));
                fraction -= digit;
                if (fraction > 0.5 || (fraction == 0.5 && (digit & 1) != 0)) {
                    if (fraction + delta > 1) {
                        while (true) {
                            if (frac.length() == 0) {
                                integer += 1;
                                break;
                            }
                            char c = frac.charAt(frac.length() - 1);
                            frac.setLength(frac.length() - 1);
                            int dg = c > '9' ? c - 'a' + 10 : c - '0';
                            if (dg + 1 < radix) {
                                frac.append(DIGITS.charAt(dg + 1));
                                break;
                            }
                        }
                        break;
                    }
                }
            } while (fraction >= delta);
        }
        StringBuilder ip = new StringBuilder();
        while (integer / radix >= 9007199254740992.0) {
            integer /= radix;
            ip.append('0');
        }
        do {
            double rem = integer % radix;
            ip.append(DIGITS.charAt((int) rem));
            integer = (integer - rem) / radix;
        } while (integer > 0);
        ip.reverse();
        StringBuilder sb = new StringBuilder();
        if (neg) {
            sb.append('-');
        }
        sb.append(ip);
        if (frac.length() > 0) {
            sb.append('.').append(frac);
        }
        return sb.toString();
    }

    static String toFixed(double x, int f) {
        if (x != x) {
            return "NaN";
        }
        if (Math.abs(x) >= 1e21 || Double.isInfinite(x)) {
            return toString(x);
        }
        boolean neg = x < 0;
        BigDecimal bd = new BigDecimal(Math.abs(x)).setScale(f, RoundingMode.HALF_UP);
        String s = bd.toPlainString();
        return neg ? "-" + s : s;
    }

    static String toPrecision(double x, int p) {
        if (x != x || Double.isInfinite(x)) {
            return toString(x);
        }
        boolean neg = x < 0;
        double a = Math.abs(x);
        String digits;
        int e;
        if (a == 0) {
            digits = "0".repeat(p);
            e = 0;
        } else {
            BigDecimal bd = new BigDecimal(a).round(new MathContext(p, RoundingMode.HALF_UP));
            digits = bd.unscaledValue().toString();
            e = bd.precision() - bd.scale() - 1;
            if (digits.length() < p) {
                digits = digits + "0".repeat(p - digits.length());
            } else if (digits.length() > p) {
                digits = digits.substring(0, p);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (neg) {
            sb.append('-');
        }
        if (e < -6 || e >= p) {
            sb.append(digits.charAt(0));
            if (p > 1) {
                sb.append('.').append(digits, 1, p);
            }
            sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        } else if (e == p - 1) {
            sb.append(digits);
        } else if (e >= 0) {
            sb.append(digits, 0, e + 1).append('.').append(digits, e + 1, p);
        } else {
            sb.append("0.");
            for (int i = 0; i < -(e + 1); i++) {
                sb.append('0');
            }
            sb.append(digits);
        }
        return sb.toString();
    }

    /** {@code f < 0} significa "tantos decimales como haga falta". */
    static String toExponential(double x, int f) {
        if (x != x || Double.isInfinite(x)) {
            return toString(x);
        }
        boolean neg = x < 0;
        double a = Math.abs(x);
        String digits;
        int e;
        if (a == 0) {
            digits = "0".repeat(Math.max(f, 0) + 1);
            e = 0;
        } else if (f < 0) {
            String s = toString(a);
            BigDecimal bd = new BigDecimal(s);
            String u = bd.unscaledValue().toString();
            e = bd.precision() - bd.scale() - 1;
            int end = u.length();
            while (end > 1 && u.charAt(end - 1) == '0') {
                end--;
            }
            digits = u.substring(0, end);
        } else {
            BigDecimal bd = new BigDecimal(a).round(new MathContext(f + 1, RoundingMode.HALF_UP));
            digits = bd.unscaledValue().toString();
            e = bd.precision() - bd.scale() - 1;
            if (digits.length() < f + 1) {
                digits = digits + "0".repeat(f + 1 - digits.length());
            } else if (digits.length() > f + 1) {
                digits = digits.substring(0, f + 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (neg) {
            sb.append('-');
        }
        sb.append(digits.charAt(0));
        if (digits.length() > 1) {
            sb.append('.').append(digits, 1, digits.length());
        }
        sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        return sb.toString();
    }

    /** Número con separadores de miles y decimales, como {@code toLocaleString('en-US')}. */
    static String localeString(double x, int minFrac, int maxFrac, boolean grouping) {
        if (x != x) {
            return "NaN";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "∞" : "-∞";
        }
        boolean neg = x < 0 || (x == 0 && 1 / x < 0);
        BigDecimal bd = new BigDecimal(toString(Math.abs(x))).setScale(maxFrac, RoundingMode.HALF_UP);
        String plain = bd.toPlainString();
        String ip = plain;
        String fp = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            ip = plain.substring(0, dot);
            fp = plain.substring(dot + 1);
        }
        int end = fp.length();
        while (end > minFrac && fp.charAt(end - 1) == '0') {
            end--;
        }
        fp = fp.substring(0, end);
        StringBuilder sb = new StringBuilder();
        if (grouping) {
            for (int i = 0; i < ip.length(); i++) {
                if (i > 0 && (ip.length() - i) % 3 == 0) {
                    sb.append(',');
                }
                sb.append(ip.charAt(i));
            }
        } else {
            sb.append(ip);
        }
        if (!fp.isEmpty()) {
            sb.append('.').append(fp);
        }
        boolean zero = bd.signum() == 0;
        return neg && !zero ? "-" + sb : (neg && x < 0 ? "-" + sb : sb.toString());
    }

    // ------------------------------------------------------------------ texto a número

    static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0xA0 || c == 0xFEFF || c == 0x1680
                || (c >= 0x2000 && c <= 0x200A) || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F || c == 0x3000;
    }

    static String trim(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && isWs(s.charAt(a))) {
            a++;
        }
        while (b > a && isWs(s.charAt(b - 1))) {
            b--;
        }
        return s.substring(a, b);
    }

    /** Number("texto"). */
    static double parse(String raw) {
        String s = trim(raw);
        if (s.isEmpty()) {
            return 0;
        }
        if (s.length() > 2 && s.charAt(0) == '0') {
            char c = s.charAt(1);
            int radix = c == 'x' || c == 'X' ? 16 : c == 'o' || c == 'O' ? 8 : c == 'b' || c == 'B' ? 2 : 0;
            if (radix != 0) {
                double v = 0;
                for (int i = 2; i < s.length(); i++) {
                    int dg = Character.digit(s.charAt(i), radix);
                    if (dg < 0) {
                        return Double.NaN;
                    }
                    v = v * radix + dg;
                }
                return v;
            }
        }
        int end = decimalPrefix(s, 0);
        if (end != s.length()) {
            return Double.NaN;
        }
        return decimalValue(s);
    }

    private static double decimalValue(String s) {
        if (s.endsWith("Infinity")) {
            return s.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Double.parseDouble(s);
    }

    /** Fin del mayor prefijo (desde {@code from}) que es un número decimal válido de JavaScript; si no hay ninguno devuelve {@code from}. */
    static int decimalPrefix(String s, int from) {
        int i = from;
        int n = s.length();
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        if (s.startsWith("Infinity", i)) {
            return i + 8;
        }
        int digits = 0;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
            digits++;
        }
        if (i < n && s.charAt(i) == '.') {
            int j = i + 1;
            int fd = 0;
            while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                j++;
                fd++;
            }
            if (digits > 0 || fd > 0) {
                i = j;
                digits += fd;
            }
        }
        if (digits == 0) {
            return from;
        }
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            int j = i + 1;
            if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                j++;
            }
            int ed = 0;
            while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                j++;
                ed++;
            }
            if (ed > 0) {
                i = j;
            }
        }
        return i;
    }

    static double parseFloat(String raw) {
        String s = trim(raw);
        int end = decimalPrefix(s, 0);
        if (end == 0) {
            return Double.NaN;
        }
        return decimalValue(s.substring(0, end));
    }

    static double parseInt(String raw, int radix) {
        String s = trim(raw);
        int i = 0;
        boolean neg = false;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        boolean strip = true;
        if (radix != 0) {
            if (radix < 2 || radix > 36) {
                return Double.NaN;
            }
            if (radix != 16) {
                strip = false;
            }
        } else {
            radix = 10;
        }
        if (strip && i + 1 < s.length() && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            i += 2;
            radix = 16;
        }
        int start = i;
        double v = 0;
        while (i < s.length()) {
            int dg = Character.digit(s.charAt(i), radix);
            if (dg < 0 || s.charAt(i) > 127) {
                break;
            }
            v = v * radix + dg;
            i++;
        }
        if (i == start) {
            return Double.NaN;
        }
        if (radix == 10 && i - start > 15) {
            v = Double.parseDouble(s.substring(start, i));
        }
        return neg ? -v : v;
    }
}
