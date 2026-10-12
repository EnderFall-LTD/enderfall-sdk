package uk.co.enderfall.sdk.api.item;

import java.util.Objects;

/** One immutable literal or translated line in a portable item tooltip. */
public record TooltipLine(String text, boolean translated, TooltipColor color, TooltipVisibility visibility) {
    /**
     * Maximum characters in one tooltip line. Matches the bound the generated portable screens
     * already apply to tooltip lines, so declaring the line is the single place it is enforced.
     */
    public static final int MAXIMUM_CHARACTERS = 1_024;

    public TooltipLine {
        text = Objects.requireNonNull(text, "text");
        color = Objects.requireNonNull(color, "color");
        visibility = Objects.requireNonNull(visibility, "visibility");
        if (text.isBlank()) throw new IllegalArgumentException("Tooltip text cannot be blank");
        // One line per record, like every other portable text type. An unbounded or multi-line
        // value reaches native tooltip rendering and item-tooltip networking verbatim.
        if (text.length() > MAXIMUM_CHARACTERS
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Tooltip text must be a single line of at most "
                    + MAXIMUM_CHARACTERS + " characters");
        }
    }

    public static TooltipLine translated(String translationKey) {
        return translated(translationKey, TooltipColor.GRAY, TooltipVisibility.ALWAYS);
    }

    public static TooltipLine translated(String translationKey, TooltipColor color,
            TooltipVisibility visibility) {
        return new TooltipLine(translationKey, true, color, visibility);
    }

    public static TooltipLine literal(String text) {
        return literal(text, TooltipColor.GRAY, TooltipVisibility.ALWAYS);
    }

    public static TooltipLine literal(String text, TooltipColor color, TooltipVisibility visibility) {
        return new TooltipLine(text, false, color, visibility);
    }
}
