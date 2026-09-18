package uk.co.enderfall.sdk.api.item;

import java.util.Objects;

/** One immutable literal or translated line in a portable item tooltip. */
public record TooltipLine(String text, boolean translated, TooltipColor color, TooltipVisibility visibility) {
    public TooltipLine {
        text = Objects.requireNonNull(text, "text");
        color = Objects.requireNonNull(color, "color");
        visibility = Objects.requireNonNull(visibility, "visibility");
        if (text.isBlank()) throw new IllegalArgumentException("Tooltip text cannot be blank");
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
