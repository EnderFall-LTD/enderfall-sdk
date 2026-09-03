package uk.co.enderfall.sdk.api.config;

import java.util.List;
import java.util.Objects;

/** Runtime type description used by the loader-neutral TOML implementation. */
public final class ConfigType<T> {
    public static final ConfigType<Boolean> BOOLEAN = scalar(Kind.BOOLEAN, Boolean.class);
    public static final ConfigType<Integer> INTEGER = scalar(Kind.INTEGER, Integer.class);
    public static final ConfigType<Long> LONG = scalar(Kind.LONG, Long.class);
    public static final ConfigType<Double> DOUBLE = scalar(Kind.DOUBLE, Double.class);
    public static final ConfigType<String> STRING = scalar(Kind.STRING, String.class);

    private final Kind kind;
    private final Class<?> valueClass;
    private final ConfigType<?> elementType;

    private ConfigType(Kind kind, Class<?> valueClass, ConfigType<?> elementType) {
        this.kind = kind;
        this.valueClass = valueClass;
        this.elementType = elementType;
    }

    private static <T> ConfigType<T> scalar(Kind kind, Class<T> valueClass) {
        return new ConfigType<>(kind, valueClass, null);
    }

    public static <E extends Enum<E>> ConfigType<E> enumeration(Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass");
        return new ConfigType<>(Kind.ENUM, enumClass, null);
    }

    public static <E> ConfigType<List<E>> listOf(ConfigType<E> elementType) {
        Objects.requireNonNull(elementType, "elementType");
        if (elementType.kind == Kind.LIST) {
            throw new IllegalArgumentException("Nested config lists are not supported");
        }
        return new ConfigType<>(Kind.LIST, List.class, elementType);
    }

    public Kind kind() {
        return kind;
    }

    public Class<?> valueClass() {
        return valueClass;
    }

    public ConfigType<?> elementType() {
        if (elementType == null) {
            throw new IllegalStateException("Only list types have an element type");
        }
        return elementType;
    }

    public enum Kind {
        BOOLEAN,
        INTEGER,
        LONG,
        DOUBLE,
        STRING,
        ENUM,
        LIST
    }
}
