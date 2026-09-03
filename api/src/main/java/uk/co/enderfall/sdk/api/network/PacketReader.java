package uk.co.enderfall.sdk.api.network;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

public interface PacketReader {
    boolean readBoolean();

    byte readByte();

    int readInt();

    long readLong();

    float readFloat();

    double readDouble();

    int readVarInt();

    String readString(int maximumBytes);

    byte[] readBytes(int maximumBytes);

    UUID readUuid();

    ResourceId readResourceId();

    int remainingBytes();
}
