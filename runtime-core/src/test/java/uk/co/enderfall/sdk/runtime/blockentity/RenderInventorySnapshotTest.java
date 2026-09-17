package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class RenderInventorySnapshotTest {
    private static final BlockRef BLOCK = new BlockRef(ResourceId.of("test", "board"));
    private static final BlockEntitySpec SPEC = BlockEntitySpec.builder(BLOCK)
            .inventorySlots(3).renderSlot(2).renderSlot(0).build();
    private static final BlockEntityStackCodec<int[]> CODEC = new BlockEntityStackCodec<>() {
        public int[] empty() { return new int[]{0}; }
        public int[] copy(int[] stack) { return stack.clone(); }
        public byte[] encode(int[] stack) { return ByteBuffer.allocate(4).putInt(stack[0]).array(); }
        public int[] decode(byte[] bytes) {
            if (bytes.length != 4) throw new IllegalArgumentException("Bad test stack");
            return new int[]{ByteBuffer.wrap(bytes).getInt()};
        }
    };

    @Test void explicitlyExposesSlotsAndDetachesMutableStacks() {
        int[][] live = {{5}, {999}, {7}};
        var snapshot = RenderInventorySnapshot.capture(SPEC, slot -> {
            assertNotEquals(1, slot, "Must not even read private inventory");
            return live[slot];
        }, CODEC);
        live[0][0] = 50;
        snapshot.stack(0)[0] = 70;
        assertEquals(5, snapshot.stack(0)[0]);
        assertThrows(IllegalArgumentException.class, () -> snapshot.stack(1));
        byte[] payload = snapshot.encode();
        var decoded = RenderInventorySnapshot.decode(payload, SPEC, CODEC);
        Arrays.fill(payload, (byte) 0);
        assertEquals(7, decoded.stack(2)[0]);
        assertArrayEquals(snapshot.encode(), decoded.encode());
    }

    @Test void validatesDeclarationsAndFullFraming() {
        assertTrue(BlockEntitySpec.builder(BLOCK).inventorySlots(3).build().renderSlots().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(BLOCK)
                .inventorySlots(1).renderSlot(1).build());
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(BLOCK).renderSlot(0).renderSlot(0));
        assertThrows(UnsupportedOperationException.class, () -> SPEC.renderSlots().clear());
        byte[] valid = RenderInventorySnapshot.capture(SPEC, slot -> new int[]{slot}, CODEC).encode();
        for (int size = 0; size < valid.length; size++) {
            byte[] truncated = Arrays.copyOf(valid, size);
            assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(truncated, SPEC, CODEC));
        }
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(trailing, SPEC, CODEC));
        byte[] privateSlot = valid.clone(); privateSlot[6] = 1;
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(privateSlot, SPEC, CODEC));
        byte[] hugeLength = valid.clone(); ByteBuffer.wrap(hugeLength).putInt(7, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(hugeLength, SPEC, CODEC));
        byte[] duplicate = valid.clone(); duplicate[15] = 0;
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(duplicate, SPEC, CODEC));
    }

    @Test void enforcesTotalSizeBeforeNativeDecoding() {
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(
                new byte[RenderInventorySnapshot.MAXIMUM_BYTES + 1], SPEC, CODEC));
        var oversizedCodec = new BlockEntityStackCodec<int[]>() {
            public int[] empty() { return new int[]{0}; }
            public int[] copy(int[] value) { return value.clone(); }
            public byte[] encode(int[] value) { return new byte[RenderInventorySnapshot.MAXIMUM_BYTES]; }
            public int[] decode(byte[] value) { throw new AssertionError("Must not decode"); }
        };
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.capture(
                SPEC, slot -> new int[]{1}, oversizedCodec).encode());
        byte[] invalid = RenderInventorySnapshot.capture(SPEC, slot -> new int[]{slot}, CODEC).encode();
        invalid[invalid.length - 9] = 1; // Undeclared second slot; framing fails before any decoding.
        assertThrows(IllegalArgumentException.class, () -> RenderInventorySnapshot.decode(invalid, SPEC, oversizedCodec));
    }
}
