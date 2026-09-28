package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

class DefaultClientCommandManagerTest {
    @Test
    void validatesClientSafeCommandsAndDelegatesOnce() {
        FakeAdapter adapter = new FakeAdapter();
        RegistrationGate gate = new RegistrationGate();
        DefaultClientCommandManager manager = new DefaultClientCommandManager(
                "example", "1.21.4-fabric", adapter, gate);
        CommandSpec valid = CommandSpec.builder("enderui")
                .argument(Arguments.optional(Arguments.word("operation")))
                .executes(context -> 1)
                .build();

        manager.register(valid);
        assertEquals(valid, adapter.command);
        assertThrows(IllegalStateException.class, () -> manager.register(valid));
        assertThrows(IllegalStateException.class, () -> manager.register(CommandSpec.builder("admin")
                .permissionLevel(2).executes(context -> 1).build()));
        assertThrows(IllegalStateException.class, () -> manager.register(CommandSpec.builder("player")
                .argument(Arguments.player("target")).executes(context -> 1).build()));
        gate.freeze();
        assertThrows(IllegalStateException.class, () -> manager.register(CommandSpec.builder("late")
                .executes(context -> 1).build()));
    }

    private static final class FakeAdapter implements PlatformAdapter {
        private CommandSpec command;

        @Override public void registerClientCommand(CommandSpec value) { command = value; }
        @Override public PlatformInfo platformInfo() { return new ClientPlatformInfo(); }
        @Override public CapabilitySet capabilities() {
            return new ImmutableCapabilitySet(java.util.Set.of());
        }
        @Override public Path commonConfigDirectory() { return Path.of("config"); }
        @Override public Path serverConfigDirectory() { return Path.of("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec item) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec spec) { }
        @Override public void registerPayload(ResourceId id,
                uk.co.enderfall.sdk.api.network.PacketDirection direction, int maximumBytes,
                PayloadReceiver receiver) { }
        @Override public void sendToServer(ResourceId id, byte[] payload) { }
        @Override public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) { }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
    }

    private static final class ClientPlatformInfo implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public Environment environment() { return Environment.CLIENT; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
