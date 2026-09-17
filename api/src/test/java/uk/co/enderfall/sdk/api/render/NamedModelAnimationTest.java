package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;

class NamedModelAnimationTest {
    @Test void commonDeclarationsAreBoundedAndMenuNamesMustExist() {
        var block = new BlockRef(ResourceId.of("test", "machine"));
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(block).menuAnimations("open", "close").build());
        var builder = BlockEntitySpec.builder(block).animation("open").animation("close").menuAnimations("open", "close");
        var spec = builder.build();
        assertEquals("open", spec.menuOpenAnimation().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> builder.animation("open"));
        assertThrows(UnsupportedOperationException.class, () -> spec.animations().add("other"));
        for (int i = 0; i < 30; i++) builder.animation("a" + i);
        assertThrows(IllegalArgumentException.class, () -> builder.animation("extra"));
    }
    @Test void namedDisplayUsesLateArrivalTimeAndIdleFallback() {
        var idle = RenderTransform.at(0, 0, 0);
        var opening = new RenderAnimation(new RenderTransition(idle, idle.rotate(90, 0, 0),
                10, RenderTransition.Easing.LINEAR), RenderAnimation.Playback.ONCE);
        var model = new ModelRef(ResourceId.of("test", "block/lid"));
        var display = BlockEntityRenderSpec.builder().namedModel(model, idle, Map.of("open", opening)).build().models().get(0);
        assertSame(idle, display.sample(null, 105, 0));
        assertEquals(45, display.sample(AnimationPlaybackState.playing("open", 100), 105, 0).rotationX());
        assertSame(idle, display.sample(AnimationPlaybackState.playing("close", 100), 105, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockEntityRenderSpec.builder().namedModel(model, idle, Map.of("open", opening.startingAt(5))));
    }
}
