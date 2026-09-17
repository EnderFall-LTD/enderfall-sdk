package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RenderTransitionTest {
    private final RenderTransform closed = RenderTransform.at(0, 1, 0).pivot(.5f, 0, 1);
    private final RenderTransform open = closed.rotate(90, 0, 0);

    @Test void samplesPartialTicksAndClampsEndpoints() {
        var transition = new RenderTransition(closed, open, 10, RenderTransition.Easing.LINEAR);
        assertSame(closed, transition.sample(-1));
        assertSame(open, transition.sample(20));
        assertEquals(22.5f, transition.sample(2.5).rotationX());
        assertEquals(closed.pivotZ(), transition.sample(2.5).pivotZ());
        assertEquals(transition.sample(2.5), transition.sample(2.5));
    }

    @Test void smoothstepEasesAndAuthoredTurnsArePreserved() {
        var transition = new RenderTransition(closed, open, 10, RenderTransition.Easing.SMOOTHSTEP);
        assertEquals(14.0625f, transition.sample(2.5).rotationX());
        assertEquals(45, transition.sample(5).rotationX());
        assertEquals(180, closed.interpolate(closed.rotate(360, 0, 0), .5f).rotationX());
    }

    @Test void interpolatesEveryComponent() {
        var a = new RenderTransform(-2, -4, -6, -90, -180, -360, 1, -2, -4, -6);
        var b = new RenderTransform(2, 4, 6, 90, 180, 360, 3, 2, 4, 6);
        assertEquals(new RenderTransform(0, 0, 0, 0, 0, 0, 2, 0, 0, 0), a.interpolate(b, .5f));
    }

    @Test void rejectsInvalidInputs() {
        for (double duration : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new RenderTransition(closed, open, duration, RenderTransition.Easing.LINEAR));
        }
        var transition = new RenderTransition(closed, open, 10, RenderTransition.Easing.LINEAR);
        assertThrows(IllegalArgumentException.class, () -> transition.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> transition.sample(Double.POSITIVE_INFINITY));
        for (float progress : new float[] {-1, 2, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> closed.interpolate(open, progress));
        }
        assertThrows(NullPointerException.class, () -> closed.interpolate(null, .5f));
    }
}
