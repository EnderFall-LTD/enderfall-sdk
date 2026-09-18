package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MenuTextInputTest {
    @Test
    void validatesCodePointsUtf8BytesAndLinesIndependently() {
        MenuTextInput unicode = new MenuTextInput(
                "letter.text", "Write...", 10, 20, 100, 60, 2, 4, 2, true);

        assertEquals("ab", unicode.validate("ab"));
        assertEquals("\u00e9\u00e9", unicode.validate("\u00e9\u00e9"));
        assertThrows(IllegalArgumentException.class, () -> unicode.validate("\ud83d\ude00\ud83d\ude00"));
        assertThrows(IllegalArgumentException.class, () -> unicode.validate("a\nb\nc"));
    }

    @Test
    void singleLineRejectsNewlinesAndUnsupportedControls() {
        MenuTextInput input = MenuTextInput.singleLine("name", "Name", 10, 20, 100, 20);

        assertThrows(IllegalArgumentException.class, () -> input.validate("first\nsecond"));
        assertThrows(IllegalArgumentException.class, () -> input.validate("first\rsecond"));
        assertThrows(IllegalArgumentException.class, () -> input.validate("first\0second"));
    }

    @Test
    void rejectsUnsafeOrUnboundedDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> MenuTextInput.singleLine("Bad Key", "", 0, 0, 100, 20));
        assertThrows(IllegalArgumentException.class,
                () -> MenuTextInput.multiline("text", "", 0, 0, 100, 60, 1_025, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuTextInput("text", "", 0, 0, 100, 60, 20, 80, 65, true));
    }
}
