package uk.co.enderfall.sdk.api.network;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

public interface PacketWriter {
    void writeBoolean(boolean value);

    void writeByte(int value);

    void writeInt(int value);

    void writeLong(long value);

    void writeFloat(float value);

    void writeDouble(double value);

    void writeVarInt(int value);

    void writeString(String value, int maximumBytes);

    void writeBytes(byte[] value, int maximumBytes);

    void writeUuid(UUID value);

    void writeResourceId(ResourceId value);
}
