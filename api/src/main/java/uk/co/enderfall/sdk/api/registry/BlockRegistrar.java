package uk.co.enderfall.sdk.api.registry;

import uk.co.enderfall.sdk.api.ResourceId;

public interface BlockRegistrar {
    BlockRef register(ResourceId id, BlockSpec spec);

    BlockRef register(String path, BlockSpec spec);

    BlockRef registerWithItem(String path, BlockSpec blockSpec, ItemSpec itemSpec);

    /** Registers a new block, its item, and block-entity storage together; never retrofits an existing block. */
    @uk.co.enderfall.sdk.api.annotation.Experimental("Persistent block-entity integration is under development")
    default BlockRef registerPersistentWithItem(String path, BlockSpec blockSpec, ItemSpec itemSpec,
            uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage) {
        throw new UnsupportedOperationException("Persistent blocks are unavailable on this implementation");
    }
}
