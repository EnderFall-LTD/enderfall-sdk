package uk.co.enderfall.sdk.api.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiFocusManagerTest {
    private static final UiFocusTarget FIRST = UiFocusTarget.enabled("first", new UiRect(0, 0, 20, 20));
    private static final UiFocusTarget SECOND = UiFocusTarget.enabled("second", new UiRect(10, 10, 20, 20));

    @Test
    void pointerUsesTopmostEnabledTargetAndClearsOnOutsideClick() {
        UiFocusManager focus = new UiFocusManager();
        focus.updateTargets(List.of(FIRST, SECOND));

        assertEquals("second", focus.targetAt(15, 15).orElseThrow());
        assertTrue(focus.mouseClicked(15, 15, UiFocusManager.PRIMARY_MOUSE_BUTTON));
        assertEquals("second", focus.focusedId().orElseThrow());
        assertTrue(focus.state("second", 15, 15).hovered());
        assertFalse(focus.mouseClicked(50, 50, UiFocusManager.PRIMARY_MOUSE_BUTTON));
        assertTrue(focus.focusedId().isEmpty());
    }

    @Test
    void tabTraversalSkipsDisabledTargetsAndWrapsInBothDirections() {
        UiFocusManager focus = new UiFocusManager();
        focus.updateTargets(List.of(FIRST, UiFocusTarget.disabled("disabled", new UiRect(0, 0, 1, 1)), SECOND));

        assertTrue(focus.keyPressed(UiKeys.TAB, 0));
        assertEquals("first", focus.focusedId().orElseThrow());
        assertTrue(focus.keyPressed(UiKeys.TAB, 0));
        assertEquals("second", focus.focusedId().orElseThrow());
        assertTrue(focus.keyPressed(UiKeys.TAB, 0));
        assertEquals("first", focus.focusedId().orElseThrow());
        assertTrue(focus.keyPressed(UiKeys.TAB, UiModifiers.SHIFT));
        assertEquals("second", focus.focusedId().orElseThrow());
        assertFalse(focus.keyPressed(UiKeys.ENTER, 0));
    }

    @Test
    void layoutUpdatesPreserveValidFocusAndRejectDuplicateIds() {
        UiFocusManager focus = new UiFocusManager();
        focus.updateTargets(List.of(FIRST));
        assertTrue(focus.requestFocus("first"));
        focus.updateTargets(List.of(UiFocusTarget.enabled("first", new UiRect(100, 100, 10, 10))));
        assertTrue(focus.isFocused("first"));
        focus.updateTargets(List.of(UiFocusTarget.disabled("first", new UiRect(100, 100, 10, 10))));
        assertTrue(focus.focusedId().isEmpty());
        assertFalse(focus.requestFocus("missing"));
        assertThrows(IllegalArgumentException.class, () -> focus.updateTargets(List.of(FIRST, FIRST)));
    }
}
