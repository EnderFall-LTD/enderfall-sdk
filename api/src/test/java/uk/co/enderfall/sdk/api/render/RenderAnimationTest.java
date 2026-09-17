package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RenderAnimationTest {
    @Test void playbackModesUseAbsoluteStartAndExactBoundaries() {
        var start = RenderTransform.at(0, 0, 0);
        var end = start.rotate(360, 0, 0);
        var transition = new RenderTransition(start, end, 10, RenderTransition.Easing.LINEAR);
        var loop = new RenderAnimation(transition, RenderAnimation.Playback.LOOP).startingAt(100);
        assertSame(start, loop.sample(50));
        assertSame(start, loop.sample(100));
        assertEquals(180, loop.sample(105).rotationX());
        assertEquals(start, loop.sample(110));
        var once = new RenderAnimation(transition, RenderAnimation.Playback.ONCE, 100);
        assertEquals(180, once.sample(105).rotationX());
        assertSame(end, once.sample(110));
        assertSame(end, once.sample(Double.MAX_VALUE));
        var ping = new RenderAnimation(transition).startingAt(100);
        assertEquals(180, ping.sample(115).rotationX());
        assertEquals(start, ping.sample(120));
        assertThrows(NullPointerException.class, () -> new RenderAnimation(transition, null));
        for (double tick : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> loop.startingAt(tick));
        }
    }
    @Test void itemPlansSampleAnimationsAndKeepStaticCompatibility() {
        var start = RenderTransform.at(0, 1, 0);
        var animation = new RenderAnimation(new RenderTransition(start, start.rotate(90, 0, 0),
                10, RenderTransition.Easing.LINEAR));
        var item = new uk.co.enderfall.sdk.api.registry.ItemRef(
                uk.co.enderfall.sdk.api.ResourceId.of("minecraft", "apple"));
        var plan = BlockEntityRenderSpec.builder().animatedItem(item, animation)
                .animatedInventorySlot(0, animation, ItemRenderPose.GROUND).item(item, start).build();
        assertEquals(45, plan.items().get(0).sample(5).rotationX());
        assertEquals(ItemRenderPose.FIXED, plan.items().get(0).pose());
        assertEquals(ItemRenderPose.GROUND, plan.items().get(1).pose());
        assertSame(start, plan.items().get(2).sample(5));
        assertThrows(NullPointerException.class, () -> BlockEntityRenderSpec.builder().animatedItem(item, null));
        assertThrows(IllegalArgumentException.class, () -> BlockEntityRenderSpec.builder().animatedInventorySlot(-1, animation));
        var builder = BlockEntityRenderSpec.builder();
        for (int i = 0; i < 64; i++) builder.animatedItem(item, animation);
        assertThrows(IllegalArgumentException.class, () -> builder.animatedItem(item, animation));
    }
    @Test void finiteExtremeTimesAndDurationsDoNotOverflow() {
        var start = RenderTransform.at(0, 0, 0);
        var end = start.rotate(90, 0, 0);
        for (double duration : new double[] {Double.MIN_VALUE, 1e-300, 40, Double.MAX_VALUE}) {
            var animation = new RenderAnimation(new RenderTransition(start, end, duration, RenderTransition.Easing.LINEAR));
            var pose = assertDoesNotThrow(() -> animation.sample(Double.MAX_VALUE));
            assertTrue(pose.rotationX() >= 0 && pose.rotationX() <= 90);
        }
    }
    @Test void loopsWithPartialTicksAndReversesAtEndpoints() {
        var start = RenderTransform.at(0, 1, 0).pivot(.5f, 0, 1);
        var end = start.rotate(90, 0, 0);
        var animation = new RenderAnimation(new RenderTransition(start, end, 10, RenderTransition.Easing.LINEAR));
        assertEquals(start, animation.sample(0));
        assertEquals(end, animation.sample(10));
        assertEquals(start, animation.sample(20));
        assertEquals(22.5f, animation.sample(2.5).rotationX());
        assertEquals(animation.sample(2.5), animation.sample(17.5));
        assertEquals(animation.sample(2.5), animation.sample(22.5));
        assertThrows(IllegalArgumentException.class, () -> animation.sample(-1));
        assertThrows(IllegalArgumentException.class, () -> animation.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> animation.sample(Double.POSITIVE_INFINITY));
    }
}
