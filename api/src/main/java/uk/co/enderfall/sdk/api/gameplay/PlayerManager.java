package uk.co.enderfall.sdk.api.gameplay;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.MutableItemData;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Server-authoritative portable player feedback, inventory, and reward operations. */
public interface PlayerManager {
    Optional<PlayerSnapshot> find(UUID playerId);

    int count(UUID playerId, ItemRef item);

    /** Checks every requirement before changing the inventory. */
    boolean tryConsume(UUID playerId, InventoryCost cost);

    /** Adds the item to inventory and safely drops any overflow at the player. */
    void give(UUID playerId, ItemRef item, int amount);

    /**
     * Reads one declared field from the expected item in a precise carried-inventory slot.
     * Empty means the player, item, declared value, or slot no longer matches.
     */
    <T> Optional<T> itemData(UUID playerId, PlayerInventorySlot slot, ItemRef expectedItem,
            ItemDataKey<T> key);

    /**
     * Atomically rechecks the slot and expected item before exposing its declared data on the
     * server thread. Returns false when the player moved or replaced the item.
     */
    boolean updateItemData(UUID playerId, PlayerInventorySlot slot, ItemRef expectedItem,
            Consumer<MutableItemData> update);

    void message(UUID playerId, String message);

    void actionBar(UUID playerId, String message);

    void heal(UUID playerId, double amount);

    void addExperience(UUID playerId, int points);
}
