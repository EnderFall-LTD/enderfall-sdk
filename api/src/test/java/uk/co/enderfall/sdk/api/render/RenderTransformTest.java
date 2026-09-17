package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RenderTransformTest {
    @Test void preservesOriginBasedConstructorAndDefaults() {
        assertEquals(new RenderTransform(1, 2, 3, 0, 0, 0, 1, 0, 0, 0),
                RenderTransform.at(1, 2, 3));
        assertEquals(new RenderTransform(1, 2, 3, 10, 20, 30, 2, 0, 0, 0),
                new RenderTransform(1, 2, 3, 10, 20, 30, 2));
    }

    @Test void fluentOperationsRetainPivotRegardlessOfOrder() {
        var original = RenderTransform.at(0, 1, 0);
        var expected = new RenderTransform(0, 1, 0, -45, 0, 0, 2, .5f, 0, 1);
        assertEquals(expected, original.pivot(.5f, 0, 1).rotate(-45, 0, 0).scaled(2));
        assertEquals(expected, original.scaled(2).rotate(-45, 0, 0).pivot(.5f, 0, 1));
        assertEquals(0, original.pivotZ());
    }

    @Test void rejectsInvalidPivotOnEveryAxis() {
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 16.01f, -16.01f}) {
            assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(0, 0, 0).pivot(value, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(0, 0, 0).pivot(0, value, 0));
            assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(0, 0, 0).pivot(0, 0, value));
        }
        assertDoesNotThrow(() -> RenderTransform.at(0, 0, 0).pivot(-16, 16, 0));
    }
}
