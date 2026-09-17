package uk.co.enderfall.sdk.api.render;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** A JSON model under assets/namespace/models/path.json, not an item or block registry ID. */
@Experimental("Custom model loading is target-limited")
public record ModelRef(ResourceId id) {
    public ModelRef {
        Objects.requireNonNull(id, "id");
        if (id.path().startsWith("models/") || id.path().endsWith(".json")) {
            throw new IllegalArgumentException("Model ID must omit models/ and .json: " + id);
        }
    }
}
