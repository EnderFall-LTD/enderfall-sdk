package uk.co.enderfall.sdk.api.recipe;

import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.platform.Capability;

/** Registers loader-neutral custom recipe types backed by EnderFall serializers. */
@Experimental("Portable custom recipe serializers are being proven across the supported target matrix")
@CapabilityGated(Capability.CUSTOM_RECIPES)
public interface RecipeRegistrar {
    /** Reserves a native type ID for SDK data-pack machine recipes (one item output). */
    default WorkbenchRecipeTypeRef registerMachineType(String path, int inputSlots) {
        return registerWorkbenchType(path, inputSlots);
    }
    default WorkbenchRecipeTypeRef registerMachineType(ResourceId id, int inputSlots) {
        return registerWorkbenchType(id, inputSlots);
    }
    WorkbenchRecipeTypeRef registerWorkbenchType(ResourceId id, int inputSlots);

    WorkbenchRecipeTypeRef registerWorkbenchType(String path, int inputSlots);
}
