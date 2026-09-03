package uk.co.enderfall.sdk.api.registry;

import uk.co.enderfall.sdk.api.ResourceId;

public interface BlockRegistrar {
    BlockRef register(ResourceId id, BlockSpec spec);

    BlockRef register(String path, BlockSpec spec);

    BlockRef registerWithItem(String path, BlockSpec blockSpec, ItemSpec itemSpec);
}
