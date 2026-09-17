package uk.co.enderfall.sdk.api.recipe;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Reference to a registered, positional workbench recipe type and serializer. */
public record WorkbenchRecipeTypeRef(ResourceId id, int inputSlots) {
    public WorkbenchRecipeTypeRef {
        Objects.requireNonNull(id, "id");
        if (inputSlots < 1 || inputSlots > 5) {
            throw new IllegalArgumentException("Workbench recipe types support 1-5 input slots");
        }
    }
}
