package uk.co.enderfall.sdk.api.network;

@FunctionalInterface
public interface PacketHandler<T> {
    void handle(T packet, NetworkContext context) throws Exception;
}
