package uk.co.enderfall.sdk.runtime.fluid;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidTank;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/** Native-owned view: validates ownership on every access, including retained handles. */
public final class GuardedFluidTank implements FluidTank {
    private final FluidTank delegate;
    private final Runnable requireAccess;
    private final Runnable markChanged;

    /** markChanged must be a non-throwing native dirty notification, not a consumer callback. */
    public GuardedFluidTank(FluidTank delegate, Runnable requireAccess, Runnable markChanged) {
        this.delegate = Objects.requireNonNull(delegate);
        this.requireAccess = Objects.requireNonNull(requireAccess);
        this.markChanged = Objects.requireNonNull(markChanged);
    }

    public long capacity() { requireAccess.run(); return delegate.capacity(); }
    public Optional<FluidVolume> contents() { requireAccess.run(); return delegate.contents(); }
    public long fill(FluidVolume volume, boolean simulate) {
        requireAccess.run();
        long changed = delegate.fill(volume, simulate);
        if (!simulate && changed > 0) markChanged.run();
        return changed;
    }
    public long drain(ResourceId expected, long maximum, boolean simulate) {
        requireAccess.run();
        long changed = delegate.drain(expected, maximum, simulate);
        if (!simulate && changed > 0) markChanged.run();
        return changed;
    }
}
