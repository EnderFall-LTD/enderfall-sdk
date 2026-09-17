package uk.co.enderfall.sdk.api.gameplay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** An immutable, atomic set of registered-item requirements. */
public final class InventoryCost {
    private final Map<ResourceId, Integer> items;

    private InventoryCost(Map<ResourceId, Integer> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("An inventory cost must contain at least one item");
        }
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static InventoryCost of(ItemRef item, int amount) {
        return builder().item(item, amount).build();
    }

    public Map<ResourceId, Integer> items() {
        return items;
    }

    public static final class Builder {
        private final Map<ResourceId, Integer> items = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder item(ItemRef item, int amount) {
            Objects.requireNonNull(item, "item");
            if (amount < 1) {
                throw new IllegalArgumentException("Item amount must be positive");
            }
            items.merge(item.id(), amount, Math::addExact);
            return this;
        }

        public InventoryCost build() {
            return new InventoryCost(items);
        }
    }
}
