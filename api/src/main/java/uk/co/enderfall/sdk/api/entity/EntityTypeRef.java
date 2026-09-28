package uk.co.enderfall.sdk.api.entity;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable reference to a vanilla, SDK-owned, or third-party entity type. */
public record EntityTypeRef(ResourceId id) {
    public EntityTypeRef { Objects.requireNonNull(id, "id"); }
}
