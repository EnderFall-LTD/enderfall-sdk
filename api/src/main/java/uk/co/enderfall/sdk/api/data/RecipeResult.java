package uk.co.enderfall.sdk.api.data;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record RecipeResult(ResourceId item, int count) {
    public RecipeResult {
        Objects.requireNonNull(item, "item");
        if (count < 1 || count > 99) {
            throw new IllegalArgumentException("Recipe result count must be between 1 and 99");
        }
    }
}
