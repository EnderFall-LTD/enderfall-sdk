package uk.co.enderfall.sdk.api.recipe;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Stable identifier for one loaded recipe without exposing a native recipe object. */
public record RecipeRef(ResourceId id) {
    public RecipeRef { Objects.requireNonNull(id, "id"); }
}
