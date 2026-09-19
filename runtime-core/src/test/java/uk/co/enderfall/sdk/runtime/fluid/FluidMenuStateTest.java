package uk.co.enderfall.sdk.runtime.fluid;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.api.ui.MenuState;

class FluidMenuStateTest {
    @Test void displayIsAnImmutableSnapshotAndDoesNotOverflow() {
        var tank = new PortableFluidTank(Long.MAX_VALUE);
        tank.fill(new FluidVolume(ResourceId.parse("minecraft:water"), Long.MAX_VALUE), false);
        var state = MenuState.builder().tank("tank", tank).build();
        assertEquals("100", state.value("tank.percent"));
        assertEquals("minecraft:water", state.value("tank.fluid"));
        tank.drain(ResourceId.parse("minecraft:water"), Long.MAX_VALUE, false);
        assertEquals("100", state.value("tank.percent"));
        assertEquals("0", MenuState.builder().tank("tank", tank).build().value("tank.percent"));
    }

    @Test void invalidPrefixOrEntryLimitCannotPartiallyChangeBuilder() {
        var builder = MenuState.builder();
        int existingEntries = MenuState.MAXIMUM_ENTRIES - 2;
        for (int index = 0; index < existingEntries; index++) builder.value("entry" + index, index);
        var tank = new PortableFluidTank(100);
        assertThrows(IllegalArgumentException.class, () -> builder.tank("tank", tank));
        assertEquals(existingEntries, builder.build().values().size());
        assertFalse(builder.build().values().containsKey("tank.fluid"));
        assertThrows(IllegalArgumentException.class, () -> builder.tank("Bad prefix", tank));
    }
}
