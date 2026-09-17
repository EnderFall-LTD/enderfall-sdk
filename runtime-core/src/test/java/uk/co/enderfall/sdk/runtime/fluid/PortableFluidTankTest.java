package uk.co.enderfall.sdk.runtime.fluid;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

class PortableFluidTankTest {
    private static final ResourceId WATER = ResourceId.parse("minecraft:water");
    private static final ResourceId LAVA = ResourceId.parse("minecraft:lava");

    @Test void simulationsAndWrongFluidNeverMutate() {
        var tank = new PortableFluidTank(100);
        assertEquals(100, tank.fill(new FluidVolume(WATER, 200), true));
        assertEquals(0, tank.revision());
        assertTrue(tank.contents().isEmpty());
        tank.fill(new FluidVolume(WATER, 60), false);
        assertEquals(0, tank.fill(new FluidVolume(LAVA, 10), false));
        assertEquals(0, tank.drain(LAVA, 20, false));
        assertEquals(60, tank.drain(WATER, 100, true));
        assertEquals(1, tank.revision());
        assertEquals(60, tank.contents().orElseThrow().amount());
    }

    @Test void transferConservesFluidAndRespectsCapacity() {
        var source = new PortableFluidTank(200);
        var destination = new PortableFluidTank(100);
        source.fill(new FluidVolume(WATER, 150), false);
        destination.fill(new FluidVolume(WATER, 80), false);
        assertEquals(20, source.transferTo(destination, 100, true));
        assertEquals(150, source.contents().orElseThrow().amount());
        assertEquals(20, source.transferTo(destination, 100, false));
        assertEquals(130, source.contents().orElseThrow().amount());
        assertEquals(100, destination.contents().orElseThrow().amount());
        assertEquals(0, source.transferTo(destination, 100, false));
        assertEquals(0, source.transferTo(source, 100, false));
    }

    @Test void incompatibleDestinationAndInvalidRestorePreserveBothSides() {
        var source = new PortableFluidTank(100);
        var destination = new PortableFluidTank(100);
        source.fill(new FluidVolume(WATER, 80), false);
        destination.fill(new FluidVolume(LAVA, 50), false);
        assertEquals(0, source.transferTo(destination, 80, false));
        assertThrows(IllegalArgumentException.class,
                () -> source.restore(Optional.of(new FluidVolume(LAVA, 101))));
        assertEquals(new FluidVolume(WATER, 80), source.contents().orElseThrow());
        assertEquals(new FluidVolume(LAVA, 50), destination.contents().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> source.transferTo(destination, -1, false));
    }

    @Test void emptyTankCanSwitchFluidAndLargeAmountsCannotOverflow() {
        var tank = new PortableFluidTank(Long.MAX_VALUE);
        tank.fill(new FluidVolume(WATER, Long.MAX_VALUE - 1), false);
        assertEquals(1, tank.fill(new FluidVolume(WATER, Long.MAX_VALUE), false));
        assertEquals(Long.MAX_VALUE, tank.drain(WATER, Long.MAX_VALUE, false));
        assertTrue(tank.contents().isEmpty());
        assertEquals(1, tank.fill(new FluidVolume(LAVA, 1), false));
        assertEquals(LAVA, tank.contents().orElseThrow().fluid());
        assertThrows(IllegalArgumentException.class, () -> new PortableFluidTank(0));
        assertThrows(IllegalArgumentException.class, () -> new FluidVolume(WATER, 0));
    }
}
