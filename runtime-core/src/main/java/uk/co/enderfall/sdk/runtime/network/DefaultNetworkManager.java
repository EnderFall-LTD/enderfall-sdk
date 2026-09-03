package uk.co.enderfall.sdk.runtime.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.network.NetworkContext;
import uk.co.enderfall.sdk.api.network.NetworkManager;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.network.PacketHandler;
import uk.co.enderfall.sdk.api.network.PacketRequirement;
import uk.co.enderfall.sdk.api.network.PacketType;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RegistrationGateAccess;

public final class DefaultNetworkManager implements NetworkManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final ModLogger logger;
    private final Map<ResourceId, Registration<?>> registrations = new HashMap<>();
    private final Map<UUID, Set<ProtocolKey>> remoteCapabilities = new ConcurrentHashMap<>();

    public DefaultNetworkManager(String modId, String target, PlatformAdapter adapter,
                                 RegistrationGateAccess gate, ModLogger logger) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
        this.logger = logger;
    }

    @Override
    public <T> void register(PacketType<T> packet, PacketHandler<T> handler) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(handler, "handler");
        if (!modId.equals(packet.id().namespace())) {
            throw new IllegalArgumentException("[" + modId + "] Packet ID must use the mod namespace: " + packet.id());
        }
        Registration<T> registration = new Registration<>(packet, handler);
        if (registrations.putIfAbsent(packet.id(), registration) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate packet ID " + packet.id() + " on " + target);
        }
        adapter.registerPayload(packet.id(), packet.direction(), packet.maximumBytes(),
                (payload, direction, playerId, disconnect) -> receive(registration, payload, direction, playerId,
                        disconnect::disconnect));
    }

    @Override
    public <T> void sendToServer(PacketType<T> packet, T value) {
        requireDirection(packet, PacketDirection.SERVERBOUND);
        adapter.sendToServer(packet.id(), encode(packet, value));
    }

    @Override
    public <T> void sendToPlayer(UUID playerId, PacketType<T> packet, T value) {
        requireDirection(packet, PacketDirection.CLIENTBOUND);
        requireRemote(playerId, packet);
        adapter.sendToPlayer(playerId, packet.id(), encode(packet, value));
    }

    @Override
    public <T> void sendToAll(PacketType<T> packet, T value) {
        requireDirection(packet, PacketDirection.CLIENTBOUND);
        adapter.sendToAll(packet.id(), encode(packet, value));
    }

    @Override
    public boolean remoteSupports(UUID playerId, PacketType<?> packet) {
        return remoteCapabilities.getOrDefault(playerId, Set.of())
                .contains(new ProtocolKey(packet.id(), packet.schemaVersion()));
    }

    public void setRemoteCapabilities(UUID playerId, Set<ProtocolKey> capabilities) {
        remoteCapabilities.put(playerId, Set.copyOf(capabilities));
    }

    public Set<ProtocolKey> localCapabilities() {
        return registrations.values().stream()
                .map(registration -> new ProtocolKey(registration.packet.id(), registration.packet.schemaVersion()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Creates the deterministic capability payload sent during login/configuration. */
    public byte[] createCapabilityManifest() {
        Set<NetworkProtocol.PacketCapability> capabilities = registrations.values().stream()
                .map(registration -> new NetworkProtocol.PacketCapability(registration.packet.id(),
                        registration.packet.schemaVersion(),
                        registration.packet.requirement() == PacketRequirement.REQUIRED))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return NetworkProtocol.encode(capabilities);
    }

    /** Validates and records a remote connection's packet capabilities. */
    public boolean acceptCapabilityManifest(UUID playerId, byte[] payload,
                                            java.util.function.Consumer<String> disconnect) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(disconnect, "disconnect");
        try {
            NetworkProtocol.Manifest manifest = NetworkProtocol.decode(payload);
            if (manifest.major() != NetworkProtocol.MAJOR) {
                disconnect.accept("Incompatible EnderFall SDK network protocol: local "
                        + NetworkProtocol.MAJOR + ", remote " + manifest.major());
                return false;
            }
            Set<ProtocolKey> remote = manifest.capabilities().stream()
                    .map(capability -> new ProtocolKey(capability.id(), capability.schemaVersion()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (Registration<?> registration : registrations.values()) {
                if (registration.packet.requirement() == PacketRequirement.REQUIRED
                        && !remote.contains(new ProtocolKey(registration.packet.id(),
                        registration.packet.schemaVersion()))) {
                    disconnect.accept("Missing required EnderFall packet " + registration.packet.id()
                            + " schema " + registration.packet.schemaVersion());
                    return false;
                }
            }
            remoteCapabilities.put(playerId, remote);
            return true;
        } catch (RuntimeException exception) {
            logger.warn("Rejected malformed EnderFall capability manifest: {}", exception.getMessage());
            disconnect.accept("Invalid EnderFall SDK capability manifest");
            return false;
        }
    }

    private <T> void receive(Registration<T> registration, byte[] payload, PacketDirection direction,
                             Optional<UUID> playerId, java.util.function.Consumer<String> disconnect) {
        try {
            requireIncomingDirection(registration.packet, direction);
            ByteArrayPacketReader reader = new ByteArrayPacketReader(payload, registration.packet.maximumBytes());
            T decoded = registration.packet.codec().decode(reader);
            if (reader.remainingBytes() != 0) {
                throw new PacketDecodingException("Packet has " + reader.remainingBytes() + " unread bytes");
            }
            registration.handler.handle(decoded,
                    new DefaultNetworkContext(direction, playerId, remoteCapabilities, disconnect));
        } catch (Exception exception) {
            logger.warn("Rejected malformed packet {}: {}", registration.packet.id(), exception.getMessage());
            disconnect.accept("Invalid packet " + registration.packet.id());
        }
    }

    private <T> byte[] encode(PacketType<T> packet, T value) {
        Registration<?> registered = registrations.get(packet.id());
        if (registered == null || registered.packet.schemaVersion() != packet.schemaVersion()) {
            throw new IllegalStateException("Packet is not registered: " + packet.id());
        }
        ByteArrayPacketWriter writer = new ByteArrayPacketWriter(packet.maximumBytes());
        packet.codec().encode(writer, value);
        return writer.toByteArray();
    }

    private void requireRemote(UUID playerId, PacketType<?> packet) {
        if (packet.requirement() == PacketRequirement.REQUIRED && !remoteSupports(playerId, packet)) {
            throw new IllegalStateException("Remote player does not support required packet " + packet.id());
        }
    }

    private static void requireDirection(PacketType<?> packet, PacketDirection requested) {
        if (packet.direction() != PacketDirection.BIDIRECTIONAL && packet.direction() != requested) {
            throw new IllegalArgumentException("Packet " + packet.id() + " cannot be sent " + requested);
        }
    }

    private static void requireIncomingDirection(PacketType<?> packet, PacketDirection received) {
        requireDirection(packet, received);
    }

    private record Registration<T>(PacketType<T> packet, PacketHandler<T> handler) {
    }

    public record ProtocolKey(ResourceId id, int schemaVersion) {
    }

    private record DefaultNetworkContext(PacketDirection receivedDirection, Optional<UUID> playerId,
                                         Map<UUID, Set<ProtocolKey>> capabilities,
                                         java.util.function.Consumer<String> disconnector)
            implements NetworkContext {
        @Override
        public boolean remoteSupports(ResourceId packetId, int schemaVersion) {
            return playerId.map(id -> capabilities.getOrDefault(id, Set.of())
                    .contains(new ProtocolKey(packetId, schemaVersion))).orElse(false);
        }

        @Override
        public void disconnect(String reason) {
            disconnector.accept(reason);
        }
    }
}
