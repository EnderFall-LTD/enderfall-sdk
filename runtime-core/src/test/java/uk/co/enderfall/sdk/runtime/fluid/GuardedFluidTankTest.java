package uk.co.enderfall.sdk.runtime.fluid;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

class GuardedFluidTankTest {
    @Test void everyRetainedHandleAccessChecksOwnerAndOnlyRealChangesNotify() {
        var storage = new PortableFluidTank(100);
        boolean[] attached = {true};
        int[] notifications = {0};
        var tank = new GuardedFluidTank(storage, () -> {
            if (!attached[0]) throw new IllegalStateException("Detached");
        }, () -> notifications[0]++);
        var water = new FluidVolume(ResourceId.parse("minecraft:water"), 80);
        tank.fill(water, true);
        assertEquals(0, notifications[0]);
        tank.fill(water, false);
        tank.drain(water.fluid(), 0, false);
        assertEquals(1, notifications[0]);
        attached[0] = false;
        assertThrows(IllegalStateException.class, tank::capacity);
        assertThrows(IllegalStateException.class, tank::contents);
        assertThrows(IllegalStateException.class, () -> tank.fill(water, true));
        assertThrows(IllegalStateException.class, () -> tank.drain(water.fluid(), 1, false));
        assertEquals(80, storage.contents().orElseThrow().amount());
    }
}
