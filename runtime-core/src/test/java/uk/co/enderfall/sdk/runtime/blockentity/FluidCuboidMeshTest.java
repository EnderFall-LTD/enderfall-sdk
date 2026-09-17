package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.render.FluidRenderBounds;

class FluidCuboidMeshTest {
    private static final FluidRenderBounds BOUNDS = new FluidRenderBounds(.1,.1,.1,.9,.9,.9);
    @Test void sideTexturesHaveConsistentOrientationAndDoNotStretchWhenDraining() {
        var full = FluidCuboidMesh.create(BOUNDS, 1000, 1000);
        var half = FluidCuboidMesh.create(BOUNDS, 500, 1000);
        for (int index = 2; index < 6; index++) {
            var face = full.get(index);
            var drained = half.get(index);
            assertTrue(face.u(face.a()) < face.u(face.b()));
            assertEquals(face.u(face.a()), drained.u(drained.a()));
            assertEquals(face.v(face.a()), drained.v(drained.a()));
            assertEquals(.5, drained.v(drained.c()), 0.000001);
            assertEquals(.1, face.v(face.c()), 0.000001);
        }
        for (var face : full) for (var point : java.util.List.of(face.a(), face.b(), face.c(), face.d())) {
            assertTrue(face.u(point) >= 0 && face.u(point) <= 1);
            assertTrue(face.v(point) >= 0 && face.v(point) <= 1);
        }
    }
    @Test void calculatesHeightAndEmptyGeometryWithoutOverflow() {
        assertTrue(FluidCuboidMesh.create(BOUNDS, 0, 1000).isEmpty());
        assertEquals(.5, BOUNDS.surfaceY(500, 1000), 0.000001);
        assertEquals(.9, BOUNDS.surfaceY(Long.MAX_VALUE, Long.MAX_VALUE), 0.000001);
        assertEquals(6, FluidCuboidMesh.create(BOUNDS, 1, 1000).size());
        assertThrows(IllegalArgumentException.class, () -> BOUNDS.surfaceY(1, 0));
        assertThrows(IllegalArgumentException.class, () -> BOUNDS.surfaceY(1001, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FluidRenderBounds(0, 0, 0, Double.NaN, 1, 1));
    }
    @Test void everyFaceHasOutwardWindingAndStaysBelowSurface() {
        for (var face : FluidCuboidMesh.create(BOUNDS, 500, 1000)) {
            double ux = face.b().x()-face.a().x(), uy = face.b().y()-face.a().y(), uz = face.b().z()-face.a().z();
            double vx = face.c().x()-face.a().x(), vy = face.c().y()-face.a().y(), vz = face.c().z()-face.a().z();
            double outward = (uy*vz-uz*vy)*face.normalX() + (uz*vx-ux*vz)*face.normalY() + (ux*vy-uy*vx)*face.normalZ();
            assertTrue(outward > 0);
            for (var point : java.util.List.of(face.a(), face.b(), face.c(), face.d())) {
                assertTrue(point.y() >= .1 && point.y() <= .5);
            }
        }
    }
}
