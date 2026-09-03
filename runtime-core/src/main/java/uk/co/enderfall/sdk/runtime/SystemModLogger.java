package uk.co.enderfall.sdk.runtime;

import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import uk.co.enderfall.sdk.api.logging.ModLogger;

/** Dependency-free logger used until an adapter supplies its loader logger. */
public final class SystemModLogger implements ModLogger {
    private final System.Logger delegate;
    private final Object slf4jDelegate;

    public SystemModLogger(String modId) {
        String loggerName = Objects.requireNonNull(modId, "modId");
        delegate = System.getLogger(loggerName);
        slf4jDelegate = findSlf4jLogger(loggerName);
    }

    @Override
    public void debug(String message, Object... arguments) {
        log(Level.DEBUG, message, null, arguments);
    }

    @Override
    public void info(String message, Object... arguments) {
        log(Level.INFO, message, null, arguments);
    }

    @Override
    public void warn(String message, Object... arguments) {
        log(Level.WARNING, message, null, arguments);
    }

    @Override
    public void error(String message, Object... arguments) {
        log(Level.ERROR, message, null, arguments);
    }

    @Override
    public void error(String message, Throwable error, Object... arguments) {
        log(Level.ERROR, message, error, arguments);
    }

    private void log(Level level, String template, Throwable error, Object... arguments) {
        String rendered = format(template, arguments);
        if (slf4jDelegate != null && logWithSlf4j(level, rendered, error)) {
            return;
        }
        if (error == null) {
            delegate.log(level, rendered);
        } else {
            delegate.log(level, rendered, error);
        }
    }

    private boolean logWithSlf4j(Level level, String message, Throwable error) {
        String methodName = switch (level) {
            case ALL, TRACE, DEBUG -> "debug";
            case INFO -> "info";
            case WARNING -> "warn";
            case ERROR, OFF -> "error";
        };
        try {
            Method method = error == null
                    ? slf4jDelegate.getClass().getMethod(methodName, String.class)
                    : slf4jDelegate.getClass().getMethod(methodName, String.class, Throwable.class);
            method.invoke(slf4jDelegate, error == null ? new Object[] {message} : new Object[] {message, error});
            return true;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            return false;
        }
    }

    private static Object findSlf4jLogger(String loggerName) {
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory", false,
                    Thread.currentThread().getContextClassLoader());
            return factory.getMethod("getLogger", String.class).invoke(null, loggerName);
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException exception) {
            return null;
        }
    }

    static String format(String template, Object... arguments) {
        Objects.requireNonNull(template, "template");
        StringBuilder result = new StringBuilder(template.length() + arguments.length * 8);
        int argumentIndex = 0;
        int cursor = 0;
        int placeholder;
        while ((placeholder = template.indexOf("{}", cursor)) >= 0 && argumentIndex < arguments.length) {
            result.append(template, cursor, placeholder).append(String.valueOf(arguments[argumentIndex++]));
            cursor = placeholder + 2;
        }
        result.append(template, cursor, template.length());
        while (argumentIndex < arguments.length) {
            result.append(' ').append(String.valueOf(arguments[argumentIndex++]));
        }
        return result.toString();
    }
}
