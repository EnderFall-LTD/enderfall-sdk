package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuSpecTest {
    @Test
    void buildsADeclarativeStateTemplatedLayout() {
        MenuSpec spec = MenuSpec.builder("Workbench {mode}")
                .size(220, 140)
                .label(MenuLabel.text("Charge: {charge}", 12, 30))
                .textInput(MenuTextInput.singleLine("name", "Name", 12, 50, 120, 40))
                .button(MenuButton.of("craft", "Craft", 20, 90, 80))
                .build();

        assertEquals(220, spec.width());
        assertEquals(1, spec.labels().size());
        assertEquals(1, spec.textInputs().size());
        assertTrue(spec.textInput("name").isPresent());
        assertTrue(spec.supportsAction("craft"));
    }

    @Test
    void rejectsDuplicateActions() {
        MenuSpec.Builder builder = MenuSpec.builder("Menu")
                .button(MenuButton.of("craft", "Craft", 10, 50, 60));

        assertThrows(IllegalArgumentException.class,
                () -> builder.button(MenuButton.of("craft", "Again", 80, 50, 60)));
    }

    @Test
    void rejectsDuplicateTextInputKeys() {
        MenuSpec.Builder builder = MenuSpec.builder("Menu")
                .textInput(MenuTextInput.singleLine("name", "Name", 10, 20, 80, 20));

        assertThrows(IllegalArgumentException.class, () -> builder.textInput(
                MenuTextInput.singleLine("name", "Again", 10, 45, 80, 20)));
    }

    @Test
    void rejectsControlsOutsideThePanel() {
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder("Menu")
                .size(120, 80)
                .label(MenuLabel.text("Outside", 121, 10))
                .build());
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder("Menu")
                .size(120, 80)
                .button(MenuButton.of("outside", "Outside", 50, 60, 80))
                .build());
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder("Menu")
                .size(120, 80)
                .textInput(MenuTextInput.singleLine("outside", "Outside", 50, 65, 80, 20))
                .build());
    }

    @Test
    void expandsASelectionListIntoStateValidatedActions() {
        var list = MenuSelectionList.of("recipes", 10, 20, 120, 3);
        var spec = MenuSpec.builder("Recipes").size(160, 120).selectionList(list).build();
        var state = MenuState.builder().selectionPage(list, java.util.List.of(
                MenuSelectionEntry.enabled("example:first", "First"),
                new MenuSelectionEntry("example:locked", "Locked", false),
                MenuSelectionEntry.enabled("example:third", "Third"),
                MenuSelectionEntry.enabled("example:fourth", "Fourth")), 0, "").build();

        assertEquals(5, spec.buttons().size());
        assertEquals(1, spec.selectionLists().size());
        assertTrue(spec.supportsAction("recipes.select.0"));
        assertTrue(spec.actionEnabled("recipes.select.0", state));
        assertFalse(spec.actionEnabled("recipes.select.1", state));
        assertFalse(spec.actionEnabled("recipes.previous", state));
        assertTrue(spec.actionEnabled("recipes.next", state));
        assertEquals("> First", spec.buttonText(spec.buttons().get(0), MenuState.builder()
                .selectionPage(list, java.util.List.of(
                        MenuSelectionEntry.enabled("example:first", "First")), 0, "example:first")
                .build()));
        assertEquals("recipes.next", spec.scrollAction(20, 30, -1.0D, state).orElseThrow());
        assertTrue(spec.scrollAction(20, 30, 1.0D, state).isEmpty());
        assertTrue(spec.scrollAction(150, 30, -1.0D, state).isEmpty());
        var selected = list.resolve("recipes.select.0", state).orElseThrow();
        assertEquals(MenuListAction.Type.SELECT, selected.type());
        assertEquals("example:first", selected.entryId());
    }

    @Test
    void rejectsDuplicateAndOutOfBoundsSelectionLists() {
        var list = MenuSelectionList.of("recipes", 10, 20, 100, 2);
        var builder = MenuSpec.builder("Recipes").selectionList(list);
        assertThrows(IllegalArgumentException.class, () -> builder.selectionList(list));
        assertThrows(IllegalArgumentException.class, () -> MenuSpec.builder("Recipes").size(120, 80)
                .selectionList(MenuSelectionList.of("large", 10, 20, 100, 3)).build());
    }
}
