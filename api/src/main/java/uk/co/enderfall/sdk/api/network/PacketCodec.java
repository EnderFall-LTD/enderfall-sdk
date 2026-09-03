package uk.co.enderfall.sdk.api.network;

public interface PacketCodec<T> {
    void encode(PacketWriter writer, T value);

    T decode(PacketReader reader);
}
