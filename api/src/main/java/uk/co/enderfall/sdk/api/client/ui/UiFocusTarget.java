package uk.co.enderfall.sdk.api.client.ui;

import java.util.Objects;

/** A focusable element in the current portable screen layout. */
public record UiFocusTarget(String id, UiRect bounds, boolean enabled) {
    private static final int MAXIMUM_ID_LENGTH = 128;

    public UiFocusTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bounds, "bounds");
        if (id.isBlank() || id.length() > MAXIMUM_ID_LENGTH) {
            throw new IllegalArgumentException("UI focus target ID must contain 1-128 characters");
        }
    }

    public static UiFocusTarget enabled(String id, UiRect bounds) {
        return new UiFocusTarget(id, bounds, true);
    }

    public static UiFocusTarget disabled(String id, UiRect bounds) {
        return new UiFocusTarget(id, bounds, false);
    }
}
