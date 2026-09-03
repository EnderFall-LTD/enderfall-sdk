package uk.co.enderfall.sdk.api.registry;

import uk.co.enderfall.sdk.api.ResourceId;

public interface ItemRegistrar {
    ItemRef register(ResourceId id, ItemSpec spec);

    ItemRef register(String path, ItemSpec spec);
}
