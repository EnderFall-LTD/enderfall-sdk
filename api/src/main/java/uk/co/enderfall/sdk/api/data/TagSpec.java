package uk.co.enderfall.sdk.api.data;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record TagSpec(Registry registry, List<ResourceId> values, boolean replace) {
    public TagSpec {
        Objects.requireNonNull(registry, "registry");
        values = List.copyOf(values);
    }

    public enum Registry {
        ITEMS,
        BLOCKS
    }
}
