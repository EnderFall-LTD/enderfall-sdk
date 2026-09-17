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
                () -> MenuState.builder().value("message", "x".repeat(513)));
    }
}
