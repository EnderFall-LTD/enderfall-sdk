package uk.co.enderfall.sdk.api.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.registry.BlockSpec;

class BlockShapeTest {
    @Test void sixWayFacingIsExclusiveAndVerticalRotationMapsNorthToUp() {
        var six = BlockSpec.builder().horizontalFacing().sixWayFacing().build();
        assertTrue(six.sixWayFacing()); assertFalse(six.horizontalFacing());
        assertEquals(SixWayPlacement.VIEW_DIRECTION, six.sixWayPlacement());
        var attached = BlockSpec.builder().sixWayFacing(SixWayPlacement.CLICKED_FACE).build();
        assertEquals(SixWayPlacement.CLICKED_FACE, attached.sixWayPlacement());
        var horizontal = BlockSpec.builder().sixWayFacing().horizontalFacing().build();
        assertTrue(horizontal.horizontalFacing()); assertFalse(horizontal.sixWayFacing());
        var axis = BlockSpec.builder().sixWayFacing().axisFacing().build();
        assertTrue(axis.axisFacing()); assertFalse(axis.sixWayFacing()); assertFalse(axis.horizontalFacing());
        var backToSix = BlockSpec.builder().axisFacing().sixWayFacing().build();
        assertTrue(backToSix.sixWayFacing()); assertFalse(backToSix.axisFacing());
        assertFalse(BlockSpec.builder().copyFrom(six).build().sixWayFacing());
        assertFalse(BlockSpec.builder().copyFrom(axis).build().axisFacing());
        var north = BlockShape.box(2, 3, 0, 6, 8, 2);
        assertEquals(BlockShape.box(2, 14, 3, 6, 16, 8), north.rotateX(1));
        assertEquals(BlockShape.box(2, 0, 8, 6, 2, 13), north.rotateX(-1));
        assertEquals(north, north.rotateX(1).rotateX(3));
    }
    @Test void facingIsOptInAndPropertyCopyDoesNotCopyStateDefinitions() {
        assertFalse(BlockSpec.builder().build().horizontalFacing());
        var directional = BlockSpec.builder().horizontalFacing().build();
        assertTrue(directional.horizontalFacing());
        assertFalse(BlockSpec.builder().copyFrom(directional).build().horizontalFacing());
        assertTrue(BlockSpec.builder().horizontalFacing().copyFrom(BlockSpec.builder().build()).build().horizontalFacing());
    }
    @Test void unionIsImmutableAndBounded() {
        var original = new ArrayList<BlockShape.Box>();
        original.add(new BlockShape.Box(0, 0, 0, 1, 1, 1));
        var shape = new BlockShape(original);
        original.clear();
        assertEquals(1, shape.boxes().size());
        assertThrows(UnsupportedOperationException.class, () -> shape.boxes().clear());
        assertThrows(IllegalArgumentException.class, () -> new BlockShape(java.util.Collections.nCopies(65, shape.boxes().get(0))));
        var full = new BlockShape(java.util.Collections.nCopies(64, shape.boxes().get(0)));
        assertThrows(IllegalArgumentException.class, () -> BlockShape.union(full, shape));
        assertEquals(shape, BlockShape.union(BlockShape.empty(), shape));
    }
    @Test void invalidCoordinatesAreRejected() {
        for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -1, 16 }) {
            assertThrows(IllegalArgumentException.class, () -> BlockShape.box(invalid, 0, 0, 16, 16, 16));
        }
        assertThrows(IllegalArgumentException.class, () -> BlockShape.box(0, 0, 0, 17, 16, 16));
        assertThrows(IllegalArgumentException.class, () -> BlockShape.box(0, 0, 2, 1, 1, 1));
        assertThrows(NullPointerException.class, () -> new BlockShape(java.util.Arrays.asList((BlockShape.Box) null)));
    }
    @Test void rotationPreservesGeometryAndSupportsNegativeTurns() {
        var north = BlockShape.box(2, 1, 0, 6, 8, 3);
        assertEquals(BlockShape.box(13, 1, 2, 16, 8, 6), north.rotateY(1));
        assertEquals(north, north.rotateY(1).rotateY(1).rotateY(1).rotateY(1));
        assertEquals(north.rotateY(3), north.rotateY(-1));
        assertEquals(north, north.rotateY(Integer.MIN_VALUE));
        assertEquals(BlockShape.empty(), BlockShape.empty().rotateY(2));
    }
    @Test void collisionAndOutlineAreSeparateAndPropertyCopyDoesNotCopyBehavior() {
        var slab = BlockShape.box(0, 0, 0, 16, 8, 16);
        var spec = BlockSpec.builder().shape(slab).collisionShape(BlockShape.empty()).build();
        assertEquals(slab, spec.outlineShape().orElseThrow());
        assertEquals(BlockShape.empty(), spec.collisionShape().orElseThrow());
        assertTrue(BlockSpec.builder().copyFrom(spec).build().outlineShape().isEmpty());
        assertEquals(slab, BlockSpec.builder().shape(slab).copyFrom(spec).build().outlineShape().orElseThrow());
    }
}
