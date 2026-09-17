package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable handle for a registered container-backed portable workbench. */
public record WorkbenchRef(ResourceId id) {
    public WorkbenchRef {
        Objects.requireNonNull(id, "id");
    }
}
