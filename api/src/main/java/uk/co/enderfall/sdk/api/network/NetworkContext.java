package uk.co.enderfall.sdk.api.network;

import java.util.Optional;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

public interface NetworkContext {
    PacketDirection receivedDirection();

    Optional<UUID> playerId();

    boolean remoteSupports(ResourceId packetId, int schemaVersion);

    void disconnect(String reason);
}
