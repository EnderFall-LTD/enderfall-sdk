package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

/** Target-neutral successful craft reported by a native container menu. */
public record PortableWorkbenchCraft(UUID playerId, ResourceId recipeId, ResourceId result, int resultCount) {
    public PortableWorkbenchCraft {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(result, "result");
        if (resultCount < 1) {
            throw new IllegalArgumentException("resultCount must be positive");
        }
    }
}
