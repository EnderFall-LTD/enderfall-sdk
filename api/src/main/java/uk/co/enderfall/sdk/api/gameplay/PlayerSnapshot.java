package uk.co.enderfall.sdk.api.gameplay;

import java.util.Objects;
import java.util.UUID;

/** Small immutable view of an online server player with no native objects exposed. */
public record PlayerSnapshot(UUID playerId, double health, double maximumHealth) {
    public PlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth)
                || health < 0.0D || maximumHealth <= 0.0D || health > maximumHealth) {
            throw new IllegalArgumentException("Invalid player health values");
        }
    }

    public double missingHealth() {
        return maximumHealth - health;
    }
}
