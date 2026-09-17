package uk.co.enderfall.sdk.runtime.blockentity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableMachineStatusTest {
    @Test void wireCodesRoundTripAndAreUnique() {
        var codes = new java.util.HashSet<Integer>();
        for (var status : PortableMachineStatus.values()) {
            assertTrue(codes.add(status.code()));
            assertSame(status, PortableMachineStatus.fromCode(status.code()));
        }
    }
    @Test void unknownCodesShowFaultNotSuccess() {
        assertSame(PortableMachineStatus.FAULT, PortableMachineStatus.fromCode(-1));
        assertSame(PortableMachineStatus.FAULT, PortableMachineStatus.fromCode(32767));
    }
}
