package uk.co.enderfall.sdk.api.blockentity;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Per-instance server tick behaviour, invoked only while the block is loaded and ticking. */
@Experimental("Available only in the isolated persistent block-entity preview")
@FunctionalInterface
public interface BlockEntityTicker {
    /**
     * Runs on the owning server thread, never the client. Keep per-block values in state,
     * not callback fields: one callback is shared by all instances of the definition.
     * Do not retain state or access it asynchronously. Updates are not transactional.
     * A runtime exception disables this instance's callback until it is loaded again;
     * earlier valid changes remain saved and unrelated instances continue ticking.
     */
    void tick(BlockEntityState state);
}
