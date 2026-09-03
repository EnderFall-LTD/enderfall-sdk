package uk.co.enderfall.sdk.runtime.network;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.network.PacketDecodingException;
import uk.co.enderfall.sdk.api.network.PacketReader;

public final class ByteArrayPacketReader implements PacketReader {
    private final ByteBuffer input;

    public ByteArrayPacketReader(byte[] bytes, int packetLimit) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > packetLimit) {
            throw new PacketDecodingException("Packet contains " + bytes.length + " bytes; limit is " + packetLimit);
        }
        input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    }

    @Override
    public boolean readBoolean() {
        byte value = readByte();
        if (value != 0 && value != 1) {
            throw new PacketDecodingException("Invalid boolean byte: " + value);
        }
        return value == 1;
    }

    @Override
    public byte readByte() {
        requireRemaining(1);
        return input.get();
    }

    @Override
    public int readInt() {
        requireRemaining(Integer.BYTES);
        return input.getInt();
    }

    @Override
    public long readLong() {
        requireRemaining(Long.BYTES);
        return input.getLong();
    }

    @Override
    public float readFloat() {
        requireRemaining(Float.BYTES);
        return input.getFloat();
    }

    @Override
    public double readDouble() {
        requireRemaining(Double.BYTES);
        return input.getDouble();
    }

    @Override
    public int readVarInt() {
        int value = 0;
        int position = 0;
        byte current;
        do {
            if (position >= 35) {
                throw new PacketDecodingException("VarInt exceeds five bytes");
            }
            current = readByte();
            if (position == 28 && (current & 0xF0) != 0) {
                throw new PacketDecodingException("VarInt exceeds 32 bits");
            }
            value |= (current & 0x7F) << position;
            position += 7;
        } while ((current & 0x80) != 0);
        return value;
    }

    @Override
    public String readString(int maximumBytes) {
        byte[] encoded = readBytes(maximumBytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new PacketDecodingException("String is not valid UTF-8", exception);
        }
    }

    @Override
    public byte[] readBytes(int maximumBytes) {
        int length = readVarInt();
        if (length < 0 || length > maximumBytes) {
            throw new PacketDecodingException("Byte array length " + length + " exceeds " + maximumBytes);
        }
        requireRemaining(length);
        byte[] result = new byte[length];
        input.get(result);
        return result;
    }

    @Override
    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    @Override
    public ResourceId readResourceId() {
        try {
            return ResourceId.parse(readString(255));
        } catch (IllegalArgumentException exception) {
            throw new PacketDecodingException("Invalid resource ID", exception);
        }
    }

    @Override
    public int remainingBytes() {
        return input.remaining();
    }

    private void requireRemaining(int bytes) {
        if (bytes < 0 || input.remaining() < bytes) {
            throw new PacketDecodingException("Truncated packet: needed " + bytes + ", remaining " + input.remaining());
        }
    }
}
