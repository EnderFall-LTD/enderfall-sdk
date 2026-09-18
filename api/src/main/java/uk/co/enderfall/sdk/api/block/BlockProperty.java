package uk.co.enderfall.sdk.api.block;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** A typed, finite block-state property. Property identity is intentional. */
@Experimental("Portable block-state definitions")
public final class BlockProperty<T> {
    private final String name;
    private final List<T> values;
    private final List<String> serialized;

    private BlockProperty(String name, List<T> values, Function<T, String> encoder) {
        this.name = token(name);
        this.values = List.copyOf(values);
        if (values.size() < 2 || values.size() > 256 || values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException("Property must have 2..256 distinct values: " + name);
        }
        this.serialized = values.stream().map(value -> token(encoder.apply(value))).toList();
        if (serialized.stream().distinct().count() != serialized.size()) {
            throw new IllegalArgumentException("Duplicate serialized property values: " + name);
        }
    }

    public static BlockProperty<Boolean> bool(String name) {
        return new BlockProperty<>(name, List.of(false, true), Object::toString);
    }

    public static BlockProperty<Integer> integer(String name, int minimum, int maximum) {
        if (minimum < 0 || maximum <= minimum || (long) maximum - minimum >= 256) {
            throw new IllegalArgumentException("Integer property needs 2..256 non-negative values");
        }
        return new BlockProperty<>(name, IntStream.rangeClosed(minimum, maximum).boxed().toList(), Object::toString);
    }

    /** Explicit names keep saved state independent of Java enum spelling and locale. */
    public static <E extends Enum<E>> BlockProperty<E> enumeration(
            String name, Class<E> type, Function<E, String> encoder) {
        Objects.requireNonNull(encoder, "encoder");
        return new BlockProperty<>(name, List.of(Objects.requireNonNull(type, "type").getEnumConstants()), encoder);
    }

    public String name() { return name; }
    public List<T> values() { return values; }
    public String serialize(T value) {
        int index = values.indexOf(Objects.requireNonNull(value, "value"));
        if (index < 0) throw new IllegalArgumentException("Invalid value for " + name);
        return serialized.get(index);
    }
    public T parse(String value) {
        int index = serialized.indexOf(value);
        if (index < 0) throw new IllegalArgumentException("Invalid encoded value for " + name);
        return values.get(index);
    }
    /** Returns the next declared value, wrapping from the final value to the first. */
    public T next(T value) {
        int index = values.indexOf(Objects.requireNonNull(value, "value"));
        if (index < 0) throw new IllegalArgumentException("Invalid value for " + name);
        return values.get((index + 1) % values.size());
    }
    private static String token(String value) {
        if (!Objects.requireNonNull(value, "name").matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Expected lowercase block-state token: " + value);
        }
        return value;
    }
}
