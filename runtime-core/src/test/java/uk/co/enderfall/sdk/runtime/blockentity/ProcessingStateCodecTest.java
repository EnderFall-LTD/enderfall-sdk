package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingStateCodecTest {
    private final PortableProcessingCycle.State state = new PortableProcessingCycle.State(
            new PortableProcessingCycle.Job(ResourceId.parse("demo:assembly"), "revision-1", 200), 123);

    @Test void roundTripsActiveAndIdleStateDeterministically() {
        assertEquals(state, ProcessingStateCodec.decode(ProcessingStateCodec.encode(state)));
        var idle = PortableProcessingCycle.State.idle();
        assertEquals(idle, ProcessingStateCodec.decode(ProcessingStateCodec.encode(idle)));
        assertArrayEquals(ProcessingStateCodec.encode(state), ProcessingStateCodec.encode(state));
    }

    @Test void rejectsAllTruncationsAndTrailingData() {
        byte[] bytes = ProcessingStateCodec.encode(state);
        for (int size = 0; size < bytes.length; size++) {
            byte[] truncated = Arrays.copyOf(bytes, size);
            assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }

    @Test void rejectsUnknownVersionInvalidFlagsAndAllocationAttacks() {
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(new byte[] {2, 0}));
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(new byte[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(new byte[] {1, 1, 127, -1, -1, -1}));
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(new byte[4097]));
    }

    @Test void rejectsMalformedUtf8AndOutOfRangeProgress() {
        byte[] bytes = ProcessingStateCodec.encode(state);
        bytes[6] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(bytes));
        byte[] invalidProgress = ProcessingStateCodec.encode(state);
        Arrays.fill(invalidProgress, invalidProgress.length - 4, invalidProgress.length, (byte) 0xff);
        assertThrows(IllegalArgumentException.class, () -> ProcessingStateCodec.decode(invalidProgress));
    }
}
