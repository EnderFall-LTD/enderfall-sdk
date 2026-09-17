package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable reference to a registered synchronized menu. */
public record MenuRef(ResourceId id) {
    public MenuRef {
        Objects.requireNonNull(id, "id");
    }
}
