package uk.co.enderfall.sdk.api.ui;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import static org.junit.jupiter.api.Assertions.*;

class TimedWorkbenchSpecTest {
    private final WorkbenchRecipeTypeRef recipes = new WorkbenchRecipeTypeRef(ResourceId.parse("test:assembly"), 3);
    private BlockEntitySpec storage(int slots) {
        return BlockEntitySpec.builder(new BlockRef(ResourceId.parse("test:machine"))).inventorySlots(slots).build();
    }
    @Test void requiresSeparateStoredOutputAndValidDuration() {
        assertThrows(IllegalArgumentException.class, () -> WorkbenchSpec.builder("Machine", recipes).persistentTimed(storage(3), 100));
        assertThrows(IllegalArgumentException.class, () -> WorkbenchSpec.builder("Machine", recipes).persistentTimed(storage(4), 0));
        assertThrows(IllegalArgumentException.class, () -> WorkbenchSpec.builder("Machine", recipes).persistentTimed(storage(4), 1_728_001));
        var spec = WorkbenchSpec.builder("Machine", recipes).persistentTimed(storage(4), 100).build();
        assertEquals(100, spec.processingTicks());
        assertEquals(4, spec.storage().orElseThrow().inventorySlots());
    }
    @Test void switchingBackToInstantClearsDuration() {
        var spec = WorkbenchSpec.builder("Machine", recipes).persistentTimed(storage(4), 100).persistent(storage(3)).build();
        assertEquals(0, spec.processingTicks());
    }
}
