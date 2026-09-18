package uk.co.enderfall.sdk.api.item;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/**
 * A typed, bounded key stored on an individual item stack. Keys must be declared on
 * the owning {@code ItemSpec}; undeclared access is rejected by native bridges.
 */
public final class ItemDataKey<T> {
    public static final int MAXIMUM_STRING_BYTES = 32_000;

    private final ResourceId id;
    private final ItemDataType type;
    private final Class<T> valueClass;
    private final int maximumStringBytes;
    private final long minimumLong;
    private final long maximumLong;
    private final double minimum;
    private final double maximum;

    private ItemDataKey(ResourceId id, ItemDataType type, Class<T> valueClass,
            int maximumStringBytes, long minimumLong, long maximumLong,
            double minimum, double maximum) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
        this.maximumStringBytes = maximumStringBytes;
        this.minimumLong = minimumLong;
        this.maximumLong = maximumLong;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static ItemDataKey<String> string(ResourceId id, int maximumUtf8Bytes) {
        if (maximumUtf8Bytes < 1 || maximumUtf8Bytes > MAXIMUM_STRING_BYTES) {
            throw new IllegalArgumentException("String item data must allow between 1 and "
                    + MAXIMUM_STRING_BYTES + " UTF-8 bytes");
        }
        return new ItemDataKey<>(id, ItemDataType.STRING, String.class, maximumUtf8Bytes, 0, 0,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public static ItemDataKey<Boolean> bool(ResourceId id) {
        return new ItemDataKey<>(id, ItemDataType.BOOLEAN, Boolean.class, 0, 0, 1, 0, 1);
    }

    public static ItemDataKey<Integer> integer(ResourceId id, int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum cannot exceed maximum");
        return new ItemDataKey<>(id, ItemDataType.INTEGER, Integer.class, 0,
                minimum, maximum, minimum, maximum);
    }

    public static ItemDataKey<Long> longInteger(ResourceId id, long minimum, long maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum cannot exceed maximum");
        return new ItemDataKey<>(id, ItemDataType.LONG, Long.class, 0,
                minimum, maximum, minimum, maximum);
    }

    public static ItemDataKey<Double> decimal(ResourceId id, double minimum, double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Decimal item-data bounds must be finite and ordered");
        }
        return new ItemDataKey<>(id, ItemDataType.DOUBLE, Double.class, 0,
                0, 0, minimum, maximum);
    }

    public ResourceId id() { return id; }
    public ItemDataType type() { return type; }
    public int maximumStringBytes() { return maximumStringBytes; }

    /** Validates and returns a value with this key's exact public type. */
    public T validate(Object value) {
        Objects.requireNonNull(value, "value");
        if (!valueClass.isInstance(value)) {
            throw new IllegalArgumentException("Item data " + id + " requires " + valueClass.getSimpleName());
        }
        T typed = valueClass.cast(value);
        if (typed instanceof String text
                && text.getBytes(StandardCharsets.UTF_8).length > maximumStringBytes) {
            throw new IllegalArgumentException("Item data " + id + " exceeds "
                    + maximumStringBytes + " UTF-8 bytes");
        }
        if (typed instanceof Integer integer && (integer < minimumLong || integer > maximumLong)) {
            throw outsideRange();
        }
        if (typed instanceof Long integer && (integer < minimumLong || integer > maximumLong)) {
            throw outsideRange();
        }
        if (typed instanceof Double numeric) {
            if (!Double.isFinite(numeric) || numeric < minimum || numeric > maximum) {
                throw outsideRange();
            }
        }
        return typed;
    }

    private IllegalArgumentException outsideRange() {
        String range = type == ItemDataType.INTEGER || type == ItemDataType.LONG
                ? "[" + minimumLong + ", " + maximumLong + "]"
                : "[" + minimum + ", " + maximum + "]";
        return new IllegalArgumentException("Item data " + id + " is outside " + range);
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ItemDataKey<?> key
                && id.equals(key.id) && type == key.type
                && maximumStringBytes == key.maximumStringBytes
                && minimumLong == key.minimumLong && maximumLong == key.maximumLong
                && Double.compare(minimum, key.minimum) == 0
                && Double.compare(maximum, key.maximum) == 0;
    }

    @Override public int hashCode() {
        return Objects.hash(id, type, maximumStringBytes, minimumLong, maximumLong, minimum, maximum);
    }

    @Override public String toString() { return id + " (" + type + ")"; }
}
