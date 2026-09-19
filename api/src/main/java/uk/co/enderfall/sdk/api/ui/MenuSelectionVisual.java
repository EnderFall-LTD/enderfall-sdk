package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** A resolved, server-owned list row ready for a target-native screen renderer. */
public record MenuSelectionVisual(String entryId, int x, int y, int width, int height,
        Optional<ItemRef> icon, int count, String tooltip, boolean enabled, boolean selected) {
    public MenuSelectionVisual {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(tooltip, "tooltip");
        if (entryId.isBlank() || x < 0 || y < 0 || width < 1 || height < 1) {
            throw new IllegalArgumentException("Resolved menu selection row is invalid");
        }
        if (count < 1 || count > 999) {
            throw new IllegalArgumentException("Resolved menu selection count is invalid");
        }
    }
}
