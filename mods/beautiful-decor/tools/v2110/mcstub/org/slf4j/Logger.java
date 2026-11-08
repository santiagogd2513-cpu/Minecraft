package org.slf4j;
public interface Logger {
    void info(String format, Object... args);
    void warn(String format, Object... args);
    void error(String msg, Throwable t);
    void error(String format, Object arg1, Object arg2);
    void error(String format, Object... args);
}
