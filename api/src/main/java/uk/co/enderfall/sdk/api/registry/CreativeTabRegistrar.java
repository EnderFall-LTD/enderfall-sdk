package uk.co.enderfall.sdk.api.registry;

import uk.co.enderfall.sdk.api.ResourceId;

public interface CreativeTabRegistrar {
    CreativeTabRef register(ResourceId id, CreativeTabSpec spec);

    CreativeTabRef register(String path, CreativeTabSpec spec);
}
