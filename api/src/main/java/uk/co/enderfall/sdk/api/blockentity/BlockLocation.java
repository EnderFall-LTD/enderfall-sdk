package uk.co.enderfall.sdk.api.blockentity;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** An exact block position in a dimension, never inferred from a player or block type. */
@Experimental("Persistent block-entity integration is under development")
public record BlockLocation(ResourceId dimension, int x, int y, int z) {
    public BlockLocation { Objects.requireNonNull(dimension, "dimension"); }
}
