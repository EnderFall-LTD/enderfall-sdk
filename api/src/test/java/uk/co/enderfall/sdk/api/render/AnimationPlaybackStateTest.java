package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AnimationPlaybackStateTest {
    private final RenderAnimation definition = new RenderAnimation(new RenderTransition(
            RenderTransform.at(0, 0, 0), RenderTransform.at(0, 0, 0).rotate(100, 0, 0),
            10, RenderTransition.Easing.LINEAR), RenderAnimation.Playback.ONCE);

    @Test void lateArrivalSamplesElapsedTimeAndStopHoldsPose() {
        var state = AnimationPlaybackState.playing("open", 100);
        assertEquals(55, state.sample(definition, 105, .5f).rotationX());
        assertEquals(0, state.sample(definition, 99, .5f).rotationX());
        var stopped = state.stop(106);
        assertEquals(60, stopped.sample(definition, 200, .5f).rotationX(), .0001f);
        assertSame(stopped, stopped.stop(300));
        assertEquals(100, state.sample(definition, 200, 0).rotationX());
    }

    @Test void preservesPartialTicksInOldWorldsAndRejectsInvalidState() {
        var state = AnimationPlaybackState.playing("open", Long.MAX_VALUE - 10);
        assertEquals(55, state.sample(definition, Long.MAX_VALUE - 5, .5f).rotationX());
        assertThrows(IllegalArgumentException.class, () -> AnimationPlaybackState.playing("../open", 0));
        assertThrows(IllegalArgumentException.class, () -> AnimationPlaybackState.playing("open", -1));
        assertThrows(IllegalArgumentException.class, () -> state.stop(0));
        assertThrows(IllegalArgumentException.class, () -> state.sample(definition, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.sample(definition.startingAt(1), 0, 0));
    }
}
