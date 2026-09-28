package uk.co.enderfall.sdk.api.client.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable identifier for a registered portable client-only screen. */
public record ClientScreenRef(ResourceId id) {
    public ClientScreenRef { Objects.requireNonNull(id, "id"); }
}
