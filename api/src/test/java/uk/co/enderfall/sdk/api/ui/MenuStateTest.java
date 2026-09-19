package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MenuStateTest {
    @Test
    void resolvesKnownStateAndBlanksUnknownPlaceholders() {
        MenuState state = MenuState.builder().value("charge", 42).value("maximum", 100).build();

        assertEquals("Resonance 42/100 ()", state.resolve("Resonance {charge}/{maximum} ({missing})"));
    }

    @Test
    void rejectsInvalidKeysAndUnboundedValues() {
        assertThrows(IllegalArgumentException.class, () -> MenuState.builder().value("Bad Key", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> MenuState.builder().value("message", "x".repeat(1_025)));
    }

    @Test
    void buildsDeterministicBoundedSelectionPages() {
        var list = MenuSelectionList.of("recipes", 0, 0, 100, 2);
        var entries = java.util.List.of(
                MenuSelectionEntry.enabled("a", "Alpha"),
                MenuSelectionEntry.enabled("b", "Beta"),
                MenuSelectionEntry.enabled("c", "Gamma"));
        var first = MenuState.builder().selectionPage(list, entries, 0, "b").build();
        assertEquals("a", first.value("recipes.row.0.id"));
        assertEquals("2", first.value("recipes.pages"));
        assertEquals("b", first.value("recipes.selected"));
        var second = MenuState.builder().selectionPage(list, entries, 1, "b").build();
        assertEquals("c", second.value("recipes.row.0.id"));
        assertEquals("", second.value("recipes.row.1.id"));
        assertEquals("true", second.value("recipes.previous.enabled"));
        assertEquals("false", second.value("recipes.next.enabled"));
        assertThrows(IllegalArgumentException.class,
                () -> MenuState.builder().selectionPage(list, entries, 2, ""));
    }
}
