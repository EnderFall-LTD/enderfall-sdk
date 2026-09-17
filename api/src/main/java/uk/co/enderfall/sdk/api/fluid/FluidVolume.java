package uk.co.enderfall.sdk.api.fluid;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Experimental, component-free fluid quantity. One bucket is exactly 81,000 SDK units. */
@uk.co.enderfall.sdk.api.annotation.Experimental("Fluid storage is not yet connected to native tanks")
public record FluidVolume(ResourceId fluid, long amount) {
    public static final long BUCKET = 81_000;

    public FluidVolume {
        Objects.requireNonNull(fluid, "fluid");
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive");
    }
}
