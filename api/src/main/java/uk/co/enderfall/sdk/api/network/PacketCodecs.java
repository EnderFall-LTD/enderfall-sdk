package uk.co.enderfall.sdk.api.network;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** Composable codecs for the stable wire format. */
public final class PacketCodecs {
    public static final PacketCodec<Boolean> BOOLEAN = codec(PacketWriter::writeBoolean, PacketReader::readBoolean);
    public static final PacketCodec<Integer> INT = codec(PacketWriter::writeInt, PacketReader::readInt);
    public static final PacketCodec<Integer> VAR_INT = codec(PacketWriter::writeVarInt, PacketReader::readVarInt);
    public static final PacketCodec<Long> LONG = codec(PacketWriter::writeLong, PacketReader::readLong);
    public static final PacketCodec<Float> FLOAT = codec(PacketWriter::writeFloat, PacketReader::readFloat);
    public static final PacketCodec<Double> DOUBLE = codec(PacketWriter::writeDouble, PacketReader::readDouble);

    private PacketCodecs() {
    }

    public static PacketCodec<String> string(int maximumBytes) {
        int limit = boundedLimit(maximumBytes);
        return codec((writer, value) -> writer.writeString(value, limit),
                reader -> reader.readString(limit));
    }

    public static PacketCodec<byte[]> bytes(int maximumBytes) {
        int limit = boundedLimit(maximumBytes);
        return codec((writer, value) -> writer.writeBytes(value, limit),
                reader -> reader.readBytes(limit));
    }

    /**
     * Validates a codec byte bound where the codec is declared. Without this the bound is only
     * discovered when the packet is first sent, inside loader networking, reporting "exceeds -1
     * bytes" for a value that should never have been accepted in the first place.
     */
    private static int boundedLimit(int maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > PacketType.SDK_MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "maximumBytes must be between 1 and " + PacketType.SDK_MAXIMUM_BYTES);
        }
        return maximumBytes;
    }

    public static <T> PacketCodec<List<T>> list(PacketCodec<T> elementCodec, int maximumSize) {
        Objects.requireNonNull(elementCodec, "elementCodec");
        if (maximumSize < 0) {
            throw new IllegalArgumentException("maximumSize cannot be negative");
        }
        return new PacketCodec<>() {
            @Override
            public void encode(PacketWriter writer, List<T> values) {
                if (values.size() > maximumSize) {
                    throw new IllegalArgumentException("List exceeds maximum size " + maximumSize);
                }
                writer.writeVarInt(values.size());
                values.forEach(value -> elementCodec.encode(writer, value));
            }

            @Override
            public List<T> decode(PacketReader reader) {
                int size = reader.readVarInt();
                if (size < 0 || size > maximumSize) {
                    throw new PacketDecodingException("List size " + size + " exceeds maximum " + maximumSize);
                }
                return java.util.stream.IntStream.range(0, size)
                        .mapToObj(ignored -> elementCodec.decode(reader))
                        .toList();
            }
        };
    }

    private static <T> PacketCodec<T> codec(Encoder<T> encoder, Decoder<T> decoder) {
        return new PacketCodec<>() {
            @Override
            public void encode(PacketWriter writer, T value) {
                encoder.encode(writer, value);
            }

            @Override
            public T decode(PacketReader reader) {
                return decoder.decode(reader);
            }
        };
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(PacketWriter writer, T value);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(PacketReader reader);
    }
}
