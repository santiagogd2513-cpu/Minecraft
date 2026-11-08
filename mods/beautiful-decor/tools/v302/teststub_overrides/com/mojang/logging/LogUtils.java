package com.mojang.logging;
import org.slf4j.Logger;
public class LogUtils {
    public static Logger getLogger() {
        return new Logger() {
            private String f(String format, Object[] args) {
                StringBuilder sb = new StringBuilder();
                int ai = 0, i = 0;
                while (i < format.length()) {
                    if (i + 1 < format.length() && format.charAt(i) == '{' && format.charAt(i + 1) == '}') { sb.append(ai < args.length ? String.valueOf(args[ai++]) : "{}"); i += 2; }
                    else sb.append(format.charAt(i++));
                }
                return sb.toString();
            }
            public void info(String format, Object... args) { System.out.println("[INFO] " + f(format, args)); }
            public void warn(String format, Object... args) { System.out.println("[WARN] " + f(format, args)); }
            public void error(String msg, Throwable t) { System.out.println("[ERROR] " + msg + " " + t); t.printStackTrace(System.out); }
            public void error(String format, Object... args) { System.out.println("[ERROR] " + f(format, args)); }
            public void error(String format, Object a, Object b) { System.out.println("[ERROR] " + f(format, new Object[]{a, b})); if (b instanceof Throwable t) t.printStackTrace(System.out); }
        };
    }
}
