package uk.co.enderfall.sdk.runtime.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.network.PacketCodec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.network.PacketReader;
import uk.co.enderfall.sdk.api.network.PacketRequirement;
import uk.co.enderfall.sdk.api.network.PacketType;
import uk.co.enderfall.sdk.api.network.PacketWriter;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.runtime.ImmutableCapabilitySet;
import uk.co.enderfall.sdk.runtime.PayloadReceiver;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

class DefaultNetworkManagerTest {
    private static final UUID PLAYER_ID = UUID.fromString("0ac1e217-8995-4dce-bc8a-e18fdb52d8bb");

    @TempDir
    Path temporaryDirectory;

    @Test
    void negotiatesBeforeDeliveringRequiredPacketsAndSkipsUnsupportedOptionalPackets() {
        LoopbackAdapter serverAdapter = new LoopbackAdapter(temporaryDirectory, Environment.DEDICATED_SERVER);
        LoopbackAdapter clientAdapter = new LoopbackAdapter(temporaryDirectory, Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;

        RuntimeModContext server = new RuntimeModContext("test_mod", serverAdapter);
        RuntimeModContext client = new RuntimeModContext("test_mod", clientAdapter);
        PacketType<String> required = packet("required_echo", PacketDirection.BIDIRECTIONAL,
                PacketRequirement.REQUIRED);
        PacketType<String> optional = packet("optional_notice", PacketDirection.CLIENTBOUND,
                PacketRequirement.OPTIONAL);
        AtomicReference<String> serverValue = new AtomicReference<>();
        AtomicReference<String> clientValue = new AtomicReference<>();
        server.networking().register(required, (value, context) -> serverValue.set(value));
        client.networking().register(required, (value, context) -> clientValue.set(value));
        server.networking().register(optional, (value, context) -> { });

        server.runtimeNetworking().connectionOpened(PLAYER_ID);

        assertTrue(server.networking().remoteSupports(PLAYER_ID, required));
        client.networking().sendToServer(required, "from-client");
        server.networking().sendToPlayer(PLAYER_ID, required, "from-server");
        server.networking().sendToPlayer(PLAYER_ID, optional, "not-supported");

        assertEquals("from-client", serverValue.get());
        assertEquals("from-server", clientValue.get());
        assertEquals(1, clientAdapter.deliveredConsumerPackets);
        assertTrue(serverAdapter.disconnects.isEmpty());
        assertTrue(clientAdapter.disconnects.isEmpty());
    }

    private static PacketType<String> packet(String path, PacketDirection direction,
                                             PacketRequirement requirement) {
        return new PacketType<>(ResourceId.of("test_mod", path), 1, direction, requirement, 128,
                new PacketCodec<>() {
                    @Override
                    public void encode(PacketWriter writer, String value) {
                        writer.writeString(value, 64);
                    }

                    @Override
                    public String decode(PacketReader reader) {
                        return reader.readString(64);
                    }
                });
    }

    private static final class LoopbackAdapter implements PlatformAdapter {
        private final Path directory;
        private final Environment environment;
        private final Map<ResourceId, PayloadReceiver> receivers = new LinkedHashMap<>();
        private final List<String> disconnects = new ArrayList<>();
        private LoopbackAdapter peer;
        private int deliveredConsumerPackets;

        private LoopbackAdapter(Path directory, Environment environment) {
            this.directory = directory;
            this.environment = environment;
        }

        @Override
        public PlatformInfo platformInfo() {
            return new PlatformInfo() {
                @Override public Loader loader() { return Loader.FABRIC; }
                @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
                @Override public Environment environment() { return environment; }
                @Override public boolean isModLoaded(String modId) { return true; }
                @Override public Optional<String> modVersion(String modId) { return Optional.of("test"); }
            };
        }

        @Override public CapabilitySet capabilities() { return new ImmutableCapabilitySet(java.util.Set.of()); }
        @Override public Path commonConfigDirectory() { return directory; }
        @Override public Path serverConfigDirectory() { return directory.resolve("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec command) { }

        @Override
        public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                    PayloadReceiver receiver) {
            receivers.put(id, receiver);
        }

        @Override
        public void sendToServer(ResourceId id, byte[] payload) {
            deliver(peer, id, payload, PacketDirection.SERVERBOUND, Optional.of(PLAYER_ID));
        }

        @Override
        public void sendToPlayer(UUID playerId, ResourceId id, byte[] payload) {
            deliver(peer, id, payload, PacketDirection.CLIENTBOUND, Optional.empty());
        }

        @Override public void sendToAll(ResourceId id, byte[] payload) { sendToPlayer(PLAYER_ID, id, payload); }

        @Override
        public Collection<UUID> connectedPlayers() {
            return environment == Environment.DEDICATED_SERVER ? List.of(PLAYER_ID) : List.of();
        }

        private void deliver(LoopbackAdapter destination, ResourceId id, byte[] payload,
                             PacketDirection direction, Optional<UUID> playerId) {
            PayloadReceiver receiver = destination.receivers.get(id);
            if (receiver == null) {
                throw new AssertionError("No receiver for " + id);
            }
            if (!id.path().equals("_enderfall/manifest")) {
                destination.deliveredConsumerPackets++;
            }
            receiver.receive(payload, direction, playerId, destination.disconnects::add);
        }
    }
}
