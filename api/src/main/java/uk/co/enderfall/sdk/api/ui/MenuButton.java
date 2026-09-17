package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import java.util.regex.Pattern;

/** A button whose action is validated against the active server-side menu session. */
public record MenuButton(String action, String text, int x, int y, int width, int height) {
    private static final Pattern ACTION = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");

    public MenuButton {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(text, "text");
        if (!ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("Invalid menu action: " + action);
        }
        if (text.isBlank() || text.length() > 128) {
            throw new IllegalArgumentException("Menu button text must contain 1-128 characters");
        }
        if (width < 20 || width > 300 || height < 10 || height > 40) {
            throw new IllegalArgumentException("Menu button dimensions are outside the supported range");
        }
    }

    public static MenuButton of(String action, String text, int x, int y, int width) {
        return new MenuButton(action, text, x, y, width, 20);
    }
}
