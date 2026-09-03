package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record ItemRef(ResourceId id) implements RegistryRef {
    public ItemRef {
        Objects.requireNonNull(id, "id");
    }
}
