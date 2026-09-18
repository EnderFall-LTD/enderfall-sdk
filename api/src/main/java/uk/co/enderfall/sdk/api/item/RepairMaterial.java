package uk.co.enderfall.sdk.api.item;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Item or item-tag accepted as a portable durable item's anvil repair material. */
public record RepairMaterial(Kind kind, ResourceId id) {
    public RepairMaterial {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static RepairMaterial item(ResourceId id) {
        return new RepairMaterial(Kind.ITEM, id);
    }

    public static RepairMaterial tag(ResourceId id) {
        return new RepairMaterial(Kind.TAG, id);
    }

    public enum Kind {
        ITEM,
        TAG
    }
}
