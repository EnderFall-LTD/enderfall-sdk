package uk.co.enderfall.sdk.runtime;

import java.lang.System.Logger.Level;
import java.util.Objects;
import uk.co.enderfall.sdk.api.logging.ModLogger;

/** Dependency-free logger used until an adapter supplies its loader logger. */
public final class SystemModLogger implements ModLogger {
    private final System.Logger delegate;

    public SystemModLogger(String modId) {
        delegate = System.getLogger(Objects.requireNonNull(modId, "modId"));
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
        if (error == null) {
            delegate.log(level, rendered);
        } else {
            delegate.log(level, rendered, error);
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
