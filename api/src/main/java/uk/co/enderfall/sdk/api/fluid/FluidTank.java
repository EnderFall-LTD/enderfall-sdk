package uk.co.enderfall.sdk.api.fluid;

import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Single-fluid tank view; use only on its owning game thread. Quantities are SDK units. */
@Experimental("Native tank registration and persistence are not yet available")
public interface FluidTank {
    long capacity();
    Optional<FluidVolume> contents();
    /** Returns accepted units. Simulation is a query, not a reservation. */
    long fill(FluidVolume volume, boolean simulate);
    /** Returns removed units of the expected fluid, without changing state when simulated. */
    long drain(ResourceId expected, long maximum, boolean simulate);
}
