package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Stable identifier for one portable block-property tool policy, such as a wrench or hammer. */
@Experimental("Portable block-property tools")
public record BlockToolRef(ResourceId id) {
    public BlockToolRef {
        Objects.requireNonNull(id, "id");
    }

    public static BlockToolRef of(String namespace, String path) {
        return new BlockToolRef(ResourceId.of(namespace, path));
    }
}
