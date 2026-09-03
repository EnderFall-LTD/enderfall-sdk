package uk.co.enderfall.sdk.api.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigSpec {
    private final List<ConfigKey<?>> keys;

    private ConfigSpec(Builder builder) {
        keys = List.copyOf(builder.keys);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ConfigKey<?>> keys() {
        return keys;
    }

    public static final class Builder {
        private final List<ConfigKey<?>> keys = new ArrayList<>();
        private final Set<String> paths = new HashSet<>();

        public ConfigKey<Boolean> booleanValue(String path, boolean defaultValue, String comment) {
            return value(path, ConfigType.BOOLEAN, defaultValue, comment, ConfigValidator.acceptingAll(), false);
        }

        public ConfigKey<Integer> integer(String path, int defaultValue, int minimum, int maximum, String comment) {
            if (minimum > maximum) {
                throw new IllegalArgumentException("minimum cannot exceed maximum");
            }
            return value(path, ConfigType.INTEGER, defaultValue, comment,
                    ConfigValidator.matching(number -> number >= minimum && number <= maximum,
                            "must be between " + minimum + " and " + maximum), false);
        }

        public ConfigKey<Long> longValue(String path, long defaultValue, long minimum, long maximum, String comment) {
            if (minimum > maximum) {
                throw new IllegalArgumentException("minimum cannot exceed maximum");
            }
            return value(path, ConfigType.LONG, defaultValue, comment,
                    ConfigValidator.matching(number -> number >= minimum && number <= maximum,
                            "must be between " + minimum + " and " + maximum), false);
        }

        public ConfigKey<Double> decimal(String path, double defaultValue, double minimum, double maximum,
                                         String comment) {
            if (!Double.isFinite(defaultValue) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                    || minimum > maximum) {
                throw new IllegalArgumentException("Invalid decimal range");
            }
            return value(path, ConfigType.DOUBLE, defaultValue, comment,
                    ConfigValidator.matching(number -> number >= minimum && number <= maximum,
                            "must be between " + minimum + " and " + maximum), false);
        }

        public ConfigKey<String> string(String path, String defaultValue, String comment) {
            return value(path, ConfigType.STRING, defaultValue, comment, ConfigValidator.acceptingAll(), false);
        }

        public <E extends Enum<E>> ConfigKey<E> enumeration(String path, E defaultValue, String comment) {
            Class<E> enumClass = defaultValue.getDeclaringClass();
            return value(path, ConfigType.enumeration(enumClass), defaultValue, comment,
                    ConfigValidator.acceptingAll(), false);
        }

        public <E> ConfigKey<List<E>> list(String path, ConfigType<E> elementType, List<E> defaultValue,
                                           int maximumSize, String comment) {
            if (maximumSize < 0) {
                throw new IllegalArgumentException("maximumSize cannot be negative");
            }
            List<E> immutableDefault = List.copyOf(defaultValue);
            return value(path, ConfigType.listOf(elementType), immutableDefault, comment,
                    ConfigValidator.matching(values -> values.size() <= maximumSize,
                            "must contain at most " + maximumSize + " entries"), false);
        }

        public <T> ConfigKey<T> value(String path, ConfigType<T> type, T defaultValue, String comment,
                                      ConfigValidator<T> validator, boolean sensitive) {
            validatePath(path);
            if (!paths.add(path)) {
                throw new IllegalArgumentException("Duplicate config key: " + path);
            }
            ConfigKey<T> key = new ConfigKey<>(path, type, defaultValue, comment, validator, sensitive);
            ValidationResult defaultResult = validator.validate(defaultValue);
            if (!defaultResult.valid()) {
                throw new IllegalArgumentException("Default value for " + path + ' ' + defaultResult.message());
            }
            keys.add(key);
            return key;
        }

        public ConfigSpec build() {
            return new ConfigSpec(this);
        }

        private static void validatePath(String path) {
            Objects.requireNonNull(path, "path");
            if (!path.matches("[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)*")) {
                throw new IllegalArgumentException("Invalid config path: " + path);
            }
        }
    }
}
