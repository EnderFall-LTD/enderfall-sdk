package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.render.AnimationPlaybackState;

class RenderAnimationSnapshotTest {
    @Test void roundTripsPlayingStoppedAndReset() {
        for (var state : new AnimationPlaybackState[] {null,
                AnimationPlaybackState.playing("open", 100), AnimationPlaybackState.playing("open", 100).stop(105)}) {
            var bytes = RenderAnimationSnapshot.encode(state);
            assertEquals(state, RenderAnimationSnapshot.decode(bytes, Set.of("open")));
            assertArrayEquals(bytes, RenderAnimationSnapshot.encode(state));
        }
    }
    @Test void rejectsTruncationTrailingBytesUnknownNamesAndInvalidSchema() {
        byte[] bytes = RenderAnimationSnapshot.encode(AnimationPlaybackState.playing("open", 100));
        for (int length = 0; length < bytes.length; length++) {
            byte[] cut = Arrays.copyOf(bytes, length);
            assertThrows(IllegalArgumentException.class, () -> RenderAnimationSnapshot.decode(cut, Set.of("open")));
        }
        assertThrows(IllegalArgumentException.class, () -> RenderAnimationSnapshot.decode(Arrays.copyOf(bytes, bytes.length + 1), Set.of("open")));
        assertThrows(IllegalArgumentException.class, () -> RenderAnimationSnapshot.decode(bytes, Set.of("close")));
        byte[] invalid = bytes.clone(); invalid[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> RenderAnimationSnapshot.decode(invalid, Set.of("open")));
        assertThrows(IllegalArgumentException.class, () -> RenderAnimationSnapshot.decode(new byte[84], Set.of("open")));
    }
}
