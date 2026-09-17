package uk.co.enderfall.sdk.api.block;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.registry.Registration;

/**
 * Reusable portable block definition. One instance is created per registered block ID,
 * not per placed block. Store placed-block data in declared block-entity storage.
 * This is not a Minecraft Block subclass and does not inherit native behavior.
 */
@Experimental("Custom block definitions: configuration and server use hooks")
public interface PortableBlock {
    /** Runs once during registration preflight, before the declaration's property overrides. */
    void configure(Registration.BlockOptions properties);

    /**
     * Chooses the block's initial custom state when it is placed. Built-in horizontal or
     * six-way facing is applied first and is preserved separately by the native runtime.
     * Return a state from {@link BlockPlacementContext#state()} with the required changes.
     */
    default PortableBlockState onPlace(BlockPlacementContext context) { return context.state(); }

    /**
     * Placement transition used by scheduled-tick-aware blocks. Existing implementations
     * of {@link #onPlace(BlockPlacementContext)} remain valid through this default bridge.
     */
    default BlockTransition onPlaced(BlockPlacementContext context) {
        return BlockTransition.stable(onPlace(context));
    }

    /**
     * Derives this block's custom state after the adjacent block in
     * {@link BlockNeighborContext#direction()} changes. The optional neighbour state is
     * present only for another SDK portable block. This callback must not mutate the world.
     */
    default PortableBlockState onNeighborUpdate(BlockNeighborContext context) { return context.state(); }

    /**
     * Neighbor transition used by scheduled-tick-aware blocks. Return
     * {@link BlockTransition#after(PortableBlockState, int)} to debounce or delay work.
     * Existing implementations of {@link #onNeighborUpdate(BlockNeighborContext)} remain
     * valid through this default bridge.
     */
    default BlockTransition onNeighborChanged(BlockNeighborContext context) {
        return BlockTransition.stable(onNeighborUpdate(context));
    }

    /**
     * Runs on the server thread after a tick requested by a prior transition. The result
     * may update declared state and request another bounded follow-up tick.
     */
    default BlockTransition onScheduledTick(BlockScheduledTickContext context) {
        return BlockTransition.stable(context.state());
    }

    /**
     * Server-side block-use callback through the SDK interaction bus. Already handled or
     * cancelled events are excluded. Call event.handle() or event.cancel() explicitly;
     * doing nothing preserves other handlers and native fallback. Hand and hit-vector
     * details are not part of this initial interface.
     */
    default void onUse(ModContext context, InteractionEvent event) { }
}
