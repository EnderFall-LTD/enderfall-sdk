package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Immutable information available while a portable block chooses its initial custom state. */
@Experimental("Portable custom block placement")
public record BlockPlacementContext(
        PortableBlockState state,
        BlockDirection clickedFace,
        BlockDirection playerFacing,
        BlockDirection nearestLookingDirection) {
    public BlockPlacementContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(clickedFace, "clickedFace");
        Objects.requireNonNull(playerFacing, "playerFacing");
        Objects.requireNonNull(nearestLookingDirection, "nearestLookingDirection");
        if (!playerFacing.horizontal()) {
            throw new IllegalArgumentException("playerFacing must be horizontal");
        }
    }
}
