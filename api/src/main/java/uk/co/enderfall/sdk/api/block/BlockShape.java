package uk.co.enderfall.sdk.api.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Immutable union of up to 64 block-local cuboids, measured in model pixels (0..16). */
@Experimental("Static outline and collision shapes")
public record BlockShape(List<Box> boxes) {
    public BlockShape {
        boxes = List.copyOf(boxes);
        if (boxes.size() > 64) throw new IllegalArgumentException("A block shape supports at most 64 boxes");
    }
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (!valid(minX, maxX) || !valid(minY, maxY) || !valid(minZ, maxZ))
                throw new IllegalArgumentException("Shape bounds must be finite, ordered, nonempty and within 0..16");
        }
        private static boolean valid(double min, double max) {
            return Double.isFinite(min) && Double.isFinite(max) && min >= 0 && max <= 16 && min < max;
        }
    }
    public static BlockShape empty() { return new BlockShape(List.of()); }
    public static BlockShape fullCube() { return box(0, 0, 0, 16, 16, 16); }
    public static BlockShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new BlockShape(List.of(new Box(x1, y1, z1, x2, y2, z2)));
    }
    public static BlockShape union(BlockShape... shapes) {
        List<Box> boxes = new ArrayList<>();
        for (BlockShape shape : shapes) {
            Objects.requireNonNull(shape, "shape");
            if (boxes.size() + shape.boxes.size() > 64) throw new IllegalArgumentException("Too many shape boxes");
            boxes.addAll(shape.boxes);
        }
        return new BlockShape(boxes);
    }
    /** Clockwise quarter-turns viewed from above; rotates around the center of the block. */
    public BlockShape rotateY(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        BlockShape result = this;
        for (int i = 0; i < turns; i++) result = new BlockShape(result.boxes.stream()
                .map(b -> new Box(16 - b.maxZ, b.minY, b.minX, 16 - b.minZ, b.maxY, b.maxX)).toList());
        return result;
    }
    /** Quarter-turns about X through the block center. One turn maps north to up. */
    public BlockShape rotateX(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        BlockShape result = this;
        for (int i = 0; i < turns; i++) result = new BlockShape(result.boxes.stream()
                .map(b -> new Box(b.minX, 16 - b.maxZ, b.minY, b.maxX, 16 - b.minZ, b.maxY)).toList());
        return result;
    }
}
