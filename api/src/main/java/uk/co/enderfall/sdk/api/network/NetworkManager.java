package uk.co.enderfall.sdk.api.network;

import java.util.UUID;

public interface NetworkManager {
    <T> void register(PacketType<T> packet, PacketHandler<T> handler);

    <T> void sendToServer(PacketType<T> packet, T value);

    <T> void sendToPlayer(UUID playerId, PacketType<T> packet, T value);

    <T> void sendToAll(PacketType<T> packet, T value);

    boolean remoteSupports(UUID playerId, PacketType<?> packet);
}
