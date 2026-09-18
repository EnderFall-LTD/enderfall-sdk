package uk.co.enderfall.sdk.api.ui;

import java.util.UUID;
import java.util.Map;

/** Server-side context for an action originating from the player's active menu. */
public interface MenuActionContext {
    UUID playerId();

    MenuRef menu();

    String action();

    /** Immutable, server-validated text submitted by the active screen. */
    default Map<String, String> inputs() {
        return Map.of();
    }

    default String input(String key) {
        String value = inputs().get(key);
        if (value == null) throw new IllegalArgumentException("Missing menu input: " + key);
        return value;
    }

    MenuState state();

    void update(MenuState state);

    void close();
}
