package uk.co.enderfall.sdk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ui.MenuSelectionEntry;
import uk.co.enderfall.sdk.api.ui.MenuSelectionList;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;

/**
 * MenuState and MenuSpec are both public API and must agree on how much synchronized state one
 * menu may produce. This pins that agreement so a consumer can never legally declare a menu the
 * SDK then refuses to open.
 */
class MenuStateCapacityContractTest {
    private static final MenuSelectionList RECIPES = MenuSelectionList.of("recipes", 8, 0, 120, 8);
    private static final MenuSelectionList CRAFTS = MenuSelectionList.of("crafts", 8, 60, 120, 8);

    private static List<MenuSelectionEntry> entries() {
        return List.of(new MenuSelectionEntry("test:first", "First", true));
    }

    @Test
    void theLargestLegalSelectionPageFitsInsideTheStateBudget() {
        // Two eight-row lists are the largest selection shape MenuSpec accepts.
        MenuSpec spec = MenuSpec.builder("Browser").size(320, 240)
                .selectionList(RECIPES)
                .selectionList(CRAFTS)
                .build();
        assertEquals(2, spec.selectionLists().size());

        MenuState state = MenuState.builder()
                .selectionPage(RECIPES, entries(), 0, "")
                .selectionPage(CRAFTS, entries(), 0, "")
                .build();

        // Two lists x (8 rows x 6 row keys + 5 page keys).
        assertEquals(2 * (8 * 6 + 5), state.values().size());
        assertTrue(2 * (8 * 6 + 5) <= MenuState.MAXIMUM_ENTRIES,
                "MenuState.MAXIMUM_ENTRIES (" + MenuState.MAXIMUM_ENTRIES
                        + ") cannot hold the largest MenuSpec a consumer may legally declare");
    }

    @Test
    void theStateBudgetIsStillEnforced() {
        MenuState.Builder builder = MenuState.builder();
        for (int index = 0; index < MenuState.MAXIMUM_ENTRIES; index++) {
            builder.value("k" + index, "v");
        }
        assertEquals(MenuState.MAXIMUM_ENTRIES, builder.build().values().size());
        assertThrows(IllegalArgumentException.class, () -> builder.value("overflow", "v"));
    }
}