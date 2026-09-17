package uk.co.enderfall.sdk.runtime.fluid;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.api.fluid.FluidPortMode;

class FluidPortViewTest {
    @Test void tankFacesDefaultClosedAndDefensivelyCopyPolicies() {
        var ports = new java.util.EnumMap<uk.co.enderfall.sdk.api.fluid.FluidFace, FluidPortMode>(
                uk.co.enderfall.sdk.api.fluid.FluidFace.class);
        ports.put(uk.co.enderfall.sdk.api.fluid.FluidFace.UP, FluidPortMode.INPUT);
        var spec = new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("water", 100, ports);
        ports.clear();
        assertEquals(FluidPortMode.INPUT, spec.port(uk.co.enderfall.sdk.api.fluid.FluidFace.UP));
        assertEquals(FluidPortMode.CLOSED, spec.port(uk.co.enderfall.sdk.api.fluid.FluidFace.DOWN));
        assertThrows(UnsupportedOperationException.class, () -> spec.ports().clear());
    }
    @Test void allModesEnforceBothSimulatedAndExecutedOperations() {
        var water = new FluidVolume(ResourceId.parse("minecraft:water"), 10);
        for (var mode : FluidPortMode.values()) {
            var tank = new PortableFluidTank(100);
            tank.fill(water, false);
            var port = new FluidPortView(tank, mode);
            assertEquals(mode.allowsFill() ? 10 : 0, port.fill(water, true));
            assertEquals(mode.allowsDrain() ? 5 : 0, port.drain(water.fluid(), 5, true));
            assertEquals(10, tank.contents().orElseThrow().amount());
            assertEquals(mode.allowsFill() ? 10 : 0, port.fill(water, false));
            assertEquals(mode.allowsDrain() ? 5 : 0, port.drain(water.fluid(), 5, false));
            assertEquals(10 + (mode.allowsFill() ? 10 : 0) - (mode.allowsDrain() ? 5 : 0),
                    tank.contents().orElseThrow().amount());
        }
    }
}
