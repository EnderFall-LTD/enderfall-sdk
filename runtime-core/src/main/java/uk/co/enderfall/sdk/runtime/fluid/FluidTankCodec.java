package uk.co.enderfall.sdk.runtime.fluid;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/** Bounded, versioned, network-order tank snapshot. Capacity belongs to the definition, not save data. */
public final class FluidTankCodec {
    private static final int MAGIC = 0x4546544B; // EFTK
    private static final int HEADER = 15;
    public static final int MAXIMUM_ID_BYTES = 1024;
    public static final int MAXIMUM_BYTES = HEADER + MAXIMUM_ID_BYTES;

    private FluidTankCodec() { }

    public static byte[] encode(Optional<FluidVolume> contents) {
        Objects.requireNonNull(contents, "contents");
        byte[] id = contents.map(value -> value.fluid().toString().getBytes(StandardCharsets.UTF_8))
                .orElseGet(() -> new byte[0]);
        if (id.length > MAXIMUM_ID_BYTES) throw new IllegalArgumentException("Fluid ID exceeds save limit");
        return ByteBuffer.allocate(HEADER + id.length).putInt(MAGIC).put((byte) 1)
                .putLong(contents.map(FluidVolume::amount).orElse(0L))
                .putShort((short) id.length).put(id).array();
    }

    public static Optional<FluidVolume> decode(byte[] bytes, long capacity) {
        Objects.requireNonNull(bytes, "bytes");
        if (capacity <= 0) throw new IllegalArgumentException("Invalid tank capacity");
        if (bytes.length < HEADER || bytes.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Invalid tank snapshot size");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes);
        if (input.getInt() != MAGIC || input.get() != 1) {
            throw new IllegalArgumentException("Unsupported tank snapshot format");
        }
        long amount = input.getLong();
        int length = Short.toUnsignedInt(input.getShort());
        if (length > MAXIMUM_ID_BYTES || length != input.remaining() || amount < 0 || amount > capacity) {
            throw new IllegalArgumentException("Invalid tank snapshot contents");
        }
        if (amount == 0) {
            if (length != 0) throw new IllegalArgumentException("Empty tank must not carry a fluid ID");
            return Optional.empty();
        }
        try {
            String id = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(input).toString();
            return Optional.of(new FluidVolume(ResourceId.parse(id), amount));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid tank fluid ID encoding", exception);
        }
    }
}
