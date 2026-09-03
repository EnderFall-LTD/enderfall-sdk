package uk.co.enderfall.sdk.api.logging;

/** Loader-neutral structured logger using SLF4J-style {@code {}} placeholders. */
public interface ModLogger {
    void debug(String message, Object... arguments);

    void info(String message, Object... arguments);

    void warn(String message, Object... arguments);

    void error(String message, Object... arguments);

    void error(String message, Throwable error, Object... arguments);
}
