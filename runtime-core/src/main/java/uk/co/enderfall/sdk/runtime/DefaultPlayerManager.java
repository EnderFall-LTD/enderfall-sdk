package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.gameplay.InventoryCost;
import uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot;
import uk.co.enderfall.sdk.api.gameplay.PlayerManager;
import uk.co.enderfall.sdk.api.gameplay.PlayerSnapshot;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.MutableItemData;
import uk.co.enderfall.sdk.api.registry.ItemRef;

final class DefaultPlayerManager implements PlayerManager {
    private static final int MAXIMUM_GRANT = 64;
    private static final int MAXIMUM_MESSAGE_LENGTH = 1_024;

    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;

    DefaultPlayerManager(String modId, String target, PlatformAdapter adapter) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
    }

    @Override
    public Optional<PlayerSnapshot> find(UUID playerId) {
        return adapter.playerSnapshot(requirePlayerId(playerId));
    }

    @Override
    public int count(UUID playerId, ItemRef item) {
        Objects.requireNonNull(item, "item");
        return adapter.countPlayerItem(requirePlayerId(playerId), item.id());
    }

    @Override
    public boolean tryConsume(UUID playerId, InventoryCost cost) {
        Objects.requireNonNull(cost, "cost");
        return adapter.consumePlayerItems(requirePlayerId(playerId), cost.items());
    }

    @Override
    public void give(UUID playerId, ItemRef item, int amount) {
        Objects.requireNonNull(item, "item");
        if (amount < 1 || amount > MAXIMUM_GRANT) {
            throw new IllegalArgumentException("Grant amount must be between 1 and " + MAXIMUM_GRANT);
        }
        adapter.givePlayerItem(requirePlayerId(playerId), item.id(), amount);
    }

    @Override
    public <T> Optional<T> itemData(UUID playerId, PlayerInventorySlot slot, ItemRef expectedItem,
            ItemDataKey<T> key) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(expectedItem, "expectedItem");
        Objects.requireNonNull(key, "key");
        return adapter.playerItemData(requirePlayerId(playerId), slot, expectedItem.id(), key);
    }

    @Override
    public boolean updateItemData(UUID playerId, PlayerInventorySlot slot, ItemRef expectedItem,
            Consumer<MutableItemData> update) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(expectedItem, "expectedItem");
        Objects.requireNonNull(update, "update");
        return adapter.updatePlayerItemData(requirePlayerId(playerId), slot, expectedItem.id(), update);
    }

    @Override
    public void message(UUID playerId, String message) {
        adapter.sendPlayerMessage(requirePlayerId(playerId), requireMessage(message), false);
    }

    @Override
    public void actionBar(UUID playerId, String message) {
        adapter.sendPlayerMessage(requirePlayerId(playerId), requireMessage(message), true);
    }

    @Override
    public void heal(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D || amount > 1_024.0D) {
            throw new IllegalArgumentException("Heal amount must be finite and between 0 and 1024");
        }
        adapter.healPlayer(requirePlayerId(playerId), amount);
    }

    @Override
    public void addExperience(UUID playerId, int points) {
        if (points < 0 || points > 1_000_000) {
            throw new IllegalArgumentException("Experience points must be between 0 and 1000000");
        }
        adapter.addPlayerExperience(requirePlayerId(playerId), points);
    }

    private UUID requirePlayerId(UUID playerId) {
        return Objects.requireNonNull(playerId, "playerId");
    }

    private String requireMessage(String message) {
        Objects.requireNonNull(message, "message");
        if (message.length() > MAXIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Player message exceeds " + MAXIMUM_MESSAGE_LENGTH + " characters");
        }
        return message;
    }

    @Override
    public String toString() {
        return "PlayerManager[mod=" + modId + ", target=" + target + ']';
    }
}
