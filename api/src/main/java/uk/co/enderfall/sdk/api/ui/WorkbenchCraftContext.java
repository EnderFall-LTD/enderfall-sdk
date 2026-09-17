package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Server-side notification emitted after a player takes a workbench result. */
public record WorkbenchCraftContext(UUID playerId, WorkbenchRef workbench, ResourceId recipeId,
                                    ItemRef result, int resultCount) {
    public WorkbenchCraftContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(workbench, "workbench");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(result, "result");
        if (resultCount < 1) {
            throw new IllegalArgumentException("resultCount must be positive");
        }
    }
}
