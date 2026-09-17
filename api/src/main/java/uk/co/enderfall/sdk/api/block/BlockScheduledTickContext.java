package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;

/** Immutable server-side input for a portable scheduled block tick. */
@Experimental("Portable scheduled block ticks")
public record BlockScheduledTickContext(
        PortableBlockState state,
        BlockLocation location,
        long gameTime) {
    public BlockScheduledTickContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(location, "location");
    }
}
