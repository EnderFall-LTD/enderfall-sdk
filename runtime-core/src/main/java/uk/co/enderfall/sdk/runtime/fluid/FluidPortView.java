package uk.co.enderfall.sdk.runtime.fluid;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidPortMode;
import uk.co.enderfall.sdk.api.fluid.FluidTank;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/** Fixed access policy layered over a guarded tank; does not register a loader capability. */
public final class FluidPortView implements FluidTank {
    private final FluidTank tank;
    private final FluidPortMode mode;

    public FluidPortView(FluidTank tank, FluidPortMode mode) {
        this.tank = Objects.requireNonNull(tank);
        this.mode = Objects.requireNonNull(mode);
    }

    public long capacity() { return tank.capacity(); }
    public Optional<FluidVolume> contents() { return tank.contents(); }
    public long fill(FluidVolume volume, boolean simulate) {
        Objects.requireNonNull(volume);
        // Even denied operations must validate the native owner of a retained view.
        if (!mode.allowsFill()) { tank.capacity(); return 0; }
        return tank.fill(volume, simulate);
    }
    public long drain(ResourceId expected, long maximum, boolean simulate) {
        Objects.requireNonNull(expected);
        if (maximum < 0) throw new IllegalArgumentException("Drain limit cannot be negative");
        if (!mode.allowsDrain()) { tank.capacity(); return 0; }
        return tank.drain(expected, maximum, simulate);
    }
}
