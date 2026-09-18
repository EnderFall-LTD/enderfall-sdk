package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
