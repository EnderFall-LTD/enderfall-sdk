package uk.co.enderfall.sdk.runtime;

import java.util.ArrayList;
import java.util.List;
import uk.co.enderfall.sdk.api.logging.ModLogger;

final class RecordingLogger implements ModLogger {
    private final List<String> warnings = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();

    List<String> warnings() {
        return List.copyOf(warnings);
    }

    List<Throwable> failures() {
        return List.copyOf(failures);
    }

    @Override
    public void debug(String message, Object... arguments) {
    }

    @Override
    public void info(String message, Object... arguments) {
    }

    @Override
    public void warn(String message, Object... arguments) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Object... arguments) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Throwable error, Object... arguments) {
        failures.add(error);
    }
}
