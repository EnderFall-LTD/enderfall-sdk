package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable reference to a registered block-owned storage container. */
public record StorageContainerRef(ResourceId id) {
    public StorageContainerRef {
        Objects.requireNonNull(id, "id");
    }
}
