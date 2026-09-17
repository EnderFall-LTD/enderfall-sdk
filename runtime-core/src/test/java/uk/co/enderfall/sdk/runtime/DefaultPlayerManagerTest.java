package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.gameplay.InventoryCost;
import uk.co.enderfall.sdk.api.gameplay.PlayerSnapshot;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

class DefaultPlayerManagerTest {
    private static final UUID PLAYER = UUID.fromString("18f6eaf0-397b-4f07-8424-cc860573d071");
    private static final ItemRef CRYSTAL = new ItemRef(ResourceId.of("test_mod", "crystal"));

    @Test
    void delegatesAtomicInventoryAndPlayerFeedbackOperations() {
        PlayerAdapter adapter = new PlayerAdapter();
        DefaultPlayerManager players = new DefaultPlayerManager("test_mod", "1.21.4-fabric", adapter);

        assertTrue(players.find(PLAYER).isPresent());
        assertEquals(5, players.count(PLAYER, CRYSTAL));
        assertTrue(players.tryConsume(PLAYER, InventoryCost.of(CRYSTAL, 3)));
        assertEquals(2, players.count(PLAYER, CRYSTAL));
        assertFalse(players.tryConsume(PLAYER, InventoryCost.of(CRYSTAL, 3)));

        players.give(PLAYER, CRYSTAL, 2);
        players.message(PLAYER, "hello");
        players.actionBar(PLAYER, "charge 4/10");
        players.heal(PLAYER, 2.5D);
        players.addExperience(PLAYER, 7);

        assertEquals(4, players.count(PLAYER, CRYSTAL));
        assertEquals("hello", adapter.chatMessage);
        assertEquals("charge 4/10", adapter.actionBarMessage);
        assertEquals(2.5D, adapter.healing);
        assertEquals(7, adapter.experience);
    }

    @Test
    void validatesDangerousAmountsBeforeCallingTheAdapter() {
        DefaultPlayerManager players = new DefaultPlayerManager(
                "test_mod", "1.21.4-fabric", new PlayerAdapter());

        assertThrows(IllegalArgumentException.class, () -> players.give(PLAYER, CRYSTAL, 65));
        assertThrows(IllegalArgumentException.class, () -> players.heal(PLAYER, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> players.addExperience(PLAYER, -1));
    }

    private static final class PlayerAdapter implements PlatformAdapter {
        private final Map<ResourceId, Integer> inventory = new LinkedHashMap<>(Map.of(CRYSTAL.id(), 5));
        private String chatMessage;
        private String actionBarMessage;
        private double healing;
        private int experience;

        @Override public PlatformInfo platformInfo() { return new TestPlatformInfo(); }
        @Override public CapabilitySet capabilities() { return new ImmutableCapabilitySet(java.util.Set.of()); }
        @Override public Path commonConfigDirectory() { return Path.of("config"); }
        @Override public Path serverConfigDirectory() { return Path.of("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec command) { }
        @Override public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                              PayloadReceiver receiver) { }
        @Override public void sendToServer(ResourceId id, byte[] payload) { }
        @Override public void sendToPlayer(UUID playerId, ResourceId id, byte[] payload) { }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
        @Override public Collection<UUID> connectedPlayers() { return java.util.List.of(PLAYER); }
        @Override public Optional<PlayerSnapshot> playerSnapshot(UUID playerId) {
            return Optional.of(new PlayerSnapshot(playerId, 14.0D, 20.0D));
        }
        @Override public int countPlayerItem(UUID playerId, ResourceId itemId) {
            return inventory.getOrDefault(itemId, 0);
        }
        @Override public boolean consumePlayerItems(UUID playerId, Map<ResourceId, Integer> items) {
            if (items.entrySet().stream().anyMatch(entry -> countPlayerItem(playerId, entry.getKey())
                    < entry.getValue())) {
                return false;
            }
            items.forEach((item, amount) -> inventory.compute(item, (ignored, current) -> current - amount));
            return true;
        }
        @Override public void givePlayerItem(UUID playerId, ResourceId itemId, int amount) {
            inventory.merge(itemId, amount, Integer::sum);
        }
        @Override public void sendPlayerMessage(UUID playerId, String message, boolean actionBar) {
            if (actionBar) {
                actionBarMessage = message;
            } else {
                chatMessage = message;
            }
        }
        @Override public void healPlayer(UUID playerId, double amount) { healing = amount; }
        @Override public void addPlayerExperience(UUID playerId, int points) { experience = points; }
    }

    private static final class TestPlatformInfo implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public Environment environment() { return Environment.DEDICATED_SERVER; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
