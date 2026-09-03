package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record CreativeTabRef(ResourceId id) implements RegistryRef {
    public CreativeTabRef {
        Objects.requireNonNull(id, "id");
    }
}
