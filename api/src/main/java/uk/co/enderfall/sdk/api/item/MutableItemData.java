package uk.co.enderfall.sdk.api.item;

/** Server-authoritative mutable portable data on one real item stack. */
public interface MutableItemData extends ItemDataView {
    <T> void set(ItemDataKey<T> key, T value);

    void remove(ItemDataKey<?> key);
}
