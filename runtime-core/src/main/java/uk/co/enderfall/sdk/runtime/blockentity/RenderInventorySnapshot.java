package uk.co.enderfall.sdk.runtime.blockentity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntFunction;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;

/** Full replacement of explicitly public visual slots, never the saved storage envelope.
 * A failed decode cannot change an existing snapshot. Owning game thread only.
 */
public final class RenderInventorySnapshot<S> {
    public static final int MAXIMUM_BYTES = 32_000;
    private static final int MAGIC = 0x45465249; // EFRI
    private final Map<Integer, S> stacks;
    private final BlockEntityStackCodec<S> codec;

    private RenderInventorySnapshot(Map<Integer, S> stacks, BlockEntityStackCodec<S> codec) {
        this.stacks = Collections.unmodifiableMap(new TreeMap<>(stacks));
        this.codec = codec;
    }

    /** Captures only declared slots and detaches each stack from live mutable inventory. */
    public static <S> RenderInventorySnapshot<S> capture(BlockEntitySpec spec,
            IntFunction<S> inventory, BlockEntityStackCodec<S> codec) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(codec, "codec");
        Map<Integer, S> copies = new TreeMap<>();
        for (int slot : spec.renderSlots()) {
            copies.put(slot, Objects.requireNonNull(codec.copy(Objects.requireNonNull(inventory.apply(slot), "stack")), "copy"));
        }
        return new RenderInventorySnapshot<>(copies, codec);
    }

    /** Returns a detached copy; unexposed slots are rejected rather than disclosed. */
    public S stack(int slot) {
        if (!stacks.containsKey(slot)) throw new IllegalArgumentException("Slot is not in the visual snapshot: " + slot);
        return Objects.requireNonNull(codec.copy(stacks.get(slot)), "copy");
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeByte(1);
            output.writeByte(stacks.size());
            for (var entry : stacks.entrySet()) {
                byte[] encoded = Objects.requireNonNull(codec.encode(codec.copy(entry.getValue())), "encoded stack");
                if (encoded.length > MAXIMUM_BYTES - bytes.size() - 5) {
                    throw new IllegalArgumentException("Visual inventory exceeds 32000-byte limit");
                }
                output.writeByte(entry.getKey());
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode visual inventory", impossible);
        }
    }

    public static <S> RenderInventorySnapshot<S> decode(byte[] payload, BlockEntitySpec spec,
            BlockEntityStackCodec<S> codec) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(codec, "codec");
        if (payload.length < 6 || payload.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Invalid visual inventory size");
        }
        try {
            var input = new DataInputStream(new ByteArrayInputStream(payload));
            if (input.readInt() != MAGIC || input.readUnsignedByte() != 1) {
                throw new IllegalArgumentException("Unsupported visual inventory format");
            }
            int count = input.readUnsignedByte();
            if (count != spec.renderSlots().size()) throw new IllegalArgumentException("Visual slot declarations differ");
            // Validate the complete framing before invoking any native item decoder.
            Map<Integer, byte[]> encodedStacks = new TreeMap<>();
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int slot = input.readUnsignedByte();
                int size = input.readInt();
                if (slot <= previous || !spec.renderSlots().contains(slot) || size < 0 || size > input.available()) {
                    throw new IllegalArgumentException("Invalid visual slot or item length");
                }
                previous = slot;
                encodedStacks.put(slot, input.readNBytes(size));
            }
            if (input.available() != 0) throw new IllegalArgumentException("Trailing visual inventory bytes");
            Map<Integer, S> decoded = new TreeMap<>();
            for (var entry : encodedStacks.entrySet()) {
                S stack = Objects.requireNonNull(codec.decode(entry.getValue()), "decoded stack");
                decoded.put(entry.getKey(), Objects.requireNonNull(codec.copy(stack), "copy"));
            }
            return new RenderInventorySnapshot<>(decoded, codec);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Truncated visual inventory", malformed);
        }
    }
}
