package uk.co.enderfall.sdk.api.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TooltipLineTest {
    @Test
    void acceptsASingleBoundedLine() {
        TooltipLine line = TooltipLine.literal("Resonance amplifier");
        assertEquals("Resonance amplifier", line.text());
        assertEquals(TooltipColor.GRAY, line.color());
        assertEquals(TooltipVisibility.ALWAYS, line.visibility());
        assertEquals(TooltipLine.MAXIMUM_CHARACTERS, "x".repeat(TooltipLine.MAXIMUM_CHARACTERS).length());
        TooltipLine.literal("x".repeat(TooltipLine.MAXIMUM_CHARACTERS));
    }

    @Test
    void rejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> TooltipLine.literal("   "));
        assertThrows(NullPointerException.class, () -> TooltipLine.literal(null));
    }

    @Test
    void rejectsTextBeyondTheBound() {
        assertThrows(IllegalArgumentException.class,
                () -> TooltipLine.literal("x".repeat(TooltipLine.MAXIMUM_CHARACTERS + 1)));
        assertThrows(IllegalArgumentException.class, () -> TooltipLine.translated(
                "key." + "x".repeat(TooltipLine.MAXIMUM_CHARACTERS)));
    }

    @Test
    void rejectsTextThatIsNotASingleLine() {
        // One record is one rendered line; newlines and NUL would otherwise reach native tooltip
        // rendering and item-tooltip networking unchecked.
        assertThrows(IllegalArgumentException.class, () -> TooltipLine.literal("first\nsecond"));
        assertThrows(IllegalArgumentException.class, () -> TooltipLine.literal("first\r\nsecond"));
        assertThrows(IllegalArgumentException.class, () -> TooltipLine.literal("with \0 nul"));
    }
}