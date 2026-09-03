package uk.co.enderfall.sdk.runtime.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigKey;

final class DefaultConfigHandle implements ConfigHandle {
    private final Path path;
    private final Map<ConfigKey<?>, Object> values;
    private final Map<String, Object> snapshot;

    DefaultConfigHandle(Path path, Map<ConfigKey<?>, Object> values) {
        this.path = Objects.requireNonNull(path, "path");
        this.values = Map.copyOf(values);
        java.util.LinkedHashMap<String, Object> byPath = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> byPath.put(key.path(), value));
        snapshot = Map.copyOf(byPath);
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public <T> T get(ConfigKey<T> key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Config key does not belong to " + path + ": " + key.path());
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }

    @Override
    public Map<String, Object> snapshot() {
        return snapshot;
    }
}
