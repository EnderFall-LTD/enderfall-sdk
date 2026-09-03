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
import uk.co.enderfall.sdk.runtime.DisconnectHandler;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RegistrationGateAccess;

public final class DefaultNetworkManager implements NetworkManager {
    private static final UUID SERVER_CONNECTION_ID = new UUID(0L, 0L);
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final ModLogger logger;
    private final Map<ResourceId, Registration<?>> registrations = new HashMap<>();
    private final Map<UUID, Set<ProtocolKey>> remoteCapabilities = new ConcurrentHashMap<>();
    private final ResourceId manifestId;

    public DefaultNetworkManager(String modId, String target, PlatformAdapter adapter,
                                 RegistrationGateAccess gate, ModLogger logger) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
        this.logger = logger;
        manifestId = ResourceId.of(modId, "_enderfall/manifest");
        adapter.registerPayload(manifestId, PacketDirection.BIDIRECTIONAL,
                PacketType.SDK_MAXIMUM_BYTES, this::receiveCapabilityManifest);
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
        if (!requireRemote(SERVER_CONNECTION_ID, packet)) {
            return;
        }
        adapter.sendToServer(packet.id(), encode(packet, value));
    }

    @Override
    public <T> void sendToPlayer(UUID playerId, PacketType<T> packet, T value) {
        requireDirection(packet, PacketDirection.CLIENTBOUND);
        if (!requireRemote(playerId, packet)) {
            return;
        }
        adapter.sendToPlayer(playerId, packet.id(), encode(packet, value));
    }

    @Override
    public <T> void sendToAll(PacketType<T> packet, T value) {
        requireDirection(packet, PacketDirection.CLIENTBOUND);
        for (UUID playerId : adapter.connectedPlayers()) {
            sendToPlayer(playerId, packet, value);
        }
    }

    @Override
    public boolean remoteSupports(UUID playerId, PacketType<?> packet) {
        return remoteCapabilities.getOrDefault(playerId, Set.of())
                .contains(new ProtocolKey(packet.id(), packet.schemaVersion()));
    }

    /** Begins play-phase negotiation for a newly joined player. */
    public void connectionOpened(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        remoteCapabilities.remove(playerId);
        adapter.sendToPlayer(playerId, manifestId, createCapabilityManifest());
    }

    /** Removes all negotiated state for a disconnected player. */
    public void connectionClosed(UUID playerId) {
        remoteCapabilities.remove(Objects.requireNonNull(playerId, "playerId"));
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
            Set<ProtocolKey> local = localCapabilities();
            for (NetworkProtocol.PacketCapability capability : manifest.capabilities()) {
                if (capability.required()
                        && !local.contains(new ProtocolKey(capability.id(), capability.schemaVersion()))) {
                    disconnect.accept("Missing required EnderFall packet " + capability.id()
                            + " schema " + capability.schemaVersion());
                    return false;
                }
            }
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
            UUID remoteId = playerId.orElse(SERVER_CONNECTION_ID);
            if (!remoteSupports(remoteId, registration.packet)) {
                throw new PacketDecodingException("Packet was not negotiated for this connection");
            }
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

    private void receiveCapabilityManifest(byte[] payload, PacketDirection direction, Optional<UUID> playerId,
                                           DisconnectHandler disconnect) {
        UUID remoteId;
        if (direction == PacketDirection.SERVERBOUND) {
            remoteId = playerId.orElseThrow(() ->
                    new IllegalStateException("Serverbound manifest has no player"));
        } else {
            remoteId = SERVER_CONNECTION_ID;
        }
        if (acceptCapabilityManifest(remoteId, payload, disconnect::disconnect)
                && direction == PacketDirection.CLIENTBOUND) {
            adapter.sendToServer(manifestId, createCapabilityManifest());
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

    private boolean requireRemote(UUID playerId, PacketType<?> packet) {
        if (remoteSupports(playerId, packet)) {
            return true;
        }
        if (packet.requirement() == PacketRequirement.REQUIRED) {
            throw new IllegalStateException("Remote connection does not support required packet " + packet.id());
        }
        return false;
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
                    .contains(new ProtocolKey(packetId, schemaVersion)))
                    .orElseGet(() -> capabilities.getOrDefault(SERVER_CONNECTION_ID, Set.of())
                            .contains(new ProtocolKey(packetId, schemaVersion)));
        }

        @Override
        public void disconnect(String reason) {
            disconnector.accept(reason);
        }
    }
}
