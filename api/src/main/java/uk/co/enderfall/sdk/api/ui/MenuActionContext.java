package uk.co.enderfall.sdk.api.ui;

import java.util.UUID;

/** Server-side context for an action originating from the player's active menu. */
public interface MenuActionContext {
    UUID playerId();

    MenuRef menu();

    String action();

    MenuState state();

    void update(MenuState state);

    void close();
}
