package uk.co.enderfall.sdk.api.ui;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One server-owned entry displayed by a portable selection list. */
public record MenuSelectionEntry(String id, String label, boolean enabled) {
    public MenuSelectionEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (id.isBlank() || id.codePointCount(0, id.length()) > 256
                || id.getBytes(StandardCharsets.UTF_8).length > 1_024) {
            throw new IllegalArgumentException("Menu selection ID must contain 1-256 characters");
        }
        if (label.isBlank() || label.codePointCount(0, label.length()) > 128
                || label.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw new IllegalArgumentException("Menu selection label must contain 1-128 characters");
        }
        if (id.indexOf('\0') >= 0 || label.indexOf('\0') >= 0
                || id.indexOf('\n') >= 0 || label.indexOf('\n') >= 0
                || id.indexOf('\r') >= 0 || label.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Menu selections must be single-line text");
        }
    }

    public static MenuSelectionEntry enabled(String id, String label) {
        return new MenuSelectionEntry(id, label, true);
    }
}
