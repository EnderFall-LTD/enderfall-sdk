package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;

class WorkbenchRecipeBrowserSpecTest {
    private static final WorkbenchRecipeTypeRef RECIPES =
            new WorkbenchRecipeTypeRef(ResourceId.of("example", "assembly"), 1);

    @Test void enablesAFiveEntryBrowserByDefault() {
        assertEquals(5, WorkbenchSpec.builder("Browser", RECIPES).recipeBrowser().build()
                .recipeBrowserEntries());
        assertEquals(3, WorkbenchSpec.builder("Browser", RECIPES).recipeBrowser(3).build()
                .recipeBrowserEntries());
    }

    @Test void rejectsUnsupportedBrowserShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkbenchSpec.builder("Browser", RECIPES).recipeBrowser(0));
        assertThrows(IllegalArgumentException.class,
                () -> WorkbenchSpec.builder("Browser", RECIPES).recipeBrowser(6));
    }
}
