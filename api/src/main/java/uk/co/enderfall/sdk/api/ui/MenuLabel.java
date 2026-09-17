package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;

/** A state-templated line of text positioned inside a portable menu panel. */
public record MenuLabel(String text, int x, int y, int color, boolean centered) {
    public MenuLabel {
        Objects.requireNonNull(text, "text");
        if (text.isBlank() || text.length() > 512) {
            throw new IllegalArgumentException("Menu label text must contain 1-512 characters");
        }
    }

    public static MenuLabel text(String text, int x, int y) {
        return new MenuLabel(text, x, y, 0xFFE8E8E8, false);
    }

    public static MenuLabel centered(String text, int x, int y) {
        return new MenuLabel(text, x, y, 0xFFE8E8E8, true);
    }
}
