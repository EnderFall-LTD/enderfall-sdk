package uk.co.enderfall.sdk.api.config;

import java.util.Objects;

public final class ConfigKey<T> {
    private final String path;
    private final ConfigType<T> type;
    private final T defaultValue;
    private final String comment;
    private final ConfigValidator<T> validator;
    private final boolean sensitive;

    ConfigKey(String path, ConfigType<T> type, T defaultValue, String comment,
              ConfigValidator<T> validator, boolean sensitive) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.comment = Objects.requireNonNull(comment, "comment");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.sensitive = sensitive;
    }

    public String path() {
        return path;
    }

    public ConfigType<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String comment() {
        return comment;
    }

    public ConfigValidator<T> validator() {
        return validator;
    }

    public boolean sensitive() {
        return sensitive;
    }
}
