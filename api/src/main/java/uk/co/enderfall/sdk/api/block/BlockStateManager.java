package uk.co.enderfall.sdk.api.block;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
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

    /**
     * Selects the next property exposed by this block for the named tool without changing
     * world state. Empty means the chunk/block is unavailable or the tool is unsupported.
     */
    default Optional<BlockToolResult> selectNextToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int currentSelection) {
        return Optional.empty();
    }

    /**
     * Atomically cycles the selected declared property. Empty means the chunk/block is
     * unavailable or the tool is unsupported. Selection indices are normalized safely.
     */
    default Optional<BlockToolResult> cycleToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int selection) {
        return Optional.empty();
    }

    /**
     * Atomically cycles a declared property with validation and committed-change callbacks.
     * The policy runs on the server thread after the expected loaded block and current state
     * have been verified but before mutation. Returning false leaves the world unchanged.
     * This is the appropriate place for an all-or-nothing permission or resource claim.
     *
     * <p>The committed callback runs exactly once after a successful world mutation and
     * never runs for unavailable, rejected or unchanged edits. Exceptions from the policy
     * leave block state unchanged; exceptions from the committed callback propagate after
     * the state has already changed.</p>
     */
    default Optional<BlockToolResult> cycleToolProperty(BlockRef block, BlockLocation location,
            BlockToolRef tool, int selection, Predicate<BlockToolChange> policy,
            Consumer<BlockToolResult> committed) {
        return Optional.empty();
    }
}
