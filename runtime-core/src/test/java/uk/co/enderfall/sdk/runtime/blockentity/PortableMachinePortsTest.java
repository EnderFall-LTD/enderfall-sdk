package uk.co.enderfall.sdk.runtime.blockentity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.enderfall.sdk.runtime.blockentity.PortableMachinePorts.Face.*;

class PortableMachinePortsTest {
    @Test void threeInputsHaveDistinctFacesAndBottomIsOutputOnly() {
        var ports = new PortableMachinePorts(3);
        assertArrayEquals(new int[] {0}, ports.slots(UP));
        assertArrayEquals(new int[] {1}, ports.slots(NORTH));
        assertArrayEquals(new int[] {2}, ports.slots(EAST));
        assertArrayEquals(new int[] {3}, ports.slots(DOWN));
        assertEquals(0, ports.slots(SOUTH).length);
        assertEquals(0, ports.slots(WEST).length);
    }

    @Test void everySupportedSizeBlocksInputExtractionAndOutputInsertion() {
        for (int inputs = 1; inputs <= 5; inputs++) {
            var ports = new PortableMachinePorts(inputs);
            for (var face : PortableMachinePorts.Face.values()) {
                for (int slot = -1; slot <= inputs + 1; slot++) {
                    if (ports.canInsert(slot, face)) {
                        assertTrue(slot >= 0 && slot < inputs);
                        assertArrayEquals(new int[] {slot}, ports.slots(face));
                        assertNotEquals(DOWN, face);
                    }
                    assertEquals(face == DOWN && slot == inputs, ports.canExtract(slot, face));
                    assertFalse(ports.canInsert(inputs, face));
                }
            }
        }
    }

    @Test void rejectsBadSizeAndDoesNotExposeMutableRouting() {
        assertThrows(IllegalArgumentException.class, () -> new PortableMachinePorts(0));
        assertThrows(IllegalArgumentException.class, () -> new PortableMachinePorts(6));
        var ports = new PortableMachinePorts(3);
        ports.slots(UP)[0] = 3;
        assertArrayEquals(new int[] {0}, ports.slots(UP));
        assertFalse(ports.canInsert(0, null));
        assertFalse(ports.canExtract(3, null));
    }
}
