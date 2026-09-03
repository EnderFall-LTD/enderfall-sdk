package uk.co.enderfall.sdk.runtime.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigKey;

final class DefaultConfigHandle implements ConfigHandle {
    private final String description;
    private volatile State state;

    DefaultConfigHandle(Path path, Map<ConfigKey<?>, Object> values) {
        this(Objects.requireNonNull(path, "path").toString(), path, values);
    }

    private DefaultConfigHandle(String description, Path path, Map<ConfigKey<?>, Object> values) {
        this.description = Objects.requireNonNull(description, "description");
        state = createState(path, values);
    }

    static DefaultConfigHandle pending(String description, Map<ConfigKey<?>, Object> defaults) {
        return new DefaultConfigHandle(description, null, defaults);
    }

    void replace(DefaultConfigHandle loaded) {
        state = loaded.state;
    }

    void reset(Map<ConfigKey<?>, Object> defaults) {
        state = createState(null, defaults);
    }

    private static State createState(Path path, Map<ConfigKey<?>, Object> values) {
        Map<ConfigKey<?>, Object> immutableValues = Map.copyOf(values);
        java.util.LinkedHashMap<String, Object> byPath = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> byPath.put(key.path(), value));
        return new State(path, immutableValues, Map.copyOf(byPath));
    }

    @Override
    public Path path() {
        Path path = state.path();
        if (path == null) {
            throw new IllegalStateException(description + " is not attached to a running world yet");
        }
        return path;
    }

    @Override
    public <T> T get(ConfigKey<T> key) {
        State current = state;
        Object value = current.values().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Config key does not belong to " + description + ": " + key.path());
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }

    @Override
    public Map<String, Object> snapshot() {
        return state.snapshot();
    }

    private record State(Path path, Map<ConfigKey<?>, Object> values, Map<String, Object> snapshot) {
    }
}
