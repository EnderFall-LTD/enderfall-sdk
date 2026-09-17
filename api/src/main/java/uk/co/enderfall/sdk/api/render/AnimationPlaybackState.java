package uk.co.enderfall.sdk.api.render;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Immutable server-authored playback descriptor, suitable for a visual snapshot.
 * A stopped descriptor freezes the pose at stopTick, rather than resetting it.
 */
@Experimental("Server-controlled render animation")
public record AnimationPlaybackState(String animation, long startTick, Long stopTick) {
    public AnimationPlaybackState {
        Objects.requireNonNull(animation, "animation");
        if (!animation.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid animation name");
        }
        if (startTick < 0 || (stopTick != null && stopTick < startTick)) {
            throw new IllegalArgumentException("Invalid animation timeline");
        }
    }

    public static AnimationPlaybackState playing(String name, long tick) {
        return new AnimationPlaybackState(name, tick, null);
    }

    /** Freezes playback. Stopping an already stopped descriptor is idempotent. */
    public AnimationPlaybackState stop(long tick) {
        if (tick < startTick) throw new IllegalArgumentException("Stop precedes animation start");
        return stopTick == null ? new AnimationPlaybackState(animation, startTick, tick) : this;
    }

    /** Samples using elapsed server ticks. Subtract integer ticks before conversion
     * to retain fractional-tick precision even in very old worlds.
     */
    public RenderTransform sample(RenderAnimation definition, long gameTick, float partialTick) {
        Objects.requireNonNull(definition, "definition");
        if (gameTick < 0 || !Float.isFinite(partialTick) || partialTick < 0 || partialTick > 1) {
            throw new IllegalArgumentException("Invalid render time");
        }
        if (definition.startTick() != 0) {
            throw new IllegalArgumentException("Named animation definitions must use relative start tick zero");
        }
        long sampledTick = stopTick == null ? gameTick : Math.min(gameTick, stopTick);
        double elapsed = sampledTick < startTick ? 0 : (double) (sampledTick - startTick);
        if (sampledTick >= startTick && (stopTick == null || gameTick < stopTick)) elapsed += partialTick;
        return definition.sample(elapsed);
    }
}
