package uk.co.enderfall.sdk.api.render;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Axis-aligned tank interior in block-local units, within one block. */
@Experimental("Block-entity fluid rendering foundation")
public record FluidRenderBounds(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
    public FluidRenderBounds {
        axis(minX, maxX); axis(minY, maxY); axis(minZ, maxZ);
    }
    private static void axis(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max > 1 || min >= max) {
            throw new IllegalArgumentException("Fluid bounds must be finite and satisfy 0 <= min < max <= 1");
        }
    }
    /** Bottom-up surface height; does not round to bucket or millibucket units. */
    public double surfaceY(long amount, long capacity) {
        if (capacity <= 0 || amount < 0 || amount > capacity) {
            throw new IllegalArgumentException("Invalid visual tank amount/capacity");
        }
        return minY + (maxY - minY) * ((double) amount / capacity);
    }
}
