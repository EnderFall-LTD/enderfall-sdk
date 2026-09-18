package uk.co.enderfall.sdk.api.item;

import java.util.Optional;

/** Read-only typed view of portable data stored on one item stack. */
public interface ItemDataView {
    <T> Optional<T> get(ItemDataKey<T> key);

    default <T> T getOrDefault(ItemDataKey<T> key, T fallback) {
        key.validate(fallback);
        return get(key).orElse(fallback);
    }

    default boolean contains(ItemDataKey<?> key) { return getUnchecked(key).isPresent(); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<?> getUnchecked(ItemDataKey<?> key) { return get((ItemDataKey) key); }
}
