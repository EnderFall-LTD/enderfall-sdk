package uk.co.enderfall.sdk.api.data;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record ModelSpec(Kind kind, ResourceId texture) {
    public ModelSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(texture, "texture");
    }

    public enum Kind {
        GENERATED_ITEM,
        HANDHELD_ITEM,
        CUBE_ALL_BLOCK
    }
}
