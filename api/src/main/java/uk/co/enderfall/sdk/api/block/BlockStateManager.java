package uk.co.enderfall.sdk.api.block;

import java.util.Optional;
import java.util.function.UnaryOperator;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;

/** Server-world access to state declared by a portable block definition. */
@Experimental("Portable world block-state access")
@CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.BLOCK_STATES)
public interface BlockStateManager {
    /**
     * Returns the custom state only when the dimension and chunk are already loaded and
     * the position contains the expected registered block. This never loads a chunk.
     */
    Optional<PortableBlockState> get(BlockRef block, BlockLocation location);

    /**
     * Atomically transforms custom properties while preserving native properties such as
     * facing. Returns false for an unloaded/mismatched position or an unchanged state.
     * The callback executes on the server thread and must not retain the supplied value.
     */
    boolean update(BlockRef block, BlockLocation location, UnaryOperator<PortableBlockState> change);

    default <T> boolean set(BlockRef block, BlockLocation location, BlockProperty<T> property, T value) {
        return update(block, location, state -> state.with(property, value));
    }
}
