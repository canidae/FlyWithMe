package net.exent.flywithme.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Log {
    private enum Level {
        D, I, W, E
    }
    private final String name;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:MM:ss.SSSZ");


    public Log() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        name = stackTraceElements.length > 2 ? stackTraceElements[2].getClassName() : "UnknownClass";
    }

    public void d(Object... data) {
        log(Level.D, null, data);
    }

    public void d(Throwable throwable, Object... data) {
        log(Level.D, throwable, data);
    }

    public void i(Object... data) {
        log(Level.I, null, data);
    }

    public void i(Throwable throwable, Object... data) {
        log(Level.I, throwable, data);
    }

    public void w(Object... data) {
        log(Level.W, null, data);
    }

    public void w(Throwable throwable, Object... data) {
        log(Level.W, throwable, data);
    }

    public void e(Object... data) {
        log(Level.E, null, data);
    }

    public void e(Throwable throwable, Object... data) {
        log(Level.E, throwable, data);
    }

    private void log(Level level, Throwable throwable, Object... data) {
        System.out.println(dateFormat.format(new Date()) + " [" + level.name() + "] <" + name + "> " + Stream.of(data).map(Object::toString).collect(Collectors.joining()));
        if (throwable != null)
            throwable.printStackTrace();
    }
}