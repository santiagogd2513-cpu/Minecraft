package com.example.beautifuldecor.browser.js;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Convierte el código fuente en una lista de tokens. */
final class Lexer {
    private static final String[] PUNCT = {
        ">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
        "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>", "**",
        "{", "}", "(", ")", "[", "]", ";", ",", "<", ">", "+", "-", "*", "/", "%", "&", "|", "^", "!", "~", "?", ":", "=", ".", "@"
    };
    /** Palabras tras las cuales una barra empieza una expresión regular. */
    private static final Set<String> REGEX_AFTER = Set.of("return", "typeof", "instanceof", "in", "of", "new", "delete", "void", "throw",
            "case", "do", "else", "yield", "await");

    private final String src;
    private int p;
    private int line = 1;

    Lexer(String src) {
        this.src = src.startsWith("#!") ? "//" + src : src;
    }

    private JsSyntaxError err(String msg) {
        return new JsSyntaxError(msg, this.line);
    }

    List<Token> tokenize() {
        List<Token> out = lexList(false);
        return out;
    }

    private List<Token> lexList(boolean untilBrace) {
        ArrayList<Token> out = new ArrayList<>();
        int depth = 0;
        Token prev = null;
        while (true) {
            boolean nl = skipSpace();
            if (this.p >= this.src.length()) {
                if (untilBrace) {
                    throw err("Unterminated template literal");
                }
                Token eof = new Token();
                eof.type = Token.EOF;
                eof.pos = this.p;
                eof.end = this.p;
                eof.line = this.line;
                eof.nl = nl;
                out.add(eof);
                return out;
            }
            Token t = next(prev);
            t.nl = nl;
            if (untilBrace && t.type == Token.PUNCT) {
                if (t.text.equals("{")) {
                    depth++;
                } else if (t.text.equals("}")) {
                    if (depth == 0) {
                        Token eof = new Token();
                        eof.type = Token.EOF;
                        eof.pos = t.pos;
                        eof.end = t.pos;
                        eof.line = t.line;
                        out.add(eof);
                        return out;
                    }
                    depth--;
                }
            }
            out.add(t);
            prev = t;
        }
    }

    private boolean skipSpace() {
        boolean nl = false;
        int n = this.src.length();
        while (this.p < n) {
            char c = this.src.charAt(this.p);
            if (c == '\n' || c == ' ' || c == ' ') {
                nl = true;
                this.line++;
                this.p++;
            } else if (c == '\r') {
                nl = true;
                this.p++;
                if (this.p < n && this.src.charAt(this.p) == '\n') {
                    this.p++;
                }
                this.line++;
            } else if (c == ' ' || c == '\t' || NumFmt.isWs(c)) {
                this.p++;
            } else if (c == '/' && this.p + 1 < n && this.src.charAt(this.p + 1) == '/') {
                this.p += 2;
                while (this.p < n && !isLineEnd(this.src.charAt(this.p))) {
                    this.p++;
                }
            } else if (c == '/' && this.p + 1 < n && this.src.charAt(this.p + 1) == '*') {
                int e = this.src.indexOf("*/", this.p + 2);
                if (e < 0) {
                    throw err("Invalid or unexpected token");
                }
                for (int i = this.p; i < e; i++) {
                    char d = this.src.charAt(i);
                    if (d == '\n' || d == ' ' || d == ' ' || (d == '\r' && (i + 1 >= n || this.src.charAt(i + 1) != '\n'))) {
                        nl = true;
                        this.line++;
                    }
                }
                this.p = e + 2;
            } else if (c == '<' && this.src.startsWith("<!--", this.p)) {
                while (this.p < n && !isLineEnd(this.src.charAt(this.p))) {
                    this.p++;
                }
            } else if (c == '-' && nl && this.src.startsWith("-->", this.p)) {
                while (this.p < n && !isLineEnd(this.src.charAt(this.p))) {
                    this.p++;
                }
            } else {
                break;
            }
        }
        return nl;
    }

    private static boolean isLineEnd(char c) {
        return c == '\n' || c == '\r' || c == ' ' || c == ' ';
    }

    private static boolean idStart(int c) {
        return c == '$' || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c > 127 && Character.isUnicodeIdentifierStart(c));
    }

    private static boolean idPart(int c) {
        return c == '$' || c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || (c > 127 && (Character.isUnicodeIdentifierPart(c) || c == 0x200C || c == 0x200D) && !Character.isIdentifierIgnorable(c));
    }

    private boolean regexAllowed(Token prev) {
        if (prev == null) {
            return true;
        }
        switch (prev.type) {
            case Token.NUM, Token.STR, Token.TEMPLATE, Token.REGEX, Token.PRIVATE:
                return false;
            case Token.NAME:
                return !prev.escaped && REGEX_AFTER.contains(prev.text);
            case Token.PUNCT:
                return !(prev.text.equals(")") || prev.text.equals("]") || prev.text.equals("}") || prev.text.equals("++")
                        || prev.text.equals("--"));
            default:
                return true;
        }
    }

    private Token next(Token prev) {
        char c = this.src.charAt(this.p);
        Token t = new Token();
        t.pos = this.p;
        t.line = this.line;
        int cp = this.src.codePointAt(this.p);
        if (idStart(cp) || c == '\\') {
            lexName(t, Token.NAME);
        } else if (c == '#' && this.p + 1 < this.src.length() && (idStart(this.src.codePointAt(this.p + 1)) || this.src.charAt(this.p + 1) == '\\')) {
            this.p++;
            lexName(t, Token.PRIVATE);
            t.text = "#" + t.text;
        } else if ((c >= '0' && c <= '9') || (c == '.' && this.p + 1 < this.src.length() && isDigit(this.src.charAt(this.p + 1)))) {
            lexNumber(t);
        } else if (c == '"' || c == '\'') {
            lexString(t, c);
        } else if (c == '`') {
            lexTemplate(t);
        } else if (c == '/' && regexAllowed(prev)) {
            lexRegex(t);
        } else {
            t.type = Token.PUNCT;
            String found = null;
            for (String s : PUNCT) {
                if (this.src.startsWith(s, this.p)) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                throw err("Invalid or unexpected token");
            }
            if (found.equals("?.") && this.p + 2 < this.src.length() && isDigit(this.src.charAt(this.p + 2))) {
                found = "?";
            }
            t.text = found;
            this.p += found.length();
        }
        t.end = this.p;
        return t;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void lexName(Token t, int type) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        while (this.p < this.src.length()) {
            int cp = this.src.codePointAt(this.p);
            if (cp == '\\') {
                if (this.p + 1 >= this.src.length() || this.src.charAt(this.p + 1) != 'u') {
                    throw err("Invalid or unexpected token");
                }
                this.p += 2;
                int v = readUnicodeEscape();
                if (!(first ? idStart(v) : idPart(v))) {
                    throw err("Invalid Unicode escape sequence");
                }
                sb.appendCodePoint(v);
                t.escaped = true;
            } else if (first ? idStart(cp) : idPart(cp)) {
                sb.appendCodePoint(cp);
                this.p += Character.charCount(cp);
            } else {
                break;
            }
            first = false;
        }
        t.type = type;
        t.text = sb.toString();
    }

    /** Lee lo que sigue a la u de un escape Unicode: XXXX o {X...}. */
    private int readUnicodeEscape() {
        if (this.p < this.src.length() && this.src.charAt(this.p) == '{') {
            int e = this.src.indexOf('}', this.p);
            if (e < 0) {
                throw err("Invalid Unicode escape sequence");
            }
            try {
                int v = Integer.parseInt(this.src.substring(this.p + 1, e), 16);
                if (v > 0x10FFFF) {
                    throw err("Undefined Unicode code-point");
                }
                this.p = e + 1;
                return v;
            } catch (NumberFormatException ex) {
                throw err("Invalid Unicode escape sequence");
            }
        }
        if (this.p + 4 > this.src.length()) {
            throw err("Invalid Unicode escape sequence");
        }
        try {
            int v = Integer.parseInt(this.src.substring(this.p, this.p + 4), 16);
            this.p += 4;
            return v;
        } catch (NumberFormatException ex) {
            throw err("Invalid Unicode escape sequence");
        }
    }

    private void lexNumber(Token t) {
        t.type = Token.NUM;
        int start = this.p;
        int n = this.src.length();
        char c = this.src.charAt(this.p);
        if (c == '0' && this.p + 1 < n) {
            char d = this.src.charAt(this.p + 1);
            int radix = d == 'x' || d == 'X' ? 16 : d == 'o' || d == 'O' ? 8 : d == 'b' || d == 'B' ? 2 : 0;
            if (radix != 0) {
                this.p += 2;
                double v = 0;
                int digits = 0;
                while (this.p < n) {
                    char e = this.src.charAt(this.p);
                    if (e == '_') {
                        this.p++;
                        continue;
                    }
                    int dg = Character.digit(e, radix);
                    if (dg < 0 || e > 127) {
                        break;
                    }
                    v = v * radix + dg;
                    digits++;
                    this.p++;
                }
                if (digits == 0) {
                    throw err("Invalid or unexpected token");
                }
                if (this.p < n && this.src.charAt(this.p) == 'n') {
                    throw err("BigInt literals are not supported");
                }
                t.num = v;
                t.text = this.src.substring(start, this.p);
                checkAfterNumber();
                return;
            }
            if (isDigit(d)) {
                // octal heredado (010) si todos los dígitos son menores que 8; si no, decimal
                int q = this.p + 1;
                boolean octal = true;
                while (q < n && isDigit(this.src.charAt(q))) {
                    if (this.src.charAt(q) >= '8') {
                        octal = false;
                    }
                    q++;
                }
                if (octal) {
                    double v = 0;
                    for (int i = this.p + 1; i < q; i++) {
                        v = v * 8 + (this.src.charAt(i) - '0');
                    }
                    this.p = q;
                    t.num = v;
                    t.text = this.src.substring(start, q);
                    checkAfterNumber();
                    return;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        while (this.p < n && (isDigit(this.src.charAt(this.p)) || this.src.charAt(this.p) == '_')) {
            if (this.src.charAt(this.p) != '_') {
                sb.append(this.src.charAt(this.p));
            }
            this.p++;
        }
        if (this.p < n && this.src.charAt(this.p) == 'n') {
            throw err("BigInt literals are not supported");
        }
        if (this.p < n && this.src.charAt(this.p) == '.') {
            sb.append('.');
            this.p++;
            while (this.p < n && (isDigit(this.src.charAt(this.p)) || this.src.charAt(this.p) == '_')) {
                if (this.src.charAt(this.p) != '_') {
                    sb.append(this.src.charAt(this.p));
                }
                this.p++;
            }
        }
        if (this.p < n && (this.src.charAt(this.p) == 'e' || this.src.charAt(this.p) == 'E')) {
            int save = this.p;
            StringBuilder ex = new StringBuilder("e");
            this.p++;
            if (this.p < n && (this.src.charAt(this.p) == '+' || this.src.charAt(this.p) == '-')) {
                ex.append(this.src.charAt(this.p));
                this.p++;
            }
            int ed = 0;
            while (this.p < n && isDigit(this.src.charAt(this.p))) {
                ex.append(this.src.charAt(this.p));
                this.p++;
                ed++;
            }
            if (ed == 0) {
                this.p = save;
                throw err("Invalid or unexpected token");
            }
            sb.append(ex);
        }
        String s = sb.toString();
        if (s.startsWith(".")) {
            s = "0" + s;
        }
        if (s.endsWith(".")) {
            s = s + "0";
        }
        s = s.replace(".e", ".0e");
        t.num = Double.parseDouble(s);
        t.text = this.src.substring(start, this.p);
        checkAfterNumber();
    }

    private void checkAfterNumber() {
        if (this.p < this.src.length()) {
            int cp = this.src.codePointAt(this.p);
            if (idStart(cp) || isDigit((char) cp)) {
                throw err("Invalid or unexpected token");
            }
        }
    }

    private void lexString(Token t, char quote) {
        t.type = Token.STR;
        this.p++;
        StringBuilder sb = new StringBuilder();
        int n = this.src.length();
        while (true) {
            if (this.p >= n) {
                throw err("Invalid or unexpected token");
            }
            char c = this.src.charAt(this.p);
            if (c == quote) {
                this.p++;
                break;
            }
            if (c == '\n' || c == '\r') {
                throw err("Invalid or unexpected token");
            }
            if (c == '\\') {
                t.escaped = true;
                this.p++;
                String r = escape(false);
                if (r == null) {
                    throw err("Invalid escape sequence");
                }
                sb.append(r);
            } else {
                sb.append(c);
                this.p++;
            }
        }
        t.text = sb.toString();
    }

    /**
     * Lee una secuencia de escape (el {@code \} ya se leyó). Devuelve el texto que representa, o null si no es válida (solo
     * permitido en plantillas, donde se guarda sin interpretar).
     */
    private String escape(boolean template) {
        if (this.p >= this.src.length()) {
            throw err("Invalid or unexpected token");
        }
        char c = this.src.charAt(this.p++);
        switch (c) {
            case 'n':
                return "\n";
            case 't':
                return "\t";
            case 'r':
                return "\r";
            case 'b':
                return "\b";
            case 'f':
                return "\f";
            case 'v':
                return "\u000B";
            case '\r':
                if (this.p < this.src.length() && this.src.charAt(this.p) == '\n') {
                    this.p++;
                }
                this.line++;
                return "";
            case '\n':
            case ' ':
            case ' ':
                this.line++;
                return "";
            case 'x': {
                if (this.p + 2 > this.src.length()) {
                    return null;
                }
                try {
                    int v = Integer.parseInt(this.src.substring(this.p, this.p + 2), 16);
                    this.p += 2;
                    return String.valueOf((char) v);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case 'u': {
                try {
                    return new String(Character.toChars(readUnicodeEscape()));
                } catch (JsSyntaxError e) {
                    return null;
                }
            }
            default:
                if (c >= '0' && c <= '7') {
                    if (c == '0' && (this.p >= this.src.length() || !isDigit(this.src.charAt(this.p)))) {
                        return "\0";
                    }
                    if (template) {
                        return null;
                    }
                    int v = c - '0';
                    int max = c <= '3' ? 2 : 1;
                    while (max-- > 0 && this.p < this.src.length() && this.src.charAt(this.p) >= '0' && this.src.charAt(this.p) <= '7') {
                        v = v * 8 + (this.src.charAt(this.p++) - '0');
                    }
                    return String.valueOf((char) v);
                }
                if ((c == '8' || c == '9') && template) {
                    return null;
                }
                return String.valueOf(c);
        }
    }

    private void lexTemplate(Token t) {
        t.type = Token.TEMPLATE;
        this.p++;
        t.cooked = new ArrayList<>();
        t.raws = new ArrayList<>();
        t.exprs = new ArrayList<>();
        StringBuilder cooked = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        boolean valid = true;
        int n = this.src.length();
        while (true) {
            if (this.p >= n) {
                throw err("Unterminated template literal");
            }
            char c = this.src.charAt(this.p);
            if (c == '`') {
                this.p++;
                t.cooked.add(valid ? cooked.toString() : null);
                t.raws.add(raw.toString());
                break;
            }
            if (c == '$' && this.p + 1 < n && this.src.charAt(this.p + 1) == '{') {
                this.p += 2;
                t.cooked.add(valid ? cooked.toString() : null);
                t.raws.add(raw.toString());
                cooked.setLength(0);
                raw.setLength(0);
                valid = true;
                t.exprs.add(lexList(true));
                continue;
            }
            if (c == '\\') {
                int start = this.p;
                this.p++;
                String r = escape(true);
                if (r == null) {
                    valid = false;
                } else {
                    cooked.append(r);
                }
                raw.append(this.src, start, this.p);
            } else if (c == '\r') {
                this.p++;
                if (this.p < n && this.src.charAt(this.p) == '\n') {
                    this.p++;
                }
                this.line++;
                cooked.append('\n');
                raw.append('\n');
            } else {
                if (c == '\n' || c == ' ' || c == ' ') {
                    this.line++;
                }
                cooked.append(c);
                raw.append(c);
                this.p++;
            }
        }
        t.text = "`";
    }

    private void lexRegex(Token t) {
        t.type = Token.REGEX;
        this.p++;
        int n = this.src.length();
        StringBuilder sb = new StringBuilder();
        boolean inClass = false;
        while (true) {
            if (this.p >= n || isLineEnd(this.src.charAt(this.p))) {
                throw err("Invalid regular expression: missing /");
            }
            char c = this.src.charAt(this.p);
            if (c == '\\') {
                sb.append(c);
                this.p++;
                if (this.p >= n || isLineEnd(this.src.charAt(this.p))) {
                    throw err("Invalid regular expression: missing /");
                }
                sb.append(this.src.charAt(this.p));
                this.p++;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                this.p++;
                break;
            }
            sb.append(c);
            this.p++;
        }
        int fs = this.p;
        while (this.p < n && idPart(this.src.charAt(this.p))) {
            this.p++;
        }
        t.text = sb.toString();
        t.flags = this.src.substring(fs, this.p);
    }
}
