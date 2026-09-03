package uk.co.enderfall.sdk.api.event;

import java.util.Objects;
import java.util.UUID;

public record PlayerEvent(Action action, UUID playerId, String playerName) {
    public PlayerEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
    }

    public enum Action {
        JOIN,
        LEAVE
    }
}
