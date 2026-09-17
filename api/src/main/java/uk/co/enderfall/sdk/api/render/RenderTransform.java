package uk.co.enderfall.sdk.api.render;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Block-local translation, XYZ rotations in degrees and positive uniform scale.
 * Origin is the block's minimum corner; one coordinate unit is one block.
 * Native bridges compose placement translation, pivot translation, X/Y/Z rotations,
 * uniform scale, then negative pivot translation. The pivot is in model-local units;
 * it stays at placement plus pivot as rotation and scale change.
 */
@Experimental("Portable block-entity item rendering")
public record RenderTransform(float x, float y, float z,
                              float rotationX, float rotationY, float rotationZ, float scale,
                              float pivotX, float pivotY, float pivotZ) {
    public RenderTransform {
        coordinate(x); coordinate(y); coordinate(z);
        coordinate(pivotX); coordinate(pivotY); coordinate(pivotZ);
        rotation(rotationX); rotation(rotationY); rotation(rotationZ);
        if (!Float.isFinite(scale) || scale <= 0 || scale > 16) {
            throw new IllegalArgumentException("Render scale must be finite and in (0, 16]");
        }
    }

    /** Retains the original origin-based transform contract. */
    public RenderTransform(float x, float y, float z, float rotationX, float rotationY,
                           float rotationZ, float scale) {
        this(x, y, z, rotationX, rotationY, rotationZ, scale, 0, 0, 0);
    }

    /** Selects the model-local point around which rotation and scale operate. */
    public RenderTransform pivot(float localX, float localY, float localZ) {
        return new RenderTransform(x, y, z, rotationX, rotationY, rotationZ, scale,
                localX, localY, localZ);
    }

    public static RenderTransform at(float x, float y, float z) {
        return new RenderTransform(x, y, z, 0, 0, 0, 1);
    }

    public RenderTransform rotate(float xDegrees, float yDegrees, float zDegrees) {
        return new RenderTransform(x, y, z, xDegrees, yDegrees, zDegrees, scale, pivotX, pivotY, pivotZ);
    }

    public RenderTransform scaled(float value) {
        return new RenderTransform(x, y, z, rotationX, rotationY, rotationZ, value, pivotX, pivotY, pivotZ);
    }

    /** Interpolates all components with progress in [0,1]. Rotations follow the
     * authored angles, not the shortest arc: 0 to 360 describes a full turn.
     * Matching endpoint pivots keep a hinge fixed throughout the transition.
     */
    public RenderTransform interpolate(RenderTransform target, float progress) {
        java.util.Objects.requireNonNull(target, "target");
        if (!Float.isFinite(progress) || progress < 0 || progress > 1) {
            throw new IllegalArgumentException("Render progress must be finite and within 0..1");
        }
        if (progress == 0) return this;
        if (progress == 1) return target;
        return new RenderTransform(
                mix(x, target.x, progress), mix(y, target.y, progress), mix(z, target.z, progress),
                mix(rotationX, target.rotationX, progress), mix(rotationY, target.rotationY, progress),
                mix(rotationZ, target.rotationZ, progress), mix(scale, target.scale, progress),
                mix(pivotX, target.pivotX, progress), mix(pivotY, target.pivotY, progress),
                mix(pivotZ, target.pivotZ, progress));
    }

    private static float mix(float from, float to, float progress) {
        return (float) ((1.0 - progress) * from + (double) progress * to);
    }

    private static void coordinate(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 16) {
            throw new IllegalArgumentException("Render coordinates must be finite and within -16..16");
        }
    }

    private static void rotation(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 360) {
            throw new IllegalArgumentException("Render rotations must be finite and within -360..360 degrees");
        }
    }
}
