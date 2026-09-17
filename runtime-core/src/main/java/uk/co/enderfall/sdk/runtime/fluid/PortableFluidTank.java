package uk.co.enderfall.sdk.runtime.fluid;

import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/**
 * Experimental single-fluid storage. All access must use the owning game thread.
 * Transfers between these tanks commit together without callbacks or partial drains.
 * Native world persistence, side policies and external loader storage are not wired yet.
 */
public final class PortableFluidTank implements uk.co.enderfall.sdk.api.fluid.FluidTank {
    private final long capacity;
    private ResourceId fluid;
    private long amount;
    private long revision;
    private long persistedRevision;

    public PortableFluidTank(long capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Tank capacity must be positive");
        this.capacity = capacity;
    }

    public long capacity() { return capacity; }
    public long revision() { return revision; }
    public boolean dirty() { return revision != persistedRevision; }

    /** Snapshot only: the caller is responsible for actually persisting the bytes. */
    public byte[] save() { return FluidTankCodec.encode(contents()); }

    /** Decode completely before applying. A rejected snapshot leaves the tank unchanged. */
    public void restore(byte[] bytes) { restore(FluidTankCodec.decode(bytes, capacity)); }

    /** Acknowledge only the revision whose snapshot was successfully saved. */
    public void markPersisted(long savedRevision) {
        if (savedRevision != revision) throw new IllegalStateException("Cannot acknowledge stale tank snapshot");
        persistedRevision = savedRevision;
    }
    public Optional<FluidVolume> contents() {
        return amount == 0 ? Optional.empty() : Optional.of(new FluidVolume(fluid, amount));
    }

    /** Returns accepted units; simulation never changes contents or revision. */
    public long fill(FluidVolume volume, boolean simulate) {
        Objects.requireNonNull(volume, "volume");
        validateId(volume.fluid());
        long accepted = accepted(volume.fluid(), volume.amount());
        if (!simulate && accepted > 0) {
            fluid = volume.fluid();
            amount += accepted;
            revision++;
        }
        return accepted;
    }

    /** Drains only the requested fluid. Zero is a no-op; negative limits are rejected. */
    public long drain(ResourceId expected, long maximum, boolean simulate) {
        Objects.requireNonNull(expected, "expected");
        requireLimit(maximum);
        long drained = expected.equals(fluid) ? Math.min(amount, maximum) : 0;
        if (!simulate && drained > 0) remove(drained);
        return drained;
    }

    /** Atomic for two SDK tanks on the same owning thread. Never use simulation as a reservation. */
    public long transferTo(PortableFluidTank destination, long maximum, boolean simulate) {
        Objects.requireNonNull(destination, "destination");
        requireLimit(maximum);
        if (destination == this || amount == 0 || maximum == 0) return 0;
        long moved = destination.accepted(fluid, Math.min(amount, maximum));
        if (!simulate && moved > 0) {
            destination.fluid = fluid;
            destination.amount += moved;
            destination.revision++;
            remove(moved);
        }
        return moved;
    }

    /** Validates a decoded snapshot before changing anything. Native codecs must validate their input too. */
    public void restore(Optional<FluidVolume> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        snapshot.ifPresent(value -> validateId(value.fluid()));
        if (snapshot.isPresent() && snapshot.get().amount() > capacity) {
            throw new IllegalArgumentException("Snapshot exceeds tank capacity");
        }
        if (contents().equals(snapshot)) return;
        fluid = snapshot.map(FluidVolume::fluid).orElse(null);
        amount = snapshot.map(FluidVolume::amount).orElse(0L);
        revision++;
    }

    private long accepted(ResourceId candidate, long maximum) {
        return amount == 0 || candidate.equals(fluid) ? Math.min(capacity - amount, maximum) : 0;
    }

    private void remove(long removed) {
        amount -= removed;
        if (amount == 0) fluid = null;
        revision++;
    }

    private static void requireLimit(long maximum) {
        if (maximum < 0) throw new IllegalArgumentException("Transfer limit cannot be negative");
    }

    private static void validateId(ResourceId id) {
        // ResourceId's alphabet is ASCII, so characters equal UTF-8 bytes.
        if (id.toString().length() > FluidTankCodec.MAXIMUM_ID_BYTES) {
            throw new IllegalArgumentException("Fluid ID exceeds save limit");
        }
    }
}
