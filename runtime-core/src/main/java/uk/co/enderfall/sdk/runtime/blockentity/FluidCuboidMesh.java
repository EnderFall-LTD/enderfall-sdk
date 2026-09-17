package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.render.FluidRenderBounds;

/** Loader-neutral geometry only; native bridges supply texture, tint, light and transparency. */
public final class FluidCuboidMesh {
    private FluidCuboidMesh() { }
    public record Point(double x, double y, double z) { }
    /** Four counter-clockwise vertices viewed from outside the fluid, with outward normal. */
    public record Face(Point a, Point b, Point c, Point d, int normalX, int normalY, int normalZ) {
        /** Block-local texture coordinates, oriented consistently when viewed from outside. */
        public double u(Point point) {
            Objects.requireNonNull(point, "point");
            if (normalX < 0) return point.z();
            if (normalX > 0) return 1 - point.z();
            return normalZ < 0 ? 1 - point.x() : point.x();
        }
        /** Side textures are cropped, not stretched, as the surface moves. */
        public double v(Point point) {
            Objects.requireNonNull(point, "point");
            if (normalY > 0) return point.z();
            if (normalY < 0) return 1 - point.z();
            return 1 - point.y();
        }
    }
    public static List<Face> create(FluidRenderBounds bounds, long amount, long capacity) {
        Objects.requireNonNull(bounds, "bounds");
        double top = bounds.surfaceY(amount, capacity);
        if (amount == 0 || top == bounds.minY()) return List.of();
        double x = bounds.minX(), X = bounds.maxX(), y = bounds.minY(), z = bounds.minZ(), Z = bounds.maxZ();
        return List.of(
                new Face(new Point(x,y,z), new Point(X,y,z), new Point(X,y,Z), new Point(x,y,Z), 0,-1,0),
                new Face(new Point(x,top,z), new Point(x,top,Z), new Point(X,top,Z), new Point(X,top,z), 0,1,0),
                new Face(new Point(X,y,z), new Point(x,y,z), new Point(x,top,z), new Point(X,top,z), 0,0,-1),
                new Face(new Point(x,y,Z), new Point(X,y,Z), new Point(X,top,Z), new Point(x,top,Z), 0,0,1),
                new Face(new Point(x,y,z), new Point(x,y,Z), new Point(x,top,Z), new Point(x,top,z), -1,0,0),
                new Face(new Point(X,y,Z), new Point(X,y,z), new Point(X,top,z), new Point(X,top,Z), 1,0,0));
    }
}
