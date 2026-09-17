package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Immutable input for deriving custom state after one adjacent block changes. */
@Experimental("Portable neighbour-driven block state")
public record BlockNeighborContext(
        PortableBlockState state,
        BlockDirection direction,
        ResourceId neighborBlock,
        boolean sameBlock,
        Optional<PortableBlockState> neighborState) {
    public BlockNeighborContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(neighborBlock, "neighborBlock");
        neighborState = Objects.requireNonNull(neighborState, "neighborState");
    }
}
