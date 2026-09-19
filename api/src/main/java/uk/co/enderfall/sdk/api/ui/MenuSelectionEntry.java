package uk.co.enderfall.sdk.api.ui;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** One server-owned entry displayed by a portable selection list. */
public record MenuSelectionEntry(String id, String label, boolean enabled,
        Optional<ItemRef> icon, int count, String tooltip) {
    public MenuSelectionEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(tooltip, "tooltip");
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
        if (count < 1 || count > 999) {
            throw new IllegalArgumentException("Menu selection icon count must be between 1 and 999");
        }
        if (tooltip.codePointCount(0, tooltip.length()) > 256
                || tooltip.getBytes(StandardCharsets.UTF_8).length > 1_024
                || tooltip.indexOf('\0') >= 0 || tooltip.indexOf('\n') >= 0
                || tooltip.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Menu selection tooltip must be single-line and at most 256 characters");
        }
    }

    public MenuSelectionEntry(String id, String label, boolean enabled) {
        this(id, label, enabled, Optional.empty(), 1, "");
    }

    public static MenuSelectionEntry enabled(String id, String label) {
        return new MenuSelectionEntry(id, label, true);
    }

    /** Returns a copy with a portable item-stack icon. */
    public MenuSelectionEntry item(ItemRef item, int count) {
        return new MenuSelectionEntry(id, label, enabled, Optional.of(Objects.requireNonNull(item, "item")),
                count, tooltip);
    }

    /** Returns a copy with one bounded hover-detail line. */
    public MenuSelectionEntry details(String tooltip) {
        return new MenuSelectionEntry(id, label, enabled, icon, count,
                Objects.requireNonNull(tooltip, "tooltip"));
    }
}
