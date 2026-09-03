package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record BlockRef(ResourceId id) implements RegistryRef {
    public BlockRef {
        Objects.requireNonNull(id, "id");
    }
}
