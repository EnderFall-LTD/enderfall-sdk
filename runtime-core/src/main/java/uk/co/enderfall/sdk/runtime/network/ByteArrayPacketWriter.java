package uk.co.enderfall.sdk.runtime.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.network.PacketWriter;

public final class ByteArrayPacketWriter implements PacketWriter {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream output = new DataOutputStream(bytes);
    private final int packetLimit;

    public ByteArrayPacketWriter(int packetLimit) {
        if (packetLimit < 1) {
            throw new IllegalArgumentException("packetLimit must be positive");
        }
        this.packetLimit = packetLimit;
    }

    @Override
    public void writeBoolean(boolean value) {
        write(() -> output.writeBoolean(value));
    }

    @Override
    public void writeByte(int value) {
        if (value < Byte.MIN_VALUE || value > 255) {
            throw new IllegalArgumentException("Byte value out of range: " + value);
        }
        write(() -> output.writeByte(value));
    }

    @Override
    public void writeInt(int value) {
        write(() -> output.writeInt(value));
    }

    @Override
    public void writeLong(long value) {
        write(() -> output.writeLong(value));
    }

    @Override
    public void writeFloat(float value) {
        write(() -> output.writeFloat(value));
    }

    @Override
    public void writeDouble(double value) {
        write(() -> output.writeDouble(value));
    }

    @Override
    public void writeVarInt(int value) {
        int remaining = value;
        do {
            int next = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            writeByte(next);
        } while (remaining != 0);
    }

    @Override
    public void writeString(String value, int maximumBytes) {
        Objects.requireNonNull(value, "value");
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException("Encoded string exceeds " + maximumBytes + " bytes");
        }
        writeBytes(encoded, maximumBytes);
    }

    @Override
    public void writeBytes(byte[] value, int maximumBytes) {
        Objects.requireNonNull(value, "value");
        if (maximumBytes < 0 || value.length > maximumBytes) {
            throw new IllegalArgumentException("Byte array exceeds " + maximumBytes + " bytes");
        }
        writeVarInt(value.length);
        write(() -> output.write(value));
    }

    @Override
    public void writeUuid(UUID value) {
        Objects.requireNonNull(value, "value");
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
    }

    @Override
    public void writeResourceId(ResourceId value) {
        writeString(value.toString(), 255);
    }

    public byte[] toByteArray() {
        return bytes.toByteArray();
    }

    private void write(IoOperation operation) {
        try {
            operation.run();
            if (bytes.size() > packetLimit) {
                throw new IllegalArgumentException("Encoded packet exceeds " + packetLimit + " bytes");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory packet write failure", exception);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws IOException;
    }
}
