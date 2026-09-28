package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.Subscription;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

class DefaultClientResourceManagerTest {
    @Test
    void validatesOwnershipLifecycleAndIsolatesListenerFailures() {
        RecordingAdapter adapter = new RecordingAdapter();
        RegistrationGate gate = new RegistrationGate();
        RecordingLogger logger = new RecordingLogger();
        DefaultClientResourceManager manager =
                new DefaultClientResourceManager("test_mod", "1.21.4-fabric", adapter, gate, logger);
        AtomicInteger calls = new AtomicInteger();

        Subscription subscription = manager.onReload(ResourceId.of("test_mod", "ui"), calls::incrementAndGet);
        manager.onReload(ResourceId.of("test_mod", "broken"), () -> { throw new IllegalStateException("boom"); });
        adapter.listeners.forEach(Runnable::run);

        assertEquals(1, calls.get());
        assertEquals(1, logger.failures().size());
        assertThrows(IllegalStateException.class,
                () -> manager.onReload(ResourceId.of("test_mod", "ui"), () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> manager.onReload(ResourceId.of("another_mod", "ui"), () -> { }));

        subscription.close();
        assertFalse(subscription.active());
        adapter.listeners.forEach(Runnable::run);
        assertEquals(1, calls.get());

        gate.freeze();
        assertThrows(IllegalStateException.class,
                () -> manager.onReload(ResourceId.of("test_mod", "late"), () -> { }));
    }

    @Test
    void returnsTheNativeReloadCompletion() {
        RecordingAdapter adapter = new RecordingAdapter();
        DefaultClientResourceManager manager = new DefaultClientResourceManager(
                "test_mod", "1.21.4-fabric", adapter, new RegistrationGate(), new RecordingLogger());
        CompletionStage<Void> requested = manager.reload();
        assertFalse(requested.toCompletableFuture().isDone());
        adapter.reload.complete(null);
        assertEquals(null, requested.toCompletableFuture().join());
    }

    private static final class RecordingAdapter implements PlatformAdapter {
        private final List<Runnable> listeners = new ArrayList<>();
        private final CompletableFuture<Void> reload = new CompletableFuture<>();
        @Override public void registerClientResourceReloadListener(ResourceId id, Runnable listener) {
            listeners.add(listener);
        }
        @Override public CompletionStage<Void> reloadClientResources() { return reload; }
        @Override public PlatformInfo platformInfo() { return new ClientPlatformInfo(); }
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
    }

    private static final class ClientPlatformInfo implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public Environment environment() { return Environment.CLIENT; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
