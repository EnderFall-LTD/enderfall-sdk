package uk.co.enderfall.sdk.api.gameplay;

import java.util.Optional;
import java.util.UUID;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Server-authoritative portable player feedback, inventory, and reward operations. */
public interface PlayerManager {
    Optional<PlayerSnapshot> find(UUID playerId);

    int count(UUID playerId, ItemRef item);

    /** Checks every requirement before changing the inventory. */
    boolean tryConsume(UUID playerId, InventoryCost cost);

    /** Adds the item to inventory and safely drops any overflow at the player. */
    void give(UUID playerId, ItemRef item, int amount);

    void message(UUID playerId, String message);

    void actionBar(UUID playerId, String message);

    void heal(UUID playerId, double amount);

    void addExperience(UUID playerId, int points);
}
