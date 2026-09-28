package uk.co.enderfall.sdk.api.item;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Immutable, loader-neutral item stack value suitable for client rendering and data views. */
public record ItemStackRef(ItemRef item, int count) {
    public static final int MAXIMUM_COUNT = 1_000_000;

    public ItemStackRef {
        Objects.requireNonNull(item, "item");
        if (count < 1 || count > MAXIMUM_COUNT) {
            throw new IllegalArgumentException("Item stack count must be between 1 and " + MAXIMUM_COUNT);
        }
    }

    public static ItemStackRef of(ItemRef item) { return new ItemStackRef(item, 1); }

    public static ItemStackRef of(ItemRef item, int count) { return new ItemStackRef(item, count); }

    public static ItemStackRef of(ResourceId item, int count) {
        return new ItemStackRef(new ItemRef(item), count);
    }
}
