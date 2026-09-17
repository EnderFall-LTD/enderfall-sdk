package uk.co.enderfall.sdk.api.blockentity;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Server-owned saved values. Access only on the owning game thread. */
@Experimental("Block-entity persistence foundation; native registration is not available yet")
public interface BlockEntityState {
    /** Starts or restarts a declared visual animation at the current server tick. */
    default void playAnimation(String name) { throw new UnsupportedOperationException("Animation playback unavailable"); }
    /** Freezes the current visual pose. */
    default void stopAnimation() { throw new UnsupportedOperationException("Animation playback unavailable"); }
    /** Pushes to an adjacent SDK-owned tank, respecting both faces. Does not load chunks. */
    default long pushFluid(uk.co.enderfall.sdk.api.fluid.FluidTankSpec source,
            uk.co.enderfall.sdk.api.fluid.FluidFace face, long maximum, boolean simulate) {
        throw new UnsupportedOperationException("Adjacent fluid transfer is unavailable");
    }
    default uk.co.enderfall.sdk.api.fluid.FluidTank tank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec spec) {
        throw new UnsupportedOperationException("Fluid tanks are not supported by this block-entity state");
    }
    int get(BlockEntityInt field);

    /** Updates a declared field and marks the owning storage dirty when its value changes. */
    void set(BlockEntityInt field, int value);
}
