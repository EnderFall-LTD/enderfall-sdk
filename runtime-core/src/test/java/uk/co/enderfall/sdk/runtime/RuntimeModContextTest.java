package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.EnderfallMod;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

class RuntimeModContextTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsDuplicateAndLateRegistrationsWithModAndTargetContext() {
        RecordingAdapter adapter = new RecordingAdapter(temporaryDirectory);
        RuntimeModContext context = new RuntimeModContext("test_mod", adapter);
        context.items().register("hammer", ItemSpec.builder().build());
        assertEquals(List.of(ResourceId.of("test_mod", "hammer")), adapter.items);
        assertThrows(IllegalStateException.class,
                () -> context.items().register("hammer", ItemSpec.builder().build()));

        context.freezeRegistrations();
        IllegalStateException late = assertThrows(IllegalStateException.class,
                () -> context.items().register("later", ItemSpec.builder().build()));
        assertTrue(late.getMessage().contains("[test_mod]"));
        assertTrue(late.getMessage().contains("1.21.4-fabric"));
    }

    @Test
    void dedicatedServerNeverLoadsClientEntrypointClass() {
        RuntimeModContext context = RuntimeModBootstrap.initialize("test_mod", ServerMod.class.getName(),
                ExplodingClientMod.class.getName(), new RecordingAdapter(temporaryDirectory),
                getClass().getClassLoader());
        assertTrue(context.registrationsFrozen());
    }

    public static final class ServerMod implements EnderfallMod {
        @Override
        public void initialize(ModContext context) {
            context.items().register("server_item", ItemSpec.builder().build());
        }
    }

    public static final class ExplodingClientMod implements EnderfallClientMod {
        static {
            if (true) {
                throw new AssertionError("Client entrypoint was loaded on a dedicated server");
            }
        }

        @Override
        public void initialize(ClientModContext context) {
        }
    }

    private static final class RecordingAdapter implements PlatformAdapter {
        private final Path directory;
        private final List<ResourceId> items = new ArrayList<>();

        private RecordingAdapter(Path directory) {
            this.directory = directory;
        }

        @Override public PlatformInfo platformInfo() { return new TestPlatformInfo(); }
        @Override public CapabilitySet capabilities() { return new ImmutableCapabilitySet(java.util.Set.of()); }
        @Override public Path commonConfigDirectory() { return directory; }
        @Override public Path serverConfigDirectory() { return directory.resolve("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { items.add(id); }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec command) { }
        @Override public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                              PayloadReceiver receiver) { }
        @Override public void sendToServer(ResourceId id, byte[] payload) { }
        @Override public void sendToPlayer(UUID playerId, ResourceId id, byte[] payload) { }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
    }

    private static final class TestPlatformInfo implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public Environment environment() { return Environment.DEDICATED_SERVER; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
