package uk.co.enderfall.sdk.api.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PacketCodecsTest {
    @Test
    void byteBoundsAreValidatedWhereTheCodecIsDeclared() {
        // A negative or oversized bound used to be accepted here and only fail later, inside loader
        // networking, reporting "exceeds -1 bytes" for a value that should never have compiled.
        for (int bound : new int[] {-1, 0, PacketType.SDK_MAXIMUM_BYTES + 1}) {
            assertThrows(IllegalArgumentException.class, () -> PacketCodecs.string(bound));
            assertThrows(IllegalArgumentException.class, () -> PacketCodecs.bytes(bound));
        }
        assertThrows(IllegalArgumentException.class, () -> PacketCodecs.list(PacketCodecs.INT, -1));
    }

    @Test
    void acceptsBoundsInsideTheUniformPayloadCeiling() {
        assertDoesNotThrow(() -> PacketCodecs.string(1));
        assertDoesNotThrow(() -> PacketCodecs.bytes(PacketType.SDK_MAXIMUM_BYTES));
        assertDoesNotThrow(() -> PacketCodecs.list(PacketCodecs.INT, 0));
        assertDoesNotThrow(() -> PacketCodecs.list(PacketCodecs.INT, 1024));
    }
}