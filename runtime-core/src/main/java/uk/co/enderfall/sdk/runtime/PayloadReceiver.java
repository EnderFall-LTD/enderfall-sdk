package uk.co.enderfall.sdk.runtime;

import java.util.Optional;
import java.util.UUID;
import uk.co.enderfall.sdk.api.network.PacketDirection;

@FunctionalInterface
public interface PayloadReceiver {
    void receive(byte[] payload, PacketDirection direction, Optional<UUID> playerId, DisconnectHandler disconnect);
}
